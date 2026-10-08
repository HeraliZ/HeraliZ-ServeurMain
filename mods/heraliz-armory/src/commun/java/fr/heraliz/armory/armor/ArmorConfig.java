package fr.heraliz.armory.armor;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Configuration serveur (config/heraliz_armory-common.toml). */
public final class ArmorConfig {
    private ArmorConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue PROTECTION_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BULLET_DAMAGE_TYPES;
    public static final ForgeConfigSpec.DoubleValue EXPLOSION_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GAS_EFFECTS;
    /** 0.9.0 : slots Curios de vetements bloques par une combinaison (le sac a dos « back » reste libre). */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SUIT_BLOCKED_SLOTS;
    /** 0.9.0 : tenue de plongee (reprise du mod heraliz_plongee du proprietaire). */
    public static final ForgeConfigSpec.IntValue TANK_SECONDS;
    public static final ForgeConfigSpec.IntValue REFILL_SECONDS;
    public static final ForgeConfigSpec.DoubleValue SWIM_BOOST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("protection");
        PROTECTION_MULTIPLIER = b.comment("Multiplicateur des protections des pieces (balles, corps a corps, morsure, radiation) :",
                        "1.0 = valeurs du catalogue (plafond commun 75 %, radiation 100 %), 0 = desactive")
                .defineInRange("multiplier", 1.0, 0.0, 2.0);
        BULLET_DAMAGE_TYPES = b.comment("Types de degats traites comme des balles, en plus des projectiles et des balles TaCZ (tag tacz:bullets)")
                .defineListAllowEmpty("bullet_damage_types", List.of(), o -> o instanceof String);
        EXPLOSION_MULTIPLIER = b.comment("Multiplicateur de la protection contre les explosions (0 = desactive)")
                .defineInRange("explosion_multiplier", 1.0, 0.0, 2.0);
        GAS_EFFECTS = b.comment("Effets bloques par un masque filtrant ou une combinaison Hazmat, ex. \"minecraft:poison\"")
                .defineListAllowEmpty("gas_effects", List.of("minecraft:poison"), o -> o instanceof String);
        b.pop();
        b.push("combinaisons");
        SUIT_BLOCKED_SLOTS = b.comment("Slots Curios de vetements : une combinaison ne s'enfile que s'ils sont vides, et rien n'y entre tant",
                        "qu'elle est portee (avec les 4 slots d'armure). Le sac a dos (back) n'y est pas : il reste autorise.")
                .defineListAllowEmpty("slots_bloques", List.of("head", "mask", "face", "belt", "hands", "knees", "body", "bracelet",
                        "ear_protection"), o -> o instanceof String);
        b.pop();
        b.push("plongee");
        TANK_SECONDS = b.comment("Reserve d'air de la bouteille de la tenue de plongee, en secondes passees la tete sous l'eau")
                .defineInRange("tank_seconds", 250, 10, 3600);
        REFILL_SECONDS = b.comment("Temps de recharge complete de la bouteille quand la tete sort de l'eau (0 = immediat)")
                .defineInRange("refill_seconds", 5, 0, 600);
        SWIM_BOOST = b.comment("Bonus de vitesse de nage des palmes (0.8 = +80 %)")
                .defineInRange("swim_boost", 0.8, 0.0, 3.0);
        b.pop();
        SPEC = b.build();
    }
}
