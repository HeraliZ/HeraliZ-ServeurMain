package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tickets à échéance (20/11, audit du tick : {@code DistanceManager.purgeStaleTickets}, 0,5 ms par tick au banc à 100
 * joueurs). À chaque tick, Minecraft passe en revue TOUS les tickets de chargement du monde (un par tronçon vu par un
 * joueur : 5 000 à 10 000 avec une vue de 8) pour retirer ceux qui ont fini leur temps ; presque aucun n'a de durée (vue des
 * joueurs, zone de départ, tickets de nos mods sans fin). Ici, la carte des tickets retient le tick de la première fin
 * possible : la pose d'un ticket qui a une durée l'avance si besoin, et chaque vraie revue la recalcule (tickets restants).
 * Avant ce tick, la revue ne retirerait rien : elle est sautée (le compteur de ticks des tickets avance quand même, comme
 * chez Minecraft). Dès ce tick, la revue de Minecraft, inchangée. Mêmes tickets retirés, au même tick. Réglage
 * {@code [perf] tickets_a_echeance}. Fil serveur (comme Minecraft).
 */
@Mixin(DistanceManager.class)
public abstract class TicketsEcheanceMixin {
   @Shadow
   private long ticketTickCounter;

   /** Premier tick (compteur des tickets) où un ticket peut avoir fini ; MIN : inconnu, la revue se fait. */
   @Unique
   private long heraliz$premiereFin = Long.MIN_VALUE;
   /** Plus petite fin vue pendant la revue en cours. */
   @Unique
   private long heraliz$finVue = Long.MAX_VALUE;
   /**
    * Crochets vus en marche (la revue juge un ticket, une pose est suivie) : sans eux, la première fin ne serait pas sûre,
    * la revue se fait alors à chaque tick, comme chez Minecraft.
    */
   @Unique
   private static boolean heraliz$revueVue, heraliz$poseVue;

   @Inject(method = "purgeStaleTickets", at = @At("HEAD"), cancellable = true)
   private void heraliz$avantRevue(CallbackInfo ci) {
      if (NoyauConfig.ticketsAEcheance && heraliz$revueVue && heraliz$poseVue && heraliz$premiereFin != Long.MIN_VALUE
            && this.ticketTickCounter + 1 < heraliz$premiereFin) {
         ++this.ticketTickCounter;
         Allege.revuesTicketsSautees++;
         ci.cancel();
         return;
      }
      heraliz$finVue = Long.MAX_VALUE;
   }

   @Redirect(method = "purgeStaleTickets", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/Ticket;timedOut(J)Z"))
   private boolean heraliz$fini(Ticket<?> ticket, long tick) {
      heraliz$revueVue = true;
      TicketAcces t = (TicketAcces) (Object) ticket;
      boolean fini = t.heraliz$finiAu(tick);
      if (!fini) {
         long duree = ticket.getType().timeout();
         if (duree != 0L) {
            long fin = t.heraliz$pose() + duree + 1;
            if (fin < heraliz$finVue) {
               heraliz$finVue = fin;
            }
         }
      }
      return fini;
   }

   @Inject(method = "purgeStaleTickets", at = @At("RETURN"))
   private void heraliz$apresRevue(CallbackInfo ci) {
      heraliz$premiereFin = heraliz$finVue;
      Allege.revuesTicketsFaites++;
   }

   @Inject(method = "addTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("RETURN"))
   private void heraliz$pose(long troncon, Ticket<?> ticket, CallbackInfo ci) {
      heraliz$poseVue = true;
      long duree = ticket.getType().timeout();
      // inconnu (MIN) : la prochaine revue se fait de toute façon et recalcule tout
      if (duree != 0L && heraliz$premiereFin != Long.MIN_VALUE) {
         long fin = this.ticketTickCounter + duree + 1;
         if (fin < heraliz$premiereFin) {
            heraliz$premiereFin = fin;
         }
      }
   }
}
