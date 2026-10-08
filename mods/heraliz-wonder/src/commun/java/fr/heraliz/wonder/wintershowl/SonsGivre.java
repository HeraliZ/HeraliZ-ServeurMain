package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du Winter's Howl joues par le mod (synthetises par {@code tools/wonder/wh_sons.py}, rien de telecharge) ; les sons
 * de tir, rechargement, sortie et inspection sont ceux du gunpack (TaCZ les joue). Registre propre (meme espace de noms
 * heraliz_wonder) : aucune ligne a ajouter dans {@code Sons.java}, seulement les entrees de {@code sounds.json}.
 */
public final class SonsGivre {
    private SonsGivre() {
    }

    public static final DeferredRegister<SoundEvent> REGISTRE = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizWonder.MODID);

    /** Le souffle de givre qui balaie le cone (sifflement glace, hurlement du vent). */
    public static final RegistryObject<SoundEvent> SOUFFLE = son("givre_souffle");
    /** Une creature gele (craquement de la glace qui prend). */
    public static final RegistryObject<SoundEvent> GEL = son("givre_gel");
    /** Une creature gelee vole en eclats (verre glace qui se brise, eclats qui tintent). */
    public static final RegistryObject<SoundEvent> ECLAT = son("givre_eclat");
    /** Craquements de la glace juste avant l'eclatement. */
    public static final RegistryObject<SoundEvent> CRAQUE = son("givre_craque");

    private static RegistryObject<SoundEvent> son(String nom) {
        return REGISTRE.register(nom, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeralizWonder.MODID, nom)));
    }
}
