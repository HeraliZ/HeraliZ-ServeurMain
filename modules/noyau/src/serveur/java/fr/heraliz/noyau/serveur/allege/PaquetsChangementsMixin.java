package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.PaquetsTroncons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Point 4 du chantier « Minecraft allégé » (01/11) : un tronçon qui a des changements à renvoyer aux joueurs (bloc changé ou
 * renvoyé, entité de bloc renvoyée par {@code sendBlockUpdated}, lumière) : son paquet gardé dans ce tick n'est plus
 * redonné ({@link PaquetsTroncons}). La carte des tronçons est celle du monde ({@code playerProvider}).
 */
@Mixin(ChunkHolder.class)
public abstract class PaquetsChangementsMixin {
   @Shadow
   @Final
   ChunkPos pos;

   @Shadow
   @Final
   private ChunkHolder.PlayerProvider playerProvider;

   @Inject(method = "blockChanged", at = @At("HEAD"))
   private void heraliz$bloc(BlockPos bloc, CallbackInfo ci) {
      PaquetsTroncons.envoiChange(this.playerProvider, this.pos.toLong());
   }

   @Inject(method = "sectionLightChanged", at = @At("HEAD"))
   private void heraliz$lumiere(LightLayer couche, int section, CallbackInfo ci) {
      PaquetsTroncons.oublier(this.playerProvider, this.pos.toLong());
   }
}
