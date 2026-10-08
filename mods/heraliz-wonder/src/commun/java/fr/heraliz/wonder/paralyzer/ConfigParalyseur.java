package fr.heraliz.wonder.paralyzer;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages serveur du Paralyzer (section {@code [paralyzer]} de {@code heraliz_wonder-server.toml}). Declares par
 * {@link #definir}, appele une fois depuis {@code WonderConfig} juste avant {@code b.build()} (une seule ligne a ajouter au
 * fichier partage). Lus par {@link fr.heraliz.wonder.WonderConfig#lire} (surs avant le chargement du fichier).
 */
public final class ConfigParalyseur {
    private ConfigParalyseur() {
    }

    public static ForgeConfigSpec.DoubleValue PORTEE, PORTEE_PAP, RAYON, RAYON_PAP, PV_MAX, DEGATS_COLOSSE, DEGATS_HORS_ZOMBIES, HAUTEUR_FLOTTE,
            SOL_MAX, ANGLE_SOL;
    public static ForgeConfigSpec.IntValue IMPULSIONS_GEL, IMPULSIONS_GEL_PAP, IMPULSIONS_POUSSIERE, IMPULSIONS_POUSSIERE_PAP, CIBLES, CIBLES_PAP,
            DEGEL;
    public static ForgeConfigSpec.BooleanValue FLOTTER;

    /** Ajoute la section au constructeur de reglages du mod (appele avant {@code build()}). */
    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Paralyzer : rayon continu qui fige puis reduit en poussiere (Pack-a-Punch : Petrifier). Jamais de blocs casses,",
                "jamais un joueur touche ; hors partie de zombies, seulement en creatif ou avec tirHorsZombies.").push("paralyzer");
        PORTEE = b.comment("Longueur du rayon (blocs) ; il s'arrete au premier bloc plein.").defineInRange("portee", 24.0, 4.0, 64.0);
        PORTEE_PAP = b.comment("Longueur au Pack-a-Punch niveau 1 (Petrifier) ; +2 par niveau au-dessus.").defineInRange("porteePap", 30.0, 4.0, 80.0);
        RAYON = b.comment("Rayon du faisceau (blocs) : une creature dont le corps passe a moins de cette distance de l'axe est touchee.")
                .defineInRange("rayonFaisceau", 0.6, 0.1, 3.0);
        RAYON_PAP = b.comment("Rayon du faisceau au Pack-a-Punch niveau 1 ; +0,1 par niveau au-dessus.").defineInRange("rayonFaisceauPap", 0.85, 0.1, 3.0);
        IMPULSIONS_GEL = b.comment("Impulsions du rayon (10 par seconde) avant qu'une creature se fige.").defineInRange("impulsionsGel", 3, 1, 100);
        IMPULSIONS_GEL_PAP = b.comment("Idem au Pack-a-Punch (Petrifier : paralysie plus rapide).").defineInRange("impulsionsGelPap", 2, 1, 100);
        IMPULSIONS_POUSSIERE = b.comment("Impulsions avant qu'une creature figee tombe en poussiere (en partie de zombies).")
                .defineInRange("impulsionsPoussiere", 10, 1, 200);
        IMPULSIONS_POUSSIERE_PAP = b.comment("Idem au Pack-a-Punch niveau 1 ; -1 par niveau au-dessus (au moins impulsionsGelPap + 1).")
                .defineInRange("impulsionsPoussierePap", 6, 1, 200);
        CIBLES = b.comment("Creatures touchees au plus par impulsion (les plus proches d'abord).").defineInRange("cibles", 12, 1, 64);
        CIBLES_PAP = b.comment("Idem au Pack-a-Punch.").defineInRange("ciblesPap", 20, 1, 64);
        PV_MAX = b.comment("En partie de zombies, toute creature de la partie ayant au plus ces PV max est figee puis reduite en poussiere.",
                "Au-dessus (boss) : ralentie, et prend degatsColosse a chaque impulsion.").defineInRange("pvMaxPoussiere", 4000.0, 1.0, 1.0E7);
        DEGATS_COLOSSE = b.comment("Degats d'une impulsion sur un boss (x1,5 / x2 / x2,5 au Pack-a-Punch).").defineInRange("degatsColosse", 30.0, 0.0, 1.0E6);
        DEGATS_HORS_ZOMBIES = b.comment("Degats d'une impulsion sur un monstre fige, hors partie de zombies (jamais les joueurs).")
                .defineInRange("degatsHorsZombies", 6.0, 0.0, 10000.0);
        DEGEL = b.comment("Une creature figee que le rayon ne touche plus se remet a bouger apres ce nombre de ticks.").defineInRange("degelTicks", 30, 1, 1200);
        FLOTTER = b.comment("Viser le sol sous ses pieds en tirant fait flotter le joueur (limite : seulement tant que le rayon touche le sol",
                "a moins de solMax blocs ; aucun degat de chute ensuite).").define("flotter", true);
        HAUTEUR_FLOTTE = b.comment("Hauteur de flottement au-dessus du sol (blocs).").defineInRange("hauteurFlotte", 2.2, 0.5, 4.0);
        SOL_MAX = b.comment("Distance maximale du sol sous les pieds pour flotter (blocs).").defineInRange("solMax", 4.0, 1.0, 8.0);
        ANGLE_SOL = b.comment("Inclinaison minimale du regard vers le bas pour flotter (degres).").defineInRange("angleSol", 50.0, 10.0, 89.0);
        b.pop();
    }
}
