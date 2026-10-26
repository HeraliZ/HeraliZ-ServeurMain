package com.hbm_m.block;

import com.hbm_m.block.decorations.DoorBlock;
import com.hbm_m.block.machines.TransitionSealBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** HeralisZ : uniquement les portes (reprises de HBM Modernized, GPL-3.0). Généré par tools/gen_door_registries.py. */
public final class ModBlocks {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "heralisz_bunker");
   public static RegistryObject<Block> LARGE_VEHICLE_DOOR;
   public static RegistryObject<Block> ROUND_AIRLOCK_DOOR;
   public static RegistryObject<Block> FIRE_DOOR;
   public static RegistryObject<Block> SLIDING_SEAL_DOOR;
   public static RegistryObject<Block> SECURE_ACCESS_DOOR;
   public static RegistryObject<Block> QE_CONTAINMENT;
   public static RegistryObject<Block> QE_SLIDING;
   public static RegistryObject<Block> WATER_DOOR;
   public static RegistryObject<Block> SILO_HATCH;
   public static RegistryObject<Block> SILO_HATCH_LARGE;
   public static RegistryObject<Block> VAULT_DOOR;
   public static RegistryObject<Block> SLIDE_DOOR;
   public static RegistryObject<Block> CARGO_DOOR;
   public static RegistryObject<Block> TRANSITION_SEAL;
   public static RegistryObject<Block> UNIVERSAL_MACHINE_PART;
   public static RegistryObject<Block> DOOR_OFFICE;
   public static RegistryObject<Block> DOOR_BUNKER;
   public static RegistryObject<Block> METAL_DOOR;

   static {
      LARGE_VEHICLE_DOOR = BLOCKS.register(
            "large_vehicle_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "large_vehicle_door"
            )
         );
      ROUND_AIRLOCK_DOOR = BLOCKS.register(
            "round_airlock_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "round_airlock_door"
            )
         );
      FIRE_DOOR = BLOCKS.register(
            "fire_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "fire_door"
            )
         );
      SLIDING_SEAL_DOOR = BLOCKS.register(
            "sliding_seal_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "sliding_seal_door"
            )
         );
      SECURE_ACCESS_DOOR = BLOCKS.register(
            "secure_access_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "secure_access_door"
            )
         );
      QE_CONTAINMENT = BLOCKS.register(
            "qe_containment_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "qe_containment_door"
            )
         );
      QE_SLIDING = BLOCKS.register(
            "qe_sliding_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "qe_sliding_door"
            )
         );
      WATER_DOOR = BLOCKS.register(
            "water_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "water_door"
            )
         );
      SILO_HATCH = BLOCKS.register(
            "silo_hatch",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "silo_hatch"
            )
         );
      SILO_HATCH_LARGE = BLOCKS.register(
            "silo_hatch_large",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "silo_hatch_large"
            )
         );
      VAULT_DOOR = BLOCKS.register(
            "vault_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "vault_door"
            )
         );
      SLIDE_DOOR = BLOCKS.register(
            "sliding_blast_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "sliding_blast_door"
            )
         );
      CARGO_DOOR = BLOCKS.register(
            "cargo_door",
            () -> new DoorBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false),
               "cargo_door"
            )
         );
      TRANSITION_SEAL = BLOCKS.register(
            "transition_seal",
            () -> new TransitionSealBlock(
               Properties.of().strength(10.0F, 1000.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false)
            )
         );
      UNIVERSAL_MACHINE_PART = BLOCKS.register(
            "universal_machine_part",
            () -> new UniversalMachinePartBlock(Properties.copy(Blocks.IRON_BLOCK).strength(5.0F).noOcclusion().isSuffocating((state, world, pos) -> false).noParticlesOnBreak())
         );
      DOOR_OFFICE = BLOCKS.register(
            "door_office",
            () -> new net.minecraft.world.level.block.DoorBlock(
               Properties.copy(Blocks.CHERRY_WOOD).sound(SoundType.CHERRY_WOOD).noOcclusion(), BlockSetType.CHERRY
            )
         );
      DOOR_BUNKER = BLOCKS.register(
            "door_bunker",
            () -> new net.minecraft.world.level.block.DoorBlock(
               Properties.copy(Blocks.NETHERITE_BLOCK).sound(SoundType.NETHERITE_BLOCK).noOcclusion(), BlockSetType.STONE
            )
         );
      METAL_DOOR = BLOCKS.register(
            "metal_door",
            () -> new net.minecraft.world.level.block.DoorBlock(
               Properties.copy(Blocks.CHAIN).sound(SoundType.CHAIN).noOcclusion(), BlockSetType.BIRCH
            )
         );
   }

   private ModBlocks() {}
}
