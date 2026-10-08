package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.AtelierBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocLarge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Etabli de fabrication (modele {@code atelier}) : lampe d'atelier toujours allumee (halo chaud sur le plateau), plan
 * bleu legerement lumineux ; a chaque fabrication la lampe vacille et le plan s'illumine 1 s. Repere : origine au centre
 * bas de la case maitresse, +z vers la salle.
 */
public class AtelierRendu implements BlockEntityRenderer<AtelierBE> {
    private static final RenderType HALO = ZbTypesRendu.lueur(HeraliszMatchs.id("textures/entity/zombies/halo.png"));
    private static final float[] PLEIN = {0, 0, 1, 1};
    private static ModelesZb.Tex tex;

    public AtelierRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(AtelierBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        ModeleZb m = ModelesZb.get("atelier");
        if (m == null) return;
        if (tex == null) tex = ModelesZb.tex(m.texture);
        Direction f = be.getBlockState().hasProperty(BlocLarge.FACING) ? be.getBlockState().getValue(BlocLarge.FACING) : Direction.NORTH;
        float age = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 24000L) + partiel;
        float t = be.ecoule(partiel);
        float fab = t < 22 ? 1 - t / 22F : 0;
        float lampe = 0.92F + 0.08F * Mth.sin(age * 0.21F) * Mth.sin(age * 0.07F);
        if (fab > 0) lampe = (Mth.sin(t * 2.3F) > -0.2F ? 1F : 0.3F);
        float plan = 0.6F + 0.4F * fab;
        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        VertexConsumer vb = buf.getBuffer(tex.base());
        for (ModeleZb.Groupe g : m.groupes()) ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, overlay, 0xFFFFFFFF);
        VertexConsumer vl = buf.getBuffer(tex.lueur());
        ModeleZb.dessiner(m.groupe("lampe"), ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(lampe, 0xFFFFFF));
        ModeleZb.dessiner(m.groupe("plan"), ModeleZb.LUEUR, ps, vl, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(plan, 0xFFFFFF));
        // flaque de lumiere chaude de la lampe sur le plateau
        ps.pushPose();
        ps.translate(0.18F, 15.1F / 16F, -0.05F);
        ps.mulPose(Axis.XP.rotationDegrees(-90));
        Geo.plan(ps, buf.getBuffer(HALO), -0.55F, -0.4F, 0.55F, 0.4F, PLEIN, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(0.35F * lampe, 0xFFD98A));
        ps.popPose();
        ps.popPose();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
