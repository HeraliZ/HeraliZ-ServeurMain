package fr.heraliz.emote.client;

import fr.heraliz.emote.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Emote (docs/METHODE_CLIENT_SERVEUR.md §4) : emotes, objets 3D, musiques, menu et roue,
 * copie de l'emote d'un joueur. Le serveur n'a que la règle des emotes (mixin commun) et Emotecraft embarqué.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        HeralizEmoteClient.init(busMod);
    }
}
