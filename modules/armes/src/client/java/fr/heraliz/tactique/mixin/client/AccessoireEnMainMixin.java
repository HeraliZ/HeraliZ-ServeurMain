package fr.heraliz.tactique.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;
import fr.heraliz.tactique.client.accessoires.AccessoiresEnMain;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Accessoire TaCZ tenu en main (première personne) : un silencieux ou un bipied qui sortait de l'écran est tenu par son
 * milieu et réduit pour tenir à l'écran (AccessoiresEnMain, table écrite par modules/armes/outils/accessoires_en_main.py).
 * <p>
 * Cible : AttachmentItemRenderer.renderByItem de TaCZ 1.1.8 (dessin de tout accessoire tenu ou posé comme objet). Nom de
 * développement et nom du jeu (TaCZ est livré avec les noms du jeu), comme GunItemIconeMixin ; require = 0 : si TaCZ
 * change, il dessine comme avant, rien ne casse.
 */
@Mixin(value = AttachmentItemRenderer.class, remap = false)
public abstract class AccessoireEnMainMixin {
   @Inject(method = {
      "renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
      "m_108829_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"},
      at = @At("HEAD"), require = 0)
   private void heraliz$tenuParLeMilieu(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light,
                                        int overlay, CallbackInfo ci) {
      AccessoiresEnMain.placer(stack, ctx, pose);
   }
}
