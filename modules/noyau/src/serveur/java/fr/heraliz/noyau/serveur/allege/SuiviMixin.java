package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.SuiviParTroncon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

/**
 * Point 1 de l'étape 2 du chantier « Minecraft allégé » (01/11) : voir {@link SuiviParTroncon}. À la création d'une entrée
 * du suivi des entités, sa liste {@code seenBy} est la liste {@link SuiviParTroncon.Vus} (la même que celle de Minecraft,
 * qui tient en plus le miroir de chaque joueur) ; {@code updatePlayers(liste)} ne juge que les joueurs à portée ou qui
 * voient déjà l'entité (les autres : Minecraft ne ferait rien).
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class SuiviMixin {
   @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Sets;newIdentityHashSet()Ljava/util/Set;",
         remap = false))
   private Set<ServerPlayerConnection> heraliz$listeVus() {
      return SuiviParTroncon.nouveauVus(this);
   }

   @Inject(method = "updatePlayers", at = @At("HEAD"), cancellable = true)
   private void heraliz$joueurs(List<ServerPlayer> joueurs, CallbackInfo ci) {
      if (SuiviParTroncon.majJoueurs(this, joueurs)) {
         ci.cancel();
      }
   }
}
