package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.JoueursProches;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Point 1 du chantier « Minecraft allégé » (01/11) : voir {@link JoueursProches}. Les deux questions « un joueur est-il
 * assez près ? » de {@code tickChunks} (celle de Minecraft, et celle à portée réduite ajoutée par Mohist, absente sans
 * Mohist : alors rien n'est changé) passent par la grille des joueurs du monde, refaite une fois par tick.
 */
@Mixin(ServerChunkCache.class)
public abstract class JoueursProchesMixin {
   @Shadow
   @Final
   ServerLevel level;

   @Unique
   private final JoueursProches heraliz$proches = new JoueursProches();

   @Inject(method = "tickChunks", at = @At("HEAD"))
   private void heraliz$nouveauTick(CallbackInfo ci) {
      heraliz$proches.nouveauTick(this.level);
   }

   @Redirect(method = "tickChunks", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z"))
   private boolean heraliz$proche(ChunkMap carte, ChunkPos pos) {
      return heraliz$proches.proche(carte, pos);
   }

   @Redirect(method = "tickChunks", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;Z)Z", remap = false))
   private boolean heraliz$procheMohist(ChunkMap carte, ChunkPos pos, boolean reduit) {
      return heraliz$proches.procheMohist(carte, pos, reduit);
   }
}
