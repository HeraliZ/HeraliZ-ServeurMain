package fr.heralisz.camo.mixin;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import fr.heralisz.camo.client.AffichagesArmes;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Affichage d'une arme gardé le temps d'un tick (voir {@link AffichagesArmes}). */
@Mixin(value = TimelessAPI.class, remap = false)
public abstract class TimelessApiAffichageMixin {
   private static final String GET = "getGunDisplay(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;";

   @Inject(method = GET, at = @At("HEAD"), cancellable = true)
   private static void heraliz$lire(ItemStack stack, CallbackInfoReturnable<Optional<GunDisplayInstance>> cir) {
      Optional<GunDisplayInstance> garde = AffichagesArmes.lire(stack);
      if (garde != null) {
         cir.setReturnValue(garde);
      }
   }

   @Inject(method = GET, at = @At("RETURN"))
   private static void heraliz$garder(ItemStack stack, CallbackInfoReturnable<Optional<GunDisplayInstance>> cir) {
      AffichagesArmes.garder(stack, cir.getReturnValue());
   }
}
