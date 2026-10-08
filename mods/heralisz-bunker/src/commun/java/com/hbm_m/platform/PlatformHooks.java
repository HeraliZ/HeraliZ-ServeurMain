package com.hbm_m.platform;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

public final class PlatformHooks {
   private PlatformHooks() {
   }

   public static CompoundTag getItemTag(ItemStack stack) {
      return stack.getTag();
   }

   public static CompoundTag getItemTag(ClientboundBlockEntityDataPacket pkt) {
      return pkt.getTag();
   }

   public static boolean hasItemTag(ItemStack stack) {
      return stack.hasTag();
   }

   public static void editItemTag(ItemStack stack, Consumer<CompoundTag> editor) {
      editor.accept(stack.getOrCreateTag());
   }

   public static void setItemTag(ItemStack stack, CompoundTag tag) {
      stack.setTag(tag);
   }

   public static int getInt(ItemStack stack, String key) {
      CompoundTag t = getItemTag(stack);
      return t == null ? 0 : t.getInt(key);
   }

   public static long getLong(ItemStack stack, String key) {
      CompoundTag t = getItemTag(stack);
      return t == null ? 0L : t.getLong(key);
   }

   public static boolean getBoolean(ItemStack stack, String key) {
      CompoundTag t = getItemTag(stack);
      return t != null && t.getBoolean(key);
   }

   public static String getString(ItemStack stack, String key) {
      CompoundTag t = getItemTag(stack);
      return t == null ? "" : t.getString(key);
   }

   public static CompoundTag getCompound(ItemStack stack, String key) {
      CompoundTag t = getItemTag(stack);
      return t == null ? new CompoundTag() : t.getCompound(key);
   }

   public static void putInt(ItemStack stack, String key, int val) {
      editItemTag(stack, t -> t.putInt(key, val));
   }

   public static void putLong(ItemStack stack, String key, long val) {
      editItemTag(stack, t -> t.putLong(key, val));
   }

   public static void putBoolean(ItemStack stack, String key, boolean val) {
      editItemTag(stack, t -> t.putBoolean(key, val));
   }

   public static void putString(ItemStack stack, String key, String val) {
      editItemTag(stack, t -> t.putString(key, val));
   }

   public static void put(ItemStack stack, String key, CompoundTag val) {
      editItemTag(stack, t -> t.put(key, val));
   }

   public static boolean contains(ItemStack stack, String key) {
      CompoundTag t = getItemTag(stack);
      return t != null && t.contains(key);
   }

   public static void remove(ItemStack stack, String key) {
      editItemTag(stack, t -> t.remove(key));
   }

   public static Path getConfigDir() {
      return FMLPaths.CONFIGDIR.get();
   }
}
