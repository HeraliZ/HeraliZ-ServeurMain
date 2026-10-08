package fr.heraliz.armory.registry;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons du mod : gresillement des jumelles de vision nocturne en service (boucle, portee courte). */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizArmory.MOD_ID);

    public static final RegistryObject<SoundEvent> NVG_HUM = SOUNDS.register("nvg_hum",
            () -> SoundEvent.createVariableRangeEvent(HeralizArmory.id("nvg_hum")));

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
