package fr.heralisz.camo.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tirs par arme, pour les skins qui réagissent au tir : historique des derniers coups (billets éjectés du Money Gun...) et
 * chaleur de l'arme (Surchauffe) — elle monte à chaque tir et redescend toute seule. Le joueur local est suivi par l'horodatage
 * de TaCZ ; les autres joueurs par l'événement de tir que le serveur envoie à tous (GunFireEvent côté client), rangé sur l'objet
 * qu'ils ont en main — celui-là même que dessine le rendu en 3e personne.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class ShotTracker {
   /** Nombre de tirs gardés. */
   public static final int HISTORY = 24;
   /** Chaleur ajoutée par tir, refroidissement par seconde (exponentiel), délai avant de refroidir. */
   private static final float HEAT_PER_SHOT = 0.045F, COOL_RATE = 0.28F, COOL_DELAY = 0.6F;
   private static final Map<ItemStack, History> MAP = new WeakHashMap<>();
   private static long lastLocalTs;

   /** Historique des tirs d'une arme. */
   private static final class History {
      final long[] shots = new long[HISTORY];
      int head, seq;
      float heat;
      long heatAt = System.currentTimeMillis();

      void shot(long ts) {
         this.heat = this.heat(ts) + HEAT_PER_SHOT * (1.1F - this.heat(ts));
         this.heatAt = ts;
         this.head = (this.head + 1) % HISTORY;
         this.shots[this.head] = ts;
         this.seq++;
      }

      float heat(long now) {
         float dt = (now - this.heatAt) / 1000F - COOL_DELAY;
         return dt <= 0 ? Math.min(1, this.heat) : Math.min(1, this.heat * (float) Math.exp(-dt * COOL_RATE));
      }
   }

   private ShotTracker() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      long ts;
      try {
         ts = IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientShootTimestamp;
      } catch (RuntimeException | LinkageError e) {
         return;
      }
      if (ts != lastLocalTs && ts > 0) {
         lastLocalTs = ts;
         MAP.computeIfAbsent(mc.player.getMainHandItem(), k -> new History()).shot(ts);
      }
   }

   /** Tir d'un autre joueur (ou d'une créature armée), annoncé par le serveur. */
   @SubscribeEvent
   public static void onRemoteFire(GunFireEvent event) {
      if (!event.getLogicalSide().isClient()) return;
      Minecraft mc = Minecraft.getInstance();
      if (event.getShooter() == null || event.getShooter() == mc.player) return;
      MAP.computeIfAbsent(event.getShooter().getMainHandItem(), k -> new History()).shot(System.currentTimeMillis());
   }

   /** Âges (s) des derniers tirs de cette arme, du plus récent au plus ancien ; 99 pour les cases vides ; null si aucun. */
   public static float[] ages(ItemStack gun) {
      History h = MAP.get(gun);
      if (h == null) return null;
      long now = System.currentTimeMillis();
      float[] out = new float[HISTORY];
      for (int i = 0; i < HISTORY; i++) {
         long s = h.shots[(h.head - i + HISTORY) % HISTORY];
         out[i] = s > 0 ? (now - s) / 1000F : 99;
      }
      return out;
   }

   /** Numéro du dernier tir de cette arme (graine stable des objets éjectés : le tir i a le numéro seq - i). */
   public static int seq(ItemStack gun) {
      History h = MAP.get(gun);
      return h == null ? 0 : h.seq;
   }

   /** Chaleur de cette arme : 0 froide .. 1 chauffée à blanc. */
   public static float heat(ItemStack gun) {
      History h = MAP.get(gun);
      return h == null ? 0 : h.heat(System.currentTimeMillis());
   }
}
