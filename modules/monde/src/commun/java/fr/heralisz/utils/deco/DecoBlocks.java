package fr.heralisz.utils.deco;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.pont.Usine;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Décors 3D de la map HeralisZ (lampadaires, panneaux de villes, flèches de direction, épaves, usine nucléaire...),
 * décrits dans heralisz_utils_deco.json (tools/gen_map_models.py écrit ce catalogue avec les modèles et les textures).
 * Tous sont des modèles fixes cuits dans le maillage des chunks : aucun coût par image. Rendus animés (une entité de
 * bloc sans tick, dessinée côté client, peu d'exemplaires) : le mélangeur du bassin radioactif, les décors de Genre
 * (moulins, éolienne, roue à aubes, radar, drapeaux, manche à air : maillages de tools/gen_anim_meshes.py) et les ponts
 * de corde (PontBlock : plancher invisible où l'on marche, PontTeteBlock : sa tête qui dessine le pont qui tangue).
 */
public final class DecoBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszUtils.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HeraliszUtils.MODID);
    public static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();
    /** Liquide radioactif : version serveur (poison) branchée par la partie serveur (jar fusionné). */
    public static final Usine<BlockBehaviour.Properties, LiquideRadioactif> LIQUIDE_USINE = new Usine<>(LiquideRadioactif::new);

    public static final RegistryObject<Block> MELANGEUR = register("melangeur", () -> new MelangeurBlock(
            props("metal", 0).noOcclusion()));
    public static final RegistryObject<BlockEntityType<MelangeurBlockEntity>> MELANGEUR_BE = BLOCK_ENTITIES.register(
            "melangeur", () -> BlockEntityType.Builder.of(MelangeurBlockEntity::new, MELANGEUR.get()).build(null));

    public static final List<RegistryObject<Block>> ANIMES = new ArrayList<>();

    static {
        for (Genre g : Genre.values()) {
            ANIMES.add(register(g.id, () -> new AnimeBlock(props(g.son, g.lumiere()), g)));
        }
    }

    public static final RegistryObject<BlockEntityType<AnimeBlockEntity>> ANIME_BE = BLOCK_ENTITIES.register("anime",
            () -> BlockEntityType.Builder.of(AnimeBlockEntity::new, ANIMES.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<Block> PONT = register("pont_corde", () -> new PontBlock(pontProps()));
    public static final RegistryObject<Block> PONT_TETE = register("pont_corde_tete", () -> new PontTeteBlock(pontProps()));
    public static final RegistryObject<BlockEntityType<PontBlockEntity>> PONT_BE = BLOCK_ENTITIES.register("pont_corde",
            () -> BlockEntityType.Builder.of(PontBlockEntity::new, PONT_TETE.get()).build(null));
    /** Corps des décors 3D (collision exacte du modèle, tools/gen_corps.py) : sans objet, posé par le générateur. */
    public static final RegistryObject<Block> CORPS = BLOCKS.register("corps", () -> new CorpsBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE).strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().forceSolidOn()
            .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
            .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    static {
        for (JsonObject o : catalog()) {
            String id = o.get("id").getAsString();
            String kind = o.has("kind") ? o.get("kind").getAsString() : "deco";
            String sound = o.has("son") ? o.get("son").getAsString() : "metal";
            int light = o.has("lumiere") ? o.get("lumiere").getAsInt() : 0;
            boolean solid = !o.has("collision") || o.get("collision").getAsBoolean();
            double[] box = box(o);
            switch (kind) {
                case "axis" -> register(id, () -> new AxisDecoBlock(props(sound, light), box, solid));
                case "plein" -> register(id, () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL).strength(4.0f, 8.0f).sound(SoundType.METAL).lightLevel(st -> light)));
                case "liquide" -> register(id, () -> LIQUIDE_USINE.apply(props(sound, light).noCollission()
                        .replaceable().pushReaction(PushReaction.DESTROY)));
                default -> register(id, () -> new DecoBlock(props(sound, light), box, solid));
            }
        }
    }

    private DecoBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private static BlockBehaviour.Properties props(String sound, int light) {
        SoundType st = switch (sound) {
            case "bois" -> SoundType.WOOD;
            case "pierre" -> SoundType.STONE;
            case "verre" -> SoundType.GLASS;
            case "chair" -> SoundType.SLIME_BLOCK;
            case "liquide" -> SoundType.MUD;
            default -> SoundType.METAL;
        };
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 8.0f).sound(st).noOcclusion()
                .lightLevel(s -> light).isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    /** Planchers des ponts de corde : incassables en survie, sons de bois, rien ne pousse ni n'apparaît dessus. */
    private static BlockBehaviour.Properties pontProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0f, 3600000.0f).noLootTable()
                .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, e) -> false)
                .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    private static double[] box(JsonObject o) {
        if (!o.has("boite")) {
            return new double[] {0, 0, 0, 16, 16, 16};
        }
        JsonArray a = o.getAsJsonArray("boite");
        double[] b = new double[6];
        for (int i = 0; i < 6; i++) {
            b[i] = a.get(i).getAsDouble();
        }
        return b;
    }

    private static RegistryObject<Block> register(String id, Supplier<Block> block) {
        RegistryObject<Block> b = BLOCKS.register(id, block);
        ITEMS.add(HeraliszUtils.ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties())));
        return b;
    }

    private static List<JsonObject> catalog() {
        try (Reader r = new InputStreamReader(DecoBlocks.class.getResourceAsStream("/heralisz_utils_deco.json"), StandardCharsets.UTF_8)) {
            List<JsonObject> out = new ArrayList<>();
            new Gson().fromJson(r, JsonObject.class).getAsJsonArray("blocs").forEach(e -> out.add(e.getAsJsonObject()));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("heralisz_utils_deco.json illisible", e);
        }
    }
}
