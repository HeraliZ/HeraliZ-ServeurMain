import fr.heraliz.loot.client.rendu.conteneurs.Allegement;
import fr.heraliz.loot.client.rendu.conteneurs.AtlasConteneurs;
import fr.heraliz.loot.client.rendu.conteneurs.Conteneurs;
import fr.heraliz.loot.client.rendu.mesh.Mesh;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Outil (pas dans le jeu) : version allégée « C » des conteneurs de HeraliZ Loot, pour modules/loot/outils/lod_props.py.
 * Compilé avec le seul code des modèles (Java pur, sans Minecraft) et JOML ; la simplification est celle du jeu
 * ({@link Allegement#simplifier}), appelée telle quelle.
 * <p>
 * Pour chaque conteneur (maillage cuit par le jeu, {@link Conteneurs#maillages}) :
 * <ol>
 * <li><b>faces cachées</b> : depuis des points répartis sur chaque face opaque, {@code DIRS} rayons vers son avant (sens
 * des sommets, comme la carte graphique qui ne dessine pas l'envers). Un rayon est arrêté par l'avant d'une face opaque
 * sur une partie pleine de sa texture (découpe : seuil 0,5) ; le verre et l'envers laissent passer. Une face dont aucun
 * rayon ne sort est retirée ; une face dont les seuls rayons qui sortent passent sous le bloc (plan y = 0, dans le carré
 * du bloc) va dans la liste « dessous » (cachée par Minecraft quand le bloc d'en dessous est plein) ;</li>
 * <li><b>contrôle des faces cachées</b> : images « numéro de face » vues de {@code VUES} directions (sphère entière, avec
 * et sans bloc plein dessous) : le modèle allégé doit montrer exactement les mêmes faces que le complet ;</li>
 * <li><b>simplification</b> : regroupement des sommets ({@link Allegement#simplifier}), puis contrôles, et les cases en
 * défaut sont gelées (leurs sommets ne bougent plus), jusqu'à ce qu'il n'y ait plus rien : face retournée ou tordue
 * (triangle qui tourne de plus de 37°), deux faces collées l'une sur l'autre (scintillement) ou passées l'une derrière
 * l'autre, pièce aplatie (épaisseur entre deux faces en vis-à-vis fondue de plus de moitié : paroi, rebord), sommet décollé d'une face qu'il touchait (fente), petite pièce effacée (moins de 70 % de sa surface), puis
 * modèle percé ({@code VUES_GEL} vues : on voit le fond, ou à plus de 1 px derrière, au milieu d'une surface du complet) ;</li>
 * <li><b>contrôle de la simplification</b> : percées sur {@code VUES} autres vues, faces dégénérées, retournées ou
 * collées, écart moyen des couleurs sur les rendus.</li>
 * </ol>
 * Sorties : {@code conteneurs_lod.txt} (données du jeu, {@link Allegement#ecrire}), {@code lod.csv}, et sur demande des
 * rendus PNG (actuel | C | écart ×4 ; de près, de loin, de dos) en moteur logiciel (textures, couleurs cuites, ombrage
 * des blocs, sans la lumière du monde).
 * <pre>java -cp &lt;classes&gt;:&lt;joml&gt; LodConteneurs &lt;textures&gt; &lt;sortie&gt; &lt;rendus: nom,nom|-&gt; [nom...]</pre>
 */
public final class LodConteneurs {
    private static final int S = Mesh.STRIDE;
    private static final int DIRS = 256;
    private static final int VUES = 96;
    private static final int VUES_REPRISE = 300;
    private static final int VUES_GEL = 150;
    /** Face tordue : un de ses triangles tourne de plus de 37° (cosinus 0,8). */
    private static final float ROTATION = 0.8f;
    /** Petite pièce effacée : il lui reste moins de 70 % de sa surface visible. */
    private static final float SURFACE = 0.7f;
    /** Contact perdu : un sommet posé sur une face s'en écarte ou sort de son contour de plus de 0,15 px. */
    private static final float CONTACT = 0.15f;
    private static final float ALPHA = 0.5f;
    private static final float CELLULE = Allegement.CELLULE;
    private static final int TOURS = 16;

    private static BufferedImage[] pages;

    public static void main(String[] args) throws IOException {
        if (args[0].equals("--controle")) {
            System.exit(controle(new File(args[1])));
            return;
        }
        File textures = new File(args[0]);
        File sortie = new File(args[1]);
        List<String> rendus = args[2].equals("-") ? List.of() : Arrays.asList(args[2].split(","));
        List<String> noms = new ArrayList<>();
        for (int i = 3; i < args.length; i++) noms.add(args[i]);
        if (noms.isEmpty()) for (String n : Conteneurs.noms()) noms.add(n);
        sortie.mkdirs();
        pages = new BufferedImage[AtlasConteneurs.NOMS.length];
        for (int k = 0; k < pages.length; k++) pages[k] = ImageIO.read(new File(textures, AtlasConteneurs.NOMS[k] + ".png"));
        File dossierRendus = new File(sortie, "rendus");
        if (!rendus.isEmpty()) dossierRendus.mkdirs();

        try (PrintWriter csv = new PrintWriter(new File(sortie, "lod.csv"), StandardCharsets.UTF_8);
             PrintWriter txt = new PrintWriter(new File(sortie, "conteneurs_lod.txt"), StandardCharsets.UTF_8)) {
            csv.println("nom,faces,verre,cachees,dessous,reprises,apres_a,apres_c,gel,tours,ecarts_a,trous_c,retournees,collees,degenerees,ecart_c,sens_inverse");
            txt.println("# Conteneurs de HeraliZ Loot : faces cachées et simplification « C » (Allegement.java).");
            txt.println("# ÉCRIT par python modules/loot/outils/lod_props.py --installer : jamais à la main.");
            txt.printf(Locale.ROOT, "# cellule %s px ; colonnes : nom faces C|A retirées dessous gel empreinte%n", CELLULE);
            for (String nom : noms) {
                Mesh[] m = Conteneurs.maillages(nom);
                if (m == null) continue;
                Geo plein = new Geo(m[0], m[1], null);
                int[] classe = plein.visibilite();
                int reprises = plein.reprendre(classe);
                boolean[] retiree = new boolean[plein.n], dessous = new boolean[plein.n];
                int nr = 0, nd = 0;
                for (int q = 0; q < plein.n; q++) {
                    retiree[q] = classe[q] == 2;
                    dessous[q] = classe[q] == 1;
                    if (retiree[q]) nr++;
                    if (dessous[q]) nd++;
                }
                // contrôle A : mêmes faces vues (sans bloc dessous : retirées seules ; avec : retirées + dessous)
                Allegement.Resultat ra = Allegement.simplifier(m[0], retiree, dessous, null, 0);
                Geo ga = new Geo(ra, m[1], plein);
                int ecartsA = plein.ecartsVues(ga, false) + plein.ecartsVues(ga.sans(ra.dessous().vertexCount() / 4), true);
                // C : regroupement, gel des cases en défaut, jusqu'à ce qu'il n'y ait plus rien
                Set<Integer> gel = new LinkedHashSet<>();
                Allegement.Resultat rc = null;
                int tours = 0;
                boolean propre = false;
                while (tours < TOURS) {
                    tours++;
                    rc = Allegement.simplifier(m[0], retiree, dessous, liste(gel), CELLULE);
                    Set<Integer> neufs = new TreeSet<>(new Controle(plein, retiree, rc).aGeler());
                    // plus rien à reprocher face par face : on regarde (vues) si le modèle est percé quelque part
                    if (neufs.isEmpty()) plein.percees(new Geo(rc, m[1], plein), fibonacci(VUES_GEL), neufs, null);
                    if (neufs.isEmpty()) {
                        propre = true;
                        break;
                    }
                    // rien de nouveau à geler : le défaut reste, modèle gardé sans simplification
                    if (!gel.addAll(neufs)) break;
                }
                int[] reps = representants(m[0], liste(gel));
                rc = Allegement.simplifier(m[0], retiree, dessous, reps, CELLULE);
                Controle fin = new Controle(plein, retiree, rc);
                propre &= fin.aGeler().isEmpty();
                Geo gc = new Geo(rc, m[1], plein);
                BufferedImage[] imgTrous = rendus.contains(nom) ? new BufferedImage[1] : null;
                int trous = plein.trous(gc, imgTrous);
                if (imgTrous != null && imgTrous[0] != null) ImageIO.write(imgTrous[0], "png", new File(dossierRendus, nom + "_trous.png"));
                Allegement.Fiche f = new Allegement.Fiche(plein.n, indices(retiree), indices(dessous), reps, propre, rc.empreinte());
                // le jeu, avec la même fiche, doit retrouver exactement le même résultat
                Allegement.Version v = Allegement.calculer(m[0], f);
                int attendu = propre ? rc.origineGarde().length + rc.origineDessous().length : plein.n - nr;
                if (v == null || v.simplifiee() != propre || v.faces() != attendu) {
                    throw new IllegalStateException(nom + " : le calcul du jeu ne retrouve pas celui de l'outil");
                }
                txt.println(Allegement.ecrire(nom, f));
                int nc = propre ? v.faces() : plein.n - nr;
                double ecart = 0;
                if (rendus.contains(nom)) ecart = rendus(dossierRendus, nom, plein, gc);
                csv.printf(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%d%n", nom, plein.n, plein.nVerre, nr, nd, reprises,
                        plein.n - nr, nc, reps.length, tours, ecartsA, trous, fin.retournees, fin.collees, fin.degenerees, ecart,
                        plein.sensInverse);
                System.out.printf("%s : %d faces, %d cachees, %d dessous, C %d (%s), gel %d, ecarts A %d, trous C %d%n", nom, plein.n,
                        nr, nd, nc, propre ? "ok" : "A seul", reps.length, ecartsA, trous);
            }
        }
    }

    /**
     * Contrôle rapide des données installées (sans rayons ni vues) : chaque conteneur a sa fiche, et le calcul du jeu
     * retrouve exactement l'empreinte écrite (sinon le jeu garderait ce modèle moins allégé). 0 : à jour, 1 : à refaire.
     */
    private static int controle(File donnees) throws IOException {
        Map<String, Allegement.Fiche> fiches;
        try (java.io.Reader r = new java.io.InputStreamReader(new java.io.FileInputStream(donnees), StandardCharsets.UTF_8)) {
            fiches = Allegement.lire(r);
        }
        int erreurs = 0;
        for (String nom : Conteneurs.noms()) {
            Mesh[] m = Conteneurs.maillages(nom);
            Allegement.Fiche f = fiches.get(nom);
            Allegement.Version v = f == null ? null : Allegement.calculer(m[0], f);
            String pb = f == null ? "absent des données" : v == null ? "nombre de faces changé" : f.simplifier() && !v.simplifiee()
                    ? "maillage changé (empreinte)" : null;
            if (pb != null) {
                System.out.println("[lod] " + nom + " : " + pb);
                erreurs++;
            }
        }
        for (String nom : fiches.keySet()) {
            if (!Conteneurs.existe(nom)) {
                System.out.println("[lod] " + nom + " : n'existe plus");
                erreurs++;
            }
        }
        return erreurs == 0 ? 0 : 1;
    }

    private static int[] liste(Set<Integer> s) {
        int[] r = new int[s.size()];
        int i = 0;
        for (int v : s) r[i++] = v;
        Arrays.sort(r);
        return r;
    }

    private static int[] indices(boolean[] b) {
        int k = 0;
        for (boolean x : b) if (x) k++;
        int[] r = new int[k];
        k = 0;
        for (int i = 0; i < b.length; i++) if (b[i]) r[k++] = i;
        return r;
    }

    /** Un sommet par case gelée (le jeu gèle toute la case d'un sommet listé). */
    private static int[] representants(Mesh m, int[] gel) {
        int[] comp = Allegement.composantes(m.data(), m.vertexCount() / 4);
        Map<Long, Integer> parCase = new HashMap<>();
        for (int v : gel) parCase.merge(Allegement.cle(m.data(), v * S, comp[v / 4], CELLULE), v, Math::min);
        TreeSet<Integer> r = new TreeSet<>(parCase.values());
        return r.stream().mapToInt(Integer::intValue).toArray();
    }

    private static double rendus(File dossier, String nom, Geo plein, Geo c) throws IOException {
        BufferedImage planche = new BufferedImage(3 * 360, 360 + 152 + 360, BufferedImage.TYPE_INT_RGB);
        BufferedImage a = plein.rendu(360, 2, 35, 28), b = c.rendu(360, 2, 35, 28);
        copier(planche, a, 0, 0, 1);
        copier(planche, b, 360, 0, 1);
        double ecart = ecart(a, b, planche, 720, 0);
        BufferedImage la = plein.rendu(48, 8, 35, 28), lb = c.rendu(48, 8, 35, 28);
        copier(planche, la, 108, 364, 3);
        copier(planche, lb, 360 + 108, 364, 3);
        ecart(la, lb, planche, 720 + 108, 364, 3);
        BufferedImage da = plein.rendu(360, 2, 215, 8), db = c.rendu(360, 2, 215, 8);
        copier(planche, da, 0, 512, 1);
        copier(planche, db, 360, 512, 1);
        ecart(da, db, planche, 720, 512);
        ImageIO.write(planche, "png", new File(dossier, nom + ".png"));
        // tout près (modèle sur 760 points) : actuel | C
        BufferedImage pres = new BufferedImage(2 * 760, 760, BufferedImage.TYPE_INT_RGB);
        copier(pres, plein.rendu(760, 2, 35, 28), 0, 0, 1);
        copier(pres, c.rendu(760, 2, 35, 28), 760, 0, 1);
        ImageIO.write(pres, "png", new File(dossier, nom + "_pres.png"));
        return ecart;
    }

    private static double ecart(BufferedImage a, BufferedImage b, BufferedImage dst, int x0, int y0) {
        return ecart(a, b, dst, x0, y0, 1);
    }

    /** Écart des couleurs (×4, en clair sur fond noir), et sa moyenne (0-255) sur les points où l'un des deux a le modèle. */
    private static double ecart(BufferedImage a, BufferedImage b, BufferedImage dst, int x0, int y0, int k) {
        long somme = 0, cnt = 0;
        int fond = a.getRGB(0, 0);
        BufferedImage e = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                int dr = Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)), dg = Math.abs(((p >> 8) & 255) - ((q >> 8) & 255));
                int db = Math.abs((p & 255) - (q & 255));
                int dmax = Math.max(dr, Math.max(dg, db));
                if ((p & 0xFFFFFF) != (fond & 0xFFFFFF) || (q & 0xFFFFFF) != (fond & 0xFFFFFF)) {
                    somme += (dr + dg + db) / 3;
                    cnt++;
                }
                int v = Math.min(255, dmax * 4);
                e.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        copier(dst, e, x0, y0, k);
        return cnt == 0 ? 0 : (double) somme / cnt;
    }

    private static void copier(BufferedImage dst, BufferedImage src, int x0, int y0, int k) {
        for (int y = 0; y < src.getHeight() * k; y++) {
            for (int x = 0; x < src.getWidth() * k; x++) {
                if (x0 + x < dst.getWidth() && y0 + y < dst.getHeight()) dst.setRGB(x0 + x, y0 + y, src.getRGB(x / k, y / k));
            }
        }
    }

    /** Texel de la page (u avec sa page, v), ARGB. */
    private static int texel(float u, float v) {
        int k = AtlasConteneurs.page(u);
        BufferedImage img = pages[k];
        int w = img.getWidth(), h = img.getHeight();
        int x = Math.min(w - 1, Math.max(0, (int) Math.floor((u - k) * w)));
        int y = Math.min(h - 1, Math.max(0, (int) Math.floor(v * h)));
        return img.getRGB(x, y);
    }

    private static boolean transparent(int argb) {
        return ((argb >>> 24) & 0xFF) < ALPHA * 255f;
    }

    // =====================================================================================================================
    /** Maillage d'un conteneur (opaque puis verre), avec sa géométrie et sa grille de rayons. */
    private static final class Geo {
        final int n, nVerre, total;
        final float[] d;
        /** Normale géométrique (sens des sommets, comme la carte graphique) et normale des coins (ombrage). */
        final float[] gx, gy, gz, nx, ny, nz;
        final boolean[] pleine;
        final float[] cadre;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        /** Faces dont le sens des sommets contredit la normale des coins (le jeu les montre de l'autre côté). */
        int sensInverse;
        Grille grille;

        Geo(Mesh opaque, Mesh verre, Geo origine) {
            this(concat(opaque, verre), opaque.vertexCount() / 4, verre.vertexCount() / 4, origine);
        }

        Geo(Allegement.Resultat r, Mesh verre, Geo origine) {
            this(concat(Mesh.concat(r.garde(), r.dessous()), verre), (r.garde().vertexCount() + r.dessous().vertexCount()) / 4,
                    verre.vertexCount() / 4, origine);
        }

        private static float[] concat(Mesh a, Mesh b) {
            return Mesh.concat(a, b).data();
        }

        Geo(float[] data, int n, int nVerre, Geo origine) {
            this.n = n;
            this.nVerre = nVerre;
            total = n + nVerre;
            d = data;
            gx = new float[total];
            gy = new float[total];
            gz = new float[total];
            nx = new float[total];
            ny = new float[total];
            nz = new float[total];
            pleine = new boolean[total];
            for (int q = 0; q < total; q++) {
                float ax = 0, ay = 0, az = 0;
                for (int k = 0; k < 4; k++) {
                    int o = (q * 4 + k) * S;
                    ax += d[o + 5];
                    ay += d[o + 6];
                    az += d[o + 7];
                    minX = Math.min(minX, d[o]);
                    minY = Math.min(minY, d[o + 1]);
                    minZ = Math.min(minZ, d[o + 2]);
                    maxX = Math.max(maxX, d[o]);
                    maxY = Math.max(maxY, d[o + 1]);
                    maxZ = Math.max(maxZ, d[o + 2]);
                }
                float[] g = newell(d, q);
                float lg = len(g);
                float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                if (l < 1e-6f) {
                    ax = g[0];
                    ay = g[1];
                    az = g[2];
                    l = Math.max(lg, 1e-9f);
                }
                nx[q] = ax / l;
                ny[q] = ay / l;
                nz[q] = az / l;
                if (lg > 1e-9f) {
                    gx[q] = g[0] / lg;
                    gy[q] = g[1] / lg;
                    gz[q] = g[2] / lg;
                    if (gx[q] * nx[q] + gy[q] * ny[q] + gz[q] * nz[q] < 0) sensInverse++;
                }
                pleine[q] = q < n && pleine(q);
            }
            cadre = origine != null ? origine.cadre : new float[]{minX, minY, minZ, maxX, maxY, maxZ};
        }

        /** Le même sans ses {@code k} dernières faces opaques (les faces « dessous », rangées après les gardées). */
        Geo sans(int k) {
            int m = n - k;
            float[] out = new float[(m + nVerre) * 4 * S];
            System.arraycopy(d, 0, out, 0, m * 4 * S);
            System.arraycopy(d, n * 4 * S, out, m * 4 * S, nVerre * 4 * S);
            return new Geo(out, m, nVerre, this);
        }

        /**
         * La texture de la face est-elle pleine partout ? Tous les points de texture du rectangle qui contient ses
         * coordonnées (un point transparent, même petit, laisse passer le regard) ; sinon les rayons lisent la texture.
         */
        private boolean pleine(int q) {
            int o = q * 4 * S;
            float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                u0 = Math.min(u0, d[o + k * S + 3]);
                u1 = Math.max(u1, d[o + k * S + 3]);
                v0 = Math.min(v0, d[o + k * S + 4]);
                v1 = Math.max(v1, d[o + k * S + 4]);
            }
            int page = AtlasConteneurs.page((u0 + u1) / 2);
            BufferedImage img = pages[page];
            int w = img.getWidth(), h = img.getHeight();
            int x0 = Math.max(0, (int) Math.floor((u0 - page) * w)), x1 = Math.min(w - 1, (int) Math.floor((u1 - page) * w));
            int y0 = Math.max(0, (int) Math.floor(v0 * h)), y1 = Math.min(h - 1, (int) Math.floor(v1 * h));
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) > 1 << 20) return false;
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if (transparent(img.getRGB(x, y))) return false;
            return true;
        }

        private float[] bilin(int q, float s, float t, int off) {
            int o = q * 4 * S;
            float[] r = new float[3];
            int cnt = off == 3 ? 2 : 3;
            for (int c = 0; c < cnt; c++) {
                float a0 = d[o + off + c], a1 = d[o + S + off + c], a2 = d[o + 2 * S + off + c], a3 = d[o + 3 * S + off + c];
                r[c] = (1 - s) * (1 - t) * a0 + s * (1 - t) * a1 + s * t * a2 + (1 - s) * t * a3;
            }
            return r;
        }

        // ------------------------------------------------------------------------------------------ faces cachées
        /** Par face opaque : 0 vue, 1 vue seulement par-dessous le bloc, 2 jamais vue. */
        /**
         * Filet de sécurité des rayons : une face vue dans une des {@code VUES_REPRISE} vues (numéros de face, sphère
         * entière) est gardée (sans bloc dessous : au moins « dessous » ; avec : vue). Rend le nombre de faces reprises.
         */
        int reprendre(int[] classe) {
            int reprises = 0;
            for (float[] dir : fibonacci(VUES_REPRISE)) {
                for (int bloc = 0; bloc < 2; bloc++) {
                    float[] z = idBuffer(dir, 320, bloc == 1, cadre, true);
                    for (int i = 1; i < z.length; i += 2) {
                        int q = (int) z[i];
                        if (q < 0) continue;
                        int c = bloc == 1 ? 0 : 1;
                        if (classe[q] > c) {
                            classe[q] = c;
                            reprises++;
                        }
                    }
                }
            }
            return reprises;
        }

        int[] visibilite() {
            grille = new Grille(this);
            float[][] dirs = fibonacci(DIRS);
            int[] r = new int[n];
            for (int q = 0; q < n; q++) r[q] = vue(q, dirs);
            return r;
        }

        private int vue(int q, float[][] dirs) {
            int o = q * 4 * S;
            float e1 = dist(o, o + S), e2 = dist(o + S, o + 2 * S), e3 = dist(o + 2 * S, o + 3 * S), e4 = dist(o + 3 * S, o);
            int ns = Math.max(2, Math.min(12, (int) Math.ceil(Math.max(e1, e3) * 1.5f) + 1));
            int nt = Math.max(2, Math.min(12, (int) Math.ceil(Math.max(e2, e4) * 1.5f) + 1));
            // points jusqu'à 0,01 px des bords : une bande vue plus fine que l'écart des points n'est pas manquée au bord
            float bs = Math.min(0.25f, 0.01f / Math.max(1e-3f, Math.max(e1, e3))), bt = Math.min(0.25f, 0.01f / Math.max(1e-3f, Math.max(e2, e4)));
            float fx = gx[q], fy = gy[q], fz = gz[q];
            if (fx == 0 && fy == 0 && fz == 0) return 0;   // face sans surface : gardée telle quelle
            int r = 2;
            for (float[] dir : dirs) {
                if (dir[0] * fx + dir[1] * fy + dir[2] * fz <= 0.02f) continue;
                for (int i = 0; i < ns; i++) {
                    for (int j = 0; j < nt; j++) {
                        float s = bs + (1 - 2 * bs) * i / (ns - 1), t = bt + (1 - 2 * bt) * j / (nt - 1);
                        float[] p = bilin(q, s, t, 0);
                        float ox = p[0] + fx * 0.02f, oy = p[1] + fy * 0.02f, oz = p[2] + fz * 0.02f;
                        int k = grille.sort(ox, oy, oz, dir[0], dir[1], dir[2], q);
                        if (k == 0) return 0;
                        r = Math.min(r, k);
                    }
                }
            }
            return r;
        }

        private float dist(int a, int b) {
            float x = d[a] - d[b], y = d[a + 1] - d[b + 1], z = d[a + 2] - d[b + 2];
            return (float) Math.sqrt(x * x + y * y + z * z);
        }

        // ------------------------------------------------------------------------------------------ contrôles par vues
        /**
         * Points où l'autre version ne montre pas la même face (vues orthographiques sur toute la sphère) ; bloc plein
         * dessous ou non. Les deux versions doivent avoir le même début de numérotation : ici, l'autre est construite
         * dans l'ordre des faces gardées, on compare donc les faces par leur place exacte (centre, normale).
         */
        int ecartsVues(Geo autre, boolean blocDessous) {
            float[][] dirs = fibonacci(VUES);
            int ecarts = 0;
            for (float[] dir : dirs) {
                float[] a = idBuffer(dir, 160, blocDessous), b = autre.idBuffer(dir, 160, blocDessous, cadre);
                for (int i = 0; i < a.length; i += 2) {
                    // même profondeur et même couleur cuite = même face ; seul un écart net compte
                    if (Math.abs(a[i] - b[i]) > 0.05f || a[i + 1] != b[i + 1]) ecarts++;
                }
            }
            return ecarts;
        }

        float[] idBuffer(float[] dir, int W, boolean blocDessous) {
            return idBuffer(dir, W, blocDessous, cadre, false);
        }

        /** Profondeur et « signature » (face vue : couleur de texture) de chaque point ; fond : -inf, bloc dessous : -2. */
        float[] idBuffer(float[] dir, int W, boolean blocDessous, float[] cad) {
            return idBuffer(dir, W, blocDessous, cad, false);
        }

        /** indices : la signature est le numéro de la face (au lieu de sa couleur). */
        float[] idBuffer(float[] dir, int W, boolean blocDessous, float[] cad, boolean indices) {
            float cx = dir[0], cy = dir[1], cz = dir[2];
            float[] r0 = Math.abs(cy) < 0.9f ? norm(cross(new float[]{0, 1, 0}, dir)) : norm(cross(new float[]{1, 0, 0}, dir));
            float[] u0 = cross(dir, r0);
            float lx = cad[3] - cad[0], ly = cad[4] - Math.min(cad[1], blocDessous ? -16 : cad[1]), lz = cad[5] - cad[2];
            float mx = (cad[0] + cad[3]) / 2, my = (cad[1] + cad[4]) / 2, mz = (cad[2] + cad[5]) / 2;
            float rayon = 0.5f * (float) Math.sqrt(lx * lx + ly * ly + lz * lz) + 1;
            float k = W * 0.5f / rayon;
            float[] zb = new float[W * W * 2];
            for (int i = 0; i < W * W; i++) {
                zb[i * 2] = Float.NEGATIVE_INFINITY;
                zb[i * 2 + 1] = -1;
            }
            Proj pr = new Proj(cx, cy, cz, r0, u0, mx, my, mz, k, W);
            pr.indices = indices;
            if (blocDessous) {   // bloc plein sous le conteneur : 6 faces de [0,16] x [-16,0] x [0,16]
                float[][] c = {{0, -16, 0}, {16, -16, 0}, {16, 0, 0}, {0, 0, 0}, {0, -16, 16}, {16, -16, 16}, {16, 0, 16}, {0, 0, 16}};
                int[][] f = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
                for (int[] fa : f) {
                    float[] p = new float[12];
                    for (int v = 0; v < 4; v++) System.arraycopy(c[fa[v]], 0, p, v * 3, 3);
                    pr.quad(p, null, -2, zb, this, -1, false);
                }
            }
            float[] p = new float[12];
            for (int q = 0; q < n; q++) {
                for (int v = 0; v < 4; v++) {
                    int o = (q * 4 + v) * S;
                    p[v * 3] = d[o];
                    p[v * 3 + 1] = d[o + 1];
                    p[v * 3 + 2] = d[o + 2];
                }
                pr.quad(p, null, 0, zb, this, q, true);
            }
            return zb;
        }

        /** Points du complet couverts (modèle) devenus fond dans l'autre version, loin des bords de la silhouette. */
        int trous(Geo autre, BufferedImage[] image) {
            return percees(autre, fibonacci(VUES), null, image);
        }

        /**
         * Points où l'on voit à travers l'autre version : fond, ou surface à plus de 1 px derrière, là où le complet montre
         * une surface continue (tout le voisinage de 2 points couvert, à moins de 1 px de profondeur : pas un bord, où un
         * léger rétrécissement est normal). faces : reçoit les sommets des faces du complet vues à ces points (à geler).
         * image : la vue qui en a le plus (autre version en gris, percées en rouge).
         */
        int percees(Geo autre, float[][] dirs, Set<Integer> faces, BufferedImage[] image) {
            int total = 0, pire = -1;
            int W = 192;
            for (float[] dir : dirs) {
                float[] a = idBuffer(dir, W, false, cadre, true), b = autre.idBuffer(dir, W, false, cadre, false);
                boolean[] t = new boolean[W * W];
                int ici = 0;
                for (int y = 2; y < W - 2; y++) {
                    for (int x = 2; x < W - 2; x++) {
                        int i = y * W + x;
                        float za = a[i * 2], zb = b[i * 2];
                        if (za == Float.NEGATIVE_INFINITY || zb >= za - 1f) continue;
                        boolean continu = true;
                        for (int dy = -2; dy <= 2 && continu; dy++) {
                            for (int dx = -2; dx <= 2; dx++) {
                                float zv = a[((y + dy) * W + x + dx) * 2];
                                if (zv == Float.NEGATIVE_INFINITY || Math.abs(zv - za) > 1f) {
                                    continu = false;
                                    break;
                                }
                            }
                        }
                        if (!continu) continue;
                        t[i] = true;
                        ici++;
                        if (faces != null) {
                            int q = (int) a[i * 2 + 1];
                            for (int k = 0; k < 4; k++) faces.add(q * 4 + k);
                        }
                    }
                }
                total += ici;
                if (image != null && ici > pire) {
                    pire = ici;
                    BufferedImage img = new BufferedImage(W * 2, W * 2, BufferedImage.TYPE_INT_RGB);
                    float zmin = Float.MAX_VALUE, zmax = -Float.MAX_VALUE;
                    for (int i = 0; i < W * W; i++) {
                        if (b[i * 2] == Float.NEGATIVE_INFINITY) continue;
                        zmin = Math.min(zmin, b[i * 2]);
                        zmax = Math.max(zmax, b[i * 2]);
                    }
                    for (int i = 0; i < W * W; i++) {
                        int c;
                        if (t[i]) c = 0xFF2020;
                        else if (b[i * 2] == Float.NEGATIVE_INFINITY) c = 0x202024;
                        else {
                            int g = 70 + (int) (150 * (b[i * 2] - zmin) / Math.max(1e-3f, zmax - zmin));
                            c = (g << 16) | (g << 8) | g;
                        }
                        int x = i % W, y = i / W;
                        for (int k = 0; k < 4; k++) img.setRGB(x * 2 + (k & 1), y * 2 + (k >> 1), c);
                    }
                    image[0] = img;
                }
            }
            return total;
        }

        // ------------------------------------------------------------------------------------------ rendu
        /** Vue orthographique depuis (yaw, pitch) degrés, image taille x taille, suréchantillonnée ss fois. */
        BufferedImage rendu(int taille, int ss, float yawDeg, float pitchDeg) {
            int W = taille * ss;
            float[] zb = new float[W * W];
            int[] col = new int[W * W];
            Arrays.fill(zb, Float.NEGATIVE_INFINITY);
            for (int y = 0; y < W; y++) {
                int g = 46 + (int) (24f * y / W);
                for (int x = 0; x < W; x++) col[y * W + x] = (g << 16) | (g << 8) | (g + 4);
            }
            double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
            float cx = (float) (Math.sin(yaw) * Math.cos(pitch)), cy = (float) Math.sin(pitch), cz = (float) (Math.cos(yaw) * Math.cos(pitch));
            float rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw);
            float[] up = cross(new float[]{cx, cy, cz}, new float[]{rx, 0, rz});
            float ux = up[0], uy = up[1], uz = up[2];
            float lx = cadre[3] - cadre[0], ly = cadre[4] - cadre[1], lz = cadre[5] - cadre[2];
            float mx = (cadre[0] + cadre[3]) / 2, my = (cadre[1] + cadre[4]) / 2, mz = (cadre[2] + cadre[5]) / 2;
            float rayon = 0.5f * (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
            float k = W * 0.46f / Math.max(rayon, 1e-3f);
            float[] sx = new float[4], sy = new float[4], sz = new float[4];
            for (int q = 0; q < total; q++) {
                for (int v = 0; v < 4; v++) {
                    int o = (q * 4 + v) * S;
                    float px = d[o] - mx, py = d[o + 1] - my, pz = d[o + 2] - mz;
                    sx[v] = W / 2f + k * (px * rx + pz * rz);
                    sy[v] = W / 2f - k * (px * ux + py * uy + pz * uz);
                    sz[v] = px * cx + py * cy + pz * cz;
                }
                boolean verre = q >= n;
                float nyq = ny[q];
                float ombre = nx[q] * nx[q] * 0.6f + nz[q] * nz[q] * 0.8f + nyq * nyq * (nyq > 0 ? 1f : 0.5f);
                tri(q, 0, 1, 2, sx, sy, sz, zb, col, W, ombre, verre);
                tri(q, 0, 2, 3, sx, sy, sz, zb, col, W, ombre, verre);
            }
            BufferedImage img = new BufferedImage(taille, taille, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < taille; y++) {
                for (int x = 0; x < taille; x++) {
                    int r = 0, g = 0, b = 0;
                    for (int j = 0; j < ss; j++) {
                        for (int i = 0; i < ss; i++) {
                            int c = col[(y * ss + j) * W + x * ss + i];
                            r += (c >> 16) & 255;
                            g += (c >> 8) & 255;
                            b += c & 255;
                        }
                    }
                    int a = ss * ss;
                    img.setRGB(x, y, ((r / a) << 16) | ((g / a) << 8) | (b / a));
                }
            }
            return img;
        }

        private void tri(int q, int a, int b, int c, float[] sx, float[] sy, float[] sz, float[] zb, int[] col, int W,
                         float ombre, boolean verre) {
            float area = (sx[b] - sx[a]) * (sy[c] - sy[a]) - (sx[c] - sx[a]) * (sy[b] - sy[a]);
            // envers (sens des sommets à l'écran, comme la carte graphique) : pas dessiné ; y de l'écran vers le bas
            if (area >= -1e-9f) return;
            int x0 = Math.max(0, (int) Math.floor(Math.min(sx[a], Math.min(sx[b], sx[c]))));
            int x1 = Math.min(W - 1, (int) Math.ceil(Math.max(sx[a], Math.max(sx[b], sx[c]))));
            int y0 = Math.max(0, (int) Math.floor(Math.min(sy[a], Math.min(sy[b], sy[c]))));
            int y1 = Math.min(W - 1, (int) Math.ceil(Math.max(sy[a], Math.max(sy[b], sy[c]))));
            int o = q * 4 * S;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    float px = x + 0.5f, py = y + 0.5f;
                    float wa = ((sx[b] - px) * (sy[c] - py) - (sx[c] - px) * (sy[b] - py)) / area;
                    float wb = ((sx[c] - px) * (sy[a] - py) - (sx[a] - px) * (sy[c] - py)) / area;
                    float wc = 1 - wa - wb;
                    if (wa < 0 || wb < 0 || wc < 0) continue;
                    float z = wa * sz[a] + wb * sz[b] + wc * sz[c];
                    int idx = y * W + x;
                    if (z <= zb[idx]) continue;
                    int oa = o + a * S, ob = o + b * S, oc = o + c * S;
                    float u = wa * d[oa + 3] + wb * d[ob + 3] + wc * d[oc + 3];
                    float v = wa * d[oa + 4] + wb * d[ob + 4] + wc * d[oc + 4];
                    int t = texel(u, v);
                    if (transparent(t)) continue;
                    float cr = wa * d[oa + 8] + wb * d[ob + 8] + wc * d[oc + 8];
                    float cg = wa * d[oa + 9] + wb * d[ob + 9] + wc * d[oc + 9];
                    float cb = wa * d[oa + 10] + wb * d[ob + 10] + wc * d[oc + 10];
                    int r = Math.min(255, (int) (((t >> 16) & 255) * cr * ombre));
                    int g = Math.min(255, (int) (((t >> 8) & 255) * cg * ombre));
                    int bl = Math.min(255, (int) ((t & 255) * cb * ombre));
                    if (verre) {
                        float al = ((t >>> 24) & 255) / 255f;
                        int p = col[idx];
                        r = (int) (r * al + ((p >> 16) & 255) * (1 - al));
                        g = (int) (g * al + ((p >> 8) & 255) * (1 - al));
                        bl = (int) (bl * al + (p & 255) * (1 - al));
                        col[idx] = (r << 16) | (g << 8) | bl;
                        continue;
                    }
                    zb[idx] = z;
                    col[idx] = (r << 16) | (g << 8) | bl;
                }
            }
        }
    }

    /** Projection orthographique d'un tampon « profondeur + signature » (contrôles par vues). */
    private static final class Proj {
        final float cx, cy, cz, mx, my, mz, k;
        final float[] r, u;
        final int W;
        boolean indices;

        Proj(float cx, float cy, float cz, float[] r, float[] u, float mx, float my, float mz, float k, int W) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.r = r;
            this.u = u;
            this.mx = mx;
            this.my = my;
            this.mz = mz;
            this.k = k;
            this.W = W;
        }

        /** p : 4 sommets (x y z) ; sig &lt; 0 : occultant uni ; sinon la signature est la couleur de texture × couleur cuite. */
        void quad(float[] p, Object unused, int sig, float[] zb, Geo g, int q, boolean texture) {
            float[] sx = new float[4], sy = new float[4], sz = new float[4];
            for (int v = 0; v < 4; v++) {
                float px = p[v * 3] - mx, py = p[v * 3 + 1] - my, pz = p[v * 3 + 2] - mz;
                sx[v] = W / 2f + k * (px * r[0] + py * r[1] + pz * r[2]);
                sy[v] = W / 2f - k * (px * u[0] + py * u[1] + pz * u[2]);
                sz[v] = px * cx + py * cy + pz * cz;
            }
            if (!texture || deFace(p, 0, 1, 2)) tri(0, 1, 2, sx, sy, sz, zb, g, q, sig, texture);
            if (!texture || deFace(p, 0, 2, 3)) tri(0, 2, 3, sx, sy, sz, zb, g, q, sig, texture);
        }

        /**
         * Triangle vu à plus de 1,15° de la tranche (comme les rayons des faces cachées) : vu presque par la tranche, une
         * face n'est qu'un trait sans épaisseur que la grille de points attrape ou non selon le cadrage.
         */
        private boolean deFace(float[] p, int a, int b, int c) {
            float[] e1 = {p[b * 3] - p[a * 3], p[b * 3 + 1] - p[a * 3 + 1], p[b * 3 + 2] - p[a * 3 + 2]};
            float[] e2 = {p[c * 3] - p[a * 3], p[c * 3 + 1] - p[a * 3 + 1], p[c * 3 + 2] - p[a * 3 + 2]};
            float[] n = cross(e1, e2);
            float l = len(n);
            return l > 1e-9f && (n[0] * cx + n[1] * cy + n[2] * cz) / l > 0.02f;
        }

        private void tri(int a, int b, int c, float[] sx, float[] sy, float[] sz, float[] zb, Geo g, int q, int sig, boolean texture) {
            float area = (sx[b] - sx[a]) * (sy[c] - sy[a]) - (sx[c] - sx[a]) * (sy[b] - sy[a]);
            if (area >= -1e-9f) return;   // envers : pas dessiné
            int x0 = Math.max(0, (int) Math.floor(Math.min(sx[a], Math.min(sx[b], sx[c]))));
            int x1 = Math.min(W - 1, (int) Math.ceil(Math.max(sx[a], Math.max(sx[b], sx[c]))));
            int y0 = Math.max(0, (int) Math.floor(Math.min(sy[a], Math.min(sy[b], sy[c]))));
            int y1 = Math.min(W - 1, (int) Math.ceil(Math.max(sy[a], Math.max(sy[b], sy[c]))));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    float px = x + 0.5f, py = y + 0.5f;
                    float wa = ((sx[b] - px) * (sy[c] - py) - (sx[c] - px) * (sy[b] - py)) / area;
                    float wb = ((sx[c] - px) * (sy[a] - py) - (sx[a] - px) * (sy[c] - py)) / area;
                    float wc = 1 - wa - wb;
                    if (wa < 0 || wb < 0 || wc < 0) continue;
                    float z = wa * sz[a] + wb * sz[b] + wc * sz[c];
                    int idx = (y * W + x) * 2;
                    if (z <= zb[idx]) continue;
                    float s = sig;
                    if (texture) {
                        int o = q * 4 * S, oa = o + a * S, ob = o + b * S, oc = o + c * S;
                        float tu = wa * g.d[oa + 3] + wb * g.d[ob + 3] + wc * g.d[oc + 3];
                        float tv = wa * g.d[oa + 4] + wb * g.d[ob + 4] + wc * g.d[oc + 4];
                        int t = texel(tu, tv);
                        if (transparent(t)) continue;
                        s = indices ? q : (t & 0xFFFFFF) ^ (Math.round(g.d[oa + 8] * 15) << 24);
                    }
                    zb[idx] = z;
                    zb[idx + 1] = s;
                }
            }
        }
    }

    // =====================================================================================================================
    /** Contrôles de la simplification : ce qu'il faut geler (sommets), et ce qui reste en défaut. */
    private static final class Controle {
        final Geo g;
        final boolean[] retiree;
        final int n;
        /** Nouvelles places des sommets des faces gardées (n * 4 * 3), et faces gardées. */
        final float[] np;
        final boolean[] gardee;
        int retournees, collees, degenerees;
        private final Set<Integer> geler = new TreeSet<>();
        final int[] comp;

        Controle(Geo plein, boolean[] retiree, Allegement.Resultat r) {
            g = plein;
            this.retiree = retiree;
            n = plein.n;
            np = new float[n * 12];
            gardee = new boolean[n];
            placer(r.garde(), r.origineGarde());
            placer(r.dessous(), r.origineDessous());
            comp = Allegement.composantes(plein.d, n);
            retournees();
            surfaces();
            contactsEtCouches();
        }

        private void placer(Mesh m, int[] origine) {
            float[] d = m.data();
            for (int i = 0; i < origine.length; i++) {
                int q = origine[i];
                gardee[q] = true;
                for (int k = 0; k < 4; k++) System.arraycopy(d, (i * 4 + k) * S, np, (q * 4 + k) * 3, 3);
            }
        }

        Set<Integer> aGeler() {
            return geler;
        }

        private void gelerFace(int q) {
            for (int k = 0; k < 4; k++) geler.add(q * 4 + k);
        }

        private float[] op(int v) {
            int o = v * S;
            return new float[]{g.d[o], g.d[o + 1], g.d[o + 2]};
        }

        private float[] nouv(int v) {
            return new float[]{np[v * 3], np[v * 3 + 1], np[v * 3 + 2]};
        }

        /** Triangle de la face q (0 : sommets 0 1 2 ; 1 : 0 2 3) qui se retourne ; triangle devenu plat : rien (invisible). */
        private void retournees() {
            for (int q = 0; q < n; q++) {
                if (!gardee[q]) continue;
                int[][] tris = {{0, 1, 2}, {0, 2, 3}};
                boolean degen = true;
                for (int[] t : tris) {
                    float[] a = cross(sub(op(q * 4 + t[1]), op(q * 4 + t[0])), sub(op(q * 4 + t[2]), op(q * 4 + t[0])));
                    float[] b = cross(sub(nouv(q * 4 + t[1]), nouv(q * 4 + t[0])), sub(nouv(q * 4 + t[2]), nouv(q * 4 + t[0])));
                    float la = len(a), lb = len(b);
                    if (lb > 2e-4f) degen = false;
                    if (la < 2e-4f || lb < 2e-4f) continue;
                    if (dot(a, b) / (la * lb) < ROTATION) {
                        retournees++;
                        gelerFace(q);
                    }
                }
                if (degen) degenerees++;
            }
        }

        /** Pièce dont il reste moins de la moitié de la surface visible : toute la pièce gelée (petites pièces gardées). */
        private void surfaces() {
            Map<Integer, double[]> aires = new HashMap<>();
            for (int q = 0; q < n; q++) {
                if (retiree[q]) continue;
                double[] a = aires.computeIfAbsent(comp[q], x -> new double[2]);
                a[0] += aireQuad(q, false);
                if (gardee[q]) a[1] += aireQuad(q, true);
            }
            for (int q = 0; q < n; q++) {
                if (retiree[q]) continue;
                double[] a = aires.get(comp[q]);
                if (a[0] > 1e-6 && a[1] < SURFACE * a[0]) gelerFace(q);
            }
        }

        double aireQuad(int q, boolean nouvelle) {
            float[] p0 = nouvelle ? nouv(q * 4) : op(q * 4), p1 = nouvelle ? nouv(q * 4 + 1) : op(q * 4 + 1);
            float[] p2 = nouvelle ? nouv(q * 4 + 2) : op(q * 4 + 2), p3 = nouvelle ? nouv(q * 4 + 3) : op(q * 4 + 3);
            return 0.5 * (len(cross(sub(p1, p0), sub(p2, p0))) + len(cross(sub(p2, p0), sub(p3, p0))));
        }

        /**
         * Paires proches (grille de 1 px) : faces parallèles de même sens qui se recouvrent (devenues collées, ou passées
         * l'une derrière l'autre), faces nouvellement collées, sommet posé sur une face d'une autre pièce qui s'en décolle.
         */
        private void contactsEtCouches() {
            Map<Long, List<Integer>> cases = new HashMap<>();
            float[][] bo = new float[n][];
            for (int q = 0; q < n; q++) {
                if (retiree[q]) continue;
                float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                for (int k = 0; k < 4; k++) {
                    float[] p = op(q * 4 + k);
                    for (int a = 0; a < 3; a++) {
                        b[a] = Math.min(b[a], p[a] - 0.1f);
                        b[a + 3] = Math.max(b[a + 3], p[a] + 0.1f);
                    }
                }
                bo[q] = b;
                for (int i = (int) Math.floor(b[0]); i <= (int) Math.floor(b[3]); i++)
                    for (int j = (int) Math.floor(b[1]); j <= (int) Math.floor(b[4]); j++)
                        for (int k = (int) Math.floor(b[2]); k <= (int) Math.floor(b[5]); k++)
                            cases.computeIfAbsent(((long) (i + 1024) << 40) | ((long) (j + 1024) << 20) | (k + 1024), x -> new ArrayList<>()).add(q);
            }
            Set<Long> vues = new HashSet<>();
            for (List<Integer> l : cases.values()) {
                for (int i = 0; i < l.size(); i++) {
                    for (int j = i + 1; j < l.size(); j++) {
                        int a = Math.min(l.get(i), l.get(j)), b = Math.max(l.get(i), l.get(j));
                        if (!chevauche(bo[a], bo[b]) || !vues.add(((long) a << 32) | b)) continue;
                        paire(a, b);
                    }
                }
            }
        }

        private static boolean chevauche(float[] a, float[] b) {
            return a[0] <= b[3] && b[0] <= a[3] && a[1] <= b[4] && b[1] <= a[4] && a[2] <= b[5] && b[2] <= a[5];
        }

        private void paire(int a, int b) {
            // 1) couches : faces parallèles de même sens qui se recouvrent
            float[] na = norm(newellP(a, false)), nb = norm(newellP(b, false));
            if (len(na) > 0.5f && len(nb) > 0.5f && dot(na, nb) > 0.995f && recouvre(a, b, na, false)) {
                float s0 = dot(sub(centre(b, false), centre(a, false)), na);
                if (gardee[a] && gardee[b]) {
                    float[] na1 = norm(newellP(a, true)), nb1 = norm(newellP(b, true));
                    if (len(na1) > 0.5f && len(nb1) > 0.5f && recouvre(a, b, na1, true)) {
                        float s1 = dot(sub(centre(b, true), centre(a, true)), na1);
                        boolean avant = Math.abs(s0) > 0.003f;
                        // séparées avant, collées ou inversées après
                        if (avant && (Math.abs(s1) < 0.003f || Math.signum(s1) != Math.signum(s0))) {
                            collees++;
                            gelerFace(a);
                            gelerFace(b);
                        }
                    }
                }
            } else if (len(na) > 0.5f && len(nb) > 0.5f && dot(na, nb) < -0.995f && recouvre(a, b, na, false)) {
                // 1 bis) faces parallèles de sens opposés en vis-à-vis (deux côtés d'une plaque, d'une paroi, d'un tube) :
                // l'épaisseur entre elles ne doit pas fondre de plus de moitié (pièce aplatie, rebord perdu)
                float s0 = dot(sub(centre(b, false), centre(a, false)), na);
                if (Math.abs(s0) > 0.03f && gardee[a] && gardee[b]) {
                    float[] na1 = norm(newellP(a, true));
                    if (len(na1) > 0.5f) {
                        float s1 = dot(sub(centre(b, true), centre(a, true)), na1);
                        if (Math.abs(s1) < 0.5f * Math.abs(s0) || Math.signum(s1) != Math.signum(s0)) {
                            collees++;
                            gelerFace(a);
                            gelerFace(b);
                        }
                    }
                }
            } else if (gardee[a] && gardee[b]) {
                // 2) faces non parallèles avant, devenues collées (même plan, même sens, qui se recouvrent)
                float[] na1 = norm(newellP(a, true)), nb1 = norm(newellP(b, true));
                if (len(na1) > 0.5f && len(nb1) > 0.5f && dot(na1, nb1) > 0.995f
                        && Math.abs(dot(sub(centre(b, true), centre(a, true)), na1)) < 0.003f && recouvre(a, b, na1, true)) {
                    collees++;
                    gelerFace(a);
                    gelerFace(b);
                }
            }
            // 3) contacts : un sommet posé sur une face (autre pièce, ou jonction en T) reste posé dessus, dans son contour
            contact(a, b);
            contact(b, a);
        }

        /**
         * Sommets de v posés (à 0,06 px) sur la face f, dans son contour, sans être un de ses coins : après, ils doivent
         * rester à la même distance (0,06 px près) et dans le contour (0,06 px près), sinon une fente s'ouvre entre les deux.
         */
        private void contact(int v, int f) {
            float[] nf = norm(newellP(f, false));
            if (len(nf) < 0.5f) return;
            float[] cf = centre(f, false);
            for (int k = 0; k < 4; k++) {
                float[] p = op(v * 4 + k);
                float d0 = dot(sub(p, cf), nf);
                if (Math.abs(d0) > 0.06f || !dans(f, p, nf, false, 0.02f) || coin(f, p)) continue;
                if (!gardee[v] || !gardee[f]) continue;
                float[] nf1 = norm(newellP(f, true));
                if (len(nf1) < 0.5f) continue;
                float[] p1 = nouv(v * 4 + k);
                float d1 = dot(sub(p1, centre(f, true)), nf1);
                if (Math.abs(d1 - d0) > CONTACT || !dans(f, p1, nf1, true, CONTACT)) {
                    geler.add(v * 4 + k);
                    gelerFace(f);
                }
            }
        }

        private boolean coin(int f, float[] p) {
            for (int k = 0; k < 4; k++) {
                float[] c = op(f * 4 + k);
                if (Math.abs(c[0] - p[0]) < 1e-3f && Math.abs(c[1] - p[1]) < 1e-3f && Math.abs(c[2] - p[2]) < 1e-3f) return true;
            }
            return false;
        }

        private float[] centre(int q, boolean nouvelle) {
            float[] c = new float[3];
            for (int k = 0; k < 4; k++) {
                float[] p = nouvelle ? nouv(q * 4 + k) : op(q * 4 + k);
                for (int a = 0; a < 3; a++) c[a] += p[a] / 4;
            }
            return c;
        }

        private float[] newellP(int q, boolean nouvelle) {
            float[] p0 = nouvelle ? nouv(q * 4) : op(q * 4), p1 = nouvelle ? nouv(q * 4 + 1) : op(q * 4 + 1);
            float[] p2 = nouvelle ? nouv(q * 4 + 2) : op(q * 4 + 2), p3 = nouvelle ? nouv(q * 4 + 3) : op(q * 4 + 3);
            return cross(sub(p2, p0), sub(p3, p1));
        }

        /** Le point (projeté le long de n) est-il dans le contour de la face (à marge px près) ? */
        private boolean dans(int q, float[] p, float[] nrm, boolean nouvelle, float marge) {
            float[][] base = base(nrm);
            float[][] poly = poly(q, base, nouvelle);
            float px = dot(p, base[0]), py = dot(p, base[1]);
            int signe = 0;
            for (int i = 0; i < 4; i++) {
                float[] a = poly[i], b = poly[(i + 1) % 4];
                float ex = b[0] - a[0], ey = b[1] - a[1];
                float el = (float) Math.sqrt(ex * ex + ey * ey);
                if (el < 1e-5f) continue;
                float c = (ex * (py - a[1]) - ey * (px - a[0])) / el;
                if (Math.abs(c) <= marge) continue;
                int sg = c > 0 ? 1 : -1;
                if (signe == 0) signe = sg;
                else if (sg != signe) return false;
            }
            return true;
        }

        /** Les deux faces se recouvrent-elles (projetées le long de n) d'au moins 0,02 px ? (séparation des axes) */
        private boolean recouvre(int a, int b, float[] nrm, boolean nouvelle) {
            float[][] base = base(nrm);
            float[][] pa = poly(a, base, nouvelle), pb = poly(b, base, nouvelle);
            return !separe(pa, pb) && !separe(pb, pa);
        }

        private static boolean separe(float[][] p, float[][] o) {
            for (int i = 0; i < 4; i++) {
                float ex = p[(i + 1) % 4][0] - p[i][0], ey = p[(i + 1) % 4][1] - p[i][1];
                float el = (float) Math.sqrt(ex * ex + ey * ey);
                if (el < 1e-5f) continue;
                float ax = -ey / el, ay = ex / el;
                float min1 = Float.MAX_VALUE, max1 = -Float.MAX_VALUE, min2 = Float.MAX_VALUE, max2 = -Float.MAX_VALUE;
                for (float[] v : p) {
                    float t = v[0] * ax + v[1] * ay;
                    min1 = Math.min(min1, t);
                    max1 = Math.max(max1, t);
                }
                for (float[] v : o) {
                    float t = v[0] * ax + v[1] * ay;
                    min2 = Math.min(min2, t);
                    max2 = Math.max(max2, t);
                }
                if (Math.min(max1, max2) - Math.max(min1, min2) < 0.02f) return true;
            }
            return false;
        }

        private static float[][] base(float[] nrm) {
            float[] r = Math.abs(nrm[1]) < 0.9f ? norm(cross(new float[]{0, 1, 0}, nrm)) : norm(cross(new float[]{1, 0, 0}, nrm));
            return new float[][]{r, cross(nrm, r)};
        }

        private float[][] poly(int q, float[][] base, boolean nouvelle) {
            float[][] r = new float[4][];
            for (int k = 0; k < 4; k++) {
                float[] p = nouvelle ? nouv(q * 4 + k) : op(q * 4 + k);
                r[k] = new float[]{dot(p, base[0]), dot(p, base[1])};
            }
            return r;
        }
    }

    // =====================================================================================================================
    private static float[] newell(float[] d, int q) {
        int o0 = q * 4 * S, o1 = o0 + S, o2 = o0 + 2 * S, o3 = o0 + 3 * S;
        float ux = d[o2] - d[o0], uy = d[o2 + 1] - d[o0 + 1], uz = d[o2 + 2] - d[o0 + 2];
        float vx = d[o3] - d[o1], vy = d[o3 + 1] - d[o1 + 1], vz = d[o3 + 2] - d[o1 + 2];
        return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float len(float[] a) {
        return (float) Math.sqrt(dot(a, a));
    }

    private static float[] norm(float[] a) {
        float l = len(a);
        return l < 1e-12f ? new float[3] : new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    private static float[][] fibonacci(int n) {
        float[][] r = new float[n][];
        double phi = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - 2.0 * (i + 0.5) / n;
            double rad = Math.sqrt(1 - y * y);
            double th = phi * i;
            r[i] = new float[]{(float) (Math.cos(th) * rad), (float) y, (float) (Math.sin(th) * rad)};
        }
        return r;
    }

    // =====================================================================================================================
    /** Grille de triangles (opaques) pour lancer les rayons : cases de c px, parcours case par case (Amanatides-Woo). */
    private static final class Grille {
        final Geo m;
        final float x0, y0, z0, c;
        final int gx, gy, gz;
        final int[][] cases;
        final int[] marque;
        int rayon;

        Grille(Geo m) {
            this.m = m;
            float pad = 0.05f;
            x0 = m.minX - pad;
            y0 = Math.min(0, m.minY) - pad;
            z0 = m.minZ - pad;
            float lx = m.maxX + pad - x0, ly = m.maxY + pad - y0, lz = m.maxZ + pad - z0;
            c = Math.max(0.5f, Math.max(lx, Math.max(ly, lz)) / 48f);
            gx = Math.max(1, (int) Math.ceil(lx / c));
            gy = Math.max(1, (int) Math.ceil(ly / c));
            gz = Math.max(1, (int) Math.ceil(lz / c));
            List<List<Integer>> tmp = new ArrayList<>(gx * gy * gz);
            for (int i = 0; i < gx * gy * gz; i++) tmp.add(null);
            for (int q = 0; q < m.n; q++) {   // le verre n'arrête aucun regard
                float a0 = Float.MAX_VALUE, a1 = Float.MAX_VALUE, a2 = Float.MAX_VALUE, b0 = -Float.MAX_VALUE, b1 = -Float.MAX_VALUE, b2 = -Float.MAX_VALUE;
                for (int k = 0; k < 4; k++) {
                    int o = (q * 4 + k) * S;
                    a0 = Math.min(a0, m.d[o]);
                    a1 = Math.min(a1, m.d[o + 1]);
                    a2 = Math.min(a2, m.d[o + 2]);
                    b0 = Math.max(b0, m.d[o]);
                    b1 = Math.max(b1, m.d[o + 1]);
                    b2 = Math.max(b2, m.d[o + 2]);
                }
                int i0 = cl((int) Math.floor((a0 - x0) / c), gx), i1 = cl((int) Math.floor((b0 - x0) / c), gx);
                int j0 = cl((int) Math.floor((a1 - y0) / c), gy), j1 = cl((int) Math.floor((b1 - y0) / c), gy);
                int k0 = cl((int) Math.floor((a2 - z0) / c), gz), k1 = cl((int) Math.floor((b2 - z0) / c), gz);
                for (int i = i0; i <= i1; i++) for (int j = j0; j <= j1; j++) for (int k = k0; k <= k1; k++) {
                    int idx = (i * gy + j) * gz + k;
                    List<Integer> l = tmp.get(idx);
                    if (l == null) tmp.set(idx, l = new ArrayList<>(4));
                    l.add(q);
                }
            }
            cases = new int[tmp.size()][];
            for (int i = 0; i < cases.length; i++) {
                List<Integer> l = tmp.get(i);
                if (l == null) continue;
                cases[i] = new int[l.size()];
                for (int j = 0; j < l.size(); j++) cases[i][j] = l.get(j);
            }
            marque = new int[m.n];
        }

        private static int cl(int v, int n) {
            return Math.max(0, Math.min(n - 1, v));
        }

        /**
         * Le rayon (o, dir) : 0 il sort du modèle, 1 il n'est arrêté que par le sol sous le bloc (plan y = 0, carré du
         * bloc [0,16] x [0,16]), 2 il est arrêté par une face. La face « soi » est ignorée.
         */
        int sort(float ox, float oy, float oz, float dx, float dy, float dz, int soi) {
            rayon++;
            float tSortie = Float.MAX_VALUE;
            float bx1 = x0 + gx * c, by1 = y0 + gy * c, bz1 = z0 + gz * c;
            if (dx > 0) tSortie = Math.min(tSortie, (bx1 - ox) / dx);
            else if (dx < 0) tSortie = Math.min(tSortie, (x0 - ox) / dx);
            if (dz > 0) tSortie = Math.min(tSortie, (bz1 - oz) / dz);
            else if (dz < 0) tSortie = Math.min(tSortie, (z0 - oz) / dz);
            if (dy > 0) tSortie = Math.min(tSortie, (by1 - oy) / dy);
            else if (dy < 0) tSortie = Math.min(tSortie, (y0 - oy) / dy);
            tSortie = Math.max(0, tSortie);
            boolean sol = false;
            if (dy < 0 && oy >= 0) {
                float tSol = -oy / dy;
                float sx = ox + dx * tSol, sz = oz + dz * tSol;
                if (tSol <= tSortie && sx >= 0 && sx <= 16 && sz >= 0 && sz <= 16) {
                    sol = true;
                    tSortie = tSol;
                }
            }
            int i = cl((int) Math.floor((ox - x0) / c), gx), j = cl((int) Math.floor((oy - y0) / c), gy), k = cl((int) Math.floor((oz - z0) / c), gz);
            int si = dx > 0 ? 1 : -1, sj = dy > 0 ? 1 : -1, sk = dz > 0 ? 1 : -1;
            float tdx = dx != 0 ? Math.abs(c / dx) : Float.MAX_VALUE, tdy = dy != 0 ? Math.abs(c / dy) : Float.MAX_VALUE, tdz = dz != 0 ? Math.abs(c / dz) : Float.MAX_VALUE;
            float tmx = dx != 0 ? ((x0 + (i + (dx > 0 ? 1 : 0)) * c) - ox) / dx : Float.MAX_VALUE;
            float tmy = dy != 0 ? ((y0 + (j + (dy > 0 ? 1 : 0)) * c) - oy) / dy : Float.MAX_VALUE;
            float tmz = dz != 0 ? ((z0 + (k + (dz > 0 ? 1 : 0)) * c) - oz) / dz : Float.MAX_VALUE;
            float t = 0;
            while (true) {
                int[] l = cases[(i * gy + j) * gz + k];
                if (l != null) {
                    for (int q : l) {
                        if (q == soi || marque[q] == rayon) continue;
                        marque[q] = rayon;
                        if (arrete(q, ox, oy, oz, dx, dy, dz, tSortie)) {
                            return 2;
                        }
                    }
                }
                if (tmx <= tmy && tmx <= tmz) {
                    t = tmx;
                    tmx += tdx;
                    i += si;
                    if (i < 0 || i >= gx) break;
                } else if (tmy <= tmz) {
                    t = tmy;
                    tmy += tdy;
                    j += sj;
                    if (j < 0 || j >= gy) break;
                } else {
                    t = tmz;
                    tmz += tdz;
                    k += sk;
                    if (k < 0 || k >= gz) break;
                }
                if (t > tSortie) break;
            }
            return sol ? 1 : 0;
        }

        /** La face q arrête-t-elle ce rayon ? Seulement vue de face (sens des sommets), sur une partie pleine. */
        private boolean arrete(int q, float ox, float oy, float oz, float dx, float dy, float dz, float tMax) {
            if (dx * m.gx[q] + dy * m.gy[q] + dz * m.gz[q] <= 0) return false;
            return touche(q, 0, 1, 2, ox, oy, oz, dx, dy, dz, tMax) || touche(q, 0, 2, 3, ox, oy, oz, dx, dy, dz, tMax);
        }

        private boolean touche(int q, int a, int b, int cc, float ox, float oy, float oz, float dx, float dy, float dz, float tMax) {
            int o = q * 4 * S;
            float[] d = m.d;
            int pa = o + a * S, pb = o + b * S, pc = o + cc * S;
            float e1x = d[pb] - d[pa], e1y = d[pb + 1] - d[pa + 1], e1z = d[pb + 2] - d[pa + 2];
            float e2x = d[pc] - d[pa], e2y = d[pc + 1] - d[pa + 1], e2z = d[pc + 2] - d[pa + 2];
            // ce triangle-là, vu de face ? (un quad tordu a deux triangles qui peuvent regarder ailleurs)
            float tnx = e1y * e2z - e1z * e2y, tny = e1z * e2x - e1x * e2z, tnz = e1x * e2y - e1y * e2x;
            if (dx * tnx + dy * tny + dz * tnz <= 0) return false;
            float px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
            float det = e1x * px + e1y * py + e1z * pz;
            if (Math.abs(det) < 1e-9f) return false;
            float inv = 1 / det;
            float tx = ox - d[pa], ty = oy - d[pa + 1], tz = oz - d[pa + 2];
            float u = (tx * px + ty * py + tz * pz) * inv;
            if (u < 0 || u > 1) return false;
            float qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
            float v = (dx * qx + dy * qy + dz * qz) * inv;
            if (v < 0 || u + v > 1) return false;
            float t = (e2x * qx + e2y * qy + e2z * qz) * inv;
            if (t <= 1e-4f || t > tMax) return false;
            if (m.pleine[q]) return true;
            float w = 1 - u - v;
            float tu = w * d[pa + 3] + u * d[pb + 3] + v * d[pc + 3];
            float tv = w * d[pa + 4] + u * d[pb + 4] + v * d[pc + 4];
            return !transparent(texel(tu, tv));
        }
    }
}
