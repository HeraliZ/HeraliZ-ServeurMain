package fr.heralisz.utils.spawn;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.pont.Usine;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Le monde du spawn (dimension {@code heralisz:spawn}, déclarée par les données du jar serveur : la grotte du terminus,
 * celle de la cinématique d'arrivée) et sa borne de départ : clic droit = départ, après un court délai, vers un point
 * sûr tiré au hasard sur la carte (points et zones posés par le staff, /depart). Les joueurs arrivent au spawn à leur
 * première connexion et à la réapparition sans lit (cinématique : CineReseauServeur) ; une extraction les y ramène
 * aussi (HeraliZ Extraction, par SpawnApi).
 * Jar fusionné : ici le bloc (forme, pose), son objet et les réglages ; le départ, la recherche des points d'arrivée,
 * les commandes et les gardes du monde du spawn sont dans DepartServeur (src/serveur).
 */
public final class Depart {
    public static final DeferredRegister<Block> BLOCS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszUtils.MODID);
    /** Usine du bloc : version serveur (clic = départ) branchée par la partie serveur. */
    public static final Usine<BlockBehaviour.Properties, BorneDepart> BORNE_USINE = new Usine<>(BorneDepart::new);
    public static final RegistryObject<Block> BORNE = BLOCS.register("borne_depart", () -> BORNE_USINE.apply(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 7).pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredRegister<BlockEntityType<?>> ENTITES_BLOC =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HeraliszUtils.MODID);
    /** Entité de la borne : seulement pour dessiner l'hologramme chez le joueur (rien n'est gardé). */
    public static final RegistryObject<BlockEntityType<BorneDepartEntite>> BORNE_BE = ENTITES_BLOC.register("borne_depart",
            () -> BlockEntityType.Builder.of(BorneDepartEntite::new, BORNE.get()).build(null));
    public static final RegistryObject<Item> BORNE_OBJET = HeraliszUtils.ITEMS.register("borne_depart",
            () -> new BlockItem(BORNE.get(), new Item.Properties()));

    private Depart() {}

    /** Registres et réglages (fichier heraliz_spawn-server.toml, dans serverconfig/ du monde). */
    public static void register(IEventBus bus) {
        BLOCS.register(bus);
        ENTITES_BLOC.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DepartConfig.SPEC, "heraliz_spawn-server.toml");
    }
}
