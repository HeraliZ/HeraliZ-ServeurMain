package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.tir.HorlogeTirs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Horloge des tirs (retours du 02/11) : un tir que le jeu du joueur a daté avec l'ancienne base juste après un échange
 * d'horloge de TaCZ est remis à l'heure de la nouvelle base ({@link HorlogeTirs#heureDuTir}) avant que TaCZ le juge
 * (cadence et réseau inchangés). Sans ça, il était refusé et relançait un échange.
 * <p>
 * Cible : LivingEntityShoot.shoot(Supplier, Supplier, long, float, boolean) de TaCZ 1.1.8 (privée, par laquelle passent
 * tous les tirs), heure du tir au début de la méthode. Noms de TaCZ : remap = false. require = 0 : si TaCZ change, rien ne
 * plante.
 */
@Mixin(value = LivingEntityShoot.class, remap = false)
public abstract class TaczHorlogeTirMixin {
   @Shadow
   @Final
   private ShooterDataHolder data;

   @ModifyVariable(
      method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0,
      require = 0
   )
   private long heraliz$heureDuTir(long heure) {
      return HorlogeTirs.heureDuTir(this.data, heure, System.currentTimeMillis());
   }
}
