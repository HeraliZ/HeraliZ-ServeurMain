package fr.heraliz.loot.serveur.mixin;

import fr.heraliz.loot.CraftReserve;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Garde-fou de la fabrication réservée au grade Fondateur ({@link CraftReserve}, audit du 15/11, duplication n° 2) : la
 * case résultat d'une grille de fabrication ({@code ResultSlot} de Minecraft : établi, inventaire de base, écrans de Curios)
 * ne se prend pas pour un joueur qui n'a pas le droit, quel que soit le geste (clic, Maj + clic, touches 1 à 9, jet,
 * glisser, double clic : tous passent par {@code Slot.mayPickup}). Même si un écran calculait encore un résultat
 * (autre mod, nouvelle version de Curios), rien ne sort de la grille.
 * <p>{@code ResultSlot} ne redéfinit pas {@code mayPickup} (celui de {@code Slot}, qui répond toujours oui) : l'injection est
 * dans {@code Slot} et ne regarde que les {@code ResultSlot}, côté serveur. Les autres cases (enclume, four, marchands,
 * Table d'armurier de TaCZ, recycleur) ne sont pas des {@code ResultSlot} : inchangées. Config non obligatoire,
 * {@code require = 0}.</p>
 */
@Mixin(Slot.class)
public abstract class CraftResultatMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true, require = 0)
    private void heraliz$resultatReserve(Player joueur, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ResultSlot && joueur instanceof ServerPlayer sp && !CraftReserve.permis(sp)) {
            cir.setReturnValue(false);
        }
    }
}
