package fr.heraliz.crac.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A thrown raiding explosive (C4, Explosive Charge) that sticks to the first block or entity it hits and goes off when
 * its fuse runs out. The stuck state and the fuse are saved with the entity, so an armed charge survives a chunk
 * unload or a restart, and the fuse is synced to clients (length and arming time) for the countdown display.
 */
public abstract class StickyExplosiveEntity extends ThrowableItemProjectile implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_STUCK = SynchedEntityData.defineId(StickyExplosiveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_FACE = SynchedEntityData.defineId(StickyExplosiveEntity.class, EntityDataSerializers.BYTE);
    /** Fuse length in ticks, fixed when the explosive sticks. */
    private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData.defineId(StickyExplosiveEntity.class, EntityDataSerializers.INT);
    /** Game time the fuse started at, moved forward after a reload so that the countdown stays right. */
    private static final EntityDataAccessor<Long> DATA_ARMED_AT = SynchedEntityData.defineId(StickyExplosiveEntity.class, EntityDataSerializers.LONG);
    /** Went out instead of exploding (explosive charge): stays put until a player takes it back. */
    private static final EntityDataAccessor<Boolean> DATA_DUD = SynchedEntityData.defineId(StickyExplosiveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG_STUCK = "Stuck";

    private boolean stuck;
    @Nullable
    private BlockPos targetPos;
    /** Where the explosive touched the block: picks the piece it is stuck to when several share that block. */
    @Nullable
    private Vec3 targetHit;
    @Nullable
    private Direction stuckFace;
    private int fuseTicks;
    private int fuseLength;
    private boolean dud;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected StickyExplosiveEntity(EntityType<? extends StickyExplosiveEntity> type, Level level) {
        super(type, level);
    }

    protected StickyExplosiveEntity(EntityType<? extends StickyExplosiveEntity> type, LivingEntity owner, Level level) {
        super(type, owner, level);
    }

    /** Configured fuse in seconds, read when the explosive sticks. */
    protected abstract double configuredFuseSeconds();

    /** Fuse length in ticks drawn when the explosive sticks (default: the configured fuse). */
    protected int drawFuseTicks() {
        return Fuse.ticks(this.configuredFuseSeconds());
    }

    /** Whether this explosive goes out when its fuse runs out instead of exploding (drawn then, server side). */
    protected boolean drawDud() {
        return false;
    }

    /** Sound when the fuse goes out. */
    protected void playDudSound() {
    }

    /** Damage when the fuse runs out: {@code pos} is the block it is stuck to, {@code face} null when stuck to an entity. */
    protected abstract void explode(ServerLevel level, BlockPos pos, Vec3 hit, @Nullable Direction face);

    protected abstract void playArmSound();

    /** GeckoLib animation looped while armed. */
    protected abstract String blinkAnimation();

    // ------------------------------------------------------------------ state

    public boolean isStuck() {
        return this.level().isClientSide ? this.entityData.get(DATA_STUCK) : this.stuck;
    }

    @Nullable
    public Direction getStuckFace() {
        if (this.level().isClientSide) {
            byte b = this.entityData.get(DATA_FACE);
            return b >= 0 ? Direction.from3DDataValue(b) : null;
        }
        return this.stuckFace;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    @Nullable
    public Vec3 getTargetHit() {
        return this.targetHit;
    }

    /** Whether the fuse went out: nothing burns any more and a player can take the explosive back. */
    public boolean isDud() {
        return this.level().isClientSide ? this.entityData.get(DATA_DUD) : this.dud;
    }

    /** Ticks the fuse has burnt (server side). */
    public int getFuseTicks() {
        return this.fuseTicks;
    }

    /** Fuse length in ticks once stuck (synced to clients), 0 before. */
    public int getFuseLength() {
        return this.level().isClientSide ? this.entityData.get(DATA_FUSE) : this.fuseLength;
    }

    /** Seconds the fuse has been burning, or -1 when the explosive is not stuck. */
    public double fuseElapsedSeconds(float partialTick) {
        if (!this.isStuck() || this.isDud()) {
            return -1.0;
        }
        return Fuse.elapsedSeconds(this.level().getGameTime(), this.entityData.get(DATA_ARMED_AT), partialTick, this.getFuseLength());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STUCK, false);
        this.entityData.define(DATA_FACE, (byte) -1);
        this.entityData.define(DATA_FUSE, 0);
        this.entityData.define(DATA_ARMED_AT, 0L);
        this.entityData.define(DATA_DUD, false);
    }

    private void syncStuck() {
        this.entityData.set(DATA_STUCK, this.stuck);
        this.entityData.set(DATA_FACE, this.stuckFace == null ? (byte) -1 : (byte) this.stuckFace.get3DDataValue());
        this.entityData.set(DATA_FUSE, this.fuseLength);
        this.entityData.set(DATA_ARMED_AT, this.level().getGameTime() - this.fuseTicks);
        this.entityData.set(DATA_DUD, this.dud);
    }

    // ------------------------------------------------------------------ life cycle

    @Override
    public void tick() {
        if (this.isStuck()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(true);
            if (this.isDud()) {
                super.tick();
                return;
            }
            if (this.level() instanceof ServerLevel sl && ++this.fuseTicks >= this.fuseLength) {
                if (this.drawDud()) {
                    this.dud = true;
                    this.syncStuck();
                    this.playDudSound();
                    return;
                }
                BlockPos pos = this.targetPos != null ? this.targetPos : this.blockPosition();
                Vec3 hit = this.targetHit != null ? this.targetHit : this.position();
                fr.heraliz.crac.pont.Serveur.Moteur m = fr.heraliz.crac.pont.Serveur.MOTEUR;
                if (m != null) {
                    m.avantDetonation(sl, pos, this.getOwner());
                }
                this.explode(sl, pos, hit, this.stuckFace);
                this.discard();
                return;
            }
        }
        super.tick();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (!this.stuck) {
            this.stickTo(hit.getBlockPos(), hit.getLocation(), hit.getDirection());
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!this.stuck) {
            Vec3 at = hit.getEntity().position();
            this.stickTo(BlockPos.containing(at), at, null);
        }
    }

    /**
     * Sticks the explosive at {@code hit} on {@code face} of the block at {@code pos} ({@code face} null: stuck to an
     * entity standing there) and lights the fuse.
     */
    public void stickTo(BlockPos pos, Vec3 hit, @Nullable Direction face) {
        this.stuck = true;
        this.targetPos = pos.immutable();
        this.targetHit = hit;
        this.stuckFace = face;
        if (face != null) {
            Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
            this.setPos(hit.x + n.x * 0.001, hit.y + n.y * 0.001, hit.z + n.z * 0.001);
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.fuseTicks = 0;
        this.fuseLength = Math.max(Fuse.MIN_TICKS, this.drawFuseTicks());
        this.syncStuck();
        this.playArmSound();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!this.stuck) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("Fuse", this.fuseTicks);
        t.putInt("FuseLength", this.fuseLength);
        t.putBoolean("Dud", this.dud);
        if (this.targetPos != null) {
            t.putLong("Target", this.targetPos.asLong());
        }
        if (this.targetHit != null) {
            t.putDouble("HitX", this.targetHit.x);
            t.putDouble("HitY", this.targetHit.y);
            t.putDouble("HitZ", this.targetHit.z);
        }
        t.putByte("Face", this.stuckFace == null ? (byte) -1 : (byte) this.stuckFace.get3DDataValue());
        tag.put(TAG_STUCK, t);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains(TAG_STUCK, Tag.TAG_COMPOUND)) {
            if (this.isNoGravity()) {
                // armed and saved before the stuck state was kept: let it drop and stick again instead of hanging forever
                this.setNoGravity(false);
            }
            return;
        }
        CompoundTag t = tag.getCompound(TAG_STUCK);
        this.stuck = true;
        this.fuseLength = Math.max(Fuse.MIN_TICKS, t.getInt("FuseLength"));
        this.fuseTicks = Math.max(0, Math.min(this.fuseLength - 1, t.getInt("Fuse")));
        this.dud = t.getBoolean("Dud");
        this.targetPos = t.contains("Target", Tag.TAG_LONG) ? BlockPos.of(t.getLong("Target")) : this.blockPosition();
        this.targetHit = t.contains("HitX", Tag.TAG_DOUBLE) ? new Vec3(t.getDouble("HitX"), t.getDouble("HitY"), t.getDouble("HitZ")) : null;
        byte face = t.getByte("Face");
        this.stuckFace = face >= 0 && face < 6 ? Direction.from3DDataValue(face) : null;
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.syncStuck();
    }

    // ------------------------------------------------------------------ taking a dud back

    @Override
    public boolean isPickable() {
        return this.isDud();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.isDud() || !this.isAlive()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            ItemStack stack = new ItemStack(this.getDefaultItem());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
            this.discard();
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void registerControllers(ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            if (this.isStuck() && !this.isDud()) {
                state.setAnimation(RawAnimation.begin().thenLoop(this.blinkAnimation()));
                return PlayState.CONTINUE;
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
