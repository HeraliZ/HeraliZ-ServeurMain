package fr.heralisz.bunker.keycard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Fragment numéroté (1 à 4) d'une carte d'accès, trouvé dans le loot de la carte. Les 4 fragments DIFFÉRENTS d'une même
 * carte donnent la carte, seulement à la Table d'armurier, onglet Divers (26/10 ; recette écrite par outils/crafts.py ;
 * l'ancienne recette de la grille 2 x 2 et l'assemblage au clic droit sont retirés, le 28/10 pour le clic droit).
 * Plusieurs fois le même numéro ne sert à rien. Nom en couleur de rareté par HeraliZ Loot (raretes.json, outils/loot_armes.py).
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
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.heralisz_bunker.keycard_fragment.desc1").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.heralisz_bunker.keycard_fragment.desc2",
         Component.translatable("item.heralisz_bunker.keycard_" + this.level.id).withStyle(this.level.color)).withStyle(ChatFormatting.DARK_GRAY));
   }
}
