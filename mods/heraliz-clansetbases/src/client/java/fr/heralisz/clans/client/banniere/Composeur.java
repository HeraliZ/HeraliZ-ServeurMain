package fr.heralisz.clans.client.banniere;

/**
 * Composeur des bannieres v2 (spec docs/specs/BANNIERE_V2_SPEC.md §3), en Java pur : aucune classe Minecraft, sortie
 * dans un {@code int[]} ARGB, atlas SDF en {@code byte[]} (2048 x 2048, 8 x 8 tuiles de 256 px, valeur = 128 + d x 8).
 * L'algorithme est celui de la reference Python {@code tools/clans/formes_art.py --composer} (etapes A a D de son
 * en-tete), reproduit pas a pas pour la parite (programme {@code tools/clans/composeur_parite}).
 * <p>
 * Un calque est passe sous forme de {@value #CHAMPS} entiers consecutifs (voir les constantes {@code F_*}), ce qui
 * garde ce fichier compilable seul. Chaque calque ne parcourt que la boite englobante tournee de sa tuile.
 * <p>
 * Une instance garde ses tampons de travail (trois plans float, agrandis au besoin, jamais rendus) : une instance par
 * fil (rendu, fil de fond de l'atelier). Pas thread-safe.
 */
public final class Composeur {
    /** Canevas virtuel (unites) : fanion 64 x 96, corps rectangulaire 64 x 75. */
    public static final int LARGEUR = 64, HAUTEUR = 96, CORPS = 75;
    /** Atlas : tuile de 256 px, carre utile de 224 (marge 16), 8 colonnes, cote 2048, etalement du SDF +-16 px. */
    public static final int TUILE = 256, UTILE = 224, COLONNES = 8, ATLAS = 2048, ETALEMENT = 16;
    public static final int MAX_CALQUES = 12;
    /** Champs d'un calque emballe (miroirs : 0 ou 1). */
    public static final int F_FORME = 0, F_CX = 1, F_CY = 2, F_W = 3, F_H = 4, F_ROT = 5, F_COULEUR = 6, F_CONTOUR = 7,
            F_COULEUR_CONTOUR = 8, F_MIROIR_X = 9, F_MIROIR_Y = 10, CHAMPS = 11;
    /** Ordinaux des degrades (meme ordre que Banniere.Degrade). */
    public static final int VERTICAL = 0, DIAGONAL = 1, RADIAL = 2, PARTAGE = 3, UNI = 4;

    private float[] pr = new float[0], pg = new float[0], pb = new float[0];

    private void reserver(int n) {
        if (pr.length >= n) return;
        pr = new float[n];
        pg = new float[n];
        pb = new float[n];
    }

    // ================================================================== banniere (fanion)

    /**
     * Banniere entiere dans {@code sortie} (W x H, ARGB, ligne par ligne) : fond, calques, decoupe du fanion.
     *
     * @param calques {@code n} calques emballes ({@value #CHAMPS} entiers chacun), du fond vers le dessus
     * @param atlas   atlas SDF, ou null (fond seul)
     */
    public void banniere(int[] sortie, int W, int H, int a, int b, int degrade, int[] calques, int n, byte[] atlas) {
        int np = W * H;
        reserver(np);
        // A. fond (entier, identique a l'ancien BannerArt, sans l'ombre des bords)
        for (int y = 0; y < H; y++) {
            float v = (y + 0.5f) / H;
            for (int x = 0; x < W; x++) {
                float u = (x + 0.5f) / W;
                fondPixel(y * W + x, x, y, a, b, ratioBanniere(degrade, u, v));
            }
        }
        // C. calques
        float sx = W / (float) LARGEUR, sy = H / (float) HAUTEUR;
        if (atlas != null) {
            for (int i = 0; i < n; i++) calque(calques, i * CHAMPS, atlas, W, H, sx, sy, 0, 0, false, 0, 0, 0, 0);
        }
        // B + D. decoupe du fanion et sortie
        double[] ax0 = aretes[0], ay0 = aretes[1], anx = aretes[2], any = aretes[3];
        for (int s = 0; s < 5; s++) {
            int t = (s + 1) % 5;
            double x0 = PENTA_X[s] * sx, y0 = PENTA_Y[s] * sy, ex = PENTA_X[t] * sx - x0, ey = PENTA_Y[t] * sy - y0;
            double L = Math.sqrt(ex * ex + ey * ey);
            // sommets dans le sens horaire a l'ecran (y vers le bas) : normale interieure (-ey, ex) / L
            ax0[s] = x0;
            ay0[s] = y0;
            anx[s] = -ey / L;
            any[s] = ex / L;
        }
        // au-dessus de la pointe (2 px de marge), dF = min(haut, gauche, droite, diagonales) >= 0,5 : alpha plein
        int yPointe = Math.max(0, Math.min(H, (int) Math.floor(CORPS * sy - 2)));
        for (int y = 0; y < yPointe; y++) {
            int k = y * W;
            for (int x = 0; x < W; x++, k++) sortie[k] = 0xFF000000 | canaux(k);
        }
        for (int y = yPointe; y < H; y++) {
            double cyp = y + 0.5;
            for (int x = 0; x < W; x++) {
                double cxp = x + 0.5;
                double dF = Double.POSITIVE_INFINITY;
                for (int s = 0; s < 5; s++) {
                    double d = (cxp - ax0[s]) * anx[s] + (cyp - ay0[s]) * any[s];
                    if (d < dF) dF = d;
                }
                int alpha = (int) (lisse(dF) * 255 + 0.5);
                int k = y * W + x;
                sortie[k] = alpha <= 0 ? 0 : (Math.min(255, alpha) << 24) | canaux(k);
            }
        }
    }

    private static final double[] PENTA_X = {0, 64, 64, 32, 0}, PENTA_Y = {0, 0, 75, 96, 75};
    /** Aretes du fanion en pixels de destination (x0, y0, normale interieure), recalculees a chaque banniere. */
    private final double[][] aretes = new double[4][5];

    /** Position dans le degrade du fanion (0 = A, 1 = B) : formules de l'ancien BannerArt, en float. */
    public static float ratioBanniere(int d, float u, float v) {
        return switch (d) {
            case VERTICAL -> v;
            case DIAGONAL -> (u + v) / 2f;
            case RADIAL -> Math.min(1, (float) Math.sqrt((u - 0.5f) * (u - 0.5f) * 1.3f + (v - 0.42f) * (v - 0.42f)) * 1.9f);
            case PARTAGE -> u < 0.5f ? 0 : 1;
            default -> 0;
        };
    }

    // ================================================================== tissu du drapeau 3D

    /**
     * Tissu du drapeau 3D (paysage, u = 0 cote mat, v = 0 en haut) : degrade sur tout le tissu, composition (zone
     * rectangulaire 64 x 75 du canevas, sans la pointe) centree sur toute la hauteur et decoupee a ce rectangle,
     * ourlet plus sombre cote mat, bords libres legerement assombris. Opaque.
     */
    public void tissu(int[] sortie, int W, int H, int a, int b, int degrade, int[] calques, int n, byte[] atlas) {
        int np = W * H;
        reserver(np);
        for (int y = 0; y < H; y++) {
            float v = (y + 0.5f) / H;
            for (int x = 0; x < W; x++) {
                float u = (x + 0.5f) / W;
                fondPixel(y * W + x, x, y, a, b, ratioTissu(degrade, u, v));
            }
        }
        float s = H / (float) CORPS;
        float ox = (W - LARGEUR * s) / 2f;
        if (atlas != null) {
            for (int i = 0; i < n; i++) {
                calque(calques, i * CHAMPS, atlas, W, H, s, s, ox, 0, true, ox, 0, ox + LARGEUR * s, H);
            }
        }
        // ourlet cote mat (6 px a 256) et bords libres (4 px), proportionnels a la taille
        float ourlet = W * 6f / 256f, bord = W * 4f / 256f;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float ombre = 1f;
                if (x < ourlet) ombre = 0.78f + 0.21f * (x / ourlet);
                else if (x >= W - bord || y < bord || y >= H - bord) ombre = 0.9f;
                int k = y * W + x;
                pr[k] *= ombre;
                pg[k] *= ombre;
                pb[k] *= ombre;
                sortie[k] = 0xFF000000 | canaux(k);
            }
        }
    }

    /** Degrade du tissu (format paysage) : memes styles, radial centre et elargi. */
    public static float ratioTissu(int d, float u, float v) {
        return switch (d) {
            case VERTICAL -> v;
            case DIAGONAL -> (u + v) / 2f;
            case RADIAL -> Math.min(1, (float) Math.sqrt((u - 0.5f) * (u - 0.5f) * 1.6f + (v - 0.5f) * (v - 0.5f)) * 1.9f);
            case PARTAGE -> u < 0.5f ? 0 : 1;
            default -> 0;
        };
    }

    // ================================================================== forme seule (vignettes)

    /**
     * Vignette d'une forme seule, blanche sur transparent, dans un carre S x S : la plus grande dimension de sa boite
     * (proportions w:h) fait S px, centree (comme forme_seule() de la reference). Alpha = couverture.
     */
    public void forme(int[] sortie, int S, int forme, int w, int h, byte[] atlas) {
        int np = S * S;
        reserver(np);
        java.util.Arrays.fill(pr, 0, np, 0f);
        java.util.Arrays.fill(pg, 0, np, 0f);
        java.util.Arrays.fill(pb, 0, np, 0f);
        if (atlas != null) {
            float m = Math.max(1, Math.max(w, h));
            // centre et taille en pixels (sx = sy = 1), valeurs flottantes
            if (forme >= 0 && forme < COLONNES * COLONNES) calqueFlottant(forme, S / 2f, S / 2f, S * w / m, S * h / m, atlas, S, S);
        }
        for (int k = 0; k < np; k++) {
            int al = (int) (pr[k] * 255 + 0.5f);
            sortie[k] = al <= 0 ? 0 : (Math.min(255, al) << 24) | 0xFFFFFF;
        }
    }

    /** Couverture d'une forme seule (centre et taille flottants, sans rotation) ecrite dans le plan rouge. */
    private void calqueFlottant(int forme, float cx, float cy, float w, float h, byte[] atlas, int W, int H) {
        double hw = w / 2.0 * TUILE / UTILE, hh = h / 2.0 * TUILE / UTILE;
        int x0 = Math.max(0, (int) Math.floor(cx - hw)), x1 = Math.min(W, (int) Math.ceil(cx + hw));
        int y0 = Math.max(0, (int) Math.floor(cy - hh)), y1 = Math.min(H, (int) Math.ceil(cy + hh));
        double A1 = UTILE / (double) w, E1 = UTILE / (double) h;
        double kRepli = 1 / Math.sqrt(Math.abs(A1 * E1));
        int base = (forme / COLONNES) * TUILE * ATLAS + (forme % COLONNES) * TUILE;
        for (int y = y0; y < y1; y++) {
            double tv = 128 + (y + 0.5 - cy) * E1;
            if (tv < 0 || tv >= TUILE) continue;
            for (int x = x0; x < x1; x++) {
                double tu = 128 + (x + 0.5 - cx) * A1;
                if (tu < 0 || tu >= TUILE) continue;
                double dk = distanceTuile(atlas, base, tu, tv, A1, 0, 0, E1, kRepli);
                if (dk == Double.NEGATIVE_INFINITY) continue;
                pr[y * W + x] = (float) lisse(dk);
            }
        }
    }

    // ================================================================== calque

    /** Resultats du dernier echantillonnage (d en px de tuile, k = facteur tuile -> destination). */
    private double sD, sK;

    /**
     * Echantillonne la tuile en (tu, tv) (bilineaire borne a la tuile), calcule d et k (etape C) ; rend d x k (distance
     * en px de destination), ou -infini si les 4 texels valent 0 (plus de 16 px hors de la forme : rien a peindre,
     * contour compris).
     */
    private double distanceTuile(byte[] atlas, int base, double tu, double tv, double ja, double jb, double jc, double je,
                                 double kRepli) {
        double fu = tu - 0.5, fv = tv - 0.5;
        int i0 = (int) (fu + 1) - 1, j0 = (int) (fv + 1) - 1;       // floor pour fu >= -0,5
        double ax = fu - i0, ay = fv - j0;
        int i1 = Math.min(i0 + 1, TUILE - 1), j1 = Math.min(j0 + 1, TUILE - 1);
        if (i0 < 0) i0 = 0;
        if (j0 < 0) j0 = 0;
        int l0 = base + j0 * ATLAS, l1 = base + j1 * ATLAS;
        int p00 = atlas[l0 + i0] & 255, p10 = atlas[l0 + i1] & 255, p01 = atlas[l1 + i0] & 255, p11 = atlas[l1 + i1] & 255;
        if ((p00 | p10 | p01 | p11) == 0) return Double.NEGATIVE_INFINITY;
        double val = (p00 * (1 - ax) + p10 * ax) * (1 - ay) + (p01 * (1 - ax) + p11 * ax) * ay;
        double d = (val - 128) / 8;
        double gu = ((1 - ay) * (p10 - p00) + ay * (p11 - p01)) / 8;
        double gv = ((1 - ax) * (p01 - p00) + ax * (p11 - p10)) / 8;
        double gn = Math.sqrt(gu * gu + gv * gv);
        double k;
        if (gn > 0.05) {
            double nu = gu / gn, nv = gv / gn;
            double tx = ja * nu + jc * nv, ty = jb * nu + je * nv;
            k = 1 / Math.max(Math.sqrt(tx * tx + ty * ty), 1e-12);
        } else {
            k = kRepli;
        }
        sD = d;
        sK = k;
        return d * k;
    }

    /**
     * Peint un calque (etape C) dans les plans de travail. Destination -> canevas : X = (x + 0,5 - ox) / sx,
     * Y = (y + 0,5 - oy) / sy. Avec {@code decoupe}, la couverture est multipliee par celle du rectangle
     * [rx0, rx1] x [ry0, ry1] (px de destination, bord anticrenele).
     */
    private void calque(int[] c, int o, byte[] atlas, int W, int H, float sx, float sy, float ox, float oy,
                        boolean decoupe, float rx0, float ry0, float rx1, float ry1) {
        int forme = c[o + F_FORME];
        if (forme < 0 || forme >= COLONNES * COLONNES) return;
        double cx = c[o + F_CX], cy = c[o + F_CY], w = Math.max(1, c[o + F_W]), h = Math.max(1, c[o + F_H]);
        double th = Math.toRadians(c[o + F_ROT]);
        double co = Math.cos(th), si = Math.sin(th);
        double mx = c[o + F_MIROIR_X] != 0 ? -1 : 1, my = c[o + F_MIROIR_Y] != 0 ? -1 : 1;
        // boite englobante : tuile entiere, tournee, en pixels de destination
        double hw = w / 2 * TUILE / UTILE, hh = h / 2 * TUILE / UTILE;
        double ex = Math.abs(co) * hw + Math.abs(si) * hh, ey = Math.abs(si) * hw + Math.abs(co) * hh;
        int x0 = Math.max(0, (int) Math.floor((cx - ex) * sx + ox)), x1 = Math.min(W, (int) Math.ceil((cx + ex) * sx + ox));
        int y0 = Math.max(0, (int) Math.floor((cy - ey) * sy + oy)), y1 = Math.min(H, (int) Math.ceil((cy + ey) * sy + oy));
        if (decoupe) {
            x0 = Math.max(x0, (int) Math.floor(rx0));
            x1 = Math.min(x1, (int) Math.ceil(rx1));
            y0 = Math.max(y0, (int) Math.floor(ry0));
            y1 = Math.min(y1, (int) Math.ceil(ry1));
        }
        if (x0 >= x1 || y0 >= y1) return;
        // tu = 128 + A1 dx + B1 dy ; tv = 128 + C1 dx + E1 dy (dx, dy en unites du canevas)
        double A1 = mx * co * UTILE / w, B1 = mx * si * UTILE / w, C1 = -my * si * UTILE / h, E1 = my * co * UTILE / h;
        // jacobienne tuile <- destination
        double ja = A1 / sx, jb = B1 / sy, jc = C1 / sx, je = E1 / sy;
        double kRepli = 1 / Math.sqrt(Math.abs(ja * je - jb * jc));
        double T = c[o + F_CONTOUR] * 0.5 * Math.sqrt(sx * (double) sy);
        boolean contour = T > 0;
        int coul = c[o + F_COULEUR], cont = c[o + F_COULEUR_CONTOUR];
        float cr = (coul >> 16) & 255, cg = (coul >> 8) & 255, cb = coul & 255;
        float kr = (cont >> 16) & 255, kg = (cont >> 8) & 255, kb = cont & 255;
        int base = (forme / COLONNES) * TUILE * ATLAS + (forme % COLONNES) * TUILE;
        // bornes de k sur toutes les directions (valeurs singulieres de J) : k dans [1 / smax, 1 / smin]. Elles donnent
        // des raccourcis exacts (meme resultat que le calcul complet) : interieur franc -> aS = 1, exterieur franc -> rien.
        double S2 = ja * ja + jb * jb + jc * jc + je * je, det = ja * je - jb * jc;
        double smax = Math.sqrt((S2 + Math.sqrt(Math.max(0, S2 * S2 - 4 * det * det))) / 2);
        double kMin = 1 / Math.max(smax, 1e-12);
        // texel minimal garantissant aS = 1 : (p - 128) / 8 x kMin >= 0,5 ; texel maximal garantissant le vide :
        // (p - 128) / 8 x kMin + T <= -0,5 (T = 0 sans contour)
        double pPlein = 128 + 8 * 0.5 / kMin, pVide = 128 + 8 * (-0.5 - T) / kMin;
        // pas en x (une colonne de destination) dans la tuile
        double pasU = A1 / sx, pasV = C1 / sx;
        for (int y = y0; y < y1; y++) {
            double dy = (y + 0.5 - oy) / sy - cy;
            double qy = 1;
            if (decoupe) qy = lisse(Math.min(y + 0.5 - ry0, ry1 - (y + 0.5)));
            int ligne = y * W;
            double dx0 = (x0 + 0.5 - ox) / sx - cx;
            double tu = 128 + A1 * dx0 + B1 * dy - pasU, tv = 128 + C1 * dx0 + E1 * dy - pasV;
            for (int x = x0; x < x1; x++) {
                tu += pasU;
                tv += pasV;
                if (tu < 0 || tu >= TUILE || tv < 0 || tv >= TUILE) continue;
                double fu = tu - 0.5, fv = tv - 0.5;
                int i0 = (int) (fu + 1) - 1, j0 = (int) (fv + 1) - 1;       // floor pour fu >= -0,5
                double ax = fu - i0, ay = fv - j0;
                int i1 = Math.min(i0 + 1, TUILE - 1), j1 = Math.min(j0 + 1, TUILE - 1);
                if (i0 < 0) i0 = 0;
                if (j0 < 0) j0 = 0;
                int l0 = base + j0 * ATLAS, l1 = base + j1 * ATLAS;
                int p00 = atlas[l0 + i0] & 255, p10 = atlas[l0 + i1] & 255, p01 = atlas[l1 + i0] & 255, p11 = atlas[l1 + i1] & 255;
                int pmax = Math.max(Math.max(p00, p10), Math.max(p01, p11));
                if (pmax <= pVide) continue;
                int pmin = Math.min(Math.min(p00, p10), Math.min(p01, p11));
                double aS, aC = 0;
                if (pmin >= pPlein) {
                    aS = 1;
                } else {
                    double d = ((p00 * (1 - ax) + p10 * ax) * (1 - ay) + (p01 * (1 - ax) + p11 * ax) * ay - 128) / 8;
                    double gu = ((1 - ay) * (p10 - p00) + ay * (p11 - p01)) / 8;
                    double gv = ((1 - ax) * (p01 - p00) + ax * (p11 - p10)) / 8;
                    double g2 = gu * gu + gv * gv;
                    double k;
                    if (g2 > 0.0025) {      // |g| > 0,05
                        double gn = Math.sqrt(g2);
                        double nu = gu / gn, nv = gv / gn;
                        double tx = ja * nu + jc * nv, ty = jb * nu + je * nv;
                        k = 1 / Math.max(Math.sqrt(tx * tx + ty * ty), 1e-12);
                    } else {
                        k = kRepli;
                    }
                    aS = lisse(d * k);
                    if (contour) {
                        double tt = Math.max(Math.min(T / k, ETALEMENT - 0.5 / k), 0);
                        aC = lisse((d + tt) * k);
                    }
                }
                if (decoupe) {
                    double q = qy * lisse(Math.min(x + 0.5 - rx0, rx1 - (x + 0.5)));
                    aS *= q;
                    aC *= q;
                }
                if (aS <= 0 && aC <= 0) continue;
                int k = ligne + x;
                float r = pr[k], g = pg[k], b = pb[k];
                if (aC > 0) {
                    float fa = (float) aC, fi = 1 - fa;
                    r = r * fi + kr * fa;
                    g = g * fi + kg * fa;
                    b = b * fi + kb * fa;
                }
                float fa = (float) aS, fi = 1 - fa;
                pr[k] = r * fi + cr * fa;
                pg[k] = g * fi + cg * fa;
                pb[k] = b * fi + cb * fa;
            }
        }
    }

    // ================================================================== test de clic

    /**
     * Distance signee (unites du canevas, interieur positif, contour compris) du point (X, Y) au bord du calque
     * {@code o} ; -infini hors de la tuile ou a plus de 16 px de tuile de la forme. Sert a la selection au clic.
     */
    public double distanceCanevas(int[] c, int o, byte[] atlas, double X, double Y) {
        int forme = c[o + F_FORME];
        if (atlas == null || forme < 0 || forme >= COLONNES * COLONNES) return Double.NEGATIVE_INFINITY;
        double w = Math.max(1, c[o + F_W]), h = Math.max(1, c[o + F_H]);
        double th = Math.toRadians(c[o + F_ROT]);
        double co = Math.cos(th), si = Math.sin(th);
        double mx = c[o + F_MIROIR_X] != 0 ? -1 : 1, my = c[o + F_MIROIR_Y] != 0 ? -1 : 1;
        double A1 = mx * co * UTILE / w, B1 = mx * si * UTILE / w, C1 = -my * si * UTILE / h, E1 = my * co * UTILE / h;
        double dx = X - c[o + F_CX], dy = Y - c[o + F_CY];
        double tu = 128 + A1 * dx + B1 * dy, tv = 128 + C1 * dx + E1 * dy;
        if (tu < 0 || tu >= TUILE || tv < 0 || tv >= TUILE) return Double.NEGATIVE_INFINITY;
        int base = (forme / COLONNES) * TUILE * ATLAS + (forme % COLONNES) * TUILE;
        double dk = distanceTuile(atlas, base, tu, tv, A1, B1, C1, E1, 1 / Math.sqrt(Math.abs(A1 * E1 - B1 * C1)));
        if (dk == Double.NEGATIVE_INFINITY) return dk;
        // meme borne que le dessin (etape C) : le contour ne depasse jamais l'etalement du SDF (sK = unites par px de tuile)
        return dk + Math.min(c[o + F_CONTOUR] * 0.5, Math.max(0, ETALEMENT - 0.5) * sK);
    }

    // ================================================================== outils

    /** Fond d'un pixel : Ui.mix (troncature) puis grain +-5 deterministe. */
    private void fondPixel(int k, int x, int y, int a, int b, float t) {
        int n = (hash(x, y) % 11) - 5;
        float it = 1 - t;
        pr[k] = clamp((int) (((a >> 16) & 255) * it + ((b >> 16) & 255) * t) + n);
        pg[k] = clamp((int) (((a >> 8) & 255) * it + ((b >> 8) & 255) * t) + n);
        pb[k] = clamp((int) ((a & 255) * it + (b & 255) * t) + n);
    }

    /** RGB 24 bits arrondi depuis les plans de travail. */
    private int canaux(int k) {
        return (clamp((int) (pr[k] + 0.5f)) << 16) | (clamp((int) (pg[k] + 0.5f)) << 8) | clamp((int) (pb[k] + 0.5f));
    }

    /** smoothstep(-0,5 ; 0,5 ; v). */
    public static double lisse(double v) {
        double t = v + 0.5;
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t * t * (3 - 2 * t);
    }

    public static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    /** ARGB -> ABGR (ordre des octets R, G, B, A en memoire little-endian, attendu par GL_RGBA). */
    public static int versAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }
}
