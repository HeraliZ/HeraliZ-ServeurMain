package fr.heraliz.noyau.client;

import fr.heraliz.noyau.pont.Partie;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client du noyau : indicateur « EN COMBAT », analyse du jeu ({@code /diagclient}, {@code /diag client}). */
public final class ClientNoyau implements Partie {
   @Override
   public void init(IEventBus busMod) {
      fr.heraliz.noyau.client.diag.DiagClient.init();
      busMod.addListener((RegisterGuiOverlaysEvent e) ->
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "combat", (gui, g, partial, w, h) -> HudCombat.dessiner(g, w, h)));
   }
}
