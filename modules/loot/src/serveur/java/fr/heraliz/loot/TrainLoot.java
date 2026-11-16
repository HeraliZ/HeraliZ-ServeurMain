package fr.heraliz.loot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Butin des bagages du train (HeraliZ Monde, 09/11) : objets tirés dans la table d'un palier (« t1 » à « t4 ») avec les
 * mêmes règles qu'un conteneur de la carte (nombre d'objets de la table, pas de doublon). API lue par réflexion par
 * fr.heralisz.utils.train.BagagesServeur : ne jamais renommer la classe ni changer la signature.
 */
public final class TrainLoot {
    private TrainLoot() {}

    /** Objets d'un bagage du palier donné ; liste vide si la table est absente. */
    public static List<ItemStack> tirer(String palier, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        TablesCoffres.Choix c = TablesCoffres.pourConteneur(palier);
        if (c == null) return out;
        int n = Math.max(1, TablesCoffres.nombre(c.table(), c.min(), c.max(), r));
        Set<String> deja = new HashSet<>();
        for (int k = 0; k < n; k++) {
            ItemStack st = k == 0 ? TablesCoffres.tirerPlein(c.table(), r, 0, deja) : TablesCoffres.tirerUnique(c.table(), r, 0, deja);
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }
}
