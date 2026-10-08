package fr.heralisz.camo.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.fx.FxContext;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Apparition façon « Kuronami » quand on sort une arme dont le skin en a une : l'arme n'est d'abord
 * qu'un hologramme sombre ; la main gauche glisse de la crosse vers le canon et, juste devant elle, la tête de la créature file
 * le long de l'arme et la « construit » (la surface se matérialise derrière un front lumineux). Arrivée au bout, la tête se
 * dresse. Vu par le porteur seulement (1re personne), environ une seconde.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class SkinReveal {
   /** Délai avant le passage de la main, durée du passage, fin (la tête se dresse), en secondes. */
   private static final float DELAY = 0.3F, SWEEP = 1.25F, TAIL = 0.4F;
   private static long start = -1, lastDrawTs = Long.MIN_VALUE;
   private static ItemStack stack = ItemStack.EMPTY;
   private static int sounds;

   private SkinReveal() {
   }

   /** Skins qui ont une apparition (aucun pour l'instant : les skins de créature qui l'utilisaient sont retirés). */
   public static boolean has(Camo camo) {
      return false;
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         start = -1;
         return;
      }
      ItemStack held = mc.player.getMainHandItem();
      long ts;
      try {
         ts = IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientDrawTimestamp;
      } catch (RuntimeException | LinkageError e) {
         return;
      }
      if (ts != lastDrawTs) {
         lastDrawTs = ts;
         Camo camo = IGun.getIGunOrNull(held) != null ? CamoData.get(held) : null;
         if (camo != null && has(camo) && ts > 0) {
            start = Math.max(ts, System.currentTimeMillis());
            stack = held;
            sounds = 0;
         }
      }
      if (start >= 0 && (held != stack || time() > DELAY + SWEEP + TAIL)) {
         start = -1;
      }
      if (start >= 0) playSounds(mc, CamoData.get(held));
   }

   private static void playSounds(Minecraft mc, Camo camo) {
      float t = time();
      if (sounds == 0 && t >= DELAY) {
         sounds = 1;
         play(mc, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.2F);
         play(mc, SoundEvents.ELYTRA_FLYING, 0.12F, 1.8F);
      }
      if (sounds == 1 && t >= DELAY + SWEEP * 0.95F) {
         sounds = 2;
         play(mc, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.8F);
      }
   }

   private static void play(Minecraft mc, SoundEvent s, float vol, float pitch) {
      mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(s, pitch, vol));
   }

   private static float time() {
      return start < 0 ? -1 : (System.currentTimeMillis() - start) / 1000F;
   }

   /** L'apparition se joue-t-elle pour cette arme ? */
   public static boolean activeFor(ItemStack gun) {
      return start >= 0 && gun == stack && time() >= 0;
   }

   /** Front de matérialisation le long de l'arme : 0 (crosse) .. 1 (bouche du canon), -1 hors apparition. */
   public static float front(ItemStack gun) {
      if (!activeFor(gun)) return -1;
      return FxContext.clamp((time() - DELAY) / SWEEP, 0, 1);
   }

   /** Fin de l'apparition (0..1) : la créature se dresse. */
   public static float settle() {
      return start < 0 ? 0 : FxContext.clamp((time() - DELAY - SWEEP) / TAIL, 0, 1);
   }

   /** Main gauche : poids du déplacement (0 = prise normale) et position le long de l'arme (0..1). */
   public static float handWeight() {
      if (start < 0) return 0;
      float t = time();
      return FxContext.smooth(DELAY * 0.3F, DELAY + 0.12F, t) * (1 - FxContext.smooth(DELAY + SWEEP - 0.05F, DELAY + SWEEP + TAIL * 0.8F, t));
   }

   public static float handPos() {
      return start < 0 ? 0 : FxContext.clamp((time() - DELAY) / SWEEP, 0, 1);
   }
}
