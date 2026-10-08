package fr.heralisz.clans.territory.supply;

import fr.heraliz.clansetbases.pont.Crochet;
import fr.heralisz.clans.registry.ClansRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Avion cargo du largage : ligne droite a y = 180, 1,2 bloc/tick, au-dessus du drapeau 400 blocs apres son point de
 * depart theorique, retire 400 blocs plus loin. L'entite ne se deplace pas elle-meme : le vol de
 * {@code Ravitaillement} la positionne a chaque tick serveur ({@link #deplacer}), ce qui marche aussi dans un chunk
 * charge mais non « entity-ticking » (son propre tick n'y tourne pas). Pas sauvegardee : un avion dont le chunk se
 * decharge disparait, le vol continue seul. Cote client : interpolation douce, trainee derriere les moteurs, son
 * moteur en boucle (territory/client/AvionSon) ; sans nouvelle du serveur pendant {@link #FIGE_TICKS} ticks, l'avion
 * est considere fige (plus de son, plus de rendu) jusqu'a la prochaine mise a jour.
 */
public class AvionEntity extends Entity {
    /** Distance d'approche et vitesse du vol (blocs, blocs/tick) : constantes de Ravitaillement (partie serveur), qui les reprend d'ici. */
    public static final double DISTANCE_APPROCHE = 400, VITESSE = 1.2;
    public static final double LARGAGE = DISTANCE_APPROCHE, FIN = 2 * DISTANCE_APPROCHE;
    /** Son du moteur (territory/client/AvionSon.jouer) : branche par la partie client, neutre ailleurs. */
    public static final Crochet<AvionEntity> SON_CLIENT = new Crochet<>();
    /** Ticks sans mise a jour du vol (serveur) ou sans paquet de position (client) avant de considerer l'avion perdu. */
    public static final int FIGE_TICKS = 100;
    /** Position des moteurs dans le repere du maillage (x, y, z arriere de la nacelle). */
    private static final double MOTEUR_X = 3.3, MOTEUR_Y = 0.8, MOTEUR_Z = 1.6;

    private double dirX, dirZ = 1;
    /** Serveur : heure de jeu de la derniere position poussee par le vol. */
    private long derniereMaj;
    // client
    private int lerpSteps, derniereSync;
    private double lerpX, lerpY, lerpZ, lerpYRot;
    private boolean sonLance;

    public AvionEntity(EntityType<? extends AvionEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Parametres du vol (serveur) : cap unitaire et heure de jeu courante. */
    public void initialiser(double dirX, double dirZ, long gameTime) {
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.derniereMaj = gameTime;
        float yaw = yaw(dirX, dirZ);
        setYRot(yaw);
        this.yRotO = yaw;
        setDeltaMovement(dirX * VITESSE, 0, dirZ * VITESSE);
    }

    /** Position imposee par le vol (serveur, chaque tick) : l'avion ne se deplace jamais autrement. */
    public void deplacer(double x, double y, double z, long gameTime) {
        setPos(x, y, z);
        setDeltaMovement(dirX * VITESSE, 0, dirZ * VITESSE);
        derniereMaj = gameTime;
    }

    /** Lacet Minecraft (0 = +z) pour un cap (dx, dz). */
    public static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Client : le serveur ne donne plus de position depuis {@link #FIGE_TICKS} ticks (vol perdu, avion fige). */
    public boolean fige() {
        return tickCount - derniereSync > FIGE_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClient();
            return;
        }
        // garde-fou : un avion que plus aucun vol ne pilote (moteur des territoires relance) disparait
        if (level().getGameTime() - derniereMaj > FIGE_TICKS) discard();
    }

    private void tickClient() {
        if (fige()) return;
        if (lerpSteps > 0) {
            double x = getX() + (lerpX - getX()) / lerpSteps, y = getY() + (lerpY - getY()) / lerpSteps, z = getZ() + (lerpZ - getZ()) / lerpSteps;
            setYRot(getYRot() + (float) Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
            lerpSteps--;
            setPos(x, y, z);
        } else {
            Vec3 d = getDeltaMovement();
            setPos(getX() + d.x, getY() + d.y, getZ() + d.z);
        }
        // trainee : deux particules par moteur, derriere les nacelles
        float yaw = getYRot();
        double phi = Math.toRadians(180 - yaw), c = Math.cos(phi), s = Math.sin(phi);
        double fx = -Mth.sin((float) Math.toRadians(yaw)), fz = Mth.cos((float) Math.toRadians(yaw));
        for (int k = -1; k <= 1; k += 2) {
            double lx = k * MOTEUR_X;
            double wx = lx * c + MOTEUR_Z * s, wz = -lx * s + MOTEUR_Z * c;
            level().addParticle(ClansRegistry.TRAINEE.get(), getX() + wx, getY() + MOTEUR_Y, getZ() + wz, -fx * 0.15, 0.01, -fz * 0.15);
            level().addParticle(ClansRegistry.TRAINEE.get(), getX() + wx - fx * 0.6, getY() + MOTEUR_Y, getZ() + wz - fz * 0.6, -fx * 0.12, 0.0, -fz * 0.12);
        }
        if (!sonLance) {
            sonLance = true;
            SON_CLIENT.accept(this);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = steps + 2;
        this.derniereSync = tickCount;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        dirX = tag.getDouble("dirX");
        dirZ = tag.getDouble("dirZ");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("dirX", dirX);
        tag.putDouble("dirZ", dirZ);
    }

    /** Jamais sauvegarde : un avion dans un chunk decharge disparait, le vol continue seul. */
    @Override
    public boolean shouldBeSaved() {
        return false;
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
        return d < 512 * 512;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(9, 4, 9);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
