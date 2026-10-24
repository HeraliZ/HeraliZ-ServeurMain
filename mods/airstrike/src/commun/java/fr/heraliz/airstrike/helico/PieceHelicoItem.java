package fr.heraliz.airstrike.helico;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Pièce d'hélicoptère récupérée sur une épave : ne sert qu'à être revendue (Mila, la racheteuse du spawn). */
public class PieceHelicoItem extends Item {
    public final PieceHelico piece;

    public PieceHelicoItem(PieceHelico piece, Properties properties) {
        super(properties);
        this.piece = piece;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        lignes.add(Component.translatable("item.airstrike.helico_piece.desc").withStyle(ChatFormatting.GRAY));
    }
}
