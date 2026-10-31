package fr.heraliz.tactique.client.hitbox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * F3+B seulement en créatif et en spectateur (Tactique 2.40.0, demande du propriétaire du 31/10 : « on bloque
 * complètement en survie comme si ça n'existait pas ») : en survie et en aventure, aucune boîte de collision ne
 * s'affiche (ni celles de Minecraft, ni la hitbox « en morceaux » de {@link HitboxCorpsRendu}, ni celles des autres mods
 * qui suivent le réglage de Minecraft) et F3+B ne fait rien, sans message. Le staff en créatif ou en spectateur les
 * garde. Lu par BoiteBlancheJoueurMixin et BoitesDebogageToucheMixin.
 */
public final class BoitesDebogage {
   private BoitesDebogage() {
   }

   /** Vrai si le joueur peut voir les boîtes de collision (créatif ou spectateur). */
   public static boolean permises() {
      LocalPlayer p = Minecraft.getInstance().player;
      return p != null && (p.isCreative() || p.isSpectator());
   }
}
