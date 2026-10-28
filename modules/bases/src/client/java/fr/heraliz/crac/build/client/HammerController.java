package fr.heraliz.crac.build.client;

import java.util.List;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.net.C2SHammer;
import fr.heraliz.crac.build.net.C2SPieceInfo;
import fr.heraliz.crac.build.net.S2CPieceInfo;
import fr.heraliz.crac.block.Adosse;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.ClaimBlockHaut;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.network.C2SPickup;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.client.RaidBlockOverlay;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Building Hammer in hand: tracks the piece under the crosshair (and asks the server for its health), repairs while
 * left click is held, opens the upgrade wheel on right click (or the menu key). HeraliZ (28/10) : sur un objet posé
 * (caisse, coffre, armoire...), le clic droit le ramasse (s'il est vide ; l'armoire, peu après sa pose).
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class HammerController {
    private static final long REFRESH_MS = 1000L;
    private static final long REQUEST_GAP_MS = C2SPieceInfo.COOLDOWN_MS + 30L;
    private static final long MESSAGE_MS = 1500L;
    private static final long OBJET_GAP_MS = 350L;

    @Nullable
    private static Piece target;
    @Nullable
    private static BlockPos targetPos;
    private static long requestedId = -1L;
    private static long requestedAt;
    private static int progress;
    private static boolean autoRepair;
    @Nullable
    private static Component message;
    private static long messageAt;
    /** HeraliZ (28/10) : objet posé sous le viseur (à la place d'une pièce), et quand le dernier ramassage est parti. */
    @Nullable
    private static BlockPos objet;
    @Nullable
    private static BlockState objetEtat;
    private static long objetEnvoye;
    /** Distance de l'œil au point visé de la pièce {@link #target}. */
    private static double cibleDist = Double.MAX_VALUE;

    private HammerController() {
    }

    /** Piece under the crosshair while holding the hammer. */
    @Nullable
    public static Piece target() {
        return target;
    }

    @Nullable
    public static BlockPos targetPos() {
        return targetPos;
    }

    /** HeraliZ : l'objet posé visé au marteau (caisse, armoire...), ou null. */
    @Nullable
    public static BlockState objetEtat() {
        return objet == null ? null : objetEtat;
    }

    /** Repair progress 0..1, or 0 when not repairing. */
    public static float progress() {
        return progress <= 0 ? 0.0F : Math.min(1.0F, (float) progress / BuildClient.repairTicks());
    }

    @Nullable
    public static Component message() {
        return message != null && Util.getMillis() - messageAt < MESSAGE_MS ? message : null;
    }

    public static void reset() {
        target = null;
        targetPos = null;
        requestedId = -1L;
        progress = 0;
        autoRepair = false;
        objet = null;
        objetEtat = null;
    }

    /** Repair chosen in the wheel: one repair cycle runs without holding the button. */
    public static void startAutoRepair() {
        autoRepair = true;
        progress = 0;
    }

    public static void send(int action, Tier tier) {
        if (target != null && targetPos != null) {
            NetworkHandler.CHANNEL.sendToServer(new C2SHammer(action, targetPos, target.id, tier.ordinal()));
            requestedAt = Util.getMillis();
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) {
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    private static void say(String msg) {
        message = fr.heraliz.crac.heraliz.Txt.c(msg);
        messageAt = Util.getMillis();
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && BuildClient.holdingHammer(p) && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !BuildClient.holdingHammer(mc.player)) {
            if (target != null || autoRepair) {
                reset();
            }
            return;
        }
        if (mc.screen != null) {
            return;
        }
        while (mc.options.keyAttack.consumeClick()) {
            // repairing is driven by the held button below
        }
        boolean clicUse = false;
        while (mc.options.keyUse.consumeClick()) {
            // the wheel opens below while the button is held; on a placed object, a click picks it up
            clicUse = true;
        }
        if (mc.player.isSpectator()) {
            reset();
            return;
        }
        updateTarget(mc);
        updateObjet(mc);
        if (objet != null && clicUse && Util.getMillis() - objetEnvoye >= OBJET_GAP_MS) {
            objetEnvoye = Util.getMillis();
            NetworkHandler.CHANNEL.sendToServer(new C2SPickup(objet));
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        requestInfo();
        if (target != null && mc.options.keyUse.isDown() && RadialMenuScreen.mayOpen()) {
            mc.setScreen(new HammerRadialScreen(mc.options.keyUse.getKey(), target));
            return;
        }
        if (target != null && BuildKeys.MENU.isDown() && RadialMenuScreen.mayOpen()) {
            mc.setScreen(new HammerRadialScreen(BuildKeys.MENU.getKey(), target));
            return;
        }
        tickRepair(mc.player, mc.options.keyAttack.isDown());
    }

    private static void updateTarget(Minecraft mc) {
        Sockets.Aim aim = BuildClient.aim(mc, 1.0F);
        Piece p = aim.piece();
        if (p == null) {
            target = null;
            targetPos = null;
            progress = 0;
            autoRepair = false;
            return;
        }
        if (target == null || target.id != p.id) {
            progress = 0;
            autoRepair = false;
        }
        target = p;
        targetPos = aim.block();
        cibleDist = aim.hit() != null ? aim.hit().distanceTo(aim.eye()) : Double.MAX_VALUE;
    }

    /**
     * HeraliZ (28/10) : l'objet posé sous le viseur (le même que le clic droit ordinaire), s'il n'est pas derrière la
     * pièce visée. Une caisse ou une armoire adossée dépasse dans la case du mur : celle-ci renvoie à l'objet. Il passe
     * alors devant la pièce (ni roue ni réparation).
     */
    private static void updateObjet(Minecraft mc) {
        objet = null;
        objetEtat = null;
        if (mc.player.getAbilities().instabuild || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof StructureBlock) {
            Adosse.Voisin v = Adosse.contenant(mc.level, pos, hit.getLocation());
            if (v == null) {
                return;
            }
            pos = v.pos();
            state = v.state();
        }
        if (state.getBlock() instanceof ClaimBlockHaut) {
            pos = pos.below();
            state = mc.level.getBlockState(pos);
        }
        boolean armoire = state.getBlock() instanceof ClaimBlock;
        if (armoire ? fr.heraliz.crac.Config.claimMoveMinutes.get() <= 0 : !PlacableSet.containsClient(state.getBlock())) {
            return;
        }
        if (target != null && hit.getLocation().distanceTo(mc.player.getEyePosition()) > cibleDist + 0.1) {
            return;
        }
        objet = pos.immutable();
        objetEtat = state;
        target = null;
        targetPos = null;
        progress = 0;
        autoRepair = false;
    }

    private static void requestInfo() {
        if (target == null || targetPos == null) {
            return;
        }
        long now = Util.getMillis();
        boolean changed = target.id != requestedId;
        if ((changed && now - requestedAt >= REQUEST_GAP_MS) || now - requestedAt >= REFRESH_MS) {
            NetworkHandler.CHANNEL.sendToServer(new C2SPieceInfo(targetPos, target.id));
            requestedId = target.id;
            requestedAt = now;
        }
    }

    private static void tickRepair(LocalPlayer player, boolean held) {
        if (target == null || !(held || autoRepair)) {
            progress = 0;
            return;
        }
        PieceInfoCache.Entry entry = PieceInfoCache.get(target.id);
        if (entry == null) {
            progress = 0;
            return;
        }
        String problem = repairProblem(player, entry.info());
        if (problem != null) {
            say(problem);
            progress = 0;
            autoRepair = false;
            return;
        }
        progress++;
        if (progress >= BuildClient.repairTicks()) {
            send(C2SHammer.REPAIR, Tier.TWIG);
            progress = 0;
            autoRepair = false;
        }
    }

    /** Why a repair would be refused, as far as the client knows; null when it can go ahead. */
    @Nullable
    public static String repairProblem(LocalPlayer player, S2CPieceInfo info) {
        if (info.hp() >= info.maxHp()) {
            return "crac.hammer.full";
        }
        if (!info.privileged()) {
            return "crac.build.not_yours";
        }
        if (player.getAbilities().instabuild) {
            return null;
        }
        if (BuildClient.repairBlockedDuringRaid() && RaidBlockOverlay.ownBaseBlocked()) {
            return "crac.build.repair_raid";
        }
        List<BuildCost> cost = BuildClient.repairCost(Tier.byOrdinal(info.tier()));
        if (!BuildClient.has(player, cost)) {
            return fr.heraliz.crac.heraliz.Txt.k("crac.hammer.need", BuildClient.describe(cost));
        }
        return null;
    }
}
