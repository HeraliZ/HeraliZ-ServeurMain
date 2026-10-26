package fr.heralisz.mere;

import fr.heraliz.boss.HeralizBoss;
import fr.heraliz.boss.pont.Parties;
import fr.heraliz.boss.pont.UsineEntite;
import fr.heralisz.mere.dungeon.ModBlocks;
import fr.heralisz.mere.entity.RuinMother;
import fr.heralisz.mere.item.TrophyItem;
import fr.heralisz.mere.sound.Sounds;
import fr.heralisz.mere.vfx.Vfx;
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
 * HeralisZ - La Mère des Ruines : une araignée géante à trois phases dans son Nid (un temple englouti sous une grotte), réveillée
 * par l'offrande d'un œuf sur l'autel de couvée ; cinématiques d'entrée, d'éclosion, de phases et de mort ; décors 3D animés.
 */
@Mod(HeraliszMere.MODID)
public class HeraliszMere {
   public static final String MODID = "heralisz_mere";

   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

   /** Usines des entités coupées en deux : version neutre ici, version serveur branchée par serveur.PartieServeur. */
   public static final UsineEntite<RuinMother> RUIN_MOTHER_USINE = new UsineEntite<>(RuinMother::new);
   public static final UsineEntite<fr.heralisz.mere.entity.Spiderling> SPIDERLING_USINE = new UsineEntite<>(fr.heralisz.mere.entity.Spiderling::new);
   public static final UsineEntite<fr.heralisz.mere.entity.WebGlob> WEB_GLOB_USINE = new UsineEntite<>(fr.heralisz.mere.entity.WebGlob::new);

   public static final RegistryObject<EntityType<RuinMother>> RUIN_MOTHER = ENTITIES.register("ruin_mother",
      () -> EntityType.Builder.of(RUIN_MOTHER_USINE, MobCategory.MONSTER).sized(4.2F, 3.4F).fireImmune().clientTrackingRange(20).updateInterval(1)
         .build("ruin_mother"));

   public static final RegistryObject<EntityType<fr.heralisz.mere.entity.Spiderling>> SPIDERLING = ENTITIES.register("spiderling",
      () -> EntityType.Builder.<fr.heralisz.mere.entity.Spiderling>of(SPIDERLING_USINE, MobCategory.MONSTER)
         .sized(0.9F, 0.55F).clientTrackingRange(10).updateInterval(1).build("spiderling"));

   public static final RegistryObject<EntityType<fr.heralisz.mere.entity.WebGlob>> WEB_GLOB = ENTITIES.register("web_glob",
      () -> EntityType.Builder.<fr.heralisz.mere.entity.WebGlob>of(WEB_GLOB_USINE, MobCategory.MISC)
         .sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(1).build("web_glob"));

   /** Lame de test : chaque coup ôte un tiers de la vie de la Mère (pour parcourir les phases). */
   public static final RegistryObject<Item> DEBUG_BLADE = ITEMS.register("debug_blade",
      () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.NETHERITE, 3, -2.4F, new Item.Properties().rarity(Rarity.EPIC)));
   public static final RegistryObject<Item> MOTHER_EGG = ITEMS.register("ruin_mother_spawn_egg",
      () -> new ForgeSpawnEggItem(RUIN_MOTHER, 0x2A2420, 0xC89A5A, new Item.Properties()));
   /** L'Œuf de la Couvée : déposé sur l'autel, il réveille la Mère. */
   public static final RegistryObject<Item> BROOD_EGG = ITEMS.register("brood_egg",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> RUIN_FANG = ITEMS.register("ruin_fang",
      () -> new TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> RUIN_EYE = ITEMS.register("ruin_eye",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), true));
   public static final RegistryObject<Item> RUIN_VENOM = ITEMS.register("ruin_venom",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), false));
   public static final RegistryObject<Item> RUIN_SILK = ITEMS.register("ruin_silk",
      () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON), false));

   public HeraliszMere() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ModBlocks.init();
      ENTITIES.register(bus);
      ITEMS.register(bus);
      BLOCKS.register(bus);
      BLOCK_ENTITIES.register(bus);
      Sounds.SOUNDS.register(bus);
      Vfx.register();
      fr.heralisz.mere.fx.Particles.register(bus);
      bus.addListener(this::attributes);
      ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, MereConfig.SPEC);
      // onglet créatif unique « HeraliZ · Boss » (mod heraliz_boss) : ce boss y est un groupe
      // groupe(s) de l'onglet HeraliZ · Boss : déclarés par HeralizBoss (ordre fixe), contenu ci-dessous
      // parties serveur (logique) et client (affichage), si elles sont dans ce jar
      Parties.serveur(MODID, "fr.heralisz.mere.serveur.PartieServeur", bus);
      Parties.client(MODID, "fr.heralisz.mere.client.PartieClient", bus);
   }

   private void attributes(EntityAttributeCreationEvent event) {
      event.put(RUIN_MOTHER.get(), RuinMother.createAttributes().build());
      event.put(SPIDERLING.get(), fr.heralisz.mere.entity.Spiderling.createAttributes().build());
   }

   /** Icône du groupe « Mère des Ruines » de l'onglet HeraliZ · Boss (groupe déclaré par HeralizBoss, ordre fixe). */
   public static ItemStack iconeOnglet() {
      return new ItemStack(RUIN_FANG.get());
   }

   /** Contenu du groupe « Mère des Ruines » de l'onglet HeraliZ · Boss (inchangé). */
   public static void remplirOnglet(net.minecraft.world.item.CreativeModeTab.Output out) {
      out.accept(DEBUG_BLADE.get());
      out.accept(MOTHER_EGG.get());
      out.accept(BROOD_EGG.get());
      out.accept(RUIN_FANG.get());
      out.accept(RUIN_EYE.get());
      out.accept(RUIN_VENOM.get());
      out.accept(RUIN_SILK.get());
      for (RegistryObject<Item> it : ModBlocks.TAB) out.accept(it.get());
   }
}
