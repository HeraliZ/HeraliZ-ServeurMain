package fr.heralisz.utils.serveur;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

/**
 * Distance de vue du serveur imposée par HeraliZ (heraliz_distances-server.toml, 24/10, pour 50 joueurs) : le
 * server.properties du Ptero n'est jamais mis à jour par la mise en ligne, ce réglage l'est (defaultconfigs). Moins de
 * tronçons gardés et envoyés par joueur (15 : 961 tronçons, 12 : 625) : moins de mémoire, de réseau et de travail pour
 * le serveur, et plus d'images par seconde chez les joueurs (leur jeu ne dessine pas plus loin que le serveur). 0 = celle
 * du server.properties, comme avant. Appliquée au démarrage du serveur.
 * <p>01/11 (choix du propriétaire) : une vue plus courte pour certains mondes seulement ({@code vues_par_monde}, par défaut
 * le tutoriel des bases, {@code crac:tuto=4}) : chaque joueur y est seul sur sa parcelle, mais Minecraft chargeait autour
 * de lui autant de terrain qu'ailleurs (banc du 01/11 : 4 ms par tick et ~11 700 tronçons pour 15 joueurs). Posée après la
 * vue générale, et à l'arrivée d'un monde chargé plus tard. La carte, le spawn et les bases gardent la vue générale.
 */
final class DistanceVue {
    private DistanceVue() {
    }

    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.IntValue VUE, SIMULATION;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> VUES_PAR_MONDE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("distances");
        VUE = b.comment("Distance de vue du serveur, en tronçons (16 blocs). 0 : celle du server.properties.")
                .defineInRange("vue", 8, 0, 32);
        SIMULATION = b.comment("Distance de simulation (créatures, blocs qui vivent), en tronçons. 0 : celle du server.properties.")
                .defineInRange("simulation", 0, 0, 32);
        VUES_PAR_MONDE = b.comment("Vue plus courte pour certains mondes seulement, « monde=tronçons » (2 à 32). Liste vide : la vue",
                        "générale partout.")
                .defineListAllowEmpty(List.of("vues_par_monde"), () -> List.of("crac:tuto=4"), o -> o instanceof String t && t.contains("="));
        b.pop();
        SPEC = b.build();
    }

    static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "heraliz_distances-server.toml");
        MinecraftForge.EVENT_BUS.addListener(DistanceVue::demarre);
        MinecraftForge.EVENT_BUS.addListener(DistanceVue::mondeCharge);
    }

    private static boolean pret;

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
            for (ServerLevel lv : s.getAllLevels()) {
                parMonde(lv);
            }
            pret = true;
        } catch (RuntimeException ex) {
            HeraliszUtils.LOG.warn("[utils] distances du serveur non appliquées : {}", ex.toString());
        }
    }

    /** Monde chargé après le démarrage : sa vue propre, s'il en a une (avant, la fin du démarrage s'en charge). */
    private static void mondeCharge(LevelEvent.Load e) {
        if (pret && e.getLevel() instanceof ServerLevel lv) {
            try {
                parMonde(lv);
            } catch (RuntimeException ex) {
                HeraliszUtils.LOG.warn("[utils] distance de vue de {} non appliquée : {}", lv.dimension().location(), ex.toString());
            }
        }
    }

    /** Vue propre de ce monde ({@code vues_par_monde}), rien sinon. */
    private static void parMonde(ServerLevel lv) {
        ResourceLocation id = lv.dimension().location();
        for (String ligne : VUES_PAR_MONDE.get()) {
            int i = ligne.lastIndexOf('=');
            if (i <= 0 || !id.toString().equals(ligne.substring(0, i).trim())) continue;
            int vue;
            try {
                vue = Math.max(2, Math.min(32, Integer.parseInt(ligne.substring(i + 1).trim())));
            } catch (NumberFormatException ex) {
                HeraliszUtils.LOG.warn("[utils] vues_par_monde : « {} » illisible", ligne);
                return;
            }
            lv.getChunkSource().setViewDistance(vue);
            HeraliszUtils.LOG.info("[utils] distance de vue de {} : {} tronçons", id, vue);
            return;
        }
    }
}
