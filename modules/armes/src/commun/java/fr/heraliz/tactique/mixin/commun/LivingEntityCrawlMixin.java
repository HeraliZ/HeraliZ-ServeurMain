package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.entity.shooter.LivingEntityCrawl;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heraliz.tactique.allonge.Allonge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S'allonger, côté serveur (fr.heraliz.tactique.allonge.Allonge) : le « ramper » de TaCZ vu par le serveur.
 * <ul>
 *    <li>{@code crawl(true)} (paquet ClientMessagePlayerCrawl du joueur, avec ou sans arme en main) est refusé si le délai
 *    pour se rallonger n'est pas écoulé ou si le joueur est à terre (décision branchée par la partie serveur, qui renvoie
 *    aussi le refus au joueur) ;</li>
 *    <li>avant chaque tick ({@code tickCrawling}), pour les joueurs : la partie serveur traite elle-même le joueur sans
 *    arme TaCZ (TaCZ le relèverait à chaque tick) et le plongeon (corps à plat en l'air) ; avec une arme, TaCZ garde la
 *    main, comme avant ;</li>
 *    <li>après la touche et après chaque tick de TaCZ ({@code tickCrawling}, qui relève le joueur tout seul), l'heure du
 *    coucher ou du relevé est notée sur les données du tireur ;</li>
 *    <li>{@code crawl(false)} pendant un plongeon : le joueur est retombé debout, le plongeon est fini (corps plus gardé
 *    à plat, 2.12.0).</li>
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
         // refusé en retombant d'un plongeon : le joueur reste debout, le corps n'est plus gardé à plat
         Allonge.releverPendantPlongeon(this.data);
         ci.cancel();
      }
   }

   @Inject(method = "crawl(Z)V", at = @At("RETURN"), require = 0)
   private void heraliz$noterApresTouche(boolean allonger, CallbackInfo ci) {
      if (!this.shooter.level().isClientSide) {
         if (!allonger) {
            // « se relever » pendant un plongeon : le joueur est retombé debout, le corps n'est plus gardé à plat
            Allonge.releverPendantPlongeon(this.data);
         }
         Allonge.noter(this.data);
      }
   }

   /** Joueurs seulement (TaCZ passe ici pour chaque entité vivante, à chaque tick) ; traité par HeraliZ : TaCZ ne passe pas. */
   @Inject(method = "tickCrawling()V", at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$avantTick(CallbackInfo ci) {
      if (this.shooter instanceof Player joueur && !joueur.level().isClientSide && Allonge.avantTickServeur(joueur, this.data)) {
         ci.cancel();
      }
   }

   @Inject(method = "tickCrawling()V", at = @At("RETURN"), require = 0)
   private void heraliz$noterApresTick(CallbackInfo ci) {
      if (!this.shooter.level().isClientSide) {
         Allonge.noter(this.data);
      }
   }
}
