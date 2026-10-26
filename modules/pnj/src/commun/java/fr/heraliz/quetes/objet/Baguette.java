package fr.heraliz.quetes.objet;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * Baguette de quête du staff (/quete baguette &lt;groupe&gt;) : clic droit sur un bloc = un point de quête de ce groupe à
 * cet endroit (le bloc n'est pas modifié) ; accroupi + clic droit = retirer le point de ce bloc. Le traitement est dans
 * la partie serveur ({@link #SERVEUR}).
 */
public class Baguette extends Item {
    public static final String GROUPE = "HeralizQueteGroupe";
    /** Branché par la partie serveur ; sans elle (jeu du joueur) : rien. */
    public static volatile Function<UseOnContext, InteractionResult> SERVEUR = c -> InteractionResult.PASS;

    public Baguette(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getLevel().isClientSide) return InteractionResult.SUCCESS;
        return SERVEUR.apply(c);
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lignes, TooltipFlag flag) {
        String g = s.hasTag() ? s.getTag().getString(GROUPE) : "";
        lignes.add(Component.translatable("item.heraliz_quetes.baguette.groupe", g.isEmpty() ? "?" : g));
        lignes.add(Component.translatable("item.heraliz_quetes.baguette.aide1"));
        lignes.add(Component.translatable("item.heraliz_quetes.baguette.aide2"));
    }
}
