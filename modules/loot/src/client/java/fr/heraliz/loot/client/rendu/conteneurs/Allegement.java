package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version allégée des conteneurs T1-T6 cuits dans le rendu du terrain (« C » validée par le propriétaire le 31/10), en
 * Java pur : le même code sert au jeu et à l'outil {@code modules/loot/outils/lod_props.py} (qui écrit les données).
 * <ol>
 * <li><b>faces cachées</b> (sans perte) : calculées HORS du jeu par l'outil (rayons lancés depuis chaque face), lues dans
 * {@link #CHEMIN} : les faces qu'aucun regard n'atteint sont retirées ; celles qu'on ne voit que si le bloc du dessous
 * n'est pas plein (dessous d'un meuble sur pieds, fond d'une caisse posée…) vont dans la liste « dessous » du modèle :
 * Minecraft ne les dessine que si le bloc d'en dessous ne les cache pas ;</li>
 * <li><b>simplification</b> : faite ICI, une fois par modèle au premier affichage (quelques millisecondes), par
 * regroupement des sommets à {@link #CELLULE} px près, pièce par pièce. Les cases « gelées » par l'outil (là où le
 * regroupement retournerait ou tordrait une face, collerait deux faces l'une sur l'autre, aplatirait une paroi, ouvrirait
 * une fente entre deux pièces qui se touchent, percerait le modèle ou effacerait une petite pièce) gardent leurs sommets.
 * Le résultat est comparé à l'empreinte écrite par l'outil : s'il diffère (maillage changé, calcul flottant différent),
 * le modèle garde ses faces cachées retirées sans simplification ; si le nombre de faces ne correspond plus, le modèle
 * complet.</li>
 * </ol>
 * Coordonnées de texture, couleurs cuites et normales des coins ne changent jamais ; le verre n'est jamais touché.
 * Données : {@code conteneurs_lod.txt}, écrit par l'outil, jamais à la main.
 */
public final class Allegement {
    private Allegement() {
    }

    /** Taille des cases du regroupement (px). */
    public static final float CELLULE = 0.4f;
    public static final String CHEMIN = "/assets/heraliz_loot/lod/conteneurs_lod.txt";
    private static final int S = Mesh.STRIDE;

    /** Ce que l'outil a écrit pour un conteneur. */
    public record Fiche(int faces, int[] retirees, int[] dessous, int[] gel, boolean simplifier, long empreinte) {
    }

    /** Maillage opaque allégé : faces toujours dessinées, faces « dessous » (cachées par un bloc plein en dessous). */
    public record Version(Mesh garde, Mesh dessous, boolean simplifiee) {
        public int faces() {
            return (garde.vertexCount() + dessous.vertexCount()) / 4;
        }
    }

    private static volatile Map<String, Fiche> fiches;
    private static final Map<String, Optional<Version>> FAITES = new ConcurrentHashMap<>();

    /** Version allégée du conteneur (calculée une fois, gardée), ou null : garder le modèle complet. */
    public static Version de(String nom, Mesh opaque) {
        Optional<Version> v = FAITES.get(nom);
        if (v == null) {
            Fiche f = fiches().get(nom);
            Version r = null;
            try {
                r = f == null ? null : calculer(opaque, f);
            } catch (RuntimeException e) {
                r = null;   // jamais de plantage du rendu du terrain : modèle complet
            }
            v = Optional.ofNullable(r);
            FAITES.putIfAbsent(nom, v);
        }
        return v.orElse(null);
    }

    private static Map<String, Fiche> fiches() {
        Map<String, Fiche> m = fiches;
        if (m == null) {
            synchronized (Allegement.class) {
                m = fiches;
                if (m == null) {
                    m = Map.of();
                    try (InputStream in = Allegement.class.getResourceAsStream(CHEMIN)) {
                        if (in != null) m = lire(new InputStreamReader(in, StandardCharsets.UTF_8));
                    } catch (IOException | RuntimeException e) {
                        m = Map.of();   // données absentes ou abîmées : modèles complets
                    }
                    fiches = m;
                }
            }
        }
        return m;
    }

    /** Lecture du fichier de l'outil (une ligne par conteneur, voir {@link #ecrire}). */
    public static Map<String, Fiche> lire(Reader r) throws IOException {
        Map<String, Fiche> m = new HashMap<>();
        BufferedReader br = new BufferedReader(r);
        String l;
        while ((l = br.readLine()) != null) {
            l = l.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] c = l.split(" ", -1);
            if (c.length != 7) throw new IOException("ligne illisible : " + l);
            m.put(c[0], new Fiche(Integer.parseInt(c[1]), plages(c[3]), plages(c[4]), plages(c[5]), c[2].equals("C"),
                    Long.parseUnsignedLong(c[6], 16)));
        }
        return m;
    }

    /** Ligne du fichier : nom, faces, C|A, retirées, dessous, gel, empreinte (listes en plages « 3-7,12 », « - » si vide). */
    public static String ecrire(String nom, Fiche f) {
        return nom + " " + f.faces() + " " + (f.simplifier() ? "C" : "A") + " " + plages(f.retirees()) + " "
                + plages(f.dessous()) + " " + plages(f.gel()) + " " + Long.toHexString(f.empreinte());
    }

    static String plages(int[] v) {
        if (v.length == 0) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; ) {
            int j = i;
            while (j + 1 < v.length && v[j + 1] == v[j] + 1) j++;
            if (sb.length() > 0) sb.append(',');
            sb.append(v[i]);
            if (j > i) sb.append('-').append(v[j]);
            i = j + 1;
        }
        return sb.toString();
    }

    static int[] plages(String s) {
        if (s.equals("-")) return new int[0];
        int[] out = new int[16];
        int n = 0;
        for (String p : s.split(",")) {
            int t = p.indexOf('-');
            int a = Integer.parseInt(t < 0 ? p : p.substring(0, t)), b = t < 0 ? a : Integer.parseInt(p.substring(t + 1));
            if (b < a) throw new IllegalArgumentException(p);
            for (int x = a; x <= b; x++) {
                if (n == out.length) out = Arrays.copyOf(out, n * 2);
                out[n++] = x;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Version allégée d'après la fiche, ou null si la fiche ne correspond plus au maillage. */
    public static Version calculer(Mesh opaque, Fiche f) {
        int n = opaque.vertexCount() / 4;
        if (n != f.faces()) return null;
        boolean[] retiree = marques(n, f.retirees());
        boolean[] dessous = marques(n, f.dessous());
        if (retiree == null || dessous == null) return null;
        if (f.simplifier()) {
            Resultat r = simplifier(opaque, retiree, dessous, f.gel(), CELLULE);
            if (r != null && r.empreinte() == f.empreinte()) return new Version(r.garde(), r.dessous(), true);
        }
        // sans simplification : seulement les faces cachées (indices bruts, insensibles aux arrondis)
        Resultat r = simplifier(opaque, retiree, dessous, null, 0);
        return new Version(r.garde(), r.dessous(), false);
    }

    private static boolean[] marques(int n, int[] idx) {
        boolean[] b = new boolean[n];
        for (int i : idx) {
            if (i < 0 || i >= n) return null;
            b[i] = true;
        }
        return b;
    }

    /** Résultat du regroupement : faces gardées, faces « dessous », numéro d'origine de chaque face sortie, empreinte. */
    public record Resultat(Mesh garde, Mesh dessous, int[] origineGarde, int[] origineDessous, long empreinte) {
    }

    /**
     * Regroupement des sommets (Rossignac-Borrel) pièce par pièce : dans une pièce (faces reliées par un sommet commun),
     * les sommets d'une même case de {@code cellule} px vont à leur position moyenne ; une face réduite à moins de 3 sommets
     * distincts ou d'aire nulle disparaît. Un sommet posé sur un bord du bloc (coordonnée multiple de 16) n'en bouge pas
     * (contact avec le sol et les murs gardé, faces des bords toujours reconnues). Les cases des sommets de {@code gel}
     * ne bougent pas. {@code cellule} = 0 : aucun regroupement (faces retirées seulement).
     */
    public static Resultat simplifier(Mesh opaque, boolean[] retiree, boolean[] dessous, int[] gel, float cellule) {
        float[] d = opaque.data();
        int n = opaque.vertexCount() / 4;
        long[] cles = new long[n * 4];
        float[] pos = new float[n * 4 * 3];
        if (cellule > 0) {
            int[] comp = composantes(d, n);
            for (int q = 0; q < n; q++) {
                for (int k = 0; k < 4; k++) cles[q * 4 + k] = cle(d, (q * 4 + k) * S, comp[q], cellule);
            }
            Set<Long> geles = new HashSet<>();
            if (gel != null) for (int v : gel) if (v >= 0 && v < n * 4) geles.add(cles[v]);
            Map<Long, float[]> amas = new HashMap<>();
            for (int q = 0; q < n; q++) {
                if (retiree[q]) continue;
                for (int k = 0; k < 4; k++) {
                    int v = q * 4 + k, o = v * S;
                    long c = cles[v];
                    if (geles.contains(c)) {
                        cles[v] = exacte(d, o);   // case gelée : le sommet garde sa place exacte
                        continue;
                    }
                    float[] a = amas.computeIfAbsent(c, x -> new float[4]);
                    a[0] += d[o];
                    a[1] += d[o + 1];
                    a[2] += d[o + 2];
                    a[3]++;
                }
            }
            for (int q = 0; q < n; q++) {
                if (retiree[q]) continue;
                for (int k = 0; k < 4; k++) {
                    int v = q * 4 + k, o = v * S;
                    float[] a = cles[v] < 0 ? null : amas.get(cles[v]);
                    if (a == null) {
                        pos[v * 3] = d[o];
                        pos[v * 3 + 1] = d[o + 1];
                        pos[v * 3 + 2] = d[o + 2];
                    } else {
                        pos[v * 3] = a[0] / a[3];
                        pos[v * 3 + 1] = a[1] / a[3];
                        pos[v * 3 + 2] = a[2] / a[3];
                    }
                }
            }
        } else {
            for (int q = 0; q < n; q++) {
                for (int k = 0; k < 4; k++) {
                    int v = q * 4 + k, o = v * S;
                    cles[v] = v;   // tous distincts : aucune face ne disparaît
                    pos[v * 3] = d[o];
                    pos[v * 3 + 1] = d[o + 1];
                    pos[v * 3 + 2] = d[o + 2];
                }
            }
        }
        float[] g = new float[n * 4 * S], b = new float[n * 4 * S];
        int[] og = new int[n], ob = new int[n];
        int ng = 0, nb = 0;
        long h = 0xcbf29ce484222325L;
        for (int q = 0; q < n; q++) {
            if (retiree[q]) continue;
            if (cellule > 0) {
                long c0 = cles[q * 4], c1 = cles[q * 4 + 1], c2 = cles[q * 4 + 2], c3 = cles[q * 4 + 3];
                int distincts = 1 + (c1 != c0 ? 1 : 0) + (c2 != c0 && c2 != c1 ? 1 : 0) + (c3 != c0 && c3 != c1 && c3 != c2 ? 1 : 0);
                if (distincts < 3) continue;
                if (aire(pos, q * 4, q * 4 + 1, q * 4 + 2) + aire(pos, q * 4, q * 4 + 2, q * 4 + 3) < 1e-4f) continue;
            }
            boolean bas = dessous[q];
            float[] out = bas ? b : g;
            int o2 = (bas ? nb : ng) * 4 * S;
            for (int k = 0; k < 4; k++) {
                int v = q * 4 + k;
                System.arraycopy(d, v * S, out, o2 + k * S, S);
                out[o2 + k * S] = pos[v * 3];
                out[o2 + k * S + 1] = pos[v * 3 + 1];
                out[o2 + k * S + 2] = pos[v * 3 + 2];
                // empreinte : positions arrondies au 1/32 px (insensible au dernier chiffre des calculs flottants)
                for (int a = 0; a < 3; a++) h = (h ^ Math.round(pos[v * 3 + a] * 32f)) * 0x100000001b3L;
            }
            h = (h ^ (bas ? -1 - q : q)) * 0x100000001b3L;
            if (bas) ob[nb++] = q;
            else og[ng++] = q;
        }
        return new Resultat(Mesh.of(Arrays.copyOf(g, ng * 4 * S), true), Mesh.of(Arrays.copyOf(b, nb * 4 * S), true),
                Arrays.copyOf(og, ng), Arrays.copyOf(ob, nb), h);
    }

    /** Case d'un sommet : pièce, case de la grille, et bords du bloc sur lesquels il est posé (ceux-là ne bougent pas). */
    public static long cle(float[] d, int o, int comp, float cellule) {
        long k = (long) (comp & 0xFFFFF) << 42;
        for (int a = 0; a < 3; a++) {
            float x = d[o + a];
            float r = Math.round(x / 16f) * 16f;
            long i;
            if (Math.abs(x - r) < 1e-3f) i = 0x2000 | (((long) Math.round(r / 16f) + 64) & 0xFFF);   // sur un bord du bloc
            else i = ((long) Math.floor(x / cellule) + 4096) & 0x1FFF;
            k |= i << (a * 14);
        }
        return k;
    }

    /** Identifiant d'une place exacte (sommet d'une case gelée) : négatif, jamais confondu avec une case. */
    private static long exacte(float[] d, int o) {
        return Long.MIN_VALUE | position(d, o);
    }

    /** Position arrondie au 1/256 px, en un nombre (21 bits par axe). */
    static long position(float[] d, int o) {
        long k = 0;
        for (int a = 0; a < 3; a++) k |= (((long) Math.round(d[o + a] * 256f) + (1 << 20)) & 0x1FFFFF) << (a * 21);
        return k;
    }

    /** Pièce de chaque face : faces reliées par un sommet commun (à 1/256 px près). */
    public static int[] composantes(float[] d, int n) {
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        Map<Long, Integer> vus = new HashMap<>();
        for (int q = 0; q < n; q++) {
            for (int k = 0; k < 4; k++) {
                Integer autre = vus.putIfAbsent(position(d, (q * 4 + k) * S), q);
                if (autre != null) union(parent, q, autre);
            }
        }
        // numéros = plus petite face de la pièce (indépendant de l'ordre des unions)
        int[] r = new int[n];
        int[] petite = new int[n];
        Arrays.fill(petite, Integer.MAX_VALUE);
        for (int q = 0; q < n; q++) {
            int f = find(parent, q);
            petite[f] = Math.min(petite[f], q);
        }
        for (int q = 0; q < n; q++) r[q] = petite[find(parent, q)];
        return r;
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
}
