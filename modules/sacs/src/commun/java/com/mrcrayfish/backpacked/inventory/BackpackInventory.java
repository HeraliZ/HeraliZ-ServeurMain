package com.mrcrayfish.backpacked.inventory;

import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.backpacked.util.InventoryHelper;
import com.mrcrayfish.backpacked.util.PickpocketUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class BackpackInventory extends SimpleContainer {
   private final Player player;
   private final ItemStack stack;
   private boolean save;
   private boolean chargement;

   public BackpackInventory(int cols, int rows, Player player, ItemStack stack) {
      super(rows * cols);
      this.player = player;
      this.stack = stack;
      this.loadBackpackContents(player);
   }

   private void loadBackpackContents(Player player) {
      CompoundTag compound = this.stack.getOrCreateTag();
      if (compound.contains("Items", 9)) {
         this.chargement = true;
         try {
            InventoryHelper.loadAllItems(compound.getList("Items", 10), this, player.level(), player.position());
         } finally {
            this.chargement = false;
         }
         // Les objets en trop sont jetés au sol par le chargement : on réécrit le sac tout de suite pour qu'ils ne
         // restent pas aussi dans ses données (sinon, rouvert, il les rejetterait encore : copie).
         if (!player.level().isClientSide()) {
            this.saveItemsToStack();
         }
      }
   }

   public ItemStack getBackpackStack() {
      return this.stack;
   }

   public boolean stillValid(Player player) {
      return this.player.isAlive()
         && Services.BACKPACK.getBackpackStack(this.player).equals(this.stack)
         && (
            this.player.equals(player)
               || PickpocketUtil.canPickpocketEntity(this.player, player, (Double)Config.SERVER.pickpocketing.maxReachDistance.get() + 0.5)
         );
   }

   public void setChanged() {
      super.setChanged();
      if (this.chargement) {
         return;
      }
      // Écrit tout de suite dans l'objet sac : si le sac change de main avant le prochain tick (échange de case,
      // jet, mort), ses données sont déjà à jour et rien ne peut être copié ni perdu.
      this.saveItemsToStack();
   }

   public void tick() {
      if (this.save) {
         this.saveItemsToStack();
         this.save = false;
      }
   }

   public void saveItemsToStack() {
      CompoundTag compound = this.stack.getOrCreateTag();
      compound.put("Items", InventoryHelper.saveAllItems(new ListTag(), this));
   }
}
