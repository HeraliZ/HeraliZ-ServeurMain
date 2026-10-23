package fr.heraliz.tactique.armurier;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Registre de l'armurier (op) : clic droit = éditeur des recettes de la Table d'armurier. Droits vus par le serveur. */
public class RegistreArmurier extends Item {
   public RegistreArmurier(Properties p) {
      super(p);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player joueur, InteractionHand main) {
      ItemStack s = joueur.getItemInHand(main);
      if (!level.isClientSide && joueur instanceof ServerPlayer sp) Armurier.OUVRIR_SERVEUR.accept(sp);
      return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
   }

   @Override
   public boolean isFoil(ItemStack s) {
      return true;
   }

   @Override
   public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
      lignes.add(Component.translatable("item.heraliz_tactique.registre_armurier.aide"));
   }
}
