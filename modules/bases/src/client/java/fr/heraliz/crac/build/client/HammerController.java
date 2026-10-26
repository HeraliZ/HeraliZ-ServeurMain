package fr.heraliz.crac.build.client;

import java.util.List;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.net.C2SHammer;
import fr.heraliz.crac.build.net.C2SPieceInfo;
import fr.heraliz.crac.build.net.S2CPieceInfo;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.client.RaidBlockOverlay;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Building Hammer in hand: tracks the piece under the crosshair (and asks the server for its health), repairs while
 * left click is held, opens the upgrade wheel on right click (or the menu key).
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class HammerController {
    private static final long REFRESH_MS = 1000L;
    private static final long REQUEST_GAP_MS = C2SPieceInfo.COOLDOWN_MS + 30L;
    private static final long MESSAGE_MS = 1500L;

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
        while (mc.options.keyUse.consumeClick()) {
            // the wheel opens below while the button is held
        }
        if (mc.player.isSpectator()) {
            reset();
            return;
        }
        updateTarget(mc);
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
