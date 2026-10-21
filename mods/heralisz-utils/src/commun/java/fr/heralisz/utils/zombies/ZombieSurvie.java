package fr.heralisz.utils.zombies;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** Zombie de Minecraft avec une tenue (voir {@link ZombiesSurvie}). Rien d'autre ne change. */
public class ZombieSurvie extends Zombie {
    public static final EntityDataAccessor<Integer> VARIANTE = SynchedEntityData.defineId(ZombieSurvie.class, EntityDataSerializers.INT);

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
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HeralizTenue", entityData.get(VARIANTE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANTE, tag.getInt("HeralizTenue"));
    }

    /** Pas de bebes ni de conversion en noye : un seul modele, une seule taille. */
    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean bebe) {
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }
}
