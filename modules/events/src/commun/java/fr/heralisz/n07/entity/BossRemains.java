package fr.heralisz.n07.entity;

import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Dépouille d'un boss abattu (Sujet N-07 : sa statue de trinitite) : reste figée sur place quelques minutes, puis
 * s'effrite. Pas de collision, pas de dégâts : un monument le temps que les joueurs ramassent le butin.
 */
public class BossRemains extends Entity implements GeoEntity {
   public enum Kind {
      SUJET_N07("sujet_n07", 5 * 60 * 20);

      public final String id;
      public final int life;
      final RawAnimation pose;

      Kind(String id, int life) {
         this.id = id;
         this.life = life;
         this.pose = RawAnimation.begin().thenLoop("animation." + id + ".dead");
      }
   }

   /** Dernières secondes : il s'effrite et s'enfonce. */
   public static final int CRUMBLE = 60;
   static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BossRemains.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(BossRemains.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(BossRemains.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(BossRemains.class, EntityDataSerializers.INT);

   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

   public BossRemains(EntityType<? extends BossRemains> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   @Override
   protected void defineSynchedData() {
      this.entityData.define(KIND, 0);
      this.entityData.define(VARIANT, 3);
      this.entityData.define(AGE, 0);
      this.entityData.define(LIFE, 6000);
   }

   /** Durée totale (ticks) avant qu'il ne disparaisse. */
   public int life() {
      return Math.max(CRUMBLE + 20, this.entityData.get(LIFE));
   }

   public Kind kind() {
      return Kind.values()[Mth.clamp(this.entityData.get(KIND), 0, Kind.values().length - 1)];
   }

   public int variant() {
      return this.entityData.get(VARIANT);
   }

   /** Âge synchronisé (toutes les secondes, puis à chaque tick pendant l'effritement) ; côté serveur : BossRemainsServeur. */
   public int age() {
      return this.entityData.get(AGE);
   }

   /** 0 → 1 pendant l'effritement final. */
   public float crumble(float partial) {
      float a = age() + (this.level().isClientSide ? partial : 0);
      return Mth.clamp((a - (life() - CRUMBLE)) / CRUMBLE, 0, 1);
   }

   /** Côté client : vapeur, braises, éclats. Le vieillissement, les sons et la disparition sont dans BossRemainsServeur. */
   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) clientEffects();
   }

   /** Verre encore chaud : vapeur, braises radioactives ; à la fin il se fend en éclats verts. */
   void clientEffects() {
      if (random.nextInt(3) == 0) this.level().addParticle(ModParticles.RAD_EMBER.get(), getX() + (random.nextDouble() - 0.5) * 3, getY() + random.nextDouble() * 5,
         getZ() + (random.nextDouble() - 0.5) * 3, 0, 0.02, 0);
      if (age() < 400 && random.nextInt(6) == 0) this.level().addParticle(ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5) * 3, getY() + 1 + random.nextDouble() * 4,
         getZ() + (random.nextDouble() - 0.5) * 3, 0, 0.02, 0);
      if (crumble(0) > 0) this.level().addParticle(ModParticles.GLASS_SHARD.get(), getX() + (random.nextDouble() - 0.5) * 3, getY() + random.nextDouble() * 5,
         getZ() + (random.nextDouble() - 0.5) * 3, 0, 0.02, 0);
   }

   @Override
   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(7, 12, 7);
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 160 * 160;
   }

   /** Sauvegarde : dans BossRemainsServeur (jamais lue ni écrite côté client). */
   @Override
   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   @Override
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return NetworkHooks.getEntitySpawningPacket(this);
   }

   // ------------------------------------------------------------------ GeckoLib
   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
      reg.add(new AnimationController<>(this, "pose", 0, s -> s.setAndContinue(kind().pose)));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }
}
