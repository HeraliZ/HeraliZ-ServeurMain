package fr.heraliz.crac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.crac.item.C4Item;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Held / inventory C4: same model as the entity, display showing the configured fuse, LED off. */
public class C4ItemRenderer extends GeoItemRenderer<C4Item> {
    public C4ItemRenderer() {
        super(new C4ItemModel());
        this.addRenderLayer(new C4GlowLayer<>(this));
    }

    @Override
    public void renderRecursively(PoseStack poseStack, C4Item animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        int seconds = (int)Math.ceil(C4Display.c4FuseSeconds());
        C4Display.applyTimerBone(bone, C4Display.tensOf(seconds), C4Display.onesOf(seconds), false);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
            red, green, blue, alpha);
    }
}
