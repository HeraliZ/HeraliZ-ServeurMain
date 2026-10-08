package fr.heralisz.camo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import fr.heralisz.camo.client.CamoFx;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Effets 3D des camos (CamoFx) : copie des sommets de l'arme pendant son rendu, puis dessin des effets juste après. */
@Mixin(value = BedrockModel.class, remap = false)
public abstract class BedrockModelFxMixin {
   private static final String RENDER =
      "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF)V";

   @Inject(method = RENDER, at = @At("HEAD"))
   private void heralisz$fxContext(PoseStack pose, ItemDisplayContext ctx, RenderType type, int light, int overlay, float r, float g, float b, float a,
                                   CallbackInfo ci) {
      CamoFx.context(ctx, light);
   }

   @Redirect(method = RENDER, at = @At(value = "INVOKE", remap = true,
      target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
   private VertexConsumer heralisz$fxBuffer(MultiBufferSource.BufferSource source, RenderType type) {
      return CamoFx.begin(source.getBuffer(type), type);
   }

   @Inject(method = RENDER, at = @At(value = "INVOKE",
      target = "Lcom/tacz/guns/compat/oculus/OculusCompat;endBatch(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z"))
   private void heralisz$fxFreeze(CallbackInfo ci) {
      CamoFx.freeze();
   }

   @Inject(method = RENDER, at = @At("TAIL"))
   private void heralisz$fxDraw(CallbackInfo ci) {
      CamoFx.end();
   }
}
