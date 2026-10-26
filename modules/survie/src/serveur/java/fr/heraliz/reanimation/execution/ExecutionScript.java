package fr.heraliz.reanimation.execution;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;

public abstract class ExecutionScript {
   public abstract void tick(Executions.Execution var1, ServerLevel var2, int var3);

   public abstract void strike(Executions.Execution var1, ServerLevel var2);

   @Nullable
   public static ExecutionScript of(ExecutionType type) {
      return switch (type) {
         case FRAPPE_ORBITALE -> FrappeOrbitaleScript.INSTANCE;
         case CHUTE_METEORE -> ChuteMeteoreScript.INSTANCE;
         case COMBO_AERIEN -> ComboAerienScript.INSTANCE;
         case ARRACHE_AME -> ArracheAmeScript.INSTANCE;
         default -> null;
      };
   }
}
