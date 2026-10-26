package fr.heralisz.mammoth.fx;

import fr.heralisz.mammoth.HeraliszMammoth;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particules propres à Ymirak (aucune particule vanilla) : flocons, brume de givre, flammes d'aurore, éclats de glace. */
public final class Particles {
   public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeraliszMammoth.MODID);

   /** Flocon qui tourbillonne lentement. */
   public static final RegistryObject<SimpleParticleType> FLAKE = TYPES.register("flake", () -> new SimpleParticleType(false));
   /** Brume de givre qui gonfle et se dissipe (souffle, impacts). */
   public static final RegistryObject<SimpleParticleType> MIST = TYPES.register("mist", () -> new SimpleParticleType(false));
   /** Flamme d'aurore : feu froid des totems et braseros de la tribu. */
   public static final RegistryObject<SimpleParticleType> AURORA = TYPES.register("aurora", () -> new SimpleParticleType(false));
   /** Éclat de glace qui retombe et rebondit. */
   public static final RegistryObject<SimpleParticleType> SHARD = TYPES.register("shard", () -> new SimpleParticleType(false));
   /** Braise de fourrure qui brûle (bûcher des chasseurs). */
   public static final RegistryObject<SimpleParticleType> CINDER = TYPES.register("cinder", () -> new SimpleParticleType(false));

   /** Filet d'âme : file droit (vitesse donnée) pendant 40 ticks sans ralentir, en ondulant ; aspiré par la Gueule. */
   public static final RegistryObject<SimpleParticleType> SOUL = TYPES.register("soul_wisp", () -> new SimpleParticleType(true));

   private Particles() {}

   public static void register(IEventBus bus) {
      TYPES.register(bus);
   }
}
