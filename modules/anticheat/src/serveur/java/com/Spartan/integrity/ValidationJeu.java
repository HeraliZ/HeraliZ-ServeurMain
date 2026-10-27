package com.Spartan.integrity;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.alert.DiscordWebhook;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.scan.Finding;
import com.Spartan.scan.Finding.Severity;
import com.Spartan.scan.OfficialProfile;
import com.Spartan.scan.ScanManager;
import com.Spartan.scan.ScanReport;
import com.Spartan.serveur.GardienServeur;
import com.Spartan.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * « Jeu validé » à chaque connexion (27/10, demande du propriétaire : « pour les gens qui sont bons, un message en mode
 * jeu validé ; s'il n'y a pas ce message, c'est qu'il a peut-être réussi à désactiver ou contourner l'anticheat »).
 *
 * <p>Une fois le joueur vérifié (jeton du launcher + /login), le serveur attend les preuves d'un jeu officiel : jeton du
 * launcher accepté avec le dossier mods officiel (LauncherGate), Spartan de la même version que le serveur avec un défi
 * d'intégrité réussi et aucun faux (ChallengeManager), premier contrôle d'intégrité de la connexion terminé sans rien
 * de suspect et identique au jeu officiel (ScanManager, ConformiteStricte.ecarts). Tout est là : « ✅ Jeu validé » dans
 * le fil Discord du joueur et une ligne dans la console. Rien au bout de {@code [validation] timeoutSeconds} : « ⚠️
 * Validation jamais reçue » (fil du joueur, « Informations du staff », staff en jeu) avec ce qui manque. Un seul des deux
 * par connexion. Joueur déconnecté sans validation après au moins {@value #DECONNEXION_MIN_MS} ms vérifié : même alerte.
 * Joueur déjà refusé par le mode strict : rien de plus (l'alerte du refus dit tout). Bots de charge et faux joueurs :
 * ignorés. Tout se décide sur le serveur ; le jeu du joueur n'en sait rien.
 */
public final class ValidationJeu {
    private static final Map<UUID, Etat> ETATS = new ConcurrentHashMap<>();
    private static final int COULEUR_VALIDE = 0x2ECC71;
    private static final int COULEUR_MANQUE = 0xE67E22;
    /** Déconnecté sans validation : alerte seulement s'il est resté vérifié au moins ce temps (un jeu honnête : ~15 s). */
    private static final long DECONNEXION_MIN_MS = 30_000L;

    private ValidationJeu() {
    }

    private static final class Etat {
        final String nom;
        /** Heure de la vérification (jeton + /login), 0 avant ; le délai compte à partir d'elle. */
        long verifieA;
        int verifieTick;
        /** Message parti (validé ou jamais reçu), ou joueur refusé : plus rien pour cette connexion. */
        boolean fini;
        String resultat = "";
        long resultatA;
        // premier contrôle d'intégrité terminé de la connexion
        boolean controleRecu;
        boolean controleOk;
        String controle = "";
        String jeu = "";

        Etat(String nom) {
            this.nom = nom;
        }
    }

    private static boolean fictif(ServerPlayer p) {
        return p instanceof FakePlayer || p.getTags().contains("heraliz_bot");
    }

    public static void onLogin(ServerPlayer p) {
        if (fictif(p)) {
            return;
        }
        ETATS.put(p.getUUID(), new Etat(p.getName().getString()));
    }

    /**
     * Premier contrôle d'intégrité terminé de la connexion (ScanManager, fil du serveur). Seul le premier compte : un jeu
     * suspect au premier contrôle n'est pas validé (l'alerte du contrôle est déjà partie).
     */
    public static void controle(ServerPlayer p, Severity verdict, List<Finding> findings, List<String> ecarts, ScanReport r) {
        Etat e = ETATS.get(p.getUUID());
        if (e == null || e.fini || e.controleRecu) {
            return;
        }
        e.controleRecu = true;
        e.jeu = r.java + " · " + r.os;
        boolean propre = verdict == null || verdict == Severity.INFO;
        if (propre && ecarts.isEmpty()) {
            e.controleOk = true;
            e.controle = "rien de suspect · contrôle en " + r.durationMs + " ms";
            return;
        }
        List<String> raisons = new ArrayList<>();
        if (!propre) {
            long forts = findings.stream().filter(f -> f.severity() == Severity.STRONG).count();
            long suspects = findings.stream().filter(f -> f.severity() == Severity.SUSPECT).count();
            raisons.add("contrôle d'intégrité : " + (forts > 0 ? "triche probable" : "joueur suspect") + " (" + forts
                    + " indice(s) fort(s), " + suspects + " suspect(s) : alerte envoyée)");
        }
        if (!ecarts.isEmpty()) {
            raisons.add("jeu différent du jeu officiel : " + ecarts.get(0)
                    + (ecarts.size() > 1 ? " (+" + (ecarts.size() - 1) + " autre(s))" : ""));
        }
        e.controle = String.join(" ; ", raisons);
    }

    /** À chaque tick du joueur (fil du serveur) : presque rien tant qu'il n'y a rien à décider. */
    public static void tick(ServerPlayer p) {
        Etat e = ETATS.get(p.getUUID());
        if (e == null || e.fini || p.hasDisconnected()) {
            return;
        }
        if (e.verifieA == 0) {
            if (!PorteJoueurs.estVerifie(p.getUUID())) {
                return; // le délai ne court qu'après le /login (un joueur peut taper son mot de passe longtemps)
            }
            e.verifieA = Util.getMillis();
            e.verifieTick = p.server.getTickCount();
        }
        if ((p.server.getTickCount() + p.getId()) % 10 != 0) {
            return; // deux fois par seconde suffit
        }
        if (!SpartanConfig.VALIDATION_ENABLED.get()) {
            return;
        }
        if (ConformiteStricte.estRefuse(p)) {
            e.fini = true; // refusé par le mode strict : le staff a déjà la raison
            return;
        }
        List<String> manque = manque(p, e);
        if (manque.isEmpty()) {
            valide(p, e);
            return;
        }
        int delai = SpartanConfig.VALIDATION_TIMEOUT.get();
        // Compté en temps réel ET en ticks du serveur : un serveur figé ne fait accuser personne.
        if (Util.getMillis() - e.verifieA > delai * 1000L && p.server.getTickCount() - e.verifieTick > delai * 20) {
            jamaisRecue(p, e, manque, "pas validé " + delai + " s après son /login");
        }
    }

    /**
     * Déconnexion (appelée AVANT que les autres parties oublient le joueur). Un joueur resté assez longtemps sans être
     * validé laisse une alerte ; un jeu validé juste avant de partir (entre deux passages) a son message.
     */
    public static void deconnexion(ServerPlayer p) {
        Etat e = ETATS.remove(p.getUUID());
        if (e == null || e.fini || e.verifieA == 0 || fictif(p) || !SpartanConfig.VALIDATION_ENABLED.get()
                || ConformiteStricte.estRefuse(p)) {
            return;
        }
        long resteMs = Util.getMillis() - e.verifieA;
        List<String> manque = manque(p, e);
        if (manque.isEmpty()) {
            valide(p, e);
        } else if (resteMs >= DECONNEXION_MIN_MS) {
            jamaisRecue(p, e, manque, "déconnecté " + resteMs / 1000 + " s après son /login sans avoir été validé");
        }
    }

    /** Ce qui manque encore pour valider le jeu (vide : validé). */
    private static List<String> manque(ServerPlayer p, Etat e) {
        UUID id = p.getUUID();
        List<String> out = new ArrayList<>();
        if (!LauncherGate.conforme(id)) {
            out.add("launcher : " + LauncherGate.statut(id));
        }
        if (SpartanConfig.CHECK_CHALLENGE.get() || SpartanConfig.CHECK_BUILD.get()) {
            if (!ChallengeManager.buildRecu(id)) {
                out.add("Spartan : numéro de version jamais reçu du jeu");
            } else if (!ChallengeManager.isBuildValidated(id)) {
                out.add("Spartan : autre version que le serveur (défis impossibles)");
            } else if (ChallengeManager.echoues(id) > 0) {
                out.add("défi d'intégrité : réponse fausse (Spartan du jeu modifié)");
            } else if (SpartanConfig.CHECK_CHALLENGE.get() && ChallengeManager.reussis(id) == 0) {
                out.add("défi d'intégrité : aucune réponse juste reçue");
            }
        }
        if (SpartanConfig.SCAN_ENABLED.get() && !e.controleOk) {
            out.add(e.controleRecu ? e.controle : "contrôle d'intégrité : aucun rapport reçu du jeu");
        }
        return out;
    }

    private static void valide(ServerPlayer p, Etat e) {
        e.fini = true;
        long apres = Math.max(0, (Util.getMillis() - e.verifieA) / 1000);
        e.resultat = "jeu validé " + apres + " s après le /login";
        e.resultatA = Util.getMillis();
        UUID id = p.getUUID();
        String controle = SpartanConfig.SCAN_ENABLED.get() ? e.controle : "désactivé sur le serveur (scan.enabled = false)";
        String jeu = jeuOfficiel();
        String defi = SpartanConfig.CHECK_CHALLENGE.get()
                ? "même version que le serveur, défi d'intégrité réussi (" + ChallengeManager.reussis(id) + ")"
                : SpartanConfig.CHECK_BUILD.get() ? "même version que le serveur (défis désactivés)" : "non vérifié (défis et version désactivés)";

        String ligne = "[Spartan] Jeu validé — " + Text.oneLine(e.nom) + " : contrôle " + controle + ", launcher "
                + LauncherGate.statut(id) + ", " + jeu + ", Spartan " + defi + " (" + apres + " s après le /login)";
        Spartan.LOGGER.info(ligne);
        AlertManager.journal(ligne);

        List<String[]> champs = new ArrayList<>();
        champs.add(new String[] {"Joueur", Text.discord(e.nom) + " (" + id + ")"});
        champs.add(new String[] {"Contrôle d'intégrité", Text.discord(controle)});
        champs.add(new String[] {"Launcher", Text.discord(LauncherGate.statut(id))});
        champs.add(new String[] {"Jeu", Text.discord(jeu)});
        champs.add(new String[] {"Spartan", Text.discord(defi)});
        champs.add(new String[] {"Validé", apres + " s après le /login"});
        if (!e.jeu.isBlank()) {
            champs.add(new String[] {"Java / système", Text.discord(e.jeu)});
        }
        if (SpartanConfig.GARDIEN_ENABLED.get()) {
            champs.add(new String[] {"Gardien du launcher", Text.discord(GardienServeur.etat(id))});
        }
        DiscordWebhook.sendJoueur(id.toString(), Text.oneLine(e.nom), "✅ Jeu validé : " + e.nom, COULEUR_VALIDE,
                "Contrôles de la connexion réussis : jeu officiel, rien de suspect. Une connexion sans ce message = jeu"
                        + " jamais validé (voir « Validation jamais reçue »).", champs);
    }

    private static void jamaisRecue(ServerPlayer p, Etat e, List<String> manque, String quand) {
        e.fini = true;
        e.resultat = "validation jamais reçue (" + quand + ")";
        e.resultatA = Util.getMillis();
        UUID id = p.getUUID();
        CheckManager.recordSilent("Validation", id);
        String nom = Text.oneLine(e.nom);

        StringBuilder liste = new StringBuilder();
        for (String m : manque) {
            String l = "• " + Text.discord(m) + "\n";
            if (liste.length() + l.length() > 1000) {
                liste.append("…");
                break;
            }
            liste.append(l);
        }
        List<String> recu = new ArrayList<>();
        if (LauncherGate.conforme(id)) {
            recu.add("launcher : " + LauncherGate.statut(id));
        }
        if (ChallengeManager.isBuildValidated(id)) {
            recu.add("Spartan de la même version (" + ChallengeManager.reussis(id) + " défi(s) réussi(s))");
        }
        if (e.controleOk) {
            recu.add("contrôle d'intégrité : " + e.controle);
        }
        List<String[]> champs = new ArrayList<>();
        champs.add(new String[] {"Joueur", Text.discord(e.nom) + " (" + id + ")"});
        champs.add(new String[] {"Ce qui manque", liste.toString()});
        champs.add(new String[] {"Reçu", recu.isEmpty() ? "rien" : Text.discord(String.join(" ; ", recu))});
        if (!e.jeu.isBlank()) {
            champs.add(new String[] {"Java / système", Text.discord(e.jeu)});
        }
        String code = nom.replace('`', '\''); // dans un bloc de code, Discord n'interprète rien (sauf l'accent grave)
        DiscordWebhook.sendJoueur(id.toString(), nom, "⚠️ Validation jamais reçue : " + e.nom, COULEUR_MANQUE,
                "Jeu " + Text.discord(quand) + " : anticheat bloqué, désactivé ou contourné ? Aucune action automatique :"
                        + " surveillez-le en spectateur, `/spartan scan " + code + "` pour un contrôle immédiat,"
                        + " `/spartan status " + code + "` pour l'état.", champs);
        // fil « Informations du staff », staff en jeu, console et alerts.log (⚠ sans variante émoji : lisible en jeu)
        // (texte coupé à 200 caractères : la commande avant la liste)
        AlertManager.info(p.server, "⚠ Validation jamais reçue", nom + " : " + quand + " — /spartan scan " + nom
                + " — manque : " + String.join(" ; ", manque), COULEUR_MANQUE);
    }

    /** « identique au jeu officiel (40 jars, modpack du …) », ou rien à comparer. */
    private static String jeuOfficiel() {
        OfficialProfile off = ScanManager.official();
        if (off == null || off.isEmpty()) {
            return "non comparé au jeu officiel (config/spartan/officiel.json absent)";
        }
        return "identique au jeu officiel (" + off.mods.size() + " jars, modpack du " + off.genere + ")";
    }

    /** Ligne de /spartan status. */
    public static String describe(UUID uuid) {
        if (!SpartanConfig.VALIDATION_ENABLED.get()) {
            return "validation : désactivée";
        }
        Etat e = ETATS.get(uuid);
        if (e == null) {
            return "validation : rien (connexion antérieure ou faux joueur)";
        }
        if (e.fini && !e.resultat.isEmpty()) {
            return "validation : " + e.resultat + ", il y a " + (Util.getMillis() - e.resultatA) / 1000 + " s";
        }
        if (e.fini) {
            return "validation : aucune (connexion refusée par le mode strict)";
        }
        return e.verifieA == 0 ? "validation : en attente du /login" : "validation : en cours ("
                + (Util.getMillis() - e.verifieA) / 1000 + " s après le /login)";
    }
}
