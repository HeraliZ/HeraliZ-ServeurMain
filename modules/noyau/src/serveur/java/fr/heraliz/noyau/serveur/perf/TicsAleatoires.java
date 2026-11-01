package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Tics aléatoires coupés (01/11, choix du propriétaire : « carte figée ») : gamerule {@code randomTickSpeed} de chaque
 * monde réglée par {@code [monde] tics_aleatoires} de {@code heraliz_noyau-server.toml} (0 par défaut ; -1 = ne pas
 * toucher). Banc du 01/11 : les tics aléatoires des blocs (herbe qui pousse ou meurt, feuilles qui tombent, neige et glace
 * qui fondent, cultures, lave qui met le feu…) faisaient ~14 % du tick ; nos mods n'en ont aucun.
 * <p>Seulement par l'API du jeu, comme {@code /gamerule} (rien n'est écrit à la main dans les fichiers du monde ; Minecraft
 * enregistre la règle dans level.dat comme d'habitude). Sous Mohist, les mondes ajoutés par les mods et les datapacks
 * (spawn, tutoriel, bases, arènes) n'ont pas de level.dat à eux : leurs règles SONT celles de la carte (même objet,
 * {@code DerivedLevelData}) ; un monde créé par un plugin Bukkit a les siennes. Chaque monde est donc réglé un par un, au
 * chargement (mondes créés plus tard compris), une deuxième fois après le démarrage et à chaque relecture du réglage.
 * Fil serveur seulement.
 */
public final class TicsAleatoires {
   private static final Logger LOG = LogUtils.getLogger();

   private TicsAleatoires() {
   }

   public static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((LevelEvent.Load e) -> {
         if (e.getLevel() instanceof ServerLevel niveau && niveau.getServer() != null && niveau.getServer().isSameThread()) {
            regler(niveau, NoyauConfig.ticsAleatoires);
         }
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
         demarre = true;
         appliquer(e.getServer(), "démarrage");
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> demarre = false);
   }

   /** Après le démarrage seulement (avant, chaque monde est réglé à son chargement). */
   private static volatile boolean demarre;

   /** Réglages relus (fichier changé pendant que le serveur tourne) : appliqué sur le fil serveur. */
   public static void reglagesRelus() {
      MinecraftServer serveur = ServerLifecycleHooks.getCurrentServer();
      if (demarre && serveur != null && serveur.isRunning()) {
         serveur.execute(() -> appliquer(serveur, "réglage relu"));
      }
   }

   /** Tous les mondes, puis une ligne dans le journal : quels mondes, quelle valeur. */
   private static void appliquer(MinecraftServer serveur, String quand) {
      int voulu = NoyauConfig.ticsAleatoires;
      ServerLevel carte = serveur.overworld();
      GameRules reglesCarte = carte == null ? null : carte.getGameRules();
      List<String> l = new ArrayList<>();
      for (ServerLevel niveau : serveur.getAllLevels()) {
         regler(niveau, voulu);
         GameRules regles = niveau.getGameRules();
         String partage = niveau != carte && regles == reglesCarte ? " (règles de la carte)" : "";
         l.add(niveau.dimension().location() + " " + regles.getInt(GameRules.RULE_RANDOMTICKING) + partage);
      }
      if (voulu < 0) {
         LOG.info("[noyau] tics aléatoires ({}) : réglage -1, gamerule randomTickSpeed laissée telle quelle : {}", quand, String.join(", ", l));
      } else {
         LOG.info("[noyau] tics aléatoires ({}) : randomTickSpeed = {} : {}", quand, voulu, String.join(", ", l));
      }
   }

   private static void regler(ServerLevel niveau, int voulu) {
      if (voulu < 0) {
         return;
      }
      GameRules.IntegerValue regle = niveau.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
      if (regle.get() != voulu) {
         regle.set(voulu, niveau.getServer());
      }
   }
}
