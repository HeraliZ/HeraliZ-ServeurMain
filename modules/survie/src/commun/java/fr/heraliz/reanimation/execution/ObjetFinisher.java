package fr.heraliz.reanimation.execution;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Finisher à débloquer (26/10, demande du propriétaire) : objet gagné dans les caisses Vote et Quête, qui porte
 * l'identifiant d'une exécution (NBT « Execution », par exemple {@code {Execution:"mille_coupes"}}). Clic droit : il entre
 * pour toujours dans la collection du joueur (menu des exécutions, touche =) et l'objet est consommé ; déjà possédé : rien
 * n'est consommé. Le déblocage est fait par la partie serveur ({@code execution.FinisherServeur}) ; ici, le nom, l'infobulle
 * et le clic.
 */
public class ObjetFinisher extends Item {
   public static final String CLE = "Execution";

   public ObjetFinisher() {
      super(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
   }

   @Nullable
   public static ExecutionType type(ItemStack stack) {
      return stack.hasTag() ? ExecutionType.byId(stack.getTag().getString(CLE)) : null;
   }

   @Override
   public Component getName(ItemStack stack) {
      ExecutionType t = type(stack);
      return t == null ? super.getName(stack)
         : Component.translatable("item.reanimation.finisher.de", Component.translatable("execution.reanimation." + t.id));
   }

   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
      ExecutionType t = type(stack);
      if (t != null) lignes.add(Component.translatable("execution.reanimation." + t.id + ".desc").withStyle(ChatFormatting.GRAY));
      lignes.add(Component.translatable("item.reanimation.finisher.aide").withStyle(ChatFormatting.DARK_GRAY));
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand main) {
      // déblocage : partie serveur (FinisherServeur, sur l'événement du clic droit) ; ici, le clic est seulement consommé
      return InteractionResultHolder.sidedSuccess(player.getItemInHand(main), level.isClientSide);
   }
}
