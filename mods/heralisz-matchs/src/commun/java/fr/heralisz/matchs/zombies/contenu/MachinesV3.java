package fr.heralisz.matchs.zombies.contenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocCollision;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocMachine;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Machines v3 de « The Giant » (D-051) : donnees generees par {@code tools/matchs/zombies/exporter_machines_v3.py}
 * ({@code data/heralisz_matchs/machines_v3/machines.json}, lu une fois dans le jar, des deux cotes) et pose dans le monde.
 * <p>Chaque machine = une case maitresse ({@link BlocMachine}, bloc-entite, corps cuit) ; les tres grandes (tour du
 * mainframe, plateforme) sont decoupees en morceaux de 4 x 4 x 4 blocs cuits chacun dans la case de son centre
 * ({@link BlocPiece}), pour ne jamais disparaitre quand la case d'origine sort de l'ecran ; les collisions sont des blocs
 * invisibles ({@link BlocCollision}) poses seulement dans les cases d'air (jamais sur un bloc de la carte). Repere : celui
 * du modele (+z = vers la salle = {@code facing}), tourne comme l'etat de bloc (sud = 0, ouest = 90, nord = 180, est = 270).
 * <p>0.2.3 : collisions FINES ({@link CollisionsFines}, {@code collisions/<type>.json}) quand elles existent : chaque case
 * recoit sa forme exacte (marches, rambardes, pont), tournee selon {@code facing} et construite une fois au chargement
 * (table de formes indexee par {@link BlocCollision#FORME} ; 0..16 = anciennes formes). Pose : jamais de morceau saute en
 * silence (avertissement), jamais une case maitresse posee sur un morceau ou une collision posee pendant la meme
 * installation ({@link Session} : cases maitresses reservees d'abord, ordre fixe).
 */
public final class MachinesV3 {
    private MachinesV3() {
    }

    /** Type de machine (nom du modele dans le JSON). */
    public enum Type {
        COURANT("courant"), TP_PAD("tp_pad"), TP_PUPITRE("tp_pupitre"), CUVE_AMES("cuve_ames"), MF_PUPITRE("mf_pupitre"),
        MF_TOUR("mf_tour"), MF_PLATEFORME("mf_plateforme"), PAP_RIDEAU("pap_rideau");

        public final String id;

        Type(String id) {
            this.id = id;
        }
    }

    /**
     * Case de collision (repere du modele) et sa forme ({@link BlocCollision#FORME}) ; {@code fines} = index de la forme
     * fine par orientation (S, W, N, E), null pour une ancienne forme.
     */
    public record Cellule(int dx, int dy, int dz, int forme, @javax.annotation.Nullable int[] fines) {
        public int forme(Direction f) {
            return fines != null ? fines[f.get2DDataValue() & 3] : forme;
        }
    }

    /** Morceau cuit : index de {@link BlocPiece#PIECE} et decalage de sa case (repere du modele). */
    public record Piece(int index, int dx, int dy, int dz) {
    }

    private static final class Donnees {
        final VoxelShape[] hote = new VoxelShape[4];
        final List<Cellule> cellules = new ArrayList<>();
        final List<Piece> pieces = new ArrayList<>();
        boolean fines;
    }

    private static final EnumMap<Type, Donnees> DONNEES = new EnumMap<>(Type.class);
    private static int nombrePieces = 1;
    /** Formes des blocs de collision par forme (0 = demi-bloc du haut, n = boite du bas de n px). */
    private static final VoxelShape[] FORMES = new VoxelShape[17];
    /** Formes fines (0.2.3), index {@code 17 + i} de {@link BlocCollision#FORME}, et leur cle de deduplication. */
    private static final List<VoxelShape> FINES = new ArrayList<>();
    private static final java.util.Map<String, Integer> INDEX_FINES = new java.util.HashMap<>();
    /** Formes des morceaux cuits (index de morceau -> 4 orientations), null = sans collision. */
    private static VoxelShape[][] formesPieces = new VoxelShape[0][];

    static {
        FORMES[0] = Block.box(0, 8, 0, 16, 16, 16);
        for (int n = 1; n <= 16; n++) FORMES[n] = Block.box(0, 0, 0, 16, n, 16);
        for (Type t : Type.values()) {
            Donnees d = new Donnees();
            for (int i = 0; i < 4; i++) d.hote[i] = Shapes.empty();
            DONNEES.put(t, d);
        }
        charger();
    }

    private static void charger() {
        long t0 = System.nanoTime();
        try (InputStream in = MachinesV3.class.getResourceAsStream("/data/heralisz_matchs/machines_v3/machines.json")) {
            if (in == null) {
                HeraliszMatchs.LOG.warn("[zombies] machines_v3/machines.json absent : machines v3 sans collision");
                return;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            nombrePieces = Math.max(1, o.has("pieces") ? o.get("pieces").getAsInt() : 1);
            JsonObject ms = o.getAsJsonObject("machines");
            for (Type t : Type.values()) {
                JsonElement e = ms.get(t.id);
                if (e == null || !e.isJsonObject()) continue;
                JsonObject m = e.getAsJsonObject();
                Donnees d = DONNEES.get(t);
                JsonElement hb = m.get("hote_boite");
                int hf = m.has("hote") ? m.get("hote").getAsInt() : -1;
                for (Direction f : Direction.Plane.HORIZONTAL) {
                    VoxelShape s;
                    if (hb != null && hb.isJsonArray()) {
                        JsonArray a = hb.getAsJsonArray();
                        s = tourner(a.get(0).getAsDouble() + 8, a.get(1).getAsDouble(), a.get(2).getAsDouble() + 8, a.get(3).getAsDouble() + 8,
                                a.get(4).getAsDouble(), a.get(5).getAsDouble() + 8, f);
                    } else {
                        s = hf >= 0 && hf <= 16 ? FORMES[hf] : Shapes.empty();
                    }
                    d.hote[f.get2DDataValue()] = s;
                }
                for (JsonElement c : tableau(m, "cellules")) {
                    JsonArray a = c.getAsJsonArray();
                    d.cellules.add(new Cellule(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), Mth.clamp(a.get(3).getAsInt(), 0, 16), null));
                }
                for (JsonElement c : tableau(m, "pieces")) {
                    JsonArray a = c.getAsJsonArray();
                    d.pieces.add(new Piece(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()));
                }
            }
            HeraliszMatchs.LOG.debug("[zombies] machines v3 : {} morceaux, donnees lues en {} us", nombrePieces, (System.nanoTime() - t0) / 1000);
        } catch (Exception ex) {
            HeraliszMatchs.LOG.warn("[zombies] machines_v3/machines.json illisible : {}", ex.toString());
        }
        chargerFines();
    }

    /**
     * Collisions fines (0.2.3) : pour chaque type qui a son {@code collisions/<type>.json}, la case maitresse, les morceaux
     * et les autres cases prennent la forme exacte du modele ; les cellules de {@code machines.json} sont remplacees.
     */
    private static void chargerFines() {
        long t0 = System.nanoTime();
        formesPieces = new VoxelShape[Math.max(1, nombrePieces)][];
        int types = 0, cases = 0;
        for (Type t : Type.values()) {
            CollisionsFines.Modele m = CollisionsFines.modele(t.id);
            if (m == null) continue;
            Donnees d = DONNEES.get(t);
            d.fines = true;
            types++;
            VoxelShape[] h = m.case_(0, 0, 0);
            for (Direction f : Direction.Plane.HORIZONTAL) d.hote[f.get2DDataValue()] = h != null ? h[f.get2DDataValue()] : Shapes.empty();
            java.util.Set<BlockPos> casesPieces = new java.util.HashSet<>();
            for (Piece p : d.pieces) {
                BlockPos c = new BlockPos(p.dx(), p.dy(), p.dz());
                casesPieces.add(c);
                VoxelShape[] fp = m.formes().get(c);
                if (fp != null && p.index() >= 0 && p.index() < formesPieces.length) formesPieces[p.index()] = fp;
            }
            d.cellules.clear();
            for (java.util.Map.Entry<BlockPos, List<double[]>> e : m.boites().entrySet()) {
                BlockPos c = e.getKey();
                if (c.equals(BlockPos.ZERO) || casesPieces.contains(c) || e.getValue().isEmpty()) continue;
                VoxelShape[] fs = m.formes().get(c);
                String cle = CollisionsFines.cle(e.getValue());
                int[] idx = new int[4];
                for (int k = 0; k < 4; k++) {
                    final int kk = k;
                    idx[k] = INDEX_FINES.computeIfAbsent(cle + "#" + k, x -> {
                        FINES.add(fs[kk]);
                        return FORMES.length + FINES.size() - 1;
                    });
                }
                d.cellules.add(new Cellule(c.getX(), c.getY(), c.getZ(), 16, idx));
                cases++;
            }
        }
        if (types > 0) HeraliszMatchs.LOG.info("[zombies] collisions fines des machines : {} type(s), {} case(s), {} forme(s) en {} us", types, cases,
                FINES.size(), (System.nanoTime() - t0) / 1000);
    }

    /** Nombre de formes de {@link BlocCollision#FORME} (anciennes 0..16 + fines). */
    public static int nombreFormes() {
        return FORMES.length + FINES.size();
    }

    /** Forme d'un morceau cuit pour cette orientation (collisions fines), vide sinon. */
    public static VoxelShape formePiece(int index, Direction f) {
        VoxelShape[] s = index >= 0 && index < formesPieces.length ? formesPieces[index] : null;
        return s == null ? Shapes.empty() : s[f.getAxis() == Direction.Axis.Y ? 0 : f.get2DDataValue()];
    }

    private static Iterable<JsonElement> tableau(JsonObject o, String cle) {
        JsonElement e = o.get(cle);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    /** Nombre de morceaux cuits (taille de la propriete {@link BlocPiece#PIECE}). */
    public static int nombrePieces() {
        return nombrePieces;
    }

    /** Forme de la case maitresse d'une machine pour cette orientation. */
    public static VoxelShape forme(Type t, Direction f) {
        return DONNEES.get(t).hote[f.getAxis() == Direction.Axis.Y ? 0 : f.get2DDataValue()];
    }

    /** Forme d'un bloc de collision. */
    public static VoxelShape formeCollision(int forme) {
        if (forme >= FORMES.length) {
            int i = forme - FORMES.length;
            return i < FINES.size() ? FINES.get(i) : Shapes.block();
        }
        return FORMES[Mth.clamp(forme, 0, 16)];
    }

    public static List<Cellule> cellules(Type t) {
        return DONNEES.get(t).cellules;
    }

    public static List<Piece> pieces(Type t) {
        return DONNEES.get(t).pieces;
    }

    /** Case decalee de (dx, dy, dz) dans le repere du modele d'une machine tournee vers {@code f}. */
    public static BlockPos decaler(BlockPos o, Direction f, int dx, int dy, int dz) {
        return switch (f) {
            case WEST -> o.offset(-dz, dy, dx);
            case NORTH -> o.offset(-dx, dy, -dz);
            case EAST -> o.offset(dz, dy, -dx);
            default -> o.offset(dx, dy, dz);
        };
    }

    /** Boite (px, case de 0 a 16) definie pour une machine tournee vers le sud, tournee vers {@code f}. */
    static VoxelShape tourner(double x0, double y0, double z0, double x1, double y1, double z1, Direction f) {
        double[] xs = {x0, x1}, zs = {z0, z1};
        double mnx = 16, mnz = 16, mxx = 0, mxz = 0;
        for (double x : xs) {
            for (double z : zs) {
                double cx = x - 8, cz = z - 8, wx, wz;
                switch (f) {
                    case WEST -> {
                        wx = -cz;
                        wz = cx;
                    }
                    case NORTH -> {
                        wx = -cx;
                        wz = -cz;
                    }
                    case EAST -> {
                        wx = cz;
                        wz = -cx;
                    }
                    default -> {
                        wx = cx;
                        wz = cz;
                    }
                }
                mnx = Math.min(mnx, wx + 8);
                mxx = Math.max(mxx, wx + 8);
                mnz = Math.min(mnz, wz + 8);
                mxz = Math.max(mxz, wz + 8);
            }
        }
        return Block.box(Mth.clamp(mnx, 0, 16), Mth.clamp(y0, 0, 16), Mth.clamp(mnz, 0, 16), Mth.clamp(mxx, 0, 16),
                Mth.clamp(y1, 0, 16), Mth.clamp(mxz, 0, 16));
    }

    /**
     * Pose une machine : case maitresse ({@code hote}, deja orientee), morceaux cuits et collisions dans les cases d'air
     * (ou deja occupees par un ancien morceau ou bloc de collision). Rend le nombre de cases posees.
     */
    public static int poser(ServerLevel l, Type t, BlockPos pos, Direction f, BlockState hote, int drapeaux) {
        Session s = new Session();
        s.reserver(pos);
        return poser(l, t, pos, f, hote, drapeaux, s);
    }

    /**
     * Installation de plusieurs machines (0.2.3) : les cases maitresses (et l'ouverture du rideau) sont reservees AVANT toute
     * pose, et chaque case ecrite garde le nom de sa machine ; ainsi aucun morceau n'atterrit sur une case maitresse et
     * aucune case maitresse n'ecrase un morceau pose juste avant, quel que soit l'etat laisse par la partie precedente.
     */
    public static final class Session {
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet reserves = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<String> ecrites = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        /** Morceaux et collisions non poses (toutes machines). */
        public int sautes;

        public void reserver(@javax.annotation.Nullable BlockPos p) {
            if (p != null) reserves.add(p.asLong());
        }

        public void reserver(@javax.annotation.Nullable net.minecraft.world.level.levelgen.structure.BoundingBox b) {
            if (b == null) return;
            for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) reserves.add(p.asLong());
        }

        /** Machine qui a ecrit cette case pendant l'installation, ou null. */
        @javax.annotation.Nullable
        public String ecritePar(BlockPos p) {
            return ecrites.get(p.asLong());
        }

        void ecrite(BlockPos p, String machine) {
            ecrites.put(p.asLong(), machine);
        }
    }

    /**
     * Pose une machine dans une {@link Session} : morceaux, puis collisions, puis case maitresse. Une case refusee (bloc de
     * la carte, case maitresse reservee, case deja ecrite par une autre machine) n'est jamais remplacee : elle est comptee
     * et signalee dans le journal (avertissement, avec les cases), jamais sautee en silence.
     */
    public static int poser(ServerLevel l, Type t, BlockPos pos, Direction f, BlockState hote, int drapeaux, Session s) {
        long t0 = System.nanoTime();
        int n = 0;
        Donnees d = DONNEES.get(t);
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        List<String> refus = new ArrayList<>();
        int sautesP = 0, sautesC = 0;
        BlockState piece = ZbRegistres.PIECE.get().defaultBlockState().setValue(BlocPiece.FACING, f);
        for (Piece p : d.pieces) {
            if (p.index() >= nombrePieces) {
                sautesP++;
                refus.add("morceau " + p.index() + " hors de l'etat de bloc");
                continue;
            }
            c.set(decaler(pos, f, p.dx(), p.dy(), p.dz()));
            String raison = refus(l, s, c, pos, t, false);
            if (raison != null) {
                sautesP++;
                if (refus.size() < 12) refus.add("morceau " + p.index() + " en " + c.toShortString() + " (" + raison + ")");
                continue;
            }
            l.setBlock(c, piece.setValue(BlocPiece.PIECE, p.index()), drapeaux);
            s.ecrite(c, t.id);
            n++;
        }
        BlockState col = ZbRegistres.COLLISION.get().defaultBlockState();
        for (Cellule ce : d.cellules) {
            c.set(decaler(pos, f, ce.dx(), ce.dy(), ce.dz()));
            String raison = refus(l, s, c, pos, t, true);
            if (raison == null && l.getBlockState(c).getBlock() instanceof BlocPiece && s.ecritePar(c) != null) raison = "morceau de " + s.ecritePar(c);
            if (raison != null) {
                sautesC++;
                if (refus.size() < 12) refus.add("collision en " + c.toShortString() + " (" + raison + ")");
                continue;
            }
            int forme = ce.forme(f);
            l.setBlock(c, col.setValue(BlocCollision.FORME, Mth.clamp(forme, 0, nombreFormes() - 1)), drapeaux);
            s.ecrite(c, t.id);
            n++;
        }
        String avant = s.ecritePar(pos);
        if (avant != null && !avant.equals(t.id)) {
            HeraliszMatchs.LOG.warn("[zombies] machine {} : sa case maitresse {} remplace une case posee par {} (case non reservee)", t.id,
                    pos.toShortString(), avant);
        }
        l.setBlock(pos, hote, drapeaux);
        s.ecrite(pos, t.id);
        s.sautes += sautesP + sautesC;
        if (sautesP + sautesC > 0) {
            HeraliszMatchs.LOG.warn("[zombies] machine {} en {} ({}) : {} morceau(x) et {} collision(s) NON poses : {}", t.id, pos.toShortString(),
                    f.getName().toLowerCase(Locale.ROOT), sautesP, sautesC, String.join(" ; ", refus));
        }
        HeraliszMatchs.LOG.debug("[zombies] machine {} posee en {} ({}) : {} cases, {} us", t.id, pos.toShortString(),
                f.getName().toLowerCase(Locale.ROOT), n + 1, (System.nanoTime() - t0) / 1000);
        return n + 1;
    }

    /**
     * Raison de ne pas poser un morceau ou une collision dans cette case, ou null si elle est libre. Une collision peut
     * remplacer la collision d'une machine posee avant elle dans la meme installation : l'export (C) y met alors l'union
     * des deux formes ({@code cellules_unies}, ordre : pupitre, plateforme, tour) ; un morceau ne remplace jamais rien.
     */
    @javax.annotation.Nullable
    private static String refus(ServerLevel l, Session s, BlockPos c, BlockPos hote, Type t, boolean collision) {
        if (!c.equals(hote) && s.reserves.contains(c.asLong())) return "case maitresse reservee";
        String par = s.ecritePar(c);
        if (par != null && !par.equals(t.id) && !(collision && l.getBlockState(c).getBlock() instanceof BlocCollision)) return "deja posee par " + par;
        if (!libre(l, c)) return "bloc de la carte " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(c).getBlock());
        return null;
    }

    /** Case libre pour un morceau ou une collision : air, ou un de nos blocs (repose). */
    private static boolean libre(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.isAir() || s.getBlock() instanceof BlocCollision || s.getBlock() instanceof BlocPiece;
    }
}
