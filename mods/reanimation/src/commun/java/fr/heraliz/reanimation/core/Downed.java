package fr.heraliz.reanimation.core;

import java.util.function.IntUnaryOperator;
import net.minecraft.world.entity.Entity;

public final class Downed {
   public static final int DOWNED = 1;
   public static final int SEARCHED = 2;
   public static final int REVIVED = 4;
   public static final int SEARCHING = 8;
   public static final int REVIVING = 16;
   public static final int PAUSED = 32;
   public static final int FROZEN = 64;
   public static final int EXECUTING = 128;
   public static final int EXECUTED = 256;
   public static IntUnaryOperator clientFlags = id -> 0;

   private Downed() {
   }

   public static int flags(Entity e) {
      return e.level().isClientSide ? clientFlags.applyAsInt(e.getId()) : DownedManager.flags(e);
   }

   public static boolean isDowned(Entity e) {
      return (flags(e) & 1) != 0;
   }

   public static boolean isActing(Entity e) {
      return (flags(e) & 24) != 0;
   }

   public static boolean isReviving(Entity e) {
      return (flags(e) & 16) != 0;
   }

   public static boolean isBusy(Entity e) {
      return (flags(e) & 144) != 0;
   }
}
