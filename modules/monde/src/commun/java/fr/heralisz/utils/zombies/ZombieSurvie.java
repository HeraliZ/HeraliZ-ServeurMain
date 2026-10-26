package fr.heralisz.utils.zombies;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Zombie de Minecraft avec une tenue (voir {@link ZombiesSurvie}). Rien d'autre ne change. */
public class ZombieSurvie extends Zombie {
    public static final EntityDataAccessor<Integer> VARIANTE = SynchedEntityData.defineId(ZombieSurvie.class, EntityDataSerializers.INT);

    /** Faux tant que la tenue n'a pas ete choisie selon l'endroit (zombie des vagues de drapeau, /summon sans tenue). */
    public boolean tenueChoisie;

    public ZombieSurvie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANTE, 0);
    }

    public ZombiesSurvie.Tenue tenue() {
        return ZombiesSurvie.Tenue.de(entityData.get(VARIANTE));
    }

    public void tenue(ZombiesSurvie.Tenue t) {
        entityData.set(VARIANTE, t.ordinal());
        tenueChoisie = true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HeralizTenue", entityData.get(VARIANTE));
        tag.putBoolean("HeralizTenueChoisie", tenueChoisie);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANTE, tag.getInt("HeralizTenue"));
        tenueChoisie = tag.contains("HeralizTenue") || tag.getBoolean("HeralizTenueChoisie");
    }

    /** Pas de bebes ni de conversion en noye : un seul modele, une seule taille. */
    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean bebe) {
    }

    /** Ne brule jamais au soleil (le feu, les molotovs et la lave brulent toujours). */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    /** Un zombie pousse ses voisins un tick sur {@value #POUSSE_RYTHME} (banc du 26/10 : dans une horde, chercher les
     *  voisins à pousser à chaque tick était la plus grosse part du coût d'un zombie). */
    public static final int POUSSE_RYTHME = 4;
    /** Au repos, le déplacement (gravité, collisions) n'est refait qu'un tick sur {@value #REPOS_RYTHME}. */
    public static final int REPOS_RYTHME = 10;

    /** Jamais écrasé par la foule (dégâts « entassement » de Minecraft au-delà de 24 créatures au même endroit) : une
     *  horde serrée autour d'un joueur ne se tue pas toute seule. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.CRAMMING)) return false;
        return super.hurt(source, amount);
    }

    @Override
    protected void pushEntities() {
        if ((tickCount + getId()) % POUSSE_RYTHME == 0) super.pushEntities();
    }

    /**
     * Zombie au repos (serveur : posé au sol, sans cible, sans chemin, immobile, ni dans l'eau ni dans la lave) : son
     * déplacement n'est calculé qu'un tick sur {@value #REPOS_RYTHME} (le reste du temps il ne bouge pas de toute façon).
     * Un coup, une poussée, une cible ou un chemin le réveillent aussitôt.
     */
    @Override
    public void travel(Vec3 entree) {
        if (!level().isClientSide && auRepos(entree) && (tickCount + getId()) % REPOS_RYTHME != 0) return;
        super.travel(entree);
    }

    private boolean auRepos(Vec3 entree) {
        if (!onGround() || getTarget() != null || !getNavigation().isDone() || isPassenger() || isVehicle()) return false;
        if (entree.lengthSqr() > 1.0E-6 || jumping || hurtTime > 0 || isInWaterOrBubble() || isInLava()) return false;
        Vec3 v = getDeltaMovement();
        return v.x * v.x + v.z * v.z < 1.0E-6 && v.y <= 0 && v.y > -0.1;
    }
}
