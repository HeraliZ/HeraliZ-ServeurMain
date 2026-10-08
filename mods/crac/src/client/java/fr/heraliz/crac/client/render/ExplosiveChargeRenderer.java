package fr.heraliz.crac.client.render;

import java.util.Map;
import java.util.WeakHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.crac.entity.ExplosiveChargeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Satchel-style charge: a taped burlap sack of bean-can charges with a fuse. While armed the fuse burns down over the
 * configured fuse time, the ember glows (explosive_charge_glowmask.png) and throws sparks.
 */
public class ExplosiveChargeRenderer extends GeoEntityRenderer<ExplosiveChargeEntity> {
    private final Map<ExplosiveChargeEntity, Integer> lastSparkTick = new WeakHashMap<>();
    private boolean lit;
    private float left = 1.0F;

    public ExplosiveChargeRenderer(Context ctx) {
        super(ctx, new ExplosiveChargeModel());
        this.shadowRadius = 0.18F;
        this.addRenderLayer(new C4GlowLayer<>(this));
    }

    @Override
    public void render(ExplosiveChargeEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        this.lit = entity.isStuck();
        this.left = ExplosiveChargeFuse.left(entity.fuseElapsedSeconds(partialTick), C4Display.fuseSeconds(entity, C4Display.chargeFuseSeconds()));
        this.shadowRadius = this.lit ? 0.0F : 0.18F;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (this.lit) {
            Integer last = this.lastSparkTick.put(entity, entity.tickCount);
            if (last == null || last != entity.tickCount) {
                this.model.getBone("ember").ifPresent(b -> ExplosiveChargeFuse.spawnSparks(b, entity.level().random));
            }
        }
    }

    @Override
    protected void applyRotations(ExplosiveChargeEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        C4Display.orient(entity, entity.isStuck(), entity.getStuckFace(), poseStack, ageInTicks, partialTick, 2.2F);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ExplosiveChargeEntity animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                  int packedOverlay, float red, float green, float blue, float alpha) {
        ExplosiveChargeFuse.applyBone(bone, this.lit, this.left);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
            red, green, blue, alpha);
    }
}
