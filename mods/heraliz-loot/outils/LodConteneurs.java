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
import java.util.List;

/**
 * Outil (pas dans le jeu) : versions allégées des conteneurs de HeraliZ Loot, pour mods/heraliz-loot/outils/lod_props.py.
 * Compilé avec le seul code des modèles (Java pur, sans Minecraft) et JOML.
 * <p>
 * Chaque conteneur est le maillage que le jeu cuit dans le rendu du terrain (Conteneurs.maillages). Trois allègements :
 * <ol>
 * <li><b>faces cachées</b> (sans perte) : une face opaque qu'aucun regard ne peut atteindre. Depuis des points répartis sur
 * la face, des rayons partent vers son avant dans {@code DIRS} directions ; la face est vue si un rayon sort du modèle sans
 * toucher une face tournée vers lui (le jeu ne dessine pas l'envers des faces), une partie transparente de la texture
 * (découpe, seuil 0,5 comme le rendu « cutout ») ni le verre. Un rayon qui atteint le sol (y = 0) sous le modèle est
 * arrêté (bloc d'appui). Cas trouvés : dessous posé au sol, intérieurs de boîtes fermées, faces collées l'une à l'autre ;</li>
 * <li><b>petites pièces</b> (avec perte, au choix) : pièce d'un seul tenant (faces reliées par leurs sommets) dont la plus
 * grande dimension est sous le seuil (rivets, vis, têtes de boulons) : elle ne fait que quelques points d'écran au-delà de
 * quelques blocs ;</li>
 * <li><b>simplification</b> (avec perte, au choix, après les faces cachées) : regroupement des sommets pièce par pièce
 * ({@link Modele#simplifier}).</li>
 * </ol>
 * Sorties : {@code lod.csv} (faces avant / après chaque niveau), {@code conteneurs_lod.json} (pour chaque conteneur, les
 * numéros des faces cachées et des petites pièces, avec le nombre de faces du maillage en garde), et sur demande des rendus
 * PNG avant / après (moteur logiciel : textures, couleurs cuites et ombrage des blocs du jeu, sans la lumière du monde).
 * <pre>java -cp &lt;classes&gt;:&lt;joml&gt; LodConteneurs &lt;textures&gt; &lt;sortie&gt; &lt;petites_px&gt; &lt;cellule_px&gt; &lt;rendus: nom,nom|-&gt; [nom...]</pre>
 */
public final class LodConteneurs {
    private static final int S = Mesh.STRIDE;
    private static final int DIRS = 128;
    private static final float ALPHA = 0.5f;

    private static BufferedImage[] pages;

    public static void main(String[] args) throws IOException {
        File textures = new File(args[0]);
        File sortie = new File(args[1]);
        float petites = Float.parseFloat(args[2]);
        float cellule = Float.parseFloat(args[3]);
        List<String> rendus = args[4].equals("-") ? List.of() : Arrays.asList(args[4].split(","));
        List<String> noms = new ArrayList<>();
        for (int i = 5; i < args.length; i++) noms.add(args[i]);
        if (noms.isEmpty()) for (String n : Conteneurs.noms()) noms.add(n);
        sortie.mkdirs();
        pages = new BufferedImage[AtlasConteneurs.NOMS.length];
        for (int k = 0; k < pages.length; k++) pages[k] = ImageIO.read(new File(textures, AtlasConteneurs.NOMS[k] + ".png"));
        File dossierRendus = new File(sortie, "rendus");
        if (!rendus.isEmpty()) dossierRendus.mkdirs();

        try (PrintWriter csv = new PrintWriter(new File(sortie, "lod.csv"), StandardCharsets.UTF_8);
             PrintWriter json = new PrintWriter(new File(sortie, "conteneurs_lod.json"), StandardCharsets.UTF_8)) {
            csv.println("nom,faces,verre,cachees,petites,apres_cachees,apres_petites,apres_simplifie");
            json.println("{");
            json.println("  \"version\": 1,");
            json.printf(java.util.Locale.ROOT, "  \"petites_pieces_px\": %s,%n", petites);
            json.printf(java.util.Locale.ROOT, "  \"cellule_px\": %s,%n", cellule);
            json.println("  \"modeles\": {");
            boolean premier = true;
            for (String nom : noms) {
                Mesh[] m = Conteneurs.maillages(nom);
                if (m == null) continue;
                Modele mod = new Modele(m[0], m[1], null);
                boolean[] cachee = mod.cachees();
                int[] comp = mod.composantes();
                boolean[] petite = mod.petitesPieces(petites, comp);
                boolean[] tout = new boolean[mod.n];
                int nc = 0, np = 0;
                for (int q = 0; q < mod.n; q++) {
                    tout[q] = cachee[q] || petite[q];
                    if (cachee[q]) nc++;
                    else if (petite[q]) np++;
                }
                Mesh simple = mod.simplifier(cellule, comp, cachee);   // C : A + simplification (sans retirer les petites pièces)
                int ns = simple.vertexCount() / 4;
                csv.printf("%s,%d,%d,%d,%d,%d,%d,%d%n", nom, mod.n, mod.nVerre, nc, np, mod.n - nc, mod.n - nc - np, ns);
                if (!premier) json.println(",");
                premier = false;
                json.printf("    \"%s\": {\"faces\": %d, \"verre\": %d, \"cachees\": %s, \"petites\": %s, \"apres_simplifie\": %d}",
                        nom, mod.n, mod.nVerre, liste(cachee, null), liste(petite, cachee), ns);
                if (rendus.contains(nom)) {
                    Modele[] versions = {mod, new Modele(mod.garder(cachee), m[1], mod), new Modele(mod.garder(tout), m[1], mod),
                            new Modele(simple, m[1], mod)};
                    BufferedImage planche = new BufferedImage(4 * 360, 360 + 152, BufferedImage.TYPE_INT_RGB);
                    for (int v = 0; v < versions.length; v++) {
                        copier(planche, versions[v].rendu(360, 2, 35, 28), v * 360, 0, 1);
                        copier(planche, versions[v].rendu(48, 8, 35, 28), v * 360 + 108, 360 + 4, 3);
                    }
                    ImageIO.write(planche, "png", new File(dossierRendus, nom + ".png"));
                    // de derrière, en rasant le sol : là où sont la plupart des faces cachées (rien ne doit y manquer)
                    BufferedImage dos = new BufferedImage(2 * 360, 360, BufferedImage.TYPE_INT_RGB);
                    copier(dos, versions[0].rendu(360, 2, 215, 8), 0, 0, 1);
                    copier(dos, versions[1].rendu(360, 2, 215, 8), 360, 0, 1);
                    ImageIO.write(dos, "png", new File(dossierRendus, nom + "_dos.png"));
                }
                System.out.printf("%s : %d faces, %d cachees, %d en petites pieces, %d apres simplification%n", nom, mod.n, nc, np, ns);
            }
            json.println();
            json.println("  }");
            json.println("}");
        }
    }

    private static String liste(boolean[] b, boolean[] sauf) {
        StringBuilder sb = new StringBuilder("[");
        boolean p = true;
        for (int i = 0; i < b.length; i++) {
            if (!b[i] || (sauf != null && sauf[i])) continue;
            if (!p) sb.append(',');
            sb.append(i);
            p = false;
        }
        return sb.append(']').toString();
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
    private static final class Modele {
        final int n, nVerre;
        /** Faces : opaques d'abord (0 .. n-1, numéros des faces du maillage opaque), puis le verre. */
        final float[] d;
        final int total;
        /** Normale « avant » de chaque face (moyenne des normales de ses sommets). */
        final float[] nx, ny, nz;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        /** Face opaque dont toute la texture est pleine (sinon des rayons peuvent passer par ses trous). */
        final boolean[] pleine;
        /** Cadrage des rendus (bornes du modèle d'origine : toutes les versions sont vues pareil). */
        final float[] cadre;
        Grille grille;

        Modele(Mesh opaque, Mesh verre, Modele origine) {
            n = opaque.vertexCount() / 4;
            nVerre = verre.vertexCount() / 4;
            total = n + nVerre;
            d = new float[total * 4 * S];
            System.arraycopy(opaque.data(), 0, d, 0, n * 4 * S);
            System.arraycopy(verre.data(), 0, d, n * 4 * S, nVerre * 4 * S);
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
                float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                if (l < 1e-6f) {   // normales nulles : celle de la géométrie
                    float[] g = geom(q);
                    ax = g[0];
                    ay = g[1];
                    az = g[2];
                    l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                    if (l < 1e-9f) l = 1;
                }
                nx[q] = ax / l;
                ny[q] = ay / l;
                nz[q] = az / l;
                pleine[q] = q < n && pleine(q);
            }
            cadre = origine != null ? origine.cadre : new float[]{minX, minY, minZ, maxX, maxY, maxZ};
        }

        /** Maillage opaque sans les faces retirées. */
        Mesh garder(boolean[] retiree) {
            int k = 0;
            for (int q = 0; q < n; q++) if (!retiree[q]) k++;
            float[] out = new float[k * 4 * S];
            int o = 0;
            for (int q = 0; q < n; q++) {
                if (retiree[q]) continue;
                System.arraycopy(d, q * 4 * S, out, o, 4 * S);
                o += 4 * S;
            }
            return Mesh.of(out, true);
        }

        private float[] geom(int q) {
            int o0 = q * 4 * S, o1 = o0 + S, o2 = o0 + 2 * S, o3 = o0 + 3 * S;
            float ux = d[o2] - d[o0], uy = d[o2 + 1] - d[o0 + 1], uz = d[o2 + 2] - d[o0 + 2];
            float vx = d[o3] - d[o1], vy = d[o3 + 1] - d[o1 + 1], vz = d[o3 + 2] - d[o1 + 2];
            return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
        }

        /** La texture de la face est-elle pleine partout (5 x 5 points) ? */
        private boolean pleine(int q) {
            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    float s = 0.02f + 0.96f * i / 4f, t = 0.02f + 0.96f * j / 4f;
                    float[] uv = bilin(q, s, t, 3);
                    if (transparent(texel(uv[0], uv[1]))) return false;
                }
            }
            return true;
        }

        /** Interpolation bilinéaire d'un attribut (2 flottants à partir de off) sur la face q. */
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
        boolean[] cachees() {
            grille = new Grille(this);
            float[][] dirs = fibonacci(DIRS);
            boolean[] cachee = new boolean[n];
            for (int q = 0; q < n; q++) cachee[q] = !vue(q, dirs);
            return cachee;
        }

        private boolean vue(int q, float[][] dirs) {
            int o = q * 4 * S;
            float e1 = dist(o, o + S), e2 = dist(o + S, o + 2 * S), e3 = dist(o + 2 * S, o + 3 * S), e4 = dist(o + 3 * S, o);
            int ns = Math.max(2, Math.min(8, (int) Math.ceil(Math.max(e1, e3)) + 1));
            int nt = Math.max(2, Math.min(8, (int) Math.ceil(Math.max(e2, e4)) + 1));
            float fx = nx[q], fy = ny[q], fz = nz[q];
            for (float[] dir : dirs) {
                if (dir[0] * fx + dir[1] * fy + dir[2] * fz <= 0.02f) continue;
                for (int i = 0; i < ns; i++) {
                    for (int j = 0; j < nt; j++) {
                        float s = 0.04f + 0.92f * i / (ns - 1), t = 0.04f + 0.92f * j / (nt - 1);
                        float[] p = bilin(q, s, t, 0);
                        float ox = p[0] + fx * 0.02f, oy = p[1] + fy * 0.02f, oz = p[2] + fz * 0.02f;
                        if (grille.sort(ox, oy, oz, dir[0], dir[1], dir[2], q)) return true;
                    }
                }
            }
            return false;
        }

        private float dist(int a, int b) {
            float x = d[a] - d[b], y = d[a + 1] - d[b + 1], z = d[a + 2] - d[b + 2];
            return (float) Math.sqrt(x * x + y * y + z * z);
        }

        // ------------------------------------------------------------------------------------------ pièces d'un seul tenant
        /** Numéro de pièce de chaque face opaque : faces reliées par un sommet commun (à 1/256 px près). */
        int[] composantes() {
            int[] parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
            java.util.HashMap<Long, Integer> vus = new java.util.HashMap<>();
            for (int q = 0; q < n; q++) {
                for (int k = 0; k < 4; k++) {
                    int o = (q * 4 + k) * S;
                    long cle = (Math.round(d[o] * 256) * 73856093L) ^ (Math.round(d[o + 1] * 256) * 19349663L) ^ (Math.round(d[o + 2] * 256) * 83492791L);
                    Integer autre = vus.putIfAbsent(cle, q);
                    if (autre != null) union(parent, q, autre);
                }
            }
            int[] r = new int[n];
            for (int q = 0; q < n; q++) r[q] = find(parent, q);
            return r;
        }

        /** Faces des pièces dont la plus grande dimension est sous le seuil (px). */
        boolean[] petitesPieces(float seuil, int[] comp) {
            boolean[] r = new boolean[n];
            if (seuil <= 0) return r;
            float[] lo = new float[n * 3], hi = new float[n * 3];
            Arrays.fill(lo, Float.MAX_VALUE);
            Arrays.fill(hi, -Float.MAX_VALUE);
            for (int q = 0; q < n; q++) {
                int c = comp[q];
                for (int k = 0; k < 4; k++) {
                    int o = (q * 4 + k) * S;
                    for (int a = 0; a < 3; a++) {
                        lo[c * 3 + a] = Math.min(lo[c * 3 + a], d[o + a]);
                        hi[c * 3 + a] = Math.max(hi[c * 3 + a], d[o + a]);
                    }
                }
            }
            for (int q = 0; q < n; q++) {
                int c = comp[q];
                float dim = Math.max(hi[c * 3] - lo[c * 3], Math.max(hi[c * 3 + 1] - lo[c * 3 + 1], hi[c * 3 + 2] - lo[c * 3 + 2]));
                r[q] = dim < seuil;
            }
            return r;
        }

        // ------------------------------------------------------------------------------------------ simplification
        /**
         * Regroupement des sommets (Rossignac-Borrel), pièce par pièce : dans chaque pièce, les sommets d'une même case de
         * {@code cellule} px vont à leur position moyenne ; une face réduite à moins de 3 sommets distincts (ou d'aire nulle)
         * disparaît. Les pièces ne se mélangent pas (pas de décalque fondu dans sa planche, pas de trou entre deux pièces) ;
         * coordonnées de texture, couleurs et normales de chaque coin inchangées ; un sommet seul dans sa case ne bouge pas.
         */
        Mesh simplifier(float cellule, int[] comp, boolean[] retiree) {
            if (cellule <= 0) return garder(retiree);
            java.util.HashMap<Long, float[]> amas = new java.util.HashMap<>();
            long[] cles = new long[n * 4];
            for (int q = 0; q < n; q++) {
                if (retiree[q]) continue;
                for (int k = 0; k < 4; k++) {
                    int o = (q * 4 + k) * S;
                    long ix = (long) Math.floor(d[o] / cellule) & 0xFFFF, iy = (long) Math.floor(d[o + 1] / cellule) & 0xFFFF;
                    long iz = (long) Math.floor(d[o + 2] / cellule) & 0xFFFF;
                    long cle = ((long) comp[q] << 48) ^ (ix << 32) ^ (iy << 16) ^ iz;
                    cles[q * 4 + k] = cle;
                    float[] a = amas.computeIfAbsent(cle, x -> new float[4]);
                    a[0] += d[o];
                    a[1] += d[o + 1];
                    a[2] += d[o + 2];
                    a[3]++;
                }
            }
            float[] out = new float[n * 4 * S];
            int o2 = 0;
            float[] p = new float[12];
            for (int q = 0; q < n; q++) {
                if (retiree[q]) continue;
                long c0 = cles[q * 4], c1 = cles[q * 4 + 1], c2 = cles[q * 4 + 2], c3 = cles[q * 4 + 3];
                int distincts = 1 + (c1 != c0 ? 1 : 0) + (c2 != c0 && c2 != c1 ? 1 : 0) + (c3 != c0 && c3 != c1 && c3 != c2 ? 1 : 0);
                if (distincts < 3) continue;
                for (int k = 0; k < 4; k++) {
                    float[] a = amas.get(cles[q * 4 + k]);
                    p[k * 3] = a[0] / a[3];
                    p[k * 3 + 1] = a[1] / a[3];
                    p[k * 3 + 2] = a[2] / a[3];
                }
                if (aire(p, 0, 1, 2) + aire(p, 0, 2, 3) < 1e-4f) continue;
                for (int k = 0; k < 4; k++) {
                    System.arraycopy(d, (q * 4 + k) * S, out, o2, S);
                    out[o2] = p[k * 3];
                    out[o2 + 1] = p[k * 3 + 1];
                    out[o2 + 2] = p[k * 3 + 2];
                    o2 += S;
                }
            }
            return Mesh.of(Arrays.copyOf(out, o2), true);
        }

        private static float aire(float[] p, int a, int b, int c) {
            float ux = p[b * 3] - p[a * 3], uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
            float vx = p[c * 3] - p[a * 3], vy = p[c * 3 + 1] - p[a * 3 + 1], vz = p[c * 3 + 2] - p[a * 3 + 2];
            float x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
            return 0.5f * (float) Math.sqrt(x * x + y * y + z * z);
        }

        private static int find(int[] p, int i) {
            while (p[i] != i) {
                p[i] = p[p[i]];
                i = p[i];
            }
            return i;
        }

        private static void union(int[] p, int a, int b) {
            int ra = find(p, a), rb = find(p, b);
            if (ra != rb) p[ra] = rb;
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
            // axes de la caméra : vers la caméra (c), droite (r), haut (u = c x r)
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
                // envers : pas dessiné (comme le jeu)
                if (nx[q] * cx + ny[q] * cy + nz[q] * cz <= 0) continue;
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
            if (Math.abs(area) < 1e-9f) return;
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
                    if (verre) {   // verre : mélangé, sans écrire la profondeur
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

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
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
        final Modele m;
        final float x0, y0, z0, c;
        final int gx, gy, gz;
        final int[][] cases;
        final int[] marque;
        int rayon;

        Grille(Modele m) {
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
            int S = Mesh.STRIDE;
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

        /** Le rayon (o, dir) sort-il du modèle sans être arrêté ? (la face « soi » est ignorée) */
        boolean sort(float ox, float oy, float oz, float dx, float dy, float dz, int soi) {
            rayon++;
            // fin du parcours : sortie de la boîte par les côtés ou le haut ; le sol (y = 0) sous le modèle arrête le rayon
            float tSortie = Float.MAX_VALUE;
            float bx1 = x0 + gx * c, by1 = y0 + gy * c, bz1 = z0 + gz * c;
            if (dx > 0) tSortie = Math.min(tSortie, (bx1 - ox) / dx);
            else if (dx < 0) tSortie = Math.min(tSortie, (x0 - ox) / dx);
            if (dz > 0) tSortie = Math.min(tSortie, (bz1 - oz) / dz);
            else if (dz < 0) tSortie = Math.min(tSortie, (z0 - oz) / dz);
            if (dy > 0) tSortie = Math.min(tSortie, (by1 - oy) / dy);
            if (dy < 0) {
                float tSol = (0 - oy) / dy;
                if (tSol < tSortie) return false;   // atteint le sol sous le modèle : bloc d'appui
            }
            tSortie = Math.max(0, tSortie);
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
                        if (arrete(q, ox, oy, oz, dx, dy, dz, tSortie)) return false;
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
            return true;
        }

        /** La face q arrête-t-elle ce rayon ? Seulement vue de face (l'envers n'est pas dessiné), sur une partie pleine. */
        private boolean arrete(int q, float ox, float oy, float oz, float dx, float dy, float dz, float tMax) {
            // le rayon va du point vers l'oeil : la face cache le point si l'oeil voit son avant, donc si dir . n > 0
            if (dx * m.nx[q] + dy * m.ny[q] + dz * m.nz[q] <= 0) return false;
            return touche(q, 0, 1, 2, ox, oy, oz, dx, dy, dz, tMax) || touche(q, 0, 2, 3, ox, oy, oz, dx, dy, dz, tMax);
        }

        private boolean touche(int q, int a, int b, int cc, float ox, float oy, float oz, float dx, float dy, float dz, float tMax) {
            int S = Mesh.STRIDE, o = q * 4 * S;
            float[] d = m.d;
            int pa = o + a * S, pb = o + b * S, pc = o + cc * S;
            float e1x = d[pb] - d[pa], e1y = d[pb + 1] - d[pa + 1], e1z = d[pb + 2] - d[pa + 2];
            float e2x = d[pc] - d[pa], e2y = d[pc + 1] - d[pa + 1], e2z = d[pc + 2] - d[pa + 2];
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
