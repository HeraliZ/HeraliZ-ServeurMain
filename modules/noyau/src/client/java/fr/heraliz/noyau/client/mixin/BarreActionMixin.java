package fr.heraliz.noyau.client.mixin;

import fr.heraliz.noyau.client.Bandeaux;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Messages de la barre d'action (texte nu au-dessus de la barre d'objets, {@code displayClientMessage(..., true)} de tous
 * les mods, serveur ou joueur) : affichés dans le bandeau commun ({@link Bandeaux#barre}, 18/11, maquette validée). Le
 * titre d'un disque de musique (texte animé) reste celui du jeu. Facultatif : s'il ne s'applique pas, le jeu affiche les
 * messages comme avant.
 */
@Mixin(Gui.class)
public abstract class BarreActionMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void heraliz$bandeau(Component message, boolean animer, CallbackInfo ci) {
        if (animer || message == null) return;
        if (Bandeaux.barre(message)) ci.cancel();
    }
}
