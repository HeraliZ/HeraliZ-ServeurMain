package fr.heralisz.plague.dungeon;

import fr.heraliz.boss.pont.UsineBlocEntite;
import fr.heralisz.plague.HeraliszPlague;
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

/** Blocs de la Station de Quarantaine : son cœur caché (la salle), volume invisible des grands décors, décors. */
public final class ModBlocks {
   private ModBlocks() {}

   public static final List<RegistryObject<Item>> TAB = new ArrayList<>();

   private static RegistryObject<Block> block(String name, Supplier<Block> b) {
      RegistryObject<Block> r = HeraliszPlague.BLOCKS.register(name, b);
      TAB.add(HeraliszPlague.ITEMS.register(name, () -> new BlockItem(r.get(), new Item.Properties())));
      return r;
   }

   private static BlockBehaviour.Properties stone(MapColor c, SoundType s) {
      return BlockBehaviour.Properties.of().mapColor(c).strength(-1.0F, 3600000.0F).sound(s).noLootTable().pushReaction(PushReaction.BLOCK);
   }

   /** Caché sous la salle des vannes : il garde le Porteur de Peste, mène le rituel, remet la station en état. */
   public static final RegistryObject<Block> STATION_HEART = block("station_heart", () -> new StationHeartBlock(stone(MapColor.WOOL, SoundType.WOOL)));
   /** Usine de la salle : version neutre ici, version serveur (StationHallBEServeur) branchée par serveur.PartieServeur. */
   public static final UsineBlocEntite<StationHallBE> STATION_USINE = new UsineBlocEntite<>(StationHallBE::new);
   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<StationHallBE>> STATION_BE = HeraliszPlague.BLOCK_ENTITIES.register("station_heart",
      () -> BlockEntityType.Builder.of(STATION_USINE, STATION_HEART.get()).build(null));

   /** Volume invisible et solide (grands décors). */
   public static final RegistryObject<Block> PROP_SOLID = HeraliszPlague.BLOCKS.register("prop_solid", () -> new PropSolidBlock(
      BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
         .isViewBlocking((s, l, p) -> false)));

   /** Décors « héros » (GeckoLib) : un bloc par type, une seule entité de bloc pour tous. */
   public static final Map<HeroKind, RegistryObject<Block>> HERO = new EnumMap<>(HeroKind.class);

   static {
      for (HeroKind k : HeroKind.values()) HERO.put(k, block(k.id, () -> new HeroPropBlock(k)));
   }

   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<HeroPropBE>> HERO_BE = HeraliszPlague.BLOCK_ENTITIES.register("hero_prop",
      () -> BlockEntityType.Builder.of(HeroPropBE::new, HERO.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

   public static Block hero(HeroKind k) {
      return HERO.get(k).get();
   }

   /** Charge la classe (enregistrements) avant l'inscription des registres sur le bus. */
   public static void init() {}
}
