package fr.heralisz.utils.serveur;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Distance de vue du serveur imposée par HeraliZ (heraliz_distances-server.toml, 24/10, pour 50 joueurs) : le
 * server.properties du Ptero n'est jamais mis à jour par la mise en ligne, ce réglage l'est (defaultconfigs). Moins de
 * tronçons gardés et envoyés par joueur (15 : 961 tronçons, 12 : 625) : moins de mémoire, de réseau et de travail pour
 * le serveur, et plus d'images par seconde chez les joueurs (leur jeu ne dessine pas plus loin que le serveur). 0 = celle
 * du server.properties, comme avant. Appliquée au démarrage du serveur.
 */
final class DistanceVue {
    private DistanceVue() {
    }

    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.IntValue VUE, SIMULATION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("distances");
        VUE = b.comment("Distance de vue du serveur, en tronçons (16 blocs). 0 : celle du server.properties.")
                .defineInRange("vue", 8, 0, 32);
        SIMULATION = b.comment("Distance de simulation (créatures, blocs qui vivent), en tronçons. 0 : celle du server.properties.")
                .defineInRange("simulation", 0, 0, 32);
        b.pop();
        SPEC = b.build();
    }

    static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "heraliz_distances-server.toml");
        MinecraftForge.EVENT_BUS.addListener(DistanceVue::demarre);
    }

    private static void demarre(ServerStartedEvent e) {
        MinecraftServer s = e.getServer();
        PlayerList l = s.getPlayerList();
        try {
            int vue = VUE.get(), sim = SIMULATION.get();
            if (vue > 0 && vue != l.getViewDistance()) {
                HeraliszUtils.LOG.info("[utils] distance de vue du serveur : {} -> {} tronçons", l.getViewDistance(), vue);
                l.setViewDistance(vue);
            }
            if (sim > 0 && sim != l.getSimulationDistance()) {
                HeraliszUtils.LOG.info("[utils] distance de simulation du serveur : {} -> {} tronçons", l.getSimulationDistance(), sim);
                l.setSimulationDistance(sim);
            }
        } catch (RuntimeException ex) {
            HeraliszUtils.LOG.warn("[utils] distances du serveur non appliquées : {}", ex.toString());
        }
    }
}
