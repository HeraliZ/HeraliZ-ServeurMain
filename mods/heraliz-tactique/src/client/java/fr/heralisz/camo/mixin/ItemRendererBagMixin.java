package fr.heralisz.camo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.camo.client.BagCamo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rendu d'un objet : un sac camouflé (porté sur le dos, rendu par le fork Backpacked via ItemRenderer) passe par le shader camo. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererBagMixin {
   @ModifyVariable(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
      at = @At("HEAD"), argsOnly = true)
   private MultiBufferSource heralisz$bagCamo(MultiBufferSource buffers, ItemStack stack, ItemDisplayContext ctx, boolean left, PoseStack pose,
                                              MultiBufferSource original, int light, int overlay, BakedModel model) {
      // le sac porté est rendu en contexte NONE (BackpackLayer) ; icônes et sac en main sont des images plates : rien à faire
      return ctx == ItemDisplayContext.NONE ? BagCamo.wrap(stack, buffers) : buffers;
   }

   /** Après le sac porté : effet 3D du skin X (ailes, chaînes…), dessiné par-dessus le sac qui vient d'être envoyé. */
   @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
      at = @At("RETURN"))
   private void heralisz$bagFx(ItemStack stack, ItemDisplayContext ctx, boolean left, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               BakedModel model, CallbackInfo ci) {
      if (ctx == ItemDisplayContext.NONE) {
         BagCamo.afterRender(stack, pose, light, model);
      }
   }
}
