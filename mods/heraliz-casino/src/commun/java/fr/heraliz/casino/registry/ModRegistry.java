package fr.heraliz.casino.registry;

import com.mojang.serialization.Codec;
import fr.heraliz.casino.block.DecorBlock;
import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.MachinePartBlock;
import fr.heraliz.casino.block.entity.DecorBlockEntity;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.decor.DecorType;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.item.ChipItem;
import fr.heraliz.casino.item.DecorItem;
import fr.heraliz.casino.item.DirectorTabletItem;
import fr.heraliz.casino.item.MachineItem;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.particle.SparkOptions;
import fr.heraliz.casino.pont.UsineEntite;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static fr.heraliz.casino.HeralizCasino.MODID;

/**
 * Enregistrement de tous les objets du mod.
 */
public final class ModRegistry {
    private ModRegistry() {
    }

    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    // ------------------------------------------------------------------ places des joueurs (chaise, tabouret, place debout)
    public static final RegistryObject<net.minecraft.world.entity.EntityType<fr.heraliz.casino.entity.SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> net.minecraft.world.entity.EntityType.Builder.<fr.heraliz.casino.entity.SeatEntity>of(fr.heraliz.casino.entity.SeatEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.01f, 0.01f).noSave().noSummon().clientTrackingRange(10).updateInterval(20).build("seat"));

    // ------------------------------------------------------------------ videurs de la zone du casino
    /** Usine du videur : version serveur (IA, poste, retrait, dialogue) dans les mondes serveur (partie serveur). */
    public static final UsineEntite<fr.heraliz.casino.entity.GuardEntity> GUARD_USINE = new UsineEntite<>(fr.heraliz.casino.entity.GuardEntity::new);
    public static final RegistryObject<net.minecraft.world.entity.EntityType<fr.heraliz.casino.entity.GuardEntity>> GUARD = ENTITIES.register("videur",
            () -> net.minecraft.world.entity.EntityType.Builder.<fr.heraliz.casino.entity.GuardEntity>of(GUARD_USINE,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.7f, 2.0f).clientTrackingRange(10).updateInterval(2).build("videur"));
    /** Joueur sorti par les videurs : entite invisible qu'il monte, deplacee par l'expulsion. */
    public static final RegistryObject<net.minecraft.world.entity.EntityType<fr.heraliz.casino.entity.EscortEntity>> ESCORT = ENTITIES.register("escorte",
            () -> net.minecraft.world.entity.EntityType.Builder.<fr.heraliz.casino.entity.EscortEntity>of(fr.heraliz.casino.entity.EscortEntity::new,
                            net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.01f, 0.01f).noSave().noSummon().clientTrackingRange(10).updateInterval(1).build("escorte"));

    // ------------------------------------------------------------------ sons
    public static final Map<Snd, RegistryObject<SoundEvent>> SND = new EnumMap<>(Snd.class);

    static {
        for (Snd s : Snd.values()) {
            SND.put(s, SOUNDS.register(s.id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, s.id))));
        }
    }

    public static final RegistryObject<SoundEvent> SND_PLACE = SOUNDS.register("machine.place",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "machine.place")));

    /** Sons du bloc : metal lourd, pose custom. */
    public static final SoundType MACHINE_SOUND = new ForgeSoundType(1.0F, 1.0F,
            () -> SoundEvents.NETHERITE_BLOCK_BREAK,
            () -> SoundEvents.NETHERITE_BLOCK_STEP,
            SND_PLACE,
            () -> SoundEvents.NETHERITE_BLOCK_HIT,
            () -> SoundEvents.NETHERITE_BLOCK_FALL);

    public static SoundEvent sound(Snd s) {
        return SND.get(s).get();
    }

    // ------------------------------------------------------------------ particules
    public static final RegistryObject<ParticleType<SparkOptions>> SPARK = PARTICLES.register("spark",
            () -> new ParticleType<SparkOptions>(false, SparkOptions.DESERIALIZER) {
                @Override
                public Codec<SparkOptions> codec() {
                    return SparkOptions.CODEC;
                }
            });

    // ------------------------------------------------------------------ jetons
    public static final List<RegistryObject<ChipItem>> CHIPS = new ArrayList<>();

    static {
        for (int i = 0; i < Chips.VALUES.length; i++) {
            final int v = Chips.VALUES[i];
            Rarity r = v >= 10000 ? Rarity.EPIC : v >= 1000 ? Rarity.RARE : v >= 100 ? Rarity.UNCOMMON : Rarity.COMMON;
            CHIPS.add(ITEMS.register("jeton_" + v, () -> new ChipItem(v, new Item.Properties().stacksTo(64).rarity(r))));
        }
    }

    // ------------------------------------------------------------------ tablette du directeur (operateurs)
    public static final RegistryObject<DirectorTabletItem> DIRECTOR_TABLET = ITEMS.register("tablette_directeur",
            () -> new DirectorTabletItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // ------------------------------------------------------------------ machines
    public static final RegistryObject<MachinePartBlock> MACHINE_PART = BLOCKS.register("machine_part",
            () -> new MachinePartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .sound(MACHINE_SOUND)
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, e) -> false)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    public static final Map<MachineType, RegistryObject<MachineBlock>> MACHINES = new EnumMap<>(MachineType.class);
    public static final Map<MachineType, RegistryObject<Item>> MACHINE_ITEMS = new EnumMap<>(MachineType.class);
    public static final Map<MachineType, RegistryObject<BlockEntityType<MachineBlockEntity>>> BE_TYPES = new EnumMap<>(MachineType.class);

    static {
        for (MachineType type : MachineType.values()) {
            RegistryObject<MachineBlock> block = BLOCKS.register(type.id, () -> new MachineBlock(type, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(MachineBlock.LIGHT))
                    .sound(MACHINE_SOUND)
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, e) -> false)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));
            MACHINES.put(type, block);
            MACHINE_ITEMS.put(type, ITEMS.register(type.id,
                    () -> new MachineItem(block.get(), type, new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC))));
            BE_TYPES.put(type, BLOCK_ENTITIES.register(type.id,
                    () -> BlockEntityType.Builder.of(type.usine, block.get()).build(null)));
        }
    }

    // ------------------------------------------------------------------ elements de decor (modeles 3D)
    public static final Map<DecorType, RegistryObject<DecorBlock>> DECOR = new EnumMap<>(DecorType.class);
    public static final Map<DecorType, RegistryObject<Item>> DECOR_ITEMS = new EnumMap<>(DecorType.class);

    static {
        for (DecorType type : DecorType.values()) {
            RegistryObject<DecorBlock> block = BLOCKS.register(type.id, () -> {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(-1.0F, 3600000.0F)
                        .noLootTable()
                        .noOcclusion()
                        // la porte du coffre utilise l'etat "allume" pour "ouverte" : elle n'eclaire pas
                        .lightLevel(type.flicker() ? s -> fr.heraliz.casino.block.FlickerDecorBlock.light(s, type.light)
                                : s -> s.getValue(DecorBlock.ON) ? type.light : 0)
                        .sound(decorSound(type))
                        .pushReaction(PushReaction.BLOCK)
                        .isValidSpawn((s, l, p, e) -> false)
                        .isRedstoneConductor((s, l, p) -> false)
                        .isSuffocating((s, l, p) -> false)
                        .isViewBlocking((s, l, p) -> false);
                return type.flicker() ? new fr.heraliz.casino.block.FlickerDecorBlock(type, props) : new DecorBlock(type, props);
            });
            DECOR.put(type, block);
            DECOR_ITEMS.put(type, ITEMS.register(type.id, () -> new DecorItem(block.get(), type, new Item.Properties().stacksTo(16).rarity(Rarity.RARE))));
        }
    }

    /** Bruit des pas et des coups sur un element de decor, selon sa matiere. */
    private static SoundType decorSound(DecorType type) {
        return switch (type) {
            case AFFICHE, MATELAS, FAUTEUIL, CANAPE, CHESTERFIELD, TABOURET -> SoundType.WOOL;
            case BOTTE -> SoundType.GRASS;
            case PANCARTE, CABANE, SACS -> SoundType.WOOL;
            case PALMIER -> SoundType.AZALEA_LEAVES;
            case GUERIDON, VALISE -> SoundType.WOOD;
            case CHAISE -> SoundType.BAMBOO_WOOD;
            default -> MACHINE_SOUND;
        };
    }

    public static final RegistryObject<BlockEntityType<DecorBlockEntity>> DECOR_BE = BLOCK_ENTITIES.register("decor",
            () -> BlockEntityType.Builder.of(DecorBlockEntity::new,
                    DECOR.values().stream().map(RegistryObject::get).toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    // ------------------------------------------------------------------ blocs de construction du casino du bunker
    /** Blocs pleins (texture sur les six faces) et grillage ; identifiant -> bloc, dans l'ordre de l'onglet. */
    public static final Map<String, RegistryObject<net.minecraft.world.level.block.Block>> BUILD_BLOCKS = new java.util.LinkedHashMap<>();

    static {
        building("moquette_casino", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED).strength(0.8F).sound(SoundType.WOOL)));
        building("moquette_casino_verte", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GREEN).strength(0.8F).sound(SoundType.WOOL)));
        building("carrelage_sale", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("beton_bunker", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("tole_ondulee", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
        building("grillage", () -> new net.minecraft.world.level.block.IronBarsBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.CHAIN).noOcclusion()));
        // ---- le Trou (casino des clochards)
        building("beton_tache", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("parpaing", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("parpaing_tague", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("tole_rouillee", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_ORANGE).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
        building("palettes", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
        building("carton", () -> new net.minecraft.world.level.block.CarpetBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_BROWN).strength(0.2F).sound(SoundType.WOOL).ignitedByLava()));
        // ---- le Coffre (casino VIP)
        building("parquet_use", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
        building("tapis_bordeaux", () -> new net.minecraft.world.level.block.CarpetBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED).strength(0.1F).sound(SoundType.WOOL).ignitedByLava()));
        building("lambris", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
        building("blindage", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GREEN).strength(5.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK)));
        building("enduit_creme", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.SAND).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        // ---- 2.0.0 : le Trou
        building("briques_sales", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_RED).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("carrelage_metro", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("bache_bleue", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE).strength(0.8F).sound(SoundType.WOOL).ignitedByLava()));
        building("sacs_sable", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.SAND).strength(1.2F, 8.0F).sound(SoundType.SAND)));
        // ---- 2.0.0 : le Coffre
        building("marbre_noir", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("damier_marbre", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
        building("velours_capitonne", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED).strength(0.8F).sound(SoundType.WOOL).ignitedByLava()));
    }

    private static void building(String id, java.util.function.Supplier<net.minecraft.world.level.block.Block> factory) {
        RegistryObject<net.minecraft.world.level.block.Block> block = BLOCKS.register(id, factory);
        BUILD_BLOCKS.put(id, block);
        ITEMS.register(id, () -> new net.minecraft.world.item.BlockItem(block.get(), new Item.Properties()));
    }

    public static BlockEntityType<MachineBlockEntity> beType(MachineType type) {
        return BE_TYPES.get(type).get();
    }

    public static Item machineItem(MachineType type) {
        return MACHINE_ITEMS.get(type).get();
    }

    public static ChipItem chip(int index) {
        return CHIPS.get(index).get();
    }

    // ------------------------------------------------------------------ onglet creatif
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.heralizcasino"))
            .icon(() -> new ItemStack(CHIPS.get(CHIPS.size() - 1).get()))
            .displayItems((params, output) -> {
                for (RegistryObject<ChipItem> c : CHIPS) output.accept(c.get());
                for (MachineType t : MachineType.values()) output.accept(MACHINE_ITEMS.get(t).get());
                for (DecorType t : DecorType.values()) output.accept(DECOR_ITEMS.get(t).get());
                for (RegistryObject<net.minecraft.world.level.block.Block> b : BUILD_BLOCKS.values()) output.accept(b.get());
                output.accept(DIRECTOR_TABLET.get());
            })
            .build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        SOUNDS.register(bus);
        PARTICLES.register(bus);
        TABS.register(bus);
        ENTITIES.register(bus);
    }
}
