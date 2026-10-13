package fr.heraliz.armory.mixin.common;

import fr.heraliz.armory.suit.SuitRules;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slots d'armure de l'inventaire du joueur (classe anonyme InventoryMenu$1) : regle des combinaisons (0.9.0) pour TOUS
 * les objets, ceux des autres mods compris (clic, shift-clic, touches numeriques ; client et serveur). Tant qu'une
 * combinaison est portee, rien n'entre dans les 4 slots d'armure ; une combinaison n'entre que si rien d'autre n'est
 * porte ({@link SuitRules#refusesArmorSlot}, partagee avec l'ecran de Curios : CuriosArmorSlotMixin). Noms vanilla et
 * SRG cites tous les deux (pas de table de remappage) : le jeu installe n'a que le nom SRG.
 */
@Mixin(targets = "net.minecraft.world.inventory.InventoryMenu$1", remap = false)
public abstract class InventoryArmorSlotMixin {
    @Inject(method = {"mayPlace(Lnet/minecraft/world/item/ItemStack;)Z", "m_5857_(Lnet/minecraft/world/item/ItemStack;)Z"},
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void heraliz$suitRule(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SuitRules.refusesArmorSlot((Slot) (Object) this, stack)) cir.setReturnValue(false);
    }
}
