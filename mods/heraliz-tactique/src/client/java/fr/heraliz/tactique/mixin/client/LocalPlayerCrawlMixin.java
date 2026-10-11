package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.client.gameplay.LocalPlayerCrawl;
import fr.heraliz.tactique.client.allonge.AllongeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S'allonger, chez le joueur (fr.heraliz.tactique.client.allonge.AllongeClient) : le « ramper » de TaCZ du joueur local.
 * <ul>
 *    <li>{@code crawl(true)} (touche « Ramper ») pendant le délai pour se rallonger : rien ne se passe (rien n'est envoyé
 *    au serveur), un message dit dans combien de secondes ce sera possible ;</li>
 *    <li>après la touche et après chaque tick ({@code tickCrawl}, qui relève le joueur tout seul : autre objet en main,
 *    saut, eau...), les passages debout <-> allongé sont notés ;</li>
 *    <li>refus du serveur (PaquetRefusAllonge) : le joueur est relevé au début du tick suivant.</li>
 * </ul>
 * Noms de TaCZ (non obfusqués) : remap = false. require = 0 : si TaCZ change ces méthodes, le ramper reste celui de TaCZ
 * chez le joueur (le serveur garde ses propres règles).
 */
@Mixin(value = LocalPlayerCrawl.class, remap = false)
public abstract class LocalPlayerCrawlMixin {
   @Shadow
   private boolean isCrawling;

   /** Dernier état vu, pour repérer les passages debout <-> allongé. */
   @Unique
   private boolean heraliz$etaitAllonge;

   @Inject(method = "crawl(Z)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$attendreAvantDeSeRallonger(boolean allonger, CallbackInfo ci) {
      if (allonger && !this.isCrawling) {
         long restant = AllongeClient.restantMs();
         if (restant > 0L) {
            AllongeClient.afficherAttente(restant);
            ci.cancel();
         }
      }
   }

   @Inject(method = "crawl(Z)V", at = @At("RETURN"), require = 0)
   private void heraliz$noterApresTouche(boolean allonger, CallbackInfo ci) {
      this.heraliz$noter();
   }

   @Inject(method = "tickCrawl()V", at = @At("HEAD"), require = 0)
   private void heraliz$releverSiRefuse(CallbackInfo ci) {
      if (AllongeClient.prendreRelever()) {
         // refus du serveur : debout (TaCZ remet la pose dans ce même tick), sans compter un relevé normal : l'attente
         // est celle annoncée par le serveur
         this.isCrawling = false;
         this.heraliz$etaitAllonge = false;
      }
   }

   @Inject(method = "tickCrawl()V", at = @At("RETURN"), require = 0)
   private void heraliz$noterApresTick(CallbackInfo ci) {
      this.heraliz$noter();
   }

   @Unique
   private void heraliz$noter() {
      if (this.isCrawling != this.heraliz$etaitAllonge) {
         this.heraliz$etaitAllonge = this.isCrawling;
         AllongeClient.changement(this.isCrawling);
      }
   }
}
