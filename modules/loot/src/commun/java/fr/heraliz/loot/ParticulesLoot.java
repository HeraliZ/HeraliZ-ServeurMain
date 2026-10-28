package fr.heraliz.loot;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Étoiles des conteneurs de loot remplis (28/10) : blanches (T1 à T6) et dorées (T7). Envoyées par le serveur
 * (RotationLoot.scintiller), dessinées chez le joueur par client.EtoileParticule : vie courte (moins d'une seconde) pour
 * qu'elles s'éteignent tout de suite quand le conteneur est vidé (avant : END_ROD, 3 s à l'écran).
 * <p>{@code SimpleParticleType(true)} : toujours montrées, même avec le réglage « Particules : minimales » (posé chez tout le
 * monde par le profil Performance de HeraliZ Interface depuis le 25/10, ce qui les cachait toutes).
 */
public final class ParticulesLoot {
    static final DeferredRegister<ParticleType<?>> PARTICULES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HeralizLoot.MODID);
    public static final RegistryObject<SimpleParticleType> ETOILE = PARTICULES.register("etoile", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> ETOILE_OR = PARTICULES.register("etoile_or", () -> new SimpleParticleType(true));

    private ParticulesLoot() {
    }
}
