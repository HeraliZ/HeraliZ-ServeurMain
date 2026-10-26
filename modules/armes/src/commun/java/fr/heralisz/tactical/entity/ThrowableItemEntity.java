package fr.heralisz.tactical.entity;

import java.util.Optional;
import java.util.function.Predicate;
import fr.heralisz.tactical.api.item.IThrowable;
import fr.heralisz.tactical.init.ModItems;
import fr.heralisz.tactical.item.throwable.EntityData;
import fr.heralisz.tactical.network.NetworkHandler;
import fr.heralisz.tactical.network.message.SCustomSound;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ThrowableItemEntity extends Projectile implements IEntityAdditionalSpawnData {
   private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(ThrowableItemEntity.class, EntityDataSerializers.ITEM_STACK);
   private int life = 100;
   private float gravity = 0.07F;
   private double bounceFactor = 0.75;
   private boolean shouldBounce = true;
   private boolean brokeOnGround = false;
   private float hitDamage = 1.0F;
   private ParticleOptions tailParticle = null;

   public ThrowableItemEntity(EntityType<? extends Projectile> type, LivingEntity shooter, Level level, int lifeTime) {
      super(type, level);
      this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
      this.setOwner(shooter);
      this.life = lifeTime;
   }

   public ThrowableItemEntity(EntityType<? extends Projectile> type, Level level) {
      super(type, level);
   }

   /**
    * Fusion (heraliz_tactique) : ce que fait le constructeur « lanceur » ci-dessus, pour une entité créée par son type
    * ({@code TYPE.create(level)} : version serveur dans un monde serveur, par l'usine).
    */
   public void lancePar(LivingEntity shooter, int lifeTime) {
      this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
      this.setOwner(shooter);
      this.life = lifeTime;
   }

   @NotNull
   public Packet<ClientGamePacketListener> getAddEntityPacket() {
      return NetworkHooks.getEntitySpawningPacket(this);
   }

   protected void defineSynchedData() {
      this.getEntityData().define(DATA_ITEM_STACK, ItemStack.EMPTY);
   }

   public void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
      super.addAdditionalSaveData(pCompound);
      ItemStack itemstack = this.getItemRaw();
      if (!itemstack.isEmpty()) {
         pCompound.put("Item", itemstack.save(new CompoundTag()));
      }
   }

   public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
      super.readAdditionalSaveData(pCompound);
      ItemStack itemstack = ItemStack.of(pCompound.getCompound("Item"));
      this.setItem(itemstack);
   }

   protected ItemStack getItemRaw() {
      return (ItemStack)this.getEntityData().get(DATA_ITEM_STACK);
   }

   public ItemStack getItem() {
      ItemStack itemstack = this.getItemRaw();
      return itemstack.isEmpty() ? new ItemStack(this.getDefaultItem()) : itemstack;
   }

   public void setItem(ItemStack pStack) {
      if (!pStack.is(this.getDefaultItem()) || pStack.hasTag()) {
         this.getEntityData().set(DATA_ITEM_STACK, pStack.copyWithCount(1));
      }
   }

   protected Item getDefaultItem() {
      return (Item)ModItems.THROWABLE.get();
   }

   protected void onHit(HitResult result) {
      if (result.getType() != Type.MISS) {
         boolean flag = this.brokeOnGround && result instanceof BlockHitResult blockHitResult && blockHitResult.getDirection() == Direction.UP;
         if (!this.shouldBounce || flag) {
            this.onDeath(result);
            return;
         }
      }

      this.impact(result);
   }

   /**
    * Fusion (heraliz_tactique) : effets d'un impact (sons de rebond, coup sur l'entité touchée, réaction du bloc). Neutre ici
    * (le client n'en faisait rien de visible : sons joués pour « personne », coups ignorés côté client) ; la partie serveur
    * de chaque lancer le redéfinit avec le code d'origine (LancersServeur.impact).
    */
   protected void impact(HitResult result) {
   }

   public ThrowableItemEntity.BounceResult doMultiBounce(Vec3 deltaMovement) {
      Vec3 start = this.position();
      Vec3 end = start.add(deltaMovement);
      Vec3 endVecOffset = new Vec3(deltaMovement.x, deltaMovement.y, deltaMovement.z);

      for (int i = 0; i < 3; i++) {
         HitResult hitResult = this.getHitResult(start, end, endVecOffset, x$0 -> this.canHitEntity(x$0), this.level());
         if (hitResult.getType() == Type.BLOCK) {
            BlockHitResult blockResult = (BlockHitResult)hitResult;
            Vec3 hit = blockResult.getLocation();
            if (blockResult.getDirection() == Direction.UP && start.y() - hit.y() < 0.01) {
               hit = new Vec3(hit.x(), start.y(), hit.z());
            }

            if (i < 2) {
               start = start.lerp(hit, 0.8);
               Vec3 rest = end.subtract(start);
               endVecOffset = this.bounce(blockResult.getDirection(), rest);
               end = start.add(endVecOffset);
               deltaMovement = this.bounce(blockResult.getDirection(), deltaMovement);
            } else {
               end = start.lerp(hit, 0.8);
               deltaMovement = Vec3.ZERO;
            }
         } else if (hitResult.getType() == Type.ENTITY) {
            EntityHitResult entityResult = (EntityHitResult)hitResult;
            Entity entity = entityResult.getEntity();
            if (entity == this.getOwner() || entity == this.getVehicle()) {
               break;
            }

            Direction direction = Direction.getNearest(endVecOffset.x(), endVecOffset.y(), endVecOffset.z()).getOpposite();
            Vec3 hit = hitResult.getLocation();
            start = start.lerp(hit, 0.8);
            Vec3 rest = end.subtract(start);
            endVecOffset = this.bounce(direction, rest);
            end = start.add(endVecOffset);
            deltaMovement = this.bounce(direction, deltaMovement);
         } else if (hitResult.getType() == Type.MISS) {
            break;
         }

         this.onHit(hitResult);
      }

      return new ThrowableItemEntity.BounceResult(end, deltaMovement);
   }

   public Vec3 bounce(Direction direction, Vec3 deltaMovement) {
      double factor = this.getBounceFactor();

      return switch (direction.getAxis()) {
         case X -> deltaMovement.multiply(-factor / 1.5, factor, factor);
         case Y -> {
            Vec3 newVec = deltaMovement.multiply(factor, -factor / 2.5, factor);
            if (newVec.y() < this.getGravity()) {
               newVec = newVec.multiply(1.0, 0.0, 1.0);
            }

            yield newVec;
         }
         case Z -> deltaMovement.multiply(factor, factor, -factor / 1.5);
         default -> throw new IncompatibleClassChangeError();
      };
   }

   public HitResult getHitResult(Vec3 start, Vec3 end, Vec3 endVecOffset, Predicate<Entity> pFilter, Level pLevel) {
      HitResult hitresult = pLevel.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, this));
      if (hitresult.getType() != Type.MISS) {
         end = hitresult.getLocation();
      }

      HitResult hitresult1 = getEntityHitResult(pLevel, this, start, end, this.getBoundingBox().expandTowards(endVecOffset).inflate(1.0), pFilter);
      if (hitresult1 != null) {
         hitresult = hitresult1;
      }

      return hitresult;
   }

   @Nullable
   public static EntityHitResult getEntityHitResult(
      Level pLevel, Entity pProjectile, Vec3 pStartVec, Vec3 pEndVec, AABB pBoundingBox, Predicate<Entity> pFilter
   ) {
      double d0 = Double.MAX_VALUE;
      Entity entity = null;
      Vec3 hitPos = null;

      for (Entity entity1 : pLevel.getEntities(pProjectile, pBoundingBox, pFilter)) {
         AABB aabb = entity1.getBoundingBox().inflate(0.3);
         Optional<Vec3> optional = aabb.clip(pStartVec, pEndVec);
         if (optional.isPresent()) {
            double d1 = pStartVec.distanceToSqr(optional.get());
            if (d1 < d0) {
               entity = entity1;
               d0 = d1;
               hitPos = optional.get();
            }
         }
      }

      return entity == null ? null : new EntityHitResult(entity, hitPos);
   }

   public void playBounceSound() {
   }

   public void tick() {
      super.tick();
      ThrowableItemEntity.BounceResult result = this.doMultiBounce(this.getDeltaMovement());
      this.checkInsideBlocks();
      Vec3 vec3 = result.deltaMovement();
      double x = result.location().x();
      double y = result.location().y();
      double z = result.location().z();
      this.setDeltaMovement(vec3);
      this.updateRotation();
      float f;
      if (this.isInWater()) {
         for (int i = 0; i < 4; i++) {
            this.level().addParticle(ParticleTypes.BUBBLE, x - vec3.x * 0.25, y - vec3.y * 0.25, z - vec3.z * 0.25, vec3.x, vec3.y, vec3.z);
         }

         f = 0.8F;
      } else {
         f = 0.99F;
      }

      this.setDeltaMovement(vec3.scale(f));
      if (!this.isNoGravity()) {
         Vec3 vec31 = this.getDeltaMovement();
         this.setDeltaMovement(vec31.x, vec31.y - this.getGravity(), vec31.z);
      }

      this.setPos(x, y, z);
      if (this.tickCount >= this.life && this.life >= 0 && !this.level().isClientSide()) {
         this.onDeath(null);
      }

      if (this.level().isClientSide()) {
         this.renderTailParticle();
      }
   }

   public void renderTailParticle() {
      if (this.getTailParticle() != null) {
         this.level().addParticle(this.getTailParticle(), true, this.getX(), this.getY() + 0.35, this.getZ(), 0.0, 0.01, 0.0);
      }
   }

   public void onDeath(@Nullable HitResult hitResult) {
      this.discard();
      if (!this.level().isClientSide()) {
         this.playThrowableSound("death", 16.0F, 1.0F);
      }
   }

   @Deprecated
   public void onDeath() {
      this.onDeath(null);
   }

   public void playThrowableSound(String key, float volume, float pitch) {
      ItemStack stack = this.getItem();
      if (stack.getItem() instanceof IThrowable iThrowable) {
         ResourceLocation id = iThrowable.getId(stack);
         SCustomSound packet = new SCustomSound(SCustomSound.SoundType.THROWABLE, id, key, this.position(), volume, pitch);
         NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(TargetPoint.p(this.getX(), this.getY(), this.getZ(), 64.0, this.level().dimension())), packet);
      }
   }

   public boolean shouldRenderAtSqrDistance(double pDistance) {
      double d0 = this.getBoundingBox().getSize() * 4.0;
      if (Double.isNaN(d0)) {
         d0 = 4.0;
      }

      d0 *= 64.0;
      return pDistance < d0 * d0;
   }

   public void setBaseData(EntityData data) {
      this.setGravity(data.getGravity());
      this.setBounceFactor(data.getBounceFactor());
      this.setShouldBounce(data.isShouldBounce());
      this.setHitDamage(data.getHitDamage());
      this.setBrokeOnGround(data.isBrokeOnGround());
      this.setTailParticle(data.getTailParticles());
   }

   public float getGravity() {
      return this.gravity;
   }

   public void setGravity(float gravity) {
      this.gravity = gravity;
   }

   public double getBounceFactor() {
      return this.bounceFactor;
   }

   public void setBounceFactor(double bounceFactor) {
      this.bounceFactor = bounceFactor;
   }

   public boolean shouldBounce() {
      return this.shouldBounce;
   }

   public void setShouldBounce(boolean shouldBounce) {
      this.shouldBounce = shouldBounce;
   }

   public int getLife() {
      return this.life;
   }

   public void setLife(int life) {
      this.life = life;
   }

   public float getHitDamage() {
      return this.hitDamage;
   }

   public void setHitDamage(float hitDamage) {
      this.hitDamage = hitDamage;
   }

   public boolean isBrokeOnGround() {
      return this.brokeOnGround;
   }

   public void setBrokeOnGround(boolean brokeOnGround) {
      this.brokeOnGround = brokeOnGround;
   }

   public ParticleOptions getTailParticle() {
      return this.tailParticle;
   }

   public void setTailParticle(ParticleOptions tailParticle) {
      this.tailParticle = tailParticle;
   }

   public void writeSpawnData(FriendlyByteBuf buffer) {
      buffer.writeInt(this.life);
      buffer.writeFloat(this.gravity);
      buffer.writeDouble(this.bounceFactor);
      buffer.writeBoolean(this.shouldBounce);
      buffer.writeBoolean(this.brokeOnGround);
      if (this.tailParticle != null) {
         buffer.writeBoolean(true);
         EntityDataSerializers.PARTICLE.write(buffer, this.tailParticle);
      } else {
         buffer.writeBoolean(false);
      }
   }

   public void readSpawnData(FriendlyByteBuf additionalData) {
      this.life = additionalData.readInt();
      this.gravity = additionalData.readFloat();
      this.bounceFactor = additionalData.readDouble();
      this.shouldBounce = additionalData.readBoolean();
      this.brokeOnGround = additionalData.readBoolean();
      if (additionalData.readBoolean()) {
         this.tailParticle = (ParticleOptions)EntityDataSerializers.PARTICLE.read(additionalData);
      } else {
         this.tailParticle = null;
      }
   }

   public record BounceResult(Vec3 location, Vec3 deltaMovement) {
   }
}
