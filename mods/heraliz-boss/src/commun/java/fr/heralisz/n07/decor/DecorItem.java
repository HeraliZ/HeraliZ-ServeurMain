package fr.heralisz.n07.decor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** Objet d'un décor : une ligne d'ambiance et le mode d'emploi (où il se pose, ce qu'on peut régler). */
public class DecorItem extends BlockItem {
   private final DecorKind kind;

   public DecorItem(Block block, DecorKind kind, Properties p) {
      super(block, p);
      this.kind = kind;
   }

   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
      String base = "block.heralisz_n07." + kind.spec.id;
      tip.add(Component.translatable(base + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      DecorSpec s = kind.spec;
      String how = switch (s.mount) {
         case CEILING -> "decor.heralisz_n07.hint.ceiling";
         case WALL -> "decor.heralisz_n07.hint.wall";
         default -> s.rail ? "decor.heralisz_n07.hint.rail" : "decor.heralisz_n07.hint.floor";
      };
      tip.add(Component.translatable(how).withStyle(ChatFormatting.DARK_AQUA));
      if (s.drop) tip.add(Component.translatable("decor.heralisz_n07.hint.drop").withStyle(ChatFormatting.DARK_AQUA));
   }
}
