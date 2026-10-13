package fr.heraliz.usure.client;

import fr.heraliz.usure.client.kit.Ui;

import java.util.Arrays;

/**
 * Formes de l'indicateur d'usure (maquette C « anneau », choisie par le proprietaire), mises en {@link Maillage} :
 * l'anneau d'etat et sa lueur, le disque sombre du centre, le bord du verre, le contour rouge de l'alerte « ruinee ».
 * Elles reproduisent le rendu de la maquette (navigateur) : anneau de 12 px autour d'un trou de 8 px, rempli dans le
 * sens horaire depuis midi de la couleur
 * de l'etat, piste vide en blanc a 13 %, part perdue aux reparations en rouge a 55 % au bout du tour ; lueur = ombre
 * portee floue de l'anneau et du disque (flou de 1,5 px, couleur de l'etat a 53 %), d'ou la piste vide teintee pres du
 * centre et le halo plus fort autour de la partie pleine ; contour = trait de 1 px a 75 % a 1 px du verre, coins
 * arrondis, halo flou de 4 px a 45 %. Les flous sont calcules (loi normale) aux rangs du maillage.
 * <p>{@code f} = un pixel d'ecran en pixels d'interface (1 / echelle de l'interface) : largeur des bords adoucis.
 */
final class FormesHud {
    private FormesHud() {
    }

    /** Rayon du trou et rayon exterieur de l'anneau (12 px de diametre autour d'un disque de 8 px). */
    static final float R_TROU = 4f, R_EXT = 6f;
    /** Lueur de l'anneau : ecart type du flou (drop-shadow de 1,5 px) et opacite (0x88). */
    private static final float SIGMA = 0.75f, OMBRE = 0x88 / 255f;
    /** Opacites : piste vide (blanc), part perdue aux reparations (rouge), anneau d'une arme ruinee (rouge). */
    private static final float A_PISTE = 0.13f, A_PERDU = 0.55f, A_RUINEE = 0.45f;
    /** Disque du centre (opaque). */
    private static final int TROU = 0x111319;
    /** Pas du tour (40 : ecart au vrai cercle sous 0,1 pixel d'ecran a l'echelle 4). */
    private static final int SEGMENTS = 40;
    /** Contour de l'alerte : coins (centres a 0,5 px dans le verre), trait de 1,5 a 2,5 px du centre des coins, flou du halo. */
    private static final float C_TRAIT_IN = 1.5f, C_TRAIT_OUT = 2.5f, SIGMA_HALO = 2f;
    private static final int PAR_COIN = 5;
    /** Bord du verre (blanc, opacite 26 comme Ui.glass) et pas de ses coins. */
    private static final int BORD_VERRE = 26, PAR_COIN_VERRE = 3;

    /**
     * Anneau et lueur, centres sur l'origine. {@code part} et {@code partMax} : etat et etat maximal (0..1) ; la piste
     * va de l'un a l'autre, la part perdue de {@code partMax} au bout du tour. Arme ruinee : tout l'anneau en rouge.
     */
    static void anneau(Maillage m, float part, float partMax, int couleur, boolean ruinee, float f) {
        float p = borne(part), q = Math.max(p, borne(partMax));
        // regions du tour (en tours, depuis midi, sens horaire) : couleur et opacite de l'anneau
        float[] debut = ruinee ? new float[]{0} : new float[]{0, p, q};
        float[] fin = ruinee ? new float[]{1} : new float[]{p, q, 1};
        int[] rgb = ruinee ? new int[]{Ui.DANGER} : new int[]{couleur, 0xFFFFFF, Ui.DANGER};
        float[] alpha = ruinee ? new float[]{A_RUINEE} : new float[]{1f, A_PISTE, A_PERDU};
        int nr = debut.length;

        // demi-fenetres : un demi-pixel d'ecran au milieu de l'anneau (limites nettes), le flou de la lueur (plus doux)
        float dA = (float) (f / 2 / (2 * Math.PI * 5)), dO = (float) (SIGMA * Math.sqrt(3) / (2 * Math.PI * 6.5));
        float[] t = new float[SEGMENTS + 12];
        int n = 0;
        for (int i = 0; i < SEGMENTS; i++) t[n++] = i / (float) SEGMENTS;
        for (float b : new float[]{0, p, q}) {
            t[n++] = fract(b - dA);
            t[n++] = fract(b + dA);
            t[n++] = fract(b - dO);
            t[n++] = fract(b + dO);
        }
        Arrays.sort(t, 0, n);
        int k = 0;
        for (int i = 0; i < n; i++) if (k == 0 || t[i] - t[k - 1] > 1e-5f) t[k++] = t[i];
        n = k;

        // rangs : bord du trou (sous le disque), milieu, bord exterieur adouci sur un pixel d'ecran, lueur qui s'eteint
        float[] r = {R_TROU - f / 2, 5f, R_EXT - f / 2, R_EXT + f / 2, R_EXT + 0.75f, R_EXT + 1.75f};
        int dansAnneau = 3;
        float[] wa = new float[nr], wo = new float[nr];
        int cr = couleur >> 16 & 255, cg = couleur >> 8 & 255, cb = couleur & 255;
        m.taille(n + 1, r.length);
        for (int i = 0; i <= n; i++) {
            float ti = i < n ? t[i] : t[0] + 1;
            float sin = (float) Math.sin(2 * Math.PI * ti), cos = (float) Math.cos(2 * Math.PI * ti);
            for (int z = 0; z < nr; z++) {
                wa[z] = recouvre(ti, dA, debut[z], fin[z]);
                wo[z] = recouvre(ti, dO, debut[z], fin[z]);
            }
            for (int j = 0; j < r.length; j++) {
                float rj = r[j];
                float ar = 0, pr = 0, pg = 0, pb = 0, s = 0;
                for (int z = 0; z < nr; z++) {
                    if (j < dansAnneau && wa[z] > 0) {
                        float a = wa[z] * alpha[z];
                        ar += a;
                        pr += a * (rgb[z] >> 16 & 255);
                        pg += a * (rgb[z] >> 8 & 255);
                        pb += a * (rgb[z] & 255);
                    }
                    // lueur : flou du disque (opaque) et de l'anneau (opacite de la region)
                    if (wo[z] > 0) s += wo[z] * OMBRE * ((1 - alpha[z]) * phi((R_TROU - rj) / SIGMA) + alpha[z] * phi((R_EXT - rj) / SIGMA));
                }
                if (j == r.length - 1) s = 0;
                // anneau par-dessus sa lueur
                float aTot = ar + s * (1 - ar);
                float sous = s * (1 - ar);
                int c = aTot <= 0 ? 0 : couleur(
                        (pr + sous * cr) / aTot, (pg + sous * cg) / aTot, (pb + sous * cb) / aTot, aTot);
                m.sommet(i, j, sin * rj, -cos * rj, c);
            }
        }
    }

    /** Disque sombre du centre, bord adouci. */
    static void trou(Maillage m, float f) {
        float[] r = {0, R_TROU - f / 2, R_TROU + f / 2};
        int[] c = {0xFF000000 | TROU, 0xFF000000 | TROU, TROU};
        m.taille(SEGMENTS + 1, r.length);
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = 2 * Math.PI * i / SEGMENTS;
            float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
            for (int j = 0; j < r.length; j++) m.sommet(i, j, sin * r[j], -cos * r[j], c[j]);
        }
    }

    /**
     * Contour rouge de l'alerte autour du verre ({@code w} x {@code h}, coordonnees relatives a son coin haut-gauche) :
     * trait de 1 px separe du verre par 1 px, coins arrondis, halo flou au-dehors. {@code eclat} (0..1) l'avive (clic a
     * vide d'une arme ruinee).
     */
    static void contour(Maillage m, int w, int h, float f, float eclat) {
        float aTrait = 0.75f + 0.25f * eclat, aHalo = 0.45f + 0.35f * eclat;
        float[] r = {C_TRAIT_IN - f / 2, C_TRAIT_IN + f / 2, C_TRAIT_OUT - f / 2, C_TRAIT_OUT + f / 2, 3.5f, 4.5f, 5.5f, 7f};
        int[] c = new int[r.length];
        for (int j = 1; j < r.length - 1; j++) {
            float halo = aHalo * phi(-(Math.max(r[j], C_TRAIT_IN) - C_TRAIT_IN) / SIGMA_HALO);
            float a = j <= 2 ? aTrait + halo * (1 - aTrait) : halo;
            c[j] = couleur(Ui.DANGER >> 16 & 255, Ui.DANGER >> 8 & 255, Ui.DANGER & 255, a);
        }
        c[0] = Ui.DANGER;
        c[r.length - 1] = Ui.DANGER;
        tour(m, 0.5f, w, h, PAR_COIN, r, c);
    }

    /**
     * Bord du verre des ecrans de clan ({@code .verre} de la maquette : blanc a 10 % sur le verre, coins de rayon 1) pour
     * une pastille {@code w} x {@code h} : anneau d'un pixel autour du dedans, coins arrondis et adoucis. Le dedans
     * (verre) et le lisere du haut sont de simples rectangles ({@link HudUsure}). Remplace ici {@code Ui.glass}, dont les
     * coins coupes au pixel laissaient des encoches sombres sur une pastille si petite.
     */
    static void bordVerre(Maillage m, int w, int h, float f, int alphaVerre) {
        float aV = alphaVerre / 255f, aB = BORD_VERRE / 255f, a = aB + aV * (1 - aB);
        float sous = aV * (1 - aB);
        int c = couleur((255 * aB + (Ui.VERRE >> 16 & 255) * sous) / a, (255 * aB + (Ui.VERRE >> 8 & 255) * sous) / a,
                (255 * aB + (Ui.VERRE & 255) * sous) / a, a);
        tour(m, 1f, w, h, PAR_COIN_VERRE, new float[]{0, 1 - f / 2, 1 + f / 2}, new int[]{c, c, c & 0xFFFFFF});
    }

    /**
     * Maillage autour d'un rectangle {@code w} x {@code h} aux coins arrondis : centres des coins a {@code retrait} du
     * bord, rangs aux distances {@code r} de ces centres (vers le dehors), couleur {@code c} par rang.
     */
    private static void tour(Maillage m, float retrait, int w, int h, int parCoin, float[] r, int[] c) {
        float[] cx = {retrait, w - retrait, w - retrait, retrait}, cy = {retrait, retrait, h - retrait, h - retrait};
        int colonnes = 4 * (parCoin + 1);
        m.taille(colonnes + 1, r.length);
        for (int i = 0; i <= colonnes; i++) {
            int col = i % colonnes, coin = col / (parCoin + 1), s = col % (parCoin + 1);
            // normale vers le dehors : gauche, haut, droite, bas (sens horaire a l'ecran)
            double a = Math.PI * (1 + 0.5 * coin + 0.5 * s / parCoin);
            float nx = (float) Math.cos(a), ny = (float) Math.sin(a);
            for (int j = 0; j < r.length; j++) m.sommet(i, j, cx[coin] + nx * r[j], cy[coin] + ny * r[j], c[j]);
        }
    }

    /** Part de la fenetre [t - d, t + d] couverte par la region [debut, fin) du tour. */
    private static float recouvre(float t, float d, float debut, float fin) {
        if (fin <= debut) return 0;
        float tot = 0;
        for (int k = -1; k <= 1; k++) tot += Math.max(0, Math.min(t + d, fin + k) - Math.max(t - d, debut + k));
        return tot / (2 * d);
    }

    /** Fonction de repartition de la loi normale (Abramowitz et Stegun 7.1.26, erreur < 2e-7). */
    static float phi(float z) {
        double x = Math.abs(z) / Math.sqrt(2);
        double t = 1 / (1 + 0.3275911 * x);
        double erf = 1 - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return (float) (z >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf));
    }

    private static int couleur(float r, float g, float b, float a) {
        return Math.round(borne(a) * 255) << 24 | octet(r) << 16 | octet(g) << 8 | octet(b);
    }

    private static int octet(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static float borne(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }
}
