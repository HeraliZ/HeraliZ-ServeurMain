package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Atlas commun d'usure (page {@link AtlasConteneurs#USURE}, textures/block/loot_hd_usure.png), peint par
 * tools/java/PaintUsure : matières vieillies partagées par tous les tiers et décalcomanies de dégâts (coulures de
 * rouille, boue, auréoles, sang séché, croix de fouille, fissures, brûlures, affiche, griffures...).
 * Les matières « neutres » (PEINTURE_USEE, PLASTIQUE_USE, TISSU_USE) sont claires : on les teinte avec la couleur des
 * sommets (b.color(...)) pour obtenir n'importe quelle teinte usée. Décalcomanies : FIT, fond transparent, bords nets.
 */
public final class AtlasUsure {
    private AtlasUsure() {
    }

    public static final String TEXTURE = "loot_hd_usure";
    public static final int PAGE = AtlasConteneurs.USURE;

    // ------------------------------------------------------------------ matières (256)
    public static final int[] R_ROUILLE = {0, 0, 256, 256};
    public static final int[] R_BOIS_GRIS = {256, 0, 256, 256};
    public static final int[] R_BETON = {512, 0, 256, 256};
    public static final int[] R_PEINTURE_USEE = {768, 0, 256, 256};
    public static final int[] R_PLASTIQUE_USE = {0, 256, 256, 256};
    public static final int[] R_TISSU_USE = {256, 256, 256, 256};
    public static final int[] R_CARTON_MOUILLE = {512, 256, 256, 256};
    public static final int[] R_METAL_NU = {768, 256, 256, 256};
    // ------------------------------------------------------------------ matières (128)
    public static final int[] R_SCOTCH = {0, 512, 128, 128};
    public static final int[] R_SCOTCH_BRUN = {128, 512, 128, 128};
    public static final int[] R_INTERIEUR_SALE = {256, 512, 128, 128};
    public static final int[] R_MOISI = {384, 512, 128, 128};
    public static final int[] R_VERRE_SALE = {512, 512, 128, 128};
    public static final int[] R_CAOUTCHOUC_USE = {640, 512, 128, 128};
    public static final int[] R_CUIR_USE = {768, 512, 128, 128};
    public static final int[] R_GALVA_ROUILLE = {896, 512, 128, 128};
    public static final int[] R_JOURNAL = {0, 640, 128, 128};
    public static final int[] R_PAPIER = {128, 640, 128, 128};
    public static final int[] R_SAC_POUBELLE = {256, 640, 128, 128};
    public static final int[] R_TERRE = {384, 640, 128, 128};
    public static final int[] R_FEUILLES = {512, 640, 128, 128};
    public static final int[] R_CHROME_PIQUE = {640, 640, 128, 128};
    public static final int[] R_MOUSSE_JAUNE = {768, 640, 128, 128};
    public static final int[] R_GRILLAGE = {896, 640, 128, 128};
    // ------------------------------------------------------------------ décalcomanies (FIT, alpha)
    public static final int[] R_COULURE_1 = {0, 768, 64, 128};
    public static final int[] R_COULURE_2 = {64, 768, 64, 128};
    public static final int[] R_COULURE_3 = {128, 768, 64, 128};
    public static final int[] R_BOUE_1 = {192, 768, 128, 64};
    public static final int[] R_BOUE_2 = {192, 832, 128, 64};
    public static final int[] R_AUREOLE = {320, 768, 128, 128};
    public static final int[] R_SANG_1 = {448, 768, 128, 128};
    public static final int[] R_SANG_2 = {576, 768, 128, 128};
    public static final int[] R_MAIN_SANG = {704, 768, 64, 64};
    public static final int[] R_CROIX = {704, 832, 64, 64};
    public static final int[] R_FISSURE = {768, 768, 128, 128};
    public static final int[] R_BRULURE = {896, 768, 128, 128};
    public static final int[] R_AFFICHE = {0, 896, 128, 128};
    public static final int[] R_ETIQUETTE_DECHIREE = {128, 896, 128, 64};
    public static final int[] R_MOUSSE_VERTE = {128, 960, 128, 64};
    public static final int[] R_GRIFFURES = {256, 896, 128, 64};
    public static final int[] R_IMPACTS = {256, 960, 128, 64};
    public static final int[] R_VERRE_BRISE = {384, 896, 128, 128};

    /** Acier rouillé à cœur (tôle abandonnée). */
    public static final Mat ROUILLE = tri(R_ROUILLE, 16);
    /** Planches grisées par les intempéries, fentes. */
    public static final Mat BOIS_GRIS = tri(R_BOIS_GRIS, 16);
    /** Béton taché (parpaings, socles, bordures). */
    public static final Mat BETON = tri(R_BETON, 16);
    /** Peinture claire écaillée sur métal rouillé : à teinter (b.color). */
    public static final Mat PEINTURE_USEE = tri(R_PEINTURE_USEE, 16);
    /** Plastique clair décoloré, rayé : à teinter. */
    public static final Mat PLASTIQUE_USE = tri(R_PLASTIQUE_USE, 12);
    /** Toile claire tachée : à teinter. */
    public static final Mat TISSU_USE = tri(R_TISSU_USE, 12);
    /** Carton gondolé, auréoles d'eau. */
    public static final Mat CARTON_MOUILLE = tri(R_CARTON_MOUILLE, 16);
    /** Acier nu terni (bords usés, pièces sans peinture). */
    public static final Mat METAL_NU = tri(R_METAL_NU, 12);
    /** Ruban adhésif gris (« duct tape »). */
    public static final Mat SCOTCH = tri(R_SCOTCH, 6);
    /** Ruban d'emballage brun. */
    public static final Mat SCOTCH_BRUN = tri(R_SCOTCH_BRUN, 6);
    /** Intérieur crasseux sombre (fond de frigo, de placard). */
    public static final Mat INTERIEUR_SALE = tri(R_INTERIEUR_SALE, 10);
    /** Moisissure noire-verte. */
    public static final Mat MOISI = tri(R_MOISI, 8);
    /** Verre sale (passe translucide). */
    public static final Mat VERRE_SALE = fit(R_VERRE_SALE);
    public static final Mat CAOUTCHOUC_USE = tri(R_CAOUTCHOUC_USE, 6);
    public static final Mat CUIR_USE = tri(R_CUIR_USE, 10);
    /** Tôle galvanisée piquée de rouille blanche et rousse. */
    public static final Mat GALVA_ROUILLE = tri(R_GALVA_ROUILLE, 10);
    /** Papier journal imprimé, jauni. */
    public static final Mat JOURNAL = tri(R_JOURNAL, 8);
    /** Papier froissé blanc cassé. */
    public static final Mat PAPIER = tri(R_PAPIER, 8);
    /** Sac poubelle noir brillant, plis. */
    public static final Mat SAC_POUBELLE = tri(R_SAC_POUBELLE, 10);
    /** Terre, boue sèche (tas, renversée). */
    public static final Mat TERRE = tri(R_TERRE, 10);
    /** Feuilles mortes (litière). */
    public static final Mat FEUILLES = tri(R_FEUILLES, 10);
    public static final Mat CHROME_PIQUE = tri(R_CHROME_PIQUE, 8);
    /** Mousse jaunie (rembourrage, coussin éventré). */
    public static final Mat MOUSSE_JAUNE = tri(R_MOUSSE_JAUNE, 6);
    /** Grillage soudé (ajouré : trous transparents, rendu en découpe). */
    public static final Mat GRILLAGE = tri(R_GRILLAGE, 8);

    public static final Mat COULURE_1 = fit(R_COULURE_1);
    public static final Mat COULURE_2 = fit(R_COULURE_2);
    public static final Mat COULURE_3 = fit(R_COULURE_3);
    public static final Mat BOUE_1 = fit(R_BOUE_1);
    public static final Mat BOUE_2 = fit(R_BOUE_2);
    public static final Mat AUREOLE = fit(R_AUREOLE);
    public static final Mat SANG_1 = fit(R_SANG_1);
    public static final Mat SANG_2 = fit(R_SANG_2);
    public static final Mat MAIN_SANG = fit(R_MAIN_SANG);
    /** Croix de fouille à la bombe rouge (« déjà fouillé »). */
    public static final Mat CROIX = fit(R_CROIX);
    public static final Mat FISSURE = fit(R_FISSURE);
    public static final Mat BRULURE = fit(R_BRULURE);
    /** Affiche « AVIS D'ÉVACUATION » déchirée, scotchée. */
    public static final Mat AFFICHE = fit(R_AFFICHE);
    public static final Mat ETIQUETTE_DECHIREE = fit(R_ETIQUETTE_DECHIREE);
    public static final Mat MOUSSE_VERTE = fit(R_MOUSSE_VERTE);
    /** Griffures (quatre traits parallèles). */
    public static final Mat GRIFFURES = fit(R_GRIFFURES);
    /** Impacts de balles. */
    public static final Mat IMPACTS = fit(R_IMPACTS);
    /** Vitre brisée (passe translucide : trou transparent, fêlures). */
    public static final Mat VERRE_BRISE = fit(R_VERRE_BRISE);

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world).page(PAGE);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]).page(PAGE);
    }
}
