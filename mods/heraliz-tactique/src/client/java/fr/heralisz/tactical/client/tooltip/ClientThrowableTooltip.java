package fr.heralisz.tactical.client.tooltip;

import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.inventory.tooltip.ThrowableTooltip;

public class ClientThrowableTooltip extends AbstractClientItemTooltip {
   public ClientThrowableTooltip(ThrowableTooltip tooltip) {
      HeraliszTacticalAPI.getThrowableIndex(tooltip.getStack()).ifPresent(index -> this.build(index.getTooltip(), index.getData().getTooltipLines()));
   }
}
