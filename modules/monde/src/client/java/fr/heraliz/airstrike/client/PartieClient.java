package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.client.fx.FxQuality;
import fr.heraliz.airstrike.entity.EffectCloudEntity;
import fr.heraliz.airstrike.entity.Helicopter;
import fr.heraliz.airstrike.entity.HelicopterWreck;
import fr.heraliz.airstrike.item.DesignatorItem;
import fr.heraliz.airstrike.network.HelicoCartePacket;
import fr.heraliz.airstrike.network.HelicoPositionPacket;
import fr.heraliz.airstrike.network.HelicoTirPacket;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.network.StrikePacket;
import fr.heraliz.airstrike.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client d'Airstrike (docs/METHODE_CLIENT_SERVEUR.md §7) : branche les paquets reçus, le rendu des jumelles, la
 * qualité des particules des nuages et l'hélicoptère de l'événement (HelicoClient). Visée, HUD, rendus, effets, sons et
 * acouphène s'inscrivent seuls (ClientSetup, ClientEvents : {@code value = Dist.CLIENT}).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      Network.RECEPTION_CLIENT.brancher(paquet -> {
         if (paquet instanceof StrikePacket p) {
            ClientStrikes.add(p.strike);
         } else if (paquet instanceof ImpactPacket p) {
            ImpactFx.play(p);
         } else if (paquet instanceof HelicoTirPacket p) {
            HelicoClient.tir(p);
         } else if (paquet instanceof HelicoPositionPacket p) {
            HelicoClient.position(p);
         } else if (paquet instanceof HelicoCartePacket p) {
            HelicoCarte.recevoir(p);
         }
      });
      // hélicoptère de l'événement (25/10) : sons, fumée, feu, traînée ; flammes de l'épave
      Helicopter.TICK_CLIENT.brancher(HelicoClient::entite);
      HelicopterWreck.TICK_CLIENT.brancher(HelicoClient::epave);
      DesignatorItem.RENDU_CLIENT.brancher(c -> c.accept(DesignatorItemExtensions.INSTANCE));
      EffectCloudEntity.QUALITE_FX = new EffectCloudEntity.QualiteFx() {
         @Override
         public float gameplay(double x, double y, double z) {
            return FxQuality.gameplay(x, y, z);
         }

         @Override
         public float at(double x, double y, double z) {
            return FxQuality.at(x, y, z);
         }

         @Override
         public float grow(float q) {
            return FxQuality.grow(q);
         }
      };
   }
}
