package fr.heraliz.decors.ambiance;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages serveur des zones dangereuses (1.12.0, heraliz_decors-server.toml) : usure des protections. Lus seulement
 * par le serveur (ZonesServeur), déclarés ici pour être enregistrés par le constructeur du mod.
 */
public final class ConfigZones {
    private ConfigZones() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue USURE;
    public static final ForgeConfigSpec.IntValue SECONDES_GAZ;
    public static final ForgeConfigSpec.IntValue SECONDES_RADIATION;
    public static final ForgeConfigSpec.IntValue SECONDES_FROID;
    /** Objets qui protègent de chaque zone (02/11, audit mods-2 §21 : avant écrits dans ZonesServeur). */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTION_GAZ, PROTECTION_RADIATION, PROTECTION_FROID;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Usure des protections dans les zones dangereuses (gaz, radiation, froid).",
                "La pièce qui protège le joueur de la zone où il se trouve perd 1 point de durabilité toutes les N secondes.",
                "À 0 (barre vide), elle ne protège plus de cette zone (elle protège toujours des balles) : il faut la réparer.",
                "Elle n'est jamais détruite par cette usure.").push("usure");
        USURE = b.comment("false : les protections ne s'usent plus dans les zones (comme avant la 1.12.0).")
                .define("actif", true);
        SECONDES_GAZ = b.comment("Zone de gaz : secondes par point (masques à gaz 180 / 260 points : 21 / 30 min ; Hazmat 300 / 450 : 35 / 52 min).")
                .defineInRange("secondes_gaz", 7, 1, 3600);
        SECONDES_RADIATION = b.comment("Zone de radiation : secondes par point (Hazmat 300 / 450 points : 25 / 37 min).")
                .defineInRange("secondes_radiation", 5, 1, 3600);
        SECONDES_FROID = b.comment("Zone de froid : secondes par point (combinaison contre le froid 300 / 450 points : 25 / 37 min).")
                .defineInRange("secondes_froid", 5, 1, 3600);
        b.pop();
        b.comment("Objets qui protègent de chaque zone : identifiants, * = n'importe quelle suite de lettres. Portés en armure, en",
                "main gauche ou dans un emplacement Curios. Une nouvelle tenue de l'Armurerie s'ajoute ici, sans nouveau jar ;",
                "relu tout seul quand le fichier change.").push("protections");
        PROTECTION_GAZ = b.comment("Zone de gaz.").defineListAllowEmpty("gaz", List.of("heraliz_armory:gas_mask_*",
                "heraliz_armory:hazmat_suit", "heraliz_armory:hazmat_renforce_suit", "heraliz_armory:*respirator*",
                "heraliz_armory:*gasmask*"), o -> o instanceof String);
        PROTECTION_RADIATION = b.comment("Zone de radiation.").defineListAllowEmpty("radiation",
                List.of("heraliz_armory:hazmat_suit", "heraliz_armory:hazmat_renforce_suit"), o -> o instanceof String);
        PROTECTION_FROID = b.comment("Zone de froid.").defineListAllowEmpty("froid",
                List.of("heraliz_armory:arctique_suit", "heraliz_armory:arctique_renforcee_suit"), o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }
}
