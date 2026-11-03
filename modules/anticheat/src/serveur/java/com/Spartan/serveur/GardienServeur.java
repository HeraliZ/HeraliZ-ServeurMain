package com.Spartan.serveur;

import com.Spartan.Networks.GardienDefiPacket;
import com.Spartan.Networks.GardienPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Spartan;
import com.Spartan.ban.PlayerRegistry;
import com.Spartan.capture.CaptureManager;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.integrity.GardienJuge;
import com.Spartan.integrity.GardienPreuve;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Juge les rapports du Gardien, l'anticheat du launcher HeraliZ (launcher gardien.js, client/Gardien.java), section
 * {@code [gardien]} de la config. Le serveur décide seul, sur ce qu'il vérifie lui-même (audit du 02/11, point 2) :
 * <ul>
 *   <li>la clé du Gardien de la connexion vient du jeton signé par le site ({@link #lier}, appelé par LauncherGate) ;</li>
 *   <li>chaque rapport transmis par le jeu est vérifié par {@link GardienJuge} : signature du launcher, numéro, défi de
 *   la connexion (tiré ici et envoyé au jeu chaque minute) ;</li>
 *   <li>alertes « Gardien » (programme de triche ouvert, jar changé pendant la partie, rapport refusé, jeu lancé sans
 *   Gardien, ancien launcher) et « Gardien muet » (plus de rapport prouvé et frais : launcher fermé de force, ou jeu
 *   modifié qui ne transmet plus ou transmet de vieux rapports).</li>
 * </ul>
 * Une alerte par fait et par connexion ; jamais d'expulsion, sauf {@code required} (jeu lancé sans Gardien),
 * {@code ancienLauncher = "refuser"} et {@code preuveExigee = "refuser"} (03/11 : un launcher 1.16 ou plus récent doit
 * prouver qu'il veille juste après le /login, sinon c'est un launcher modifié ou imité ; refus de connexion propre, avec
 * un message clair). Programme d'injection vu par le Gardien : expulsion ({@link com.Spartan.integrity.Injection}).
 */
public final class GardienServeur {
    private GardienServeur() {
    }

    private static final long ECART_MS = 5_000L;
    private static final Pattern OUTIL = Pattern.compile("^[a-z0-9 ._-]{1,32}$");
    /** Un ancien launcher chez un joueur déjà venu avec un Gardien prouvé depuis moins de 30 jours : alerte. */
    private static final long RETOUR_ARRIERE_MS = 30L * 24 * 3600 * 1000;

    private static final class Etat {
        final long arrivee = System.currentTimeMillis();
        /** Dernier paquet reçu, dernier rapport prouvé et frais (la preuve que le Gardien veille). */
        long dernier, dernierFrais;
        boolean absent, muet, lectureSignalee, ancienSignale;
        /** Première preuve du Gardien (03/11) : refus déjà décidé pour cette connexion. */
        boolean preuveRefusee;
        /** Dernières alertes « Gardien muet » (rapport muet, silence) : redites toutes les 10 min, pas une seule fois. */
        long muetA, silenceA;
        int jars;
        final Set<String> outils = new HashSet<>();
        final Set<String> invalides = new HashSet<>();
    }

    /** État de la connexion (remis à zéro tant que le joueur n'est pas vérifié). */
    private static final Map<UUID, Etat> ETATS = new ConcurrentHashMap<>();
    /** Clé du Gardien de la connexion, connue dès le jeton (avant la vérification) : gardée jusqu'à la déconnexion. */
    private static final Map<UUID, GardienJuge> JUGES = new ConcurrentHashMap<>();
    /** Une surveillance coupée pendant la partie est redite toutes les 10 min (avant : une fois, puis plus rien). */
    private static final long RAPPEL_MS = 10 * 60_000L;

    public static void oublier(UUID id) {
        ETATS.remove(id);
        JUGES.remove(id);
    }

    /**
     * Clé du Gardien de cette connexion, d'après le jeton de lancement (fil du serveur, LauncherGate) : {@code cle} est
     * la clé publique annoncée par le jeu, déjà comparée à l'engagement du jeton quand {@code lien} vaut PROUVE.
     */
    public static void lier(ServerPlayer p, GardienJuge.Lien lien, String cle) {
        GardienJuge juge = new GardienJuge(lien, lien == GardienJuge.Lien.PROUVE || lien == GardienJuge.Lien.NON_VERIFIE
                ? GardienPreuve.cle(cle) : null);
        JUGES.put(p.getUUID(), juge);
        if (lien == GardienJuge.Lien.PROUVE && !fictif(p)) {
            PlayerRegistry.notePreuveGardien(p.getUUID(), p.getName().getString());
        }
    }

    /** État du Gardien de ce joueur, en clair (message « Jeu validé ») : jamais exigé pour la validation. */
    public static String etat(UUID id) {
        if (!SpartanConfig.GARDIEN_ENABLED.get()) {
            return "désactivé sur le serveur";
        }
        GardienJuge juge = JUGES.get(id);
        String lien = juge == null ? "" : switch (juge.lien()) {
            case PROUVE -> "";
            case NON_VERIFIE -> " (clé non liée au jeton : serveur sans clé des jetons)";
            case ANCIEN -> " (ancien launcher : rapports non prouvés, à mettre à jour)";
            case SANS_JETON -> " (sans jeton du launcher : rapports non prouvés)";
        };
        Etat e = ETATS.get(id);
        if (e == null) {
            return "pas encore de rapport (un toutes les 15 s)" + lien;
        }
        if (e.absent) {
            return "jeu lancé sans le Gardien (alerte envoyée)";
        }
        if (e.muet) {
            return "muet (alerte envoyée)" + lien;
        }
        if (!e.invalides.isEmpty()) {
            return "rapport refusé (alerte envoyée)" + lien;
        }
        if (!e.outils.isEmpty() || e.jars > 0) {
            return "a vu quelque chose (alerte envoyée)" + lien;
        }
        if (juge != null && juge.lien() == GardienJuge.Lien.PROUVE) {
            return e.dernierFrais == 0 ? "pas encore de rapport prouvé (un toutes les 15 s)"
                    : "actif, rapports signés par le launcher vérifiés par le serveur";
        }
        return e.dernier == 0 ? "pas encore de rapport (un toutes les 15 s)" + lien : "actif, rapports reçus" + lien;
    }

    private static boolean fictif(ServerPlayer p) {
        return p instanceof FakePlayer || p.getTags().contains("heraliz_bot");
    }

    /** Rapport transmis par le jeu du joueur (thread du serveur, joueur vérifié). */
    static void recevoir(ServerPlayer player, GardienPacket paquet) {
        if (!SpartanConfig.GARDIEN_ENABLED.get() || fictif(player)) {
            return;
        }
        Etat e = ETATS.computeIfAbsent(player.getUUID(), u -> new Etat());
        long now = System.currentTimeMillis();
        if (e.dernier != 0 && now - e.dernier < ECART_MS) {
            return; // un jeu modifié qui inonderait le serveur de rapports
        }
        e.dernier = now;
        String nom = player.getName().getString();
        GardienJuge juge = JUGES.computeIfAbsent(player.getUUID(), u -> new GardienJuge(GardienJuge.Lien.SANS_JETON, null));
        GardienJuge.Verdict v = juge.juger(paquet.etat(), paquet.ligne(), paquet.signature(), paquet.pid());

        switch (v.sorte()) {
            case ABSENT -> {
                if (e.absent) {
                    return;
                }
                e.absent = true;
                if (SpartanConfig.GARDIEN_REQUIRED.get()) {
                    Spartan.LOGGER.info("[Spartan] {} refusé : jeu lancé sans le Gardien du launcher.", nom);
                    CheckManager.flag("Launcher", player, "connexion refusée : jeu lancé sans le Gardien du launcher"
                            + " (launcher plus ancien que 1.14 ou launcher imité) ; invité à mettre le launcher à jour");
                    player.connection.disconnect(launcherAncien());
                    return;
                }
                CheckManager.flag("Gardien", player, "jeu lancé sans le Gardien du launcher (launcher plus ancien que 1.14.0"
                        + " ou autre lanceur)");
            }
            case MUET -> {
                if (!e.muet || now - e.muetA > RAPPEL_MS) {
                    e.muet = true;
                    e.muetA = now;
                    CheckManager.flag("Gardien muet", player, "le launcher ne surveille plus le jeu (aucun rapport lisible :"
                            + " " + court(paquet.etat()) + ") : launcher fermé de force pendant la partie ?");
                }
            }
            case INVALIDE -> {
                String raison = court(v.raison());
                // avant toute preuve, un rapport signé avec une autre clé que celle du jeton (ou un « sans Gardien » démenti
                // par le jeton) : launcher imité ou jeu qui fabrique ses rapports. Refus propre (03/11).
                if (e.dernierFrais == 0 && (raison.equals("signature") || raison.equals("absent"))
                        && juge.lien() == GardienJuge.Lien.PROUVE && refuserPreuve(player, e, "rapport du Gardien refusé ("
                        + raison + ") avant toute preuve : " + explication(raison))) {
                    return;
                }
                if (e.invalides.add(raison)) {
                    CheckManager.flag("Gardien", player, "rapport du launcher refusé par le serveur (" + raison + ") : "
                            + explication(raison));
                    capture(player, "rapport du Gardien refusé");
                }
            }
            case DOUBLON -> {
                // le jeu relit le fichier plus souvent que le launcher ne l'écrit : rien de neuf
            }
            case RAPPORT -> rapport(player, e, juge, v, now);
        }
    }

    private static String explication(String raison) {
        return switch (raison) {
            case "signature" -> "rapport inventé ou retouché (pas signé par le launcher de ce lancement) : jeu modifié ?";
            case "rejoue" -> "ancien rapport renvoyé à la place du dernier : jeu modifié ?";
            case "pid" -> "rapport du launcher pour un autre jeu";
            case "absent" -> "le jeu dit avoir été lancé sans le Gardien alors que son jeton prouve le contraire : jeu modifié ?";
            default -> "rapport illisible : jeu modifié ?";
        };
    }

    private static void rapport(ServerPlayer player, Etat e, GardienJuge juge, GardienJuge.Verdict v, long now) {
        GardienPreuve.Rapport r = v.rapport();
        if (v.frais() || !juge.avecCle()) {
            // prouvé et frais ; ou ancien launcher / sans jeton (rien de plus à attendre, jugé sur la réception)
            e.muet = false;
            e.silenceA = 0;
            if (v.frais()) {
                e.dernierFrais = now;
            }
        }
        // tous les faits nouveaux d'un rapport en UNE alerte (deux alertes du même joueur à moins de 5 s : la 2e était
        // avalée alors que son fait était déjà noté, donc plus jamais signalé). Un rapport bien signé mais pas frais dit
        // quand même vrai (le launcher l'a écrit) : ses faits comptent.
        String preuve = v.prouve() ? "" : " (rapport non prouvé : " + (juge.lien() == GardienJuge.Lien.ANCIEN
                ? "ancien launcher)" : "clé du Gardien non liée au jeton)");
        List<String> faits = new ArrayList<>();
        if (!r.lecture() && !e.lectureSignalee) {
            e.lectureSignalee = true;
            faits.add("le launcher n'arrive pas à lister les programmes du PC (indice faible)");
        }
        List<String> nouveaux = new ArrayList<>();
        List<String> ignores = SpartanConfig.getGardienIgnoredTools();
        if (!r.outils().isEmpty()) {
            for (String o : r.outils().split(",")) {
                String t = o.trim();
                if (OUTIL.matcher(t).matches() && !ignores.contains(t) && e.outils.add(t)) {
                    nouveaux.add(t);
                }
            }
        }
        // injecteurs et éditeurs de mémoire (liste injection.outils) : expulsion directe (03/11), capture comprise
        List<String> aExpulser = com.Spartan.integrity.Injection.outils(nouveaux);
        if (!nouveaux.isEmpty()) {
            List<String> lisibles = new ArrayList<>();
            for (String o : nouveaux) {
                // launcher 1.16.2 : « outil.titre » = vu seulement dans le titre d'une fenêtre (fichier, page) : alerte seule
                lisibles.add(o.endsWith(".titre") ? o.substring(0, o.length() - 6) + " (titre d'une fenêtre seulement)" : o);
            }
            faits.add("programme de triche ou d'injection ouvert sur le PC : " + String.join(", ", lisibles));
            if (aExpulser.isEmpty()) {
                capture(player, "programme de triche ouvert (" + String.join(", ", nouveaux) + ")");
            }
        }
        if (r.jars() > e.jars) {
            e.jars = r.jars();
            faits.add(r.jars() + " jar(s) du jeu changé(s), ajouté(s) ou retiré(s) pendant la partie");
        }
        if (!faits.isEmpty()) {
            CheckManager.flag("Gardien", player, String.join(" ; ", faits) + preuve);
        }
        if (!aExpulser.isEmpty()) {
            com.Spartan.integrity.Injection.expulser(player, "programme d'injection ou d'édition de la mémoire ouvert sur le PC : "
                    + String.join(", ", aExpulser) + preuve, "Gardien du launcher");
        }
    }

    /**
     * Une fois par seconde (thread du serveur) : défis des connexions, ancien launcher, silence (plus de rapport prouvé et
     * frais depuis {@code silenceSeconds}).
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || !SpartanConfig.GARDIEN_ENABLED.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        long silence = SpartanConfig.GARDIEN_SILENCE_SECONDS.get() * 1000L;
        long dureeDefi = SpartanConfig.GARDIEN_DEFI_SECONDS.get() * 1000L;
        List<ServerPlayer> anciens = null;
        sansPreuve.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (fictif(p)) {
                continue;
            }
            if (!com.Spartan.integrity.PorteJoueurs.estVerifie(p.getUUID())) {
                ETATS.remove(p.getUUID()); // rapports pas encore crus (ReceptionServeur) : le silence compte après le /login
                continue;
            }
            Etat e = ETATS.computeIfAbsent(p.getUUID(), u -> new Etat());
            GardienJuge juge = JUGES.get(p.getUUID());
            // launcher 1.16 ou plus récent : première preuve exigée dans le délai (03/11)
            if (juge != null && juge.lien() == GardienJuge.Lien.PROUVE && e.dernierFrais == 0 && !e.preuveRefusee
                    && now - e.arrivee > SpartanConfig.GARDIEN_PREUVE_SECONDES.get() * 1000L) {
                sansPreuve.add(p); // refusé hors de la boucle (un refus retire le joueur de la liste)
                e.preuveRefusee = true;
            }
            if (juge != null) {
                String defi = juge.defiAEnvoyer(now, dureeDefi);
                if (defi != null) {
                    ModMessages.sendToPlayer(new GardienDefiPacket(defi), p);
                }
                if (juge.lien() == GardienJuge.Lien.ANCIEN && !e.ancienSignale) {
                    e.ancienSignale = true;
                    if (anciens == null) {
                        anciens = new ArrayList<>(); // rare : une fois par connexion d'un ancien launcher
                    }
                    anciens.add(p);
                }
            }
            if (e.absent || (e.silenceA != 0 && now - e.silenceA < RAPPEL_MS)) {
                continue;
            }
            boolean preuveAttendue = juge != null && juge.avecCle();
            long reference = preuveAttendue ? e.dernierFrais : e.dernier;
            long depuis = reference == 0 ? e.arrivee + 30_000L : reference;
            if (now - depuis > silence) {
                e.silenceA = now;
                String detail;
                if (preuveAttendue && e.dernier != 0 && now - e.dernier <= silence) {
                    detail = "rapports reçus, mais aucun signé avec le défi de cette connexion depuis "
                            + (reference == 0 ? "la connexion" : (now - reference) / 1000 + " s")
                            + " (jeu modifié qui renvoie de vieux rapports, ou défi du serveur jamais transmis au launcher ?)";
                } else if (reference == 0) {
                    detail = "aucun rapport du Gardien depuis la connexion (jeu modifié qui ne l'envoie pas ?)";
                } else {
                    detail = "plus aucun rapport du Gardien depuis " + (now - reference) / 1000 + " s (jeu ou launcher arrêté de force ?)";
                }
                CheckManager.flag("Gardien muet", p, detail);
            }
        }
        if (anciens != null) {
            anciens.forEach(GardienServeur::ancienLauncher); // hors de la boucle : un refus peut retirer un joueur
        }
        for (ServerPlayer p : sansPreuve) {
            Etat e = ETATS.get(p.getUUID());
            if (e != null && !p.hasDisconnected()) {
                e.preuveRefusee = false;
                refuserPreuve(p, e, "aucun rapport signé du Gardien avec le défi de la connexion "
                        + SpartanConfig.GARDIEN_PREUVE_SECONDES.get() + " s après le /login (" + detailSilence(e) + ")");
            }
        }
        sansPreuve.clear();
    }

    /** Joueurs à refuser faute de première preuve (rempli et vidé à chaque passage, fil du serveur). */
    private static final List<ServerPlayer> sansPreuve = new ArrayList<>();

    private static String detailSilence(Etat e) {
        if (e.dernier == 0) {
            return "aucun rapport transmis par le jeu : launcher imité, Gardien arrêté, ou jeu qui ne le transmet pas";
        }
        if (e.muet) {
            return "le jeu ne trouve pas de rapport lisible du launcher";
        }
        return "rapports reçus mais jamais frais ni prouvés : vieux rapports rejoués ou défi jamais transmis au launcher";
    }

    /**
     * Première preuve du Gardien manquante ou fausse (launcher 1.16 ou plus récent) : refus propre selon
     * {@code preuveExigee}, sinon alerte. @return vrai si le joueur a été refusé.
     */
    private static boolean refuserPreuve(ServerPlayer p, Etat e, String raison) {
        if (e.preuveRefusee) {
            return false;
        }
        e.preuveRefusee = true;
        boolean refuser = "refuser".equals(SpartanConfig.GARDIEN_PREUVE_EXIGEE.get());
        CheckManager.flag("Launcher", p, (refuser ? "connexion refusée : " : "") + "launcher non conforme — " + raison
                + " (launcher modifié ou imité, ou launcher fermé dès le lancement)");
        if (!refuser) {
            return false;
        }
        Spartan.LOGGER.info("[Spartan] {} refusé : Gardien du launcher sans preuve ({}).", p.getName().getString(), raison);
        p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.launcher_preuve",
                "Connexion refusée : le launcher HeraliZ ne répond pas (son anticheat doit rester ouvert pendant la partie).\n"
                        + "Ferme le jeu, relance-le depuis le launcher HeraliZ à jour et laisse le launcher ouvert. Si ça"
                        + " recommence, contacte le staff.\n\nLauncher à jour :\n%s", SpartanConfig.lienLauncher()));
        return true;
    }

    /** Message d'un launcher trop ancien (ou sans Gardien) : se mettre à jour, pas un reproche. */
    private static Component launcherAncien() {
        return Component.translatableWithFallback("spartan.expulsion.launcher_ancien",
                "Ton launcher HeraliZ n'est pas à jour.\nFerme-le complètement puis rouvre-le : il se met à jour tout seul."
                        + " Sinon, télécharge le nouveau launcher, installe-le, puis relance le jeu depuis le launcher.\n\n"
                        + "Télécharger le launcher (Windows) :\n%s\nMac et Linux : page Launcher de heraliz.fr",
                SpartanConfig.lienLauncher());
    }

    /** Jeton sans clé du Gardien (launcher 1.15.x ou plus ancien) : selon {@code ancienLauncher}, et retour en arrière. */
    private static void ancienLauncher(ServerPlayer p) {
        long derniere = PlayerRegistry.preuveGardien(p.getUUID());
        boolean retour = derniere != 0 && System.currentTimeMillis() - derniere < RETOUR_ARRIERE_MS;
        String regle = SpartanConfig.GARDIEN_ANCIEN_LAUNCHER.get();
        if ("refuser".equals(regle)) {
            Spartan.LOGGER.info("[Spartan] {} refusé : ancien launcher (Gardien non prouvé).", p.getName().getString());
            CheckManager.flag("Gardien", p, "connexion refusée : ancien launcher, rapports du Gardien non prouvés"
                    + (retour ? " (avait le launcher à jour)" : "") + " ; invité à mettre le launcher à jour");
            p.connection.disconnect(launcherAncien());
            return;
        }
        if (retour) {
            CheckManager.flag("Gardien", p, "revient avec un ancien launcher alors qu'il avait le launcher à jour (Gardien"
                    + " prouvé) : ses rapports ne sont plus prouvés (launcher remplacé ou imité ?)");
        } else if ("alerte".equals(regle)) {
            CheckManager.flag("Gardien", p, "ancien launcher : rapports du Gardien non prouvés (un jeu modifié peut les"
                    + " inventer) ; lui demander de mettre le launcher à jour");
        }
    }

    private static void capture(ServerPlayer player, String raison) {
        if (SpartanConfig.GARDIEN_CAPTURE.get()) {
            CaptureManager.demander(player, raison);
        }
    }

    private static String court(String s) {
        String t = s == null ? "" : s.replaceAll("[^a-z0-9_-]", "");
        return t.length() > 24 ? t.substring(0, 24) : t;
    }
}
