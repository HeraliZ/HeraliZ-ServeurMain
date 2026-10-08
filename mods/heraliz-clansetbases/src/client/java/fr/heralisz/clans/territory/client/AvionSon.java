package fr.heralisz.clans.territory.client;

import fr.heralisz.clans.registry.Sons;
import fr.heralisz.clans.territory.supply.AvionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Son du moteur de l'avion en boucle, attache a l'entite (position suivie a chaque tick, arret quand elle
 * disparait ou quand le serveur ne la fait plus bouger : {@link AvionEntity#fige()}). Volume eleve : l'avion passe
 * a y = 180, les joueurs sont a 100 blocs et plus.
 */
@OnlyIn(Dist.CLIENT)
public class AvionSon extends AbstractTickableSoundInstance {
    private final AvionEntity avion;

    private AvionSon(AvionEntity avion) {
        super(Sons.AVION_MOTEUR.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.avion = avion;
        this.looping = true;
        this.delay = 0;
        this.volume = 12f;
        this.attenuation = Attenuation.LINEAR;
        this.x = avion.getX();
        this.y = avion.getY();
        this.z = avion.getZ();
    }

    public static void jouer(AvionEntity avion) {
        Minecraft.getInstance().getSoundManager().play(new AvionSon(avion));
    }

    @Override
    public void tick() {
        if (avion.isRemoved() || avion.fige()) {
            stop();
            return;
        }
        this.x = avion.getX();
        this.y = avion.getY();
        this.z = avion.getZ();
    }
}
