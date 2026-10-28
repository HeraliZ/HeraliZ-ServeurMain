package fr.heraliz.crac.block;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Objet d'un coffre de base (caisse en métal et coffres par rareté) : se pose collé au mur ({@link ObjetAdossable}) ;
 * l'infobulle dit sa contenance et la place qu'il prend (la rareté, en couleur, vient de HeraliZ Loot).
 */
public class ObjetCoffre extends ObjetAdossable {
    private final int cases;
    /** « 1 », « large » ou « haut » : clé crac.coffre.place.* */
    private final String place;

    public ObjetCoffre(Block block, Properties props, int hauteur, int cases, String place) {
        super(block, props, hauteur);
        this.cases = cases;
        this.place = place;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        super.appendHoverText(stack, level, lignes, flag);
        lignes.add(Component.translatable("crac.coffre.infobulle", this.cases,
            Component.translatable("crac.coffre.place." + this.place)).withStyle(ChatFormatting.GRAY));
    }
}
