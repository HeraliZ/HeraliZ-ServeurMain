package fr.heralisz.clans.client;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.clans.api.CarteApi;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import fr.heralisz.clans.territory.client.AvionSon;
import fr.heralisz.clans.territory.supply.AvionEntity;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Clans : reception des paquets du serveur (aiguillage vers ClientHandlers), son de l'avion,
 * branchements de la carte du monde ({@link CarteApi} : demande, ravitaillement, dessin des bannieres).
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        AvionEntity.SON_CLIENT.brancher(AvionSon::jouer);
        CarteApi.demande = () -> {
            if (Minecraft.getInstance().getConnection() != null) ClansNet.toServer(new Packets.CarteDemandeC2S());
        };
        CarteApi.ravitaillement = id -> {
            if (Minecraft.getInstance().getConnection() != null && id != null) ClansNet.toServer(new Packets.RavitaillementC2S(id));
        };
        CarteApi.dessin = (g, b, x, y, w, h) -> {
            if (g instanceof GuiGraphics gg) BannerArt.draw(gg, b, x, y, w, h);
        };
        Packets.RECEPTION_CLIENT.brancher(m -> {
            if (m instanceof Packets.ClanSnapshotS2C p) ClientHandlers.snapshot(p);
            else if (m instanceof Packets.InvitesS2C p) ClientHandlers.invitations(p);
            else if (m instanceof Packets.TagsS2C p) ClientHandlers.tags(p);
            else if (m instanceof Packets.SaisonS2C p) ClientHandlers.saison(p);
            else if (m instanceof Packets.NoticeS2C p) ClientHandlers.notice(p);
            else if (m instanceof Packets.OuvrirS2C p) ClientHandlers.ouvrir(p);
            else if (m instanceof Packets.TerritoireHudS2C p) ClientHandlers.hudZone(p);
            else if (m instanceof Packets.PanneauDrapeauS2C p) ClientHandlers.panneauDrapeau(p);
            else if (m instanceof Packets.CarteS2C p) CarteApi.recevoir(p.zones(), p.allies(), p.monClan());
            else if (m instanceof Packets.ArbreSaisonS2C p) ClientClans.setArbre(p);
        });
    }
}
