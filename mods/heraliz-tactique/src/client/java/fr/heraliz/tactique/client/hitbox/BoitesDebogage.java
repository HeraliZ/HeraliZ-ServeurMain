package fr.heraliz.tactique.client.hitbox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * F3+B réservé au créatif et au spectateur (Tactique 2.39.0, demande du propriétaire du 25/10) : en survie et en
 * aventure, les boîtes de collision ne s'affichent plus (ni celles de Minecraft, ni la hitbox « en morceaux » de
 * {@link HitboxCorpsRendu}, ni celles des autres mods qui suivent le réglage de Minecraft) et la touche F3+B ne fait rien
 * (petit message au-dessus de la barre d'objets). Le staff en créatif ou en spectateur les garde. Lu par
 * BoiteBlancheJoueurMixin et BoitesDebogageToucheMixin.
 */
public final class BoitesDebogage {
   private BoitesDebogage() {
   }

   /** Vrai si le joueur peut voir les boîtes de collision (créatif ou spectateur). */
   public static boolean permises() {
      LocalPlayer p = Minecraft.getInstance().player;
      return p != null && (p.isCreative() || p.isSpectator());
   }

   /** F3+B en survie : rien ne change, le joueur est prévenu. */
   public static void refuser() {
      LocalPlayer p = Minecraft.getInstance().player;
      if (p != null) {
         p.displayClientMessage(Component.translatable("heraliz_tactique.boites.refus"), true);
      }
   }
}
