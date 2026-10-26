package fr.heraliz.ping;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Les deux sons (tools/gen_sons.py) : carillon doux et alerte breve, joues par le client a l'arrivee d'un ping. */
public final class PingSons {
    private PingSons() {
    }

    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeralizPing.MODID);
    public static final RegistryObject<SoundEvent> PING = SONS.register("ping", () -> SoundEvent.createVariableRangeEvent(HeralizPing.id("ping")));
    public static final RegistryObject<SoundEvent> ALERTE = SONS.register("alerte", () -> SoundEvent.createVariableRangeEvent(HeralizPing.id("alerte")));
}
