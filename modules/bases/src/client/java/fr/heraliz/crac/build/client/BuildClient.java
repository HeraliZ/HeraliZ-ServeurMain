package fr.heraliz.crac.build.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.logic.Stability;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.ClientMirror;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.item.BuildingHammerItem;
import fr.heraliz.crac.item.BuildingPlanItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Shared client helpers of the building UI: held tools, synced server config, aiming, costs. */
@OnlyIn(Dist.CLIENT)
public final class BuildClient {
    private static final Map<String, List<BuildCost>> COSTS = new HashMap<>();
    private static long costsAt;

    private BuildClient() {
    }

    public static boolean holdingPlan(Player p) {
        return p.getMainHandItem().getItem() instanceof BuildingPlanItem;
    }

    public static boolean holdingHammer(Player p) {
        return p.getMainHandItem().getItem() instanceof BuildingHammerItem;
    }

    // ------------------------------------------------------------------ server config (synced on login)

    private static boolean config() {
        return BuildConfig.SPEC.isLoaded();
    }

    public static double reach() {
        return config() ? BuildConfig.reach.get() : 8.0;
    }

    /** Distance d'amélioration au marteau (HeraliZ, 27/10, {@code upgradeReach}), plus courte que la portée de pose. */
    public static double upgradeReach() {
        return config() ? BuildConfig.upgradeReach.get() : 4.0;
    }

    public static Stability.Params stability() {
        return config() ? BuildConfig.stability() : Stability.Params.DEFAULT;
    }

    public static int maxSkirt() {
        return config() ? BuildConfig.foundationMaxHeight.get() : 3;
    }

    public static int maxPiecesWithoutClaim() {
        return config() ? BuildConfig.maxPiecesWithoutClaim.get() : 1;
    }

    public static int repairTicks() {
        return Math.max(1, (int) Math.round((config() ? BuildConfig.repairSeconds.get() : 3.0) * 20.0));
    }

    public static boolean repairBlockedDuringRaid() {
        return !config() || BuildConfig.repairBlockedDuringRaid.get();
    }

    // ------------------------------------------------------------------ aiming

    /**
     * Camera ray up to the building reach (§9.1). One walk along the ray finds the first terrain block (any block that
     * is not a structure block, vanilla collider shapes) and the structure blocks whose collision shape it enters
     * before it; {@link Sockets#aim} then intersects the ray exactly with the solids of the mirrored pieces up to the
     * terrain, the rasters only standing for what the solids do not know.
     */
    public static Sockets.Aim aim(Minecraft mc, float partialTick) {
        Player player = mc.player;
        Level level = mc.level;
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        double reach = reach();
        Vec3 end = eye.add(look.scale(reach));
        ClipContext ctx = new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        List<Sockets.BlockHit> structures = new ArrayList<>();
        Sockets.BlockHit terrain = BlockGetter.traverseBlocks(eye, end, ctx, (c, pos) -> {
            BlockState state = level.getBlockState(pos);
            BlockHitResult r = level.clipWithInteractionOverride(eye, end, pos, c.getBlockShape(state, level, pos), state);
            if (r == null) {
                return null;
            }
            Sockets.BlockHit hit = new Sockets.BlockHit(pos.immutable(), r.getLocation(), r.getDirection());
            if (state.getBlock() instanceof StructureBlock) {
                // an eye inside a block raster (noclip) would aim at that block whatever the look: skip it; the exact
                // solids decide what is hit in a structure block: keep walking to the terrain
                if (!r.isInside()) {
                    structures.add(hit);
                }
                return null;
            }
            return hit;
        }, c -> null);
        return Sockets.aim(ClientMirror.view(), eye, look, reach, terrain, structures);
    }

    // ------------------------------------------------------------------ costs

    private static List<BuildCost> cached(String key, Supplier<List<BuildCost>> parse) {
        long now = Util.getMillis();
        if (now - costsAt > 2000L) {
            COSTS.clear();
            costsAt = now;
        }
        return COSTS.computeIfAbsent(key, k -> parse.get());
    }

    public static List<BuildCost> placeCost(PieceKind kind) {
        return config() ? cached("p" + kind.id, () -> Costs.place(kind)) : List.of();
    }

    public static List<BuildCost> upgradeCost(PieceKind kind, Tier tier) {
        return config() ? cached("u" + kind.id + tier.id, () -> Costs.upgrade(kind, tier)) : List.of();
    }

    public static List<BuildCost> repairCost(Tier tier) {
        return config() ? cached("r" + tier.id, () -> Costs.repair(tier)) : List.of();
    }

    public static boolean has(Player p, List<BuildCost> cost) {
        return Costs.has(p, cost);
    }

    /** Count of matching items in the main inventory (what the server counts). */
    public static int count(Player p, BuildCost need) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (need.matches(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** "16x Stick, 4x Nail", or "free". */
    public static String describe(List<BuildCost> cost) {
        return Costs.describe(cost).getString();
    }

    // ------------------------------------------------------------------ names

    public static Component pieceName(PieceKind kind) {
        return Component.translatable("piece.crac." + kind.id);
    }

    public static Component tierName(Tier tier) {
        return Component.translatable("tier.crac." + tier.id);
    }
}
