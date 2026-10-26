package fr.heraliz.crac.event;

import fr.heraliz.crac.heraliz.Txt;
import fr.heraliz.crac.heraliz.ClanAccess;
import java.util.Collection;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.level.BlockEvent.BlockToolModificationEvent;
import net.minecraftforge.event.level.BlockEvent.FluidPlaceBlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Claim protection beyond placing and breaking: using blocks through walls, buckets, tools, bone meal, pistons,
 * flowing fluids and fire spread into someone else's base. Members of the claim's group and creative players are
 * exempt. Teleports into a claim are refused to members too, so that no way into a base is its owners' alone.
 */
@EventBusSubscriber(
    modid = "crac"
)
public final class ClaimProtectionHandler {
    /** How far past the clicked point the sight line runs, so the clicked face itself is always hit. */
    private static final double SIGHT_OVERSHOOT = 0.05;
    /** Where the rays of {@link #blockInSight} end inside the block: its centre and the middle of each face. */
    private static final double[][] SIGHT_POINTS = {
        {0.5, 0.5, 0.5}, {0.5, 0.5, 0.1}, {0.5, 0.5, 0.9}, {0.5, 0.1, 0.5}, {0.5, 0.9, 0.5}, {0.1, 0.5, 0.5}, {0.9, 0.5, 0.5}
    };

    private ClaimProtectionHandler() {
    }

    /** The claim at {@code pos} when it belongs to a group the player is not in, else null. */
    @Nullable
    private static Claim foreignClaim(ServerLevel level, Player player, BlockPos pos) {
        if (player.getAbilities().instabuild) {
            return null;
        }
        Claim claim = ClaimManager.getAt(level, pos);
        if (claim == null) {
            return null;
        }
        Group g = GroupData.get(level.getServer()).getById(claim.groupId());
        return ClanAccess.allows(g, player.getUUID(), ClanAccess.Action.ENTER) ? null : claim;
    }

    private static void deny(ServerPlayer player, String message) {
        player.displayClientMessage(Txt.red(message), true);
    }

    // ------------------------------------------------------------------ using blocks

    /**
     * The server only checks the reach of a block use, never what stands in between, so a modified client can open
     * a crate behind a wall. In a foreign claim the first block on the line from the eyes to the clicked point must
     * be the clicked block. Respawn blocks and blocks that hand out an item without a container are refused outright.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (foreignClaim(level, player, pos) == null) {
            return;
        }
        Block block = level.getBlockState(pos).getBlock();
        // HeraliZ: beds and respawn anchors are left alone (CRac never touches respawn points)
        boolean refused = block instanceof JukeboxBlock
            || block instanceof LecternBlock;
        if (refused) {
            event.setUseBlock(Result.DENY);
            event.setUseItem(Result.DENY);
            deny(player, "crac.claim.not_yours");
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (!inSight(level, player, pos, event.getHitVec().getLocation())) {
            event.setUseBlock(Result.DENY);
            event.setUseItem(Result.DENY);
            deny(player, "crac.reach.no_sight");
        }
    }

    /**
     * Coffres de la base de son clan (25/10) : le droit de clan « Ouvrir les coffres » (BASE_COFFRES, réglable par grade et
     * par membre) n'était vérifié nulle part pour les conteneurs posés (caisses, coffres, fours...) : une recrue sans ce
     * droit vidait les coffres de la base. Seuls les membres du clan propriétaire sont concernés : un pillard qui est entré
     * pille comme avant. L'armoire garde son propre contrôle. Libres : créatif, spectateur, cabane d'essai du tutoriel.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOpenContainer(RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
            || player.getAbilities().instabuild || player.isSpectator() || event.getUseBlock() == Result.DENY
            || fr.heraliz.crac.bases.MondeBases.estTuto(level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container)) {
            return;
        }
        Claim claim = ClaimManager.getAt(level, pos);
        if (claim == null) {
            return;
        }
        Group g = GroupData.get(level.getServer()).getById(claim.groupId());
        if (fr.heraliz.crac.heraliz.BasesClans.duClanSansDroit(level.getServer(), g, player.getUUID(), ClanAccess.Action.CHESTS)) {
            event.setUseBlock(Result.DENY);
            player.displayClientMessage(Component.translatableWithFallback("crac.claim.coffre_droit",
                "Ton clan ne t'a pas donné le droit « Ouvrir les coffres ».").withStyle(net.minecraft.ChatFormatting.RED), true);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** Whether nothing stands between the player's eyes and the point {@code hit} of the block at {@code target}. */
    public static boolean inSight(Level level, Player player, BlockPos target, Vec3 hit) {
        Vec3 eye = player.getEyePosition();
        Vec3 ray = hit.subtract(eye);
        double length = ray.length();
        if (length < 1.0E-4) {
            return true;
        }
        Vec3 end = hit.add(ray.scale(SIGHT_OVERSHOOT / length));
        BlockHitResult first = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return first.getType() == HitResult.Type.MISS || first.getBlockPos().equals(target);
    }

    /**
     * For packets that name a block but carry no hit point: whether the block at {@code pos} is in sight, i.e. a ray
     * from the eyes toward its centre or the middle of one of its faces gets there unobstructed or first hits one of
     * {@code targets} (the block itself, or the other half of a bed).
     */
    public static boolean blockInSight(Level level, Player player, BlockPos pos, Collection<BlockPos> targets) {
        Vec3 eye = player.getEyePosition();
        for (double[] s : SIGHT_POINTS) {
            Vec3 point = new Vec3(pos.getX() + s[0], pos.getY() + s[1], pos.getZ() + s[2]);
            BlockHitResult first = level.clip(new ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (first.getType() == HitResult.Type.MISS || targets.contains(first.getBlockPos())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ buckets, tools, bone meal

    /**
     * A bucket answers the denied block click with a second "use item" packet that places (or picks up) the fluid
     * anyway; this is where that second step is refused.
     */
    @SubscribeEvent
    public static void onFillBucket(FillBucketEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level) || player.getAbilities().instabuild) {
            return;
        }
        if (!(event.getTarget() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos clicked = hit.getBlockPos();
        BlockPos placePos = clicked.relative(hit.getDirection());
        String problem = BuildEventHandler.useProblem(level, player, clicked, placePos, false);
        if (problem != null) {
            event.setCanceled(true);
            deny(player, problem);
            player.connection.send(new ClientboundBlockUpdatePacket(level, clicked));
            player.connection.send(new ClientboundBlockUpdatePacket(level, placePos));
            player.containerMenu.broadcastFullState();
        }
    }

    /** Stripping, tilling, path making or scraping a block in a foreign claim. */
    @SubscribeEvent
    public static void onToolModification(BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
            && event.getLevel() instanceof ServerLevel level
            && foreignClaim(level, player, event.getPos()) != null) {
            event.setCanceled(true);
            if (!event.isSimulated()) {
                deny(player, "crac.claim.not_yours");
            }
        }
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && event.getLevel() instanceof ServerLevel level
            && foreignClaim(level, player, event.getPos()) != null) {
            event.setCanceled(true);
            deny(player, "crac.claim.not_yours");
        }
    }

    // ------------------------------------------------------------------ pistons, fluids and fire

    /** A piston may only move, break or reach blocks of its own claim (or outside any claim). */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean extend = event.getPistonMoveType().isExtend;
        if (!extend && !level.getBlockState(event.getPos()).is(Blocks.STICKY_PISTON)) {
            // a plain piston pulls nothing back
            return;
        }
        Claim own = ClaimManager.getAt(level, event.getPos());
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null) {
            return;
        }
        boolean canMove = resolver.resolve();
        if (extend && crossesInto(level, own, event.getFaceOffsetPos())) {
            event.setCanceled(true);
            return;
        }
        if (!canMove) {
            return;
        }
        for (BlockPos p : resolver.getToPush()) {
            if (crossesInto(level, own, p) || crossesInto(level, own, p.relative(resolver.getPushDirection()))) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos p : resolver.getToDestroy()) {
            if (crossesInto(level, own, p)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Whether {@code pos} lies in a claim other than {@code own}. */
    private static boolean crossesInto(ServerLevel level, @Nullable Claim own, BlockPos pos) {
        Claim c = ClaimManager.getAt(level, pos);
        return c != null && c != own;
    }

    /**
     * Whether a fluid may flow from {@code from} into {@code to}: never into a claim from outside it, so lava or water
     * emptied at the edge cannot flood a base. Called by {@code FlowingFluidMixin} for every spread.
     */
    public static boolean fluidMayEnter(ServerLevel level, BlockPos from, BlockPos to) {
        Claim target = ClaimManager.getAt(level, to);
        return target == null || target.contains(from);
    }

    /** Same rule for fire spreading from {@code from} to {@code to} (FireBlockMixin): it stays out of claims it is not in. */
    public static boolean fireMaySpread(ServerLevel level, BlockPos from, BlockPos to) {
        return fluidMayEnter(level, from, to);
    }

    /** Lava outside a claim does not set fire to what is inside it. */
    @SubscribeEvent
    public static void onFluidPlace(FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getNewState().getBlock() instanceof BaseFireBlock)) {
            return;
        }
        Claim target = ClaimManager.getAt(level, event.getPos());
        if (target != null && target != ClaimManager.getAt(level, event.getLiquidPos())) {
            event.setNewState(event.getOriginalState());
        }
    }

    // ------------------------------------------------------------------ teleports

    /**
     * Chorus fruit picks a random spot within 8 blocks and drops to the ground below it, through roofs and walls.
     * A target above the footprint of a claim (and not below its floor) is refused, members included.
     */
    @SubscribeEvent
    public static void onChorusFruit(EntityTeleportEvent.ChorusFruit event) {
        if (!(event.getEntityLiving() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level) || player.getAbilities().instabuild) {
            return;
        }
        for (Claim c : ClaimData.get(level).all()) {
            boolean above = Math.abs(event.getTargetX() - (c.anchor().getX() + 0.5)) <= c.radius() + 0.5
                && Math.abs(event.getTargetZ() - (c.anchor().getZ() + 0.5)) <= c.radius() + 0.5
                && event.getTargetY() >= c.anchor().getY() - c.yRadius();
            if (above) {
                event.setCanceled(true);
                deny(player, "crac.claim.teleport");
                return;
            }
        }
    }

    /**
     * An ender pearl landing in a claim does not teleport, members included: it slips through gaps no player fits in,
     * and a base whose only way in takes a pearl would be one raiders cannot enter.
     */
    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        ServerPlayer player = event.getPlayer();
        if (player.level() instanceof ServerLevel level && !player.getAbilities().instabuild
            && ClaimManager.getAt(level, BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ())) != null) {
            event.setCanceled(true);
            deny(player, "crac.claim.teleport");
        }
    }
}
