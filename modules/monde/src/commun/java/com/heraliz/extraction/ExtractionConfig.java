package com.heraliz.extraction;

import net.minecraftforge.common.ForgeConfigSpec;

/** Fichier généré : config/heraliz_extraction-common.toml */
public final class ExtractionConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue DURATION_SECONDS;
    public static final ForgeConfigSpec.DoubleValue RADIUS;
    public static final ForgeConfigSpec.IntValue ZONE_LIFETIME_SECONDS;
    public static final ForgeConfigSpec.IntValue FIXED_DURATION_SECONDS;
    public static final ForgeConfigSpec.BooleanValue FIXED_SMOKE;
    public static final ForgeConfigSpec.BooleanValue ROTATION_ENABLED;
    public static final ForgeConfigSpec.IntValue ROTATION_OPEN;
    public static final ForgeConfigSpec.IntValue ROTATION_OPEN_MAX;
    public static final ForgeConfigSpec.IntValue ROTATION_MIN_MINUTES;
    public static final ForgeConfigSpec.IntValue ROTATION_MAX_MINUTES;
    public static final ForgeConfigSpec.IntValue ROTATION_WARN_MINUTES;
    public static final ForgeConfigSpec.BooleanValue ROTATION_ANNOUNCE;
    public static final ForgeConfigSpec.BooleanValue ROTATION_WAVES;
    public static final ForgeConfigSpec.IntValue ROTATION_MIN_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue ROTATION_SPREAD;
    public static final ForgeConfigSpec.BooleanValue USE_HERALIZ_SPAWN;
    public static final ForgeConfigSpec.BooleanValue USE_WORLD_SPAWN;
    public static final ForgeConfigSpec.ConfigValue<String> SPAWN_DIMENSION;
    public static final ForgeConfigSpec.DoubleValue SPAWN_X;
    public static final ForgeConfigSpec.DoubleValue SPAWN_Y;
    public static final ForgeConfigSpec.DoubleValue SPAWN_Z;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("extraction");
        DURATION_SECONDS = b.comment("Temps (en secondes) à tenir dans la zone pour être extrait.")
                .defineInRange("durationSeconds", 90, 5, 3600);
        RADIUS = b.comment("Rayon (en blocs) de la zone d'extraction autour du terminal.",
                        "La zone ne traverse pas les blocs : il faut rester à vue du terminal et à ciel ouvert.")
                .defineInRange("radius", 8.0D, 1.0D, 64.0D);
        ZONE_LIFETIME_SECONDS = b.comment("Durée (en secondes) pendant laquelle la zone reste ouverte après la pose du terminal.",
                        "Pendant ce temps, n'importe quel joueur peut s'extraire en tenant la zone.")
                .defineInRange("zoneLifetimeSeconds", 300, 10, 7200);
        b.pop();

        b.push("zones");
        FIXED_DURATION_SECONDS = b.comment("Temps (en secondes) à tenir dans une zone fixe de la carte créée sans temps",
                        "(/extraction create <nom> [rayon]). Une zone créée avec son propre temps garde le sien.")
                .defineInRange("durationSeconds", 30, 5, 3600);
        FIXED_SMOKE = b.comment("Colonne de fumée au-dessus des zones fixes ouvertes (repère visible de loin).")
                .define("smoke", true);
        b.pop();

        b.push("rotation");
        ROTATION_ENABLED = b.comment("Rotation des zones fixes : seules quelques zones sont ouvertes en même temps, pour un temps,",
                        "puis d'autres prennent le relais. false = les zones en rotation restent fermées",
                        "(ouverture à la main : /extraction open <nom>).")
                .define("enabled", true);
        ROTATION_OPEN = b.comment("Nombre de zones ouvertes en même temps, au moins (si la carte en a moins, elles restent toutes",
                        "ouvertes). Le nombre réel est tiré au hasard entre openAtOnce et openAtOnceMax (à chaque vague avec",
                        "waves = true, sinon à chaque fermeture).")
                .defineInRange("openAtOnce", 4, 1, 32);
        ROTATION_OPEN_MAX = b.comment("Nombre de zones ouvertes en même temps, au plus (voir openAtOnce).")
                .defineInRange("openAtOnceMax", 4, 1, 32);
        ROTATION_MIN_MINUTES = b.comment("Durée d'ouverture d'une zone (minutes), tirée au hasard entre minMinutes et maxMinutes",
                        "(avec waves = true : durée de chaque vague).")
                .defineInRange("minMinutes", 60, 1, 1440);
        ROTATION_MAX_MINUTES = b.defineInRange("maxMinutes", 60, 1, 1440);
        ROTATION_WARN_MINUTES = b.comment("Prévenir les joueurs proches d'une zone tant de minutes avant sa fermeture (0 = jamais).")
                .defineInRange("warnMinutes", 2, 0, 60);
        ROTATION_ANNOUNCE = b.comment("Annoncer dans le chat chaque zone qui ouvre (aux joueurs de la même dimension).")
                .define("announce", true);
        ROTATION_WAVES = b.comment("true = par vagues : les zones de la rotation ouvrent et ferment ensemble, puis TOUTES changent",
                        "(aucune zone de la vague précédente n'est reprise si la carte en a assez). Une zone fermée à la main",
                        "pendant une vague est remplacée jusqu'à la fin de la vague. false = chaque zone a sa propre durée",
                        "et une autre prend le relais à sa fermeture (comme avant la 1.5.0).")
                .define("waves", true);
        ROTATION_MIN_DISTANCE = b.comment("Distance minimale (blocs, à plat) entre deux zones ouvertes en même temps. Si les zones de la",
                        "carte ne le permettent pas, le tirage la baisse peu à peu (noté dans le journal du serveur). 0 = aucune.")
                .defineInRange("minDistance", 800, 0, 100000);
        ROTATION_SPREAD = b.comment("Répartir les zones ouvertes sur la carte : au plus une par quart de la carte (quarts autour du",
                        "centre des zones de la rotation ; plus d'une seulement s'il y a plus de 4 zones ouvertes). Relâché",
                        "si impossible (noté dans le journal).")
                .define("spread", true);
        b.pop();

        b.push("destination");
        USE_HERALIZ_SPAWN = b.comment("true = retour au monde du spawn de HeraliZ (point de réveil de la grotte du terminus, HeraliZ Utils)",
                        "s'il est en place ; sinon (Utils absent, monde du spawn pas encore déposé), les réglages ci-dessous.")
                .define("useHeralizSpawn", true);
        USE_WORLD_SPAWN = b.comment("true = TP au spawn du monde (/setworldspawn). false = coordonnées ci-dessous.")
                .define("useWorldSpawn", true);
        SPAWN_DIMENSION = b.comment("Dimension de destination si useWorldSpawn = false.")
                .define("dimension", "minecraft:overworld");
        SPAWN_X = b.defineInRange("x", 0.5D, -30000000D, 30000000D);
        SPAWN_Y = b.defineInRange("y", 100.0D, -2048D, 2048D);
        SPAWN_Z = b.defineInRange("z", 0.5D, -30000000D, 30000000D);
        b.pop();

        SPEC = b.build();
    }

    private ExtractionConfig() {
    }

    public static int durationTicks() {
        return DURATION_SECONDS.get() * 20;
    }

    public static int zoneLifetimeTicks() {
        return ZONE_LIFETIME_SECONDS.get() * 20;
    }
}
