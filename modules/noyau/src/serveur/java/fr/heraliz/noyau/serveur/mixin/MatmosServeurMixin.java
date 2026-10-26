package fr.heraliz.noyau.serveur.mixin;

import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MAtmos (sons d'ambiance, mod d'un autre auteur, jar intact) côté serveur (25/10, banc de charge) : ses procédures de sons
 * tournent aussi sur le serveur, à chaque tick de chaque joueur, parcourent les blocs autour de lui et ENVOIENT ces sons
 * aux joueurs voisins (sons d'eau, de cascade, du Nether, de l'inventaire des autres, en double et sans direction) ;
 * jusqu'à 110 ms mesurés sur un tick. Le jeu de chaque joueur les joue déjà pour lui-même : sur le serveur, ces procédures
 * ne font plus rien. La détection de l'environnement (variables du joueur) n'est pas touchée.
 */
@Pseudo
@Mixin(targets = {
        "net.tct.matmos.procedures.WaterfallProcedure",
        "net.tct.matmos.procedures.WaterStillProcedure",
        "net.tct.matmos.procedures.FallWaterProcedure",
        "net.tct.matmos.procedures.UnderWaterProcedure",
        "net.tct.matmos.procedures.InventorySoundProcedure",
        "net.tct.matmos.procedures.NetherloopProcedure"
}, remap = false)
public abstract class MatmosServeurMixin {
    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void heraliz$pasSurLeServeur(TickEvent.PlayerTickEvent e, CallbackInfo ci) {
        if (!e.player.level().isClientSide()) {
            ci.cancel();
        }
    }
}
