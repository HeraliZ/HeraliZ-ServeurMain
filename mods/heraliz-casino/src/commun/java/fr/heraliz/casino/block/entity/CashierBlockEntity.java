package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import javax.annotation.Nullable;

/**
 * Caisse du casino : achat de jetons de casino contre des jetons du serveur (mod HeraliZ Economie, facultatif) ou
 * des objets, revente des jetons de casino (jetons du serveur, objets ou commande d'economie).
 * Les taux sont dans config/heralizcasino/casino.json (section "caisse").
 */
public class CashierBlockEntity extends MachineBlockEntity {
    public static final int ACTION_BUY = 0, ACTION_SELL = 1, ACTION_CASH_CMD = 2;
    /** Change avec les jetons du serveur : args = {0, nombre d'unites du taux}. */
    public static final int ACTION_BUY_SERVER = 3, ACTION_SELL_SERVER = 4;

    // ---- synchronise (animation du guichet)
    public long lastTx = Long.MIN_VALUE / 4;
    public int lastTxType;
    public long lastTxAmount;

    public CashierBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.CAISSE, pos, state);
    }

    @Nullable
    public static Item item(CasinoConfig.Exchange e) {
        ResourceLocation id = ResourceLocation.tryParse(e.item);
        if (id == null) return null;
        Item it = ForgeRegistries.ITEMS.getValue(id);
        return it == null || it == Items.AIR ? null : it;
    }

    /** Nombre d'objets "simples" (sans NBT) de ce type dans l'inventaire. */
    public static int countItems(net.minecraft.world.entity.player.Player p, Item item) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.hasTag()) n += s.getCount();
        }
        return n;
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        tag.putLong("LastTx", lastTx);
        tag.putInt("LastTxType", lastTxType);
        tag.putLong("LastTxAmount", lastTxAmount);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        if (!tag.contains("LastTx")) return;
        lastTx = tag.getLong("LastTx");
        lastTxType = tag.getInt("LastTxType");
        lastTxAmount = tag.getLong("LastTxAmount");
    }
}
