package fr.heralisz.tactical.client.tooltip;

import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.inventory.tooltip.MeleeTooltip;

public class ClientMeleeTooltip extends AbstractClientItemTooltip {
   public ClientMeleeTooltip(MeleeTooltip tooltip) {
      HeraliszTacticalAPI.getMeleeIndex(tooltip.getStack()).ifPresent(index -> this.build(index.getTooltip(), index.getData().getTooltipLines()));
   }
}
