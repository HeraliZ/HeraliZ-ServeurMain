package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Queue;

/**
 * Grands déchargements étalés (19/11, audit des lenteurs, point C5) : dans {@code ChunkMap.processUnloads}, quand plus de
 * 2 000 tronçons attendent leur déchargement, Minecraft décharge ET écrit tout l'excédent dans le même tick, sans regarder
 * le temps ({@code k = unloadQueue.size() - 2000} déchargements forcés) : 11 000 tronçons d'un coup après les {@code //replace}
 * du 19/11 (1,4 et 2 s de gel). Ici, la taille lue pour ce calcul est bornée : au plus
 * {@code [perf] dechargements_forces_par_tick} déchargements forcés par tick, en plus de ceux que le temps libre du tick
 * permet (règle de Minecraft, inchangée) ; le reste suit aux ticks suivants. Au-delà de {@value #PLAFOND} tronçons en
 * attente (mémoire), ou réglage à 0 : la règle de Minecraft. La sauvegarde complète ({@code saveAllChunks(true)}, arrêt)
 * décharge tout comme avant (son temps libre est toujours « oui »).
 */
@Mixin(ChunkMap.class)
public abstract class DechargementsMixin {
   /** Au-delà, tronçons en attente trop nombreux pour la mémoire : règle de Minecraft. */
   @Unique
   private static final int PLAFOND = 20_000;
   /** Seuil de Minecraft (processUnloads). */
   @Unique
   private static final int SEUIL = 2000;

   @Redirect(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;size()I", remap = false))
   private int heraliz$forcesBornes(Queue<?> file) {
      int n = file.size();
      int forces = NoyauConfig.dechargementsForces;
      if (forces <= 0 || n <= SEUIL + forces || n > PLAFOND) {
         return n;
      }
      Allege.dechargementsReportes += n - SEUIL - forces;
      Allege.dechargementsBornes++;
      return SEUIL + forces;
   }
}
