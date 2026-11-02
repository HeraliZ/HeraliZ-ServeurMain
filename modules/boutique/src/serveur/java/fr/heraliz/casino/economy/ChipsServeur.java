package fr.heraliz.casino.economy;

import fr.heraliz.casino.item.ChipItem;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Partie serveur des jetons du casino : mises prises dans l'inventaire (monnaie rendue) puis, s'il en manque, sur le
 * compte de capsules ({@link #miser}) ; capsules donnees dans l'inventaire, jamais au sol (le reste va sur le compte,
 * {@link #give}). Les gains des machines vont sur le compte ({@link CompteCapsules#verser}). Les valeurs, le comptage et
 * l'affichage restent dans {@link Chips} (commun).
 */
public final class ChipsServeur {
    private ChipsServeur() {
    }

    /**
     * Mise d'une machine du casino (02/11) : les capsules de l'inventaire d'abord, puis le compte de capsules pour ce qui
     * manque (comme la caisse, reglage {@code compte.payer_caisse}) : les gains vont sur le compte, on rejoue sans passer
     * par le distributeur. Faux : rien n'a ete pris.
     */
    public static boolean miser(ServerPlayer p, long amount) {
        return CompteCapsules.payer(p, amount, CompteCapsules.SOURCE_MISE) != null;
    }

    /** Capsules utilisables pour une mise (inventaire, plus le compte s'il paie les mises). */
    public static long disponible(ServerPlayer p) {
        return CompteCapsules.disponible(p);
    }

    /**
     * Retire "amount" jetons de l'inventaire en rendant la monnaie si besoin.
     * @return false (et rien n'est retire) si le joueur n'a pas assez
     */
    public static boolean take(ServerPlayer p, long amount) {
        // une mise négative n'est jamais « payée » (tous les appels vérifient déjà le montant : ceinture et bretelles)
        if (amount < 0) return false;
        if (amount == 0) return true;
        convertirAnciennes(p);
        Inventory inv = p.getInventory();
        long[] have = new long[Chips.VALUES.length];
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ChipItem c) {
                int k = Chips.indexOf(c.value);
                if (k >= 0) {
                    have[k] += s.getCount();
                    total += (long) c.value * s.getCount();
                }
            }
        }
        if (total < amount) return false;
        // paiement exact avec les plus gros jetons possibles...
        long[] use = new long[Chips.VALUES.length];
        long rem = amount;
        for (int k = Chips.VALUES.length - 1; k >= 0; k--) {
            long n = Math.min(have[k], rem / Chips.VALUES[k]);
            use[k] = n;
            rem -= n * Chips.VALUES[k];
        }
        long change = 0;
        if (rem > 0) {
            // ...sinon on casse le plus petit jeton restant qui couvre le reste (toujours possible, voir la doc)
            for (int k = 0; k < Chips.VALUES.length; k++) {
                if (have[k] - use[k] > 0 && Chips.VALUES[k] >= rem) {
                    use[k]++;
                    change = Chips.VALUES[k] - rem;
                    rem = 0;
                    break;
                }
            }
            if (rem > 0) {
                // securite : tout prendre et rendre la difference
                System.arraycopy(have, 0, use, 0, have.length);
                change = total - amount;
            }
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ChipItem c) {
                int k = Chips.indexOf(c.value);
                if (k < 0 || use[k] <= 0) continue;
                int n = (int) Math.min(use[k], s.getCount());
                s.shrink(n);
                use[k] -= n;
                if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }
        inv.setChanged();
        if (change > 0) give(p, change);
        return true;
    }

    /**
     * Remplace les anciennes capsules (100, 1 000, 10 000 ; avant 2.5.0) de l'inventaire par des capsules 1 / 5 / 10 de
     * meme total : rien n'est perdu, rien n'est cree. Appele a la connexion et avant chaque paiement.
     */
    public static void convertirAnciennes(ServerPlayer p) {
        Inventory inv = p.getInventory();
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ChipItem c && Chips.ancienne(c.value)) {
                total += (long) c.value * s.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (total > 0) {
            inv.setChanged();
            give(p, total);
            fr.heraliz.casino.HeralizCasino.LOGGER.info("[HeralizCasino] {} : anciennes capsules converties ({} en capsules 1 / 5 / 10)",
                    p.getGameProfile().getName(), total);
        }
    }

    /**
     * Donne des jetons dans l'inventaire ; ce qui n'y tient pas va sur le compte de capsules (sinon mis de cote jusqu'a la
     * prochaine connexion), jamais au sol (02/11 ; avant, le reste tombait aux pieds du joueur).
     */
    public static void give(Player p, long amount) {
        if (amount <= 0) return;
        if (!(p instanceof ServerPlayer sp)) {
            for (ItemStack s : stacks(amount)) ItemHandlerHelper.giveItemToPlayer(p, s);
            return;
        }
        long reste = mettre(sp, amount);
        if (reste > 0) CompteCapsules.surplus(sp, reste);
    }

    /** Met des jetons dans l'inventaire, autant qu'il en tient ; rend la valeur de ce qui n'a pas tenu (rien n'est jete). */
    public static long mettre(ServerPlayer p, long amount) {
        if (amount <= 0) return 0;
        Inventory inv = p.getInventory();
        long reste = 0;
        for (int k = Chips.VALUES.length - 1; k >= 0 && amount > 0; k--) {
            long n = amount / Chips.VALUES[k];
            amount -= n * Chips.VALUES[k];
            while (n > 0) {
                int stack = (int) Math.min(64, n);
                n -= stack;
                ItemStack s = new ItemStack(ModRegistry.chip(k), stack);
                inv.add(s);
                if (!s.isEmpty()) reste += (long) s.getCount() * Chips.VALUES[k];
            }
        }
        reste += amount; // jamais (1 est la plus petite capsule) : par securite
        inv.setChanged();
        return reste;
    }

    /** Jetons sous forme d'objets (pour les recompenses des commandes, les caisses...). */
    public static java.util.List<ItemStack> stacks(long amount) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int k = Chips.VALUES.length - 1; k >= 0 && amount > 0; k--) {
            long n = amount / Chips.VALUES[k];
            amount -= n * Chips.VALUES[k];
            while (n > 0) {
                int stack = (int) Math.min(64, n);
                n -= stack;
                out.add(new ItemStack(ModRegistry.chip(k), stack));
            }
        }
        return out;
    }
}
