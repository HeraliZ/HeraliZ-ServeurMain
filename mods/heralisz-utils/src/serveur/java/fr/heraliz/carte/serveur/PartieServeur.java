package fr.heraliz.carte.serveur;

import fr.heralisz.utils.pont.Partie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heraliz_carte : commandes du staff ({@code /carte}) et envoi des lieux nommés à chaque joueur qui se
 * connecte. Les territoires, alliés, extractions et pings de la carte sont servis par leurs propres mods.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CarteCommandes.enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) LieuxData.de(p.server).envoyer(p);
        });
    }
}
