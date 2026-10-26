package fr.heraliz.corps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;

/**
 * Modele de joueur allonge sur le dos, « cuit » une fois pour toutes : le modele vanilla est pose (bras ecartes, tete
 * tournee) puis rendu une seule fois dans un enregistreur ; on garde les sommets (position dans le repere du corps, UV,
 * normale). A chaque image, le rendu ne fait que transformer ces ~300 sommets par la matrice courante (vecteurs
 * reutilises) : aucune allocation, aucun calcul d'animation (le rendu vanilla d'un modele alloue deux vecteurs par sommet).
 * <p>Repere du corps (apres la rotation de lacet) : centre du corps a l'origine, pieds vers -Z (la ou regardait le
 * joueur), tete vers +Z, visage vers le haut.
 */
final class Maillage {
    /** Hauteur du dos au-dessus du sol. */
    static final float LEVE = 0.13F;
    /** Demi-longueur du corps allonge (pieds -> haut de la tete = 1,875). */
    static final float DEMI = 0.9375F;

    /** x, y, z, u, v, nx, ny, nz par sommet. */
    private final float[] s;
    private final int nb;

    private Maillage(float[] s, int nb) {
        this.s = s;
        this.nb = nb;
    }

    static Maillage cuire(PlayerModel<LivingEntity> m) {
        poser(m);
        PoseStack ps = new PoseStack();
        ps.translate(0.0F, LEVE, -DEMI);
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        ps.scale(-0.9375F, -0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        Enregistreur e = new Enregistreur();
        m.renderToBuffer(ps, e, 0xF000F0, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        return new Maillage(Arrays.copyOf(e.s, e.nb * 8), e.nb);
    }

    /** Pose d'un mort : bras ecartes, jambes un peu ouvertes, tete tournee sur le cote. */
    static void poser(PlayerModel<?> m) {
        m.young = false;
        m.crouching = false;
        m.riding = false;
        m.head.setRotation(-0.12F, 0.42F, 0.0F);
        m.body.setRotation(0.0F, 0.0F, 0.0F);
        m.rightArm.setRotation(-0.08F, 0.0F, 0.34F);
        m.leftArm.setRotation(0.05F, 0.0F, -0.20F);
        m.rightLeg.setRotation(0.02F, 0.06F, 0.10F);
        m.leftLeg.setRotation(-0.02F, -0.10F, -0.14F);
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /** Emet les sommets transformes par la pose courante (v4 et v3 reutilises par l'appelant). */
    void emettre(Matrix4f pose, Matrix3f normale, VertexConsumer vc, int lumiere, float r, float g, float b, float a, Vector4f v4, Vector3f v3) {
        float[] t = s;
        for (int i = 0, k = 0; i < nb; i++, k += 8) {
            pose.transform(t[k], t[k + 1], t[k + 2], 1.0F, v4);
            normale.transform(t[k + 5], t[k + 6], t[k + 7], v3);
            vc.vertex(v4.x, v4.y, v4.z, r, g, b, a, t[k + 3], t[k + 4], OverlayTexture.NO_OVERLAY, lumiere, v3.x, v3.y, v3.z);
        }
    }

    int sommets() {
        return nb;
    }

    /** Enregistre les sommets emis par le modele (appel groupe {@code vertex(...)} de ModelPart). */
    private static final class Enregistreur implements VertexConsumer {
        float[] s = new float[8 * 512];
        int nb;

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
            if ((nb + 1) * 8 > s.length) s = Arrays.copyOf(s, s.length * 2);
            int k = nb * 8;
            s[k] = x;
            s[k + 1] = y;
            s[k + 2] = z;
            s[k + 3] = u;
            s[k + 4] = v;
            s[k + 5] = nx;
            s[k + 6] = ny;
            s[k + 7] = nz;
            nb++;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
