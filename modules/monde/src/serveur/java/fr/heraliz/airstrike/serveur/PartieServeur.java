package fr.heraliz.airstrike.serveur;

import fr.heraliz.airstrike.block.SupplyCrateBlockEntityServeur;
import fr.heraliz.airstrike.entity.EffectCloudEntityServeur;
import fr.heraliz.airstrike.entity.HelicopterServeur;
import fr.heraliz.airstrike.entity.HelicopterWreckServeur;
import fr.heraliz.airstrike.helico.Veille;
import fr.heraliz.airstrike.entity.SupplyCrateEntityServeur;
import fr.heraliz.airstrike.network.DesignatePacket;
import fr.heraliz.airstrike.network.LoadCartridgePacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.pont.Partie;
import fr.heraliz.airstrike.registry.ModBlockEntities;
import fr.heraliz.airstrike.registry.ModEntities;
import fr.heraliz.airstrike.strike.DesignatorLogic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur d'Airstrike (docs/METHODE_CLIENT_SERVEUR.md §4) : avions et frappes (planification, impacts, bombes,
 * explosions, destruction des blocs, largages, nuages de fumée / gaz / napalm, attraction des zombies), validation des
 * jumelles, commandes ; hélicoptère de l'événement (pilote, chute, épave, {@code api.HelicoApi}).
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      ModEntities.EFFECT_CLOUD_USINE.serveur(EffectCloudEntityServeur::new);
      ModEntities.SUPPLY_CRATE_USINE.serveur(SupplyCrateEntityServeur::new);
      // hélicoptère de l'événement (25/10) : pilote, chute, épave ; lancé par HeraliZ Événements (HelicoApi)
      ModEntities.HELICOPTER_USINE.serveur(HelicopterServeur::new);
      ModEntities.HELICOPTER_WRECK_USINE.serveur(HelicopterWreckServeur::new);
      ModBlockEntities.SUPPLY_CRATE_USINE.serveur(SupplyCrateBlockEntityServeur::new);
      Network.RECEPTION_SERVEUR.brancher(PartieServeur::recevoir);
      MinecraftForge.EVENT_BUS.register(ServerEvents.class);
      // hélicoptère : départs en attente de leur tronçon, marques de la carte M, fin des épaves (rien sans hélicoptère)
      MinecraftForge.EVENT_BUS.register(Veille.class);
   }

   /** Original bodies of DesignatePacket.handle and LoadCartridgePacket.handle (main thread). */
   private static void recevoir(Network.Recu recu) {
      ServerPlayer player = recu.joueur();
      if (recu.paquet() instanceof DesignatePacket p) {
         if (player != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)) {
            DesignatorLogic.designate(player, new Vec3(p.x, p.y, p.z));
         }
      } else if (recu.paquet() instanceof LoadCartridgePacket p) {
         if (player != null) DesignatorLogic.loadCartridge(player, p.type);
      }
   }
}
