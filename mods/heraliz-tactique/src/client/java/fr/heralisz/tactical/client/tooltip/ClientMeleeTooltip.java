package fr.heralisz.tactical.client.tooltip;

import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.inventory.tooltip.MeleeTooltip;

public class ClientMeleeTooltip extends AbstractClientItemTooltip {
   public ClientMeleeTooltip(MeleeTooltip tooltip) {
      // seulement la description : plus de lignes de coups (« Gauche x1, dégâts, recharge… », 19/10)
      HeraliszTacticalAPI.getMeleeIndex(tooltip.getStack()).ifPresent(index -> this.build(index.getTooltip(), java.util.List.of()));
   }
}
