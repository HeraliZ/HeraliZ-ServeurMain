package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.SauvegardesEtalees;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sauvegarde automatique étalée (19/11, audit des lenteurs, point C2 ; voir {@link SauvegardesEtalees}) : seule la
 * sauvegarde automatique ({@code saveEverything(true, false, false)}, toutes les 5 min) est marquée « automatique » ; dans
 * celle-là, {@code PlayerList.saveAll()} met les joueurs en file au lieu de tous les écrire. {@code /save-all}
 * ({@code force}), {@code flush}, l'arrêt du serveur et Bukkit ({@code savePlayers}) : tout de suite, comme Minecraft.
 * Réglage coupé ou cible absente (require = 0) : comme Minecraft.
 */
@Mixin(MinecraftServer.class)
public abstract class JoueursEtalesMixin {
   @Inject(method = "saveEverything", at = @At("HEAD"))
   private void heraliz$debut(boolean silencieux, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
      SauvegardesEtalees.automatique = !flush && !force;
      if (!flush && !force) SauvegardesEtalees.tickAuto = ((MinecraftServer) (Object) this).getTickCount();
   }

   @Inject(method = "saveEverything", at = @At("RETURN"))
   private void heraliz$fin(boolean silencieux, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
      SauvegardesEtalees.automatique = false;
   }

   @Redirect(method = "saveEverything", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;saveAll()V"))
   private void heraliz$joueursEtales(PlayerList liste) {
      if (!SauvegardesEtalees.joueurs(liste)) {
         liste.saveAll();
      }
   }
}
