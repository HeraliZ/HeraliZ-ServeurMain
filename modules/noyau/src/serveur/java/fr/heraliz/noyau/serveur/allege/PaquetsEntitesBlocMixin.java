package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.PaquetsTroncons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Point 4 du chantier « Minecraft allégé » (01/11) : une entité de bloc modifiée ({@code BlockEntity.setChanged}) : le
 * paquet de son tronçon gardé dans ce tick n'est plus redonné ({@link PaquetsTroncons}), il contient ses données.
 */
@Mixin(Level.class)
public abstract class PaquetsEntitesBlocMixin {
   @Inject(method = "blockEntityChanged", at = @At("HEAD"))
   private void heraliz$entiteDeBloc(BlockPos bloc, CallbackInfo ci) {
      if (PaquetsTroncons.garde() && (Object) this instanceof ServerLevel niveau) {
         PaquetsTroncons.oublier(niveau.getChunkSource().chunkMap, ChunkPos.asLong(bloc.getX() >> 4, bloc.getZ() >> 4));
      }
   }
}
