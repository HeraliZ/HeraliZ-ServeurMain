package fr.heralisz.matchs.zombies.contenu.entite;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.contenu.ZbSons;
import fr.heralisz.matchs.zombies.jeu.api.Allure;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * Zombie « labo » du mode zombies (MATCHS_SPEC §4) : derive de {@link Zombie} pour garder ses reflexes (morts-vivants,
 * pas de noyade...), mais sans rien de vanilla visible ni audible : modele et textures maison (4 variantes : blouse,
 * combinaison hazmat, militaire, technicien), sons maison, aucune combustion au soleil, aucun butin ni experience,
 * aucun ramassage, aucun renfort, pas de conversion dans l'eau, jamais sauvegarde (une entite dechargee disparait :
 * le jeu la compte comme retiree et ne retrouve jamais d'orpheline au rechargement).
 * <p>Contrat {@code ZombieFabrique} : la cible est choisie par le jeu ({@code setTarget}) ; sans cible le zombie ne
 * fait rien de lui-meme (le jeu le conduit aux fenetres par {@code getNavigation().moveTo}) ; avec une cible il la
 * poursuit et la griffe ({@link GriffeGoal}).
 * <p>Animations (cote client) : allure synchronisee ({@link Allure}) pour la demarche, evenements d'entite pour
 * l'arrachage d'une planche, l'enjambement d'une fenetre et la sortie de terre.
 */
public class ZombieLabo extends Zombie {
    public static final int VARIANTES = 4;
    /** Evenements d'entite (octets libres, au-dela de ceux de vanilla). */
    public static final byte EV_ARRACHE = 81, EV_ENJAMBE = 82, EV_SORTIE = 83, EV_CONDUIT = 84, EV_EGOUT = 85, EV_CHUTE = 86;
    /** Sortie d'une bouche d'egout (0.2.3) : il se hisse hors du trou, mains sur le rebord (ticks, immobile). */
    public static final int DUREE_EGOUT = 40;
    /** Chute d'une bouche d'aeration du plafond (0.2.3) : bras leves pendant la chute, reception accroupie. */
    public static final int DUREE_CHUTE = 24;
    /** Escalade (0.2.3, synchronise) : 0 = non, 1 = grimpe a un mur (pose d'escalade). */
    public static final int GRIMPE_NON = 0, GRIMPE_MUR = 1;
    /** Duree de la sortie de terre (ticks) : immobile et invulnerable aux coups de griffe pendant ce temps. */
    public static final int DUREE_SORTIE = 34;
    /** Duree de la sortie d'un conduit d'aeration (rampe hors du mur puis se releve), memes regles. */
    public static final int DUREE_CONDUIT = 36;
    /** Duree de l'animation d'arrachage d'une planche (ticks) : la planche part a 35 % (voir FenetreRendu). */
    public static final float DUREE_ARRACHAGE = 14F;
    /** Duree de l'enjambement = deplacement interpole par le jeu. */
    public static final float DUREE_ENJAMBEMENT = ZombiesApi.ENJAMBEMENT_TICKS;

    private static final EntityDataAccessor<Integer> VARIANTE = SynchedEntityData.defineId(ZombieLabo.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ALLURE = SynchedEntityData.defineId(ZombieLabo.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MANCHE = SynchedEntityData.defineId(ZombieLabo.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> GRIMPE = SynchedEntityData.defineId(ZombieLabo.class, EntityDataSerializers.BYTE);

    /** Instants (tickCount client) des animations ponctuelles ; -1000 = jamais. */
    public int arracheDebut = -1000, enjambeDebut = -1000, sortieDebut = -1000, conduitDebut = -1000, egoutDebut = -1000, chuteDebut = -1000;
    /** Cote serveur : fin de la sortie de terre (tickCount). */
    private int sortieFin = -1;
    /** Phase propre (desynchronise les balancements d'une horde). */
    public final float phase;

    public ZombieLabo(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setCanPickUpLoot(false);
        this.setPersistenceRequired();
        this.phase = (float) ((this.getUUID().getLeastSignificantBits() & 0xFFFF) / 65535.0 * Math.PI * 2);
    }

    /** Fabrique pratique (non ajoute au monde). */
    public static ZombieLabo creer(Level level, Allure allure, int variante, int manche) {
        ZombieLabo z = new ZombieLabo(ZbRegistres.ZOMBIE_LABO.get(), level);
        z.allure(allure);
        z.variante(variante < 0 || variante >= VARIANTES ? level.random.nextInt(VARIANTES) : variante);
        z.manche(manche);
        return z;
    }

    public static AttributeSupplier.Builder attributs() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, Allure.MARCHEUR.vitesse)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                // fixe a la creation le budget de recherche de chemin (x 16 noeuds) : 96 -> 1 536 noeuds par recherche
                // ratee, 48 -> 768 (zombie vanilla : 560) ; la portee de poursuite est remontee a 64 a l'apparition
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANTE, 0);
        this.entityData.define(ALLURE, 0);
        this.entityData.define(MANCHE, 1);
        this.entityData.define(GRIMPE, (byte) 0);
    }

    /** Escalade en cours ({@link #GRIMPE_NON}, {@link #GRIMPE_MUR}) : pose d'escalade cote client, immobile cote serveur. */
    public int grimpe() {
        return this.entityData.get(GRIMPE);
    }

    public void grimpe(int g) {
        this.entityData.set(GRIMPE, (byte) Mth.clamp(g, 0, 1));
    }

    // ------------------------------------------------------------------ etat synchronise
    public int variante() {
        return Mth.clamp(this.entityData.get(VARIANTE), 0, VARIANTES - 1);
    }

    public void variante(int v) {
        this.entityData.set(VARIANTE, Mth.clamp(v, 0, VARIANTES - 1));
    }

    public Allure allure() {
        return Allure.de(this.entityData.get(ALLURE));
    }

    public void allure(Allure a) {
        this.entityData.set(ALLURE, a.ordinal());
    }

    public int manche() {
        return this.entityData.get(MANCHE);
    }

    public void manche(int m) {
        this.entityData.set(MANCHE, Math.max(1, m));
    }

    // ------------------------------------------------------------------ IA (le jeu decide, voir la Javadoc de la classe)
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new GriffeGoal(this, 1.0, 20));
    }

    @Override
    protected void addBehaviourGoals() {
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || sortieFin > this.tickCount || grimpe() != GRIMPE_NON;
    }

    /** Pas de degats de chute : le jeu replace un zombie tombe, et la sortie d'un conduit du plafond le fait sauter. */
    @Override
    public boolean causeFallDamage(float distance, float multiplicateur, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------------ animations ponctuelles (serveur -> client)
    /** Le zombie tire sur une planche (bras tendus puis arraches). */
    public void animerArrachage() {
        if (!this.level().isClientSide) this.level().broadcastEntityEvent(this, EV_ARRACHE);
    }

    /** Le zombie enjambe une fenetre. */
    public void animerEnjambement() {
        if (!this.level().isClientSide) this.level().broadcastEntityEvent(this, EV_ENJAMBE);
    }

    /** Le zombie sort de terre : immobile {@link #DUREE_SORTIE} ticks, particules et son. */
    public void sortirDeTerre() {
        if (this.level().isClientSide) return;
        sortieFin = this.tickCount + DUREE_SORTIE;
        this.level().broadcastEntityEvent(this, EV_SORTIE);
        this.playSound(ZbSons.SORTIE_TERRE.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ZbRegistres.CENDRE.get(), getX(), getY() + 0.1, getZ(), 24, 0.45, 0.05, 0.45, 0.04);
            sl.sendParticles(ZbRegistres.BRUME.get(), getX(), getY() + 0.2, getZ(), 6, 0.4, 0.1, 0.4, 0.01);
        }
    }

    /**
     * Le zombie sort d'une bouche d'aeration : il est tourne dos au mur le plus proche (la bouche), rampe hors du mur
     * puis se releve ({@link #DUREE_CONDUIT} ticks, immobile pendant ce temps). Sans mur a cote : sortie de terre.
     */
    public void sortirDuConduit() {
        if (this.level().isClientSide) return;
        Direction mur = murVoisin();
        if (mur == null) {
            sortirDeTerre();
            return;
        }
        float lacet = mur.getOpposite().toYRot();
        this.setYRot(lacet);
        this.setYBodyRot(lacet);
        this.setYHeadRot(lacet);
        this.yRotO = lacet;
        sortieFin = this.tickCount + DUREE_CONDUIT;
        this.level().broadcastEntityEvent(this, EV_CONDUIT);
        this.playSound(ZbSons.GRILLE.get(), 0.9F, 0.8F + this.random.nextFloat() * 0.2F);
        this.playSound(ZbSons.SORTIE_TERRE.get(), 0.6F, 1.2F);
        if (this.level() instanceof ServerLevel sl) {
            double x = getX() + mur.getStepX() * 0.55, z = getZ() + mur.getStepZ() * 0.55;
            sl.sendParticles(ZbRegistres.CENDRE.get(), x, getY() + 0.4, z, 18, 0.15 + 0.3 * Math.abs(mur.getStepZ()), 0.25,
                    0.15 + 0.3 * Math.abs(mur.getStepX()), 0.03);
            sl.sendParticles(ZbRegistres.BRUME.get(), x, getY() + 0.4, z, 4, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** Mur plein le plus proche autour des pieds (bouche d'aeration), ou null. */
    private Direction murVoisin() {
        BlockPos p = this.blockPosition();
        Direction meilleur = null;
        double dMin = 9;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos q = p.relative(d);
            if (!this.level().getBlockState(q).isCollisionShapeFullBlock(this.level(), q)
                    && !this.level().getBlockState(q.above()).isCollisionShapeFullBlock(this.level(), q.above())) continue;
            // distance du centre du zombie a la face de ce mur
            double dist = d.getAxis() == Direction.Axis.X ? Math.abs((p.getX() + 0.5 + d.getStepX() * 0.5) - getX())
                    : Math.abs((p.getZ() + 0.5 + d.getStepZ() * 0.5) - getZ());
            if (dist < dMin) {
                dMin = dist;
                meilleur = d;
            }
        }
        return meilleur;
    }

    /**
     * Sortie d'une bouche d'egout (0.2.3) : le zombie se hisse hors du trou, mains sur le rebord ({@link #DUREE_EGOUT}
     * ticks, immobile et intouchable au corps a corps comme une sortie de terre) ; fracas de la plaque, brume.
     */
    public void sortirDeLEgout() {
        if (this.level().isClientSide) return;
        sortieFin = this.tickCount + DUREE_EGOUT;
        this.level().broadcastEntityEvent(this, EV_EGOUT);
        this.playSound(ZbSons.GRILLE.get(), 1.0F, 0.6F + this.random.nextFloat() * 0.15F);
        this.playSound(ZbSons.SORTIE_TERRE.get(), 0.7F, 1.1F);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ZbRegistres.BRUME.get(), getX(), getY() + 0.1, getZ(), 10, 0.35, 0.1, 0.35, 0.01);
            sl.sendParticles(ZbRegistres.CENDRE.get(), getX(), getY() + 0.1, getZ(), 12, 0.4, 0.05, 0.4, 0.03);
        }
    }

    /**
     * Sortie d'un conduit d'aeration (0.2.3) : contre un mur, il rampe hors de la bouche ({@link #sortirDuConduit}) ;
     * sans mur mais sous un plafond (2 a 5 blocs), il tombe d'une bouche du plafond (bras leves, reception accroupie) ;
     * sinon, sortie de terre.
     */
    public void sortirDUnConduit() {
        if (this.level().isClientSide) return;
        if (murVoisin() != null) {
            sortirDuConduit();
            return;
        }
        BlockPos p = this.blockPosition();
        for (int h = 2; h <= 5; h++) {
            BlockPos q = p.above(h);
            if (this.level().getBlockState(q).getCollisionShape(this.level(), q).isEmpty()) continue;
            if (h < 3) break;
            // juste sous la bouche du plafond : il tombe
            double y = q.getY() - this.getBbHeight() - 0.05;
            this.setPos(getX(), y, getZ());
            this.fallDistance = 0;
            sortieFin = this.tickCount + 8;
            this.level().broadcastEntityEvent(this, EV_CHUTE);
            this.playSound(ZbSons.GRILLE.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ZbRegistres.CENDRE.get(), getX(), q.getY() - 0.05, getZ(), 20, 0.35, 0.02, 0.35, 0.03);
                sl.sendParticles(ZbRegistres.BRUME.get(), getX(), q.getY() - 0.3, getZ(), 4, 0.3, 0.1, 0.3, 0.01);
            }
            return;
        }
        sortirDeTerre();
    }

    /** Sortie d'un tas de gravats : sortie de terre et eclats de pierre. */
    public void sortirDesGravats() {
        sortirDeTerre();
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()), getX(), getY() + 0.3, getZ(),
                    30, 0.4, 0.2, 0.4, 0.15);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EV_ARRACHE -> arracheDebut = this.tickCount;
            case EV_ENJAMBE -> enjambeDebut = this.tickCount;
            case EV_SORTIE -> sortieDebut = this.tickCount;
            case EV_CONDUIT -> conduitDebut = this.tickCount;
            case EV_EGOUT -> egoutDebut = this.tickCount;
            case EV_CHUTE -> chuteDebut = this.tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    /** 0..1 pendant la sortie d'un conduit (cote client), 1 ensuite. */
    public float conduit(float partial) {
        float t = (this.tickCount - conduitDebut + partial) / DUREE_CONDUIT;
        return Mth.clamp(t, 0, 1);
    }

    /**
     * Vrai (cote client) tant que le zombie est colle a une fenetre a arracher les planches : entre deux arrachages
     * (intervalle du jeu {@link ZombiesApi#ARRACHAGE_TICKS}), il griffe les planches.
     */
    public boolean aLaFenetre(float partial) {
        float dt = this.tickCount - arracheDebut + partial;
        float de = this.tickCount - enjambeDebut + partial;
        return dt >= 0 && dt < ZombiesApi.ARRACHAGE_TICKS + DUREE_ARRACHAGE + 6 && (de < 0 || de > DUREE_ENJAMBEMENT || enjambeDebut < arracheDebut);
    }

    /** 0..1 pendant la sortie d'une bouche d'egout (cote client), 1 ensuite. */
    public float egout(float partial) {
        return Mth.clamp((this.tickCount - egoutDebut + partial) / DUREE_EGOUT, 0, 1);
    }

    /** 0..1 pendant la chute d'une bouche du plafond (cote client), 1 ensuite. */
    public float chute(float partial) {
        return Mth.clamp((this.tickCount - chuteDebut + partial) / DUREE_CHUTE, 0, 1);
    }

    /** 0..1 pendant la sortie de terre (cote client), 1 ensuite. */
    public float sortie(float partial) {
        float t = (this.tickCount - sortieDebut + partial) / DUREE_SORTIE;
        return Mth.clamp(t, 0, 1);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // sortie de terre : on ne peut pas encore le toucher au corps a corps (les balles, si)
        if (sortieFin > this.tickCount && source.getDirectEntity() == source.getEntity() && source.getEntity() != null) return false;
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ rien de vanilla
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean canBreakDoors() {
        return false;
    }

    @Override
    protected boolean supportsBreakDoorGoal() {
        return false;
    }

    @Override
    public void setBaby(boolean bebe) {
        // jamais de bebe zombie
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return BuiltInLootTables.EMPTY;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    }

    @Override
    protected void dropEquipment() {
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
        // jamais de despawn : c'est le jeu qui retire ses creatures
    }

    /** Jamais ecrit dans les tronçons : une partie ne survit pas a un redemarrage. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    // ------------------------------------------------------------------ mort : chute en avant, cendres maison (pas de « poof » vanilla)
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 22 && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ZbRegistres.CENDRE.get(), getX(), getY() + 0.3, getZ(), 18, 0.5, 0.2, 0.5, 0.02);
            }
            this.remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ sons maison
    @Override
    protected SoundEvent getAmbientSound() {
        return allure() == Allure.SPRINTEUR && this.random.nextInt(3) == 0 ? ZbSons.ZOMBIE_CRI.get() : ZbSons.ZOMBIE_GROGNE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ZbSons.ZOMBIE_TOUCHE.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ZbSons.ZOMBIE_MORT.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return ZbSons.ZOMBIE_PAS.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(getStepSound(), 0.35F, 0.85F + this.random.nextFloat() * 0.3F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 90 + this.random.nextInt(80);
    }

    @Override
    public boolean doHurtTarget(Entity cible) {
        boolean ok = super.doHurtTarget(cible);
        if (ok) this.playSound(ZbSons.ZOMBIE_ATTAQUE.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        return ok;
    }
}
