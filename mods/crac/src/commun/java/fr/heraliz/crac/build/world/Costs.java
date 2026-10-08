package fr.heraliz.crac.build.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Material costs of placing, upgrading and repairing pieces. */
public final class Costs {
    private Costs() {
    }

    public static List<BuildCost> place(PieceKind kind) {
        return BuildCost.parseAll(BuildConfig.placeCost(kind));
    }

    public static List<BuildCost> upgrade(PieceKind kind, Tier target) {
        double mult = BuildConfig.costMultiplier(kind);
        List<BuildCost> out = new ArrayList<>();
        for (BuildCost c : BuildCost.parseAll(BuildConfig.upgradeCost(target))) {
            int n = (int) Math.ceil(c.count() * mult - 1e-9);
            if (n > 0) {
                out.add(new BuildCost(c.ingredient(), n, c.label(), c.problem()));
            }
        }
        return out;
    }

    public static List<BuildCost> repair(Tier tier) {
        return BuildCost.parseAll(BuildConfig.repairCost(tier));
    }

    /** Whether the player can pay the whole list at once (each stack counted for one entry only). */
    public static boolean has(Player p, List<BuildCost> cost) {
        return p.getAbilities().instabuild || allocate(p, cost) != null;
    }

    /** Takes the cost from the inventory; does nothing when it cannot be paid in full. */
    public static void take(ServerPlayer p, List<BuildCost> cost) {
        if (p.getAbilities().instabuild) {
            return;
        }
        int[][] plan = allocate(p, cost);
        if (plan == null) {
            return;
        }
        List<ItemStack> items = p.getInventory().items;
        for (int[] perSlot : plan) {
            for (int s = 0; s < perSlot.length; s++) {
                if (perSlot[s] > 0) {
                    items.get(s).shrink(perSlot[s]);
                }
            }
        }
        p.getInventory().setChanged();
    }

    /** Items taken from each inventory slot by each cost entry, or null when the inventory cannot pay. */
    private static int[][] allocate(Player p, List<BuildCost> cost) {
        for (BuildCost c : cost) {
            if (!c.isValid()) {
                return null;
            }
        }
        List<ItemStack> items = p.getInventory().items;
        int[] counts = new int[items.size()];
        boolean[][] matches = new boolean[cost.size()][items.size()];
        int[] needs = new int[cost.size()];
        for (int s = 0; s < items.size(); s++) {
            counts[s] = items.get(s).getCount();
        }
        for (int c = 0; c < cost.size(); c++) {
            needs[c] = cost.get(c).count();
            for (int s = 0; s < items.size(); s++) {
                matches[c][s] = cost.get(c).matches(items.get(s));
            }
        }
        // the most specific entries pick first, so "3 oak planks + 5 planks" leaves the oak to the oak entry
        List<Integer> order = new ArrayList<>();
        for (int c = 0; c < cost.size(); c++) {
            order.add(c);
        }
        order.sort(Comparator.comparingInt(c -> cost.get(c).variety()));
        return allocate(counts, matches, needs, order);
    }

    /**
     * Pure allocation: entry {@code c} may draw from slot {@code s} when {@code matches[c][s]}; entries are served in
     * {@code order}, each slot's items going to one entry only. Returns {@code take[c][s]}, or null when short.
     */
    static int[][] allocate(int[] counts, boolean[][] matches, int[] needs, List<Integer> order) {
        int[] left = counts.clone();
        int[][] take = new int[needs.length][counts.length];
        for (int c : order) {
            int need = needs[c];
            for (int s = 0; s < left.length && need > 0; s++) {
                if (matches[c][s] && left[s] > 0) {
                    int n = Math.min(need, left[s]);
                    take[c][s] = n;
                    left[s] -= n;
                    need -= n;
                }
            }
            if (need > 0) {
                return null;
            }
        }
        return take;
    }

    /** Misconfigured entries of every cost list, for the server log ("cost.place.wall: crac:nails (unknown item)"). */
    public static List<String> problems() {
        List<String> out = new ArrayList<>();
        for (PieceKind k : PieceKind.values()) {
            collect(out, "cost.place." + k.id, place(k));
        }
        for (Tier t : Tier.values()) {
            if (t != Tier.TWIG) {
                collect(out, "cost.upgrade." + t.id, BuildCost.parseAll(BuildConfig.upgradeCost(t)));
            }
            collect(out, "cost.repair." + t.id, repair(t));
        }
        return out;
    }

    private static void collect(List<String> out, String where, List<BuildCost> cost) {
        for (BuildCost c : cost) {
            if (!c.isValid()) {
                out.add(where + ": " + c.label() + " (" + c.problem() + ")");
            }
        }
    }

    public static Component describe(List<BuildCost> cost) {
        if (cost.isEmpty()) {
            return Component.translatable("crac.cost.free");
        }
        StringBuilder sb = new StringBuilder();
        for (BuildCost c : cost) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.count()).append("x ").append(c.displayName().getString());
        }
        return Component.literal(sb.toString());
    }
}
