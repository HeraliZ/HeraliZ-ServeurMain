package fr.heralisz.utils.deco;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Particules du vent de la faille (voir {@link Vent}) : nappes blanches de la rafale et traits de vent, côté client
 * seulement (posées par Vent.tick, jamais par le serveur). La vitesse passée à la particule est celle du vent.
 */
public final class VentParticules {
    public static final DeferredRegister<ParticleType<?>> PARTICULES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeraliszUtils.MODID);
    /** Nappe de rafale : voile blanc translucide qui file avec la bourrasque. */
    public static final RegistryObject<SimpleParticleType> RAFALE = PARTICULES.register("rafale", () -> new SimpleParticleType(false));
    /** Trait de vent : longue strie fine orientée dans le sens du vent. */
    public static final RegistryObject<SimpleParticleType> TRAIT = PARTICULES.register("trait_vent", () -> new SimpleParticleType(false));

    private VentParticules() {}

    public static void register(IEventBus bus) {
        PARTICULES.register(bus);
    }
}
