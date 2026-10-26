package fr.heraliz.crates.event;

import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.block.CrateBlock;
import fr.heraliz.crates.block.CratePartBlock;
import fr.heraliz.crates.command.HeralizCommand;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.loot.LootManager;
import fr.heraliz.crates.loot.PendingRewards;
import fr.heraliz.crates.loot.RewardHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import fr.heraliz.crates.item.CrateEditorItem;
import fr.heraliz.crates.opening.CaseOpening;
import fr.heraliz.crates.opening.LootEditing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Evenements du serveur : commandes, butin, interface d'ouverture, recompenses en attente.
 */
@Mod.EventBusSubscriber(modid = HeralizCrates.MODID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        HeralizCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        int errors = LootManager.reload();
        HeralizCrates.LOGGER.info("[HeralizCrates] Butin charge ({} erreur(s))", errors);
    }

    /** Interface d'ouverture : objets donnes a la fin de l'animation. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) CaseOpening.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CaseOpening.logout(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CaseOpening.serverStopping(event.getServer());
    }

    /** Recompenses en attente (joueur deconnecte pendant l'animation). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        PendingRewards attente = PendingRewards.get(player.getServer());
        List<CompoundTag> rewards = attente.take(player.getUUID());
        // 25/10 (audit) : retirées sur le disque AVANT d'être données, comme à la fin de la roue (CaseOpening.deliver) ;
        // avant, un plantage avant la sauvegarde suivante les redonnait à la connexion d'après
        if (!rewards.isEmpty()) attente.sauverMaintenant(player.getServer());
        for (CompoundTag tag : rewards) {
            CrateType type = CrateType.byId(tag.getString("crate"));
            RewardHelper.give(player, tag, type == null ? CrateType.VOTE : type);
        }
    }
}
