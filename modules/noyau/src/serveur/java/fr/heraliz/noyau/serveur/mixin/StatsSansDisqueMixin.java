package fr.heraliz.noyau.serveur.mixin;

import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Statistiques de Minecraft (25/10, banc de charge) : à chaque sauvegarde, chaque joueur connecté réécrivait son fichier
 * stats/<uuid>.json sur le fil serveur (jusqu'à 300 ms de gel mesurés avec 20 à 30 joueurs). Aucun de nos systèmes ne les
 * lit (les statistiques des clans, des quêtes et de la saison sont à part) : elles ne sont plus écrites sur le disque.
 */
@Mixin(ServerStatsCounter.class)
public abstract class StatsSansDisqueMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void heraliz$pasSurLeDisque(CallbackInfo ci) {
        ci.cancel();
    }
}
