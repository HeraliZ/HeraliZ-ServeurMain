package fr.heraliz.wonder.blundergat;

import fr.heraliz.wonder.WonderConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages du Blundergat et de l'Acid Gat, section {@code [blundergat]} de {@code heraliz_wonder-server.toml}. Les cles sont
 * declarees par {@link #definir} dans le constructeur de {@link WonderConfig} (une ligne, juste avant {@code b.build()}).
 * La regle « tir hors partie de zombies » est celle de toutes les Wonder Weapons ({@code general.tirHorsZombies}).
 */
public final class BlunderConfig {
    private BlunderConfig() {
    }

    public static ForgeConfigSpec.DoubleValue ACIDE_DEGATS, ACIDE_DEGATS_HORS, ACIDE_RAYON, ACIDE_RAYON_PAP, ACIDE_VITESSE, ACIDE_ECART;
    public static ForgeConfigSpec.IntValue ACIDE_DELAI, ACIDE_VOL_MAX, ACIDE_CIBLES;

    /** Declare la section {@code blundergat} dans le constructeur des reglages du mod. */
    public static void definir(ForgeConfigSpec.Builder b) {
        b.comment("Blundergat (gerbe d'obus : degats dans le gunpack, balles TaCZ) et Acid Gat (cartouches d'acide qui collent puis explosent).",
                "Jamais de blocs casses, jamais un joueur touche. Tir hors partie de zombies : general.tirHorsZombies.").push("blundergat");
        ACIDE_DEGATS = b.comment("Degats d'une cartouche d'acide au centre de l'explosion, en partie de zombies (3 cartouches par tir ;",
                "le Pack-a-Punch du mode zombies les multiplie : x2/x3/x4). 60 % de moins au bord du rayon.").defineInRange("degatsAcide", 260.0, 0.0, 100000.0);
        ACIDE_DEGATS_HORS = b.comment("Degats d'une cartouche hors partie de zombies (monstres seulement ; x1,5 / x2 / x2,5 au Pack-a-Punch).")
                .defineInRange("degatsAcideHorsZombies", 60.0, 0.0, 10000.0);
        ACIDE_RAYON = b.comment("Rayon d'une explosion d'acide (blocs).").defineInRange("rayonAcide", 3.0, 0.5, 10.0);
        ACIDE_RAYON_PAP = b.comment("Rayon au Pack-a-Punch niveau 1 (Vitriolic Withering) ; +0,25 par niveau au-dessus.")
                .defineInRange("rayonAcidePap", 4.0, 0.5, 12.0);
        ACIDE_DELAI = b.comment("Delai entre le moment ou la cartouche colle et son explosion (ticks).").defineInRange("delaiAcide", 30, 2, 200);
        ACIDE_VOL_MAX = b.comment("Duree de vol maximale d'une cartouche qui ne touche rien : elle explose en l'air (ticks).")
                .defineInRange("volMaxAcide", 50, 5, 200);
        ACIDE_VITESSE = b.comment("Vitesse de lancement des cartouches (blocs par tick).").defineInRange("vitesseAcide", 1.7, 0.3, 5.0);
        ACIDE_ECART = b.comment("Ecart horizontal entre les 3 cartouches d'un tir (degres).").defineInRange("ecartAcide", 3.5, 0.0, 30.0);
        ACIDE_CIBLES = b.comment("Creatures touchees au plus par explosion (les plus proches d'abord).").defineInRange("ciblesAcide", 24, 1, 128);
        b.pop();
    }

    // valeurs lues (defauts si la section n'a pas ete declaree : le mod marche quand meme)
    public static double degatsAcide() {
        return d(ACIDE_DEGATS, 260.0);
    }

    public static double degatsAcideHorsZombies() {
        return d(ACIDE_DEGATS_HORS, 60.0);
    }

    public static double rayonAcide(int pap) {
        return pap <= 0 ? d(ACIDE_RAYON, 3.0) : d(ACIDE_RAYON_PAP, 4.0) + 0.25 * (pap - 1);
    }

    public static int delaiAcide() {
        return i(ACIDE_DELAI, 30);
    }

    public static int volMaxAcide() {
        return i(ACIDE_VOL_MAX, 50);
    }

    public static double vitesseAcide() {
        return d(ACIDE_VITESSE, 1.7);
    }

    public static double ecartAcide() {
        return d(ACIDE_ECART, 3.5);
    }

    public static int ciblesAcide() {
        return i(ACIDE_CIBLES, 24);
    }

    private static double d(ForgeConfigSpec.DoubleValue v, double def) {
        return v == null ? def : WonderConfig.d(v);
    }

    private static int i(ForgeConfigSpec.IntValue v, int def) {
        return v == null ? def : WonderConfig.i(v);
    }
}
