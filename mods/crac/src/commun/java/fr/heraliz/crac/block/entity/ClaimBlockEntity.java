package fr.heraliz.crac.block.entity;

import fr.heraliz.crac.pont.Serveur;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.UpkeepResource;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.menu.ClaimUpkeepMenu;
import fr.heraliz.crac.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Upkeep stock of a Claim Block: a count per {@link UpkeepResource} and the item that count is made of. It is not a
 * {@code Container}: hoppers, droppers and pipes see an insert-only item handler (they can feed the upkeep, never take
 * from it), and only the menu (members of the claim's group) hands items out. Clients only receive the colour: the
 * stock is shown to the menu's viewers through the menu's data slots.
 */
public class ClaimBlockEntity extends BlockEntity implements MenuProvider {
    /** One slot per {@link UpkeepResource}, in ordinal order. */
    public static final int SLOTS = UpkeepResource.COUNT;
    /** Largest stack a slot shows (and hands out) at once. */
    public static final int WINDOW = 64;
    private final int[] stored = new int[SLOTS];
    /** Item each slot holds (null when empty): the one put in, even when the config names another item since. */
    private final Item[] items = new Item[SLOTS];
    private final int[] needed = new int[SLOTS];
    private final double[] debt = new double[SLOTS];
    private int color = 16777215;
    /** Group whose claim this block holds; kept so that a claim lost in a crash can be restored. */
    @Nullable
    private UUID ownerGroup;
    private LazyOptional<IItemHandler> input = LazyOptional.of(() -> new UpkeepInput(this));

    public ClaimBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLAIM_BLOCK.get(), pos, state);
    }

    public static UpkeepResource resourceForSlot(int slot) {
        return UpkeepResource.bySlot(slot);
    }

    public static int capacity() {
        return Config.upkeepSlotCapacity.get();
    }

    // ------------------------------------------------------------------ stock

    public int getStored(int slot) {
        return this.stored[slot];
    }

    /** The item a slot holds, or null when it is empty (AIR when its saved id no longer resolves). */
    @Nullable
    public Item storedItem(int slot) {
        return this.stored[slot] > 0 ? this.items[slot] : null;
    }

    /** Size of the stack the menu shows for a slot: at most one stack of the stored item. */
    public int window(int slot) {
        Item item = this.storedItem(slot);
        if (item == null || item == Items.AIR) {
            return 0;
        }
        return Math.min(this.stored[slot], Math.min(WINDOW, item.getMaxStackSize()));
    }

    public ItemStack windowStack(int slot) {
        int n = this.window(slot);
        return n > 0 ? new ItemStack(this.storedItem(slot), n) : ItemStack.EMPTY;
    }

    /**
     * Whether a slot takes this stack now: the item the config names for it, into a slot that is empty or already
     * holds that item (stock of an item the config no longer names is used up or taken out first), below capacity.
     */
    public boolean accepts(int slot, ItemStack stack) {
        Item want = resourceForSlot(slot).item();
        if (want == Items.AIR || stack.isEmpty() || !stack.is(want)) {
            return false;
        }
        return this.stored[slot] < capacity() && (this.stored[slot] == 0 || this.items[slot] == want);
    }

    /** Adds up to {@code amount} of {@code item}; returns how many were taken. */
    public int addStored(int slot, Item item, int amount) {
        if (amount <= 0 || item == Items.AIR || (this.stored[slot] > 0 && this.items[slot] != item)) {
            return 0;
        }
        int add = Math.min(Math.max(0, capacity() - this.stored[slot]), amount);
        if (add > 0) {
            this.items[slot] = item;
            this.stored[slot] += add;
            this.setChanged();
        }
        return add;
    }

    public int takeStored(int slot, int amount) {
        int take = Math.min(amount, this.stored[slot]);
        if (take > 0) {
            this.stored[slot] -= take;
            if (this.stored[slot] == 0) {
                this.items[slot] = null;
            }
            this.setChanged();
        }
        return take;
    }

    /** Takes up to one window of the stored item out of a slot. */
    public ItemStack withdraw(int slot, int amount) {
        Item item = this.storedItem(slot);
        int n = Math.min(amount, this.window(slot));
        if (item == null || n <= 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, this.takeStored(slot, n));
    }

    /** Vanilla slot code replaces the shown stack: apply the difference to the stock, never relabelling it. */
    public void setWindow(int slot, ItemStack stack) {
        int window = this.window(slot);
        if (stack.isEmpty()) {
            if (window > 0) {
                this.takeStored(slot, window);
            }
            return;
        }
        Item current = this.storedItem(slot);
        if (current == null ? !this.accepts(slot, stack) : !stack.is(current)) {
            return;
        }
        int delta = stack.getCount() - window;
        if (delta > 0) {
            this.addStored(slot, stack.getItem(), delta);
        } else if (delta < 0) {
            this.takeStored(slot, -delta);
        }
    }

    public boolean isStockEmpty() {
        for (int n : this.stored) {
            if (n > 0) {
                return false;
            }
        }
        return true;
    }

    /** Empties the stock into stacks of the items it holds (the Claim Block is gone). */
    public List<ItemStack> drainToStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            Item item = this.storedItem(i);
            if (item != null && item != Items.AIR) {
                int max = item.getMaxStackSize();
                while (this.stored[i] > 0) {
                    int n = Math.min(max, this.stored[i]);
                    out.add(new ItemStack(item, n));
                    this.stored[i] -= n;
                }
            }
            this.stored[i] = 0;
            this.items[i] = null;
        }
        this.setChanged();
        return out;
    }

    // ------------------------------------------------------------------ upkeep numbers

    public int getNeeded(int slot) {
        return this.needed[slot];
    }

    public void setNeeded(int[] values) {
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            if (this.needed[i] != values[i]) {
                this.needed[i] = values[i];
                changed = true;
            }
        }
        if (changed) {
            this.setChanged();
        }
    }

    public double getDebt(int slot) {
        return this.debt[slot];
    }

    public void setDebt(int slot, double value) {
        if (this.debt[slot] != value) {
            this.debt[slot] = value;
            this.setChanged();
        }
    }

    /** Minutes until a resource runs out, or -1 when nothing needs upkeep. */
    public int upkeepMinutes() {
        double mins = Double.POSITIVE_INFINITY;
        double perMin = 60.0 * Config.upkeepHours.get();
        for (int i = 0; i < SLOTS; i++) {
            if (this.needed[i] > 0) {
                double rate = this.needed[i] / perMin;
                double m = rate <= 0.0 ? Double.POSITIVE_INFINITY : this.stored[i] / rate;
                mins = Math.min(mins, m);
            }
        }
        return mins == Double.POSITIVE_INFINITY ? -1 : (int) Math.floor(mins);
    }

    // ------------------------------------------------------------------ owner and colour

    public int getColor() {
        return this.color;
    }

    public void setColor(int color) {
        if (this.color != color) {
            this.color = color;
            this.setChanged();
            // the colour is the only thing clients render: resend the block
            if (this.level != null && !this.level.isClientSide()) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    @Nullable
    public UUID ownerGroup() {
        return this.ownerGroup;
    }

    public void setOwnerGroup(@Nullable UUID group) {
        this.ownerGroup = group;
        this.setChanged();
    }

    // ------------------------------------------------------------------ menu

    /** The menu stays open for members of the claim's group within reach; a kicked member loses it at once. */
    public boolean stillValidFor(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        if (player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) > 64.0) {
            return false;
        }
        if (player.getAbilities().instabuild || !(this.level instanceof ServerLevel sl)) {
            return true;
        }
        Serveur.Moteur m = Serveur.MOTEUR;   // droits du groupe / du clan : partie serveur
        return m == null || m.claimAcces(this, sl, player);
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable("crac.upkeep.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new ClaimUpkeepMenu(id, inv, this);
    }

    // ------------------------------------------------------------------ automation

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? this.input.cast() : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.input.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.input = LazyOptional.of(() -> new UpkeepInput(this));
    }

    /** What hoppers and pipes see: the stock's windows, room up to the capacity, and no way to take anything out. */
    private static final class UpkeepInput implements IItemHandler {
        private final ClaimBlockEntity be;

        UpkeepInput(ClaimBlockEntity be) {
            this.be = be;
        }

        @Override
        public int getSlots() {
            return SLOTS;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.be.windowStack(slot);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !this.be.accepts(slot, stack)) {
                return stack;
            }
            int room = capacity() - this.be.getStored(slot);
            int n = Math.min(room, stack.getCount());
            if (n <= 0) {
                return stack;
            }
            if (!simulate) {
                n = this.be.addStored(slot, stack.getItem(), n);
            }
            return n >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - n);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return capacity();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return this.be.accepts(slot, stack);
        }
    }

    // ------------------------------------------------------------------ saving and sync

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Color", this.color);
        if (this.ownerGroup != null) {
            tag.putUUID("Group", this.ownerGroup);
        }
        tag.putIntArray("Stored", this.stored.clone());
        ListTag itemIds = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            Item item = this.storedItem(i);
            ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
            itemIds.add(StringTag.valueOf(id == null ? "" : id.toString()));
        }
        tag.put("Items", itemIds);
        tag.putIntArray("Needed", this.needed.clone());
        long[] d = new long[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            d[i] = Double.doubleToLongBits(this.debt[i]);
        }
        tag.putLongArray("Debt", d);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Color")) {
            this.color = tag.getInt("Color");
        }
        this.ownerGroup = tag.hasUUID("Group") ? tag.getUUID("Group") : null;
        int[] s = tag.getIntArray("Stored");
        boolean hasItems = tag.contains("Items", Tag.TAG_LIST);
        ListTag itemIds = tag.getList("Items", Tag.TAG_STRING);
        for (int i = 0; i < SLOTS; i++) {
            this.stored[i] = i < s.length ? Math.max(0, s[i]) : 0;
            Item item = null;
            if (this.stored[i] > 0) {
                if (hasItems) {
                    ResourceLocation id = ResourceLocation.tryParse(i < itemIds.size() ? itemIds.getString(i) : "");
                    item = id == null ? Items.AIR : ForgeRegistries.ITEMS.getValue(id);
                } else {
                    // saved before the item was kept per slot: the stock was paid in the item configured then
                    item = resourceForSlot(i).item();
                }
            }
            this.items[i] = item == null && this.stored[i] > 0 ? Items.AIR : item;
        }
        int[] n = tag.getIntArray("Needed");
        for (int i = 0; i < SLOTS; i++) {
            this.needed[i] = i < n.length ? n[i] : 0;
        }
        long[] d = tag.getLongArray("Debt");
        for (int i = 0; i < SLOTS; i++) {
            this.debt[i] = i < d.length ? Double.longBitsToDouble(d[i]) : 0.0;
        }
    }

    /** Only render data goes to clients (every player tracking the chunk), never the stock or the owner. */
    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("Color", this.color);
        return t;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (tag.contains("Color")) {
            this.color = tag.getInt("Color");
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            int before = this.color;
            this.handleUpdateTag(pkt.getTag());
            if (this.color != before && this.level != null && this.level.isClientSide()) {
                // the tint is read when the chunk is meshed: redraw the block with its group colour
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 8);
            }
        }
    }
}
