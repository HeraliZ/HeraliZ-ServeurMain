package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.pont.Crochet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Caisse militaire que transportait l'hélicoptère (deux par épave, table T6 de HeraliZ Loot ; modèle : partie « caisse »
 * de geo/helico_epave.geo). Verrouillée tant que l'épave brûle (FEU_FIN), puis fouillée comme un conteneur de loot
 * (écran de fouille de HeraliZ Loot avec le badge « T6 »), et disparaît avec l'épave. Contenu, ouverture et sauvegarde :
 * {@code CaisseHelicoEntityServeur} (partie serveur).
 */
public class CaisseHelicoEntity extends Entity implements Container, MenuProvider {
    public static final EntityDataAccessor<Long> FEU_FIN = SynchedEntityData.defineId(CaisseHelicoEntity.class, EntityDataSerializers.LONG);
    public static final int CASES = 27;

    /** Jeu du joueur, à chaque tick : fumée tant qu'elle est chaude (client/helico/HelicoClient). */
    public static final Crochet<CaisseHelicoEntity> TICK_CLIENT = new Crochet<>();

    public CaisseHelicoEntity(EntityType<? extends CaisseHelicoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FEU_FIN, 0L);
    }

    public long feuFin() {
        return entityData.get(FEU_FIN);
    }

    public boolean brule() {
        return level().getGameTime() < feuFin();
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround() && !level().isClientSide) {
            setDeltaMovement(0, Math.max(-1.5, getDeltaMovement().y - 0.06), 0);
            move(MoverType.SELF, getDeltaMovement());
        } else if (onGround() && getDeltaMovement().lengthSqr() > 0) {
            setDeltaMovement(Vec3.ZERO);
        }
        if (level().isClientSide) TICK_CLIENT.accept(this);
        else tickServeur();
    }

    /** Serveur : disparition avec l'épave. CaisseHelicoEntityServeur ; neutre ici. */
    protected void tickServeur() {
    }

    @Override
    public InteractionResult interact(Player joueur, InteractionHand main) {
        // ouverture : CaisseHelicoEntityServeur (serveur)
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventaire, Player joueur) {
        return ChestMenu.threeRows(id, inventaire, this);
    }

    @Override
    public Component getDisplayName() {
        // clé d'un bloc de HeraliZ Loot (traduite dans les langues de l'Airstrike) : l'écran de fouille de HeraliZ Loot
        // s'ouvre avec le badge du palier (« T6 »), comme pour un conteneur de la carte
        return Component.translatable("block.heraliz_loot.t6_caisse_helico");
    }

    // Contenu : CaisseHelicoEntityServeur. Neutre ici : une caisse vide.
    @Override
    public int getContainerSize() {
        return CASES;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int i, int n) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player joueur) {
        return isAlive() && joueur.distanceToSqr(this) < 8 * 8;
    }

    @Override
    public void clearContent() {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
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
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160.0 * 160.0;
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
