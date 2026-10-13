package fr.heralisz.matchs.duels.bombe;

import fr.heralisz.matchs.duels.DuelsConfig;
import fr.heralisz.matchs.duels.Registres;
import fr.heralisz.matchs.pont.Crochet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * Ordinateur d'amorçage « VX-9 » des duels (recherche et destruction), quand il est hors de l'inventaire du porteur :
 * <ul>
 *     <li>{@link #FERME} : fermé au sol (porteur tué) : un attaquant le ramasse en passant dessus ;</li>
 *     <li>{@link #AMORCAGE} : ouvert au sol devant l'attaquant accroupi près d'une bombe, saisie des 3 codes (5 s) ;</li>
 *     <li>{@link #AMORCEE} : resté posé, allumé, compte à rebours à l'écran, relié à la bombe par un câble ;</li>
 *     <li>{@link #PIRATAGE} : ramassé par un défenseur accroupi qui le pirate (6 s), toujours relié ;</li>
 *     <li>{@link #DESAMORCEE} : retombé au sol, écran vert.</li>
 * </ul>
 * Purement visuel : DuelPartie (serveur) le crée, le déplace, change son état et le retire ; jamais sauvegardé avec le
 * monde, il disparaît seul si sa partie n'existe plus. État synchronisé lu par le rendu (duels/client/bombe) : étape,
 * site, début de l'action et fin de la minuterie (temps du monde, le client en tire la progression et le compte à
 * rebours sans paquet de plus), bombe reliée (câble) et joueur qui l'utilise (pose du personnage, ordinateur dans ses
 * mains pendant le piratage). Chez le joueur, il joue aussi la frappe au clavier et les bips de piratage.
 */
public class OrdinateurDuel extends Entity implements GeoEntity {
    public static final int FERME = 0, AMORCAGE = 1, AMORCEE = 2, PIRATAGE = 3, DESAMORCEE = 4;
    /**
     * Bout de la « pointe de piratage » (côté droit du boîtier), départ du câble, en pixels de modèle (espace de
     * Blockbench, avant du modèle vers le nord, sol en y = 0) : voir outils/bombe/modeles.py.
     */
    public static final float PRISE_X = 7.5f, PRISE_Y = 0.6f, PRISE_Z = 1.4f;

    /** Suivi des ordinateurs du monde du joueur (poses des personnages, mains cachées) : branché par la partie client. */
    public static final Crochet<OrdinateurDuel> TICK_CLIENT = new Crochet<>();

    private static final EntityDataAccessor<Byte> ETAT = SynchedEntityData.defineId(OrdinateurDuel.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> SITE = SynchedEntityData.defineId(OrdinateurDuel.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DEBUT = SynchedEntityData.defineId(OrdinateurDuel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIN = SynchedEntityData.defineId(OrdinateurDuel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOMBE = SynchedEntityData.defineId(OrdinateurDuel.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> UTILISATEUR = SynchedEntityData.defineId(OrdinateurDuel.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private String partieId;
    /** Client : ticks avant le prochain bruit de clavier ou bip. */
    private int prochainSon;

    public OrdinateurDuel(EntityType<? extends OrdinateurDuel> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ETAT, (byte) FERME);
        this.entityData.define(SITE, (byte) 0);
        this.entityData.define(DEBUT, 0);
        this.entityData.define(FIN, 0);
        this.entityData.define(BOMBE, -1);
        this.entityData.define(UTILISATEUR, -1);
    }

    // ------------------------------------------------------------------ état (écrit par le serveur)
    public int etat() {
        return this.entityData.get(ETAT);
    }

    public void etat(int etat) {
        this.entityData.set(ETAT, (byte) etat);
    }

    /** "A" ou "B" (écran et lettre du site). */
    public String site() {
        return this.entityData.get(SITE) == 1 ? "B" : "A";
    }

    public void site(String site) {
        this.entityData.set(SITE, (byte) ("B".equals(site) ? 1 : 0));
    }

    /** Début de l'action en cours (amorçage, piratage), en temps du monde ({@code (int) getGameTime()}). */
    public void debut(int tempsMonde) {
        this.entityData.set(DEBUT, tempsMonde);
    }

    /** Fin de la minuterie de la bombe (explosion), en temps du monde. */
    public void fin(int tempsMonde) {
        this.entityData.set(FIN, tempsMonde);
    }

    /** Bombe reliée par le câble (id d'entité, -1 = aucune). */
    public int bombeId() {
        return this.entityData.get(BOMBE);
    }

    public void bombe(@Nullable Entity bombe) {
        this.entityData.set(BOMBE, bombe == null ? -1 : bombe.getId());
    }

    /** Joueur qui tape (amorçage) ou qui tient l'ordinateur (piratage), id d'entité ; -1 = personne. */
    public int utilisateurId() {
        return this.entityData.get(UTILISATEUR);
    }

    public void utilisateur(@Nullable Entity joueur) {
        this.entityData.set(UTILISATEUR, joueur == null ? -1 : joueur.getId());
    }

    public void partie(String id) {
        this.partieId = id;
    }

    // ------------------------------------------------------------------ lectures (rendu, sons)
    /** Durée de l'action en cours (ticks). */
    public int dureeAction() {
        return etat() == PIRATAGE ? DuelsConfig.RD_DESAMORCAGE_TICKS : DuelsConfig.RD_POSE_TICKS;
    }

    /** Avancement de l'amorçage ou du piratage, de 0 à 1. */
    public float progression(float partial) {
        int ecoule = (int) level().getGameTime() - this.entityData.get(DEBUT);
        return Mth.clamp((ecoule + partial) / dureeAction(), 0f, 1f);
    }

    /** Ticks restants avant l'explosion (amorcée, piratage), jamais négatif. */
    public float resteTicks(float partial) {
        int reste = this.entityData.get(FIN) - (int) level().getGameTime();
        return Math.max(0f, reste - partial);
    }

    // ------------------------------------------------------------------ vie de l'entité
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (this.tickCount % 20 == 0 && BombeDuel.orpheline(this.partieId)) discard();
            return;
        }
        TICK_CLIENT.accept(this);
        sons();
    }

    /** Client : frappe au clavier pendant l'amorçage, bips pendant le piratage (sons locaux, à la position de l'ordinateur). */
    private void sons() {
        int e = etat();
        if (e != AMORCAGE && e != PIRATAGE) {
            this.prochainSon = 0;
            return;
        }
        if (--this.prochainSon > 0) return;
        if (e == AMORCAGE) {
            // rythme de frappe irrégulier, avec de courtes pauses
            this.prochainSon = 2 + this.random.nextInt(3) + (this.random.nextInt(10) == 0 ? 5 : 0);
            level().playLocalSound(getX(), getY() + 0.1, getZ(), Registres.CLAVIER.get(), SoundSource.PLAYERS, 0.5f,
                    0.9f + this.random.nextFloat() * 0.25f, false);
        } else {
            this.prochainSon = 5 + this.random.nextInt(4);
            level().playLocalSound(getX(), getY() + 0.8, getZ(), Registres.PIRATAGE.get(), SoundSource.PLAYERS, 0.45f,
                    0.9f + 0.35f * progression(0f), false);
        }
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

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 96.0 * getViewScale();
        return distance < d * d;
    }

    /** Pendant le piratage, l'ordinateur est dessiné dans les mains du défenseur, au-dessus de cette position. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return etat() == PIRATAGE ? getBoundingBox().inflate(1.0, 2.0, 1.0) : getBoundingBox().inflate(1.5, 0.5, 1.5);
    }

    // ------------------------------------------------------------------ GeckoLib (aucune animation : état lu par le rendu)
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
