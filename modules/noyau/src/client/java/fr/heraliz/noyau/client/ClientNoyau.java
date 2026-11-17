package fr.heraliz.noyau.client;

import fr.heraliz.noyau.pont.Partie;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client du noyau : indicateur « EN COMBAT », analyse du jeu ({@code /diagclient}, {@code /diag client}), dessin
 * commun des zones de la carte ({@link fr.heraliz.noyau.client.zones.ZoneDessin}), badge de rareté des infobulles
 * ({@link BadgeRarete}), effets à l'écran de jeu ({@link fr.heraliz.noyau.client.effets.EffetsHud}), pièces des écrans
 * en verre ({@link fr.heraliz.noyau.client.kit.EcranVerre}).
 */
public final class ClientNoyau implements Partie {
   @Override
   public void init(IEventBus busMod) {
      fr.heraliz.noyau.client.diag.DiagClient.init();
      BadgeRarete.enregistrer(busMod);   // badge de rareté des infobulles, dessiné (14/11)
      fr.heraliz.noyau.client.kit.InfobulleVerre.enregistrer();   // infobulles des écrans en verre (14/11)
      fr.heraliz.noyau.client.effets.EffetsHud.enregistrer(busMod);   // effets à l'écran de jeu, V2 (14/11)
      busMod.addListener((RegisterGuiOverlaysEvent e) ->
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "combat", (gui, g, partial, w, h) -> HudCombat.dessiner(g, w, h)));
   }
}
