package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import fr.heraliz.noyau.serveur.perf.TronconsSansTicket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Point 2 de l'étape 2 du chantier « Minecraft allégé » (01/11) : voir {@link TronconsSansTicket}. Dans
 * {@code ServerChunkCache.getChunk} (fil serveur, après le petit cache de 4 tronçons et le court-circuit « en cours de
 * chargement » de Forge), un tronçon COMPLET déjà accessible est rendu tel que Minecraft l'aurait rendu (le même objet,
 * rangé dans le même petit cache), sans poser le ticket {@code UNKNOWN} d'un tick. Tout autre cas : Minecraft.
 */
@Mixin(ServerChunkCache.class)
public abstract class TronconSansTicketMixin {
   @Shadow
   protected abstract ChunkHolder getVisibleChunkIfPresent(long troncon);

   @Shadow
   protected abstract boolean chunkAbsent(ChunkHolder holder, int niveau);

   @Shadow
   protected abstract void storeInCache(long troncon, ChunkAccess acces, ChunkStatus statut);

   @Inject(method = "getChunk", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/server/level/ServerChunkCache;getChunkFutureMainThread(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"),
         cancellable = true)
   private void heraliz$dejaComplet(int x, int z, ChunkStatus statut, boolean charger, CallbackInfoReturnable<ChunkAccess> cir) {
      if (statut != ChunkStatus.FULL || !charger || !NoyauConfig.tronconsSansTicket) {
         return;
      }
      long cle = ChunkPos.asLong(x, z);
      ChunkHolder h = getVisibleChunkIfPresent(cle);
      if (h == null || chunkAbsent(h, ChunkLevel.byStatus(ChunkStatus.FULL))) {
         return;
      }
      ChunkAccess c = TronconsSansTicket.complet(h);
      if (c == null) {
         return;
      }
      storeInCache(cle, c, statut);
      Allege.tronconsSansTicket++;
      cir.setReturnValue(c);
   }
}
