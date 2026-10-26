package fr.heralisz.camo.fx;

import static fr.heralisz.camo.fx.FxContext.TAU;
import static fr.heralisz.camo.fx.FxContext.cos;
import static fr.heralisz.camo.fx.FxContext.hash;
import static fr.heralisz.camo.fx.FxContext.sin;
import static fr.heralisz.camo.fx.FxContext.smooth;
import static fr.heralisz.camo.fx.FxMesh.ADD;
import static fr.heralisz.camo.fx.FxMesh.SOLID;

/**
 * 233 Trophée : le skin évolue avec les kills de l'arme (CamoData.KILLS, compté par le serveur) en suivant les matériaux de
 * Minecraft — pierre, fer (25), or (100), diamant (250), netherite (500), ascendant (1000) ; la surface (trophy dans
 * camo_template) fait gagner le matériau suivant depuis le canon au fil des kills. Ici : le compteur façon StatTrak vissé sur
 * les deux flancs du boîtier (chiffres lumineux), et les effets des paliers hauts (éclats du diamant, braises de la
 * netherite, anneau de runes dorées de l'ascendant).
 */
final class FxTrophy implements FxEffect {
   static final int PLATE = 1, DIGIT = 2, SPARKLE = 3, EMBER = 4, RUNE = 5;
   public static final int[] TIERS = {0, 25, 100, 250, 500, 1000};

   static int tier(int kills) {
      int t = 0;
      for (int i = 1; i < TIERS.length; i++) if (kills >= TIERS[i]) t = i;
      return t;
   }

   @Override
   public void build(FxContext c) {
      int kills = Math.max(0, Math.min(99999, c.kills));
      int tier = tier(kills);
      float L = c.L(), t = c.t;
      GunShape s = c.shape;
      this.counter(c, s, kills);
      Surfaces sf = c.surf;
      if (tier >= 3 && sf.n > 0) {
         // diamant et au-delà : éclats qui scintillent sur l'arme
         for (int k = 0; k < 6; k++) {
            float cyc = t * 0.7F + hash(k, 3, 51);
            int ci = (int) Math.floor(cyc);
            float g = cyc - ci;
            float k0 = smooth(0, 0.12F, g) * (1 - smooth(0.2F, 0.45F, g));
            if (k0 < 0.02F) continue;
            int fi = sf.pick(hash(k, ci, 52));
            if (fi < 0) continue;
            V p = sf.point(fi, hash(k, ci, 53), hash(k, ci, 54)).add(sf.normal(fi), 0.004F * L);
            c.mesh.set(ADD, SPARKLE).p(k0, tier >= 5 ? 1 : 0).seed(k, ci & 0x7FFF);
            FxDraw.billboard(c, p, 0.08F * L * k0, 0.08F * L * k0, g * 3);
         }
      }
      if (tier >= 4 && sf.n > 0) {
         // netherite et au-delà : braises violettes qui montent
         for (int k = 0; k < 8; k++) {
            float cyc = t * 0.45F + hash(k, 5, 55);
            int ci = (int) Math.floor(cyc);
            float g = cyc - ci;
            int fi = sf.pick(hash(k, ci, 56));
            if (fi < 0) continue;
            V p = sf.point(fi, hash(k, ci, 57), hash(k, ci, 58)).add(c.up, L * 0.08F * g).add(sf.normal(fi), L * 0.01F * g);
            c.mesh.set(ADD, EMBER).p((1 - g) * smooth(0, 0.1F, g), tier >= 5 ? 1 : 0).seed(k, ci & 0x7FFF);
            FxDraw.billboard(c, p, 0.025F * L, 0.025F * L, 0);
         }
      }
      if (tier >= 5) {
         // ascendant : anneau de runes dorées qui tourne lentement autour du milieu de l'arme
         float xm = s.xAt(0.55F);
         float yc = (s.top(xm) + s.bottom(xm)) * 0.5F, zc = (s.zLo(xm) + s.zHi(xm)) * 0.5F;
         V ctr = c.f.at(xm, yc, zc);
         float r = Math.max(s.top(xm) - s.bottom(xm), s.halfW(xm) * 2) * 0.9F + 0.05F;
         for (int k = 0; k < 10; k++) {
            float a = TAU * k / 10 + t * 0.4F;
            V p = ctr.add(c.f.y, cos(a) * r * L).add(c.f.z, sin(a) * r * L).add(c.f.x, 0.02F * L * sin(t * 1.3F + k));
            c.mesh.set(ADD, RUNE).p(0.7F + 0.3F * sin(t * 2 + k), hash(k, 7, 59)).seed(k, 0);
            FxDraw.billboard(c, p, 0.05F * L, 0.05F * L, 0);
         }
      }
   }

   /** Compteur à 5 chiffres sur les deux flancs du boîtier, au-dessus de la détente. */
   private void counter(FxContext c, GunShape s, int kills) {
      float x = s.xAt(0.42F);
      float y = s.top(x) - 0.03F;
      V F = c.f.x, U = c.f.y;
      int[] digits = new int[5];
      int v = kills;
      for (int i = 4; i >= 0; i--) {
         digits[i] = v % 10;
         v /= 10;
      }
      int lead = 0;
      while (lead < 4 && digits[lead] == 0) lead++;
      for (int side = -1; side <= 1; side += 2) {
         float z = side > 0 ? s.zHi(x) + 0.004F : s.zLo(x) - 0.004F;
         V n = c.f.z.mul(side);
         V ctr = c.f.at(x, y, z);
         float k = c.L();
         // plaquette sombre
         c.mesh.set(SOLID, PLATE).p(0, 0).seed(side + 1, 0);
         FxDraw.box(c, ctr, F.mul(0.052F * k), U.mul(0.016F * k), n.mul(0.003F * k));
         // lecture de gauche à droite pour qui regarde ce flanc
         V right = F.mul(side);
         for (int i = 0; i < 5; i++) {
            V p = ctr.add(right, (i - 2) * 0.019F * k).add(n, 0.0032F * k);
            c.mesh.set(ADD, DIGIT).p(digits[i] / 10F + 0.02F, i < lead ? 0.25F : 1F).seed(i, side + 1);
            FxDraw.quad(c, p, right.mul(0.0085F * k), U.mul(0.012F * k));
         }
      }
   }
}
