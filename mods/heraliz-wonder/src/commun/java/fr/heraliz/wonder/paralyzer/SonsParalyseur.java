package fr.heraliz.wonder.paralyzer;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du Paralyzer joues par le mod (synthetises par {@code tools/wonder/pz_sons.py}, rien de telecharge) ; registre
 * propre (un {@code DeferredRegister} de plus sur le meme espace de noms). Les sons d'arme (impulsion, sortie,
 * rangement, inspection, recharges) sont ceux du gunpack, joues par TaCZ.
 */
public final class SonsParalyseur {
    private SonsParalyseur() {
    }

    public static final DeferredRegister<SoundEvent> REGISTRE = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizWonder.MODID);

    /** Boucle de 2 s du rayon (bourdon electrique, arc, dynamo de la manivelle). */
    public static final RegistryObject<SoundEvent> RAYON = son("paralyseur_rayon");
    public static final RegistryObject<SoundEvent> DEBUT = son("paralyseur_debut");
    public static final RegistryObject<SoundEvent> FIN = son("paralyseur_fin");
    public static final RegistryObject<SoundEvent> SURCHAUFFE = son("paralyseur_surchauffe");
    public static final RegistryObject<SoundEvent> PRET = son("paralyseur_pret");
    public static final RegistryObject<SoundEvent> GEL = son("paralyseur_gel");
    public static final RegistryObject<SoundEvent> POUSSIERE = son("paralyseur_poussiere");

    private static RegistryObject<SoundEvent> son(String nom) {
        return REGISTRE.register(nom, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeralizWonder.MODID, nom)));
    }
}
