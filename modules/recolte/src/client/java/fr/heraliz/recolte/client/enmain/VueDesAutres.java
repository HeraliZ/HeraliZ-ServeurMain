package fr.heraliz.recolte.client.enmain;

import fr.heraliz.recolte.objets.OutilRecolte;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;

/**
 * Outil de récolte vu des autres (et de soi, vue de dos ; A_FAIRE §7) : pose de bras à deux mains
 * ({@code HumanoidModel.ArmPose}, comme {@code TenueExplosifs.ARM_POSE} de CRac), qui suit le geste du coup
 * ({@link Geste}). Le bras de la main principale prend les angles du geste ; l'outil est fixé dans sa main
 * ({@link RenduOutilObjet}, prise à 6 cm du bas du manche) ; l'autre bras vise la prise du haut (34 cm). Le geste de
 * Minecraft (coup d'un seul bras) est retiré pendant qu'on tient l'outil, et ce que Minecraft ajoute après la pose
 * (accroupi, respiration) est compensé : la main du haut reste sur le manche.
 * <p>Les angles (radians, comme {@code ModelPart}, repère du modèle : y vers le bas, avant = -z) sont ceux d'un
 * droitier ; un gaucher est en miroir. Ils viennent de petits essais faits hors du jeu (les deux mains restent sur le
 * manche au repos, à la frappe et à l'impact ; à l'élan, la main du haut s'en écarte de 2 à 3 px) : à régler en jeu.</p>
 */
public final class VueDesAutres {
    /** Pose à deux bras, donnée pour la main principale ({@link ExtensionsOutil#getArmPose}). */
    public static final HumanoidModel.ArmPose POSE = HumanoidModel.ArmPose.create("HERALIZ_RECOLTE_OUTIL", true, VueDesAutres::bras);

    /** Bras principal (x, y, z) : au repos, élan, frappe, impact. Hache (et hache-pioche) : élan au-dessus de l'épaule droite. */
    private static final float[][] HACHE = {{-0.70F, -0.40F, 0.0F}, {-2.30F, -0.50F, -0.20F}, {-1.75F, -0.45F, 0.0F}, {-0.90F, -0.40F, 0.0F}};
    /** Pioche (et hache-pioche côté pic, vu de soi) : élan au-dessus de la tête, frappe plus basse. */
    private static final float[][] PIOCHE = {{-0.70F, -0.40F, 0.0F}, {-2.70F, -0.20F, 0.05F}, {-1.80F, -0.35F, 0.0F}, {-0.75F, -0.35F, 0.0F}};
    /** Le bras remonte un peu juste après l'impact (radians). */
    private static final float RECUL = 0.08F;
    private static final float PI = (float) Math.PI;
    /** Prise du haut dans le repère du bras principal (px), l'outil étant fixé dans sa main ({@link TenueOutil}). */
    private static final float HAUT_X, HAUT_Y, HAUT_Z;

    static {
        float[] u = TenueOutil.MANCHE, f = TenueOutil.TRANCHANT, p = TenueOutil.PRISE_MAIN;
        Matrix3f r = Repere.outil(u[0], u[1], u[2], f[0], f[1], f[2]);
        float d = (TenueOutil.PRISE_HAUT - TenueOutil.PRISE_BAS) * TenueOutil.PX;
        HAUT_X = p[0] + r.m10 * d;
        HAUT_Y = p[1] + r.m11 * d;
        HAUT_Z = p[2] + r.m12 * d;
    }

    private static final Geste GESTE = new Geste();

    private VueDesAutres() {
    }

    /** Pose des deux bras (appelée par HumanoidModel.setupAnim, pour la main principale). */
    private static void bras(HumanoidModel<?> m, LivingEntity e, HumanoidArm principal) {
        if (PremierePersonne.dessinEnCours) {
            // mains de la première personne : aucune rotation laissée sur les bras (PremierePersonne les place)
            m.rightArm.yRot = 0.0F;
            m.rightArm.zRot = 0.0F;
            m.leftArm.yRot = 0.0F;
            m.leftArm.zRot = 0.0F;
            return;
        }
        // notre geste remplace le coup d'un bras de Minecraft (setupAttackAnimation ne fait rien à 0)
        m.attackTime = 0.0F;
        float partiel = Minecraft.getInstance().getFrameTime();
        boolean pioche = e.getMainHandItem().getItem() instanceof OutilRecolte o
                && (o.legendaire() ? CoupClient.picVu(e) : o.casseLesGisements());
        float[][] cles = pioche ? PIOCHE : HACHE;
        Geste g = GESTE.calculer(CoupClient.ageVu(e, partiel));
        float[] a = cles[g.a], b = cles[g.b];
        float x = a[0] + (b[0] - a[0]) * g.f - RECUL * g.recul;
        float y = a[1] + (b[1] - a[1]) * g.f;
        float z = a[2] + (b[2] - a[2]) * g.f;
        // droitier : prise du haut dans le modèle (px), depuis le repère de la main (pivot décalé pour les bras minces,
        // comme PlayerModel.translateToHand)
        boolean mince = e instanceof AbstractClientPlayer p && "slim".equals(p.getModelName());
        float sx = Mth.sin(x), cx = Mth.cos(x), sy = Mth.sin(y), cy = Mth.cos(y), sz = Mth.sin(z), cz = Mth.cos(z);
        // Rx
        float v1x = HAUT_X, v1y = cx * HAUT_Y - sx * HAUT_Z, v1z = sx * HAUT_Y + cx * HAUT_Z;
        // Ry
        float v2x = cy * v1x + sy * v1z, v2y = v1y, v2z = -sy * v1x + cy * v1z;
        // Rz
        float gx = cz * v2x - sz * v2y + (mince ? -4.5F : -5.0F), gy = sz * v2x + cz * v2y + 2.0F, gz = v2z;
        // l'autre bras (pivot 5, 2, 0) vise la prise du haut avec le milieu de sa main
        float mx = mince ? 0.5F : 1.0F, my = 9.0F, ml = (float) Math.sqrt(mx * mx + my * my);
        float dx = gx - 5.0F, dy = gy - 2.0F, dz = gz;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float xl = x, yl = -y;
        if (dl > 1.0E-3F) {
            dx /= dl;
            dy /= dl;
            dz /= dl;
            // Rx(xl) amène la main à la bonne hauteur, Ry(yl) la tourne vers la prise
            xl = -(float) Math.acos(Mth.clamp(dy * ml / my, -1.0F, 1.0F));
            float vx = mx / ml, vz = my / ml * Mth.sin(xl);
            yl = (float) (Math.atan2(vz, vx) - Math.atan2(dz, dx));
            if (yl > PI) {
                yl -= 2.0F * PI;
            } else if (yl < -PI) {
                yl += 2.0F * PI;
            }
        }
        // gaucher : tout en miroir (le bras gauche tient le bas du manche)
        boolean droitier = principal == HumanoidArm.RIGHT;
        float s = droitier ? 1.0F : -1.0F;
        ModelPart bas = droitier ? m.rightArm : m.leftArm, haut = droitier ? m.leftArm : m.rightArm;
        bas.xRot = x;
        bas.yRot = y * s;
        bas.zRot = z * s;
        haut.xRot = xl;
        haut.yRot = yl * s;
        haut.zRot = 0.0F;
        // ce que setupAnim ajoute après la pose : bras levés de 0,4 accroupi, respiration (AnimationUtils.bobModelPart)
        float accroupi = m.crouching ? 0.4F : 0.0F;
        float age = e.tickCount + partiel;
        float bz = Mth.cos(age * 0.09F) * 0.05F + 0.05F, bx = Mth.sin(age * 0.067F) * 0.05F;
        m.rightArm.xRot -= accroupi + bx;
        m.rightArm.zRot -= bz;
        m.leftArm.xRot -= accroupi - bx;
        m.leftArm.zRot += bz;
    }
}
