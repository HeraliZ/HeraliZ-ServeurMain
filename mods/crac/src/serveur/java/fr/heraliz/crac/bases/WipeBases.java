package fr.heraliz.crac.bases;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.admin.AdminManager;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.BasesClans;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacablePositionData;
import fr.heraliz.crac.raid.PlacedTerrainData;
import fr.heraliz.crac.raid.RaidBlockData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Remise à zéro du serveur ({@code /wipe serveur} de HeraliZ Quêtes, par {@link fr.heraliz.crac.api.WipeApi}) : toutes
 * les bases de tous les mondes disparaissent, contenu compris, rien ne tombe au sol : claims (armoire, pièces, objets
 * posés, blocs de terrain posés par les joueurs), bâtiments sans claim, objets posés un jour dans un claim, cœurs de clan,
 * groupes, homes, tutoriels, protections, accès, raid blocks. Les objets à vie trouvés dans les conteneurs (critère
 * {@code garder}) sont rendus : propriétaire du groupe (base) ou de la pièce, sinon perdus. Ne touche à aucun autre bloc
 * de la carte (les décors du staff ne sont pas des pièces de CRac).
 */
public final class WipeBases {
    private WipeBases() {
    }

    /** {pièces, claims, objets posés, cœurs} ; {@code rendus} reçoit les objets à vie à rendre, par joueur. */
    public static int[] tout(MinecraftServer server, Predicate<ItemStack> garder, Map<UUID, List<ItemStack>> rendus) {
        int pieces = 0;
        int claims = 0;
        int poses = 0;
        int coeurs = BasesClans.toutOublier(server);
        GroupData groupes = GroupData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            // 1. les claims (armoires) et tout ce qu'il y a dedans
            for (Claim c : new ArrayList<>(ClaimData.get(level).all())) {
                Group g = groupes.getById(c.groupId());
                UUID proprio = g == null ? null : g.owner();
                BlockPos a = c.anchor();
                BlockPos min = new BlockPos(a.getX() - c.radius(), Math.max(level.getMinBuildHeight(), a.getY() - c.yRadius()), a.getZ() - c.radius());
                BlockPos max = new BlockPos(a.getX() + c.radius(), Math.min(level.getMaxBuildHeight() - 1, a.getY() + c.yRadius()), a.getZ() + c.radius());
                Set<Piece> tout = new LinkedHashSet<>(AdminManager.piecesIn(level, min, max));
                Building b = StructureService.buildingOfClaim(level, a);
                if (b != null) {
                    tout.addAll(StructureStore.get(level).piecesOf(b));
                }
                vider(level, min, max, proprio, garder, rendus);
                pieces += EffacerBase.retirer(level, tout);
                poses += AdminManager.removePlacables(level, min, max);
                terrain(level, min, max);
                level.removeBlock(a.above(), false);
                level.removeBlock(a, false);
                ClaimManager.releaseClaim(level, c.groupId());
                EffacerBase.ramasser(level, min, max);
                claims++;
            }
            // 2. les bâtiments restés sans claim (bases libérées, cabanes du tutoriel, essais)
            StructureStore store = StructureStore.get(level);
            Map<Long, List<Piece>> batiments = new LinkedHashMap<>();
            for (Piece p : new ArrayList<>(store.index.allPieces())) {
                batiments.computeIfAbsent(p.buildingId, k -> new ArrayList<>()).add(p);
            }
            for (List<Piece> ps : batiments.values()) {
                AABB zone = null;
                for (Piece p : ps) {
                    AABB box = StructureService.bounds(p);
                    if (box != null) {
                        zone = zone == null ? box : zone.minmax(box);
                    }
                }
                if (zone != null) {
                    BlockPos min = BlockPos.containing(zone.minX, zone.minY, zone.minZ);
                    BlockPos max = BlockPos.containing(zone.maxX, zone.maxY + 3, zone.maxZ);
                    vider(level, min, max, ps.get(0).owner, garder, rendus);
                    pieces += EffacerBase.retirer(level, ps);
                    poses += AdminManager.removePlacables(level, min, max);
                    EffacerBase.ramasser(level, min, max);
                } else {
                    pieces += EffacerBase.retirer(level, ps);
                }
            }
            // 3. les objets posés un jour dans un claim (portes, lits, coffres...) encore debout ailleurs
            PlacablePositionData pp = PlacablePositionData.get(level);
            for (long packed : pp.snapshot()) {
                BlockPos pos = BlockPos.of(packed);
                if (!pp.wasClaimed(pos)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof Container ct) {
                    garderDe(ct, null, garder, rendus);
                    ct.clearContent();
                    be.setChanged();
                }
                if (PlacableHpManager.isTracked(level.getBlockState(pos))) {
                    level.removeBlock(pos, false);
                    poses++;
                }
                PlacableHpManager.clear(level, pos);
                EffacerBase.ramasser(level, pos, pos);
            }
            RaidBlockData.get(level).clear();
            C2SRequestNearbyClaimsTraitement.pushAll(level);
        }
        // groupes (reflets des clans), données des joueurs et des bases
        for (Group g : new ArrayList<>(groupes.all())) {
            groupes.disband(g);
        }
        BasesDonnees.get(server).toutEffacer();
        AccesBase.get(server).toutEffacer();
        Protections.majTextes(server);
        Crac.LOGGER.info("[CRac] remise à zéro du serveur : {} pièces, {} claims, {} objets posés, {} cœurs retirés", pieces, claims, poses, coeurs);
        return new int[]{pieces, claims, poses, coeurs};
    }

    /** Blocs de terrain posés par les joueurs (terre, sable...) dans la zone : retirés. */
    private static void terrain(ServerLevel level, BlockPos min, BlockPos max) {
        PlacedTerrainData d = PlacedTerrainData.get(level);
        for (long packed : d.snapshot()) {
            BlockPos p = BlockPos.of(packed);
            if (p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ()) {
                level.removeBlock(p, false);
                d.remove(p);
            }
        }
    }

    /** Conteneurs de la zone vidés (leurs objets à vie mis de côté pour {@code proprio}) avant d'être retirés. */
    private static void vider(ServerLevel level, BlockPos min, BlockPos max, @Nullable UUID proprio, Predicate<ItemStack> garder,
                              Map<UUID, List<ItemStack>> rendus) {
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                for (BlockEntity be : new ArrayList<>(level.getChunk(cx, cz).getBlockEntities().values())) {
                    BlockPos q = be.getBlockPos();
                    if (be instanceof Container c && q.getX() >= min.getX() && q.getX() <= max.getX() && q.getY() >= min.getY()
                        && q.getY() <= max.getY() && q.getZ() >= min.getZ() && q.getZ() <= max.getZ()) {
                        garderDe(c, proprio, garder, rendus);
                        c.clearContent();
                        be.setChanged();
                    }
                }
            }
        }
    }

    private static void garderDe(Container c, @Nullable UUID proprio, Predicate<ItemStack> garder, Map<UUID, List<ItemStack>> rendus) {
        if (proprio == null) {
            return;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && garder.test(s)) {
                rendus.computeIfAbsent(proprio, k -> new ArrayList<>()).add(s.copy());
            }
        }
    }
}
