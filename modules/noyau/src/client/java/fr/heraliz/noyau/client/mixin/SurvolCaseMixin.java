package fr.heraliz.noyau.client.mixin;

import fr.heraliz.noyau.client.kit.EcranVerre;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Case survolée dans nos écrans en verre : pas de carré blanc de Minecraft par-dessus l'objet, la case elle-même
 * s'éclaire (bord, fond et halo, {@link fr.heraliz.noyau.client.kit.CaseVerre}). Méthode ajoutée par Forge (nom non
 * traduit) ; facultatif : s'il ne s'applique pas, le carré blanc reste.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SurvolCaseMixin {
    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private static void heraliz$survol(GuiGraphics g, int x, int y, int z, int couleur, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof EcranVerre) ci.cancel();
    }
}
