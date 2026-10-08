package fr.heraliz.casino.economy;

import fr.heraliz.casino.item.ChipItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Les jetons du casino : la seule monnaie des machines. Les mises sont prises directement dans l'inventaire
 * (la monnaie est rendue automatiquement) et les gains sont verses en jetons.
 */
public final class Chips {
    private Chips() {
    }

    /** Valeurs des jetons, du plus petit au plus grand. */
    public static final int[] VALUES = {1, 10, 100, 1000, 10000};
    /** Couleur du nom de chaque valeur (textes, particules) : fer, cuivre, argent, or, lingot d'or. */
    public static final int[] COLORS = {0xB4BCC4, 0xE0904E, 0xE4E8EC, 0xFFCC40, 0xFFE070};

    public static int indexOf(int value) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i] == value) return i;
        return -1;
    }

    /** Valeur totale des jetons de l'inventaire. */
    public static long count(Player p) {
        long total = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ChipItem c) total += (long) c.value * s.getCount();
        }
        return total;
    }

    // =================================================================== affichage
    /** 12500 -> "12 500". */
    public static String format(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            b.append(s.charAt(i));
            int left = len - i - 1;
            if (left > 0 && left % 3 == 0) b.append(' ');
        }
        return (n < 0 ? "-" : "") + b;
    }

    /** "12 500 jetons". */
    public static MutableComponent text(long n) {
        return Component.translatable(n > 1 ? "casino.heralizcasino.chips" : "casino.heralizcasino.chip", format(n));
    }
}
