package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Page d'atlas du lot T4b (militaire léger : camps, checkpoints, casernes) : textures/block/loot_hd_t4b.png, peinte par
 * tools/java/PaintLotT4b. Planches peintes olive écaillées, toile de coton, acier olive rouillé, caisse brute vert-de-gris,
 * toile de sac de sable, carton de rations, camouflage Centre-Europe, bâche ; sangles, laine, gaze, sachets, alu ;
 * panneaux (radio, carte, croix sanitaire, losange explosif, étiquettes) et pochoirs usés (fond transparent).
 */
public final class LotT4bAtlas {
    private LotT4bAtlas() {
    }

    public static final String TEXTURE = "loot_hd_t4b";
    public static final int PAGE = AtlasConteneurs.T4B;

    // ------------------------------------------------------------------ matières (256)
    public static final int[] R_BOIS_OLIVE = {0, 0, 256, 256};
    public static final int[] R_TOILE = {256, 0, 256, 256};
    public static final int[] R_ACIER = {512, 0, 256, 256};
    public static final int[] R_BOIS_KAKI = {768, 0, 256, 256};
    public static final int[] R_SAC_SABLE = {0, 256, 256, 256};
    public static final int[] R_CARTON = {256, 256, 256, 256};
    public static final int[] R_CAMO_CE = {512, 256, 256, 256};
    public static final int[] R_BACHE = {768, 256, 256, 256};
    // ------------------------------------------------------------------ matières (128)
    public static final int[] R_SANGLE = {0, 512, 128, 128};
    public static final int[] R_LAINE = {128, 512, 128, 128};
    public static final int[] R_KAKI_CLAIR = {256, 512, 128, 128};
    public static final int[] R_GAZE = {384, 512, 128, 128};
    public static final int[] R_SACHET = {512, 512, 128, 128};
    public static final int[] R_ALU = {640, 512, 128, 128};
    public static final int[] R_JAUNE = {768, 512, 128, 128};
    public static final int[] R_ORANGE = {896, 512, 128, 128};
    // ------------------------------------------------------------------ panneaux (FIT)
    public static final int[] R_RADIO_FACE = {0, 640, 128, 128};
    public static final int[] R_CARTE = {128, 640, 128, 128};
    public static final int[] R_CROIX_ROUGE = {256, 640, 128, 128};
    public static final int[] R_LOSANGE = {384, 640, 128, 128};
    public static final int[] R_ETIQ_RATION = {512, 640, 128, 128};
    public static final int[] R_ETIQ_SACHET = {640, 640, 128, 128};
    public static final int[] R_PERFUSION = {768, 640, 128, 128};
    public static final int[] R_PLAQUE = {896, 640, 128, 128};
    // ------------------------------------------------------------------ pochoirs (FIT, fond transparent)
    public static final int[] R_S_MATERIEL = {0, 768, 256, 64};
    public static final int[] R_S_CODE = {256, 768, 256, 64};
    public static final int[] R_S_NOM = {512, 768, 256, 64};
    public static final int[] R_S_MATRICULE = {768, 768, 256, 64};
    public static final int[] R_S_F34 = {0, 832, 256, 64};
    public static final int[] R_S_GAZOLE = {256, 832, 256, 64};
    public static final int[] R_S_RATIONS = {512, 832, 256, 64};
    public static final int[] R_S_SANTE = {768, 832, 256, 64};
    public static final int[] R_S_EXPLOSIFS = {0, 896, 256, 64};
    public static final int[] R_S_OBUS = {256, 896, 256, 64};
    public static final int[] R_S_762 = {512, 896, 256, 64};
    public static final int[] R_S_CART = {768, 896, 256, 64};
    public static final int[] R_S_TENTE = {0, 960, 256, 64};
    public static final int[] R_S_HAUT = {256, 960, 256, 64};
    public static final int[] R_S_LOT = {512, 960, 256, 64};
    public static final int[] R_S_DANGER = {768, 960, 256, 64};

    /** Planches peintes olive, écaillées jusqu'au bois gris, crasse et coulures. */
    public static final Mat BOIS_OLIVE = tri(R_BOIS_OLIVE, 16);
    /** Toile de coton olive (sac marin, sac à dos, sac de tente) délavée, tachée. */
    public static final Mat TOILE = tri(R_TOILE, 12);
    /** Acier peint olive, écaillé, rouillé (cantines, jerricans, fûts). */
    public static final Mat ACIER = tri(R_ACIER, 16);
    /** Planches brutes vert-de-gris (caisses de type soviétique, râteliers de campagne). */
    public static final Mat BOIS_KAKI = tri(R_BOIS_KAKI, 16);
    /** Toile tissée de sac de sable, terre incrustée. */
    public static final Mat SAC_SABLE = tri(R_SAC_SABLE, 10);
    /** Carton kaki de rations, auréoles d'humidité. */
    public static final Mat CARTON = tri(R_CARTON, 12);
    /** Camouflage Centre-Europe délavé (gilets, couvre-casque). */
    public static final Mat CAMO_CE = tri(R_CAMO_CE, 12);
    /** Bâche olive (toile enduite) : plis, taches. */
    public static final Mat BACHE = tri(R_BACHE, 14);
    public static final Mat SANGLE = tri(R_SANGLE, 4);
    public static final Mat LAINE = tri(R_LAINE, 6);
    public static final Mat KAKI_CLAIR = tri(R_KAKI_CLAIR, 6);
    public static final Mat GAZE = tri(R_GAZE, 3);
    public static final Mat SACHET = tri(R_SACHET, 4);
    public static final Mat ALU = tri(R_ALU, 6);
    public static final Mat JAUNE = tri(R_JAUNE, 4);
    public static final Mat ORANGE = tri(R_ORANGE, 4);

    public static final Mat RADIO_FACE = fit(R_RADIO_FACE);
    public static final Mat CARTE = fit(R_CARTE);
    public static final Mat CROIX_ROUGE = fit(R_CROIX_ROUGE);
    public static final Mat LOSANGE = fit(R_LOSANGE);
    public static final Mat ETIQ_RATION = fit(R_ETIQ_RATION);
    public static final Mat ETIQ_SACHET = fit(R_ETIQ_SACHET);
    public static final Mat PERFUSION = fit(R_PERFUSION);
    public static final Mat PLAQUE = fit(R_PLAQUE);

    public static final Mat S_MATERIEL = fit(R_S_MATERIEL);
    public static final Mat S_CODE = fit(R_S_CODE);
    public static final Mat S_NOM = fit(R_S_NOM);
    public static final Mat S_MATRICULE = fit(R_S_MATRICULE);
    public static final Mat S_F34 = fit(R_S_F34);
    public static final Mat S_GAZOLE = fit(R_S_GAZOLE);
    public static final Mat S_RATIONS = fit(R_S_RATIONS);
    public static final Mat S_SANTE = fit(R_S_SANTE);
    public static final Mat S_EXPLOSIFS = fit(R_S_EXPLOSIFS);
    public static final Mat S_OBUS = fit(R_S_OBUS);
    public static final Mat S_762 = fit(R_S_762);
    public static final Mat S_CART = fit(R_S_CART);
    public static final Mat S_TENTE = fit(R_S_TENTE);
    public static final Mat S_HAUT = fit(R_S_HAUT);
    public static final Mat S_LOT = fit(R_S_LOT);
    public static final Mat S_DANGER = fit(R_S_DANGER);

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world).page(PAGE);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]).page(PAGE);
    }
}
