package com.mrcrayfish.backpacked.util;

import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.item.BackpackItem_expand1;
import com.mrcrayfish.backpacked.item.BackpackItem_expand2;
import com.mrcrayfish.backpacked.item.Backpack_Mountain;
import com.mrcrayfish.backpacked.platform.Services;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ClientUtils {
   public static void createBackpackTooltip(ItemStack stack, List<Component> list) {
      contenu(stack, list);
      if ((Boolean)Config.SERVER.backpack.lockIntoSlot.get()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && Services.BACKPACK.getBackpackStack(mc.player).equals(stack)) {
            CompoundTag tag = stack.getTag();
            if (tag != null && !tag.getList("Items", 10).isEmpty()) {
               mc.font
                  .getSplitter()
                  .splitLines(BackpackItem.REMOVE_ITEMS_TOOLTIP, 150, Style.EMPTY)
                  .forEach(formattedText -> list.add(Component.literal(formattedText.getString()).withStyle(ChatFormatting.RED)));
            }
         }
      }
   }

   /**
    * HeraliZ (18/11, demande du propriétaire : « au survol d'un sac, voir le nombre d'objets qu'il contient ; sur un mort
    * qui a beaucoup de sacs, savoir dans quel sac il y a des trucs ») : cases occupées sur la place du sac et nombre
    * d'objets, lus dans la liste « Items » de l'objet sac (synchronisée avec le joueur), ou « Vide ».
    */
   private static void contenu(ItemStack stack, List<Component> list) {
      CompoundTag tag = stack.getTag();
      ListTag items = tag == null ? null : tag.getList("Items", 10);
      int cases = 0, objets = 0;
      if (items != null) {
         for (int i = 0; i < items.size(); i++) {
            int n = items.getCompound(i).getByte("Count") & 255;
            if (n > 0) {
               cases++;
               objets += n;
            }
         }
      }
      if (cases == 0) {
         list.add(Component.translatable("backpacked.tooltip.vide").withStyle(ChatFormatting.GRAY));
         return;
      }
      int place = place(stack.getItem());
      list.add(Component.translatable("backpacked.tooltip.contenu", cases, place > 0 ? place : cases, objets).withStyle(ChatFormatting.GOLD));
   }

   /** Nombre de cases du sac (colonnes x rangées de son modèle), 0 : inconnu. */
   private static int place(Item item) {
      if (item instanceof BackpackItem b) return b.getColumnCount() * b.getRowCount();
      if (item instanceof BackpackItem_expand1 b) return b.getColumnCount() * b.getRowCount();
      if (item instanceof BackpackItem_expand2 b) return b.getColumnCount() * b.getRowCount();
      if (item instanceof Backpack_Mountain b) return b.getColumnCount() * b.getRowCount();
      return 0;
   }
}
