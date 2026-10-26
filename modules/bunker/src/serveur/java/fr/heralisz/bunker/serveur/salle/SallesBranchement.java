package fr.heralisz.bunker.serveur.salle;

import fr.heralisz.bunker.salle.SallesPont;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Branchements serveur des salles à carte (appelés par la partie serveur, pendant le constructeur du @Mod). */
public final class SallesBranchement {
   private SallesBranchement() {}

   public static void brancher() {
      SallesPont.REGLES = new SallesServeur();
      MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CommandesSalle.enregistrer(e.getDispatcher()));
      // toutes les secondes, par dimension : occupation des salles, portes, loot
      MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent e) -> {
         if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel sl && sl.getGameTime() % 20 == 0) {
            SallesMonde.get(sl).tick(sl);
         }
      });
      // toutes les secondes, par joueur : contours pour le staff qui tient la baguette
      MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent e) -> {
         if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer sp && sp.tickCount % 20 == 0) Selections.baguette(sp);
      });
      MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> Selections.clicGauche(e));
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Selections.oublier(e.getEntity()));
      // 25/10 : déconnecté dans une salle à carte = sorti devant sa porte à la reconnexion (plus de loot rechargé à volonté)
      MinecraftForge.EVENT_BUS.addListener(SortieSalles::connexion);
   }
}
