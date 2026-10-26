package fr.heralisz.camo.decor.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import fr.heralisz.camo.decor.DecorData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Viser l'arme à la souris dans le menu : rayon depuis la caméra (projection de la dernière image en 1re personne), ramené
 * dans le repère de chaque pièce, contre ses cubes. Rend l'os de la pièce, le point touché et la normale de la face (repère de
 * l'os) et un « haut » par défaut pour ce qu'on y colle : le haut de l'écran, pour que ça se lise droit à la pose.
 */
public final class DecorPick {
   public record Hit(String bone, Vector3f point, Vector3f normal, Vector3f up) {
   }

   private DecorPick() {
   }

   @Nullable
   public static Hit pick(double mouseX, double mouseY, int guiW, int guiH) {
      DecorRender.Snapshot snap = DecorRender.lastFp;
      if (snap == null || System.currentTimeMillis() - snap.time() > 1000) return null;
      float x = (float) (2 * mouseX / guiW - 1), y = (float) (1 - 2 * mouseY / guiH);
      Matrix4f inv = new Matrix4f(snap.proj()).invert();
      Vector4f a = inv.transform(new Vector4f(x, y, -1, 1)), b = inv.transform(new Vector4f(x, y, 1, 1));
      Vector3f near = new Vector3f(a.x / a.w, a.y / a.w, a.z / a.w), far = new Vector3f(b.x / b.w, b.y / b.w, b.z / b.w);
      Vector3f dir = new Vector3f(far).sub(near);
      float best = Float.MAX_VALUE;
      DecorRender.Piece bestPiece = null;
      Vector3f bestPt = null, bestN = null;
      Matrix4f toLocal = new Matrix4f();
      for (DecorRender.Piece p : snap.pieces()) {
         toLocal.set(p.pose()).invert();
         Vector3f o = toLocal.transformPosition(new Vector3f(near)).mul(16F);
         Vector3f d = toLocal.transformDirection(new Vector3f(dir)).mul(16F);
         for (float[] c : p.cubes()) {
            float tmin = -Float.MAX_VALUE, tmax = Float.MAX_VALUE;
            int axis = -1;
            float sign = 0;
            boolean miss = false;
            for (int k = 0; k < 3; k++) {
               float ok = k == 0 ? o.x : k == 1 ? o.y : o.z, dk = k == 0 ? d.x : k == 1 ? d.y : d.z;
               float lo = c[k], hi = c[k + 3];
               if (Math.abs(dk) < 1e-9F) {
                  if (ok < lo || ok > hi) {
                     miss = true;
                     break;
                  }
                  continue;
               }
               float t1 = (lo - ok) / dk, t2 = (hi - ok) / dk;
               float s1 = -1;
               if (t1 > t2) {
                  float tt = t1;
                  t1 = t2;
                  t2 = tt;
                  s1 = 1;
               }
               if (t1 > tmin) {
                  tmin = t1;
                  axis = k;
                  sign = s1;
               }
               tmax = Math.min(tmax, t2);
               if (tmin > tmax) {
                  miss = true;
                  break;
               }
            }
            if (miss || axis < 0 || tmin <= 0 || tmin >= best) continue;
            best = tmin;
            bestPiece = p;
            bestPt = new Vector3f(o).add(new Vector3f(d).mul(tmin));
            bestN = new Vector3f(axis == 0 ? sign : 0, axis == 1 ? sign : 0, axis == 2 ? sign : 0);
         }
      }
      if (bestPiece == null) return null;
      DecorRender.Bone bone = snap.bones().get(bestPiece.bone());
      if (bone == null) return null;
      // pièce -> os
      Matrix4f boneInv = new Matrix4f(bone.pose()).invert();
      Matrix4f toBone = new Matrix4f(boneInv).mul(bestPiece.pose());
      Vector3f pt = toBone.transformPosition(bestPt.div(16F)).mul(16F);
      Vector3f n = toBone.transformDirection(bestN).normalize();
      // haut de l'écran ; sur une face tournée vers le haut ou le bas de l'écran, « vers le fond »
      Vector3f nView = bone.pose().transformDirection(new Vector3f(n)).normalize();
      Vector3f upView = Math.abs(nView.y) > 0.8F ? new Vector3f(0, 0, -Math.signum(nView.y)) : new Vector3f(0, 1, 0);
      Vector3f up = boneInv.transformDirection(upView).normalize();
      return new Hit(bone.name(), pt, n, up);
   }

   /** Plaquette StatTrack ajustée : centre, normale, haut (repère de l'os) et échelle. */
   public record Fit(Hit hit, float scale) {
   }

   /**
    * StatTrack : plaquette à plat contre la face visée, couchée dans la longueur de l'arme, proportionnée à l'arme (≈ 1/6 de sa
    * longueur) puis réduite et recentrée jusqu'à tenir entièrement sur la surface plane autour du point visé (faces coplanaires
    * de l'os) : elle ne déborde jamais. Rend null s'il n'y a pas la place.
    */
   @Nullable
   public static Fit fitPlate(Hit h, float userScale) {
      DecorRender.Snapshot snap = DecorRender.lastFp;
      if (snap == null || h == null) return null;
      DecorRender.Bone bone = snap.bones().get(h.bone());
      if (bone == null) return null;
      Vector3f n = new Vector3f(h.normal()).normalize(), p = h.point();
      // haut de l'arme (y vers le bas chez TaCZ) ; sur le dessus ou le dessous, la plaquette suit le canon (z)
      Vector3f up;
      if (Math.abs(n.y) < 0.7F) up = new Vector3f(0, -1, 0).sub(new Vector3f(n).mul(-n.y)).normalize();
      else {
         Vector3f r = new Vector3f(0, 0, 1).sub(new Vector3f(n).mul(n.z)).normalize();
         up = new Vector3f(n).cross(r).normalize();
      }
      Vector3f r = new Vector3f(up).cross(n).normalize();
      Matrix4f boneInv = new Matrix4f(bone.pose()).invert();
      Matrix4f toBone = new Matrix4f();
      List<float[]> quads = new ArrayList<>();
      Vector3f tmp = new Vector3f();
      for (DecorRender.Piece piece : snap.pieces()) {
         if (!piece.bone().equals(h.bone())) continue;
         toBone.set(boneInv).mul(piece.pose());
         for (float[] c : piece.cubes()) {
            for (int f = 0; f < 6; f++) {
               float[] fn = DecorRender.FACES[f];
               Vector3f nf = toBone.transformDirection(new Vector3f(fn[0], fn[1], fn[2])).normalize();
               if (nf.dot(n) < 0.985F) continue;
               float[][] q = DecorRender.face(c, f);
               float[] quad = new float[8];
               boolean flat = true;
               for (int i = 0; i < 4; i++) {
                  toBone.transformPosition(tmp.set(q[i][0], q[i][1], q[i][2]).div(16F)).mul(16F).sub(p);
                  if (Math.abs(tmp.dot(n)) > 0.2F) flat = false;
                  quad[i * 2] = tmp.dot(r);
                  quad[i * 2 + 1] = tmp.dot(up);
               }
               if (flat) quads.add(quad);
            }
         }
      }
      if (quads.isEmpty()) return null;
      // taille visée ~1/6 de l'arme ; au plus petit ~1/9 de l'arme ou 2,4 px (un pistolet a un petit compteur, comme dans CS)
      float len = snap.gunLen();
      float s0 = Math.max(1.6F, Math.min(8.5F, len * 0.17F)) / DecorRender.PLATE_W * userScale;
      float sMin = Math.max(0.2F, Math.min(2.4F, len * 0.11F) / DecorRender.PLATE_W);
      for (float s = s0; s >= sMin * 0.999F; s *= 0.93F) {
         float margin = 0.06F + 0.08F * s;
         float hw = DecorRender.PLATE_W * s * 0.5F + margin, hh = DecorRender.PLATE_H * s * 0.5F + margin;
         // décalages essayés du plus proche au plus loin du point visé
         float best = Float.MAX_VALUE, bx = 0, by = 0;
         for (int ix = -6; ix <= 6; ix++) {
            for (int iy = -3; iy <= 3; iy++) {
               float dx = ix * hw / 6F, dy = iy * hh / 3F, d = dx * dx + dy * dy;
               if (d < best && fits(quads, dx, dy, hw, hh)) {
                  best = d;
                  bx = dx;
                  by = dy;
               }
            }
         }
         if (best < Float.MAX_VALUE) {
            Vector3f c = new Vector3f(p).add(new Vector3f(r).mul(bx)).add(new Vector3f(up).mul(by));
            return new Fit(new Hit(h.bone(), c, n, up), s);
         }
      }
      return null;
   }

   /**
    * Place automatique du StatTrack (comme dans CS, il se pose tout seul) : on vise l'arme sur une grille de l'écran du menu, on
    * ajuste une plaquette à chaque surface plane trouvée et on garde la meilleure : grande, sur le flanc tourné vers la caméra
    * (celui qu'on voit en 1re personne), vers le milieu de l'arme (carcasse plutôt que crosse ou canon), sans recouvrir un
    * sticker déjà posé.
    */
   @Nullable
   public static Fit autoPlate(int guiW, int guiH, List<DecorData.Placement> avoid) {
      DecorRender.Snapshot snap = DecorRender.lastFp;
      if (snap == null) return null;
      record Cand(Fit fit, float sx, float facing) {
      }
      List<Cand> cands = new ArrayList<>();
      List<Vector3f> tried = new ArrayList<>();
      float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
      for (int iy = 0; iy < 11; iy++) {
         for (int ix = 0; ix < 19; ix++) {
            float sx = 0.2F + 0.75F * ix / 18F;
            Hit h = pick(guiW * sx, guiH * (0.28 + 0.52 * iy / 10.0), guiW, guiH);
            if (h == null) continue;
            minX = Math.min(minX, sx);
            maxX = Math.max(maxX, sx);
            // points trop proches d'un essai précédent (même face) : même résultat
            boolean dup = false;
            for (Vector3f t : tried) {
               if (t.distanceSquared(h.point()) < 1.2F) {
                  dup = true;
                  break;
               }
            }
            if (dup) continue;
            tried.add(new Vector3f(h.point()));
            Fit f = fitPlate(h, 1);
            if (f == null || overlaps(snap, f, avoid)) continue;
            DecorRender.Bone bone = snap.bones().get(h.bone());
            float facing = bone == null ? 0 : bone.pose().transformDirection(new Vector3f(h.normal())).normalize().z;
            cands.add(new Cand(f, sx, facing));
         }
      }
      Fit best = null;
      float bestScore = -1, mid = (minX + maxX) * 0.5F, half = Math.max(0.05F, (maxX - minX) * 0.5F);
      for (Cand c : cands) {
         float center = 1F - 0.45F * Math.min(1F, Math.abs(c.sx - mid) / half);
         float face = c.facing > 0.5F ? 1F : 0.35F;
         float score = c.fit.scale() * center * face;
         if (score > bestScore) {
            bestScore = score;
            best = c.fit;
         }
      }
      return best;
   }

   /** La plaquette recouvrirait-elle un élément déjà posé (même plan, rectangles qui se touchent) ? */
   private static boolean overlaps(DecorRender.Snapshot snap, Fit f, List<DecorData.Placement> avoid) {
      DecorRender.Bone pb = snap.bones().get(f.hit().bone());
      if (pb == null) return false;
      Vector3f n = f.hit().normal(), up = f.hit().up(), r = new Vector3f(up).cross(n).normalize(), c = f.hit().point();
      float hw = DecorRender.PLATE_W * f.scale() * 0.5F, hh = DecorRender.PLATE_H * f.scale() * 0.5F;
      Matrix4f inv = new Matrix4f(pb.pose()).invert();
      for (DecorData.Placement a : avoid) {
         DecorRender.Bone ab = snap.bones().get(a.bone());
         if (ab == null) continue;
         Vector3f q = ab.pose().transformPosition(new Vector3f(a.px(), a.py(), a.pz()).div(16F));
         inv.transformPosition(q).mul(16F).sub(c);
         float rad = DecorRender.stickerPx(snap.gunLen()) * a.scale() * 0.55F;
         if (Math.abs(q.dot(n)) < 2F && Math.abs(q.dot(r)) < hw + rad && Math.abs(q.dot(up)) < hh + rad) return true;
      }
      return false;
   }

   /** La plaquette (centre dx, dy ; demi-côtés hw, hh) est-elle entièrement sur les faces ? Échantillons en grille. */
   private static boolean fits(List<float[]> quads, float dx, float dy, float hw, float hh) {
      for (int i = 0; i <= 6; i++) {
         for (int j = 0; j <= 2; j++) {
            float x = dx - hw + 2 * hw * i / 6F, y = dy - hh + 2 * hh * j / 2F;
            boolean in = false;
            for (float[] q : quads) {
               if (inQuad(q, x, y)) {
                  in = true;
                  break;
               }
            }
            if (!in) return false;
         }
      }
      return true;
   }

   /** Point dans un quadrilatère convexe (sens quelconque), avec une petite tolérance sur les bords. */
   private static boolean inQuad(float[] q, float x, float y) {
      int pos = 0, neg = 0;
      for (int i = 0; i < 4; i++) {
         float ax = q[i * 2], ay = q[i * 2 + 1], bx = q[(i + 1) % 4 * 2], by = q[(i + 1) % 4 * 2 + 1];
         float ex = bx - ax, ey = by - ay;
         float cr = ex * (y - ay) - ey * (x - ax);
         float el = (float) Math.sqrt(ex * ex + ey * ey);
         if (cr > 0.02F * el) pos++;
         else if (cr < -0.02F * el) neg++;
      }
      return pos == 0 || neg == 0;
   }
}
