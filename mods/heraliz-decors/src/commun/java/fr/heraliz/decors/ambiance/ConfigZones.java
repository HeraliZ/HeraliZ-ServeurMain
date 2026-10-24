package fr.heraliz.decors.ambiance;

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
        SPEC = b.build();
    }
}
