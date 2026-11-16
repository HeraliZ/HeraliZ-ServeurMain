package fr.heraliz.recolte.client.enmain;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.recolte.client.modeles.Maillage;
import fr.heraliz.recolte.client.modeles.ModeleCuit;
import fr.heraliz.recolte.client.modeles.ModelesRecolte;
import fr.heraliz.recolte.objets.OutilRecolte;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Outil de récolte en main, à la première personne (demande du propriétaire, A_FAIRE §7) : les DEUX mains du
 * personnage, avec son skin ({@code PlayerRenderer.renderRightHand / renderLeftHand}, comme la C4 de CRac,
 * {@code TenueExplosifs}), tiennent le manche du modèle en cubes de l'outil : main droite à 6 cm du bas, main gauche à
 * 34 cm ; chaque bras part de son épaule vers sa prise, le dos de la main tourné vers l'œil. Le geste du coup
 * ({@link Geste}) mêle les poses des images validées ({@link PosesPremierePersonne}). Joueur gaucher : tout en miroir
 * (main gauche en bas). Rien n'est dessiné pour la main gauche de Minecraft (elle est sur le manche).
 * <p>Pendant le dessin des bras, la pose de bras de l'outil ({@link VueDesAutres}) ne laisse aucune rotation sur les bras
 * ({@link #dessinEnCours}) : seul reste le balancement au repos de Minecraft, annulé ici autour de l'épaule.</p>
 */
public final class PremierePersonne {
    /** Vrai pendant que les bras sont dessinés ici (lu par la pose de bras, {@link VueDesAutres}). */
    static boolean dessinEnCours;

    /** Balancement du bras au repos laissé par Minecraft en dessinant la main (radians, AnimationUtils.bobModelPart). */
    private static final float BALANCEMENT = 0.1F;
    /** La prise dans le bras : 7 cm avant le bout de la main (px du modèle, comme les images), au milieu du bras. */
    private static final float PRISE_Y = 10.0F - 0.07F * 16.0F;
    /** Recul de l'outil juste après l'impact (m, repère de la caméra). */
    private static final float RECUL_Y = 0.012F, RECUL_Z = 0.035F;
    /** L'outil qui sort quand on le prend en main : de combien il monte (m). */
    private static final float SORTIE = 0.55F;

    private static final Geste GESTE = new Geste();
    private static final Quaternionf Q = new Quaternionf();
    private static final Matrix3f R = new Matrix3f(), N = new Matrix3f();
    private static final Matrix4f M = new Matrix4f(), A = new Matrix4f();
    /** Base, prises, épaules (repère de la caméra), calculées à chaque image sans allocation. */
    private static float bx, by, bz, g1x, g1y, g1z, g2x, g2y, g2z;

    private PremierePersonne() {
    }

    static void mains(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        if (joueur == null) {
            return;
        }
        ItemStack s = joueur.getMainHandItem();
        if (!(s.getItem() instanceof OutilRecolte outil)) {
            return;
        }
        // les deux mains sont sur le manche : rien de Minecraft pour l'une ni pour l'autre
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ModeleCuit modele = ModelesRecolte.outil(outil.id());
        float partiel = e.getPartialTick();
        boolean gaucher = joueur.getMainArm() == HumanoidArm.LEFT;
        float signe = gaucher ? -1.0F : 1.0F;
        poser(outil, partiel, gaucher);
        PoseStack pose = e.getPoseStack();
        MultiBufferSource tampons = e.getMultiBufferSource();
        int lumiere = e.getPackedLight();
        if (modele != null) {
            M.set(pose.last().pose()).translate(bx, by, bz).mul(A.identity().set3x3(R));
            N.set(pose.last().normal()).mul(R);
            Maillage.dessiner(modele.tout, M, N, tampons.getBuffer(modele.decoupe()), 1.0F, lumiere, OverlayTexture.NO_OVERLAY);
        }
        if (joueur.isInvisible()) {
            return;
        }
        float[] ed = PosesPremierePersonne.EPAULE_DROITE, eg = PosesPremierePersonne.EPAULE_GAUCHE;
        // la main principale tient le bas du manche, l'autre plus haut
        HumanoidArm principale = gaucher ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        bras(mc, joueur, pose, tampons, lumiere, principale, g1x, g1y, g1z, ed[0] * signe, ed[1], ed[2]);
        bras(mc, joueur, pose, tampons, lumiere, principale.getOpposite(), g2x, g2y, g2z, eg[0] * signe, eg[1], eg[2]);
    }

    /**
     * Pose de l'outil à cette image : base (bx, by, bz), rotation R et prises g1 (bas) et g2 (haut), repère de la
     * caméra ; en miroir pour un gaucher.
     */
    private static void poser(OutilRecolte outil, float partiel, boolean gaucher) {
        PosesPremierePersonne.Pose[] poses = poses(outil);
        Geste g = GESTE.calculer(CoupClient.ageCoup(partiel));
        PosesPremierePersonne.Pose a = poses[g.a], b = poses[g.b];
        float f = g.f;
        bx = a.x + (b.x - a.x) * f;
        by = a.y + (b.y - a.y) * f + RECUL_Y * g.recul - SORTIE * CoupClient.sortie(partiel);
        bz = a.z + (b.z - a.z) * f + RECUL_Z * g.recul;
        Q.set(a.q);
        if (g.b != g.a && f > 0.0F) {
            Q.slerp(b.q, f);
        }
        R.set(Q);
        if (gaucher) {
            // miroir de la scène (x opposé) sans retourner l'outil : R' = Sx · R · Sz
            bx = -bx;
            R.m00 = -R.m00;
            R.m10 = -R.m10;
            R.m21 = -R.m21;
            R.m22 = -R.m22;
        }
        // sens du manche : +y de l'outil
        float ux = R.m10, uy = R.m11, uz = R.m12;
        float p1 = TenueOutil.PRISE_BAS, p2 = TenueOutil.PRISE_HAUT;
        g1x = bx + ux * p1;
        g1y = by + uy * p1;
        g1z = bz + uz * p1;
        g2x = bx + ux * p2;
        g2y = by + uy * p2;
        g2z = bz + uz * p2;
    }

    /** Poses du coup en cours : hache, pioche, hache-pioche côté hache ou côté pic (choisi au début du coup). */
    private static PosesPremierePersonne.Pose[] poses(OutilRecolte outil) {
        if (outil.legendaire()) {
            return CoupClient.coupPic() ? PosesPremierePersonne.COUP_LEGENDAIRE_PIERRE : PosesPremierePersonne.COUP_LEGENDAIRE_BOIS;
        }
        return outil.casseLesGisements() ? PosesPremierePersonne.COUP_PIOCHE : PosesPremierePersonne.COUP_HACHE;
    }

    /**
     * Un bras du joueur, de l'épaule (ex, ey, ez) vers la prise (gx, gy, gz) : son axe (de l'épaule vers la main) sur la
     * ligne épaule-prise, la prise 7 cm avant le bout de la main, le dos de la main tourné vers l'œil. Minecraft le
     * dessine ensuite depuis son épaule (pivot du modèle) avec le balancement au repos, annulé ici.
     */
    private static void bras(Minecraft mc, AbstractClientPlayer joueur, PoseStack pose, MultiBufferSource tampons, int lumiere,
                             HumanoidArm cote, float gx, float gy, float gz, float ex, float ey, float ez) {
        boolean droit = cote == HumanoidArm.RIGHT;
        float f = droit ? 1.0F : -1.0F;
        // axe du bras (+y du bras : de l'épaule vers la main)
        float dx = gx - ex, dy = gy - ey, dz = gz - ez;
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-4F) {
            return;
        }
        dx /= l;
        dy /= l;
        dz /= l;
        // dos de la main vers l'œil : face extérieure du bras (-x du bras droit, +x du bras gauche) tournée vers la caméra
        float pd = gx * dx + gy * dy + gz * dz;
        float xx = (gx - pd * dx) * f, xy = (gy - pd * dy) * f, xz = (gz - pd * dz) * f;
        float lx = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        if (lx < 1.0E-4F) {
            // bras dans l'axe de l'œil : le haut de l'image sert de repère
            xx = -dz * f;
            xy = 0.0F;
            xz = dx * f;
            lx = (float) Math.sqrt(xx * xx + xz * xz);
            if (lx < 1.0E-4F) {
                xx = f;
                lx = 1.0F;
            }
        }
        xx /= lx;
        xy /= lx;
        xz /= lx;
        // z = x × y
        float zx = xy * dz - xz * dy, zy = xz * dx - xx * dz, zz = xx * dy - xy * dx;
        boolean mince = "slim".equals(joueur.getModelName());
        float milieu = (mince ? 0.5F : 1.0F) * -f;
        A.set(xx, xy, xz, 0.0F, dx, dy, dz, 0.0F, zx, zy, zz, 0.0F, gx, gy, gz, 1.0F)
                .translate(-milieu / 16.0F, -PRISE_Y / 16.0F, 0.0F)
                .rotateZ(-BALANCEMENT * f)
                .translate(5.0F * f / 16.0F, -2.0F / 16.0F, 0.0F);
        pose.pushPose();
        pose.last().pose().mul(A);
        pose.last().normal().mul(N.set(A));
        dessinEnCours = true;
        try {
            // Forge lance l'évènement du bras (RenderArmEvent) dans renderRightHand / renderLeftHand : manches de
            // l'Armurerie et skin du créateur de personnage comme pour les mains de Minecraft
            if (mc.getEntityRenderDispatcher().<AbstractClientPlayer>getRenderer(joueur) instanceof PlayerRenderer rendu) {
                if (droit) {
                    rendu.renderRightHand(pose, tampons, lumiere, joueur);
                } else {
                    rendu.renderLeftHand(pose, tampons, lumiere, joueur);
                }
            }
        } finally {
            dessinEnCours = false;
            pose.popPose();
        }
    }
}
