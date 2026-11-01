package fr.heraliz.noyau.serveur.perf;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.allege.SuiviAcces;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Point 4 du chantier « Minecraft allégé » (01/11), partie « entités » : pour CHAQUE tronçon envoyé à un joueur
 * ({@code ChunkMap.playerLoadedChunk}), Minecraft parcourt TOUTES les entités suivies du monde pour trouver celles de ce
 * tronçon (et les montrer au joueur). À l'arrivée d'un joueur (connexion, téléportation, réapparition), ~170 tronçons ×
 * ~4 000 entités à 150 joueurs = ~700 000 tours de boucle ; à chaque changement de tronçon en marchant, ~13 × 4 000.
 * Banc du 01/11 : la ligne de cette boucle (ChunkMap:1330) en tête des rapports de blocage (8,8 % d'un tick bloqué).
 * <p>Ici, à partir du 4e tronçon envoyé sans que rien n'ait bougé, un index « tronçon → entités » est fait en UN parcours
 * des entités suivies, dans l'ordre de Minecraft, puis chaque tronçon ne regarde que les siennes ; la boucle de Minecraft
 * garde ses propres tests (même joueur exclu, même tronçon). L'index ne sert que tant que rien ne change : il est refait
 * dès qu'une entité change de tronçon ({@code Entity.setPosRaw}, seul endroit où Minecraft change le tronçon d'une
 * entité), ou qu'une entité commence ou cesse d'être suivie ({@code ChunkMap.addEntity / removeEntity}) — d'où les mêmes
 * entités, dans le même ordre, que la boucle de Minecraft. Réglage : {@code [perf] entites_par_troncon}.
 * <p>Fil serveur seulement (le compteur de changements peut aussi bouger depuis la génération du monde : il ne fait alors
 * que refaire un index pour rien).
 */
public final class EntitesParTroncon {
   /** Tronçons envoyés sans changement avant de faire l'index (en dessous, la boucle de Minecraft coûte moins cher). */
   private static final int SEUIL = 4;
   /** Change à chaque entité qui change de tronçon, commence ou cesse d'être suivie. */
   private static int version;
   /** Index faits (vidés à la fin du tick s'ils sont périmés : ils ne retiennent pas les entités parties). */
   private static final List<EntitesParTroncon> FAITS = new ArrayList<>();

   private final Long2ObjectOpenHashMap<ObjectArrayList<Object>> index = new Long2ObjectOpenHashMap<>();
   private boolean valide;
   private int versionIndex;
   private int derniereVersion;
   private int appels;
   private boolean inscrit;

   /**
    * Les deux crochets qui rendent l'index périmé ont-ils déjà été vus en marche (mixins appliqués sur ce serveur) ? Sans
    * l'un d'eux, l'index ne sert jamais (il ne saurait pas qu'il est périmé).
    */
   private static boolean crochetTroncon;
   private static boolean crochetSuivi;

   /** Une entité a changé de tronçon ({@code Entity.setPosRaw}). */
   public static void changementDeTroncon() {
      crochetTroncon = true;
      version++;
   }

   /** Une entité commence ou cesse d'être suivie ({@code ChunkMap.addEntity / removeEntity}). */
   public static void changementDuSuivi() {
      crochetSuivi = true;
      version++;
   }

   /**
    * Ce que la boucle de {@code playerLoadedChunk} parcourt pour le tronçon {@code troncon} : les entités suivies de ce
    * tronçon (dans l'ordre de {@code carte}), ou toute la carte comme Minecraft.
    */
   public ObjectCollection<?> entites(Int2ObjectMap<?> carte, long troncon) {
      if (!NoyauConfig.entitesParTroncon || !crochetTroncon || !crochetSuivi) {
         return carte.values();
      }
      int v = version;
      if (!(valide && versionIndex == v)) {
         if (v != derniereVersion) {
            derniereVersion = v;
            appels = 0;
         }
         if (++appels < SEUIL || !faire(carte, v)) {
            return carte.values();
         }
      }
      Allege.entitesParIndex++;
      ObjectArrayList<Object> l = index.get(troncon);
      return l != null ? l : ObjectLists.emptyList();
   }

   private boolean faire(Int2ObjectMap<?> carte, int v) {
      index.clear();
      valide = false;
      for (Object suivi : carte.values()) {
         if (!(suivi instanceof SuiviAcces acces)) {
            index.clear();
            return false;
         }
         Entity e = acces.heraliz$entite();
         long cle = e.chunkPosition().toLong();
         ObjectArrayList<Object> l = index.get(cle);
         if (l == null) {
            l = new ObjectArrayList<>(4);
            index.put(cle, l);
         }
         l.add(suivi);
      }
      valide = true;
      versionIndex = v;
      Allege.indexFaits++;
      if (!inscrit) {
         inscrit = true;
         FAITS.add(this);
      }
      return true;
   }

   /** Fin de chaque tick : les index périmés sont vidés. Rien sans index fait. */
   public static void finDuTick() {
      if (FAITS.isEmpty()) {
         return;
      }
      int v = version;
      for (Iterator<EntitesParTroncon> it = FAITS.iterator(); it.hasNext(); ) {
         EntitesParTroncon e = it.next();
         if (!e.valide || e.versionIndex != v) {
            e.index.clear();
            e.valide = false;
            e.inscrit = false;
            it.remove();
         }
      }
   }

   /** Arrêt du serveur. */
   public static void vider() {
      for (EntitesParTroncon e : FAITS) {
         e.index.clear();
         e.valide = false;
         e.inscrit = false;
      }
      FAITS.clear();
   }
}
