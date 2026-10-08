package fr.heralisz.matchs.duels;

import com.google.gson.JsonElement;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Arenes;
import fr.heralisz.matchs.moteur.api.Carte;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.Groupe;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Defis de duel (MATCHS_SPEC §3) : {@code /duel <joueur>} ou l'ecran DUEL ; le defiant choisit le format (1v1, ou 2v2
 * seulement si les deux camps sont des groupes de 2), le mode et la carte ; le defie accepte dans les 30 s (toast + ecran).
 * A l'acceptation : verifications (en ligne, hors match, Coffre possible pour chacun), reservation d'une instance de la
 * carte, Coffre de chacun puis rattachement a la {@link DuelPartie}, qui demarre quand l'instance est prete.
 * Fil serveur.
 */
public final class Defis {
    private Defis() {
    }

    /** Defi en attente. */
    public record Defi(int id, UUID defiant, UUID defie, String format, String mode, String carte, long expireMs, List<UUID> campA,
                       List<UUID> campB) {
    }

    private static final Map<Integer, Defi> DEFIS = new LinkedHashMap<>();
    private static final Map<UUID, Long> DERNIER = new HashMap<>();
    private static int prochainId = 1;

    // ------------------------------------------------------------------ lecture
    /** Dernier defi recu par ce joueur (en attente), ou null. */
    @Nullable
    public static Defi entrant(UUID joueur) {
        Defi d = null;
        for (Defi x : DEFIS.values()) if (x.defie.equals(joueur)) d = x;
        return d;
    }

    /** Dernier defi lance par ce joueur (en attente), ou null. */
    @Nullable
    public static Defi sortant(UUID joueur) {
        Defi d = null;
        for (Defi x : DEFIS.values()) if (x.defiant.equals(joueur)) d = x;
        return d;
    }

    /** Vue d'un defi pour le client. */
    public static Vues.DefiVue vue(MinecraftServer s, Defi d) {
        Carte c = Arenes.carte(d.carte);
        return new Vues.DefiVue(d.id, nom(s, d.defiant), nom(s, d.defie), d.format, d.mode, c == null ? d.carte : c.nomFr(),
                d.expireMs - System.currentTimeMillis(), noms(s, d.campA), noms(s, d.campB));
    }

    private static String nom(MinecraftServer s, UUID u) {
        ServerPlayer p = s.getPlayerList().getPlayer(u);
        return p == null ? "?" : p.getGameProfile().getName();
    }

    private static List<String> noms(MinecraftServer s, List<UUID> l) {
        List<String> r = new ArrayList<>(l.size());
        for (UUID u : l) r.add(nom(s, u));
        return r;
    }

    // ------------------------------------------------------------------ cartes
    /** Cartes de duel chargees (type duel). */
    public static List<Carte> cartes() {
        return Arenes.cartesDeType(Carte.TYPE_DUEL);
    }

    /** Vrai si la carte declare ce mode (champ "modes", absent = tous). */
    public static boolean carteAccepte(Carte c, String cle, String valeur) {
        if (c.meta() == null || !c.meta().has(cle) || !c.meta().get(cle).isJsonArray()) return true;
        for (JsonElement e : c.meta().getAsJsonArray(cle)) if (e.getAsString().equalsIgnoreCase(valeur)) return true;
        return false;
    }

    /** Liste d'une cle de la carte ("modes", "formats"), ou la valeur par defaut. */
    public static List<String> liste(Carte c, String cle, List<String> defaut) {
        if (c.meta() == null || !c.meta().has(cle) || !c.meta().get(cle).isJsonArray()) return defaut;
        List<String> l = new ArrayList<>();
        for (JsonElement e : c.meta().getAsJsonArray(cle)) l.add(e.getAsString().toLowerCase(Locale.ROOT));
        return l;
    }

    // ------------------------------------------------------------------ defier
    /** Lance un defi (toutes les regles revalidees ici). */
    public static void defier(ServerPlayer p, UUID cibleId, String format, String mode, String carteId) {
        MinecraftServer s = p.server;
        ServerPlayer cible = s.getPlayerList().getPlayer(cibleId);
        if (cible == null) {
            erreur(p, "duel.erreur.hors_ligne");
            return;
        }
        if (cible == p) {
            erreur(p, "duel.erreur.soi");
            return;
        }
        if (MatchApi.enMatch(p)) {
            erreur(p, "duel.erreur.vous_en_match");
            return;
        }
        if (MatchApi.enMatch(cible)) {
            erreur(p, "duel.erreur.cible_en_match", cible.getGameProfile().getName());
            return;
        }
        long now = System.currentTimeMillis();
        Long dernier = DERNIER.get(p.getUUID());
        if (dernier != null && now - dernier < DuelsConfig.DEFI_CADENCE_MS) {
            erreur(p, "duel.erreur.cadence");
            return;
        }
        String carte = carteId == null || carteId.isEmpty() ? DuelsConfig.CARTE_DEFAUT : carteId;
        Carte c = Arenes.carte(carte);
        if (c == null || !Carte.TYPE_DUEL.equals(c.type())) {
            erreur(p, "duel.erreur.carte");
            return;
        }
        if (!Vues.MODE_RD.equals(mode) && !Vues.MODE_MAM.equals(mode) || !carteAccepte(c, "modes", mode)) {
            erreur(p, "duel.erreur.mode");
            return;
        }
        if (!Vues.FORMAT_1V1.equals(format) && !Vues.FORMAT_2V2.equals(format) || !carteAccepte(c, "formats", format)) {
            erreur(p, "duel.erreur.format");
            return;
        }
        List<UUID> campA = new ArrayList<>(), campB = new ArrayList<>();
        if (Vues.FORMAT_2V2.equals(format)) {
            Groupe ga = Groupes.de(p.getUUID()), gb = Groupes.de(cible.getUUID());
            if (ga == null || ga.taille() != 2 || gb == null || gb.taille() != 2) {
                erreur(p, "duel.erreur.groupes");
                return;
            }
            if (ga.contient(cible.getUUID())) {
                erreur(p, "duel.erreur.meme_groupe");
                return;
            }
            campA.addAll(ga.membres());
            campB.addAll(gb.membres());
            for (UUID u : campA) {
                if (u.equals(p.getUUID())) continue;
                ServerPlayer q = s.getPlayerList().getPlayer(u);
                if (q == null || MatchApi.enMatch(q)) {
                    erreur(p, "duel.erreur.coequipier");
                    return;
                }
            }
            // le coequipier du defiant passe en second
            campA.remove(p.getUUID());
            campA.add(0, p.getUUID());
            campB.remove(cible.getUUID());
            campB.add(0, cible.getUUID());
        } else {
            campA.add(p.getUUID());
            campB.add(cible.getUUID());
        }
        // un seul defi en cours de ce joueur vers cette cible
        DEFIS.values().removeIf(d -> d.defiant.equals(p.getUUID()) && d.defie.equals(cible.getUUID()));
        DERNIER.put(p.getUUID(), now);
        Defi d = new Defi(prochainId++, p.getUUID(), cible.getUUID(), format, mode, carte, now + DuelsConfig.DEFI_MS, List.copyOf(campA),
                List.copyOf(campB));
        DEFIS.put(d.id, d);
        Vues.DefiVue v = vue(s, d);
        MatchsNet.versJoueur(cible, new PaquetsDuels.DefiS2C(v, 0, ""));
        Annonces.chat(cible, Component.translatable("duel.chat.defi_recu", p.getGameProfile().getName(), format.toUpperCase(Locale.ROOT),
                Component.translatable("duel.mode." + mode), c.nomFr()));
        Annonces.chat(p, Component.translatable("duel.chat.defi_envoye", cible.getGameProfile().getName()));
        for (UUID u : campB) {
            if (u.equals(cible.getUUID())) continue;
            ServerPlayer q = s.getPlayerList().getPlayer(u);
            if (q != null) Annonces.chat(q, Component.translatable("duel.chat.defi_groupe", p.getGameProfile().getName(), cible.getGameProfile().getName()));
        }
        // l'ecran DUEL du defiant se met a jour (defi sortant)
        DuelsLogique.rafraichirEcran(p);
    }

    // ------------------------------------------------------------------ reponse
    /** Reponse au defi d'id donne : le defie accepte ou refuse, le defiant peut l'annuler (refuser). */
    public static void repondre(ServerPlayer p, int id, boolean accepte) {
        Defi d = DEFIS.get(id);
        if (d == null) {
            erreur(p, "duel.erreur.expire");
            MatchsNet.versJoueur(p, new PaquetsDuels.DefiS2C(null, id, ""));
            return;
        }
        MinecraftServer s = p.server;
        if (p.getUUID().equals(d.defiant) && !accepte) {
            DEFIS.remove(id);
            ServerPlayer c = s.getPlayerList().getPlayer(d.defie);
            if (c != null) MatchsNet.versJoueur(c, new PaquetsDuels.DefiS2C(null, id, "duel.defi.annule"));
            Annonces.chat(p, Component.translatable("duel.chat.defi_annule"));
            DuelsLogique.rafraichirEcran(p);
            return;
        }
        if (!p.getUUID().equals(d.defie)) return;
        DEFIS.remove(id);
        ServerPlayer defiant = s.getPlayerList().getPlayer(d.defiant);
        MatchsNet.versJoueur(p, new PaquetsDuels.DefiS2C(null, id, ""));
        if (!accepte) {
            if (defiant != null) {
                Annonces.chat(defiant, Component.translatable("duel.chat.defi_refuse", p.getGameProfile().getName()));
                MatchsNet.versJoueur(defiant, new PaquetsDuels.DefiS2C(null, id, "duel.defi.refuse"));
                DuelsLogique.rafraichirEcran(defiant);
            }
            return;
        }
        lancer(s, d);
    }

    /** Commande : repond au dernier defi recu. */
    public static void repondreDernier(ServerPlayer p, boolean accepte) {
        Defi d = entrant(p.getUUID());
        if (d == null) {
            erreur(p, "duel.erreur.aucun_defi");
            return;
        }
        repondre(p, d.id, accepte);
    }

    // ------------------------------------------------------------------ lancement
    private static void lancer(MinecraftServer s, Defi d) {
        List<ServerPlayer> campA = new ArrayList<>(), campB = new ArrayList<>();
        List<ServerPlayer> tous = new ArrayList<>();
        for (UUID u : d.campA) campA.add(s.getPlayerList().getPlayer(u));
        for (UUID u : d.campB) campB.add(s.getPlayerList().getPlayer(u));
        tous.addAll(campA);
        tous.addAll(campB);
        for (ServerPlayer p : tous) {
            if (p == null) {
                messageTous(tous, Component.translatable("duel.erreur.absent"));
                return;
            }
        }
        for (ServerPlayer p : tous) {
            if (MatchApi.enMatch(p)) {
                messageTous(tous, Component.translatable("duel.erreur.joueur_en_match", p.getGameProfile().getName()));
                return;
            }
            Coffre.Refus r = Coffre.refus(p);
            if (r != null) {
                messageTous(tous, Component.translatable("duel.erreur.refus", p.getGameProfile().getName(),
                        Component.translatable("matchs.coffre.refus." + r.name().toLowerCase(Locale.ROOT))));
                return;
            }
        }
        demarrer(s, d.mode, d.format, d.carte, campA, campB, false, tous);
    }

    /** Duel d'entrainement contre la cible immobile ({@code /duel entrainement}). */
    public static void entrainement(ServerPlayer p, String mode) {
        if (MatchApi.enMatch(p)) {
            erreur(p, "duel.erreur.vous_en_match");
            return;
        }
        Coffre.Refus r = Coffre.refus(p);
        if (r != null) {
            Annonces.chat(p, Component.translatable("matchs.coffre.refus." + r.name().toLowerCase(Locale.ROOT)));
            return;
        }
        List<ServerPlayer> moi = List.of(p);
        demarrer(p.server, mode, Vues.FORMAT_1V1, DuelsConfig.CARTE_DEFAUT, moi, List.of(), true, moi);
    }

    private static void demarrer(MinecraftServer s, String mode, String format, String carte, List<ServerPlayer> campA, List<ServerPlayer> campB,
                                 boolean entrainement, List<ServerPlayer> tous) {
        Instance inst = Arenes.reserver(carte);
        if (inst == null) {
            messageTous(tous, Component.translatable("duel.erreur.arene"));
            return;
        }
        String id = "duel-" + Integer.toHexString((int) (Math.random() * 0xFFFFFF)) + "-" + (System.currentTimeMillis() % 100000);
        DuelPartie partie = new DuelPartie(id, s, mode, format, carte, entrainement, campA, campB);
        MatchApi.enregistrer(partie);
        partie.instance(inst);
        List<ServerPlayer> deposes = new ArrayList<>();
        for (ServerPlayer p : tous) {
            if (!Coffre.deposer(p, "duel")) {
                HeraliszMatchs.LOG.warn("[duels] {} : depot refuse pour {}, duel annule", id, p.getGameProfile().getName());
                for (ServerPlayer q : deposes) {
                    Coffre.rendre(q);
                    MatchApi.quitter(q);
                }
                messageTous(tous, Component.translatable("duel.erreur.depot", p.getGameProfile().getName()));
                partie.fermer();
                return;
            }
            deposes.add(p);
            MatchApi.rejoindre(p, partie);
        }
        HeraliszMatchs.LOG.info("[duels] {} : {} {} sur {} ({} joueurs{})", id, mode, format, carte, tous.size(), entrainement ? ", entrainement" : "");
        Component titre = Component.literal(nomCamp(campA) + "  ·  " + (entrainement ? Component.translatable("duel.cible").getString() : nomCamp(campB)));
        Annonces.grande(tous, new Annonces.Annonce(Component.translatable("duel.annonce.duel"), titre, DuelsConfig.COULEUR_A, Registres.SON_MANCHE, 60,
                Annonces.Style.NORMALE));
        // fermeture des ecrans de defi cote client
        for (ServerPlayer p : tous) MatchsNet.versJoueur(p, new PaquetsDuels.DefiS2C(null, -1, ""));
        inst.quandPrete(partie::demarrer);
    }

    private static String nomCamp(List<ServerPlayer> l) {
        StringBuilder sb = new StringBuilder();
        for (ServerPlayer p : l) {
            if (sb.length() > 0) sb.append(" & ");
            sb.append(p.getGameProfile().getName());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ entretien
    /** Expiration des defis (appele toutes les 20 ticks). */
    public static void tick(MinecraftServer s) {
        long now = System.currentTimeMillis();
        Iterator<Defi> it = DEFIS.values().iterator();
        while (it.hasNext()) {
            Defi d = it.next();
            if (now < d.expireMs) continue;
            it.remove();
            ServerPlayer a = s.getPlayerList().getPlayer(d.defiant), b = s.getPlayerList().getPlayer(d.defie);
            if (a != null) {
                Annonces.chat(a, Component.translatable("duel.chat.defi_expire", b == null ? "?" : b.getGameProfile().getName()));
                MatchsNet.versJoueur(a, new PaquetsDuels.DefiS2C(null, d.id, ""));
            }
            if (b != null) MatchsNet.versJoueur(b, new PaquetsDuels.DefiS2C(null, d.id, "duel.defi.expire"));
        }
        if (DERNIER.size() > 256) DERNIER.values().removeIf(t -> now - t > DuelsConfig.DEFI_CADENCE_MS);
    }

    /** Deconnexion : les defis du joueur (lances ou recus) tombent. */
    public static void oublier(MinecraftServer s, UUID joueur) {
        Iterator<Defi> it = DEFIS.values().iterator();
        while (it.hasNext()) {
            Defi d = it.next();
            if (!d.defiant.equals(joueur) && !d.defie.equals(joueur) && !d.campA.contains(joueur) && !d.campB.contains(joueur)) continue;
            it.remove();
            for (UUID u : new UUID[]{d.defiant, d.defie}) {
                if (u.equals(joueur)) continue;
                ServerPlayer p = s.getPlayerList().getPlayer(u);
                if (p != null) MatchsNet.versJoueur(p, new PaquetsDuels.DefiS2C(null, d.id, "duel.defi.annule"));
            }
        }
        DERNIER.remove(joueur);
    }

    public static void vider() {
        DEFIS.clear();
        DERNIER.clear();
    }

    private static void erreur(ServerPlayer p, String cle, Object... args) {
        Annonces.chat(p, Component.translatable(cle, args).withStyle(st -> st.withColor(0xFF6B6B)));
    }

    private static void messageTous(List<ServerPlayer> l, Component c) {
        for (ServerPlayer p : l) if (p != null) Annonces.chat(p, c.copy().withStyle(st -> st.withColor(0xFF6B6B)));
    }
}
