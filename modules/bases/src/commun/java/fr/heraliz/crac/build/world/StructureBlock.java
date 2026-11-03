package fr.heraliz.crac.build.world;

import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.pont.Serveur;
import fr.heraliz.crac.build.geom.Footprints;
import fr.heraliz.crac.build.geom.Poly;
import fr.heraliz.crac.build.geom.ShapeRaster;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The single block every building piece is made of. Shape comes from the block entity's occupants; the six
 * boolean properties say which faces are fully closed so that light is blocked by floors and walls.
 */
public class StructureBlock extends BaseEntityBlock {
    public static final BooleanProperty[] FACE = new BooleanProperty[6];
    private static final VoxelShape[] OCCLUSION = new VoxelShape[64];

    static {
        for (Direction d : Direction.values()) {
            FACE[d.get3DDataValue()] = BooleanProperty.create("occ_" + d.getName());
        }
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape s = Shapes.empty();
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.get3DDataValue())) == 0) continue;
                double t = 1.0 / 16.0;
                VoxelShape face = switch (d) {
                    case DOWN -> Shapes.box(0, 0, 0, 1, t, 1);
                    case UP -> Shapes.box(0, 1 - t, 0, 1, 1, 1);
                    case NORTH -> Shapes.box(0, 0, 0, 1, 1, t);
                    case SOUTH -> Shapes.box(0, 0, 1 - t, 1, 1, 1);
                    case WEST -> Shapes.box(0, 0, 0, t, 1, 1);
                    case EAST -> Shapes.box(1 - t, 0, 0, 1, 1, 1);
                };
                s = Shapes.or(s, face);
            }
            OCCLUSION[mask] = mask == 63 ? Shapes.block() : s;
        }
    }

    public StructureBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(-1.0F, 3600000.0F)
            .noLootTable()
            .pushReaction(PushReaction.BLOCK)
            .isRedstoneConductor((s, l, p) -> false)
            .isSuffocating((s, l, p) -> false)
            .isViewBlocking((s, l, p) -> false)
            .forceSolidOn()
            .dynamicShape()
            .sound(SoundType.WOOD));
        BlockState def = this.stateDefinition.any();
        for (BooleanProperty p : FACE) {
            def = def.setValue(p, false);
        }
        this.registerDefaultState(def);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACE);
    }

    public static int faceMask(BlockState state) {
        int mask = 0;
        for (int i = 0; i < 6; i++) {
            if (state.getValue(FACE[i])) mask |= 1 << i;
        }
        return mask;
    }

    public BlockState withFaces(int mask) {
        BlockState s = this.defaultBlockState();
        for (int i = 0; i < 6; i++) {
            s = s.setValue(FACE[i], (mask & (1 << i)) != 0);
        }
        return s;
    }

    /** Block state matching the faces closed by the given shape. */
    public BlockState stateFor(VoxelShape shape) {
        return this.withFaces(ShapeRaster.fullFaces(shape));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructureBlockEntity(pos, state);
    }

    /** Footsteps, hits and falls sound like the tier of the piece in this block (see {@link PieceSounds}). */
    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        if (level.getBlockEntity(pos) instanceof StructureBlockEntity be) {
            Tier tier = PieceSounds.tierOf(be.occupants(), pos, entity != null ? entity.position() : null);
            if (tier != null) {
                return PieceSounds.of(tier);
            }
        }
        return super.getSoundType(state, level, pos, entity);
    }

    private static VoxelShape shapeAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StructureBlockEntity be ? be.shape() : Shapes.empty();
    }

    /** Visée : la pièce, plus ce qu'un objet adossé voisin (armoire, caisse) a dans cette case (Adosse, 1.11.0). */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof StructureBlockEntity be) {
            return be.avecObjets(level, true);   // gardée (StructureBlockEntity#avecObjets)
        }
        return fr.heraliz.crac.block.Adosse.morceaux(level, pos);
    }

    /** Visée sur un objet adossé : la case du mur n'est jamais retirée (le serveur casse l'objet, AdosseCasse). */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
                                       net.minecraft.world.level.material.FluidState fluid) {
        if (fr.heraliz.crac.block.Adosse.vise(level, pos, player, shapeAt(level, pos)) != null) {
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Casser l'objet adossé visé dans cette case revient à casser l'objet (le mur, lui, ne se casse pas à la main). */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        fr.heraliz.crac.block.Adosse.Voisin o = fr.heraliz.crac.block.Adosse.vise(level, pos, player, shapeAt(level, pos));
        return o != null ? o.state().getDestroyProgress(player, level, o.pos()) : super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * Collision : la pièce, plus la partie d'un objet adossé voisin (caisse en métal, armoire) qui dépasse dans cette case
     * (20/10 : on traversait les caisses, leur forme poussée contre le mur sortait de leur propre case et Minecraft ne
     * la cherchait pas là ; on peut maintenant monter dessus).
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof StructureBlockEntity be) {
            return be.avecObjets(level, false);   // gardée (StructureBlockEntity#avecObjets)
        }
        return fr.heraliz.crac.block.Adosse.morceaux(level, pos);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return OCCLUSION[faceMask(state)];
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return faceMask(state) == 63 ? 15 : 0;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(FACE[Direction.UP.get3DDataValue()]) && !state.getValue(FACE[Direction.DOWN.get3DDataValue()]);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // clic sur la partie d'un objet adossé (armoire, caisse) qui est dans cette case : c'est l'objet qu'on utilise
        fr.heraliz.crac.block.Adosse.Voisin objet = fr.heraliz.crac.block.Adosse.contenant(level, pos, hit.getLocation());
        if (objet != null) {
            BlockHitResult h = new BlockHitResult(hit.getLocation(), hit.getDirection(), objet.pos(), hit.isInside());
            if (!level.isClientSide()) {
                var ev = net.minecraftforge.common.ForgeHooks.onRightClickBlock(player, hand, objet.pos(), h);
                if (ev.isCanceled() || ev.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY) {
                    return InteractionResult.FAIL;   // mêmes règles que sur l'objet lui-même (base protégée, droit d'ouvrir)
                }
            }
            return objet.state().use(level, player, hand, h);
        }
        if (level.isClientSide()) {   // prédiction (corps client d'origine de StructureService.onUse)
            for (Piece p : ClientMirror.view().occupants(pos)) {
                if (p.kind().hasDoor() || p.kind() == PieceKind.WINDOW) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }
        Serveur.Moteur m = Serveur.MOTEUR;
        return m != null ? m.structureUtiliser(level, pos, player, hand, hit) : InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ standing on a piece

    /** Why the top of a structure block crossed by a walkable surface cannot hold a block (placable, Claim Block). */
    public enum StandIssue {
        /** A floor half a wall up (odd h): its top is mid-block, a block above it would float. */
        HALF_LEVEL,
        /** The surfaces flush with the block's top cover only part of it (a rotated or triangle edge cuts it). */
        PARTLY_COVERED
    }

    /** {@link #standIssue(List, BlockPos)} of the structure block at {@code pos}; null for any other block. */
    @Nullable
    public static StandIssue standIssue(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StructureBlockEntity be ? standIssue(be.occupants(), pos) : null;
    }

    /**
     * Why the walkable surfaces among {@code occupants} of block {@code pos} cannot hold a block on top of it (§7.5):
     * one has its top inside the block (a floor on half walls), or those flush with the block's top face leave part of
     * it uncovered. Null when no walkable surface passes through or the face is fully covered.
     */
    @Nullable
    public static StandIssue standIssue(java.util.List<Occupant> occupants, BlockPos pos) {
        double bottom = 16.0 * pos.getY();
        double top = bottom + 16.0;
        for (Occupant o : occupants) {
            PieceKey k = o.key();
            if (!k.kind().isWalkableSurface()) continue;
            double y = 16.0 * k.origin().getY() + k.baseY();
            if (y > bottom + 1e-6 && y < top - 1e-6) {
                return StandIssue.HALF_LEVEL;
            }
        }
        double covered = flushFloorArea(occupants, pos);
        return covered > 0 && covered < 256.0 - 1e-6 ? StandIssue.PARTLY_COVERED : null;
    }

    /** Area (px²) of the top face of block {@code pos} covered by the walkable surfaces among {@code occupants} flush with it. */
    private static double flushFloorArea(java.util.List<Occupant> occupants, BlockPos pos) {
        double top = 16.0 * pos.getY() + 16.0;
        double covered = 0;
        for (Occupant o : occupants) {
            PieceKey k = o.key();
            if (!k.kind().isWalkableSurface() || Math.abs(16.0 * k.origin().getY() + k.baseY() - top) > 1e-6) continue;
            double x0 = (pos.getX() - k.origin().getX()) * 16.0;
            double z0 = (pos.getZ() - k.origin().getZ()) * 16.0;
            double[][] c = Poly.clipRect(Footprints.solidPolygon(k), x0, z0, x0 + 16, z0 + 16);
            covered += c == null ? 0 : Poly.area(c);
        }
        return covered;
    }

    /**
     * Whether a block may stand on the structure block at {@code pos} (player rule 14): foundations and floors flush
     * with its top cover the whole face, and no floor has its top inside it. Roofs (a merged roof's plateau can be flush
     * with a block top), walls, stairs and ramps never hold one, even where their collision fills the face.
     * <p>HeraliZ (03/11) : la case entière (règle d'avant la 1.33.0, reprise) ; un objet posé, lui, n'a besoin que du sol
     * sous sa vraie forme ({@link #solSousForme}, {@code PlacableSupport}).
     */
    public static boolean holdsBlockOnTop(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StructureBlockEntity be)) {
            return false;
        }
        return standIssue(be.occupants(), pos) == null && flushFloorArea(be.occupants(), pos) >= 256.0 - 1e-6;
    }

    /** {@link #solSousForme} : la forme est entièrement au-dessus du sol de la base. */
    public static final int SOL_OK = 0;
    /** {@link #solSousForme} : une partie de la forme dépasse du bord du sol (bord en biais, bord de la base). */
    public static final int SOL_DEPASSE = 1;
    /** {@link #solSousForme} : aucun sol de base sous la forme (toit, haut de mur, marche, vide). */
    public static final int SOL_AUCUN = 2;
    /** {@link #solSousForme} : un plancher sous la forme a son dessus au milieu du bloc (plancher sur demi-murs). */
    public static final int SOL_MI_BLOC = 3;

    /**
     * HeraliZ (03/11, demande du propriétaire : « rien ne doit dépasser du bord du sol ») : ce qui porte la forme
     * {@code forme} d'un objet posé en {@code pos} (posé, ou dont on essaie la pose) quand le bloc du dessous est un bloc de
     * construction. Chaque boîte de la forme, vue de dessus, doit être entièrement couverte par les fondations et planchers
     * à ras de son dessous (carrés droits, tournés et triangles réunis : les sols d'une base ne se chevauchent jamais, leurs
     * aires s'ajoutent). Seuls les blocs de construction sous les boîtes sont lus (quatre au plus par boîte), au moment
     * d'une pose ou d'une chute : rien par tick.
     */
    public static int solSousForme(BlockGetter level, BlockPos pos, VoxelShape forme) {
        double bas = 16.0 * pos.getY();
        long[] vues = new long[16];
        boolean un = false;
        for (net.minecraft.world.phys.AABB b : forme.toAabbs()) {
            double x0 = 16.0 * (pos.getX() + b.minX), x1 = 16.0 * (pos.getX() + b.maxX);
            double z0 = 16.0 * (pos.getZ() + b.minZ), z1 = 16.0 * (pos.getZ() + b.maxZ);
            double aire = (x1 - x0) * (z1 - z0);
            if (aire <= 1e-6) continue;
            double couvert = 0;
            int nVues = 0;
            for (int bx = (int) Math.floor(x0 / 16.0 + 1e-6); bx <= (int) Math.floor(x1 / 16.0 - 1e-6); bx++) {
                for (int bz = (int) Math.floor(z0 / 16.0 + 1e-6); bz <= (int) Math.floor(z1 / 16.0 - 1e-6); bz++) {
                    BlockPos dessous = new BlockPos(bx, pos.getY() - 1, bz);
                    if (!(level.getBlockEntity(dessous) instanceof StructureBlockEntity be)) continue;
                    if (standIssue(be.occupants(), dessous) == StandIssue.HALF_LEVEL) {
                        return SOL_MI_BLOC;
                    }
                    for (Occupant o : be.occupants()) {
                        PieceKey k = o.key();
                        if (!k.kind().isWalkableSurface() || Math.abs(16.0 * k.origin().getY() + k.baseY() - bas) > 1e-6) continue;
                        // une pièce couvre souvent plusieurs blocs sous la boîte : comptée une fois
                        boolean deja = false;
                        for (int v = 0; v < nVues && !deja; v++) {
                            deja = vues[v] == o.pieceId();
                        }
                        if (deja) continue;
                        if (nVues == vues.length) vues = java.util.Arrays.copyOf(vues, nVues * 2);
                        vues[nVues++] = o.pieceId();
                        double ox = 16.0 * k.origin().getX(), oz = 16.0 * k.origin().getZ();
                        double[][] c = Poly.clipRect(Footprints.solidPolygon(k), x0 - ox, z0 - oz, x1 - ox, z1 - oz);
                        couvert += c == null ? 0 : Poly.area(c);
                    }
                }
            }
            if (couvert <= 1e-6) {
                return un ? SOL_DEPASSE : SOL_AUCUN;
            }
            if (couvert < aire - 1e-3) {
                return SOL_DEPASSE;
            }
            un = true;
        }
        return SOL_OK;
    }

    /**
     * HeraliZ (28/10) : un objet qui sait descendre d'un demi-bloc ({@code Adosse.BAS}) tient sur la case de construction
     * {@code pos} : des fondations ou planchers dont le dessus est au milieu de la case (plancher posé sur des demi-murs)
     * en couvrent toute la surface, comme {@link #holdsBlockOnTop} pour un dessus à ras de la case.
     */
    public static boolean tientEnBas(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StructureBlockEntity be)) {
            return false;
        }
        double mid = 16.0 * pos.getY() + fr.heraliz.crac.block.Adosse.DEMI;
        double covered = 0;
        for (Occupant o : be.occupants()) {
            PieceKey k = o.key();
            if (!k.kind().isWalkableSurface() || Math.abs(16.0 * k.origin().getY() + k.baseY() - mid) > 1e-6) continue;
            double x0 = (pos.getX() - k.origin().getX()) * 16.0;
            double z0 = (pos.getZ() - k.origin().getZ()) * 16.0;
            double[][] c = Poly.clipRect(Footprints.solidPolygon(k), x0, z0, x0 + 16, z0 + 16);
            covered += c == null ? 0 : Poly.area(c);
        }
        return covered >= 256.0 - 1e-6;
    }

    /**
     * The block under a foundation column changed (mined, exploded, picked up): its ground is checked next tick. HeraliZ
     * (27/10) : un bloc à côté (même hauteur) devenu libre peut être la moitié d'un mur laissée à un bloc posé avant le mur
     * (terrain, bloc posé par le staff) : le mur la reprend (StructureService.reboucher, comme pour une armoire ou une caisse
     * en métal qui part) ; avant, le trou restait.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (level.isClientSide()) {
            return;
        }
        Serveur.Moteur m = Serveur.MOTEUR;
        if (m == null) {
            return;
        }
        if (fromPos.equals(pos.below())) {
            m.structureSol(level, pos);
        } else if (fromPos.getY() == pos.getY() && level instanceof net.minecraft.server.level.ServerLevel sl) {
            BlockState voisin = level.getBlockState(fromPos);
            if (!(voisin.getBlock() instanceof StructureBlock) && (voisin.isAir() || voisin.canBeReplaced())) {
                m.objetAdosseRetire(sl, fromPos);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            Serveur.Moteur m = Serveur.MOTEUR;
            if (m != null) m.structureDisparue(level, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
