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
 * Caisse du casino : echange des capsules (jetons de casino, trouvees sur la carte) contre des objets. Le change avec
 * les herals est ferme par le code (EconomieServeur.CHANGE_HERALS, choix du proprietaire du 22/10).
 * Les prix sont dans config/heralizcasino/casino.json (section "caisse").
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

    /**
     * Objet d'un echange tel que la caisse le donne : l'objet et son NBT (cle "nbt" de l'echange, ex. un soin
     * {ConsumableId:"heralisz_heal:ibuprofen"} ou des munitions {AmmoId:"tacz:9mm"}), ou null si l'objet ou le NBT est
     * invalide.
     */
    @Nullable
    public static ItemStack stack(CasinoConfig.Exchange e) {
        Item it = item(e);
        if (it == null) return null;
        ItemStack s = new ItemStack(it);
        if (e.nbt != null && !e.nbt.isBlank()) {
            try {
                s.setTag(net.minecraft.nbt.TagParser.parseTag(e.nbt));
            } catch (Exception ex) {
                return null;
            }
        }
        return s;
    }

    /** Nombre d'objets identiques a "ref" (meme objet, meme NBT ; sans NBT : objets "simples") dans l'inventaire. */
    public static int countStack(net.minecraft.world.entity.player.Player p, ItemStack ref) {
        if (!ref.hasTag()) return countItems(p, ref.getItem());
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameTags(s, ref)) n += s.getCount();
        }
        return n;
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
