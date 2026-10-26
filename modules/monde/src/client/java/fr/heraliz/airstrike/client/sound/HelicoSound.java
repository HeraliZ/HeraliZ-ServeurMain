package fr.heraliz.airstrike.client.sound;

import fr.heraliz.airstrike.client.HelicoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Son en boucle d'un hélicoptère (rotor proche, rotor lointain, alarme de la cabine) : suit sa position (entité, ou
 * position reçue chaque seconde quand il est trop loin pour que le serveur envoie l'entité), sans l'atténuation de
 * Minecraft : le volume est calculé ici selon la distance, pour qu'on l'entende jusqu'à ~250 blocs. La hauteur baisse
 * pendant la chute (le rotor ralentit).
 */
public class HelicoSound extends AbstractTickableSoundInstance {
    public enum Couche {
        /** Rotor de près : fort jusqu'à ~40 blocs, éteint à 140. */
        PRES,
        /** Rotor grave de loin : s'entend jusqu'à ~260 blocs. */
        LOIN,
        /** Alarme de la cabine, en feu : à moins de 70 blocs. */
        ALARME
    }

    private final HelicoClient.Suivi suivi;
    private final Couche couche;
    private int fondu;

    public HelicoSound(HelicoClient.Suivi suivi, Couche couche, SoundEvent son) {
        super(son, SoundSource.HOSTILE, RandomSource.create());
        this.suivi = suivi;
        this.couche = couche;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = SoundInstance.Attenuation.NONE;
        maj();
    }

    private void maj() {
        x = suivi.x;
        y = suivi.y + 2;
        z = suivi.z;
        Minecraft mc = Minecraft.getInstance();
        Vec3 ecoute = mc.gameRenderer.getMainCamera().getPosition();
        double d = Math.sqrt(ecoute.distanceToSqr(x, y, z));
        float k = Math.min(1f, fondu / 30f);
        float chute = suivi.chute;
        switch (couche) {
            case PRES -> {
                float v = (float) Mth.clamp(1.0 - d / 140.0, 0.0, 1.0);
                volume = k * v * v * (0.55f + 0.45f * suivi.rotor);
                pitch = (0.88f + 0.12f * suivi.rotor) * (1f - 0.3f * chute);
            }
            case LOIN -> {
                // plus fort de loin (le rotor proche le couvre de près), éteint à 260 blocs
                float v = (float) Mth.clamp((260.0 - d) / 200.0, 0.0, 1.0);
                float pres = (float) Mth.clamp(1.0 - d / 60.0, 0.0, 1.0);
                volume = k * 0.85f * v * (1f - 0.6f * pres) * (0.5f + 0.5f * suivi.rotor);
                pitch = (0.9f + 0.1f * suivi.rotor) * (1f - 0.25f * chute);
            }
            case ALARME -> {
                float v = (float) Mth.clamp(1.0 - d / 70.0, 0.0, 1.0);
                volume = suivi.alarme() ? 0.8f * v : 0f;
                pitch = 1f;
            }
        }
    }

    @Override
    public void tick() {
        if (suivi.fini()) {
            stop();
            return;
        }
        if (fondu < 30) fondu++;
        maj();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
