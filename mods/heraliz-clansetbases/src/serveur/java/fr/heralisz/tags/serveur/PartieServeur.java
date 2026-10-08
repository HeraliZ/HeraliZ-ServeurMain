package fr.heralisz.tags.serveur;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.tags.item.TagItem;
import fr.heralisz.tags.network.Packets;
import fr.heralisz.tags.server.ServerHandlers;
import fr.heralisz.tags.server.TagService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Tags : cycle de vie (stockage, tags poses, commandes), deblocage par l'objet tag, paquets
 * recus des joueurs (pose, roue, collection).
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.register(new EvenementsServeur());
        TagItem.DEBLOQUER = TagService::debloquer;
        Packets.RECEPTION_SERVEUR.brancher(r -> {
            Object m = r.paquet();
            if (m instanceof Packets.TagC2S t) ServerHandlers.tag(r.joueur(), t);
            else if (m instanceof Packets.RoueC2S t) ServerHandlers.roue(r.joueur(), t);
            else if (m instanceof Packets.OuvrirIndexC2S) ServerHandlers.ouvrirIndex(r.joueur());
        });
    }
}
