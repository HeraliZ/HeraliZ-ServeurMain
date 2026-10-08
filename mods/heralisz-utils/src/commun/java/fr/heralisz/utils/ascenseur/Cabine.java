package fr.heralisz.utils.ascenseur;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cabine de l'ascenseur : plateforme de 4,6 × 4,6 blocs sur laquelle on se tient (collision comme une barque) quand elle
 * est arrêtée. Au départ, les joueurs debout dessus deviennent ses passagers (debout, à leur place, jusqu'à 9) : c'est
 * le serveur qui déplace la cabine, accélère et ralentit en douceur ; à l'arrivée, chacun est reposé sur le plancher.
 * On ne peut pas descendre en marche. Le client suit les positions envoyées à chaque tick (interpolées).
 * Jar fusionné : ici l'état synchronisé, la plateforme et les passagers (lus des deux côtés) ; le déplacement, la montée
 * et la descente sont dans CabineServeur (src/serveur), créée par Ascenseur.CABINE_USINE dans un monde serveur.
 */
public class Cabine extends Entity {
    /** Épaisseur du plancher : le dessus est à getY() + PONT. */
    public static final float PONT = 0.25f;
    /** Hauteur du cadre (le crochet de la corde est au-dessus). */
    public static final float CADRE = 3.1f;
    static final EntityDataAccessor<Float> CIBLE = SynchedEntityData.defineId(Cabine.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Boolean> MARCHE = SynchedEntityData.defineId(Cabine.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> FACADE = SynchedEntityData.defineId(Cabine.class, EntityDataSerializers.INT);

    private int lerpSteps;
    private double lx, ly, lz;

    public Cabine(EntityType<? extends Cabine> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = true;
    }

    // sauvegarde : CabineServeur (partie serveur) ; rien côté client
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void defineSynchedData() {
        entityData.define(CIBLE, 0f);
        entityData.define(MARCHE, false);
        entityData.define(FACADE, Direction.NORTH.get2DDataValue());
    }

    public boolean enMarche() {
        return entityData.get(MARCHE);
    }

    public Direction facade() {
        return Direction.from2DDataValue(entityData.get(FACADE));
    }

    /** Dessus du plancher (y du monde). */
    public double plancher() {
        return getY() + PONT;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                double nx = getX() + (lx - getX()) / lerpSteps;
                double ny = getY() + (ly - getY()) / lerpSteps;
                double nz = getZ() + (lz - getZ()) / lerpSteps;
                --lerpSteps;
                setPos(nx, ny, nz);
            }
            return;
        }
        tickServeur();
    }

    /** Déplacement de la cabine (serveur) : neutre ici ; CabineServeur (partie serveur) la fait rouler. */
    void tickServeur() {}

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lx = x;
        ly = y;
        lz = z;
        lerpSteps = steps + 1;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return e instanceof Player && getPassengers().size() < 9;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Places des passagers debout : le centre d'abord, puis autour. */
    private static final double[][] PLACES = {{0, 0}, {-1.3, 0}, {1.3, 0}, {0, -1.3}, {0, 1.3}, {-1.3, -1.3}, {1.3, 1.3}, {-1.3, 1.3}, {1.3, -1.3}};

    /** Passagers debout sur le plancher, chacun sa place. */
    @Override
    protected void positionRider(Entity p, Entity.MoveFunction f) {
        if (!hasPassenger(p)) {
            return;
        }
        double[] o = PLACES[Math.min(PLACES.length - 1, getPassengers().indexOf(p))];
        f.accept(p, getX() + o[0], plancher(), getZ() + o[1]);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        return new Vec3(p.getX(), plancher() + 0.02, p.getZ());
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().expandTowards(0, CADRE + 1.0, 0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d2) {
        return d2 < 192 * 192;
    }
}
