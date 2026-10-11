package fr.heraliz.decors;

import fr.heraliz.decors.bloc.Deco;
import fr.heraliz.decors.bloc.Effet;
import fr.heraliz.decors.bloc.Proprietes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static fr.heraliz.decors.bloc.Deco.boite;
import static fr.heraliz.decors.bloc.Deco.tourner;

/** Tous les blocs de HeraliZ Décors et leurs objets (dans l'ordre de l'onglet). */
public final class Blocs {
    public static final DeferredRegister<Block> BLOCS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeralizDecors.MODID);
    public static final DeferredRegister<Item> OBJETS_REG = DeferredRegister.create(ForgeRegistries.ITEMS, HeralizDecors.MODID);
    public static final List<RegistryObject<Item>> OBJETS = new ArrayList<>();

    // ------------------------------------------------------------------ propriétés
    private static BlockBehaviour.Properties roche(MapColor c) {
        return BlockBehaviour.Properties.of().mapColor(c).strength(1.8f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties deco(SoundType son, int lumiere) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f, 3.0f).sound(son).noOcclusion()
                .lightLevel(s -> lumiere).pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false);
    }

    /** Blocs techniques invisibles (effets, ambiance) : rien à casser par erreur, pas d'objet lâché. */
    private static BlockBehaviour.Properties effet() {
        return BlockBehaviour.Properties.of().noCollission().noOcclusion().noLootTable().strength(-1.0f, 3600000.0f)
                .sound(SoundType.WOOL).isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false);
    }

    private static BlockBehaviour.Properties sansCollision(SoundType son, int lumiere) {
        return deco(son, lumiere).noCollission().replaceable().noLootTable();
    }

    // ------------------------------------------------------------------ formes
    private static final VoxelShape[] LANTERNE_MUR = new VoxelShape[4];
    static {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            LANTERNE_MUR[d.get2DDataValue()] = tourner(Shapes.or(boite(5, 3, 1, 11, 13, 7), boite(7, 10, 7, 9, 12, 16)), d);
        }
    }

    // ------------------------------------------------------------------ lumières (style apocalypse : fer, tôle, ampoules)
    public static final RegistryObject<Block> LANTERNE_FER = bloc("lanterne_fer", () -> new Deco.Lanterne(deco(SoundType.LANTERN, 14),
            s -> switch (s.getValue(Proprietes.POSE)) {
                case SOL -> boite(4, 0, 4, 12, 13, 12);
                case PLAFOND -> boite(4, 2, 4, 12, 16, 12);
                case MUR -> LANTERNE_MUR[s.getValue(Deco.Lanterne.FACING).get2DDataValue()];
            }));
    public static final RegistryObject<Block> LAMPE_SUSPENDUE = bloc("lampe_suspendue", () -> new Deco.ParTaille(
            sansCollision(SoundType.LANTERN, 15), s -> switch (s.getValue(Proprietes.TAILLE)) {
                case PETIT -> boite(4, 6, 4, 12, 16, 12);
                case MOYEN -> boite(2, 4, 2, 14, 16, 14);
                case GRAND -> boite(0, 2, 0, 16, 16, 16);
            }, true, Effet.LAMPE));
    public static final RegistryObject<Block> GUIRLANDE = bloc("guirlande", () -> new Deco.Axe(sansCollision(SoundType.WOOL, 11),
            s -> boite(0, 5, 6, 16, 11, 10), true, Effet.AUCUN));
    public static final RegistryObject<Block> PROJECTEUR = bloc("projecteur", () -> new Deco.Lanterne(deco(SoundType.METAL, 15),
            s -> s.getValue(Proprietes.POSE) == Proprietes.Pose.MUR ? LANTERNE_MUR[s.getValue(Deco.Lanterne.FACING).get2DDataValue()]
                    : boite(2, 0, 2, 14, 16, 14)));
    public static final RegistryObject<Block> CRISTAL_BLEU = bloc("cristal_bleu", () -> cristal(MapColor.COLOR_LIGHT_BLUE));
    public static final RegistryObject<Block> CRISTAL_AMBRE = bloc("cristal_ambre", () -> cristal(MapColor.COLOR_ORANGE));

    // ------------------------------------------------------------------ grands effets (dessinés d'un seul tenant), eau, ambiance
    public static final RegistryObject<Block> GRANDE_CASCADE = bloc("grande_cascade", () -> new fr.heraliz.decors.effet.CascadeBloc(effet()));
    public static final RegistryObject<Block> AMBIANCE = bloc("ambiance", () -> new fr.heraliz.decors.effet.Ambiance(effet()));
    public static final RegistryObject<Block> EAU_DECOR = bloc("eau_decor", () -> new fr.heraliz.decors.bloc.EauDecor(
            BlockBehaviour.Properties.of().mapColor(MapColor.WATER).noCollission().noOcclusion().replaceable().noLootTable()
                    .strength(100.0f).sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> MOUSSE_PENDANTE = bloc("mousse_pendante", () -> new Deco(sansCollision(SoundType.MOSS_CARPET, 0)
            .mapColor(MapColor.PLANT), s -> boite(3, 0, 3, 13, 16, 13), true, Effet.AUCUN));

    // ------------------------------------------------------------------ taches de sang (une image à plat, sol ou mur)
    public static final RegistryObject<Block> TACHE_SANG = bloc("tache_sang", () -> new fr.heraliz.decors.bloc.TacheSang(
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).noCollission().noOcclusion().replaceable().noLootTable()
                    .strength(0.5f).sound(SoundType.HONEY_BLOCK).pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    // ------------------------------------------------------------------ roche de la faille
    public static final RegistryObject<Block> ROCHE_OCRE = bloc("roche_ocre", () -> new Block(roche(MapColor.TERRACOTTA_ORANGE)));
    public static final RegistryObject<Block> ROCHE_ROUGE = bloc("roche_rouge", () -> new Block(roche(MapColor.TERRACOTTA_RED)));
    public static final RegistryObject<Block> ROCHE_SOMBRE = bloc("roche_sombre", () -> new Block(roche(MapColor.DEEPSLATE)));
    public static final RegistryObject<Block> ROCHE_OCRE_ESCALIER = escalier("roche_ocre_escalier", ROCHE_OCRE);
    public static final RegistryObject<Block> ROCHE_ROUGE_ESCALIER = escalier("roche_rouge_escalier", ROCHE_ROUGE);
    public static final RegistryObject<Block> ROCHE_SOMBRE_ESCALIER = escalier("roche_sombre_escalier", ROCHE_SOMBRE);
    public static final RegistryObject<Block> ROCHER = bloc("rocher", () -> new Deco.Rocher(roche(MapColor.STONE).noOcclusion(),
            s -> switch (s.getValue(Proprietes.TAILLE)) {
                case PETIT -> boite(3, 0, 3, 13, 6, 13);
                case MOYEN -> boite(1, 0, 1, 15, 11, 15);
                case GRAND -> boite(0, 0, 0, 16, 16, 16);
            }));
    public static final RegistryObject<Block> STALACTITE = bloc("stalactite", () -> new Deco.Pousse(roche(MapColor.STONE).noOcclusion(),
            s -> {
                boolean haut = s.getValue(Proprietes.POSE2) == Proprietes.Pose.PLAFOND;
                return switch (s.getValue(Proprietes.TAILLE)) {
                    case PETIT -> haut ? boite(5, 8, 5, 11, 16, 11) : boite(5, 0, 5, 11, 8, 11);
                    case MOYEN -> boite(4, 0, 4, 12, 16, 12);
                    case GRAND -> boite(2, 0, 2, 14, 16, 14);
                };
            }, false, Effet.AUCUN));

    // ------------------------------------------------------------------ grillage militaire (1.7.0) : se raccorde dans 8 directions
    public static final RegistryObject<Block> GRILLAGE = bloc("grillage", () -> new fr.heraliz.decors.bloc.Grillage(grillage(), false));
    public static final RegistryObject<Block> GRILLAGE_HAUT = bloc("grillage_haut", () -> new fr.heraliz.decors.bloc.Grillage(grillage(), true));

    private static BlockBehaviour.Properties grillage() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 6.0f).sound(SoundType.CHAIN).noOcclusion()
                .requiresCorrectToolForDrops().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false);
    }

    // ------------------------------------------------------------------ blocs maison : désert (Mirage) et base arctique (1.5.0)
    static {
        BlocsMaison.enregistrer();
    }

    private Blocs() {}

    private static Block cristal(MapColor c) {
        return new Deco.Pousse(deco(SoundType.AMETHYST_CLUSTER, 0).mapColor(c).lightLevel(s -> switch (s.getValue(Proprietes.TAILLE)) {
            case PETIT -> 6;
            case MOYEN -> 9;
            case GRAND -> 12;
        }), s -> {
            boolean haut = s.getValue(Proprietes.POSE2) == Proprietes.Pose.PLAFOND;
            return switch (s.getValue(Proprietes.TAILLE)) {
                case PETIT -> haut ? boite(4, 9, 4, 12, 16, 12) : boite(4, 0, 4, 12, 7, 12);
                case MOYEN -> haut ? boite(3, 4, 3, 13, 16, 13) : boite(3, 0, 3, 13, 12, 13);
                case GRAND -> boite(2, 0, 2, 14, 16, 14);
            };
        }, false, Effet.CRISTAL);
    }

    private static RegistryObject<Block> escalier(String nom, RegistryObject<Block> base) {
        return bloc(nom, () -> new StairBlock(() -> base.get().defaultBlockState(), roche(MapColor.STONE)));
    }

    static RegistryObject<Block> bloc(String nom, Supplier<Block> s) {
        RegistryObject<Block> b = BLOCS.register(nom, s);
        OBJETS.add(OBJETS_REG.register(nom, () -> new BlockItem(b.get(), new Item.Properties())));
        return b;
    }

    @SuppressWarnings("unused")
    private static BlockState etat(RegistryObject<Block> b) {
        return b.get().defaultBlockState();
    }
}
