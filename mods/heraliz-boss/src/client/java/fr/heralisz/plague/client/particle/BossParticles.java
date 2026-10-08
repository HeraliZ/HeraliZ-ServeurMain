package fr.heralisz.plague.client.particle;

import fr.heralisz.plague.particle.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
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
      e.registerSpriteSet(ModParticles.OATH_EMBER.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new FireEmber(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.OATH_FLAME.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Flame(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.OATH_ASH.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Leaf(l, x, y, z, vx, vy, vz, s), 0.8F, 0.78F, 0.76F));
      e.registerSpriteSet(ModParticles.OATH_SMOKE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> charcoal(new Smoke(l, x, y, z, vx, vy, vz, s)));
      e.registerSpriteSet(ModParticles.OATH_CINDER.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Spark(l, x, y, z, vx, vy, vz, s), 1F, 0.62F, 0.28F));
      e.registerSpriteSet(ModParticles.SEA_SPRAY.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Droplet(l, x, y, z, vx, vy, vz, s, 0.78F, 0.9F, 1F, ModParticles.SEA_RING.get(), false));
      e.registerSpriteSet(ModParticles.SEA_RING.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Splash(l, x, y, z, s), 0.82F, 0.93F, 1F));
      e.registerSpriteSet(ModParticles.SEA_BUBBLE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Bubble(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.SEA_FOAM.get(), s -> (t, l, x, y, z, vx, vy, vz) -> foam(new Smoke(l, x, y, z, vx, vy, vz, s)));
      e.registerSpriteSet(ModParticles.INK.get(), s -> (t, l, x, y, z, vx, vy, vz) -> ink(new Smoke(l, x, y, z, vx, vy, vz, s)));
      e.registerSpriteSet(ModParticles.STEAM.get(), s -> (t, l, x, y, z, vx, vy, vz) -> steam(new Smoke(l, x, y, z, vx, vy, vz, s)));
      e.registerSpriteSet(ModParticles.STONE_CHIP.get(), s -> (t, l, x, y, z, vx, vy, vz) -> chip(new Chip(l, x, y, z, vx, vy, vz, s), 0.5F, 0.48F, 0.45F));
      e.registerSpriteSet(ModParticles.WOOD_CHIP.get(), s -> (t, l, x, y, z, vx, vy, vz) -> chip(new Chip(l, x, y, z, vx, vy, vz, s), 0.46F, 0.33F, 0.2F));
      e.registerSpriteSet(ModParticles.MAGMA.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Magma(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.TESLA_SPARK.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Tesla(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.MOTE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> mote(new Ember(l, x, y, z, vx, vy, vz, s)));
      e.registerSpriteSet(ModParticles.SPIRIT_FLAME.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new SpiritFlame(l, x, y, z, vx, vy, vz, s));
      e.registerSpriteSet(ModParticles.AMBER_DROP.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Droplet(l, x, y, z, vx, vy, vz, s, 1F, 0.66F, 0.18F, ModParticles.AMBER_RING.get(), true));
      e.registerSpriteSet(ModParticles.AMBER_RING.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Splash(l, x, y, z, s), 1F, 0.7F, 0.25F));
      e.registerSpriteSet(ModParticles.BLOOD.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Droplet(l, x, y, z, vx, vy, vz, s, 0.45F, 0.04F, 0.05F, null, false));
      e.registerSpriteSet(ModParticles.FLESH_CHIP.get(), s -> (t, l, x, y, z, vx, vy, vz) -> chip(new Chip(l, x, y, z, vx, vy, vz, s), 0.42F, 0.1F, 0.1F));
      e.registerSpriteSet(ModParticles.BONE_CHIP.get(), s -> (t, l, x, y, z, vx, vy, vz) -> chip(new Chip(l, x, y, z, vx, vy, vz, s), 0.82F, 0.76F, 0.62F));
      e.registerSpriteSet(ModParticles.WEB_FLUFF.get(), s -> (t, l, x, y, z, vx, vy, vz) -> tint(new Leaf(l, x, y, z, vx, vy, vz, s), 0.9F, 0.9F, 0.86F));
   }

   static final int FULL_BRIGHT = 0xF000F0;

   /** Poussière de terre : lourde, brune, retombe lentement. */
   private static TextureSheetParticle dust(Smoke p) {
      float g = 0.7F + p.random().nextFloat() * 0.3F;
      p.setColor(0.28F * g, 0.22F * g, 0.16F * g);
      p.heavy = true;
      return p;
   }

   /** Fumée de charbon : épaisse, gris-noir, monte lentement. */
   private static TextureSheetParticle charcoal(Smoke p) {
      float g = 0.12F + p.random().nextFloat() * 0.1F;
      p.setColor(g, g * 0.95F, g * 0.9F);
      p.heavy = false;
      return p;
   }

   /** Écume : blanche, légère, se dissipe vite au ras de l'eau. */
   private static TextureSheetParticle foam(Smoke p) {
      float g = 0.85F + p.random().nextFloat() * 0.15F;
      p.setColor(g * 0.92F, g * 0.97F, g);
      p.heavy = false;
      p.shorten(0.6F);
      return p;
   }

   /** Encre : nuage épais noir-violet, lourd, qui s'étale. */
   private static TextureSheetParticle ink(Smoke p) {
      float g = 0.05F + p.random().nextFloat() * 0.05F;
      p.setColor(g * 1.1F, g * 0.7F, g * 1.6F);
      p.heavy = true;
      p.grow(1.8F);
      return p;
   }

   /** Vapeur : blanc grisé, monte et s'étire. */
   private static TextureSheetParticle steam(Smoke p) {
      float g = 0.7F + p.random().nextFloat() * 0.25F;
      p.setColor(g, g, g * 1.02F);
      p.heavy = false;
      return p;
   }

   /** Lueur : point doux cyan-vert qui dérive (lucioles, résidus magiques). */
   private static TextureSheetParticle mote(Ember p) {
      p.setColor(0.45F, 1F, 0.85F);
      p.slow();
      return p;
   }

   private static TextureSheetParticle chip(Chip p, float r, float g, float b) {
      float k = 0.75F + p.random().nextFloat() * 0.4F;
      p.setColor(r * k, g * k, b * k);
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

      private float growth = 1.8F;

      void shorten(float k) {
         this.lifetime = Math.max(6, (int) (lifetime * k));
      }

      void grow(float k) {
         this.growth = k * 1.8F;
         this.lifetime += 20;
      }

      @Override
      public void tick() {
         super.tick();
         float k = (float) this.age / this.lifetime;
         this.oRoll = this.roll;
         this.roll += spin;
         this.quadSize = baseSize * (1 + k * growth);
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

      void slow() {
         this.yd *= 0.3;
         this.lifetime += 40;
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

   /** Braise du Serment : monte en vacillant, jaune-blanc puis orange puis rouge sombre avant de s'éteindre. */
   static class FireEmber extends Ember {
      FireEmber(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z, vx, vy, vz, s);
         this.quadSize *= 1.3F;
      }

      @Override
      public void tick() {
         super.tick();
         float k = (float) age / lifetime;
         this.setColor(1F, Mth.lerp(k, 0.85F, 0.25F), Mth.lerp(k, 0.55F, 0.08F));
      }
   }

   /** Flammèche : langue de feu animée qui s'élève, grandit puis rougit et se dissipe. */
   static class Flame extends TextureSheetParticle {
      private final SpriteSet sprites;
      private final float baseSize;

      Flame(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.sprites = s;
         this.xd = vx + (random.nextDouble() - 0.5) * 0.01;
         this.yd = vy + 0.02 + random.nextDouble() * 0.02;
         this.zd = vz + (random.nextDouble() - 0.5) * 0.01;
         this.lifetime = 14 + random.nextInt(12);
         this.baseSize = 0.28F + random.nextFloat() * 0.3F;
         this.quadSize = baseSize;
         this.friction = 0.9F;
         this.hasPhysics = false;
         this.setSpriteFromAge(s);
      }

      @Override
      public void tick() {
         super.tick();
         if (!this.removed) this.setSpriteFromAge(sprites);
         float k = (float) age / lifetime;
         this.quadSize = baseSize * (0.7F + 0.6F * Mth.sin(Math.min(1F, k * 1.3F) * Mth.PI));
         this.setColor(1F, Mth.lerp(k, 0.95F, 0.35F), Mth.lerp(k, 0.8F, 0.15F));
         this.alpha = Math.min(1F, age / 3F) * (1 - k * k);
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

   /** Gouttelette (eau, ambre, sang) : jaillit, retombe, éclate en anneau au sol ou à la surface de l'eau. */
   static class Droplet extends TextureSheetParticle {
      private final net.minecraft.core.particles.ParticleOptions ring;
      private final boolean bright;

      Droplet(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s, float r, float g, float b,
         net.minecraft.core.particles.ParticleOptions ring, boolean bright) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy;
         this.zd = vz;
         this.lifetime = 50 + random.nextInt(20);
         this.gravity = 0.9F;
         this.friction = 0.98F;
         this.quadSize = 0.06F + random.nextFloat() * 0.06F;
         float k = 0.85F + random.nextFloat() * 0.15F;
         this.setColor(r * k, g * k, b * k);
         this.alpha = 0.85F;
         this.ring = ring;
         this.bright = bright;
      }

      @Override
      public void tick() {
         super.tick();
         if (this.removed) return;
         boolean wet = !this.level.getFluidState(net.minecraft.core.BlockPos.containing(x, y, z)).isEmpty();
         if (this.onGround || wet && yd < 0) {
            if (ring != null && random.nextInt(3) == 0) this.level.addParticle(ring, x, wet ? Math.floor(y) + 0.95 : y + 0.02, z, 0, 0, 0);
            this.remove();
         }
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }

      @Override
      protected int getLightColor(float partial) {
         return bright ? FULL_BRIGHT : super.getLightColor(partial);
      }
   }

   /** Bulle : monte en zigzaguant, éclate en sortant de l'eau (ou au bout de sa vie). */
   static class Bubble extends TextureSheetParticle {
      private final float phase;

      Bubble(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx * 0.5;
         this.yd = vy * 0.5 + 0.04 + random.nextDouble() * 0.03;
         this.zd = vz * 0.5;
         this.lifetime = 30 + random.nextInt(40);
         this.quadSize = 0.05F + random.nextFloat() * 0.09F;
         this.phase = random.nextFloat() * 6.28F;
         this.friction = 0.92F;
         this.gravity = -0.12F;
         this.hasPhysics = false;
         this.setColor(0.8F, 0.92F, 1F);
         this.alpha = 0.8F;
      }

      @Override
      public void tick() {
         super.tick();
         if (this.removed) return;
         this.xd += Mth.sin(age * 0.6F + phase) * 0.006;
         this.zd += Mth.cos(age * 0.55F + phase) * 0.006;
         boolean wet = !this.level.getFluidState(net.minecraft.core.BlockPos.containing(x, y, z)).isEmpty();
         if (!wet && age > 3) {
            if (random.nextInt(2) == 0) this.level.addParticle(ModParticles.SEA_RING.get(), x, y, z, 0, 0, 0);
            this.remove();
         }
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }
   }

   /** Éclat (roche, bois) : part en tournoyant, retombe, rebondit une fois, reste un instant au sol. */
   static class Chip extends TextureSheetParticle {
      private final float spin;
      private boolean bounced;

      net.minecraft.util.RandomSource random() {
         return random;
      }

      Chip(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy;
         this.zd = vz;
         this.lifetime = 40 + random.nextInt(40);
         this.gravity = 1.0F;
         this.quadSize = 0.07F + random.nextFloat() * 0.11F;
         this.spin = (random.nextFloat() - 0.5F) * 0.7F;
         this.roll = random.nextFloat() * Mth.TWO_PI;
      }

      @Override
      public void tick() {
         super.tick();
         this.oRoll = roll;
         if (!onGround) roll += spin;
         if (onGround && !bounced) {
            bounced = true;
            this.yd = 0.1;
            this.xd *= 0.35;
            this.zd *= 0.35;
         }
         float k = (float) age / lifetime;
         this.alpha = k > 0.8F ? (1 - k) / 0.2F : 1F;
      }

      @Override
      public ParticleRenderType getRenderType() {
         return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
      }
   }

   /** Goutte de magma : saute, retombe en fumant, s'assombrit en refroidissant. */
   static class Magma extends TextureSheetParticle {
      Magma(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.pickSprite(s);
         this.xd = vx + (random.nextDouble() - 0.5) * 0.12;
         this.yd = vy + 0.18 + random.nextDouble() * 0.12;
         this.zd = vz + (random.nextDouble() - 0.5) * 0.12;
         this.lifetime = 24 + random.nextInt(20);
         this.gravity = 0.75F;
         this.quadSize = 0.08F + random.nextFloat() * 0.1F;
      }

      @Override
      public void tick() {
         super.tick();
         float k = (float) age / lifetime;
         this.setColor(1F, Mth.lerp(k, 0.75F, 0.22F), Mth.lerp(k, 0.3F, 0.05F));
         if (random.nextInt(6) == 0) this.level.addParticle(ModParticles.OATH_SMOKE.get(), x, y, z, 0, 0.02, 0);
         if (onGround) this.age = Math.max(age, lifetime - 6);
      }

      @Override
      public float getQuadSize(float p) {
         return quadSize * (1 - (age + p) / lifetime * 0.5F);
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

   /** Arc électrique : éclair bleu-blanc qui claque quelques ticks, change de forme et clignote. */
   static class Tesla extends TextureSheetParticle {
      private final SpriteSet sprites;

      Tesla(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z);
         this.sprites = s;
         this.pickSprite(s);
         this.xd = vx;
         this.yd = vy;
         this.zd = vz;
         this.lifetime = 4 + random.nextInt(5);
         this.quadSize = 0.18F + random.nextFloat() * 0.22F;
         this.roll = random.nextFloat() * Mth.TWO_PI;
         this.oRoll = roll;
         this.friction = 0.7F;
         this.hasPhysics = false;
         this.setColor(0.72F, 0.88F, 1F);
      }

      @Override
      public void tick() {
         super.tick();
         if (this.removed) return;
         if (random.nextInt(2) == 0) {
            this.pickSprite(sprites);
            this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
         }
         this.alpha = random.nextInt(4) == 0 ? 0.35F : 1F;
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

   /** Flamme spectrale : la flammèche du Serment, bleu pâle. */
   static class SpiritFlame extends Flame {
      SpiritFlame(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
         super(l, x, y, z, vx, vy, vz, s);
      }

      @Override
      public void tick() {
         super.tick();
         float k = (float) age / lifetime;
         this.setColor(Mth.lerp(k, 0.75F, 0.2F), Mth.lerp(k, 0.95F, 0.55F), 1F);
      }
   }
}
