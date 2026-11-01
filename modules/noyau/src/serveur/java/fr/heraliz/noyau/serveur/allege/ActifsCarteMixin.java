package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.TronconsActifs;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Point 3 de l'étape 2 du chantier « Minecraft allégé » (01/11) : la carte des tronçons d'un monde porte la liste de ses
 * supports qui peuvent tourner ({@link TronconsActifs}).
 */
@Mixin(ChunkMap.class)
public abstract class ActifsCarteMixin implements TronconsActifs.AvecActifs {
   @Unique
   private TronconsActifs heraliz$tronconsActifs;

   @Override
   public TronconsActifs heraliz$actifs() {
      TronconsActifs t = heraliz$tronconsActifs;
      if (t == null) {
         t = heraliz$tronconsActifs = new TronconsActifs((ChunkMap) (Object) this);
      }
      return t;
   }
}
