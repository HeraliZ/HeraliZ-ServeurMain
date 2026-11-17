package fr.heraliz.recolte.entites;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Gisement de pierre, de métal ou de métal rare ({@code heraliz_recolte:gisement}, identifiant figé). Quatre étapes : à
 * chaque quart de la matière récoltée, il perd un morceau ({@link #ETAPE} + 1, modèle suivant), puis disparaît. Données
 * synchronisées pour l'affichage : {@link #TYPE}, {@link #ETAPE}, {@link #SECOUSSE} (heure de jeu du dernier coup : le
 * joueur le fait trembler). Boîte un peu sous l'emprise de chaque modèle ({@code modeles/gisement_<type>_<étape>.json}).
 * Logique (coups, matière, étapes) : {@code fr.heraliz.recolte.serveur.GisementServeur}.
 */
public class Gisement extends RessourceEntite {
    public static final int PIERRE = 0, METAL = 1, RARE = 2;
    /** Types, dans l'ordre de {@link #TYPE} : nom des modèles, des réglages et des commandes. */
    public static final String[] TYPES = {"pierre", "metal", "rare"};
    public static final int ETAPES = 4;

    public static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(Gisement.class, EntityDataSerializers.INT);
    /** 0 (entier) à 3 (presque fini). */
    public static final EntityDataAccessor<Integer> ETAPE = SynchedEntityData.defineId(Gisement.class, EntityDataSerializers.INT);
    /** Heure de jeu ({@code level.getGameTime()}) du dernier coup ; -1 : jamais frappé. */
    public static final EntityDataAccessor<Long> SECOUSSE = SynchedEntityData.defineId(Gisement.class, EntityDataSerializers.LONG);

    /** Largeur et hauteur de la boîte (m), par type puis étape (mesurées sur les modèles cuits, un peu en dessous). */
    private static final float[][] LARGEUR = {{2.1f, 1.75f, 1.4f, 1.05f}, {1.9f, 1.6f, 1.3f, 0.95f}, {1.8f, 1.45f, 1.15f, 0.9f}};
    private static final float[][] HAUTEUR = {{1.3f, 1.0f, 0.65f, 0.45f}, {1.45f, 1.05f, 0.65f, 0.35f}, {1.65f, 1.2f, 0.85f, 0.6f}};
    /** Au-delà, le joueur ne le dessine pas (64 blocs). */
    public static final double DISTANCE_RENDU = 64.0;

    public Gisement(EntityType<? extends Gisement> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TYPE, PIERRE);
        this.entityData.define(ETAPE, 0);
        this.entityData.define(SECOUSSE, -1L);
    }

    /** 0 pierre, 1 métal, 2 métal rare. */
    public int type() {
        return borne(this.entityData.get(TYPE), 0, TYPES.length - 1);
    }

    /** 0 (entier) à 3. */
    public int etape() {
        return borne(this.entityData.get(ETAPE), 0, ETAPES - 1);
    }

    public long secousse() {
        return this.entityData.get(SECOUSSE);
    }

    /** Nom du modèle cuit de l'étape en cours : {@code gisement_<type>_<1 à 4>}. */
    public String modele() {
        return "gisement_" + TYPES[type()] + "_" + (etape() + 1);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        int t = type(), e = etape();
        return EntityDimensions.fixed(LARGEUR[t][e], HAUTEUR[t][e]);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> cle) {
        super.onSyncedDataUpdated(cle);
        if (TYPE.equals(cle) || ETAPE.equals(cle)) {
            this.refreshDimensions();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(0.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance2) {
        return distance2 < DISTANCE_RENDU * DISTANCE_RENDU;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(TYPE, borne(tag.getInt("Type"), 0, TYPES.length - 1));
        this.entityData.set(ETAPE, borne(tag.getInt("Etape"), 0, ETAPES - 1));
        this.refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Type", type());
        tag.putInt("Etape", etape());
    }
}
