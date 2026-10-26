package fr.heraliz.armory.menu;

import com.mojang.datafixers.util.Pair;
import fr.heraliz.armory.entity.Mannequin;
import fr.heraliz.armory.registry.ModEntities;
import fr.heraliz.armory.suit.SuitRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu du mannequin : 4 slots d'armure, main droite / main gauche, puis tous les slots Curios du mannequin
 * (visage, gilet, mains... dans l'ordre de Curios), et l'inventaire du joueur.
 */
public class MannequinMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 190;
    public static final int ARMOR_X = 8, TOP = 18, HANDS_X = 98, CURIO_X = 152, INV_Y = 108;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Nullable
    private final Mannequin mannequin;
    private final int entityId;
    /** Slots Curios dans l'ordre d'affichage : (identifiant, index dans ce slot). */
    public final List<Pair<String, Integer>> curioSlots = new ArrayList<>();
    private final int curioStart, playerStart;

    /** Cote client (ouverture recue du serveur). */
    public static MannequinMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int n = buf.readVarInt();
        Map<String, Integer> layout = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) layout.put(buf.readUtf(), buf.readVarInt());
        var e = inv.player.level().getEntity(entityId);
        return new MannequinMenu(id, inv, e instanceof Mannequin m ? m : null, layout, entityId);
    }

    public MannequinMenu(int id, Inventory inv, Mannequin m, Map<String, Integer> layout) {
        this(id, inv, m, layout, m.getId());
    }

    private MannequinMenu(int id, Inventory inv, @Nullable Mannequin m, Map<String, Integer> layout, int entityId) {
        super(ModEntities.MANNEQUIN_MENU.get(), id);
        this.mannequin = m;
        this.entityId = entityId;
        boolean server = !inv.player.level().isClientSide;
        Container equip = m != null && server ? new Equipment(m) : new SimpleContainer(6);
        // icones des cases vides : dessinees par l'ecran (MannequinScreen), en clair sur le verre
        for (int i = 0; i < 4; i++) {
            final EquipmentSlot slot = ARMOR[i];
            addSlot(new Slot(equip, i, ARMOR_X, TOP + i * 18) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    boolean fits = Mob.getEquipmentSlotForItem(stack) == slot || (slot == EquipmentSlot.HEAD && !stack.isEmpty()
                            && stack.getItem().canEquip(stack, slot, inv.player));
                    if (!fits || mannequin == null) return fits;
                    // 0.9.0 : une combinaison se porte seule, sur le mannequin comme sur le joueur
                    if (SuitRules.mayEquipArmor(mannequin, slot, stack)) return true;
                    SuitRules.tell(inv.player, !SuitRules.isSuit(stack), true);
                    return false;
                }
            });
        }
        addSlot(new Slot(equip, 4, HANDS_X, TOP + 36));
        addSlot(new Slot(equip, 5, HANDS_X, TOP + 54));
        curioStart = slots.size();
        var curios = m != null && server ? CuriosApi.getCuriosInventory(m).resolve().orElse(null) : null;
        int k = 0;
        for (var e : layout.entrySet()) {
            String sid = e.getKey();
            IItemHandlerModifiable handler = curios != null && curios.getStacksHandler(sid).isPresent()
                    ? curios.getStacksHandler(sid).get().getStacks() : new ItemStackHandler(e.getValue());
            for (int i = 0; i < e.getValue(); i++) {
                final int index = i;
                int col = k / 4, row = k % 4;
                addSlot(new SlotItemHandler(handler, i, CURIO_X - col * 18, TOP + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        if (mannequin == null) return true;
                        if (!SuitRules.mayEquipCurio(mannequin, sid, stack)) {
                            SuitRules.tell(inv.player, true, true);
                            return false;
                        }
                        return Mannequin.canEquip(sid, index, mannequin, stack);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
                curioSlots.add(Pair.of(sid, i));
                k++;
            }
        }
        playerStart = slots.size();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
    }

    @Nullable
    public Mannequin mannequin() {
        if (mannequin != null) return mannequin;
        return null;
    }

    public int entityId() {
        return entityId;
    }

    public int curioStart() {
        return curioStart;
    }

    @Override
    public boolean stillValid(Player player) {
        return mannequin == null || (mannequin.isAlive() && player.distanceToSqr(mannequin) < 64.0D);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < playerStart && !moved; i++) {
                Slot target = slots.get(i);
                if (i == 4 || i == 5) continue;          // les mains ne prennent que ce qu'on y pose a la main
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                int hot = playerStart + 27;
                if (index < hot ? !moveItemStackTo(stack, hot, slots.size(), false) : !moveItemStackTo(stack, playerStart, hot, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** Armure + mains du mannequin vues comme un conteneur (cote serveur). */
    private static final class Equipment implements Container {
        private static final EquipmentSlot[] ORDER = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        private final LivingEntity e;

        Equipment(LivingEntity e) {
            this.e = e;
        }

        @Override
        public int getContainerSize() {
            return ORDER.length;
        }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot s : ORDER) if (!e.getItemBySlot(s).isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return e.getItemBySlot(ORDER[i]);
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            ItemStack s = e.getItemBySlot(ORDER[i]);
            if (s.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = s.split(count);
            e.setItemSlot(ORDER[i], s.isEmpty() ? ItemStack.EMPTY : s);
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack s = e.getItemBySlot(ORDER[i]);
            e.setItemSlot(ORDER[i], ItemStack.EMPTY);
            return s;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            e.setItemSlot(ORDER[i], stack);
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return e.isAlive() && player.distanceToSqr(e) < 64.0D;
        }

        @Override
        public void clearContent() {
            for (EquipmentSlot s : ORDER) e.setItemSlot(s, ItemStack.EMPTY);
        }
    }
}
