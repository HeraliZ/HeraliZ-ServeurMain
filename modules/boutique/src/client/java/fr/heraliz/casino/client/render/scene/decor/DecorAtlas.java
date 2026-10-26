package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.decor.DecorNames;
import fr.heraliz.casino.client.render.mesh.Mat;

/**
 * Textures des elements de decor, partagees avec tools/textures (PaintDecor) :
 * - atlas "decor" : matieres (rouille, acier, plastique, simili bois...), petites tuiles (ampoules, diodes, neons) ;
 * - feuille "decor_ecrans" : contenu anime des ecrans (fonds, lettres au neon, mots, neige, mire) ;
 * - atlas "decor_affiches" : les affiches de papier.
 * Zones en pixels d'un atlas de 1024.
 */
public final class DecorAtlas {
    private DecorAtlas() {
    }

    public static final String ATLAS = "decor";
    public static final String POSTERS = "decor_affiches";
    /** Feuille des ecrans : textures/machine/decor_ecrans.png. */
    public static final String SCREENS = "ecrans";

    // =================================================================== atlas "decor"
    public static final int[] R_RUST = {0, 0, 256, 256};
    public static final int[] R_STEEL = {256, 0, 256, 256};
    public static final int[] R_CASE = {512, 0, 256, 256};
    public static final int[] R_TVWOOD = {768, 0, 256, 256};
    public static final int[] R_PAINT_RED = {0, 256, 128, 128};
    public static final int[] R_PAINT_YEL = {128, 256, 128, 128};
    public static final int[] R_ENAMEL = {256, 256, 128, 128};
    public static final int[] R_DRUM = {384, 256, 128, 128};
    public static final int[] R_MESH = {0, 384, 128, 128};
    public static final int[] R_SOOT = {128, 384, 128, 128};
    public static final int[] R_HAZARD = {256, 384, 128, 128};
    public static final int[] R_PLYWOOD = {384, 384, 128, 128};
    public static final int[] R_CABLE = {512, 256, 64, 64};
    public static final int[] R_DARK = {576, 256, 64, 64};
    public static final int[] R_ENAMEL_IN = {640, 256, 64, 64};
    public static final int[] R_WHITE = {704, 256, 64, 64};
    public static final int[] R_BULB_ON = {768, 256, 64, 64};
    public static final int[] R_BULB_OFF = {832, 256, 64, 64};
    public static final int[] R_HOT = {896, 256, 64, 64};
    public static final int[] R_BRASS = {960, 256, 64, 64};
    public static final int[] R_LED_ON = {512, 320, 64, 64};
    public static final int[] R_LED_OFF = {576, 320, 64, 64};
    public static final int[] R_RUBBER = {640, 320, 64, 64};
    public static final int[] R_GLASSDK = {704, 320, 64, 64};
    /** Tubes de neon : 8 tuiles de 32 px (voir NEON_*). */
    public static final int[] R_NEON = {768, 320, 256, 32};
    public static final int[] R_TAPE = {512, 384, 256, 64};
    public static final int[] R_STICKER = {768, 384, 256, 64};
    public static final int[] R_STICKER2 = {512, 448, 256, 64};
    public static final int[] R_TAG = {768, 448, 256, 64};
    public static final int[] R_GLASS = {0, 512, 256, 128};
    public static final int[] R_GLASS_TV = {256, 512, 256, 192};
    public static final int[] R_GLASS_S = {512, 512, 256, 128};
    public static final int[] R_GRILLE = {768, 512, 128, 128};
    public static final int[] R_KNOB = {896, 512, 128, 128};

    public static final Mat RUST = Mat.tri(R_RUST[0], R_RUST[1], R_RUST[2], R_RUST[3], 12);
    public static final Mat STEEL = tri(R_STEEL, 14);
    public static final Mat CASE = tri(R_CASE, 12);
    public static final Mat TVWOOD = tri(R_TVWOOD, 16);
    public static final Mat PAINT_RED = tri(R_PAINT_RED, 8);
    public static final Mat PAINT_YEL = tri(R_PAINT_YEL, 6);
    public static final Mat ENAMEL = tri(R_ENAMEL, 8);
    public static final Mat DRUM = tri(R_DRUM, 14);
    public static final Mat MESH = tri(R_MESH, 5);
    public static final Mat SOOT = tri(R_SOOT, 8);
    public static final Mat HAZARD = tri(R_HAZARD, 6);
    public static final Mat PLYWOOD = tri(R_PLYWOOD, 12);
    public static final Mat CABLE = tri(R_CABLE, 6);
    public static final Mat DARK = tri(R_DARK, 6);
    public static final Mat ENAMEL_IN = tri(R_ENAMEL_IN, 6);
    public static final Mat WHITE = fit(R_WHITE);
    public static final Mat BULB_ON = fit(R_BULB_ON);
    public static final Mat BULB_OFF = fit(R_BULB_OFF);
    public static final Mat HOT = fit(R_HOT);
    public static final Mat BRASS = tri(R_BRASS, 6);
    public static final Mat LED_ON = fit(R_LED_ON);
    public static final Mat LED_OFF = fit(R_LED_OFF);
    public static final Mat RUBBER = tri(R_RUBBER, 6);
    public static final Mat GLASSDK = tri(R_GLASSDK, 6);
    public static final Mat TAPE = fit(R_TAPE);
    public static final Mat STICKER = fit(R_STICKER);
    public static final Mat STICKER2 = fit(R_STICKER2);
    public static final Mat TAG = fit(R_TAG);
    public static final Mat GLASS = fit(R_GLASS);
    public static final Mat GLASS_TV = fit(R_GLASS_TV);
    public static final Mat GLASS_S = fit(R_GLASS_S);
    public static final Mat GRILLE = tri(R_GRILLE, 4);
    public static final Mat KNOB = fit(R_KNOB);

    // ---- salles : le Trou (casino des clochards) et le Coffre (casino VIP)
    public static final int[] R_LEATHER = {0, 704, 128, 128};
    public static final int[] R_VELVET = {128, 704, 128, 128};
    /** Toile de matelas a rayures, tachee. */
    public static final int[] R_TICKING = {256, 704, 128, 128};
    /** Couverture militaire en laine kaki. */
    public static final int[] R_BLANKET = {384, 704, 128, 128};
    public static final int[] R_PALLET = {512, 704, 128, 128};
    /** Acier peint de la porte du coffre. */
    public static final int[] R_VAULT = {640, 704, 128, 128};
    /** Flasque de touret (contreplaque marque au pochoir) et dessus de la table (cartes, cendres, ronds de bouteille). */
    public static final int[] R_SPOOL = {768, 704, 128, 128};
    public static final int[] R_TABLETOP = {896, 704, 128, 128};
    public static final int[] R_BOTTLE = {0, 832, 64, 64};
    public static final int[] R_WAX = {64, 832, 64, 64};
    /** Cadran a chiffres du coffre. */
    public static final int[] R_DIAL = {128, 832, 128, 128};
    /** Laiton astique (VIP). */
    public static final int[] R_POLISHED = {256, 832, 64, 64};

    public static final Mat LEATHER = tri(R_LEATHER, 9), VELVET = tri(R_VELVET, 3), TICKING = tri(R_TICKING, 14), BLANKET = tri(R_BLANKET, 10);
    public static final Mat PALLET = tri(R_PALLET, 12), VAULT = tri(R_VAULT, 20), SPOOL = fit(R_SPOOL), TABLETOP = fit(R_TABLETOP);
    public static final Mat BOTTLE = tri(R_BOTTLE, 4), WAX = tri(R_WAX, 3), DIAL = fit(R_DIAL), POLISHED = tri(R_POLISHED, 5);

    /** Couleurs des tubes de neon (index de tuile). */
    public static final int NEON_PINK = 0, NEON_RED = 1, NEON_GREEN = 2, NEON_CYAN = 3, NEON_AMBER = 4, NEON_WHITE = 5, NEON_VIOLET = 6,
            NEON_ORANGE = 7;
    /** Teinte de chaque tube allume (calque lumineux) et du verre eteint (couleur). */
    public static final int[] NEON_GLOW = {0xFF3AA8, 0xFF2A2A, 0x4AFF5A, 0x3AE8FF, 0xFFB02A, 0xEAF4FF, 0xB05AFF, 0xFF7A1A};
    public static final int[] NEON_GLASS = {0xE8B8D0, 0xE0A8A8, 0xB8E0B8, 0xB0DCE4, 0xE8D0A8, 0xE4E8EC, 0xCCB8E4, 0xE8C0A0};

    public static Mat neon(int k) {
        // coeur de la tuile : la couleur y est uniforme (pas de bavure au bord des tuiles voisines)
        return Mat.fit(R_NEON[0] + k * 32 + 6, R_NEON[1] + 6, 20, 20);
    }

    // =================================================================== feuille "decor_ecrans" (contenu anime)
    public static final int[] S_SIGN_BG = {0, 0, 512, 128};
    public static final int[] S_LED_BG = {512, 0, 512, 128};
    public static final int[] S_SMALL_BG = {0, 128, 256, 112};
    public static final int[] S_TV_BG = {256, 128, 256, 192};
    public static final int[] S_MIRE = {512, 128, 256, 192};
    public static final int[] S_NOISE = {768, 128, 256, 256};
    /** Lettres C A S I N O : 6 cases de 80 x 112. */
    public static final int[] S_LETTERS = {0, 320, 480, 112};
    public static final int[] S_OUVERT = {0, 448, 256, 64};
    public static final int[] S_FERME = {256, 448, 256, 64};
    public static final int[] S_JACKPOT = {512, 448, 256, 64};
    public static final int[] S_SIGNAL = {768, 448, 256, 64};
    /** Textes de la fleche : 6 cases de 192 x 48 (3 par ligne). */
    public static final int[] S_LABELS = {0, 512, 576, 96};
    /** Enseignes des cartes (pique, coeur, carreau, trefle) : 4 cases de 64. */
    public static final int[] S_SUITS = {576, 512, 256, 64};
    public static final int[] S_SKULL = {832, 512, 128, 128};
    public static final int[] S_BLACK = {576, 576, 64, 64};
    public static final int[] S_SCAN = {640, 576, 128, 64};
    public static final int[] S_JETONS = {768, 640, 256, 64};
    public static final int[] S_WELCOME = {0, 640, 512, 64};

    public static final Mat SIGN_BG = fit(S_SIGN_BG), LED_BG = fit(S_LED_BG), SMALL_BG = fit(S_SMALL_BG), TV_BG = fit(S_TV_BG);
    public static final Mat MIRE = fit(S_MIRE), NOISE = fit(S_NOISE), OUVERT = fit(S_OUVERT), FERME = fit(S_FERME);
    public static final Mat JACKPOT = fit(S_JACKPOT), SIGNAL = fit(S_SIGNAL), SKULL = fit(S_SKULL), BLACK = fit(S_BLACK), SCAN = fit(S_SCAN);
    public static final Mat JETONS = fit(S_JETONS), WELCOME = fit(S_WELCOME);

    public static final int LETTER_W = 80, LETTER_H = 112;

    /** Case de la lettre k du mot CASINO. */
    public static Mat letter(int k) {
        return Mat.fit(S_LETTERS[0] + k * LETTER_W, S_LETTERS[1], LETTER_W, LETTER_H);
    }

    public static final int LABEL_W = 192, LABEL_H = 48;

    public static Mat label(int k) {
        return Mat.fit(S_LABELS[0] + (k % 3) * LABEL_W, S_LABELS[1] + (k / 3) * LABEL_H, LABEL_W, LABEL_H);
    }

    public static Mat suit(int k) {
        return Mat.fit(S_SUITS[0] + k * 64, S_SUITS[1], 64, 64);
    }

    // =================================================================== atlas "decor_affiches"
    public static final int POSTER_W = 256, POSTER_H = 352;
    public static final int[] P_TAPE = {0, 704, 256, 64};
    public static final int[] P_TAPE2 = {256, 704, 256, 64};
    public static final int[] P_BACK = {512, 704, 128, 128};
    public static final int[] P_NAIL = {640, 704, 64, 64};
    /** Vieille affiche dechiree, dessous. */
    public static final int[] P_OLD = {0, 768, 256, 256};
    public static final int[] P_OLD2 = {256, 768, 256, 256};

    public static int[] poster(int k) {
        return new int[]{(k % 4) * POSTER_W, (k / 4) * POSTER_H, POSTER_W, POSTER_H};
    }

    public static final Mat P_TAPE_M = fit(P_TAPE), P_TAPE2_M = fit(P_TAPE2), P_BACK_M = tri(P_BACK, 8), P_NAIL_M = fit(P_NAIL);
    public static final Mat P_OLD_M = fit(P_OLD), P_OLD2_M = fit(P_OLD2);

    // =================================================================== feuille "decor_salles" (2.0.0 : nouveaux decors du Trou et du Coffre)
    /** Feuille des nouveaux decors : textures/machine/decor_salles.png (+ _glow), a utiliser avec out.sheet(SALLES). */
    public static final String SALLES = "salles";
    /** Graffitis (fond transparent) : CAISSE et tags, fleches, VIP, couronne, etincelle. */
    public static final int[] G_CAISSE = {0, 0, 768, 256};
    public static final int[] G_ARROW = {768, 0, 256, 128};
    public static final int[] G_ARROW_VIP = {768, 128, 256, 128};
    public static final int[] G_VIP = {0, 256, 512, 256};
    public static final int[] G_CROWN = {512, 256, 128, 128};
    public static final int[] G_SPARK = {640, 256, 128, 128};
    /** Paille pressee (brins dans la longueur) et bout coupe d'une botte, ficelle agricole orange. */
    public static final int[] S_STRAW = {768, 256, 128, 128};
    public static final int[] S_STRAW_END = {896, 256, 128, 128};
    public static final int[] S_TWINE = {512, 384, 64, 64};
    public static final int[] S_CHROME = {576, 384, 64, 64};
    public static final int[] S_RED_PLASTIC = {512, 448, 64, 64};
    public static final int[] S_FOAM = {576, 448, 64, 64};
    public static final int[] S_PLASTIC_W = {640, 384, 128, 128};
    /** Plastique noir des sacs poubelle (plis luisants). */
    public static final int[] S_TRASH = {768, 384, 128, 128};
    /** Grille de fil chrome du caddie (ajouree). */
    public static final int[] S_CART = {896, 384, 128, 128};
    /** Velours a fleurs du canape defonce, carton, bouteille de gaz bleue, flamme bleue, etiquette de conserve. */
    public static final int[] S_FLORAL = {0, 512, 128, 128};
    public static final int[] S_CARDBOARD = {128, 512, 128, 128};
    public static final int[] S_GAS = {256, 512, 64, 64};
    public static final int[] S_FLAME = {320, 512, 64, 64};
    public static final int[] S_CAN = {256, 576, 128, 64};
    /** Facade du ghetto-blaster (haut-parleurs, cassette, afficheur). */
    public static final int[] S_BOOMBOX = {384, 512, 256, 128};
    /** Cuir capitonne sang-de-boeuf, marbre blanc, laque noire filetee d'or. */
    public static final int[] S_OXBLOOD = {640, 512, 128, 128};
    public static final int[] S_MARBLE = {768, 512, 128, 128};
    public static final int[] S_LACQUER = {896, 512, 128, 128};
    /** Pancartes de carton (DecorNames.SIGNS) : 4 cases de 256 x 128. */
    public static final int[] S_SIGNS = {0, 640, 1024, 128};
    /** Facade du juke-box, porte de coffre de depot, lingot (dessus estampille), or, acier brosse. */
    public static final int[] S_JUKEBOX = {0, 768, 192, 256};
    public static final int[] S_DEPOSIT = {192, 768, 128, 128};
    public static final int[] S_GOLD_TOP = {320, 768, 128, 64};
    public static final int[] S_GOLD = {320, 832, 64, 64};
    public static final int[] S_BRUSHED = {384, 832, 64, 64};
    /** Liasses : dessus (billet), tranche des billets empiles, bande de papier ; aluminium de la mallette. */
    public static final int[] S_BILLS = {448, 768, 128, 64};
    public static final int[] S_BILLS_SIDE = {448, 832, 128, 32};
    public static final int[] S_BAND = {448, 864, 64, 32};
    public static final int[] S_ALU = {512, 864, 64, 64};
    /** Verre vert de la lampe de banquier, whisky ambre, etiquette, cigare, abat-jour plisse, palme. */
    public static final int[] S_BANKER = {576, 768, 64, 64};
    public static final int[] S_WHISKY = {640, 768, 64, 64};
    public static final int[] S_LABEL = {704, 768, 64, 64};
    public static final int[] S_CIGAR = {768, 768, 64, 32};
    public static final int[] S_SHADE = {832, 768, 64, 64};
    public static final int[] S_PALM = {896, 768, 128, 128};
    /** Tubes : lumiere noire (violet), fluorescent (blanc froid), cathodes rougeoyantes ; vu-metre. */
    public static final int[] S_UV = {576, 832, 64, 64};
    public static final int[] S_FLUO = {640, 832, 64, 64};
    public static final int[] S_CATHODE = {704, 832, 64, 64};
    public static final int[] S_VU = {768, 800, 64, 32};

    public static final Mat GRAF_CAISSE = fit(G_CAISSE), GRAF_ARROW = fit(G_ARROW), GRAF_ARROW_VIP = fit(G_ARROW_VIP), GRAF_VIP = fit(G_VIP);
    public static final Mat GRAF_CROWN = fit(G_CROWN), GRAF_SPARK = fit(G_SPARK);
    public static final Mat STRAW = tri(S_STRAW, 10), STRAW_END = tri(S_STRAW_END, 10), TWINE = tri(S_TWINE, 3), CHROME = tri(S_CHROME, 6);
    public static final Mat RED_PLASTIC = tri(S_RED_PLASTIC, 6), FOAM = tri(S_FOAM, 5), PLASTIC_W = tri(S_PLASTIC_W, 10), TRASH = tri(S_TRASH, 9);
    public static final Mat CART = tri(S_CART, 6), FLORAL = tri(S_FLORAL, 10), CARDBOARD = tri(S_CARDBOARD, 14), GAS = tri(S_GAS, 5);
    public static final Mat FLAME_BLUE = fit(S_FLAME), CAN = fit(S_CAN), BOOMBOX = fit(S_BOOMBOX), OXBLOOD = tri(S_OXBLOOD, 8);
    public static final Mat MARBLE = tri(S_MARBLE, 12), LACQUER = tri(S_LACQUER, 16), JUKEBOX = fit(S_JUKEBOX), DEPOSIT = fit(S_DEPOSIT);
    public static final Mat GOLD_TOP = fit(S_GOLD_TOP), GOLD = tri(S_GOLD, 6), BRUSHED = tri(S_BRUSHED, 8), BILLS = fit(S_BILLS);
    public static final Mat BILLS_SIDE = tri(S_BILLS_SIDE, 6), BAND = tri(S_BAND, 4), ALU = tri(S_ALU, 8), BANKER = tri(S_BANKER, 5);
    public static final Mat WHISKY = tri(S_WHISKY, 4), LABEL = fit(S_LABEL), CIGAR = tri(S_CIGAR, 4), SHADE = tri(S_SHADE, 5), PALM = fit(S_PALM);
    public static final Mat UV_TUBE = fit(S_UV), FLUO_TUBE = fit(S_FLUO), CATHODE = fit(S_CATHODE), VU = fit(S_VU);

    /** Pancarte k (DecorNames.SIGNS). */
    public static Mat sign(int k) {
        return Mat.fit(S_SIGNS[0] + (k % 4) * 256, S_SIGNS[1], 256, 128);
    }

    // =================================================================== utilitaires
    public static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world);
    }

    public static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]);
    }
}
