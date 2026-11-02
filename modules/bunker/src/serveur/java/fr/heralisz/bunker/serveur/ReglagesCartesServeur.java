package fr.heralisz.bunker.serveur;

import fr.heralisz.bunker.keycard.KeycardConfig;
import fr.heralisz.bunker.keycard.KeycardNetwork;
import fr.heralisz.bunker.keycard.ReglagesCartes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Réglages des cartes d'accès, côté serveur seulement (audit du 02/11, point 11, §4 de split) : déclarés ici en COMMON,
 * avec le même nom de fichier qu'avant (config/heralisz_bunker-common.toml du serveur, valeurs gardées), ils ne sont plus
 * écrits sur le PC des joueurs. À chaque chargement du fichier, ce que lit le code commun est recopié dans
 * {@link ReglagesCartes} ; les passages des cartes partent chez chaque joueur à la connexion, et chez tous quand le
 * fichier change.
 */
public final class ReglagesCartesServeur {
   private ReglagesCartesServeur() {}

   static void brancher(IEventBus busMod) {
      ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KeycardConfig.SPEC, "heralisz_bunker-common.toml");
      busMod.addListener((ModConfigEvent.Loading e) -> {
         if (e.getConfig().getSpec() == KeycardConfig.SPEC) appliquer(false);
      });
      busMod.addListener((ModConfigEvent.Reloading e) -> {
         if (e.getConfig().getSpec() == KeycardConfig.SPEC) appliquer(true);
      });
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
         if (e.getEntity() instanceof ServerPlayer joueur) KeycardNetwork.envoyerReglages(joueur);
      });
   }

   private static void appliquer(boolean envoyer) {
      ReglagesCartes.definirPassages(KeycardConfig.USES_GREEN.get(), KeycardConfig.USES_BLUE.get(), KeycardConfig.USES_RED.get());
      ReglagesCartes.definirImpulsion(KeycardConfig.PULSE_TICKS.get());
      MinecraftServer serveur = ServerLifecycleHooks.getCurrentServer();
      // fichier relu par Forge sur son propre fil : l'envoi se fait sur le fil du serveur
      if (envoyer && serveur != null) serveur.execute(() -> KeycardNetwork.envoyerReglages(null));
   }
}
