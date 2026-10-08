package fr.heraliz.wonder;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages serveur ({@code serverconfig/heraliz_wonder-server.toml}). Lus a chaque tir (valeurs gardees en cache par
 * Forge) ; toute lecture passe par {@link #lire} pour rester sure avant le chargement du fichier.
 */
public final class WonderConfig {
    private WonderConfig() {
    }

    /** Degats de l'eclaboussure du Pistolet a rayons sur le tireur lui-meme. */
    public enum AutoDegats { ZOMBIES, TOUJOURS, JAMAIS }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue TIR_HORS_ZOMBIES;
    public static final ForgeConfigSpec.BooleanValue JOURNAL_PERF;

    public static final ForgeConfigSpec.DoubleValue RAYON_DEGATS, RAYON_DEGATS_BORD, RAYON_RAYON, RAYON_RAYON_PAP, RAYON_AUTO_MAX;
    public static final ForgeConfigSpec.EnumValue<AutoDegats> RAYON_AUTO;

    public static final ForgeConfigSpec.IntValue DG_CIBLES, DG_CIBLES_PAP;
    public static final ForgeConfigSpec.DoubleValue DG_SAUT, DG_SAUT_PAP, DG_PORTEE, DG_DEGATS_HORS_ZOMBIES, DG_PV_MAX_FOUDROYE, DG_DEGATS_COLOSSE,
            DG_RAYON_SOL;

    public static final ForgeConfigSpec.DoubleValue TG_PORTEE, TG_PORTEE_PAP, TG_ANGLE, TG_ANGLE_PAP, TG_BOUCHE, TG_PV_MAX, TG_DEGATS_COLOSSE,
            TG_DEGATS_HORS_ZOMBIES, TG_POUSSEE;
    public static final ForgeConfigSpec.IntValue TG_CIBLES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Regles generales").push("general");
        TIR_HORS_ZOMBIES = b.comment("Autoriser le tir des Wonder Weapons hors d'une partie de zombies (le mode creatif est toujours autorise).",
                "false = ces armes n'existent que dans le mode zombies (D-042).").define("tirHorsZombies", false);
        JOURNAL_PERF = b.comment("Journal du cout serveur des tirs (une ligne toutes les 30 s s'il y a eu des tirs).")
                .define("journalPerf", false);
        b.pop();

        b.comment("Pistolet a rayons (Ray Gun) : eclaboussure verte a l'impact").push("pistolet_a_rayons");
        RAYON_DEGATS = b.comment("Degats de l'eclaboussure au centre (le Pack-a-Punch du mode zombies les multiplie x2/x3/x4).")
                .defineInRange("degatsEclaboussure", 60.0, 0.0, 10000.0);
        RAYON_DEGATS_BORD = b.comment("Degats de l'eclaboussure au bord du rayon.").defineInRange("degatsEclaboussureBord", 20.0, 0.0, 10000.0);
        RAYON_RAYON = b.comment("Rayon de l'eclaboussure (blocs).").defineInRange("rayon", 2.0, 0.5, 8.0);
        RAYON_RAYON_PAP = b.comment("Rayon au Pack-a-Punch niveau 1 (Porter's X2) ; +0,25 par niveau au-dessus.")
                .defineInRange("rayonPap", 3.0, 0.5, 10.0);
        RAYON_AUTO = b.comment("L'eclaboussure blesse le tireur s'il tire a bout portant, comme dans CoD : ZOMBIES (seulement en partie de zombies,",
                "ou le mode zombies plafonne ses propres explosifs a 3 PV), TOUJOURS, JAMAIS.").defineEnum("autoDegats", AutoDegats.ZOMBIES);
        RAYON_AUTO_MAX = b.comment("Degats maximum de l'eclaboussure sur le tireur (au centre).").defineInRange("autoDegatsMax", 6.0, 0.0, 100.0);
        b.pop();

        b.comment("Wunderwaffe DG-2 : foudre en chaine").push("wunderwaffe");
        DG_CIBLES = b.comment("Cibles foudroyees au plus par tir (DG-2).").defineInRange("cibles", 10, 1, 64);
        DG_CIBLES_PAP = b.comment("Cibles au Pack-a-Punch niveau 1 (DG-3 JZ) ; +2 par niveau au-dessus.").defineInRange("ciblesPap", 20, 1, 64);
        DG_SAUT = b.comment("Distance d'un saut d'arc a la cible suivante (blocs).").defineInRange("saut", 8.0, 1.0, 24.0);
        DG_SAUT_PAP = b.comment("Distance d'un saut au Pack-a-Punch niveau 1 ; +0,75 par niveau au-dessus.").defineInRange("sautPap", 10.5, 1.0, 32.0);
        DG_PORTEE = b.comment("Portee du premier eclair (blocs).").defineInRange("portee", 64.0, 8.0, 160.0);
        DG_RAYON_SOL = b.comment("Eclair qui frappe un bloc : il saute quand meme sur une cible a moins de cette distance de l'impact.")
                .defineInRange("rayonImpactSol", 3.5, 0.0, 8.0);
        DG_PV_MAX_FOUDROYE = b.comment("En partie de zombies, toute creature de la partie ayant au plus ces PV max meurt foudroyee d'un coup (CoD).",
                "Au-dessus (boss), elle prend degatsColosse.").defineInRange("pvMaxFoudroye", 4000.0, 1.0, 1.0E7);
        DG_DEGATS_COLOSSE = b.comment("Degats d'un arc sur une creature trop solide pour mourir d'un coup.").defineInRange("degatsColosse", 250.0, 0.0, 1.0E6);
        DG_DEGATS_HORS_ZOMBIES = b.comment("Degats d'un arc hors partie de zombies (monstres seulement ; jamais les joueurs).")
                .defineInRange("degatsHorsZombies", 30.0, 0.0, 10000.0);
        b.pop();

        b.comment("Thundergun : onde de choc en cone (Pack-a-Punch : Zeus Cannon). Jamais de blocs casses, jamais un joueur touche.")
                .push("thundergun");
        TG_PORTEE = b.comment("Longueur du cone de l'onde de choc (blocs).").defineInRange("portee", 20.0, 2.0, 64.0);
        TG_PORTEE_PAP = b.comment("Longueur au Pack-a-Punch niveau 1 (Zeus Cannon) ; +2 par niveau au-dessus.").defineInRange("porteePap", 26.0, 2.0, 80.0);
        TG_ANGLE = b.comment("Demi-angle d'ouverture du cone (degres) : 22,5 = cone de 45 degres.").defineInRange("demiAngle", 22.5, 2.0, 80.0);
        TG_ANGLE_PAP = b.comment("Demi-angle au Pack-a-Punch niveau 1 ; +2,5 par niveau au-dessus.").defineInRange("demiAnglePap", 30.0, 2.0, 80.0);
        TG_BOUCHE = b.comment("Rayon du cone a la bouche de l'arme (blocs) : les creatures collees au tireur sont aussi soufflees.")
                .defineInRange("rayonBouche", 1.3, 0.0, 6.0);
        TG_CIBLES = b.comment("Creatures soufflees au plus par tir (les plus proches d'abord).").defineInRange("cibles", 48, 1, 256);
        TG_PV_MAX = b.comment("En partie de zombies, toute creature de la partie ayant au plus ces PV max meurt soufflee (CoD).",
                "Au-dessus (boss), elle prend degatsColosse et un recul reduit.").defineInRange("pvMaxSouffle", 4000.0, 1.0, 1.0E7);
        TG_DEGATS_COLOSSE = b.comment("Degats d'un tir sur une creature trop solide pour mourir d'un coup (x1,5 / x2 / x2,5 au Pack-a-Punch).")
                .defineInRange("degatsColosse", 600.0, 0.0, 1.0E6);
        TG_DEGATS_HORS_ZOMBIES = b.comment("Degats hors partie de zombies (monstres seulement ; jamais les joueurs).")
                .defineInRange("degatsHorsZombies", 40.0, 0.0, 10000.0);
        TG_POUSSEE = b.comment("Force du souffle (vitesse donnee aux creatures pres de la bouche, blocs par tick ; diminue avec la distance).")
                .defineInRange("poussee", 1.5, 0.0, 8.0);
        b.pop();
        // Wonder Weapons 1.1.0 : une section chacune, definie par son paquet
        fr.heraliz.wonder.jetgun.JetConfig.definir(b);                 // [jetgun]
        fr.heraliz.wonder.blundergat.BlunderConfig.definir(b);         // [blundergat]
        fr.heraliz.wonder.wintershowl.ConfigGivre.definir(b);          // [winters_howl]
        fr.heraliz.wonder.sliquifier.SliqConfig.definir(b);            // [sliquifier]
        fr.heraliz.wonder.paralyzer.ConfigParalyseur.definir(b);       // [paralyzer]
        SPEC = b.build();
    }

    /** Valeur du reglage, ou sa valeur par defaut si le fichier n'est pas encore charge. */
    public static <T> T lire(ForgeConfigSpec.ConfigValue<T> v) {
        try {
            return v.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return v.getDefault();
        }
    }

    public static double d(ForgeConfigSpec.DoubleValue v) {
        return lire(v);
    }

    public static int i(ForgeConfigSpec.IntValue v) {
        return lire(v);
    }
}
