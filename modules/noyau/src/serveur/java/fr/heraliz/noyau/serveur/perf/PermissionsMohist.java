package fr.heraliz.noyau.serveur.perf;

import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;

/**
 * Permissions refusées sous Mohist (20/11, audit du tick ; constat F3 du 19/11). Le build 916 de Mohist relève TOUTE la
 * pile d'appels ({@code Thread.getStackTrace}) à chaque permission refusée à un joueur, pour son journal de débogage des
 * permissions ({@code PermissionUtils.debug}), qui ne s'en sert que si {@code permissions.debug.console} est à true dans
 * {@code mohist.yml} (faux ici). Or chaque mort, {@code /spawn}, extraction ou changement de monde renvoie au joueur la
 * liste des commandes, et pour un joueur normal presque toutes celles des plugins (WorldEdit, LiteBans...) sont refusées :
 * des centaines de relevés de pile, 35 à 60 ms sur le fil du serveur. Les builds de Mohist d'après le 20/07/2025 ne le font
 * plus (même test). Ici, sans toucher au jar de Mohist ({@code PermissionsSansPileMixin}) : quand ce journal est coupé,
 * une pile vide est rendue (elle n'est pas lue) ; quand il est actif, la vraie pile, comme avant. Réglage
 * {@code [perf] permissions_sans_pile}. Tous les fils (un plugin peut tester une permission hors du fil serveur).
 */
public final class PermissionsMohist {
   private PermissionsMohist() {
   }

   private static final StackTraceElement[] VIDE = new StackTraceElement[0];
   private static volatile boolean lu;
   private static volatile Field journalConsole;
   /** Relevés de pile évités (compteur approché : plusieurs fils). */
   public static volatile long pilesEvitees;

   /** La pile pour {@code PermissionUtils.debug} : vide si elle ne sera pas lue. */
   public static StackTraceElement[] pile(Thread fil) {
      if (!NoyauConfig.permissionsSansPile || journalActif()) {
         return fil.getStackTrace();
      }
      pilesEvitees++;
      return VIDE;
   }

   /** {@code permissions.debug.console} de Mohist (absent : faux). */
   private static boolean journalActif() {
      if (!lu) {
         try {
            journalConsole = Class.forName("com.mohistmc.MohistConfig", false, MinecraftServer.class.getClassLoader())
                  .getField("permissions_debug_console");
         } catch (Throwable e) {
            journalConsole = null;
         }
         lu = true;
      }
      Field f = journalConsole;
      try {
         return f != null && f.getBoolean(null);
      } catch (Throwable e) {
         return false;
      }
   }
}
