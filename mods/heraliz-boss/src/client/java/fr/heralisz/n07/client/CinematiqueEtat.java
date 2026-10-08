package fr.heralisz.n07.client;

import fr.heralisz.n07.api.client.CinematiqueN07;
import net.minecraftforge.common.MinecraftForge;

/** Poste CinematiqueN07.Changement a chaque debut / fin de cinematique (appele a chaque tick client). */
final class CinematiqueEtat {
   private static boolean last;

   private CinematiqueEtat() {}

   static void tick() {
      boolean now = CinematiqueN07.enCours();
      if (now != last) {
         last = now;
         MinecraftForge.EVENT_BUS.post(new CinematiqueN07.Changement(now));
      }
   }
}
