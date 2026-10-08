package fr.heraliz.emote.client;

import fr.heraliz.emote.client.catalog.MoveSpec;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.MovementInputUpdateEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How the body moves during the Heraliz emotes.
 *
 * <ul>
 * <li>Emotes with props on the ground (a basketball hoop, DJ decks, a piano...): Emotecraft turns the body
 * with the camera every tick, which would turn the whole scene with it. The body keeps the
 * direction it had when the emote started instead; the camera stays free.</li>
 * <li>Emotes the player can walk with ({@link MoveSpec}): forward only and slowly (unless the
 * emote allows any direction), no jumping or sprinting. Emotecraft would stop them as soon as the
 * player moves: {@code EmoteHolderMixin} lets them go on.</li>
 * </ul>
 */
public final class EmoteMotion {
    private record Anchor(UUID emote, float yaw) {
    }

    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();
    /** How far the player may drift sideways or backward in one tick before a forward-only emote stops (blocks). */
    private static final double SIDE_TOLERANCE = 0.04;

    private EmoteMotion() {
    }

    /** End of the client tick: after the players (and Emotecraft) turned their bodies. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ANCHORS.clear();
            return;
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            EmotePlayback playback = EmotePlayback.of(player);
            if (playback == null || !playback.info().fixedScene()) {
                ANCHORS.remove(player.getId());
                continue;
            }
            Anchor anchor = ANCHORS.get(player.getId());
            if (anchor == null || !anchor.emote().equals(playback.info().uuid())) {
                anchor = new Anchor(playback.info().uuid(), player.yBodyRot);
                ANCHORS.put(player.getId(), anchor);
            }
            player.yBodyRot = anchor.yaw();
            player.yBodyRotO = anchor.yaw();
        }
        if (ANCHORS.size() > 64) ANCHORS.clear();
    }

    /** Slows the local player down and keeps them on the ground while they walk with an emote. */
    static void onInput(MovementInputUpdateEvent event) {
        MoveSpec move = moveOf(event.getEntity());
        if (move == null) return;
        Input input = event.getInput();
        input.forwardImpulse *= move.speed;
        input.leftImpulse *= move.speed;
        input.jumping = false;
        event.getEntity().setSprinting(false);
    }

    /** The emote's {@link MoveSpec} if {@code entity} plays a Heraliz emote one can walk with. */
    @Nullable
    public static MoveSpec moveOf(Object entity) {
        if (!(entity instanceof AbstractClientPlayer player)) return null;
        EmotePlayback playback = EmotePlayback.of(player);
        return playback == null ? null : playback.info().move();
    }

    /**
     * For the mixin: may the local player keep playing their emote after moving this tick?
     * Null when it is not a walking emote (Emotecraft decides as usual).
     */
    @Nullable
    public static Boolean mayKeepPlaying(IEmotePlayerEntity<?> holder) {
        MoveSpec move = moveOf(holder);
        if (move == null) return null;
        AbstractClientPlayer player = (AbstractClientPlayer) holder;
        if (holder.isNotStanding()) return false;
        if (move.sideways) return true;
        Vec3 delta = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double forward = delta.x * -Mth.sin(yaw) + delta.z * Mth.cos(yaw);
        double side = Math.abs(delta.x * Mth.cos(yaw) + delta.z * Mth.sin(yaw));
        return side <= SIDE_TOLERANCE && forward >= -SIDE_TOLERANCE;
    }
}
