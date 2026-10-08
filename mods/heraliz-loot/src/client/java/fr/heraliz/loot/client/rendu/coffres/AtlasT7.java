package fr.heraliz.loot.client.rendu.coffres;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Atlas des coffres T7 (textures/coffre/coffres_t7.png + calque lumineux _glow), peint par tools/java/PaintCoffres :
 * matières propres et modernes (graphite mat, alu brossé, acier satiné, carbone, caoutchouc, mousse), voyants et
 * bandeaux lumineux, plaques de marquage. Zones en pixels d'un atlas de 1024.
 */
public final class AtlasT7 {
    private AtlasT7() {
    }

    public static final String TEXTURE = "coffres_t7";

    // ------------------------------------------------------------------ matières (tuiles qui se répètent)
    public static final int[] R_GRAPHITE = {0, 0, 256, 256};
    public static final int[] R_ALU = {256, 0, 256, 256};
    public static final int[] R_GUNMETAL = {512, 0, 256, 256};
    public static final int[] R_CARBON = {768, 0, 256, 256};
    public static final int[] R_RUBBER = {0, 256, 128, 128};
    public static final int[] R_FOAM = {128, 256, 128, 128};
    public static final int[] R_OLIVE = {256, 256, 128, 128};
    public static final int[] R_CHROME = {384, 256, 128, 128};
    public static final int[] R_HEX = {512, 256, 128, 128};
    public static final int[] R_VENT = {640, 256, 128, 128};
    public static final int[] R_STRAP = {768, 256, 128, 128};
    public static final int[] R_SAND = {896, 256, 128, 128};
    // ------------------------------------------------------------------ petites tuiles unies (64)
    public static final int[] R_DARK = {0, 384, 64, 64};
    public static final int[] R_WHITE = {64, 384, 64, 64};
    public static final int[] R_LED_RED = {128, 384, 64, 64};
    public static final int[] R_LED_WHITE = {192, 384, 64, 64};
    public static final int[] R_LED_ICE = {256, 384, 64, 64};
    public static final int[] R_LED_GREEN = {320, 384, 64, 64};
    public static final int[] R_LED_OFF = {384, 384, 64, 64};
    public static final int[] R_GLASS = {448, 384, 64, 64};
    public static final int[] R_FROST = {512, 384, 64, 64};
    public static final int[] R_RED_PAINT = {576, 384, 64, 64};
    public static final int[] R_INNER = {640, 384, 64, 64};
    public static final int[] R_AMBER = {704, 384, 64, 64};
    // ------------------------------------------------------------------ marquages (FIT : la face entière couvre la zone)
    public static final int[] R_LOGO = {0, 448, 256, 64};
    public static final int[] R_LOGO_LIT = {256, 448, 256, 64};
    public static final int[] R_KEYPAD = {512, 448, 64, 64};
    public static final int[] R_SCREEN_LOCK = {576, 448, 128, 64};
    public static final int[] R_SCREEN_OPEN = {704, 448, 128, 64};
    public static final int[] R_DIAL = {832, 448, 64, 64};
    public static final int[] R_T7 = {896, 448, 128, 64};
    public static final int[] R_CIRCUIT = {0, 512, 256, 256};
    public static final int[] R_WARN = {256, 512, 256, 64};
    public static final int[] R_SCAN = {256, 576, 256, 64};
    public static final int[] R_CRYO = {256, 640, 256, 64};
    public static final int[] R_VENTS_SLOT = {256, 704, 256, 64};
    public static final int[] R_FOAM_CUT = {512, 512, 256, 128};
    public static final int[] R_BADGE = {768, 512, 128, 128};
    public static final int[] R_RING_LIT = {896, 512, 128, 128};
    public static final int[] R_CORE = {512, 640, 128, 128};
    public static final int[] R_GRID_LIT = {640, 640, 128, 128};
    public static final int[] R_CASH = {768, 640, 128, 64};
    public static final int[] R_CASH_SIDE = {768, 704, 128, 64};
    public static final int[] R_BAND = {896, 640, 64, 64};
    public static final int[] R_PARACHUTE = {0, 768, 256, 128};

    public static final Mat GRAPHITE = tri(R_GRAPHITE, 16);
    public static final Mat ALU = tri(R_ALU, 14);
    public static final Mat GUNMETAL = tri(R_GUNMETAL, 14);
    public static final Mat CARBON = tri(R_CARBON, 8);
    public static final Mat RUBBER = tri(R_RUBBER, 6);
    public static final Mat FOAM = tri(R_FOAM, 4);
    public static final Mat OLIVE = tri(R_OLIVE, 10);
    public static final Mat CHROME = tri(R_CHROME, 8);
    public static final Mat HEX = tri(R_HEX, 6);
    public static final Mat VENT = tri(R_VENT, 4);
    public static final Mat STRAP = tri(R_STRAP, 4);
    public static final Mat SAND = tri(R_SAND, 10);
    public static final Mat DARK = tri(R_DARK, 6);
    public static final Mat WHITE = fit(R_WHITE);
    public static final Mat LED_RED = fit(R_LED_RED);
    public static final Mat LED_WHITE = fit(R_LED_WHITE);
    public static final Mat LED_ICE = fit(R_LED_ICE);
    public static final Mat LED_GREEN = fit(R_LED_GREEN);
    public static final Mat LED_OFF = fit(R_LED_OFF);
    public static final Mat GLASS = fit(R_GLASS);
    public static final Mat FROST = fit(R_FROST);
    public static final Mat RED_PAINT = tri(R_RED_PAINT, 6);
    public static final Mat INNER = tri(R_INNER, 6);
    public static final Mat AMBER = fit(R_AMBER);
    public static final Mat LOGO = fit(R_LOGO);
    public static final Mat LOGO_LIT = fit(R_LOGO_LIT);
    public static final Mat KEYPAD = fit(R_KEYPAD);
    public static final Mat SCREEN_LOCK = fit(R_SCREEN_LOCK);
    public static final Mat SCREEN_OPEN = fit(R_SCREEN_OPEN);
    public static final Mat DIAL = fit(R_DIAL);
    public static final Mat T7 = fit(R_T7);
    public static final Mat CIRCUIT = fit(R_CIRCUIT);
    public static final Mat WARN = fit(R_WARN);
    public static final Mat SCAN = fit(R_SCAN);
    public static final Mat CRYO = fit(R_CRYO);
    public static final Mat VENTS_SLOT = fit(R_VENTS_SLOT);
    public static final Mat FOAM_CUT = fit(R_FOAM_CUT);
    public static final Mat BADGE = fit(R_BADGE);
    public static final Mat RING_LIT = fit(R_RING_LIT);
    public static final Mat CORE = fit(R_CORE);
    public static final Mat GRID_LIT = fit(R_GRID_LIT);
    public static final Mat CASH = fit(R_CASH);
    public static final Mat CASH_SIDE = tri(R_CASH_SIDE, 3);
    public static final Mat BAND = tri(R_BAND, 2);
    public static final Mat PARACHUTE = tri(R_PARACHUTE, 24);

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]);
    }
}
