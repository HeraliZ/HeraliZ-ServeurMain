package fr.heraliz.crac.menu;

import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Upkeep menu of a Claim Block. Server side the slots show one stack of each upkeep stock; the counts (stock, needed,
 * minutes left) reach the viewer through data slots, so nobody else's client ever receives them.
 */
public class ClaimUpkeepMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 165;
    public static final int[] SLOT_X = new int[]{47, 69, 91, 113};
    public static final int SLOT_Y = 26;
    public static final int INV_X = 8;
    public static final int INV_Y = 82;
    public static final int HOTBAR_Y = 140;
    /**
     * Synced numbers: stored and needed per slot, the minutes left, then 1 when unpaid upkeep makes bases decay; each int
     * travels as two 16-bit halves.
     */
    private static final int VALUES = ClaimBlockEntity.SLOTS * 2 + 2;
    private static final int DATA_COUNT = VALUES * 2;
    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;
    /** Server side only. */
    @Nullable
    private final ClaimBlockEntity be;

    public ClaimUpkeepMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, null, buf.readBlockPos());
    }

    public ClaimUpkeepMenu(int id, Inventory playerInv, ClaimBlockEntity be) {
        this(id, playerInv, be, be.getBlockPos());
    }

    private ClaimUpkeepMenu(int id, Inventory playerInv, @Nullable ClaimBlockEntity be, BlockPos pos) {
        super(ModMenus.CLAIM_UPKEEP.get(), id);
        this.be = be;
        this.pos = pos;
        this.container = be == null ? new SimpleContainer(ClaimBlockEntity.SLOTS) : new StockContainer(be);
        this.data = be == null ? new SimpleContainerData(DATA_COUNT) : new StockData(be);
        this.container.startOpen(playerInv.player);

        for (int i = 0; i < ClaimBlockEntity.SLOTS; i++) {
            this.addSlot(new UpkeepSlot(this.container, i, SLOT_X[i], SLOT_Y));
        }

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
            }
        }

        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(playerInv, c, INV_X + c * 18, HOTBAR_Y));
        }
        this.addDataSlots(this.data);
    }

    public BlockPos pos() {
        return this.pos;
    }

    public int stored(int slot) {
        return this.value(slot * 2);
    }

    public int needed(int slot) {
        return this.value(slot * 2 + 1);
    }

    /** Minutes until a resource runs out, -1 when nothing needs upkeep. */
    public int upkeepMinutes() {
        return this.value(ClaimBlockEntity.SLOTS * 2);
    }

    /** Whether a base decays once its upkeep runs out: false when the server turned decay off ({@code decay.enabled}). */
    public boolean decayEnabled() {
        return this.value(ClaimBlockEntity.SLOTS * 2 + 1) != 0;
    }

    private int value(int index) {
        return (this.data.get(index * 2 + 1) & 0xFFFF) << 16 | (this.data.get(index * 2) & 0xFFFF);
    }

    /** Whether a slot takes the stack: the stock decides on the server, the synced view on the client. */
    private boolean mayPlace(int slot, ItemStack stack) {
        if (this.be != null) {
            return this.be.accepts(slot, stack);
        }
        if (!Config.SPEC.isLoaded()) {
            return false;
        }
        Item want = ClaimBlockEntity.resourceForSlot(slot).item();
        ItemStack shown = this.container.getItem(slot);
        return want != Items.AIR && stack.is(want) && (shown.isEmpty() || shown.is(want)) && this.stored(slot) < ClaimBlockEntity.capacity();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int upkeepEnd = ClaimBlockEntity.SLOTS;
        if (index < upkeepEnd) {
            ItemStack window = this.container.getItem(index);
            if (window.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack moving = window.copy();
            if (this.moveItemStackTo(moving, upkeepEnd, this.slots.size(), true)) {
                int moved = window.getCount() - moving.getCount();
                if (moved > 0) {
                    this.container.removeItem(index, moved);
                    this.slots.get(index).setChanged();
                }
            }
            return ItemStack.EMPTY;
        }
        Slot from = this.slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        int before = stack.getCount();
        for (int i = 0; i < upkeepEnd && !stack.isEmpty(); i++) {
            this.slots.get(i).safeInsert(stack, stack.getCount());
        }
        if (stack.getCount() != before) {
            if (stack.isEmpty()) {
                from.set(ItemStack.EMPTY);
            } else {
                from.setChanged();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
        if (this.be != null && !player.level().isClientSide()) {
            this.be.majEcran();   // le stock a pu changer : l'écran de l'armoire suit tout de suite
        }
    }

    private final class UpkeepSlot extends Slot {
        private final int idx;

        UpkeepSlot(Container container, int idx, int x, int y) {
            super(container, idx, x, y);
            this.idx = idx;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ClaimUpkeepMenu.this.mayPlace(this.idx, stack);
        }

        @Override
        public int getMaxStackSize() {
            return ClaimBlockEntity.WINDOW;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(ClaimBlockEntity.WINDOW, stack.getMaxStackSize());
        }

        /** Items put in go to the stock, past the one-stack window. */
        @Override
        public ItemStack safeInsert(ItemStack stack, int increment) {
            ClaimBlockEntity stock = ClaimUpkeepMenu.this.be;
            if (stock == null) {
                return super.safeInsert(stack, increment);
            }
            if (!stack.isEmpty() && this.mayPlace(stack)) {
                int added = stock.addStored(this.idx, stack.getItem(), Math.min(increment, stack.getCount()));
                if (added > 0) {
                    stack.shrink(added);
                    this.setChanged();
                }
            }
            return stack;
        }
    }

    /** The menu's view of the stock (server side): one stack per slot, changes applied to the counts. */
    private static final class StockContainer implements Container {
        private final ClaimBlockEntity be;

        StockContainer(ClaimBlockEntity be) {
            this.be = be;
        }

        @Override
        public int getContainerSize() {
            return ClaimBlockEntity.SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return this.be.isStockEmpty();
        }

        @NotNull
        @Override
        public ItemStack getItem(int slot) {
            return this.be.windowStack(slot);
        }

        @NotNull
        @Override
        public ItemStack removeItem(int slot, int amount) {
            return this.be.withdraw(slot, amount);
        }

        @NotNull
        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return this.be.withdraw(slot, ClaimBlockEntity.WINDOW);
        }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) {
            this.be.setWindow(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return ClaimBlockEntity.WINDOW;
        }

        @Override
        public void setChanged() {
            this.be.setChanged();
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return this.be.stillValidFor(player);
        }

        @Override
        public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
            return this.be.accepts(slot, stack);
        }

        @Override
        public void clearContent() {
            // the stock is only emptied by withdrawals, upkeep and the block breaking
        }
    }

    /** Stock, needed, minutes left and the decay switch, split in 16-bit halves (data slots travel as shorts). */
    private static final class StockData implements ContainerData {
        private final ClaimBlockEntity be;

        StockData(ClaimBlockEntity be) {
            this.be = be;
        }

        @Override
        public int get(int index) {
            int v = this.full(index / 2);
            return index % 2 == 0 ? v & 0xFFFF : v >>> 16;
        }

        private int full(int value) {
            int slots = ClaimBlockEntity.SLOTS;
            if (value < slots * 2) {
                int slot = value / 2;
                return value % 2 == 0 ? this.be.getStored(slot) : this.be.getNeeded(slot);
            }
            if (value == slots * 2) {
                return this.be.upkeepMinutes();
            }
            return Config.decayEnabled.get() ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
