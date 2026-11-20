package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mesure (19/11, audit des lenteurs, point C9) : à la lecture de chaque tronçon ({@code ChunkSerializer.read}, sur le fil du
 * serveur en 1.20.1), Minecraft relit les « points d'intérêt » de chaque étage (lits, postes de villageois, portails…) en
 * attendant le disque ({@code PoiManager.checkConsistencyWithBlocks → SectionStorage.readColumn → CompletableFuture.join} :
 * 34 % des relevés du blocage de 658 ms du 18/11 à 23:14). Chaque appel est compté et chronométré, pour {@code /diag etat}.
 * <p>20/11 (audit du tick, chiffres du Ptero : jusqu'à 26 ms pour un seul tronçon ; banc : 84 ms) : dans les mondes de
 * {@code [perf] poi_sans_relecture} (la carte par défaut : ni villageois, ni abeilles, ni portails, aucun de nos mods ne lit
 * les points d'intérêt), la relecture est sautée ; les points d'intérêt gardés ne changent pas. Compteur à part.
 */
@Mixin(ChunkSerializer.class)
public abstract class PoiMesureMixin {
   /** Monde du tronçon en cours de lecture, par fil (null : inconnu, la relecture se fait). */
   @Unique
   private static final ThreadLocal<String> heraliz$monde = new ThreadLocal<>();

   @Inject(method = "read", at = @At("HEAD"))
   private static void heraliz$mondeLu(ServerLevel monde, PoiManager poi, ChunkPos pos, CompoundTag tag,
                                       CallbackInfoReturnable<ProtoChunk> cir) {
      heraliz$monde.set(monde.dimension().location().toString());
   }

   @Redirect(method = "read", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;checkConsistencyWithBlocks(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V"))
   private static void heraliz$mesurer(PoiManager poi, SectionPos section, LevelChunkSection contenu) {
      String m = heraliz$monde.get();
      if (m != null && NoyauConfig.poiSansRelecture.contains(m)) {
         Allege.poiSautes++;
         return;
      }
      long debut = System.nanoTime();
      poi.checkConsistencyWithBlocks(section, contenu);
      long duree = System.nanoTime() - debut;
      Allege.poiSections++;
      Allege.poiNanos += duree;
      if (duree > Allege.poiPireNanos) Allege.poiPireNanos = duree;
   }
}
