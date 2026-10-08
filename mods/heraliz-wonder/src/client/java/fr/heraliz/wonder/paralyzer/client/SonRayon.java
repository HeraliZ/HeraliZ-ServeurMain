package fr.heraliz.wonder.paralyzer.client;

import fr.heraliz.wonder.paralyzer.SonsParalyseur;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bourdon du rayon (boucle de 2 s) qui suit le tireur : monte en 0,1 s quand le rayon part, retombe quand il s'arrete,
 * puis s'arrete tout seul. Un seul par tireur (cree au depart du rayon, pas a chaque image).
 */
final class SonRayon extends AbstractTickableSoundInstance {
    private final LivingEntity tireur;

    SonRayon(LivingEntity tireur, int pap) {
        super(SonsParalyseur.RAYON.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.tireur = tireur;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05f;
        this.pitch = pap > 0 ? 0.9f : 1.0f;
        this.x = tireur.getX();
        this.y = tireur.getEyeY();
        this.z = tireur.getZ();
    }

    @Override
    public void tick() {
        float k = FxParalyseur.intensiteSon(tireur);
        if (tireur.isRemoved() || k <= 0f) {
            stop();
            return;
        }
        this.x = tireur.getX();
        this.y = tireur.getEyeY();
        this.z = tireur.getZ();
        this.volume = 0.9f * k;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
