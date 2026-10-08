import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.Designs;
import fr.heraliz.crates.client.render.scene.DrawList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcule la forme (hitbox) de chaque caisse a partir de son vrai modele 3D au repos, et ecrit
 * src/commun/java/fr/heraliz/crates/block/CrateFormes.java. Lance par outils/formes_caisses.py (a relancer apres tout
 * changement de modele d'une caisse, de sa taille ou de son cadenas).
 * <p>Methode : chaque face opaque ou translucide du modele (pas les lueurs, sprites ni particules) est echantillonnee dans
 * une grille de cubes de 2 px (1/8 de bloc) sur 3 x 3 x 3 blocs ; l'interieur ferme est rempli, les fentes d'un cube sont
 * bouchees (fermeture), les miettes isolees retirees ; les cubes sont enfin regroupes en boites.
 * Repere : px, origine au coin bas nord-ouest du bloc principal, caisse tournee vers le sud (+Z), comme le modele.
 */
public final class FormesCaisses {
    /** Taille d'un cube de la grille (px) : 2 px = contour net, assez fin pour suivre les bords. */
    static int RES = Integer.getInteger("res", 2);
    /** Grille (en cubes) : x, z de -16 a 32 px (3 blocs centres sur le bloc principal), y de 0 a 48 px. */
    static int G = 48 / RES, OFF = 16 / RES;
    /** Miettes de moins de 24 px³ : retirees (eclats, petites lueurs taillees). */
    static final int MIN_PIECE_PX3 = 24;

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        StringBuilder data = new StringBuilder();
        for (int a = 1; a < args.length; a++) {
            String id = args[a];
            boolean[] vox = voxelize(Designs.create(id));
            close(vox);
            removeCrumbs(vox);
            List<int[]> boxes = boxes(vox);
            int[] bb = bounds(vox);
            System.out.printf("%-18s %4d boites, x %d..%d  y %d..%d  z %d..%d (px)%n", id, boxes.size(),
                    (bb[0] - OFF) * RES, (bb[3] - OFF) * RES, bb[1] * RES, bb[4] * RES, (bb[2] - OFF) * RES, (bb[5] - OFF) * RES);
            data.append("        m.put(\"").append(id).append("\", \"");
            for (int[] b : boxes) {
                for (int k = 0; k < 6; k++) data.append((char) (BASE + b[k] * RES));
            }
            data.append("\");\n");
        }
        String src = """
                package fr.heraliz.crates.block;

                import java.util.HashMap;
                import java.util.Map;

                /**
                 * FICHIER GENERE par outils/formes_caisses.py (mods/heraliz-crates/outils/FormesCaisses.java) : ne pas modifier a la
                 * main, relancer l'outil apres tout changement de modele d'une caisse.
                 * <p>Forme de chaque caisse (son vrai modele au repos, en cubes de 2 px), regroupee en boites. Par boite, 6 caracteres :
                 * x0 y0 z0 x1 y1 z1 = (caractere - %d) px ; x et z comptes depuis -16 px (le bloc principal est en 16..32), y depuis
                 * 0 px. Caisse tournee vers le sud (+Z), comme le modele.
                 */
                final class CrateFormes {
                    private CrateFormes() {
                    }

                    /** Decalage des caracteres et origine de la grille en x et z (px). */
                    static final int BASE = %d, OFF = %d;

                    static final Map<String, String> BOXES;

                    static {
                        Map<String, String> m = new HashMap<>();
                %s        BOXES = m;
                    }
                }
                """.formatted(BASE, BASE, OFF * RES, data);
        Files.writeString(out, src, StandardCharsets.UTF_8);
    }

    /** Caracteres '#' (35) a 'S' (83) : ni guillemet ni barre oblique inverse dans la chaine. */
    static final int BASE = 35;

    // =================================================================== voxelisation
    static int idx(int x, int y, int z) {
        return (y * G + z) * G + x;
    }

    static boolean[] voxelize(Design d) {
        CrateState s = new CrateState();
        s.time = 0;
        s.clock = 0;
        s.runes = false;
        s.beams = false;
        DrawList list = new DrawList();
        d.render(s, list);
        boolean[] surf = new boolean[G * G * G];
        Matrix3f nm = new Matrix3f();
        Vector3f[] p = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        Vector3f n = new Vector3f(), q = new Vector3f();
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.mesh == null || o.sprite != null) continue;
            if (o.type != DrawList.SOLID && o.type != DrawList.TRANSLUCENT) continue;
            Matrix4f m = o.m;
            m.normal(nm);
            Mesh mesh = o.mesh;
            float[] v = mesh.data();
            for (int k = 0; k + 3 < mesh.vertexCount(); k += 4) {
                n.zero();
                for (int j = 0; j < 4; j++) {
                    int b = (k + j) * Mesh.STRIDE;
                    m.transformPosition(v[b], v[b + 1], v[b + 2], p[j]);
                    n.add(v[b + 5], v[b + 6], v[b + 7]);
                }
                nm.transform(n);
                if (n.lengthSquared() > 1e-8f) n.normalize();
                // echantillons legerement rentres vers l'interieur : une face posee sur une limite de cube tombe dans le bon
                tri(surf, p[0], p[1], p[2], n, q);
                tri(surf, p[0], p[2], p[3], n, q);
            }
        }
        // interieur : tout ce qui n'est pas atteint depuis l'exterieur
        boolean[] out = new boolean[G * G * G];
        ArrayDeque<Integer> todo = new ArrayDeque<>();
        for (int y = 0; y < G; y++) {
            for (int z = 0; z < G; z++) {
                for (int x = 0; x < G; x++) {
                    boolean edge = x == 0 || z == 0 || x == G - 1 || z == G - 1 || y == G - 1;
                    int c = idx(x, y, z);
                    // le sol (y = 0) n'est pas un bord ouvert : une caisse posee y est fermee par le dessous
                    if (edge && !surf[c]) {
                        out[c] = true;
                        todo.add(c);
                    }
                }
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!todo.isEmpty()) {
            int c = todo.poll();
            int x = c % G, z = (c / G) % G, y = c / (G * G);
            for (int[] dd : dirs) {
                int nx = x + dd[0], ny = y + dd[1], nz = z + dd[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= G || ny >= G || nz >= G) continue;
                int e = idx(nx, ny, nz);
                if (out[e] || surf[e]) continue;
                out[e] = true;
                todo.add(e);
            }
        }
        boolean[] vox = new boolean[G * G * G];
        for (int c = 0; c < vox.length; c++) vox[c] = !out[c];
        return vox;
    }

    static void tri(boolean[] surf, Vector3f a, Vector3f b, Vector3f c, Vector3f n, Vector3f q) {
        float lab = a.distance(b), lac = a.distance(c), lbc = b.distance(c);
        int steps = Math.max(1, (int) Math.ceil(Math.max(lab, Math.max(lac, lbc)) / (0.25f * RES)));
        for (int i = 0; i <= steps; i++) {
            for (int j = 0; j <= steps - i; j++) {
                float u = i / (float) steps, w = j / (float) steps;
                q.set(a).mul(1 - u - w).add(b.x * u, b.y * u, b.z * u).add(c.x * w, c.y * w, c.z * w).sub(n.x * 0.02f, n.y * 0.02f, n.z * 0.02f);
                int x = (int) Math.floor(q.x / RES) + OFF, y = (int) Math.floor(q.y / RES), z = (int) Math.floor(q.z / RES) + OFF;
                if (x < 0 || y < 0 || z < 0 || x >= G || y >= G || z >= G) continue;
                surf[idx(x, y, z)] = true;
            }
        }
    }

    // =================================================================== nettoyage
    /** Fermeture (dilatation puis erosion de 1 cube) : bouche les fentes de 1 px entre deux pieces. */
    static void close(boolean[] v) {
        boolean[] d = morph(v, true);
        boolean[] e = morph(d, false);
        System.arraycopy(e, 0, v, 0, v.length);
    }

    static boolean[] morph(boolean[] v, boolean dilate) {
        boolean[] r = new boolean[v.length];
        for (int y = 0; y < G; y++) {
            for (int z = 0; z < G; z++) {
                for (int x = 0; x < G; x++) {
                    boolean res = !dilate;
                    for (int dy = -1; dy <= 1 && res != dilate; dy++) {
                        for (int dz = -1; dz <= 1 && res != dilate; dz++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int nx = x + dx, ny = y + dy, nz = z + dz;
                                // le sol prolonge la caisse vers le bas (l'erosion ne ronge pas la base)
                                boolean on = ny < 0 ? v[idx(Math.min(G - 1, Math.max(0, nx)), 0, Math.min(G - 1, Math.max(0, nz)))]
                                        : nx >= 0 && nz >= 0 && nx < G && ny < G && nz < G && v[idx(nx, ny, nz)];
                                if (dilate && on || !dilate && !on) {
                                    res = dilate;
                                    break;
                                }
                            }
                        }
                    }
                    r[idx(x, y, z)] = res;
                }
            }
        }
        return r;
    }

    static void removeCrumbs(boolean[] v) {
        boolean[] seen = new boolean[v.length];
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int s = 0; s < v.length; s++) {
            if (!v[s] || seen[s]) continue;
            List<Integer> piece = new ArrayList<>();
            ArrayDeque<Integer> todo = new ArrayDeque<>();
            todo.add(s);
            seen[s] = true;
            while (!todo.isEmpty()) {
                int c = todo.poll();
                piece.add(c);
                int x = c % G, z = (c / G) % G, y = c / (G * G);
                for (int[] dd : dirs) {
                    int nx = x + dd[0], ny = y + dd[1], nz = z + dd[2];
                    if (nx < 0 || ny < 0 || nz < 0 || nx >= G || ny >= G || nz >= G) continue;
                    int e = idx(nx, ny, nz);
                    if (!v[e] || seen[e]) continue;
                    seen[e] = true;
                    todo.add(e);
                }
            }
            if (piece.size() * RES * RES * RES < MIN_PIECE_PX3) for (int c : piece) v[c] = false;
        }
    }

    // =================================================================== boites
    /** Regroupement glouton : chaque boite s'etend en x, puis en z, puis en y tant que tout est plein. */
    static List<int[]> boxes(boolean[] src) {
        boolean[] v = src.clone();
        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < G; y++) {
            for (int z = 0; z < G; z++) {
                for (int x = 0; x < G; x++) {
                    if (!v[idx(x, y, z)]) continue;
                    int x1 = x + 1;
                    while (x1 < G && v[idx(x1, y, z)]) x1++;
                    int z1 = z + 1;
                    while (z1 < G && row(v, x, x1, y, z1)) z1++;
                    int y1 = y + 1;
                    while (y1 < G && slab(v, x, x1, y1, z, z1)) y1++;
                    for (int yy = y; yy < y1; yy++) {
                        for (int zz = z; zz < z1; zz++) {
                            for (int xx = x; xx < x1; xx++) v[idx(xx, yy, zz)] = false;
                        }
                    }
                    out.add(new int[]{x, y, z, x1, y1, z1});
                }
            }
        }
        return out;
    }

    static boolean row(boolean[] v, int x0, int x1, int y, int z) {
        for (int x = x0; x < x1; x++) {
            if (!v[idx(x, y, z)]) return false;
        }
        return true;
    }

    static boolean slab(boolean[] v, int x0, int x1, int y, int z0, int z1) {
        for (int z = z0; z < z1; z++) {
            if (!row(v, x0, x1, y, z)) return false;
        }
        return true;
    }

    static int[] bounds(boolean[] v) {
        int[] b = {G, G, G, -1, -1, -1};
        for (int c = 0; c < v.length; c++) {
            if (!v[c]) continue;
            int x = c % G, z = (c / G) % G, y = c / (G * G);
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x + 1);
            b[4] = Math.max(b[4], y + 1);
            b[5] = Math.max(b[5], z + 1);
        }
        return b;
    }
}
