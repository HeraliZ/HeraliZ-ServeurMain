package fr.heraliz.airstrike.entity;

import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Heavy airdrop crate (3 x 3 blocks). Falls under its canopies - the kinematics mirror
 * {@link StrikePlanner#simulateCrate} so it lands where the planner expects - then stays on the ground as a
 * container. The first player to use it breaks it open: the straps snap, the lid springs up and the front and side
 * panels fall flat (reveal animation, then the inventory opens). The gear visible inside disappears as the crate is
 * looted. A green signal smoke marks it and it disappears after a while.
 */
public class SupplyCrateEntity extends Entity implements Container, MenuProvider {
    public static final int SLOTS = 27;
    /** Length of the break-open animation (ticks). */
    public static final int REVEAL_TICKS = 40;
    /** Decorative gear pieces in the model. */
    public static final int GEAR_PIECES = 6;

    /** Supply crate kinematics (moved from {@code StrikePlanner}, server part, which reads them from here). */
    public static final int CHUTE_DELAY = 22;
    public static final double CRATE_GRAVITY = 0.04;
    public static final double CRATE_AIR_DRAG = 0.985;
    public static final double CHUTE_DRAG_H = 0.9;
    public static final double CHUTE_SINK = -0.2;

    static final EntityDataAccessor<Integer> FALL_TICKS = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Long> LANDED_AT = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.LONG);
    static final EntityDataAccessor<Long> OPEN_AT = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.LONG);
    static final EntityDataAccessor<Integer> FILL = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> FILL_MAX = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> SMOKING = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.BOOLEAN);

    int fallTicks;
    private float revealFx = -1f;

    public SupplyCrateEntity(EntityType<? extends SupplyCrateEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FALL_TICKS, 0);
        entityData.define(LANDED, false);
        entityData.define(LANDED_AT, 0L);
        entityData.define(OPEN_AT, -1L);
        entityData.define(FILL, 0);
        entityData.define(FILL_MAX, 0);
        entityData.define(SMOKING, false);
    }

    public int getFallTicks() {
        return fallTicks;
    }

    /** Ticks since the crate touched down (synced, so late joiners see the canopies already collapsed). */
    public int getLandedTicks() {
        if (!isLanded()) return 0;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, level().getGameTime() - entityData.get(LANDED_AT)));
    }

    public boolean isLanded() {
        return entityData.get(LANDED);
    }

    public boolean isChuteOpen() {
        return fallTicks >= CHUTE_DELAY;
    }

    /** Game time at which the crate was broken open, -1 while sealed. */
    public long getOpenAt() {
        return entityData.get(OPEN_AT);
    }

    public boolean isRevealed() {
        long at = getOpenAt();
        return at >= 0 && level().getGameTime() - at >= REVEAL_TICKS;
    }

    /** Gear pieces still shown inside: follows how much loot is left. */
    public int gearPieces() {
        int max = entityData.get(FILL_MAX);
        if (max <= 0) return GEAR_PIECES;
        int fill = entityData.get(FILL);
        return fill <= 0 ? 0 : Math.max(1, (int) Math.ceil(GEAR_PIECES * fill / (double) max));
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (isLanded()) {
            landedTick();
            return;
        }
        if (level().isClientSide) {
            int synced = entityData.get(FALL_TICKS);
            if (Math.abs(synced - fallTicks) > 6) fallTicks = synced;
        }
        fallTicks++;
        Vec3 v = getDeltaMovement();
        double vx = v.x, vy = v.y, vz = v.z;
        if (fallTicks < CHUTE_DELAY) {
            vy -= CRATE_GRAVITY;
            vy *= 0.98;
            vx *= CRATE_AIR_DRAG;
            vz *= CRATE_AIR_DRAG;
        } else {
            if (fallTicks == CHUTE_DELAY && !level().isClientSide) {
                parachuteOuvert();
            }
            vx *= CHUTE_DRAG_H;
            vz *= CHUTE_DRAG_H;
            vy += (CHUTE_SINK - vy) * 0.15;
        }
        setDeltaMovement(vx, vy, vz);
        move(MoverType.SELF, getDeltaMovement());

        if (!level().isClientSide) {
            chuteServeur();
        }
    }

    /** Server: the canopy opens (sound). SupplyCrateEntityServeur; neutral here. */
    void parachuteOuvert() {
    }

    /** Server, after each falling step: sync of the fall, landing. SupplyCrateEntityServeur; neutral here. */
    void chuteServeur() {
    }

    /** Server, each tick once landed: smoke, fill, opening for the breaker, despawn. SupplyCrateEntityServeur; neutral here. */
    void poseServeur() {
    }

    private void landedTick() {
        // keeps resting on the ground (falls again if the ground under it is removed)
        if (!onGround()) {
            setDeltaMovement(0, Math.max(-1.5, getDeltaMovement().y - 0.06), 0);
            move(MoverType.SELF, getDeltaMovement());
        } else if (getDeltaMovement().lengthSqr() > 0) {
            setDeltaMovement(Vec3.ZERO);
        }
        if (level().isClientSide) {
            if (entityData.get(SMOKING)) signalSmoke();
            revealEffects();
            return;
        }
        poseServeur();
    }

    /** Local crate coordinates (blocks, -z = front) to world, following the crate yaw like the renderer. */
    public Vec3 local(double x, double y, double z) {
        double yaw = Math.toRadians(getYRot());
        return new Vec3(getX() + x * Math.cos(yaw) - z * Math.sin(yaw), getY() + y, getZ() + x * Math.sin(yaw) + z * Math.cos(yaw));
    }

    private void signalSmoke() {
        RandomSource rnd = random;
        // smoke pot on the back corner of the pallet, drifting away from the crate
        Vec3 p = local(1.75, 0.35, 1.75);
        Vec3 away = local(0.05, 0, 0.05).subtract(position());
        if (tickCount % 2 == 0) {
            level().addParticle(SmokeParticleOptions.of(0x3FBF4F, 0.9f + rnd.nextFloat() * 0.6f, 120 + rnd.nextInt(40), 0.12f, 0.95f),
                    p.x + (rnd.nextFloat() - 0.5) * 0.2, p.y, p.z + (rnd.nextFloat() - 0.5) * 0.2,
                    away.x + (rnd.nextFloat() - 0.5) * 0.02, 0.1, away.z + (rnd.nextFloat() - 0.5) * 0.02);
        }
        if (rnd.nextInt(6) == 0) level().addParticle(ParticleTypes.FLAME, p.x, p.y - 0.05, p.z, 0, 0.02, 0);
    }

    /** Client: bits of strap when they snap, dust when the panels slap the ground. */
    private void revealEffects() {
        long at = getOpenAt();
        if (at < 0) {
            revealFx = -1f;
            return;
        }
        float t = level().getGameTime() - at;
        float prev = revealFx;
        revealFx = t;
        if (prev < 0 && t > REVEAL_TICKS) return;   // joined after the animation
        RandomSource rnd = random;
        if (prev < 5 && t >= 5) {
            for (int i = 0; i < 16; i++) {
                Vec3 p = local((rnd.nextFloat() - 0.5) * 3.0, 2.2 + rnd.nextFloat() * 0.4, (rnd.nextFloat() - 0.5) * 3.0);
                level().addParticle(SmokeParticleOptions.of(0xC9A526, 0.35f, 18, 0.0f, 0.9f), p.x, p.y, p.z,
                        (rnd.nextFloat() - 0.5) * 0.3, 0.15 + rnd.nextFloat() * 0.2, (rnd.nextFloat() - 0.5) * 0.3);
            }
        }
        // panels hit the ground at 11 + 12, 14 + 12 and 16 + 12 ticks (see SupplyCrateRenderer#pose)
        double[][] impacts = {{23, 0, -1}, {26, -1, 0}, {28, 1, 0}};
        for (double[] im : impacts) {
            if (prev < im[0] && t >= im[0]) {
                for (int i = 0; i < 14; i++) {
                    double along = (rnd.nextFloat() - 0.5) * 2.9;
                    double out = 3.25;
                    Vec3 p = local(im[1] != 0 ? im[1] * out : along, 0.15, im[2] != 0 ? im[2] * out : along);
                    level().addParticle(SmokeParticleOptions.of(0xA89B80, 1.0f + rnd.nextFloat() * 0.8f, 30, 0.01f, 0.7f),
                            p.x, p.y, p.z, im[1] * 0.12 + (rnd.nextFloat() - 0.5) * 0.05, 0.03,
                            im[2] * 0.12 + (rnd.nextFloat() - 0.5) * 0.05);
                }
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // breaking open / opening: SupplyCrateEntityServeur (server part)
        if (!isLanded()) return InteractionResult.PASS;
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        // loot and contents: SupplyCrateEntityServeur (server part); only ever opened by the server
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.airstrike.supply_crate");
    }


    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    // Contents (loot, items): SupplyCrateEntityServeur (server part). Neutral here: an empty crate.
    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return isAlive() && isLanded() && player.distanceToSqr(this) < 8 * 8;
    }

    @Override
    public void clearContent() {
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // only creative players can remove a crate: SupplyCrateEntityServeur (server part)
        return false;
    }

    @Override
    public boolean isPickable() {
        return isLanded() && isAlive();
    }

    @Override
    public boolean canBeCollidedWith() {
        return isLanded();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Heavy crate: bombs going off next to it do not push it around. */
    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 320 * 320;
    }

    // Save data: SupplyCrateEntityServeur (server part). Neutral here (abstract in Entity).
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** The canopies spread far around the crate (above it, then downwind once landed). */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        if (isLanded() && getLandedTicks() > 600) return getBoundingBox().inflate(3.5, 0, 3.5);
        return getBoundingBox().inflate(22, 0, 22).expandTowards(0, 24, 0);
    }
}
