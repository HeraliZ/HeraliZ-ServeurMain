package fr.heralisz.tags.registry;

import com.mojang.serialization.Codec;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.particle.BrouillardOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Particules du mod. « brouillard » = brouillard de peinture a la pose (une dizaine, couleur dominante du tag) ;
 * options {@link BrouillardOptions} (case du tag + couleur de repli), rendu client par
 * {@code client.particle.BrouillardParticle} (sprite maison textures/particle/brouillard.png).
 */
public final class Particules {
    private Particules() {
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeraliszTags.MODID);

    public static final RegistryObject<ParticleType<BrouillardOptions>> BROUILLARD = PARTICLES.register("brouillard",
            () -> new ParticleType<BrouillardOptions>(false, BrouillardOptions.DESERIALIZER) {
                @Override
                public Codec<BrouillardOptions> codec() {
                    return BrouillardOptions.CODEC;
                }
            });
}
