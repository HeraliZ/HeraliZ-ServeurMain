package fr.heraliz.armory.mixin.common;

import fr.heraliz.armory.suit.SuitRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
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
 * porte. Noms vanilla et SRG cites tous les deux (pas de table de remappage) : le jeu installe n'a que le nom SRG.
 */
@Mixin(targets = "net.minecraft.world.inventory.InventoryMenu$1", remap = false)
public abstract class InventoryArmorSlotMixin {
    @Inject(method = {"mayPlace(Lnet/minecraft/world/item/ItemStack;)Z", "m_5857_(Lnet/minecraft/world/item/ItemStack;)Z"},
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void heraliz$suitRule(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (stack.isEmpty() || !(self.container instanceof Inventory inv)) return;
        // slots 36 a 39 de l'inventaire : pieds, jambes, torse, tete
        EquipmentSlot slot = switch (self.getContainerSlot()) {
            case 36 -> EquipmentSlot.FEET;
            case 37 -> EquipmentSlot.LEGS;
            case 38 -> EquipmentSlot.CHEST;
            case 39 -> EquipmentSlot.HEAD;
            default -> null;
        };
        if (slot == null) return;
        if (SuitRules.isSuit(stack)) {
            // une combinaison ailleurs qu'au torse : le jeu la refuse deja, sans message
            if (slot != EquipmentSlot.CHEST || SuitRules.canWearSuit(inv.player)) return;
            SuitRules.tell(inv.player, false);
            cir.setReturnValue(false);
        } else if (SuitRules.wearsSuit(inv.player)) {
            SuitRules.tell(inv.player, true);
            cir.setReturnValue(false);
        }
    }
}
