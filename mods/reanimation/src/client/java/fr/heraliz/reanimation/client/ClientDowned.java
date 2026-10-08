package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.network.SyncPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class ClientDowned {
   private static final Int2ObjectOpenHashMap<ClientDowned.Info> INFOS = new Int2ObjectOpenHashMap();
   private static final IntSet FORCED = new IntOpenHashSet();

   private ClientDowned() {
   }

   public static int flags(int entityId) {
      ClientDowned.Info info = (ClientDowned.Info)INFOS.get(entityId);
      return info == null ? 0 : info.flags;
   }

   @Nullable
   public static ClientDowned.Info get(int entityId) {
      return (ClientDowned.Info)INFOS.get(entityId);
   }

   @Nullable
   public static ClientDowned.Info local() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? null : (ClientDowned.Info)INFOS.get(mc.player.getId());
   }

   public static int localFlags() {
      ClientDowned.Info info = local();
      return info == null ? 0 : info.flags;
   }

   public static boolean localDowned() {
      return (localFlags() & 1) != 0;
   }

   public static Iterable<Entry<ClientDowned.Info>> all() {
      return INFOS.int2ObjectEntrySet();
   }

   public static void receive(SyncPacket p) {
      Minecraft mc = Minecraft.getInstance();
      ClientDowned.Info old = (ClientDowned.Info)INFOS.get(p.entityId);
      int oldFlags = old == null ? 0 : old.flags;
      if (p.flags == 0) {
         INFOS.remove(p.entityId);
      } else {
         ClientDowned.Info info = old != null ? old : new ClientDowned.Info();
         info.flags = p.flags;
         info.partner = p.partnerId;
         info.bleed = p.bleed;
         info.bleedMax = p.bleedMax;
         info.revive = p.revive;
         info.reviveMax = p.reviveMax;
         info.style = p.style;
         INFOS.put(p.entityId, info);
      }

      ExecutionScenes.onSync(p);
      if (mc.level != null) {
         Entity e = mc.level.getEntity(p.entityId);
         if (e instanceof Player player) {
            applyPose(player);
         }

         if (e != null) {
            LocalEffects.onChange(e, oldFlags, p.flags, p.event);
            ExecutionFx.onEvent(e, (ClientDowned.Info)INFOS.get(p.entityId), p.event);
         }
      }
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      ObjectIterator var1 = INFOS.values().iterator();

      while (var1.hasNext()) {
         ClientDowned.Info info = (ClientDowned.Info)var1.next();
         if (info.has(1) && !info.has(32) && info.bleed > 0) {
            info.bleed--;
         }

         boolean clocked = info.has(4) || info.has(16) || info.has(128) || info.has(256);
         if (clocked && info.revive < info.reviveMax) {
            info.revive++;
         }
      }

      if (mc.level != null) {
         for (AbstractClientPlayer p : mc.level.players()) {
            applyPose(p);
         }
      }
   }

   private static void applyPose(Player p) {
      int f = flags(p.getId());
      Pose want = (f & 1) != 0 ? Pose.SWIMMING : ((f & 24) != 0 ? Pose.CROUCHING : null);
      if (want != null) {
         if (p.getForcedPose() != want) {
            p.setForcedPose(want);
         }

         FORCED.add(p.getId());
      } else if (FORCED.remove(p.getId())) {
         p.setForcedPose((Pose)null);
      }
   }

   public static void clear() {
      INFOS.clear();
      FORCED.clear();
   }

   public static final class Info {
      public int flags;
      public int partner = -1;
      public int bleed;
      public int bleedMax;
      public int revive;
      public int reviveMax;
      public int style;

      public boolean has(int flag) {
         return (this.flags & flag) != 0;
      }

      public float bleedFraction() {
         return this.bleedMax <= 0 ? 0.0F : Mth.clamp((float)this.bleed / this.bleedMax, 0.0F, 1.0F);
      }

      public float reviveProgress(float partialTick) {
         return this.reviveMax <= 0 ? 0.0F : Mth.clamp((this.revive + partialTick) / this.reviveMax, 0.0F, 1.0F);
      }
   }
}
