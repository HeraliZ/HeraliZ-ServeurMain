package fr.heralisz.bases.pillage;

import fr.heralisz.bases.BasesConfig;
import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.EntreeJour;
import fr.heralisz.bases.model.Pillage;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.bases.registry.Sons;
import fr.heralisz.bases.server.BasesStore;
import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pillages en cours (BASES_SPEC §4), fil serveur :
 * <ul>
 *   <li>{@link Session} : un pillard dans une base (gardee pendant une deconnexion, jusqu'a la fin de la fenetre) ;</li>
 *   <li>{@link Raid} : le pillage d'une base par un clan attaquant (ligne du journal {@link Pillage} de la base),
 *   ouvert a la premiere entree (alerte aux defenseurs : toast + son + chat), clos quand son dernier pillard sort ou
 *   meurt : « pillee » si au moins un pillard est sorti vivant (ou renvoye a la fin de la fenetre), « defendue » si tous
 *   sont morts (journal des deux clans).</li>
 * </ul>
 * Regles d'entree ({@link #refus}) : fenetre ouverte, un clan, jamais sa propre base, une entree de pillage par joueur et
 * par jour dans une seule base (retour dans la meme base apres une sortie vivante si {@code retour_apres_sortie}), la
 * mort = plus de retour ce jour-la, {@code pillards_max} pillards par base. Les sessions ne survivent pas a un
 * redemarrage (les pillages restes « en cours » sont clos au demarrage).
 */
public final class Pillages {
    private Pillages() {
    }

    /** Un pillard dans une base. */
    public static final class Session {
        public final UUID joueur;
        public final String nom;
        /** Clan proprietaire de la base pillee. */
        public final UUID base;
        public final UUID attaquant;
        public final String tag;
        public final long debut;

        Session(UUID joueur, String nom, UUID base, UUID attaquant, String tag, long debut) {
            this.joueur = joueur;
            this.nom = nom;
            this.base = base;
            this.attaquant = attaquant;
            this.tag = tag;
            this.debut = debut;
        }
    }

    /** Pillage d'une base par un clan : ligne du journal + bilan. */
    public static final class Raid {
        public final UUID base;
        public final UUID attaquant;
        public final String tag;
        public final Pillage journal;
        public final Set<UUID> entres = new LinkedHashSet<>();
        public int vivants;
        public int morts;

        Raid(UUID base, UUID attaquant, String tag, Pillage journal) {
            this.base = base;
            this.attaquant = attaquant;
            this.tag = tag;
            this.journal = journal;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
    private static final Map<String, Raid> RAIDS = new HashMap<>();

    private static String cleRaid(UUID base, UUID attaquant) {
        return base + "|" + attaquant;
    }

    // ------------------------------------------------------------------ lecture
    @Nullable
    public static Session session(UUID joueur) {
        return SESSIONS.get(joueur);
    }

    public static Collection<Session> sessions() {
        return SESSIONS.values();
    }

    public static Collection<Raid> raids() {
        return RAIDS.values();
    }

    /** Pillards (sessions, deconnectes compris) dans la base d'un clan. */
    public static int pillards(UUID base) {
        int n = 0;
        for (Session s : SESSIONS.values()) if (s.base.equals(base)) n++;
        return n;
    }

    /** Tags des clans qui pillent la base d'un clan. */
    public static List<String> attaquants(UUID base) {
        List<String> l = new ArrayList<>();
        for (Raid r : RAIDS.values()) if (r.base.equals(base) && !l.contains(r.tag)) l.add(r.tag);
        return l;
    }

    // ------------------------------------------------------------------ regles d'entree
    /**
     * Raison du refus d'un pillage de {@code b} par {@code p} (cle de traduction gui.heralisz_bases.raison.*), null si
     * permis. Ne regarde ni le tag de combat ni l'etat a terre (verifies par le coeur au depart et a l'echeance).
     */
    @Nullable
    public static String refus(ServerPlayer p, Base b) {
        if (!BasesApi.fenetreOuverte()) return "gui.heralisz_bases.raison.fenetre";
        Optional<UUID> mon = ClansApi.clanDe(p.getUUID());
        if (mon.isEmpty()) return "gui.heralisz_bases.raison.sans_clan";
        if (b.clan.equals(mon.get())) return "gui.heralisz_bases.raison.propre";
        if (!b.prete()) return "gui.heralisz_bases.raison.indisponible";
        EntreeJour e = BasesApi.entreeJour(p.getUUID());
        if (e != null && e.jour().equals(BasesApi.jour())) {
            if (e.mort()) return "gui.heralisz_bases.raison.mort";
            if (!b.clan.equals(e.base()) || !PillageConfig.RETOUR_APRES_SORTIE.get()) return "gui.heralisz_bases.raison.entree_utilisee";
        }
        Session s = SESSIONS.get(p.getUUID());
        int n = pillards(b.clan) - (s != null && s.base.equals(b.clan) ? 1 : 0);
        if (n >= BasesConfig.PILLARDS_MAX.get()) return "gui.heralisz_bases.raison.pleine";
        return null;
    }

    /** Vrai si le joueur a deja utilise son entree du jour sur cette base. */
    public static boolean dejaPillee(ServerPlayer p, UUID base) {
        EntreeJour e = BasesApi.entreeJour(p.getUUID());
        return e != null && e.jour().equals(BasesApi.jour()) && base.equals(e.base());
    }

    // ------------------------------------------------------------------ lancement
    /** {@code /base piller <tag>} : verifications puis entree differee (10 s immobile) par un sas au hasard. */
    public static void piller(ServerPlayer p, String tag) {
        Optional<ClanInfo> cible = ClansApi.parTag(tag);
        Base b = cible.map(c -> BasesApi.base(c.id())).orElse(null);
        if (cible.isEmpty()) {
            BasesApi.erreur(p, "msg.heralisz_bases.pillage.clan_inconnu", tag);
            return;
        }
        if (b == null) {
            BasesApi.erreur(p, "msg.heralisz_bases.pillage.sans_base", cible.get().tag());
            return;
        }
        if (BasesApi.dansBunkers(p.level())) {
            BasesApi.erreur(p, "msg.heralisz_bases.pillage.sortez");
            return;
        }
        String r = refus(p, b);
        if (r != null) {
            erreurRefus(p, r, b);
            return;
        }
        String entree = entreeAuHasard();
        UUID attaquant = ClansApi.clanDe(p.getUUID()).orElseThrow();
        boolean ok = BasesApi.programmerEntree(p, b, entree, Packets.TeleportS2C.Genre.PILLAGE, j -> {
            // a l'echeance : memes regles (la fenetre a pu fermer, la base se remplir, le clan changer)
            String r2 = refus(j, b);
            if (r2 != null) {
                erreurRefus(j, r2, b);
                return false;
            }
            return ClansApi.clanDe(j.getUUID()).map(attaquant::equals).orElse(false);
        }, j -> arrivee(j, b, attaquant));
        if (ok) {
            HeraliszBases.LOG.info("[bases] {} part piller [{}] (entree {})", p.getGameProfile().getName(), cible.get().tag(), entree);
        }
    }

    private static String entreeAuHasard() {
        Plan plan = BasesApi.plan();
        if (plan == null || plan.entrees.isEmpty()) return null;
        List<String> ids = new ArrayList<>(plan.entrees.keySet());
        return ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
    }

    /** Message d'erreur d'un refus (cle de raison -> message de chat). */
    static void erreurRefus(ServerPlayer p, String raison, Base b) {
        String tag = tagDe(b);
        switch (raison) {
            case "gui.heralisz_bases.raison.fenetre" ->
                    BasesApi.erreur(p, "msg.heralisz_bases.pillage.fenetre", BasesConfig.FENETRE_DEBUT.get(), BasesConfig.FENETRE_FIN.get());
            case "gui.heralisz_bases.raison.sans_clan" -> BasesApi.erreur(p, "msg.heralisz_bases.pillage.sans_clan");
            case "gui.heralisz_bases.raison.propre" -> BasesApi.erreur(p, "msg.heralisz_bases.pillage.propre");
            case "gui.heralisz_bases.raison.indisponible" -> BasesApi.erreur(p, "msg.heralisz_bases.pillage.indisponible", tag);
            case "gui.heralisz_bases.raison.mort" -> BasesApi.erreur(p, "msg.heralisz_bases.pillage.mort");
            case "gui.heralisz_bases.raison.entree_utilisee" -> {
                EntreeJour e = BasesApi.entreeJour(p.getUUID());
                Base autre = e == null || e.base() == null ? null : BasesApi.base(e.base());
                BasesApi.erreur(p, "msg.heralisz_bases.pillage.entree_utilisee", autre == null ? "?" : tagDe(autre));
            }
            case "gui.heralisz_bases.raison.pleine" ->
                    BasesApi.erreur(p, "msg.heralisz_bases.pillage.pleine", tag, BasesConfig.PILLARDS_MAX.get());
            default -> BasesApi.erreur(p, raison);
        }
    }

    static String tagDe(Base b) {
        return ClansApi.infos(b.clan).map(ClanInfo::tag).orElse(b.tag);
    }

    /** Juste apres la teleportation dans le sas (avant le controle de presence suivant) : session, entree, raid. */
    static void arrivee(ServerPlayer p, Base b, UUID attaquant) {
        long now = System.currentTimeMillis();
        String tagAtt = ClansApi.infos(attaquant).map(ClanInfo::tag).orElse("?");
        SESSIONS.put(p.getUUID(), new Session(p.getUUID(), p.getGameProfile().getName(), b.clan, attaquant, tagAtt, now));
        EntreeJour e = BasesApi.entreeJour(p.getUUID());
        if (e == null || !e.jour().equals(BasesApi.jour())) {
            BasesApi.entreeJour(new EntreeJour(p.getUUID(), BasesApi.jour(), b.clan, false, now));
        }
        String cle = cleRaid(b.clan, attaquant);
        Raid r = RAIDS.get(cle);
        if (r == null) {
            Pillage journal = new Pillage(now, attaquant, tagAtt);
            b.ajouterPillage(journal);
            r = new Raid(b.clan, attaquant, tagAtt, journal);
            RAIDS.put(cle, r);
            r.entres.add(p.getUUID());
            journal.joueurs = r.entres.size();
            // alerte des defenseurs a la premiere entree : toast + son + chat
            BasesApi.aClan(b.clan, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.ATTAQUEE, "notice.heralisz_bases.attaquee",
                            List.of(tagAtt, String.valueOf(r.entres.size())), Sons.ALERTE.getId().getPath()),
                    Component.translatable("msg.heralisz_bases.pillage.alerte", tagAtt).withStyle(s -> s.withColor(0xFF6B6B)));
            HeraliszBases.LOG.info("[bases] pillage de [{}] par [{}] : premiere entree de {}", tagDe(b), tagAtt, p.getGameProfile().getName());
        } else {
            r.entres.add(p.getUUID());
            r.journal.joueurs = r.entres.size();
        }
        BasesApi.modifiee(b);
        // fenetre forcee par un admin : pas d'heure de fin a annoncer
        if (BasesApi.finFenetreMs() > 0) BasesApi.info(p, "msg.heralisz_bases.pillage.arrivee", tagDe(b), BasesConfig.FENETRE_FIN.get() + " h");
        else BasesApi.info(p, "msg.heralisz_bases.pillage.arrivee_libre", tagDe(b));
        BasesApi.synchroniser(b.clan);
    }

    // ------------------------------------------------------------------ fin d'une session
    /**
     * Fin de la session d'un joueur (sortie, ejection, renvoi a la fin de la fenetre, mort, deconnexion trop longue) ;
     * clot le raid si c'etait son dernier pillard.
     */
    static void finir(UUID joueur, boolean mort) {
        Session s = SESSIONS.remove(joueur);
        if (s == null) return;
        String cle = cleRaid(s.base, s.attaquant);
        Raid r = RAIDS.get(cle);
        if (r != null) {
            if (mort) r.morts++;
            else r.vivants++;
            boolean reste = false;
            for (Session o : SESSIONS.values()) {
                if (o.base.equals(s.base) && o.attaquant.equals(s.attaquant)) {
                    reste = true;
                    break;
                }
            }
            if (!reste) clore(cle, r);
        }
        Base b = BasesApi.base(s.base);
        if (b != null) BasesApi.synchroniser(b.clan);
    }

    private static void clore(String cle, Raid r) {
        RAIDS.remove(cle);
        r.journal.fin = System.currentTimeMillis();
        boolean pillee = r.vivants > 0;
        r.journal.issue = pillee ? Pillage.Issue.PILLEE : Pillage.Issue.DEFENDUE;
        r.journal.joueurs = r.entres.size();
        Base b = BasesApi.base(r.base);
        String tagBase = b == null ? "?" : tagDe(b);
        String n = String.valueOf(r.entres.size());
        if (b != null) {
            BasesApi.modifiee(b);
            ClansApi.journal(r.base, pillee ? "evenement.heralisz_bases.pillee" : "evenement.heralisz_bases.defendue", r.tag, n);
            BasesApi.aClan(r.base, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.PILLAGE_TERMINE,
                            pillee ? "notice.heralisz_bases.pillage_termine" : "notice.heralisz_bases.defendue", List.of(r.tag, n),
                            pillee ? Sons.REFUS.getId().getPath() : Sons.SUCCES.getId().getPath()),
                    Component.translatable(pillee ? "msg.heralisz_bases.pillage.termine_pillee" : "msg.heralisz_bases.pillage.termine_defendue", r.tag, n));
        }
        ClansApi.journal(r.attaquant, pillee ? "evenement.heralisz_bases.pillage_mene" : "evenement.heralisz_bases.pillage_repousse", tagBase, n);
        HeraliszBases.LOG.info("[bases] pillage de [{}] par [{}] termine : {} ({} joueur(s), {} mort(s))", tagBase, r.tag, r.journal.issue,
                r.entres.size(), r.morts);
    }

    // ------------------------------------------------------------------ evenements
    /** Mort d'un pillard : pas de retour ce jour-la, session close (le corps et le stuff restent dans la base). */
    static void mort(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        EntreeJour e = BasesApi.entreeJour(p.getUUID());
        if (e != null && e.jour().equals(BasesApi.jour())) BasesApi.entreeJour(e.avecMort());
        else BasesApi.entreeJour(new EntreeJour(p.getUUID(), BasesApi.jour(), s.base, true, System.currentTimeMillis()));
        BasesStore st = BasesStore.get();
        if (st != null) st.retour(p.getUUID(), null);
        BasesApi.info(p, "msg.heralisz_bases.pillage.mort_pillard");
        finir(p.getUUID(), true);
    }

    /** Sortie ou ejection (crochet du coeur) : session close, pillard vivant. */
    static void sortie(ServerPlayer p) {
        if (SESSIONS.containsKey(p.getUUID())) {
            finir(p.getUUID(), false);
            BasesNet.toPlayer(p, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.PILLAGE_TERMINE, "notice.heralisz_bases.pillage_termine_pillard",
                    List.of(), ""));
        }
    }

    /** Fin de la fenetre : pillards en ligne renvoyes a leur position d'origine, sessions hors ligne closes. */
    static void finDeFenetre(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.joueur);
            if (p != null && BasesApi.dansBunkers(p.level())) {
                // ramener -> crochet sortie -> finir(vivant)
                BasesApi.ejecter(p, "msg.heralisz_bases.pillage.fin");
            }
            finir(s.joueur, false);
        }
        HeraliszBases.LOG.info("[bases] fin de la fenetre de pillage : pillards renvoyes");
    }

    /** Avertissement avant la fin de la fenetre (toast + chat) aux pillards en ligne. */
    static void avertir(MinecraftServer server, int minutes, @Nullable Collection<UUID> seulement) {
        for (Session s : SESSIONS.values()) {
            if (seulement != null && !seulement.contains(s.joueur)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(s.joueur);
            if (p == null) continue;
            BasesNet.toPlayer(p, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.PILLAGE_FIN_PROCHE, "notice.heralisz_bases.pillage_fin_proche",
                    List.of(String.valueOf(minutes)), Sons.ALERTE.getId().getPath()));
            p.sendSystemMessage(Component.translatable("msg.heralisz_bases.pillage.fin_proche", minutes).withStyle(st -> st.withColor(0xE8B34A)));
        }
    }

    /** Un pillard reconnecte hors du monde des bases (serveur qui l'a deplace) : sa session est close. */
    static void reconnexion(ServerPlayer p) {
        if (SESSIONS.containsKey(p.getUUID()) && !BasesApi.dansBunkers(p.level())) finir(p.getUUID(), false);
    }

    /** Demarrage : les pillages restes « en cours » (arret pendant un pillage) sont clos. */
    static void cloreOrphelins() {
        int n = 0;
        for (Base b : BasesApi.bases()) {
            boolean modif = false;
            for (Pillage pl : b.pillages) {
                if (pl.issue == Pillage.Issue.EN_COURS) {
                    pl.issue = Pillage.Issue.PILLEE;
                    if (pl.fin <= 0) pl.fin = pl.debut;
                    modif = true;
                    n++;
                }
            }
            if (modif) BasesApi.modifiee(b);
        }
        if (n > 0) HeraliszBases.LOG.info("[bases] {} pillage(s) interrompu(s) par un arret clos", n);
    }

    static void vider() {
        SESSIONS.clear();
        RAIDS.clear();
    }

    /** Joueurs d'une session (admin) : « nom[TAG] », suivi de « (hors ligne) » traduit chez le joueur. */
    static List<Component> noms(UUID base) {
        List<Component> l = new ArrayList<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        for (Session s : SESSIONS.values()) {
            if (!s.base.equals(base)) continue;
            boolean enLigne = server != null && server.getPlayerList().getPlayer(s.joueur) != null;
            String nom = s.nom + "[" + s.tag + "]";
            l.add(enLigne ? Component.literal(nom)
                    : Component.translatableWithFallback("heralisz_bases.cmd.pillard_hors_ligne", "%s (hors ligne)", nom));
        }
        return l;
    }
}
