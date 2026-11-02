package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Tronçons forcés retirés au démarrage (02/11, {@code [monde] retirer_troncons_forces} de heraliz_noyau-server.toml) : les
 * {@code /forceload} laissés par les scripts de pose des décors du 24/10 gardaient chargés à vie 13 tronçons de la carte
 * et ~2 000 autour, même serveur vide (journal : « forced x13 (rayon 2) ») ; la commande {@code forceload} est retirée
 * ({@code [commandes] retirees}), le staff ne peut plus les enlever à la console.
 * <p>Une fois par démarrage, dans chaque monde, comme {@code /forceload remove all} : par l'API du jeu
 * ({@link ServerLevel#setChunkForced}, que Minecraft enregistre avec le monde), jamais en écrivant ses fichiers. Le journal
 * dit combien et où (tronçon et blocs). Une fois retirés, ils ne reviennent pas : les démarrages suivants n'ont rien à faire.
 * <p>Aucun de nos mods ne force de tronçon (vérifié le 02/11 : les points d'arrivée et les poses prennent leurs propres
 * tickets) ; les tickets posés par les autres mods via Forge (ForgeChunkManager) et par les plugins (tickets de plugin
 * de Bukkit) ne sont pas des tronçons forcés de Minecraft et ne sont pas touchés. Fil serveur seulement.
 */
public final class TronconsForces {
   private static final Logger LOG = LogUtils.getLogger();

   private TronconsForces() {
   }

   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> retirer(e.getServer()));
   }

   private static void retirer(MinecraftServer serveur) {
      if (!NoyauConfig.RETIRER_TRONCONS_FORCES.get()) {
         return;
      }
      for (ServerLevel niveau : serveur.getAllLevels()) {
         if (niveau.getForcedChunks().isEmpty()) {
            continue;
         }
         // copie : setChunkForced retire de l'ensemble qu'on parcourrait
         List<ChunkPos> forces = new ArrayList<>();
         for (long l : niveau.getForcedChunks().toLongArray()) {
            forces.add(new ChunkPos(l));
         }
         forces.sort(Comparator.<ChunkPos>comparingInt(c -> c.x).thenComparingInt(c -> c.z));
         StringBuilder ou = new StringBuilder();
         int n = 0;
         for (ChunkPos c : forces) {
            if (!niveau.setChunkForced(c.x, c.z, false)) {
               continue;
            }
            n++;
            ou.append(ou.length() == 0 ? "" : " ; ")
                  .append(String.format(Locale.ROOT, "%d %d (blocs %d %d)", c.x, c.z, c.getMinBlockX(), c.getMinBlockZ()));
         }
         if (n > 0) {
            LOG.info("[noyau] {} tronçon(s) forcé(s) retiré(s) de {} ([monde] retirer_troncons_forces), tronçon x z (blocs x z) : {}",
                  n, niveau.dimension().location(), ou);
         }
      }
   }
}
