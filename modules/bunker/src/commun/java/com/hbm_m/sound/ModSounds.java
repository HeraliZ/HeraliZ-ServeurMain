package com.hbm_m.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** HeralisZ : sons des portes. */
public final class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "heralisz_bunker");
   public static final RegistryObject<SoundEvent> ALARM_6 = reg("block.alarm_6");
   public static final RegistryObject<SoundEvent> DOOR_MOVE_2 = reg("block.door_move_2");
   public static final RegistryObject<SoundEvent> DOOR_WGH_BIG_START = reg("block.door_wgh_big_start");
   public static final RegistryObject<SoundEvent> DOOR_WGH_BIG_STOP = reg("block.door_wgh_big_stop");
   public static final RegistryObject<SoundEvent> GARAGE_MOVE = reg("block.garage_move");
   public static final RegistryObject<SoundEvent> GARAGE_STOP = reg("block.garage_stop");
   public static final RegistryObject<SoundEvent> LEVER_1 = reg("block.lever_1");
   public static final RegistryObject<SoundEvent> METAL_STOP_1 = reg("block.metal_stop_1");
   public static final RegistryObject<SoundEvent> SLIDING_DOOR_OPENED = reg("block.sliding_door_opened");
   public static final RegistryObject<SoundEvent> SLIDING_DOOR_OPENING = reg("block.sliding_door_opening");
   public static final RegistryObject<SoundEvent> SLIDING_DOOR_SHUT = reg("block.sliding_door_shut");
   public static final RegistryObject<SoundEvent> TRANSITION_SEAL_CLOSE = reg("block.transition_seal_close");
   public static final RegistryObject<SoundEvent> TRANSITION_SEAL_OPEN = reg("block.transition_seal_open");
   public static final RegistryObject<SoundEvent> VAULT_SCRAPE = reg("block.vault_scrape");
   public static final RegistryObject<SoundEvent> VAULT_THUD = reg("block.vault_thud");
   public static final RegistryObject<SoundEvent> WGH_START = reg("block.wgh_start");
   public static final RegistryObject<SoundEvent> WGH_STOP = reg("block.wgh_stop");

   private static RegistryObject<SoundEvent> reg(String name) {
      ResourceLocation id = new ResourceLocation("heralisz_bunker", name);
      return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
   }

   private ModSounds() {}
}
