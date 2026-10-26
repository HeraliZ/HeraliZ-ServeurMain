package fr.heralisz.camo.mixin;

import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Infobulle des accessoires TaCZ (choix du propriétaire, 21/10) : seulement leurs caractéristiques (grossissement,
 * chargeur, effets…). Plus de nom du gunpack d'où vient l'accessoire, plus de « Appuyez sur [shift] pour voir les armes
 * d'appui » ni de liste des armes avec Maj. Hauteur et largeur suivent. Méthodes de l'interface de Minecraft : nom de
 * développement et nom SRG (require = 0 : sans effet si TaCZ change).
 */
@Mixin(value = ClientAttachmentItemTooltip.class, remap = false)
public abstract class ClientAttachmentTooltipMixin {
   private static final int LIGNE = 10;
   private static final int COULEUR = 0xFFAA00;

   @Shadow
   @Final
   private List<Component> components;

   @Inject(method = {"getHeight()I", "m_142103_()I"}, at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$hauteur(CallbackInfoReturnable<Integer> cir) {
      cir.setReturnValue(this.components.size() * LIGNE);
   }

   @Inject(method = {"getWidth(Lnet/minecraft/client/gui/Font;)I", "m_142069_(Lnet/minecraft/client/gui/Font;)I"},
      at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$largeur(Font font, CallbackInfoReturnable<Integer> cir) {
      int w = 0;
      for (Component c : this.components) {
         w = Math.max(w, font.width(c));
      }
      cir.setReturnValue(w);
   }

   @Inject(method = {
      "renderText(Lnet/minecraft/client/gui/Font;IILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
      "m_142440_(Lnet/minecraft/client/gui/Font;IILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"},
      at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$texte(Font font, int x, int y, Matrix4f matrice, MultiBufferSource.BufferSource tampon, CallbackInfo ci) {
      int ly = y;
      for (Component c : this.components) {
         font.drawInBatch(c, x, ly, COULEUR, false, matrice, tampon, Font.DisplayMode.NORMAL, 0, 0xF000F0);
         ly += LIGNE;
      }
      ci.cancel();
   }

   @Inject(method = {
      "renderImage(Lnet/minecraft/client/gui/Font;IILnet/minecraft/client/gui/GuiGraphics;)V",
      "m_183452_(Lnet/minecraft/client/gui/Font;IILnet/minecraft/client/gui/GuiGraphics;)V"},
      at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$image(Font font, int x, int y, GuiGraphics g, CallbackInfo ci) {
      ci.cancel();
   }
}
