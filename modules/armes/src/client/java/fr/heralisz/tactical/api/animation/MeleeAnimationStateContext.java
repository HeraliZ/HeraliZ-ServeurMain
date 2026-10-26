package fr.heralisz.tactical.api.animation;

import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;

public class MeleeAnimationStateContext extends BaseAnimationStateContext {
   public int getActionCount(String action) {
      MeleeAction action1 = switch (action) {
         case "attack_left" -> MeleeAction.LEFT;
         case "attack_right" -> MeleeAction.RIGHT;
         default -> null;
      };
      return action1 == null
         ? 0
         : this.<Integer>processCameraEntity(
               entity -> entity.getCapability(CombatPropertiesProvider.CAPABILITY).map(cap -> cap.getActionCount(action1)).orElse(0)
            )
            .orElse(0);
   }
}
