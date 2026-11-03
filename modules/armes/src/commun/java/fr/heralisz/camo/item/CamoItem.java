package fr.heralisz.camo.item;

import fr.heralisz.camo.BagSupport;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
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
 * <p>
 * Sac (03/11) : camo tenu au curseur, clic droit sur un sac dans une case (inventaire, sac porté sur le dos dans l'écran
 * de Curios, coffre) : le camo est posé sur le sac, définitif, et l'objet est consommé. Le serveur décide de tout (le jeu
 * du joueur ne fait que laisser l'objet en place, le serveur renvoie l'état exact) ; seul l'objet camo tenu par le joueur
 * peut être posé. Avant, seul l'artisanat marchait (CamoBagRecipe : sac et camo dans une grille), donc jamais avec un
 * sac porté qui contient quelque chose (il ne s'enlève plus du dos, réglage lockIntoSlot du 02/11).
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

   /**
    * Ce camo peut-il aller sur un sac ? Pas un skin réservé à une arme, ni un skin à vie (Séraphin, Chaînes de l'Enfer :
    * seulement par la collection, jamais consommé). Même règle pour l'artisanat (CamoBagRecipe).
    */
   public static boolean vaSurUnSac(Camo camo) {
      return camo.gun == null && !fr.heraliz.tactique.avie.ObjetsAVie.estSkinAVie(camo);
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

   /**
    * Camo au curseur, clic droit sur un sac dans une case : camo posé sur le sac (voir la classe). Vrai : le clic est pris
    * (rien d'autre ne se passe, ni échange ni dépôt) ; faux : clic normal de Minecraft (pas un sac, clic gauche…).
    */
   @Override
   public boolean overrideStackedOnOther(ItemStack camoTenu, Slot slot, ClickAction action, Player player) {
      if (action != ClickAction.SECONDARY || !BagSupport.PRESENT) {
         return false;
      }
      ItemStack sac = slot.getItem();
      if (sac.getCount() != 1 || !BagSupport.isBag(sac) || !slot.allowModification(player)) {
         return false;
      }
      if (player.level().isClientSide) {
         return true; // le serveur pose le camo (ou refuse) et renvoie l'état des cases
      }
      if (!vaSurUnSac(this.camo)) {
         player.displayClientMessage(Component.translatableWithFallback("msg.heralisz_camo.sac_refuse",
            "Ce skin ne va pas sur un sac").withStyle(ChatFormatting.RED), true);
         return true;
      }
      if (CamoData.get(sac) != null) {
         player.displayClientMessage(Component.translatableWithFallback("msg.heralisz_camo.bag_locked",
            "Ce sac a déjà un camouflage (définitif)").withStyle(ChatFormatting.RED), true);
         return true;
      }
      CamoData.set(sac, this.camo);
      slot.setChanged();
      camoTenu.shrink(1);
      player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
      player.displayClientMessage(Component.translatableWithFallback("msg.heralisz_camo.sac_pose", "%s posé sur le sac",
         Component.translatable(this.camo.nameKey()).withStyle(this.camo.tier.color)).withStyle(ChatFormatting.GRAY), true);
      return true;
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
      tooltip.add(Component.translatable("item.heralisz_camo.camo.collect").withStyle(ChatFormatting.YELLOW));
      tooltip.add(Component.translatable("item.heralisz_camo.camo.howto").withStyle(ChatFormatting.DARK_GRAY));
      if (this.camo.gun == null && fr.heralisz.camo.BagSupport.PRESENT) {
         tooltip.add(Component.translatable("item.heralisz_camo.camo.howto_bag").withStyle(ChatFormatting.DARK_GRAY));
      }
      tooltip.add(Component.translatable("item.heralisz_camo.camo.permanent").withStyle(ChatFormatting.DARK_RED));
   }
}
