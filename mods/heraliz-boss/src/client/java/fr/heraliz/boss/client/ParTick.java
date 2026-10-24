package fr.heraliz.boss.client;

import java.lang.ref.WeakReference;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Résultat d'une recherche (le boss le plus proche, par exemple) gardé pour tout le tick de jeu en cours : la barre de vie
 * (à chaque image), la musique, l'ambiance et le blizzard (à chaque tick) relisaient chacun les entités autour du joueur,
 * cinq boss à la fois, partout sur la carte. Les entités ne bougent qu'entre deux ticks : le résultat est le même.
 * <p>Références faibles : rien ne retient l'ancien monde après une déconnexion.
 */
public final class ParTick<T> {
   private final Function<Minecraft, T> calcul;
   private WeakReference<ClientLevel> niveau = new WeakReference<>(null);
   private WeakReference<T> valeur = new WeakReference<>(null);
   private long tick = Long.MIN_VALUE;

   /** @param calcul recherche faite au plus une fois par tick (monde et joueur présents) */
   public ParTick(Function<Minecraft, T> calcul) {
      this.calcul = calcul;
   }

   @Nullable
   public T get(Minecraft mc) {
      ClientLevel l = mc.level;
      if (l == null || mc.player == null) {
         niveau.clear();
         valeur.clear();
         tick = Long.MIN_VALUE;
         return null;
      }
      long t = l.getGameTime();
      if (t == tick && niveau.get() == l) return valeur.get();
      T v = calcul.apply(mc);
      niveau = new WeakReference<>(l);
      valeur = new WeakReference<>(v);
      tick = t;
      return v;
   }
}
