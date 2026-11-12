package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.SauvegardesEtalees;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sauvegarde au fil de l'eau des tronçons, étalée (11/11, {@link SauvegardesEtalees}) : dans {@code processUnloads}, la
 * boucle qui passe en revue TOUS les tronçons chargés à chaque tick ({@code visibleChunkMap.values().iterator()}) reçoit à la
 * place une tranche de la carte qui reprend où le tick d'avant s'est arrêté. Le test et l'écriture restent ceux de Minecraft
 * ({@code saveChunkIfNeeded}), comme ses limites (20 écritures par tick, rien sans temps libre). Seul ce parcours est
 * changé : la sauvegarde automatique ({@code saveAllChunks}), les déchargements et l'arrêt regardent toujours tout.
 * Autre collection que la carte visible (un autre mod aurait changé la méthode), réglage coupé ou cible absente
 * (require = 0) : le parcours de Minecraft. Réglage : {@code [perf] sauvegardes_etalees}.
 */
@Mixin(ChunkMap.class)
public abstract class SauvegardesEtaleesMixin {
   @Shadow
   private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

   @Unique
   private SauvegardesEtalees heraliz$tranche;

   @Redirect(method = "processUnloads", at = @At(value = "INVOKE",
         target = "Lit/unimi/dsi/fastutil/objects/ObjectCollection;iterator()Lit/unimi/dsi/fastutil/objects/ObjectIterator;", remap = false))
   private ObjectIterator<ChunkHolder> heraliz$uneTrancheParTick(ObjectCollection<ChunkHolder> tous) {
      Long2ObjectLinkedOpenHashMap<ChunkHolder> visibles = this.visibleChunkMap;
      if (!NoyauConfig.sauvegardesEtalees || tous != visibles.values()) {
         return tous.iterator();
      }
      SauvegardesEtalees t = heraliz$tranche;
      if (t == null) {
         t = heraliz$tranche = new SauvegardesEtalees();
      }
      return t.tranche(visibles);
   }
}
