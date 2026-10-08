package fr.heraliz.loot;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Un conteneur de loot T2-T6 : bloc orientable (face avant vers le joueur qui le pose), dessiné par son modèle 3D cuit
 * dans le rendu du terrain. Il peut occuper plusieurs blocs (frigo de deux mètres, caisse longue) : les boîtes viennent
 * de conteneurs.json, en seizièmes, repère du modèle (avant vers +Z, origine au coin du bloc principal).
 * Loot : pas d'entité de bloc ; un clic droit passe par la rotation ({@link RotationLoot}) : le conteneur s'ouvre s'il
 * est actif dans sa zone, sinon « Vide. ». Les véhicules (vehicules_*) sont du décor : aucun clic.
 */
public class ConteneurBlock extends HorizontalDirectionalBlock implements Encombrant {
    /**
     * Rotation du loot (src/serveur : RotationLoot) : ouverture, conteneur pose ou retire. Branchee par la partie
     * serveur ; sans elle (jar -client), un clic ne fait rien de plus que l'animation de la main.
     */
    public interface Rotation {
        void ouvrir(ServerLevel sl, BlockPos pos, BlockState st, ConteneurBlock bloc, ServerPlayer sp);

        void blocPose(ServerLevel sl, BlockPos pos, String nom);

        void blocRetire(ServerLevel sl, BlockPos pos, String nom);
    }

    public static volatile Rotation ROTATION;

    private final Encombrement encombrement;
    /** Nom du bloc sans le namespace (t2_frigo...), tel qu'il figure dans les zones et les tables. */
    public final String nom;
    /** Véhicule de décor : jamais de loot. */
    public final boolean vehicule;

    public ConteneurBlock(Properties p, List<double[]> boites, String nom) {
        super(p);
        this.nom = nom;
        this.vehicule = nom.startsWith("vehicules_");
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        float[][] b = new float[boites.size()][];
        for (int i = 0; i < b.length; i++) {
            double[] x = boites.get(i);
            b[i] = new float[]{(float) x[0], (float) x[1], (float) x[2], (float) x[3], (float) x[4], (float) x[5]};
        }
        encombrement = new Encombrement(b.length == 0 ? new float[][]{{0, 0, 0, 16, 16, 16}} : b);
    }

    @Override
    public Encombrement encombrement() {
        return encombrement;
    }

    /** Conteneur portant ce nom (sans namespace), ou null. */
    @Nullable
    public static ConteneurBlock parNom(String nom) {
        ResourceLocation rl = ResourceLocation.tryBuild(HeralizLoot.MODID, nom);
        return rl != null && ForgeRegistries.BLOCKS.getValue(rl) instanceof ConteneurBlock cb ? cb : null;
    }

    /** Clic droit (aussi transmis par les blocs « partie ») : ouverture si le conteneur est actif. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState st, Level l, BlockPos pos, Player p, InteractionHand main, BlockHitResult hit) {
        if (vehicule) return InteractionResult.PASS;
        if (l instanceof ServerLevel sl && p instanceof ServerPlayer sp && ROTATION != null) ROTATION.ouvrir(sl, pos, st, this, sp);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    /** Boîte (seizièmes, tournée vers le nord) tournée vers d autour du centre du bloc (ancienne convention). */
    static double[] tourner(double[] b, Direction d) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (d) {
            case SOUTH -> new double[]{16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0};
            case WEST -> new double[]{z0, b[1], 16 - x1, z1, b[4], 16 - x0};
            case EAST -> new double[]{16 - z1, b[1], x0, 16 - z0, b[4], x1};
            default -> b.clone();
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getHorizontalDirection().getOpposite();
        return placeLibre(ctx.getLevel(), ctx.getClickedPos(), face, ctx) ? defaultBlockState().setValue(FACING, face) : null;
    }

    @Override
    public void setPlacedBy(Level l, BlockPos pos, BlockState st, @Nullable LivingEntity qui, ItemStack it) {
        super.setPlacedBy(l, pos, st, qui, it);
        if (!l.isClientSide) poserParties(l, pos, st);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState st, Level l, BlockPos pos, BlockState ancien, boolean deplace) {
        super.onPlace(st, l, pos, ancien, deplace);
        if (!l.isClientSide && !ancien.is(st.getBlock())) {
            poserParties(l, pos, st);
            if (!vehicule && l instanceof ServerLevel sl && ROTATION != null) ROTATION.blocPose(sl, pos, nom);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState st, Level l, BlockPos pos, BlockState nouveau, boolean deplace) {
        if (!l.isClientSide && !nouveau.is(st.getBlock())) {
            retirerParties(l, pos, st);
            if (!vehicule && l instanceof ServerLevel sl && ROTATION != null) ROTATION.blocRetire(sl, pos, nom);
        }
        super.onRemove(st, l, pos, nouveau, deplace);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState st, BlockGetter l, BlockPos pos, CollisionContext ctx) {
        return encombrement.outline(st.getValue(FACING), 0, 0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState st, BlockGetter l, BlockPos pos, CollisionContext ctx) {
        return encombrement.collision(st.getValue(FACING), 0, 0, 0);
    }
}
