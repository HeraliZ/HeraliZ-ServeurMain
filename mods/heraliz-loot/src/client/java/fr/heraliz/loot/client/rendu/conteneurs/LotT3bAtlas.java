package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Page d'atlas du lot T3b (police, pompiers, secours, chasse) : textures/block/loot_hd_t3b.png, peinte par
 * tools/java/PaintLotT3b. Matières vieillies (rouge pompier, aluminium larmé, tôle de vestiaire, orange sécurité civile,
 * plastique olive, camouflage de chasse, sac tissé, serge marine...), lettrages usés (POMPIERS, SECOURS, SAISIE,
 * DOUANE, SÉCURITÉ CIVILE, SABLE, ESSENCE), étiquettes et imprimés (scellés, PV, extincteur, boîtes de cartouches).
 * Zones en pixels d'une page de 1024.
 */
public final class LotT3bAtlas {
    private LotT3bAtlas() {
    }

    public static final String TEXTURE = "loot_hd_t3b";
    public static final int PAGE = AtlasConteneurs.T3B;

    // ------------------------------------------------------------------ matières (256)
    public static final int[] R_ROUGE_POMPIER = {0, 0, 256, 256};
    public static final int[] R_ALU_LARME = {256, 0, 256, 256};
    public static final int[] R_TOLE_GRISE = {512, 0, 256, 256};
    public static final int[] R_ORANGE_SC = {768, 0, 256, 256};
    public static final int[] R_OLIVE_PL = {0, 256, 256, 256};
    public static final int[] R_CAMO_CHASSE = {256, 256, 256, 256};
    public static final int[] R_SAC_TISSE = {512, 256, 256, 256};
    public static final int[] R_SERGE_MARINE = {768, 256, 256, 256};
    // ------------------------------------------------------------------ matières (128)
    public static final int[] R_BANDE_REFL = {0, 512, 128, 128};
    public static final int[] R_SANGLE_NOIRE = {128, 512, 128, 128};
    public static final int[] R_CUIR_NOIR = {256, 512, 128, 128};
    public static final int[] R_TOILE_SECOURS = {384, 512, 128, 128};
    public static final int[] R_MAIS = {512, 512, 128, 128};
    public static final int[] R_SABLE = {640, 512, 128, 128};
    public static final int[] R_ACIER_BRUNI = {768, 512, 128, 128};
    public static final int[] R_NOYER_CROSSE = {896, 512, 128, 128};
    public static final int[] R_SURVIE_OR = {0, 640, 128, 128};
    public static final int[] R_SACHET = {128, 640, 128, 128};
    public static final int[] R_CHEMISE = {256, 640, 128, 128};
    public static final int[] R_NOIR_MAT = {384, 640, 128, 128};
    public static final int[] R_POLYCARB = {512, 640, 128, 128};
    public static final int[] R_PAILLE = {640, 640, 128, 128};
    public static final int[] R_LINO = {768, 640, 128, 128};
    public static final int[] R_BANDAGE = {896, 640, 128, 128};
    // ------------------------------------------------------------------ lettrages, étiquettes, imprimés (FIT)
    public static final int[] R_L_POMPIERS = {0, 768, 256, 64};
    public static final int[] R_L_SECOURS = {256, 768, 256, 64};
    public static final int[] R_L_SAISIE = {512, 768, 256, 64};
    public static final int[] R_L_DOUANE = {768, 768, 256, 64};
    public static final int[] R_L_SECU_CIVILE = {0, 832, 256, 64};
    public static final int[] R_L_SABLE = {256, 832, 128, 64};
    public static final int[] R_ETIQ_SCELLE = {384, 832, 128, 64};
    public static final int[] R_ECUSSON = {512, 832, 64, 64};
    public static final int[] R_CROIX_SECOURS = {576, 832, 64, 64};
    public static final int[] R_BOITE_CART = {640, 832, 128, 64};
    public static final int[] R_BANDE_SCELLE = {768, 832, 256, 32};
    public static final int[] R_PLAQUE_NOM = {768, 864, 128, 32};
    public static final int[] R_NUMEROS = {896, 864, 128, 32};
    public static final int[] R_ETIQ_EXTINCTEUR = {0, 896, 128, 128};
    public static final int[] R_PV = {128, 896, 128, 128};
    public static final int[] R_BOUCLIER = {256, 896, 128, 128};
    public static final int[] R_PERSIENNES = {384, 896, 128, 128};
    public static final int[] R_CLAVIER_TEL = {512, 896, 64, 64};
    public static final int[] R_CARTOUCHE_TABAC = {576, 896, 64, 64};
    public static final int[] R_L_ESSENCE = {640, 896, 128, 64};
    public static final int[] R_ETIQ_MAIS = {640, 960, 128, 64};
    public static final int[] R_AVIS_RECHERCHE = {768, 896, 128, 128};
    public static final int[] R_BOITE_PANSEMENTS = {896, 896, 128, 128};
    public static final int[] R_GRILLE_FINE = {512, 960, 64, 64};
    public static final int[] R_CHIFFON = {576, 960, 64, 64};

    /** Rouge pompier écaillé sur acier (malle, extincteurs, seau, jerricans). */
    public static final Mat ROUGE_POMPIER = tri(R_ROUGE_POMPIER, 16);
    /** Aluminium larmé (tôle à motif en grains de riz), terni, piqué. */
    public static final Mat ALU_LARME = tri(R_ALU_LARME, 12);
    /** Tôle peinte gris clair de l'administration (vestiaires, bureau, armoire grillagée). */
    public static final Mat TOLE_GRISE = tri(R_TOLE_GRISE, 16);
    /** Plastique orange de la sécurité civile, décoloré. */
    public static final Mat ORANGE_SC = tri(R_ORANGE_SC, 14);
    /** Plastique olive (coffre étanche, glacière). */
    public static final Mat OLIVE_PL = tri(R_OLIVE_PL, 14);
    /** Camouflage de chasse (feuilles et branches). */
    public static final Mat CAMO_CHASSE = tri(R_CAMO_CHASSE, 14);
    /** Polypropylène tissé blanc cassé (sacs d'appâts). */
    public static final Mat SAC_TISSE = tri(R_SAC_TISSE, 10);
    /** Serge bleu marine (uniforme). */
    public static final Mat SERGE_MARINE = tri(R_SERGE_MARINE, 8);
    public static final Mat BANDE_REFL = tri(R_BANDE_REFL, 4);
    public static final Mat SANGLE_NOIRE = tri(R_SANGLE_NOIRE, 4);
    public static final Mat CUIR_NOIR = tri(R_CUIR_NOIR, 6);
    /** Cordura rouge des sacs d'intervention. */
    public static final Mat TOILE_SECOURS = tri(R_TOILE_SECOURS, 10);
    public static final Mat MAIS = tri(R_MAIS, 3);
    public static final Mat SABLE = tri(R_SABLE, 6);
    /** Acier bronzé des canons, usé aux arêtes. */
    public static final Mat ACIER_BRUNI = tri(R_ACIER_BRUNI, 8);
    public static final Mat NOYER_CROSSE = tri(R_NOYER_CROSSE, 8);
    public static final Mat SURVIE_OR = tri(R_SURVIE_OR, 6);
    public static final Mat SACHET = tri(R_SACHET, 6);
    /** Carton de chemise (clair : à teinter). */
    public static final Mat CHEMISE = tri(R_CHEMISE, 8);
    public static final Mat NOIR_MAT = tri(R_NOIR_MAT, 6);
    /** Polycarbonate rayé (passe translucide). */
    public static final Mat POLYCARB = tri(R_POLYCARB, 8);
    public static final Mat PAILLE = tri(R_PAILLE, 6);
    /** Dessus de bureau en linoléum. */
    public static final Mat LINO = tri(R_LINO, 12);
    public static final Mat BANDAGE = tri(R_BANDAGE, 3);

    public static final Mat L_POMPIERS = fit(R_L_POMPIERS);
    public static final Mat L_SECOURS = fit(R_L_SECOURS);
    public static final Mat L_SAISIE = fit(R_L_SAISIE);
    public static final Mat L_DOUANE = fit(R_L_DOUANE);
    public static final Mat L_SECU_CIVILE = fit(R_L_SECU_CIVILE);
    public static final Mat L_SABLE = fit(R_L_SABLE);
    public static final Mat ETIQ_SCELLE = fit(R_ETIQ_SCELLE);
    public static final Mat ECUSSON = fit(R_ECUSSON);
    public static final Mat CROIX_SECOURS = fit(R_CROIX_SECOURS);
    /** Face imprimée d'une boîte de cartouches (claire : à teinter). */
    public static final Mat BOITE_CART = fit(R_BOITE_CART);
    public static final Mat BANDE_SCELLE = fit(R_BANDE_SCELLE);
    public static final Mat PLAQUE_NOM = fit(R_PLAQUE_NOM);
    public static final Mat NUMEROS = fit(R_NUMEROS);
    public static final Mat ETIQ_EXTINCTEUR = fit(R_ETIQ_EXTINCTEUR);
    public static final Mat PV = fit(R_PV);
    /** Bouclier : polycarbonate rayé et lettrage POLICE (passe translucide). */
    public static final Mat BOUCLIER = fit(R_BOUCLIER);
    public static final Mat PERSIENNES = fit(R_PERSIENNES);
    public static final Mat CLAVIER_TEL = fit(R_CLAVIER_TEL);
    public static final Mat CARTOUCHE_TABAC = fit(R_CARTOUCHE_TABAC);
    public static final Mat L_ESSENCE = fit(R_L_ESSENCE);
    public static final Mat ETIQ_MAIS = fit(R_ETIQ_MAIS);
    public static final Mat AVIS_RECHERCHE = fit(R_AVIS_RECHERCHE);
    public static final Mat BOITE_PANSEMENTS = fit(R_BOITE_PANSEMENTS);
    /** Grillage soudé fin (mailles de 6 cm, fil de 8 mm), ajouré : rendu en découpe. */
    public static final Mat GRILLE_FINE = tri(R_GRILLE_FINE, 4);
    /** Chiffon gras (tache sombre). */
    public static final Mat CHIFFON = fit(R_CHIFFON);

    private static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world).page(PAGE);
    }

    private static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]).page(PAGE);
    }
}
