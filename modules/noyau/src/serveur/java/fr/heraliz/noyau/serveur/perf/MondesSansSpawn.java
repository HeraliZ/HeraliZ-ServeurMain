package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Zone de départ des mondes peu utilisés plus gardée chargée (27/10, perf) : la partie Bukkit de Mohist garde chargés en
 * permanence 2 209 tronçons autour du point de départ de CHAQUE monde (Minecraft seul : seulement la carte), même sans
 * personne (relevé du 27/10 : 13 000 tronçons chargés serveur vide, parcourus à chaque tick et à chaque sauvegarde).
 * Pour les mondes de {@code [perf] mondes_sans_spawn} de heraliz_noyau-server.toml (par défaut le monde du tutoriel des
 * bases et celui des arènes du ClashWar), après le démarrage : {@code World.setKeepSpawnInMemory(false)} de Bukkit (par
 * réflexion, comme Bukkit le fait lui-même), sinon le ticket de départ de Minecraft retiré. Les tronçons se chargent comme
 * ailleurs quand un joueur y va ; les mods qui y posent quelque chose prennent leurs propres tickets. Nombre de tronçons
 * chargés de chaque monde écrit dans le journal 30 s après.
 */
public final class MondesSansSpawn {
   private static final Logger LOG = LogUtils.getLogger();
   /** Rayon du ticket de départ de Minecraft et de Bukkit. */
   private static final int RAYON = 11;
   private static int bilanDans = -1;

   private MondesSansSpawn() {
   }

   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> appliquer(e.getServer()));
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
         if (e.phase != TickEvent.Phase.END || bilanDans < 0 || bilanDans-- > 0) return;
         List<String> l = new ArrayList<>();
         for (ServerLevel niveau : e.getServer().getAllLevels()) {
            l.add(niveau.dimension().location() + " " + niveau.getChunkSource().getLoadedChunksCount());
         }
         LOG.info("[noyau] tronçons chargés 30 s après le démarrage : {}", String.join(", ", l));
      });
   }

   private static void appliquer(MinecraftServer serveur) {
      List<? extends String> liste = NoyauConfig.MONDES_SANS_SPAWN.get();
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
}
