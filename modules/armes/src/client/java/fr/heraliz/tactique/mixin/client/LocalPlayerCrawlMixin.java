package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerCrawl;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerCrawl;
import fr.heraliz.tactique.allonge.CompatReanimation;
import fr.heraliz.tactique.client.allonge.AllongeClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S'allonger, chez le joueur (fr.heraliz.tactique.client.allonge.AllongeClient) : le « ramper » de TaCZ du joueur local.
 * <ul>
 *    <li>{@code crawl(true)} (touche « Ramper ») à terre ou en train de fouiller ou réanimer (Réanimation) : rien ne se
 *    passe ; pendant le délai pour se rallonger : rien non plus (rien n'est envoyé au serveur), un message dit dans
 *    combien de secondes ce sera possible ;</li>
 *    <li>{@code crawl(true)} en se déplaçant (devant, derrière, côté), pendant un saut ou avec la touche de saut enfoncée : plongeon demandé au serveur
 *    au lieu de s'allonger ; pendant un plongeon, la touche ne fait rien ;</li>
 *    <li>sans arme TaCZ en main : TaCZ refuserait de coucher le joueur ({@code crawl}) et le relèverait à chaque tick
 *    ({@code tickCrawl}) ; HeraliZ fait à la place ce que TaCZ fait avec une arme, sans les règles de l'arme : même état
 *    ({@code isCrawling}), même attente de 0,5 s entre deux appuis, même paquet au serveur (ClientMessagePlayerCrawl, que
 *    le serveur accepte sans arme), même pose à plat, relevé de même (saut, hors du sol, à la nage, en véhicule, en
 *    spectateur). Avec une arme, le code de TaCZ, inchangé : changer d'objet en restant allongé ne relève pas le joueur ;</li>
 *    <li>avec ou sans arme : dans l'eau, à terre ou en train de fouiller ou réanimer, plus allongé ;</li>
 *    <li>plongeon en cours : corps à plat (le tick de TaCZ ne passe pas) ; en retombant, le joueur s'allonge par le même
 *    chemin qu'une touche ;</li>
 *    <li>après la touche et après chaque tick, les passages debout <-> allongé sont notés ;</li>
 *    <li>allongé : le joueur ne court plus (2.12.0). Minecraft ne laisse pas se mettre à courir à plat, mais gardait la
 *    course d'un joueur qui s'allonge ou retombe d'un plongeon en courant, touche d'avancer tenue : pour TaCZ il courait
 *    encore à plat (tir refusé « en train de courir », arme baissée dès qu'il se relevait) ;</li>
 *    <li>plongeon fini sans s'allonger (touche de saut tenue en retombant, allongé refusé, plongeon abandonné) : le
 *    serveur, qui garde le corps à plat en attendant la demande d'allongé, est prévenu tout de suite (paquet « se
 *    relever » de TaCZ, 2.12.0) ; avant, les autres joueurs le voyaient encore couché (rechargement « couché » sur un
 *    joueur debout) jusqu'à 4 s, et sans fin s'il enchaînait les sauts ;</li>
 *    <li>refus du serveur (PaquetRefusAllonge) : le joueur est relevé au début du tick suivant.</li>
 * </ul>
 * Noms de TaCZ (non obfusqués) : remap = false. require = 0 : si TaCZ change ces méthodes, le ramper reste celui de TaCZ
 * chez le joueur (le serveur garde ses propres règles).
 */
@Mixin(value = LocalPlayerCrawl.class, remap = false)
public abstract class LocalPlayerCrawlMixin {
   @Shadow
   @Final
   private LocalPlayer player;
   @Shadow
   private boolean isCrawling;
   @Shadow
   private int crawCooldownTicks;

   /** Dernier état vu, pour repérer les passages debout <-> allongé. */
   @Unique
   private boolean heraliz$etaitAllonge;
   /** Allongé demandé par la retombée d'un plongeon (pas par la touche) : ni plongeon, ni refus « plongeon en cours ». */
   @Unique
   private boolean heraliz$retombee;
   /** Plongeon en cours au tick précédent (corps à plat en l'air) : sa fin sans allongé est signalée au serveur. */
   @Unique
   private boolean heraliz$plongeait;

   @Shadow
   public abstract void crawl(boolean isCrawl);

   @Inject(method = "crawl(Z)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$avantTouche(boolean allonger, CallbackInfo ci) {
      if (!this.heraliz$retombee && AllongeClient.plongeonEnCours()) {
         ci.cancel();
         return;
      }
      if (allonger && !this.isCrawling) {
         if (CompatReanimation.empeche(this.player)) {
            ci.cancel();
            return;
         }
         long restant = AllongeClient.restantMs();
         if (restant > 0L) {
            AllongeClient.afficherAttente(restant);
            ci.cancel();
            return;
         }
         if (!this.heraliz$retombee && AllongeClient.tenterPlongeon(this.player)) {
            ci.cancel();
            return;
         }
      }
      if (!(this.player.getMainHandItem().getItem() instanceof IGun)) {
         this.heraliz$crawlSansArme(allonger);
         ci.cancel();
      }
   }

   @Inject(method = "crawl(Z)V", at = @At("RETURN"), require = 0)
   private void heraliz$noterApresTouche(boolean allonger, CallbackInfo ci) {
      this.heraliz$noter();
   }

   @Inject(method = "tickCrawl()V", at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$avantTick(CallbackInfo ci) {
      if (AllongeClient.prendreRelever()) {
         // refus du serveur : debout (la pose est remise dans ce même tick), sans compter un relevé normal : l'attente
         // est celle annoncée par le serveur
         this.isCrawling = false;
         this.heraliz$etaitAllonge = false;
      }
      if (this.isCrawling && (this.player.isInWater() || CompatReanimation.empeche(this.player))) {
         this.isCrawling = false;
      }
      int plongeon = AllongeClient.tickPlongeon(this.player);
      if (plongeon == AllongeClient.PLONGEON_EN_VOL) {
         if (this.crawCooldownTicks > 0) {
            --this.crawCooldownTicks;
         }
         this.player.setForcedPose(Pose.SWIMMING);
         this.heraliz$plongeait = true;
         this.heraliz$noter();
         ci.cancel();
         return;
      }
      if (plongeon == AllongeClient.PLONGEON_RETOMBE) {
         // retombé d'un plongeon : allongé par le même chemin qu'une touche (avec ou sans arme, mêmes règles)
         this.heraliz$retombee = true;
         try {
            this.crawl(true);
         } finally {
            this.heraliz$retombee = false;
            AllongeClient.finRetombee();
         }
      }
      if (this.heraliz$plongeait && !this.isCrawling) {
         // plongeon fini debout (touche de saut tenue, allongé refusé, plongeon abandonné) : le serveur attend une demande
         // d'allongé en gardant le corps à plat ; « se relever » (paquet de TaCZ) lui fait finir le plongeon tout de suite
         NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerCrawl(false));
      }
      this.heraliz$plongeait = false;
      if (!(this.player.getMainHandItem().getItem() instanceof IGun)) {
         this.heraliz$tickSansArme();
         ci.cancel();
      }
   }

   @Inject(method = "tickCrawl()V", at = @At("RETURN"), require = 0)
   private void heraliz$noterApresTick(CallbackInfo ci) {
      this.heraliz$noter();
   }

   /** Sans arme : LocalPlayerCrawl.crawl de TaCZ, sans les règles de l'arme. */
   @Unique
   private void heraliz$crawlSansArme(boolean allonger) {
      if (this.crawCooldownTicks > 0 || this.player.isSpectator() || this.player.isPassenger() || !this.player.onGround()) {
         return;
      }
      if (allonger && this.player.isInWater()) {
         return;
      }
      this.isCrawling = allonger;
      this.crawCooldownTicks = AllongeClient.ATTENTE_TOUCHE_TICKS;
      NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerCrawl(allonger));
      this.heraliz$noter();
   }

   /** Sans arme : LocalPlayerCrawl.tickCrawl de TaCZ, sans les règles de l'arme (même pose, mêmes relevés). */
   @Unique
   private void heraliz$tickSansArme() {
      if (this.crawCooldownTicks > 0) {
         --this.crawCooldownTicks;
      }
      if (this.isCrawling && (this.player.isSpectator() || this.player.isPassenger() || this.heraliz$saute()
            || this.player.isSwimming() || !this.player.onGround())) {
         this.isCrawling = false;
      }
      this.player.setForcedPose(this.isCrawling ? Pose.SWIMMING : null);
      this.heraliz$noter();
   }

   /** Le joueur saute (TaCZ lit le champ jumping de Minecraft, rempli à partir de ceci). */
   @Unique
   private boolean heraliz$saute() {
      return this.player.input != null && this.player.input.jumping;
   }

   @Unique
   private void heraliz$noter() {
      if (this.isCrawling != this.heraliz$etaitAllonge) {
         this.heraliz$etaitAllonge = this.isCrawling;
         AllongeClient.changement(this.isCrawling);
      }
      // allongé : plus de course (appelé après la touche et à chaque tick, avant le mouvement du joueur). Minecraft ne
      // remet pas la course à plat (élan trop faible) ; l'arrêt part au serveur avec la position, comme d'habitude
      if (this.isCrawling && this.player.isSprinting()) {
         this.player.setSprinting(false);
      }
   }
}
