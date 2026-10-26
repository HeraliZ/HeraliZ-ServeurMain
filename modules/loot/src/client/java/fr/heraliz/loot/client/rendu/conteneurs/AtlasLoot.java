package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Atlas des conteneurs T2 à T6 (textures/block/loot_hd.png, dans l'atlas des blocs : les conteneurs sont cuits dans le
 * rendu du terrain), peint par tools/java/PaintLoot. Chaque tiers a sa matière : T4 olive militaire (acier peint,
 * planches peintes, toile), T5 bleu ONU, T6 rouge sécurité et acier noir. Zones en pixels d'un atlas de 1024.
 */
public final class AtlasLoot {
    private AtlasLoot() {
    }

    public static final String TEXTURE = "loot_hd";

    // ------------------------------------------------------------------ matières (256)
    public static final int[] R_OD_STEEL = {0, 0, 256, 256};
    public static final int[] R_OD_WOOD = {256, 0, 256, 256};
    public static final int[] R_CANVAS = {512, 0, 256, 256};
    public static final int[] R_UN_BLUE = {768, 0, 256, 256};
    public static final int[] R_RED_STEEL = {0, 256, 256, 256};
    public static final int[] R_BLACK_STEEL = {256, 256, 256, 256};
    public static final int[] R_WOOD = {512, 256, 256, 256};
    public static final int[] R_CAMO_NET = {768, 256, 256, 256};
    // ------------------------------------------------------------------ matières (128)
    public static final int[] R_STEEL = {0, 512, 128, 128};
    public static final int[] R_CHROME = {128, 512, 128, 128};
    public static final int[] R_RUBBER = {256, 512, 128, 128};
    public static final int[] R_DARK = {384, 512, 128, 128};
    public static final int[] R_ROPE = {512, 512, 128, 128};
    public static final int[] R_HAZARD = {640, 512, 128, 128};
    public static final int[] R_GLASS = {768, 512, 128, 128};
    public static final int[] R_KHAKI = {896, 512, 128, 128};
    // ------------------------------------------------------------------ pochoirs (FIT, fond transparent)
    public static final int[] R_S_762 = {0, 640, 256, 64};
    public static final int[] R_S_GRENADES = {256, 640, 256, 64};
    public static final int[] R_S_FUSILS = {512, 640, 256, 64};
    public static final int[] R_S_RADIO = {768, 640, 256, 64};
    public static final int[] R_S_UN = {0, 704, 128, 64};
    public static final int[] R_S_DANGER = {128, 704, 256, 64};
    public static final int[] R_S_ARMY = {384, 704, 256, 64};
    public static final int[] R_S_MISSILE = {640, 704, 256, 64};
    public static final int[] R_S_T = {896, 704, 128, 64};
    // ------------------------------------------------------------------ pièces
    public static final int[] R_RADIO_PANEL = {0, 768, 128, 128};
    public static final int[] R_KEYPAD = {128, 768, 64, 64};
    public static final int[] R_LCD = {192, 768, 64, 64};
    public static final int[] R_LED_RED = {256, 768, 64, 64};
    public static final int[] R_LED_GREEN = {320, 768, 64, 64};
    public static final int[] R_FOAM = {384, 768, 128, 128};
    public static final int[] R_FOAM_PISTOL = {512, 768, 256, 128};
    public static final int[] R_INNER = {768, 768, 64, 64};
    public static final int[] R_BRASS = {832, 768, 64, 64};
    public static final int[] R_OD_DARK = {896, 768, 128, 128};
    public static final int[] R_WEBBING = {0, 896, 128, 128};
    public static final int[] R_MISSILE = {128, 896, 128, 128};
    public static final int[] R_TARP = {256, 896, 128, 128};
    public static final int[] R_LABEL = {384, 896, 128, 64};
    public static final int[] R_LED_AMBER = {512, 896, 64, 64};
    public static final int[] R_WHITE = {576, 896, 64, 64};
    public static final int[] R_YELLOW = {640, 896, 64, 64};

    public static final Mat OD_STEEL = tri(R_OD_STEEL, 16);
    public static final Mat OD_WOOD = tri(R_OD_WOOD, 16);
    public static final Mat CANVAS = tri(R_CANVAS, 12);
    public static final Mat UN_BLUE = tri(R_UN_BLUE, 16);
    public static final Mat RED_STEEL = tri(R_RED_STEEL, 16);
    public static final Mat BLACK_STEEL = tri(R_BLACK_STEEL, 16);
    public static final Mat WOOD = tri(R_WOOD, 16);
    public static final Mat CAMO_NET = tri(R_CAMO_NET, 26);
    public static final Mat STEEL = tri(R_STEEL, 10);
    public static final Mat CHROME = tri(R_CHROME, 8);
    public static final Mat RUBBER = tri(R_RUBBER, 6);
    public static final Mat DARK = tri(R_DARK, 6);
    public static final Mat ROPE = tri(R_ROPE, 3);
    public static final Mat HAZARD = tri(R_HAZARD, 6);
    public static final Mat GLASS = fit(R_GLASS);
    public static final Mat KHAKI = tri(R_KHAKI, 8);
    public static final Mat S_762 = fit(R_S_762);
    public static final Mat S_GRENADES = fit(R_S_GRENADES);
    public static final Mat S_FUSILS = fit(R_S_FUSILS);
    public static final Mat S_RADIO = fit(R_S_RADIO);
    public static final Mat S_UN = fit(R_S_UN);
    public static final Mat S_DANGER = fit(R_S_DANGER);
    public static final Mat S_ARMY = fit(R_S_ARMY);
    public static final Mat S_MISSILE = fit(R_S_MISSILE);
    public static final Mat S_T = fit(R_S_T);
    public static final Mat RADIO_PANEL = fit(R_RADIO_PANEL);
    public static final Mat KEYPAD = fit(R_KEYPAD);
    public static final Mat LCD = fit(R_LCD);
    public static final Mat LED_RED = fit(R_LED_RED);
    public static final Mat LED_GREEN = fit(R_LED_GREEN);
    public static final Mat FOAM = tri(R_FOAM, 4);
    public static final Mat FOAM_PISTOL = fit(R_FOAM_PISTOL);
    public static final Mat INNER = tri(R_INNER, 6);
    public static final Mat BRASS = tri(R_BRASS, 4);
    public static final Mat OD_DARK = tri(R_OD_DARK, 10);
    public static final Mat WEBBING = tri(R_WEBBING, 4);
    public static final Mat MISSILE = tri(R_MISSILE, 10);
    public static final Mat TARP = tri(R_TARP, 10);
    public static final Mat LABEL = fit(R_LABEL);
    public static final Mat LED_AMBER = fit(R_LED_AMBER);
    public static final Mat WHITE = fit(R_WHITE);
    public static final Mat YELLOW = fit(R_YELLOW);

    /** Zones lumineuses (faces cuites en pleine lumière : voyants, écrans). */
    public static final int[][] EMISSIVES = {R_LCD, R_LED_RED, R_LED_GREEN, R_LED_AMBER};

    /** Le centre UV (0..1 de l'atlas) tombe-t-il dans une zone lumineuse ? */
    public static boolean emissif(float u, float v) {
        float x = u * 1024, y = v * 1024;
        for (int[] r : EMISSIVES) {
            if (x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) return true;
        }
        return false;
    }

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]);
    }
}
