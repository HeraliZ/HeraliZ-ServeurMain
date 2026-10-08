package com.hbm_m.blockentity;

import com.hbm_m.block.ModBlocks;
import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.hbm_m.blockentity.machines.TransitionSealBlockEntity;
import com.hbm_m.blockentity.machines.UniversalMachinePartBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "heralisz_bunker");
   public static final RegistryObject<BlockEntityType<DoorBlockEntity>> DOOR_ENTITY = BLOCK_ENTITIES.register("door",
      () -> BlockEntityType.Builder.of(DoorBlockEntity::new, ModBlocks.LARGE_VEHICLE_DOOR.get(), ModBlocks.ROUND_AIRLOCK_DOOR.get(), ModBlocks.FIRE_DOOR.get(), ModBlocks.SLIDING_SEAL_DOOR.get(), ModBlocks.SECURE_ACCESS_DOOR.get(), ModBlocks.QE_CONTAINMENT.get(), ModBlocks.QE_SLIDING.get(), ModBlocks.WATER_DOOR.get(), ModBlocks.SILO_HATCH.get(), ModBlocks.SILO_HATCH_LARGE.get(), ModBlocks.VAULT_DOOR.get(), ModBlocks.SLIDE_DOOR.get(), ModBlocks.CARGO_DOOR.get()).build(null));
   public static final RegistryObject<BlockEntityType<TransitionSealBlockEntity>> TRANSITION_SEAL_BE = BLOCK_ENTITIES.register("transition_seal_be",
      () -> BlockEntityType.Builder.of(TransitionSealBlockEntity::new, ModBlocks.TRANSITION_SEAL.get()).build(null));
   public static final RegistryObject<BlockEntityType<UniversalMachinePartBlockEntity>> UNIVERSAL_MACHINE_PART_BE = BLOCK_ENTITIES.register("universal_machine_part_be",
      () -> BlockEntityType.Builder.of(UniversalMachinePartBlockEntity::new, ModBlocks.UNIVERSAL_MACHINE_PART.get()).build(null));

   private ModBlockEntities() {}
}
