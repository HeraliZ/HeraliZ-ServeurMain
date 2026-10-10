package fr.heraliz.decors.bloc;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Grillage militaire (1.7.0, 11/10 : « qu'elles se relient au bloc d'à côté… des arrondis, comme les fences ») : se
 * raccorde aux grillages voisins dans les 8 directions. Droites est-ouest et nord-sud, diagonales à 45° (une diagonale
 * n'est prise que si aucun des deux voisins droits qu'elle enjambe n'est un grillage), angles. Poteau aux bouts, aux
 * angles, aux croisements, et en ligne droite tous les 3 blocs (tous les 2 en diagonale) : la même règle que la map
 * (fusion/heraliz-decors/tools/grillage.py, poteau_voulu). Deux blocs qu'on empile : grillage (maille), puis
 * grillage_haut (maille, main courante, rouleau de barbelés au-dessus ; collision de 1,5 bloc : on ne saute pas par-dessus).
 * Collisions fines qui suivent la maille (2 px), y compris en diagonale.
 */
public class Grillage extends Block {
    public static final BooleanProperty NORD = BlockStateProperties.NORTH, EST = BlockStateProperties.EAST,
            SUD = BlockStateProperties.SOUTH, OUEST = BlockStateProperties.WEST;
    public static final BooleanProperty NORD_EST = BooleanProperty.create("nord_est"), SUD_EST = BooleanProperty.create("sud_est"),
            SUD_OUEST = BooleanProperty.create("sud_ouest"), NORD_OUEST = BooleanProperty.create("nord_ouest");
    public static final BooleanProperty POTEAU = BooleanProperty.create("poteau");

    private final boolean haut;
    private final Map<BlockState, VoxelShape> formes = new HashMap<>(), collisions = new HashMap<>();

    public Grillage(Properties p, boolean haut) {
        super(p);
        this.haut = haut;
        registerDefaultState(stateDefinition.any().setValue(NORD, false).setValue(EST, false).setValue(SUD, false)
                .setValue(OUEST, false).setValue(NORD_EST, false).setValue(SUD_EST, false).setValue(SUD_OUEST, false)
                .setValue(NORD_OUEST, false).setValue(POTEAU, true));
        for (BlockState s : stateDefinition.getPossibleStates()) {
            formes.put(s, forme(s, 16));
            collisions.put(s, forme(s, haut ? 24 : 16));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORD, EST, SUD, OUEST, NORD_EST, SUD_EST, SUD_OUEST, NORD_OUEST, POTEAU);
    }

    // ------------------------------------------------------------------------------------------ formes
    private static VoxelShape boite(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    /** Maille de 2 px d'épaisseur le long de chaque branche (les diagonales en petites boîtes jointives). */
    private static VoxelShape forme(BlockState s, double h) {
        VoxelShape v = boite(7, 0, 7, 9, h, 9);
        if (s.getValue(NORD)) v = Shapes.or(v, boite(7, 0, 0, 9, h, 9));
        if (s.getValue(SUD)) v = Shapes.or(v, boite(7, 0, 7, 9, h, 16));
        if (s.getValue(EST)) v = Shapes.or(v, boite(7, 0, 7, 16, h, 9));
        if (s.getValue(OUEST)) v = Shapes.or(v, boite(0, 0, 7, 9, h, 9));
        int[][] diag = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        BooleanProperty[] pr = {NORD_EST, SUD_EST, SUD_OUEST, NORD_OUEST};
        for (int k = 0; k < 4; k++) {
            if (!s.getValue(pr[k])) continue;
            for (double t = 0; t <= 7.01; t += 1.0) {                   // du milieu jusqu'au coin, sans trou
                double cx = 8 + diag[k][0] * (t + 0.5), cz = 8 + diag[k][1] * (t + 0.5);
                v = Shapes.or(v, boite(Math.max(0, cx - 1.5), 0, Math.max(0, cz - 1.5), Math.min(16, cx + 1.5), h, Math.min(16, cz + 1.5)));
            }
        }
        return v.optimize();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return formes.get(s);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return collisions.get(s);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return false;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    // ------------------------------------------------------------------------------------------ raccords
    private static boolean grillage(BlockGetter l, BlockPos p) {
        return l.getBlockState(p).getBlock() instanceof Grillage;
    }

    /** État du grillage en p d'après ses voisins (la map pose les mêmes états : tools/grillage.py). */
    public static BlockState etat(BlockGetter l, BlockPos p, BlockState base) {
        boolean n = grillage(l, p.north()), e = grillage(l, p.east()), s = grillage(l, p.south()), w = grillage(l, p.west());
        boolean ne = !n && !e && grillage(l, p.north().east()), se = !s && !e && grillage(l, p.south().east());
        boolean sw = !s && !w && grillage(l, p.south().west()), nw = !n && !w && grillage(l, p.north().west());
        int x = p.getX(), z = p.getZ();
        boolean poteau;
        if (e && w && !n && !s && !ne && !se && !sw && !nw) poteau = Math.floorMod(x, 3) == 0;
        else if (n && s && !e && !w && !ne && !se && !sw && !nw) poteau = Math.floorMod(z, 3) == 0;
        else if (!n && !e && !s && !w && ((ne && sw && !nw && !se) || (nw && se && !ne && !sw))) poteau = Math.floorMod(x, 2) == 0;
        else poteau = true;
        return base.setValue(NORD, n).setValue(EST, e).setValue(SUD, s).setValue(OUEST, w).setValue(NORD_EST, ne)
                .setValue(SUD_EST, se).setValue(SUD_OUEST, sw).setValue(NORD_OUEST, nw).setValue(POTEAU, poteau);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return etat(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState s, Direction d, BlockState voisin, LevelAccessor l, BlockPos p, BlockPos pv) {
        return d.getAxis().isHorizontal() ? etat(l, p, s) : s;
    }

    /** Les voisins en diagonale ne sont pas prévenus par le jeu : on les met à jour nous-mêmes. */
    private static void diagonales(Level l, BlockPos p) {
        for (BlockPos q : new BlockPos[]{p.north().east(), p.south().east(), p.south().west(), p.north().west()}) {
            BlockState s = l.getBlockState(q);
            if (s.getBlock() instanceof Grillage) {
                BlockState n = etat(l, q, s);
                if (n != s) l.setBlock(q, n, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState ancien, boolean deplace) {
        super.onPlace(s, l, p, ancien, deplace);
        if (!l.isClientSide && !ancien.is(this)) diagonales(l, p);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nouveau, boolean deplace) {
        super.onRemove(s, l, p, nouveau, deplace);
        if (!l.isClientSide && !nouveau.is(this)) diagonales(l, p);
    }

    public boolean haut() {
        return haut;
    }
}
