package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.core.Downed;
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
         if ((oldFlags & 1) == 0 && (p.flags & 1) != 0) {
            info.downTicks = 0;
         }

         // la même réanimation, déjà en cours chez le joueur (avancée renvoyée par le serveur en cours de route) : le
         // compteur est recalé sur celui du serveur sans jamais reculer ; sinon (début, fin, exécution) il est repris tel quel
         boolean memeReanimation = old != null && reanimation(oldFlags) && reanimation(p.flags) && old.partner == p.partnerId
            && old.reviveMax == p.reviveMax && p.revive > 0;
         info.flags = p.flags;
         info.partner = p.partnerId;
         info.bleed = p.bleed;
         info.bleedMax = p.bleedMax;
         if (memeReanimation) {
            recaler(info, p.revive);
         } else {
            info.revive = p.revive;
            info.reviveRetenue = 0;
         }

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
            // HUD de celui qui réanime : fin chez le joueur, puis réponse du serveur (relevé / interrompu)
            fr.heraliz.reanimation.client.hud.HudReanimateur.onSync(e, oldFlags, p.flags, p.event);
         }
      }
   }

   /** Réanimation en cours (réanimé ou réanimateur), hors exécution. */
   private static boolean reanimation(int flags) {
      return (flags & (Downed.REVIVED | Downed.REVIVING)) != 0 && (flags & (Downed.EXECUTING | Downed.EXECUTED)) == 0;
   }

   /**
    * Recale le compteur de la réanimation sur la valeur du serveur : en retard, il la rattrape tout de suite ; en avance
    * (serveur plus lent que les 20 ticks par seconde du jeu), il s'arrête le nombre de ticks d'écart au lieu de reculer.
    * Le compte à rebours suit ainsi le serveur jusqu'à la fin réelle de la réanimation.
    */
   private static void recaler(ClientDowned.Info info, int serveur) {
      int ecart = serveur - info.revive;
      if (ecart >= 0) {
         info.revive = serveur;
         info.reviveRetenue = 0;
      } else {
         info.reviveRetenue = -ecart;
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

         if (info.has(1) && info.downTicks < 100) {
            info.downTicks++;
         }

         boolean clocked = info.has(4) || info.has(16) || info.has(128) || info.has(256);
         info.reviveAvance = false;
         if (clocked && info.revive < info.reviveMax) {
            if (info.reviveRetenue > 0) {
               // en avance sur le serveur : on l'attend
               info.reviveRetenue--;
            } else {
               info.revive++;
               info.reviveAvance = true;
            }
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
      /** Ticks pendant lesquels {@link #revive} n'avance plus : le jeu était en avance sur le serveur (voir recaler). */
      public int reviveRetenue;
      /** Vrai si {@link #revive} a avancé au dernier tick. */
      public boolean reviveAvance;
      public int style;
      /** Ticks passés à terre depuis la chute (corps couché par Poses si la pose « nage » manque). */
      public int downTicks;

      public boolean has(int flag) {
         return (this.flags & flag) != 0;
      }

      public float bleedFraction() {
         return this.bleedMax <= 0 ? 0.0F : Mth.clamp((float)this.bleed / this.bleedMax, 0.0F, 1.0F);
      }

      /**
       * Ticks de réanimation, interpolés entre deux ticks. La fraction de tick n'est pas ajoutée pendant que le compteur
       * attend le serveur, ni une fois au bout : l'anneau, le compte à rebours et les gestes ne reculent jamais.
       */
      public float reviveTicks(float partialTick) {
         return this.reviveRetenue > 0 || this.revive >= this.reviveMax ? this.revive : this.revive + partialTick;
      }

      public float reviveProgress(float partialTick) {
         return this.reviveMax <= 0 ? 0.0F : Mth.clamp(this.reviveTicks(partialTick) / this.reviveMax, 0.0F, 1.0F);
      }

      /** Chute du corps, de 0 (debout) à 1 (à plat) : même vitesse que le swimAmount de Minecraft (+0,09 par tick). */
      public float fall(float partialTick) {
         return Mth.clamp((this.downTicks + partialTick) * 0.09F, 0.0F, 1.0F);
      }
   }
}
