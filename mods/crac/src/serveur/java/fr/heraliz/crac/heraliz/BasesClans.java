package fr.heraliz.crac.heraliz;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.data.SafeSavedData;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Une base par clan (CRac 1.16.0, demande du propriétaire du 21/10) : sans clan, pas de base. Chaque clan qui pose une
 * armoire a son groupe CRac, reflet exact du clan : mêmes membres, son chef pour propriétaire, tenu à jour toutes les
 * 5 secondes ({@link #seconde}) ; un groupe ne peut tenir qu'un claim, donc une seule base par clan. Personne hors du
 * clan n'y a accès ; dans le clan, les droits des grades et des joueurs (BASE_ENTRER, BASE_CONSTRUIRE…) décident
 * ({@link ClanAccess#allows}). Un joueur qui quitte le clan perd l'accès tout de suite. Clan dissous : l'armoire disparaît
 * et le claim est libéré : la base n'appartient plus à personne, tout le monde ouvre ses portes, la pille, et peut y
 * poser sa propre armoire. Gardé avec le monde principal ({@code data/crac_bases_clans.dat}). Fil du serveur seulement ;
 * HeraliZ Clans n'est touché que par {@link Pont}, et seulement s'il est là.
 */
public final class BasesClans extends SafeSavedData {
    public static final String ID = "crac_bases_clans";

    /** Clan -> son groupe CRac. */
    private final Map<UUID, UUID> groupes = new HashMap<>();
    /** Clans déjà vus (création d'un clan : tutoriel des bases pour son chef). */
    private final Set<UUID> vus = new HashSet<>();
    private boolean premierPassage = true;
    private static int ticks;

    public static BasesClans get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BasesClans::load,
            () -> SafeSavedData.fresh(server.overworld(), ID, new BasesClans()), ID);
    }

    /** HeraliZ Clans est là : les bases sont celles des clans. */
    public static boolean actif() {
        return ClanAccess.clansLoaded();
    }

    /** Le groupe est celui d'un clan (sa base suit le clan). */
    public static boolean estGroupeClan(MinecraftServer server, @Nullable UUID groupe) {
        return groupe != null && get(server).groupes.containsValue(groupe);
    }

    @Nullable
    public static UUID clanDuGroupe(MinecraftServer server, @Nullable UUID groupe) {
        if (groupe == null) {
            return null;
        }
        for (Map.Entry<UUID, UUID> e : get(server).groupes.entrySet()) {
            if (groupe.equals(e.getValue())) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Le groupe CRac du clan du joueur, créé à la demande (pose de l'armoire, tutoriel) ; null sans clan ou sans HeraliZ
     * Clans. Le joueur quitte d'abord un ancien groupe qui n'est pas celui d'un clan (les groupes à la main n'existent plus).
     */
    @Nullable
    public static Group groupe(MinecraftServer server, UUID joueur) {
        if (!actif()) {
            return null;
        }
        try {
            return Pont.groupe(server, joueur);
        } catch (LinkageError | RuntimeException e) {
            Crac.LOGGER.warn("[CRac] bases des clans : groupe du clan introuvable", e);
            return null;
        }
    }

    /** Le joueur est dans un clan. */
    public static boolean aUnClan(UUID joueur) {
        if (!actif()) {
            return false;
        }
        try {
            return Pont.clan(joueur) != null;
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** Le joueur peut gérer la base de son clan (pose de l'armoire, agrandissement) : chef, ou droit BASE_GERER. */
    public static boolean peutGerer(UUID joueur) {
        if (!actif()) {
            return false;
        }
        try {
            return Pont.perm(joueur, ClanAccess.Action.MANAGE);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /**
     * Le joueur peut faire cette action dans la base de ce groupe de clan : membre du clan avec le droit voulu (le chef
     * toujours). Faux pour un groupe qui n'est pas celui d'un clan.
     */
    static boolean permis(MinecraftServer server, Group groupe, UUID joueur, ClanAccess.Action action) {
        UUID clan = clanDuGroupe(server, groupe.id());
        if (clan == null) {
            return false;
        }
        try {
            return clan.equals(Pont.clan(joueur)) && Pont.perm(joueur, action);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** Toutes les 5 secondes : groupes des clans remis à l'image des clans, clans dissous, nouveaux clans. */
    public static void seconde(MinecraftServer server) {
        if (!actif() || ++ticks % 5 != 0) {
            return;
        }
        try {
            Pont.synchroniser(server, get(server));
        } catch (LinkageError | RuntimeException e) {
            Crac.LOGGER.error("[CRac] bases des clans : mise à jour en erreur", e);
        }
    }

    /**
     * Clan dissous : son armoire est retirée (sans rien faire tomber) et son claim libéré ; les pièces restent, à tout le
     * monde ; son groupe disparaît.
     */
    static void liberer(MinecraftServer server, BasesClans d, UUID clan) {
        UUID gid = d.groupes.remove(clan);
        d.setDirty();
        if (gid == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Claim c = ClaimData.get(level).getByGroup(gid);
            if (c == null) {
                continue;
            }
            BlockPos a = c.anchor();
            ClaimManager.releaseClaim(level, gid);
            level.removeBlock(a.above(), false);
            level.removeBlock(a, false);
            C2SRequestNearbyClaimsTraitement.pushAll(level);
            Crac.LOGGER.info("[CRac] clan {} dissous : armoire de sa base retirée en {} {}, base libre", clan, level.dimension().location(), a);
        }
        GroupData data = GroupData.get(server);
        Group g = data.getById(gid);
        if (g != null) {
            data.disband(g);
        }
    }

    public static BasesClans load(CompoundTag tag) {
        BasesClans d = new BasesClans();
        ListTag l = tag.getList("Clans", 10);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            if (t.hasUUID("Clan") && t.hasUUID("Groupe")) {
                d.groupes.put(t.getUUID("Clan"), t.getUUID("Groupe"));
            }
        }
        ListTag v = tag.getList("Vus", 10);
        for (int i = 0; i < v.size(); i++) {
            if (v.getCompound(i).hasUUID("Clan")) {
                d.vus.add(v.getCompound(i).getUUID("Clan"));
            }
        }
        d.premierPassage = d.vus.isEmpty();
        return d;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, UUID> e : this.groupes.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Clan", e.getKey());
            t.putUUID("Groupe", e.getValue());
            l.add(t);
        }
        tag.put("Clans", l);
        ListTag v = new ListTag();
        for (UUID c : this.vus) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Clan", c);
            v.add(t);
        }
        tag.put("Vus", v);
        return tag;
    }

    /** La seule classe qui nomme les types de HeraliZ Clans : chargée au premier appel, qui n'a lieu que s'il est là. */
    private static final class Pont {
        private Pont() {
        }

        @Nullable
        static UUID clan(UUID joueur) {
            return fr.heralisz.clans.api.ClansApi.clanDe(joueur).orElse(null);
        }

        static boolean perm(UUID joueur, ClanAccess.Action action) {
            Optional<UUID> clan = fr.heralisz.clans.api.ClansApi.clanDe(joueur);
            if (clan.isEmpty()) {
                return false;
            }
            Optional<fr.heralisz.clans.api.ClanInfo> info = fr.heralisz.clans.api.ClansApi.infos(clan.get());
            if (info.isPresent() && joueur.equals(info.get().chef())) {
                return true;
            }
            fr.heralisz.clans.model.Perm p = switch (action) {
                case ENTER -> fr.heralisz.clans.model.Perm.BASE_ENTRER;
                case BUILD -> fr.heralisz.clans.model.Perm.BASE_CONSTRUIRE;
                case BREAK -> fr.heralisz.clans.model.Perm.BASE_CASSER;
                case DOORS -> fr.heralisz.clans.model.Perm.BASE_PORTES;
                case CHESTS -> fr.heralisz.clans.model.Perm.BASE_COFFRES;
                case MANAGE -> fr.heralisz.clans.model.Perm.BASE_GERER;
            };
            return fr.heralisz.clans.api.ClansApi.aPerm(joueur, clan.get(), p);
        }

        @Nullable
        static Group groupe(MinecraftServer server, UUID joueur) {
            Optional<UUID> clan = fr.heralisz.clans.api.ClansApi.clanDe(joueur);
            if (clan.isEmpty()) {
                return null;
            }
            Optional<fr.heralisz.clans.api.ClanInfo> info = fr.heralisz.clans.api.ClansApi.infos(clan.get());
            if (info.isEmpty()) {
                return null;
            }
            BasesClans d = get(server);
            GroupData data = GroupData.get(server);
            UUID gid = d.groupes.get(clan.get());
            Group g = gid == null ? null : data.getById(gid);
            if (g == null) {
                fr.heralisz.clans.api.ClanInfo i = info.get();
                UUID chef = i.chef();
                quitterAncien(data, chef, null);
                String tag = i.tag();
                if (tag != null && data.tagTaken(tag)) {
                    tag = null;
                }
                g = data.create(i.nom(), tag, i.banniere().accent() & 0xFFFFFF, chef);
                if (g == null) {
                    return null;
                }
                d.groupes.put(clan.get(), g.id());
                d.setDirty();
                Crac.LOGGER.info("[CRac] base du clan [{}] {} : groupe {}", i.tag(), i.nom(), g.id());
            }
            mettreAJour(data, g, info.get());
            return g;
        }

        /** Le joueur quitte un groupe qui n'est pas {@code garder} (groupe fait à la main, ou d'un autre clan). */
        static void quitterAncien(GroupData data, UUID joueur, @Nullable Group garder) {
            Group autre = data.getByMember(joueur);
            if (autre == null || autre == garder) {
                return;
            }
            if (autre.isOwner(joueur)) {
                if (autre.members().size() <= 1) {
                    data.disband(autre);
                    return;
                }
                for (UUID m : autre.members()) {
                    if (!m.equals(joueur)) {
                        data.transferOwnership(autre, m);
                        break;
                    }
                }
            }
            data.removeMember(autre, joueur);
        }

        /** Membres du groupe = membres du clan, propriétaire = chef. */
        static void mettreAJour(GroupData data, Group g, fr.heralisz.clans.api.ClanInfo info) {
            Set<UUID> clan = info.membres().keySet();
            for (UUID m : Set.copyOf(g.members())) {
                if (!clan.contains(m) && !m.equals(g.owner())) {
                    data.removeMember(g, m);
                }
            }
            for (UUID m : clan) {
                if (!g.isMember(m)) {
                    quitterAncien(data, m, g);
                    data.addMember(g, m);
                }
            }
            UUID chef = info.chef();
            if (chef != null && g.isMember(chef) && !g.isOwner(chef)) {
                UUID ancien = g.owner();
                data.transferOwnership(g, chef);
                if (!clan.contains(ancien)) {
                    data.removeMember(g, ancien);
                }
            } else if (!clan.contains(g.owner()) && chef != null && g.isMember(chef)) {
                data.transferOwnership(g, chef);
            }
        }

        static void synchroniser(MinecraftServer server, BasesClans d) {
            GroupData data = GroupData.get(server);
            Set<UUID> existants = new HashSet<>();
            for (fr.heralisz.clans.api.ClanInfo info : fr.heralisz.clans.api.ClansApi.tous()) {
                existants.add(info.id());
                UUID gid = d.groupes.get(info.id());
                Group g = gid == null ? null : data.getById(gid);
                if (g != null) {
                    mettreAJour(data, g, info);
                }
                if (d.vus.add(info.id())) {
                    d.setDirty();
                    if (!d.premierPassage) {
                        ServerPlayer chef = info.chef() == null ? null : server.getPlayerList().getPlayer(info.chef());
                        if (chef != null) {
                            fr.heraliz.crac.bases.TutorielApi.clanCree(chef);
                        }
                    }
                }
            }
            d.premierPassage = false;
            for (UUID clan : Set.copyOf(d.groupes.keySet())) {
                if (!existants.contains(clan)) {
                    liberer(server, d, clan);
                }
            }
            if (d.vus.retainAll(existants)) {
                d.setDirty();
            }
        }
    }
}
