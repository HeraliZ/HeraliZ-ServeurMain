package fr.heraliz.crac.build.world;

import fr.heraliz.crac.heraliz.Reply;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.model.EdgeKey;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Operator tools for the building system: {@code /cracbuild showcase <pos>} builds one demo house per tier with every
 * piece kind (and reports any piece the rules refused), {@code clear}, {@code tier}, {@code damage}, {@code doors}.
 */
@Mod.EventBusSubscriber(modid = "crac", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuildDebugCommand {
    private static final UUID ADMIN = new UUID(0L, 42L);

    private BuildDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("cracbuild").requires(s -> s.hasPermission(2))
            .then(Commands.literal("showcase")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(c -> showcase(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), null))
                    .then(Commands.argument("tier", StringArgumentType.word())
                        .executes(c -> showcaseOf(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), StringArgumentType.getString(c, "tier"))))))
            .then(Commands.literal("clear")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 96))
                        .executes(c -> clear(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "radius"))))))
            .then(Commands.literal("tier")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 96))
                        .then(Commands.argument("tier", StringArgumentType.word())
                            .executes(c -> tier(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "radius"), StringArgumentType.getString(c, "tier")))))))
            .then(Commands.literal("damage")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 96))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                            .executes(c -> damage(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "radius"), IntegerArgumentType.getInteger(c, "percent")))))))
            .then(Commands.literal("doors")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 96))
                        .executes(c -> doors(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "radius")))))));
    }

    private static List<Piece> around(ServerLevel level, BlockPos pos, int r) {
        return StructureService.piecesIn(level, new AABB(pos).inflate(r));
    }

    private static int clear(CommandSourceStack src, BlockPos pos, int r) {
        List<Piece> list = around(src.getLevel(), pos, r);
        for (Piece p : list) {
            StructureService.remove(src.getLevel(), p, StructureService.Cause.ADMIN);
        }
        // structure blocks that hold no piece of the store (kept after a lost or unreadable store)
        int orphans = StructureIntegrity.clearOrphans(src.getLevel(), new AABB(pos).inflate(r));
        Reply.ok(src, Component.translatable("crac.cmd.cleared", list.size(), orphans));
        return list.size() + orphans;
    }

    private static int tier(CommandSourceStack src, BlockPos pos, int r, String id) {
        Tier t = Tier.byId(id);
        if (t == null) {
            Reply.fail(src, Component.translatable("crac.cmd.unknown_tier", id));
            return 0;
        }
        List<Piece> list = around(src.getLevel(), pos, r);
        for (Piece p : list) {
            StructureService.setTier(src.getLevel(), p, t);
        }
        Reply.ok(src, Component.translatable("crac.cmd.tier_set", list.size(), Component.translatable("tier.crac." + t.id)));
        return list.size();
    }

    private static int damage(CommandSourceStack src, BlockPos pos, int r, int percent) {
        List<Piece> list = around(src.getLevel(), pos, r);
        for (Piece p : list) {
            StructureService.damage(src.getLevel(), p, Math.max(1, p.maxHp * percent / 100), false);
        }
        Reply.ok(src, Component.translatable("crac.cmd.damaged", list.size(), percent));
        return list.size();
    }

    private static int doors(CommandSourceStack src, BlockPos pos, int r) {
        int n = 0;
        for (Piece p : around(src.getLevel(), pos, r)) {
            if (p.kind().hasDoor()) {
                p.setOpen(!p.isOpen());
                StructureService.refresh(src.getLevel(), p);
                n++;
            }
        }
        int fn = n;
        Reply.ok(src, Component.translatable("crac.cmd.doors", fn));
        return n;
    }

    /** The showcase house of the tier named {@code id} only; a tier it does not know is reported, nothing is built. */
    private static int showcaseOf(CommandSourceStack src, BlockPos pos, String id) {
        Tier t = Tier.byId(id);
        if (t == null) {
            Reply.fail(src, Component.translatable("crac.cmd.unknown_tier", id));
            return 0;
        }
        return showcase(src, pos, t);
    }

    /**
     * One v3 house per tier along +x (or a single one for the given tier), see {@link #house}. Reports how many pieces
     * were placed and every piece the rules refused.
     */
    private static int showcase(CommandSourceStack src, BlockPos pos, Tier only) {
        Tier[] tiers = only != null ? new Tier[]{only} : Tier.values();
        List<String> failures = new ArrayList<>();
        int placed = placeShowcase(src.getLevel(), pos, tiers, failures);
        Reply.ok(src, Component.translatable("crac.cmd.showcase", placed, failures.size()));
        for (String f : failures) {
            Reply.fail(src, Component.literal(f));
        }
        return placed;
    }

    /**
     * Places the showcase houses of {@code /cracbuild showcase}: house {@code i} of {@code tiers} has its origin at
     * {@code pos + (i * SPACING, 1, 0)} (its slab on the block above {@code pos}, as a player standing at {@code pos}
     * expects). Returns the number of pieces placed; a line per refused piece goes to {@code failures}.
     */
    public static int placeShowcase(ServerLevel level, BlockPos pos, Tier[] tiers, List<String> failures) {
        int placed = 0;
        for (int i = 0; i < tiers.length; i++) {
            Tier t = tiers[i];
            BlockPos o = pos.offset(i * SPACING, 1, 0);
            for (PieceKey k : house(o)) {
                StructureService.Outcome out = StructureService.placeAdmin(level, k, t, ADMIN);
                if (out.ok()) {
                    placed++;
                } else {
                    failures.add(t.id + " " + k.kind().id + " @" + k.anchorBlock().toShortString() + " h" + k.h() + ": " + out.message().getString());
                }
            }
        }
        return placed;
    }

    /** Blocks between two showcase houses along x (a house is about 16 blocks wide and 15 deep). */
    public static final int SPACING = 24;

    private static final double[] NORTH = {0, -1};
    private static final double[] SOUTH = {0, 1};
    private static final double[] EAST = {1, 0};
    private static final double[] WEST = {-1, 0};

    /**
     * The showcase house of building origin {@code o} (deterministic, §12), ordered so that every piece comes after its
     * supports; the first piece is the building's first foundation (anchor at the origin). Cells are 3 blocks; x east.
     * <ul>
     * <li>A 2 x 2 room: a window and a doorway (swinging out) on the north side, a double doorway (swinging in) on the
     * south side, a doorway into the round wing; L stairs in its south-east square, entered from the north and leaving
     * west at h 2 onto a second floor over the west half (windows, merged roofs at h 4 over the floor and the
     * stairwell).</li>
     * <li>A round wing east of it: two squares, five equilateral triangles and two squares turned by 30° closing exactly
     * between them. Its perimeter carries every full-height wall kind on triangle edges (window, wall, double doorway,
     * doorway) and 30°/60° walls on the turned squares; merged square and triangle roofs at h 2 cover it and the room's
     * north-east square.</li>
     * <li>A terrace west of the room: half walls, a floor on them half a wall up (h 1), a wall standing on a half wall,
     * a triangle floor leaning on the terrace with half-wall parapets on its triangle edges, and a half ramp up to it.</li>
     * <li>South of the double doorway, a landing, then a ramp up to a lookout floor at h 2 with half-wall parapets (a
     * ramp cannot start right under a door: the header leaves 3 px over a standing player).</li>
     * </ul>
     */
    public static List<PieceKey> house(BlockPos o) {
        List<PieceKey> k = new ArrayList<>();
        // ---- ground: the room, the round wing, the terrace and the lookout pads
        PieceKey r00 = PieceKey.square(PieceKind.FOUNDATION, o, LPoint.ORIGIN, 0, 0, 0);
        PieceKey r10 = across(r00, EAST, PieceKind.FOUNDATION, 0);
        PieceKey r01 = across(r00, SOUTH, PieceKind.FOUNDATION, 0);
        PieceKey r11 = across(r10, SOUTH, PieceKind.FOUNDATION, 0);
        k.addAll(List.of(r00, r10, r01, r11));

        PieceKey c0 = across(r10, EAST, PieceKind.FOUNDATION, 0);
        PieceKey c1 = across(r11, EAST, PieceKind.FOUNDATION, 0);
        PieceKey tN = across(c0, NORTH, PieceKind.TRI_FOUNDATION, 0);
        PieceKey tE0 = across(c0, EAST, PieceKind.TRI_FOUNDATION, 0);
        PieceKey tE1 = across(c1, EAST, PieceKind.TRI_FOUNDATION, 0);
        PieceKey tS = across(c1, SOUTH, PieceKind.TRI_FOUNDATION, 0);
        // the 60° gap between the two east triangles: a triangle
        LPoint mid = c0.lattice()[2];
        PieceKey uE = Topology.across(tE0, edgeAt(tE0, mid, c0.edges().get(1)), PieceKind.TRI_FOUNDATION, 0);
        // round corners (Rust): a square leaning on the triangle, a triangle closing exactly against the previous one
        PieceKey[] ne = corner(c0, 1, tE0, PieceKind.FOUNDATION, PieceKind.TRI_FOUNDATION);
        PieceKey[] se = corner(c1, 2, tS, PieceKind.FOUNDATION, PieceKind.TRI_FOUNDATION);
        PieceKey sNE = ne[0];
        PieceKey uNE = ne[1];
        PieceKey sSE = se[0];
        PieceKey uSE = se[1];
        k.addAll(List.of(c0, c1, tN, tE0, tE1, tS, uE, sNE, uNE, sSE, uSE));

        PieceKey w0 = across(r00, WEST, PieceKind.FOUNDATION, 0);
        PieceKey w1 = across(r01, WEST, PieceKind.FOUNDATION, 0);
        PieceKey s0 = across(r01, SOUTH, PieceKind.FOUNDATION, 0);
        PieceKey s1 = across(s0, SOUTH, PieceKind.FOUNDATION, 0);
        PieceKey s2 = across(s1, SOUTH, PieceKind.FOUNDATION, 0);
        k.addAll(List.of(w0, w1, s0, s1, s2));
        List<PieceKey> ground = new ArrayList<>(k);

        // ---- ground floor walls (rot 1 on a footprint edge: the door swings out of that footprint)
        k.add(wall(r00, NORTH, PieceKind.WINDOW, 0, 0));
        k.add(wall(r10, NORTH, PieceKind.DOORWAY, 0, 1));
        k.add(wall(r00, WEST, PieceKind.WALL, 0, 0));
        k.add(wall(r01, WEST, PieceKind.WALL, 0, 0));
        k.add(wall(r01, SOUTH, PieceKind.DOUBLE_DOORWAY, 0, 0));
        k.add(wall(r11, SOUTH, PieceKind.WALL, 0, 0));
        k.add(wall(r10, EAST, PieceKind.DOORWAY, 0, 0));
        k.add(wall(r11, EAST, PieceKind.WALL, 0, 0));
        // the wing's perimeter: every full-height wall kind on a triangle edge, 30° and 60° walls on the turned squares
        k.add(perimeter(tN, ground, 0, PieceKind.WINDOW, 0));
        k.add(perimeter(uNE, ground, 0, PieceKind.WALL, 0));
        k.add(perimeter(sNE, ground, 0, PieceKind.WINDOW, 0));
        k.add(perimeter(sNE, ground, 1, PieceKind.WALL, 0));
        k.add(perimeter(uE, ground, 0, PieceKind.DOUBLE_DOORWAY, 1));
        k.add(perimeter(uSE, ground, 0, PieceKind.WALL, 0));
        k.add(perimeter(sSE, ground, 0, PieceKind.WALL, 0));
        k.add(perimeter(sSE, ground, 1, PieceKind.WINDOW, 0));
        k.add(perimeter(tS, ground, 0, PieceKind.DOORWAY, 1));

        // ---- terrace: half walls, a floor on them (h 1), a wall on a half wall, a triangle floor with parapets
        k.add(wall(w0, NORTH, PieceKind.HALF_WALL, 0, 0));
        k.add(wall(w0, WEST, PieceKind.HALF_WALL, 0, 0));
        k.add(wall(w1, WEST, PieceKind.HALF_WALL, 0, 0));
        PieceKey terrace = PieceKey.on(PieceKind.FLOOR, o, w0.footprint(), 1, 0);
        k.add(terrace);
        k.add(wall(w0, WEST, PieceKind.WALL, 1, 0));
        PieceKey balcony = across(terrace, NORTH, PieceKind.TRI_FLOOR, 1);
        k.add(balcony);
        for (int e : freeEdges(balcony, List.of(terrace))) {
            k.add(Topology.wallOn(balcony, e, PieceKind.HALF_WALL, 1, 0));
        }

        // ---- volumes: the half ramp up to the terrace (climbing north), L stairs (entered from the north, leaving west
        // at h 2), the ramp beyond the landing south of the double doorway, up to the lookout (climbing south)
        k.add(PieceKey.on(PieceKind.RAMP, o, w1.footprint(), 0, 2 | 4));
        k.add(PieceKey.on(PieceKind.STAIRS_L, o, r11.footprint(), 0, 2));
        k.add(wall(s2, WEST, PieceKind.WALL, 0, 0));
        k.add(wall(s2, SOUTH, PieceKind.WINDOW, 0, 0));
        k.add(PieceKey.on(PieceKind.RAMP, o, s1.footprint(), 0, 0));
        k.add(PieceKey.on(PieceKind.FLOOR, o, s2.footprint(), 2, 0));
        for (double[] d : new double[][]{WEST, SOUTH, EAST}) {
            k.add(wall(s2, d, PieceKind.HALF_WALL, 2, 0));
        }

        // ---- roofs at h 2 over the room's north-east square and the wing, merged (squares and triangles)
        k.add(PieceKey.on(PieceKind.ROOF, o, r10.footprint(), 2, 0));
        for (PieceKey p : List.of(c0, c1, tN, uNE, sNE, uE, sSE, uSE, tS, tE0, tE1)) {
            k.add(PieceKey.on(p.kind().isTriangle() ? PieceKind.TRI_ROOF : PieceKind.ROOF, o, p.footprint(), 2, 0));
        }

        // ---- second floor over the west half, walls, merged roofs at h 4 over it and the stairwell
        k.add(PieceKey.on(PieceKind.FLOOR, o, r00.footprint(), 2, 0));
        k.add(PieceKey.on(PieceKind.FLOOR, o, r01.footprint(), 2, 0));
        k.add(wall(r00, NORTH, PieceKind.WALL, 2, 0));
        k.add(wall(r00, WEST, PieceKind.WINDOW, 2, 0));
        k.add(wall(r00, EAST, PieceKind.WALL, 2, 0));
        k.add(wall(r01, WEST, PieceKind.WALL, 2, 0));
        k.add(wall(r01, SOUTH, PieceKind.WINDOW, 2, 0));
        k.add(wall(r11, SOUTH, PieceKind.WALL, 2, 0));
        k.add(wall(r11, EAST, PieceKind.WALL, 2, 0));
        k.add(PieceKey.on(PieceKind.ROOF, o, r00.footprint(), 4, 0));
        k.add(PieceKey.on(PieceKind.ROOF, o, r01.footprint(), 4, 0));
        k.add(PieceKey.on(PieceKind.ROOF, o, r11.footprint(), 4, 0));
        return k;
    }

    /** Index of the edge of {@code s} that has {@code v} as an end and is not {@code other}. */
    private static int edgeAt(PieceKey s, LPoint v, EdgeKey other) {
        List<EdgeKey> edges = s.edges();
        for (int i = 0; i < edges.size(); i++) {
            EdgeKey e = edges.get(i);
            if (!e.equals(other) && (e.start().equals(v) || e.end().equals(v))) {
                return i;
            }
        }
        throw new IllegalStateException("no edge of " + s + " at " + v);
    }

    /**
     * The round corner at vertex {@code i} of square {@code c}, between the triangle {@code t} on its edge {@code i} and
     * the triangle on edge {@code i - 1}: a square leaning on {@code t} at that vertex, then the triangle that closes
     * exactly against the other one (as in a Rust round base).
     */
    private static PieceKey[] corner(PieceKey c, int i, PieceKey t, PieceKind squareKind, PieceKind triKind) {
        LPoint v = c.lattice()[i];
        int a = edgeAt(t, v, c.edges().get(i));
        PieceKey s = Topology.across(t, a, squareKind, c.h());
        int b = edgeAt(s, v, t.edges().get(a));
        PieceKey u = Topology.across(s, b, triKind, c.h());
        return new PieceKey[]{s, u};
    }

    /** Indices of the edges of {@code key} that none of {@code others} (other than itself) has. */
    private static List<Integer> freeEdges(PieceKey key, List<PieceKey> others) {
        List<Integer> out = new ArrayList<>();
        List<EdgeKey> own = key.edges();
        for (int i = 0; i < own.size(); i++) {
            EdgeKey e = own.get(i);
            boolean shared = false;
            for (PieceKey q : others) {
                if (!q.equals(key) && q.edges().contains(e)) {
                    shared = true;
                    break;
                }
            }
            if (!shared) {
                out.add(i);
            }
        }
        return out;
    }

    /** A ground-floor wall on the {@code n}-th edge of {@code key} that no other foundation of {@code ground} shares. */
    private static PieceKey perimeter(PieceKey key, List<PieceKey> ground, int n, PieceKind kind, int rot) {
        return Topology.wallOn(key, freeEdges(key, ground).get(n), kind, 0, rot);
    }

    /** Index of the footprint edge of {@code key} whose midpoint lies farthest along {@code dir} from the centroid. */
    public static int edgeToward(PieceKey key, double[] dir) {
        LPoint[] v = key.lattice();
        int best = 0;
        double bestD = -Double.MAX_VALUE;
        for (int i = 0; i < v.length; i++) {
            LPoint a = v[i];
            LPoint b = v[(i + 1) % v.length];
            double d = (a.toX() + b.toX()) * dir[0] + (a.toZ() + b.toZ()) * dir[1];
            if (d > bestD + 1e-9) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** The piece of {@code kind} at height {@code h} across the edge of {@code key} facing {@code dir}. */
    public static PieceKey across(PieceKey key, double[] dir, PieceKind kind, int h) {
        return Topology.across(key, edgeToward(key, dir), kind, h);
    }

    /** A wall of {@code kind} at height {@code h} on the edge of {@code key} facing {@code dir}. */
    public static PieceKey wall(PieceKey key, double[] dir, PieceKind kind, int h, int rot) {
        return Topology.wallOn(key, edgeToward(key, dir), kind, h, rot);
    }
}
