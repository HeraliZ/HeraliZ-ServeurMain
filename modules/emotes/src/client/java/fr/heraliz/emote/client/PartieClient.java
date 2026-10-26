package fr.heraliz.emote.client;

import fr.heraliz.emote.pont.Partie;
import fr.heraliz.emote.reseau.ReseauEmotes;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Emote (docs/METHODE_CLIENT_SERVEUR.md §4) : emotes, objets 3D, musiques, menu et roue,
 * copie de l'emote d'un joueur, vue à la 3e personne pendant une emote, emotes verrouillées grisées dans le menu.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        HeralizEmoteClient.init(busMod);
        ReseauEmotes.DEBLOCAGES_CLIENT.brancher(DeblocagesClient::recevoir);
        ReseauEmotes.SANS_ARME_CLIENT.brancher(SansArmeClient::recevoir);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            DeblocagesClient.oublier();
            SansArmeClient.oublier();
        });
        // pas de tir « fantôme » pendant le délai sans arme après une emote (25/10)
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) TirsClient.brancher();
    }
}
