package fr.heralisz.sylvorn.client.fx;

import fr.heralisz.sylvorn.HeraliszSylvorn;
import fr.heralisz.sylvorn.fx.Particles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Rendu des particules de la Salle du Trône. */
@Mod.EventBusSubscriber(modid = HeraliszSylvorn.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FxParticles {
   private FxParticles() {}

   @SubscribeEvent
   public static void providers(RegisterParticleProvidersEvent e) {
      e.registerSpriteSet(Particles.FLAKE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new P(l, x, y, z, vx, vy, vz, s, Kind.FLAKE));
      e.registerSpriteSet(Particles.MIST.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new P(l, x, y, z, vx, vy, vz, s, Kind.MIST));
      e.registerSpriteSet(Particles.AURORA.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new P(l, x, y, z, vx, vy, vz, s, Kind.AURORA));
      e.registerSpriteSet(Particles.SHARD.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new P(l, x, y, z, vx, vy, vz, s, Kind.SHARD));
      e.registerSpriteSet(Particles.SOUL.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new P(l, x, y, z, vx, vy, vz, s, Kind.SOUL));
      e.registerSpriteSet(Particles.CINDER.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new P(l, x, y, z, vx, vy, vz, s, Kind.CINDER));
   }

   enum Kind { FLAKE, MIST, AURORA, SHARD, CINDER, SOUL }

   static final class P extends TextureSheetParticle {
      private final SpriteSet sprites;
      private final Kind kind;
      private final float baseSize, spin;

      P(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Kind kind) {
         super(l, x, y, z, vx, vy, vz);
         this.sprites = sprites;
         this.kind = kind;
         this.xd = vx;
         this.yd = vy;
         this.zd = vz;
         this.hasPhysics = kind == Kind.SHARD;
         switch (kind) {
            case FLAKE -> { lifetime = 60 + random.nextInt(60); gravity = 0.015F; friction = 0.97F; baseSize = 0.06F + random.nextFloat() * 0.06F; }
            case MIST -> { lifetime = 26 + random.nextInt(20); gravity = -0.004F; friction = 0.9F; baseSize = 0.5F + random.nextFloat() * 0.5F; }
            case AURORA -> { lifetime = 14 + random.nextInt(10); gravity = -0.03F; friction = 0.92F; baseSize = 0.14F + random.nextFloat() * 0.1F; }
            case SOUL -> { lifetime = 40; gravity = 0F; friction = 1.0F; baseSize = 0.16F + random.nextFloat() * 0.14F; setColor(1F, 0.86F, 0.6F); }
            case SHARD -> { lifetime = 30 + random.nextInt(25); gravity = 1.0F; friction = 0.98F; baseSize = 0.08F + random.nextFloat() * 0.08F; }
            default -> { lifetime = 20 + random.nextInt(20); gravity = -0.02F; friction = 0.94F; baseSize = 0.05F + random.nextFloat() * 0.04F; }
         }
         this.spin = (random.nextFloat() - 0.5F) * 0.3F;
         this.roll = random.nextFloat() * Mth.TWO_PI;
         this.oRoll = roll;
         this.quadSize = baseSize;
         setSpriteFromAge(sprites);
      }

      @Override
      public void tick() {
         super.tick();
         if (removed) return;
         oRoll = roll;
         roll += spin;
         float f = (float) age / lifetime;
         switch (kind) {
            case FLAKE -> { xd += Math.sin((age + x) * 0.2) * 0.002; alpha = f > 0.8F ? (1 - f) * 5 : 1; }
            case MIST -> { quadSize = baseSize * (1 + f * 2.5F); alpha = 0.55F * (1 - f); }
            case AURORA -> { quadSize = baseSize * (1 - f * 0.7F); alpha = 1 - f * f; }
            case CINDER -> alpha = 1 - f;
            case SOUL -> {
               quadSize = baseSize * (0.6F + 0.8F * Mth.sin(f * Mth.PI));
               alpha = Math.min(1, f * 6) * (f > 0.85F ? (1 - f) / 0.15F : 1);
               x += Math.sin(age * 0.5 + baseSize * 40) * 0.04;
               z += Math.cos(age * 0.5 + baseSize * 40) * 0.04;
            }
            default -> alpha = f > 0.7F ? (1 - f) / 0.3F : 1;
         }
         setSpriteFromAge(sprites);
      }

      @Override
      protected int getLightColor(float partial) {
         return kind == Kind.AURORA || kind == Kind.CINDER || kind == Kind.SOUL ? 0xF000F0 : super.getLightColor(partial);
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }
   }
}
