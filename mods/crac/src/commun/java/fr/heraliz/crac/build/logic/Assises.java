package fr.heraliz.crac.build.logic;

import fr.heraliz.crac.heraliz.Txt;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.PieceShapes;
import fr.heraliz.crac.build.geom.Poly;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.logic.Stability;
import fr.heraliz.crac.build.logic.StructureView;
import fr.heraliz.crac.build.logic.Supports;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.logic.Variants;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.heraliz.Heraliz;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.placable.PlacableSupport;
import fr.heraliz.crac.registry.ModBlocks;
import fr.heraliz.crac.registry.ModItems;
import fr.heraliz.crac.registry.ModParticles;
import fr.heraliz.crac.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Pièce sur laquelle on se tient au-dessus d'un bloc (corps d'origine de StructureService.seatUnder, sur une vue). */
public final class Assises {
    private Assises() {
    }

    /** StructureService.seatUnder over any view (the client mirror for the ghost preview). */
    @Nullable
    public static Piece seatUnder(StructureView view, BlockPos pos) {
        Map<Long, double[]> covered = new HashMap<>();
        Map<Long, Piece> best = new HashMap<>();
        for (Piece p : view.occupants(pos.below())) {
            PieceKey k = p.key();
            if (!k.kind().isWalkableSurface() || (k.h() & 1) != 0 || k.origin().getY() + k.h() / 2 * 3 != pos.getY()) {
                continue;
            }
            double x0 = (pos.getX() - k.origin().getX()) * 16.0;
            double z0 = (pos.getZ() - k.origin().getZ()) * 16.0;
            double[][] c = Poly.clipRect(Footprints.solidPolygon(k), x0, z0, x0 + 16, z0 + 16);
            double area = c == null ? 0 : Poly.area(c);
            if (area <= 0) continue;
            double[] acc = covered.computeIfAbsent(p.buildingId, id -> new double[2]);
            acc[0] += area;
            Piece cur = best.get(p.buildingId);
            if (cur == null || area > acc[1] + 1e-9 || (Math.abs(area - acc[1]) <= 1e-9 && p.id < cur.id)) {
                best.put(p.buildingId, p);
                acc[1] = area;
            }
        }
        for (Map.Entry<Long, double[]> e : covered.entrySet()) {
            // surfaces of one building at one h never overlap, so the areas add up to the covered part of the face
            if (e.getValue()[0] >= 256.0 - 1e-6) {
                return best.get(e.getKey());
            }
        }
        return null;
    }
}
