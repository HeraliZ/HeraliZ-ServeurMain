package com.hbm_m.item;

import com.hbm_m.block.ModBlocks;
import com.hbm_m.multiblock.DoorBlockItem;
import com.hbm_m.multiblock.MultiblockBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** HeralisZ : objets de pose des portes. */
public final class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "heralisz_bunker");
   public static RegistryObject<Item> LARGE_VEHICLE_DOOR;
   public static RegistryObject<Item> ROUND_AIRLOCK_DOOR;
   public static RegistryObject<Item> FIRE_DOOR;
   public static RegistryObject<Item> SLIDING_SEAL_DOOR;
   public static RegistryObject<Item> SECURE_ACCESS_DOOR;
   public static RegistryObject<Item> QE_CONTAINMENT;
   public static RegistryObject<Item> QE_SLIDING;
   public static RegistryObject<Item> WATER_DOOR;
   public static RegistryObject<Item> SILO_HATCH;
   public static RegistryObject<Item> SILO_HATCH_LARGE;
   public static RegistryObject<Item> VAULT_DOOR;
   public static RegistryObject<Item> SLIDE_DOOR;
   public static RegistryObject<Item> CARGO_DOOR;
   public static RegistryObject<Item> TRANSITION_SEAL;
   /** Référencés par le code des portes (changement de modèle) ; simples objets ici. */
   public static final RegistryObject<Item> SCREWDRIVER = ITEMS.register("screwdriver", () -> new Item(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> SCREWDRIVER_DESH = ITEMS.register("screwdriver_desh", () -> new Item(new Properties().stacksTo(1)));

   static {
      LARGE_VEHICLE_DOOR = ITEMS.register("large_vehicle_door", () -> new DoorBlockItem((Block)ModBlocks.LARGE_VEHICLE_DOOR.get(), new Properties()));
      ROUND_AIRLOCK_DOOR = ITEMS.register("round_airlock_door", () -> new DoorBlockItem((Block)ModBlocks.ROUND_AIRLOCK_DOOR.get(), new Properties()));
      FIRE_DOOR = ITEMS.register("fire_door", () -> new DoorBlockItem((Block)ModBlocks.FIRE_DOOR.get(), new Properties()));
      SLIDING_SEAL_DOOR = ITEMS.register("sliding_seal_door", () -> new DoorBlockItem((Block)ModBlocks.SLIDING_SEAL_DOOR.get(), new Properties()));
      SECURE_ACCESS_DOOR = ITEMS.register("secure_access_door", () -> new DoorBlockItem((Block)ModBlocks.SECURE_ACCESS_DOOR.get(), new Properties()));
      QE_CONTAINMENT = ITEMS.register("qe_containment_door", () -> new DoorBlockItem((Block)ModBlocks.QE_CONTAINMENT.get(), new Properties()));
      QE_SLIDING = ITEMS.register("qe_sliding_door", () -> new DoorBlockItem((Block)ModBlocks.QE_SLIDING.get(), new Properties()));
      WATER_DOOR = ITEMS.register("water_door", () -> new DoorBlockItem((Block)ModBlocks.WATER_DOOR.get(), new Properties()));
      SILO_HATCH = ITEMS.register("silo_hatch", () -> new DoorBlockItem((Block)ModBlocks.SILO_HATCH.get(), new Properties()));
      SILO_HATCH_LARGE = ITEMS.register("silo_hatch_large", () -> new DoorBlockItem((Block)ModBlocks.SILO_HATCH_LARGE.get(), new Properties()));
      VAULT_DOOR = ITEMS.register("vault_door", () -> new DoorBlockItem((Block)ModBlocks.VAULT_DOOR.get(), new Properties()));
      SLIDE_DOOR = ITEMS.register("sliding_blast_door", () -> new DoorBlockItem((Block)ModBlocks.SLIDE_DOOR.get(), new Properties()));
      CARGO_DOOR = ITEMS.register("cargo_door", () -> new DoorBlockItem((Block)ModBlocks.CARGO_DOOR.get(), new Properties()));
      TRANSITION_SEAL = ITEMS.register("transition_seal", () -> new MultiblockBlockItem((Block)ModBlocks.TRANSITION_SEAL.get(), new Properties()));
      ITEMS.register("door_office", () -> new BlockItem(ModBlocks.DOOR_OFFICE.get(), new Properties()));
      ITEMS.register("door_bunker", () -> new BlockItem(ModBlocks.DOOR_BUNKER.get(), new Properties()));
      ITEMS.register("metal_door", () -> new BlockItem(ModBlocks.METAL_DOOR.get(), new Properties()));
   }

   private ModItems() {}
}
