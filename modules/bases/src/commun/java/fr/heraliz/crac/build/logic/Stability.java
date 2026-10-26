package fr.heraliz.crac.build.logic;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;

/**
 * Rust-like structural stability. Foundations are 100. A piece is worth the best of its supporters minus the cost
 * of the link: {@link Supports.Link#VERTICAL} costs {@code verticalLoss}, {@link Supports.Link#CANTILEVER} costs
 * {@code cantileverLoss}. A piece with no path to a foundation is worth 0 and collapses.
 */
public final class Stability {
    public static final int MAX = 100;

    public record Params(int verticalLoss, int cantileverLoss, int minToPlace) {
        public Params {
            verticalLoss = Math.max(1, verticalLoss);
            cantileverLoss = Math.max(1, cantileverLoss);
        }

        public static final Params DEFAULT = new Params(5, 35, 10);

        public int cost(Supports.Link link) {
            return switch (link) {
                case DIRECT -> 0;
                case VERTICAL -> this.verticalLoss;
                case CANTILEVER -> this.cantileverLoss;
            };
        }
    }

    private Stability() {
    }

    /** Value a new piece would get, using the stored stability of existing pieces. */
    public static int estimate(StructureView view, PieceKey key, Params params) {
        if (Supports.isGrounded(key)) {
            return MAX;
        }
        int best = 0;
        for (Supports.Support s : Supports.of(view, key)) {
            best = Math.max(best, s.piece().stability - params.cost(s.link()));
        }
        return Math.max(0, best);
    }

    /**
     * Recompute stability of {@code pieces} (typically a whole building) and store it in {@link Piece#stability}.
     * Dijkstra on "max of min" paths: costs are positive, so cycles of floors leaning on each other converge.
     */
    public static void recompute(StructureView view, Collection<Piece> pieces, Params params) {
        Long2ObjectOpenHashMap<List<long[]>> carried = new Long2ObjectOpenHashMap<>();
        Long2IntOpenHashMap value = new Long2IntOpenHashMap();
        PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(b[1], a[1]));
        for (Piece p : pieces) {
            p.stability = 0;
            if (Supports.isGrounded(p.key())) {
                value.put(p.id, MAX);
                queue.add(new long[]{p.id, MAX});
                continue;
            }
            for (Supports.Support s : Supports.of(view, p.key())) {
                carried.computeIfAbsent(s.piece().id, k -> new ArrayList<>()).add(new long[]{p.id, params.cost(s.link())});
            }
        }
        while (!queue.isEmpty()) {
            long[] top = queue.poll();
            long id = top[0];
            int v = (int) top[1];
            if (value.getOrDefault(id, -1) != v) continue;
            List<long[]> next = carried.get(id);
            if (next == null) continue;
            for (long[] e : next) {
                int nv = v - (int) e[1];
                if (nv > value.getOrDefault(e[0], 0)) {
                    value.put(e[0], nv);
                    queue.add(new long[]{e[0], nv});
                }
            }
        }
        for (Piece p : pieces) {
            p.stability = Math.max(0, value.getOrDefault(p.id, 0));
        }
    }

    /**
     * Recomputes {@code pieces} (a building after a removal) and returns those left without support: stability 0 and
     * not grounded. With {@code onlyCutOff}, a piece that had no support before the removal either stays up: entries the
     * store could not read may be what holds it.
     */
    public static List<Piece> recomputeFallen(StructureView view, Collection<Piece> pieces, Params params, boolean onlyCutOff) {
        Long2IntOpenHashMap before = new Long2IntOpenHashMap();
        if (onlyCutOff) {
            for (Piece p : pieces) {
                before.put(p.id, p.stability);
            }
        }
        recompute(view, pieces, params);
        List<Piece> fallen = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.stability <= 0 && !Supports.isGrounded(p.key()) && (!onlyCutOff || before.get(p.id) > 0)) {
                fallen.add(p);
            }
        }
        return fallen;
    }
}
