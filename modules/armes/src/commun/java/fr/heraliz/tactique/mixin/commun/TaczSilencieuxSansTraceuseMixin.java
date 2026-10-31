package fr.heraliz.tactique.mixin.commun;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import fr.heralisz.tactical.config.ServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pas de balles traçantes avec un silencieux (31/10, « on repère encore trop facilement la position exacte d'un tireur
 * silencieux »). Dans nos gunpacks, 123 armes sur 124 ont « tracer_count_interval » à 0 : CHAQUE balle est une traçante,
 * un trait lumineux que voient tous les joueurs à 80 blocs autour (portée de suivi des balles de TaCZ) et qui part du
 * canon : le son était flou, mais le trait montrait le tireur. Avec un silencieux, la balle part maintenant sans
 * traçante (une petite balle presque invisible, comme une arme sans traçante) ; sans silencieux, rien ne change.
 * <p>
 * Cible : la partie de ModernKineticGunScriptAPI.shootOnce de TaCZ 1.1.8 qui crée les balles et envoie le son du tir
 * (lambda$shootOnce$2 ; son dernier paramètre dit si le son du silencieux est joué, le même que lit TaczSonSilencieuxMixin).
 * Seul le serveur crée les balles. Noms de TaCZ : remap = false. require = 0 : si TaCZ change, les traçantes restent.
 * Réglage : [silencieux] traceuses de heralisz_tactical-server.toml (false par défaut).
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class TaczSilencieuxSansTraceuseMixin {
   @WrapOperation(
      method = "lambda$shootOnce$2(ZLcom/tacz/guns/resource/pojo/data/gun/GunData;ILcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFFIZ)Z",
      at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/entity/IGunOperator;nextBulletIsTracer(I)Z"),
      require = 0,
      remap = false
   )
   private boolean heraliz$sansTraceuse(IGunOperator tireur, int intervalle, Operation<Boolean> original,
                                       @Local(argsOnly = true, ordinal = 1) boolean silencieux) {
      // appelé quand même : TaCZ y compte les tirs (une traçante toutes les N balles)
      boolean traceuse = original.call(tireur, intervalle);
      return traceuse && !(silencieux && heraliz$coupees());
   }

   @Unique
   private static boolean heraliz$coupees() {
      try {
         return !ServerConfig.TRACEUSES_SILENCIEUX.get();
      } catch (IllegalStateException pasEncoreLu) {
         return true;
      }
   }
}
