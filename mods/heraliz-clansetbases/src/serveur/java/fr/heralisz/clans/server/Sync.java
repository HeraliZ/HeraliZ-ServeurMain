package fr.heralisz.clans.server;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Invitation;
import fr.heralisz.clans.model.InvitationVue;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.model.Mouvement;
import fr.heralisz.clans.model.Niveaux;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.model.ReglagesPerms;
import fr.heralisz.clans.model.TagVue;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.territory.Territoires;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronisation serveur -> client de l'etat des clans : construction du {@link ClanSnapshot} (membres en ligne,
 * soldes, cooldowns, limites du niveau, quetes, territoires), invitations, table des tags.
 *
 * <p>Regroupement : une modification marque le clan « sale » ({@link #marquer}) ; le tick vide la liste en envoyant au
 * plus un snapshot par seconde et par clan. Les changements frequents et peu urgents (stats, progression des quetes,
 * temps de jeu) passent par {@link #marquerLent} : au plus un envoi toutes les 10 s, et seulement aux membres dont
 * l'ecran du clan est ouvert (OUVRIR recu depuis moins de {@link #ECRAN_TTL_MS}, pas de FERMER depuis) : hors ecran
 * le client ne lit que le tag et la couleur, qui ne changent jamais par un envoi lent. Un changement rapide envoie le
 * snapshot complet a tous les membres. Un joueur qui ouvre l'ecran redemande son etat (OUVRIR) et le recoit
 * immediatement.
 */
public final class Sync {
    private Sync() {
    }

    private static final long CADENCE_MS = 1000;
    private static final long CADENCE_LENTE_MS = 10_000;
    /** Duree pendant laquelle un ecran est considere ouvert apres le dernier OUVRIR (sans FERMER). */
    static final long ECRAN_TTL_MS = 30_000;

    private static final Set<UUID> SALES = new HashSet<>();
    private static final Set<UUID> SALES_LENTS = new HashSet<>();
    private static final Map<UUID, Long> DERNIER_ENVOI = new HashMap<>();
    /** Joueur -> ms du dernier OUVRIR (retire a FERMER et a la deconnexion). */
    private static final Map<UUID, Long> ECRANS = new HashMap<>();

    /** Le serveur en cours (null hors serveur). */
    @Nullable
    static MinecraftServer serveur() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** A appeler a l'arret du serveur. */
    static void vider() {
        SALES.clear();
        SALES_LENTS.clear();
        DERNIER_ENVOI.clear();
        ECRANS.clear();
    }

    // ------------------------------------------------------------------ ecrans ouverts
    /** OUVRIR recu : l'ecran du joueur compte comme ouvert pendant {@link #ECRAN_TTL_MS}. */
    public static void ecranOuvert(UUID joueur) {
        ECRANS.put(joueur, System.currentTimeMillis());
    }

    /** FERMER recu (ou deconnexion) : plus de mises a jour lentes pour ce joueur. */
    public static void ecranFerme(UUID joueur) {
        ECRANS.remove(joueur);
    }

    /** Vrai si le joueur regarde son ecran de clan (l'entree perimee est retiree au passage). */
    static boolean regarde(UUID joueur, long now) {
        Long t = ECRANS.get(joueur);
        if (t == null) return false;
        if (now - t < ECRAN_TTL_MS) return true;
        ECRANS.remove(joueur);
        return false;
    }

    // ------------------------------------------------------------------ marquage
    /** Le clan a change : snapshot a tous ses membres en ligne (au plus 1/s). */
    public static void marquer(Clan c) {
        if (c != null) SALES.add(c.id);
    }

    /** Changement peu urgent (stats, progression) : snapshot au plus toutes les 10 s. */
    public static void marquerLent(Clan c) {
        if (c != null) SALES_LENTS.add(c.id);
    }

    /** Appele a chaque tick serveur (fin) : envoie les snapshots des clans sales dont la cadence est ecoulee. */
    static void tick() {
        if (SALES.isEmpty() && SALES_LENTS.isEmpty()) return;
        ClanStore store = ClanStore.get();
        MinecraftServer server = serveur();
        if (store == null || server == null) return;
        long now = System.currentTimeMillis();
        vider(SALES, CADENCE_MS, false, store, server, now);
        vider(SALES_LENTS, CADENCE_LENTE_MS, true, store, server, now);
    }

    private static void vider(Set<UUID> sales, long cadence, boolean ecransSeulement, ClanStore store, MinecraftServer server, long now) {
        if (sales.isEmpty()) return;
        for (Iterator<UUID> it = sales.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Clan c = store.get(id);
            if (c == null) {
                it.remove();
                DERNIER_ENVOI.remove(id);
                continue;
            }
            long dernier = DERNIER_ENVOI.getOrDefault(id, 0L);
            if (now - dernier < cadence) continue;
            it.remove();
            envoyerAuxMembres(c, server, now, ecransSeulement);
        }
    }

    /** Snapshot a tous les membres en ligne du clan, tout de suite (les deux listes de « sale » sont nettoyees). */
    public static void envoyerAuxMembres(Clan c) {
        MinecraftServer server = serveur();
        if (server != null) envoyerAuxMembres(c, server, System.currentTimeMillis(), false);
    }

    /**
     * Envoi groupe : a tous les membres en ligne, ou (mise a jour lente) seulement a ceux dont l'ecran est ouvert. Le
     * snapshot n'est construit que s'il y a au moins un destinataire ; la cadence n'est consommee que s'il part.
     */
    private static void envoyerAuxMembres(Clan c, MinecraftServer server, long now, boolean ecransSeulement) {
        SALES.remove(c.id);
        SALES_LENTS.remove(c.id);
        Base base = null;
        for (UUID u : c.membres.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) continue;
            if (ecransSeulement && !regarde(u, now)) continue;
            if (base == null) {
                base = base(c, server, now);
                DERNIER_ENVOI.put(c.id, now);
            }
            ClansNet.toPlayer(p, new Packets.ClanSnapshotS2C(pour(base, c, p)));
            envoyerArbre(c, p);
        }
    }

    // ------------------------------------------------------------------ envoi a un joueur
    /** Etat complet du joueur : snapshot de son clan (ou null) + ses invitations. */
    public static void envoyerEtat(ServerPlayer p) {
        ClanStore store = ClanStore.get();
        MinecraftServer server = serveur();
        if (store == null || server == null) return;
        Clan c = store.clanDe(p.getUUID());
        long now = System.currentTimeMillis();
        ClansNet.toPlayer(p, new Packets.ClanSnapshotS2C(c == null ? null : pour(base(c, server, now), c, p)));
        if (c != null) envoyerArbre(c, p);
        envoyerInvitations(p);
    }

    /** Snapshot seul (le clan du joueur, ou null s'il n'en a plus). */
    public static void envoyerSnapshot(ServerPlayer p) {
        ClanStore store = ClanStore.get();
        MinecraftServer server = serveur();
        if (store == null || server == null) return;
        Clan c = store.clanDe(p.getUUID());
        ClansNet.toPlayer(p, new Packets.ClanSnapshotS2C(c == null ? null : pour(base(c, server, System.currentTimeMillis()), c, p)));
        if (c != null) envoyerArbre(c, p);
    }

    /** Arbre d'ameliorations et points de saison du clan (onglet Niveau). */
    static void envoyerArbre(Clan c, ServerPlayer p) {
        fr.heralisz.clans.model.Arbre.Branche[] br = fr.heralisz.clans.model.Arbre.Branche.values();
        int[] paliers = new int[br.length];
        for (int i = 0; i < br.length; i++) paliers[i] = c.palier(br[i]);
        double[] sc = fr.heralisz.clans.saison.Saison.scores(c.id);
        ClansNet.toPlayer(p, new Packets.ArbreSaisonS2C(paliers, c.pointsLibres(), c.estChef(p.getUUID()),
                fr.heralisz.clans.saison.Saison.active(), fr.heralisz.clans.saison.Saison.fin(),
                fr.heralisz.clans.saison.SaisonApi.aUnCoeur(c.id), fr.heralisz.clans.saison.Saison.entiers(sc[0]),
                fr.heralisz.clans.saison.Saison.entiers(sc[1]), fr.heralisz.clans.saison.Saison.entiers(sc[2]),
                fr.heralisz.clans.saison.Saison.rangs(c.id)));
    }

    /** Les invitations en attente du joueur (expirees purgees). */
    public static void envoyerInvitations(ServerPlayer p) {
        ClansNet.toPlayer(p, new Packets.InvitesS2C(invitationsDe(p.getUUID())));
    }

    /** Invitations valides adressees a un joueur, dans tous les clans. */
    public static List<InvitationVue> invitationsDe(UUID joueur) {
        List<InvitationVue> l = new ArrayList<>();
        ClanStore store = ClanStore.get();
        if (store == null) return l;
        long now = System.currentTimeMillis();
        for (Clan c : store.tous()) {
            if (c.invitations.isEmpty()) continue;
            if (c.purgerInvitations(now)) store.marquerDirty();
            Invitation i = c.invitationDe(joueur);
            if (i == null) continue;
            l.add(new InvitationVue(c.id, c.tag, c.nom, c.banniere, c.membres.size(), c.membresMax(), i.auteurNom(),
                    Math.max(0, i.expireLe() - now)));
            if (l.size() >= 32) break;
        }
        return l;
    }

    // ------------------------------------------------------------------ tags
    /** Table des tags de tous les joueurs en ligne (sauf « exclure », joueur en train de partir). */
    public static Map<UUID, TagVue> tableTags(@Nullable UUID exclure) {
        Map<UUID, TagVue> m = new HashMap<>();
        ClanStore store = ClanStore.get();
        MinecraftServer server = serveur();
        if (store == null || server == null) return m;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(exclure)) continue;
            Clan c = store.clanDe(p.getUUID());
            if (c != null) m.put(p.getUUID(), new TagVue(c.tag, c.banniere.accent()));
        }
        return m;
    }

    /**
     * Diffuse la table des tags a tout le monde et rafraichit la liste Tab des membres en ligne du clan donne (null :
     * aucun rafraichissement, par exemple a la deconnexion, pour ne pas envoyer n² paquets).
     */
    public static void diffuserTags(@Nullable Clan clanChange, @Nullable UUID exclure) {
        MinecraftServer server = serveur();
        if (server == null) return;
        Packets.TagsS2C paquet = new Packets.TagsS2C(tableTags(exclure));
        ClansNet.toAll(paquet);
        if (clanChange == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(exclure)) continue;
            if (clanChange.estMembre(p.getUUID())) p.refreshTabListName();
        }
    }

    /** Variante : rafraichit aussi la liste Tab d'un joueur precis (qui vient de quitter un clan). */
    public static void diffuserTags(@Nullable Clan clanChange, @Nullable UUID exclure, @Nullable ServerPlayer aussi) {
        diffuserTags(clanChange, exclure);
        if (aussi != null) aussi.refreshTabListName();
    }

    // ------------------------------------------------------------------ construction du snapshot
    /** Partie du snapshot commune a tous les destinataires. */
    private record Base(List<ClanSnapshot.MembreVue> membres, EnumMap<Grade, Integer> perms, String symbole, long cdNom, long cdTag,
                        long cdDesc, long cdBan, List<ClanSnapshot.TerritoireVue> territoires, long resetJour, long resetSemaine, long now,
                        Map<UUID, ReglagesPerms> reglages) {
    }

    private static Base base(Clan c, MinecraftServer server, long now) {
        Economie eco = Economie.active();
        boolean ecoOk = eco.disponible();
        List<ClanSnapshot.MembreVue> membres = new ArrayList<>(c.membres.size());
        for (Membre m : c.membres.values()) {
            boolean enLigne = server.getPlayerList().getPlayer(m.uuid) != null;
            membres.add(ClanSnapshot.MembreVue.de(m, enLigne, ecoOk ? eco.solde(m.uuid) : 0));
        }
        EnumMap<Grade, Integer> perms = new EnumMap<>(Grade.class);
        for (Grade g : Grade.modifiables()) perms.put(g, Perm.masque(c.permsDe(g)));
        // reglages individuels (le chef n'en a jamais : il a tout)
        Map<UUID, ReglagesPerms> reglages = new HashMap<>();
        for (Membre m : c.membres.values()) {
            if (m.permsJoueur.isEmpty() || c.estChef(m.uuid)) continue;
            reglages.put(m.uuid, m.reglages());
        }
        long cooldown = ClansConfig.COOLDOWN_MODIF_H.get() * 3_600_000L;
        String symbole = eco.symbole();
        if (symbole.length() > 8) symbole = symbole.substring(0, 8);
        return new Base(membres, perms, symbole, reste(c.dernierChgtNom, cooldown, now), reste(c.dernierChgtTag, cooldown, now),
                reste(c.dernierChgtDesc, cooldown, now), reste(c.dernierChgtBanniere, cooldown, now), Territoires.tenusPar(c.id),
                Math.max(0, c.prochainResetJour - now), Math.max(0, c.prochainResetSemaine - now), now, reglages);
    }

    private static long reste(long dernier, long cooldown, long now) {
        return dernier <= 0 ? 0 : Math.max(0, dernier + cooldown - now);
    }

    /**
     * Partie personnelle : grade, solde, le journal de banque seulement avec la perm BANQUE_JOURNAL et les reglages
     * individuels de tous seulement avec GERER_PERMISSIONS (sinon les siens) : zero confiance.
     */
    private static ClanSnapshot pour(Base b, Clan c, ServerPlayer p) {
        UUID u = p.getUUID();
        Grade g = c.gradeDe(u);
        if (g == null) g = Grade.RECRUE;
        Economie eco = Economie.active();
        long monSolde = eco.disponible() ? eco.solde(u) : 0;
        List<ClanSnapshot.TerritoireVue> terr = b.territoires.size() > 16 ? b.territoires.subList(0, 16) : b.territoires;
        List<Mouvement> journalBanque = c.peut(u, Perm.BANQUE_JOURNAL) ? c.journalBanque : List.of();
        Map<UUID, ReglagesPerms> reglages;
        if (c.peut(u, Perm.GERER_PERMISSIONS)) reglages = b.reglages;
        else {
            ReglagesPerms miens = b.reglages.get(u);
            reglages = miens == null ? Map.of() : Map.of(u, miens);
        }
        return new ClanSnapshot(c.id, c.tag, c.nom, c.description, c.banniere, c.chef, c.creeLe, c.banque, c.niveau, c.xp,
                Niveaux.xpPour(c.niveau), c.membresMax(), c.territoiresMax(), b.membres, b.perms,
                u, g, monSolde, b.symbole, b.cdNom, b.cdTag, b.cdDesc, b.cdBan, journalBanque, c.quetesJour, c.quetesSemaine,
                b.resetJour, b.resetSemaine, terr, c.journal, b.now, reglages);
    }
}
