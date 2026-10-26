package fr.heralisz.tactical.api.client;

import java.util.Optional;
import fr.heralisz.tactical.api.item.IConsumable;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.item.IThrowable;
import fr.heralisz.tactical.client.resource.TacticalClientAssetsManager;
import fr.heralisz.tactical.client.resource.display.ConsumableDisplayInstance;
import fr.heralisz.tactical.client.resource.display.MeleeDisplayInstance;
import fr.heralisz.tactical.client.resource.display.ThrowableDisplayInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Fusion (heraliz_tactique) : accès aux affichages (modèles, animations, sons) des objets du moteur, sortis de
 * {@code fr.heralisz.tactical.api.HeraliszTacticalAPI} (code commun) sans changement : mêmes méthodes, même code.
 */
public class HeraliszTacticalClientAPI {
   @OnlyIn(Dist.CLIENT)
   public static Optional<ConsumableDisplayInstance> getConsumableDisplay(ItemStack stack) {
      if (stack.getItem() instanceof IConsumable item) {
         ConsumableDisplayInstance display = TacticalClientAssetsManager.INSTANCE.getConsumableDisplay(item.getDisplayId(stack));
         return display != null ? Optional.of(display) : Optional.ofNullable(TacticalClientAssetsManager.INSTANCE.getConsumableDisplay(item.getId(stack)));
      } else {
         return Optional.empty();
      }
   }

   @OnlyIn(Dist.CLIENT)
   public static Optional<ConsumableDisplayInstance> getConsumableDisplay(ResourceLocation id) {
      return Optional.ofNullable(TacticalClientAssetsManager.INSTANCE.getConsumableDisplay(id));
   }

   @OnlyIn(Dist.CLIENT)
   public static Optional<ThrowableDisplayInstance> getThrowableDisplay(ItemStack stack) {
      if (stack.getItem() instanceof IThrowable item) {
         ThrowableDisplayInstance display = TacticalClientAssetsManager.INSTANCE.getThrowableDisplay(item.getDisplayId(stack));
         return display != null ? Optional.of(display) : Optional.ofNullable(TacticalClientAssetsManager.INSTANCE.getThrowableDisplay(item.getId(stack)));
      } else {
         return Optional.empty();
      }
   }

   @OnlyIn(Dist.CLIENT)
   public static Optional<ThrowableDisplayInstance> getThrowableDisplay(ResourceLocation id) {
      return Optional.ofNullable(TacticalClientAssetsManager.INSTANCE.getThrowableDisplay(id));
   }

   @OnlyIn(Dist.CLIENT)
   public static Optional<MeleeDisplayInstance> getMeleeDisplay(ItemStack stack) {
      if (stack.getItem() instanceof IMeleeWeapon item) {
         MeleeDisplayInstance display = TacticalClientAssetsManager.INSTANCE.getMeleeDisplay(item.getDisplayId(stack));
         return display != null ? Optional.of(display) : Optional.ofNullable(TacticalClientAssetsManager.INSTANCE.getMeleeDisplay(item.getId(stack)));
      } else {
         return Optional.empty();
      }
   }

   @OnlyIn(Dist.CLIENT)
   public static Optional<MeleeDisplayInstance> getMeleeDisplay(ResourceLocation index) {
      return Optional.ofNullable(TacticalClientAssetsManager.INSTANCE.getMeleeDisplay(index));
   }

}
