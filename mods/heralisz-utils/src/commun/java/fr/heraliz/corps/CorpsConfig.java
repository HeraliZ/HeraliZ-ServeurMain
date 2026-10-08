package fr.heraliz.corps;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reglages. Serveur ({@code serverconfig/heraliz_corps-server.toml} du monde) : regles des corps. Client
 * ({@code config/heraliz_corps-client.toml}) : affichage. Les valeurs lues souvent sont recopiees dans des champs
 * simples au chargement (aucune lecture de config pendant le rendu).
 */
public final class CorpsConfig {
    private CorpsConfig() {
    }

    public static final ForgeConfigSpec SERVEUR_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.BooleanValue ACTIF;
    public static final ForgeConfigSpec.IntValue PROTECTION_S;
    public static final ForgeConfigSpec.BooleanValue PROTECTION_CLAN;
    public static final ForgeConfigSpec.IntValue DISPARITION_MIN;
    public static final ForgeConfigSpec.IntValue DISPARITION_VIDE_S;
    public static final ForgeConfigSpec.IntValue MAX_PAR_CHUNK;
    public static final ForgeConfigSpec.IntValue MAX_PAR_MONDE;
    public static final ForgeConfigSpec.DoubleValue DISTANCE_FOUILLE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS_EXCLUES;

    public static final ForgeConfigSpec.IntValue DISTANCE_RENDU;
    public static final ForgeConfigSpec.IntValue MAX_RENDUS;
    public static final ForgeConfigSpec.IntValue MAX_RENDUS_PAR_CHUNK;
    public static final ForgeConfigSpec.BooleanValue HUD;
    public static final ForgeConfigSpec.BooleanValue MARQUEUR_CLAN;

    /** Dimensions sans corps (copie du reglage, lue a chaque mort). */
    private static volatile Set<String> exclues = Set.of("heralisz_matchs:arenes", "heralisz_matchs:zombies");

    /** Copies client lues au rendu. */
    public static volatile double distanceRenduCarre = 48 * 48;
    public static volatile int maxRendus = 32, maxRendusParChunk = 8;
    public static volatile boolean hud = true, marqueurClan = true;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("corps");
        ACTIF = b.comment("Corps a la mort des joueurs (false = comportement vanilla : objets au sol)").define("actif", true);
        PROTECTION_S = b.comment("Secondes pendant lesquelles seul le mort peut fouiller son corps (0 = personne n'est protege, style DayZ)")
                .defineInRange("protection_secondes", 0, 0, 3600);
        PROTECTION_CLAN = b.comment("Pendant la protection, les membres du clan du mort peuvent aussi fouiller (mod HeraliZ Clans)")
                .define("protection_clan", false);
        DISPARITION_MIN = b.comment("Minutes (de jeu, serveur allume) avant qu'un corps disparaisse avec ce qu'il contient")
                .defineInRange("disparition_minutes", 30, 1, 10080);
        DISPARITION_VIDE_S = b.comment("Secondes avant qu'un corps vide disparaisse").defineInRange("disparition_vide_secondes", 10, 1, 600);
        MAX_PAR_CHUNK = b.comment("Corps au plus par chunk : au-dela, le plus ancien du chunk disparait (filet anti-lag)")
                .defineInRange("max_par_chunk", 16, 1, 256);
        MAX_PAR_MONDE = b.comment("Corps au plus sur le serveur : au-dela, le plus ancien disparait (filet anti-lag)")
                .defineInRange("max_par_monde", 400, 1, 100000);
        DISTANCE_FOUILLE = b.comment("Distance maximale (blocs) pour fouiller un corps").defineInRange("distance_fouille", 5.0, 2.0, 16.0);
        DIMENSIONS_EXCLUES = b.comment("Dimensions ou aucun corps n'apparait (les matchs gerent leurs morts eux-memes)")
                .defineListAllowEmpty("dimensions_exclues", List.of("heralisz_matchs:arenes", "heralisz_matchs:zombies"),
                        o -> o instanceof String);
        b.pop();
        SERVEUR_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("affichage");
        DISTANCE_RENDU = c.comment("Distance (blocs) au-dela de laquelle les corps ne sont plus dessines").defineInRange("distance_rendu", 48, 8, 128);
        MAX_RENDUS = c.comment("Corps dessines au plus en meme temps (les plus proches)").defineInRange("max_rendus", 32, 1, 512);
        MAX_RENDUS_PAR_CHUNK = c.comment("Corps dessines au plus par chunk").defineInRange("max_rendus_par_chunk", 8, 1, 256);
        HUD = c.comment("Petit panneau (nom, cause, minuteur) quand on regarde un corps").define("panneau", true);
        MARQUEUR_CLAN = c.comment("Petit losange a la couleur du clan au-dessus des corps des membres de son clan").define("marqueur_clan", true);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    public static boolean dimensionExclue(String dimension) {
        return exclues.contains(dimension);
    }

    static void surChargement(ModConfigEvent.Loading e) {
        copier(e.getConfig());
    }

    static void surRechargement(ModConfigEvent.Reloading e) {
        copier(e.getConfig());
    }

    private static void copier(ModConfig config) {
        if (config.getSpec() == SERVEUR_SPEC) {
            Set<String> s = new HashSet<>();
            for (String d : DIMENSIONS_EXCLUES.get()) s.add(d.trim());
            exclues = Set.copyOf(s);
        } else if (config.getSpec() == CLIENT_SPEC) {
            double d = DISTANCE_RENDU.get();
            distanceRenduCarre = d * d;
            maxRendus = MAX_RENDUS.get();
            maxRendusParChunk = MAX_RENDUS_PAR_CHUNK.get();
            hud = HUD.get();
            marqueurClan = MARQUEUR_CLAN.get();
        }
    }
}
