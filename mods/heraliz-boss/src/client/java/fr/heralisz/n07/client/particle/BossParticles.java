package fr.heralisz.n07.client.particle;

import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/** Comportements des particules maison. */
public final class BossParticles {
   private BossParticles() {}

   public static void registerProviders(RegisterParticleProvidersEvent e) {
      e.registerSpriteSet(ModParticles.TOXIC_SMOKE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Smoke(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.TOXIC_SPARK.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Spark(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.ACID_DROP.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Drop(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.ACID_SPLASH.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Splash(l, x, y, z, s));
      e.registerSpriteSet(ModParticles.GLASS_SHARD.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Shard(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.FROST_MIST.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Smoke(l, x, y, z, vx, vy, vz, s), 0.75F, 0.9F, 1F));
      e.registerSpriteSet(ModParticles.ICE_SHARD.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Shard(l, x, y, z, vx, vy, vz, s), 0.7F, 0.9F, 1F));
      e.registerSpriteSet(ModParticles.FROST_SPARK.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Spark(l, x, y, z, vx, vy, vz, s), 0.6F, 0.88F, 1F));
      e.registerSpriteSet(ModParticles.SNOWFLAKE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Snow(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.RAD_EMBER.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Ember(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.SPORE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Ember(l, x, y, z, vx, vy, vz, s), 0.72F, 1F, 0.55F));
      e.registerSpriteSet(ModParticles.DUST.get(), s -> (t, l, x, y, z, vx, vy, vz) -> dust(new Smoke(l, x, y, z, vx, vy, vz, s)));
      e.registerSpriteSet(ModParticles.LEAF.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Leaf(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.LEAF_AUTUMN.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Leaf(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.PETAL.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Leaf(l, x, y, z, vx, vy, vz, s));
   }

   static final int FULL_BRIGHT = 0xF000F0;

   /** Poussière de terre : lourde, brune, retombe lentement. */
   private static TextureSheetParticle dust(Smoke p) {
      float g = 0.7F + p.random().nextFloat() * 0.3F;
      p.setColor(0.28F * g, 0.22F * g, 0.16F * g);
      p.heavy = true;
      return p;
   }

   private static TextureSheetParticle tint(TextureSheetParticle p, float r, float g, float b) {
      p.setColor(r, g, b);
      return p;
   }

   /** Flocon : tombe doucement en tourbillonnant, porté par le vent. */
   static class Snow extends TextureSheetParticle {
      private final float phase, spin;

      Snow(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy - 0.01;
         this.zd = vz;
         this.lifetime = 40 + random.nextInt(50);
         this.quadSize = 0.05F + random.nextFloat() * 0.07F;
         this.phase = random.nextFloat() * 6.28F;
         this.spin = (random.nextFloat() - 0.5F) * 0.2F;
         this.friction = 0.95F;
         this.gravity = 0.04F;
         this.setColor(0.95F, 0.98F, 1F);
      }

      @Override
      public void tick() {
         super.tick();
         this.oRoll = roll;
         roll += spin;
         this.xd += Mth.sin(age * 0.2F + phase) * 0.003;
         this.zd += Mth.cos(age * 0.17F + phase) * 0.003;
         this.alpha = Math.min(1, (lifetime - age) / 10F);
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }
   }

   /** Fumée toxique : grosse bouffée qui gonfle, tourne, monte doucement et se dissipe. */
   static class Smoke extends TextureSheetParticle {
      private final float baseSize, spin;
      boolean heavy;

      net.minecraft.util.RandomSource random() {
         return random;
      }

      Smoke(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx + (random.nextDouble() - 0.5) * 0.02;
         this.yd = vy + 0.01 + random.nextDouble() * 0.02;
         this.zd = vz + (random.nextDouble() - 0.5) * 0.02;
         this.lifetime = 30 + random.nextInt(30);
         this.baseSize = 0.3F + random.nextFloat() * 0.35F;
         this.quadSize = baseSize;
         this.spin = (random.nextFloat() - 0.5F) * 0.08F;
         this.roll = random.nextFloat() * Mth.TWO_PI;
         float g = 0.55F + random.nextFloat() * 0.4F;
         this.setColor(0.25F + 0.2F * g, g, 0.12F + 0.1F * g);
         this.friction = 0.92F;
         this.hasPhysics = false;
      }

      @Override
      public void tick() {
         super.tick();
         float k = (float) this.age / this.lifetime;
         this.oRoll = this.roll;
         this.roll += spin;
         this.quadSize = baseSize * (1 + k * 1.8F);
         this.alpha = Math.min(1, age / 5F) * (1 - k) * (1 - k) * (heavy ? 0.7F : 0.32F);
         if (heavy) this.yd -= 0.0015;
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      protected int getLightColor(float partial) {
         return 0xA000A0;
      }
   }

   /** Étincelle lumineuse : jaillit, retombe, rétrécit. */
   static class Spark extends TextureSheetParticle {
      Spark(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z, vx, vy, vz);
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy;
         this.zd = vz;
         this.lifetime = 10 + random.nextInt(14);
         this.quadSize = 0.08F + random.nextFloat() * 0.08F;
         this.gravity = 0.35F;
         this.friction = 0.9F;
         this.setColor(0.7F + random.nextFloat() * 0.3F, 1F, 0.35F + random.nextFloat() * 0.3F);
      }

      @Override
      public void tick() {
         super.tick();
         this.alpha = 1 - (float) age / lifetime;
      }

      @Override
      public float getQuadSize(float p) {
         return quadSize * (1 - (age + p) / lifetime * 0.7F);
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      protected int getLightColor(float partial) {
         return FULL_BRIGHT;
      }
   }

   /** Goutte d'acide : tombe et éclate au sol en petite éclaboussure. */
   static class Drop extends TextureSheetParticle {
      Drop(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy;
         this.zd = vz;
         this.lifetime = 60;
         this.gravity = 1.0F;
         this.quadSize = 0.07F + random.nextFloat() * 0.05F;
         this.setColor(0.5F, 1F, 0.25F);
      }

      @Override
      public void tick() {
         super.tick();
         if (this.onGround) {
            this.level.addParticle(ModParticles.ACID_SPLASH.get(), x, y + 0.02, z, 0, 0, 0);
            this.remove();
         }
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      protected int getLightColor(float partial) {
         return FULL_BRIGHT;
      }
   }

   /** Éclaboussure : anneau animé, court. */
   static class Splash extends TextureSheetParticle {
      private final SpriteSet sprites;

      Splash(ClientLevel l, double x, double y, double z, SpriteSet s) {
         super(l, x, y, z);
         this.sprites = s;
         this.lifetime = 8;
         this.quadSize = 0.25F + random.nextFloat() * 0.15F;
         this.setSpriteFromAge(s);
         this.setColor(0.55F, 1F, 0.3F);
         this.hasPhysics = false;
      }

      @Override
      public void tick() {
         super.tick();
         this.setSpriteFromAge(sprites);
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      protected int getLightColor(float partial) {
         return FULL_BRIGHT;
      }
   }

   /** Éclat de verre : tourne sur lui-même, retombe, rebondit une fois, brille par intermittence. */
   static class Shard extends TextureSheetParticle {
      private final float spin;
      private boolean bounced;

      Shard(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy;
         this.zd = vz;
         this.lifetime = 40 + random.nextInt(30);
         this.gravity = 0.9F;
         this.quadSize = 0.08F + random.nextFloat() * 0.1F;
         this.spin = (random.nextFloat() - 0.5F) * 0.6F;
         this.setColor(0.8F, 1F, 0.95F);
      }

      @Override
      public void tick() {
         super.tick();
         this.oRoll = roll;
         if (!onGround) roll += spin;
         if (onGround && !bounced) {
            bounced = true;
            this.yd = 0.12;
            this.xd *= 0.4;
            this.zd *= 0.4;
         }
         float k = (float) age / lifetime;
         this.alpha = k > 0.7F ? (1 - k) / 0.3F : 0.9F;
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      protected int getLightColor(float partial) {
         return (age / 3) % 4 == 0 ? FULL_BRIGHT : super.getLightColor(partial);
      }
   }

   /** Braise radioactive : monte en ondulant et scintille. */
   static class Ember extends TextureSheetParticle {
      private final float phase;

      Ember(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy + 0.03 + random.nextDouble() * 0.03;
         this.zd = vz;
         this.lifetime = 30 + random.nextInt(30);
         this.quadSize = 0.05F + random.nextFloat() * 0.06F;
         this.phase = random.nextFloat() * 6.28F;
         this.friction = 0.96F;
         this.hasPhysics = false;
         this.setColor(0.75F, 1F, 0.35F);
      }

      @Override
      public void tick() {
         super.tick();
         this.xd += Mth.sin(age * 0.3F + phase) * 0.004;
         this.zd += Mth.cos(age * 0.27F + phase) * 0.004;
         float k = (float) age / lifetime;
         this.alpha = (1 - k) * (0.6F + 0.4F * Mth.sin(age * 0.8F + phase));
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      protected int getLightColor(float partial) {
         return FULL_BRIGHT;
      }
   }

   /** Feuille / éclat de cendre : part avec l'élan reçu, puis tombe en se balançant et en tournoyant. */
   static class Leaf extends TextureSheetParticle {
      private final float phase, spin;

      Leaf(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy;
         this.zd = vz;
         this.lifetime = 50 + random.nextInt(50);
         this.quadSize = 0.09F + random.nextFloat() * 0.08F;
         this.phase = random.nextFloat() * 6.28F;
         this.spin = (random.nextFloat() - 0.5F) * 0.3F;
         this.roll = random.nextFloat() * Mth.TWO_PI;
         this.friction = 0.93F;
         this.gravity = 0.06F;
      }

      @Override
      public void tick() {
         super.tick();
         this.oRoll = roll;
         if (!onGround) roll += spin * Mth.sin(age * 0.15F + phase);
         this.xd += Mth.sin(age * 0.18F + phase) * 0.006;
         this.zd += Mth.cos(age * 0.14F + phase) * 0.006;
         this.alpha = Math.min(1, (lifetime - age) / 12F);
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }
   }
}
