package fr.heralisz.tactical.client.tooltip;

import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.inventory.tooltip.ConsumableTooltip;

public class ClientConsumableTooltip extends AbstractClientItemTooltip {
   public ClientConsumableTooltip(ConsumableTooltip tooltip) {
      HeraliszTacticalAPI.getConsumableIndex(tooltip.getStack()).ifPresent(index -> this.build(index.getTooltip(), index.getData().getTooltipLines()));
   }
}
