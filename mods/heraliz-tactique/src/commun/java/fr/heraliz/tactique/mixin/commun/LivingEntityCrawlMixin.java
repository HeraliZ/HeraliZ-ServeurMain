package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.entity.shooter.LivingEntityCrawl;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.allonge.Allonge;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S'allonger, côté serveur (fr.heraliz.tactique.allonge.Allonge) : le « ramper » de TaCZ vu par le serveur.
 * <ul>
 *    <li>{@code crawl(true)} (paquet ClientMessagePlayerCrawl du joueur) est refusé si le délai pour se rallonger n'est
 *    pas écoulé (décision branchée par la partie serveur, qui renvoie aussi le refus au joueur) ;</li>
 *    <li>après la touche et après chaque tick ({@code tickCrawling}, qui relève le joueur tout seul), l'heure du coucher
 *    ou du relevé est notée sur les données du tireur.</li>
 * </ul>
 * TaCZ n'appelle cette classe que sur le serveur (et le serveur intégré du solo) ; on le vérifie quand même. Noms de TaCZ
 * (non obfusqués) : remap = false. require = 0 : si TaCZ change ces méthodes, le ramper reste celui de TaCZ, rien ne casse.
 */
@Mixin(value = LivingEntityCrawl.class, remap = false)
public abstract class LivingEntityCrawlMixin {
   @Shadow
   @Final
   private LivingEntity shooter;
   @Shadow
   @Final
   private ShooterDataHolder data;

   @Inject(method = "crawl(Z)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$refuserSiTropTot(boolean allonger, CallbackInfo ci) {
      if (allonger && !this.data.isCrawling && !this.shooter.level().isClientSide && !Allonge.peutSAllonger(this.shooter, this.data)) {
         ci.cancel();
      }
   }

   @Inject(method = "crawl(Z)V", at = @At("RETURN"), require = 0)
   private void heraliz$noterApresTouche(boolean allonger, CallbackInfo ci) {
      if (!this.shooter.level().isClientSide) {
         Allonge.noter(this.data);
      }
   }

   @Inject(method = "tickCrawling()V", at = @At("RETURN"), require = 0)
   private void heraliz$noterApresTick(CallbackInfo ci) {
      if (!this.shooter.level().isClientSide) {
         Allonge.noter(this.data);
      }
   }
}
