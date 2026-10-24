package fr.heraliz.airstrike.client.helico;

import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Son des rotors, en boucle, collé à l'hélicoptère (entité proche ou position lointaine) : porte loin (420 blocs,
 * sounds.json), effet Doppler léger, plus grave et irrégulier pendant la chute. S'arrête quand l'hélicoptère n'existe
 * plus pour ce joueur.
 */
public class RotorSon extends AbstractTickableSoundInstance {
    private static final double VITESSE_SON = 17.0;   // blocs par tick
    private final int id;
    private int fondu;
    private Vec3 avant;

    public RotorSon(int id, Vec3 p) {
        super(ModSounds.HELICO_ROTOR.get(), SoundSource.HOSTILE, RandomSource.create());
        this.id = id;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = p.x;
        this.y = p.y;
        this.z = p.z;
        this.avant = p;
    }

    @Override
    public void tick() {
        HelicoClient.Etat e = HelicoClient.etat(id);
        if (e == null) {
            stop();
            return;
        }
        Vec3 p = e.position(0f);
        Vec3 v = p.subtract(avant);
        avant = p;
        x = p.x;
        y = p.y;
        z = p.z;
        Minecraft mc = Minecraft.getInstance();
        Vec3 oreille = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 vers = oreille.subtract(p);
        double d = vers.length();
        double radial = d < 1e-3 ? 0 : v.dot(vers) / d;
        double doppler = VITESSE_SON / Math.max(6.0, VITESSE_SON - radial);
        float base = e.chute() ? 0.78f + 0.08f * Mth.sin(e.age * 0.7f) : 1.0f;
        pitch = (float) Mth.clamp(base * doppler, 0.5, 1.6);
        if (fondu < 30) fondu++;
        float pres = (float) Mth.clamp(1.0 - d / 70.0, 0.0, 1.0);
        volume = fondu / 30f * (0.75f + pres * 0.25f);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
