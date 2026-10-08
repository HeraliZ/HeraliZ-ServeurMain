package fr.heralisz.tags.registry;

import fr.heralisz.tags.HeraliszTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons maison (fichiers produits par l'agent sons dans assets/heralisz_tags/sounds/, declares dans sounds.json).
 */
public final class Sons {
    private Sons() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszTags.MODID);

    /** Bombe de peinture a la pose d'un tag. */
    public static final RegistryObject<SoundEvent> TAG_SPRAY = son("tag_spray");
    /** Ouverture de la roue (J maintenu). */
    public static final RegistryObject<SoundEvent> ROUE_OUVRIR = son("roue_ouvrir");
    /** Quartier choisi / survole dans la roue. */
    public static final RegistryObject<SoundEvent> ROUE_CHOISIR = son("roue_choisir");
    /** Nouveau tag debloque (objet consomme). */
    public static final RegistryObject<SoundEvent> TAG_DEBLOQUE = son("tag_debloque");

    private static RegistryObject<SoundEvent> son(String nom) {
        return SOUNDS.register(nom, () -> SoundEvent.createVariableRangeEvent(HeraliszTags.id(nom)));
    }
}
