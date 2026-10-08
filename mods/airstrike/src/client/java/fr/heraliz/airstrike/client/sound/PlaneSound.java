package fr.heraliz.airstrike.client.sound;

import fr.heraliz.airstrike.client.ClientStrikes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Looping engine sound glued to an aircraft, with a Doppler shift. */
public class PlaneSound extends AbstractTickableSoundInstance {
    private static final double SPEED_OF_SOUND = 17.0; // blocks per tick
    private final ClientStrikes.ClientStrike strike;
    private final float basePitch;
    private final float baseVolume;
    private int fade;

    public PlaneSound(ClientStrikes.ClientStrike strike, SoundEvent event, float volume, float pitch) {
        super(event, SoundSource.NEUTRAL, RandomSource.create());
        this.strike = strike;
        this.looping = true;
        this.delay = 0;
        this.basePitch = pitch;
        this.baseVolume = volume;
        this.volume = 0.01f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        update(Minecraft.getInstance().getPartialTick());
    }

    private void update(float pt) {
        double t = strike.time(pt);
        Vec3 p = strike.strike.path.position(t);
        x = p.x;
        y = p.y;
        z = p.z;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 v = strike.strike.path.position(t + 0.5).subtract(strike.strike.path.position(t - 0.5));
        Vec3 toListener = listener.subtract(p);
        double d = toListener.length();
        double radial = d < 1e-3 ? 0 : v.dot(toListener) / d; // >0 approaching
        double doppler = SPEED_OF_SOUND / Math.max(4.0, SPEED_OF_SOUND - radial);
        pitch = (float) Mth.clamp(basePitch * doppler, 0.5, 2.0);
        // close-range roar: louder when very close
        float near = (float) Mth.clamp(1.0 - d / 90.0, 0.0, 1.0);
        volume = Math.min(1.0f, fade / 20f) * (baseVolume + near * 0.8f);
    }

    @Override
    public void tick() {
        if (strike.finished() || strike.time(0) > strike.strike.path.duration + 10) {
            stop();
            return;
        }
        if (fade < 20) fade++;
        update(0);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
