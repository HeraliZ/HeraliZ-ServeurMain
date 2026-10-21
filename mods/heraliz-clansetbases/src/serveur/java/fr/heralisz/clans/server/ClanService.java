package fr.heralisz.clans.server;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.api.events.ClanDissousEvent;
import fr.heralisz.clans.api.events.MembreQuitteEvent;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.ClanLigne;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Invitation;
import fr.heralisz.clans.model.LienJoueur;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.model.Mouvement;
import fr.heralisz.clans.model.Niveaux;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.territory.Territoires;
import fr.heralisz.economie.api.EconomieApi;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Regles de jeu des clans (§1.1 a 1.6 du cahier des charges), toutes verifiees ici, sur le fil serveur. Les handlers
 * reseau et les commandes ne font que traduire leurs arguments et appeler ces methodes ; chaque methode renvoie vrai
 * si l'action a eu lieu et a deja envoye ses messages (erreurs traduites via {@link ServerHandlers#erreur}).
 *
 * <p>Conventions : les montants sont en jetons (HeraliZ Économie) ; les motifs passes a l'economie sont prefixes
 * « clan: » ; un changement d'etat marque le clan a synchroniser ({@link Sync#marquer}) et le store a sauvegarder ;
 * toute operation d'argent sauvegarde immediatement les deux cotes (banque de clan et comptes de l'economie), le cote
 * debite d'abord : une mort brutale du serveur entre les deux ne peut que faire perdre quelques jetons, jamais en creer.
 */
public final class ClanService {
    private ClanService() {
    }

    /** Plafond de la banque d'un clan (au-dela, les gains sont tronques et journalises). */
    public static final long BANQUE_MAX = 1_000_000_000_000L;
    /** Invitations en attente au plus par clan. */
    public static final int INVITATIONS_MAX = 50;
    /** Anti-spam : invitations qu'un joueur peut emettre par fenetre de 5 min. */
    public static final int INVITATIONS_PAR_FENETRE = 10;
    public static final long INVITATIONS_FENETRE_MS = 5 * 60_000L;
    /** Apres un refus explicite, le clan ne peut pas reinviter ce joueur pendant 10 min. */
    public static final long REFUS_COOLDOWN_MS = 10 * 60_000L;
    /** Longueur maximale d'un message de chat de clan, et cadence : un message par seconde et par joueur. */
    public static final int CHAT_MAX = 256;
    public static final long CHAT_CADENCE_MS = 1000;

    /** Emetteur -> {debut de la fenetre, invitations emises dans la fenetre}. */
    private static final Map<UUID, long[]> INVITATIONS_EMISES = new HashMap<>();
    /** Clan -> (joueur qui a refuse -> ms jusqu'auxquelles le clan ne peut pas le reinviter). */
    private static final Map<UUID, Map<UUID, Long>> REFUS = new HashMap<>();
    /** Joueur -> ms de son dernier message de clan. */
    private static final Map<UUID, Long> DERNIER_CHAT = new HashMap<>();

    /** A l'arret du serveur. */
    static void vider() {
        INVITATIONS_EMISES.clear();
        REFUS.clear();
        DERNIER_CHAT.clear();
    }

    /** A la deconnexion d'un joueur. */
    static void oublier(UUID joueur) {
        DERNIER_CHAT.remove(joueur);
    }

    /** Toutes les 20 s : retire les fenetres d'invitation et les refus perimes. */
    static void purger(long now) {
        INVITATIONS_EMISES.values().removeIf(q -> now - q[0] >= INVITATIONS_FENETRE_MS);
        for (var it = REFUS.values().iterator(); it.hasNext(); ) {
            Map<UUID, Long> m = it.next();
            m.values().removeIf(t -> t <= now);
            if (m.isEmpty()) it.remove();
        }
    }

    // ------------------------------------------------------------------ aides
    @Nullable
    private static ClanStore store() {
        return ClanStore.get();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static void erreur(ServerPlayer p, String cle, Object... args) {
        ServerHandlers.erreur(p, cle, args);
    }

    private static void info(ServerPlayer p, String cle, Object... args) {
        ServerHandlers.info(p, cle, args);
    }

    /** Le clan du joueur, ou null avec le message « pas de clan ». */
    @Nullable
    private static Clan monClan(ServerPlayer p) {
        ClanStore s = store();
        Clan c = s == null ? null : s.clanDe(p.getUUID());
        if (c == null) erreur(p, "msg.pas_de_clan");
        return c;
    }

    /** Vrai si le joueur a la permission, sinon message et faux. */
    private static boolean exiger(ServerPlayer p, Clan c, Perm perm) {
        if (c.peut(p.getUUID(), perm)) return true;
        erreur(p, "msg.permission");
        return false;
    }

    private static boolean exigerChef(ServerPlayer p, Clan c) {
        if (c.estChef(p.getUUID())) return true;
        erreur(p, "msg.chef_seulement");
        return false;
    }

    public static long plafonnerBanque(long v) {
        return v < 0 ? 0 : Math.min(BANQUE_MAX, v);
    }

    /** « 2 j 3 h », « 1 h 03 », « 12 min », « 45 s » (traduit chez le joueur ; argument d'un message). */
    public static MutableComponent duree(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s >= 86_400) {
            return Component.translatableWithFallback("heralisz_clans.duree.jours", "%s j %s h", String.valueOf(s / 86_400),
                    String.valueOf((s % 86_400) / 3600));
        }
        if (s >= 3600) {
            return Component.translatableWithFallback("heralisz_clans.duree.heures", "%s h %s", String.valueOf(s / 3600),
                    String.format(Locale.ROOT, "%02d", (s % 3600) / 60));
        }
        if (s >= 60) return Component.translatableWithFallback("heralisz_clans.duree.minutes", "%s min", String.valueOf(s / 60));
        return Component.translatableWithFallback("heralisz_clans.duree.secondes", "%s s", String.valueOf(s));
    }

    /**
     * Grade ecrit dans le journal d'evenements (les arguments sont des chaines) : le nom de l'enum, traduit par le
     * client ({@link Grade#duJournal}, qui lit aussi l'ancien libelle francais des journaux deja enregistres).
     */
    public static String libelleGrade(Grade g) {
        return g.name();
    }

    /** Nom affiche d'un joueur : en ligne, sinon dernier nom connu dans le clan ou le lien. */
    public static String nomDe(UUID u) {
        MinecraftServer server = Sync.serveur();
        if (server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) return p.getGameProfile().getName();
        }
        ClanStore s = store();
        if (s != null) {
            Clan c = s.clanDe(u);
            Membre m = c == null ? null : c.membre(u);
            if (m != null && !m.dernierNom.isEmpty() && !"?".equals(m.dernierNom)) return m.dernierNom;
            LienJoueur l = s.lien(u);
            if (!l.dernierNom.isEmpty()) return l.dernierNom;
        }
        return u.toString().substring(0, 8);
    }

    /** Nom d'un membre de ce clan : en ligne, sinon son dernier nom connu dans le clan, sinon {@link #nomDe}. */
    public static String nomDans(Clan c, UUID u) {
        MinecraftServer server = Sync.serveur();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(u);
        if (p != null) return p.getGameProfile().getName();
        Membre m = c == null ? null : c.membre(u);
        return m != null && !m.dernierNom.isEmpty() && !"?".equals(m.dernierNom) ? m.dernierNom : nomDe(u);
    }

    /** Membres du clan connectes a ce serveur. */
    public static List<ServerPlayer> membresEnLigne(Clan c) {
        List<ServerPlayer> l = new ArrayList<>();
        MinecraftServer server = Sync.serveur();
        if (server == null || c == null) return l;
        for (UUID u : c.membres.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) l.add(p);
        }
        return l;
    }

    /** Notice (toast + son) a tous les membres en ligne. */
    public static void diffuser(Clan c, Packets.NoticeS2C notice) {
        for (ServerPlayer p : membresEnLigne(c)) ClansNet.toPlayer(p, notice);
    }

    /** Message de chat a tous les membres en ligne. */
    public static void diffuserChat(Clan c, Component message) {
        for (ServerPlayer p : membresEnLigne(c)) p.sendSystemMessage(message);
    }

    /** Message de chat (cle traduite) aux membres en ligne, dans la couleur du clan. */
    public static void diffuserChat(Clan c, String cle, Object... args) {
        diffuserChat(c, prefixe(c).append(Component.translatable(cle, args)));
    }

    /** « [TAG] » dans la couleur du clan, suivi d'un espace. */
    public static MutableComponent prefixe(Clan c) {
        // tag gris avant le niveau 8, couleur du clan ensuite, or au niveau 20 (avantages des niveaux)
        int couleur = c.niveau >= fr.heralisz.clans.model.Avantages.NIVEAU_LEGENDAIRE ? 0xFFC23A
                : c.niveau >= fr.heralisz.clans.model.Avantages.NIVEAU_TAG ? c.banniere.accent() : 0xAAAAAA;
        return Component.literal("[" + c.tag + "] ").withStyle(s -> s.withColor(couleur));
    }

    private static void changement(Clan c) {
        ClanStore s = store();
        if (s != null) s.marquerDirty();
        Sync.marquer(c);
    }

    /** Ecrit tout de suite les comptes de l'economie (apres un debit ou un credit fait par les clans). */
    private static void persisterEconomie() {
        if (EconomieApi.disponible()) EconomieApi.sauvegarderMaintenant();
    }

    /** Place restante sur le compte d'un joueur avant son plafond. */
    private static long place(Economie eco, UUID u) {
        return Math.max(0, EconomieApi.maxSolde() - eco.solde(u));
    }

    // ------------------------------------------------------------------ creation
    /** Fonde un clan : validation, unicite, delai de rejoindre, cout debite au fondateur (CHEF). */
    public static boolean creer(ServerPlayer p, String tag, String nom, String description, Banniere banniere) {
        ClanStore s = store();
        if (s == null) return false;
        UUID u = p.getUUID();
        if (s.clanDe(u) != null) {
            erreur(p, "msg.deja_clan");
            return false;
        }
        long reste = delaiRejoindre(s, u);
        if (reste > 0) {
            erreur(p, "msg.delai_rejoindre", duree(reste));
            return false;
        }
        String err = Clan.validerTag(tag);
        if (err == null) err = Clan.validerNom(nom);
        if (err == null) err = Clan.validerDescription(description);
        if (err != null) {
            erreur(p, err);
            return false;
        }
        String tagP = Clan.nettoyer(tag).toUpperCase(Locale.ROOT), nomP = Clan.nettoyer(nom), descP = Clan.nettoyerDescription(description);
        if (!s.tagLibre(tagP)) {
            erreur(p, "msg.tag_pris");
            return false;
        }
        if (!s.nomLibre(nomP)) {
            erreur(p, "msg.nom_pris");
            return false;
        }
        Economie eco = Economie.active();
        long cout = ClansConfig.COUT_CREATION.get();
        if (cout > 0) {
            if (!eco.disponible()) {
                erreur(p, "msg.economie_indisponible");
                return false;
            }
            if (!eco.retirer(u, cout, "clan:creation")) {
                erreur(p, "msg.argent_insuffisant_cout", eco.format(cout), eco.format(eco.solde(u)));
                return false;
            }
        }
        long now = now();
        Clan c = new Clan(UUID.randomUUID(), tagP, nomP, u, now);
        c.description = descP;
        c.banniere = banniere == null ? Banniere.DEFAUT : banniere.valider();
        c.membres.put(u, new Membre(u, p.getGameProfile().getName(), Grade.CHEF, now, now, null));
        c.ajouterEvenement(new Evenement(now, Evenement.Genre.CREATION));
        if (cout > 0) c.ajouterMouvement(new Mouvement(now, u, p.getGameProfile().getName(), -cout, "creation"));
        Quetes.initialiser(c, now);
        // les invitations recues ailleurs ne servent plus
        for (Clan autre : s.tous()) autre.retirerInvitation(u);
        s.ajouter(c);
        LienJoueur l = s.lien(u);
        l.chatClan = false;
        l.dernierNom = p.getGameProfile().getName();
        if (cout > 0) persisterEconomie(); // le debit d'abord, puis le clan
        s.sauvegarderMaintenant();
        HeraliszClans.LOG.info("[clans] clan [{}] {} fonde par {} ({}) ; banniere {}", c.tag, c.nom, p.getGameProfile().getName(), c.id,
                c.banniere.toJson());
        info(p, "msg.cree", c.tag, c.nom);
        Sync.envoyerEtat(p);
        Sync.diffuserTags(c, null);
        return true;
    }

    /** Ms restantes avant que le joueur puisse rejoindre un clan (0 = libre). */
    public static long delaiRejoindre(ClanStore s, UUID u) {
        LienJoueur l = s.lien(u);
        if (l.quitteLe <= 0) return 0;
        long delai = ClansConfig.DELAI_REJOINDRE_H.get() * 3_600_000L;
        return Math.max(0, l.quitteLe + delai - now());
    }

    // ------------------------------------------------------------------ invitations
    /** Ms jusqu'auxquelles ce clan ne peut pas reinviter ce joueur (0 si libre). */
    private static long refusJusqua(UUID clan, UUID cible, long now) {
        Map<UUID, Long> m = REFUS.get(clan);
        Long t = m == null ? null : m.get(cible);
        if (t == null) return 0;
        if (t > now) return t;
        m.remove(cible);
        return 0;
    }

    /** Consomme une invitation du quota de l'emetteur ; renvoie 0, ou les ms a attendre si le quota est atteint. */
    private static long quotaInvitation(UUID emetteur, long now) {
        long[] q = INVITATIONS_EMISES.computeIfAbsent(emetteur, k -> new long[2]);
        if (now - q[0] >= INVITATIONS_FENETRE_MS) {
            q[0] = now;
            q[1] = 0;
        }
        if (q[1] >= INVITATIONS_PAR_FENETRE) return q[0] + INVITATIONS_FENETRE_MS - now;
        q[1]++;
        return 0;
    }

    /**
     * Invite un joueur en ligne (perm INVITER, pas deja en clan, place libre, delai de l'invite ecoule). Anti-spam :
     * une invitation valide existante n'est pas renvoyee (« deja invite », la cible n'est pas notifiee), un refus
     * bloque le clan 10 min pour cette cible, et un joueur n'emet pas plus de 10 invitations par 5 min.
     */
    public static boolean inviter(ServerPlayer p, String nom) {
        ClanStore s = store();
        Clan c = monClan(p);
        if (c == null || s == null) return false;
        if (!exiger(p, c, Perm.INVITER)) return false;
        MinecraftServer server = p.getServer();
        ServerPlayer cible = server == null ? null : server.getPlayerList().getPlayerByName(nom);
        if (cible == null) {
            erreur(p, "msg.joueur_hors_ligne", nom);
            return false;
        }
        String nomCible = cible.getGameProfile().getName();
        if (cible.getUUID().equals(p.getUUID())) {
            erreur(p, "msg.soi_meme");
            return false;
        }
        if (s.clanDe(cible.getUUID()) != null) {
            erreur(p, "msg.deja_dans_clan", nomCible);
            return false;
        }
        int max = Niveaux.membresMax(c.niveau);
        if (c.membres.size() >= max) {
            erreur(p, "msg.clan_plein", max, c.niveau);
            return false;
        }
        long reste = delaiRejoindre(s, cible.getUUID());
        if (reste > 0) {
            erreur(p, "msg.delai_rejoindre_autre", nomCible, duree(reste));
            return false;
        }
        long now = now();
        if (c.purgerInvitations(now)) s.marquerDirty();
        if (c.invitationDe(cible.getUUID()) != null) {
            erreur(p, "msg.invitation_deja", nomCible);
            return false;
        }
        long refus = refusJusqua(c.id, cible.getUUID(), now);
        if (refus > 0) {
            erreur(p, "msg.invitation_refus_recent", nomCible, duree(refus - now));
            return false;
        }
        if (c.invitations.size() >= INVITATIONS_MAX) {
            erreur(p, "msg.invitation_limite", INVITATIONS_MAX);
            return false;
        }
        long attente = quotaInvitation(p.getUUID(), now);
        if (attente > 0) {
            erreur(p, "msg.invitation_trop", INVITATIONS_PAR_FENETRE, duree(attente));
            return false;
        }
        c.invitations.add(new Invitation(cible.getUUID(), p.getUUID(), p.getGameProfile().getName(), now + Invitation.DUREE_MS));
        s.marquerDirty();
        info(p, "msg.invitation_envoyee", nomCible);
        cible.sendSystemMessage(prefixe(c).append(Component.translatable("msg.invitation_recue", c.tag, c.nom)));
        ClansNet.toPlayer(cible, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.INVITATION, List.of(c.tag, c.nom), "invitation", c.banniere));
        Sync.envoyerInvitations(cible);
        return true;
    }

    /** Accepte (membre RECRUE) ou refuse une invitation. */
    public static boolean repondre(ServerPlayer p, UUID clanId, boolean accepte) {
        ClanStore s = store();
        if (s == null) return false;
        Clan c = s.get(clanId);
        if (c == null) {
            erreur(p, "msg.clan_introuvable", clanId == null ? "?" : clanId.toString().substring(0, 8));
            return false;
        }
        UUID u = p.getUUID();
        long now = now();
        Invitation inv = c.invitationDe(u);
        if (inv == null) {
            erreur(p, "msg.invitation_aucune");
            return false;
        }
        if (inv.expiree(now)) {
            c.invitations.remove(inv);
            s.marquerDirty();
            erreur(p, "msg.invitation_expiree");
            Sync.envoyerInvitations(p);
            return false;
        }
        if (!accepte) {
            c.invitations.remove(inv);
            s.marquerDirty();
            REFUS.computeIfAbsent(c.id, k -> new HashMap<>()).put(u, now + REFUS_COOLDOWN_MS);
            info(p, "msg.invitation_refusee");
            Sync.envoyerInvitations(p);
            return true;
        }
        if (s.clanDe(u) != null) {
            erreur(p, "msg.deja_clan");
            return false;
        }
        long reste = delaiRejoindre(s, u);
        if (reste > 0) {
            erreur(p, "msg.delai_rejoindre", duree(reste));
            return false;
        }
        int max = Niveaux.membresMax(c.niveau);
        if (c.membres.size() >= max) {
            erreur(p, "msg.clan_plein", max, c.niveau);
            return false;
        }
        String nom = p.getGameProfile().getName();
        c.invitations.remove(inv);
        c.membres.put(u, new Membre(u, nom, Grade.RECRUE, now, now, null));
        c.ajouterEvenement(new Evenement(now, Evenement.Genre.ARRIVEE, nom));
        LienJoueur l = s.lien(u);
        l.clan = c.id;
        l.chatClan = false;
        l.dernierNom = nom;
        s.marquerDirtyJoueurs();
        for (Clan autre : s.tous()) if (autre != c && autre.retirerInvitation(u)) s.marquerDirty();
        changement(c);
        diffuserChat(c, "msg.rejoint", nom);
        info(p, "msg.bienvenue", c.tag, c.nom);
        Sync.envoyerEtat(p);
        Sync.diffuserTags(c, null);
        return true;
    }

    // ------------------------------------------------------------------ depart, exclusion
    /** Quitte le clan (le chef doit transferer ou dissoudre). Delai avant de rejoindre un autre clan. */
    public static boolean quitter(ServerPlayer p) {
        ClanStore s = store();
        Clan c = monClan(p);
        if (c == null || s == null) return false;
        if (c.estChef(p.getUUID())) {
            erreur(p, "msg.chef_ne_peut_quitter");
            return false;
        }
        String nom = p.getGameProfile().getName();
        retirerMembre(s, c, p.getUUID(), true);
        c.ajouterEvenement(new Evenement(now(), Evenement.Genre.DEPART, nom));
        changement(c);
        diffuserChat(c, "msg.quitte", nom);
        info(p, "msg.vous_quittez");
        Sync.envoyerSnapshot(p);
        Sync.diffuserTags(c, null, p);
        MinecraftForge.EVENT_BUS.post(new MembreQuitteEvent(c.id, p.getUUID(), false));
        return true;
    }

    /** Retire un membre du clan et met son lien a jour (delai de rejoindre si demande). */
    private static void retirerMembre(ClanStore s, Clan c, UUID u, boolean delai) {
        c.membres.remove(u);
        LienJoueur l = s.lien(u);
        if (c.id.equals(l.clan)) l.clan = null;
        if (delai) l.quitteLe = now();
        l.chatClan = false;
        s.marquerDirtyJoueurs();
    }

    /** Exclut un membre (perm EXCLURE, grade strictement superieur a celui de la cible). */
    public static boolean exclure(ServerPlayer p, UUID cible) {
        ClanStore s = store();
        Clan c = monClan(p);
        if (c == null || s == null) return false;
        if (!exiger(p, c, Perm.EXCLURE)) return false;
        Membre m = cibleValide(p, c, cible);
        if (m == null) return false;
        if (!c.gradeDe(p.getUUID()).domine(m.grade)) {
            erreur(p, "msg.hierarchie");
            return false;
        }
        String nom = m.dernierNom;
        retirerMembre(s, c, cible, true);
        c.ajouterEvenement(new Evenement(now(), Evenement.Genre.EXCLUSION, nom));
        changement(c);
        diffuserChat(c, "msg.exclu", nom);
        MinecraftServer server = p.getServer();
        ServerPlayer ex = server == null ? null : server.getPlayerList().getPlayer(cible);
        if (ex != null) {
            info(ex, "msg.vous_exclu", c.tag);
            ClansNet.toPlayer(ex, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.INFO,
                    ServerHandlers.cleEtArgs("msg.vous_exclu", c.tag), "", null));
            Sync.envoyerSnapshot(ex);
        }
        Sync.diffuserTags(c, null, ex);
        MinecraftForge.EVENT_BUS.post(new MembreQuitteEvent(c.id, cible, true));
        return true;
    }

    /** Membre cible d'une action (dans le clan, pas soi-meme), ou null avec message. */
    @Nullable
    private static Membre cibleValide(ServerPlayer p, Clan c, UUID cible) {
        if (cible == null || cible.equals(p.getUUID())) {
            erreur(p, "msg.soi_meme");
            return null;
        }
        Membre m = c.membre(cible);
        if (m == null) {
            erreur(p, "msg.pas_membre", nomDe(cible));
            return null;
        }
        return m;
    }

    // ------------------------------------------------------------------ grades
    /** Monte la cible d'un grade (perm PROMOUVOIR ; le nouveau grade reste sous celui de l'auteur). */
    public static boolean promouvoir(ServerPlayer p, UUID cible) {
        Clan c = monClan(p);
        if (c == null) return false;
        if (!exiger(p, c, Perm.PROMOUVOIR)) return false;
        Membre m = cibleValide(p, c, cible);
        if (m == null) return false;
        Grade nouveau = m.grade.superieur();
        if (nouveau == null) {
            erreur(p, "msg.grade_max");
            return false;
        }
        if (!c.gradeDe(p.getUUID()).domine(nouveau)) {
            erreur(p, "msg.hierarchie");
            return false;
        }
        m.grade = nouveau;
        c.ajouterEvenement(new Evenement(now(), Evenement.Genre.PROMOTION, m.dernierNom, libelleGrade(nouveau)));
        changement(c);
        diffuserChat(c, prefixe(c).append(Component.translatable("msg.promu", m.dernierNom, Component.translatable(nouveau.libelle()))));
        return true;
    }

    /** Descend la cible d'un grade (perm RETROGRADER ; l'auteur doit dominer la cible). */
    public static boolean retrograder(ServerPlayer p, UUID cible) {
        Clan c = monClan(p);
        if (c == null) return false;
        if (!exiger(p, c, Perm.RETROGRADER)) return false;
        Membre m = cibleValide(p, c, cible);
        if (m == null) return false;
        if (!c.gradeDe(p.getUUID()).domine(m.grade)) {
            erreur(p, "msg.hierarchie");
            return false;
        }
        Grade nouveau = m.grade.inferieur();
        if (nouveau == null) {
            erreur(p, "msg.grade_min");
            return false;
        }
        m.grade = nouveau;
        c.ajouterEvenement(new Evenement(now(), Evenement.Genre.RETROGRADATION, m.dernierNom, libelleGrade(nouveau)));
        changement(c);
        diffuserChat(c, prefixe(c).append(Component.translatable("msg.retrograde", m.dernierNom, Component.translatable(nouveau.libelle()))));
        return true;
    }

    /** Transfere la direction (chef seulement) ; l'ancien chef devient HAUT_GRADE. */
    public static boolean transferer(ServerPlayer p, UUID cible) {
        Clan c = monClan(p);
        if (c == null) return false;
        if (!exigerChef(p, c)) return false;
        Membre m = cibleValide(p, c, cible);
        if (m == null) return false;
        Membre ancien = c.membre(p.getUUID());
        if (ancien != null) ancien.grade = Grade.HAUT_GRADE;
        m.grade = Grade.CHEF;
        m.permsJoueur.clear(); // le chef a tout : ses anciens reglages individuels ne veulent plus rien dire
        c.chef = cible;
        c.ajouterEvenement(new Evenement(now(), Evenement.Genre.TRANSFERT, m.dernierNom));
        changement(c);
        diffuserChat(c, "msg.transfere", m.dernierNom);
        HeraliszClans.LOG.info("[clans] [{}] direction transferee de {} a {}", c.tag, p.getGameProfile().getName(), m.dernierNom);
        return true;
    }

    // ------------------------------------------------------------------ dissolution
    /**
     * Dissout le clan (chef seulement). Refus clair si la banque ne tient pas sur les comptes des membres (le chef
     * doit d'abord retirer ou depenser le surplus) ou si l'economie est indisponible : aucun jeton ne doit disparaitre.
     */
    public static boolean dissoudre(ServerPlayer p) {
        Clan c = monClan(p);
        if (c == null) return false;
        if (!exigerChef(p, c)) return false;
        if (c.banque > 0) {
            Economie eco = Economie.active();
            if (!eco.disponible()) {
                erreur(p, "msg.economie_indisponible");
                return false;
            }
            long manque = c.banque - capaciteRemboursement(c, eco);
            if (manque > 0) {
                erreur(p, "msg.dissolution_banque", eco.format(manque));
                return false;
            }
        }
        dissoudre(c, "dissolution");
        return true;
    }

    /** Membres dans l'ordre de remboursement : le chef, puis par grade decroissant et par anciennete. */
    private static List<UUID> ordreRemboursement(Clan c) {
        List<Membre> l = new ArrayList<>(c.membres.values());
        l.sort(Comparator.comparingInt((Membre m) -> c.estChef(m.uuid) ? -1 : m.grade.ordinal()).thenComparingLong(m -> m.rejointLe));
        List<UUID> ordre = new ArrayList<>(l.size() + 1);
        if (!c.estMembre(c.chef)) ordre.add(c.chef); // donnees abimees : le chef reste premier servi
        for (Membre m : l) ordre.add(m.uuid);
        return ordre;
    }

    /** Ce que les comptes des membres peuvent recevoir en tout (borne a la banque : on s'arrete des que ca suffit). */
    static long capaciteRemboursement(Clan c, Economie eco) {
        long total = 0;
        for (UUID u : ordreRemboursement(c)) {
            total += place(eco, u);
            if (total >= c.banque) break;
        }
        return total;
    }

    /**
     * Dissolution effective (chef ou admin) : la banque est versee au chef jusqu'a son plafond, le reste aux autres
     * membres (grade puis anciennete, chacun jusqu'a son plafond), meme hors ligne ; le clan est retire du store AVANT
     * la liberation des territoires (aucun « zone perdue » n'est envoye) ; les membres ne subissent aucun delai. Renvoie
     * les jetons qui n'ont pu etre verses a personne (0 par le chemin joueur, qui a verifie la place ; possible par
     * /clan admin supprimer ou si l'economie est indisponible : journalises).
     */
    public static long dissoudre(Clan c, String motif) {
        ClanStore s = store();
        if (s == null) return c.banque;
        List<ServerPlayer> enLigne = membresEnLigne(c);
        Economie eco = Economie.active();
        Map<UUID, Long> verses = new LinkedHashMap<>();
        long reste = c.banque;
        if (reste > 0) {
            if (eco.disponible()) {
                for (UUID u : ordreRemboursement(c)) {
                    if (reste <= 0) break;
                    long v = Math.min(reste, place(eco, u));
                    if (v <= 0) continue;
                    eco.deposer(u, v, "clan:" + motif);
                    verses.merge(u, v, Long::sum);
                    reste -= v;
                }
            }
            if (reste > 0) HeraliszClans.LOG.warn("[clans] dissolution de [{}] : {} jetons non verses ({})", c.tag, reste,
                    eco.disponible() ? "comptes des membres pleins" : "economie indisponible");
            c.banque = 0;
        }
        c.invitations.clear();
        s.supprimer(c);                 // d'abord : les evenements Perdu ne trouvent plus le clan
        REFUS.remove(c.id);
        s.sauvegarderMaintenant();      // le cote debite (la banque n'existe plus)...
        if (!verses.isEmpty()) persisterEconomie(); // ...puis les comptes credites
        Territoires.liberer(c.id);
        Sync.marquer(c); // sans effet (clan retire) mais nettoie les listes
        MinecraftServer server = Sync.serveur();
        for (ServerPlayer m : enLigne) {
            m.sendSystemMessage(Component.translatable("msg.dissous", c.tag));
            ClansNet.toPlayer(m, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.INFO,
                    ServerHandlers.cleEtArgs("msg.dissous", c.tag), "", null));
            ClansNet.toPlayer(m, new Packets.ClanSnapshotS2C(null));
        }
        if (server != null) {
            for (Map.Entry<UUID, Long> e : verses.entrySet()) {
                ServerPlayer b = server.getPlayerList().getPlayer(e.getKey());
                if (b != null) info(b, "msg.dissolution_verse", eco.format(e.getValue()), c.tag);
            }
        }
        Sync.diffuserTags(c, null); // c.membres est encore rempli : leur liste Tab perd le prefixe
        long total = 0;
        for (long v : verses.values()) total += v;
        HeraliszClans.LOG.info("[clans] clan [{}] {} dissous ({}) : {} jetons verses a {} membre(s)", c.tag, c.nom, motif, total, verses.size());
        MinecraftForge.EVENT_BUS.post(new ClanDissousEvent(c.id, c.tag, c.membres.keySet()));
        return reste;
    }

    // ------------------------------------------------------------------ banque
    /** Depot (perm BANQUE_DEPOSER, solde du joueur) ou retrait (perm BANQUE_RETIRER, solde de la banque, plafond du joueur). */
    public static boolean banque(ServerPlayer p, boolean depot, long montant) {
        ClanStore s = store();
        Clan c = monClan(p);
        if (c == null || s == null) return false;
        if (montant <= 0 || montant > BANQUE_MAX) {
            erreur(p, "msg.montant_invalide");
            return false;
        }
        Economie eco = Economie.active();
        if (!eco.disponible()) {
            erreur(p, "msg.economie_indisponible");
            return false;
        }
        UUID u = p.getUUID();
        String nom = p.getGameProfile().getName();
        long now = now();
        if (depot) {
            if (!exiger(p, c, Perm.BANQUE_DEPOSER)) return false;
            if (c.banque + montant > BANQUE_MAX) {
                erreur(p, "msg.banque_pleine");
                return false;
            }
            if (!eco.retirer(u, montant, "clan:banque")) {
                erreur(p, "msg.argent_insuffisant");
                return false;
            }
            c.banque += montant;
            c.ajouterMouvement(new Mouvement(now, u, nom, montant, "depot"));
            info(p, "msg.depot", eco.format(montant));
            Quetes.mouvementBanque(c, montant);
        } else {
            if (!exiger(p, c, Perm.BANQUE_RETIRER)) return false;
            if (c.banque < montant) {
                erreur(p, "msg.banque_insuffisante");
                return false;
            }
            if (EconomieApi.maxSolde() - eco.solde(u) < montant) {
                erreur(p, "msg.plafond_jetons", eco.format(EconomieApi.maxSolde()));
                return false;
            }
            c.banque -= montant;
            eco.deposer(u, montant, "clan:banque");
            c.ajouterMouvement(new Mouvement(now, u, nom, -montant, "retrait"));
            info(p, "msg.retrait", eco.format(montant));
            Quetes.mouvementBanque(c, -montant); // le net depose recule : un cycle depot/retrait ne compte pas
        }
        ClansNet.toPlayer(p, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.INFO,
                ServerHandlers.cleEtArgs(depot ? "msg.depot" : "msg.retrait", eco.format(montant)), "banque", null));
        changement(c);
        // les deux cotes sont ecrits tout de suite, le cote debite en premier
        if (depot) {
            persisterEconomie();
            s.sauvegarderMaintenant();
        } else {
            s.sauvegarderMaintenant();
            persisterEconomie();
        }
        return true;
    }

    // ------------------------------------------------------------------ parametres
    /** Change le nom, le tag ou la description (perm, cooldown par champ, validation, unicite). */
    public static boolean parametre(ServerPlayer p, Packets.ParametreC2S.Champ champ, String valeur) {
        ClanStore s = store();
        Clan c = monClan(p);
        if (c == null || s == null) return false;
        long now = now(), cooldown = ClansConfig.COOLDOWN_MODIF_H.get() * 3_600_000L;
        switch (champ) {
            case NOM -> {
                if (!exiger(p, c, Perm.MODIFIER_NOM)) return false;
                if (!cooldownOk(p, c.dernierChgtNom, cooldown, now)) return false;
                String err = Clan.validerNom(valeur);
                if (err != null) {
                    erreur(p, err);
                    return false;
                }
                String v = Clan.nettoyer(valeur);
                if (v.equals(c.nom)) {
                    erreur(p, "msg.aucun_changement");
                    return false;
                }
                Clan autre = s.parNom(v);
                if (autre != null && autre != c) {
                    erreur(p, "msg.nom_pris");
                    return false;
                }
                c.nom = v;
                c.dernierChgtNom = now;
                s.reindexer(c);
                info(p, "msg.modifie", Component.translatable("gui.creation.nom"));
            }
            case TAG -> {
                if (!exiger(p, c, Perm.MODIFIER_TAG)) return false;
                if (!cooldownOk(p, c.dernierChgtTag, cooldown, now)) return false;
                String err = Clan.validerTag(valeur);
                if (err != null) {
                    erreur(p, err);
                    return false;
                }
                String v = Clan.nettoyer(valeur).toUpperCase(Locale.ROOT);
                if (v.equals(c.tag)) {
                    erreur(p, "msg.aucun_changement");
                    return false;
                }
                Clan autre = s.parTag(v);
                if (autre != null && autre != c) {
                    erreur(p, "msg.tag_pris");
                    return false;
                }
                c.tag = v;
                c.dernierChgtTag = now;
                s.reindexer(c);
                info(p, "msg.modifie", Component.translatable("gui.creation.tag"));
                Sync.diffuserTags(c, null);
            }
            case DESCRIPTION -> {
                if (!exiger(p, c, Perm.MODIFIER_DESCRIPTION)) return false;
                if (!cooldownOk(p, c.dernierChgtDesc, cooldown, now)) return false;
                String err = Clan.validerDescription(valeur);
                if (err != null) {
                    erreur(p, err);
                    return false;
                }
                String v = Clan.nettoyerDescription(valeur);
                if (v.equals(c.description)) {
                    erreur(p, "msg.aucun_changement");
                    return false;
                }
                c.description = v;
                c.dernierChgtDesc = now;
                info(p, "msg.modifie", Component.translatable("gui.creation.description"));
            }
        }
        changement(c);
        return true;
    }

    private static boolean cooldownOk(ServerPlayer p, long dernier, long cooldown, long now) {
        if (dernier <= 0 || dernier + cooldown <= now) return true;
        erreur(p, "msg.cooldown", duree(dernier + cooldown - now));
        return false;
    }

    /**
     * Change la banniere (perm MODIFIER_BANNIERE, cooldown) : banniere revalidee, journal du clan, log serveur avec le
     * JSON avant/apres (moderation) ; les tags sont rediffuses (couleur).
     */
    public static boolean banniere(ServerPlayer p, Banniere b) {
        Clan c = monClan(p);
        if (c == null || b == null) return false;
        if (!exiger(p, c, Perm.MODIFIER_BANNIERE)) return false;
        long now = now();
        if (!cooldownOk(p, c.dernierChgtBanniere, ClansConfig.COOLDOWN_MODIF_H.get() * 3_600_000L, now)) return false;
        Banniere nouvelle = b.valider();
        if (!fr.heralisz.clans.model.Avantages.degradeLibre(c.niveau, nouvelle.degrade) && nouvelle.degrade != c.banniere.degrade) {
            erreur(p, "msg.banniere_niveau_degrade");
            return false;
        }
        if (c.niveau < fr.heralisz.clans.model.Avantages.NIVEAU_FORMES) {
            for (fr.heralisz.clans.model.Calque k : nouvelle.calques) {
                if (!fr.heralisz.clans.model.Avantages.formeRare(k.forme())) continue;
                boolean deja = false;
                for (fr.heralisz.clans.model.Calque a : c.banniere.calques) if (a.forme() == k.forme()) deja = true;
                if (!deja) {
                    erreur(p, "msg.banniere_niveau_forme");
                    return false;
                }
            }
        }
        if (nouvelle.equals(c.banniere)) {
            erreur(p, "msg.aucun_changement");
            return false;
        }
        Banniere avant = c.banniere;
        String nom = p.getGameProfile().getName();
        c.banniere = nouvelle;
        c.dernierChgtBanniere = now;
        c.ajouterEvenement(new Evenement(now, Evenement.Genre.BANNIERE, nom));
        HeraliszClans.LOG.info("[clans] banniere de [{}] modifiee par {} ({}) : avant {} apres {}", c.tag, nom, p.getUUID(),
                avant.toJson(), nouvelle.toJson());
        changement(c);
        info(p, "msg.modifie", Component.translatable("gui.parametres.banniere"));
        Sync.diffuserTags(c, null);
        return true;
    }

    /**
     * Moderation : remet la banniere par defaut ({@link Banniere#DEFAUT}) sans toucher au delai du clan ; journal du
     * clan, log serveur avant/apres, message (chat + toast) au chef s'il est en ligne. Faux si elle l'etait deja.
     */
    public static boolean adminBanniereReset(Clan c, String auteur) {
        if (c.banniere.equals(Banniere.DEFAUT)) return false;
        Banniere avant = c.banniere;
        c.banniere = Banniere.DEFAUT;
        c.ajouterEvenement(new Evenement(now(), Evenement.Genre.BANNIERE_REINITIALISEE));
        HeraliszClans.LOG.info("[clans] banniere de [{}] reinitialisee par {} (moderation) : avant {} apres {}", c.tag, auteur,
                avant.toJson(), Banniere.DEFAUT.toJson());
        changement(c);
        Sync.diffuserTags(c, null);
        MinecraftServer server = Sync.serveur();
        ServerPlayer chef = server == null ? null : server.getPlayerList().getPlayer(c.chef);
        if (chef != null) {
            Component texte = Component.translatable("msg.banniere_reinitialisee", c.tag);
            chef.sendSystemMessage(texte.copy().withStyle(s -> s.withColor(0xFFB347)));
            ClansNet.toPlayer(chef, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.INFO, ServerHandlers.cleEtArgs("msg.banniere_reinitialisee", c.tag), "",
                    Banniere.DEFAUT));
        }
        return true;
    }

    /** Permissions d'un grade modifiable (chef seulement). */
    public static boolean perms(ServerPlayer p, Grade grade, int masque) {
        Clan c = monClan(p);
        if (c == null) return false;
        if (!exigerChef(p, c)) return false;
        if (grade == null || grade == Grade.CHEF) {
            erreur(p, "msg.hierarchie");
            return false;
        }
        c.perms.put(grade, Perm.depuisMasque(masque & Perm.TOUTES));
        changement(c);
        return true;
    }

    // ------------------------------------------------------------------ permissions par joueur
    /** Nom de l'auteur d'un reglage fait par le systeme (API sans acteur, admin). */
    public static final String AUTEUR_SYSTEME = "HeraliZ";
    /** Fenetre de regroupement des lignes « permission » du journal (meme auteur, meme cible). */
    private static final long JOURNAL_PERMS_MS = 5 * 60_000L;

    /**
     * Raison du refus d'un reglage individuel (cle de traduction), ou null s'il est permis. Regles : la cible est un
     * membre du clan et n'est pas le chef (il a tout, non reglable) ; l'acteur (null = systeme : API, admin, aucune
     * autre regle) peut GERER_PERMISSIONS (defaut chef et haut grade), est d'un grade strictement superieur a la cible
     * et n'accorde pas une permission qu'il n'a pas lui-meme (AUTORISE, ou retour au grade depuis un refus quand le
     * grade l'a).
     */
    @Nullable
    public static String refusPermJoueur(Clan c, @Nullable UUID acteur, UUID membre, Perm perm, Perm.Etat etat) {
        if (c == null || perm == null || etat == null) return "msg.permission";
        Membre m = membre == null ? null : c.membre(membre);
        if (m == null) return "msg.pas_membre";
        if (c.estChef(membre) || m.grade == Grade.CHEF) return "msg.perm_joueur_chef";
        if (acteur == null) return null;
        Grade ga = c.gradeDe(acteur);
        if (ga == null) return "msg.pas_de_clan";
        if (!c.peut(acteur, Perm.GERER_PERMISSIONS)) return "msg.permission";
        if (!ga.domine(m.grade)) return "msg.perm_joueur_hierarchie";
        if (accorde(c, m, perm, etat) && !c.peut(acteur, perm)) return "msg.perm_joueur_accorder";
        return null;
    }

    /** Vrai si passer a cet etat accorde la permission : AUTORISE, ou retour au grade (qui l'a) depuis un refus. */
    private static boolean accorde(Clan c, Membre m, Perm perm, Perm.Etat etat) {
        if (etat == Perm.Etat.AUTORISE) return true;
        return etat == Perm.Etat.HERITE && m.etatPerm(perm) == Perm.Etat.REFUSE && c.permsDe(m.grade).contains(perm);
    }

    /** Applique un reglage deja verifie : vrai si quelque chose a change (journal regroupe + log). */
    private static boolean appliquerPermJoueur(Clan c, Membre m, Perm perm, Perm.Etat etat, String auteur, long now) {
        Perm.Etat avant = m.etatPerm(perm);
        if (!m.definirPerm(perm, etat)) return false;
        journalPerm(c, m.dernierNom, auteur, perm, etat, now);
        HeraliszClans.LOG.info("[clans] [{}] permission {} de {} : {} -> {} (par {})", c.tag, perm, m.dernierNom, avant, etat, auteur);
        return true;
    }

    /**
     * Ligne du journal du clan : un changement (« X a autorise « Construire » a Y »), ou regroupee avec la precedente si
     * elle vient du meme auteur sur la meme cible depuis moins de 5 min (« X a modifie les permissions de Y (3
     * changements) ») : regler dix permissions ne noie pas le journal.
     */
    private static void journalPerm(Clan c, String cible, String auteur, Perm perm, Perm.Etat etat, long now) {
        Evenement e0 = c.journal.isEmpty() ? null : c.journal.get(0);
        if (e0 != null && e0.genre() == Evenement.Genre.PERMISSION && e0.args().size() >= 4 && now - e0.quand() < JOURNAL_PERMS_MS
                && e0.args().get(0).equals(cible) && e0.args().get(1).equals(auteur)) {
            int n = 2;
            if ("*".equals(e0.args().get(2))) {
                try {
                    n = Integer.parseInt(e0.args().get(3)) + 1;
                } catch (NumberFormatException ex) {
                    n = 2;
                }
            }
            c.journal.set(0, new Evenement(now, Evenement.Genre.PERMISSION, cible, auteur, "*", String.valueOf(n)));
            return;
        }
        c.ajouterEvenement(new Evenement(now, Evenement.Genre.PERMISSION, cible, auteur, perm.name(), etat.name()));
    }

    /**
     * Reglages envoyes par l'editeur (PermsJoueurC2S) : pour chaque permission du masque, l'etat voulu (AUTORISE si
     * dans {@code autorise}, REFUSE si dans {@code refuse}, sinon HERITE). Chaque reglage est verifie seul
     * ({@link #refusPermJoueur}) ; les refus envoient un message par sorte et le snapshot du joueur (son affichage
     * optimiste est corrige). Renvoie le nombre de reglages appliques.
     */
    public static int permsJoueur(ServerPlayer p, UUID membre, int masque, int autorise, int refuse) {
        Clan c = monClan(p);
        if (c == null) return 0;
        masque &= Perm.TOUTES;
        if (masque == 0) return 0;
        Membre m = membre == null ? null : c.membre(membre);
        if (m == null) {
            erreur(p, "msg.pas_membre", membre == null ? "?" : nomDe(membre));
            Sync.envoyerSnapshot(p);
            return 0;
        }
        String auteur = p.getGameProfile().getName();
        long now = now();
        int faits = 0;
        Map<String, Perm> refus = new LinkedHashMap<>();
        for (Perm perm : Perm.values()) {
            if ((masque & perm.bit()) == 0) continue;
            Perm.Etat etat = (autorise & perm.bit()) != 0 ? Perm.Etat.AUTORISE
                    : (refuse & perm.bit()) != 0 ? Perm.Etat.REFUSE : Perm.Etat.HERITE;
            String r = refusPermJoueur(c, p.getUUID(), membre, perm, etat);
            if (r != null) {
                refus.putIfAbsent(r, perm);
                continue;
            }
            if (appliquerPermJoueur(c, m, perm, etat, auteur, now)) faits++;
        }
        for (Map.Entry<String, Perm> e : refus.entrySet()) {
            if (e.getKey().equals("msg.perm_joueur_accorder")) erreur(p, e.getKey(), Component.translatable(e.getValue().libelle()));
            else erreur(p, e.getKey());
        }
        if (faits > 0) changement(c);
        if (!refus.isEmpty()) Sync.envoyerSnapshot(p);
        return faits;
    }

    /**
     * Reglage individuel par l'API ou l'admin (acteur null = systeme). Memes regles que l'editeur
     * ({@link #refusPermJoueur}) ; journal et snapshot si quelque chose change. Vrai si le reglage est permis (applique,
     * ou deja dans cet etat).
     */
    public static boolean definirPermJoueur(@Nullable UUID acteur, Clan c, UUID membre, Perm perm, Perm.Etat etat) {
        if (refusPermJoueur(c, acteur, membre, perm, etat) != null) return false;
        Membre m = c.membre(membre);
        String auteur = acteur == null ? AUTEUR_SYSTEME : nomDans(c, acteur);
        if (appliquerPermJoueur(c, m, perm, etat, auteur, now())) changement(c);
        return true;
    }

    // ------------------------------------------------------------------ chat de clan
    /** Bascule le mode /cc (tous les messages partent au clan). */
    public static boolean chatBascule(ServerPlayer p) {
        ClanStore s = store();
        Clan c = monClan(p);
        if (c == null || s == null) return false;
        if (!exiger(p, c, Perm.CHAT_CLAN)) return false;
        LienJoueur l = s.lien(p.getUUID());
        l.chatClan = !l.chatClan;
        s.marquerDirtyJoueurs();
        info(p, l.chatClan ? "msg.chat_actif" : "msg.chat_inactif");
        return true;
    }

    /**
     * Envoie un message au clan : « [TAG] Nom : message » dans la couleur du clan, aux membres en ligne. Cadence
     * 1 message/s par joueur ; un joueur muet (noeud {@code heralisz.clans.mute} ou mute Essentials, voir
     * {@link Moderation}) est refuse, en commande comme en mode bascule.
     */
    public static boolean chat(ServerPlayer p, String message) {
        Clan c = monClan(p);
        if (c == null) return false;
        if (!exiger(p, c, Perm.CHAT_CLAN)) return false;
        String m = Clan.nettoyer(message);
        if (m.isEmpty()) return false;
        if (m.length() > CHAT_MAX) m = m.substring(0, CHAT_MAX);
        long now = now();
        Long dernier = DERNIER_CHAT.get(p.getUUID());
        if (dernier != null && now - dernier < CHAT_CADENCE_MS) {
            erreur(p, "msg.chat_trop_rapide");
            return false;
        }
        if (Moderation.estMuet(p)) {
            erreur(p, "msg.chat_muet");
            return false;
        }
        DERNIER_CHAT.put(p.getUUID(), now);
        String nom = p.getGameProfile().getName();
        Component ligne = Component.translatable("chat.clan", c.tag, nom, m).withStyle(s -> s.withColor(c.banniere.accent()));
        diffuserChat(c, ligne);
        HeraliszClans.LOG.info("[clans] [{}] {} : {}", c.tag, nom, m);
        return true;
    }

    /** Vrai si le joueur est en mode chat de clan et peut l'utiliser (sinon le mode est coupe en silence). */
    public static boolean enModeChat(ServerPlayer p) {
        ClanStore s = store();
        if (s == null) return false;
        LienJoueur l = s.lien(p.getUUID());
        if (!l.chatClan) return false;
        Clan c = s.clanDe(p.getUUID());
        if (c == null || !c.peut(p.getUUID(), Perm.CHAT_CLAN)) {
            l.chatClan = false;
            s.marquerDirtyJoueurs();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ XP et niveaux
    /**
     * Ajoute de l'XP au clan (comptee dans les stats de l'auteur s'il est donne) ; monte les niveaux tant que l'XP
     * suffit, le surplus est reporte ; au niveau max l'XP continue de s'accumuler (classement).
     */
    public static void ajouterXp(Clan c, long xp, @Nullable UUID auteur) {
        if (c == null || xp <= 0) return;
        c.xp += xp;
        if (auteur != null) {
            Membre m = c.membre(auteur);
            if (m != null) m.stats.xpApporte += xp;
        }
        boolean monte = false;
        while (c.niveau < Niveaux.MAX) {
            long besoin = Niveaux.xpPour(c.niveau);
            if (besoin <= 0 || c.xp < besoin) break;
            c.xp -= besoin;
            c.niveau++;
            monte = true;
            c.ajouterEvenement(new Evenement(now(), Evenement.Genre.NIVEAU, String.valueOf(c.niveau)));
            diffuserChat(c, "msg.niveau_sup", c.niveau);
            diffuser(c, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.NIVEAU, List.of(String.valueOf(c.niveau)), "niveau_sup", null));
            HeraliszClans.LOG.info("[clans] [{}] passe au niveau {}", c.tag, c.niveau);
        }
        ClanStore s = store();
        if (s != null) s.marquerDirty();
        if (monte) Sync.marquer(c);
        else Sync.marquerLent(c);
    }

    // ------------------------------------------------------------------ classement
    /** Top n clans par niveau puis XP totale, avec kills, membres, territoires tenus. */
    public static List<ClanLigne> classement(int n) {
        List<ClanLigne> l = new ArrayList<>();
        ClanStore s = store();
        if (s == null) return l;
        int rang = 1;
        for (Clan c : s.classement()) {
            if (rang > n) break;
            l.add(new ClanLigne(rang++, c.id, c.tag, c.nom, c.banniere, c.niveau, c.xpTotale(), c.membres.size(), c.totaux().kills,
                    Territoires.compterTenus(c.id)));
        }
        return l;
    }

    // ------------------------------------------------------------------ administration
    /** Place un joueur dans un clan sans condition (limites et delai ignores) ; un chef d'un autre clan est refuse. */
    public static boolean adminForcer(UUID joueur, String nom, Clan c) {
        ClanStore s = store();
        if (s == null || c == null) return false;
        Clan actuel = s.clanDe(joueur);
        if (actuel == c) return true;
        if (actuel != null) {
            if (actuel.estChef(joueur)) return false;
            retirerMembre(s, actuel, joueur, false);
            actuel.ajouterEvenement(new Evenement(now(), Evenement.Genre.DEPART, nom));
            changement(actuel);
            MinecraftForge.EVENT_BUS.post(new MembreQuitteEvent(actuel.id, joueur, false));
        }
        long now = now();
        c.membres.put(joueur, new Membre(joueur, nom, Grade.RECRUE, now, now, null));
        c.ajouterEvenement(new Evenement(now, Evenement.Genre.ARRIVEE, nom));
        for (Clan autre : s.tous()) autre.retirerInvitation(joueur);
        LienJoueur l = s.lien(joueur);
        l.clan = c.id;
        l.quitteLe = 0;
        l.chatClan = false;
        if (!nom.isEmpty()) l.dernierNom = nom;
        s.marquerDirtyJoueurs();
        changement(c);
        diffuserChat(c, "msg.rejoint", nom);
        MinecraftServer server = Sync.serveur();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(joueur);
        if (p != null) Sync.envoyerEtat(p);
        Sync.diffuserTags(c, null, p);
        return true;
    }

    /** Fixe le niveau (1..20) et remet l'XP du niveau a 0. */
    public static void adminNiveau(Clan c, int niveau) {
        c.niveau = Math.max(1, Math.min(Niveaux.MAX, niveau));
        c.xp = 0;
        changement(c);
        diffuserChat(c, "msg.niveau_sup", c.niveau);
    }
}
