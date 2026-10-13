package fr.heralisz.utils.train;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du train (registre commun ; la lecture est dans TrainSonsClient, côté joueur seulement, tirée de l'horaire comme
 * le reste : le serveur n'envoie rien). Boucles : voie (roulement vu de dehors), cabine (à bord), moteur (diesel de la
 * locomotive), roulement (cinématique). Ponctuels : joint (une roue sur un joint de rail), attelage, depart, klaxon,
 * freins (arrivée), detente (air à l'arrêt), carillon, portes_ouverture, portes_fermeture, porte_interieure. Les fichiers
 * sont fabriqués par le calcul (outils/sons_train.py, aucun enregistrement), leurs portées sont dans sounds.json.
 */
public final class TrainSons {
    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszUtils.MODID);
    public static final RegistryObject<SoundEvent> KLAXON = son("train.klaxon"), ROULEMENT = son("train.roulement"),
            FREINS = son("train.freins"), MOTEUR = son("train.moteur"), CARILLON = son("train.carillon"),
            VOIE = son("train.voie"), CABINE = son("train.cabine"), JOINT = son("train.joint"),
            ATTELAGE = son("train.attelage"), DEPART = son("train.depart"), DETENTE = son("train.detente"),
            PORTES_OUVERTURE = son("train.portes_ouverture"), PORTES_FERMETURE = son("train.portes_fermeture"),
            PORTE_INTERIEURE = son("train.porte_interieure");

    private TrainSons() {}

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeraliszUtils.MODID, id)));
    }
}
