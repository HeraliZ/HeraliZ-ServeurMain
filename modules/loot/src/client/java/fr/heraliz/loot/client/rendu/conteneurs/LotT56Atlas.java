package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;

/**
 * Page d'atlas du lot T56 ({@link AtlasConteneurs#T56}, textures/block/loot_hd_t56.png), peinte par
 * tools/java/PaintLotT56 : T5 forces de l'ONU (bleu passé, blanc sale, carton humanitaire, nylon de parachute, filet de
 * cargaison) et T6 haute sécurité (rouge sécurité, acier noir, inox, aluminium, or terni, billets, écrans éteints,
 * claviers, voyants). Les matières « neutres » (PLASTIQUE_GRAIN, SANGLE, CABLE) se teintent avec b.color(...).
 */
public final class LotT56Atlas {
    private LotT56Atlas() {
    }

    public static final String TEXTURE = "loot_hd_t56";
    public static final int PAGE = AtlasConteneurs.T56;

    // ------------------------------------------------------------------ matières (256)
    public static final int[] R_BLEU_ONU = {0, 0, 256, 256};
    public static final int[] R_BLANC_ONU = {256, 0, 256, 256};
    public static final int[] R_ACIER_NOIR = {512, 0, 256, 256};
    public static final int[] R_ROUGE_SECU = {768, 0, 256, 256};
    public static final int[] R_CARTON_BLANC = {0, 256, 256, 256};
    public static final int[] R_INOX = {256, 256, 256, 256};
    public static final int[] R_ALU = {512, 256, 256, 256};
    public static final int[] R_PLASTIQUE_GRAIN = {768, 256, 256, 256};
    // ------------------------------------------------------------------ matières (128)
    public static final int[] R_JAUNE_FUT = {0, 512, 128, 128};
    public static final int[] R_OR_TERNI = {128, 512, 128, 128};
    public static final int[] R_MOUSSE_GRISE = {256, 512, 128, 128};
    public static final int[] R_NYLON_PARA = {384, 512, 128, 128};
    public static final int[] R_SANGLE = {512, 512, 128, 128};
    public static final int[] R_FILET = {640, 512, 128, 128};
    public static final int[] R_TOLE_PERFOREE = {768, 512, 128, 128};
    public static final int[] R_CABLE = {896, 512, 128, 128};
    // ------------------------------------------------------------------ pièces (128)
    public static final int[] R_BILLETS = {0, 640, 128, 64};
    public static final int[] R_TACHE_ENCRE = {0, 704, 64, 64};
    public static final int[] R_BILLETS_TRANCHE = {64, 704, 64, 64};
    public static final int[] R_CARTE = {128, 640, 128, 128};
    public static final int[] R_CLAVIER_PC = {256, 640, 128, 128};
    public static final int[] R_ECRAN_OFF = {384, 640, 128, 128};
    public static final int[] R_VERRE_FELE = {512, 640, 128, 128};
    public static final int[] R_PANNEAU_RADIO = {640, 640, 128, 128};
    public static final int[] R_DOCUMENTS = {768, 640, 128, 128};
    public static final int[] R_FILM = {896, 640, 128, 128};
    // ------------------------------------------------------------------ pochoirs (FIT, fond transparent, 4:1)
    public static final int[] R_S_AIDE = {0, 768, 128, 32};
    public static final int[] R_S_127 = {128, 768, 128, 32};
    public static final int[] R_S_CONFIDENTIEL = {256, 768, 128, 32};
    public static final int[] R_S_PROTOTYPE = {384, 768, 128, 32};
    public static final int[] R_S_ROQUETTE = {512, 768, 128, 32};
    public static final int[] R_S_OPTIQUE = {640, 768, 128, 32};
    public static final int[] R_S_TRANSMISSIONS = {768, 768, 128, 32};
    public static final int[] R_S_FONDS = {896, 768, 128, 32};
    public static final int[] R_S_MEDICAL = {0, 800, 128, 32};
    public static final int[] R_S_BIO = {128, 800, 128, 32};
    /** « UN » (2:1). */
    public static final int[] R_S_UN_NOIR = {256, 800, 64, 32};
    public static final int[] R_S_UN_BLANC = {320, 800, 64, 32};
    public static final int[] R_S_RETRAIT = {384, 800, 128, 32};
    public static final int[] R_S_FRAGILE = {512, 800, 128, 32};
    public static final int[] R_S_ECHANTILLONS = {640, 800, 128, 32};
    public static final int[] R_S_LARGAGE = {768, 800, 128, 32};
    public static final int[] R_S_ARMES = {896, 800, 128, 32};
    // ------------------------------------------------------------------ façades, voyants, petites pièces
    /** Façade de serveur 2U (4:1) et 1U (8:1), tiroir de disque (4:1), étiquette danger biologique (4:1). */
    public static final int[] R_SERVEUR_2U = {0, 832, 256, 64};
    public static final int[] R_SERVEUR_1U = {256, 832, 256, 32};
    public static final int[] R_DISQUE = {256, 864, 128, 32};
    public static final int[] R_ETIQUETTE_BIO = {384, 864, 128, 32};
    public static final int[] R_LED_ROUGE = {512, 832, 32, 32};
    public static final int[] R_LED_VERTE = {544, 832, 32, 32};
    public static final int[] R_LED_AMBRE = {576, 832, 32, 32};
    public static final int[] R_LED_ETEINTE = {608, 832, 32, 32};
    /** Écran en veille rouge (lumineux, 2:1) et afficheur de température éteint (2:1). */
    public static final int[] R_ECRAN_VEILLE = {512, 864, 64, 32};
    public static final int[] R_LCD_OFF = {576, 864, 64, 32};
    public static final int[] R_BANDE_ROUGE = {640, 832, 128, 64};
    public static final int[] R_SCELLE = {768, 832, 64, 64};
    public static final int[] R_POIGNEE_NOIRE = {832, 832, 64, 64};
    public static final int[] R_PAPIER_KRAFT = {896, 832, 128, 64};
    public static final int[] R_BIOHAZARD = {0, 896, 128, 128};
    public static final int[] R_CROIX_MED = {128, 896, 64, 64};
    public static final int[] R_CADRAN = {192, 896, 64, 64};
    public static final int[] R_ATM_ECRAN = {128, 960, 64, 64};
    public static final int[] R_ATM_CLAVIER = {192, 960, 64, 64};
    public static final int[] R_KEYPAD_ACIER = {256, 896, 64, 64};
    public static final int[] R_PLAQUE_LAITON = {256, 960, 64, 64};
    /** 4 x 8 portes de coffres individuels (chacune 32 x 16, numérotées). */
    public static final int[] R_PORTES_CASIERS = {320, 896, 128, 128};
    public static final int[] R_FILM_DECHIRE = {448, 896, 128, 128};
    public static final int[] R_MOUSSE_DECOUPE = {576, 896, 128, 128};
    public static final int[] R_INTERIEUR_ATM = {704, 896, 128, 128};
    public static final int[] R_VIDE_RESERVE = {832, 896, 192, 128};

    // ------------------------------------------------------------------ matériaux
    /** Acier peint bleu ONU passé au soleil, écaillé, coulures. */
    public static final Mat BLEU_ONU = tri(R_BLEU_ONU, 16);
    /** Acier peint blanc ONU (véhicules) : jauni, sale, rouille aux éclats. */
    public static final Mat BLANC_ONU = tri(R_BLANC_ONU, 16);
    /** Acier noir poudré, arêtes usées jusqu'au métal, rayures, poussière. */
    public static final Mat ACIER_NOIR = tri(R_ACIER_NOIR, 16);
    /** Rouge sécurité, écaillé et terni. */
    public static final Mat ROUGE_SECU = tri(R_ROUGE_SECU, 16);
    /** Carton blanchi (aide humanitaire) : humidité, auréoles, kraft à vif. */
    public static final Mat CARTON_BLANC = tri(R_CARTON_BLANC, 12);
    public static final Mat INOX = tri(R_INOX, 16);
    public static final Mat ALU = tri(R_ALU, 12);
    /** Plastique grenu clair (caisses étanches, ordinateur durci) : à teinter. */
    public static final Mat PLASTIQUE_GRAIN = tri(R_PLASTIQUE_GRAIN, 12);
    public static final Mat JAUNE_FUT = tri(R_JAUNE_FUT, 12);
    public static final Mat OR_TERNI = tri(R_OR_TERNI, 5);
    /** Mousse prédécoupée en cubes (calage des caisses). */
    public static final Mat MOUSSE_GRISE = tri(R_MOUSSE_GRISE, 6);
    /** Nylon de parachute olive, maille anti-déchirure. */
    public static final Mat NYLON_PARA = tri(R_NYLON_PARA, 8);
    /** Sangle tissée claire : à teinter. */
    public static final Mat SANGLE = tri(R_SANGLE, 4);
    /** Filet de cargaison (ajouré, rendu en découpe). */
    public static final Mat FILET = tri(R_FILET, 10);
    /** Tôle perforée (ajourée). */
    public static final Mat TOLE_PERFOREE = tri(R_TOLE_PERFOREE, 6);
    /** Gaine de câble claire : à teinter. */
    public static final Mat CABLE = tri(R_CABLE, 6);
    public static final Mat BILLETS = fit(R_BILLETS);
    public static final Mat TACHE_ENCRE = fit(R_TACHE_ENCRE);
    public static final Mat BILLETS_TRANCHE = tri(R_BILLETS_TRANCHE, 2);
    public static final Mat CARTE = fit(R_CARTE);
    public static final Mat CLAVIER_PC = fit(R_CLAVIER_PC);
    public static final Mat ECRAN_OFF = fit(R_ECRAN_OFF);
    /** Vitre fêlée (passe translucide). */
    public static final Mat VERRE_FELE = fit(R_VERRE_FELE);
    public static final Mat PANNEAU_RADIO = fit(R_PANNEAU_RADIO);
    public static final Mat DOCUMENTS = fit(R_DOCUMENTS);
    /** Film étirable (passe translucide). */
    public static final Mat FILM = tri(R_FILM, 10);
    public static final Mat FILM_DECHIRE = fit(R_FILM_DECHIRE);
    public static final Mat S_AIDE = fit(R_S_AIDE);
    public static final Mat S_127 = fit(R_S_127);
    public static final Mat S_CONFIDENTIEL = fit(R_S_CONFIDENTIEL);
    public static final Mat S_PROTOTYPE = fit(R_S_PROTOTYPE);
    public static final Mat S_ROQUETTE = fit(R_S_ROQUETTE);
    public static final Mat S_OPTIQUE = fit(R_S_OPTIQUE);
    public static final Mat S_TRANSMISSIONS = fit(R_S_TRANSMISSIONS);
    public static final Mat S_FONDS = fit(R_S_FONDS);
    public static final Mat S_MEDICAL = fit(R_S_MEDICAL);
    public static final Mat S_BIO = fit(R_S_BIO);
    public static final Mat S_UN_NOIR = fit(R_S_UN_NOIR);
    public static final Mat S_UN_BLANC = fit(R_S_UN_BLANC);
    public static final Mat S_RETRAIT = fit(R_S_RETRAIT);
    public static final Mat S_FRAGILE = fit(R_S_FRAGILE);
    public static final Mat S_ECHANTILLONS = fit(R_S_ECHANTILLONS);
    public static final Mat S_LARGAGE = fit(R_S_LARGAGE);
    public static final Mat S_ARMES = fit(R_S_ARMES);
    public static final Mat SERVEUR_2U = fit(R_SERVEUR_2U);
    public static final Mat SERVEUR_1U = fit(R_SERVEUR_1U);
    public static final Mat DISQUE = fit(R_DISQUE);
    public static final Mat ETIQUETTE_BIO = fit(R_ETIQUETTE_BIO);
    public static final Mat LED_ROUGE = fit(R_LED_ROUGE);
    public static final Mat LED_VERTE = fit(R_LED_VERTE);
    public static final Mat LED_AMBRE = fit(R_LED_AMBRE);
    public static final Mat LED_ETEINTE = fit(R_LED_ETEINTE);
    public static final Mat ECRAN_VEILLE = fit(R_ECRAN_VEILLE);
    public static final Mat LCD_OFF = fit(R_LCD_OFF);
    public static final Mat BANDE_ROUGE = tri(R_BANDE_ROUGE, 6);
    /** Plastique rouge brillant des scellés. */
    public static final Mat SCELLE = tri(R_SCELLE, 3);
    /** Poignée / pièces en plastique noir dur. */
    public static final Mat POIGNEE_NOIRE = tri(R_POIGNEE_NOIRE, 4);
    /** Papier kraft (bagues de liasses, enveloppes) : à teinter au besoin. */
    public static final Mat PAPIER_KRAFT = tri(R_PAPIER_KRAFT, 6);
    /** Trèfle danger biologique (noir, fond transparent). */
    public static final Mat BIOHAZARD = fit(R_BIOHAZARD);
    public static final Mat CROIX_MED = fit(R_CROIX_MED);
    public static final Mat CADRAN = fit(R_CADRAN);
    public static final Mat ATM_ECRAN = fit(R_ATM_ECRAN);
    public static final Mat ATM_CLAVIER = fit(R_ATM_CLAVIER);
    public static final Mat KEYPAD_ACIER = fit(R_KEYPAD_ACIER);
    public static final Mat PLAQUE_LAITON = fit(R_PLAQUE_LAITON);
    public static final Mat PORTES_CASIERS = fit(R_PORTES_CASIERS);
    public static final Mat MOUSSE_DECOUPE = fit(R_MOUSSE_DECOUPE);
    public static final Mat INTERIEUR_ATM = fit(R_INTERIEUR_ATM);

    /** Porte de coffre individuel n (0..31) : sous-tuile de {@link #PORTES_CASIERS}. */
    public static Mat porteCasier(int n) {
        int c = Math.floorMod(n, 4), l = Math.floorMod(n / 4, 8);
        return PORTES_CASIERS.sub(c / 4f, l / 8f, (c + 1) / 4f, (l + 1) / 8f);
    }

    /** Zones lumineuses (voyants, écran en veille) : à déclarer dans AtlasConteneurs. */
    public static final int[][] LUMINEUX = {R_LED_ROUGE, R_LED_VERTE, R_LED_AMBRE, R_ECRAN_VEILLE};

    static Mat tri(int[] r, float world) {
        return Mat.tri(r[0], r[1], r[2], r[3], world).page(PAGE);
    }

    static Mat fit(int[] r) {
        return Mat.fit(r[0], r[1], r[2], r[3]).page(PAGE);
    }
}
