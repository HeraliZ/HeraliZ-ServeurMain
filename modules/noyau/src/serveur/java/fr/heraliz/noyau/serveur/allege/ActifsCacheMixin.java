package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.TronconsActifs;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;

/**
 * Point 3 de l'étape 2 du chantier « Minecraft allégé » (01/11) : la boucle de {@code tickChunks} qui cherche les tronçons qui
 * tournent (sur {@code chunkMap.getChunks()}, le premier {@code Iterable.iterator()} de la méthode) parcourt la liste de la
 * carte ({@link TronconsActifs}) au lieu de tous ses tronçons chargés ; son test {@code getTickingChunk() != null} reste
 * celui de Minecraft. Liste pas prête ou mixin de la carte absent : les tronçons de Minecraft.
 */
@Mixin(ServerChunkCache.class)
public abstract class ActifsCacheMixin {
   @SuppressWarnings("unchecked")
   @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Ljava/lang/Iterable;iterator()Ljava/util/Iterator;", ordinal = 0,
         remap = false))
   private Iterator<?> heraliz$tronconsQuiTournent(Iterable<?> minecraft) {
      Object carte = ((ServerChunkCache) (Object) this).chunkMap;
      if (carte instanceof TronconsActifs.AvecActifs a) {
         Iterator<ChunkHolder> liste = a.heraliz$actifs().troncons();
         if (liste != null) {
            return liste;
         }
      }
      return minecraft.iterator();
   }
}
