package fr.heraliz.casino.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

/**
 * Videur du casino. A son poste (pose par /casino zone entree ou /casino zone garde), il reste plante la, bras
 * croises, suit du regard les joueurs qui approchent et arrete ceux qui veulent entrer sans payer. Les videurs
 * "de renfort" qui sortent un joueur fauche sont temporaires : l'expulsion les deplace elle-meme, pas a pas.
 */
public class GuardEntity extends PathfinderMob {
    /** Attitudes (synchronisees : le modele pose les bras en consequence). */
    public static final byte IDLE = 0, STOP = 1, RUN = 2, PUSH = 3, WINDUP = 4, THROW = 5, DUST = 6, POINT = 7;

    private static final EntityDataAccessor<Byte> STANCE = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Component> SPEECH = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.COMPONENT);
    /** Change a chaque replique (meme repetee) : relance la bulle chez les clients. */
    private static final EntityDataAccessor<Integer> SPEECH_ID = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.INT);
    /** Cote du videur dans une paire (-1 gauche, 1 droite) : les gestes sont symetriques. */
    static final EntityDataAccessor<Byte> SIDE = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.BYTE);

    /** Geste "entre" : la porte est a sa gauche (il la montre du bras gauche), sinon a sa droite. */
    private static final EntityDataAccessor<Boolean> POINT_LEFT = SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.BOOLEAN);

    /** Duree d'affichage d'une replique (ticks). */
    public static final int SPEECH_TICKS = 70;
    int stanceLeft;

    // ---- client : ticks depuis la derniere replique / le dernier changement d'attitude, attitude precedente (fondu)
    public int speechAge = 10_000, stanceAge;
    public byte prevStance, lastStance;

    public GuardEntity(EntityType<? extends GuardEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    /** Position et orientation imposees (corps et tete dans la meme direction). */
    public void place(Vec3 at, float yaw) {
        setPos(at.x, at.y, at.z);
        setYRot(yaw);
        setXRot(0);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STANCE, IDLE);
        entityData.define(SPEECH, Component.empty());
        entityData.define(SPEECH_ID, 0);
        entityData.define(SIDE, (byte) 1);
        entityData.define(POINT_LEFT, false);
    }

    public boolean pointsLeft() {
        return entityData.get(POINT_LEFT);
    }

    /** "Entre" : il montre un endroit (la porte) du bras qui est de ce cote-la, quelques instants. */
    public void pointAt(Vec3 target, int ticks) {
        double r = Math.toRadians(yBodyRot);
        // droite du videur : (-cos, -sin) du lacet
        double side = (target.x - getX()) * -Math.cos(r) + (target.z - getZ()) * -Math.sin(r);
        entityData.set(POINT_LEFT, side < 0);
        stance(POINT, ticks);
    }

    public byte stance() {
        return entityData.get(STANCE);
    }

    public void stance(byte s) {
        if (stance() != s) entityData.set(STANCE, s);
    }

    /** Attitude tenue quelques instants, puis retour aux bras croises (videur en poste). */
    public void stance(byte s, int ticks) {
        stance(s);
        stanceLeft = ticks;
    }

    public int side() {
        return entityData.get(SIDE);
    }

    public Component speech() {
        return entityData.get(SPEECH);
    }

    /** Le videur parle : bulle au-dessus de sa tete et grognement. */
    public void say(Component text) {
        entityData.set(SPEECH, text);
        entityData.set(SPEECH_ID, entityData.get(SPEECH_ID) + 1);
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY() + 1.6, getZ(), SoundEvents.VINDICATOR_AMBIENT, SoundSource.NEUTRAL, 1f, 0.72f + random.nextFloat() * 0.12f);
        }
    }

    /** Un joueur tente d'entrer sans payer : main levee, regard sur lui. */
    public void challenge(ServerPlayer p, Component line) {
        stance(STOP, 70);
        getLookControl().setLookAt(p, 60, 60);
        say(line);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SPEECH_ID.equals(key)) speechAge = 0;
        if (STANCE.equals(key)) {
            prevStance = lastStance;
            lastStance = stance();
            stanceAge = 0;
        }
    }

    /** Bras tendus (lancer, geste vers la porte) : le modele deborde de la boite de collision. */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.0, 0.3, 1.0);
    }

    // =================================================================== vie
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            speechAge++;
            stanceAge++;
        } else {
            tickServeur();
        }
    }

    /** Partie serveur du tick (renfort oublie qui s'en va) : redefinie par GuardEntityServeur, neutre ici. */
    protected void tickServeur() {
    }

    // =================================================================== ni bouscule, ni blesse
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity other) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** Un operateur en creatif retire un videur d'un coup ; personne d'autre ne peut lui faire de mal. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // part commune : jamais blesse, sauf degats qui ignorent l'invulnerabilite (cote serveur) ; le retrait par un
        // operateur est dans GuardEntityServeur
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // part commune ; le dialogue avec le videur (ZoneManager.talk) est dans GuardEntityServeur
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }
}
