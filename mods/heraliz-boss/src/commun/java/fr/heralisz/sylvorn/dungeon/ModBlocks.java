package fr.heralisz.sylvorn.dungeon;

import fr.heraliz.boss.pont.UsineBlocEntite;
import fr.heralisz.sylvorn.HeraliszSylvorn;
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

/** Blocs de l'Arbre-Monde : son cœur caché (la salle), volume invisible des grands décors, décors. */
public final class ModBlocks {
   private ModBlocks() {}

   public static final List<RegistryObject<Item>> TAB = new ArrayList<>();

   private static RegistryObject<Block> block(String name, Supplier<Block> b) {
      RegistryObject<Block> r = HeraliszSylvorn.BLOCKS.register(name, b);
      TAB.add(HeraliszSylvorn.ITEMS.register(name, () -> new BlockItem(r.get(), new Item.Properties())));
      return r;
   }

   private static BlockBehaviour.Properties stone(MapColor c, SoundType s) {
      return BlockBehaviour.Properties.of().mapColor(c).strength(-1.0F, 3600000.0F).sound(s).noLootTable().pushReaction(PushReaction.BLOCK);
   }

   /** Caché sous le sol du tronc : il garde Sylvorn, mène le rituel, remet l'Arbre en état. */
   public static final RegistryObject<Block> TREE_HEART = block("tree_heart", () -> new TreeHeartBlock(stone(MapColor.WOOL, SoundType.WOOL)));
   /** Usine de l'entité de bloc de la salle : version serveur (TreeHallBEServeur) branchée par serveur.PartieServeur. */
   public static final UsineBlocEntite<TreeHallBE> TREE_USINE = new UsineBlocEntite<>(TreeHallBE::new);
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<TreeHallBE>> TREE_BE = HeraliszSylvorn.BLOCK_ENTITIES.register("tree_heart",
      () -> BlockEntityType.Builder.of(TREE_USINE, TREE_HEART.get()).build(null));

   /** Volume invisible et solide (grands décors). */
   public static final RegistryObject<Block> PROP_SOLID = HeraliszSylvorn.BLOCKS.register("prop_solid", () -> new PropSolidBlock(
      BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
         .isViewBlocking((s, l, p) -> false)));

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
   public static final RegistryObject<BlockEntityType<HeroPropBE>> HERO_BE = HeraliszSylvorn.BLOCK_ENTITIES.register("hero_prop",
      () -> BlockEntityType.Builder.of(HeroPropBE::new, HERO.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

   public static Block hero(HeroKind k) {
      return HERO.get(k).get();
   }

   /** Charge la classe (enregistrements) avant l'inscription des registres sur le bus. */
   public static void init() {}
}
