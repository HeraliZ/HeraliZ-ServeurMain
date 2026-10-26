package fr.heralisz.bunker.keycard;

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

/**
 * Fragment numéroté (1 à 4) d'une carte d'accès, trouvé dans le loot de la carte. Les 4 fragments DIFFÉRENTS d'une même
 * carte donnent la carte : Table d'armurier, onglet Divers (26/10 ; recette écrite par outils/crafts.py, l'ancienne
 * recette de la grille 2 x 2 est retirée) ou clic droit
 * avec un fragment en main quand les 4 numéros sont dans l'inventaire (décidé par le serveur, FragmentsPont). Plusieurs
 * fois le même numéro ne sert à rien. Nom en couleur de rareté par HeraliZ Loot (raretes.json, outils/loot_armes.py).
 */
public class KeycardFragmentItem extends Item {
   public static final int MORCEAUX = 4;
   public final AccessLevel level;
   public final int numero;

   public KeycardFragmentItem(AccessLevel level, int numero) {
      super(new Properties().stacksTo(16));
      this.level = level;
      this.numero = numero;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      for (int n = 1; n <= MORCEAUX; n++) {
         player.getCooldowns().addCooldown(KeycardRegistry.fragment(this.level, n), 10);   // pas de clics en rafale
      }
      if (!world.isClientSide && player instanceof ServerPlayer sp) {
         FragmentsPont.assembler(sp, this.level);
      }
      return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.heralisz_bunker.keycard_fragment.desc1").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.heralisz_bunker.keycard_fragment.desc2",
         Component.translatable("item.heralisz_bunker.keycard_" + this.level.id).withStyle(this.level.color)).withStyle(ChatFormatting.DARK_GRAY));
   }
}
