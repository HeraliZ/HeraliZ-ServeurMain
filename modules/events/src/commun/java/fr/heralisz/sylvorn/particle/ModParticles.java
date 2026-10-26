package fr.heralisz.sylvorn.particle;

import fr.heralisz.sylvorn.HeraliszSylvorn;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particules maison du boss (textures dessinées, comportements propres). */
public final class ModParticles {
   public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeraliszSylvorn.MODID);

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

   // Chevalier Sans Visage : le feu du Serment (aucune flamme vanilla)
   public static final RegistryObject<SimpleParticleType> OATH_EMBER = PARTICLES.register("oath_ember", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> OATH_FLAME = PARTICLES.register("oath_flame", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> OATH_ASH = PARTICLES.register("oath_ash", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> OATH_SMOKE = PARTICLES.register("oath_smoke", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> OATH_CINDER = PARTICLES.register("oath_cinder", () -> new SimpleParticleType(true));

   // communes : remplacent toutes les particules vanilla (eau, encre, vapeur, éclats, magma, arcs, lueurs)
   public static final RegistryObject<SimpleParticleType> SEA_SPRAY = PARTICLES.register("sea_spray", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> SEA_RING = PARTICLES.register("sea_ring", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> SEA_BUBBLE = PARTICLES.register("sea_bubble", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> SEA_FOAM = PARTICLES.register("sea_foam", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> INK = PARTICLES.register("ink", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> STEAM = PARTICLES.register("steam", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> STONE_CHIP = PARTICLES.register("stone_chip", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> WOOD_CHIP = PARTICLES.register("wood_chip", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> MAGMA = PARTICLES.register("magma", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> TESLA_SPARK = PARTICLES.register("tesla_spark", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> MOTE = PARTICLES.register("mote", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> SPIRIT_FLAME = PARTICLES.register("spirit_flame", () -> new SimpleParticleType(true));
   public static final RegistryObject<SimpleParticleType> AMBER_DROP = PARTICLES.register("amber_drop", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> AMBER_RING = PARTICLES.register("amber_ring", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> BLOOD = PARTICLES.register("blood", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> FLESH_CHIP = PARTICLES.register("flesh_chip", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> BONE_CHIP = PARTICLES.register("bone_chip", () -> new SimpleParticleType(false));
   public static final RegistryObject<SimpleParticleType> WEB_FLUFF = PARTICLES.register("web_fluff", () -> new SimpleParticleType(false));

   private ModParticles() {}
}
