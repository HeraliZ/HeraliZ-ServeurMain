package fr.heralisz.matchs.pont;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charge les parties « serveur » et « client » d'un mod, si elles sont dans le jar (docs/METHODE_CLIENT_SERVEUR.md §4).
 * <ul>
 *    <li>{@code -serveur.jar} : commun + serveur. La partie client n'existe pas : rien n'est chargé, rien ne casse.</li>
 *    <li>{@code -client.jar} : commun + client. La partie serveur n'existe pas : en solo, les boss restent neutres (pas
 *    d'IA, pas d'attaques) ; c'est voulu, le solo se joue avec {@code -complet.jar}.</li>
 *    <li>{@code -complet.jar} : les deux. La partie client n'est chargée que sur un client physique (jamais sur un serveur
 *    dédié, même si ses classes sont dans le jar).</li>
 * </ul>
 * À appeler dans le constructeur du @Mod, APRÈS les DeferredRegister.register(bus) et AVANT de rendre la main :
 * <pre>
 *    Parties.serveur(MODID, "fr.heralisz.mammoth.serveur.MammothServeur", bus);
 *    Parties.client(MODID, "fr.heralisz.mammoth.client.MammothClient", bus);
 * </pre>
 */
public final class Parties {
   private static final Logger LOG = LogUtils.getLogger();
   /** modId -> parties chargées (« serveur », « client », « serveur+client » ou « aucune »), pour le journal et les tests. */
   private static final Map<String, String> CHARGEES = new ConcurrentHashMap<>();

   private Parties() {}

   /** Charge la partie serveur (logique de jeu) si sa classe est dans le jar. Sert aussi au serveur intégré du solo. */
   public static boolean serveur(String modId, String classe, IEventBus busMod) {
      return charger(modId, "serveur", classe, busMod);
   }

   /** Charge la partie client (affichage) si sa classe est dans le jar ET qu'on tourne sur un client physique. */
   public static boolean client(String modId, String classe, IEventBus busMod) {
      if (FMLEnvironment.dist != Dist.CLIENT) {
         noter(modId, null);
         return false;
      }
      return charger(modId, "client", classe, busMod);
   }

   /** Vrai si la partie serveur de ce mod a été chargée (jar -serveur ou -complet). */
   public static boolean serveurPresent(String modId) {
      return CHARGEES.getOrDefault(modId, "").contains("serveur");
   }

   /** Vrai si la partie client de ce mod a été chargée (jar -client ou -complet, sur un client physique). */
   public static boolean clientPresent(String modId) {
      return CHARGEES.getOrDefault(modId, "").contains("client");
   }

   private static boolean charger(String modId, String cote, String classe, IEventBus busMod) {
      Class<?> c;
      try {
         c = Class.forName(classe, true, Parties.class.getClassLoader());
      } catch (ClassNotFoundException absente) {
         // jar sans cette partie : normal (-client sans serveur, -serveur sans client)
         noter(modId, null);
         LOG.info("[{}] partie {} absente de ce jar ({})", modId, cote, classe);
         return false;
      }
      Partie p;
      try {
         p = (Partie) c.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException | ClassCastException ex) {
         throw new IllegalStateException("[" + modId + "] partie " + cote + " illisible : " + classe, ex);
      }
      p.init(busMod);
      noter(modId, cote);
      LOG.info("[{}] partie {} chargée", modId, cote);
      return true;
   }

   private static void noter(String modId, String cote) {
      CHARGEES.merge(modId, cote == null ? "" : cote, (a, b) -> b.isEmpty() ? a : a.isEmpty() ? b : a + "+" + b);
   }
}
