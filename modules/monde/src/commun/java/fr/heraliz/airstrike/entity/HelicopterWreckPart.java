package fr.heraliz.airstrike.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/**
 * Une boîte de collision de l'épave de l'hélicoptère (cabine, bout de poutre, queue arrachée), comme les rondins de la
 * barricade du bunker : Forge range ces morceaux à part dans le monde, des deux côtés ({@code Level#getPartEntities}), et
 * les rend avec les autres entités. {@link #canBeCollidedWith()} : joueurs, zombies et objets ne passent plus au travers
 * (même calcul chez le joueur et sur le serveur : pas de retour en arrière) ; {@link #isPickable()} : les balles de TaCZ s'y
 * arrêtent (l'épave couvre ceux qui sont derrière). Un coup ne fait rien à l'épave ({@link HelicopterWreck#touche}).
 * Placé par {@link HelicopterWreck#placerMorceaux()}. Jamais sauvegardé, jamais envoyé seul aux joueurs : le jeu du joueur
 * recrée les mêmes morceaux avec les mêmes numéros d'entité ({@link HelicopterWreck#setId}).
 */
public class HelicopterWreckPart extends PartEntity<HelicopterWreck> {
    public final HelicopterWreck epave;
    /** Rang dans {@link HelicopterWreck#BOITES}. */
    public final int rang;
    private final EntityDimensions taille;

    public HelicopterWreckPart(HelicopterWreck epave, int rang, float cote, float hauteur) {
        super(epave);
        this.epave = epave;
        this.rang = rang;
        this.taille = EntityDimensions.fixed(cote, hauteur);
        this.refreshDimensions();
        this.noPhysics = true;
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
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return epave.touche(this, source);
    }

    @Override
    public boolean is(Entity e) {
        return this == e || epave == e;
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
