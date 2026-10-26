package fr.heraliz.airstrike.registry;

import com.mojang.serialization.Codec;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, AirstrikeMod.MODID);

    /** Animated fire ball of an explosion. */
    public static final RegistryObject<SimpleParticleType> FIREBALL = PARTICLES.register("fireball", () -> new SimpleParticleType(true));
    /** Flat expanding ring on the ground. */
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE = PARTICLES.register("shockwave", () -> new SimpleParticleType(true));
    /** Bright ember flying away from explosions. */
    public static final RegistryObject<SimpleParticleType> SPARK = PARTICLES.register("spark", () -> new SimpleParticleType(true));
    /** Big soft smoke puff with custom colour / size / life. */
    public static final RegistryObject<ParticleType<SmokeParticleOptions>> SMOKE = PARTICLES.register("smoke",
            () -> new ParticleType<SmokeParticleOptions>(true, SmokeParticleOptions.DESERIALIZER) {
                @Override
                public Codec<SmokeParticleOptions> codec() {
                    return SmokeParticleOptions.codec(this);
                }
            });

    private ModParticles() {
    }
}
