package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.entite.MateriauEntite;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tas de materiaux de recuperation ({@link MateriauEntite}, modele {@code materiau}) : pose au sol, tourne lentement,
 * eclat dore qui balaie les pieces, halo discret ; un peu plus gros avec la quantite ; clignote avant de disparaitre.
 */
public class MateriauRendu extends EntityRenderer<MateriauEntite> {
    private static final ResourceLocation TEXTURE = HeraliszMatchs.id("textures/entity/zombies/v2/easter.png");
    private static final RenderType HALO = ZbTypesRendu.lueur(HeraliszMatchs.id("textures/entity/zombies/halo.png"));
    private static final float[] PLEIN = {0, 0, 1, 1};

    public MateriauRendu(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(MateriauEntite e, float lacet, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere) {
        ModeleZb m = ModelesZb.get("materiau");
        if (m == null) return;
        float age = e.tickCount + partiel;
        int reste = e.reste();
        if (reste > 0) {
            float periode = Mth.clamp(reste * 4F, 3, 20);
            if ((age % periode) < periode * 0.35F) return;
        }
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        float s = 1.1F + Math.min(0.5F, (e.quantite() - 1) * 0.08F);
        ps.pushPose();
        ps.translate(0, 0.02F + 0.03F * Mth.sin(age * 0.08F), 0);
        ps.mulPose(Axis.YP.rotationDegrees(e.getYRot() + age * 1.2F));
        ps.scale(s, s, s);
        VertexConsumer vb = buf.getBuffer(tx.base());
        for (ModeleZb.Groupe g : m.groupes()) ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        float eclat = 0.35F + 0.65F * Math.max(0, Mth.sin(age * 0.15F));
        for (ModeleZb.Groupe g : m.groupes()) {
            ModeleZb.dessiner(g, ModeleZb.LUEUR, ps, buf.getBuffer(tx.lueur()), 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(eclat, 0xFFFFFF));
        }
        ps.popPose();
        ps.pushPose();
        ps.translate(0, 0.03F, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-90));
        Geo.plan(ps, buf.getBuffer(HALO), -0.45F, -0.45F, 0.45F, 0.45F, PLEIN, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(0.22F, 0xFFD27A));
        ps.popPose();
        super.render(e, lacet, partiel, ps, buf, lumiere);
    }

    @Override
    public ResourceLocation getTextureLocation(MateriauEntite e) {
        return TEXTURE;
    }
}
