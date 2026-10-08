package fr.heralisz.utils.deco;

/**
 * Décors animés de la map (tools/gen_anim_meshes.py écrit leurs maillages, textures et modèles fixes) : maillage
 * (assets/heralisz_utils/meshes/&lt;maillage&gt;.json, et &lt;maillage&gt;_loin.json au-delà de {@code lod} blocs),
 * distance d'affichage {@code vue}, rayon de la boîte de rendu (les ailes d'un moulin dépassent de 10 blocs), forme du
 * bloc en pixels pour la face nord.
 */
public enum Genre {
    MOULIN("moulin_ailes", "moulin", 256, 11, 72, "bois", true, 0, 0, 0, 16, 16, 16),
    MOULIN_USE("moulin_ailes_usees", "moulin_use", 256, 11, 72, "bois", true, 0, 0, 0, 16, 16, 16),
    EOLIENNE("eolienne", "eolienne", 144, 4, 40, "metal", true, 5, 0, 3, 11, 9, 13),
    ROUE("roue_aubes", "roue", 112, 4, 40, "bois", false, 6, 6, 6, 10, 10, 10),
    RADAR("radar_antenne", "radar", 192, 6, 48, "metal", true, 2, 0, 2, 14, 4, 14),
    MANCHE("manche_a_air", "manche_a_air", 128, 7, 32, "metal", true, 7, 0, 7, 9, 16, 9),
    DRAPEAU_HERALIZ("drapeau_heraliz", "drapeau_heraliz", 128, 8, 32, "metal", true, 7, 0, 7, 9, 16, 9),
    DRAPEAU_ARMEE("drapeau_armee", "drapeau_armee", 128, 8, 32, "metal", true, 7, 0, 7, 9, 16, 9),
    DRAPEAU_QUARANTAINE("drapeau_quarantaine", "drapeau_quarantaine", 128, 8, 32, "metal", true, 7, 0, 7, 9, 16, 9),
    DRAPEAU_SECOURS("drapeau_secours", "drapeau_secours", 128, 8, 32, "metal", true, 7, 0, 7, 9, 16, 9),
    DRAPEAU_BLANC("drapeau_blanc", "drapeau_blanc", 128, 8, 32, "metal", true, 7, 0, 7, 9, 16, 9),
    // faille des nains : lanterne au bout d'une corde (éclaire), bannières de toile (contre un mur, face au vide)
    LANTERNE_CORDE("lanterne_corde", "lanterne_corde", 96, 2, 32, "metal", false, 6, 12, 6, 10, 16, 10),
    BANNIERE_NAINE_ROUGE("banniere_naine_rouge", "banniere_naine_rouge", 128, 3, 40, "bois", false, 1, 4, 13, 15, 16, 16),
    BANNIERE_NAINE_BLEU("banniere_naine_bleu", "banniere_naine_bleu", 128, 3, 40, "bois", false, 1, 4, 13, 15, 16, 16),
    // décors vivants vus de loin (tools/gen_anim_props.py) : grande roue des fêtes foraines, chevalets des champs de
    // pétrole, grues des chantiers, lanterne du phare, mâts d'antenne relais, corbeaux au-dessus des lieux morts
    GRANDE_ROUE("grande_roue", "grande_roue", 256, 28, 80, "metal", true, 0, 0, 0, 16, 4, 16),
    CHEVALET("chevalet_pompage", "chevalet", 224, 6, 64, "metal", true, 0, 0, 0, 16, 2, 16),
    GRUE("grue_tour", "grue", 256, 34, 96, "metal", true, 0, 0, 0, 16, 4, 16),
    PHARE("phare_lanterne", "phare", 256, 46, 96, "metal", true, 0, 0, 0, 16, 2, 16),
    MAT_ANTENNE("mat_antenne", "mat_antenne", 256, 40, 96, "metal", true, 0, 0, 0, 16, 4, 16),
    CORBEAUX("corbeaux", "corbeaux", 192, 26, 64, "bois", false, 7, 0, 7, 9, 1, 9),
    // lot 3 (05/10, refonte de la map ; tools/gen_anim_deco3.py) : décanteur de la station d'épuration radioactive,
    // gyrophare, ventilateur d'extraction, grande éolienne des crêtes, château d'eau des villages
    DECANTEUR("decanteur", "decanteur", 160, 8, 48, "metal", true, 0, 0, 0, 16, 2, 16),
    GYROPHARE("gyrophare", "gyrophare", 128, 4, 32, "metal", true, 4, 0, 4, 12, 13, 12),
    VENTILATEUR("ventilateur", "ventilateur", 64, 2, 24, "metal", true, 0, 0, 0, 16, 16, 16),
    EOLIENNE_MODERNE("eolienne_moderne", "eolienne_moderne", 384, 42, 96, "metal", true, 0, 0, 0, 16, 4, 16),
    CHATEAU_EAU("chateau_eau", "chateau_eau", 256, 14, 80, "metal", true, 0, 0, 0, 16, 4, 16);

    public final String id, maillage, son;
    public final int vue, rayon, lod;
    public final boolean solide;
    public final double[] boite;

    /** Lumière émise par le bloc (la lanterne éclaire autour d'elle). */
    public int lumiere() {
        return this == LANTERNE_CORDE ? 14 : this == PHARE ? 15 : this == GYROPHARE ? 11 : this == DECANTEUR ? 7 : 0;
    }

    Genre(String id, String maillage, int vue, int rayon, int lod, String son, boolean solide, double... boite) {
        this.id = id;
        this.maillage = maillage;
        this.vue = vue;
        this.rayon = rayon;
        this.lod = lod;
        this.son = son;
        this.solide = solide;
        this.boite = boite;
    }
}
