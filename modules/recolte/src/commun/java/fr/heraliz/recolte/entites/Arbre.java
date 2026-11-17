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
 * Arbre à abattre, bouleau (9,2 m) ou épicéa (12 m) ({@code heraliz_recolte:arbre}, identifiant figé). Chaque coup de
 * hache creuse l'entaille ({@link #COUPS} : 0 à 3, un modèle de coupe par état) ; au dernier coup il tombe
 * ({@link #CHUTE} : heure de jeu du début de la chute, dans la direction {@link #DIRECTION}), puis le tronc s'efface et la
 * souche reste ({@link #SOUCHE}) 10 minutes. Debout, la boîte est celle du tronc (on ne le traverse pas, on le frappe sur
 * toute sa hauteur) ; dès la chute, celle de la souche (l'arbre qui tombe ne gêne ni ne blesse personne).
 * Logique (coups, matière, chute, souche) : {@code fr.heraliz.recolte.serveur.ArbreServeur}.
 */
public class Arbre extends RessourceEntite {
    public static final int BOULEAU = 0, EPICEA = 1;
    /** Espèces, dans l'ordre de {@link #ESPECE} : nom des modèles, des réglages et des commandes. */
    public static final String[] ESPECES = {"bouleau", "epicea"};
    /** États de l'entaille (modèles {@code coupe_0} à {@code coupe_3}). */
    public static final int ENTAILLES = 4;

    public static final EntityDataAccessor<Integer> ESPECE = SynchedEntityData.defineId(Arbre.class, EntityDataSerializers.INT);
    /** État de l'entaille : 0 (intact) à 3 (presque coupé). */
    public static final EntityDataAccessor<Integer> COUPS = SynchedEntityData.defineId(Arbre.class, EntityDataSerializers.INT);
    /** Heure de jeu du début de la chute ; -1 : debout. */
    public static final EntityDataAccessor<Long> CHUTE = SynchedEntityData.defineId(Arbre.class, EntityDataSerializers.LONG);
    /**
     * Lacet (degrés, comme {@code getYRot}) vers lequel l'arbre tombe : à l'opposé du dernier joueur qui l'a frappé (mis à
     * jour à chaque coup, figé au début de la chute).
     */
    public static final EntityDataAccessor<Float> DIRECTION = SynchedEntityData.defineId(Arbre.class, EntityDataSerializers.FLOAT);
    /** Le tronc est parti : il ne reste que la souche. */
    public static final EntityDataAccessor<Boolean> SOUCHE = SynchedEntityData.defineId(Arbre.class, EntityDataSerializers.BOOLEAN);

    /** Hauteur du tronc debout (m), par espèce ; largeur du tronc ; souche. */
    private static final float[] HAUTEUR = {9.2f, 12.0f};
    private static final float LARGEUR_TRONC = 0.65f, LARGEUR_SOUCHE = 0.9f, HAUTEUR_SOUCHE = 0.5f;
    /** Coupe à 0,5 m (comme les modèles). */
    public static final double HAUTEUR_COUPE = 0.5;
    /** Au-delà, le joueur ne le dessine pas (96 blocs). */
    public static final double DISTANCE_RENDU = 96.0;

    public Arbre(EntityType<? extends Arbre> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ESPECE, BOULEAU);
        this.entityData.define(COUPS, 0);
        this.entityData.define(CHUTE, -1L);
        this.entityData.define(DIRECTION, 0.0f);
        this.entityData.define(SOUCHE, false);
    }

    /** 0 bouleau, 1 épicéa. */
    public int espece() {
        return borne(this.entityData.get(ESPECE), 0, ESPECES.length - 1);
    }

    /** État de l'entaille, 0 à 3. */
    public int entaille() {
        return borne(this.entityData.get(COUPS), 0, ENTAILLES - 1);
    }

    /** Heure de jeu du début de la chute, -1 debout. */
    public long chute() {
        return this.entityData.get(CHUTE);
    }

    public float direction() {
        return this.entityData.get(DIRECTION);
    }

    public boolean souche() {
        return this.entityData.get(SOUCHE);
    }

    /** Debout : ni en train de tomber, ni réduit à sa souche. */
    public boolean debout() {
        return chute() < 0 && !souche();
    }

    /** Nom du modèle cuit : {@code arbre_bouleau} ou {@code arbre_epicea}. */
    public String modele() {
        return "arbre_" + ESPECES[espece()];
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return debout() ? EntityDimensions.fixed(LARGEUR_TRONC, HAUTEUR[espece()]) : EntityDimensions.fixed(LARGEUR_SOUCHE, HAUTEUR_SOUCHE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> cle) {
        super.onSyncedDataUpdated(cle);
        if (ESPECE.equals(cle) || CHUTE.equals(cle) || SOUCHE.equals(cle)) {
            this.refreshDimensions();
        }
    }

    /** Ce que le joueur voit : le feuillage déborde du tronc, et l'arbre qui tombe s'étend sur toute sa hauteur. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ(), h = HAUTEUR[espece()] + 1.0;
        if (debout()) {
            return new AABB(x - 3.5, y - 1.0, z - 3.5, x + 3.5, y + h, z + 3.5);
        }
        return new AABB(x - h - 3.5, y - 1.0, z - h - 3.5, x + h + 3.5, y + h, z + h + 3.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance2) {
        return distance2 < DISTANCE_RENDU * DISTANCE_RENDU;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(ESPECE, borne(tag.getInt("Espece"), 0, ESPECES.length - 1));
        this.entityData.set(COUPS, borne(tag.getInt("Coups"), 0, ENTAILLES - 1));
        this.entityData.set(CHUTE, tag.contains("Chute") ? tag.getLong("Chute") : -1L);
        this.entityData.set(DIRECTION, tag.getFloat("Direction"));
        this.entityData.set(SOUCHE, tag.getBoolean("Souche"));
        this.refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Espece", espece());
        tag.putInt("Coups", entaille());
        tag.putLong("Chute", chute());
        tag.putFloat("Direction", direction());
        tag.putBoolean("Souche", souche());
    }
}
