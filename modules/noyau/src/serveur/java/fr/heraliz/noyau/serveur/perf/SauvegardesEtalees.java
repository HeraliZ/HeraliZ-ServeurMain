package fr.heraliz.noyau.serveur.perf;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ChunkHolder;

import java.util.NoSuchElementException;

/**
 * Sauvegarde au fil de l'eau des tronçons (11/11, profil du banc à 100 joueurs : {@code ChunkMap.processUnloads} 8,1 ms par
 * tick sur 37,8, soit 21 % ; déjà 5,8 ms le 02/11). À CHAQUE tick, Minecraft repart du début de la carte des tronçons chargés
 * (18 500 au banc) et les passe tous en revue ({@code saveChunkIfNeeded}) jusqu'à en avoir écrit 20 ou manqué de temps : quand
 * peu de tronçons ont changé (le cas normal), il les regarde donc tous, 20 fois par seconde, et pour chacun cherche 24 fois
 * si un de ses étages de points d'intérêt est à écrire ({@code PoiManager.flush}).
 * <p>Ici, le tour est étalé : chaque tick regarde une tranche de la carte (un vingtième, 64 tronçons au moins), en reprenant
 * là où le tick d'avant s'est arrêté ; chaque tronçon est donc regardé une fois par seconde au lieu de vingt. Le reste est
 * celui de Minecraft : mêmes tronçons écrits ({@code saveChunkIfNeeded} inchangé), 20 écritures par tick au plus, rien quand
 * le serveur n'a plus de temps, délai de 10 s entre deux écritures d'un même tronçon. Un tronçon modifié est écrit une
 * seconde plus tard au pire. La sauvegarde automatique (toutes les 5 min), celle des tronçons déchargés et celle de l'arrêt
 * ne passent pas par ici : elles regardent toujours tout.
 * <p>Un objet par carte de tronçons (par monde), réemployé à chaque tick : aucune allocation. Fil serveur seulement.
 */
public final class SauvegardesEtalees implements ObjectIterator<ChunkHolder> {
   /** Part de la carte regardée par tick : un vingtième (un tour complet par seconde). */
   private static final int PART = 20;
   /** Au moins autant de tronçons par tick (petites cartes : tour complet plus vite). */
   private static final int MINIMUM = 64;

   private Long2ObjectLinkedOpenHashMap<ChunkHolder> carte;
   private LongIterator cles;
   private int reste;
   /** Déjà reparti du début de la carte pendant ce tick. */
   private boolean reparti;
   /** Dernier tronçon regardé (clé de la carte) : le tick suivant reprend juste après. */
   private long reprise;
   private boolean aReprise;

   /** Prépare la tranche de ce tick sur la carte visible du moment, et se rend lui-même. */
   public SauvegardesEtalees tranche(Long2ObjectLinkedOpenHashMap<ChunkHolder> visibles) {
      this.carte = visibles;
      int n = visibles.size();
      this.reste = Math.min(n, Math.max(MINIMUM, (n + PART - 1) / PART));
      this.reparti = false;
      LongIterator depart = null;
      if (aReprise) {
         try {
            depart = visibles.keySet().iterator(reprise);   // juste après le dernier regardé
         } catch (NoSuchElementException plusCharge) {
            depart = null;                                  // ce tronçon n'est plus chargé : on repart du début
         }
      }
      if (depart == null) {
         depart = visibles.keySet().iterator();
         this.reparti = true;
      }
      this.cles = depart;
      Allege.sauvegardesTranches++;
      return this;
   }

   @Override
   public boolean hasNext() {
      if (reste <= 0) {
         return false;
      }
      if (cles.hasNext()) {
         return true;
      }
      if (reparti) {
         return false;
      }
      reparti = true;
      cles = carte.keySet().iterator();
      return cles.hasNext();
   }

   @Override
   public ChunkHolder next() {
      if (!hasNext()) {
         throw new NoSuchElementException();
      }
      long cle = cles.nextLong();
      reste--;
      reprise = cle;
      aReprise = true;
      Allege.sauvegardesRegardes++;
      return carte.get(cle);
   }
}
