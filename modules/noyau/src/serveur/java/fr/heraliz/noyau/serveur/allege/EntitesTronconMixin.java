package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.EntitesParTroncon;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Point 4 du chantier « Minecraft allégé » (01/11), partie « entités » : voir {@link EntitesParTroncon}. La boucle de
 * {@code playerLoadedChunk} sur toutes les entités suivies du monde ne parcourt plus que celles du tronçon envoyé.
 */
@Mixin(ChunkMap.class)
public abstract class EntitesTronconMixin {
   @Unique
   private final EntitesParTroncon heraliz$entites = new EntitesParTroncon();
   @Unique
   private long heraliz$troncon;
   /** Vrai entre le début de playerLoadedChunk et sa boucle (si le début n'a pas été pris : boucle de Minecraft). */
   @Unique
   private boolean heraliz$tronconPret;

   @Inject(method = "playerLoadedChunk", at = @At("HEAD"))
   private void heraliz$tronconEnvoye(ServerPlayer joueur, MutableObject<ClientboundLevelChunkWithLightPacket> paquet, LevelChunk troncon,
                                      CallbackInfo ci) {
      heraliz$troncon = troncon.getPos().toLong();
      heraliz$tronconPret = true;
   }

   @Redirect(method = "playerLoadedChunk", at = @At(value = "INVOKE",
         target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;", remap = false))
   private ObjectCollection<?> heraliz$entitesDuTroncon(Int2ObjectMap<?> suivies) {
      if (!heraliz$tronconPret) {
         return suivies.values();
      }
      heraliz$tronconPret = false;
      return heraliz$entites.entites(suivies, heraliz$troncon);
   }

   @Inject(method = {"addEntity", "removeEntity"}, at = {@At("HEAD"), @At("RETURN")})
   private void heraliz$suiviChange(Entity entite, CallbackInfo ci) {
      EntitesParTroncon.changementDuSuivi();
   }
}
