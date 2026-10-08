package fr.heraliz.crac.event;

import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import fr.heraliz.crac.placable.PlacableSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class PlayerEventHandler {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            C2SRequestNearbyClaimsTraitement.sendToPlayer((ServerLevel)sp.level(), sp);
            PlacableSet.syncTo(sp);
            if (sp.getServer() != null) {
                GroupData gd = GroupData.get(sp.getServer());
                Group g = gd.getByMember(sp.getUUID());
                if (g != null) {
                    g.touchActive(sp.getServer().overworld().getGameTime());
                    gd.markDirty();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MinecraftServer server = sp.getServer();
            if (server != null) {
                // HeraliZ: CRac no longer has sleeping bags and never touches where a player respawns
                // a respawn may land in another dimension, and Forge fires no dimension change event for it
                C2SRequestNearbyClaimsTraitement.sendToPlayer(sp.serverLevel(), sp);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            C2SRequestNearbyClaimsTraitement.sendToPlayer((ServerLevel)sp.level(), sp);
        }
    }
}
