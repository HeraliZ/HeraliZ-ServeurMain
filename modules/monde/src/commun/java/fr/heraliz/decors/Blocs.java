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
import fr.heraliz.decors.bloc.Aiguillage;
import fr.heraliz.decors.bloc.Applique;
import fr.heraliz.decors.bloc.Arcade;
import fr.heraliz.decors.bloc.ArcadeFormes;
import fr.heraliz.decors.bloc.BalustradeBasse;
import fr.heraliz.decors.bloc.Barriere;
import fr.heraliz.decors.bloc.BlocOriente;
import fr.heraliz.decors.bloc.Clignotement;
import fr.heraliz.decors.bloc.Conduit;
import fr.heraliz.decors.bloc.Cristaux;
import fr.heraliz.decors.bloc.CuveSpecimen;
import fr.heraliz.decors.bloc.EauDecor;
import fr.heraliz.decors.bloc.EchelleJaune;
import fr.heraliz.decors.bloc.Engin;
import fr.heraliz.decors.bloc.FenetreOrientale;
import fr.heraliz.decors.bloc.FormesEngins;
import fr.heraliz.decors.bloc.FormesEngins123;
import fr.heraliz.decors.bloc.FormesEngins124;
import fr.heraliz.decors.bloc.FormesEngins125;
import fr.heraliz.decors.bloc.FormesEngins126;
import fr.heraliz.decors.bloc.FormesEngins127;
import fr.heraliz.decors.bloc.FormesEngins128;
import fr.heraliz.decors.bloc.FormesEngins129;
import fr.heraliz.decors.bloc.FormesEngins130;
import fr.heraliz.decors.bloc.GardeCorps;
import fr.heraliz.decors.bloc.Grillage;
import fr.heraliz.decors.bloc.Gyrophare;
import fr.heraliz.decors.bloc.HublotGrand;
import fr.heraliz.decors.bloc.LampeBunker;
import fr.heraliz.decors.bloc.LampeFixe;
import fr.heraliz.decors.bloc.LiquideToxique;
import fr.heraliz.decors.bloc.MeubleDouble;
import fr.heraliz.decors.bloc.MultiBloc;
import fr.heraliz.decors.bloc.PorteGrille;
import fr.heraliz.decors.bloc.TacheSang;
import fr.heraliz.decors.bloc.TagMur;
import fr.heraliz.decors.effet.Ambiance;
import fr.heraliz.decors.effet.CascadeBloc;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;

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

    // ------------------------------------------------------------------ garde-corps en métal (1.10.0) : 1 à 4 côtés par bloc
    public static final RegistryObject<Block> GARDE_CORPS = bloc("garde_corps", () -> new fr.heraliz.decors.bloc.GardeCorps(grillage()));

    // ------------------------------------------------------------------ mobilier de prison (1.11.0) : modèles par outils/prison.py
    public static final RegistryObject<Block> TOILETTES_SALES = bloc("toilettes_sales", () -> new Deco.Oriente(meuble(SoundType.STONE),
            s -> tourner(Shapes.or(boite(5, 0, 5, 11, 6, 12), boite(3.5, 6, 3.5, 12.5, 10, 14), boite(3, 8, 0, 13, 21, 4.5)),
                    s.getValue(Deco.Oriente.FACING).getOpposite()), false, Effet.AUCUN));
    public static final RegistryObject<Block> LAVABO_SALE = bloc("lavabo_sale", () -> new Deco.Oriente(meuble(SoundType.STONE),
            s -> tourner(Shapes.or(boite(6, 0, 2.5, 10, 11, 6.5), boite(2, 11, 0.5, 14, 15, 11), boite(1.5, 15, 0, 14.5, 19, 1)),
                    s.getValue(Deco.Oriente.FACING).getOpposite()), false, Effet.AUCUN));
    public static final RegistryObject<Block> LIT_PRISON = bloc("lit_prison", () -> fr.heraliz.decors.bloc.MeubleDouble.murale(
            meuble(SoundType.CHAIN), s -> tourner(boite(0, 8, 0, 16, 11.5, 12), s.getValue(Deco.Oriente.FACING).getOpposite())));
    public static final RegistryObject<Block> LIT_SUPERPOSE = bloc("lit_superpose", () -> fr.heraliz.decors.bloc.MeubleDouble.enLongueur(
            meuble(SoundType.METAL), s -> tourner(boite(1, 0, 0, 15, 9.5, 16),
                    s.getValue(Deco.Oriente.FACING).getOpposite())));

    // ------------------------------------------------------------------ décors de la nouvelle carte (1.30.3, repris du jar
    // de la carte le 08/11) : médina, prison, labo, fête foraine, port, gare, avion, station-service, mine, arctique
    public static final RegistryObject<Block> LANTERNE_ORIENTALE = bloc("lanterne_orientale", () -> new Deco.Lanterne(deco(SoundType.LANTERN, 14), s -> {
            return switch ((Proprietes.Pose)s.getValue(Proprietes.POSE)) {
                case SOL -> Deco.boite(4.0, 0.0, 4.0, 12.0, 15.0, 12.0);
                case PLAFOND -> Deco.boite(4.0, 1.5, 4.0, 12.0, 16.0, 12.0);
                case MUR -> LANTERNE_MUR[s.getValue(Deco.Lanterne.FACING).get2DDataValue()];
            };
        }));
    public static final RegistryObject<Block> JARRE = bloc("jarre", () -> new Deco.ParTaille(meuble(SoundType.DECORATED_POT), s -> {
            return switch ((Proprietes.Taille)s.getValue(Proprietes.TAILLE)) {
                case PETIT -> Deco.boite(5.0, 0.0, 5.0, 11.0, 7.0, 11.0);
                case MOYEN -> Deco.boite(4.0, 0.0, 4.0, 12.0, 11.0, 12.0);
                case GRAND -> Deco.boite(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
            };
        }, false, Effet.AUCUN));
    public static final RegistryObject<Block> COUSSIN = bloc("coussin", () -> new Deco(meuble(SoundType.WOOL), s -> Deco.boite(1.0, 0.0, 1.0, 15.0, 4.0, 15.0), false, Effet.AUCUN));
    public static final RegistryObject<Block> ARC_SABLE = bloc("arc_sable", () -> arc(MapColor.SAND));
    public static final RegistryObject<Block> ARC_SABLE_GAUCHE = bloc("arc_sable_gauche", () -> arc(MapColor.SAND));
    public static final RegistryObject<Block> ARC_SABLE_DROIT = bloc("arc_sable_droit", () -> arc(MapColor.SAND));
    public static final RegistryObject<Block> ARC_BLANC = bloc("arc_blanc", () -> arc(MapColor.QUARTZ));
    public static final RegistryObject<Block> ARC_BLANC_GAUCHE = bloc("arc_blanc_gauche", () -> arc(MapColor.QUARTZ));
    public static final RegistryObject<Block> ARC_BLANC_DROIT = bloc("arc_blanc_droit", () -> arc(MapColor.QUARTZ));
    public static final RegistryObject<Block> ARCADE_SABLE = bloc("arcade_sable", () -> arcade(MapColor.SAND, false));
    public static final RegistryObject<Block> ARCADE_BLANC = bloc("arcade_blanc", () -> arcade(MapColor.QUARTZ, false));
    public static final RegistryObject<Block> GRANDE_ARCADE_SABLE = bloc("grande_arcade_sable", () -> arcade(MapColor.SAND, true));
    public static final RegistryObject<Block> GRANDE_ARCADE_BLANC = bloc("grande_arcade_blanc", () -> arcade(MapColor.QUARTZ, true));
    public static final RegistryObject<Block> FRONDE_PALMIER = bloc(
            "fronde_palmier",
            () -> new Deco.Oriente(sansCollision(SoundType.GRASS, 0).mapColor(MapColor.PLANT), s -> Deco.boite(2.0, 0.0, 2.0, 14.0, 14.0, 14.0), true, Effet.AUCUN)
        );
    public static final RegistryObject<Block> FRONDE_PALMIER_DIAGONALE = bloc(
            "fronde_palmier_diagonale",
            () -> new Deco.Oriente(sansCollision(SoundType.GRASS, 0).mapColor(MapColor.PLANT), s -> Deco.boite(2.0, 0.0, 2.0, 14.0, 14.0, 14.0), true, Effet.AUCUN)
        );
    public static final RegistryObject<Block> BOUQUET_PALMIER = bloc(
            "bouquet_palmier",
            () -> new Deco(sansCollision(SoundType.GRASS, 0).mapColor(MapColor.PLANT), s -> Deco.boite(2.0, 0.0, 2.0, 14.0, 16.0, 14.0), true, Effet.AUCUN)
        );
    public static final BlockSetType CELLULE = BlockSetType.register(
            new BlockSetType(
                "heraliz_cellule",
                true,
                SoundType.METAL,
                SoundEvents.IRON_DOOR_CLOSE,
                SoundEvents.IRON_DOOR_OPEN,
                SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
                SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundEvents.STONE_BUTTON_CLICK_ON
            )
        );
    public static final RegistryObject<Block> CELLULE_DOOR = bloc(
            "cellule_door",
            () -> new DoorBlock(
                Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY), CELLULE
            )
        );
    public static final RegistryObject<Block> BARBELE = bloc(
            "barbele",
            () -> new Deco.Axe(
                Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.CHAIN)
                    .noOcclusion()
                    .isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false),
                s -> Deco.boite(0.0, 0.0, 0.0, 16.0, 14.0, 16.0),
                false,
                Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> GROUPE_VENTILATION = bloc(
            "groupe_ventilation", () -> new Deco(meuble(SoundType.METAL), s -> Deco.boite(1.0, 0.0, 1.0, 15.0, 13.0, 15.0), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> CONDUIT = bloc("conduit", () -> new Conduit(meuble(SoundType.METAL), s -> {
            VoxelShape v = Deco.boite(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);
            if (s.getValue(Conduit.NORTH)) {
                v = Shapes.or(v, Deco.boite(5.0, 5.0, 0.0, 11.0, 11.0, 5.0));
            }

            if (s.getValue(Conduit.SOUTH)) {
                v = Shapes.or(v, Deco.boite(5.0, 5.0, 11.0, 11.0, 11.0, 16.0));
            }

            if (s.getValue(Conduit.WEST)) {
                v = Shapes.or(v, Deco.boite(0.0, 5.0, 5.0, 5.0, 11.0, 11.0));
            }

            if (s.getValue(Conduit.EAST)) {
                v = Shapes.or(v, Deco.boite(11.0, 5.0, 5.0, 16.0, 11.0, 11.0));
            }

            if (s.getValue(Conduit.DOWN)) {
                v = Shapes.or(v, Deco.boite(5.0, 0.0, 5.0, 11.0, 5.0, 11.0));
            }

            if (s.getValue(Conduit.UP)) {
                v = Shapes.or(v, Deco.boite(5.0, 11.0, 5.0, 11.0, 16.0, 11.0));
            }

            return v;
        }));
    public static final RegistryObject<Block> PANIER_BASKET = bloc(
            "panier_basket",
            () -> new Deco.Oriente(
                meuble(SoundType.METAL),
                s -> Deco.tourner(
                    Shapes.or(Deco.boite(6.5, 0.0, 12.5, 9.5, 16.0, 15.5), Deco.boite(0.0, 8.0, 9.0, 16.0, 16.0, 10.5)), s.getValue(Deco.Oriente.FACING)
                ),
                false,
                Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> POTEAU_BASKET = bloc(
            "poteau_basket",
            () -> new Deco.Oriente(
                meuble(SoundType.METAL), s -> Deco.tourner(Deco.boite(6.5, 0.0, 12.5, 9.5, 16.0, 15.5), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> GRAND_GROUPE_VENTILATION = bloc("grand_groupe_ventilation", () -> new Deco.ParQuart(meuble(SoundType.METAL), s -> {
            return switch ((Proprietes.Quart)s.getValue(Proprietes.QUART)) {
                case NO -> Deco.boite(1.0, 0.0, 1.0, 16.0, 14.0, 16.0);
                case NE -> Deco.boite(0.0, 0.0, 1.0, 15.0, 14.0, 16.0);
                case SO -> Deco.boite(1.0, 0.0, 0.0, 16.0, 14.0, 15.0);
                case SE -> Deco.boite(0.0, 0.0, 0.0, 15.0, 14.0, 15.0);
            };
        }, false, Effet.AUCUN));
    public static final RegistryObject<Block> GRILLE_TOIT = bloc("grille_toit", () -> new Deco(meuble(SoundType.METAL), s -> Deco.boite(0.0, 13.0, 0.0, 16.0, 16.0, 16.0), false, Effet.AUCUN));
    public static final RegistryObject<Block> LIQUIDE_TOXIQUE = bloc(
            "liquide_toxique",
            () -> new LiquideToxique(
                sansCollision(SoundType.SLIME_BLOCK, 7).mapColor(MapColor.COLOR_LIGHT_GREEN), s -> Deco.boite(0.0, 0.0, 0.0, 16.0, 13.0, 16.0)
            )
        );
    public static final RegistryObject<Block> FLAQUE_TOXIQUE = bloc(
            "flaque_toxique",
            () -> new Deco(
                sansCollision(SoundType.HONEY_BLOCK, 5).mapColor(MapColor.COLOR_LIGHT_GREEN), s -> Deco.boite(0.0, 0.0, 0.0, 16.0, 1.0, 16.0), true, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> FUT_RADIOACTIF = bloc(
            "fut_radioactif", () -> new Deco.Oriente(meuble(SoundType.METAL), s -> Deco.boite(3.0, 0.0, 3.0, 13.0, 15.0, 13.0), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> FUT_RENVERSE = bloc(
            "fut_renverse",
            () -> new Deco.Oriente(
                meuble(SoundType.METAL), s -> Deco.tourner(Deco.boite(3.0, 0.0, 0.0, 13.0, 10.0, 16.0), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> PANNEAU_DANGER = bloc(
            "panneau_danger",
            () -> new Deco.Oriente(
                sansCollision(SoundType.METAL, 0),
                s -> Deco.tourner(Deco.boite(2.0, 2.0, 15.0, 14.0, 14.0, 16.0), s.getValue(Deco.Oriente.FACING)),
                true,
                Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> LAMPE_URGENCE = bloc("lampe_urgence", () -> new Deco(deco(SoundType.LANTERN, 11), s -> Deco.boite(4.0, 10.5, 4.0, 12.0, 16.0, 12.0), false, Effet.AUCUN));
    public static final RegistryObject<Block> COMBINAISON_NRBC = bloc(
            "combinaison_nrbc",
            () -> new Deco.Oriente(
                sansCollision(SoundType.WOOL, 0),
                s -> Deco.tourner(Deco.boite(1.0, 0.0, 13.0, 15.0, 16.0, 16.0), s.getValue(Deco.Oriente.FACING)),
                true,
                Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> GRAVATS = bloc("gravats", () -> new Deco.ParTaille(roche(MapColor.STONE).noOcclusion(), s -> {
            return switch ((Proprietes.Taille)s.getValue(Proprietes.TAILLE)) {
                case PETIT -> Deco.boite(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);
                case MOYEN -> Deco.boite(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
                case GRAND -> Deco.boite(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);
            };
        }, false, Effet.AUCUN));
    public static final RegistryObject<Block> BARRICADE = bloc(
            "barricade",
            () -> new Deco.Oriente(
                meuble(SoundType.WOOD), s -> Deco.tourner(Deco.boite(0.0, 0.0, 7.0, 16.0, 16.0, 9.0), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> PORTE_AU_SOL = bloc(
            "porte_au_sol", () -> new Deco.Oriente(sansCollision(SoundType.METAL, 0), s -> Deco.boite(0.0, 0.0, 0.0, 16.0, 2.0, 16.0), true, Effet.AUCUN)
        );
    public static final RegistryObject<Block> CABLES_PENDANTS = bloc(
            "cables_pendants", () -> new Deco(sansCollision(SoundType.CHAIN, 0), s -> Deco.boite(3.0, 0.0, 3.0, 13.0, 16.0, 13.0), true, Effet.AUCUN)
        );
    public static final RegistryObject<Block> JONCHEE = bloc(
            "jonchee", () -> new Deco.ParTaille(sansCollision(SoundType.GRAVEL, 0), s -> Deco.boite(0.0, 0.0, 0.0, 16.0, 1.0, 16.0), true, Effet.AUCUN)
        );
    public static final RegistryObject<Block> PORTE_GRILLE = bloc(
            "porte_grille",
            () -> new PorteGrille(
                Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY), CELLULE
            )
        );
    public static final RegistryObject<Block> PORTE_ARCTIQUE = bloc(
            "porte_arctique",
            () -> new DoorBlock(
                Properties.of().mapColor(MapColor.SNOW).strength(5.0F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY), CELLULE
            )
        );
    public static final RegistryObject<Block> PORTE_CHAMBRE_FROIDE = bloc(
            "porte_chambre_froide",
            () -> new DoorBlock(
                Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY), CELLULE
            )
        );
    public static final RegistryObject<Block> GAINE_CALORIFUGEE = bloc("gaine_calorifugee", () -> new Conduit(meuble(SoundType.WOOL), s -> {
            VoxelShape v = Deco.boite(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
            if (s.getValue(Conduit.NORTH)) {
                v = Shapes.or(v, Deco.boite(4.5, 4.5, 0.0, 11.5, 11.5, 4.0));
            }

            if (s.getValue(Conduit.SOUTH)) {
                v = Shapes.or(v, Deco.boite(4.5, 4.5, 12.0, 11.5, 11.5, 16.0));
            }

            if (s.getValue(Conduit.WEST)) {
                v = Shapes.or(v, Deco.boite(0.0, 4.5, 4.5, 4.0, 11.5, 11.5));
            }

            if (s.getValue(Conduit.EAST)) {
                v = Shapes.or(v, Deco.boite(12.0, 4.5, 4.5, 16.0, 11.5, 11.5));
            }

            if (s.getValue(Conduit.DOWN)) {
                v = Shapes.or(v, Deco.boite(4.5, 0.0, 4.5, 11.5, 4.0, 11.5));
            }

            if (s.getValue(Conduit.UP)) {
                v = Shapes.or(v, Deco.boite(4.5, 12.0, 4.5, 11.5, 16.0, 11.5));
            }

            return v;
        }));
    public static final RegistryObject<Block> MOTONEIGE = bloc(
            "motoneige",
            () -> new Deco.Oriente(meuble(SoundType.METAL), s -> Deco.tourner(engin("motoneige"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> CHENILLETTE = bloc(
            "chenillette",
            () -> new Engin(
                meuble(SoundType.METAL), s -> Deco.tourner(engin("chenillette_" + s.getValue(Engin.PARTIE).getSerializedName()), s.getValue(Engin.FACING))
            )
        );
    public static final RegistryObject<Block> CHEVAL_CARROUSEL = bloc(
            "cheval_carrousel",
            () -> new Deco.Oriente(meuble(SoundType.WOOD), s -> Deco.tourner(engin("cheval_carrousel"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> AUTO_TAMPONNEUSE = bloc(
            "auto_tamponneuse", () -> new Deco.Oriente(meuble(SoundType.METAL), s -> Deco.boite(0.0, 0.0, 0.0, 16.0, 8.0, 16.0), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> WAGONNET = bloc(
            "wagonnet", () -> new Deco.Oriente(meuble(SoundType.METAL), s -> Deco.tourner(engin("wagonnet"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> BARQUE_BUCHE = bloc(
            "barque_buche",
            () -> new Deco.Oriente(
                meuble(SoundType.WOOD), s -> Deco.tourner(Deco.boite(1.0, 0.0, 0.0, 15.0, 9.0, 16.0), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> BARRE_CARROUSEL = bloc("barre_carrousel", () -> new Deco(meuble(SoundType.METAL), s -> engin("barre_carrousel"), false, Effet.AUCUN));
    public static final RegistryObject<Block> NACELLE_GRANDE_ROUE = bloc(
            "nacelle_grande_roue",
            () -> new Deco.Oriente(meuble(SoundType.METAL), s -> Deco.tourner(engin("nacelle_grande_roue"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> NACELLE_GRANDE_ROUE_CASSEE = bloc(
            "nacelle_grande_roue_cassee",
            () -> new Deco.Oriente(
                meuble(SoundType.METAL), s -> Deco.tourner(engin("nacelle_grande_roue_cassee"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> RAIL_MONTAGNES_RUSSES = bloc(
            "rail_montagnes_russes",
            () -> new Deco.Oriente(meuble(SoundType.METAL), s -> Deco.tourner(engin("rail_montagnes_russes"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> RAIL_MONTAGNES_RUSSES_PENTE = bloc(
            "rail_montagnes_russes_pente",
            () -> new Deco.Oriente(
                meuble(SoundType.METAL), s -> Deco.tourner(engin("rail_montagnes_russes_pente"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> PYLONE_MONTAGNES_RUSSES = bloc(
            "pylone_montagnes_russes", () -> new Deco(meuble(SoundType.METAL), s -> engin("pylone_montagnes_russes"), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> TAG_MUR = bloc(
            "tag_mur",
            () -> new TagMur(
                Properties.of()
                    .mapColor(MapColor.NONE)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
            )
        );
    public static final RegistryObject<Block> LAMPE_ROUGE_CLIGNOTANTE = bloc(
            "lampe_rouge_clignotante", () -> new LampeBunker(deco(SoundType.LANTERN, 0).mapColor(MapColor.COLOR_RED).lightLevel(Clignotement::lumiere))
        );
    public static final RegistryObject<Block> GYROPHARE = bloc("gyrophare", () -> new Gyrophare(deco(SoundType.LANTERN, 0).mapColor(MapColor.COLOR_RED).lightLevel(Clignotement::lumiere)));
    public static final RegistryObject<Block> LAMPE_ROUGE_FIXE = bloc("lampe_rouge_fixe", () -> new LampeFixe(deco(SoundType.GLASS, 0).mapColor(MapColor.COLOR_RED).lightLevel(Clignotement::lumiere)));
    public static final RegistryObject<Block> ECHELLE_JAUNE = bloc(
            "echelle_jaune", () -> new EchelleJaune(Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.0F, 3.0F).sound(SoundType.LADDER).noOcclusion())
        );
    public static final RegistryObject<Block> HUBLOT_GRAND = bloc(
            "hublot_grand",
            () -> new HublotGrand(
                Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
            )
        );
    public static final RegistryObject<Block> SCAPHANDRE = bloc("scaphandre", () -> oriente("scaphandre", SoundType.METAL));
    public static final RegistryObject<Block> RACK_BOUTEILLES_PLONGEE = bloc("rack_bouteilles_plongee", () -> oriente("rack_bouteilles_plongee", SoundType.METAL));
    public static final RegistryObject<Block> CUVE_SPECIMEN = bloc(
            "cuve_specimen",
            () -> new CuveSpecimen(
                meuble(SoundType.GLASS),
                s -> Deco.tourner(engin(s.getValue(CuveSpecimen.CASSEE) ? "cuve_specimen_cassee" : "cuve_specimen"), s.getValue(Deco.Oriente.FACING))
            )
        );
    public static final RegistryObject<Block> CONSOLE_SONAR = bloc("console_sonar", () -> oriente("console_sonar", SoundType.METAL));
    public static final RegistryObject<Block> POSTE_COMMANDE_SOUS_MARIN = bloc("poste_commande_sous_marin", () -> oriente("poste_commande_sous_marin", SoundType.METAL));
    public static final RegistryObject<Block> POMPE_HYDRAULIQUE = bloc("pompe_hydraulique", () -> oriente("pompe_hydraulique", SoundType.METAL));
    public static final RegistryObject<Block> TREUIL_CABLE = bloc("treuil_cable", () -> oriente("treuil_cable", SoundType.METAL));
    public static final RegistryObject<Block> MICROSCOPE_LABO = bloc("microscope_labo", () -> oriente("microscope_labo", SoundType.METAL));
    public static final RegistryObject<Block> ARMOIRE_COMBINAISONS = bloc("armoire_combinaisons", () -> oriente("armoire_combinaisons", SoundType.METAL));
    public static final RegistryObject<Block> PENDU = bloc(
            "pendu", () -> new Deco.Oriente(meuble(SoundType.WOOL), s -> Deco.tourner(engin("pendu"), s.getValue(Deco.Oriente.FACING)), true, Effet.AUCUN)
        );
    public static final RegistryObject<Block> SAC_MORTUAIRE = bloc("sac_mortuaire", () -> oriente("sac_mortuaire", SoundType.WOOL));
    public static final RegistryObject<Block> LIT_CAMP_MILITAIRE = bloc("lit_camp_militaire", () -> oriente("lit_camp_militaire", SoundType.WOOL));
    public static final RegistryObject<Block> RADIO_MILITAIRE = bloc("radio_militaire", () -> oriente("radio_militaire", SoundType.METAL));
    public static final RegistryObject<Block> CARTE_MURALE_TACTIQUE = bloc(
            "carte_murale_tactique",
            () -> new Applique(meuble(SoundType.WOOD), s -> Deco.tourner(engin("carte_murale_tactique"), s.getValue(Deco.Oriente.FACING)))
        );
    public static final RegistryObject<Block> MASQUE_GAZ_AU_SOL = bloc("masque_gaz_au_sol", () -> oriente("masque_gaz_au_sol", SoundType.WOOL));
    public static final RegistryObject<Block> TABLEAU_COMPTEUR_JOURS = bloc(
            "tableau_compteur_jours",
            () -> new Applique(meuble(SoundType.WOOD), s -> Deco.tourner(engin("tableau_compteur_jours"), s.getValue(Deco.Oriente.FACING)))
        );
    public static final RegistryObject<Block> FAUTEUIL_RENVERSE = bloc("fauteuil_renverse", () -> oriente("fauteuil_renverse", SoundType.WOOL));
    public static final RegistryObject<Block> PETIT_TRAIN_LOCOMOTIVE = bloc("petit_train_locomotive", () -> train("petit_train_locomotive"));
    public static final RegistryObject<Block> PETIT_TRAIN_WAGON = bloc("petit_train_wagon", () -> train("petit_train_wagon"));
    public static final RegistryObject<Block> CHARIOT_GLACIER = bloc("chariot_glacier", () -> oriente("chariot_glacier", SoundType.METAL));
    public static final RegistryObject<Block> POELE_A_BOIS_ARCTIQUE = bloc(
            "poele_a_bois_arctique",
            () -> new Deco.Oriente(
                meuble(SoundType.METAL).lightLevel(s -> 7), s -> Deco.tourner(engin("poele_a_bois_arctique"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> TRAINEAU = bloc("traineau", () -> oriente("traineau", SoundType.WOOD));
    public static final RegistryObject<Block> SKIS_CROISES = bloc("skis_croises", () -> oriente("skis_croises", SoundType.WOOD));
    public static final RegistryObject<Block> FUT_GELE = bloc("fut_gele", () -> oriente("fut_gele", SoundType.METAL));
    public static final RegistryObject<Block> GROUPE_ELECTROGENE_POLAIRE = bloc("groupe_electrogene_polaire", () -> oriente("groupe_electrogene_polaire", SoundType.METAL));
    public static final RegistryObject<Block> CAISSE_MATERIEL_POLAIRE = bloc("caisse_materiel_polaire", () -> oriente("caisse_materiel_polaire", SoundType.WOOD));
    public static final RegistryObject<Block> ANTENNE_PARABOLIQUE = bloc("antenne_parabolique", () -> oriente("antenne_parabolique", SoundType.METAL));
    public static final RegistryObject<Block> CAROTTIER_GLACE = bloc("carottier_glace", () -> oriente("carottier_glace", SoundType.METAL));
    public static final RegistryObject<Block> RADIATEUR_MURAL = bloc(
            "radiateur_mural", () -> new Applique(meuble(SoundType.METAL), s -> Deco.tourner(engin("radiateur_mural"), s.getValue(Deco.Oriente.FACING)))
        );
    public static final RegistryObject<Block> STATION_AUTO = bloc("station_auto", () -> oriente("station_auto", SoundType.METAL));
    public static final RegistryObject<Block> TRACTEUR_PORTUAIRE = bloc(
            "tracteur_portuaire",
            () -> new MultiBloc.EnginCarre(
                meuble(SoundType.METAL),
                s -> Deco.tourner(engin("tracteur_portuaire_" + s.getValue(MultiBloc.EnginCarre.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
            )
        );
    public static final RegistryObject<Block> CAMION_PORTE_CONTENEUR = bloc(
            "camion_porte_conteneur",
            () -> new MultiBloc.Camion(
                meuble(SoundType.METAL),
                s -> Deco.tourner(engin("camion_porte_conteneur_" + s.getValue(MultiBloc.Camion.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
            )
        );
    public static final RegistryObject<Block> BARRE_NAVIRE = bloc("barre_navire", () -> oriente("barre_navire", SoundType.WOOD));
    public static final RegistryObject<Block> CONSOLE_NAVIGATION = bloc("console_navigation", () -> oriente("console_navigation", SoundType.METAL));
    public static final RegistryObject<Block> RADAR_BORD = bloc("radar_bord", () -> oriente("radar_bord", SoundType.METAL));
    public static final RegistryObject<Block> TABLE_CARTES_MARINES = bloc("table_cartes_marines", () -> oriente("table_cartes_marines", SoundType.WOOD));
    public static final RegistryObject<Block> TRANSMETTEUR_ORDRES = bloc("transmetteur_ordres", () -> oriente("transmetteur_ordres", SoundType.METAL));
    public static final RegistryObject<Block> COFFRE_CAPITAINE = bloc("coffre_capitaine", () -> oriente("coffre_capitaine", SoundType.WOOD));
    public static final RegistryObject<Block> HABITACLE_COMPAS = bloc("habitacle_compas", () -> oriente("habitacle_compas", SoundType.METAL));
    public static final RegistryObject<Block> SIEGE_GARE = bloc("siege_gare", () -> oriente("siege_gare", SoundType.METAL));
    public static final RegistryObject<Block> HORLOGE_GARE = bloc("horloge_gare", () -> oriente("horloge_gare", SoundType.METAL));
    public static final RegistryObject<Block> PANNEAU_DEPARTS = bloc("panneau_departs", () -> oriente("panneau_departs", SoundType.METAL));
    public static final RegistryObject<Block> DISTRIBUTEUR_BILLETS = bloc("distributeur_billets", () -> oriente("distributeur_billets", SoundType.METAL));
    public static final RegistryObject<Block> BARRAGE_ROUTIER = bloc("barrage_routier", () -> oriente("barrage_routier", SoundType.WOOD));
    public static final RegistryObject<Block> BOUEE_SAUVETAGE = bloc(
            "bouee_sauvetage", () -> new Applique(meuble(SoundType.WOOL), s -> Deco.tourner(engin("bouee_sauvetage"), s.getValue(Deco.Oriente.FACING)))
        );
    public static final RegistryObject<Block> EXTINCTEUR_MURAL = bloc(
            "extincteur_mural", () -> new Applique(meuble(SoundType.WOOL), s -> Deco.tourner(engin("extincteur_mural"), s.getValue(Deco.Oriente.FACING)))
        );
    public static final RegistryObject<Block> VOITURE_ACCIDENTEE = bloc(
            "voiture_accidentee",
            () -> new MultiBloc.Voiture(
                meuble(SoundType.METAL),
                s -> Deco.tourner(engin("voiture_accidentee_" + s.getValue(MultiBloc.Voiture.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
            )
        );
    public static final RegistryObject<Block> PORTE_PHARE = bloc("porte_phare", () -> porteBois(MapColor.SNOW));
    public static final RegistryObject<Block> PORTE_STATION_SERVICE = bloc("porte_station_service", () -> porteMetal(MapColor.METAL));
    public static final RegistryObject<Block> PORTE_DOUANE = bloc("porte_douane", () -> porteMetal(MapColor.COLOR_GREEN));
    public static final RegistryObject<Block> PORTE_GRANGE = bloc("porte_grange", () -> porteBois(MapColor.WOOD));
    public static final RegistryObject<Block> PORTE_CHALET_ISOLEE = bloc("porte_chalet_isolee", () -> porteBois(MapColor.COLOR_RED));
    public static final RegistryObject<Block> PORTE_CLOUTEE_SOMBRE = bloc("porte_cloutee_sombre", () -> porteBois(MapColor.COLOR_BROWN));
    public static final RegistryObject<Block> LUMIERE_PHARE = bloc(
            "lumiere_phare",
            () -> new MultiBloc.Colonne(
                meuble(SoundType.GLASS).lightLevel(s -> 15),
                s -> Deco.tourner(engin("lumiere_phare_" + s.getValue(MultiBloc.Colonne.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
            )
        );
    public static final RegistryObject<Block> FEU_PHARE = bloc(
            "feu_phare",
            () -> new Deco.Oriente(
                meuble(SoundType.GLASS).lightLevel(s -> 15), s -> Deco.tourner(engin("feu_phare"), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> SIEGE_AVION = bloc("siege_avion", () -> oriente("siege_avion", SoundType.WOOL));
    public static final RegistryObject<Block> SIEGE_AVION_DOUBLE = bloc("siege_avion_double", () -> oriente("siege_avion_double", SoundType.WOOL));
    public static final RegistryObject<Block> GALLEY_AVION = bloc("galley_avion", () -> oriente("galley_avion", SoundType.METAL));
    public static final RegistryObject<Block> CHARIOT_REPAS = bloc("chariot_repas", () -> oriente("chariot_repas", SoundType.METAL));
    public static final RegistryObject<Block> HUBLOT_CARLINGUE = bloc("hublot_carlingue", () -> new BlocOriente(carlingueVitree()));
    public static final RegistryObject<Block> HUBLOT_CARLINGUE_USE = bloc("hublot_carlingue_use", () -> new BlocOriente(carlingueVitree()));
    public static final RegistryObject<Block> PANNEAU_CARLINGUE = bloc("panneau_carlingue", () -> new Block(carlingue()));
    public static final RegistryObject<Block> PANNEAU_CARLINGUE_BANDE = bloc("panneau_carlingue_bande", () -> new Block(carlingue()));
    public static final RegistryObject<Block> PANNEAU_CARLINGUE_USE = bloc("panneau_carlingue_use", () -> new Block(carlingue()));
    public static final RegistryObject<Block> PANNEAU_CARLINGUE_BANDE_USE = bloc("panneau_carlingue_bande_use", () -> new Block(carlingue()));
    public static final RegistryObject<Block> PANNEAU_CARLINGUE_BRULE = bloc("panneau_carlingue_brule", () -> new Block(carlingue()));
    public static final RegistryObject<Block> POMPE_ESSENCE = bloc(
            "pompe_essence",
            () -> new MultiBloc.Colonne(
                meuble(SoundType.METAL),
                s -> Deco.tourner(engin("pompe_essence_" + s.getValue(MultiBloc.Colonne.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
            )
        );
    public static final RegistryObject<Block> COMPTOIR_CAISSE = bloc("comptoir_caisse", () -> oriente("comptoir_caisse", SoundType.WOOD));
    public static final RegistryObject<Block> RAYONNAGE_BOUTIQUE = bloc("rayonnage_boutique", () -> oriente("rayonnage_boutique", SoundType.METAL));
    public static final RegistryObject<Block> PRESENTOIR_HUILE = bloc("presentoir_huile", () -> oriente("presentoir_huile", SoundType.METAL));
    public static final RegistryObject<Block> CHAISE_BISTROT = bloc("chaise_bistrot", () -> oriente("chaise_bistrot", SoundType.WOOD));
    public static final RegistryObject<Block> CHAISE_PAILLEE = bloc("chaise_paillee", () -> oriente("chaise_paillee", SoundType.WOOD));
    public static final RegistryObject<Block> CHAISE_METAL = bloc("chaise_metal", () -> oriente("chaise_metal", SoundType.METAL));
    public static final RegistryObject<Block> TABLE_BISTROT = bloc("table_bistrot", () -> oriente("table_bistrot", SoundType.STONE));
    public static final RegistryObject<Block> TABLE_RUSTIQUE = bloc("table_rustique", () -> oriente("table_rustique", SoundType.WOOD));
    public static final RegistryObject<Block> TABLE_METAL = bloc("table_metal", () -> oriente("table_metal", SoundType.METAL));
    public static final RegistryObject<Block> RAIL_MINE = bloc("rail_mine", () -> new Deco.Oriente(meuble(SoundType.METAL), s -> Deco.boite(0.0, 0.0, 0.0, 16.0, 2.0, 16.0), false, Effet.AUCUN));
    public static final RegistryObject<Block> RAIL_MINE_VIRAGE = bloc(
            "rail_mine_virage", () -> new Deco.Oriente(meuble(SoundType.METAL), s -> Deco.boite(0.0, 0.0, 0.0, 16.0, 2.0, 16.0), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> AIGUILLAGE = bloc(
            "aiguillage",
            () -> new Aiguillage(
                meuble(SoundType.METAL), s -> Deco.tourner(engin("aiguillage_" + s.getValue(Aiguillage.COTE).getSerializedName()), s.getValue(Deco.Oriente.FACING))
            )
        );
    public static final RegistryObject<Block> BUTOIR = bloc("butoir", () -> oriente("butoir", SoundType.WOOD));
    public static final RegistryObject<Block> RAIL_MINE_PENTE = bloc(
            "rail_mine_pente",
            () -> new Deco.Oriente(
                meuble(SoundType.METAL),
                s -> Deco.tourner(
                    Shapes.or(
                        Deco.boite(0.0, 0.0, 12.0, 16.0, 4.0, 16.0),
                        Deco.boite(0.0, 0.0, 8.0, 16.0, 8.0, 12.0),
                        Deco.boite(0.0, 0.0, 4.0, 16.0, 12.0, 8.0),
                        Deco.boite(0.0, 0.0, 0.0, 16.0, 16.0, 4.0)
                    ),
                    s.getValue(Deco.Oriente.FACING)
                ),
                false,
                Effet.AUCUN
            )
        );
    public static final RegistryObject<Block> BERLINE_VIDE_PENTE = bloc("berline_vide_pente", () -> berlinePente());
    public static final RegistryObject<Block> BERLINE_CHARBON_PENTE = bloc("berline_charbon_pente", () -> berlinePente());
    public static final RegistryObject<Block> BERLINE_FER_PENTE = bloc("berline_fer_pente", () -> berlinePente());
    public static final RegistryObject<Block> BERLINE_VIDE = bloc("berline_vide", () -> oriente("berline_vide", SoundType.METAL));
    public static final RegistryObject<Block> BERLINE_CHARBON = bloc("berline_charbon", () -> oriente("berline_charbon", SoundType.METAL));
    public static final RegistryObject<Block> BERLINE_FER = bloc("berline_fer", () -> oriente("berline_fer", SoundType.METAL));
    public static final RegistryObject<Block> BERLINE_RENVERSEE = bloc("berline_renversee", () -> oriente("berline_renversee", SoundType.METAL));
    public static final RegistryObject<Block> TAS_CHARBON = bloc("tas_charbon", () -> tas("tas_charbon"));
    public static final RegistryObject<Block> TAS_FER = bloc("tas_fer", () -> tas("tas_fer"));
    public static final RegistryObject<Block> FILON_CHARBON = bloc("filon_charbon", () -> new Block(roche(MapColor.STONE)));
    public static final RegistryObject<Block> FILON_FER = bloc("filon_fer", () -> new Block(roche(MapColor.STONE)));
    public static final RegistryObject<Block> CRISTAUX_MINE = bloc("cristaux_mine", () -> new Cristaux(deco(SoundType.AMETHYST_CLUSTER, 10).mapColor(MapColor.COLOR_ORANGE).noCollission()));
    public static final RegistryObject<Block> LAMPE_MINEUR = bloc(
            "lampe_mineur",
            () -> new Deco.Lanterne(
                deco(SoundType.LANTERN, 15),
                s -> {
                    return switch ((Proprietes.Pose)s.getValue(Proprietes.POSE)) {
                        case SOL -> Deco.boite(5.0, 0.0, 5.0, 11.0, 10.0, 11.0);
                        case PLAFOND -> Deco.boite(5.0, 4.0, 5.0, 11.0, 16.0, 11.0);
                        case MUR -> Deco.tourner(
                            Shapes.or(Deco.boite(5.0, 2.5, 3.5, 11.0, 11.0, 9.5), Deco.boite(7.5, 9.0, 9.5, 8.5, 12.0, 16.0)), s.getValue(Deco.Lanterne.FACING)
                        );
                    };
                }
            )
        );
    public static final RegistryObject<Block> PORTE_MAISON = bloc("porte_maison", () -> porteBois(MapColor.COLOR_LIGHT_BLUE));
    public static final RegistryObject<Block> PORTE_MOTEL = bloc("porte_motel", () -> porteBois(MapColor.WOOD));
    public static final RegistryObject<Block> PORTE_ATELIER = bloc("porte_atelier", () -> porteMetal(MapColor.METAL));
    public static final RegistryObject<Block> BALUSTRADE_BOIS = bloc(
            "balustrade_bois", () -> new FenceBlock(Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion())
        );
    public static final RegistryObject<Block> BALUSTRADE_FER = bloc(
            "balustrade_fer", () -> new FenceBlock(Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion())
        );
    public static final RegistryObject<Block> BALUSTRADE_BOIS_BASSE = bloc(
            "balustrade_bois_basse", () -> new BalustradeBasse(Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion())
        );
    public static final RegistryObject<Block> PORTE_SANITAIRES = bloc("porte_sanitaires", () -> porteMetal(MapColor.COLOR_LIGHT_BLUE));
    public static final RegistryObject<Block> RAYONNAGE_RENVERSE = bloc("rayonnage_renverse", () -> oriente("rayonnage_renverse", SoundType.METAL));
    public static final RegistryObject<Block> ROULEAU_LAVAGE = bloc("rouleau_lavage", () -> colonne("rouleau_lavage", SoundType.WOOL));
    public static final RegistryObject<Block> PORTIQUE_LAVAGE = bloc("portique_lavage", () -> colonne("portique_lavage", SoundType.METAL));
    public static final RegistryObject<Block> LANCE_HAUTE_PRESSION = bloc(
            "lance_haute_pression", () -> new Applique(meuble(SoundType.METAL), s -> Deco.tourner(engin("lance_haute_pression"), s.getValue(Deco.Oriente.FACING)))
        );
    public static final RegistryObject<Block> ECHELLE_BOIS_BRUT = bloc("echelle_bois_brut", () -> echelle(MapColor.WOOD, SoundType.LADDER));
    public static final RegistryObject<Block> ECHELLE_GIVREE = bloc("echelle_givree", () -> echelle(MapColor.ICE, SoundType.LADDER));
    public static final RegistryObject<Block> ECHELLE_CORDE_DESERT = bloc("echelle_corde_desert", () -> echelle(MapColor.SAND, SoundType.WOOL));
    public static final RegistryObject<Block> TABLEAU_ECOLE = bloc(
            "tableau_ecole", () -> new Applique(meuble(SoundType.WOOD), s -> Deco.tourner(engin("tableau_ecole"), s.getValue(Deco.Oriente.FACING)))
        );
    public static final RegistryObject<Block> BANC_PARC = bloc("banc_parc", () -> oriente("banc_parc", SoundType.WOOD));
    public static final RegistryObject<Block> BANC_PARC_CASSE = bloc("banc_parc_casse", () -> oriente("banc_parc_casse", SoundType.WOOD));
    public static final RegistryObject<Block> BARRIERE_FILE_ATTENTE = bloc("barriere_file_attente", () -> new Barriere(meuble(SoundType.METAL)));
    public static final RegistryObject<Block> LAMPADAIRE_TOMBE = bloc(
            "lampadaire_tombe",
            () -> new MultiBloc.Lampadaire(
                meuble(SoundType.METAL),
                s -> Deco.tourner(engin("lampadaire_tombe_" + s.getValue(MultiBloc.Lampadaire.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
            )
        );
    public static final RegistryObject<Block> LAMPADAIRE_CASSE = bloc("lampadaire_casse", () -> oriente("lampadaire_casse", SoundType.METAL));
    public static final RegistryObject<Block> GRANDE_NACELLE = bloc("grande_nacelle", () -> nacelle("grande_nacelle"));
    public static final RegistryObject<Block> GRANDE_NACELLE_CASSEE = bloc("grande_nacelle_cassee", () -> nacelle("grande_nacelle_cassee"));
    public static final RegistryObject<Block> RAIL_MONTAGNES_RUSSES_VERTICAL = bloc("rail_montagnes_russes_vertical", () -> oriente("rail_montagnes_russes_vertical", SoundType.METAL));
    public static final RegistryObject<Block> RAIL_MONTAGNES_RUSSES_VIRAGE = bloc("rail_montagnes_russes_virage", () -> oriente("rail_montagnes_russes_virage", SoundType.METAL));
    public static final RegistryObject<Block> CHARIOT_ELEVATEUR = bloc(
            "chariot_elevateur",
            () -> new MultiBloc.Chariot(
                meuble(SoundType.METAL),
                s -> Deco.tourner(engin("chariot_elevateur_" + s.getValue(MultiBloc.Chariot.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
            )
        );
    public static final RegistryObject<Block> CROCHET_GRUE = bloc("crochet_grue", () -> new Deco(meuble(SoundType.CHAIN), s -> engin("crochet_grue"), false, Effet.AUCUN));
    static {
        for (String c : new String[]{"rouge", "bleu", "vert", "orange", "gris", "blanc"}) {
            for (String k : new String[]{"paroi", "porte"}) {
                bloc("conteneur_" + k + "_" + c, () -> new BlocOriente(Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL)));
            }
        }
    }
    private static final Map<String, VoxelShape> FORMES_ENGINS = new ConcurrentHashMap<>();
    public static final RegistryObject<Block> FENETRE_MOUCHARABIEH = bloc("fenetre_moucharabieh", () -> fenetre(MapColor.COLOR_BROWN, SoundType.WOOD));
    public static final RegistryObject<Block> FENETRE_MOUCHARABIEH_BLANC = bloc("fenetre_moucharabieh_blanc", () -> fenetre(MapColor.QUARTZ, SoundType.WOOD));
    public static final RegistryObject<Block> FENETRE_ARC = bloc("fenetre_arc", () -> fenetre(MapColor.QUARTZ, SoundType.STONE, FenetreOrientale.Saillie.APPUI));
    public static final RegistryObject<Block> FENETRE_CLAUSTRA = bloc("fenetre_claustra", () -> fenetre(MapColor.QUARTZ, SoundType.STONE));
    public static final RegistryObject<Block> FENETRE_VOLETS = bloc("fenetre_volets", () -> fenetre(MapColor.COLOR_BLUE, SoundType.WOOD, FenetreOrientale.Saillie.VOLETS));
    public static final RegistryObject<Block> PORTE_ORIENTALE = bloc("porte_orientale", () -> porteBois(MapColor.COLOR_BROWN));
    public static final RegistryObject<Block> PORTE_ORIENTALE_BLEUE = bloc("porte_orientale_bleue", () -> porteBois(MapColor.COLOR_BLUE));
    public static final RegistryObject<Block> PORTE_ORIENTALE_VERTE = bloc("porte_orientale_verte", () -> porteBois(MapColor.COLOR_GREEN));
    public static final RegistryObject<Block> LANTERNE_ORIENTALE_MURALE = bloc(
            "lanterne_orientale_murale",
            () -> new Applique(deco(SoundType.LANTERN, 13), s -> Deco.tourner(Deco.boite(5.0, 1.5, 5.5, 11.0, 16.0, 16.0), s.getValue(Deco.Oriente.FACING)))
        );
    public static final RegistryObject<Block> LAMPE_ORIENTALE_SOL = bloc(
            "lampe_orientale_sol", () -> new Deco(deco(SoundType.LANTERN, 14), s -> Deco.boite(3.5, 0.0, 3.5, 12.5, 16.0, 12.5), false, Effet.AUCUN)
        );
    public static final RegistryObject<Block> LUSTRE_ORIENTAL = bloc(
            "lustre_oriental", () -> new Deco(deco(SoundType.LANTERN, 14).noCollission(), s -> Deco.boite(1.0, 0.0, 1.0, 15.0, 16.0, 15.0), true, Effet.AUCUN)
        );
    public static final RegistryObject<Block> COUSSIN_ROUGE = bloc("coussin_rouge", () -> new Deco(meuble(SoundType.WOOL), s -> Deco.boite(1.0, 0.0, 1.0, 15.0, 4.5, 15.0), false, Effet.AUCUN));
    public static final RegistryObject<Block> COUSSIN_BLEU = bloc("coussin_bleu", () -> new Deco(meuble(SoundType.WOOL), s -> Deco.boite(1.0, 0.0, 1.0, 15.0, 4.5, 15.0), false, Effet.AUCUN));
    public static final RegistryObject<Block> PETITE_JARRE = bloc("petite_jarre", () -> new Deco(meuble(SoundType.DECORATED_POT), s -> Deco.boite(4.0, 0.0, 4.0, 12.0, 8.0, 12.0), false, Effet.AUCUN));

    private static BlockBehaviour.Properties meuble(SoundType son) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(son).noOcclusion()
                .pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false);
    }

    private static BlockBehaviour.Properties grillage() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 6.0f).sound(SoundType.CHAIN).noOcclusion()
                .requiresCorrectToolForDrops().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false);
    }

    // ------------------------------------------------------------------ blocs maison : désert (Mirage) et base arctique (1.5.0)
    static {
        BlocsMaison.enregistrer();
    }

    private Blocs() {}

    private static Block arcade(MapColor c, boolean grande) {
        double e = grande ? 2.5 : 2.0;
        return new Arcade(
            Properties.of()
                .mapColor(c)
                .strength(1.2F, 6.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false),
            s -> {
                String cle = (grande ? "grande_arcade_" : "arcade_")
                    + s.getValue(Arcade.RANG).getSerializedName()
                    + (grande ? "_" + s.getValue(Arcade.COTE).getSerializedName() : "");
                boolean x = s.getValue(Arcade.AXIS) == Axis.X;
                VoxelShape v = Shapes.empty();

                for (double[] b : ArcadeFormes.BOITES.get(cle)) {
                    v = Shapes.or(v, x ? Deco.boite(b[0], b[1], 0.0, b[2], b[3], 16.0) : Deco.boite(0.0, b[1], b[0], 16.0, b[3], b[2]));
                }

                return v.optimize();
            }
        );
    }

    private static Block echelle(MapColor c, SoundType son) {
        return new EchelleJaune(Properties.of().mapColor(c).strength(1.0F, 3.0F).sound(son).noOcclusion());
    }

    private static Block colonne(String nom, SoundType son) {
        return new MultiBloc.Colonne(
            meuble(son), s -> Deco.tourner(engin(nom + "_" + s.getValue(MultiBloc.Colonne.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
        );
    }

    private static Block berlinePente() {
        return new Deco.Oriente(meuble(SoundType.METAL), s -> Shapes.block(), false, Effet.AUCUN);
    }

    private static Block tas(String nom) {
        return new Deco.ParTaille(
            roche(MapColor.COLOR_BLACK).noOcclusion(), s -> engin(nom + "_" + s.getValue(Proprietes.TAILLE).getSerializedName()), false, Effet.AUCUN
        );
    }

    private static Block porteMetal(MapColor c) {
        return new DoorBlock(Properties.of().mapColor(c).strength(5.0F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY), CELLULE);
    }

    private static Properties carlingue() {
        return Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL);
    }

    private static Properties carlingueVitree() {
        return carlingue().noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false);
    }

    private static Block train(String nom) {
        return new MultiBloc.EnginLong(
            meuble(SoundType.METAL),
            s -> Deco.tourner(engin(nom + "_" + s.getValue(MultiBloc.EnginLong.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
        );
    }

    private static Block oriente(String cle, SoundType son) {
        return new Deco.Oriente(meuble(son), s -> Deco.tourner(engin(cle), s.getValue(Deco.Oriente.FACING)), false, Effet.AUCUN);
    }

    private static Block nacelle(String nom) {
        return new MultiBloc.Nacelle(
            meuble(SoundType.METAL), s -> Deco.tourner(engin(nom + "_" + s.getValue(MultiBloc.Nacelle.PARTIE).getSerializedName()), s.getValue(MultiBloc.FACING))
        );
    }

    private static VoxelShape engin(String cle) {
        return FORMES_ENGINS.computeIfAbsent(cle, k -> {
            double[][] bs = FormesEngins127.BOITES.get(k);
            if (bs == null) {
                bs = FormesEngins128.BOITES.get(k);
            }

            if (bs == null) {
                bs = FormesEngins129.BOITES.get(k);
            }

            if (bs == null) {
                bs = FormesEngins130.BOITES.get(k);
            }

            if (bs == null) {
                bs = FormesEngins.BOITES.get(k);
            }

            if (bs == null) {
                bs = FormesEngins123.BOITES.get(k);
            }

            if (bs == null) {
                bs = FormesEngins124.BOITES.get(k);
            }

            if (bs == null) {
                bs = FormesEngins125.BOITES.get(k);
            }

            if (bs == null) {
                bs = FormesEngins126.BOITES.get(k);
            }

            if (bs == null) {
                throw new IllegalArgumentException("forme d'engin inconnue : " + k);
            } else {
                VoxelShape v = Shapes.empty();

                for (double[] b : bs) {
                    v = Shapes.or(v, Deco.boite(b[0], b[1], b[2], b[3], b[4], b[5]));
                }

                return v.optimize();
            }
        });
    }

    private static Block fenetre(MapColor c, SoundType son) {
        return fenetre(c, son, FenetreOrientale.Saillie.AUCUNE);
    }

    private static Block fenetre(MapColor c, SoundType son, FenetreOrientale.Saillie saillie) {
        return new FenetreOrientale(
            Properties.of()
                .mapColor(c)
                .strength(1.5F, 6.0F)
                .sound(son)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK)
                .isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false),
            saillie
        );
    }

    private static Block porteBois(MapColor c) {
        return new DoorBlock(
            Properties.of().mapColor(c).strength(3.0F, 6.0F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY), BlockSetType.OAK
        );
    }

    private static Block arc(MapColor c) {
        return new Deco.Axe(
            Properties.of()
                .mapColor(c)
                .strength(1.2F, 6.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false),
            s -> Shapes.block(),
            false,
            Effet.AUCUN
        );
    }

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
