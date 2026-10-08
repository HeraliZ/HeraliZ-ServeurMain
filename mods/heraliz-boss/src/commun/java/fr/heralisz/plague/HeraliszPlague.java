package fr.heralisz.plague;

import fr.heraliz.boss.HeralizBoss;
import fr.heraliz.boss.pont.Parties;
import fr.heraliz.boss.pont.UsineEntite;
import fr.heralisz.plague.dungeon.ModBlocks;
import fr.heralisz.plague.entity.PlagueBearer;
import fr.heralisz.plague.entity.PlagueGlob;
import fr.heralisz.plague.entity.PlagueSwarm;
import fr.heralisz.plague.item.TrophyItem;
import fr.heralisz.plague.sound.Sounds;
import fr.heralisz.plague.vfx.Vfx;
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
 * HeralisZ - le Porteur de Peste : le sergent Oswin Hale, de l'unité NBC envoyée contenir la zone rouge, fondu dans sa
 * combinaison. Il est agenouillé au fond du bassin de décantation de la Station de Quarantaine, sous les boues ; on le libère en
 * posant le volant sur la vanne de purge.
 */
@Mod(HeraliszPlague.MODID)
public class HeraliszPlague {
   public static final String MODID = "heralisz_plague";
   public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

   /** Usines des entités coupées en deux : version neutre ici, version serveur branchée par serveur.PartieServeur. */
   public static final UsineEntite<PlagueBearer> PLAGUE_BEARER_USINE = new UsineEntite<>(PlagueBearer::new);
   public static final UsineEntite<PlagueGlob> PLAGUE_GLOB_USINE = new UsineEntite<>(PlagueGlob::new);
   public static final UsineEntite<PlagueSwarm> PLAGUE_SWARM_USINE = new UsineEntite<>(PlagueSwarm::new);

   public static final RegistryObject<EntityType<PlagueBearer>> PLAGUE_BEARER = ENTITIES.register("plague_bearer",
      () -> EntityType.Builder.of(PLAGUE_BEARER_USINE, MobCategory.MONSTER).sized(3.2F, 5.8F).fireImmune().clientTrackingRange(16).updateInterval(1)
         .build("plague_bearer"));
   public static final RegistryObject<EntityType<PlagueGlob>> PLAGUE_GLOB = ENTITIES.register("plague_glob",
      () -> EntityType.Builder.<PlagueGlob>of(PLAGUE_GLOB_USINE, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(1)
         .build("plague_glob"));
   public static final RegistryObject<EntityType<PlagueSwarm>> PLAGUE_SWARM = ENTITIES.register("plague_swarm",
      () -> EntityType.Builder.<PlagueSwarm>of(PLAGUE_SWARM_USINE, MobCategory.MISC).sized(1.2F, 1.2F).fireImmune().clientTrackingRange(8).updateInterval(1)
         .build("plague_swarm"));

   /** Lame de test : chaque coup ôte un tiers de sa vie (pour parcourir les phases). */
   public static final RegistryObject<Item> DEBUG_BLADE = ITEMS.register("debug_blade",
      () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.NETHERITE, 3, -2.4F, new Item.Properties().rarity(Rarity.EPIC)));
   public static final RegistryObject<Item> PLAGUE_EGG = ITEMS.register("plague_bearer_spawn_egg",
      () -> new ForgeSpawnEggItem(PLAGUE_BEARER, 0x3A4A1A, 0x9ACD32, new Item.Properties()));
   /** Le volant de la vanne de purge : posé sur la vanne, il vide le bassin de décantation. */
   public static final RegistryObject<Item> PURGE_WHEEL = ITEMS.register("purge_wheel",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> PLAGUE_MASK = ITEMS.register("plague_mask",
      () -> new TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> PLAGUE_CANISTER = ITEMS.register("plague_canister",
      () -> new TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> PLAGUE_HOOK = ITEMS.register("plague_hook",
      () -> new TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), false));
   public static final RegistryObject<Item> PLAGUE_SPORES = ITEMS.register("plague_spores",
      () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON), false));

   public HeraliszPlague() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      ModBlocks.init();
      ENTITIES.register(bus);
      ITEMS.register(bus);
      BLOCKS.register(bus);
      BLOCK_ENTITIES.register(bus);
      Sounds.SOUNDS.register(bus);
      Vfx.register();
      fr.heralisz.plague.fvfx.Vfx.register();
      fr.heralisz.plague.fx.Particles.register(bus);
      fr.heralisz.plague.particle.ModParticles.PARTICLES.register(bus);
      bus.addListener(this::attributes);
      ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PlagueConfig.SPEC);
      // onglet créatif unique « HeraliZ · Boss » (mod heraliz_boss) : ce boss y est un groupe
      // groupe(s) de l'onglet HeraliZ · Boss : déclarés par HeralizBoss (ordre fixe), contenu ci-dessous
      // parties serveur (logique) et client (affichage), si elles sont dans ce jar
      Parties.serveur(MODID, "fr.heralisz.plague.serveur.PartieServeur", bus);
      Parties.client(MODID, "fr.heralisz.plague.client.PartieClient", bus);
   }

   private void attributes(EntityAttributeCreationEvent event) {
      event.put(PLAGUE_BEARER.get(), PlagueBearer.createAttributes().build());
   }

   /** Icône du groupe « Porteur de Peste » de l'onglet HeraliZ · Boss (groupe déclaré par HeralizBoss, ordre fixe). */
   public static ItemStack iconeOnglet() {
      return new ItemStack(PLAGUE_MASK.get());
   }

   /** Contenu du groupe « Porteur de Peste » de l'onglet HeraliZ · Boss (inchangé). */
   public static void remplirOnglet(net.minecraft.world.item.CreativeModeTab.Output out) {
      out.accept(DEBUG_BLADE.get());
      out.accept(PLAGUE_EGG.get());
      out.accept(PURGE_WHEEL.get());
      out.accept(PLAGUE_MASK.get());
      out.accept(PLAGUE_CANISTER.get());
      out.accept(PLAGUE_HOOK.get());
      out.accept(PLAGUE_SPORES.get());
      for (RegistryObject<Item> it : ModBlocks.TAB) out.accept(it.get());
   }
}
