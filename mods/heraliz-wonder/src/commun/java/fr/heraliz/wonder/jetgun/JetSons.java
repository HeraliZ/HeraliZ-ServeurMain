package fr.heraliz.wonder.jetgun;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du Jet Gun joués par le mod (synthétisés par {@code tools/wonder/jg_sons.py}, rien de téléchargé) : boucle du
 * réacteur, emballement, ralentissement, calage (surchauffe), zombie déchiqueté. Registre à part (même espace de noms) :
 * aucun conflit avec {@code Sons} ; les entrées de {@code sounds.json} sont listées dans INTEGRATION.md.
 */
public final class JetSons {
    private JetSons() {
    }

    public static final DeferredRegister<SoundEvent> REGISTRE = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizWonder.MODID);

    public static final RegistryObject<SoundEvent> MOTEUR = son("jetgun_moteur");
    public static final RegistryObject<SoundEvent> DEMARRAGE = son("jetgun_demarrage");
    public static final RegistryObject<SoundEvent> ARRET = son("jetgun_arret");
    public static final RegistryObject<SoundEvent> SURCHAUFFE = son("jetgun_surchauffe");
    public static final RegistryObject<SoundEvent> BROYAGE = son("jetgun_broyage");

    private static RegistryObject<SoundEvent> son(String nom) {
        return REGISTRE.register(nom, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeralizWonder.MODID, nom)));
    }
}
