package fr.heraliz.airstrike.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** Non looping sound following a moving position until the supplier returns null. */
public class FollowSound extends AbstractTickableSoundInstance {
    private final Supplier<Vec3> position;

    public FollowSound(SoundEvent event, float volume, float pitch, Supplier<Vec3> position) {
        super(event, SoundSource.NEUTRAL, RandomSource.create());
        this.position = position;
        this.volume = volume;
        this.pitch = pitch;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        Vec3 p = position.get();
        if (p != null) {
            x = p.x;
            y = p.y;
            z = p.z;
        }
    }

    @Override
    public void tick() {
        Vec3 p = position.get();
        if (p == null) {
            stop();
            return;
        }
        x = p.x;
        y = p.y;
        z = p.z;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
