package fr.heraliz.crac.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "crac");
    public static final RegistryObject<SoundEvent> C4_BEEP = SOUND_EVENTS.register(
        "c4_beep", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("crac", "c4_beep"))
    );
    public static final RegistryObject<SoundEvent> SHEET_METAL_UPGRADE = SOUND_EVENTS.register(
        "sheet_metal_upgrade", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("crac", "sheet_metal_upgrade"))
    );

    private ModSounds() {
    }
}
