package fr.heralisz.camo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import fr.heralisz.camo.client.IconesPack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventaire : l'image « slot » du gunpack, lissée quand elle est réduite (IconesPack ; images des armes à feu rendues
 * hors du jeu en 128 px par outils/icones_armes.py). Rien n'est photographié en jeu (photos 3D coupées, couteaux
 * compris). Nom de développement et nom du jeu (TaCZ est livré avec les noms du jeu) ; require = 0 : si TaCZ change,
 * TaCZ dessine son image comme avant, rien ne casse.
 */
@Mixin(value = GunItemRendererWrapper.class, remap = false)
public abstract class GunItemIconeMixin {
   @Inject(method = {
      "renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
      "m_108829_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"},
      at = @At("HEAD"), cancellable = true, require = 0)
   private void heralisz$iconePack(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light,
                                   int overlay, CallbackInfo ci) {
      if (ctx == ItemDisplayContext.GUI && IconesPack.dessiner(stack, pose, buffers, light, overlay)) {
         ci.cancel();
      }
   }
}
