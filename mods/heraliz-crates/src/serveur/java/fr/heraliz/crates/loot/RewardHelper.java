package fr.heraliz.crates.loot;

import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;


/**
 * Remise d'une recompense a un joueur (objets + commandes + annonces).
 */
public final class RewardHelper {
    private RewardHelper() {
    }

    /** Ouverture dans le monde : objets, commandes, message, son, grand titre et diffusion. */
    public static void give(ServerPlayer player, CompoundTag rewardTag, CrateType type) {
        LootEntry e = LootEntry.fromTag(rewardTag);
        if (player.getServer() == null) return;
        deliver(player, e, type);
        announce(player, e, type);
    }

    /** Objets (decoupes en piles pleines, au sol si l'inventaire est plein) et commandes de la recompense. */
    public static void deliver(ServerPlayer player, LootEntry e, CrateType type) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (e.giveItem) {
            ItemStack full = e.createStack(1);
            if (!full.isEmpty()) {
                int remaining = e.count;
                int max = Math.max(1, full.getMaxStackSize());
                while (remaining > 0) {
                    int n = Math.min(remaining, max);
                    ItemStack part = full.copy();
                    part.setCount(n);
                    ItemHandlerHelper.giveItemToPlayer(player, part);
                    remaining -= n;
                }
            }
        }
        for (String cmd : e.commands) {
            String c = cmd.replace("{player}", player.getGameProfile().getName())
                    .replace("{uuid}", player.getStringUUID())
                    .replace("{crate}", type.id);
            if (c.startsWith("/")) c = c.substring(1);
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
            } catch (Exception ex) {
                HeralizCrates.LOGGER.error("[HeralizCrates] Commande de recompense en erreur : {}", c, ex);
            }
        }
    }

    /** Nom de l'objet gagne suivi de sa quantite. */
    public static MutableComponent label(LootEntry e) {
        ItemStack display = e.displayStack();
        MutableComponent itemName = display.isEmpty() ? Component.literal(e.item) : display.getDisplayName().copy();
        return itemName.append(Component.literal(e.count > 1 ? " x" + e.count : "").withStyle(ChatFormatting.WHITE));
    }

    /** Annonces d'une ouverture dans le monde. */
    public static void announce(ServerPlayer player, LootEntry e, CrateType type) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        player.sendSystemMessage(Component.translatable("message.heralizcrates.won",
                label(e), e.rarity.displayName(), type.crateName()).withStyle(ChatFormatting.GRAY));
        player.level().playSound(null, player.blockPosition(), ModRegistry.SND_REWARD_RECEIVE.get(), SoundSource.PLAYERS, 0.9F, 1.0F);

        if (e.rarity.atLeast(GeneralConfig.titleMinRarity)) {
            ItemStack display = e.displayStack();
            MutableComponent itemName = display.isEmpty() ? Component.literal(e.item) : display.getHoverName().copy();
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("✦ ").append(e.rarity.displayName().withStyle(ChatFormatting.BOLD)).append(" ✦")
                            .withStyle(s -> s.withColor(e.rarity.color))));
            player.connection.send(new ClientboundSetSubtitleTextPacket(itemName.append(
                    Component.literal(e.count > 1 ? " x" + e.count : "").withStyle(ChatFormatting.WHITE))));
        }
        broadcast(server, player.getGameProfile().getName(), e, type);
    }

    /**
     * Annonces d'une ouverture dans l'interface : l'ecran montre deja l'objet et joue ses sons, donc ni son ni grand
     * titre ; le message habituel, et les objets rares diffuses a tout le serveur comme d'habitude.
     */
    public static void announceInterface(ServerPlayer player, LootEntry e, CrateType type) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        player.sendSystemMessage(Component.translatable("message.heralizcrates.won",
                label(e), e.rarity.displayName(), type.crateName()).withStyle(ChatFormatting.GRAY));
        broadcast(server, player.getGameProfile().getName(), e, type);
    }

    private static void broadcast(MinecraftServer server, String playerName, LootEntry e, CrateType type) {
        if (!e.rarity.atLeast(GeneralConfig.broadcastMinRarity)) return;
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.heralizcrates.broadcast",
                Component.literal(playerName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                label(e), e.rarity.displayName(), type.crateName()).withStyle(ChatFormatting.GRAY), false);
    }
}
