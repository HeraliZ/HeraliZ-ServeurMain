package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.client.fx.FxQuality;
import fr.heraliz.airstrike.entity.EffectCloudEntity;
import fr.heraliz.airstrike.item.DesignatorItem;
import fr.heraliz.airstrike.network.ImpactPacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.network.StrikePacket;
import fr.heraliz.airstrike.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client d'Airstrike (docs/METHODE_CLIENT_SERVEUR.md §7) : branche les paquets reçus, le rendu des jumelles et la
 * qualité des particules des nuages. Visée, HUD, rendus, effets, sons et acouphène s'inscrivent seuls (ClientSetup,
 * ClientEvents : {@code value = Dist.CLIENT}).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      Network.RECEPTION_CLIENT.brancher(paquet -> {
         if (paquet instanceof StrikePacket p) {
            ClientStrikes.add(p.strike);
         } else if (paquet instanceof ImpactPacket p) {
            ImpactFx.play(p);
         }
      });
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
