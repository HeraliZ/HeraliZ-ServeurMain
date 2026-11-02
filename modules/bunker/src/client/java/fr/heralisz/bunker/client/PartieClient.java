package fr.heralisz.bunker.client;

import com.hbm_m.block.UniversalMachinePartBlock;
import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.hbm_m.block.entity.doors.variant.DoorModelSelection;
import com.hbm_m.client.ClientRenderHandler;
import com.hbm_m.client.model.variant.DoorModelRegistry;
import com.hbm_m.client.overlay.DoorAnimationDelayHelper;
import com.hbm_m.client.render.DoorChunkInvalidationHelper;
import fr.heralisz.bunker.keycard.KeycardClientHooks;
import fr.heralisz.bunker.keycard.KeycardNetwork;
import fr.heralisz.bunker.pont.Partie;
import fr.heralisz.bunker.salle.ContoursPacket;
import fr.heralisz.bunker.salle.client.ContoursSalle;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de HeraliZ Bunker (docs/METHODE_CLIENT_SERVEUR.md §4) : délai d'animation et rechargement des chunks des
 * portes, choix de modèle par défaut (configs client), blocs fantômes, passage de carte. Rendus, modèles DAE, shaders et
 * écrans s'inscrivent seuls (ClientSetup et @EventBusSubscriber).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      DoorBlockEntity.CLIENT = new DoorBlockEntity.ClientHooks() {
         @Override
         public boolean enDelai(DoorBlockEntity porte) {
            return DoorAnimationDelayHelper.isInDelayPeriod(porte);
         }

         @Override
         public void ajouterDelai(DoorBlockEntity porte, long ms) {
            DoorAnimationDelayHelper.addToQueue(porte, ms);
         }

         @Override
         public void invaliderChunk(BlockPos pos) {
            DoorChunkInvalidationHelper.scheduleChunkInvalidation(pos);
         }

         @Override
         public DoorModelSelection selectionParDefaut(String declId) {
            DoorModelRegistry registry = DoorModelRegistry.getInstance();
            return registry.isInitialized() ? registry.getDefaultSelection(declId) : null;
         }
      };
      UniversalMachinePartBlock.ORPHELIN_CLIENT = (pos, orphelin) -> {
         if (orphelin) {
            ClientRenderHandler.addOrphanedPhantomBlock(pos);
         } else {
            ClientRenderHandler.removeOrphanedPhantomBlock(pos);
         }
      };
      KeycardNetwork.RECEPTION_CLIENT.brancher(KeycardClientHooks::onSwipe);
      // salles à carte : contours envoyés par le serveur au staff (baguette, /salle voir)
      ContoursPacket.RECEPTION_CLIENT.brancher(ContoursSalle::recevoir);
      MinecraftForge.EVENT_BUS.addListener(ContoursSalle::rendu);
      // portes qui s'ouvrent à la main : ouvertes avec une arme en main (HeraliZ, 31/10, noyau : api/Portes)
      PortesBunker.declarer();
      // 02/11 (audit n° 17) : fin du délai d'animation des portes et rafraîchissement de leurs tronçons à chaque tick,
      // comme dans HBM (ces files n'étaient jamais vidées : chaque porte qui finissait de bouger restait en mémoire avec
      // tout son monde, à chaque passage carte <-> spawn) ; vidées aussi au changement de monde et à la déconnexion
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
         if (e.phase == TickEvent.Phase.END) {
            DoorAnimationDelayHelper.processQueue();
            DoorChunkInvalidationHelper.processPendingInvalidations();
         }
      });
      MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
         if (e.getLevel().isClientSide()) {
            viderPortes();
         }
      });
      MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> viderPortes());
   }

   private static void viderPortes() {
      DoorAnimationDelayHelper.vider();
      DoorChunkInvalidationHelper.vider();
   }
}
