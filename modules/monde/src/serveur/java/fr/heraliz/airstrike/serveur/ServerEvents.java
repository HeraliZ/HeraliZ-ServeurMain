package fr.heraliz.airstrike.serveur;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.command.AirstrikeCommand;
import fr.heraliz.airstrike.item.PayloadItem;
import fr.heraliz.airstrike.registry.ModItems;
import fr.heraliz.airstrike.strike.StrikeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server listeners of Airstrike (formerly AirstrikeMod.ServerEvents, bodies unchanged). Registered on the Forge bus by
 * PartieServeur, during the @Mod constructor as before.
 */
public final class ServerEvents {

        @SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
                StrikeManager.tick(level);
            }
        }

        /** The old single strike payloads a player carries become the cartridge of their level (every 2 s). */
        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.getItem() instanceof PayloadItem old) {
                    // une ancienne recharge ne valait qu'une frappe : cartouche entamée (26/10, cartouches à 2 frappes)
                    inv.setItem(i, fr.heraliz.airstrike.item.CartridgeItem.avecReste(new ItemStack(ModItems.cartridge(old.tier()), s.getCount()), 1));
                }
            }
        }

        @SubscribeEvent
        public static void onCommands(RegisterCommandsEvent event) {
            AirstrikeCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer p) {
                StrikeManager.syncTo(p);
                fr.heraliz.airstrike.strike.DesignatorLogic.connexion(p);
            }
        }

        /** 26/10 : une explosion de frappe ne touche aucun bloc ni un joueur à l'abri dans une base (AbriFrappes). */
        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
            fr.heraliz.airstrike.strike.AbriFrappes.explosion(event);
        }

        @SubscribeEvent
        public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer p) StrikeManager.syncTo(p);
        }

        @SubscribeEvent
        public static void onStarting(ServerStartingEvent event) {
            AirstrikeConfig.migrate();
        }

        @SubscribeEvent
        public static void onStopped(ServerStoppedEvent event) {
            StrikeManager.clear();
        }
    }
