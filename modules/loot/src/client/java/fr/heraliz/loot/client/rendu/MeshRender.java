package fr.heraliz.loot.client.rendu;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.loot.client.rendu.mesh.Mesh;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Envoi d'un Mesh (positions en pixels, couleurs de sommet) vers un VertexConsumer Minecraft.
 * La matrice "pose" contient deja le passage pixels -> blocs.
 * <p>
 * Optimise : position transformee ici et un seul appel par sommet (le tampon de Minecraft a un chemin rapide pour ce
 * format) ; quand la matrice des normales ne fait que tourner et mettre a l'echelle uniformement (cas courant), elle
 * est normalisee une fois pour toutes au lieu de renormaliser chaque sommet.
 */
public final class MeshRender {
    private MeshRender() {
    }

    /**
     * @param vcol true : la couleur finale est multipliee par la couleur (ombrage) de chaque sommet
     */
    public static void render(Mesh mesh, Matrix4f pose, Matrix3f normal, VertexConsumer vc, float r, float g, float b, float a,
                              int light, int overlay, boolean vcol) {
        if (mesh == null || mesh.isEmpty()) return;
        float[] d = mesh.data();
        int count = mesh.vertexCount();
        float m00 = pose.m00(), m01 = pose.m01(), m02 = pose.m02(), m10 = pose.m10(), m11 = pose.m11(), m12 = pose.m12();
        float m20 = pose.m20(), m21 = pose.m21(), m22 = pose.m22(), m30 = pose.m30(), m31 = pose.m31(), m32 = pose.m32();
        float n00 = normal.m00(), n01 = normal.m01(), n02 = normal.m02(), n10 = normal.m10(), n11 = normal.m11(), n12 = normal.m12();
        float n20 = normal.m20(), n21 = normal.m21(), n22 = normal.m22();
        // echelle uniforme ? colonnes de meme longueur et orthogonales : on normalise la matrice une seule fois
        float l0 = n00 * n00 + n01 * n01 + n02 * n02, l1 = n10 * n10 + n11 * n11 + n12 * n12, l2 = n20 * n20 + n21 * n21 + n22 * n22;
        float d01 = n00 * n10 + n01 * n11 + n02 * n12, d02 = n00 * n20 + n01 * n21 + n02 * n22, d12 = n10 * n20 + n11 * n21 + n12 * n22;
        boolean uniform = l0 > 1e-12f && Math.abs(l1 - l0) < l0 * 1e-3f && Math.abs(l2 - l0) < l0 * 1e-3f
                && Math.abs(d01) < l0 * 1e-3f && Math.abs(d02) < l0 * 1e-3f && Math.abs(d12) < l0 * 1e-3f;
        if (uniform) {
            float k = 1f / (float) Math.sqrt(l0);
            n00 *= k;
            n01 *= k;
            n02 *= k;
            n10 *= k;
            n11 *= k;
            n12 *= k;
            n20 *= k;
            n21 *= k;
            n22 *= k;
        }
        float ca = Math.min(1f, a);
        for (int i = 0, o = 0; i < count; i++, o += Mesh.STRIDE) {
            float x = d[o], y = d[o + 1], z = d[o + 2];
            float nx = d[o + 5], ny = d[o + 6], nz = d[o + 7];
            float tx = n00 * nx + n10 * ny + n20 * nz;
            float ty = n01 * nx + n11 * ny + n21 * nz;
            float tz = n02 * nx + n12 * ny + n22 * nz;
            if (!uniform) {
                float l = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (l > 1e-6f) {
                    tx /= l;
                    ty /= l;
                    tz /= l;
                }
            }
            float cr = r, cg = g, cb = b;
            if (vcol) {
                cr *= d[o + 8];
                cg *= d[o + 9];
                cb *= d[o + 10];
            }
            vc.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
                    Math.min(1f, cr), Math.min(1f, cg), Math.min(1f, cb), ca, d[o + 3], d[o + 4], overlay, light, tx, ty, tz);
        }
    }
}
