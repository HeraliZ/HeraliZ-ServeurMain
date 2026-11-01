package fr.heraliz.noyau.serveur.perf;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.function.Function;

/**
 * Point 2 de l'étape 2 du chantier « Minecraft allégé » (01/11) : {@code ServerChunkCache.getChunk(x, z, FULL, true)} sur le
 * fil serveur (chaque {@code Level.getBlockState}, {@code getChunk}… qui rate le petit cache de 4 tronçons). Pour un tronçon
 * déjà complet et accessible, Minecraft pose quand même un ticket {@code UNKNOWN} (niveau 33, durée un tick) avant de rendre
 * le tronçon : banc du 01/11, 513 de ces tickets dans la carte et 160 dans le tutoriel à un instant donné, chacun ajouté puis
 * retiré par le gestionnaire des distances (recalcul des niveaux autour, {@code purgeStaleTickets}, {@code runAllUpdates}).
 * Paper rend directement un tronçon déjà complet, sans ticket.
 * <p>Ici (mixin {@code TronconSansTicketMixin}) : seulement pour le statut FULL avec chargement demandé, seulement si
 * Minecraft lui-même trouverait le tronçon présent (même test {@code chunkAbsent}, celui de Mohist : ancien niveau du ticket)
 * et que son avenir FULL est déjà terminé avec le tronçon : c'est exactement l'objet que Minecraft aurait rendu (même
 * tronçon, même petit cache). Le reste (tronçon absent, en cours, en échec, autre statut, autre fil) : Minecraft.
 * <p><b>Ce qui change</b> : seulement la durée de vie d'un tronçon que plus RIEN d'autre ne tient (aucun joueur, aucun autre
 * ticket) : sans ce ticket d'un tick, il redescend 1 à 2 ticks (au plus 0,1 s) plus tôt. Un tronçon près d'un joueur est
 * toujours tenu par le ticket du joueur : rien de visible. Le tronçon rendu reste en mémoire tant que le code qui l'a demandé
 * s'en sert (Minecraft ne décharge un tronçon qu'à la fin de son tick des tronçons). Réglage :
 * {@code [perf] troncons_sans_ticket}.
 */
public final class TronconsSansTicket {
   private TronconsSansTicket() {
   }

   /** Le tronçon complet que l'avenir FULL du support a déjà rendu, ou null (Minecraft fait le reste). */
   public static ChunkAccess complet(ChunkHolder h) {
      Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> e = h.getFutureIfPresentUnchecked(ChunkStatus.FULL).getNow(null);
      if (e == null) {
         return null;
      }
      return e.map(Function.identity(), echec -> null);
   }
}
