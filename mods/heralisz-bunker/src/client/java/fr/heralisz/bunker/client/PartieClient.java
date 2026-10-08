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
import net.minecraft.core.BlockPos;
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
   }
}
