package fr.heraliz.crac.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.crac.client.kit.EcranCanevas;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lettres lissées pendant le dessin d'un écran agrandi d'un facteur non entier ({@link EcranCanevas#textesLisses}) :
 * Minecraft lit les polices au point près, et réduites ou agrandies d'un facteur comme 2,6 des colonnes de points
 * sautaient (bords des lettres « mangés », fiche {@code /base info}, 22/10). Le lissage n'est posé que sur les rendus de
 * texte, et Minecraft le retire lui-même au rendu de texte suivant.
 */
@Mixin(RenderStateShard.class)
public abstract class TexteLisseMixin {
    @Shadow
    @Final
    protected String name;

    @Inject(method = "setupRenderState", at = @At("TAIL"))
    private void crac$lisser(CallbackInfo ci) {
        if (!EcranCanevas.lisse || this.name == null || !this.name.startsWith("text")) {
            return;
        }
        int id = RenderSystem.getShaderTexture(0);
        if (id <= 0) {
            return;
        }
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(3553, 10241, 9729);
        GlStateManager._texParameter(3553, 10240, 9729);
    }
}
