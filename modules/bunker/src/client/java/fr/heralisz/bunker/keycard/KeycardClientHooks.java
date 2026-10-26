package fr.heralisz.bunker.keycard;

import fr.heralisz.bunker.keycard.client.KeycardSwipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/** Réception côté client du paquet de passage de carte (classe chargée uniquement sur le client). */
public final class KeycardClientHooks {
   public static void onSwipe(KeycardNetwork.SwipePacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !(mc.level.getEntity(p.entityId()) instanceof AbstractClientPlayer swiper)) return;
      KeycardSwipes.begin(swiper, p.reader(), p.facing());                                  // geste + carte, vus par tous
      // plus de cinématique de caméra au passage de la carte (24/10, demande du propriétaire) : chacun garde sa vue
   }

   private KeycardClientHooks() {}
}
