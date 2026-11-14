package fr.heraliz.noyau.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.noyau.client.kit.EcranVerre;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Icône d'une case vide (silhouette du casque, du gilet, des gants…) atténuée dans nos écrans en verre
 * ({@link EcranVerre#ICONE_VIDE}) : Minecraft la dessine pleine, elle ressortait plus que les objets sur le verre sombre.
 * Ailleurs, rien ne change. Facultatif ({@code require = 0}) : s'il ne s'applique pas, l'icône reste pleine.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class CaseVideMixin {
    @Redirect(method = "renderSlot", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void heraliz$caseVide(GuiGraphics g, int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
        if (!((Object) this instanceof EcranVerre)) {
            g.blit(x, y, z, w, h, sprite);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, EcranVerre.ICONE_VIDE);
        g.blit(x, y, z, w, h, sprite);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
