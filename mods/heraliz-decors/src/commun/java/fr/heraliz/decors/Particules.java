package fr.heraliz.decors;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Particules maison (leur rendu est dans la partie client) : brume, brume basse, poussière, poussière dorée, éclat, embruns, lucioles. */
public final class Particules {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeralizDecors.MODID);

    public static final RegistryObject<SimpleParticleType> BRUME = TYPES.register("brume", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> POUSSIERE = TYPES.register("poussiere", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> ECLAT = TYPES.register("eclat", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> EMBRUN = TYPES.register("embrun", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> BRUME_BASSE = TYPES.register("brume_basse", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> LUCIOLE = TYPES.register("luciole", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> POUSSIERE_DOREE = TYPES.register("poussiere_doree", () -> new SimpleParticleType(false));

    private Particules() {}
}
