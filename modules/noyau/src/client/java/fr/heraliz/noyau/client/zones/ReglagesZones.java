package fr.heraliz.noyau.client.zones;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Réglages du dessin des zones chez le joueur ({@code config/heraliz_noyau-client.toml}, section {@code [zones]}) :
 * distance jusqu'à laquelle une zone se voit à travers le décor ({@link ZoneDessin}). Un réglage d'affichage seulement :
 * les zones (interdites, bases, extractions) ne sont pas secrètes, elles sont aussi sur la carte.
 */
public final class ReglagesZones {
   private ReglagesZones() {}

   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue A_TRAVERS;
   /** Lu à chaque image par {@link ZoneDessin} (relu avec le fichier). */
   public static volatile double aTravers = 10;

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      b.push("zones");
      A_TRAVERS = b.comment("Zones (interdites, bases plan en main, extraction) : distance (blocs) jusqu'à laquelle on les voit à travers",
            "le décor (montagnes, bâtiments, arbres), de plus en plus légères ; au-delà, seulement là où on les voit. 0 : jamais.")
            .defineInRange("a_travers", 10, 0, 32);
      b.pop();
      SPEC = b.build();
   }

   /** Appelé par la partie client du noyau pendant la construction du mod. */
   public static void enregistrer(IEventBus busMod) {
      ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "heraliz_noyau-client.toml");
      busMod.addListener((ModConfigEvent.Loading e) -> appliquer(e.getConfig()));
      busMod.addListener((ModConfigEvent.Reloading e) -> appliquer(e.getConfig()));
   }

   private static void appliquer(ModConfig c) {
      if (c.getSpec() == SPEC) aTravers = A_TRAVERS.get();
   }
}
