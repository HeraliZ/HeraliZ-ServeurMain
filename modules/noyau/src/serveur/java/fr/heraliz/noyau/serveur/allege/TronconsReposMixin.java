package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import fr.heraliz.noyau.serveur.perf.TronconsRepos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Tronçons qui tournent « au repos » (20/11, audit du tick, {@link TronconsRepos}) et relevé des apparitions sauté.
 * <ul>
 *   <li>Début et fin de {@code tickChunks} : le monde est-il au repos pendant ce tick ?</li>
 *   <li>Au repos, un tronçon hors de la tranche du tick passe au suivant ({@code isNaturalSpawningAllowed} répond non :
 *   Minecraft saute alors tout son travail) ; un tronçon de la tranche reçoit N ticks de temps passé d'un coup, et ses
 *   tics de blocs ({@code tickChunk}, sans effet au repos) sont sautés.</li>
 *   <li>Relevé des créatures pour les apparitions naturelles ({@code NaturalSpawner.createState} : toutes les entités du
 *   monde et, pour chaque tronçon qui a un monstre, les joueurs à moins de 128 blocs ; 0,7 ms par tick à 100 joueurs) :
 *   inutile quand {@code doMobSpawning} est à faux (il ne sert qu'aux apparitions). Le relevé d'avant est rendu (jamais
 *   vide après le premier tick). Réglage {@code [perf] apparitions_sans_releve}.</li>
 * </ul>
 * Cibles de Minecraft (aussi chez Mohist) ; un appel introuvable ne change rien (compteurs de {@code /diag etat}).
 */
@Mixin(ServerChunkCache.class)
public abstract class TronconsReposMixin {
   @Shadow
   @Final
   ServerLevel level;

   @Shadow
   @Nullable
   private NaturalSpawner.SpawnState lastSpawnState;

   @Inject(method = "tickChunks", at = @At("HEAD"))
   private void heraliz$debutRepos(CallbackInfo ci) {
      TronconsRepos.debut(this.level);
   }

   @Inject(method = "tickChunks", at = @At("RETURN"))
   private void heraliz$finRepos(CallbackInfo ci) {
      TronconsRepos.fin();
   }

   @Redirect(method = "tickChunks", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/level/NaturalSpawner;createState(ILjava/lang/Iterable;Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;Lnet/minecraft/world/level/LocalMobCapCalculator;)Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"))
   private NaturalSpawner.SpawnState heraliz$releveApparitions(int troncons, Iterable<Entity> entites, NaturalSpawner.ChunkGetter tronconPlein,
                                                               LocalMobCapCalculator plafonds) {
      NaturalSpawner.SpawnState avant = this.lastSpawnState;
      if (NoyauConfig.apparitionsSansReleve && avant != null && !this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
         Allege.relevesApparitionsEvites++;
         return avant;
      }
      return NaturalSpawner.createState(troncons, entites, tronconPlein, plafonds);
   }

   @Redirect(method = "tickChunks", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/server/level/ServerLevel;isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z"))
   private boolean heraliz$horsTranche(ServerLevel niveau, ChunkPos pos) {
      if (TronconsRepos.sauter(pos)) {
         return false;
      }
      return niveau.isNaturalSpawningAllowed(pos);
   }

   @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;incrementInhabitedTime(J)V"))
   private void heraliz$tempsPasse(LevelChunk troncon, long duree) {
      troncon.incrementInhabitedTime(TronconsRepos.duree(troncon.getPos(), duree));
   }

   @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;shouldTickBlocksAt(J)Z"))
   private boolean heraliz$ticsDeBlocs(ServerLevel niveau, long troncon) {
      if (TronconsRepos.repos()) {
         Allege.reposTicsSautes++;
         return false;
      }
      return niveau.shouldTickBlocksAt(troncon);
   }
}
