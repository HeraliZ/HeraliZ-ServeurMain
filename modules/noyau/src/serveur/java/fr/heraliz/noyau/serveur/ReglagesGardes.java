package fr.heraliz.noyau.serveur;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.network.ConfigSync;
import net.minecraftforge.network.HandshakeMessages;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

/**
 * Réglages du serveur gardés au serveur (audit du 02/11, point 11, §3 de split) : à la connexion, Forge envoie à chaque
 * joueur le contenu complet de TOUS les fichiers de réglages SERVER (serverconfig du monde : {@code ConfigSync.syncConfigs}).
 * Les fichiers de {@link #FICHIERS} ne sont lus par aucun code du jeu des joueurs : ils sont retirés de cet envoi
 * ({@code mixin/ReglagesGardesMixin}). Rien ne change sur le serveur (mêmes fichiers, mêmes valeurs, rechargés pareil).
 * <p>Vérifié fichier par fichier le 02/11 : la classe de réglages n'est citée par aucun fichier client, et dans le code
 * commun seulement par sa déclaration (registerConfig) ou par du code qui ne tourne que sur le serveur. Un réglage lu
 * par le jeu du joueur (aperçu, prédiction) ne doit JAMAIS être ajouté ici : chez le joueur, un réglage SERVER qui n'est
 * pas reçu n'est jamais chargé, et le lire fait planter son jeu. Restent envoyés exprès : crac-*, heralisz_tactical,
 * airstrike, heraliz_corps, heraliz_ping, heraliz_vehicules, caracrea (lus par le jeu des joueurs) et les réglages des
 * mods des autres auteurs (forge, curios, tacz, ywzj_vehicle, mutantszombies).
 */
public final class ReglagesGardes {
   private ReglagesGardes() {}

   private static final Logger LOG = LogUtils.getLogger();

   /** Noms des fichiers SERVER jamais envoyés aux joueurs. */
   static final Set<String> FICHIERS = Set.of(
         "heraliz_noyau-server.toml",       // NoyauConfig : lu par la partie serveur du noyau seulement
         "heraliz_loot-server.toml",        // ConfigLoot : déclaré et lu par la partie serveur de Loot
         "heraliz_zombies-server.toml",     // ZombiesConfig : déclaré et lu par la partie serveur de Monde
         "heraliz_distances-server.toml",   // DistanceVue : partie serveur de Monde
         "heraliz_cycle-server.toml",       // CycleConfig : partie serveur de Monde
         "heraliz_spawn-server.toml",       // DepartConfig : lu par DepartServeur et ProtectionSpawn
         "heraliz_decors-server.toml",      // ConfigZones : lu par ZonesServeur
         "reanimation-server.toml",         // ReanimationConfig : lu par la partie serveur de Réanimation
         "heralisz_utils-server.toml");     // BonusLootConfig : bonus de loot et /suicide, partie serveur de Monde

   /** Vrai dès que le filtre a été appelé par Forge (le mixin est bien en place). */
   private static volatile boolean branche;

   /** Le fichier ne part jamais chez les joueurs. */
   public static boolean garde(String fichier) {
      return fichier != null && FICHIERS.contains(fichier);
   }

   /** Appelé par le mixin à chaque envoi. */
   public static void vu() {
      branche = true;
   }

   static void enregistrer() {
      MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> verifier());
   }

   /** Au démarrage : demande une fois l'envoi de Forge et dit dans le journal s'il est bien filtré. */
   private static void verifier() {
      try {
         List<Pair<String, HandshakeMessages.S2CConfigData>> envoi = ConfigSync.INSTANCE.syncConfigs(false);
         int restes = 0;
         for (Pair<String, HandshakeMessages.S2CConfigData> p : envoi) {
            if (p != null && p.getRight() != null && garde(p.getRight().getFileName())) restes++;
         }
         if (branche && restes == 0) {
            LOG.info("[noyau] réglages du serveur : {} fichier(s) envoyé(s) aux joueurs à la connexion, {} gardé(s) au serveur",
                  envoi.size(), FICHIERS.size());
         } else {
            LOG.warn("[noyau] réglages du serveur : filtre de l'envoi pas en place ({} fichier(s) gardé(s) encore envoyé(s)) :"
                  + " ils partent chez chaque joueur à la connexion, comme avant", restes);
         }
      } catch (RuntimeException ex) {
         LOG.warn("[noyau] réglages du serveur : contrôle de l'envoi impossible ({})", ex.toString());
      }
   }
}
