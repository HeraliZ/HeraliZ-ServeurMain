package fr.heralisz.bases.client;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.bases.blindage.client.BlindageClient;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.bases.pillage.PillageNet;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de HeraliZ Bases : reception des paquets du coeur (ClientHandlers) et du blindage (BlindageClient). */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        Packets.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof Packets.EtatBaseS2C p) ClientHandlers.etat(p);
            else if (m instanceof Packets.PlanS2C p) ClientHandlers.plan(p);
            else if (m instanceof Packets.PillablesS2C p) ClientHandlers.pillables(p);
            else if (m instanceof Packets.OuvrirS2C p) ClientHandlers.ouvrir(p);
            else if (m instanceof Packets.NoticeS2C p) ClientHandlers.notice(p);
            else if (m instanceof Packets.HudS2C p) ClientHandlers.hud(p);
            else if (m instanceof Packets.TeleportS2C p) ClientHandlers.teleport(p);
        });
        PillageNet.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof PillageNet.InfoBlindageS2C p) BlindageClient.info(p);
            else if (m instanceof PillageNet.ProtectionS2C p) BlindageClient.table(p);
        });
    }
}
