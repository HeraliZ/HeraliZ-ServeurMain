package fr.heralisz.n07.particle;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particules maison du boss (textures dessinées, comportements propres). */
public final class ModParticles {
   public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeraliszN07.MODID);

   public static final RegistryObject<SimpleParticleType> TOXIC_SMOKE = PARTICLES.register("toxic_smoke", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> TOXIC_SPARK = PARTICLES.register("toxic_spark", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> ACID_DROP = PARTICLES.register("acid_drop", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> ACID_SPLASH = PARTICLES.register("acid_splash", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> GLASS_SHARD = PARTICLES.register("glass_shard", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> SNOWFLAKE = PARTICLES.register("snowflake", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> FROST_MIST = PARTICLES.register("frost_mist", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> ICE_SHARD = PARTICLES.register("ice_shard", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> FROST_SPARK = PARTICLES.register("frost_spark", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> RAD_EMBER = PARTICLES.register("rad_ember", () -> new SimpleParticleType(true));

   public static final RegistryObject<SimpleParticleType> SPORE = PARTICLES.register("spore", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> LEAF = PARTICLES.register("leaf", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> LEAF_AUTUMN = PARTICLES.register("leaf_autumn", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> PETAL = PARTICLES.register("petal", () -> new SimpleParticleType(false));

   public static final RegistryObject<SimpleParticleType> DUST = PARTICLES.register("dust", () -> new SimpleParticleType(false));

   private ModParticles() {}
}
