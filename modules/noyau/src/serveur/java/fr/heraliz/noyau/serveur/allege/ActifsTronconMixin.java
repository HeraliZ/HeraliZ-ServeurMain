package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.TronconsActifs;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

/**
 * Point 3 de l'étape 2 du chantier « Minecraft allégé » (01/11) : à la fin de {@code ChunkHolder.updateFutures} (seul endroit
 * où Minecraft crée ou retire l'avenir « qui tourne » d'un tronçon), le support entre dans la liste de sa carte ou en sort
 * ({@link TronconsActifs}).
 */
@Mixin(ChunkHolder.class)
public abstract class ActifsTronconMixin {
   @Inject(method = "updateFutures", at = @At("RETURN"))
   private void heraliz$niveauChange(ChunkMap carte, Executor executeur, CallbackInfo ci) {
      if ((Object) carte instanceof TronconsActifs.AvecActifs a) {
         a.heraliz$actifs().maj((ChunkHolder) (Object) this);
      }
   }
}
