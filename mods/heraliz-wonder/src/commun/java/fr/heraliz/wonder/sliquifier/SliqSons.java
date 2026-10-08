package fr.heraliz.wonder.sliquifier;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons des effets du Sliquifier (synthetises par {@code tools/wonder/sq_sons.py}, aucun son telecharge). Registre a part
 * (meme espace de noms {@code heraliz_wonder}) : rien a changer dans {@code Sons} ; seules les entrees de
 * {@code sounds.json} sont a fusionner. Les sons de tir et de rechargement sont ceux du gunpack (TaCZ les joue).
 */
public final class SliqSons {
    private SliqSons() {
    }

    public static final DeferredRegister<SoundEvent> REGISTRE = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizWonder.MODID);

    /** La boule s'ecrase (3 variantes). */
    public static final RegistryObject<SoundEvent> SPLAT = son("sliq_splat");
    /** La flaque se forme et bouillonne. */
    public static final RegistryObject<SoundEvent> FLAQUE = son("sliq_flaque");
    /** Un zombie glisse (2 variantes). */
    public static final RegistryObject<SoundEvent> GLISSE = son("sliq_glisse");
    /** Un zombie fond (2 variantes). */
    public static final RegistryObject<SoundEvent> LIQUEFIE = son("sliq_liquefie");
    /** Pas d'un joueur qui glisse dans une flaque (3 variantes). */
    public static final RegistryObject<SoundEvent> PAS = son("sliq_pas");

    private static RegistryObject<SoundEvent> son(String nom) {
        return REGISTRE.register(nom, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeralizWonder.MODID, nom)));
    }
}
