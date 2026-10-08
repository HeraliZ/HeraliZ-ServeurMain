package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Page d'atlas du lot T2b (maisons de campagne, fermes, jardins, caves, ateliers) : textures/block/loot_hd_t2b.png,
 * peinte par tools/java/PaintLotT2b. Chêne ciré grisé, sapin brut, bois peint écaillé et émail (neutres, à teinter),
 * paille, douelles de tonneau, miroir terni, fonte, alu, raphia, plaid, linge, torchon ; tapis de sol, cire, herbe
 * sèche, isorel perforé, grillage à poules, toile d'araignée, cuirs ; assiettes, bocaux, conserves, étiquettes et
 * imprimés (marques inventées, textes en français). Zones en pixels d'une page de 1024.
 */
public final class LotT2bAtlas {
    private LotT2bAtlas() {
    }

    public static final String TEXTURE = "loot_hd_t2b";
    public static final int PAGE = AtlasConteneurs.T2B;

    // ------------------------------------------------------------------ matières (256)
    public static final int[] R_CHENE = {0, 0, 256, 256};
    public static final int[] R_SAPIN = {256, 0, 256, 256};
    public static final int[] R_BOIS_PEINT = {512, 0, 256, 256};
    public static final int[] R_EMAIL = {768, 0, 256, 256};
    public static final int[] R_PAILLE = {0, 256, 256, 256};
    public static final int[] R_TONNEAU = {256, 256, 256, 256};
    // ------------------------------------------------------------------ matières (128)
    public static final int[] R_MIROIR = {512, 256, 128, 256};
    public static final int[] R_FONTE = {640, 256, 128, 128};
    public static final int[] R_ALU = {640, 384, 128, 128};
    public static final int[] R_RAPHIA = {768, 256, 128, 128};
    public static final int[] R_PLAID = {896, 256, 128, 128};
    public static final int[] R_LINGE = {768, 384, 128, 128};
    public static final int[] R_TORCHON = {896, 384, 128, 128};
    public static final int[] R_TAPIS_SOL = {0, 512, 128, 128};
    public static final int[] R_CIRE = {128, 512, 128, 128};
    public static final int[] R_HERBE = {256, 512, 128, 128};
    public static final int[] R_PERFORE = {384, 512, 128, 128};
    public static final int[] R_GRILLAGE_HEXA = {512, 512, 128, 128};
    public static final int[] R_TOILE_ARAIGNEE = {640, 512, 128, 128};
    public static final int[] R_CUIR_FAUVE = {768, 512, 128, 128};
    public static final int[] R_TOILE_VALISE = {896, 512, 128, 128};
    // ------------------------------------------------------------------ objets peints (FIT)
    public static final int[] R_ASSIETTE_1 = {0, 640, 128, 128};
    public static final int[] R_ASSIETTE_2 = {128, 640, 128, 128};
    public static final int[] R_BOCAL_TOMATE = {256, 640, 64, 128};
    public static final int[] R_BOCAL_HARICOT = {320, 640, 64, 128};
    public static final int[] R_BOCAL_ABRICOT = {384, 640, 64, 128};
    public static final int[] R_BOCAL_CONFITURE = {448, 640, 64, 128};
    public static final int[] R_CONSERVE_1 = {512, 640, 128, 64};
    public static final int[] R_CONSERVE_2 = {512, 704, 128, 64};
    public static final int[] R_CONSERVE_3 = {640, 640, 128, 64};
    public static final int[] R_ETIQ_VIN = {640, 704, 128, 64};
    public static final int[] R_GRAINES = {768, 640, 128, 128};
    public static final int[] R_SECOURS = {896, 640, 128, 128};
    public static final int[] R_S_ENGRAIS = {0, 768, 256, 128};
    public static final int[] R_S_TERREAU = {256, 768, 256, 128};
    public static final int[] R_ETIQ_VALISE = {512, 768, 128, 128};
    public static final int[] R_DECOR_MACHINE = {640, 768, 128, 64};
    public static final int[] R_PILE_ASSIETTES = {640, 832, 128, 64};
    public static final int[] R_FONTE_AJOUREE = {768, 768, 128, 128};
    public static final int[] R_FOND_TONNEAU = {896, 768, 128, 128};
    // ------------------------------------------------------------------ plaques, petites matières
    public static final int[] R_P_FOURNEAU = {0, 896, 128, 32};
    public static final int[] R_P_TONDEUSE = {0, 928, 128, 32};
    public static final int[] R_P_SERVANTE = {0, 960, 128, 32};
    public static final int[] R_P_CONGEL = {0, 992, 128, 32};
    public static final int[] R_P_LAIT = {128, 896, 128, 32};
    public static final int[] R_P_RUCHE = {128, 928, 128, 32};
    public static final int[] R_P_GAZ = {128, 960, 128, 64};
    public static final int[] R_GRANULES = {256, 896, 128, 128};
    public static final int[] R_CENDRE = {384, 896, 128, 128};
    public static final int[] R_ECORCE = {512, 896, 128, 128};
    public static final int[] R_LEURRES = {640, 896, 128, 128};
    public static final int[] R_FILET = {768, 896, 128, 128};
    public static final int[] R_P_SAC = {896, 896, 128, 64};
    public static final int[] R_THERMO = {896, 960, 64, 64};
    public static final int[] R_BOUT_BOIS = {960, 960, 64, 64};

    /** Chêne ciré grisé (fil horizontal ; une pièce verticale se construit tournée, voir LotT2bKit.bois). */
    public static final Mat CHENE = tri(R_CHENE, 16);
    /** Sapin brut de sciage, grisé par les intempéries. */
    public static final Mat SAPIN = tri(R_SAPIN, 16);
    /** Bois peint clair écaillé (bois gris dessous) : à teinter. */
    public static final Mat BOIS_PEINT = tri(R_BOIS_PEINT, 16);
    /** Émail clair éclaté (noir dessous), rouille aux éclats : à teinter. */
    public static final Mat EMAIL = tri(R_EMAIL, 16);
    public static final Mat PAILLE = tri(R_PAILLE, 10);
    /** Douelles de tonneau (joints selon v) : profil de révolution, voir LotT2b.tonneau. */
    public static final Mat TONNEAU = tri(R_TONNEAU, 9);
    public static final Mat MIROIR = fit(R_MIROIR);
    public static final Mat FONTE = tri(R_FONTE, 8);
    public static final Mat ALU = tri(R_ALU, 10);
    public static final Mat RAPHIA = tri(R_RAPHIA, 8);
    public static final Mat PLAID = tri(R_PLAID, 8);
    public static final Mat LINGE = tri(R_LINGE, 8);
    public static final Mat TORCHON = tri(R_TORCHON, 6);
    public static final Mat TAPIS_SOL = tri(R_TAPIS_SOL, 6);
    public static final Mat CIRE = tri(R_CIRE, 4);
    public static final Mat HERBE = tri(R_HERBE, 8);
    public static final Mat PERFORE = tri(R_PERFORE, 8);
    /** Grillage à poules (ajouré). */
    public static final Mat GRILLAGE_HEXA = tri(R_GRILLAGE_HEXA, 6);
    public static final Mat TOILE_ARAIGNEE = fit(R_TOILE_ARAIGNEE);
    public static final Mat CUIR_FAUVE = tri(R_CUIR_FAUVE, 8);
    public static final Mat TOILE_VALISE = tri(R_TOILE_VALISE, 8);
    public static final Mat ASSIETTE_1 = fit(R_ASSIETTE_1);
    public static final Mat ASSIETTE_2 = fit(R_ASSIETTE_2);
    public static final Mat BOCAL_TOMATE = fit(R_BOCAL_TOMATE);
    public static final Mat BOCAL_HARICOT = fit(R_BOCAL_HARICOT);
    public static final Mat BOCAL_ABRICOT = fit(R_BOCAL_ABRICOT);
    public static final Mat BOCAL_CONFITURE = fit(R_BOCAL_CONFITURE);
    public static final Mat CONSERVE_1 = fit(R_CONSERVE_1);
    public static final Mat CONSERVE_2 = fit(R_CONSERVE_2);
    public static final Mat CONSERVE_3 = fit(R_CONSERVE_3);
    public static final Mat ETIQ_VIN = fit(R_ETIQ_VIN);
    public static final Mat GRAINES = fit(R_GRAINES);
    public static final Mat SECOURS = fit(R_SECOURS);
    public static final Mat S_ENGRAIS = fit(R_S_ENGRAIS);
    public static final Mat S_TERREAU = fit(R_S_TERREAU);
    public static final Mat ETIQ_VALISE = fit(R_ETIQ_VALISE);
    public static final Mat DECOR_MACHINE = fit(R_DECOR_MACHINE);
    public static final Mat FONTE_AJOUREE = fit(R_FONTE_AJOUREE);
    public static final Mat FOND_TONNEAU = fit(R_FOND_TONNEAU);
    public static final Mat P_FOURNEAU = fit(R_P_FOURNEAU);
    public static final Mat P_TONDEUSE = fit(R_P_TONDEUSE);
    public static final Mat P_SERVANTE = fit(R_P_SERVANTE);
    public static final Mat P_CONGEL = fit(R_P_CONGEL);
    public static final Mat P_LAIT = fit(R_P_LAIT);
    public static final Mat P_RUCHE = fit(R_P_RUCHE);
    public static final Mat P_GAZ = fit(R_P_GAZ);
    public static final Mat GRANULES = tri(R_GRANULES, 6);
    public static final Mat CENDRE = tri(R_CENDRE, 6);
    public static final Mat ECORCE = tri(R_ECORCE, 6);
    public static final Mat LEURRES = fit(R_LEURRES);
    /** Filet d'épuisette (ajouré). */
    public static final Mat FILET = tri(R_FILET, 4);
    public static final Mat P_SAC = fit(R_P_SAC);
    public static final Mat THERMO = fit(R_THERMO);
    /** Bois de bout (cernes) : tranches de bûches, bouts de manche. */
    public static final Mat BOUT_BOIS = fit(R_BOUT_BOIS);

    /** Tranche d'une pile d'assiettes (une tuile = hauteur de la pile, voir LotT2bKit.pileAssiettes). */
    public static Mat pileAssiettes(float hauteur) {
        return Mat.tri(R_PILE_ASSIETTES[0], R_PILE_ASSIETTES[1], R_PILE_ASSIETTES[2], R_PILE_ASSIETTES[3], hauteur).page(PAGE);
    }

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world).page(PAGE);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]).page(PAGE);
    }
}
