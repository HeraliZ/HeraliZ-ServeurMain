package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Succès de Minecraft (27/10, perf) : chaque joueur écoutait les critères de tous les succès (plus d'un millier, recettes
 * comprises), vérifiés à chaque changement d'inventaire, donc à chaque tir de TaCZ (environ 1 % du serveur mesuré avec 90
 * joueurs), et son fichier advancements/<uuid>.json était réécrit sur le fil serveur à chaque sauvegarde. Aucun de nos
 * systèmes ne s'en sert : avec {@code [succes] actifs = false} de {@code heraliz_noyau-server.toml}, plus aucune écoute ni
 * écriture (les succès déjà gagnés restent dans leur fichier, sans changer).
 */
@Mixin(PlayerAdvancements.class)
public abstract class SuccesMixin {
    @Inject(method = "registerListeners(Lnet/minecraft/server/ServerAdvancementManager;)V", at = @At("HEAD"), cancellable = true)
    private void heraliz$sansEcoute(ServerAdvancementManager succes, CallbackInfo ci) {
        if (!NoyauConfig.succesActifs) {
            ci.cancel();
        }
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void heraliz$pasSurLeDisque(CallbackInfo ci) {
        if (!NoyauConfig.succesActifs) {
            ci.cancel();
        }
    }
}
