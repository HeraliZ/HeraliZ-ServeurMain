package fr.heralisz.sylvorn;

import fr.heraliz.boss.HeralizBoss;
import fr.heraliz.boss.pont.Parties;
import fr.heraliz.boss.pont.UsineEntite;
import fr.heralisz.sylvorn.dungeon.ModBlocks;
import fr.heralisz.sylvorn.entity.SapNode;
import fr.heralisz.sylvorn.entity.SeedPod;
import fr.heralisz.sylvorn.entity.Sylvorn;
import fr.heralisz.sylvorn.entity.SylvornLegs;
import fr.heralisz.sylvorn.entity.ThornSpear;
import fr.heralisz.sylvorn.entity.ThrownLog;
import fr.heralisz.sylvorn.item.TrophyItem;
import fr.heralisz.sylvorn.sound.Sounds;
import fr.heralisz.sylvorn.vfx.Vfx;
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
 * HeralisZ - Sylvorn : le gardien de l'Arbre-Monde, pourri avec lui. Il dort dans l'écorce du tronc creux, sous un cœur vert
 * géant qui bat tout là-haut ; on le réveille en déposant une graine ancestrale sur l'autel du Sanctuaire des Racines.
 */
@Mod(HeraliszSylvorn.MODID)
public class HeraliszSylvorn {
   public static final String MODID = "heralisz_sylvorn";
   public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

   /** Usines des entités coupées en deux : versions serveur (SylvornServeur...) branchées par serveur.PartieServeur. */
   public static final UsineEntite<Sylvorn> SYLVORN_USINE = new UsineEntite<>(Sylvorn::new);
   public static final UsineEntite<SylvornLegs> SYLVORN_LEGS_USINE = new UsineEntite<>(SylvornLegs::new);
   public static final UsineEntite<ThrownLog> THROWN_LOG_USINE = new UsineEntite<>(ThrownLog::new);
   public static final UsineEntite<SeedPod> SEED_POD_USINE = new UsineEntite<>(SeedPod::new);
   public static final UsineEntite<SapNode> SAP_NODE_USINE = new UsineEntite<>(SapNode::new);
   public static final UsineEntite<ThornSpear> THORN_SPEAR_USINE = new UsineEntite<>(ThornSpear::new);

   public static final RegistryObject<EntityType<Sylvorn>> SYLVORN = ENTITIES.register("sylvorn",
      () -> EntityType.Builder.of(SYLVORN_USINE, MobCategory.MONSTER).sized(3.0F, 9.0F).fireImmune().clientTrackingRange(20).updateInterval(1)
         .build("sylvorn"));
   public static final RegistryObject<EntityType<SylvornLegs>> SYLVORN_LEGS = ENTITIES.register("sylvorn_legs",
      () -> EntityType.Builder.<SylvornLegs>of(SYLVORN_LEGS_USINE, MobCategory.MISC).sized(1.5F, 1.5F).noSave().clientTrackingRange(20)
         .updateInterval(1).build("sylvorn_legs"));
   public static final RegistryObject<EntityType<ThrownLog>> THROWN_LOG = ENTITIES.register("thrown_log",
      () -> EntityType.Builder.<ThrownLog>of(THROWN_LOG_USINE, MobCategory.MISC).sized(1.2F, 1.2F).clientTrackingRange(8).updateInterval(1)
         .build("thrown_log"));
   public static final RegistryObject<EntityType<SeedPod>> SEED_POD = ENTITIES.register("seed_pod",
      () -> EntityType.Builder.<SeedPod>of(SEED_POD_USINE, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(1)
         .build("seed_pod"));
   public static final RegistryObject<EntityType<SapNode>> SAP_NODE = ENTITIES.register("sap_node",
      () -> EntityType.Builder.<SapNode>of(SAP_NODE_USINE, MobCategory.MISC).sized(1.4F, 2.8F).fireImmune().clientTrackingRange(10).updateInterval(4)
         .build("sap_node"));
   public static final RegistryObject<EntityType<ThornSpear>> THORN_SPEAR = ENTITIES.register("thorn_spear",
      () -> EntityType.Builder.<ThornSpear>of(THORN_SPEAR_USINE, MobCategory.MISC).sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1)
         .build("thorn_spear"));

   /** Lame de test : chaque coup ôte un tiers de sa vie (pour parcourir les phases). */
   public static final RegistryObject<Item> DEBUG_BLADE = ITEMS.register("debug_blade",
      () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.NETHERITE, 3, -2.4F, new Item.Properties().rarity(Rarity.EPIC)));
   public static final RegistryObject<Item> SYLVORN_EGG = ITEMS.register("sylvorn_spawn_egg",
      () -> new ForgeSpawnEggItem(SYLVORN, 0x2A2A1E, 0x7CE04A, new Item.Properties()));
   /** La Graine Ancestrale : déposée sur l'autel du Sanctuaire des Racines, elle réveille le gardien. */
   public static final RegistryObject<Item> ANCESTRAL_SEED = ITEMS.register("ancestral_seed",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> SYLVORN_ANTLER = ITEMS.register("sylvorn_antler",
      () -> new TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> SYLVORN_HEART = ITEMS.register("sylvorn_heart",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> SYLVORN_MASK_SHARD = ITEMS.register("sylvorn_mask_shard",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), false));
   public static final RegistryObject<Item> PETRIFIED_BARK = ITEMS.register("petrified_bark",
      () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON), false));

   public HeraliszSylvorn() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ModBlocks.init();
      ENTITIES.register(bus);
      ITEMS.register(bus);
      BLOCKS.register(bus);
      BLOCK_ENTITIES.register(bus);
      Sounds.SOUNDS.register(bus);
      Vfx.register();
      fr.heralisz.sylvorn.fvfx.Vfx.register();
      fr.heralisz.sylvorn.fx.Particles.register(bus);
      fr.heralisz.sylvorn.particle.ModParticles.PARTICLES.register(bus);
      bus.addListener(this::attributes);
      ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SylvornConfig.SPEC);
      // onglet créatif unique « HeraliZ · Boss » (mod heraliz_boss) : ce boss y est un groupe
      // groupe(s) de l'onglet HeraliZ · Boss : déclarés par HeralizBoss (ordre fixe), contenu ci-dessous
      // parties serveur (logique) et client (affichage), si elles sont dans ce jar
      Parties.serveur(MODID, "fr.heralisz.sylvorn.serveur.PartieServeur", bus);
      Parties.client(MODID, "fr.heralisz.sylvorn.client.PartieClient", bus);
   }

   private void attributes(EntityAttributeCreationEvent event) {
      event.put(SYLVORN.get(), Sylvorn.createAttributes().build());
      // SapNode est une LivingEntity : sans ses attributs, le serveur plante quand Sylvorn pose un nœud (attaque CHANNEL).
      event.put(SAP_NODE.get(), SapNode.createAttributes().build());
   }

   /** Icône du groupe « Sylvorn » de l'onglet HeraliZ · Boss (groupe déclaré par HeralizBoss, ordre fixe). */
   public static ItemStack iconeOnglet() {
      return new ItemStack(SYLVORN_ANTLER.get());
   }

   /** Contenu du groupe « Sylvorn » de l'onglet HeraliZ · Boss (inchangé). */
   public static void remplirOnglet(net.minecraft.world.item.CreativeModeTab.Output out) {
      out.accept(DEBUG_BLADE.get());
      out.accept(SYLVORN_EGG.get());
      out.accept(ANCESTRAL_SEED.get());
      out.accept(SYLVORN_ANTLER.get());
      out.accept(SYLVORN_HEART.get());
      out.accept(SYLVORN_MASK_SHARD.get());
      out.accept(PETRIFIED_BARK.get());
      for (RegistryObject<Item> it : ModBlocks.TAB) out.accept(it.get());
   }
}
