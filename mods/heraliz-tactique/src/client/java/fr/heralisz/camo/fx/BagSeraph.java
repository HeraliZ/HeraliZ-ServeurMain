package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.fract;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 208 Séraphin sur un sac : deux grandes ailes d'ange sortent du haut du dos, derrière le sac, et respirent lentement
 * (elles s'ouvrent et se replient à peine) ; une auréole d'or flotte au-dessus de la tête ; des éclats de lumière et parfois
 * une plume tombent doucement.
 * <p>Les plumes sont dessinées pleines (sorte 6, opaques, bord net) et rangées en couches décalées de quelques millimètres :
 * elles restent justes devant ou derrière le joueur quel que soit l'ordre de rendu, sans se battre entre elles. Une lueur
 * dorée (sorte 2, additive) les double.
 */
final class BagSeraph implements BagEffects.BagEffect {
   static final int FEATHER_GLOW = 2, HALO = 3, HALO_GLOW = 4, MOTE = 5, FEATHER_SOLID = 6;

   /** Squelette d'une aile pour cette image : épaule, coude, poignet, axes du plan de l'aile. */
   private static final class Wing {
      V sh, elbow, wrist, along, upw, nrm;
      float span;
   }

   private final Wing[] wings = {new Wing(), new Wing()};

   @Override
   public void build(FxContext c, BagFrame b) {
      float t = c.t, k = b.scale;
      for (int i = 0; i < 2; i++) {
         this.wing(c, b, this.wings[i], i == 0 ? -1 : 1, t, k);
      }
      this.halo(c, b, t, k);
      this.drift(c, t, k);
   }

   private void wing(FxContext c, BagFrame b, Wing w, int sg, float t, float k) {
      V S = b.side.mul(sg), U = b.up, O = b.out;
      // respiration lente (~5 s) : l'aile s'ouvre sur le côté puis se replie un peu vers l'arrière ; la main se relève en ouvrant
      float breathe = 0.5F + 0.5F * sin(t * 1.25F);
      // grandes ailes ouvertes sur les côtés (vues en entier de dos), un peu en arrière, relevées en V
      float open = 0.72F + 0.12F * breathe;
      V along = S.mul(open).add(O, 1 - open).add(U, 0.44F + 0.06F * breathe).norm();
      V upw = U.perp(along);
      V nrm = along.cross(upw).norm();
      if (nrm.dot(O) < 0) nrm = nrm.neg();
      float W = 1.25F * k;
      // épaules : haut du dos, derrière le sac, un peu écartées (les ailes sortent de derrière ses coins hauts)
      V back = b.neck.add(O, b.backDepth() * 0.8F);
      V sh = back.add(U, -0.08F * k).add(S, Math.max(0.45F * b.hx, 0.1F * k));
      V elbow = sh.add(along, 0.30F * W).add(upw, 0.24F * W);
      V wrist = elbow.add(along, 0.31F * W).add(upw, 0.05F * W + 0.03F * W * breathe);
      w.sh = sh;
      w.elbow = elbow;
      w.wrist = wrist;
      w.along = along;
      w.upw = upw;
      w.nrm = nrm;
      w.span = W;
      int id0 = sg > 0 ? 0 : 200;
      float mm = 0.0011F * k;   // écart entre plumes voisines (évite qu'elles se battent à l'écran)
      // rémiges primaires : éventail depuis la main, les plus longues vers l'extérieur
      for (int i = 0; i < 11; i++) {
         float q = i / 10F;
         V root = wrist.lerp(elbow, q * 0.42F);
         V dir = along.mul(0.96F - 0.78F * q).add(upw, 0.36F - 1.08F * q).norm();
         feather(c, root, dir, nrm, (0.52F - 0.15F * q) * W, 0.25F, mm * i, id0 + i, t, 0);
      }
      // rémiges secondaires : le long de l'avant-bras puis du bras, vers le bas
      for (int i = 0; i < 13; i++) {
         float q = i / 12F;
         V root = arm(w, q);
         V dir = upw.neg().add(along, 0.42F - 0.46F * q).norm();
         feather(c, root, dir, nrm, (0.40F - 0.15F * q) * W, 0.30F, 0.006F * k + mm * i, id0 + 20 + i, t, 0.25F);
      }
      // grandes couvertures : deuxième rang, plus court, qui cache la base des rémiges
      for (int i = 0; i < 14; i++) {
         float q = i / 13F;
         V root = arm(w, q * 0.96F).add(upw, 0.03F * W);
         V dir = upw.neg().add(along, 0.30F - 0.34F * q).norm();
         feather(c, root, dir, nrm, 0.20F * W, 0.42F, 0.012F * k + mm * i, id0 + 40 + i, t, 0.55F);
      }
      // petites couvertures : le bord d'attaque de l'aile, arrondi et plein
      for (int i = 0; i < 12; i++) {
         float q = i / 11F;
         V root = arm(w, q * 0.98F).add(upw, 0.07F * W);
         V dir = upw.neg().add(along, 0.18F - 0.2F * q).norm();
         feather(c, root, dir, nrm, 0.115F * W, 0.55F, 0.018F * k + mm * i, id0 + 60 + i, t, 0.8F);
      }
      // scapulaires : couvrent la jonction avec le dos
      for (int i = 0; i < 5; i++) {
         float q = i / 4F;
         V root = sh.add(along, 0.05F * W * q).add(upw, 0.02F * W);
         V dir = upw.neg().add(along, 0.35F).add(S, -0.2F).norm();
         feather(c, root, dir, nrm, (0.16F - 0.03F * q) * W, 0.5F, 0.024F * k + mm * i, id0 + 80 + i, t, 0.9F);
      }
   }

   /** Point du bras de l'aile : 0 = près du poignet, 1 = épaule (en passant par le coude). */
   private static V arm(Wing w, float q) {
      V a = w.wrist.lerp(w.elbow, 0.35F);
      return q < 0.6F ? a.lerp(w.elbow, q / 0.6F) : w.elbow.lerp(w.sh, (q - 0.6F) / 0.4F);
   }

   /** Une plume : pleine et nette (sorte 6), doublée d'une lueur dorée ; tone = rang (plus doré vers le bord d'attaque). */
   private static void feather(FxContext c, V root, V dir, V nrm, float len, float width, float lift, int id, float t, float tone) {
      float ruffle = 0.035F * sin(t * 2.1F + id * 0.9F);
      V d = dir.rot(nrm, ruffle);
      V across = nrm.cross(d).norm();
      V center = root.add(d, len * 0.5F).add(nrm, lift);
      c.mesh.set(SOLID, FEATHER_SOLID).p(tone, 0).seed(id & 0x7FFF, 0);
      FxDraw.quad(c, center, across.mul(len * width * 0.5F), d.mul(len * 0.5F));
      c.mesh.set(ADD, FEATHER_GLOW).p(tone, 0).seed(id & 0x7FFF, 0);
      FxDraw.quad(c, center.add(nrm, 0.0015F), across.mul(len * width * 0.58F), d.mul(len * 0.52F));
   }

   /** Auréole d'or au-dessus de la tête (placée d'après la nuque, pas d'après le sac : même hauteur sur tous les sacs). */
   private void halo(FxContext c, BagFrame b, float t, float k) {
      V head = b.neck.add(b.up, 0.64F * k + 0.012F * k * sin(t * 1.1F)).add(b.out, -0.14F * k);
      V axis = b.up.rot(b.side, 0.2F);
      c.mesh.set(SOLID, HALO).seed(1, 0);
      FxDraw.torus(c, head, axis, b.side, 0.17F * k, 0.17F * k, 0.014F * k, c.count(40, 22), 6);
      c.mesh.set(ADD, HALO_GLOW).seed(2, 0);
      FxDraw.torus(c, head, axis, b.side, 0.17F * k, 0.17F * k, 0.045F * k, c.count(40, 22), 6);
   }

   /** Éclats de lumière qui glissent le long des ailes et plumes qui se détachent de temps en temps. */
   private void drift(FxContext c, float t, float k) {
      for (int j = 0; j < c.count(18, 8); j++) {
         Wing w = this.wings[j & 1];
         float life = fract(t * 0.22F + hash(j, 7, 1));
         V p = arm(w, hash(j, 7, 2)).add(w.upw, -w.span * (0.1F + 0.35F * hash(j, 7, 3)) - life * 0.3F * k).add(w.nrm, 0.03F * k);
         c.mesh.set(ADD, MOTE).p(sin(3.1416F * life), 0).seed(j, 0);
         FxDraw.billboard(c, p, 0.028F * k, 0.028F * k, 0);
      }
      for (int j = 0; j < 3; j++) {
         float per = 5 + 2 * hash(j, 1, 1);
         float cyc = t / per + hash(j, 1, 2);
         int ci = (int) Math.floor(cyc);
         float life = cyc - ci;
         if (hash(j, ci, 9) < 0.4F) continue;
         Wing w = this.wings[ci & 1];
         V start = w.wrist.lerp(w.elbow, hash(j, ci, 3)).add(w.upw, -0.3F * w.span);
         V p = start.add(c.up, -life * 0.9F * k).add(w.along, sin(life * 5 + j) * 0.08F * k);
         V dir = c.up.neg().rot(w.along, sin(life * 6 + j) * 0.9F).rot(c.up, t * 0.8F + j);
         float grow = smooth(0, 0.12F, life) * (1 - smooth(0.8F, 1, life));
         float len = 0.09F * k * grow;
         if (len < 0.004F) continue;
         V n = dir.anyPerp();
         c.mesh.set(SOLID, FEATHER_SOLID).p(0.3F, 0).seed(300 + j, 0);
         FxDraw.quad(c, p, n.cross(dir).norm().mul(len * 0.16F), dir.mul(len * 0.5F));
      }
   }
}
