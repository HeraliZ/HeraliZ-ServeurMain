package fr.heraliz.crac.bases;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import fr.heraliz.crac.block.MetalCrateBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.logic.PlacementRules;
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
    static final int PLAN = 1, FONDATION_1 = 2, FONDATION_2 = 3, MURS = 4, TOIT = 5, BOIS = 6, CLAIM = 7, ENTRETIEN = 8, COFFRE = 9;
    static final int ETAPES = 9;
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
                PieceKind k = af >= 0 && i == (af + 2) % 4 ? PieceKind.DOORWAY : PieceKind.WALL;
                murs.add(Topology.wallOn(f.key(), i, k, top, 0));
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
        if (c == null) {
            return new Etat(CLAIM, 0, 1, List.of(), List.of(surFondation(f)), ModItems.CLAIM_BLOCK.get(), -1, false);
        }
        int seuil = Math.max(1, Tutoriel.entretienKit(pieces.size()) / 2);
        int stock = bases.getBlockEntity(c.anchor()) instanceof ClaimBlockEntity be ? be.getStored(UpkeepResource.WOOD.slot()) : 0;
        if (stock < seuil) {
            return new Etat(ENTRETIEN, Math.min(stock, seuil), seuil, List.of(), List.of(c.anchor()), null, -1, false);
        }

        // la caisse en métal, dans la cabane
        if (!caisseDans(bases, pieces)) {
            return new Etat(COFFRE, 0, 1, List.of(), List.of(surFondation(s)), ModItems.METAL_CRATE.get(), -1, false);
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
    private static int areteCommune(Piece a, Piece b) {
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
