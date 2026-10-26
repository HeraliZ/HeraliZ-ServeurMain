package fr.heraliz.casino.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.core.BlockPos;
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

import javax.annotation.Nullable;

/**
 * Place d'un joueur sur une machine (chaise de la machine a sous, tabouret de la bataille navale, place debout du
 * pierre-feuille-ciseaux) : entite invisible que le joueur "monte". Le joueur est assis (ou debout), tourne vers la
 * machine ; la place disparait des qu'il la quitte (accroupi, fin de partie, ecran ferme).
 */
public class SeatEntity extends Entity {
    private static final EntityDataAccessor<Boolean> SIT = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> OFFSET = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FACE = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RANGE = SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.FLOAT);
    /** Machine de la place, connue aussi du client (verrou pendant une partie). */
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> MACHINE = SynchedEntityData.defineId(SeatEntity.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);

    /** Serveur : machine a laquelle la place appartient, point de sortie. */
    @Nullable
    private BlockPos machine;
    @Nullable
    private Vec3 exit;
    private int age;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Cree une place et y installe le joueur.
     * @param face   direction du regard (lacet Minecraft)
     * @param range  ecart permis du regard autour de "face" (degres)
     * @param offset hauteur des pieds du joueur au-dessus de la place (blocs)
     */
    public static SeatEntity create(ServerLevel level, BlockPos machine, Vec3 at, Vec3 exit, float face, float range, float offset, boolean sit) {
        SeatEntity s = new SeatEntity(ModRegistry.SEAT.get(), level);
        s.setPos(at.x, at.y, at.z);
        s.setYRot(face);
        s.entityData.set(SIT, sit);
        s.entityData.set(OFFSET, offset);
        s.entityData.set(FACE, face);
        s.entityData.set(RANGE, range);
        s.machine = machine;
        s.entityData.set(MACHINE, java.util.Optional.of(machine));
        s.exit = exit;
        level.addFreshEntity(s);
        return s;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SIT, true);
        entityData.define(OFFSET, 0f);
        entityData.define(FACE, 0f);
        entityData.define(RANGE, 180f);
        entityData.define(MACHINE, java.util.Optional.empty());
    }

    @Nullable
    public BlockPos machine() {
        return machine != null ? machine : entityData.get(MACHINE).orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        age++;
        // plus personne dessus, ou machine (ou siege du decor) disparu : la place s'en va
        if (age > 5 && !isVehicle()) {
            discard();
            return;
        }
        if (machine != null && age % 20 == 0) {
            net.minecraft.world.level.block.entity.BlockEntity be = level().getBlockEntity(machine);
            if (!(be instanceof MachineBlockEntity) && !(be instanceof fr.heraliz.casino.block.entity.DecorBlockEntity)) {
                ejectPassengers();
                discard();
            }
        }
    }

    // =================================================================== passager
    @Override
    public double getPassengersRidingOffset() {
        // Player#getMyRidingOffset = -0.35 : les pieds du joueur sont a OFFSET au-dessus de la place
        return entityData.get(OFFSET) + 0.35;
    }

    @Override
    public boolean shouldRiderSit() {
        return entityData.get(SIT);
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

    /** Le corps regarde la machine ; la tete peut tourner dans la limite permise. */
    private void clamp(Entity e) {
        float face = entityData.get(FACE), range = entityData.get(RANGE);
        if (e instanceof LivingEntity le) {
            le.setYBodyRot(face);
            le.yBodyRotO = face;
        }
        float d = Mth.wrapDegrees(e.getYRot() - face);
        float c = Mth.clamp(d, -range, range);
        e.yRotO += c - d;
        e.setYRot(e.getYRot() + c - d);
        e.setYHeadRot(e.getYRot());
    }

    /**
     * Point ou l'on se releve : devant la place, sinon un peu plus haut (marche), sinon a cote ; en dernier recours
     * la sortie de Minecraft.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (exit == null) return super.getDismountLocationForPassenger(passenger);
        net.minecraft.world.entity.EntityDimensions dim = passenger.getDimensions(net.minecraft.world.entity.Pose.STANDING);
        Vec3 c = position();
        Vec3 d = new Vec3(exit.x - c.x, 0, exit.z - c.z);
        Vec3[] tries = {exit, exit.add(0, 0.55, 0), c.add(d.z, 0, -d.x), c.add(-d.z, 0, d.x), c.subtract(d.x, 0, d.z)};
        for (Vec3 t : tries) {
            if (level().noCollision(passenger, dim.makeBoundingBox(t))) return t;
        }
        return super.getDismountLocationForPassenger(passenger);
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
