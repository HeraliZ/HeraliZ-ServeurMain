package fr.heraliz.crac.block;

import fr.heraliz.crac.block.entity.CoffreBlockEntity;
import fr.heraliz.crac.build.world.Occupant;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Coffres de stockage par rareté des bases (27/10, rendus validés par le propriétaire) : malle de chantier (Peu commune),
 * casier métallique (Rare), caisse d'armes renforcée (Épique), coffre-fort (Légendaire). La caisse en métal
 * ({@link MetalCrateBlock}) est la Commune.
 * <p>Chaque coffre prend deux cases : côte à côte ({@link #large}) ou l'une sur l'autre. La case principale garde
 * l'inventaire ({@link CoffreBlockEntity}) ; l'autre ({@link #ANNEXE}) n'a rien et renvoie les clics à la principale. Les
 * deux cases partent ensemble (pose, ramassage, raid : {@link fr.heraliz.crac.placable.MultiBlocks}).
 * <p>Collés aux murs comme la caisse en métal ({@link Adosse}) : les deux cases sont poussées du même décalage. Modèles cuits
 * (rien n'est dessiné à chaque image tant que le coffre est fermé), écrits avec leurs mesures ({@link CoffresDonnees}) par
 * {@code modules/bases/outils/coffres_modeles.py --installer}.
 */
public class CoffreBlock extends Block implements EntityBlock, Adosse.Adossable {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** La case qui n'est pas la principale : à droite du coffre vu de face (2 de large) ou au-dessus (2 de haut). */
    public static final BooleanProperty ANNEXE = BooleanProperty.create("annexe");

    private final String nom;
    private final boolean large;
    private final int colonnes, lignes;
    private final SoundEvent sonOuvre, sonFerme;
    private final float hauteurSon;
    private final CoffresDonnees.Mesures mesures;
    /** Formes poussées contre le mur : [case annexe ou non][sens][décalage]. */
    private final VoxelShape[][][] formes;

    public CoffreBlock(String nom, boolean large, int colonnes, int lignes, SoundEvent sonOuvre, SoundEvent sonFerme,
                       float hauteurSon, Properties props) {
        super(props);
        this.nom = nom;
        this.large = large;
        this.colonnes = colonnes;
        this.lignes = lignes;
        this.sonOuvre = sonOuvre;
        this.sonFerme = sonFerme;
        this.hauteurSon = hauteurSon;
        this.mesures = CoffresDonnees.de(nom);
        double[] boite = this.mesures.boite();
        this.formes = new VoxelShape[][][]{
            Adosse.formesDecalees(f -> tournee(this.mesures.formePrincipale(), f), boite),
            Adosse.formesDecalees(f -> tournee(this.mesures.formeAnnexe(), f), boite)};
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
            .setValue(ANNEXE, false).setValue(Adosse.ADOSSE, Adosse.AUCUN));
    }

    public String nom() {
        return this.nom;
    }

    public boolean large() {
        return this.large;
    }

    public int colonnes() {
        return this.colonnes;
    }

    public int lignes() {
        return this.lignes;
    }

    public CoffresDonnees.Mesures mesures() {
        return this.mesures;
    }

    public SoundEvent sonOuvre() {
        return this.sonOuvre;
    }

    public SoundEvent sonFerme() {
        return this.sonFerme;
    }

    public float hauteurSon() {
        return this.hauteurSon;
    }

    /** Boîte x0, y0, z0, x1, y1, z1 (px, coffre tourné vers le nord) tournée pour {@code f}, comme le modèle. */
    static VoxelShape tournee(double[] b, Direction f) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (f) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(FACING, OPEN, ANNEXE, Adosse.ADOSSE);
    }

    // ---------------------------------------------------------------- les deux cases

    /** L'autre case d'un coffre dont la case principale est en {@code principal}. */
    public BlockPos annexeDe(BlockPos principal, Direction f) {
        return this.large ? principal.relative(f.getClockWise()) : principal.above();
    }

    /** La case principale d'une case de ce coffre (elle-même si c'est la principale). */
    public BlockPos principal(BlockState s, BlockPos pos) {
        if (!s.getValue(ANNEXE)) {
            return pos.immutable();
        }
        return this.large ? pos.relative(s.getValue(FACING).getCounterClockWise()) : pos.below();
    }

    /** L'autre case de ce coffre. */
    public BlockPos partenaire(BlockState s, BlockPos pos) {
        return s.getValue(ANNEXE) ? this.principal(s, pos) : this.annexeDe(pos, s.getValue(FACING));
    }

    private Direction versPartenaire(BlockState s) {
        Direction f = s.getValue(FACING);
        if (this.large) {
            return s.getValue(ANNEXE) ? f.getCounterClockWise() : f.getClockWise();
        }
        return s.getValue(ANNEXE) ? Direction.DOWN : Direction.UP;
    }

    // ---------------------------------------------------------------- adossement

    @Override
    public double[] boiteNord() {
        return this.mesures.boite();
    }

    @Override
    public double hauteur(BlockState state) {
        return (state.getValue(ANNEXE) ? this.mesures.formeAnnexe() : this.mesures.formePrincipale())[4];
    }

    // ---------------------------------------------------------------- pose

    /** Face avant vers le joueur ; l'autre case doit être libre. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection().getOpposite();
        BlockPos pos = ctx.getClickedPos();
        BlockPos autre = this.annexeDe(pos, f);
        Level level = ctx.getLevel();
        if (autre.getY() >= level.getMaxBuildHeight() || !level.getBlockState(autre).canBeReplaced(ctx)) {
            return null;
        }
        BlockState s = this.defaultBlockState().setValue(FACING, f);
        return s.setValue(Adosse.ADOSSE, this.choisir(ctx, s, pos, autre));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide && !state.getValue(ANNEXE)) {
            level.setBlock(this.annexeDe(pos, state.getValue(FACING)), state.setValue(ANNEXE, true), Block.UPDATE_ALL);
        }
    }

    /**
     * Le sens où pousser le coffre, comme {@link Adosse#choisir} mais pour ses deux cases : vers la case visée si on a posé
     * en visant un mur ({@link ObjetAdossable}), sinon vers un mur voisin de l'une des deux cases ; le premier choix où les
     * deux cases tiennent.
     */
    private int choisir(BlockPlaceContext ctx, BlockState etat, BlockPos principal, BlockPos annexe) {
        BlockGetter level = ctx.getLevel();
        List<BlockPos> cases = List.of(principal, annexe);
        BlockPos cible = Adosse.CIBLE.get();
        int x, z;
        if (cible != null) {
            BlockPos ref = cible.distSqr(principal) <= cible.distSqr(annexe) ? principal : annexe;
            x = Integer.signum(cible.getX() - ref.getX());
            z = Integer.signum(cible.getZ() - ref.getZ());
        } else {
            Vec3 clic = ctx.getClickLocation();
            x = axe(level, cases, Direction.EAST, clic.x - principal.getX() - 0.5);
            z = axe(level, cases, Direction.SOUTH, clic.z - principal.getZ() - 0.5);
        }
        for (int[] c : new int[][]{{x, z}, {x, 0}, {0, z}}) {
            if (c[0] != 0 || c[1] != 0) {
                int v = Adosse.code(c[0], c[1]);
                if (this.tient(level, etat.setValue(Adosse.ADOSSE, v), principal, annexe)) {
                    return v;
                }
            }
        }
        return Adosse.AUCUN;
    }

    private static int axe(BlockGetter level, List<BlockPos> cases, Direction plus, double clic) {
        boolean p = false, m = false;
        for (BlockPos c : cases) {
            BlockPos a = c.relative(plus), b = c.relative(plus.getOpposite());
            p |= !cases.contains(a) && Adosse.estStructure(level, a);
            m |= !cases.contains(b) && Adosse.estStructure(level, b);
        }
        if (p && m) {
            return clic >= 0 ? 1 : -1;
        }
        return p ? 1 : m ? -1 : 0;
    }

    /**
     * Les deux cases poussées tiennent ({@link Adosse#tient} pour un objet de deux cases) : hors des colonnes du coffre,
     * chaque case touchée est une case de construction dont la forme ne le touche pas, jamais devant une porte, et rien
     * d'autre d'adossé n'est sur son chemin.
     */
    private boolean tient(BlockGetter level, BlockState principal, BlockPos pp, BlockPos pa) {
        BlockState annexe = principal.setValue(ANNEXE, true);
        for (int k = 0; k < 2; k++) {
            BlockPos pos = k == 0 ? pp : pa;
            AABB boite = Adosse.boiteMonde(k == 0 ? principal : annexe, pos).deflate(1.0E-4);
            for (BlockPos c : BlockPos.betweenClosed(BlockPos.containing(boite.minX, boite.minY, boite.minZ),
                BlockPos.containing(boite.maxX, boite.maxY, boite.maxZ))) {
                if (memeColonne(c, pp) || memeColonne(c, pa)) {
                    continue;
                }
                BlockState s = level.getBlockState(c);
                if (!(s.getBlock() instanceof StructureBlock)) {
                    return false;
                }
                if (level.getBlockEntity(c) instanceof StructureBlockEntity be) {
                    for (Occupant o : be.occupants()) {
                        if (o.key().kind().hasDoor()) {
                            return false;
                        }
                    }
                }
                VoxelShape mur = s.getCollisionShape(level, c, CollisionContext.empty());
                if (!mur.isEmpty() && Shapes.joinIsNotEmpty(mur.move(c.getX(), c.getY(), c.getZ()), Shapes.create(boite), BooleanOp.AND)) {
                    return false;
                }
            }
            for (Adosse.Voisin o : Adosse.voisins(level, pos, 2)) {
                if (o.boite().intersects(boite) && !memeColonne(o.pos(), pp) && !memeColonne(o.pos(), pa)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean memeColonne(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getZ() == b.getZ() && Math.abs(a.getY() - b.getY()) <= 1;
    }

    // ---------------------------------------------------------------- forme, rendu

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return this.formes[s.getValue(ANNEXE) ? 1 : 0][s.getValue(FACING).get2DDataValue()][s.getValue(Adosse.ADOSSE)];
    }

    /** La case du haut d'un coffre de 2 de haut est dessinée par la case du bas (modèle de 2 blocs). */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return !this.large && state.getValue(ANNEXE) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /** L'autre case a disparu (cassée, effacée) : celle-ci part aussi. */
    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState voisin, LevelAccessor level, BlockPos pos, BlockPos posVoisin) {
        if (dir == this.versPartenaire(s) && !(voisin.is(this) && voisin.getValue(ANNEXE) != s.getValue(ANNEXE)
            && voisin.getValue(FACING) == s.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return s;
    }

    // ---------------------------------------------------------------- ouverture

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos p = this.principal(state, pos);
        if (!p.equals(pos)) {
            // clic sur l'autre case : mêmes règles que sur la case principale (droit d'ouvrir les coffres, base protégée)
            if (!level.getBlockState(p).is(this)) {
                return InteractionResult.CONSUME;
            }
            BlockHitResult h = new BlockHitResult(hit.getLocation(), hit.getDirection(), p, hit.isInside());
            var ev = net.minecraftforge.common.ForgeHooks.onRightClickBlock(player, hand, p, h);
            if (ev.isCanceled() || ev.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY) {
                return InteractionResult.FAIL;
            }
        }
        if (level.getBlockEntity(p) instanceof CoffreBlockEntity be) {
            be.ouvrir(player);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(this.principal(state, pos)) instanceof CoffreBlockEntity be ? be : null;
    }

    /** Ouverture et fermeture envoyées aux joueurs proches (animation du couvercle ou de la porte). */
    @Override
    @SuppressWarnings("deprecation")
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    /** Programmé par la fermeture et par le compteur d'ouvertures : referme quand personne ne regarde plus. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof CoffreBlockEntity be) {
            be.verifierOuverture();
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(ANNEXE) ? null : new CoffreBlockEntity(pos, state);
    }

    // ---------------------------------------------------------------- pose, retrait

    /** Serveur : pose ou changement d'état ; les cases de mur voisines refont leur forme si la boîte a bougé. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        Adosse.changement(oldState, state);
    }

    @Override
    public void onBlockStateChange(LevelReader level, BlockPos pos, BlockState oldState, BlockState newState) {
        Adosse.changement(oldState, newState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            Adosse.changement();   // les cases de mur voisines refont leur forme (sans le coffre)
            if (!state.getValue(ANNEXE) && level.getBlockEntity(pos) instanceof CoffreBlockEntity be) {
                Containers.dropContents(level, pos, be);
            }
            fr.heraliz.crac.pont.Serveur.Moteur m = fr.heraliz.crac.pont.Serveur.MOTEUR;
            if (level instanceof ServerLevel sl && m != null) {
                m.objetAdosseRetire(sl, pos);   // un mur reprend la moitié de case que le coffre fermait
            }
            // l'autre case part avec celle-ci, sans rien laisser tomber
            BlockPos autre = this.partenaire(state, pos);
            BlockState a = level.getBlockState(autre);
            if (a.is(this) && a.getValue(ANNEXE) != state.getValue(ANNEXE)) {
                level.setBlock(autre, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }
}
