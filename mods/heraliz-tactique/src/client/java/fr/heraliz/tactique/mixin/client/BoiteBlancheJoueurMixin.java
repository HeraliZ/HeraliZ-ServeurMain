package fr.heraliz.tactique.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.tactique.hitbox.HitboxCorps;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F3+B : plus de boîte blanche de Minecraft sur les joueurs, seulement leur hitbox « en morceaux » (HitboxCorpsRendu).
 * La boîte blanche sert toujours aux murs et aux déplacements, elle n'est juste plus dessinée. Les autres entités gardent
 * la leur. require = 0 : si le rendu change, la boîte blanche revient, rien ne plante.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class BoiteBlancheJoueurMixin {
   @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true, require = 0)
   private static void heraliz$sansBoiteJoueur(PoseStack pile, VertexConsumer lignes, Entity entite, float partiel, CallbackInfo ci) {
      if (HitboxCorps.actif && entite instanceof Player && !entite.isSpectator() && !((Player) entite).isSleeping()) {
         ci.cancel();
      }
   }
}
