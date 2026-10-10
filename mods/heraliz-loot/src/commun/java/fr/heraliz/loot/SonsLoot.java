package fr.heraliz.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons des coffres (synthétisés par tools/gen_sons_loot.py, aucun son vanilla). */
public final class SonsLoot {
    static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizLoot.MODID);
    public static final RegistryObject<SoundEvent> VOLANT = son("coffre.volant"), VERROUS = son("coffre.verrous"),
            PORTE_LOURDE = son("coffre.porte_lourde"), LOQUETS = son("coffre.loquets"), HYDRAULIQUE = son("coffre.hydraulique"),
            PANNEAUX = son("coffre.panneaux"), SCAN = son("coffre.scan"), RUNES = son("coffre.runes"), GLACE = son("coffre.glace"),
            TIC = son("identif.tic"), REVELE_COMMUN = son("identif.commun"), REVELE_RARE = son("identif.rare"),
            REVELE_EPIQUE = son("identif.epique"), REVELE_LEGENDAIRE = son("identif.legendaire"),
            REVELE_MYTHIQUE = son("identif.mythique"), REVELE_UNIQUE = son("identif.unique");

    private SonsLoot() {}

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeralizLoot.MODID, id)));
    }

    public static SoundEvent revelation(Rarete r) {
        return switch (r) {
            case COMMUN, PEU_COMMUN -> REVELE_COMMUN.get();
            case RARE -> REVELE_RARE.get();
            case EPIQUE -> REVELE_EPIQUE.get();
            case LEGENDAIRE -> REVELE_LEGENDAIRE.get();
            case UNIQUE -> REVELE_UNIQUE.get();
        };
    }
}
