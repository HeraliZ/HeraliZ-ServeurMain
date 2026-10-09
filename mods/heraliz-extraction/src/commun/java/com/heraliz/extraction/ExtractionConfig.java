package com.heraliz.extraction;

import net.minecraftforge.common.ForgeConfigSpec;

/** Fichier généré : config/heraliz_extraction-common.toml */
public final class ExtractionConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue DURATION_SECONDS;
    public static final ForgeConfigSpec.DoubleValue RADIUS;
    public static final ForgeConfigSpec.IntValue ZONE_LIFETIME_SECONDS;
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

        b.push("destination");
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
