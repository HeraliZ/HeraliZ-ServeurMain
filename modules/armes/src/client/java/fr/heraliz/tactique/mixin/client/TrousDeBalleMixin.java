package fr.heraliz.tactique.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Impacts de balles sur les blocs (04/11, demande du propriétaire : « des impacts de balles sur les murs, assez légers,
 * mais qu'on voit »). TaCZ pose déjà un trou de balle ({@code tacz:bullet_hole} : la texture du bloc touché, qui s'efface
 * en 8 s, réglage d'Interface) là où une balle touche un bloc, mais comme une particule ordinaire : Minecraft ne la montre
 * pas avec les particules au minimum, et n'en montre qu'une sur trois avec les particules réduites. Or le profil
 * « Performance », posé par défaut chez tout le monde (Interface, {@code Qualite}), met les particules au minimum : presque
 * personne ne voyait d'impact. Ici, un trou de balle est toujours dessiné, quel que soit ce réglage, jusqu'à 32 blocs de
 * la caméra comme les autres particules (le serveur ne les envoie de toute façon qu'aux joueurs à 32 blocs). Les autres
 * particules suivent toujours le réglage du joueur. Chez le joueur seulement ; {@code require = 0} : si Minecraft change
 * cette fonction, rien ne plante (les trous redeviennent ceux de TaCZ).
 */
@Mixin(LevelRenderer.class)
public abstract class TrousDeBalleMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private static ParticleType<?> heraliz$trou;
    @Unique
    private static boolean heraliz$cherche;

    @Inject(method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void heraliz$trouDeBalle(ParticleOptions options, boolean force, boolean reduite, double x, double y, double z,
                                     double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        if (force || options.getType() != heraliz$type()) return;
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || this.minecraft.particleEngine == null) return;
        if (camera.getPosition().distanceToSqr(x, y, z) > 1024.0D) {
            cir.setReturnValue(null);
            return;
        }
        cir.setReturnValue(this.minecraft.particleEngine.createParticle(options, x, y, z, vx, vy, vz));
    }

    /** Le type de particule du trou de balle de TaCZ, cherché une fois (null sans TaCZ). */
    @Unique
    private static ParticleType<?> heraliz$type() {
        if (!heraliz$cherche) {
            heraliz$cherche = true;
            heraliz$trou = BuiltInRegistries.PARTICLE_TYPE.getOptional(new ResourceLocation("tacz", "bullet_hole")).orElse(null);
        }
        return heraliz$trou;
    }
}
