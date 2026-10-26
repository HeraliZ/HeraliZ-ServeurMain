package fr.heralisz.mere.dungeon;

import fr.heraliz.boss.pont.UsineBlocEntite;
import fr.heralisz.mere.HeraliszMere;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Blocs du Nid : cœur du nid (la salle), pierre de soie, briques du temple englouti, sacs d'œufs, mur de toile, décors. */
public final class ModBlocks {
   private ModBlocks() {}

   public static final List<RegistryObject<Item>> TAB = new ArrayList<>();

   private static RegistryObject<Block> block(String name, Supplier<Block> b) {
      RegistryObject<Block> r = HeraliszMere.BLOCKS.register(name, b);
      TAB.add(HeraliszMere.ITEMS.register(name, () -> new BlockItem(r.get(), new Item.Properties())));
      return r;
   }

   private static BlockBehaviour.Properties stone(MapColor c, SoundType s) {
      return BlockBehaviour.Properties.of().mapColor(c).strength(-1.0F, 3600000.0F).sound(s).noLootTable().pushReaction(PushReaction.BLOCK);
   }

   /** Le cœur du nid, sous l'autel : il garde la Mère, mène le rituel, remet le nid en état. */
   public static final RegistryObject<Block> NEST_HEART = block("nest_heart", () -> new NestHeartBlock(stone(MapColor.WOOL, SoundType.WOOL)));
   /** Usine de l'entité de bloc coupée en deux : version neutre ici, version serveur branchée par serveur.PartieServeur. */
   public static final UsineBlocEntite<NestHallBE> NEST_USINE = new UsineBlocEntite<>(NestHallBE::new);
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<NestHallBE>> NEST_BE = HeraliszMere.BLOCK_ENTITIES.register("nest_heart",
      () -> BlockEntityType.Builder.of(NEST_USINE, NEST_HEART.get()).build(null));

   /** Volume invisible et solide (grands décors). */
   public static final RegistryObject<Block> PROP_SOLID = HeraliszMere.BLOCKS.register("prop_solid", () -> new PropSolidBlock(
      BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
         .isViewBlocking((s, l, p) -> false)));

   public static final RegistryObject<Block> SILK_STONE = block("silk_stone", () -> new Block(stone(MapColor.WOOL, SoundType.WOOL)));
   public static final RegistryObject<Block> RUIN_BRICKS = block("ruin_bricks", () -> new Block(stone(MapColor.STONE, SoundType.STONE)));
   /** Sac d'œufs collé aux parois : terne, puis il palpite et luit quand la Mère s'éveille. */
   public static final RegistryObject<Block> BROOD_SAC = block("brood_sac", () -> new AlertBlock(stone(MapColor.COLOR_ORANGE, SoundType.SLIME_BLOCK)
      .lightLevel(s -> s.getValue(AlertBlock.ALERT) ? 10 : 2)));
   /** Mur de toile tissé en travers de la galerie : il scelle le nid pendant le combat. */
   public static final RegistryObject<Block> WEB_WALL = block("web_wall", () -> new Block(stone(MapColor.WOOL, SoundType.WOOL).noOcclusion()
      .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

   /** Décors immobiles (modèles cuits dans les chunks). */
   public static final Map<StaticKind, RegistryObject<Block>> STATIC = new EnumMap<>(StaticKind.class);

   static {
      for (StaticKind k : StaticKind.values()) STATIC.put(k, block(k.id, () -> new StaticPropBlock(k)));
   }

   /** Décors « héros » (GeckoLib) : un bloc par type, une seule entité de bloc pour tous. */
   public static final Map<HeroKind, RegistryObject<Block>> HERO = new EnumMap<>(HeroKind.class);

   static {
      for (HeroKind k : HeroKind.values()) HERO.put(k, block(k.id, () -> new HeroPropBlock(k)));
   }

   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<HeroPropBE>> HERO_BE = HeraliszMere.BLOCK_ENTITIES.register("hero_prop",
      () -> BlockEntityType.Builder.of(HeroPropBE::new, HERO.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

   public static Block hero(HeroKind k) {
      return HERO.get(k).get();
   }

   /** Charge la classe (enregistrements) avant l'inscription des registres sur le bus. */
   public static void init() {}
}
