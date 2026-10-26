package fr.heraliz.armory.mixin.common;

import fr.heraliz.armory.curios.MajClicCurios;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Maj + clic dans l'inventaire du joueur (InventoryMenu, client et serveur) : un objet Curios part dans le premier slot
 * Curios libre qui l'accepte, sans ouvrir le panneau Curios ({@link MajClicCurios}, rangement fait par le serveur).
 * Tout le reste (armures, inventaire &lt;-&gt; barre d'objets, artisanat) garde le Maj + clic de Minecraft. Noms
 * vanilla et SRG cites tous les deux (pas de table de remappage, comme InventoryArmorSlotMixin).
 */
@Mixin(value = InventoryMenu.class, remap = false)
public abstract class InventoryCuriosQuickMoveMixin {
    @Inject(method = {"quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;",
            "m_7648_(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"},
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void heraliz$curios(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (MajClicCurios.majClic((InventoryMenu) (Object) this, player, index)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
