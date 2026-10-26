package fr.heraliz.crates.loot;

import fr.heraliz.crates.crate.CrateType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Table de butin d'un coffre (tirage pondere).
 */
public final class CrateLoot {
    public final CrateType type;
    public final List<LootEntry> entries = new ArrayList<>();

    public CrateLoot(CrateType type) {
        this.type = type;
    }

    public boolean isEmpty() {
        return totalWeight() <= 0;
    }

    public int totalWeight() {
        int w = 0;
        for (LootEntry e : entries) {
            if (e.isValid()) w += e.weight;
        }
        return w;
    }

    @Nullable
    public LootEntry roll(RandomSource rnd) {
        int total = totalWeight();
        if (total <= 0) return null;
        int r = rnd.nextInt(total);
        for (LootEntry e : entries) {
            if (!e.isValid()) continue;
            r -= e.weight;
            if (r < 0) return e;
        }
        return null;
    }

    /** Tirage pondere parmi les entrees acceptees par le filtre (caisse des emotes : emotes pas encore debloquees). */
    @Nullable
    public LootEntry roll(RandomSource rnd, Predicate<LootEntry> permis) {
        int total = 0;
        for (LootEntry e : entries) {
            if (e.isValid() && permis.test(e)) total += e.weight;
        }
        if (total <= 0) return null;
        int r = rnd.nextInt(total);
        for (LootEntry e : entries) {
            if (!e.isValid() || !permis.test(e)) continue;
            r -= e.weight;
            if (r < 0) return e;
        }
        return null;
    }

    /** Objet "leurre" pour la roue : on evite de repeter l'objet gagnant quand c'est possible. */
    public ItemStack rollDisplay(RandomSource rnd, LootEntry avoid) {
        LootEntry pick = rollDisplayEntry(rnd, avoid);
        return pick == null ? ItemStack.EMPTY : pick.displayStack();
    }

    /** Entree "leurre" pour la roue (objet et rarete), differente de l'objet gagnant quand c'est possible. */
    @Nullable
    public LootEntry rollDisplayEntry(RandomSource rnd, LootEntry avoid) {
        LootEntry pick = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            pick = roll(rnd);
            if (pick != avoid) break;
        }
        return pick;
    }

    public double chance(LootEntry e) {
        int total = totalWeight();
        return total <= 0 || !e.isValid() ? 0 : 100.0 * e.weight / total;
    }
}
