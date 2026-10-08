package fr.heralisz.tactical.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "heralisz_tactical");
   public static final RegistryObject<SoundEvent> GRENADE_BOUNCE = SOUNDS.register(
      "grenade_bounce", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("heralisz_tactical", "entity.grenade.bounce"))
   );
   public static final RegistryObject<SoundEvent> DEAFENED_RING = SOUNDS.register(
      "player_ring", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("heralisz_tactical", "player.ring"))
   );
}
