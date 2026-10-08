package fr.heralisz.tactical.init;

import fr.heralisz.tactical.effect.BurnedEffect;
import fr.heralisz.tactical.effect.HarmfulEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
   public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "heralisz_tactical");
   public static final RegistryObject<HarmfulEffect> BLIND = EFFECTS.register("blinded", () -> new HarmfulEffect(16777215));
   public static final RegistryObject<HarmfulEffect> DEAFENED = EFFECTS.register("deafened", () -> new HarmfulEffect(16777215));
   public static final RegistryObject<BurnedEffect> FLAMMABLE = EFFECTS.register("flammable", () -> new BurnedEffect(11159335));
}
