package fr.heralisz.n07.dungeon;

import fr.heraliz.boss.pont.Usine;
import fr.heraliz.boss.pont.UsineBlocEntite;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.item.SummonItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Blocs, objets et entité de bloc des donjons d'invocation : sanctuaires, blocs rituels (qui s'éveillent avec le rituel), blocs
 * de construction propres à chaque donjon et objets d'invocation.
 */
public final class DungeonRegistry {
   private DungeonRegistry() {}

   /** Tout ce qui va dans l'onglet créatif, dans l'ordre. */
   public static final List<RegistryObject<Item>> TAB = new ArrayList<>();

   private static RegistryObject<Block> block(String name, Supplier<Block> b) {
      RegistryObject<Block> r = HeraliszN07.BLOCKS.register(name, b);
      TAB.add(HeraliszN07.ITEMS.register(name, () -> new BlockItem(r.get(), new Item.Properties())));
      return r;
   }

   private static RegistryObject<Item> summon(String name) {
      RegistryObject<Item> r = HeraliszN07.ITEMS.register(name, () -> new SummonItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));
      TAB.add(r);
      return r;
   }

   private static BlockBehaviour.Properties stone(MapColor c) {
      return BlockBehaviour.Properties.of().mapColor(c).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.5F, 9.0F);
   }

   private static BlockBehaviour.Properties metal(MapColor c) {
      return BlockBehaviour.Properties.of().mapColor(c).requiresCorrectToolForDrops().strength(4.0F, 9.0F).sound(SoundType.METAL);
   }

   private static BlockBehaviour.Properties lamp(BlockBehaviour.Properties p, int lit) {
      return p.lightLevel(s -> s.getValue(RitualLampBlock.LIT) ? lit : 0);
   }

   // ------------------------------------------------------------------ usines (docs/METHODE_CLIENT_SERVEUR.md §4) : versions serveur
   // branchées par fr.heralisz.n07.serveur.PartieServeur (rituel du terminal, buses et luminaires, effets des rigoles, téléporteur)
   public static final UsineBlocEntite<ShrineBlockEntity> SHRINE_USINE = new UsineBlocEntite<>(ShrineBlockEntity::new);
   public static final UsineBlocEntite<PropBlockEntity> PROP_USINE = new UsineBlocEntite<>(PropBlockEntity::new);
   public static final Usine<RadChannelBlock.Params, Block> CHANNEL_USINE = new Usine<>(RadChannelBlock::new);
   public static final Usine<BlockBehaviour.Properties, Block> SLUDGE_USINE = new Usine<>(SludgeBlock::new);
   public static final Usine<Void, Block> TELEPORTER_935_USINE = Usine.simple(Teleporter935Block::new);

   // ------------------------------------------------------------------ objets d'invocation
   public static final RegistryObject<Item> N07_KEYCARD = summon("n07_keycard");

   // ------------------------------------------------------------------ sanctuaires
   public static final RegistryObject<Block> N07_TERMINAL = block("n07_terminal", () -> new ShrineBlock(DungeonKind.N07));

   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<ShrineBlockEntity>> SHRINE_BE = HeraliszN07.BLOCK_ENTITIES.register("shrine",
      () -> BlockEntityType.Builder.of(SHRINE_USINE, N07_TERMINAL.get()).build(null));

   // ------------------------------------------------------------------ décors animés
   public static final RegistryObject<Block> WASTE_PIPE = block("waste_pipe", () -> new PropBlock(PropKind.WASTE_PIPE));
   public static final RegistryObject<Block> RAD_VAT = block("rad_vat", () -> new PropBlock(PropKind.RAD_VAT));
   public static final RegistryObject<Block> SLUDGE_PIPE = block("sludge_pipe", () -> new PropBlock(PropKind.SLUDGE_PIPE));
   public static final RegistryObject<Block> SLUDGE_VAT = block("sludge_vat", () -> new PropBlock(PropKind.SLUDGE_VAT));
   public static final RegistryObject<Block> KNIGHT_STATUE = block("knight_statue", () -> new PropBlock(PropKind.KNIGHT_STATUE));
   public static final RegistryObject<Block> SYLVAN_TOTEM = block("sylvan_totem", () -> new PropBlock(PropKind.SYLVAN_TOTEM));
   public static final RegistryObject<Block> WRAPPED_VICTIM = block("wrapped_victim", () -> new PropBlock(PropKind.WRAPPED_VICTIM));
   public static final RegistryObject<Block> CONTAINMENT_TANK = block("containment_tank", () -> new PropBlock(PropKind.CONTAINMENT_TANK));
   public static final RegistryObject<Block> VENT_FAN = block("vent_fan", () -> new PropBlock(PropKind.VENT_FAN));
   public static final RegistryObject<Block> GIANT_FAN = block("giant_fan", () -> new PropBlock(PropKind.GIANT_FAN));
   // luminaires suspendus
   public static final RegistryObject<Block> CAGE_LAMP = block("cage_lamp", () -> new LampBlock(PropKind.CAGE_LAMP));
   public static final RegistryObject<Block> CHANDELIER = block("chandelier", () -> new LampBlock(PropKind.CHANDELIER));
   public static final RegistryObject<Block> SPORE_LANTERN = block("spore_lantern", () -> new LampBlock(PropKind.SPORE_LANTERN));
   public static final RegistryObject<Block> BROOD_LAMP = block("brood_lamp", () -> new LampBlock(PropKind.BROOD_LAMP));

   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<PropBlockEntity>> PROP_BE = HeraliszN07.BLOCK_ENTITIES.register("prop",
      () -> BlockEntityType.Builder.of(PROP_USINE, WASTE_PIPE.get(), RAD_VAT.get(), SLUDGE_PIPE.get(), SLUDGE_VAT.get(),
         KNIGHT_STATUE.get(), SYLVAN_TOTEM.get(), WRAPPED_VICTIM.get(), CAGE_LAMP.get(), CHANDELIER.get(), SPORE_LANTERN.get(),
         BROOD_LAMP.get(), CONTAINMENT_TANK.get(), VENT_FAN.get(), GIANT_FAN.get()).build(null));

   /** Volume invisible des grands décors. */
   public static final RegistryObject<Block> PROP_SOLID = HeraliszN07.BLOCKS.register("prop_solid", () -> new PropSolidBlock(
      BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
         .isViewBlocking((s, l, p) -> false)));

   /** Rigole de liquide radioactif. */
   public static final RegistryObject<Block> RAD_CHANNEL = block("rad_channel", () -> CHANNEL_USINE.apply(new RadChannelBlock.Params(BlockBehaviour.Properties.of()
      .mapColor(MapColor.COLOR_LIGHT_GREEN).requiresCorrectToolForDrops().strength(3.0F, 9.0F).sound(SoundType.STONE).noOcclusion()
      .lightLevel(s -> 10).isViewBlocking((s, l, p) -> false), false, false)));
   /** Rigole de boue toxique (station de quarantaine). */
   public static final RegistryObject<Block> SLUDGE_CHANNEL = block("sludge_channel", () -> CHANNEL_USINE.apply(new RadChannelBlock.Params(BlockBehaviour.Properties.of()
      .mapColor(MapColor.TERRACOTTA_GREEN).requiresCorrectToolForDrops().strength(3.0F, 9.0F).sound(SoundType.STONE).noOcclusion()
      .lightLevel(s -> 5).isViewBlocking((s, l, p) -> false), true, false)));

   /** Rivière de lave (forge engloutie) : la surface coule, brûle ceux qui y marchent. */
   public static final RegistryObject<Block> LAVA_CHANNEL = block("lava_channel", () -> CHANNEL_USINE.apply(new RadChannelBlock.Params(BlockBehaviour.Properties.of()
      .mapColor(MapColor.FIRE).requiresCorrectToolForDrops().strength(3.0F, 9.0F).sound(SoundType.BASALT).noOcclusion()
      .lightLevel(s -> 15).isViewBlocking((s, l, p) -> false), false, true)));

   // ------------------------------------------------------------------ blocs rituels
   public static final RegistryObject<Block> ALARM_LAMP = block("alarm_lamp", () -> new RitualLampBlock(
      lamp(metal(MapColor.COLOR_RED).strength(3.0F, 9.0F), 12), DungeonKind.N07, Block.box(0, 0, 0, 16, 16, 16)));

   // ------------------------------------------------------------------ blocs de construction
   public static final RegistryObject<Block> HAZARD_STRIPES = block("hazard_stripes", () -> new Block(metal(MapColor.COLOR_YELLOW)));
   // Secteur 7
   public static final RegistryObject<Block> LAB_PANEL = block("lab_panel", () -> new Block(metal(MapColor.QUARTZ)));
   public static final RegistryObject<Block> LAB_TILE = block("lab_tile", () -> new Block(stone(MapColor.COLOR_LIGHT_GRAY)));
   public static final RegistryObject<Block> LAB_PANEL_STAINED = block("lab_panel_stained", () -> new Block(metal(MapColor.TERRACOTTA_GREEN)));
   public static final RegistryObject<Block> LAB_LIGHT = block("lab_light", () -> new SwitchLightBlock(metal(MapColor.QUARTZ).strength(2.0F).sound(SoundType.GLASS), 15));
   public static final RegistryObject<Block> CONTAINMENT_GLASS = block("containment_glass", () -> new GlassBlock(
      BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(6.0F, 12.0F).sound(SoundType.GLASS).noOcclusion()
         .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
         .isViewBlocking((s, l, p) -> false)));
   // Station de quarantaine
   public static final RegistryObject<Block> RUSTED_PLATE = block("rusted_plate", () -> new Block(metal(MapColor.TERRACOTTA_BROWN)));
   public static final RegistryObject<Block> STAINED_TILES = block("stained_tiles", () -> new Block(stone(MapColor.TERRACOTTA_GREEN)));
   public static final RegistryObject<Block> TOXIC_SLUDGE = block("toxic_sludge", () -> SLUDGE_USINE.apply(BlockBehaviour.Properties.of()
      .mapColor(MapColor.COLOR_LIGHT_GREEN).strength(1.0F).sound(SoundType.SLIME_BLOCK).lightLevel(s -> 7).noOcclusion().pushReaction(PushReaction.DESTROY)));
   // Chapelle du Serment
   public static final RegistryObject<Block> OATH_BRICKS = block("oath_bricks", () -> new Block(stone(MapColor.COLOR_BLACK).sound(SoundType.DEEPSLATE_BRICKS)));
   public static final RegistryObject<Block> OATH_TILES = block("oath_tiles", () -> new Block(stone(MapColor.COLOR_BLACK).sound(SoundType.DEEPSLATE_TILES)));
   public static final RegistryObject<Block> CHAPEL_PILLAR = block("chapel_pillar", () -> new RotatedPillarBlock(stone(MapColor.COLOR_BLACK)
      .sound(SoundType.DEEPSLATE_BRICKS)));
   /** Poutre de la voûte : le Chevalier les abat sur les joueurs (elles sont remises en place après le combat). */
   public static final RegistryObject<Block> OATH_BEAM = block("oath_beam", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
      .mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASS).strength(3.0F, 9.0F).sound(SoundType.WOOD)));
   // Tertre sylvestre
   public static final RegistryObject<Block> ANCIENT_BARK = block("ancient_bark", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
      .mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD)));
   public static final RegistryObject<Block> SACRED_MOSS = block("sacred_moss", () -> new Block(BlockBehaviour.Properties.of()
      .mapColor(MapColor.COLOR_GREEN).strength(0.6F).sound(SoundType.MOSS)));
   public static final RegistryObject<Block> GLOWROOT = block("glowroot", () -> new GlowrootBlock(BlockBehaviour.Properties.of()
      .mapColor(MapColor.COLOR_LIGHT_GREEN).noCollission().instabreak().sound(SoundType.HANGING_ROOTS).lightLevel(s -> 10).noOcclusion()
      .pushReaction(PushReaction.DESTROY)));
   // Nid des Ruines
   public static final RegistryObject<Block> SILK_STONE = block("silk_stone", () -> new Block(stone(MapColor.WOOL).sound(SoundType.WOOL)));
   public static final RegistryObject<Block> RUIN_BRICKS = block("ruin_bricks", () -> new Block(stone(MapColor.STONE).sound(SoundType.STONE)));

   /** Teleporteur du Groupe 935 (retour a la carte apres la victoire, mode Zombies). */
   public static final RegistryObject<Block> TELEPORTER_935 = block("teleporter_935", TELEPORTER_935_USINE::creer);

   // ------------------------------------------------------------------ alerte : sirènes, grilles d'arène
   public static final RegistryObject<Block> SIREN = block("siren", SirenBlock::new);
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<SirenBlockEntity>> SIREN_BE = HeraliszN07.BLOCK_ENTITIES.register("siren",
      () -> BlockEntityType.Builder.of(SirenBlockEntity::new, SIREN.get()).build(null));
   public static final RegistryObject<Block> GATE_SHUTTER = block("gate_shutter", () -> new ArenaGateBlock(ArenaGateBlock.Style.SHUTTER));
   public static final RegistryObject<Block> GATE_RUSTED = block("gate_rusted", () -> new ArenaGateBlock(ArenaGateBlock.Style.RUSTED));
   public static final RegistryObject<Block> GATE_PORTCULLIS = block("gate_portcullis", () -> new ArenaGateBlock(ArenaGateBlock.Style.PORTCULLIS));
   public static final RegistryObject<Block> GATE_ROOTS = block("gate_roots", () -> new ArenaGateBlock(ArenaGateBlock.Style.ROOTS));
   public static final RegistryObject<Block> GATE_WEB = block("gate_web", () -> new ArenaGateBlock(ArenaGateBlock.Style.WEB));
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<ArenaGateBlockEntity>> GATE_BE = HeraliszN07.BLOCK_ENTITIES.register("arena_gate",
      () -> BlockEntityType.Builder.of(ArenaGateBlockEntity::new, GATE_SHUTTER.get(), GATE_RUSTED.get(), GATE_PORTCULLIS.get(), GATE_ROOTS.get(),
         GATE_WEB.get()).build(null));

   // feux d'alerte : brasero (chapelle), feu follet (tertre), sac d'œufs (nid) — allumés pendant le combat
   public static final RegistryObject<Block> ALERT_BRAZIER = block("alert_brazier", () -> new AlertLightBlock(AlertLightBlock.Style.BRAZIER));
   public static final RegistryObject<Block> ALERT_WISP = block("alert_wisp", () -> new AlertLightBlock(AlertLightBlock.Style.WISP));
   public static final RegistryObject<Block> ALERT_EGGSAC = block("alert_eggsac", () -> new AlertLightBlock(AlertLightBlock.Style.EGGSAC));

   // ------------------------------------------------------------------ décors immobiles (modèles vanilla, tools/props/static.py)
   public static final java.util.Map<StaticPropKind, RegistryObject<Block>> STATIC = new java.util.EnumMap<>(StaticPropKind.class);

   static {
      for (StaticPropKind k : StaticPropKind.values()) STATIC.put(k, block(k.id, () -> new StaticPropBlock(k)));
   }

   /** Charge la classe (enregistrements) avant l'inscription des registres sur le bus. */
   public static void init() {}
}
