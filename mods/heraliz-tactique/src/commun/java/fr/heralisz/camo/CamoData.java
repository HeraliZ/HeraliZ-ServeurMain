package fr.heralisz.camo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Le camo est une simple étiquette NBT sur l'arme : il suit l'arme (ramassée, échangée, lootée). */
public final class CamoData {
   public static final String KEY = "HeraliszCamo";

   @Nullable
   public static Camo get(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      return tag != null && tag.contains(KEY) ? Camo.byId(tag.getString(KEY)) : null;
   }

   public static void set(ItemStack stack, Camo camo) {
      stack.getOrCreateTag().putString(KEY, camo.id);
   }

   /** Compteur de kills de l'arme (skins qui évoluent, compteur façon StatTrak). */
   public static final String KILLS = "HeraliszKills";

   public static int kills(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      return tag != null ? tag.getInt(KILLS) : 0;
   }

   public static void addKill(ItemStack stack) {
      CompoundTag tag = stack.getOrCreateTag();
      tag.putInt(KILLS, tag.getInt(KILLS) + 1);
   }

   private CamoData() {}
}
