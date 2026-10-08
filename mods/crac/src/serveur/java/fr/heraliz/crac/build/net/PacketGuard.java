package fr.heraliz.crac.build.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side checks shared by the building packets. A modified client can send anything: every field is
 * bounds-checked, targets must be loaded, near the player and in sight, and each action is rate limited per player.
 */
@Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PacketGuard {
    public enum Limit { PLACE, HAMMER, REPAIR, INFO }

    /** Creative players skip the reach rules of the service, but packets still may not reach farther than this. */
    private static final double CREATIVE_RANGE = 64.0;
    /** Extra blocks of slack between the player and the centre of a piece to place (a piece is up to 3 blocks wide). */
    public static final double KEY_SLACK = 6.0;
    /** Server-thread only (packets are handled through enqueueWork). */
    private static final Map<UUID, long[]> LAST = new HashMap<>();

    private PacketGuard() {
    }

    /** A living, non-spectator player in a server level. */
    public static boolean canAct(@Nullable ServerPlayer p) {
        return p != null && p.isAlive() && !p.isRemoved() && !p.isSpectator() && p.level() instanceof ServerLevel;
    }

    /** True when at least {@code minMillis} passed since the last accepted packet of that kind; records the new one. */
    public static boolean allow(ServerPlayer p, Limit limit, long minMillis) {
        long now = Util.getMillis();
        long[] last = LAST.computeIfAbsent(p.getUUID(), k -> new long[Limit.values().length]);
        if (last[limit.ordinal()] != 0 && now - last[limit.ordinal()] < minMillis) {
            return false;
        }
        last[limit.ordinal()] = now;
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    public static double reach() {
        return BuildConfig.reach.get();
    }

    /** {@code pos} is loaded, inside the build height and within {@code range} blocks of the player's eyes. */
    public static boolean near(ServerPlayer p, ServerLevel level, BlockPos pos, double range) {
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
            return false;
        }
        double r = p.getAbilities().instabuild ? Math.max(range, CREATIVE_RANGE) : range;
        return p.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= r * r;
    }

    /**
     * The key's centre is in a loaded block inside the build height and within {@code range} blocks of the player's
     * eyes (pieces are placed by their centre: a far anchor of a big piece still counts as near).
     */
    public static boolean near(ServerPlayer p, ServerLevel level, PieceKey key, double range) {
        Vec3 c = key.centreWorld();
        BlockPos pos = BlockPos.containing(c);
        if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos)) {
            return false;
        }
        double r = p.getAbilities().instabuild ? Math.max(range, CREATIVE_RANGE) : range;
        return p.getEyePosition().distanceToSqr(c) <= r * r;
    }

    /** The piece {@code id} if it really occupies {@code pos}, else null. */
    @Nullable
    public static Piece pieceAt(ServerLevel level, BlockPos pos, long id) {
        StructureStore store = StructureService.store(level);
        Piece p = store.index.piece(id);
        return p != null && store.index.occupants(pos).contains(p) ? p : null;
    }

    /** {@link #inSight(ServerLevel, Entity, Vec3, BlockPos, Piece)} from the player's eyes; creative players skip it. */
    public static boolean inSight(ServerPlayer p, ServerLevel level, BlockPos pos, Piece piece) {
        return p.getAbilities().instabuild || inSight(level, p, p.getEyePosition(), pos, piece);
    }

    /**
     * Whether {@code piece}, seen in block {@code pos}, is in sight from {@code eye}: a ray toward one of the piece's
     * own collision boxes in that block ({@link StructureService#sightPoints}) first hits the piece itself
     * ({@link StructureService#pieceAt(ServerLevel, BlockPos, Vec3)}); a miss never counts. It uses collision shapes
     * like the client's aim, and keeps a modified client from working on or inspecting pieces behind a wall, also when
     * pieces share a block (a floor behind a rotated wall).
     */
    public static boolean inSight(ServerLevel level, Entity viewer, Vec3 eye, BlockPos pos, Piece piece) {
        for (Vec3 target : StructureService.sightPoints(piece, pos)) {
            BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
            if (hit.getType() == HitResult.Type.BLOCK && StructureService.pieceAt(level, hit.getBlockPos(), hit.getLocation()) == piece) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a key written by {@link PieceKey#write}; null unless every field is in range for its kind (kind, canonical
     * orientation, lattice and height bounds, rotation: {@link PieceKey#isWellFormed}).
     */
    @Nullable
    public static PieceKey readKey(FriendlyByteBuf buf) {
        return PieceKey.read(buf);
    }
}
