package fr.heralisz.bunker.keycard;

import fr.heralisz.bunker.HeraliszBunker;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** Cartes d'accès et lecteurs de cartes (verts, bleus, rouges). */
public final class KeycardRegistry {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszBunker.MODID);
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszBunker.MODID);
   public static final Map<AccessLevel, RegistryObject<Item>> CARDS = new EnumMap<>(AccessLevel.class);
   public static final Map<AccessLevel, RegistryObject<Block>> READERS = new EnumMap<>(AccessLevel.class);

   static {
      for (AccessLevel level : AccessLevel.values()) {
         CARDS.put(level, ITEMS.register("keycard_" + level.id, () -> new KeycardItem(level)));
         RegistryObject<Block> reader = BLOCKS.register("card_reader_" + level.id, () -> new CardReaderBlock(level,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(-1.0F, 3600000.0F).noOcclusion()
               .lightLevel(s -> switch (s.getValue(CardReaderBlock.STATUS)) {
                  case GRANTED, DENIED -> 7;
                  default -> 4;
               })));
         READERS.put(level, reader);
         ITEMS.register("card_reader_" + level.id, () -> new BlockItem(reader.get(), new Item.Properties()));
      }
   }

   public static void register(IEventBus bus) {
      BLOCKS.register(bus);
      ITEMS.register(bus);
   }

   private KeycardRegistry() {}
}
