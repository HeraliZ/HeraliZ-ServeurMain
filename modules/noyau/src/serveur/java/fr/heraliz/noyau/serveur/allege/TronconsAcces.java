package fr.heraliz.noyau.serveur.allege;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Point 3 de l'étape 2 du chantier « Minecraft allégé » (01/11, {@link fr.heraliz.noyau.serveur.perf.TronconsActifs}) : accès
 * aux tronçons d'une carte (un tronçon visible, un tronçon de la carte en cours, tous les tronçons visibles) et à son monde.
 * À part des autres accès : un accès introuvable ici ne touche que ce point.
 */
@Mixin(ChunkMap.class)
public interface TronconsAcces {
   @Invoker("getVisibleChunkIfPresent")
   ChunkHolder heraliz$visible(long troncon);

   @Invoker("getUpdatingChunkIfPresent")
   ChunkHolder heraliz$enCours(long troncon);

   @Invoker("getChunks")
   Iterable<ChunkHolder> heraliz$tronconsMinecraft();

   @Accessor("level")
   ServerLevel heraliz$monde();
}
