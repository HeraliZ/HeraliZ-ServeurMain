package fr.heraliz.crac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.crac.item.ExplosiveChargeItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Held / inventory Explosive Charge: full-length unlit fuse. */
public class ExplosiveChargeItemRenderer extends GeoItemRenderer<ExplosiveChargeItem> {
    public ExplosiveChargeItemRenderer() {
        super(new ExplosiveChargeItemModel());
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ExplosiveChargeItem animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, float red, float green, float blue, float alpha) {
        ExplosiveChargeFuse.applyBone(bone, false, 1.0F);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
            red, green, blue, alpha);
    }
}
