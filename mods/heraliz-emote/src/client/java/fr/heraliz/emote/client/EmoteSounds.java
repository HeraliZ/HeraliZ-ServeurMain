package fr.heraliz.emote.client;

import fr.heraliz.emote.client.catalog.SoundSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * The sound effects of the Heraliz emotes (the music is a note block song Emotecraft plays), for
 * every player in earshot who plays one, on the ticks the catalog gives.
 */
public final class EmoteSounds {
    private static final Map<Integer, Integer> LAST_TICK = new HashMap<>();
    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();

    private EmoteSounds() {
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            LAST_TICK.clear();
            return;
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            EmotePlayback playback = EmotePlayback.of(player);
            if (playback == null || playback.info().sounds().isEmpty()
                    || player.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 32 * 32
                    || (player != mc.player && !HeralizConfig.OTHER_PLAYERS_SOUNDS.get())) {
                LAST_TICK.remove(player.getId());
                continue;
            }
            int tick = playback.player().getTick();
            Integer last = LAST_TICK.put(player.getId(), tick);
            if (last != null && last == tick) continue;
            for (SoundSpec spec : playback.info().sounds()) {
                if (spec.t != tick) continue;
                double x = player.getX(), y = player.getY() + 0.9, z = player.getZ();
                if (spec.attach != null) {
                    Vector4f at = EmoteParticles.worldPoint(player, playback, spec.attach, spec.pos);
                    x = at.x();
                    y = at.y();
                    z = at.z();
                }
                mc.level.playLocalSound(x, y, z, event(spec.id), SoundSource.PLAYERS, spec.volume * volume(), spec.pitch, false);
            }
        }
        if (LAST_TICK.size() > 64) LAST_TICK.clear();
    }

    /** For the menu preview: the sound as a UI sound (no position). */
    public static void playUi(SoundSpec spec) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event(spec.id), spec.pitch,
                Math.min(1.0F, spec.volume * volume() * 0.6F)));
    }

    /** The sound effects play at 40 % of their volume. */
    private static float volume() {
        return 0.4F;
    }

    private static SoundEvent event(String id) {
        return EVENTS.computeIfAbsent(id, k -> SoundEvent.createVariableRangeEvent(new ResourceLocation(k)));
    }
}
