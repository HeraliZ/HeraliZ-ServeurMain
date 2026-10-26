package fr.heralisz.camo.item;

import fr.heralisz.camo.Camo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Objet camo : posé depuis l'onglet Skin du menu de modification, il est définitif sur l'arme. Clic droit : le camo entre
 * dans la collection à vie du joueur et l'objet est consommé (déjà possédé : message, rien de consommé).
 */
public class CamoItem extends Item {
   /**
    * Ajout à la collection à vie (CamosAVie.ajouter, branché par la partie serveur du mod) ; vrai si le camo vient d'y
    * entrer (un exemplaire est alors consommé). Sans partie serveur (jar -client) : rien n'est ajouté.
    */
   public static volatile BiPredicate<ServerPlayer, Camo> AJOUTER_COLLECTION = (p, c) -> false;

   private final Camo camo;

   public CamoItem(Camo camo) {
      super(new Properties().stacksTo(16));
      this.camo = camo;
   }

   public Camo camo() {
      return this.camo;
   }

   @Override
   public Component getName(ItemStack stack) {
      return super.getName(stack).copy().withStyle(this.camo.tier.color);
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return this.camo.tier == Camo.Tier.LEGENDARY || this.camo.tier == Camo.Tier.MYTHIC;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         if (AJOUTER_COLLECTION.test(sp, this.camo) && !player.getAbilities().instabuild) {
            stack.shrink(1);
         }
         return InteractionResultHolder.consume(stack);
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable(this.camo.tier.key()).withStyle(this.camo.tier.color, ChatFormatting.BOLD));
      tooltip.add(Component.translatable("item.heralisz_camo.camo.desc." + this.camo.id).withStyle(ChatFormatting.GRAY));
      if (this.camo.gun != null) {
         tooltip.add(Component.translatable("item.heralisz_camo.camo.gun." + this.camo.id).withStyle(ChatFormatting.GOLD));
      }
      if (fr.heraliz.tactique.avie.ObjetsAVie.estSkinAVie(this.camo)) {
         // skin à vie (21/10) : seulement par la collection, jamais posé depuis l'objet
         tooltip.add(Component.translatable("item.heralisz_camo.camo.a_vie").withStyle(ChatFormatting.GOLD));
         return;
      }
      if (!this.camo.isPackAPunch()) {
         tooltip.add(Component.translatable("item.heralisz_camo.camo.collect").withStyle(ChatFormatting.YELLOW));
      }
      tooltip.add(Component.translatable("item.heralisz_camo.camo.howto").withStyle(ChatFormatting.DARK_GRAY));
      if (this.camo.gun == null && fr.heralisz.camo.BagSupport.PRESENT) {
         tooltip.add(Component.translatable("item.heralisz_camo.camo.howto_bag").withStyle(ChatFormatting.DARK_GRAY));
      }
      tooltip.add(Component.translatable("item.heralisz_camo.camo.permanent").withStyle(ChatFormatting.DARK_RED));
   }
}
