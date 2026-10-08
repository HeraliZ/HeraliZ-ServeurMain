package fr.heraliz.wonder.sliquifier;

import fr.heraliz.wonder.WonderConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages du Sliquifier, section {@code [sliquifier]} du fichier {@code serverconfig/heraliz_wonder-server.toml}.
 * {@link #definir} est appele une fois par {@code WonderConfig} pendant la construction de sa spec (une seule ligne a
 * ajouter, voir {@code work/wonder/sliquifier/INTEGRATION.md}). Le tir hors partie de zombies suit le reglage commun
 * {@code general.tirHorsZombies}.
 */
public final class SliqConfig {
    private SliqConfig() {
    }

    public static ForgeConfigSpec.DoubleValue VITESSE, GRAVITE, RAYON, RAYON_PAP, DUREE, DUREE_PAP, VITESSE_GLISSADE, PV_MAX, DEGATS_COLOSSE,
            DEGATS_HORS_ZOMBIES, BOOST_JOUEURS, VITESSE_MAX_JOUEURS;
    public static ForgeConfigSpec.IntValue FLAQUES_MAX, DUREE_GLISSADE, VIE_BOULE;
    public static ForgeConfigSpec.BooleanValue CHAINE, GLISSE_JOUEURS;

    /** Ajoute la section {@code sliquifier} a la spec commune (appele par WonderConfig, avant {@code b.build()}). */
    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Sliquifier : boules de gelee rose, flaques ou les zombies glissent et fondent (Pack-a-Punch : Sliquidator).",
                "Jamais de blocs casses, jamais un joueur blesse ; les joueurs glissent vite sur les flaques.").push("sliquifier");
        VITESSE = b.comment("Vitesse de depart de la boule (blocs par tick ; +6 % par niveau de Pack-a-Punch).").defineInRange("vitesse", 1.55, 0.3, 6.0);
        GRAVITE = b.comment("Gravite de la boule (blocs par tick, chaque tick) : elle decrit une courbe.").defineInRange("gravite", 0.022, 0.0, 0.2);
        VIE_BOULE = b.comment("Duree de vol maximale de la boule (ticks).").defineInRange("vieBoule", 60, 5, 200);
        RAYON = b.comment("Rayon d'une flaque (blocs).").defineInRange("rayonFlaque", 1.5, 0.5, 6.0);
        RAYON_PAP = b.comment("Rayon d'une flaque au Pack-a-Punch niveau 1 (Sliquidator) ; +0,2 par niveau au-dessus.").defineInRange("rayonFlaquePap", 2.2, 0.5, 8.0);
        DUREE = b.comment("Duree d'une flaque (secondes).").defineInRange("dureeFlaque", 12.0, 1.0, 120.0);
        DUREE_PAP = b.comment("Duree d'une flaque au Pack-a-Punch niveau 1 (secondes) ; +3 s par niveau au-dessus.").defineInRange("dureeFlaquePap", 20.0, 1.0, 180.0);
        FLAQUES_MAX = b.comment("Flaques au plus en meme temps par dimension (la plus vieille disparait).").defineInRange("flaquesMax", 24, 1, 64);
        DUREE_GLISSADE = b.comment("Duree de la glissade d'un zombie entre dans une flaque avant qu'il ne fonde (ticks).").defineInRange("dureeGlissade", 16, 2, 100);
        VITESSE_GLISSADE = b.comment("Vitesse de depart de la glissade d'un zombie (blocs par tick).").defineInRange("vitesseGlissade", 0.36, 0.0, 2.0);
        CHAINE = b.comment("Un zombie qui fond laisse une petite flaque (reaction en chaine, comme dans CoD).").define("reactionEnChaine", true);
        PV_MAX = b.comment("En partie de zombies, toute creature de la partie ayant au plus ces PV max fond d'un coup (CoD).",
                "Au-dessus (boss), elle prend degatsColosse et ne glisse pas.").defineInRange("pvMaxLiquefie", 4000.0, 1.0, 1.0E7);
        DEGATS_COLOSSE = b.comment("Degats sur une creature trop solide pour fondre (x1,5 / x2 / x2,5 au Pack-a-Punch).").defineInRange("degatsColosse", 300.0, 0.0, 1.0E6);
        DEGATS_HORS_ZOMBIES = b.comment("Degats hors partie de zombies (monstres seulement ; jamais les joueurs).").defineInRange("degatsHorsZombies", 40.0, 0.0, 10000.0);
        GLISSE_JOUEURS = b.comment("Les joueurs glissent sur les flaques (deplacement rapide, aucun degat).").define("glisseJoueurs", true);
        BOOST_JOUEURS = b.comment("Glisse des joueurs : la vitesse est multipliee par ce facteur a chaque tick sur la gelee (1 = rien).")
                .defineInRange("boostJoueurs", 1.6, 1.0, 2.0);
        VITESSE_MAX_JOUEURS = b.comment("Glisse des joueurs : vitesse horizontale maximale (blocs par tick ; marche ~0,22, sprint ~0,28).")
                .defineInRange("vitesseMaxJoueurs", 0.7, 0.2, 2.0);
        b.pop();
    }

    public static double d(ForgeConfigSpec.DoubleValue v) {
        return v == null ? 0 : WonderConfig.d(v);
    }

    public static int i(ForgeConfigSpec.IntValue v) {
        return v == null ? 0 : WonderConfig.i(v);
    }

    public static boolean b(ForgeConfigSpec.BooleanValue v) {
        return v != null && WonderConfig.lire(v);
    }
}
