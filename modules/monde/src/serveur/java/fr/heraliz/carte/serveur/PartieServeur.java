package fr.heraliz.carte.serveur;

import fr.heralisz.utils.pont.Partie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heraliz_carte : commandes du staff ({@code /carte}) et envoi des lieux nommés à chaque joueur qui se
 * connecte, repère de la base du clan ({@link BaseCarte}), repères des recycleurs de la carte ({@link RecycleursCarte},
 * 19/11). Les territoires, alliés, extractions et pings de la carte sont servis par leurs propres mods.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CarteCommandes.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) LieuxData.de(p.server).envoyer(p);
        });
        // 04/11 : repère de la base du clan, demandé à l'ouverture de la carte
        fr.heraliz.carte.CarteNet.DEMANDE_BASE.brancher(BaseCarte::demande);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BaseCarte.oublier(e.getEntity().getUUID()));
        // 19/11 : repères des recycleurs de la carte (posés tout seuls), relus au chargement de leur tronçon ; recherche
        // automatique une fois (le tick ne fait rien hors recherche)
        MinecraftForge.EVENT_BUS.addListener(RecycleursCarte::troncon);
        MinecraftForge.EVENT_BUS.addListener(RecycleursCarte::demarrage);
        MinecraftForge.EVENT_BUS.addListener(RecycleursCarte::arret);
        MinecraftForge.EVENT_BUS.addListener(RechercheRecycleurs::tick);
    }
}
