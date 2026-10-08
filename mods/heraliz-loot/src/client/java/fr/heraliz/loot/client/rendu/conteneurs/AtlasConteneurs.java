package fr.heraliz.loot.client.rendu.conteneurs;

import java.util.ArrayList;
import java.util.List;

/**
 * Les pages d'atlas des conteneurs (textures/block/&lt;nom&gt;.png, 1024 x 1024 chacune). Un matériau vit sur une page
 * ({@code Mat.page(k)}) : la page est codée dans la partie entière de u, le rendu choisit la texture d'après floor(u).
 * Un conteneur peut donc mélanger les matières de plusieurs pages (sa famille + l'usure commune).
 */
public final class AtlasConteneurs {
    private AtlasConteneurs() {
    }

    /** Index = page. Ne jamais réordonner : les matériaux codent leur page en dur. */
    public static final String[] NOMS = {
            "loot_hd",        // 0 militaire T4-T6 (AtlasLoot)
            "loot_hd_civil",  // 1 civil T2-T3 (AtlasCivil)
            "loot_hd_usure",  // 2 usure commune : coulures, taches, scotch, matières vieillies (AtlasUsure)
            "loot_hd_t1a",    // 3 lot T1a
            "loot_hd_t1b",    // 4 lot T1b
            "loot_hd_t2b",    // 5 lot T2b
            "loot_hd_t3b",    // 6 lot T3b
            "loot_hd_t4b",    // 7 lot T4b
            "loot_hd_t56",    // 8 lot T56
    };

    public static final int MILITAIRE = 0, CIVIL = 1, USURE = 2, T1A = 3, T1B = 4, T2B = 5, T3B = 6, T4B = 7, T56 = 8;

    /** Page d'une coordonnée u (avec sa page). */
    public static int page(float u) {
        int k = (int) Math.floor(u);
        return k < 0 ? 0 : Math.min(k, NOMS.length - 1);
    }

    private static final List<int[]> LUMINEUX = new ArrayList<>();

    static {
        for (int[] r : AtlasLoot.EMISSIVES) lumineux(MILITAIRE, r);
    }

    /** Déclare une zone lumineuse (voyants, écrans : faces cuites en pleine lumière) : {x, y, w, h} en pixels de la page. */
    public static synchronized void lumineux(int page, int[] r) {
        LUMINEUX.add(new int[]{page, r[0], r[1], r[2], r[3]});
    }

    /** Le centre UV d'une face (u avec sa page, v) tombe-t-il dans une zone lumineuse ? */
    public static boolean emissif(float u, float v) {
        int k = page(u);
        float x = (u - k) * 1024, y = v * 1024;
        for (int[] r : LUMINEUX) {
            if (r[0] == k && x >= r[1] && x <= r[1] + r[3] && y >= r[2] && y <= r[2] + r[4]) return true;
        }
        return false;
    }
}
