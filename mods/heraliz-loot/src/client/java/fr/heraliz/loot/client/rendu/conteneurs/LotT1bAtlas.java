package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Page d'atlas du lot T1b (logements abandonnés : cuisine, chambre, salon, salle de bain, bureau) :
 * textures/block/loot_hd_t1b.png, peinte par tools/java/PaintLotT1b. Intérieurs des années 80-2000, teintes passées :
 * mélaminé, bois verni terni, formica, électroménager jauni, velours de salon, coutil de matelas, tôle de bureau,
 * linge en vrac, étiquettes de produits (marques inventées), lettrages au feutre.
 * Les matières claires (MELAMINE, ELECTRO, TOLE, PELUCHE, DUVET, EPONGE, LIVRE) se teintent avec b.color(...).
 */
public final class LotT1bAtlas {
    private LotT1bAtlas() {
    }

    public static final String TEXTURE = "loot_hd_t1b";
    public static final int PAGE = AtlasConteneurs.T1B;

    // ------------------------------------------------------------------ matières (256)
    public static final int[] R_MELAMINE = {0, 0, 256, 256};
    public static final int[] R_BOIS_VERNI = {256, 0, 256, 256};
    public static final int[] R_FORMICA = {512, 0, 256, 256};
    public static final int[] R_ELECTRO = {768, 0, 256, 256};
    public static final int[] R_VELOURS = {0, 256, 256, 256};
    public static final int[] R_MATELAS = {256, 256, 256, 256};
    public static final int[] R_TOLE = {512, 256, 256, 256};
    public static final int[] R_LINGE = {768, 256, 256, 256};
    // ------------------------------------------------------------------ matières (128)
    public static final int[] R_INOX = {0, 512, 128, 128};
    public static final int[] R_CERAMIQUE = {128, 512, 128, 128};
    public static final int[] R_AGGLO = {256, 512, 128, 128};
    public static final int[] R_PELUCHE = {384, 512, 128, 128};
    public static final int[] R_CABAS = {512, 512, 128, 128};
    public static final int[] R_DUVET = {640, 512, 128, 128};
    public static final int[] R_PLAID = {768, 512, 128, 128};
    public static final int[] R_EPONGE = {896, 512, 128, 128};
    public static final int[] R_GRILLE_MO = {0, 640, 128, 128};
    public static final int[] R_VITRE_FOUR = {128, 640, 128, 128};
    public static final int[] R_ECRAN_TV = {256, 640, 128, 128};
    public static final int[] R_PAGES = {384, 640, 128, 128};
    public static final int[] R_SEYES = {512, 640, 128, 128};
    public static final int[] R_KRAFT = {640, 640, 128, 128};
    public static final int[] R_LIVRE = {768, 640, 128, 128};
    public static final int[] R_FONTE = {896, 640, 128, 128};
    // ------------------------------------------------------------------ étiquettes (128 x 64, autour des cylindres : en miroir)
    public static final int[] R_L_JAVEL = {0, 768, 128, 64};
    public static final int[] R_L_VAISSELLE = {128, 768, 128, 64};
    public static final int[] R_L_LESSIVE = {256, 768, 128, 64};
    public static final int[] R_L_SHAMPOOING = {384, 768, 128, 64};
    public static final int[] R_L_COLA = {512, 768, 128, 64};
    public static final int[] R_L_BIERE = {640, 768, 128, 64};
    public static final int[] R_BANDEAU_FOUR = {768, 768, 128, 64};
    public static final int[] R_VHS = {896, 768, 128, 64};
    // ------------------------------------------------------------------ bandeaux et lettrages (256 x 64)
    public static final int[] R_BANDEAU_LAVE = {0, 832, 256, 64};
    public static final int[] R_T_CUISINE = {256, 832, 256, 64};
    public static final int[] R_T_CHAMBRE = {512, 832, 256, 64};
    public static final int[] R_T_SALON = {768, 832, 256, 64};
    public static final int[] R_T_LIVRES = {0, 896, 256, 64};
    public static final int[] R_T_PAPIERS = {256, 896, 256, 64};
    public static final int[] R_PERSIENNES = {512, 896, 128, 64};
    public static final int[] R_NUMEROS = {640, 896, 128, 64};
    public static final int[] R_AUTOCOLLANTS = {768, 896, 128, 64};
    public static final int[] R_PANNEAU_MO = {896, 896, 64, 128};
    public static final int[] R_ENERGIE = {960, 896, 64, 128};
    public static final int[] R_L_PEINTURE = {0, 960, 128, 64};
    public static final int[] R_L_CONSERVE = {128, 960, 128, 64};
    public static final int[] R_CAHIER = {256, 960, 128, 64};
    public static final int[] R_PHOTO = {384, 960, 64, 64};
    public static final int[] R_MEDOC = {448, 960, 64, 64};
    public static final int[] R_L_PILE = {512, 960, 64, 64};
    public static final int[] R_DESSIN = {576, 960, 64, 64};
    public static final int[] R_MAGNETO = {640, 960, 128, 64};
    public static final int[] R_FACADE_TV = {768, 960, 128, 64};

    /** Mélaminé crème jauni (à teinter), éclats laissant voir l'aggloméré. */
    public static final Mat MELAMINE = tri(R_MELAMINE, 16);
    /** Placage chêne verni, vernis terni et rayé. */
    public static final Mat BOIS_VERNI = tri(R_BOIS_VERNI, 16);
    /** Stratifié de plan de travail façon granit beige, brûlures de casseroles, entailles. */
    public static final Mat FORMICA = tri(R_FORMICA, 16);
    /** Tôle émaillée d'électroménager (blanc cassé, à teinter : amande, brun). */
    public static final Mat ELECTRO = tri(R_ELECTRO, 16);
    /** Velours de salon des années 80 (bandes et motifs bruns passés). */
    public static final Mat VELOURS = tri(R_VELOURS, 14);
    /** Coutil de matelas matelassé (losanges, damassé), auréoles. */
    public static final Mat MATELAS = tri(R_MATELAS, 16);
    /** Tôle peinte de mobilier de bureau (gris beige, à teinter), éclats et rouille. */
    public static final Mat TOLE = tri(R_TOLE, 16);
    /** Linge en vrac (jean, jersey, rayures, coton délavé). */
    public static final Mat LINGE = tri(R_LINGE, 10);
    /** Inox brossé terni, traces de doigts et de calcaire. */
    public static final Mat INOX = tri(R_INOX, 10);
    /** Faïence blanche, tartre, craquelures. */
    public static final Mat CERAMIQUE = tri(R_CERAMIQUE, 10);
    /** Aggloméré nu (chants arrachés, dos de meuble), gonflé par l'humidité. */
    public static final Mat AGGLO = tri(R_AGGLO, 8);
    /** Peluche emmêlée (à teinter). */
    public static final Mat PELUCHE = tri(R_PELUCHE, 6);
    /** Plastique tissé bleu de grand cabas. */
    public static final Mat CABAS = tri(R_CABAS, 8);
    /** Nylon matelassé de sac de couchage (clair, à teinter). */
    public static final Mat DUVET = tri(R_DUVET, 8);
    /** Plaid écossais passé. */
    public static final Mat PLAID = tri(R_PLAID, 8);
    /** Éponge de serviette (claire, à teinter). */
    public static final Mat EPONGE = tri(R_EPONGE, 6);
    /** Hublot de micro-ondes (grille perforée noire). */
    public static final Mat GRILLE_MO = fit(R_GRILLE_MO);
    /** Vitre de four (verre fumé gras). */
    public static final Mat VITRE_FOUR = fit(R_VITRE_FOUR);
    /** Écran cathodique éteint, fêlé. */
    public static final Mat ECRAN_TV = fit(R_ECRAN_TV);
    /** Tranche de pages (livres, cahiers, liasses). */
    public static final Mat PAGES = tri(R_PAGES, 3);
    /** Page de cahier à grands carreaux, écriture d'enfant. */
    public static final Mat SEYES = fit(R_SEYES);
    /** Carton kraft des dossiers suspendus. */
    public static final Mat KRAFT = tri(R_KRAFT, 8);
    /** Toile de reliure (claire, à teinter), nerfs et titre au dos. */
    public static final Mat LIVRE = tri(R_LIVRE, 4);
    /** Fonte émaillée noire (grilles de cuisinière, brûleurs), graisse brûlée. */
    public static final Mat FONTE = tri(R_FONTE, 6);

    public static final Mat L_JAVEL = fit(R_L_JAVEL);
    public static final Mat L_VAISSELLE = fit(R_L_VAISSELLE);
    public static final Mat L_LESSIVE = fit(R_L_LESSIVE);
    public static final Mat L_SHAMPOOING = fit(R_L_SHAMPOOING);
    public static final Mat L_COLA = fit(R_L_COLA);
    public static final Mat L_BIERE = fit(R_L_BIERE);
    public static final Mat BANDEAU_FOUR = fit(R_BANDEAU_FOUR);
    public static final Mat VHS = fit(R_VHS);
    public static final Mat BANDEAU_LAVE = fit(R_BANDEAU_LAVE);
    public static final Mat T_CUISINE = fit(R_T_CUISINE);
    public static final Mat T_CHAMBRE = fit(R_T_CHAMBRE);
    public static final Mat T_SALON = fit(R_T_SALON);
    public static final Mat T_LIVRES = fit(R_T_LIVRES);
    public static final Mat T_PAPIERS = fit(R_T_PAPIERS);
    public static final Mat PERSIENNES = fit(R_PERSIENNES);
    public static final Mat NUMEROS = fit(R_NUMEROS);
    public static final Mat AUTOCOLLANTS = fit(R_AUTOCOLLANTS);
    public static final Mat PANNEAU_MO = fit(R_PANNEAU_MO);
    public static final Mat ENERGIE = fit(R_ENERGIE);
    public static final Mat L_PEINTURE = fit(R_L_PEINTURE);
    public static final Mat L_CONSERVE = fit(R_L_CONSERVE);
    public static final Mat CAHIER = fit(R_CAHIER);
    public static final Mat PHOTO = fit(R_PHOTO);
    public static final Mat MEDOC = fit(R_MEDOC);
    public static final Mat L_PILE = fit(R_L_PILE);
    public static final Mat DESSIN = fit(R_DESSIN);
    public static final Mat MAGNETO = fit(R_MAGNETO);
    public static final Mat FACADE_TV = fit(R_FACADE_TV);

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world).page(PAGE);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]).page(PAGE);
    }

    /** Une moitié (gauche 0 / droite 1) d'une étiquette 128 x 64 : sur une face plane, l'étiquette sans répétition. */
    static Mat moitie(Mat m, int k) {
        return m.sub(k * 0.5f, 0, k * 0.5f + 0.5f, 1);
    }
}
