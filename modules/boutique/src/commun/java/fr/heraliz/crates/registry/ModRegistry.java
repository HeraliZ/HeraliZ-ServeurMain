package fr.heraliz.crates.registry;

import fr.heraliz.crates.pont.UsineBlocEntite;
import com.mojang.serialization.Codec;
import fr.heraliz.crates.block.CrateBlock;
import fr.heraliz.crates.block.CrateBlockEntity;
import fr.heraliz.crates.block.CratePartBlock;
import fr.heraliz.crates.registry.Snd;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateBlockItem;
import fr.heraliz.crates.item.CrateKeyItem;
import fr.heraliz.crates.particle.SparkOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static fr.heraliz.crates.HeralizCrates.MODID;

/**
 * Enregistrement de tous les objets du mod.
 */
public final class ModRegistry {
    private ModRegistry() {
    }

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ------------------------------------------------------------------ sons
    private static final Map<String, RegistryObject<SoundEvent>> SOUND_BY_ID = new HashMap<>();

    public static final RegistryObject<SoundEvent> SND_LOCKED = registerSound("crate.locked");
    public static final RegistryObject<SoundEvent> SND_KEY_INSERT = registerSound("key.insert");
    public static final RegistryObject<SoundEvent> SND_KEY_TURN = registerSound("key.turn");
    public static final RegistryObject<SoundEvent> SND_UNLOCK = registerSound("crate.unlock");
    public static final RegistryObject<SoundEvent> SND_OPEN = registerSound("crate.open");
    public static final RegistryObject<SoundEvent> SND_CLOSE = registerSound("crate.close");
    public static final RegistryObject<SoundEvent> SND_PLACE = registerSound("crate.place");
    public static final RegistryObject<SoundEvent> SND_AMBIENT = registerSound("crate.ambient");
    public static final RegistryObject<SoundEvent> SND_ROLL_START = registerSound("roll.start");
    public static final RegistryObject<SoundEvent> SND_ROLL_TICK = registerSound("roll.tick");
    public static final RegistryObject<SoundEvent> SND_ROLL_STOP = registerSound("roll.stop");
    public static final RegistryObject<SoundEvent> SND_REWARD_COMMON = registerSound("reward.common");
    public static final RegistryObject<SoundEvent> SND_REWARD_RARE = registerSound("reward.rare");
    public static final RegistryObject<SoundEvent> SND_REWARD_LEGENDARY = registerSound("reward.legendary");
    public static final RegistryObject<SoundEvent> SND_REWARD_RECEIVE = registerSound("reward.receive");

    /** Tous les sons utilises par les animations des caisses (sons communs + sons thematiques). */
    public static final Map<Snd, RegistryObject<SoundEvent>> SND = new EnumMap<>(Snd.class);

    static {
        for (Snd s : Snd.values()) {
            SND.put(s, registerSound(s.id));
        }
    }

    /** Sons du bloc : pose custom, le reste en metal lourd (pas de bruit de porte !). */
    public static final SoundType CRATE_SOUND = new ForgeSoundType(1.0F, 1.0F,
            () -> SoundEvents.NETHERITE_BLOCK_BREAK,
            () -> SoundEvents.NETHERITE_BLOCK_STEP,
            SND_PLACE,
            () -> SoundEvents.NETHERITE_BLOCK_HIT,
            () -> SoundEvents.NETHERITE_BLOCK_FALL);

    // ------------------------------------------------------------------ particules
    public static final RegistryObject<ParticleType<SparkOptions>> SPARK = PARTICLES.register("spark",
            () -> new ParticleType<SparkOptions>(false, SparkOptions.DESERIALIZER) {
                @Override
                public Codec<SparkOptions> codec() {
                    return SparkOptions.CODEC;
                }
            });

    // ------------------------------------------------------------------ blocs & items
    public static final Map<CrateType, RegistryObject<CrateBlock>> CRATES = new EnumMap<>(CrateType.class);
    public static final Map<CrateType, RegistryObject<Item>> CRATE_ITEMS = new EnumMap<>(CrateType.class);
    public static final Map<CrateType, RegistryObject<Item>> KEYS = new EnumMap<>(CrateType.class);

    public static final RegistryObject<CratePartBlock> CRATE_PART = BLOCKS.register("crate_part",
            () -> new CratePartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    // forme propre a chaque caisse (lue sur le bloc principal) : pas de cache de forme
                    .dynamicShape()
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .sound(CRATE_SOUND)
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, e) -> false)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    static {
        for (CrateType type : CrateType.values()) {
            RegistryObject<CrateBlock> block = BLOCKS.register(type.blockId(),
                    () -> new CrateBlock(type, BlockBehaviour.Properties.of()
                            .mapColor(mapColor(type))
                            .strength(-1.0F, 3600000.0F)
                            .noLootTable()
                            .noOcclusion()
                            .lightLevel(s -> 12)
                            .sound(CRATE_SOUND)
                            .pushReaction(PushReaction.BLOCK)
                            .isValidSpawn((s, l, p, e) -> false)
                            .isRedstoneConductor((s, l, p) -> false)
                            .isSuffocating((s, l, p) -> false)
                            .isViewBlocking((s, l, p) -> false)));
            CRATES.put(type, block);
            CRATE_ITEMS.put(type, ITEMS.register(type.blockId(),
                    () -> new CrateBlockItem(block.get(), type, new Item.Properties().stacksTo(16).fireResistant())));
            KEYS.put(type, ITEMS.register(type.keyId(),
                    () -> new CrateKeyItem(type, new Item.Properties().stacksTo(64).fireResistant())));
        }
    }

    /** Outil des operateurs : clic droit sur une caisse pour modifier son butin. */
    public static final RegistryObject<Item> CRATE_EDITOR = ITEMS.register("crate_editor",
            () -> new fr.heraliz.crates.item.CrateEditorItem(new Item.Properties().stacksTo(1).fireResistant()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    /** Usine de l'entité de bloc (docs/METHODE_CLIENT_SERVEUR.md §4) : version serveur branchée par la partie serveur. */
    public static final UsineBlocEntite<CrateBlockEntity> CRATE_USINE = new UsineBlocEntite<>(CrateBlockEntity::new);
    public static final RegistryObject<BlockEntityType<CrateBlockEntity>> CRATE_BE = BLOCK_ENTITIES.register("crate",
            () -> BlockEntityType.Builder.of(CRATE_USINE,
                    CRATES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.heralizcrates"))
            .icon(() -> new ItemStack(CRATE_ITEMS.get(CrateType.GEMME).get()))
            .displayItems((params, output) -> {
                output.accept(CRATE_EDITOR.get());
                for (CrateType t : CrateType.values()) {
                    output.accept(CRATE_ITEMS.get(t).get());
                    output.accept(KEYS.get(t).get());
                }
            })
            .build());

    // ------------------------------------------------------------------ utilitaires
    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUND_BY_ID.computeIfAbsent(name,
                n -> SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, n))));
    }

    public static SoundEvent sound(Snd s) {
        return SND.get(s).get();
    }

    private static MapColor mapColor(CrateType t) {
        return switch (t) {
            case SUJET_N07 -> MapColor.COLOR_LIGHT_GREEN;
            case SYLVORN -> MapColor.PLANT;
            case MERE_DES_RUINES -> MapColor.COLOR_RED;
            case PORTEUR_DE_PESTE -> MapColor.COLOR_YELLOW;
            case VOTE -> MapColor.COLOR_BLUE;
            case QUETE -> MapColor.GOLD;
            case EVENT -> MapColor.COLOR_MAGENTA;
            case GEMME -> MapColor.DIAMOND;
            case LARGAGE -> MapColor.COLOR_GREEN;
            case TRAUMA -> MapColor.SNOW;
            case BUILD -> MapColor.COLOR_YELLOW;
            case STICKER -> MapColor.COLOR_LIGHT_BLUE;
            case CAMO -> MapColor.COLOR_MAGENTA;
            case SPECTACLE -> MapColor.COLOR_PINK;
        };
    }

    public static Item key(CrateType t) {
        return KEYS.get(t).get();
    }

    public static Item crateItem(CrateType t) {
        return CRATE_ITEMS.get(t).get();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        SOUNDS.register(bus);
        PARTICLES.register(bus);
        TABS.register(bus);
    }
}
