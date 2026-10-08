package fr.heralisz.clans.client;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.territory.client.AvionSon;
import fr.heralisz.clans.territory.supply.AvionEntity;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de HeraliZ Clans : reception des paquets du serveur (aiguillage vers ClientHandlers), son de l'avion. */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        AvionEntity.SON_CLIENT.brancher(AvionSon::jouer);
        Packets.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof Packets.ClanSnapshotS2C p) ClientHandlers.snapshot(p);
            else if (m instanceof Packets.InvitesS2C p) ClientHandlers.invitations(p);
            else if (m instanceof Packets.TagsS2C p) ClientHandlers.tags(p);
            else if (m instanceof Packets.ClassementS2C p) ClientHandlers.classement(p);
            else if (m instanceof Packets.NoticeS2C p) ClientHandlers.notice(p);
            else if (m instanceof Packets.OuvrirS2C p) ClientHandlers.ouvrir(p);
            else if (m instanceof Packets.TerritoireHudS2C p) ClientHandlers.hudZone(p);
            else if (m instanceof Packets.PanneauDrapeauS2C p) ClientHandlers.panneauDrapeau(p);
        });
    }
}
