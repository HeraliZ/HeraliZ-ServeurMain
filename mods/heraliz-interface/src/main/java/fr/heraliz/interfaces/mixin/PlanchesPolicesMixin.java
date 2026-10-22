package fr.heraliz.interfaces.mixin;

import fr.heraliz.interfaces.client.PolicesNettes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Retient les planches de lettres de nos polices TTF à leur enregistrement (FontSet les enregistre sous
 * « espace:police/n ») : voir {@link PolicesNettes}.
 */
@Mixin(TextureManager.class)
public abstract class PlanchesPolicesMixin {
    @Inject(method = "register(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V",
            at = @At("HEAD"))
    private void heraliz$noterPlanche(ResourceLocation nom, AbstractTexture texture, CallbackInfo ci) {
        PolicesNettes.noter(nom, texture);
    }
}
