package fr.heraliz.wonder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du mod (synthetises par {@code tools/wonder/gen_sons_wonder.py} et {@code tools/wonder/tg_sons.py}, aucun son
 * telecharge) : impacts, arcs et tonnerre de la foudre, electrocution, souffle du Thundergun. Les sons de tir et de rechargement restent ceux du gunpack (TaCZ les joue).
 */
public final class Sons {
    private Sons() {
    }

    public static final DeferredRegister<SoundEvent> REGISTRE = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizWonder.MODID);

    public static final RegistryObject<SoundEvent> RAYON_IMPACT = son("rayon_impact");
    public static final RegistryObject<SoundEvent> MK2_IMPACT = son("mk2_impact");
    public static final RegistryObject<SoundEvent> FOUDRE_TONNERRE = son("foudre_tonnerre");
    public static final RegistryObject<SoundEvent> FOUDRE_ARC = son("foudre_arc");
    public static final RegistryObject<SoundEvent> ELECTROCUTION = son("electrocution");
    /** Thundergun (1.0.2) : rafale de vent et debris qui balaie le cone ; corps projete qui retombe. */
    public static final RegistryObject<SoundEvent> SOUFFLE = son("souffle");
    public static final RegistryObject<SoundEvent> SOUFFLE_CORPS = son("souffle_corps");

    private static RegistryObject<SoundEvent> son(String nom) {
        return REGISTRE.register(nom, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeralizWonder.MODID, nom)));
    }
}
