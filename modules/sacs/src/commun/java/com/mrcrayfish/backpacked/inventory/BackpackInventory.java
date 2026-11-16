package com.mrcrayfish.backpacked.inventory;

import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.backpacked.util.InventoryHelper;
import com.mrcrayfish.backpacked.util.PickpocketUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class BackpackInventory extends SimpleContainer {
   private final Player player;
   private final ItemStack stack;
   private boolean save;
   private boolean chargement;
   /**
    * HeraliZ (15/11, audit : copie d'un sac) : la liste « Items » de l'objet sac telle que ce sac l'a lue ou écrite en
    * dernier (le même objet, comparé par identité). Si l'objet en porte une autre, quelqu'un d'autre a changé ce sac
    * depuis (il est passé par un coffre, un autre joueur l'a vidé) : ce contenu en mémoire ne vaut plus rien.
    */
   private Tag dernierItems;

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
      this.dernierItems = compound.get("Items");
   }

   /** HeraliZ (15/11) : l'objet sac contient-il encore ce que ce sac a lu ou écrit en dernier ? Sinon, à oublier. */
   public boolean aJour() {
      CompoundTag tag = this.stack.getTag();
      return (tag == null ? null : tag.get("Items")) == this.dernierItems;
   }

   public ItemStack getBackpackStack() {
      return this.stack;
   }

   /**
    * L'écran de ce sac vaut-il encore pour {@code player} ? HeraliZ (14/11, anti-duplication, sacs dans les coffres des
    * bases) : seulement tant que CET objet sac (la même pile, comparée par identité : jamais une copie) est sur le dos de
    * son porteur vivant, et que celui qui regarde est vivant. Le sac qui part ailleurs (coffre, inventaire, sol, corps,
    * fouille, autre joueur) emporte son contenu, déjà écrit dans l'objet ({@link #setChanged}) : l'écran ne vaut plus,
    * aucun clic n'y est plus accepté et Minecraft le ferme au tick suivant. Un autre joueur que le porteur : seulement si
    * l'ouverture du sac d'autrui est permise ({@code enabledPickpocketing}, coupée sur HeraliZ) et à portée.
    */
   public boolean stillValid(Player player) {
      if (!this.player.isAlive() || !player.isAlive() || Services.BACKPACK.getBackpackStack(this.player) != this.stack
         || !this.aJour()) {
         return false;
      }

      return this.player.equals(player)
         || Boolean.TRUE.equals(Config.SERVER.pickpocketing.enabled.get())
            && PickpocketUtil.canPickpocketEntity(this.player, player, (Double)Config.SERVER.pickpocketing.maxReachDistance.get() + 0.5);
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
      ListTag items = InventoryHelper.saveAllItems(new ListTag(), this);
      compound.put("Items", items);
      this.dernierItems = items;
   }
}
