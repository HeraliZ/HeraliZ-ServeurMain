package fr.heraliz.quetes.objet;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Registre du marchand (staff) : clic droit sur un PNJ marchand = la liste de ce qu'il vend ou rachète et de ses prix,
 * à modifier depuis le chat. Le traitement est dans la partie serveur (PnjServeur, EditionMarchands).
 */
public class RegistreMarchand extends Item {
    public RegistreMarchand(Properties p) {
        super(p);
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        lignes.add(Component.translatable("item.heraliz_quetes.registre.aide1"));
        lignes.add(Component.translatable("item.heraliz_quetes.registre.aide2"));
    }
}
