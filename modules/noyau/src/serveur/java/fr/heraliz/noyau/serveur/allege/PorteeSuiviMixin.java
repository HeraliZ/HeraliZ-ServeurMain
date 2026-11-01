package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.PorteeSuivi;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Point 2 du chantier « Minecraft allégé » (01/11) : voir {@link PorteeSuivi}. Les passagers parcourus par
 * {@code ChunkMap.TrackedEntity.getEffectiveRange} : une liste vide sans rien créer pour une entité sans passager.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class PorteeSuiviMixin {
   @Redirect(method = "getEffectiveRange", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;getIndirectPassengers()Ljava/lang/Iterable;"))
   private Iterable<Entity> heraliz$passagers(Entity entite) {
      return PorteeSuivi.passagers(entite);
   }
}
