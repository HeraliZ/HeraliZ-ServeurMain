package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.PaquetsTroncons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Point 4 du chantier « Minecraft allégé » (01/11) : un bloc changé dans un tronçon, même sans prévenir les joueurs : son
 * paquet gardé dans ce tick n'est plus redonné ({@link PaquetsTroncons}).
 */
@Mixin(LevelChunk.class)
public abstract class PaquetsBlocsMixin {
   @Inject(method = "setBlockState", at = @At("HEAD"))
   private void heraliz$bloc(BlockPos bloc, BlockState etat, boolean deplacement, CallbackInfoReturnable<BlockState> cir) {
      if (PaquetsTroncons.blocChange() && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel niveau) {
         PaquetsTroncons.oublier(niveau.getChunkSource().chunkMap, ((LevelChunk) (Object) this).getPos().toLong());
      }
   }
}
