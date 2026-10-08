package fr.heralisz.utils.cine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Les bras du joueur vus par ses yeux pendant la cinématique, dessinés à la place des mains du jeu (même repère, son skin,
 * ses manches) : deux bras placés exactement comme le bras du joueur à la première personne dans le jeu, qui montent dans
 * le champ ou redescendent selon la pose : course (un bras monte quand l'autre descend, à chaque foulée), marche, mains
 * plaquées sur une vitre, mains levées qui tremblent.
 */
@OnlyIn(Dist.CLIENT)
final class BrasFP {
    enum Pose { AUCUNE, COURSE, MARCHE, VITRE, TREMBLE }

    static Pose pose = Pose.AUCUNE;
    /** Phase de la foulée (radians), ampleur (0..1), et la valeur du tick précédent pour l'interpolation. */
    static float phase, phaseO, ampleur = 1f;

    private BrasFP() {}

    static void tick() {
        phaseO = phase;
    }

    /** Dessine les deux bras (repère des mains du jeu). Base : la place exacte du bras du joueur à la première personne dans
     *  le jeu (ItemInHandRenderer.renderPlayerArm, sans objet), puis chaque bras monte dans le champ ou redescend selon la
     *  pose (u : 0 = baissé hors du champ, 1 = levé devant soi). */
    static void dessiner(PoseStack ps, MultiBufferSource src, int light, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (pose == Pose.AUCUNE || mc.player == null) {
            return;
        }
        if (!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer r)) {
            return;
        }
        float ph = Mth.lerp(partial, phaseO, phase);
        for (int s = 1; s >= -1; s -= 2) {                       // 1 : droit, -1 : gauche
            float sw = Mth.sin(ph + (s > 0 ? 0 : Mth.PI));
            float u, leve = 0f, interieur = 0f;
            switch (pose) {
                case COURSE -> {                                  // la foulée : un bras monte quand l'autre descend
                    u = (float) Math.pow(Math.max(0, sw), 0.8) * ampleur;
                    leve = 0.24f * u;
                    interieur = 0.16f * u;
                }
                case MARCHE -> {
                    u = (0.28f + 0.22f * sw) * ampleur;
                }
                case VITRE -> {                                   // mains plaquées sur la vitre devant soi
                    u = 1f;
                    leve = 0.32f + 0.01f * Mth.sin(ph * 0.7f + s);
                    interieur = 0.1f;
                }
                default -> {                                      // les mains levées devant soi, qui tremblent
                    u = 1f;
                    leve = 0.18f + 0.012f * Mth.sin(ph * 3.1f + s * 1.3f);
                    interieur = 0.16f + 0.01f * Mth.sin(ph * 2.7f + s);
                }
            }
            if (u <= 0.01f) {
                continue;
            }
            float f = s;
            ps.pushPose();
            ps.translate(f * (0.64f - interieur), -0.6f - 0.75f * (1f - u) + leve, -0.72f - 0.12f * u);
            ps.mulPose(Axis.YP.rotationDegrees(f * (45f - 12f * u)));
            ps.mulPose(Axis.XP.rotationDegrees(-22f * u));          // le poing remonte
            ps.translate(f * -1.0f, 3.6f, 3.5f);
            ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0f));
            ps.mulPose(Axis.XP.rotationDegrees(200.0f));
            ps.mulPose(Axis.YP.rotationDegrees(f * -135.0f));
            ps.translate(f * 5.6f, 0.0f, 0.0f);
            if (s > 0) {
                r.renderRightHand(ps, src, light, mc.player);
            } else {
                r.renderLeftHand(ps, src, light, mc.player);
            }
            ps.popPose();
        }
    }
}
