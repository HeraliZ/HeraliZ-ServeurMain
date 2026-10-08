package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Page d'atlas du lot T1a (rue, commerces, déchets) : textures/block/loot_hd_t1a.png, peinte par
 * tools/java/PaintLotT1a. Plastiques de bac délavés, tôle de benne rouillée, cartons imprimés et emballages (marques
 * inventées), journaux, canettes et conserves, légumes pourris, pneus, tissus de vêtements, film et sacs translucides,
 * enseignes éteintes, décalcomanies (gras, flaques, coulures de peinture, trous de fût).
 * Les étiquettes enroulées sur un cylindre se posent avec {@link LotT1aKit#etiquetteCyl} (lecture à l'endroit).
 * Matières neutres à teinter (b.color) : TRICOT, CASIER, CAGETTE, SIMILI, RIPSTOP.
 */
public final class LotT1aAtlas {
    private LotT1aAtlas() {
    }

    public static final String TEXTURE = "loot_hd_t1a";
    public static final int PAGE = AtlasConteneurs.T1A;

    // ------------------------------------------------------------------ grandes tuiles (256)
    public static final int[] R_BAC_VERT = {0, 0, 256, 256};
    public static final int[] R_BENNE = {256, 0, 256, 256};
    public static final int[] R_PIZZA = {512, 0, 256, 256};
    public static final int[] R_UNE = {768, 0, 256, 256};
    // cartons imprimés (4 x 128, faces entières)
    public static final int[] R_CT_LESSIVE = {0, 256, 128, 128};
    public static final int[] R_CT_EAU = {128, 256, 128, 128};
    public static final int[] R_CT_FRAGILE = {0, 384, 128, 128};
    public static final int[] R_CT_BISCOTTES = {128, 384, 128, 128};
    // enseignes et pochoirs (256 x 64)
    public static final int[] R_E_FRIGO = {256, 256, 256, 64};
    public static final int[] R_E_DISTRIB = {256, 320, 256, 64};
    public static final int[] R_E_PRESSE = {256, 384, 256, 64};
    public static final int[] R_E_BENNE = {256, 448, 256, 64};
    // étiquettes de canettes et de conserves (128 x 32, enroulées)
    public static final int[] R_CAN_COLA = {512, 256, 128, 32};
    public static final int[] R_CAN_MENTHE = {512, 288, 128, 32};
    public static final int[] R_CAN_ORANGE = {512, 320, 128, 32};
    public static final int[] R_CAN_BIERE = {512, 352, 128, 32};
    public static final int[] R_BOITE_POIS = {640, 256, 128, 32};
    public static final int[] R_BOITE_RAVIOLI = {640, 288, 128, 32};
    public static final int[] R_BOITE_THON = {640, 320, 128, 32};
    public static final int[] R_BOITE_TOMATE = {640, 352, 128, 32};
    // emballages de snacks (64)
    public static final int[] R_SN_CHIPS = {768, 256, 64, 64};
    public static final int[] R_SN_CHOCO = {832, 256, 64, 64};
    public static final int[] R_SN_BISCUIT = {768, 320, 64, 64};
    public static final int[] R_SN_BONBON = {832, 320, 64, 64};
    // légumes pourris (64)
    public static final int[] R_TOMATE = {896, 256, 64, 64};
    public static final int[] R_SALADE = {960, 256, 64, 64};
    public static final int[] R_CAROTTE = {896, 320, 64, 64};
    public static final int[] R_PATATE = {960, 320, 64, 64};
    // matières (128)
    public static final int[] R_PNEU = {512, 384, 128, 128};
    public static final int[] R_JEAN = {640, 384, 128, 128};
    public static final int[] R_TRICOT = {768, 384, 128, 128};
    public static final int[] R_CENDRES = {896, 384, 128, 128};
    // flanc de pneu (disque entier), panneau du distributeur
    public static final int[] R_FLANC_PNEU = {0, 512, 256, 256};
    public static final int[] R_PANNEAU = {256, 512, 128, 256};
    // logos et petits papiers (128 x 64, fond transparent)
    public static final int[] R_L_MARCHE = {384, 512, 128, 64};
    public static final int[] R_L_TRI = {384, 576, 128, 64};
    public static final int[] R_L_PANNE = {384, 640, 128, 64};
    public static final int[] R_L_PROMO = {384, 704, 128, 64};
    // translucides (passe verre)
    public static final int[] R_FILM = {512, 512, 128, 128};
    public static final int[] R_SAC_TRANSP = {640, 512, 128, 128};
    // flancs de casiers ajourés (faces entières, à teinter)
    public static final int[] R_CASIER = {768, 512, 128, 64};
    public static final int[] R_CAGETTE = {768, 576, 128, 64};
    public static final int[] R_BACHE = {896, 512, 128, 128};
    public static final int[] R_CAHIER_BLEU = {512, 640, 64, 128};
    public static final int[] R_CAHIER_ROUGE = {576, 640, 64, 128};
    public static final int[] R_POT_BLANC = {640, 640, 128, 64};
    public static final int[] R_POT_ROUGE = {640, 704, 128, 64};
    public static final int[] R_CP_BLANC = {768, 640, 64, 128};
    public static final int[] R_CP_VERT = {832, 640, 64, 128};
    public static final int[] R_FLEURI = {896, 640, 128, 128};
    public static final int[] R_CONTREPLAQUE = {0, 768, 128, 128};
    public static final int[] R_MARINIERE = {128, 768, 128, 128};
    public static final int[] R_PET_EAU = {256, 768, 128, 32};
    public static final int[] R_PET_SODA = {256, 800, 128, 32};
    public static final int[] R_PET_JUS = {256, 832, 128, 32};
    public static final int[] R_PET_HUILE = {256, 864, 128, 32};
    public static final int[] R_D_GRAS = {384, 768, 64, 64};
    public static final int[] R_D_SAUCE = {448, 768, 64, 64};
    public static final int[] R_D_FLAQUE = {384, 832, 64, 64};
    public static final int[] R_D_TROUS = {448, 832, 64, 64};
    public static final int[] R_SAC_BLANC = {512, 768, 128, 128};
    public static final int[] R_ORDURES = {640, 768, 128, 128};
    public static final int[] R_SIMILI = {768, 768, 128, 128};
    public static final int[] R_P_PHOTO = {896, 768, 64, 64};
    public static final int[] R_P_CARTE = {960, 768, 64, 64};
    public static final int[] R_P_LETTRE = {896, 832, 64, 64};
    public static final int[] R_P_TICKET = {960, 832, 64, 64};
    public static final int[] R_TRANCHE = {0, 896, 128, 128};
    public static final int[] R_GRILLE = {128, 896, 128, 128};
    public static final int[] R_RIPSTOP = {256, 896, 128, 128};
    public static final int[] R_RESTES = {384, 896, 128, 128};
    public static final int[] R_INOX = {512, 896, 128, 128};
    public static final int[] R_PEINTURE_SECHE = {640, 896, 128, 128};
    public static final int[] R_DOUBLURE = {768, 896, 128, 128};
    public static final int[] R_ROUILLE_FUT = {896, 896, 128, 128};

    // ------------------------------------------------------------------ matériaux
    /** Plastique de bac roulant vert délavé (rayures, traînées, plaques blanchies). */
    public static final Mat BAC_VERT = tri(R_BAC_VERT, 16);
    /** Tôle peinte vert sombre de benne, rouille en plaques et coulures. */
    public static final Mat BENNE = tri(R_BENNE, 16);
    /** Dessus de boîte à pizza imprimé, taché de gras (face entière). */
    public static final Mat PIZZA = fit(R_PIZZA);
    /** Une de journal « LE COURRIER » (face entière). */
    public static final Mat UNE = fit(R_UNE);
    public static final Mat CT_LESSIVE = fit(R_CT_LESSIVE);
    public static final Mat CT_EAU = fit(R_CT_EAU);
    public static final Mat CT_FRAGILE = fit(R_CT_FRAGILE);
    public static final Mat CT_BISCOTTES = fit(R_CT_BISCOTTES);
    public static final Mat E_FRIGO = fit(R_E_FRIGO);
    public static final Mat E_DISTRIB = fit(R_E_DISTRIB);
    public static final Mat E_PRESSE = fit(R_E_PRESSE);
    /** Pochoir blanc usé (fond transparent) « ORDURES MÉNAGÈRES ». */
    public static final Mat E_BENNE = fit(R_E_BENNE);
    public static final Mat CAN_COLA = fit(R_CAN_COLA);
    public static final Mat CAN_MENTHE = fit(R_CAN_MENTHE);
    public static final Mat CAN_ORANGE = fit(R_CAN_ORANGE);
    public static final Mat CAN_BIERE = fit(R_CAN_BIERE);
    public static final Mat BOITE_POIS = fit(R_BOITE_POIS);
    public static final Mat BOITE_RAVIOLI = fit(R_BOITE_RAVIOLI);
    public static final Mat BOITE_THON = fit(R_BOITE_THON);
    public static final Mat BOITE_TOMATE = fit(R_BOITE_TOMATE);
    public static final Mat SN_CHIPS = fit(R_SN_CHIPS);
    public static final Mat SN_CHOCO = fit(R_SN_CHOCO);
    public static final Mat SN_BISCUIT = fit(R_SN_BISCUIT);
    public static final Mat SN_BONBON = fit(R_SN_BONBON);
    public static final Mat TOMATE = tri(R_TOMATE, 3);
    public static final Mat SALADE = tri(R_SALADE, 4);
    public static final Mat CAROTTE = tri(R_CAROTTE, 3);
    public static final Mat PATATE = tri(R_PATATE, 3);
    /** Bande de roulement (sculptures, boue). */
    public static final Mat PNEU = tri(R_PNEU, 6);
    public static final Mat JEAN = tri(R_JEAN, 8);
    /** Tricot clair : à teinter. */
    public static final Mat TRICOT = tri(R_TRICOT, 8);
    /** Cendres, charbons, suie. */
    public static final Mat CENDRES = tri(R_CENDRES, 8);
    /** Flanc de pneu lettré (disque entier, voir {@link LotT1aKit#anneau}). */
    public static final Mat FLANC_PNEU = fit(R_FLANC_PNEU);
    /** Colonne de commande du distributeur (clavier, monnayeur, écran éteint). */
    public static final Mat PANNEAU = fit(R_PANNEAU);
    public static final Mat L_MARCHE = fit(R_L_MARCHE);
    public static final Mat L_TRI = fit(R_L_TRI);
    /** Papier scotché « HORS SERVICE » écrit au feutre. */
    public static final Mat L_PANNE = fit(R_L_PANNE);
    public static final Mat L_PROMO = fit(R_L_PROMO);
    /** Film étirable (passe translucide), déchirures. */
    public static final Mat FILM = tri(R_FILM, 10);
    /** Sac plastique transparent froissé (passe translucide). */
    public static final Mat SAC_TRANSP = tri(R_SAC_TRANSP, 10);
    /** Flanc de casier à bière (poignée ajourée), à teinter. */
    public static final Mat CASIER = fit(R_CASIER);
    /** Flanc de cagette plastique ajourée, à teinter. */
    public static final Mat CAGETTE = fit(R_CAGETTE);
    public static final Mat BACHE = tri(R_BACHE, 10);
    public static final Mat CAHIER_BLEU = fit(R_CAHIER_BLEU);
    public static final Mat CAHIER_ROUGE = fit(R_CAHIER_ROUGE);
    public static final Mat POT_BLANC = fit(R_POT_BLANC);
    public static final Mat POT_ROUGE = fit(R_POT_ROUGE);
    public static final Mat CP_BLANC = fit(R_CP_BLANC);
    public static final Mat CP_VERT = fit(R_CP_VERT);
    public static final Mat FLEURI = tri(R_FLEURI, 8);
    public static final Mat CONTREPLAQUE = tri(R_CONTREPLAQUE, 16);
    public static final Mat MARINIERE = tri(R_MARINIERE, 6);
    public static final Mat PET_EAU = fit(R_PET_EAU);
    public static final Mat PET_SODA = fit(R_PET_SODA);
    public static final Mat PET_JUS = fit(R_PET_JUS);
    public static final Mat PET_HUILE = fit(R_PET_HUILE);
    public static final Mat D_GRAS = fit(R_D_GRAS);
    public static final Mat D_SAUCE = fit(R_D_SAUCE);
    public static final Mat D_FLAQUE = fit(R_D_FLAQUE);
    public static final Mat D_TROUS = fit(R_D_TROUS);
    public static final Mat SAC_BLANC = tri(R_SAC_BLANC, 10);
    /** Ordures en vrac (papiers, emballages) : surface d'un bac plein. */
    public static final Mat ORDURES = tri(R_ORDURES, 10);
    /** Simili cuir craquelé : à teinter. */
    public static final Mat SIMILI = tri(R_SIMILI, 8);
    public static final Mat P_PHOTO = fit(R_P_PHOTO);
    public static final Mat P_CARTE = fit(R_P_CARTE);
    public static final Mat P_LETTRE = fit(R_P_LETTRE);
    public static final Mat P_TICKET = fit(R_P_TICKET);
    /** Tranche d'une pile de papier (feuilles serrées, jaunies). */
    public static final Mat TRANCHE = tri(R_TRANCHE, 4);
    /** Grille d'aération noire (persiennes). */
    public static final Mat GRILLE = tri(R_GRILLE, 6);
    /** Nylon ripstop clair : à teinter. */
    public static final Mat RIPSTOP = tri(R_RIPSTOP, 8);
    /** Restes de nourriture séchés (croûte de pizza, sauce, fromage figé). */
    public static final Mat RESTES = tri(R_RESTES, 6);
    /** Inox brossé sale (spirales, intérieur de distributeur). */
    public static final Mat INOX = tri(R_INOX, 8);
    /** Peinture séchée en croûte (fond de pot, flaque). */
    public static final Mat PEINTURE_SECHE = tri(R_PEINTURE_SECHE, 6);
    /** Doublure de valise (nylon gris, élastiques). */
    public static final Mat DOUBLURE = tri(R_DOUBLURE, 8);
    /** Fût d'acier : peinture bleue cuite et rouille. */
    public static final Mat ROUILLE_FUT = tri(R_ROUILLE_FUT, 12);

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world).page(PAGE);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]).page(PAGE);
    }
}
