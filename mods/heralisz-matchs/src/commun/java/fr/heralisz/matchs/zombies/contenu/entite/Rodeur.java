package fr.heralisz.matchs.zombies.contenu.entite;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.contenu.ZbSons;
import fr.heralisz.matchs.zombies.jeu.api.Allure;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;

/**
 * Rodeur : creature rapide des manches speciales (MATCHS_SPEC §4, l'equivalent original des chiens de CoD) — un
 * mutant quadrupede ecorche du laboratoire N-07, yeux orange, colonne hérissée. Meme contrat que le zombie (cible
 * choisie par le jeu, aucun butin, jamais sauvegarde, sons maison) ; en plus il bondit sur sa cible a mi-distance.
 * A sa mort il se dissout en brume (particules maison), sans « poof » vanilla.
 */
public class Rodeur extends Monster {
    public static final byte EV_APPARITION = 84;
    /** Instant (tickCount client) de l'apparition, -1000 = aucune. */
    public int apparitionDebut = -1000;
    private int bondRecharge;
    public final float phase;

    public Rodeur(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setCanPickUpLoot(false);
        this.setPersistenceRequired();
        this.setMaxUpStep(1.0F);
        this.phase = (float) ((this.getUUID().getLeastSignificantBits() & 0xFFFF) / 65535.0 * Math.PI * 2);
    }

    public static Rodeur creer(Level level) {
        return new Rodeur(ZbRegistres.RODEUR.get(), level);
    }

    public static AttributeSupplier.Builder attributs() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, Allure.RODEUR.vitesse)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                // fixe a la creation le budget de recherche de chemin (x 16 noeuds) : 96 -> 1 536 noeuds par recherche
                // ratee, 48 -> 768 (zombie vanilla : 560) ; la portee de poursuite est remontee a 64 a l'apparition
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new GriffeGoal(this, 1.0, 16));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            // braises qui s'echappent de la gueule
            if (this.random.nextInt(6) == 0) {
                float a = this.yBodyRot * Mth.DEG_TO_RAD;
                this.level().addParticle(ZbRegistres.BRUME.get(), getX() - Mth.sin(a) * 0.7, getY() + 0.7, getZ() + Mth.cos(a) * 0.7, 0, 0.01, 0);
            }
            return;
        }
        if (bondRecharge > 0) bondRecharge--;
        LivingEntity c = getTarget();
        if (c != null && bondRecharge <= 0 && onGround()) {
            double d2 = distanceToSqr(c);
            if (d2 > 9 && d2 < 36 && getSensing().hasLineOfSight(c)) {
                // bond : elan vers la cible
                Vec3 dir = new Vec3(c.getX() - getX(), 0, c.getZ() - getZ()).normalize();
                setDeltaMovement(dir.x * 0.9, 0.42, dir.z * 0.9);
                bondRecharge = 50 + random.nextInt(30);
                playSound(ZbSons.RODEUR_ATTAQUE.get(), 1.0F, 1.1F);
            }
        }
    }

    /** Apparition : decharge de brume et d'etincelles, son, pose a l'arret une demi-seconde (cote client). */
    public void animerApparition() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        sl.broadcastEntityEvent(this, EV_APPARITION);
        sl.sendParticles(ZbRegistres.BRUME.get(), getX(), getY() + 0.5, getZ(), 30, 0.6, 0.5, 0.6, 0.03);
        sl.sendParticles(ZbRegistres.ETINCELLE.get(), getX(), getY() + 0.6, getZ(), 18, 0.4, 0.4, 0.4, 0.2);
        this.playSound(ZbSons.RODEUR_APPARITION.get(), 1.4F, 0.95F + random.nextFloat() * 0.1F);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EV_APPARITION) apparitionDebut = this.tickCount;
        else super.handleEntityEvent(id);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    // ------------------------------------------------------------------ rien de vanilla
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
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 14 && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ZbRegistres.BRUME.get(), getX(), getY() + 0.4, getZ(), 22, 0.5, 0.3, 0.5, 0.02);
                sl.sendParticles(ZbRegistres.CENDRE.get(), getX(), getY() + 0.4, getZ(), 12, 0.4, 0.2, 0.4, 0.03);
            }
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ZbSons.RODEUR_GROGNE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ZbSons.ZOMBIE_TOUCHE.get();
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.15F;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ZbSons.RODEUR_MORT.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ZbSons.ZOMBIE_PAS.get(), 0.25F, 1.5F + this.random.nextFloat() * 0.3F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60 + this.random.nextInt(60);
    }

    @Override
    public boolean doHurtTarget(Entity cible) {
        boolean ok = super.doHurtTarget(cible);
        if (ok) this.playSound(ZbSons.RODEUR_ATTAQUE.get(), 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
        return ok;
    }
}
