package fr.heraliz.vehicules;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Réglages serveur ({@code serverconfig/heraliz_vehicules-server.toml} du monde). {@code coffre_retire} et
 * {@code pv_pourcent} sont appliqués par la partie serveur ({@code CoffreRetire}, {@code PointsDeVie}).
 */
public final class VehiculesConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS_INTERDITES;
    public static final ForgeConfigSpec.BooleanValue REFUS_EN_COMBAT;
    public static final ForgeConfigSpec.BooleanValue PROTEGER_BLOCS;
    public static final ForgeConfigSpec.BooleanValue COFFRE_RETIRE;
    public static final ForgeConfigSpec.IntValue PV_POURCENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("regles");
        DIMENSIONS_INTERDITES = b.comment("Dimensions sans aucun vehicule (matchs ClashWar/Duels, mode Zombies, bunkers des bases) :",
                        "on ne peut pas y poser de vehicule et un vehicule qui y arrive est retire (rendu en objet).")
                .defineListAllowEmpty("dimensions_interdites",
                        List.of("heralisz_matchs:arenes", "heralisz_matchs:zombies", "heralisz_bases:bunkers"), o -> o instanceof String);
        REFUS_EN_COMBAT = b.comment("Refuser de sortir (poser) un vehicule pendant le tag de combat (cle heralisz:combat_jusqua de HeraliZ Matchs).")
                .define("refus_en_combat", true);
        PROTEGER_BLOCS = b.comment("Les explosions de vehicules ne cassent aucun bloc (bases, bunkers) : force canDestroyBlock et",
                        "explosionDropBlock a false dans la config de Limitless Vehicle.")
                .define("proteger_blocs", true);
        COFFRE_RETIRE = b.comment("Les vehicules n'ont plus de coffre : impossible de l'ouvrir (a bord ou sur une epave). Les objets deja",
                        "ranges dans un vehicule pose sont poses au sol a cote de lui (sans limite de temps) quand il se charge.")
                .define("coffre_retire", true);
        PV_POURCENT = b.comment("Points de vie max des vehicules, en pour cent de ceux de Limitless Vehicle (20 = divises par 5 :",
                        "moto 20, buggy et fourgon 60, camion 100). Les vehicules deja poses passent au nouveau maximum en gardant",
                        "leur pourcentage de vie.")
                .defineInRange("pv_pourcent", 20, 1, 1000);
        b.pop();
        SPEC = b.build();
    }

    private VehiculesConfig() {
    }
}
