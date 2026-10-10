package fr.heraliz.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import fr.heraliz.loot.pont.Parties;
import fr.heraliz.loot.pont.UsineBlocEntite;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HeraliZ Loot : les conteneurs de loot par tiers (T1 commun à T7 unique). T2-T6 : blocs décrits par
 * {@code assets/heraliz_loot/conteneurs.json} ; T7 : coffres animés en code ({@code coffres_t7.json}) avec identification
 * des objets. Modèles, textures et listes générés par {@code tools/gen_loot.py}.
 * Loot des conteneurs T1-T6 et des familles : rotation par zones ({@link RotationLoot}, {@link ZonesLoot}, docs/ROTATION.md).
 */
@Mod(HeralizLoot.MODID)
public final class HeralizLoot {
    public static final String MODID = "heraliz_loot";
    public static final Logger LOG = LoggerFactory.getLogger("HeraliZ Loot");

    static final DeferredRegister<Block> BLOCS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    static final DeferredRegister<Item> OBJETS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    static final DeferredRegister<CreativeModeTab> ONGLETS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    static final DeferredRegister<BlockEntityType<?>> ENTITES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    /** Conteneurs dans l'ordre des tiers : l'onglet créatif les montre ainsi. */
    static final List<RegistryObject<Item>> ORDRE = new ArrayList<>();

    /** Objets de l'onglet créatif, dans l'ordre (filtres par grade : client/FiltresOnglet). */
    public static List<RegistryObject<Item>> objetsOnglet() {
        return ORDRE;
    }
    static final List<RegistryObject<Block>> COFFRES = new ArrayList<>();

    static {
        JsonArray liste = lire("conteneurs.json");
        for (JsonElement e : liste) {
            JsonObject o = e.getAsJsonObject();
            String nom = o.get("nom").getAsString();
            // « boites » : repère du modèle (avant vers +Z), peut dépasser le bloc ; « collision » : ancienne convention
            // (modèle tourné vers le nord, dans le bloc), convertie
            List<double[]> boites = new ArrayList<>();
            boolean neuf = o.has("boites");
            for (JsonElement b : o.getAsJsonArray(neuf ? "boites" : "collision")) {
                JsonArray a = b.getAsJsonArray();
                double[] x = {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                        a.get(3).getAsDouble(), a.get(4).getAsDouble(), a.get(5).getAsDouble()};
                boites.add(neuf ? x : new double[]{16 - x[3], x[1], 16 - x[5], 16 - x[0], x[4], 16 - x[2]});
            }
            String matiere = o.has("matiere") ? o.get("matiere").getAsString() : "bois";
            RegistryObject<Block> bloc = BLOCS.register(nom, () -> new ConteneurBlock(proprietes(matiere), boites, nom));
            ORDRE.add(OBJETS.register(nom, () -> new BlockItem(bloc.get(), new Item.Properties())));
        }
        TypeCoffre.charger();
        for (TypeCoffre t : TypeCoffre.TOUS.values()) {
            RegistryObject<Block> bloc = BLOCS.register(t.nom, () -> new CoffreBlock(proprietes("metal").strength(-1f, 3600000f), t));
            COFFRES.add(bloc);
            ORDRE.add(OBJETS.register(t.nom, () -> new CoffreItem(bloc.get(), new Item.Properties())));
        }
        LOG.info("{} conteneurs de loot, {} coffres T7", liste.size(), TypeCoffre.TOUS.size());
    }

    /** Bloc invisible des objets sur plusieurs blocs. */
    public static final RegistryObject<Block> PARTIE = BLOCS.register("partie", () -> new PartieBlock(BlockBehaviour.Properties.of()
            .strength(2.0f, 6.0f).noOcclusion().noLootTable().sound(SoundType.METAL)));

    public static final RegistryObject<Item> OBJET_INCONNU = OBJETS.register("objet_inconnu", () -> new Item(new Item.Properties()));

    /**
     * Usine du coffre T7 (docs/METHODE_CLIENT_SERVEUR.md) : version commune (animation), ou version serveur (loot,
     * identification, apparitions) branchee par la partie serveur.
     */
    public static final UsineBlocEntite<CoffreBlockEntity> COFFRE_USINE = new UsineBlocEntite<>(CoffreBlockEntity::new);
    public static final RegistryObject<BlockEntityType<CoffreBlockEntity>> COFFRE_BE = ENTITES.register("coffre",
            () -> BlockEntityType.Builder.of(COFFRE_USINE, COFFRES.stream().map(RegistryObject::get).toArray(Block[]::new))
                    .build(null));

    public static final RegistryObject<MenuType<CoffreMenu>> COFFRE_MENU = MENUS.register("coffre",
            () -> IForgeMenuType.create(CoffreMenu::new));

    public static final RegistryObject<CreativeModeTab> ONGLET = ONGLETS.register("loot", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.heraliz_loot"))
            .icon(() -> ORDRE.isEmpty() ? ItemStack.EMPTY : new ItemStack(ORDRE.get(ORDRE.size() - 1).get()))
            .displayItems((p, out) -> ORDRE.forEach(o -> out.accept(o.get())))
            .build());

    public HeralizLoot() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCS.register(bus);
        OBJETS.register(bus);
        ONGLETS.register(bus);
        ENTITES.register(bus);
        MENUS.register(bus);
        SonsLoot.SONS.register(bus);
        // raretés des objets envoyées aux joueurs (fond des cases, infobulle)
        ReseauLoot.enregistrer();
        // rotation du loot : réglages serveur (heraliz_loot-server.toml) ; un changement recalcule les zones (partie serveur)
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ConfigLoot.SPEC);
        // parties coupées (docs/METHODE_CLIENT_SERVEUR.md) : tables, tirages, zones, rotation, identification, commandes
        // (serveur) ; rendus, animations, écrans, sons (client)
        Parties.serveur(MODID, "fr.heraliz.loot.serveur.PartieServeur", bus);
        Parties.client(MODID, "fr.heraliz.loot.client.PartieClient", bus);
    }

    private static BlockBehaviour.Properties proprietes(String matiere) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().strength(2.0f, 6.0f).noOcclusion();
        return switch (matiere) {
            case "metal" -> p.sound(SoundType.METAL).mapColor(MapColor.METAL);
            case "tissu" -> p.sound(SoundType.WOOL).mapColor(MapColor.WOOL);
            case "plastique" -> p.sound(SoundType.STONE).mapColor(MapColor.STONE);
            default -> p.sound(SoundType.WOOD).mapColor(MapColor.WOOD);
        };
    }

    private static JsonArray lire(String fichier) {
        try (InputStream in = HeralizLoot.class.getResourceAsStream("/assets/" + MODID + "/" + fichier)) {
            if (in == null) {
                LOG.error("{} introuvable", fichier);
                return new JsonArray();
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (Exception ex) {
            LOG.error("{} illisible", fichier, ex);
            return new JsonArray();
        }
    }
}
