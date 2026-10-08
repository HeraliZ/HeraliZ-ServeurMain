package fr.heraliz.crates.menu;

import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.loot.CrateLoot;
import fr.heraliz.crates.loot.LootEntry;
import fr.heraliz.crates.loot.LootManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Apercu (lecture seule) du contenu d'un coffre avec les chances de chaque objet.
 * Utilise un menu coffre vanilla : aucun objet ne peut etre retire.
 */
public class PreviewMenu extends ChestMenu {

    private PreviewMenu(MenuType<?> type, int id, Inventory inv, Container container, int rows) {
        super(type, id, inv, container, rows);
    }

    public static void open(ServerPlayer player, CrateType type) {
        CrateLoot loot = LootManager.get(type);
        List<LootEntry> entries = new ArrayList<>();
        for (LootEntry e : loot.entries) {
            if (e.isValid()) entries.add(e);
        }
        entries.sort(Comparator.<LootEntry>comparingInt(e -> -e.rarity.ordinal()).thenComparingInt(e -> e.weight));
        int n = Math.min(54, Math.max(1, entries.size()));
        int rows = Math.max(1, Math.min(6, (n + 8) / 9));
        SimpleContainer container = new SimpleContainer(rows * 9);
        for (int i = 0; i < Math.min(entries.size(), rows * 9); i++) {
            LootEntry e = entries.get(i);
            ItemStack s = e.displayStack();
            if (s.isEmpty()) continue;
            addLore(s, Component.literal(""));
            addLore(s, Component.translatable("gui.heralizcrates.rarity", e.rarity.displayName()).withStyle(ChatFormatting.GRAY));
            addLore(s, Component.translatable("gui.heralizcrates.chance",
                    Component.literal(String.format(Locale.ROOT, "%.2f%%", loot.chance(e))).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.GRAY));
            if (e.count > 64) {
                addLore(s, Component.translatable("gui.heralizcrates.amount", e.count).withStyle(ChatFormatting.GRAY));
            }
            container.setItem(i, s);
        }
        if (entries.isEmpty()) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.setHoverName(Component.translatable("message.heralizcrates.empty").withStyle(ChatFormatting.RED));
            container.setItem(4, barrier);
        }
        MenuType<?> menuType = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        final int r = rows;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new PreviewMenu(menuType, id, inv, container, r),
                Component.translatable("gui.heralizcrates.preview", type.crateName())));
    }

    private static void addLore(ItemStack stack, Component line) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        Component clean = line.copy().withStyle(s -> s.withItalic(false));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(clean)));
        display.put("Lore", lore);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // lecture seule
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
