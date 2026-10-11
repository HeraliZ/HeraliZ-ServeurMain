package fr.heralisz.matchs.zombies.contenu;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Materiaux maison de la carte The Giant (matchs 0.2.3, retour n. 16 : plus de blocs vanilla repeints) : briques, betons,
 * aciers, toles, ardoises, bois, sols, carrelages, vitrages, garde-corps, luminaires et petits modeles, 62 blocs.
 * <p>FICHIER GENERE par {@code tools/matchs/cartes/giant_materiaux.py --assets} (avec les blockstates, modeles et
 * textures de {@code zombies/giant_mat}) : ne pas le modifier a la main. Aucun bloc-entite, aucune logique : de simples
 * blocs poses par la structure de la carte. Un onglet creatif de developpement les regroupe.
 */
public final class MateriauxGiant {
    private MateriauxGiant() {
    }

    public static final DeferredRegister<Block> BLOCS = DeferredRegister.create(Registries.BLOCK, HeraliszMatchs.MODID);
    public static final DeferredRegister<Item> OBJETS = DeferredRegister.create(Registries.ITEM, HeraliszMatchs.MODID);
    public static final DeferredRegister<CreativeModeTab> ONGLETS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HeraliszMatchs.MODID);
    /** Tous les materiaux, par id, dans l'ordre du catalogue. */
    public static final Map<String, RegistryObject<Block>> TOUS = new LinkedHashMap<>();

    static {
        cube("zb_mat_brique", SoundType.NETHER_BRICKS, MapColor.TERRACOTTA_RED, 0);
        cube("zb_mat_brique_suie", SoundType.NETHER_BRICKS, MapColor.TERRACOTTA_BROWN, 0);
        cube("zb_mat_brique_abimee", SoundType.NETHER_BRICKS, MapColor.TERRACOTTA_RED, 0);
        cube("zb_mat_brique_pale", SoundType.NETHER_BRICKS, MapColor.TERRACOTTA_PINK, 0);
        cube("zb_mat_beton", SoundType.STONE, MapColor.STONE, 0);
        cube("zb_mat_beton_tache", SoundType.STONE, MapColor.STONE, 0);
        cube("zb_mat_beton_fissure", SoundType.STONE, MapColor.STONE, 0);
        cube("zb_mat_beton_sombre", SoundType.STONE, MapColor.DEEPSLATE, 0);
        cube("zb_mat_acier_peint", SoundType.METAL, MapColor.COLOR_GRAY, 0);
        cube("zb_mat_acier_rivete", SoundType.METAL, MapColor.COLOR_GRAY, 0);
        cube("zb_mat_acier_rouille", SoundType.METAL, MapColor.TERRACOTTA_ORANGE, 0);
        cube("zb_mat_tole_larmee", SoundType.METAL, MapColor.METAL, 0);
        cube("zb_mat_tole_ondulee", SoundType.METAL, MapColor.COLOR_LIGHT_GRAY, 0);
        cube("zb_mat_ardoise", SoundType.DEEPSLATE_TILES, MapColor.DEEPSLATE, 0);
        pilier("zb_mat_poteau_acier", SoundType.METAL, MapColor.COLOR_GRAY);
        cube("zb_mat_planches", SoundType.WOOD, MapColor.WOOD, 0);
        cube("zb_mat_parquet", SoundType.WOOD, MapColor.WOOD, 0);
        cube("zb_mat_caisse", SoundType.WOOD, MapColor.WOOD, 0);
        cube("zb_mat_caisse_renforcee", SoundType.WOOD, MapColor.WOOD, 0);
        pilier("zb_mat_poutre_bois", SoundType.WOOD, MapColor.WOOD);
        cube("zb_mat_sol_usine", SoundType.STONE, MapColor.STONE, 0);
        cube("zb_mat_paves", SoundType.STONE, MapColor.DEEPSLATE, 0);
        cube("zb_mat_asphalte", SoundType.STONE, MapColor.COLOR_BLACK, 0);
        cube("zb_mat_dalles", SoundType.STONE, MapColor.STONE, 0);
        cube("zb_mat_carreaux_blancs", SoundType.STONE, MapColor.QUARTZ, 0);
        cube("zb_mat_carreaux_verts", SoundType.STONE, MapColor.COLOR_GREEN, 0);
        cube("zb_mat_damier", SoundType.STONE, MapColor.QUARTZ, 0);
        cube("zb_mat_rayures", SoundType.METAL, MapColor.COLOR_YELLOW, 0);
        cube("zb_mat_terre_gelee", SoundType.GRAVEL, MapColor.DIRT, 0);
        cube("zb_mat_gravats", SoundType.GRAVEL, MapColor.STONE, 0);
        cube("zb_mat_moellon", SoundType.STONE, MapColor.STONE, 0);
        cube("zb_mat_enduit", SoundType.STONE, MapColor.SAND, 0);
        cube("zb_mat_rideau", SoundType.METAL, MapColor.COLOR_GRAY, 0);
        cube("zb_mat_egout", SoundType.METAL, MapColor.STONE, 0);
        cube("zb_mat_grille_sol", SoundType.METAL, MapColor.COLOR_GRAY, 0);
        cube("zb_mat_grille_aeration", SoundType.METAL, MapColor.COLOR_GRAY, 0);
        verre("zb_mat_verre_arme", SoundType.GLASS);
        verre("zb_mat_vitre_sombre", SoundType.GLASS);
        cube("zb_mat_lampe_encastree", SoundType.METAL, MapColor.COLOR_ORANGE, 14);
        cube("zb_mat_neon", SoundType.METAL, MapColor.QUARTZ, 14);
        cube("zb_mat_lueur_violette", SoundType.METAL, MapColor.COLOR_PURPLE, 10);
        cube("zb_mat_fenetre_eclairee", SoundType.GLASS, MapColor.COLOR_ORANGE, 11);
        forme("zb_mat_suspension", SoundType.LANTERN, MapColor.COLOR_GREEN, 15, Block.box(3, 6.5, 3, 13, 16, 13), false);
        applique("zb_mat_applique", SoundType.LANTERN, 13);
        forme("zb_mat_relais_935", SoundType.METAL, MapColor.COLOR_PURPLE, 9, Block.box(3, 0, 3, 13, 16, 13), true);
        tuyau("zb_mat_tuyau", SoundType.METAL);
        forme("zb_mat_fut", SoundType.METAL, MapColor.COLOR_GREEN, 0, Block.box(1, 0, 1, 15, 16, 15), true);
        forme("zb_mat_barbeles", SoundType.CHAIN, MapColor.NONE, 0, Block.box(1, 0, 1, 15, 14, 15), false);
        gardeCorps("zb_mat_rambarde_wall", SoundType.CHAIN, MapColor.COLOR_GRAY);
        marches("zb_mat_brique_stairs", "zb_mat_brique", SoundType.NETHER_BRICKS, MapColor.TERRACOTTA_RED);
        dalle("zb_mat_brique_slab", SoundType.NETHER_BRICKS, MapColor.TERRACOTTA_RED);
        marches("zb_mat_beton_stairs", "zb_mat_beton", SoundType.STONE, MapColor.STONE);
        dalle("zb_mat_beton_slab", SoundType.STONE, MapColor.STONE);
        marches("zb_mat_ardoise_stairs", "zb_mat_ardoise", SoundType.DEEPSLATE_TILES, MapColor.DEEPSLATE);
        dalle("zb_mat_ardoise_slab", SoundType.DEEPSLATE_TILES, MapColor.DEEPSLATE);
        marches("zb_mat_tole_larmee_stairs", "zb_mat_tole_larmee", SoundType.METAL, MapColor.METAL);
        dalle("zb_mat_tole_larmee_slab", SoundType.METAL, MapColor.METAL);
        marches("zb_mat_planches_stairs", "zb_mat_planches", SoundType.WOOD, MapColor.WOOD);
        dalle("zb_mat_planches_slab", SoundType.WOOD, MapColor.WOOD);
        marches("zb_mat_paves_stairs", "zb_mat_paves", SoundType.STONE, MapColor.DEEPSLATE);
        dalle("zb_mat_paves_slab", SoundType.STONE, MapColor.DEEPSLATE);
        dalle("zb_mat_gravats_slab", SoundType.GRAVEL, MapColor.STONE);
    }

    /** Onglet creatif de developpement (banc d'essai, retouches de la carte a la main). */
    public static final RegistryObject<CreativeModeTab> ONGLET = ONGLETS.register("zb_mat_dev", () -> CreativeModeTab.builder()
            .title(Component.translatableWithFallback("heralisz_matchs.onglet.materiaux_giant", "HeraliZ - matériaux The Giant (dev)"))
            .icon(() -> new ItemStack(TOUS.get("zb_mat_brique").get()))
            .displayItems((p, sortie) -> TOUS.values().forEach(r -> sortie.accept(r.get())))
            .build());

    /** Appele une fois depuis le constructeur du mod. */
    public static void enregistrer(IEventBus bus) {
        BLOCS.register(bus);
        OBJETS.register(bus);
        ONGLETS.register(bus);
    }

    // ------------------------------------------------------------------ fabriques
    private static BlockBehaviour.Properties base(SoundType son, MapColor carte) {
        return BlockBehaviour.Properties.of().mapColor(carte).strength(1.5F, 6.0F).sound(son).noLootTable()
                .pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties fin(SoundType son, MapColor carte) {
        return base(son, carte).noOcclusion().isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false);
    }

    private static RegistryObject<Block> ajouter(String id, Supplier<Block> fab) {
        RegistryObject<Block> r = BLOCS.register(id, fab);
        OBJETS.register(id, () -> new BlockItem(r.get(), new Item.Properties()));
        TOUS.put(id, r);
        return r;
    }

    private static void cube(String id, SoundType son, MapColor carte, int lumiere) {
        ajouter(id, () -> new Block(lumiere > 0 ? base(son, carte).lightLevel(s -> lumiere) : base(son, carte)));
    }

    private static void verre(String id, SoundType son) {
        ajouter(id, () -> new GlassBlock(fin(son, MapColor.NONE)));
    }

    private static void pilier(String id, SoundType son, MapColor carte) {
        ajouter(id, () -> new RotatedPillarBlock(base(son, carte)));
    }

    private static void marches(String id, String base, SoundType son, MapColor carte) {
        ajouter(id, () -> new StairBlock(() -> TOUS.get(base).get().defaultBlockState(), base(son, carte)));
    }

    private static void dalle(String id, SoundType son, MapColor carte) {
        ajouter(id, () -> new SlabBlock(base(son, carte)));
    }

    private static void gardeCorps(String id, SoundType son, MapColor carte) {
        ajouter(id, () -> new WallBlock(base(son, carte).noOcclusion().forceSolidOn()));
    }

    private static void forme(String id, SoundType son, MapColor carte, int lumiere, VoxelShape forme, boolean collision) {
        ajouter(id, () -> new Forme(fin(son, carte).lightLevel(s -> lumiere), forme, collision));
    }

    private static void applique(String id, SoundType son, int lumiere) {
        ajouter(id, () -> new Applique(fin(son, MapColor.COLOR_GRAY).noCollission().lightLevel(s -> lumiere)));
    }

    private static void tuyau(String id, SoundType son) {
        ajouter(id, () -> new Tuyau(fin(son, MapColor.COLOR_GRAY)));
    }

    // ------------------------------------------------------------------ blocs a forme
    /** Petit modele a forme fixe (fut, relais, suspension, barbeles) ; collision optionnelle. */
    public static final class Forme extends Block {
        private final VoxelShape forme;
        private final boolean collision;

        public Forme(Properties p, VoxelShape forme, boolean collision) {
            super(p);
            this.forme = forme;
            this.collision = collision;
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return forme;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return collision ? forme : Shapes.empty();
        }
    }

    /** Applique murale : {@code facing} = cote eclaire (le dos est contre le mur, du cote oppose). */
    public static final class Applique extends HorizontalDirectionalBlock {
        private static final VoxelShape N = Block.box(5, 4, 10.5, 11, 12, 16);
        private static final VoxelShape S = Block.box(5, 4, 0, 11, 12, 5.5);
        private static final VoxelShape E = Block.box(0, 4, 5, 5.5, 12, 11);
        private static final VoxelShape O = Block.box(10.5, 4, 5, 16, 12, 11);

        public Applique(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext c) {
            return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return switch (s.getValue(FACING)) {
                case SOUTH -> S;
                case EAST -> E;
                case WEST -> O;
                default -> N;
            };
        }
    }

    /** Tuyau de 6/16 le long de son axe. */
    public static final class Tuyau extends RotatedPillarBlock {
        private static final VoxelShape Y = Block.box(4, 0, 4, 12, 16, 12);
        private static final VoxelShape X = Block.box(0, 4, 4, 16, 12, 12);
        private static final VoxelShape Z = Block.box(4, 4, 0, 12, 12, 16);

        public Tuyau(Properties p) {
            super(p);
        }

        @Override
        public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return switch (s.getValue(AXIS)) {
                case X -> X;
                case Z -> Z;
                default -> Y;
            };
        }
    }
}
