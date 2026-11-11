package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import java.util.Set;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chargement d'un tronçon (11/11, rapports de blocage du 08 au 11/11 : 10 à 25 % du temps de lecture d'un tronçon dans
 * {@code Heightmap.primeHeightmaps}, appelé par {@code ChunkSerializer.read}). Minecraft y refait les relevés de hauteur
 * qui MANQUENT dans le tronçon lu ; quand il n'en manque aucun (le cas d'un tronçon complet enregistré normalement), la
 * liste à refaire est vide mais la fonction parcourt quand même les 256 colonnes du tronçon, de la plus haute section qui
 * a un bloc jusqu'au premier bloc qui n'est pas de l'air, pour ne rien noter. Sur une carte construite (bâtiments hauts,
 * blocs en hauteur), ce sont des dizaines de milliers de lectures de blocs par tronçon, sur le fil du serveur.
 * <p>Ici : liste vide = rien à faire, la fonction rend tout de suite (résultat identique : elle ne changeait rien). Liste
 * non vide (relevés vraiment absents, génération du monde) : Minecraft fait son travail comme avant. Réglage :
 * {@code [perf] hauteurs_sans_balayage}. Compteurs dans {@code /diag etat} et les rapports (sautés / refaits).
 */
@Mixin(Heightmap.class)
public abstract class RelevesHauteurMixin {
   @Inject(method = "primeHeightmaps", at = @At("HEAD"), cancellable = true)
   private static void heraliz$rienARelever(ChunkAccess troncon, Set<Heightmap.Types> types, CallbackInfo ci) {
      if (types.isEmpty()) {
         if (NoyauConfig.hauteursSansBalayage) {
            Allege.hauteursSautees++;
            ci.cancel();
         }
      } else {
         Allege.hauteursRefaites++;
      }
   }
}
