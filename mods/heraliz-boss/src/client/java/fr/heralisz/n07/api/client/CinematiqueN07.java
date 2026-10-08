package fr.heralisz.n07.api.client;

import net.minecraftforge.eventbus.api.Event;

/**
 * Cote CLIENT : une cinematique du Sujet N-07 est-elle a l'ecran (scene scriptee, cinematique du boss ou rituel du
 * terminal) ? Les autres mods cachent alors leur HUD (le mod du boss annule deja tous les calques Forge sauf les siens).
 * <pre>
 *    if (ModList.get().isLoaded("heralisz_n07") &amp;&amp; CinematiqueN07.enCours()) return; // pas de HUD
 * </pre>
 * Un evenement {@link Changement} est aussi poste sur MinecraftForge.EVENT_BUS (fil client) a chaque debut / fin.
 */
public final class CinematiqueN07 {
   private CinematiqueN07() {}

   /** Vrai pendant une scene, une cinematique du boss ou un rituel (camera et commandes prises par le boss). */
   public static boolean enCours() {
      return fr.heralisz.n07.client.Cinematic.active() || fr.heralisz.n07.client.ScenePlayer.active() || fr.heralisz.n07.client.Ritual.localActive();
   }

   /** Vrai si la camera est celle d'une cinematique (pas seulement le gel du rituel). */
   public static boolean cameraPrise() {
      return fr.heralisz.n07.client.Cinematic.active() || fr.heralisz.n07.client.ScenePlayer.active();
   }

   /** Debut (enCours = true) ou fin d'une cinematique, fil client. */
   public static final class Changement extends Event {
      public final boolean enCours;

      public Changement(boolean enCours) {
         this.enCours = enCours;
      }
   }
}
