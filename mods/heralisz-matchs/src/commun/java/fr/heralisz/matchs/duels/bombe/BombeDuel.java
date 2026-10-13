package fr.heralisz.matchs.duels.bombe;

import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * Bombe d'un site de recherche et destruction (duels) : « ogive sur berceau », posée en permanence sur les sites A et B
 * de la carte pendant un duel (points {@code bombe_a} / {@code bombe_b}). Purement visuelle : l'attaquant l'amorce avec
 * l'ordinateur ({@link OrdinateurDuel}), les règles sont dans DuelPartie (serveur), qui la pose, change son état et la
 * retire. Elle ne se sauvegarde jamais avec le monde et disparaît seule si sa partie n'existe plus (redémarrage, partie
 * finie) : aucune bombe hors des duels.
 * <p>État synchronisé (lu par le rendu, duels/client/bombe) : site (texture A ou B, lettre lumineuse) et état
 * ({@link #VEILLE} : gyrophare ambre qui tourne ; {@link #ARMEE} : écran ARMED, voyants rouges qui clignotent ;
 * {@link #DESAMORCEE} : gyrophare éteint ; {@link #EXPLOSEE} : plus dessinée jusqu'à la manche suivante).
 */
public class BombeDuel extends Entity implements GeoEntity {
    public static final int VEILLE = 0, ARMEE = 1, DESAMORCEE = 2, EXPLOSEE = 3;
    /**
     * Prise « DATA » du module de commande (bout du câble de l'ordinateur), en pixels de modèle (espace de Blockbench,
     * avant du modèle vers le nord, sol en y = 0) : voir outils/bombe/modeles.py (module de la face nord).
     */
    public static final float PRISE_X = -2.6f, PRISE_Y = 8.35f, PRISE_Z = -6.75f;

    private static final EntityDataAccessor<Byte> SITE = SynchedEntityData.defineId(BombeDuel.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> ETAT = SynchedEntityData.defineId(BombeDuel.class, EntityDataSerializers.BYTE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private String partieId;

    public BombeDuel(EntityType<? extends BombeDuel> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SITE, (byte) 0);
        this.entityData.define(ETAT, (byte) VEILLE);
    }

    // ------------------------------------------------------------------ état
    /** "A" ou "B". */
    public String site() {
        return this.entityData.get(SITE) == 1 ? "B" : "A";
    }

    public void site(String site) {
        this.entityData.set(SITE, (byte) ("B".equals(site) ? 1 : 0));
    }

    public int etat() {
        return this.entityData.get(ETAT);
    }

    public void etat(int etat) {
        this.entityData.set(ETAT, (byte) etat);
    }

    /** Rattache la bombe à sa partie (serveur) : sans elle, la bombe disparaît. */
    public void partie(String id) {
        this.partieId = id;
    }

    // ------------------------------------------------------------------ vie de l'entité
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && this.tickCount % 20 == 0 && orpheline(this.partieId)) discard();
    }

    /** Vrai si la partie n'existe plus (ou est finie) : la bombe et l'ordinateur disparaissent. */
    static boolean orpheline(@Nullable String id) {
        if (id == null) return true;
        Partie p = MatchApi.partie(id);
        return p == null || p.phase() == Partie.Phase.TERMINEE;
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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isAttackable() {
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
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    /** Visible de loin (la carte de duel fait 90 blocs de long). */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 128.0 * getViewScale();
        return distance < d * d;
    }

    /** Le modèle (1,6 bloc de long, gyrophare compris) dépasse la boîte de l'entité. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(0.5, 0.3, 0.5);
    }

    // ------------------------------------------------------------------ GeckoLib (aucune animation : os tournés par le rendu)
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
