package fr.heraliz.armory.mixin.common;

import fr.heraliz.armory.suit.SuitRules;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slots d'armure de l'ecran de Curios (0.14.0) : c'est l'inventaire du joueur depuis que la touche de l'inventaire
 * l'ouvre (client/curios/InventaireCurios). Curios y cree ses propres slots d'armure (classes anonymes
 * CuriosContainerV2$1, menu actuel, et CuriosContainer$1, ancien menu) : sans ce mixin, la regle des combinaisons ne
 * jouait qu'apres coup (garde-fou serveur de SuitEventsServeur, objet rendu au tick suivant). Meme regle et meme message
 * que l'inventaire de base ({@link SuitRules#refusesArmorSlot}, InventoryArmorSlotMixin), client et serveur.
 * Curios 5.14.1 (jar de serveur/mods-tiers) : si une autre version change ces classes, {@code require = 0} laisse le jeu
 * demarrer et le garde-fou serveur continue de s'appliquer.
 */
@Mixin(targets = {"top.theillusivec4.curios.common.inventory.container.CuriosContainerV2$1",
        "top.theillusivec4.curios.common.inventory.container.CuriosContainer$1"}, remap = false)
public abstract class CuriosArmorSlotMixin {
    @Inject(method = {"mayPlace(Lnet/minecraft/world/item/ItemStack;)Z", "m_5857_(Lnet/minecraft/world/item/ItemStack;)Z"},
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void heraliz$suitRule(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SuitRules.refusesArmorSlot((Slot) (Object) this, stack)) cir.setReturnValue(false);
    }
}
