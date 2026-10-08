package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Atlas des conteneurs T2 (maison, ferme) et T3 (police, chasse) : textures/block/loot_hd_civil.png, peint par
 * tools/java/PaintCivil. T3 : nylon noir, bande bleue, lettrage POLICE, camouflage, noyer ; T2 : carton, pin, émail,
 * peinture verte, jute, osier, cuir, plastiques. Zones en pixels d'un atlas de 1024.
 */
public final class AtlasCivil {
    private AtlasCivil() {
    }

    public static final String TEXTURE = "loot_hd_civil";

    public static final int[] R_CORDURA = {0, 0, 256, 256};
    public static final int[] R_CAMO = {256, 0, 256, 256};
    public static final int[] R_CARTON = {512, 0, 256, 256};
    public static final int[] R_WALNUT = {768, 0, 256, 256};
    public static final int[] R_PINE = {0, 256, 256, 256};
    public static final int[] R_ENAMEL = {256, 256, 256, 256};
    public static final int[] R_PAINT_GREEN = {512, 256, 256, 256};
    public static final int[] R_BURLAP = {768, 256, 256, 256};
    public static final int[] R_STEEL = {0, 512, 128, 128};
    public static final int[] R_CHROME = {128, 512, 128, 128};
    public static final int[] R_RUBBER = {256, 512, 128, 128};
    public static final int[] R_DARK = {384, 512, 128, 128};
    public static final int[] R_GALVA = {512, 512, 128, 128};
    public static final int[] R_PLASTIC_GREY = {640, 512, 128, 128};
    public static final int[] R_PLASTIC_BLUE = {768, 512, 128, 128};
    public static final int[] R_PLASTIC_GREEN = {896, 512, 128, 128};
    public static final int[] R_WICKER = {0, 640, 128, 128};
    public static final int[] R_LEATHER = {128, 640, 128, 128};
    public static final int[] R_FABRIC_RED = {256, 640, 128, 128};
    public static final int[] R_PAINT_RED = {384, 640, 128, 128};
    public static final int[] R_POLICE_BLUE = {512, 640, 128, 128};
    public static final int[] R_CHECK = {640, 640, 128, 128};
    public static final int[] R_ORANGE = {768, 640, 128, 128};
    public static final int[] R_GLASS = {896, 640, 128, 128};
    public static final int[] R_WHITE = {0, 768, 64, 64};
    public static final int[] R_YELLOW = {64, 768, 64, 64};
    public static final int[] R_BRASS = {128, 768, 64, 64};
    public static final int[] R_INNER = {192, 768, 64, 64};
    public static final int[] R_APPLE = {256, 768, 64, 64};
    public static final int[] R_BOTTLE_GREEN = {320, 768, 64, 64};
    public static final int[] R_BOTTLE_BROWN = {384, 768, 64, 64};
    public static final int[] R_MILK = {448, 768, 64, 64};
    public static final int[] R_TAPE = {512, 768, 64, 64};
    public static final int[] R_BONE = {576, 768, 64, 64};
    public static final int[] R_LABEL = {640, 768, 128, 64};
    public static final int[] R_KNOB = {768, 768, 64, 64};
    public static final int[] R_BLUE_BOX = {832, 768, 64, 64};
    public static final int[] R_RED_BOX = {896, 768, 64, 64};
    public static final int[] R_GREY_BOX = {960, 768, 64, 64};
    public static final int[] R_S_POLICE = {0, 832, 256, 64};
    public static final int[] R_S_PREUVES = {256, 832, 256, 64};
    public static final int[] R_S_FRAGILE = {512, 832, 256, 64};
    public static final int[] R_S_COURRIER = {768, 832, 256, 64};
    public static final int[] R_S_POMMES = {0, 896, 256, 64};
    public static final int[] R_S_GRAIN = {256, 896, 256, 64};
    public static final int[] R_S_SPRINT = {512, 896, 256, 64};
    public static final int[] R_S_LAIT = {768, 896, 256, 64};
    public static final int[] R_S_CHASSE = {0, 960, 256, 64};
    public static final int[] R_S_OUTILS = {256, 960, 256, 64};

    public static final Mat CORDURA = tri(R_CORDURA, 12);
    public static final Mat CAMO = tri(R_CAMO, 14);
    public static final Mat CARTON = tri(R_CARTON, 16);
    public static final Mat WALNUT = tri(R_WALNUT, 16);
    public static final Mat PINE = tri(R_PINE, 16);
    public static final Mat ENAMEL = tri(R_ENAMEL, 16);
    public static final Mat PAINT_GREEN = tri(R_PAINT_GREEN, 16);
    public static final Mat BURLAP = tri(R_BURLAP, 10);
    public static final Mat STEEL = tri(R_STEEL, 10);
    public static final Mat CHROME = tri(R_CHROME, 8);
    public static final Mat RUBBER = tri(R_RUBBER, 6);
    public static final Mat DARK = tri(R_DARK, 6);
    public static final Mat GALVA = tri(R_GALVA, 10);
    public static final Mat PLASTIC_GREY = tri(R_PLASTIC_GREY, 10);
    public static final Mat PLASTIC_BLUE = tri(R_PLASTIC_BLUE, 10);
    public static final Mat PLASTIC_GREEN = tri(R_PLASTIC_GREEN, 10);
    public static final Mat WICKER = tri(R_WICKER, 5);
    public static final Mat LEATHER = tri(R_LEATHER, 10);
    public static final Mat FABRIC_RED = tri(R_FABRIC_RED, 10);
    public static final Mat PAINT_RED = tri(R_PAINT_RED, 10);
    public static final Mat POLICE_BLUE = tri(R_POLICE_BLUE, 6);
    public static final Mat CHECK = tri(R_CHECK, 8);
    public static final Mat ORANGE = tri(R_ORANGE, 8);
    public static final Mat GLASS = fit(R_GLASS);
    public static final Mat WHITE = tri(R_WHITE, 4);
    public static final Mat YELLOW = tri(R_YELLOW, 4);
    public static final Mat BRASS = tri(R_BRASS, 4);
    public static final Mat INNER = tri(R_INNER, 6);
    public static final Mat APPLE = tri(R_APPLE, 4);
    public static final Mat BOTTLE_GREEN = tri(R_BOTTLE_GREEN, 4);
    public static final Mat BOTTLE_BROWN = tri(R_BOTTLE_BROWN, 4);
    public static final Mat MILK = tri(R_MILK, 4);
    public static final Mat TAPE = tri(R_TAPE, 4);
    public static final Mat BONE = tri(R_BONE, 4);
    public static final Mat LABEL = fit(R_LABEL);
    public static final Mat KNOB = tri(R_KNOB, 4);
    public static final Mat BLUE_BOX = tri(R_BLUE_BOX, 6);
    public static final Mat RED_BOX = tri(R_RED_BOX, 6);
    public static final Mat GREY_BOX = tri(R_GREY_BOX, 6);
    public static final Mat S_POLICE = fit(R_S_POLICE);
    public static final Mat S_PREUVES = fit(R_S_PREUVES);
    public static final Mat S_FRAGILE = fit(R_S_FRAGILE);
    public static final Mat S_COURRIER = fit(R_S_COURRIER);
    public static final Mat S_POMMES = fit(R_S_POMMES);
    public static final Mat S_GRAIN = fit(R_S_GRAIN);
    public static final Mat S_SPRINT = fit(R_S_SPRINT);
    public static final Mat S_LAIT = fit(R_S_LAIT);
    public static final Mat S_CHASSE = fit(R_S_CHASSE);
    public static final Mat S_OUTILS = fit(R_S_OUTILS);

    /** Page de cet atlas dans {@link AtlasConteneurs#NOMS}. */
    public static final int PAGE = 1;

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world).page(PAGE);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]).page(PAGE);
    }
}
