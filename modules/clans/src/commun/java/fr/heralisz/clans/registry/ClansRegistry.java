package fr.heralisz.clans.registry;

import fr.heraliz.clansetbases.pont.Usine;
import fr.heraliz.clansetbases.pont.UsineEntite;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.territory.CaisseRavitaillementBlock;
import fr.heralisz.clans.territory.CaisseRavitaillementBlockEntity;
import fr.heralisz.clans.territory.DrapeauBlock;
import fr.heralisz.clans.territory.DrapeauBlockEntity;
import fr.heralisz.clans.territory.supply.AvionEntity;
import fr.heralisz.clans.territory.supply.CaisseParachuteEntity;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
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
 * Registres du mod : blocs (drapeau de territoire, caisse de ravitaillement), objets, blocs-entites, entites (avion,
 * caisse sous parachute), particules maison (fumee rouge, trainee, flamme de fusee) et menu de la caisse. Les sons
 * sont dans {@link Sons}.
 */
public final class ClansRegistry {
    private ClansRegistry() {
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszClans.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszClans.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HeraliszClans.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HeraliszClans.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeraliszClans.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, HeraliszClans.MODID);

    // ------------------------------------------------------------------ blocs
    public static final RegistryObject<Block> DRAPEAU = BLOCKS.register("drapeau_territoire", () -> new DrapeauBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noLootTable()
                    .sound(SoundType.STONE).pushReaction(PushReaction.BLOCK).noOcclusion()));
    /** Caisse posee : version serveur (verrou de clan, ouverture) branchee par la partie serveur. */
    public static final Usine<BlockBehaviour.Properties, CaisseRavitaillementBlock> CAISSE_USINE = new Usine<>(CaisseRavitaillementBlock::new);
    public static final RegistryObject<Block> CAISSE = BLOCKS.register("caisse_ravitaillement", () -> CAISSE_USINE.apply(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0F, 6.0F).noLootTable()
                    .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion()));

    public static final RegistryObject<Item> DRAPEAU_ITEM = ITEMS.register("drapeau_territoire", () -> new BlockItem(DRAPEAU.get(), new Item.Properties()));
    public static final RegistryObject<Item> CAISSE_ITEM = ITEMS.register("caisse_ravitaillement", () -> new BlockItem(CAISSE.get(), new Item.Properties()));

    // ------------------------------------------------------------------ blocs-entites
    public static final RegistryObject<BlockEntityType<DrapeauBlockEntity>> DRAPEAU_BE = BLOCK_ENTITIES.register("drapeau_territoire",
            () -> BlockEntityType.Builder.of(DrapeauBlockEntity::new, DRAPEAU.get()).build(null));
    public static final RegistryObject<BlockEntityType<CaisseRavitaillementBlockEntity>> CAISSE_BE = BLOCK_ENTITIES.register("caisse_ravitaillement",
            () -> BlockEntityType.Builder.of(CaisseRavitaillementBlockEntity::new, CAISSE.get()).build(null));

    // ------------------------------------------------------------------ entites
    public static final RegistryObject<EntityType<AvionEntity>> AVION = ENTITIES.register("avion",
            () -> EntityType.Builder.<AvionEntity>of(AvionEntity::new, MobCategory.MISC).sized(6.0F, 2.0F)
                    .clientTrackingRange(16).updateInterval(2).fireImmune().build(HeraliszClans.id("avion").toString()));
    /** Caisse sous parachute : version serveur (chute, atterrissage, butin) branchee par la partie serveur. */
    public static final UsineEntite<CaisseParachuteEntity> CAISSE_PARACHUTE_USINE = new UsineEntite<>(CaisseParachuteEntity::new);
    public static final RegistryObject<EntityType<CaisseParachuteEntity>> CAISSE_PARACHUTE = ENTITIES.register("caisse_parachute",
            () -> EntityType.Builder.<CaisseParachuteEntity>of(CAISSE_PARACHUTE_USINE, MobCategory.MISC).sized(1.0F, 1.0F)
                    .clientTrackingRange(12).updateInterval(2).fireImmune().build(HeraliszClans.id("caisse_parachute").toString()));

    // ------------------------------------------------------------------ particules maison (textures/particle/*.png, particles/*.json)
    /** Fumee rouge du signal (autour du drapeau 3 min, autour de la caisse posee 3 min). */
    public static final RegistryObject<SimpleParticleType> FUMEE_ROUGE = PARTICLES.register("fumee_rouge", () -> new SimpleParticleType(false));
    /** Trainee blanche derriere les moteurs de l'avion (emise cote client). */
    public static final RegistryObject<SimpleParticleType> TRAINEE = PARTICLES.register("trainee", () -> new SimpleParticleType(false));
    /** Flamme de la fusee de signalisation (colonne lumineuse 20 s). */
    public static final RegistryObject<SimpleParticleType> FLAMME_FUSEE = PARTICLES.register("flamme_fusee", () -> new SimpleParticleType(false));

    // ------------------------------------------------------------------ menu de la caisse (27 cases, ecran maison cote client)
    public static final RegistryObject<MenuType<ChestMenu>> CAISSE_MENU = MENUS.register("caisse_ravitaillement",
            () -> new MenuType<>((id, inv) -> new ChestMenu(ClansRegistry.CAISSE_MENU.get(), id, inv, new SimpleContainer(27), 3), FeatureFlags.VANILLA_SET));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        PARTICLES.register(bus);
        MENUS.register(bus);
    }
}
