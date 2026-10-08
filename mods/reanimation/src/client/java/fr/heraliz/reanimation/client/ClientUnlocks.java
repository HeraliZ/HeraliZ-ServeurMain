package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.network.UnlocksPacket;
import net.minecraft.client.Minecraft;

public final class ClientUnlocks {
   private static long owned;
   private static int selected = -1;
   private static boolean known;

   private ClientUnlocks() {
   }

   public static void receive(UnlocksPacket p) {
      owned = p.owned;
      selected = p.selected;
      known = true;
      Minecraft mc = Minecraft.getInstance();
      if (p.open && !(mc.screen instanceof ExecutionScreen)) {
         mc.setScreen(new ExecutionScreen());
      }
   }

   static boolean known() {
      return known;
   }

   static boolean owns(ExecutionType type) {
      return (owned & 1L << type.ordinal()) != 0L;
   }

   public static int selected() {
      return selected;
   }

   static void select(ExecutionType type) {
      selected = type.ordinal();
   }

   static void clear() {
      owned = 0L;
      selected = -1;
      known = false;
   }
}
