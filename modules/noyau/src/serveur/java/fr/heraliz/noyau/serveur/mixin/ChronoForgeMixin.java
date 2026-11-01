package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.serveur.diag.Systemes;
import net.minecraftforge.server.timings.TimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Profil du serveur (01/11) : temps de chaque entité et entité de bloc rangé par type, sur tout le profil. Forge entoure
 * déjà leur tick de son chronomètre ({@link TimeTracker}) ; ses méthodes à deux arguments ne sont appelées que s'il est
 * allumé, c'est-à-dire pendant un profil ou {@code /diag mesure} : hors de là, ce mixin ne coûte rien. Pendant un profil,
 * la durée va à {@link Systemes}, et Forge saute son propre relevé (sauf si {@code /diag mesure} tourne). Config facultative
 * (classe de Forge, aucun nom de Minecraft) : si Forge change, le profil le dit et prend le chronomètre de Forge seul.
 */
@Mixin(value = TimeTracker.class, remap = false)
public abstract class ChronoForgeMixin {
    @Inject(method = "trackStart(Ljava/lang/Object;J)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void heraliz$debut(Object objet, long nanos, CallbackInfo ci) {
        if (Systemes.debutChrono(this, objet, nanos)) ci.cancel();
    }

    @Inject(method = "trackEnd(Ljava/lang/Object;J)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void heraliz$fin(Object objet, long nanos, CallbackInfo ci) {
        if (Systemes.finChrono(this, objet, nanos)) ci.cancel();
    }
}
