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
import net.minecraftforge.entity.PartEntity;

/**
 * Épave de l'hélicoptère abattu : partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.1). Modèle {@code helicopter_wreck}
 * immobile, tourné comme l'hélicoptère au moment de l'impact ; tant qu'elle brûle (heure de fin synchronisée), le jeu du
 * joueur fait sortir flammes et fumée de ses foyers ({@code fire_0} à {@code fire_3}), ensuite une fumée plus légère. Le
 * minuteur du feu, la zone de feu, la colonne de fumée et le retrait sont dans {@code HelicopterWreckServeur}.
 * <p><b>Collision</b> (11/11, demande du propriétaire : « qu'il y ait une collision avec l'hélico quand il est crashé au
 * sol ») : 8 boîtes ({@link #BOITES}, {@link HelicopterWreckPart}) posées des deux côtés d'après la position et le lacet de
 * l'épave, comme les rondins de la barricade du bunker : on ne passe plus au travers de la cabine, du bout de poutre ni de
 * la queue arrachée, et les balles s'y arrêtent. Le lacet est arrondi au 1/256 de tour à la pose
 * ({@link #lacetEnvoye}) : c'est celui que reçoit le jeu du joueur, donc les boîtes sont au même endroit des deux côtés.
 * Elles n'existent qu'avec l'épave : sans épave, rien. Jamais sauvegardée : au redémarrage du serveur, l'épave n'est plus
 * là.
 */
public class HelicopterWreck extends Entity {
    /** Tick de jeu (tronqué à 31 bits) où le feu s'éteint. */
    static final EntityDataAccessor<Integer> FEU_FIN = SynchedEntityData.defineId(HelicopterWreck.class, EntityDataSerializers.INT);

    /** Tick client d'une épave (flammes et fumée des foyers) : branché par PartieClient (docs §6). */
    public static final Crochet<HelicopterWreck> TICK_CLIENT = new Crochet<>();

    /**
     * Boîtes de collision, repère naturel de l'hélicoptère (blocs ; x à droite, z vers l'arrière, origine au sol sous la
     * cabine, comme {@link Helicopter}) : {x du centre, z du centre, y du bas, côté (boîte carrée vue de dessus), hauteur}.
     * Relevées sur le modèle livré ({@code helicopter_wreck.geo}, écrit par {@code modules/monde/outils/modele/helico.py}) :
     * cabine écrasée (x -1,2 à 1,4 ; z -4,6 à 2,3 ; jusqu'à 3,2 de haut, mât du rotor non compris), bout de poutre (z 2,2 à
     * 4,8 ; de 0,8 à 2,5 de haut), queue arrachée couchée à droite (x 2,2 à 4,4 ; z 5,5 à 10,3 ; dérive jusqu'à 4,5 de haut).
     * Les pales tordues, fines, n'en ont pas.
     */
    public static final float[][] BOITES = {
            {0.1f, -3.3f, 0.0f, 2.6f, 3.2f},
            {0.1f, -0.8f, 0.0f, 2.6f, 3.2f},
            {0.1f, 1.0f, 0.0f, 2.6f, 3.2f},
            {0.2f, 3.0f, 0.8f, 1.5f, 1.75f},
            {0.2f, 4.1f, 0.8f, 1.5f, 1.75f},
            {3.4f, 6.1f, 0.0f, 1.3f, 1.8f},
            {3.3f, 8.1f, 0.8f, 2.2f, 3.7f},
            {3.65f, 9.85f, 0.8f, 0.8f, 1.25f}};

    final HelicopterWreckPart[] morceaux;
    /** Position et lacet pour lesquels les morceaux sont placés (NaN : jamais placés). */
    private double placeX = Double.NaN, placeY, placeZ;
    private float placeLacet;

    public HelicopterWreck(EntityType<? extends HelicopterWreck> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.morceaux = new HelicopterWreckPart[BOITES.length];
        for (int i = 0; i < BOITES.length; i++) morceaux[i] = new HelicopterWreckPart(this, i, BOITES[i][3], BOITES[i][4]);
        // numéros des morceaux = numéro de l'épave + 1, + 2… : le jeu du joueur recrée les mêmes (comme le dragon)
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
        this.entityData.define(FEU_FIN, 0);
    }

    /** L'épave brûle encore. */
    public boolean enFeu() {
        return (int) (level().getGameTime() & 0x7FFFFFFFL) < entityData.get(FEU_FIN);
    }

    /** Lacet tel que le jeu du joueur le reçoit (paquet d'apparition : 1/256 de tour). */
    public static float lacetEnvoye(float lacet) {
        return (byte) Mth.floor(lacet * 256.0F / 360.0F) * 360.0F / 256.0F;
    }

    /**
     * Pose les boîtes de collision autour de la position, selon le lacet (même rotation que le rendu :
     * {@link Helicopter#versMonde}). Seulement si l'épave a bougé ou tourné depuis la dernière fois (elle ne bouge pas :
     * une fois à la pose sur le serveur, une fois à l'arrivée chez le joueur). Ancienne position = nouvelle : TaCZ ne
     * voit aucune vitesse, sa boîte de tir n'est pas étirée.
     */
    void placerMorceaux() {
        float lacet = getYRot();
        if (getX() == placeX && getY() == placeY && getZ() == placeZ && lacet == placeLacet) return;
        placeX = getX();
        placeY = getY();
        placeZ = getZ();
        placeLacet = lacet;
        double c = Math.toRadians(180.0 - lacet), cos = Math.cos(c), sin = Math.sin(c);
        for (int i = 0; i < morceaux.length; i++) {
            HelicopterWreckPart m = morceaux[i];
            double xn = BOITES[i][0], zn = BOITES[i][1];
            double x = getX() + xn * cos + zn * sin, y = getY() + BOITES[i][2], z = getZ() - xn * sin + zn * cos;
            m.setPos(x, y, z);
            m.xo = m.xOld = x;
            m.yo = m.yOld = y;
            m.zo = m.zOld = z;
        }
    }

    /** Une boîte de collision de l'épave touche cette boîte (place du coffre, d'un objet posé). */
    public boolean gene(AABB boite) {
        placerMorceaux();
        for (HelicopterWreckPart m : morceaux) {
            if (m.getBoundingBox().intersects(boite)) return true;
        }
        return false;
    }

    /** Chez le joueur, à l'arrivée de l'épave : boîtes posées tout de suite (sinon au premier tick, ailleurs une image). */
    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket paquet) {
        super.recreateFromPacket(paquet);
        placerMorceaux();
    }

    @Override
    public void tick() {
        super.tick();
        placerMorceaux();
        if (level().isClientSide) {
            TICK_CLIENT.accept(this);
        } else {
            tickServeur();
        }
    }

    /** Minuteurs du feu et du retrait : HelicopterWreckServeur. Neutre ici. */
    void tickServeur() {
    }

    /** Coup reçu par une boîte de l'épave (balle, coup, explosion) : rien n'est abîmé. Étincelles : HelicopterWreckServeur. */
    boolean touche(HelicopterWreckPart m, DamageSource source) {
        return false;
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 256.0 * 256.0;
    }

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
