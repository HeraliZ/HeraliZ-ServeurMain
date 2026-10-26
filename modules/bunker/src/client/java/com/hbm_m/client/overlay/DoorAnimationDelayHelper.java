package com.hbm_m.client.overlay;

import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.hbm_m.client.render.DoorChunkInvalidationHelper;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DoorAnimationDelayHelper {
   private static final long ANIMATION_DELAY_MS = 500L;
   private static final List<DoorAnimationDelayHelper.DelayEntry> QUEUE = new ArrayList<>();
   private static final Map<DoorBlockEntity, Long> DELAY_UNTIL_MAP = new ConcurrentHashMap<>();

   private DoorAnimationDelayHelper() {
   }

   public static void addToQueue(DoorBlockEntity be, long delayMs) {
      long untilMs = System.currentTimeMillis() + delayMs;
      DELAY_UNTIL_MAP.put(be, untilMs);
      QUEUE.add(new DoorAnimationDelayHelper.DelayEntry(untilMs, new WeakReference<>(be)));
   }

   public static void processQueue() {
      long now = System.currentTimeMillis();
      Iterator<DoorAnimationDelayHelper.DelayEntry> it = QUEUE.iterator();

      while (it.hasNext()) {
         DoorAnimationDelayHelper.DelayEntry entry = it.next();
         if (now >= entry.untilMs) {
            DoorBlockEntity be = entry.ref.get();
            if (be != null && !be.isRemoved() && be.getLevel() != null) {
               DELAY_UNTIL_MAP.remove(be);
               be.clearAnimationDelayClient();
               DoorChunkInvalidationHelper.scheduleChunkInvalidation(be.getBlockPos());
            }

            it.remove();
         }
      }
   }

   public static boolean isInDelayPeriod(DoorBlockEntity be) {
      Long untilMs = DELAY_UNTIL_MAP.get(be);
      return untilMs != null && System.currentTimeMillis() < untilMs;
   }

   private record DelayEntry(long untilMs, WeakReference<DoorBlockEntity> ref) {
   }
}
