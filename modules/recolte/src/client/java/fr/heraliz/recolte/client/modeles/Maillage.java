package fr.heraliz.recolte.client.modeles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Envoi des quadrilatères d'un modèle cuit ({@link ModeleCuit} : x y z u v nx ny nz par sommet) vers le tampon de
 * Minecraft, comme {@code fr.heraliz.casino.client.render.mesh.MeshRender} : la position est transformée ici et chaque
 * sommet part en un seul appel (chemin rapide du tampon des entités). Aucune allocation.
 * <p>Les matrices reçues ne font que tourner, déplacer et (pour la position seulement) mettre à l'échelle de façon
 * uniforme : la matrice des normales reste une rotation, les normales restent unitaires.</p>
 */
public final class Maillage {
    private Maillage() {
    }

    /**
     * @param q       quadrilatères ({@link ModeleCuit#PAR_SOMMET} flottants par sommet)
     * @param m       position : repère du modèle (mètres) vers celui du tampon
     * @param n       normales (rotation seule)
     * @param a       opacité (1 : opaque ; rendu translucide seulement en dessous)
     * @param lumiere lumière de l'endroit (paquet de Minecraft)
     * @param voile   superposition (rouge des coups : {@code OverlayTexture.NO_OVERLAY} ici)
     */
    public static void dessiner(float[] q, Matrix4f m, Matrix3f n, VertexConsumer vc, float a, int lumiere, int voile) {
        if (q.length == 0) {
            return;
        }
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02(), n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();
        float alpha = Math.max(0.0F, Math.min(1.0F, a));
        for (int o = 0; o + ModeleCuit.PAR_SOMMET <= q.length; o += ModeleCuit.PAR_SOMMET) {
            float x = q[o], y = q[o + 1], z = q[o + 2];
            float nx = q[o + 5], ny = q[o + 6], nz = q[o + 7];
            vc.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
                    1.0F, 1.0F, 1.0F, alpha, q[o + 3], q[o + 4], voile, lumiere,
                    n00 * nx + n10 * ny + n20 * nz, n01 * nx + n11 * ny + n21 * nz, n02 * nx + n12 * ny + n22 * nz);
        }
    }
}
