package fr.heralisz.tactical.api;

import java.util.Collection;
import java.util.Optional;
import fr.heralisz.tactical.api.item.IConsumable;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.item.IThrowable;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import fr.heralisz.tactical.resource.CommonAssetsManager;
import net.minecraft.world.item.ItemStack;

/**
 * API du moteur (index des armes de mêlée, soins, lancers), lue aussi par d'autres mods (heralisz_matchs, par réflexion :
 * getMeleeIndexes, getConsumableIndexes, getThrowableIndexes, get*Index(ItemStack)) : noms et signatures inchangés.
 * Fusion (heraliz_tactique) : les accès aux affichages (get*Display, @OnlyIn(CLIENT)) sont passés côté client, dans
 * {@code fr.heralisz.tactical.api.client.HeraliszTacticalClientAPI} (mêmes noms, mêmes signatures).
 */
public class HeraliszTacticalAPI {
   public static Optional<ThrowableIndex<?, ?>> getThrowableIndex(ItemStack stack) {
      return stack.getItem() instanceof IThrowable item
         ? Optional.ofNullable(CommonAssetsManager.get().getThrowableIndex(item.getId(stack)))
         : Optional.empty();
   }

   public static Collection<ThrowableIndex<?, ?>> getThrowableIndexes() {
      return CommonAssetsManager.get().getThrowableIndexes();
   }

   public static Optional<ConsumableIndex> getConsumableIndex(ItemStack stack) {
      return stack.getItem() instanceof IConsumable item
         ? Optional.ofNullable(CommonAssetsManager.get().getConsumableIndex(item.getId(stack)))
         : Optional.empty();
   }

   public static Collection<ConsumableIndex> getConsumableIndexes() {
      return CommonAssetsManager.get().getConsumableIndexes();
   }

   public static Optional<MeleeWeaponIndex<?>> getMeleeIndex(ItemStack stack) {
      return stack.getItem() instanceof IMeleeWeapon item ? Optional.ofNullable(CommonAssetsManager.get().getMeleeIndex(item.getId(stack))) : Optional.empty();
   }

   public static Collection<MeleeWeaponIndex<?>> getMeleeIndexes() {
      return CommonAssetsManager.get().getMeleeIndexes();
   }
}
