package fr.heralisz.clans.territory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.territory.supply.CaisseParachuteEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu de la caisse sous parachute : maillage caisse_parachute (origine centre-bas de la caisse, toile qui ondule
 * par le flottement du JSON) avec un balancement pendulaire lent.
 */
@OnlyIn(Dist.CLIENT)
public class CaisseParachuteRenderer extends EntityRenderer<CaisseParachuteEntity> {
    private static final ResourceLocation TEXTURE = HeraliszClans.id("textures/entity/caisse.png");

    public CaisseParachuteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(CaisseParachuteEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float t = e.tickCount + partialTick;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
        // balancement autour du point d'attache du parachute (y = 4), pas autour de la caisse
        ps.translate(0, 4.0, 0);
        ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.07f) * 5f));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.cos(t * 0.053f) * 4f));
        ps.translate(0, -4.0, 0);
        Maillage.get("caisse_parachute").dessiner(ps, buf, packedLight, t, 0);
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buf, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CaisseParachuteEntity e) {
        return TEXTURE;
    }
}
