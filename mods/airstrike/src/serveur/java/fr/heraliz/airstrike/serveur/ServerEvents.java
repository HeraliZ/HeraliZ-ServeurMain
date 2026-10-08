package fr.heraliz.airstrike.serveur;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.command.AirstrikeCommand;
import fr.heraliz.airstrike.strike.StrikeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

        @SubscribeEvent
        public static void onCommands(RegisterCommandsEvent event) {
            AirstrikeCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer p) StrikeManager.syncTo(p);
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
