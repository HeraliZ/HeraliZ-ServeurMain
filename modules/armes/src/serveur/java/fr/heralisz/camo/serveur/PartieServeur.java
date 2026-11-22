package fr.heralisz.camo.serveur;

import fr.heralisz.camo.item.CamoItem;
import fr.heralisz.camo.network.ApplyCamoPacket;
import fr.heralisz.camo.network.CollectionCamoPacket;
import fr.heralisz.camo.network.DecorPacket;
import fr.heralisz.camo.network.DessinsBilanPacket;
import fr.heralisz.camo.network.GivrePacket;
import fr.heraliz.tactique.pont.Partie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heralisz_camo (docs/METHODE_CLIENT_SERVEUR.md §4, §6) : application des camos et des décorations reçues
 * des joueurs (ReceptionServeur), collection de camos à vie (CamosAVie, CollectionCamos, /camos), copie des dessins des
 * skins envoyée aux joueurs (DessinsServeur, docs/SKINS_PROTEGES.md), givre du camo Givre Vivant (GivreServeur). Le
 * compteur de kills des armes skinnées (KillCounter) et la reprise des objets retirés (CamoMigration) s'inscrivent seuls
 * par leur @Mod.EventBusSubscriber(modid = "heralisz_camo").
 */
public final class PartieServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      ApplyCamoPacket.RECEPTION_SERVEUR.brancher(ReceptionServeur::appliquerCamo);
      DecorPacket.RECEPTION_SERVEUR.brancher(ReceptionServeur::decor);
      CollectionCamoPacket.RECEPTION_SERVEUR.brancher(CamosAVie::recevoir);
      DessinsBilanPacket.RECEPTION_SERVEUR.brancher(DessinsServeur::bilan);
      GivrePacket.RECEPTION_SERVEUR.brancher(GivreServeur::basculer);
      CamoItem.AJOUTER_COLLECTION = CamosAVie::ajouter;

      MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> CollectionCamos.charger(e.getServer()));
      // copie des dessins des skins (21/11, docs/SKINS_PROTEGES.md, étape 1) : lue et compressée une fois, au démarrage
      MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> DessinsServeur.charger());
      MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
         CollectionCamos.arreter();
         Cadence.vider();
         DessinsServeur.vider();
         GivreServeur.vider();
      });
      // cadence des camos et des stickers, demande /camos dessins en attente (21/11) : rien ne reste d'un joueur parti
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
         Cadence.oublier(e.getEntity().getUUID());
         DessinsServeur.oublier(e.getEntity().getUUID());
         GivreServeur.oublier(e.getEntity().getUUID());
      });
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
         if (e.phase == TickEvent.Phase.END) {
            CollectionCamos.tick();
            CamosAVie.tick(e.getServer());
         }
      });
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
         if (e.getEntity() instanceof ServerPlayer p) {
            CamosAVie.verifier(p);
            CamosAVie.envoyer(p);
            CollectionCamos.prevenirStaff(p);
            DessinsServeur.envoyer(p);
         }
      });
      // après Curios (HIGHEST, ajoute ses objets), avant les corps (LOWEST) et les sacs (NORMAL, peut annuler la chute)
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CamosAVie::chute);
      MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CommandeCamos.enregistrer(e.getDispatcher()));
   }
}
