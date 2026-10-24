package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.pont.Crochet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Un morceau de l'épave de l'hélicoptère (cabine, queue, rotor, moteur, patins, aile ; modèle geo/helico_epave.geo,
 * partie piece_&lt;n&gt;). Projeté au crash, il retombe, brûle jusqu'à FEU_FIN (heure du monde), puis reste un moment.
 * Trois morceaux par épave sont « fouillables » : une pièce d'hélicoptère au bout de 8 s de fouille (clic droit tenu, sans
 * bouger), une seule fois. Partie serveur : {@code EpaveHelicoEntityServeur} (chute, disparition, sauvegarde) ; la fouille
 * est décidée par le serveur (helico/Fouilles).
 */
public class EpaveHelicoEntity extends Entity {
    public static final EntityDataAccessor<Byte> PIECE = SynchedEntityData.defineId(EpaveHelicoEntity.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Boolean> FOUILLABLE = SynchedEntityData.defineId(EpaveHelicoEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Long> FEU_FIN = SynchedEntityData.defineId(EpaveHelicoEntity.class, EntityDataSerializers.LONG);

    public static final int PIECES = 6;
    /** Boîte de chaque morceau (largeur, hauteur en blocs), d'après outils/modele/helico.py. */
    private static final float[][] TAILLES = {{5.0f, 3.6f}, {4.6f, 3.2f}, {5.0f, 1.6f}, {3.4f, 2.0f}, {3.8f, 1.5f}, {2.4f, 1.4f}};

    /** Jeu du joueur, à chaque tick : flammes, fumée, éclat sur un morceau à fouiller (client/helico/HelicoClient). */
    public static final Crochet<EpaveHelicoEntity> TICK_CLIENT = new Crochet<>();

    private int lerpPas;
    private double lerpX, lerpY, lerpZ;
    private float lerpLacet;

    public EpaveHelicoEntity(EntityType<? extends EpaveHelicoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PIECE, (byte) 0);
        entityData.define(FOUILLABLE, false);
        entityData.define(FEU_FIN, 0L);
    }

    public int piece() {
        return Mth.clamp(entityData.get(PIECE), 0, PIECES - 1);
    }

    public boolean fouillable() {
        return entityData.get(FOUILLABLE);
    }

    public long feuFin() {
        return entityData.get(FEU_FIN);
    }

    /** Encore en feu (verrouillé) ? */
    public boolean brule() {
        return level().getGameTime() < feuFin();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> cle) {
        super.onSyncedDataUpdated(cle);
        if (PIECE.equals(cle)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float[] t = TAILLES[piece()];
        return EntityDimensions.scalable(t[0], t[1]);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpPas > 0) {
                double nx = getX() + (lerpX - getX()) / lerpPas;
                double ny = getY() + (lerpY - getY()) / lerpPas;
                double nz = getZ() + (lerpZ - getZ()) / lerpPas;
                setYRot(getYRot() + Mth.wrapDegrees(lerpLacet - getYRot()) / lerpPas);
                lerpPas--;
                setPos(nx, ny, nz);
            }
            TICK_CLIENT.accept(this);
        } else {
            tickServeur();
        }
    }

    /** Serveur : chute, disparition. EpaveHelicoEntityServeur ; neutre ici. */
    protected void tickServeur() {
    }

    @Override
    public void lerpTo(double x, double y, double z, float lacet, float tangage, int pas, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpLacet = lacet;
        this.lerpPas = Math.max(1, pas);
    }

    @Override
    public InteractionResult interact(Player joueur, InteractionHand main) {
        // fouille : serveur (Fouilles), par le clic de Minecraft ou le paquet du jeu du joueur
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256.0 * 256.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3.0, 1.0, 3.0);
    }

    // Sauvegarde : EpaveHelicoEntityServeur. Neutre ici.
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
