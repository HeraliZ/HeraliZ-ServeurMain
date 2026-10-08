package fr.heraliz.wonder.wintershowl;

import fr.heraliz.wonder.WonderConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages du Winter's Howl, section {@code [winters_howl]} de {@code serverconfig/heraliz_wonder-server.toml}. Definis
 * par {@link WonderConfig} (un appel a {@link #definir} juste avant {@code SPEC = b.build()}) ; lus a chaque tir par
 * {@link WonderConfig#d} / {@link WonderConfig#i} (surs avant le chargement du fichier).
 */
public final class ConfigGivre {
    private ConfigGivre() {
    }

    public static ForgeConfigSpec.DoubleValue PORTEE, PORTEE_PAP, ANGLE, ANGLE_PAP, BOUCHE, ZONE_GEL, ZONE_GEL_PAP, GAIN_LOIN, GAIN_LOIN_PAP,
            RALENTI_MAX, DUREE_FROID, FONTE, DELAI_ECLAT, DELAI_ECLAT_PAP, PV_MAX_GEL, DEGATS_COLOSSE, RALENTI_COLOSSE, DEGATS_HORS_ZOMBIES;
    public static ForgeConfigSpec.IntValue CIBLES;

    /** Section [winters_howl] : appelee par WonderConfig pendant la construction de sa specification. */
    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Winter's Howl : souffle d'azote liquide en cone court (Pack-a-Punch : Winter's Fury). Ralentit, puis gele ; les creatures",
                "gelees volent en eclats quand on les touche ou apres un delai. Jamais de blocs casses, jamais un joueur touche.").push("winters_howl");
        PORTEE = b.comment("Longueur du cone de givre (blocs).").defineInRange("portee", 11.0, 2.0, 48.0);
        PORTEE_PAP = b.comment("Longueur au Pack-a-Punch niveau 1 (Winter's Fury) ; +1,5 par niveau au-dessus.").defineInRange("porteePap", 15.0, 2.0, 64.0);
        ANGLE = b.comment("Demi-angle d'ouverture du cone (degres) : 16 = cone de 32 degres.").defineInRange("demiAngle", 16.0, 2.0, 80.0);
        ANGLE_PAP = b.comment("Demi-angle au Pack-a-Punch niveau 1 ; +2 par niveau au-dessus.").defineInRange("demiAnglePap", 22.0, 2.0, 80.0);
        BOUCHE = b.comment("Rayon du cone a la bouche de l'arme (blocs).").defineInRange("rayonBouche", 0.9, 0.0, 4.0);
        CIBLES = b.comment("Creatures touchees au plus par tir (les plus proches d'abord).").defineInRange("cibles", 32, 1, 128);
        ZONE_GEL = b.comment("Fraction de la longueur du cone ou le gel est immediat (un tir suffit) ; au-dela, le gel apporte diminue.")
                .defineInRange("zoneGelImmediat", 0.4, 0.0, 1.0);
        ZONE_GEL_PAP = b.comment("Meme chose au Pack-a-Punch (gel plus rapide).").defineInRange("zoneGelImmediatPap", 0.6, 0.0, 1.0);
        GAIN_LOIN = b.comment("Gel apporte par un tir au bout du cone (1 = gele d'un coup) : 0,5 = deux tirs.").defineInRange("gelBout", 0.5, 0.01, 1.0);
        GAIN_LOIN_PAP = b.comment("Gel apporte au bout du cone au Pack-a-Punch.").defineInRange("gelBoutPap", 0.85, 0.01, 1.0);
        RALENTI_MAX = b.comment("Ralentissement d'une creature presque gelee (0,8 = 80 % plus lente), proportionnel au gel accumule.")
                .defineInRange("ralentiMax", 0.8, 0.0, 0.95);
        DUREE_FROID = b.comment("Secondes avant que le gel accumule (sans gel complet) commence a fondre.").defineInRange("dureeFroid", 3.0, 0.0, 30.0);
        FONTE = b.comment("Gel perdu par seconde pendant la fonte.").defineInRange("fonte", 0.35, 0.01, 5.0);
        DELAI_ECLAT = b.comment("Secondes avant qu'une creature gelee vole en eclats d'elle-meme (un coup la brise tout de suite).")
                .defineInRange("delaiEclat", 2.5, 0.2, 20.0);
        DELAI_ECLAT_PAP = b.comment("Meme chose au Pack-a-Punch.").defineInRange("delaiEclatPap", 2.0, 0.2, 20.0);
        PV_MAX_GEL = b.comment("En partie de zombies, une creature de la partie d'au plus ces PV max peut geler (puis mourir en eclats).",
                "Au-dessus (boss), elle ne gele jamais : elle ralentit et prend degatsColosse.").defineInRange("pvMaxGel", 4000.0, 1.0, 1.0E7);
        DEGATS_COLOSSE = b.comment("Degats d'un tir sur une creature trop solide pour geler (x1,5 / x2 / x2,5 au Pack-a-Punch).")
                .defineInRange("degatsColosse", 300.0, 0.0, 1.0E6);
        RALENTI_COLOSSE = b.comment("Ralentissement maximum d'un boss.").defineInRange("ralentiColosse", 0.5, 0.0, 0.95);
        DEGATS_HORS_ZOMBIES = b.comment("Degats de l'eclatement hors partie de zombies (monstres seulement ; jamais les joueurs).")
                .defineInRange("degatsHorsZombies", 40.0, 0.0, 10000.0);
        b.pop();
    }

    // lecture sure (valeur par defaut tant que le fichier n'est pas charge, ou si la section n'a pas ete definie)
    static double d(ForgeConfigSpec.DoubleValue v, double defaut) {
        return v == null ? defaut : WonderConfig.d(v);
    }

    static int i(ForgeConfigSpec.IntValue v, int defaut) {
        return v == null ? defaut : WonderConfig.i(v);
    }
}
