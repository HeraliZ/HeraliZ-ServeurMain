package fr.heraliz.noyau.serveur.perf;

import fr.heraliz.noyau.NoyauConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Point 4 du chantier « Minecraft allégé » (01/11), partie « paquets » : quand un joueur reçoit un tronçon
 * ({@code ChunkMap.playerLoadedChunk}), Minecraft fabrique un paquet complet (blocs de toutes les sections, entités de
 * bloc, cartes de hauteur, lumière : des dizaines de Ko recopiés, ~50 à 100 µs) POUR CHAQUE JOUEUR, même si un autre
 * joueur vient de recevoir le même tronçon dans le même tick. Banc du 01/11 : ~12 % des ticks bloqués. Exemple : 6 joueurs
 * extraits ensemble = ~1 000 paquets dans un tick au lieu de ~170.
 * <p>Ici, le paquet d'un tronçon est gardé jusqu'à la fin du tick et redonné aux joueurs suivants. Minecraft fait déjà
 * pareil quand un tronçon devient actif pour plusieurs joueurs à la fois (un paquet pour tous) : un paquet n'est jamais
 * modifié après sa fabrication. Il est oublié dès que le tronçon change : un bloc posé ou changé (même sans prévenir les
 * joueurs), un bloc renvoyé aux joueurs, une entité de bloc modifiée, la lumière ou les biomes ; un joueur qui le reçoit
 * plus tard dans le tick a donc les mêmes octets que ceux que Minecraft aurait fabriqués pour lui. Tout est vidé à la fin
 * de chaque tick. Réglage : {@code [perf] paquets_troncons_partages}.
 * <p>Sécurité : rien n'est redonné tant que les crochets qui font oublier un paquet (bloc changé dans un tronçon, bloc
 * renvoyé aux joueurs) n'ont pas été vus en marche sur ce serveur. Fil serveur seulement (un changement venu d'un autre fil
 * ne touche pas au cache).
 */
public final class PaquetsTroncons {
   /** Au moins un paquet gardé quelque part : sinon les crochets de changement ne font rien. */
   private static boolean garde;
   private static boolean crochetBloc;
   private static boolean crochetEnvoi;
   /** Le fil du serveur (celui qui garde les paquets) : un changement venu d'un autre fil ne touche pas au cache. */
   private static Thread fil;
   private static final List<PaquetsTroncons> NON_VIDES = new ArrayList<>();

   private record Entree(LevelChunk troncon, ClientboundLevelChunkWithLightPacket paquet) {
   }

   private final Long2ObjectOpenHashMap<Entree> paquets = new Long2ObjectOpenHashMap<>();
   private int tick;
   private boolean inscrit;

   /** Le paquet déjà fabriqué pour ce tronçon dans ce tick, ou null (Minecraft le fabrique). */
   public ClientboundLevelChunkWithLightPacket reprendre(int tickServeur, LevelChunk troncon) {
      if (!NoyauConfig.paquetsTronconsPartages || !crochetBloc || !crochetEnvoi || paquets.isEmpty() || tick != tickServeur) {
         return null;
      }
      Entree e = paquets.get(troncon.getPos().toLong());
      if (e == null || e.troncon() != troncon) {
         return null;
      }
      Allege.paquetsPartages++;
      return e.paquet();
   }

   /** Paquet envoyé au joueur : gardé pour les suivants de ce tick. */
   public void garder(int tickServeur, LevelChunk troncon, ClientboundLevelChunkWithLightPacket paquet) {
      if (!NoyauConfig.paquetsTronconsPartages || paquet == null) {
         return;
      }
      if (tick != tickServeur) {
         paquets.clear();
         tick = tickServeur;
      }
      paquets.put(troncon.getPos().toLong(), new Entree(troncon, paquet));
      fil = Thread.currentThread();
      garde = true;
      if (!inscrit) {
         inscrit = true;
         NON_VIDES.add(this);
      }
   }

   /** Ce tronçon a changé : son paquet n'est plus redonné. {@code carte} : la carte des tronçons du monde. */
   public static void oublier(Object carte, long troncon) {
      if (garde && Thread.currentThread() == fil && carte instanceof AvecPaquets a) {
         PaquetsTroncons p = a.heraliz$paquets();
         if (!p.paquets.isEmpty()) {
            p.paquets.remove(troncon);
         }
      }
   }

   /**
    * Un bloc change dans un tronçon (crochet de LevelChunk.setBlockState) : vrai s'il faut chercher son paquet à oublier
    * (au moins un paquet gardé), le crochet appelle alors {@link #oublier}.
    */
   public static boolean blocChange() {
      if (!crochetBloc) {
         crochetBloc = true;
      }
      return garde;
   }

   /** Au moins un paquet gardé (sinon rien à oublier). */
   public static boolean garde() {
      return garde;
   }

   /** Un tronçon a des changements à renvoyer aux joueurs (crochet de ChunkHolder.blockChanged). */
   public static void envoiChange(Object carte, long troncon) {
      if (!crochetEnvoi) {
         crochetEnvoi = true;
      }
      oublier(carte, troncon);
   }

   /** Fin de chaque tick (et arrêt du serveur) : plus aucun paquet gardé. Rien si aucun n'a été gardé. */
   public static void finDuTick() {
      if (!garde) {
         return;
      }
      for (PaquetsTroncons p : NON_VIDES) {
         p.paquets.clear();
         p.inscrit = false;
      }
      NON_VIDES.clear();
      garde = false;
   }

   /** La carte des tronçons d'un monde (ChunkMap, par mixin) et ses paquets gardés. */
   public interface AvecPaquets {
      PaquetsTroncons heraliz$paquets();
   }
}
