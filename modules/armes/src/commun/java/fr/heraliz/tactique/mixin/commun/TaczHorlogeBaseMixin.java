package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.network.message.ClientMessageSyncBaseTimestamp;
import fr.heraliz.tactique.tir.HorlogeTirs;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Horloge des tirs (retours du 02/11, « balles à blanc » et « 15 tirs refusés par TaCZ ») : quand le joueur répond à
 * l'échange d'horloge de TaCZ, la nouvelle base du serveur passe par {@link HorlogeTirs#recaler} : l'heure du dernier tir
 * est recomptée depuis cette base (sinon TaCZ refuse ensuite tous les tirs de l'arme pour « cadence »).
 * <p>
 * Cible : la tâche du fil serveur de ClientMessageSyncBaseTimestamp.handle de TaCZ 1.1.8 ({@code lambda$handle$0}, seule
 * écriture de {@code ShooterDataHolder.baseTimestamp} côté serveur, vérifiée avec javap). Noms de TaCZ : remap = false.
 * require = 0 : si TaCZ change, son comportement revient, rien ne plante.
 */
@Mixin(value = ClientMessageSyncBaseTimestamp.class, remap = false)
public abstract class TaczHorlogeBaseMixin {
   @Redirect(
      method = "lambda$handle$0",
      at = @At(value = "FIELD", target = "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;baseTimestamp:J", opcode = Opcodes.PUTFIELD),
      require = 0
   )
   private static void heraliz$nouvelleBase(ShooterDataHolder donnees, long base) {
      HorlogeTirs.recaler(donnees, base, System.currentTimeMillis());
   }
}
