package fr.heralisz.camo.mixin;

import fr.heraliz.tactique.client.IconesOnglets;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Onglets d'armes TaCZ sans icône (pistolets-mitrailleurs, précision) : une arme présente à la place (IconesOnglets).
 * Nom de développement et nom du jeu ; require = 0 : si le nom ne correspond pas, l'onglet reste comme avant.
 */
@Mixin(value = CreativeModeTab.class, remap = false)
public abstract class IconeOngletMixin {
   @Inject(method = {"getIconItem()Lnet/minecraft/world/item/ItemStack;", "m_40787_()Lnet/minecraft/world/item/ItemStack;"},
      at = @At("RETURN"), cancellable = true, require = 0)
   private void heraliz$iconeOnglet(CallbackInfoReturnable<ItemStack> cir) {
      ItemStack autre = IconesOnglets.remplacer((CreativeModeTab) (Object) this, cir.getReturnValue());
      if (autre != null) {
         cir.setReturnValue(autre);
      }
   }
}
