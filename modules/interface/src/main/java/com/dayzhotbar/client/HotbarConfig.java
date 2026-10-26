package com.dayzhotbar.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/dayzhotbar-client.toml, reloaded live when the file is saved. */
public final class HotbarConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue SKEW;
    public static final ForgeConfigSpec.IntValue SPACING;
    public static final ForgeConfigSpec.ConfigValue<String> FRAME_COLOR;
    public static final ForgeConfigSpec.BooleanValue BLOOD;
    public static final ForgeConfigSpec.ConfigValue<String> BLOOD_COLOR;
    public static final ForgeConfigSpec.BooleanValue NUMBERS;
    public static final ForgeConfigSpec.BooleanValue DIM_WHEN_IDLE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("hotbar");
        ENABLED = b.comment("Remplace la barre vanilla par la barre facon DayZ.")
                .define("enabled", true);
        SKEW = b.comment("Inclinaison des cases, en degres (0 = droites).")
                .defineInRange("skew", 12, 0, 25);
        // Was "gap" (default 4) in 1.0.0: renamed so that existing files pick up the new default.
        SPACING = b.comment("Espace entre deux cases, en pixels de l'interface.")
                .defineInRange("spacing", 2, 0, 12);
        FRAME_COLOR = b.comment("Couleur du trait de craie des cases (#RRGGBB) ; la case selectionnee reste blanche.")
                .define("frameColor", "#4A4844", HotbarConfig::isColor);
        BLOOD = b.comment("Taches et gouttes de sang sur le cadre de la case selectionnee.")
                .define("blood", true);
        BLOOD_COLOR = b.comment("Couleur du sang (#RRGGBB).")
                .define("bloodColor", "#8E1111", HotbarConfig::isColor);
        NUMBERS = b.comment("Affiche le numero de chaque case dans son coin.")
                .define("slotNumbers", true);
        DIM_WHEN_IDLE = b.comment("Les cadres s'estompent apres quelques secondes sans changer d'objet, comme dans DayZ.")
                .define("dimWhenIdle", true);
        b.pop();
        SPEC = b.build();
    }

    private HotbarConfig() {
    }

    private static boolean isColor(Object value) {
        return value instanceof String s && s.matches("#?[0-9a-fA-F]{6}");
    }

    static int frameColor() {
        return rgb(FRAME_COLOR);
    }

    static int bloodColor() {
        return rgb(BLOOD_COLOR);
    }

    /** A #RRGGBB value as 0xRRGGBB. */
    private static int rgb(ForgeConfigSpec.ConfigValue<String> value) {
        String s = value.get();
        return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16);
    }
}
