package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.allege.DistanceManagerAcces;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Zone de départ des mondes peu utilisés plus gardée chargée (27/10, perf) : la partie Bukkit de Mohist garde chargés en
 * permanence 2 209 tronçons autour du point de départ de CHAQUE monde (Minecraft seul : seulement la carte), même sans
 * personne (relevé du 27/10 : 13 000 tronçons chargés serveur vide, parcourus à chaque tick et à chaque sauvegarde).
 * Pour les mondes de {@code [perf] mondes_sans_spawn} de heraliz_noyau-server.toml (par défaut l'ancien monde des bases, le
 * monde du tutoriel et celui des arènes du ClashWar) :
 * <ul>
 *    <li>01/11 (« Minecraft allégé ») : avec {@code spawn_jamais_prepare}, la zone n'est même plus préparée au démarrage
 *    (champ {@code keepSpawnInMemory} de Mohist mis à faux au chargement du monde, juste avant que Mohist le lise : avant,
 *    441 tronçons chargés, voire générés, puis libérés), ni remise par {@code /setworldspawn} (Minecraft y remet son ticket
 *    sans regarder ce champ : {@link fr.heraliz.noyau.serveur.allege.SpawnSansTicketMixin}) ;</li>
 *    <li>après le démarrage (comme avant, et filet si le champ n'existe pas) : {@code World.setKeepSpawnInMemory(false)} de
 *    Bukkit (par réflexion, comme Bukkit le fait lui-même), sinon le ticket de départ de Minecraft retiré.</li>
 * </ul>
 * Les tronçons se chargent comme ailleurs quand un joueur y va ; les mods qui y posent quelque chose prennent leurs propres
 * tickets. Pas la carte (chaque réapparition sans lit passe d'abord par son point de départ, chargé sur le fil du serveur
 * s'il ne l'était pas) ni le monde du spawn (choix du propriétaire). 30 s après le démarrage, le journal donne les
 * tronçons chargés de chaque monde et les tickets qui les tiennent ({@code /diag etat} aussi).
 */
public final class MondesSansSpawn {
   private static final Logger LOG = LogUtils.getLogger();
   /** Rayon du ticket de départ de Minecraft et de Bukkit. */
   private static final int RAYON = 11;
   /** Niveau d'un tronçon chargé en entier (un ticket de rayon r a le niveau 33 - r). */
   private static final int NIVEAU_PLEIN = 33;
   private static int bilanDans = -1;
   private static Field garderSpawn;
   private static boolean garderSpawnCherche;

   private MondesSansSpawn() {
   }

   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((LevelEvent.Load e) -> {
         if (e.getLevel() instanceof ServerLevel niveau) {
            avantPreparation(niveau);
         }
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> appliquer(e.getServer()));
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
         if (e.phase != TickEvent.Phase.END || bilanDans < 0 || bilanDans-- > 0) return;
         List<String> l = new ArrayList<>();
         for (ServerLevel niveau : e.getServer().getAllLevels()) {
            l.add(niveau.dimension().location() + " " + niveau.getChunkSource().getLoadedChunksCount());
         }
         LOG.info("[noyau] tronçons chargés 30 s après le démarrage : {}", String.join(", ", l));
         tickets(e.getServer(), t -> LOG.info("[noyau] {}", t.stripTrailing()));
      });
   }

   /** Monde de la liste, et zone de départ jamais préparée (réglages lus avant la création des mondes). */
   public static boolean sansSpawn(ServerLevel niveau) {
      return NoyauConfig.spawnJamaisPrepare && liste().contains(niveau.dimension().location().toString());
   }

   private static List<? extends String> liste() {
      try {
         return NoyauConfig.MONDES_SANS_SPAWN.get();
      } catch (IllegalStateException e) {
         return NoyauConfig.MONDES_SANS_SPAWN_DEFAUT;
      }
   }

   /** Chargement du monde (Mohist : juste avant de préparer sa zone de départ) : champ de Mohist mis à faux. */
   private static void avantPreparation(ServerLevel niveau) {
      if (!sansSpawn(niveau)) {
         return;
      }
      Field f = champGarderSpawn();
      if (f == null) {
         return;
      }
      try {
         if (f.getBoolean(niveau)) {
            f.setBoolean(niveau, false);
            LOG.info("[noyau] zone de départ de {} jamais préparée (mondes_sans_spawn)", niveau.dimension().location());
         }
      } catch (ReflectiveOperationException | RuntimeException ex) {
         LOG.debug("[noyau] keepSpawnInMemory de Mohist inaccessible ({})", ex.toString());
      }
   }

   /** {@code Level.keepSpawnInMemory} de Mohist (public), null ailleurs. */
   private static Field champGarderSpawn() {
      if (!garderSpawnCherche) {
         garderSpawnCherche = true;
         try {
            Field f = Level.class.getField("keepSpawnInMemory");
            garderSpawn = f.getType() == boolean.class ? f : null;
         } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            garderSpawn = null;
         }
      }
      return garderSpawn;
   }

   private static void appliquer(MinecraftServer serveur) {
      List<? extends String> liste = liste();
      List<String> faits = new ArrayList<>();
      for (ServerLevel niveau : serveur.getAllLevels()) {
         String id = niveau.dimension().location().toString();
         if (!liste.contains(id)) continue;
         if (!parBukkit(niveau)) {
            niveau.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(niveau.getSharedSpawnPos()), RAYON, Unit.INSTANCE);
         }
         faits.add(id);
      }
      if (!faits.isEmpty()) {
         LOG.info("[noyau] zone de départ plus gardée chargée : {}", String.join(", ", faits));
      }
      bilanDans = 600;
   }

   /** {@code setKeepSpawnInMemory(false)} sur le monde Bukkit de ce niveau ; faux sans Bukkit ou monde introuvable. */
   private static boolean parBukkit(ServerLevel niveau) {
      try {
         Class<?> bukkit = Class.forName("org.bukkit.Bukkit", false, MinecraftServer.class.getClassLoader());
         for (Object monde : (List<?>) bukkit.getMethod("getWorlds").invoke(null)) {
            Method poignee = monde.getClass().getMethod("getHandle");
            if (poignee.invoke(monde) == niveau) {
               monde.getClass().getMethod("setKeepSpawnInMemory", boolean.class).invoke(monde, false);
               return true;
            }
         }
      } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
         LOG.debug("[noyau] zone de départ par Bukkit impossible ({}) : ticket de Minecraft retiré", ex.toString());
      }
      return false;
   }

   /**
    * Tickets qui tiennent les tronçons de chaque monde, par sorte : nombre et rayon (un ticket de rayon r charge en entier
    * (2r+1)² tronçons, et Minecraft en garde encore ~12 de plus tout autour, à moitié chargés). « player » : joueurs ;
    * « start » : zone de départ ; « forced » : /forceload ; « heraliz_arrivee_fixe » / « crac_arrivee_fixe » : points
    * d'arrivée gardés (réveil, /spawn). Fil serveur seulement (lit les tables de Minecraft).
    */
   public static void tickets(MinecraftServer serveur, Consumer<String> ligne) {
      for (ServerLevel niveau : serveur.getAllLevels()) {
         if (!((Object) niveau.getChunkSource().chunkMap.getDistanceManager() instanceof DistanceManagerAcces acces)) {
            continue;
         }
         Map<String, int[]> parSorte = new TreeMap<>();
         for (Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> e : acces.heraliz$tickets().long2ObjectEntrySet()) {
            for (Ticket<?> t : e.getValue()) {
               int[] c = parSorte.computeIfAbsent(t.getType().toString(), k -> new int[]{0, Integer.MAX_VALUE});
               c[0]++;
               c[1] = Math.min(c[1], t.getTicketLevel());
            }
         }
         StringBuilder b = new StringBuilder("tickets de ").append(niveau.dimension().location()).append(" (")
               .append(niveau.getChunkSource().getLoadedChunksCount()).append(" tronçons chargés) :");
         if (parSorte.isEmpty()) {
            b.append(" aucun");
         }
         parSorte.forEach((sorte, c) -> b.append(String.format(Locale.ROOT, " %s x%d (rayon %d)", sorte, c[0], NIVEAU_PLEIN - c[1])));
         ligne.accept(b.append('\n').toString());
      }
   }
}
