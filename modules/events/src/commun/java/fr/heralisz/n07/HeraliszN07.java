package fr.heralisz.n07;

import fr.heraliz.boss.HeralizBoss;
import fr.heraliz.boss.pont.Parties;
import fr.heraliz.boss.pont.Usine;
import fr.heraliz.boss.pont.UsineBlocEntite;
import fr.heraliz.boss.pont.UsineEntite;
import fr.heralisz.n07.block.ContainmentVatBlock;
import fr.heralisz.n07.block.ContainmentVatBlockEntity;
import fr.heralisz.n07.effect.IrradiatedEffect;
import fr.heralisz.n07.entity.N07Boulder;
import fr.heralisz.n07.entity.N07Isotope;
import fr.heralisz.n07.entity.N07Shell;
import fr.heralisz.n07.entity.SujetN07;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
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

/** Boss HeralisZ. Premier boss : Sujet N-07, mutant radioactif (zone carte rouge). */
@Mod(HeraliszN07.MODID)
public class HeraliszN07 {
   public static final String MODID = "heralisz_n07";
   public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
   public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);

   // usines (docs/METHODE_CLIENT_SERVEUR.md §4) : versions serveur branchées par fr.heralisz.n07.serveur.PartieServeur
   public static final UsineEntite<SujetN07> SUJET_N07_USINE = new UsineEntite<>(SujetN07::new);
   public static final UsineEntite<N07Shell> N07_SHELL_USINE = new UsineEntite<>(N07Shell::new);
   public static final UsineEntite<N07Boulder> N07_BOULDER_USINE = new UsineEntite<>(N07Boulder::new);
   public static final UsineEntite<N07Isotope> N07_ISOTOPE_USINE = new UsineEntite<>(N07Isotope::new);
   public static final UsineEntite<fr.heralisz.n07.entity.BossRemains> BOSS_REMAINS_USINE = new UsineEntite<>(fr.heralisz.n07.entity.BossRemains::new);
   public static final Usine<Void, MobEffect> IRRADIATED_USINE = Usine.simple(IrradiatedEffect::new);
   public static final UsineBlocEntite<ContainmentVatBlockEntity> CONTAINMENT_VAT_BE_USINE = new UsineBlocEntite<>(ContainmentVatBlockEntity::new);

   public static final RegistryObject<EntityType<SujetN07>> SUJET_N07 = ENTITIES.register("sujet_n07",
      () -> EntityType.Builder.of(SUJET_N07_USINE, MobCategory.MONSTER).sized(3.2F, 6.4F).fireImmune().clientTrackingRange(12).build("sujet_n07"));
   public static final RegistryObject<EntityType<N07Shell>> N07_SHELL = ENTITIES.register("n07_shell",
      () -> EntityType.Builder.<N07Shell>of(N07_SHELL_USINE, MobCategory.MISC).sized(0.6F, 0.6F).fireImmune().clientTrackingRange(8).updateInterval(1)
         .build("n07_shell"));
   public static final RegistryObject<EntityType<N07Boulder>> N07_BOULDER = ENTITIES.register("n07_boulder",
      () -> EntityType.Builder.<N07Boulder>of(N07_BOULDER_USINE, MobCategory.MISC).sized(1.4F, 1.4F).fireImmune().clientTrackingRange(10).updateInterval(1)
         .build("n07_boulder"));
   public static final RegistryObject<EntityType<N07Isotope>> N07_ISOTOPE = ENTITIES.register("n07_isotope",
      () -> EntityType.Builder.<N07Isotope>of(N07_ISOTOPE_USINE, MobCategory.MISC).sized(0.9F, 0.9F).fireImmune().clientTrackingRange(10).updateInterval(1)
         .build("n07_isotope"));

   public static final RegistryObject<EntityType<fr.heralisz.n07.entity.BossRemains>> BOSS_REMAINS = ENTITIES.register("boss_remains",
      () -> EntityType.Builder.<fr.heralisz.n07.entity.BossRemains>of(BOSS_REMAINS_USINE, MobCategory.MISC).sized(2.5F, 4.0F)
         .fireImmune().clientTrackingRange(16).updateInterval(10).build("boss_remains"));
   public static final RegistryObject<MobEffect> IRRADIATED = EFFECTS.register("irradiated", IRRADIATED_USINE::creer);

   public static final RegistryObject<Block> CONTAINMENT_VAT = BLOCKS.register("containment_vat", ContainmentVatBlock::new);
   public static final RegistryObject<BlockEntityType<ContainmentVatBlockEntity>> CONTAINMENT_VAT_BE = BLOCK_ENTITIES.register("containment_vat",
      () -> BlockEntityType.Builder.of(CONTAINMENT_VAT_BE_USINE, CONTAINMENT_VAT.get()).build(null));

   public static final RegistryObject<Item> CONTAINMENT_VAT_ITEM = ITEMS.register("containment_vat", () -> new BlockItem(CONTAINMENT_VAT.get(), new Item.Properties()));
   public static final RegistryObject<Item> SUJET_N07_EGG = ITEMS.register("sujet_n07_spawn_egg",
      () -> new ForgeSpawnEggItem(SUJET_N07, 0x55664A, 0x7CFF3A, new Item.Properties()));
   public static final RegistryObject<Item> N07_RESPIRATOR = ITEMS.register("n07_respirator",
      () -> new fr.heralisz.n07.item.TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> N07_CORE = ITEMS.register("n07_core",
      () -> new fr.heralisz.n07.item.TrophyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
   public static final RegistryObject<Item> N07_BADGE = ITEMS.register("n07_badge",
      () -> new fr.heralisz.n07.item.TrophyItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), false));
   public static final RegistryObject<Item> TRINITITE = ITEMS.register("trinitite",
      () -> new fr.heralisz.n07.item.TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON), true));

   public HeraliszN07() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      fr.heralisz.n07.dungeon.DungeonRegistry.init();
      fr.heralisz.n07.decor.DecorRegistry.init();
      ENTITIES.register(bus);
      ITEMS.register(bus);
      BLOCKS.register(bus);
      BLOCK_ENTITIES.register(bus);
      EFFECTS.register(bus);
      fr.heralisz.n07.particle.ModParticles.PARTICLES.register(bus);
      fr.heralisz.n07.sound.BossSounds.SOUNDS.register(bus);
      fr.heralisz.n07.vfx.Vfx.register();
      bus.addListener(this::attributes);
      ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BossConfig.SPEC);
      // onglet créatif unique « HeraliZ · Boss » (mod heraliz_boss) : le boss et les décors du spawn y sont deux groupes
      // groupe(s) de l'onglet HeraliZ · Boss : déclarés par HeralizBoss (ordre fixe), contenu ci-dessous
      // parties serveur (logique) et client (affichage), si elles sont dans ce jar
      Parties.serveur(MODID, "fr.heralisz.n07.serveur.PartieServeur", bus);
      Parties.client(MODID, "fr.heralisz.n07.client.PartieClient", bus);
   }

   private void attributes(EntityAttributeCreationEvent event) {
      event.put(SUJET_N07.get(), SujetN07.createAttributes().build());
   }

   /** Icône du groupe « Sujet N-07 » de l'onglet HeraliZ · Boss (groupe déclaré par HeralizBoss, ordre fixe). */
   public static ItemStack iconeOnglet() {
      return new ItemStack(SUJET_N07_EGG.get());
   }

   /** Contenu du groupe « Sujet N-07 » de l'onglet HeraliZ · Boss (inchangé). */
   public static void remplirOnglet(net.minecraft.world.item.CreativeModeTab.Output out) {
      out.accept(CONTAINMENT_VAT_ITEM.get());
      out.accept(SUJET_N07_EGG.get());
      out.accept(N07_RESPIRATOR.get());
      out.accept(N07_CORE.get());
      out.accept(N07_BADGE.get());
      out.accept(TRINITITE.get());
      out.accept(fr.heralisz.n07.dungeon.DungeonRegistry.N07_KEYCARD.get());
      out.accept(fr.heralisz.n07.dungeon.DungeonRegistry.N07_TERMINAL.get().asItem());
   }

   /** Icône du groupe « Décors du spawn » (contenu : DecorRegistry.remplir, inchangé). */
   public static ItemStack iconeDecor() {
      return new ItemStack(fr.heralisz.n07.decor.DecorRegistry.ITEMS.get(0).get());
   }
}
