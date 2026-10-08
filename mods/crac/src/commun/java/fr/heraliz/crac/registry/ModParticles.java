package fr.heraliz.crac.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "crac");
    public static final RegistryObject<SimpleParticleType> SHEET_METAL_SPARK = PARTICLE_TYPES.register(
        "sheet_metal_spark", () -> new SimpleParticleType(false) {}
    );

    private ModParticles() {
    }
}
