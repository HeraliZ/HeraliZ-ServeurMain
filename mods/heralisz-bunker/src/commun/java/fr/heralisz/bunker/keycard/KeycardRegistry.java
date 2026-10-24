package fr.heralisz.bunker.keycard;

import fr.heralisz.bunker.HeraliszBunker;
import fr.heralisz.bunker.salle.BaguetteSalleItem;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Cartes d'accès, leurs fragments numérotés 1 à 4 et lecteurs de cartes (verts, bleus, rouges). */
public final class KeycardRegistry {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszBunker.MODID);
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HeraliszBunker.MODID);
   public static final Map<AccessLevel, RegistryObject<Item>> CARDS = new EnumMap<>(AccessLevel.class);
   public static final Map<AccessLevel, RegistryObject<Block>> READERS = new EnumMap<>(AccessLevel.class);
   /** Fragments de chaque carte, numéros 1 à 4 dans l'ordre (keycard_fragment_<couleur>_<n>). */
   public static final Map<AccessLevel, List<RegistryObject<Item>>> FRAGMENTS = new EnumMap<>(AccessLevel.class);
   /** Baguette du staff pour délimiter les salles à carte (/salle). */
   public static final RegistryObject<Item> BAGUETTE_SALLE;

   static {
      for (AccessLevel level : AccessLevel.values()) {
         CARDS.put(level, ITEMS.register("keycard_" + level.id, () -> new KeycardItem(level)));
         List<RegistryObject<Item>> fragments = new ArrayList<>();
         for (int n = 1; n <= KeycardFragmentItem.MORCEAUX; n++) {
            int numero = n;
            fragments.add(ITEMS.register("keycard_fragment_" + level.id + "_" + n, () -> new KeycardFragmentItem(level, numero)));
         }
         FRAGMENTS.put(level, List.copyOf(fragments));
         RegistryObject<Block> reader = BLOCKS.register("card_reader_" + level.id, () -> new CardReaderBlock(level,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(-1.0F, 3600000.0F).noOcclusion()
               .lightLevel(s -> switch (s.getValue(CardReaderBlock.STATUS)) {
                  case GRANTED, DENIED -> 7;
                  default -> 4;
               })));
         READERS.put(level, reader);
         ITEMS.register("card_reader_" + level.id, () -> new BlockItem(reader.get(), new Item.Properties()));
      }
      BAGUETTE_SALLE = ITEMS.register("baguette_salle", BaguetteSalleItem::new);
   }

   /** Fragment n (1 à 4) de la carte de ce niveau (registre déjà rempli). */
   public static Item fragment(AccessLevel level, int n) {
      return FRAGMENTS.get(level).get(n - 1).get();
   }

   public static void register(IEventBus bus) {
      BLOCKS.register(bus);
      ITEMS.register(bus);
   }

   private KeycardRegistry() {}
}
