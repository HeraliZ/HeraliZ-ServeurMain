package fr.heraliz.airstrike.entity;

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
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * Hélicoptère militaire de l'événement « hélicoptère » (module heraliz_evenements, docs/EVENEMENTS.md §4.4) : partie
 * COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.1). Elle porte l'état synchronisé que lit l'affichage (phase, santé, roulis,
 * tangage, vitesse du rotor, chute), les morceaux de la boîte de touche ({@link HelicopterPart}, comme le dragon de l'End)
 * et le lissage du mouvement chez le joueur. Le pilote (patrouille, cible, tirs, chute, crash) est dans
 * {@code HelicopterServeur} : créer l'hélicoptère par son type, jamais par {@code new Helicopter(...)}.
 * <p>Repère du modèle (« naturel ») : x à droite, y en haut, z vers l'arrière, en blocs ; origine = sol sous le centre de
 * la cabine (bas des roues), nez vers −z. La position de l'entité est à cette origine. Jamais sauvegardée.
 */
public class Helicopter extends Entity {
    public static final byte VOL = 0, COMBAT = 1, CHUTE = 2, DEPART = 3;

    static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(Helicopter.class, EntityDataSerializers.BYTE);
    /** Santé restante, de 0 à 1 (fumée sous 0,5, feu sous 0,25). */
    static final EntityDataAccessor<Float> SANTE = SynchedEntityData.defineId(Helicopter.class, EntityDataSerializers.FLOAT);
    /** Degrés ; positif = penché à droite (virage à droite). */
    static final EntityDataAccessor<Float> ROULIS = SynchedEntityData.defineId(Helicopter.class, EntityDataSerializers.FLOAT);
    /** Degrés ; positif = nez vers le bas (en avançant, en accélérant). */
    static final EntityDataAccessor<Float> TANGAGE = SynchedEntityData.defineId(Helicopter.class, EntityDataSerializers.FLOAT);
    /** Vitesse du rotor, de 0 (arrêté) à 1 (vol). */
    static final EntityDataAccessor<Float> ROTOR = SynchedEntityData.defineId(Helicopter.class, EntityDataSerializers.FLOAT);
    /** Chute : tick de jeu du début (−1 : pas de chute) et durée en ticks, pour que tous les joueurs la voient pareil. */
    static final EntityDataAccessor<Integer> CHUTE_DEBUT = SynchedEntityData.defineId(Helicopter.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> CHUTE_DUREE = SynchedEntityData.defineId(Helicopter.class, EntityDataSerializers.INT);

    /**
     * Morceaux de la boîte de touche, repère naturel, en blocs : {z du centre, y du bas, largeur, hauteur, queue (1) ou
     * cabine (0)}. Deux pour la cabine (3,2 × 3,4 : nez à −4,7, fin de la cabine vers 2), trois pour la poutre de queue
     * (1,4 ; de 2,7 à 8,7, dérive comprise), mesurés sur le modèle livré (helicopter.geo du 25/10).
     */
    public static final float[][] MORCEAUX = {
            {-2.85f, 0.2f, 3.2f, 3.4f, 0},
            {0.35f, 0.2f, 3.2f, 3.4f, 0},
            {3.4f, 1.55f, 1.4f, 1.4f, 1},
            {5.4f, 1.6f, 1.4f, 1.4f, 1},
            {8.0f, 1.9f, 1.4f, 1.4f, 1}};

    /** Pivot du tangage et du roulis (point {@code cabin_center} du modèle), repère naturel, en blocs. */
    public static final double[] CENTRE = {0, 1.7, -0.8};
    /** Bouches des miniguns de porte (points {@code muzzle_l} / {@code muzzle_r}) : départ des tirs côté serveur. */
    public static final double[] CANON_G = {-1.934, 1.789, -2.349}, CANON_D = {1.934, 1.789, -2.349};
    /** Sorties des moteurs ({@code engine_l} / {@code engine_r}) si le modèle ne les donne pas. */
    public static final double[] MOTEUR_G = {-1.170, 3.269, 1.978}, MOTEUR_D = {1.170, 3.269, 1.978};

    /** Pas de lissage chez le joueur : la position du serveur est rejointe en 2 ticks (fluide, 1 tick de retard). */
    private static final int LISSAGE = 2;
    /** Vitesse des pales à plein régime, en radians par tick (vitesse « lisible », pas la vraie : sinon effet stroboscope). */
    public static final float VITESSE_PALES = 0.95f;

    /** Tick client d'un hélicoptère (sons, fumée, feu, traînée) : branché par PartieClient (docs §6). */
    public static final Crochet<Helicopter> TICK_CLIENT = new Crochet<>();

    final HelicopterPart[] morceaux;

    // ------------------------------------------------------------------ état du joueur (affichage)
    private int lerpPas;
    private double lerpX, lerpY, lerpZ;
    private float lerpLacet;
    private float roulisO, tangageO;
    private float pales, palesO, queue, queueO;

    public Helicopter(EntityType<? extends Helicopter> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.morceaux = new HelicopterPart[MORCEAUX.length];
        for (int i = 0; i < MORCEAUX.length; i++) morceaux[i] = new HelicopterPart(this, i, MORCEAUX[i][2], MORCEAUX[i][3]);
        // numéros des morceaux = numéro de l'hélicoptère + 1, + 2… : le jeu du joueur recrée les mêmes (comme le dragon)
        this.setId(ENTITY_COUNTER.getAndAdd(morceaux.length + 1) + 1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (morceaux == null) return;
        for (int i = 0; i < morceaux.length; i++) morceaux[i].setId(id + i + 1);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(PHASE, VOL);
        this.entityData.define(SANTE, 1f);
        this.entityData.define(ROULIS, 0f);
        this.entityData.define(TANGAGE, 0f);
        this.entityData.define(ROTOR, 1f);
        this.entityData.define(CHUTE_DEBUT, -1);
        this.entityData.define(CHUTE_DUREE, 1);
    }

    // ------------------------------------------------------------------ lectures (affichage)
    public byte phase() {
        return entityData.get(PHASE);
    }

    public float sante() {
        return entityData.get(SANTE);
    }

    public float rotor() {
        return entityData.get(ROTOR);
    }

    public float roulis(float pt) {
        return Mth.lerp(pt, roulisO, entityData.get(ROULIS));
    }

    public float tangage(float pt) {
        return Mth.lerp(pt, tangageO, entityData.get(TANGAGE));
    }

    /** Angle des pales du rotor principal (radians). */
    public float pales(float pt) {
        return Mth.lerp(pt, palesO, pales);
    }

    /** Angle du rotor de queue (radians). */
    public float rotorQueue(float pt) {
        return Mth.lerp(pt, queueO, queue);
    }

    /** Avancement de la chute, de 0 à 1 ; −1 hors chute. */
    public float chute(float pt) {
        int debut = entityData.get(CHUTE_DEBUT);
        if (debut < 0 || phase() != CHUTE) return -1f;
        float t = (float) ((level().getGameTime() & 0x7FFFFFFFL) - debut) + pt;
        return Mth.clamp(t / Math.max(1, entityData.get(CHUTE_DUREE)), 0f, 1f);
    }

    /** Lacet affiché (degrés), lissé. */
    public float lacet(float pt) {
        return Mth.rotLerp(pt, yRotO, getYRot());
    }

    // ------------------------------------------------------------------ repère
    /**
     * Décalage, dans le monde (blocs), d'un point du repère naturel (blocs) pour ce lacet, ce tangage et ce roulis (pivot
     * {@link #CENTRE}). Même rotation que le rendu.
     */
    public static Vec3 versMonde(double x, double y, double z, double lacet, double tangage, double roulis) {
        double px = CENTRE[0], py = CENTRE[1], pz = CENTRE[2];
        // roulis puis tangage autour du pivot
        double a = Math.toRadians(-roulis);
        double x1 = (x - px) * Math.cos(a) - (y - py) * Math.sin(a);
        double y1 = (x - px) * Math.sin(a) + (y - py) * Math.cos(a);
        double b = Math.toRadians(-tangage);
        double y2 = y1 * Math.cos(b) - (z - pz) * Math.sin(b);
        double z2 = y1 * Math.sin(b) + (z - pz) * Math.cos(b);
        x1 += px;
        y2 += py;
        z2 += pz;
        // lacet (même rotation que Axis.YP de 180 - lacet)
        double c = Math.toRadians(180.0 - lacet);
        return new Vec3(x1 * Math.cos(c) + z2 * Math.sin(c), y2, -x1 * Math.sin(c) + z2 * Math.cos(c));
    }

    /** Pose les morceaux de la boîte de touche autour de la position (lacet seulement). Appelé chaque tick, des deux côtés. */
    void placerMorceaux() {
        double r = Math.toRadians(getYRot());
        double s = Math.sin(r), c = Math.cos(r);
        for (int i = 0; i < morceaux.length; i++) {
            HelicopterPart m = morceaux[i];
            double ox = m.getX(), oy = m.getY(), oz = m.getZ();
            double zl = MORCEAUX[i][0];
            m.setPos(getX() + zl * s, getY() + MORCEAUX[i][1], getZ() - zl * c);
            m.xo = m.xOld = ox;
            m.yo = m.yOld = oy;
            m.zo = m.zOld = oz;
        }
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClient();
        } else {
            tickServeur();
        }
        if (!isRemoved()) placerMorceaux();
    }

    /** Pilote (patrouille, cible, tirs, chute, crash) : HelicopterServeur. Neutre ici. */
    void tickServeur() {
    }

    private void tickClient() {
        if (lerpPas > 0) {
            double f = 1.0 / lerpPas;
            setPos(getX() + (lerpX - getX()) * f, getY() + (lerpY - getY()) * f, getZ() + (lerpZ - getZ()) * f);
            setYRot(getYRot() + Mth.wrapDegrees(lerpLacet - getYRot()) * (float) f);
            lerpPas--;
        }
        roulisO = entityData.get(ROULIS);
        tangageO = entityData.get(TANGAGE);
        palesO = pales;
        queueO = queue;
        float v = rotor() * VITESSE_PALES;
        pales = (pales + v) % (Mth.TWO_PI * 64);
        queue = (queue + v * 1.7f) % (Mth.TWO_PI * 64);
        if (palesO > pales) palesO -= Mth.TWO_PI * 64;
        if (queueO > queue) queueO -= Mth.TWO_PI * 64;
        TICK_CLIENT.accept(this);
    }

    @Override
    public void lerpTo(double x, double y, double z, float lacet, float pitch, int pas, boolean teleport) {
        // un paquet de rotation seule redonne la position affichée : la position visée reste celle d'avant
        if (lerpPas == 0 || x != getX() || y != getY() || z != getZ()) {
            lerpX = x;
            lerpY = y;
            lerpZ = z;
        }
        lerpLacet = lacet;
        lerpPas = LISSAGE;
    }

    // ------------------------------------------------------------------ dégâts
    /** Coup reçu par un morceau ({@code m}, ou null pour l'hélicoptère lui-même) : HelicopterServeur. Neutre ici. */
    boolean toucheMorceau(@Nullable HelicopterPart m, DamageSource source, float degats) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return toucheMorceau(null, source, amount);
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return morceaux;
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
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 320.0 * 320.0;
    }

    /** Rotor de 13 blocs et queue : boîte de rendu bien plus large que la boîte de l'entité. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(9.0, 2.5, 9.0);
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
