package fr.heraliz.crates.opening;

import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.loot.CrateLoot;
import fr.heraliz.crates.loot.LootEntry;
import fr.heraliz.crates.loot.LootManager;
import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.network.HzNet;
import fr.heraliz.crates.network.Packets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Editeur de butin des administrateurs (outil d'edition sur une caisse, ou /heraliz edit) : objets, quantites, raretes
 * et chances, enregistres dans config/heralizcrates/<caisse>.json comme avec les commandes.
 */
public final class LootEditing {
    private LootEditing() {
    }

    public static final int MAX_WEIGHT = 1_000_000, MAX_COUNT = fr.heraliz.crates.block.CrateAnim.EDITEUR_MAX_COUNT;

    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    public static void open(ServerPlayer player, CrateType type) {
        if (!allowed(player)) {
            player.displayClientMessage(Component.translatable("message.heralizcrates.editor.denied").withStyle(ChatFormatting.RED), true);
            return;
        }
        CrateLoot loot = LootManager.get(type);
        List<Packets.Row> rows = new ArrayList<>();
        for (LootEntry e : loot.entries) {
            ItemStack shown = e.isValid() ? e.displayStack() : missing(e);
            rows.add(new Packets.Row(shown, e.count, e.weight, e.rarity.ordinal(), !e.giveItem, e.item));
        }
        HzNet.toPlayer(player, new Packets.EditorOpen(type.id, version(loot), rows));
    }

    /** Objet introuvable (mod retire) : une barriere a son nom. */
    private static ItemStack missing(LootEntry e) {
        ItemStack st = new ItemStack(Items.BARRIER);
        st.setHoverName(Component.literal(e.item).withStyle(ChatFormatting.RED));
        return st;
    }

    /** Empreinte du butin : refuse une sauvegarde faite sur une version perimee (autre admin, /heraliz reload...). */
    private static int version(CrateLoot loot) {
        StringBuilder b = new StringBuilder();
        for (LootEntry e : loot.entries) b.append(e.toJson()).append('\n');
        return b.toString().hashCode();
    }

    public static void save(ServerPlayer player, Packets.EditorSave msg) {
        if (!allowed(player)) return;
        CrateType type = CrateType.byId(msg.crate());
        if (type == null) return;
        CrateLoot loot = LootManager.get(type);
        if (msg.version() != version(loot)) {
            player.sendSystemMessage(Component.translatable("message.heralizcrates.editor.stale").withStyle(ChatFormatting.RED));
            open(player, type);
            return;
        }
        List<LootEntry> old = loot.entries;
        List<LootEntry> out = new ArrayList<>();
        for (Packets.SaveRow r : msg.rows()) {
            LootEntry e;
            if (r.orig() >= 0 && r.orig() < old.size()) {
                e = LootEntry.fromTag(old.get(r.orig()).toTag());
            } else {
                ItemStack st = r.stack();
                if (st.isEmpty()) continue;
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
                if (id == null) continue;
                e = new LootEntry();
                e.item = id.toString();
                if (st.hasTag()) e.nbt = st.getTag().toString();
            }
            e.count = Math.max(1, Math.min(MAX_COUNT, r.count()));
            e.weight = Math.max(0, Math.min(MAX_WEIGHT, r.weight()));
            e.rarity = LootRarity.values()[Math.max(0, Math.min(LootRarity.values().length - 1, r.rarity()))];
            out.add(e);
        }
        loot.entries.clear();
        loot.entries.addAll(out);
        LootManager.save(type);
        HeralizCrates.LOGGER.info("[HeralizCrates] {} a modifie le butin de {} ({} objets)", player.getGameProfile().getName(), type.id, out.size());
        player.sendSystemMessage(Component.translatable("message.heralizcrates.editor.saved", type.crateName(),
                Component.literal(String.valueOf(out.size())).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN));
    }
}
