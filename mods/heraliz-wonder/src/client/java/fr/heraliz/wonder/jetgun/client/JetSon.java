package fr.heraliz.wonder.jetgun.client;

import fr.heraliz.wonder.jetgun.JetSons;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Boucle du réacteur d'un tireur ({@code jetgun_moteur}, 2 s raccordées) : suit le tireur, volume et hauteur suivent
 * l'emballement (montée en 0,45 s, descente en 0,9 s) et un peu la chaleur ; s'éteint d'elle-même quand la soufflante
 * s'arrête. Une instance par séance de tir (pas par image).
 */
final class JetSon extends AbstractTickableSoundInstance {
    private final Entity e;
    private final int slot;

    JetSon(Entity e, int slot) {
        super(JetSons.MOTEUR.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.e = e;
        this.slot = slot;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.pitch = 0.6f;
        this.x = e.getX();
        this.y = e.getEyeY() - 0.4;
        this.z = e.getZ();
    }

    @Override
    public void tick() {
        float sp = JetFx.spin(slot, e.getId());
        if (e.isRemoved() || sp <= 0.005f) {
            stop();
            return;
        }
        x = e.getX();
        y = e.getEyeY() - 0.4;
        z = e.getZ();
        float ch = JetFx.chaleurDe(slot);
        volume = 1.15f * sp * sp * (3 - 2 * sp);
        pitch = 0.58f + 0.42f * sp + 0.07f * ch;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    void arreter() {
        stop();
    }
}
