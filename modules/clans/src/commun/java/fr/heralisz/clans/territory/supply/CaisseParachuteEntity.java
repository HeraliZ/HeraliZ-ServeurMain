package fr.heralisz.clans.territory.supply;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;


/**
 * Caisse sous parachute : chute de 0,35 bloc/tick, derive horizontale aleatoire bornee a 10 blocs du point de
 * largage (a la verticale du drapeau) ; traverse le feuillage ; a l'atterrissage : bloc caisse_ravitaillement rempli
 * depuis la table de butin, son, fumee rouge 3 min. Sauvegardee (une caisse en l'air dans un chunk qui se decharge
 * reprend sa chute au rechargement). Chute, atterrissage et sauvegarde : {@code CaisseParachuteEntityServeur} (partie
 * serveur, creee par {@code ClansRegistry.CAISSE_PARACHUTE_USINE}).
 */
public class CaisseParachuteEntity extends Entity {
    public static final double CHUTE = 0.35, DERIVE_MAX = 10;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;

    public CaisseParachuteEntity(EntityType<? extends CaisseParachuteEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps, getZ() + (lerpZ - getZ()) / lerpSteps);
                lerpSteps--;
            } else {
                Vec3 d = getDeltaMovement();
                setPos(getX() + d.x, getY() + d.y, getZ() + d.z);
            }
            return;
        }
        chute();
    }

    /** Part serveur du tick (chute, derive, feuillage, atterrissage) : {@code CaisseParachuteEntityServeur} ; rien ici. */
    protected void chute() {
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpSteps = steps + 2;
    }

    @Override
    protected void defineSynchedData() {
    }

    /** Sauvegarde : version serveur (zone, clan, derive, point de largage). */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3, 0, 3).expandTowards(0, 6.5, 0);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
