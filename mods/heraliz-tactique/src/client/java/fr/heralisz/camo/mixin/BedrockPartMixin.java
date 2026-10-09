package fr.heralisz.camo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import fr.heralisz.camo.decor.client.DecorRender;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Personnalisation des armes : relève la matrice et les cubes de chaque pièce dessinée (stickers, StatTrack). */
@Mixin(value = BedrockPart.class, remap = false)
public abstract class BedrockPartMixin {
   @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
      at = @At(value = "INVOKE",
         target = "Lcom/tacz/guns/client/model/bedrock/BedrockPart;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
   private void heralisz$decorPart(PoseStack poseStack, ItemDisplayContext ctx, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a,
                                   CallbackInfo ci) {
      DecorRender.onPart((BedrockPart) (Object) this, poseStack);
   }

   @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
      at = @At("RETURN"))
   private void heralisz$decorPartEnd(PoseStack poseStack, ItemDisplayContext ctx, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a,
                                      CallbackInfo ci) {
      DecorRender.onPartEnd((BedrockPart) (Object) this);
   }
}
