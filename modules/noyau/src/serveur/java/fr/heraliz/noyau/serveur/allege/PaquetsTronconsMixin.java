package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.PaquetsTroncons;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Point 4 du chantier « Minecraft allégé » (01/11), partie « paquets » : voir {@link PaquetsTroncons}. Au début de
 * {@code playerLoadedChunk}, le paquet déjà fabriqué pour ce tronçon dans ce tick est redonné (Minecraft ne fabrique un
 * paquet que s'il n'en a pas déjà un, comme quand il en donne un seul à plusieurs joueurs) ; juste avant l'envoi, le
 * paquet est gardé pour les suivants. Biomes renvoyés ({@code /fillbiome}) : paquets de ces tronçons oubliés.
 */
@Mixin(ChunkMap.class)
public abstract class PaquetsTronconsMixin implements PaquetsTroncons.AvecPaquets {
   @Shadow
   @Final
   ServerLevel level;

   @Unique
   private final PaquetsTroncons heraliz$paquets = new PaquetsTroncons();

   @Override
   public PaquetsTroncons heraliz$paquets() {
      return heraliz$paquets;
   }

   @Inject(method = "playerLoadedChunk", at = @At("HEAD"))
   private void heraliz$reprendre(ServerPlayer joueur, MutableObject<ClientboundLevelChunkWithLightPacket> paquet, LevelChunk troncon,
                                  CallbackInfo ci) {
      if (paquet.getValue() == null) {
         ClientboundLevelChunkWithLightPacket p = heraliz$paquets.reprendre(this.level.getServer().getTickCount(), troncon);
         if (p != null) {
            paquet.setValue(p);
         }
      }
   }

   @Inject(method = "playerLoadedChunk", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/server/level/ServerPlayer;trackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/network/protocol/Packet;)V"))
   private void heraliz$garder(ServerPlayer joueur, MutableObject<ClientboundLevelChunkWithLightPacket> paquet, LevelChunk troncon,
                               CallbackInfo ci) {
      heraliz$paquets.garder(this.level.getServer().getTickCount(), troncon, paquet.getValue());
   }

   @Inject(method = "resendBiomesForChunks", at = @At("HEAD"))
   private void heraliz$biomes(List<ChunkAccess> troncons, CallbackInfo ci) {
      for (ChunkAccess t : troncons) {
         PaquetsTroncons.oublier(this, t.getPos().toLong());
      }
   }
}
