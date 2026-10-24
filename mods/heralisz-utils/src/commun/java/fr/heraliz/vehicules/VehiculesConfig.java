package fr.heraliz.vehicules;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Réglages serveur ({@code serverconfig/heraliz_vehicules-server.toml} du monde). {@code coffre_retire} et
 * {@code pv_pourcent} sont appliqués par la partie serveur ({@code CoffreRetire}, {@code PointsDeVie}) ; la section
 * {@code physique} par {@code Stabilite} (mixins serveur sur le moteur de Limitless Vehicle).
 */
public final class VehiculesConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS_INTERDITES;
    public static final ForgeConfigSpec.BooleanValue REFUS_EN_COMBAT;
    public static final ForgeConfigSpec.BooleanValue PROTEGER_BLOCS;
    public static final ForgeConfigSpec.BooleanValue COFFRE_RETIRE;
    public static final ForgeConfigSpec.IntValue PV_POURCENT;
    public static final ForgeConfigSpec.BooleanValue PHYSIQUE_STABLE;
    public static final ForgeConfigSpec.DoubleValue INERTIE;
    public static final ForgeConfigSpec.DoubleValue BASCULE_MAX;
    public static final ForgeConfigSpec.DoubleValue ROTATION_MAX;
    public static final ForgeConfigSpec.DoubleValue MONTEE_MAX;
    public static final ForgeConfigSpec.DoubleValue PETITE_MARCHE;
    public static final ForgeConfigSpec.DoubleValue ADHERENCE;
    public static final ForgeConfigSpec.DoubleValue BRAQUAGE_MIN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("regles");
        DIMENSIONS_INTERDITES = b.comment("Dimensions sans aucun vehicule (anciens bunkers des bases) :",
                        "on ne peut pas y poser de vehicule et un vehicule qui y arrive est retire (rendu en objet).")
                .defineListAllowEmpty("dimensions_interdites", List.of("heralisz_bases:bunkers"), o -> o instanceof String);
        REFUS_EN_COMBAT = b.comment("Refuser de sortir (poser) un vehicule pendant le tag de combat (cle heralisz:combat_jusqua, tag de combat de HeraliZ Utils).")
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
        b.push("physique");
        PHYSIQUE_STABLE = b.comment("Vehicules a roues plus stables et plus maniables (moteur de Limitless Vehicle corrige par HeraliZ Utils).",
                        "A false : physique d'origine du moteur (bonds et retournements au moindre obstacle).")
                .define("stable", true);
        INERTIE = b.comment("Effet d'un choc ou d'un freinage sur l'equilibre du vehicule (1 = moteur d'origine : le centre de gravite",
                        "part de plusieurs blocs vers l'avant au moindre choc, d'ou les retournements).")
                .defineInRange("inertie", 0.25, 0.0, 1.0);
        BASCULE_MAX = b.comment("Deplacement maximal du centre de gravite du a un choc, en part de la plus petite dimension du vehicule",
                        "(0.2 : un choc seul ne retourne jamais un vehicule pose a plat ; une pente trop forte le peut toujours).")
                .defineInRange("bascule_max", 0.2, 0.0, 10.0);
        ROTATION_MAX = b.comment("Vitesse maximale de bascule, en radians par tick (moteur d'origine : 0.3, soit 17 degres par tick).")
                .defineInRange("rotation_max", 0.12, 0.01, 1.0);
        MONTEE_MAX = b.comment("Vitesse verticale maximale (blocs par tick) que les contacts avec le sol peuvent donner au vehicule :",
                        "plus de bonds sur les bosses (moteur d'origine : jusqu'a 0.1 par tick et plus).")
                .defineInRange("montee_max", 0.06, 0.0, 1.0);
        PETITE_MARCHE = b.comment("Hauteur (blocs) d'un obstacle franchi sans freiner : dalle, couche de neige, chemin (0 : le vehicule",
                        "s'arrete net contre tout ce qui depasse sa garde au sol, comme a l'origine).")
                .defineInRange("petite_marche", 0.55, 0.0, 1.0);
        ADHERENCE = b.comment("Adherence minimale des pneus a pleine vitesse : part de la glissade corrigee a chaque tick (moteur",
                        "d'origine : 0.01, le vehicule glisse comme sur de la glace dans les virages rapides).")
                .defineInRange("adherence", 0.2, 0.0, 1.0);
        BRAQUAGE_MIN = b.comment("Vitesse minimale de braquage du volant (degres par tick ; moteur d'origine : 0.1 pour le camion).")
                .defineInRange("braquage_min", 0.25, 0.0, 2.0);
        b.pop();
        SPEC = b.build();
    }

    private VehiculesConfig() {
    }
}
