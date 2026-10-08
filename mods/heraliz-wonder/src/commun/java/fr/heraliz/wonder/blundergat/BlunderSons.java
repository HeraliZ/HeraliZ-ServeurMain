package fr.heraliz.wonder.blundergat;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons joues par le mod pour le Blundergat et l'Acid Gat (synthetises par {@code tools/wonder/bg_sons.py}, rien de
 * telecharge ; les sons de tir, rechargement, sortie et inspection sont ceux du gunpack, joues par TaCZ). Registre a part
 * (meme espace de noms {@code heraliz_wonder}) : rien a toucher dans {@link fr.heraliz.wonder.Sons}.
 */
public final class BlunderSons {
    private BlunderSons() {
    }

    public static final DeferredRegister<SoundEvent> REGISTRE = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizWonder.MODID);

    /** Obus qui frappe un bloc : choc mat et grésillement d'étincelles. */
    public static final RegistryObject<SoundEvent> IMPACT = son("blundergat_impact");
    /** Obus qui transperce une créature. */
    public static final RegistryObject<SoundEvent> CHAIR = son("blundergat_chair");
    /** Cartouche d'acide qui colle (bruit mouillé). */
    public static final RegistryObject<SoundEvent> ACIDE_COLLE = son("acide_colle");
    /** Bip de la cartouche avant l'explosion. */
    public static final RegistryObject<SoundEvent> ACIDE_BIP = son("acide_bip");
    /** Explosion d'acide (éclaboussure, grésillement). */
    public static final RegistryObject<SoundEvent> ACIDE_EXPLOSION = son("acide_explosion");
    /** Chair qui grésille sous l'acide. */
    public static final RegistryObject<SoundEvent> ACIDE_GRESILLE = son("acide_gresille");

    private static RegistryObject<SoundEvent> son(String nom) {
        return REGISTRE.register(nom, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeralizWonder.MODID, nom)));
    }
}
