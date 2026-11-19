package fr.heraliz.noyau.client.mixin.matmos;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MAtmos (sons d'ambiance, mod d'un autre auteur, jar intact) chez le joueur (audit des images par seconde du 19/11,
 * point 7) : Minecraft envoie le tick des joueurs pour CHAQUE joueur du monde, les autres compris ({@code RemotePlayer}),
 * et les procédures de MAtmos tournaient donc pour chaque joueur visible : détection de l'environnement (≈ 250 lectures de
 * blocs et autant de positions créées par tick et par joueur), cubes de 12 x 12 x 12 blocs autour de l'eau, et des sons
 * d'ambiance joués aussi à la place de chaque joueur proche (≈ 7 500 lectures de blocs par tick au spawn avec 30 joueurs).
 * Elles ne tournent plus que pour le joueur lui-même : chacun entend sa propre ambiance, comme MAtmos l'a prévu.
 * Côté serveur, {@code serveur/mixin/MatmosServeurMixin} les coupe déjà toutes.
 */
@Pseudo
@Mixin(targets = {
        "net.tct.matmos.procedures.WaterfallProcedure",
        "net.tct.matmos.procedures.WaterStillProcedure",
        "net.tct.matmos.procedures.FallWaterProcedure",
        "net.tct.matmos.procedures.UnderWaterProcedure",
        "net.tct.matmos.procedures.InventorySoundProcedure",
        "net.tct.matmos.procedures.NetherloopProcedure",
        "net.tct.matmos.procedures.EnvironmentDetectorProcedure",
        "net.tct.matmos.procedures.AmbiantTimerProcedure"
}, remap = false)
public abstract class MatmosJoueurMixin {
    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void heraliz$seulementLeJoueur(TickEvent.PlayerTickEvent e, CallbackInfo ci) {
        if (e.player.level().isClientSide() && e.player != Minecraft.getInstance().player) {
            ci.cancel();
        }
    }
}
