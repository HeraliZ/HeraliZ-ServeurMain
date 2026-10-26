package fr.heralisz.tactical.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticleTypes {
   public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "heralisz_tactical");
   public static final RegistryObject<SimpleParticleType> SMOKE_CLOUD = PARTICLE_TYPES.register("smoke_cloud", () -> new SimpleParticleType(true));
}
