package fr.heralisz.clans.territory;

import fr.heralisz.clans.registry.ClansRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * Bloc-entite de la caisse larguee : 27 cases, moment de l'atterrissage (ms epoch : parachute degonfle 30 s cote
 * client, disparition 15 min cote serveur), zone et tag du clan qui a lance le signal (verrouillage optionnel).
 * Synchronise aux clients (zone, clan, atterrissage) ; le contenu reste serveur (menu).
 */
public class CaisseRavitaillementBlockEntity extends BaseContainerBlockEntity {
    public static final int CASES = 27;
    public static final long DUREE_SOL_MS = 30_000, DUREE_VIE_MS = 15 * 60_000;

    private NonNullList<ItemStack> items = NonNullList.withSize(CASES, ItemStack.EMPTY);
    public long atterriLe;
    public String zoneId = "";
    public String clanTag = "";

    public CaisseRavitaillementBlockEntity(BlockPos pos, BlockState state) {
        super(ClansRegistry.CAISSE_BE.get(), pos, state);
    }

    public void initialiser(String zoneId, String clanTag, long atterriLe) {
        this.zoneId = zoneId == null ? "" : zoneId;
        this.clanTag = clanTag == null ? "" : clanTag;
        this.atterriLe = atterriLe;
    }

    /** Toutes les secondes : la caisse disparait 15 min apres l'atterrissage. */
    public static void tickServeur(Level level, BlockPos pos, BlockState state, CaisseRavitaillementBlockEntity be) {
        if (level.getGameTime() % 20 != 0 || be.atterriLe <= 0) return;
        if (System.currentTimeMillis() - be.atterriLe > DUREE_VIE_MS) {
            be.items.clear();
            level.removeBlock(pos, false);
        }
    }

    /** Parachute degonfle encore visible (30 s apres l'atterrissage, horloge du client). */
    public boolean parachuteAuSol() {
        return atterriLe > 0 && System.currentTimeMillis() - atterriLe < DUREE_SOL_MS;
    }

    // ------------------------------------------------------------------ conteneur
    @Override
    protected Component getDefaultName() {
        return Component.translatable("zone.caisse.titre");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new ChestMenu(ClansRegistry.CAISSE_MENU.get(), id, inv, this, 3);
    }

    @Override
    public int getContainerSize() {
        return CASES;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return items.get(i);
    }

    @Override
    public ItemStack removeItem(int i, int n) {
        ItemStack s = ContainerHelper.removeItem(items, i, n);
        if (!s.isEmpty()) setChanged();
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        return ContainerHelper.takeItem(items, i);
    }

    @Override
    public void setItem(int i, ItemStack s) {
        items.set(i, s);
        if (s.getCount() > getMaxStackSize()) s.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player p) {
        return level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // ------------------------------------------------------------------ NBT et synchronisation
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("atterriLe", atterriLe);
        tag.putString("zone", zoneId);
        tag.putString("clan", clanTag);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        atterriLe = tag.getLong("atterriLe");
        zoneId = tag.getString("zone");
        clanTag = tag.getString("clan");
        items = NonNullList.withSize(CASES, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
    }

    /** Vers les clients : pas le contenu. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putLong("atterriLe", atterriLe);
        t.putString("zone", zoneId);
        t.putString("clan", clanTag);
        return t;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Le parachute degonfle s'etale sur 3 blocs autour. */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 4, p.getY(), p.getZ() - 4, p.getX() + 5, p.getY() + 2, p.getZ() + 5);
    }

    /** Cote serveur : sauvegarde + envoi aux clients. */
    public void synchroniser() {
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
}
