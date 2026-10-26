package fr.heraliz.tactique.client.inspection;

import com.tacz.guns.client.sound.GunSoundInstance;
import java.util.ArrayDeque;

/**
 * Inspection (touche de TaCZ) d'une arme ou d'un couteau : appuyer plusieurs fois empilait les sons (jusqu'à 8 à la fois).
 * TaCZ ne coupe que le dernier son lancé ; les bruitages des animations (images clés) continuaient tous. On garde ici les
 * sons d'arme lancés par le joueur depuis la dernière inspection et on les coupe tous quand une nouvelle commence.
 * Un appui à moins de 0,25 s du précédent (touche tenue, répétition du clavier) est ignoré.
 */
public final class SonsInspection {
   private static final int MAX = 64;
   private static final long ECART_MIN_MS = 250;
   private static final ArrayDeque<GunSoundInstance> SONS = new ArrayDeque<>();
   private static long dernier;

   private SonsInspection() {
   }

   /** Un son d'arme vient d'être lancé pour le joueur lui-même. */
   public static void noter(GunSoundInstance son) {
      if (son == null) {
         return;
      }
      if (SONS.size() >= MAX) {
         SONS.pollFirst();
      }
      SONS.addLast(son);
   }

   /** Nouvelle inspection demandée : false si l'appui est à ignorer ; sinon les sons précédents sont coupés. */
   public static boolean nouvelle() {
      long t = System.currentTimeMillis();
      if (t - dernier < ECART_MIN_MS) {
         return false;
      }
      dernier = t;
      GunSoundInstance s;
      while ((s = SONS.pollFirst()) != null) {
         try {
            s.setStop();
         } catch (RuntimeException ignore) {
            // son déjà retiré par le moteur du jeu
         }
      }
      return true;
   }
}
