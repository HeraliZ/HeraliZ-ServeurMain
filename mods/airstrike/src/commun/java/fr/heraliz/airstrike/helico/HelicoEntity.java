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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hélicoptère de patrouille (événement « hélico », 24/10) : partie commune. État synchronisé (vie, vol ou chute, cible
 * de la mitrailleuse, rafale en cours) et ce que lit le dessin : orientation lissée, tourelle, barillet. Tout ce qui
 * décide (vol, cibles, tirs, dégâts, chute, épave, butin) est dans {@code HelicoEntityServeur} (partie serveur), créée
 * par l'usine du type : jamais « new HelicoEntity(...) » côté serveur.
 * <p>Repère du modèle (outils/modele/helico.py) : pixels, x à droite, y en haut, nez vers -z ; origine au milieu du bas
 * de la boîte de l'entité. Orientation dans le monde comme StrikeRenderer : YP(180 - lacet), tangage (nez en bas &gt; 0),
 * roulis (côté droit en bas &gt; 0).</p>
 */
public class HelicoEntity extends Entity {
    public static final EntityDataAccessor<Float> VIE = SynchedEntityData.defineId(HelicoEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Byte> ETAT = SynchedEntityData.defineId(HelicoEntity.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Integer> CIBLE = SynchedEntityData.defineId(HelicoEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> TIR = SynchedEntityData.defineId(HelicoEntity.class, EntityDataSerializers.BOOLEAN);

    public static final byte VOL = 0, CHUTE = 1;
    /** Pivot de la tourelle, pivot du canon, sortie des balles (pixels, repère naturel du modèle). */
    public static final Vec3 TOURELLE = new Vec3(0, 7, -66), CANON = new Vec3(0, 2.5, -66), BOUCHE = new Vec3(0, 2.5, -97);
    /** Échappements (fumée quand il est abîmé). */
    public static final Vec3 ECHAPPEMENT_G = new Vec3(-18, 58.5, 34), ECHAPPEMENT_D = new Vec3(18, 58.5, 34);
    /** Limites de la tourelle : lacet de part et d'autre du nez, tangage du canon (degrés, &gt; 0 vers le haut). */
    public static final float LACET_MAX = 120f, TANGAGE_MIN = -80f, TANGAGE_MAX = 15f;

    /** Jeu du joueur, à chaque tick : fumée, flammes, son (client/helico/HelicoClient). Neutre côté serveur. */
    public static final Crochet<HelicoEntity> TICK_CLIENT = new Crochet<>();

    // ---- client : orientation et animation lissées (lues par le dessin)
    public float tangage, tangageO, roulis, roulisO;
    public float tourelleLacet, tourelleLacetO, canonTangage = -8f, canonTangageO = -8f, barillet, barilletO;
    private float vitesseBarillet;
    private int lerpPas;
    private double lerpX, lerpY, lerpZ;
    private float lerpLacet;

    public HelicoEntity(EntityType<? extends HelicoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(VIE, 1f);
        entityData.define(ETAT, VOL);
        entityData.define(CIBLE, -1);
        entityData.define(TIR, false);
    }

    /** Vie restante, de 0 à 1. */
    public float vie() {
        return entityData.get(VIE);
    }

    public byte etat() {
        return entityData.get(ETAT);
    }

    public boolean enChute() {
        return etat() == CHUTE;
    }

    public boolean tire() {
        return entityData.get(TIR);
    }

    /** Point du modèle (pixels, repère naturel) dans le monde, en ne tenant compte que du lacet. */
    public Vec3 versMonde(Vec3 p) {
        return versMonde(p, getX(), getY(), getZ(), getYRot());
    }

    public static Vec3 versMonde(Vec3 p, double x, double y, double z, float lacet) {
        double a = Math.toRadians(180.0 - lacet);
        double nx = p.x / 16.0, ny = p.y / 16.0, nz = p.z / 16.0;
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(x + nx * c + nz * s, y + ny, z - nx * s + nz * c);
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClient();
            TICK_CLIENT.accept(this);
        } else {
            tickServeur();
        }
    }

    /** Serveur : vol, cibles, tirs, chute. HelicoEntityServeur ; neutre ici. */
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

    private void tickClient() {
        if (lerpPas > 0) {
            double nx = getX() + (lerpX - getX()) / lerpPas;
            double ny = getY() + (lerpY - getY()) / lerpPas;
            double nz = getZ() + (lerpZ - getZ()) / lerpPas;
            setYRot(getYRot() + Mth.wrapDegrees(lerpLacet - getYRot()) / lerpPas);
            lerpPas--;
            setPos(nx, ny, nz);
        }
        tangageO = tangage;
        roulisO = roulis;
        tourelleLacetO = tourelleLacet;
        canonTangageO = canonTangage;
        barilletO = barillet;
        double vx = getX() - xo, vz = getZ() - zo;
        double a = Math.toRadians(getYRot());
        double avant = -vx * Math.sin(a) + vz * Math.cos(a);
        double cote = -vx * Math.cos(a) - vz * Math.sin(a);
        float virage = Mth.wrapDegrees(getYRot() - yRotO);
        float tCible, rCible;
        if (enChute()) {
            tCible = 18f;
            rCible = 22f * Mth.sin(tickCount * 0.31f);
        } else {
            tCible = Mth.clamp((float) avant * 20f, -10f, 14f);
            rCible = Mth.clamp((float) cote * 28f + virage * 2.2f, -24f, 24f);
        }
        tangage += (tCible - tangage) * 0.08f;
        roulis += (rCible - roulis) * 0.08f;
        // tourelle : vers la cible de la mitrailleuse, sinon au repos
        float lCible = 0f, cTangage = -8f;
        Entity cible = cible();
        if (cible != null) {
            Vec3 t = versMonde(TOURELLE);
            Vec3 d = new Vec3(cible.getX() - t.x, cible.getY() + cible.getBbHeight() * 0.6 - t.y, cible.getZ() - t.z);
            // dans le repère du modèle (lacet seul) : YP(lacet - 180)
            double b = Math.toRadians(getYRot() - 180.0);
            double lx = d.x * Math.cos(b) + d.z * Math.sin(b), lz = -d.x * Math.sin(b) + d.z * Math.cos(b);
            lCible = Mth.clamp((float) Math.toDegrees(Math.atan2(-lx, -lz)), -LACET_MAX, LACET_MAX);
            cTangage = Mth.clamp((float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(lx * lx + lz * lz))), TANGAGE_MIN, TANGAGE_MAX);
        }
        tourelleLacet += Mth.clamp(Mth.wrapDegrees(lCible - tourelleLacet), -9f, 9f);
        canonTangage += Mth.clamp(cTangage - canonTangage, -7f, 7f);
        vitesseBarillet = tire() ? Math.min(1.25f, vitesseBarillet + 0.25f) : vitesseBarillet * 0.9f;
        barillet += vitesseBarillet;
    }

    /** Entité visée par la mitrailleuse (synchronisée), ou null. */
    public Entity cible() {
        int id = entityData.get(CIBLE);
        return id < 0 ? null : level().getEntity(id);
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // dégâts (balles TaCZ, explosions) : HelicoEntityServeur ; le jeu du joueur ne décide rien
        return false;
    }

    @Override
    public boolean isPickable() {
        return isAlive() && !enChute();
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
    public boolean shouldBeSaved() {
        // événement : jamais sauvegardé avec le monde (un redémarrage le fait disparaître)
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 640.0 * 640.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(8.0, 3.5, 8.0);
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
