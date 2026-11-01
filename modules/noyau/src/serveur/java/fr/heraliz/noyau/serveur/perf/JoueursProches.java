package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.allege.ChunkMapAcces;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Point 1 du chantier « Minecraft allégé » (01/11) : « un joueur est-il assez près de ce tronçon ? »
 * ({@code ChunkMap.anyPlayerCloseEnoughForSpawning}), posé par {@code ServerChunkCache.tickChunks} pour CHAQUE tronçon
 * actif à CHAQUE tick (apparitions des créatures, tics aléatoires des blocs, temps passé dans le tronçon). Minecraft y
 * parcourt tous les joueurs du monde (sa carte des joueurs rend tout le monde) jusqu'à en trouver un à moins de 128
 * blocs ; Mohist ajoute deux écritures atomiques par joueur regardé, et un 2e appel par tronçon avec la portée réduite de
 * {@code mob-spawn-range} (spigot.yml). Banc de charge du 01/11 (80 joueurs) : environ 8 % du tick.
 * <p>Ici, une fois par passage de {@code tickChunks} (par monde et par tick), les joueurs de la même carte des joueurs,
 * spectateurs exclus comme chez Minecraft, sont rangés dans une grille de cases de 128 blocs ; pour un tronçon, seuls les
 * joueurs des 9 cases autour de son centre sont regardés (tout joueur à moins de 128 blocs y est forcément). Même calcul
 * de distance, même borne (strictement moins que 128², ou la portée réduite de Mohist, recalculée comme Mohist à chaque
 * tick), même condition « joueur présent à 8 tronçons » du gestionnaire des distances (lue après la grille au lieu
 * d'avant : les deux ne font que lire, la réponse est la même). Les états internes de Mohist (portée en cours) restent
 * comme après son propre appel. Réglage : {@code [perf] joueurs_proches_grille}.
 * <p>Une instance par monde (champ ajouté à {@code ServerChunkCache}). Fil serveur seulement.
 */
public final class JoueursProches {
   private static final Logger LOG = LogUtils.getLogger();
   /** Case de la grille : 1 << 7 = 128 blocs, la plus grande portée de Minecraft. */
   private static final int CASE = 7;
   /** Portée de Minecraft, au carré (128 blocs). */
   private static final double PORTEE = 16384.0D;

   private final Long2IntOpenHashMap tetes = new Long2IntOpenHashMap();
   private double[] xs = new double[32];
   private double[] zs = new double[32];
   private int[] suivants = new int[32];
   private int nombre;
   private boolean aRefaire = true;
   /** Portée réduite de Mohist (mob-spawn-range), au carré, relue à chaque tick ; négative = inconnue. */
   private double porteeReduite = -1;
   /** Dernier tronçon dont la présence d'un joueur à 8 tronçons a été lue (2e appel de Mohist, même tronçon). */
   private long cleLue;
   private boolean presenceLue;
   private boolean lue;

   public JoueursProches() {
      tetes.defaultReturnValue(-1);
   }

   /** Début de {@code tickChunks} : la grille sera refaite au premier tronçon de ce tick. */
   public void nouveauTick(ServerLevel niveau) {
      aRefaire = true;
      lue = false;
      porteeReduite = Mohist.porteeReduite(niveau);
   }

   /** Appel de Minecraft (portée de 128 blocs). */
   public boolean proche(ChunkMap carte, ChunkPos pos) {
      if (!NoyauConfig.joueursProchesGrille) {
         return Mohist.minecraft(carte, pos);
      }
      Boolean r = juger(carte, pos, PORTEE);
      return r != null ? r : Mohist.minecraft(carte, pos);
   }

   /** 2e appel ajouté par Mohist ({@code reduit} : portée de mob-spawn-range de spigot.yml). */
   public boolean procheMohist(ChunkMap carte, ChunkPos pos, boolean reduit) {
      if (!NoyauConfig.joueursProchesGrille || (reduit && porteeReduite < 0)) {
         return Mohist.mohist(carte, pos, reduit);
      }
      Boolean r = juger(carte, pos, reduit ? porteeReduite : PORTEE);
      return r != null ? r : Mohist.mohist(carte, pos, reduit);
   }

   /** La réponse, ou null si la carte des joueurs est inaccessible (alors, celle de Minecraft). */
   private Boolean juger(ChunkMap carte, ChunkPos pos, double portee) {
      long cle = pos.toLong();
      if (aRefaire && !refaire(carte, cle)) {
         return null;
      }
      Allege.prochesGrille++;
      double cx = (double) SectionPos.sectionToBlockCoord(pos.x, 8);
      double cz = (double) SectionPos.sectionToBlockCoord(pos.z, 8);
      boolean trouve = portee <= PORTEE ? dansLaGrille(pos, cx, cz, portee) : partout(cx, cz, portee);
      if (!trouve) {
         Allege.prochesSansJoueur++;
         return Boolean.FALSE;
      }
      if (lue && cleLue == cle) {
         return presenceLue;
      }
      boolean presence = carte.getDistanceManager().hasPlayersNearby(cle);
      cleLue = cle;
      presenceLue = presence;
      lue = true;
      return presence;
   }

   /** Joueurs des 9 cases autour du centre du tronçon (la sienne d'abord). */
   private boolean dansLaGrille(ChunkPos pos, double cx, double cz, double portee) {
      int kx = SectionPos.sectionToBlockCoord(pos.x, 8) >> CASE;
      int kz = SectionPos.sectionToBlockCoord(pos.z, 8) >> CASE;
      if (dansLaCase(kx, kz, cx, cz, portee)) {
         return true;
      }
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && dansLaCase(kx + dx, kz + dz, cx, cz, portee)) {
               return true;
            }
         }
      }
      return false;
   }

   private boolean dansLaCase(int kx, int kz, double cx, double cz, double portee) {
      for (int i = tetes.get(ChunkPos.asLong(kx, kz)); i >= 0; i = suivants[i]) {
         // même calcul que ChunkMap.euclideanDistanceSquared
         double ex = cx - xs[i];
         double ez = cz - zs[i];
         if (ex * ex + ez * ez < portee) {
            return true;
         }
      }
      return false;
   }

   /** Portée plus grande que 128 blocs (jamais avec Minecraft ni Mohist) : tous les joueurs. */
   private boolean partout(double cx, double cz, double portee) {
      for (int i = 0; i < nombre; i++) {
         double ex = cx - xs[i];
         double ez = cz - zs[i];
         if (ex * ex + ez * ez < portee) {
            return true;
         }
      }
      return false;
   }

   private boolean refaire(ChunkMap carte, long cle) {
      if (!((Object) carte instanceof ChunkMapAcces acces)) {
         return false;
      }
      tetes.clear();
      int n = 0;
      for (ServerPlayer p : acces.heraliz$joueurs().getPlayers(cle)) {
         if (p.isSpectator()) {
            continue;
         }
         if (n == xs.length) {
            xs = Arrays.copyOf(xs, n * 2);
            zs = Arrays.copyOf(zs, n * 2);
            suivants = Arrays.copyOf(suivants, n * 2);
         }
         double x = p.getX();
         double z = p.getZ();
         xs[n] = x;
         zs[n] = z;
         long c = ChunkPos.asLong(Mth.floor(x) >> CASE, Mth.floor(z) >> CASE);
         suivants[n] = tetes.get(c);
         tetes.put(c, n);
         n++;
      }
      nombre = n;
      aRefaire = false;
      return true;
   }

   /** Réponses d'origine (réglage coupé) et réglages de Mohist lus par réflexion (absents sans Mohist). */
   static final class Mohist {
      private static boolean resolu;
      private static Field configMonde;
      private static Field porteeApparitions;
      private static Field vue;
      private static MethodHandle procheMohist;
      private static Method procheMinecraft;

      private Mohist() {
      }

      private static void resoudre() {
         if (resolu) {
            return;
         }
         resolu = true;
         try {
            configMonde = Level.class.getField("spigotConfig");
            porteeApparitions = configMonde.getType().getField("mobSpawnRange");
            vue = configMonde.getType().getField("viewDistance");
         } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            configMonde = null;
         }
         try {
            procheMohist = MethodHandles.publicLookup().findVirtual(ChunkMap.class, "anyPlayerCloseEnoughForSpawning",
                  MethodType.methodType(boolean.class, ChunkPos.class, boolean.class));
         } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            procheMohist = null;
         }
      }

      /** Comme Mohist : min(mob-spawn-range, view-distance de spigot.yml, 8) tronçons, en blocs, au carré ; -1 sans Mohist. */
      static double porteeReduite(ServerLevel niveau) {
         resoudre();
         if (configMonde == null) {
            return -1;
         }
         try {
            Object config = configMonde.get(niveau);
            if (config == null) {
               return -1;
            }
            int chunkRange = porteeApparitions.getInt(config);
            int distanceVue = vue.getInt(config);
            chunkRange = (chunkRange > distanceVue) ? (byte) distanceVue : chunkRange;
            chunkRange = Math.min(chunkRange, 8);
            return Math.pow(chunkRange << 4, 2);
         } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
         }
      }

      static boolean mohist(ChunkMap carte, ChunkPos pos, boolean reduit) {
         resoudre();
         try {
            if (procheMohist != null) {
               return (boolean) procheMohist.invokeExact(carte, pos, reduit);
            }
         } catch (Throwable t) {
            LOG.warn("[noyau] joueurs proches : réponse de Mohist impossible ({})", t.toString());
         }
         return minecraft(carte, pos);
      }

      static boolean minecraft(ChunkMap carte, ChunkPos pos) {
         if ((Object) carte instanceof ChunkMapAcces acces) {
            return acces.heraliz$procheMinecraft(pos);
         }
         try {
            if (procheMinecraft == null) {
               procheMinecraft = ObfuscationReflectionHelper.findMethod(ChunkMap.class, "m_183879_", ChunkPos.class);
            }
            return (boolean) procheMinecraft.invoke(carte, pos);
         } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("[noyau] joueurs proches : réponse de Minecraft impossible", e);
         }
      }
   }
}
