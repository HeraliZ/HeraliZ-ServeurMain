package fr.heraliz.airstrike.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/**
 * Un morceau de la boîte de touche de l'hélicoptère (cabine, poutre de queue), comme ceux du dragon de l'End : Forge les
 * range à part dans le monde ({@code Level#getEntities} les rend avec les autres entités), les balles de TaCZ les touchent
 * ({@code EntityKineticBullet.MaybeMultipartEntity} : dégâts au morceau, invulnérabilité remise à zéro sur l'hélicoptère),
 * et chaque coup est renvoyé à l'hélicoptère avec le numéro du morceau (multiplicateur de la queue). Placé chaque tick par
 * {@link Helicopter#placerMorceaux()}, selon le lacet seulement. Jamais sauvegardé, jamais envoyé seul aux joueurs : le jeu
 * du joueur recrée les mêmes morceaux, avec les mêmes numéros d'entité ({@link Helicopter#setId}).
 */
public class HelicopterPart extends PartEntity<Helicopter> {
    public final Helicopter helico;
    /** Rang dans {@link Helicopter#MORCEAUX}. */
    public final int rang;
    private final EntityDimensions taille;

    public HelicopterPart(Helicopter helico, int rang, float largeur, float hauteur) {
        super(helico);
        this.helico = helico;
        this.rang = rang;
        this.taille = EntityDimensions.fixed(largeur, hauteur);
        this.refreshDimensions();
        this.noPhysics = true;
    }

    /** Morceau de la poutre de queue (multiplicateur des réglages). */
    public boolean queue() {
        return Helicopter.MORCEAUX[rang][4] > 0;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return helico.toucheMorceau(this, source, amount);
    }

    @Override
    public boolean is(Entity e) {
        return this == e || helico == e;
    }

    @Override
    public ItemStack getPickResult() {
        return ItemStack.EMPTY;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return taille;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
