package fr.heraliz.casino.client.render.mesh;

/**
 * Maillage de quads. Par sommet : x y z (pixels = 1/16 bloc), u v (atlas), nx ny nz, r g b (couleur/ombrage du sommet).
 * Classe "pure" (sans dependance Minecraft) : le rendu est fait par le moteur de scene.
 * Les maillages "statiques" (construits une fois) peuvent etre mis en cache (filtre lumineux) ;
 * les maillages "dynamiques" (reconstruits a chaque image : tentacules, pattes...) non.
 */
public final class Mesh {
    public static final int STRIDE = 11;
    public static final Mesh EMPTY = new Mesh(new float[0], true);

    private final float[] v;
    private final int count;
    public final boolean cacheable;

    Mesh(float[] data, boolean cacheable) {
        this(data, data.length, cacheable);
    }

    Mesh(float[] data, int floats, boolean cacheable) {
        this.v = data;
        this.count = floats / STRIDE;
        this.cacheable = cacheable;
    }

    /** Maillage a partir de donnees brutes (STRIDE flottants par sommet, 4 sommets par quad). */
    public static Mesh of(float[] data, boolean cacheable) {
        return new Mesh(data, cacheable);
    }

    public float[] data() {
        return v;
    }

    public int vertexCount() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    /** Test sur le centre UV (atlas) d'un quad. */
    @FunctionalInterface
    public interface QuadFilter {
        boolean keep(float u, float v);
    }

    /** Nouveau maillage ne gardant que les quads acceptes par le filtre. */
    public Mesh filter(QuadFilter f) {
        int quads = count / 4;
        float[] out = new float[count * STRIDE];
        int n = 0;
        for (int q = 0; q < quads; q++) {
            int o = q * STRIDE * 4;
            float u = 0, w = 0;
            for (int k = 0; k < 4; k++) {
                u += v[o + k * STRIDE + 3];
                w += v[o + k * STRIDE + 4];
            }
            if (f.keep(u / 4f, w / 4f)) {
                System.arraycopy(v, o, out, n, STRIDE * 4);
                n += STRIDE * 4;
            }
        }
        if (n == count * STRIDE) return this;
        return new Mesh(java.util.Arrays.copyOf(out, n), cacheable);
    }

    /** Concatenation de plusieurs maillages. */
    public static Mesh concat(Mesh... meshes) {
        int total = 0;
        for (Mesh m : meshes) if (m != null) total += m.count * STRIDE;
        float[] out = new float[total];
        int o = 0;
        boolean cache = true;
        for (Mesh m : meshes) {
            if (m == null) continue;
            System.arraycopy(m.v, 0, out, o, m.count * STRIDE);
            o += m.count * STRIDE;
            cache &= m.cacheable;
        }
        return new Mesh(out, cache);
    }
}
