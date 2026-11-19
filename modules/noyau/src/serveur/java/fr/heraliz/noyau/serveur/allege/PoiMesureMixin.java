package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.Allege;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mesure seulement (19/11, audit des lenteurs, point C9) : à la lecture de chaque tronçon ({@code ChunkSerializer.read},
 * sur le fil du serveur en 1.20.1), Minecraft relit les « points d'intérêt » de chaque étage (lits, postes de villageois,
 * portails…) en attendant le disque ({@code PoiManager.checkConsistencyWithBlocks → SectionStorage.readColumn →
 * CompletableFuture.join} : 34 % des relevés du blocage de 658 ms du 18/11 à 23:14). Rien n'est sauté : chaque appel est
 * compté et chronométré, pour {@code /diag etat} ; la décision de le sauter sur la carte viendra des chiffres.
 */
@Mixin(ChunkSerializer.class)
public abstract class PoiMesureMixin {
   @Redirect(method = "read", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;checkConsistencyWithBlocks(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V"))
   private static void heraliz$mesurer(PoiManager poi, SectionPos section, LevelChunkSection contenu) {
      long debut = System.nanoTime();
      poi.checkConsistencyWithBlocks(section, contenu);
      long duree = System.nanoTime() - debut;
      Allege.poiSections++;
      Allege.poiNanos += duree;
      if (duree > Allege.poiPireNanos) Allege.poiPireNanos = duree;
   }
}
