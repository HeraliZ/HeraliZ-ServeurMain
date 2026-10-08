package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocLevier;
import fr.heralisz.matchs.zombies.contenu.bloc.LevierBE;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Levier du courant : plaque a rayures vissee au mur, grosse poignee rouge sur une tige, deux voyants. Coupe : levier
 * leve, voyant rouge qui clignote lentement. Retabli : le levier bascule vers le bas avec un rebond (12 ticks), le
 * voyant vert s'allume. Repere local : origine au centre bas de la case, +z vers la piece, mur en z = -0,5.
 */
public class LevierRendu implements BlockEntityRenderer<LevierBE> {
    private static final RenderType TYPE = RenderType.entityCutout(HeraliszMatchs.id("textures/entity/zombies/levier.png"));
    private static final RenderType LUEUR = ZbTypesRendu.lueur(HeraliszMatchs.id("textures/entity/zombies/levier_lueur.png"));
    private static final float[] PLAQUE = Geo.uv(0, 0, 32, 32, 64, 32), POIGNEE = Geo.uv(32, 0, 16, 16, 64, 32),
            TIGE = Geo.uv(48, 0, 16, 16, 64, 32), ROUGE = Geo.uv(32, 16, 16, 16, 64, 32), VERT = Geo.uv(48, 16, 16, 16, 64, 32);

    public LevierRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(LevierBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        Direction f = be.getBlockState().hasProperty(BlocLevier.FACING) ? be.getBlockState().getValue(BlocLevier.FACING) : Direction.NORTH;
        float age = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 24000L) + partiel;
        float t = be.ecoule(partiel);
        boolean actif = be.actif();
        // angle de la tige : 35 degres (leve) -> 145 degres (baisse), avec depassement
        float p = Mth.clamp(t / 12F, 0, 1);
        float e = 1 + 2.4F * (float) Math.pow(p - 1, 3) + 1.4F * (float) Math.pow(p - 1, 2);
        float angle = actif ? 35 + 110 * e : 145 - 110 * e;
        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        VertexConsumer vc = buf.getBuffer(TYPE);
        Geo.pave(ps, vc, -0.42F, 0.08F, -0.5F, 0.42F, 0.92F, -0.44F, PLAQUE, lumiere, overlay, 0xFFFFFFFF);
        // boitier du pivot
        Geo.pave(ps, vc, -0.12F, 0.38F, -0.44F, 0.12F, 0.62F, -0.36F, TIGE, lumiere, overlay, 0xFF8A8A8A);
        // tige + poignee
        ps.pushPose();
        ps.translate(0, 0.5F, -0.4F);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        Geo.pave(ps, vc, -0.035F, 0, -0.035F, 0.035F, 0.4F, 0.035F, TIGE, lumiere, overlay, 0xFFFFFFFF);
        Geo.pave(ps, vc, -0.11F, 0.36F, -0.06F, 0.11F, 0.5F, 0.06F, POIGNEE, lumiere, overlay, 0xFFFFFFFF);
        ps.popPose();
        // voyants
        float rouge = actif ? 0.08F : 0.6F + 0.4F * Math.max(0, Mth.sin(age * 0.15F));
        float vert = actif ? 0.9F + 0.1F * Mth.sin(age * 0.3F) : 0.05F;
        Geo.pave(ps, vc, -0.34F, 0.76F, -0.44F, -0.22F, 0.86F, -0.4F, ROUGE, lumiere, overlay, 0xFFFFFFFF);
        Geo.pave(ps, vc, 0.22F, 0.76F, -0.44F, 0.34F, 0.86F, -0.4F, VERT, lumiere, overlay, 0xFFFFFFFF);
        VertexConsumer vl = buf.getBuffer(LUEUR);
        Geo.face(ps, vl, 3, -0.4F, 0.7F, -0.4F, -0.16F, 0.92F, -0.398F, ROUGE, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(rouge, 0xFFFFFF));
        Geo.face(ps, vl, 3, 0.16F, 0.7F, -0.4F, 0.4F, 0.92F, -0.398F, VERT, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(vert, 0xFFFFFF));
        ps.popPose();
    }
}
