package fr.heraliz.casino.entity;

import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Le joueur qu'on sort du casino : entite invisible qu'il "monte" (debout), deplacee pas a pas par l'expulsion. Il ne
 * peut ni marcher ni descendre : ce sont les videurs qui le tiennent. Sa phase (attend, tenu, pousse, lance) est
 * synchronisee pour que chaque client pose le joueur en consequence.
 */
public class EscortEntity extends Entity {
    public static final byte WAIT = 0, HELD = 1, MARCH = 2, WINDUP = 3, THROWN = 4;

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(EscortEntity.class, EntityDataSerializers.BYTE);
    /** Direction vers laquelle le corps du joueur est tourne (lacet Minecraft). */
    private static final EntityDataAccessor<Float> FACE = SynchedEntityData.defineId(EscortEntity.class, EntityDataSerializers.FLOAT);

    /** Vrai pendant que l'expulsion fait elle-meme descendre le joueur. */
    public static boolean allowDismount;

    private int age;
    // ---- client : deplacement lisse vers la derniere position recue, ticks depuis le dernier changement de phase
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    public int phaseAge;

    public EscortEntity(EntityType<? extends EscortEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static EscortEntity create(ServerLevel level, Vec3 at, float face) {
        EscortEntity e = new EscortEntity(ModRegistry.ESCORT.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.setYRot(face);
        e.entityData.set(FACE, face);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE, WAIT);
        entityData.define(FACE, 0f);
    }

    public byte phase() {
        return entityData.get(PHASE);
    }

    public void phase(byte p) {
        if (phase() != p) entityData.set(PHASE, p);
    }

    public float face() {
        return entityData.get(FACE);
    }

    public void face(float yaw) {
        if (Math.abs(Mth.wrapDegrees(yaw - face())) > 0.5f) entityData.set(FACE, yaw);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key)) phaseAge = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            phaseAge++;
            if (lerpSteps > 0) {
                setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps, getZ() + (lerpZ - getZ()) / lerpSteps);
                lerpSteps--;
            }
            return;
        }
        // plus personne dessus (deconnexion, expulsion interrompue) : l'entite s'en va
        if (++age > 10 && !isVehicle()) discard();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = 3;
    }

    // =================================================================== passager
    @Override
    public double getPassengersRidingOffset() {
        // Player#getMyRidingOffset = -0.35 : les pieds du joueur sont exactement sur l'entite
        return 0.35;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        super.positionRider(passenger, move);
        clamp(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clamp(passenger);
    }

    /** Le corps suit la direction imposee ; la tete tourne librement de 75 degres de chaque cote. */
    private void clamp(Entity e) {
        float face = face();
        // (l'ancienne valeur reste celle du tick precedent : le corps tourne en douceur d'une image a l'autre)
        if (e instanceof LivingEntity le) le.setYBodyRot(face);
        float d = Mth.wrapDegrees(e.getYRot() - face);
        float c = Mth.clamp(d, -75, 75);
        e.yRotO += c - d;
        e.setYRot(e.getYRot() + c - d);
        e.setYHeadRot(e.getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    // =================================================================== entite inerte
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

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
}
