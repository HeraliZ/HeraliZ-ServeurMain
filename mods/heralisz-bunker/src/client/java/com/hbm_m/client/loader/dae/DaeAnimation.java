package com.hbm_m.client.loader.dae;

import java.util.HashMap;
import java.util.Map;

public class DaeAnimation {
   public final String name;
   private final Map<String, Map<String, DaeCurve>> channels = new HashMap<>();
   private float startTime = Float.MAX_VALUE;
   private float endTime = -Float.MAX_VALUE;

   public DaeAnimation(String name) {
      this.name = name;
   }

   public void addChannel(String nodeName, String property, DaeCurve curve) {
      this.channels.computeIfAbsent(nodeName, k -> new HashMap<>()).put(property, curve);
      this.startTime = Math.min(this.startTime, curve.getStartTime());
      this.endTime = Math.max(this.endTime, curve.getEndTime());
   }

   public boolean isEmpty() {
      return this.channels.isEmpty();
   }

   public Map<String, DaeCurve> getChannels(String nodeName) {
      return this.channels.get(nodeName);
   }

   public float getStartTime() {
      return this.startTime == Float.MAX_VALUE ? 0.0F : this.startTime;
   }

   public float getEndTime() {
      return this.endTime == -Float.MAX_VALUE ? 0.0F : this.endTime;
   }

   public float getDuration() {
      return Math.max(0.0F, this.getEndTime() - this.getStartTime());
   }
}
