package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.SuiviParTroncon;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Point 1 de l'étape 2 du chantier « Minecraft allégé » (01/11) : voir {@link SuiviParTroncon}. Chaque entité garde son
 * entrée dans l'index du suivi (rien si elle n'est pas suivie) ; juste après que Minecraft a changé son tronçon (seul endroit
 * où il l'écrit, {@code setPosRaw}), l'entrée change de case. Rien d'autre : ne tourne que quand l'entité change de tronçon.
 */
@Mixin(Entity.class)
public abstract class SuiviEntiteMixin implements SuiviParTroncon.AvecEntree {
   @Unique
   private Object heraliz$entreeSuivi;

   @Override
   public Object heraliz$entree() {
      return heraliz$entreeSuivi;
   }

   @Override
   public void heraliz$entree(Object entree) {
      heraliz$entreeSuivi = entree;
   }

   @Inject(method = "setPosRaw", at = @At(value = "FIELD",
         target = "Lnet/minecraft/world/entity/Entity;chunkPosition:Lnet/minecraft/world/level/ChunkPos;", opcode = Opcodes.PUTFIELD,
         shift = At.Shift.AFTER))
   private void heraliz$troncon(double x, double y, double z, CallbackInfo ci) {
      SuiviParTroncon.changeDeTroncon((Entity) (Object) this);
   }
}
