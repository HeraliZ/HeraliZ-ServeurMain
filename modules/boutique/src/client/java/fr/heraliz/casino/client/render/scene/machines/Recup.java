package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;

/**
 * Feuille commune des pieces de recuperation des machines a sous (textures/machine/recup.png et son calque _glow,
 * peinte par tools/textures/PaintRecup) : futs peints au pochoir, caisses, palette, parpaing, pneu, plastique de vieux
 * televiseur, tole ondulee, adhesif, cables, batterie de voiture. Les maillages qui l'utilisent sont dessines apres
 * DrawList.sheet(SHEET) ; la carrosserie figee les cuit dans sa propre partie.
 */
public final class Recup {
    private Recup() {
    }

    public static final String SHEET = "/recup";

    /** Futs (tuile deroulee une fois sur le pourtour : marquage en miroir, devant et au dos). */
    public static final int[] R_DRUM_TOXIC = {0, 0, 256, 128};
    public static final int[] R_DRUM_BLUE = {256, 0, 256, 128};
    public static final int[] R_DRUM_OLIVE = {512, 0, 256, 128};
    public static final int[] R_DRUM_RED = {768, 0, 256, 128};
    /** Flancs de caisses marques au pochoir, face de la batterie. */
    public static final int[] R_AMMO = {0, 128, 256, 128};
    public static final int[] R_CROQ = {256, 128, 256, 128};
    public static final int[] R_APPLE = {512, 128, 256, 128};
    public static final int[] R_BATTERY = {768, 128, 256, 128};
    public static final int[] R_RUST = {0, 256, 128, 128};
    public static final int[] R_WOOD = {128, 256, 128, 128};
    public static final int[] R_PALLET = {256, 256, 128, 128};
    public static final int[] R_CONCRETE = {384, 256, 128, 128};
    public static final int[] R_RUBBER = {512, 256, 128, 128};
    public static final int[] R_PLASTIC = {640, 256, 128, 128};
    public static final int[] R_STEEL = {768, 256, 128, 128};
    public static final int[] R_DARK = {896, 256, 64, 64};
    public static final int[] R_TAPE = {960, 256, 64, 64};
    public static final int[] R_CABLE = {896, 320, 64, 64};
    public static final int[] R_LED = {960, 320, 64, 64};
    public static final int[] R_CORRUGATED = {0, 384, 256, 128};
    public static final int[] R_OLIVE = {256, 384, 128, 128};
    public static final int[] R_BURLAP = {384, 384, 128, 128};
    public static final int[] R_VINYL = {512, 384, 128, 128};
    public static final int[] R_HAZARD = {640, 384, 128, 64};
    public static final int[] R_WIRE = {640, 448, 128, 64};
    /** Grille du haut-parleur et plaque de marque du televiseur. */
    public static final int[] R_SPEAKER = {768, 384, 128, 64};
    public static final int[] R_LABEL = {768, 448, 128, 64};
    public static final int[] R_RED = {896, 384, 64, 64};
    public static final int[] R_BRASS = {960, 384, 64, 64};
    public static final int[] R_BULB = {896, 448, 64, 64};
    public static final int[] R_GOLD = {960, 448, 64, 64};

    /**
     * Jetons de poker des piles posees sur les tables (ChipStacks) : face de la valeur k en (k x 128, 512), 128 x 128 ;
     * tranche en (0, 640 + k x 32), 256 x 32 (peintes par tools/textures/PaintChips).
     */
    public static final int CHIP_FACE_Y = 512, CHIP_FACE = 128, CHIP_EDGE_Y = 640, CHIP_EDGE_W = 256, CHIP_EDGE_H = 32;

    public static Mat chipFace(int k) {
        return Mat.fit(k * CHIP_FACE, CHIP_FACE_Y, CHIP_FACE, CHIP_FACE);
    }

    public static Mat chipEdge(int k) {
        return Mat.fit(0, CHIP_EDGE_Y + k * CHIP_EDGE_H, CHIP_EDGE_W, CHIP_EDGE_H);
    }

    /** Rayon, hauteur des futs et hauteur couverte par leur tuile (le marquage tombe entre les cerclages). */
    public static final float DRUM_R = 3.0f, DRUM_H = 9.3f, DRUM_WORLD = 9.9f;

    static final Mat DRUM_TOXIC = Mats.tri(R_DRUM_TOXIC, DRUM_WORLD), DRUM_BLUE = Mats.tri(R_DRUM_BLUE, DRUM_WORLD);
    static final Mat DRUM_OLIVE = Mats.tri(R_DRUM_OLIVE, DRUM_WORLD), DRUM_RED = Mats.tri(R_DRUM_RED, DRUM_WORLD);
    static final Mat AMMO = Mats.fit(R_AMMO), CROQ = Mats.fit(R_CROQ), APPLE = Mats.fit(R_APPLE), BATTERY = Mats.fit(R_BATTERY);
    static final Mat RUST = Mats.tri(R_RUST, 8), WOOD = Mats.tri(R_WOOD, 10), PALLET = Mats.tri(R_PALLET, 10);
    static final Mat CONCRETE = Mats.tri(R_CONCRETE, 8), RUBBER = Mats.tri(R_RUBBER, 6), PLASTIC = Mats.tri(R_PLASTIC, 10);
    static final Mat STEEL = Mats.tri(R_STEEL, 6), DARK = Mats.tri(R_DARK, 4), TAPE = Mats.tri(R_TAPE, 3);
    static final Mat CABLE = Mats.tri(R_CABLE, 4), LED = Mats.fit(R_LED), CORRUGATED = Mats.tri(R_CORRUGATED, 8);
    static final Mat OLIVE = Mats.tri(R_OLIVE, 8), BURLAP = Mats.tri(R_BURLAP, 6), VINYL = Mats.tri(R_VINYL, 6);
    static final Mat HAZARD = Mats.tri(R_HAZARD, 6), WIRE = Mats.tri(R_WIRE, 4), SPEAKER = Mats.fit(R_SPEAKER);
    static final Mat LABEL = Mats.fit(R_LABEL), RED = Mats.tri(R_RED, 4), BRASS = Mats.tri(R_BRASS, 3);
    static final Mat BULB = Mats.fit(R_BULB), GOLD = Mats.tri(R_GOLD, 3);
}
