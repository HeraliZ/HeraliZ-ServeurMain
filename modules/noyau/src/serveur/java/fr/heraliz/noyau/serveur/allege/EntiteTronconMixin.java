package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.EntitesParTroncon;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Point 4 du chantier « Minecraft allégé » (01/11) : une entité qui change de tronçon (seul endroit où Minecraft écrit
 * son tronçon) rend périmé l'index « tronçon → entités » de {@link EntitesParTroncon}. Rien d'autre : cette ligne ne
 * tourne que quand l'entité passe d'un tronçon à un autre.
 */
@Mixin(Entity.class)
public abstract class EntiteTronconMixin {
   @Inject(method = "setPosRaw", at = @At(value = "FIELD",
         target = "Lnet/minecraft/world/entity/Entity;chunkPosition:Lnet/minecraft/world/level/ChunkPos;", opcode = Opcodes.PUTFIELD))
   private void heraliz$changeDeTroncon(double x, double y, double z, CallbackInfo ci) {
      EntitesParTroncon.changementDeTroncon();
   }
}
