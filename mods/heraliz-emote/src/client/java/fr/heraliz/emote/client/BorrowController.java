package fr.heraliz.emote.client;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.HeralizEmote;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayer;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import io.github.kosmx.emotes.main.network.ClientEmotePlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Borrowing: look at a player who plays an emote and press the borrow key (Shift + right click by
 * default) to play the same emote. Holding Shift makes you crouch, and Emotecraft stops emotes of
 * players who are not standing, so the borrowed emote waits until you stand up.
 */
public final class BorrowController {
    /** Two use clicks closer than this are the same held button repeating. */
    private static final int REPEAT_TICKS = 5;

    private static long clientTicks;
    private static boolean keyWasDown;
    private static long lastUseAttempt = -100;
    private static boolean lastUseBorrowed;

    @Nullable
    private static KeyframeAnimation pending;
    @Nullable
    private static UUID pendingFrom;
    private static int pendingTick;
    private static long pendingAt;

    private BorrowController() {
    }

    static void tick() {
        clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        if (!HeralizConfig.BORROW_ENABLED.get() || mc.player == null || mc.level == null) {
            pending = null;
            keyWasDown = false;
            return;
        }
        // bindings other than the use button (the default shares it, see onInteraction)
        boolean down = mc.screen == null && HeralizKeys.keyHeld(HeralizKeys.BORROW) && HeralizKeys.modifierHeld(HeralizKeys.BORROW);
        if (down && !keyWasDown && !HeralizKeys.sharesUseKey(HeralizKeys.BORROW)) {
            tryBorrow(mc);
        }
        keyWasDown = down;
        // presses are read above; empty the click counter so it does not pile up
        while (HeralizKeys.BORROW.consumeClick()) {
            // nothing per click
        }
        tickPending(mc);
    }

    /** Shift + right click: borrow instead of using the held item, only when there is something to borrow. */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !HeralizConfig.BORROW_ENABLED.get()) return;
        if (!HeralizKeys.sharesUseKey(HeralizKeys.BORROW) || !HeralizKeys.modifierHeld(HeralizKeys.BORROW)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean repeat = clientTicks - lastUseAttempt <= REPEAT_TICKS;
        lastUseAttempt = clientTicks;
        if (!repeat) lastUseBorrowed = tryBorrow(mc);
        if (lastUseBorrowed) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** What the borrow key does. Public for the CI scenario. */
    public static boolean tryBorrow(Minecraft mc) {
        LocalPlayer self = mc.player;
        if (self == null || self.isDeadOrDying()) return false;
        if (HeralizConfig.EMPTY_HAND_ONLY.get() && !self.getMainHandItem().isEmpty()) return false;
        AbstractClientPlayer target = lookedAtPlayer(self);
        return target != null && borrowFrom(target);
    }

    /** Queues the emote {@code target} is playing. */
    private static boolean borrowFrom(AbstractClientPlayer target) {
        Minecraft mc = Minecraft.getInstance();
        IEmotePlayer playing = playingEmote(target);
        if (playing == null || playing.getData() == null) {
            message(mc, "heraliz_emote.borrow.no_emote", target.getDisplayName());
            return false;
        }
        boolean withSound = HeralizConfig.BORROW_SOUND.get() ^ reverseSoundHeld();
        KeyframeAnimation animation = playing.getData();
        pending = withSound ? animation : mute(animation);
        pendingFrom = target.getUUID();
        pendingTick = playing.getTick();
        pendingAt = clientTicks;
        message(mc, "heraliz_emote.borrow.success", target.getDisplayName());
        return true;
    }

    public static boolean hasPending() {
        return pending != null;
    }

    private static void tickPending(Minecraft mc) {
        if (pending == null) return;
        LocalPlayer self = mc.player;
        if (self == null || clientTicks - pendingAt > HeralizConfig.HOLD_SECONDS.get() * 20L) {
            pending = null;
            return;
        }
        if (self.getPose() != Pose.STANDING) return; // still crouching: Emotecraft would stop it at once
        KeyframeAnimation animation = pending;
        pending = null;
        play(self, animation, startTick(mc, animation));
    }

    /** Where to start: in step with the other player for looping emotes, from the beginning otherwise. */
    private static int startTick(Minecraft mc, KeyframeAnimation animation) {
        if (!HeralizConfig.SYNC_EMOTE.get() || !animation.isInfinite || mc.level == null) return 0;
        Player source = pendingFrom == null ? null : mc.level.getPlayerByUUID(pendingFrom);
        IEmotePlayer playing = source == null ? null : playingEmote(source);
        if (playing != null && playing.getData() != null && animation.getUuid().equals(playing.getData().getUuid())) {
            return playing.getTick();
        }
        return pendingTick + (int) (clientTicks - pendingAt);
    }

    private static void play(LocalPlayer self, KeyframeAnimation animation, int tick) {
        // the regular way: checks, event, packet to the server, local play from the start
        if (!ClientEmoteAPI.playEmote(animation) || tick <= 0) return;
        try {
            // then jump to the other player's frame, here and for everyone around
            ((IEmotePlayerEntity<?>) self).playEmote(animation, tick, false);
            ClientEmotePlay.clientRepeatLocalEmote(animation, tick, null);
        } catch (Throwable t) {
            HeralizEmote.LOGGER.debug("Emote sync unavailable with this Emotecraft version", t);
        }
    }

    @Nullable
    private static IEmotePlayer playingEmote(Player player) {
        if (player instanceof IEmotePlayerEntity<?> holder && holder.isPlayingEmote()) {
            return holder.getEmote();
        }
        return null;
    }

    private static KeyframeAnimation mute(KeyframeAnimation animation) {
        if (!animation.extraData.containsKey("song")) return animation;
        KeyframeAnimation.AnimationBuilder builder = animation.mutableCopy();
        builder.song = null;
        builder.extraData.remove("song");
        return builder.build();
    }

    private static boolean reverseSoundHeld() {
        return HeralizKeys.keyHeld(HeralizKeys.REVERSE_SOUND) && HeralizKeys.modifierHeld(HeralizKeys.REVERSE_SOUND);
    }

    @Nullable
    private static AbstractClientPlayer lookedAtPlayer(LocalPlayer self) {
        double reach = HeralizConfig.REACH.get();
        Vec3 eye = self.getEyePosition(1.0F);
        Vec3 look = self.getViewVector(1.0F);
        HitResult block = self.pick(reach, 1.0F, false);
        double max = block.getType() == HitResult.Type.MISS ? reach : Math.min(reach, block.getLocation().distanceTo(eye));
        Vec3 end = eye.add(look.scale(max));
        AABB box = self.getBoundingBox().expandTowards(look.scale(max)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(self, eye, end, box,
                e -> e instanceof AbstractClientPlayer && e != self && !e.isSpectator(), max * max);
        return hit == null ? null : (AbstractClientPlayer) hit.getEntity();
    }

    private static void message(Minecraft mc, String key, Component name) {
        if (HeralizConfig.SHOW_MESSAGES.get() && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(key, name), true);
        }
    }
}
