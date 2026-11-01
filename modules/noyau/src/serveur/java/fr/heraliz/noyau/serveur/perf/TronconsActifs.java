package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.allege.TronconsAcces;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Iterator;
import java.util.List;

/**
 * Point 3 de l'étape 2 du chantier « Minecraft allégé » (01/11) : {@code ServerChunkCache.tickChunks} parcourt à chaque tick
 * TOUS les tronçons chargés du monde ({@code ChunkMap.getChunks()}) pour garder ceux qui tournent
 * ({@code getTickingChunk() != null}) : ~23 000 tronçons au banc à 100 joueurs, alors que seuls ceux près des joueurs tournent
 * ({@code Iterators$1.hasNext} 1,9 ms par tick à 100 joueurs, 4,3 ms à 150).
 * <p>Ici, chaque carte des tronçons tient la liste de ses supports ({@code ChunkHolder}) dont le niveau de ticket permet de
 * tourner (BLOCK_TICKING ou mieux), mise à jour à la fin de {@code ChunkHolder.updateFutures} : c'est là, et seulement là, que
 * Minecraft crée ou retire leur avenir « qui tourne », avec ce même test ; un support qui tourne est donc toujours dans la
 * liste. {@code tickChunks} parcourt cette liste au lieu de tous les tronçons (seulement ses supports qui sont aussi ceux des
 * tronçons visibles de Minecraft, une recherche chacun), et garde le même test {@code getTickingChunk() != null} : mêmes
 * tronçons (un support de la liste dont l'avenir n'est pas encore prêt est écarté par ce test, comme chez Minecraft). L'ordre
 * de départ change, mais Minecraft mélange aussitôt cette liste au hasard ({@code Collections.shuffle}, même nombre de
 * tirages) : apparitions et tics dans un ordre aussi quelconque qu'avant. Essai hors du jeu : 20 000 ticks de supports qui
 * changent de niveau, avenirs prêts en retard, supports qui partent de la carte avec retard, 0 différence.
 * <p>Sécurité : jamais utilisée tant que le crochet n'a pas été vu en marche ; toutes les 60 s, comparée en entier aux
 * tronçons de Minecraft (refaite si écart, coupée après 5 écarts) ; une mise à jour venue d'un autre fil la fait refaire.
 * Réglage : {@code [perf] troncons_actifs_liste}. Fil serveur seulement.
 */
public final class TronconsActifs {
   private static final Logger LOG = LogUtils.getLogger();
   private static boolean crochet;
   private static boolean coupe;
   private static int ecarts;
   private static volatile Thread fil;
   private static final List<TronconsActifs> TOUS = new ArrayList<>();

   final ChunkMap carte;
   private final ReferenceLinkedOpenHashSet<ChunkHolder> actifs = new ReferenceLinkedOpenHashSet<>();
   private volatile boolean aRefaire;
   private boolean utilise;

   public TronconsActifs(ChunkMap carte) {
      this.carte = carte;
      TOUS.add(this);
   }

   /** La carte des tronçons d'un monde ({@code ChunkMap}, par mixin) et sa liste. */
   public interface AvecActifs {
      TronconsActifs heraliz$actifs();
   }

   public static void fil(Thread f) {
      fil = f;
   }

   private static boolean tourne(ChunkHolder h) {
      return ChunkLevel.fullStatus(h.getTicketLevel()).isOrAfter(FullChunkStatus.BLOCK_TICKING);
   }

   /** Fin de {@code ChunkHolder.updateFutures} : le support peut-il tourner ? */
   public void maj(ChunkHolder h) {
      Thread f = fil;
      if (f != null && Thread.currentThread() != f) {
         aRefaire = true;
         return;
      }
      if (tourne(h)) {
         crochet = true;
         actifs.add(h);
      } else {
         actifs.remove(h);
      }
   }

   /**
    * Ce que la boucle de {@code tickChunks} parcourt : les supports de la liste qui sont aussi ceux des tronçons visibles de
    * Minecraft (même objet à la même place : ce qu'il aurait parcouru, moins ceux qui ne tournent pas ; une recherche par
    * support de la liste, ~1/8 des tronçons), ou null : tous les tronçons, comme Minecraft.
    */
   public Iterator<ChunkHolder> troncons() {
      if (!NoyauConfig.tronconsActifsListe || coupe || !crochet || aRefaire || !((Object) carte instanceof TronconsAcces a)) {
         return null;
      }
      utilise = true;
      Allege.tronconsActifsTicks++;
      Allege.tronconsActifsParcourus += actifs.size();
      return new Visibles(a, actifs.iterator());
   }

   private static final class Visibles implements Iterator<ChunkHolder> {
      private final TronconsAcces carte;
      private final Iterator<ChunkHolder> it;
      private ChunkHolder suivant;

      Visibles(TronconsAcces carte, Iterator<ChunkHolder> it) {
         this.carte = carte;
         this.it = it;
         this.suivant = avancer();
      }

      private ChunkHolder avancer() {
         while (it.hasNext()) {
            ChunkHolder h = it.next();
            if (carte.heraliz$visible(h.getPos().toLong()) == h) {
               return h;
            }
         }
         return null;
      }

      @Override
      public boolean hasNext() {
         return suivant != null;
      }

      @Override
      public ChunkHolder next() {
         ChunkHolder h = suivant;
         if (h == null) {
            throw new NoSuchElementException();
         }
         suivant = avancer();
         return h;
      }
   }

   /** Fin de chaque tick : liste refaite si besoin ; toutes les 60 s, comparaison complète avec Minecraft. */
   public static void finDuTick(int tick) {
      for (int i = 0; i < TOUS.size(); i++) {
         TronconsActifs t = TOUS.get(i);
         if (t.aRefaire) {
            t.refaire();
         } else if (tick % 1200 == 600 && t.utilise && !coupe) {
            t.verifier();
         }
      }
   }

   public static void oublierCarte(ChunkMap carte) {
      TOUS.removeIf(t -> t.carte == carte);
   }

   public static void vider() {
      TOUS.clear();
   }

   private void refaire() {
      aRefaire = false;
      if (!((Object) carte instanceof TronconsAcces a)) {
         coupe = true;
         return;
      }
      actifs.clear();
      for (ChunkHolder h : a.heraliz$tronconsMinecraft()) {
         if (tourne(h) || h.getTickingChunk() != null) {
            actifs.add(h);
         }
      }
      Allege.tronconsActifsRefaits++;
   }

   private void verifier() {
      if (!((Object) carte instanceof TronconsAcces a)) {
         return;
      }
      int manquants = 0;
      for (ChunkHolder h : a.heraliz$tronconsMinecraft()) {
         if (h.getTickingChunk() != null && !actifs.contains(h)) {
            manquants++;
         }
      }
      // supports partis de la carte sans repasser par updateFutures (jamais vu) : déjà écartés par le parcours, retirés ici
      // (un support pas encore visible mais déjà dans la carte en cours de Minecraft est gardé)
      int etrangers = 0;
      for (Iterator<ChunkHolder> it = actifs.iterator(); it.hasNext(); ) {
         ChunkHolder h = it.next();
         long k = h.getPos().toLong();
         if (a.heraliz$visible(k) != h && a.heraliz$enCours(k) != h) {
            it.remove();
            etrangers++;
         }
      }
      Allege.tronconsActifsControles++;
      if (etrangers > 0) {
         LOG.info("[noyau] tronçons qui tournent : {} support(s) hors de la carte de {} retirés de la liste", etrangers,
               a.heraliz$monde().dimension().location());
      }
      if (manquants > 0) {
         ecarts++;
         LOG.warn("[noyau] tronçons qui tournent : {} tronçon(s) qui tournent absents de la liste dans {}, liste refaite", manquants,
               a.heraliz$monde().dimension().location());
         refaire();
         if (ecarts > 5) {
            coupe = true;
            LOG.warn("[noyau] tronçons qui tournent : plus de 5 écarts, liste coupée jusqu'au redémarrage (parcours de Minecraft)");
         }
      }
   }

   static String etat() {
      int n = 0;
      for (TronconsActifs t : TOUS) {
         n += t.actifs.size();
      }
      return (coupe ? "COUPÉE, " : "") + n + " tronçons dans la liste";
   }
}
