package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * level.dat écrit à côté du fil serveur (27/10, perf) : à chaque sauvegarde automatique (toutes les 5 min), Minecraft
 * compressait et écrivait sur le fil serveur ce fichier de 400 Ko (registres de Forge compris), une bonne part du gel de
 * 100 à 200 ms mesuré serveur vide. Le contenu est toujours relevé par Minecraft sur le fil serveur (puis copié) ; seuls la
 * compression, l'écriture et le remplacement de level.dat (level.dat_old gardé, comme Minecraft) partent sur le fil
 * « HeraliZ-level-dat », un à la fois, dans l'ordre. Pendant l'arrêt du serveur, l'écriture se fait tout de suite, après
 * celles encore en file : rien n'est perdu à la fermeture. Branché par LevelDatEnFondMixin ;
 * {@code [perf] level_dat_en_fond = false} de heraliz_noyau-server.toml rend l'écriture de Minecraft.
 */
public final class LevelDatFond {
   private static final Logger LOG = LogUtils.getLogger();
   private static final ExecutorService FIL = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "HeraliZ-level-dat");
      t.setDaemon(true);
      return t;
   });
   /** Contenu relevé par Minecraft en attendant le remplacement du fichier (même fil, même sauvegarde). */
   private static final ThreadLocal<Releve> RELEVE = new ThreadLocal<>();
   /** Première écriture à côté déjà notée dans le journal (fil d'écriture seulement). */
   private static boolean premiere;

   private record Releve(CompoundTag contenu, File temporaire) {
   }

   private LevelDatFond() {
   }

   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerStoppedEvent.class, e -> attendre());
   }

   /** Écriture à part seulement serveur en marche ; pendant l'arrêt (sauvegarde finale), comme Minecraft. */
   private static boolean enFond() {
      MinecraftServer serveur = ServerLifecycleHooks.getCurrentServer();
      return NoyauConfig.levelDatEnFond && serveur != null && serveur.isRunning() && serveur.isSameThread();
   }

   /**
    * À la place de {@code NbtIo.writeCompressed(contenu, temporaire)} : vrai si l'écriture est gardée pour le fil d'écriture
    * (faite au remplacement), faux si Minecraft doit l'écrire lui-même tout de suite.
    */
   public static boolean garder(CompoundTag contenu, File temporaire) {
      if (!enFond()) {
         attendre();   // pas deux écritures de level.dat en même temps
         return false;
      }
      RELEVE.set(new Releve(contenu.copy(), temporaire));
      return true;
   }

   /** À la place de {@code Util.safeReplaceFile} : vrai si le remplacement part avec l'écriture gardée par {@link #garder}. */
   public static boolean remplacer(File cible, File temporaire, File ancien) {
      Releve r = RELEVE.get();
      RELEVE.remove();
      if (r == null) {
         return false;   // Minecraft a écrit le fichier lui-même
      }
      if (!r.temporaire().equals(temporaire)) {
         // jamais vu : le fichier qui va remplacer level.dat est écrit tout de suite (jamais un level.dat vide)
         try {
            NbtIo.writeCompressed(r.contenu(), temporaire);
         } catch (IOException ex) {
            LOG.error("[noyau] écriture de {} impossible (le fichier d'avant est gardé)", cible, ex);
            return true;
         }
         return false;
      }
      FIL.execute(() -> {
         try {
            long t = System.nanoTime();
            NbtIo.writeCompressed(r.contenu(), temporaire);
            Util.safeReplaceFile(cible, temporaire, ancien);
            if (!premiere) {
               premiere = true;
               LOG.info("[noyau] level.dat écrit à côté du fil serveur ({} ms, une seule fois dans le journal)", (System.nanoTime() - t) / 1_000_000);
            }
         } catch (IOException | RuntimeException ex) {
            LOG.error("[noyau] écriture de {} impossible (le fichier d'avant est gardé)", cible, ex);
            try {
               Files.deleteIfExists(temporaire.toPath());
            } catch (IOException ignore) {
               // fichier temporaire laissé : sans effet sur le monde
            }
         }
      });
      return true;
   }

   /** Attend la fin des écritures en file (arrêt du serveur, sauvegarde faite par Minecraft lui-même), 30 s au plus. */
   public static void attendre() {
      try {
         FIL.submit(() -> { }).get(30, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
      } catch (Exception ex) {
         LOG.error("[noyau] écriture de level.dat pas terminée", ex);
      }
   }
}
