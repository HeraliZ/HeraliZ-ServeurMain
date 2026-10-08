package fr.heralisz.mammoth.dungeon;

import fr.heraliz.boss.pont.UsineBlocEntite;
import fr.heralisz.mammoth.HeraliszMammoth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Blocs de l'arène : autel du Cor, totems d'os (rituel), braseros de la tribu (alerte), mur de glace, décors 3D. */
public final class ModBlocks {
   private ModBlocks() {}

   public static final List<RegistryObject<Item>> TAB = new ArrayList<>();

   private static RegistryObject<Block> block(String name, Supplier<Block> b) {
      RegistryObject<Block> r = HeraliszMammoth.BLOCKS.register(name, b);
      TAB.add(HeraliszMammoth.ITEMS.register(name, () -> new BlockItem(r.get(), new Item.Properties())));
      return r;
   }

   public static final RegistryObject<Block> HORN_ALTAR = block("horn_altar", HornAltarBlock::new);
   /** Usine de l'autel : version neutre ici, version serveur (rituel, combat) branchée par serveur.PartieServeur. */
   public static final UsineBlocEntite<HornAltarBlockEntity> ALTAR_USINE = new UsineBlocEntite<>(HornAltarBlockEntity::new);
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<HornAltarBlockEntity>> ALTAR_BE = HeraliszMammoth.BLOCK_ENTITIES.register("horn_altar",
      () -> BlockEntityType.Builder.of(ALTAR_USINE, HORN_ALTAR.get()).build(null));

   public static final RegistryObject<Block> BONE_TOTEM = block("bone_totem", BoneTotemBlock::new);
   public static final RegistryObject<Block> TRIBE_BRAZIER = block("tribe_brazier", TribeBrazierBlock::new);
   public static final RegistryObject<Block> HEART_SOCKET = block("heart_socket", HeartSocketBlock::new);
   public static final RegistryObject<Block> SOUL_MAW = block("soul_maw", SoulMawBlock::new);
   public static final RegistryObject<Block> ICE_PRISON = block("ice_prison", IcePrisonBlock::new);
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<IcePrisonBlock.BE>> PRISON_BE = HeraliszMammoth.BLOCK_ENTITIES.register("ice_prison",
      () -> BlockEntityType.Builder.of(IcePrisonBlock.BE::new, ICE_PRISON.get()).build(null));
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<LitPropBE>> LIT_BE = HeraliszMammoth.BLOCK_ENTITIES.register("lit_prop",
      () -> BlockEntityType.Builder.of(LitPropBE::new, BONE_TOTEM.get(), TRIBE_BRAZIER.get(), HEART_SOCKET.get(), SOUL_MAW.get()).build(null));

   public static final RegistryObject<Block> GATE_ICE = block("gate_ice", IceGateBlock::new);
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<IceGateBlockEntity>> GATE_BE = HeraliszMammoth.BLOCK_ENTITIES.register("arena_gate",
      () -> BlockEntityType.Builder.of(IceGateBlockEntity::new, GATE_ICE.get()).build(null));

   /** Volume invisible et solide autour des grands décors (et bouchon du mur de glace). */
   public static final RegistryObject<Block> PROP_SOLID = HeraliszMammoth.BLOCKS.register("prop_solid", () -> new PropSolidBlock(
      BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
         .isViewBlocking((s, l, p) -> false)));

   // ------------------------------------------------------------------ maçonnerie du Cimetière (aucun bloc vanilla dans l'arène)
   private static BlockBehaviour.Properties stone(MapColor c, SoundType s) {
      return BlockBehaviour.Properties.of().mapColor(c).strength(-1.0F, 3600000.0F).sound(s).noLootTable().pushReaction(PushReaction.BLOCK);
   }

   public static final RegistryObject<Block> RIME_ROCK = block("rime_rock", () -> new Block(stone(MapColor.COLOR_GRAY, SoundType.DEEPSLATE)));
   public static final RegistryObject<Block> RIME_BRICKS = block("rime_bricks", () -> new Block(stone(MapColor.COLOR_GRAY, SoundType.DEEPSLATE_BRICKS)));
   public static final RegistryObject<Block> RIME_BRICK_STAIRS = block("rime_brick_stairs",
      () -> new FixedStairBlock(() -> RIME_BRICKS.get().defaultBlockState(), stone(MapColor.COLOR_GRAY, SoundType.DEEPSLATE_BRICKS)));
   public static final RegistryObject<Block> RIME_BRICK_SLAB = block("rime_brick_slab", () -> new SlabBlock(stone(MapColor.COLOR_GRAY, SoundType.DEEPSLATE_BRICKS)));
   public static final RegistryObject<Block> RIME_FLAGSTONE = block("rime_flagstone", () -> new Block(stone(MapColor.COLOR_GRAY, SoundType.DEEPSLATE_TILES)));
   /** Éclat de glace claire (débris des pics, stalactites et glace brisée) : jamais posé, seulement dessiné en morceaux. */
   public static final RegistryObject<Block> ICE_CHUNK = HeraliszMammoth.BLOCKS.register("ice_chunk",
      () -> new net.minecraft.world.level.block.HalfTransparentBlock(stone(MapColor.ICE, SoundType.GLASS).noOcclusion()));
   /** Glace qui mure l'entrée des passages secrets : elle s'effondre à la mort d'Ymirak (sorties de secours). */
   public static final RegistryObject<Block> EXIT_SEAL = block("exit_seal",
      () -> new ExitSealBlock(stone(MapColor.ICE, SoundType.GLASS).strength(-1.0F, 3600000.0F).noLootTable()));
   public static final RegistryObject<Block> GLACIER_ICE = block("glacier_ice", () -> new Block(stone(MapColor.ICE, SoundType.GLASS).friction(0.9F)));
   public static final RegistryObject<Block> GLACIER_GLOW = block("glacier_glow", () -> new SetPieceBlocks.AlertBlock(stone(MapColor.ICE, SoundType.GLASS).lightLevel(s -> 13)));
   public static final RegistryObject<Block> MELTWATER = block("meltwater", () -> new SetPieceBlocks.Meltwater(stone(MapColor.WATER, SoundType.GLASS)
      .lightLevel(s -> 9).noOcclusion()));
   public static final RegistryObject<Block> AURORA_VEIL = block("aurora_veil", () -> new SetPieceBlocks.Aurora(stone(MapColor.NONE, SoundType.GLASS)
      .noCollission().noOcclusion()));
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<SetPieceEntities.AuroraBE>> AURORA_BE = HeraliszMammoth.BLOCK_ENTITIES.register("aurora_veil",
      () -> BlockEntityType.Builder.of(SetPieceEntities.AuroraBE::new, AURORA_VEIL.get()).build(null));
   public static final RegistryObject<Block> FROZEN_TITAN = block("frozen_titan", () -> new SetPieceBlocks.Titan(stone(MapColor.ICE, SoundType.GLASS)
      .noOcclusion().lightLevel(s -> 7)));
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<SetPieceEntities.TitanBE>> TITAN_BE = HeraliszMammoth.BLOCK_ENTITIES.register("frozen_titan",
      () -> BlockEntityType.Builder.of(SetPieceEntities.TitanBE::new, FROZEN_TITAN.get()).build(null));
   public static final RegistryObject<Block> CLEAR_ICE = block("clear_ice", () -> new HalfTransparentBlock(stone(MapColor.ICE, SoundType.GLASS).noOcclusion()
      .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
   public static final RegistryObject<Block> PACKED_SNOW = block("packed_snow", () -> new Block(stone(MapColor.SNOW, SoundType.SNOW)));
   public static final RegistryObject<Block> FROZEN_EARTH = block("frozen_earth", () -> new Block(stone(MapColor.DIRT, SoundType.ROOTED_DIRT)));
   public static final RegistryObject<Block> BONE_BRICKS = block("bone_bricks", () -> new Block(stone(MapColor.SAND, SoundType.BONE_BLOCK)));
   public static final RegistryObject<Block> BONE_PILLAR = block("bone_pillar", () -> new RotatedPillarBlock(stone(MapColor.SAND, SoundType.BONE_BLOCK)));

   /** Décors « héros » (GeckoLib) : un bloc par type, une seule entité de bloc pour tous. */
   public static final Map<HeroKind, RegistryObject<Block>> HERO = new EnumMap<>(HeroKind.class);

   static {
      for (HeroKind k : HeroKind.values()) HERO.put(k, block(k.id, () -> new HeroPropBlock(k)));
   }

   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<HeroPropBE>> HERO_BE = HeraliszMammoth.BLOCK_ENTITIES.register("hero_prop",
      () -> BlockEntityType.Builder.of(HeroPropBE::new, HERO.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));


   /** Charge la classe (enregistrements) avant l'inscription des registres sur le bus. */
   public static void init() {}
}
