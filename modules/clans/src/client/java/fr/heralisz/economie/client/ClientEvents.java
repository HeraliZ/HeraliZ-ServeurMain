package fr.heralisz.economie.client;

import fr.heralisz.economie.HeraliszEconomie;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Evenements client (bus Forge) : l'etat est vide a la deconnexion (le HUD ne montre rien tant que le serveur n'a rien envoye). */
@Mod.EventBusSubscriber(modid = HeraliszEconomie.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientSolde.vider();
    }
}
