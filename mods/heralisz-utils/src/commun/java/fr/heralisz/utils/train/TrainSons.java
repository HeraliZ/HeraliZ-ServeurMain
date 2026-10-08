package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du train (registre commun ; la lecture est dans TrainSonsClient), côté client et tirés de l'horaire (comme le reste : rien à envoyer) : roulement (boucle) sur la
 * voiture la plus proche de l'oreille, volume et hauteur selon la vitesse ; moteur (boucle) sur la locomotive ; klaxon
 * au départ et à 25 s d'une gare ; freins à l'approche ; carillon à l'arrivée en gare. Les sons sont synthétisés par
 * tools/gen_train_sons.py.
 */
public final class TrainSons {
    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszUtils.MODID);
    public static final RegistryObject<SoundEvent> KLAXON = son("train.klaxon"), ROULEMENT = son("train.roulement"),
            FREINS = son("train.freins"), MOTEUR = son("train.moteur"), CARILLON = son("train.carillon");

    private TrainSons() {}

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeraliszUtils.MODID, id)));
    }
}
