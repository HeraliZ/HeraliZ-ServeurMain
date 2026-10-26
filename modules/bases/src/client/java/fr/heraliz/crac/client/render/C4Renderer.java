package fr.heraliz.crac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.crac.entity.C4Entity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Timed explosive charge: taped brick of plastic explosive with a timer unit. Sticks flush on the face it hit; the
 * display counts down the fuse and the LED blinks faster and faster (emissive via c4_glowmask.png).
 */
public class C4Renderer extends GeoEntityRenderer<C4Entity> {
    private int tens;
    private int ones;
    private boolean ledOn;

    public C4Renderer(Context ctx) {
        super(ctx, new C4Model());
        this.shadowRadius = 0.15F;
        this.addRenderLayer(new C4GlowLayer<>(this));
    }

    @Override
    public void render(C4Entity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        boolean armed = entity.isStuck();
        double fuse = C4Display.fuseSeconds(entity, C4Display.c4FuseSeconds());
        double elapsed = entity.fuseElapsedSeconds(partialTick);
        int seconds = (int)Math.ceil(armed ? Math.max(0.0, fuse - elapsed) : fuse);
        this.tens = C4Display.tensOf(seconds);
        this.ones = C4Display.onesOf(seconds);
        this.ledOn = armed && C4Display.ledOn(elapsed, fuse);
        this.shadowRadius = armed ? 0.0F : 0.15F;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void applyRotations(C4Entity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        C4Display.orient(entity, entity.isStuck(), entity.getStuckFace(), poseStack, ageInTicks, partialTick, 2.2F);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, C4Entity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        C4Display.applyTimerBone(bone, this.tens, this.ones, this.ledOn);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
            red, green, blue, alpha);
    }
}
