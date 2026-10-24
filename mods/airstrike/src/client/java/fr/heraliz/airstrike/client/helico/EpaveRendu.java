package fr.heraliz.airstrike.client.helico;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.airstrike.client.model.GeoModel;
import fr.heraliz.airstrike.client.model.ModModels;
import fr.heraliz.airstrike.helico.CaisseHelicoEntity;
import fr.heraliz.airstrike.helico.EpaveHelicoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Morceaux de l'épave et caisses de l'hélicoptère (geo/helico_epave.geo : parties piece_0 à piece_5 et caisse, chacune
 * posée au sol, centrée ; texture brûlée de outils/modele/helico.py).
 */
public class EpaveRendu<T extends Entity> extends EntityRenderer<T> {
    public static final ResourceLocation TEXTURE = ModModels.texture("helico_epave");

    public EpaveRendu(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return TEXTURE;
    }

    @Override
    public void render(T e, float lacetE, float pt, PoseStack ps, MultiBufferSource buf, int lumiere) {
        String partie = e instanceof EpaveHelicoEntity ep ? "piece_" + ep.piece() : e instanceof CaisseHelicoEntity ? "caisse" : null;
        if (partie == null) return;
        GeoModel m = ModModels.epave();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.scale(-1f, -1f, 1f);
        m.renderPart(partie, ps, buf.getBuffer(RenderType.entityCutout(TEXTURE)), lumiere, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        ps.popPose();
        super.render(e, lacetE, pt, ps, buf, lumiere);
    }
}
