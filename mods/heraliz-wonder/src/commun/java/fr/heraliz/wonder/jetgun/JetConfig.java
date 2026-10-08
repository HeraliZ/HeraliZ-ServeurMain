package fr.heraliz.wonder.jetgun;

import fr.heraliz.wonder.WonderConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;

/**
 * Réglages du Jet Gun, section {@code [jetgun]} de {@code serverconfig/heraliz_wonder-server.toml}. La section est
 * ajoutée par {@link WonderConfig} (une ligne : {@code JetConfig.definir(b);} avant {@code SPEC = b.build()}) ; si elle
 * manque, chaque lecture rend la valeur par défaut. La chaleur (montée, refroidissement, durée du blocage, purge) est
 * dans les données du gunpack ({@code jetgun_data.json} : « heat » et « script_param »).
 */
public final class JetConfig {
    private JetConfig() {
    }

    @Nullable
    public static ForgeConfigSpec.DoubleValue PORTEE, DEMI_ANGLE, RAYON_BOUCHE, FORCE, DISTANCE_BROYAGE, PV_MAX, DEGATS_COLOSSE, DEGATS_HORS_ZOMBIES;
    @Nullable
    public static ForgeConfigSpec.IntValue CIBLES, BROYAGES_PAR_TICK;

    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Jet Gun : aspiration en cone tant que la detente est tenue, les creatures qui atteignent la bouche sont dechiquetees.",
                "Jamais de blocs casses, jamais un joueur touche. La chaleur se regle dans les donnees du gunpack (jetgun_data.json).").push("jetgun");
        PORTEE = b.comment("Longueur du cone d'aspiration (blocs).").defineInRange("portee", 12.0, 2.0, 40.0);
        DEMI_ANGLE = b.comment("Demi-angle d'ouverture du cone (degres).").defineInRange("demiAngle", 24.0, 2.0, 70.0);
        RAYON_BOUCHE = b.comment("Rayon du cone a la bouche (blocs).").defineInRange("rayonBouche", 1.0, 0.0, 4.0);
        FORCE = b.comment("Vitesse d'aspiration pres de la bouche (blocs par tick) ; 45 % au bout du cone.").defineInRange("force", 0.62, 0.0, 3.0);
        DISTANCE_BROYAGE = b.comment("Distance a la bouche en dessous de laquelle une creature est dechiquetee (blocs).")
                .defineInRange("distanceBroyage", 2.3, 0.5, 6.0);
        CIBLES = b.comment("Creatures aspirees au plus par tireur (les plus proches d'abord).").defineInRange("cibles", 32, 1, 128);
        BROYAGES_PAR_TICK = b.comment("Creatures dechiquetees au plus par tick et par tireur (le cout des morts est etale sur la horde).")
                .defineInRange("broyagesParTick", 4, 1, 32);
        PV_MAX = b.comment("En partie de zombies, toute creature de la partie ayant au plus ces PV max meurt dechiquetee (CoD).",
                "Au-dessus (boss), elle prend degatsColosse toutes les 5 ticks et n'est presque pas aspiree.").defineInRange("pvMaxBroyage", 4000.0, 1.0, 1.0E7);
        DEGATS_COLOSSE = b.comment("Degats sur une creature trop solide, toutes les 5 ticks passes dans la bouche.").defineInRange("degatsColosse", 120.0, 0.0, 1.0E6);
        DEGATS_HORS_ZOMBIES = b.comment("Degats hors partie de zombies (monstres seulement ; jamais les joueurs), toutes les 4 ticks dans la bouche.")
                .defineInRange("degatsHorsZombies", 8.0, 0.0, 10000.0);
        b.pop();
    }

    public static double d(@Nullable ForgeConfigSpec.DoubleValue v, double defaut) {
        return v == null ? defaut : WonderConfig.lire(v);
    }

    public static int i(@Nullable ForgeConfigSpec.IntValue v, int defaut) {
        return v == null ? defaut : WonderConfig.lire(v);
    }

    public static double portee() {
        return d(PORTEE, 12.0);
    }

    public static double demiAngle() {
        return d(DEMI_ANGLE, 24.0);
    }

    public static double rayonBouche() {
        return d(RAYON_BOUCHE, 1.0);
    }

    public static double force() {
        return d(FORCE, 0.62);
    }

    public static double distanceBroyage() {
        return d(DISTANCE_BROYAGE, 2.3);
    }

    public static int cibles() {
        return i(CIBLES, 32);
    }

    public static int broyagesParTick() {
        return i(BROYAGES_PAR_TICK, 4);
    }

    public static double pvMax() {
        return d(PV_MAX, 4000.0);
    }

    public static double degatsColosse() {
        return d(DEGATS_COLOSSE, 120.0);
    }

    public static double degatsHorsZombies() {
        return d(DEGATS_HORS_ZOMBIES, 8.0);
    }
}
