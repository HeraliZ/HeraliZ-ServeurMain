package fr.heraliz.interfaces.mixin;

import fr.heraliz.interfaces.client.PolicesNettes;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lettres de nos polices TTF lissées partout (écrans et HUD) : voir {@link PolicesNettes}. À la fin de la préparation d'un
 * rendu (un RenderType de texte prépare d'abord ses états, dont la texture des lettres, puis passe ici), la planche liée
 * passe en GL_LINEAR si c'est une planche de nos polices. Même point d'accroche que {@code TexteLisseMixin} de CRac : les
 * deux peuvent régler GL_LINEAR l'un après l'autre sans gêne.
 */
@Mixin(RenderStateShard.class)
public abstract class TexteNetMixin {
    @Shadow
    @Final
    protected String name;

    @Inject(method = "setupRenderState", at = @At("TAIL"))
    private void heraliz$lettresNettes(CallbackInfo ci) {
        PolicesNettes.apresRendu(this.name);
    }
}
