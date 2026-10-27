package fr.heraliz.noyau.serveur;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.api.CombatTag;
import fr.heraliz.noyau.pont.Partie;
import fr.heraliz.noyau.serveur.combat.CombatEvents;
import fr.heraliz.noyau.serveur.combat.CombatService;
import fr.heraliz.noyau.serveur.combat.CommandeCombat;
import fr.heraliz.noyau.serveur.chat.Reponse;
import fr.heraliz.noyau.serveur.diag.CommandeDiag;
import fr.heraliz.noyau.serveur.diag.Diag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur du noyau : règles du combat (tag, déconnexion, commandes), commande du staff {@code /combat}, étiquette
 * des bots de charge réservée aux vrais bots, {@code /r} (répondre au dernier message privé), {@code /diag} (blocages et
 * profil du serveur, façon spark).
 */
public final class NoyauServeur implements Partie {
   @Override
   public void init(IEventBus busMod) {
      CombatTag.APRES_MARQUAGE.brancher(CombatService::apresMarquage);
      CombatTag.APRES_EFFACEMENT.brancher(CombatService::apresEffacement);
      NoyauConfig.APPLIQUES.brancher(v -> CombatService.commandesPermises(NoyauConfig.COMMANDES_COMBAT.get(), NoyauConfig.COMMANDES_CARTE.get()));
      CombatEvents.enregistrer();
      EtiquetteBots.enregistrer();
      Reponse.enregistrer();
      Diag.enregistrer();
      fr.heraliz.noyau.serveur.diag.Moniteur.enregistrer();
      fr.heraliz.noyau.serveur.diag.DiagClients.enregistrer();
      fr.heraliz.noyau.serveur.maintenance.Maintenance.enregistrer();
      fr.heraliz.noyau.serveur.perf.LevelDatFond.enregistrer();
      fr.heraliz.noyau.serveur.perf.MondesSansSpawn.enregistrer();
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
         if (e.phase == TickEvent.Phase.END) CombatService.tick(e.getServer());
      });
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
         CombatService.oublier(e.getEntity().getUUID());
         fr.heraliz.noyau.serveur.diag.DiagClients.oublier(e.getEntity().getUUID());
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
         CombatTag.vider();
         CombatService.vider();
      });
      MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
         CommandeCombat.enregistrer(e.getDispatcher());
         Reponse.commande(e.getDispatcher());
         CommandeDiag.enregistrer(e.getDispatcher());
         fr.heraliz.noyau.serveur.maintenance.Maintenance.commandes(e.getDispatcher());
      });
   }
}
