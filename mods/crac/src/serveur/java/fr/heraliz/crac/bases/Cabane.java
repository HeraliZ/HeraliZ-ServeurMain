package fr.heraliz.crac.bases;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import fr.heraliz.crac.block.MetalCrateBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.LevelEnv;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.UpkeepResource;
import fr.heraliz.crac.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * La cabane du tutoriel des bases (2 × 1, une porte), pièce par pièce : où en est le joueur et ce qu'il doit poser, à
 * quel endroit exact. Tout est relu sur la cabane elle-même (rien n'est cru du joueur) :
 * <ol>
 *   <li>prendre le plan de construction en main ;</li>
 *   <li>la première fondation, sur un coin plat ;</li>
 *   <li>la deuxième, collée à la première, sur le côté choisi à la pose de la première ({@code arete}) ;</li>
 *   <li>les 5 murs et la porte, sur le tour des deux fondations (la porte : sur le petit côté de la première) ;</li>
 *   <li>les 2 planchers du toit, sur les murs ;</li>
 *   <li>toute la cabane en bois (marteau) ;</li>
 *   <li>le Claim Block, sur la première fondation (la règle de CRac « un Claim Block avant de construire plus » est
 *   levée pour la cabane du tutoriel, {@link Tutoriel#sansClaimPermis}) ;</li>
 *   <li>des planches dans le Claim Block (entretien) ;</li>
 *   <li>la caisse en métal, sur la deuxième fondation.</li>
 * </ol>
 */
final class Cabane {
    static final int PLAN = 1, FONDATION_1 = 2, FONDATION_2 = 3, MURS = 4, TOIT = 5, BOIS = 6, CLAIM = 7, ENTRETIEN = 8, COFFRE = 9,
        HOME = 10;
    static final int ETAPES = 10;
    static final int FINIE = ETAPES + 1;

    /**
     * Où en est la cabane. {@code cibles} : pièces à poser (ou à passer en bois) à leur place exacte ; {@code blocs} :
     * blocs à montrer (Claim Block, caisse) ; {@code objet} : l'objet à prendre en main ; {@code roue} : la pièce à
     * choisir dans la roue du plan (-1 : rien) ; {@code bois} : le choix « Bois » à montrer dans la roue du marteau.
     */
    record Etat(int etape, int fait, int total, List<PieceKey> cibles, List<BlockPos> blocs, @Nullable Item objet, int roue,
                boolean bois) {
        static final Etat HORS = new Etat(0, 0, 0, List.of(), List.of(), null, -1, false);
    }

    private Cabane() {
    }

    static Etat etat(ServerPlayer p, BasesDonnees.Joueur j, @Nullable Building b) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return Etat.HORS;
        }
        StructureStore store = StructureService.store(bases);
        List<Piece> pieces = b == null ? List.of() : store.piecesOf(b);
        List<Piece> fondations = new ArrayList<>();
        for (Piece x : pieces) {
            if (x.kind() == PieceKind.FOUNDATION) {
                fondations.add(x);
            }
        }
        fondations.sort(Comparator.comparingLong(x -> x.id));
        Item plan = ModItems.BUILDING_PLAN.get();
        if (fondations.isEmpty()) {
            return p.getMainHandItem().is(plan)
                ? new Etat(FONDATION_1, 0, 1, List.of(), List.of(), plan, PieceKind.FOUNDATION.ordinal(), false)
                : new Etat(PLAN, 0, 1, List.of(), List.of(), plan, -1, false);
        }
        Piece f = fondations.get(0);
        if (fondations.size() < 2) {
            List<PieceKey> c = j.arete >= 0 && j.arete < 4
                ? List.of(Topology.across(f.key(), j.arete, PieceKind.FOUNDATION, f.key().h())) : List.of();
            return new Etat(FONDATION_2, 1, 2, c, List.of(), plan, PieceKind.FOUNDATION.ordinal(), false);
        }
        Piece s = fondations.get(1);
        int af = areteCommune(f, s);
        int as = areteCommune(s, f);
        int top = f.key().top();

        // les murs : le tour des deux fondations, la porte sur le petit côté de la première
        List<PieceKey> murs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i != af) {
                if (af >= 0 && i == (af + 2) % 4) {
                    // la porte de la cabane s'ouvre vers l'extérieur (demande du propriétaire, 18/10)
                    murs.add(versExterieur(Topology.wallOn(f.key(), i, PieceKind.DOORWAY, top, 0), f.key().centre()));
                } else {
                    murs.add(Topology.wallOn(f.key(), i, PieceKind.WALL, top, 0));
                }
            }
            if (i != as) {
                murs.add(Topology.wallOn(s.key(), i, PieceKind.WALL, top, 0));
            }
        }
        List<PieceKey> manquants = manquants(pieces, murs);
        if (!manquants.isEmpty()) {
            return new Etat(MURS, murs.size() - manquants.size(), murs.size(), manquants, List.of(), plan,
                manquants.get(0).kind().ordinal(), false);
        }

        // le toit : un plancher sur chaque fondation, sur les murs
        int haut = murs.get(0).top();
        List<PieceKey> toit = List.of(
            PieceKey.on(PieceKind.FLOOR, f.key().origin(), f.key().footprint(), haut, 0),
            PieceKey.on(PieceKind.FLOOR, s.key().origin(), s.key().footprint(), haut, 0));
        manquants = manquants(pieces, toit);
        if (!manquants.isEmpty()) {
            return new Etat(TOIT, toit.size() - manquants.size(), toit.size(), manquants, List.of(), plan,
                PieceKind.FLOOR.ordinal(), false);
        }

        // toute la cabane en bois
        List<PieceKey> brindilles = new ArrayList<>();
        for (Piece x : pieces) {
            if (x.tier.ordinal() < Tier.WOOD.ordinal()) {
                brindilles.add(x.key());
            }
        }
        if (!brindilles.isEmpty()) {
            return new Etat(BOIS, pieces.size() - brindilles.size(), pieces.size(), brindilles, List.of(),
                ModItems.BUILDING_HAMMER.get(), -1, true);
        }

        // le Claim Block, puis l'entretien
        Claim c = StructureService.claimOf(bases, b);
        Coin coin = coin(f, s);
        if (c == null) {
            // dans l'angle du fond de la cabane (la deuxième fondation ; la porte est sur la première)
            return new Etat(CLAIM, 0, 1, List.of(), List.of(coin.vueClaim(), coin.vueClaim().above()), ModItems.CLAIM_BLOCK.get(), -1, false);
        }
        int seuil = Math.max(1, Tutoriel.entretienKit(pieces.size()) / 2);
        int stock = bases.getBlockEntity(c.anchor()) instanceof ClaimBlockEntity be ? be.getStored(UpkeepResource.WOOD.slot()) : 0;
        if (stock < seuil) {
            BlockPos vue = vue(bases, c.anchor());
            return new Etat(ENTRETIEN, Math.min(stock, seuil), seuil, List.of(), List.of(vue, vue.above()), null, -1, false);
        }

        // la caisse en métal, dans la cabane
        if (!caisseDans(bases, pieces)) {
            return new Etat(COFFRE, 0, 1, List.of(), List.of(coin.vueCaisse()), ModItems.METAL_CRATE.get(), -1, false);
        }

        // son home, posé par lui-même (/sethome) sur un sol de la cabane
        if (!homeDans(bases, p, j)) {
            return new Etat(HOME, 0, 1, List.of(), List.of(), null, -1, false);
        }
        return new Etat(FINIE, 1, 1, List.of(), List.of(), null, -1, false);
    }

    /** Les pièces de {@code voulues} pas encore posées (même genre, même place, même hauteur ; le sens de la porte ne compte pas). */
    private static List<PieceKey> manquants(List<Piece> pieces, List<PieceKey> voulues) {
        List<PieceKey> out = new ArrayList<>();
        for (PieceKey v : voulues) {
            boolean posee = false;
            for (Piece x : pieces) {
                if (memePlace(x.key(), v)) {
                    posee = true;
                    break;
                }
            }
            if (!posee) {
                out.add(v);
            }
        }
        return out;
    }

    /** Même genre de pièce, même place, même hauteur (la rotation, sens d'ouverture d'une porte, ne compte pas). */
    static boolean memePlace(PieceKey a, PieceKey b) {
        if (a.kind() != b.kind() || a.h() != b.h()) {
            return false;
        }
        return a.kind().isWall() ? a.edgeKey().equals(b.edgeKey()) : a.footprint().equals(b.footprint());
    }

    /** L'arête de {@code a} que touche {@code b} (0 à 3), -1 si elles ne se touchent pas. */
    static int areteCommune(Piece a, Piece b) {
        for (int i = 0; i < 4; i++) {
            PieceKey voisin = Topology.across(a.key(), i, PieceKind.FOUNDATION, a.key().h());
            if (voisin.footprint().equals(b.key().footprint())) {
                return i;
            }
        }
        return -1;
    }

    /** Le bloc où se pose quelque chose au milieu d'une fondation (juste au-dessus de son sol). */
    static BlockPos surFondation(Piece f) {
        Vec3 c = f.key().centreWorld();
        int y = f.key().origin().getY() + Math.floorDiv(24 * f.key().top(), 16);
        return BlockPos.containing(c.x, y, c.z);
    }

    /**
     * Fin du tutoriel : Varga ajoute un sas devant la porte, comme dans Rust : une fondation triangle contre l'entrée,
     * avec son plancher en toit, tout en bois. En sortant par la porte de la cabane (ouverte vers l'extérieur, donc dans
     * le sas), un mur à droite et une deuxième porte à gauche, qui s'ouvre vers l'intérieur du sas (demande du
     * propriétaire, 18/10). Vrai si le sas est posé (le terrain peut l'empêcher : rien n'est posé alors).
     */
    static boolean sas(ServerLevel level, ServerPlayer p, Building b) {
        Piece[] fs = fondations(level, b);
        if (fs == null) {
            return false;
        }
        Piece f = fs[0];
        int af = areteCommune(f, fs[1]);
        if (af < 0) {
            return false;
        }
        int bordPorte = (af + 2) % 4;
        PieceKey tri = Topology.across(f.key(), bordPorte, PieceKind.TRI_FOUNDATION, f.key().h());
        if (!StructureService.placeAdmin(level, tri, Tier.WOOD, p.getUUID()).ok()) {
            return false;
        }
        int top = f.key().top();
        Vec3 fc = f.key().centre(), tc = tri.centre();
        // la porte de la cabane : vers l'extérieur, c'est-à-dire dans le sas
        Piece porte = null;
        fr.heraliz.crac.build.model.EdgeKey entree = f.key().edges().get(bordPorte);
        for (Piece x : StructureService.store(level).piecesOf(b)) {
            if (x.kind() == PieceKind.DOORWAY && bord(x.key()).equals(entree)) {
                porte = x;
            }
        }
        if (porte != null && !ouvreVers(porte.key(), tc)) {
            remplacer(level, porte, versExterieur(porte.key(), fc));
        }
        // les deux autres côtés du triangle, vus en sortant (de la cabane vers le sas) : gauche = porte, droite = mur
        double dx = tc.x - fc.x, dz = tc.z - fc.z;
        int gauche = -1, droite = -1;
        fr.heraliz.crac.build.model.LPoint[] v = tri.lattice();
        for (int e = 0; e < 3; e++) {
            if (tri.edges().get(e).equals(entree)) {
                continue;
            }
            double mx = (v[e].toX() + v[(e + 1) % 3].toX()) / 2 - tc.x, mz = (v[e].toZ() + v[(e + 1) % 3].toZ()) / 2 - tc.z;
            if (dz * mx - dx * mz > 0) {
                gauche = e;
            } else {
                droite = e;
            }
        }
        if (gauche < 0 || droite < 0) {
            return true;
        }
        StructureService.placeAdmin(level, Topology.wallOn(tri, droite, PieceKind.WALL, top, 0), Tier.WOOD, p.getUUID());
        // la deuxième porte s'ouvre dans le sas : le premier sens (charnière) qui tient à côté de la première porte
        for (int rot = 0; rot < 4; rot++) {
            PieceKey k = Topology.wallOn(tri, gauche, PieceKind.DOORWAY, top, rot);
            if (ouvreVers(k, tc) && StructureService.placeAdmin(level, k, Tier.WOOD, p.getUUID()).ok()) {
                break;
            }
        }
        StructureService.placeAdmin(level, PieceKey.on(PieceKind.TRI_FLOOR, tri.origin(), tri.footprint(), Topology.wallOn(tri, droite,
            PieceKind.WALL, top, 0).top(), 0), Tier.WOOD, p.getUUID());
        return true;
    }

    /** Le joueur a un home dans sa base (posé avec /sethome). */
    private static boolean homeDans(ServerLevel level, ServerPlayer p, BasesDonnees.Joueur j) {
        for (BasesDonnees.Home h : j.homes.values()) {
            if (MondeBases.ID.equals(h.monde()) && Deplacements.baseEn(level, BlockPos.containing(h.x(), h.y(), h.z()), p.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** Le fond de la cabane : la direction de la première fondation (porte) vers la deuxième. */
    private static Direction fond(Piece f, Piece s) {
        Vec3 fc = f.key().centreWorld(), sc = s.key().centreWorld();
        double dx = sc.x - fc.x, dz = sc.z - fc.z;
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /**
     * L'angle du fond de la cabane (CRac 1.11.0, demande du propriétaire du 18/10) : l'armoire dans l'angle du fond à
     * droite (vu depuis la porte), dos au mur de droite, et la caisse juste à côté contre le même mur, vers la porte.
     * Les murs occupent les cases du bord des fondations : l'armoire est posée dans la case du milieu de la deuxième
     * fondation et la caisse dans celle d'à côté (vers la porte), toutes deux poussées vers le mur ({@code Adosse}).
     * {@code vueClaim}, {@code vueCaisse} : où elles se voient (les cases vertes du guide).
     */
    record Coin(BlockPos claim, BlockPos caisse, Direction face, int adosse, BlockPos vueClaim, BlockPos vueCaisse) {
    }

    static Coin coin(Piece f, Piece s) {
        Direction d = fond(f, s), droite = d.getClockWise();
        BlockPos claim = surFondation(s);
        BlockPos caisse = claim.relative(d.getOpposite());
        int v = fr.heraliz.crac.block.Adosse.code(droite.getStepX() + d.getStepX(), droite.getStepZ() + d.getStepZ());
        return new Coin(claim, caisse, droite.getOpposite(), v, claim.relative(d).relative(droite), claim.relative(droite));
    }

    /** Les deux premières fondations de la cabane (la porte est sur la première), sinon null. */
    @Nullable
    static Piece[] fondations(ServerLevel level, Building b) {
        List<Piece> out = new ArrayList<>();
        for (Piece x : StructureService.store(level).piecesOf(b)) {
            if (x.kind() == PieceKind.FOUNDATION) {
                out.add(x);
            }
        }
        if (out.size() < 2) {
            return null;
        }
        out.sort(Comparator.comparingLong(x -> x.id));
        return new Piece[]{out.get(0), out.get(1)};
    }

    /** La case où se voit un objet adossé posé en {@code pos} (la case de mur vers laquelle il est poussé). */
    static BlockPos vue(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
        if (!s.hasProperty(fr.heraliz.crac.block.Adosse.ADOSSE)) {
            return pos;
        }
        int v = s.getValue(fr.heraliz.crac.block.Adosse.ADOSSE);
        return pos.offset(fr.heraliz.crac.block.Adosse.sx(v), 0, fr.heraliz.crac.block.Adosse.sz(v));
    }

    /**
     * Tourne l'armoire ou la caisse du tutoriel comme prévu ({@link #coin}) si elle est à sa place : face à la pièce,
     * poussée dans l'angle. Rien si la place n'est pas libre (murs différents, autre objet).
     */
    static void orienter(ServerLevel level, BlockPos pos, Direction face, int adosse) {
        net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof fr.heraliz.crac.block.Adosse.Adossable) || !s.hasProperty(fr.heraliz.crac.block.Adosse.ADOSSE)) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState voulu = s.setValue(fr.heraliz.crac.block.ClaimBlock.FACING, face)
            .setValue(fr.heraliz.crac.block.Adosse.ADOSSE, adosse);
        if (voulu == s || !fr.heraliz.crac.block.Adosse.tient(level, pos, voulu)) {
            return;
        }
        level.setBlock(pos, voulu, net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (voulu.getBlock() instanceof fr.heraliz.crac.block.ClaimBlock) {
            fr.heraliz.crac.block.ClaimBlock.poserHaut(level, pos, voulu);
        }
    }

    // ---------------------------------------------------------------- portes

    /** Centre (x, z en px du bâtiment) de la zone où s'ouvre une porte. */
    private static double[] ouverture(PieceKey k) {
        double x = 0, z = 0;
        int n = 0;
        for (fr.heraliz.crac.build.geom.Prism pr : fr.heraliz.crac.build.geom.PieceSolids.swing(k)) {
            for (double[] v : pr.poly()) {
                x += v[0];
                z += v[1];
                n++;
            }
        }
        return n == 0 ? null : new double[]{x / n, z / n};
    }

    /** La porte s'ouvre du côté du point {@code cote} (px du bâtiment). */
    static boolean ouvreVers(PieceKey porte, Vec3 cote) {
        double[] o = ouverture(porte);
        if (o == null) {
            return false;
        }
        Vec3 m = porte.centre();
        return (o[0] - m.x) * (cote.x - m.x) + (o[1] - m.z) * (cote.z - m.z) > 0;
    }

    /** La même porte, ouverte du côté opposé à {@code dedans} (px du bâtiment) ; la charnière ne bouge pas. */
    static PieceKey versExterieur(PieceKey porte, Vec3 dedans) {
        return ouvreVers(porte, dedans) ? porte.withRot(porte.rot() ^ 1) : porte;
    }

    /**
     * La porte de la cabane posée par le joueur s'ouvre toujours vers l'extérieur (tutoriel) : remise dans l'autre sens
     * si besoin (retirée puis reposée telle quelle, sans coût). Appelé un tick après la pose.
     */
    static void porteVersExterieur(ServerLevel level, long id) {
        StructureStore store = StructureService.store(level);
        Piece porte = store.index.piece(id);
        Building b = porte == null || porte.kind() != PieceKind.DOORWAY ? null : store.index.building(porte.buildingId);
        Piece[] fs = b == null ? null : fondations(level, b);
        if (fs == null || !fs[0].key().edges().contains(bord(porte.key()))) {
            return;
        }
        remplacer(level, porte, versExterieur(porte.key(), fs[0].key().centre()));
    }

    private static fr.heraliz.crac.build.model.EdgeKey bord(PieceKey k) {
        return fr.heraliz.crac.build.model.EdgeKey.of(k.originKey(), k.anchor(), k.orient());
    }

    /** Remplace une porte par la même à un autre sens ; remise telle quelle si le nouveau sens ne tient pas. */
    private static boolean remplacer(ServerLevel level, Piece porte, PieceKey neuf) {
        if (neuf.equals(porte.key())) {
            return true;
        }
        Tier tier = porte.tier;
        java.util.UUID owner = porte.owner;
        PieceKey avant = porte.key();
        StructureService.remove(level, porte, StructureService.Cause.ADMIN);
        if (StructureService.placeAdmin(level, neuf, tier, owner).ok()) {
            return true;
        }
        StructureService.placeAdmin(level, avant, tier, owner);
        return false;
    }

    /** Une caisse en métal est posée dans la cabane (au-dessus de ses fondations). */
    private static boolean caisseDans(ServerLevel level, List<Piece> pieces) {
        AABB zone = null;
        for (Piece x : pieces) {
            if (x.kind() == PieceKind.FOUNDATION) {
                AABB box = x.bounds();
                zone = zone == null ? box : zone.minmax(box);
            }
        }
        if (zone == null) {
            return false;
        }
        for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(zone.minX, zone.minY, zone.minZ),
            BlockPos.containing(zone.maxX, zone.maxY + 3, zone.maxZ))) {
            if (level.getBlockState(q).getBlock() instanceof MetalCrateBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Le côté de la première fondation où ira la deuxième : celui où elle peut se poser, de préférence devant le
     * joueur (son regard). -1 si aucun ne va (la deuxième pourra alors se coller sur n'importe quel côté).
     */
    static int choisirArete(ServerPlayer p, Piece f) {
        ServerLevel level = p.serverLevel();
        StructureStore store = StructureService.store(level);
        float lacet = (float) Math.toRadians(p.getYRot());
        double lx = -Math.sin(lacet), lz = Math.cos(lacet);
        int best = -1;
        double bestScore = -10;
        for (int i = 0; i < 4; i++) {
            PieceKey k = Topology.across(f.key(), i, PieceKind.FOUNDATION, f.key().h());
            PlacementRules.Plan plan = PlacementRules.check(store.index, new LevelEnv(level), k, BuildConfig.stability(),
                BuildConfig.foundationMaxHeight.get());
            if (!plan.ok()) {
                continue;
            }
            // vers l'extérieur de l'arête i : sa direction moins 90° (+3 pas = vers l'intérieur)
            int dehors = f.key().edgeDir(i) - 3;
            double score = Dirs.ex(dehors) * lx + Dirs.ez(dehors) * lz;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
