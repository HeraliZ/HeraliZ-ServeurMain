package fr.heralisz.n07.decor;

import fr.heraliz.boss.pont.Usine;
import fr.heralisz.n07.HeraliszN07;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Blocs, objets, entité de bloc et onglet créatif des décors du spawn (un bloc par DecorKind). */
public final class DecorRegistry {
   private DecorRegistry() {}

   /** Usines (docs/METHODE_CLIENT_SERVEUR.md §5.3) : versions serveur (cases de collision et lumières posées / retirées, chaîne
    * rallongée, case annexe qui casse tout le décor) branchées par fr.heralisz.n07.serveur.PartieServeur. */
   public static final Usine<DecorKind, Block> DECOR_USINE = new Usine<>(DecorBlock::new);
   public static final Usine<BlockBehaviour.Properties, Block> PART_USINE = new Usine<>(DecorPartBlock::new);

   public static final Map<DecorKind, RegistryObject<Block>> BLOCKS = new EnumMap<>(DecorKind.class);
   public static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();

   static {
      for (DecorKind k : DecorKind.values()) {
         RegistryObject<Block> b = HeraliszN07.BLOCKS.register(k.spec.id, () -> DECOR_USINE.apply(k));
         BLOCKS.put(k, b);
         ITEMS.add(HeraliszN07.ITEMS.register(k.spec.id, () -> new DecorItem(b.get(), k, new Item.Properties())));
      }
   }

   public static final RegistryObject<Block> PART = HeraliszN07.BLOCKS.register("decor_part", () -> PART_USINE.apply(
      BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

   @SuppressWarnings("ConstantConditions")
   public static final RegistryObject<BlockEntityType<DecorBlockEntity>> BE = HeraliszN07.BLOCK_ENTITIES.register("decor",
      () -> BlockEntityType.Builder.of(DecorBlockEntity::new, BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

   /** Contenu du groupe « Décors du spawn » de l'onglet HeraliZ · Boss (ancien onglet heralisz_n07:decor). */
   public static void remplir(CreativeModeTab.Output out) {
      for (RegistryObject<Item> it : ITEMS) out.accept(it.get());
   }

   /** Charge la classe (enregistrements) avant que les registres différés ne soient branchés sur le bus. */
   public static void init() {}
}
