package fr.heralisz.mammoth;

import fr.heraliz.boss.HeralizBoss;
import fr.heraliz.boss.pont.Parties;
import fr.heraliz.boss.pont.UsineEntite;
import fr.heralisz.mammoth.dungeon.ModBlocks;
import fr.heralisz.mammoth.entity.Mammoth;
import fr.heralisz.mammoth.item.TrophyItem;
import fr.heralisz.mammoth.item.WarHornItem;
import fr.heralisz.mammoth.sound.Sounds;
import fr.heralisz.mammoth.vfx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * HeralisZ - Ymirak, le Mammouth Mort : un boss à trois phases dans son arène (le Cimetière des Mammouths), réveillé par le
 * cor de guerre de la tribu posé sur l'autel.
 */
@Mod(HeraliszMammoth.MODID)
public class HeraliszMammoth {
   public static final String MODID = "heralisz_mammoth";

   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

   /** Usines des entités coupées en deux : version neutre ici, version serveur branchée par serveur.PartieServeur. */
   public static final UsineEntite<Mammoth> MAMMOTH_USINE = new UsineEntite<>(Mammoth::new);
   public static final UsineEntite<fr.heralisz.mammoth.entity.Draugr> DRAUGR_USINE = new UsineEntite<>(fr.heralisz.mammoth.entity.Draugr::new);
   public static final UsineEntite<fr.heralisz.mammoth.entity.GuardianSpear> GUARDIAN_SPEAR_USINE = new UsineEntite<>(fr.heralisz.mammoth.entity.GuardianSpear::new);

   public static final RegistryObject<EntityType<Mammoth>> MAMMOTH = ENTITIES.register("mammoth",
      () -> EntityType.Builder.of(MAMMOTH_USINE, MobCategory.MONSTER).sized(6.0F, 7.0F).fireImmune().clientTrackingRange(20).updateInterval(1)
         .build("mammoth"));

   public static final RegistryObject<EntityType<fr.heralisz.mammoth.entity.Draugr>> DRAUGR = ENTITIES.register("draugr",
      () -> EntityType.Builder.of(DRAUGR_USINE, MobCategory.MONSTER).sized(0.8F, 2.5F).clientTrackingRange(10)
         .build("draugr"));

   public static final RegistryObject<EntityType<fr.heralisz.mammoth.entity.GuardianSpear>> GUARDIAN_SPEAR = ENTITIES.register("guardian_spear",
      () -> EntityType.Builder.<fr.heralisz.mammoth.entity.GuardianSpear>of(GUARDIAN_SPEAR_USINE, MobCategory.MISC)
         .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1).build("guardian_spear"));

   /** Lame de test : chaque coup ôte un tiers de la vie d'Ymirak (pour parcourir les phases). */
   public static final RegistryObject<Item> DEBUG_BLADE = ITEMS.register("debug_blade",
      () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.NETHERITE, 3, -2.4F, new Item.Properties().rarity(Rarity.EPIC)));
   public static final RegistryObject<EntityType<fr.heralisz.mammoth.entity.SpectralHand>> SPECTRAL_HAND = ENTITIES.register("spectral_hand",
      () -> EntityType.Builder.<fr.heralisz.mammoth.entity.SpectralHand>of(fr.heralisz.mammoth.entity.SpectralHand::new, MobCategory.MISC)
         .sized(1.0F, 2.0F).clientTrackingRange(10).updateInterval(20).noSave().build("spectral_hand"));

   public static final RegistryObject<Item> WAR_HORN = ITEMS.register("war_horn",
      () -> new WarHornItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
   public static final RegistryObject<Item> MAMMOTH_EGG = ITEMS.register("mammoth_spawn_egg",
      () -> new ForgeSpawnEggItem(MAMMOTH, 0x16181C, 0x7AC8FF, new Item.Properties()));
   public static final RegistryObject<Item> YMIRAK_TUSK = ITEMS.register("ymirak_tusk",
      () -> new TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> WINTER_HEART = ITEMS.register("winter_heart",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> FROZEN_FUR = ITEMS.register("frozen_fur",
      () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON), false));
   public static final RegistryObject<Item> BLACK_ICE_SHARD = ITEMS.register("black_ice_shard",
      () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE), false));

   public HeraliszMammoth() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ModBlocks.init();
      ENTITIES.register(bus);
      ITEMS.register(bus);
      BLOCKS.register(bus);
      BLOCK_ENTITIES.register(bus);
      Sounds.SOUNDS.register(bus);
      Vfx.register();
      fr.heralisz.mammoth.fx.Particles.register(bus);
      bus.addListener(this::attributes);
      ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, MammothConfig.SPEC);
      // onglet créatif unique « HeraliZ · Boss » (mod heraliz_boss) : ce boss y est un groupe
      // groupe(s) de l'onglet HeraliZ · Boss : déclarés par HeralizBoss (ordre fixe), contenu ci-dessous
      // parties serveur (logique) et client (affichage), si elles sont dans ce jar
      Parties.serveur(MODID, "fr.heralisz.mammoth.serveur.PartieServeur", bus);
      Parties.client(MODID, "fr.heralisz.mammoth.client.PartieClient", bus);
   }

   private void attributes(EntityAttributeCreationEvent event) {
      event.put(MAMMOTH.get(), Mammoth.createAttributes().build());
      event.put(DRAUGR.get(), fr.heralisz.mammoth.entity.Draugr.createAttributes().build());
   }

   /** Icône du groupe « Ymirak » de l'onglet HeraliZ · Boss (groupe déclaré par HeralizBoss, ordre fixe). */
   public static ItemStack iconeOnglet() {
      return new ItemStack(WAR_HORN.get());
   }

   /** Contenu du groupe « Ymirak » de l'onglet HeraliZ · Boss (inchangé). */
   public static void remplirOnglet(net.minecraft.world.item.CreativeModeTab.Output out) {
      out.accept(WAR_HORN.get());
      out.accept(DEBUG_BLADE.get());
      out.accept(MAMMOTH_EGG.get());
      out.accept(YMIRAK_TUSK.get());
      out.accept(WINTER_HEART.get());
      out.accept(FROZEN_FUR.get());
      out.accept(BLACK_ICE_SHARD.get());
      for (RegistryObject<Item> it : ModBlocks.TAB) out.accept(it.get());
   }
}
