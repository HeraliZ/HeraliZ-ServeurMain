package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.FenetreBE;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Planches d'une fenetre barricadee : 6 planches de bois brut (6 textures), clouees de travers sur la face interieure
 * du mur et debordant sur l'encadrement. Planche arrachee : tiree vers l'exterieur a travers l'ouverture en tournoyant
 * puis tombe (12 ticks) ; planche reposee : vole depuis la piece jusqu'a sa place et claque avec un petit rebond
 * (8 ticks). Repere local : origine au centre bas de l'ouverture, +z vers la piece.
 */
public class FenetreRendu implements BlockEntityRenderer<FenetreBE> {
    private static final RenderType TYPE = RenderType.entityCutout(HeraliszMatchs.id("textures/entity/zombies/planches.png"));
    private static final float[][] UV = new float[FenetreBE.MAX][];

    static {
        for (int i = 0; i < FenetreBE.MAX; i++) UV[i] = Geo.uv(0, 16 * i + 2, 64, 12, 64, 96);
    }

    /** La planche est agrippee {@link #PRISE} ticks (le zombie plonge les bras) puis arrachee : 17 ticks en tout. */
    private static final float PRISE = 5F, DUREE_ARRACHEE = 17F;

    public FenetreRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(FenetreBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        Direction ext = be.exterieur();
        Direction in = ext.getOpposite();
        float L = be.largeur(), H = be.hauteur();
        ps.pushPose();
        if (ext.getAxis() == Direction.Axis.Z) ps.translate(L / 2F, 0, 0.5F);
        else ps.translate(0.5F, 0, L / 2F);
        ps.mulPose(Axis.YP.rotationDegrees(-in.toYRot()));
        VertexConsumer vc = buf.getBuffer(TYPE);
        long graine = be.getBlockPos().asLong() * 31L;
        float t = be.ecoule(partiel);
        int action = be.action(), emp = be.emplacement();
        for (int k = 0; k < FenetreBE.MAX; k++) {
            boolean anime = k == emp && (action == FenetreBE.ARRACHEE && t < DUREE_ARRACHEE || action == FenetreBE.REPAREE && t < 8);
            if (!anime && !be.presente(k)) continue;
            // pose de repos : hauteur repartie, angle alterne et tire de la position
            float alea = (float) (((graine >>> (k * 5)) & 31) / 31.0);
            float y = H * (0.12F + 0.152F * k) + (alea - 0.5F) * 0.06F;
            float angle = (k % 2 == 0 ? 1 : -1) * (5 + 10 * alea);
            float dx = (alea - 0.5F) * 0.18F;
            float z = 0.5F + 0.045F + k * 0.007F;
            ps.pushPose();
            ps.translate(dx, y, z);
            if (anime && action == FenetreBE.ARRACHEE) {
                if (t < PRISE) {
                    // agrippee par le zombie : la planche tremble et se decolle un peu (clous qui cedent)
                    float g = t / PRISE;
                    ps.translate(Mth.sin(t * 2.9F) * 0.02F * g, Mth.sin(t * 3.7F) * 0.015F, -0.06F * g);
                    ps.mulPose(Axis.ZP.rotationDegrees(angle + Mth.sin(t * 2.3F) * 3 * g));
                    ps.mulPose(Axis.XP.rotationDegrees(-8 * g));
                } else {
                    float p = Mth.clamp((t - PRISE) / (DUREE_ARRACHEE - PRISE), 0, 1);
                    // arrachee vers l'exterieur (a travers l'ouverture), puis chute en tournoyant
                    ps.translate(0, -1.1F * p * p + 0.25F * Mth.sin(p * Mth.PI), -0.06F - 1.7F * Mth.sqrt(p));
                    ps.mulPose(Axis.YP.rotationDegrees((alea - 0.5F) * 80 * p));
                    ps.mulPose(Axis.ZP.rotationDegrees(angle * (1 - p) + (k % 2 == 0 ? 70 : -70) * p));
                    ps.mulPose(Axis.XP.rotationDegrees(-8 - 35 * p));
                }
            } else if (anime) {
                float p = Mth.clamp(t / 8F, 0, 1);
                // depuis la piece, avec depassement (claque)
                float e = 1 + 2.2F * (float) Math.pow(p - 1, 3) + 1.2F * (float) Math.pow(p - 1, 2);
                float k1 = 1 - e;
                ps.translate(0.4F * k1, -0.7F * k1, 1.6F * k1);
                ps.mulPose(Axis.ZP.rotationDegrees(angle + 55 * k1));
                ps.mulPose(Axis.YP.rotationDegrees(35 * k1));
            } else {
                ps.mulPose(Axis.ZP.rotationDegrees(angle));
            }
            float demi = (L + 0.55F) / 2F;
            Geo.pave(ps, vc, -demi, -0.135F, 0, demi, 0.135F, 0.075F, UV[k], lumiere, overlay, 0xFFFFFFFF);
            ps.popPose();
        }
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(FenetreBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
