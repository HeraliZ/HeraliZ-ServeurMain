package fr.heralisz.n07.client;

import fr.heralisz.n07.dungeon.PropBlockEntity;
import fr.heralisz.n07.dungeon.ShrineBlockEntity;
import fr.heralisz.n07.entity.SujetN07;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * État visuel de la cuve de confinement du Sujet N-07, déduit du sanctuaire et du boss (rien n'est synchronisé en plus) :
 * <ul>
 *    <li>au repos : pleine, le sujet y flotte, les bobines tournent lentement ;</li>
 *    <li>rituel : les bobines accélèrent, les arcs frappent le verre de plus en plus fort ;</li>
 *    <li>évasion (apparition du boss) : à fond jusqu'à l'explosion, puis verre brisé, liquide qui se vide, bobines qui ralentissent ;</li>
 *    <li>après la mort, une fois la salle remise en état : le verre remonte, le liquide remplit la cuve, un clone y grandit.</li>
 * </ul>
 */
final class TankView {
   /** Vitesse des bobines (0..1), charge électrique (0..1), niveau du liquide (0..1), hauteur du verre (0..1), croissance du clone (-1 : aucun). */
   float spin, charge, level = 1, glass = 1, clone = -1;
   boolean broken;
   /** Ticks depuis l'explosion (-1 : pas d'explosion en cours). */
   float breakAge = -1;
   /** Instant des coups contre le verre (pour les éclairs), en ticks d'apparition ; -1 hors apparition. */
   float spawnT = -1;
   /** État du sanctuaire (-1 : pas de sanctuaire). */
   int shrineState = -1;

   private TankView() {}

   static TankView of(PropBlockEntity be, float partial) {
      TankView v = new TankView();
      Level l = be.getLevel();
      float now = (l == null ? 0 : l.getGameTime()) + partial;
      ShrineBlockEntity sh = be.shrine();
      SujetN07 boss = be.clientBoss instanceof SujetN07 n && n.isAlive() ? n : null;
      v.spin = 0.06F;
      if (boss != null && !boss.dormant() && boss.action() == SujetN07.Action.SPAWN) {
         float t = boss.actionTime() + partial;
         v.spawnT = t;
         if (t < SujetN07.SPAWN_BREAK) {
            v.spin = 1;
            v.charge = 1;
         } else {
            v.broken = true;
            v.breakAge = t - SujetN07.SPAWN_BREAK;
            v.spin = Math.max(0, 1 - v.breakAge / 70F);
            v.charge = Math.max(0, 0.6F - v.breakAge / 30F);
            v.level = Math.max(0, 1 - v.breakAge / 7F);
         }
      } else if (sh != null) {
         v.shrineState = sh.state();
         switch (sh.state()) {
            case ShrineBlockEntity.RITUAL -> {
               float s = sh.ritualTime(partial) - sh.kind.grant;
               v.spin = 0.06F + 0.94F * Mth.clamp(s / 70F, 0, 1);
               v.charge = Mth.clamp((s - 12) / 55F, 0, 1);
            }
            case ShrineBlockEntity.AWAKE -> {
               v.broken = true;
               v.spin = 0;
               v.level = 0;
            }
            case ShrineBlockEntity.COOLDOWN -> {
               float since = sh.sinceRestore(partial);
               if (since > now - sh.stateTime()) {
                  // pas encore remise en état depuis la mort : toujours brisée
                  v.broken = true;
                  v.spin = 0;
                  v.level = 0;
               } else {
                  v.glass = Mth.clamp(since / 60F, 0, 1);
                  v.broken = v.glass <= 0;
                  v.level = Mth.clamp((since - 60) / 140F, 0, 1);
                  v.spin = 0.06F * v.level;
                  float span = Math.max(200F, sh.nextReady() - (now - since) - 200F);
                  v.clone = since < 200 ? -1 : Mth.clamp((since - 200) / span, 0, 1);
               }
            }
            default -> {
            }
         }
      }
      // les bobines accumulent leur angle (vitesse variable)
      if (be.orbitT >= 0) {
         float dt = Mth.clamp(now - be.orbitT, 0, 5);
         be.orbitAngle = (be.orbitAngle + dt * v.spin * 16F) % 360F;
      }
      be.orbitT = now;
      return v;
   }
}
