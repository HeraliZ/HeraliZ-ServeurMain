package fr.heraliz.boss.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.boss.client.kit.Ui;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * HUD commun aux cinq boss (maquettes validées) : la barre de vie en haut de l'écran (nom, épithète, phase en trois traits,
 * piste à trois crans, pourcentage) et la carte de titre des cinématiques (filets, bandeau, grand titre lettre par lettre
 * avec un halo, sous-titre). Chaque boss garde ses couleurs : accent (traits, halo, lueur) et dégradé de la jauge.
 * <p>Positions calées sur les métriques de DM Sans (haut des capitales à 0,224 × taille + 1, ligne de base à 0,762 × taille
 * + 1). Les textes d'une même image sont envoyés en un seul lot ({@code drawManaged}).
 */
public final class HudBoss {
   private HudBoss() {
   }

   /** Petites capitales (épithète, phase, pourcentage), en moyen et en gras, et grand titre des cinématiques. */
   public static final ResourceLocation MINI = new ResourceLocation("heraliz_boss", "ui_mini"),
         MINI_GRAS = new ResourceLocation("heraliz_boss", "ui_mini_bold"), TITRE = new ResourceLocation("heraliz_boss", "ui_titre");
   private static final String[] ROMAINS = {"I", "II", "III"};
   private static final int SOMBRE = 0x06080A;

   // ------------------------------------------------------------------ barre de vie

   /** Barre de vie d'un boss : vie affichée (lissée), traînée blanche des dégâts récents, apparition, éclair de phase. */
   public static final class Barre {
      private int suivi = -1, phase = 1, acc = 0xFFFFFF, dessus = 0xFFFFFF, dessous = 0x808080;
      private float vie = 1, trainee = 1, derniere = 1, apparition;
      private long dernierCoup = -100, flashPhase = -100, derniereImage;

      /**
       * Vrai si la barre n'a rien à dessiner (aucun boss suivi, fondu de sortie fini) : l'appelant n'a alors ni textes à
       * traduire ni barre à appeler (cinq barres testées à chaque image, partout sur la carte). La prochaine apparition
       * repart d'une image sans saut ({@code derniereImage} remis à zéro, comme au premier affichage).
       */
      public boolean auRepos(@Nullable LivingEntity boss, boolean visible) {
         if (boss != null || visible || apparition > 0) return false;
         suivi = -1;
         derniereImage = 0;
         return true;
      }

      /**
       * Dessine la barre, ou la fait disparaître en fondu.
       *
       * @param boss      boss suivi (null : aucun)
       * @param visible   la barre doit être montrée (pas de cinématique, boss pas encore effacé)
       * @param phaseBoss phase du boss (1 à 3)
       * @param nom       nom en capitales, {@code epithete} : sous-titre (vide : aucun)
       * @param accent    couleur du boss (traits de phase, lueurs), {@code haut} / {@code bas} : dégradé de la jauge ; sans
       *                  boss, les dernières couleurs reçues restent (pas de saut de couleur pendant le fondu)
       */
      public void dessiner(GuiGraphics g, float partial, int w, @Nullable LivingEntity boss, boolean visible, int phaseBoss, String nom,
                           String epithete, int accent, int haut, int bas) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) return;
         long nanos = System.nanoTime();
         float dt = derniereImage == 0 ? 0 : Math.min(0.1F, (nanos - derniereImage) / 1e9F);
         derniereImage = nanos;
         if (!visible) {
            apparition = Math.max(0, apparition - dt * 3);
            if (boss == null) suivi = -1;
            if (apparition <= 0) return;
         } else {
            apparition = Math.min(1, apparition + dt * 2.5F);
         }
         long tick = mc.level.getGameTime();
         if (boss != null) {
            float pv = Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0, 1);
            if (boss.getId() != suivi) {
               suivi = boss.getId();
               vie = trainee = derniere = pv;
               phase = phaseBoss;
            }
            if (pv < derniere - 1e-4F) dernierCoup = tick;
            derniere = pv;
            if (phaseBoss != phase) {
               flashPhase = tick;
               phase = phaseBoss;
            }
            acc = accent;
            dessus = haut;
            dessous = bas;
         }
         int teinte = acc;
         vie += (derniere - vie) * Math.min(1, dt * 16);
         if (tick - dernierCoup > 12) trainee += (vie - trainee) * Math.min(1, dt * 3);
         if (trainee < vie) trainee = vie;

         float t = tick + partial;
         float ease = 1 - (1 - apparition) * (1 - apparition);
         int a = Math.round(ease * 255);
         Font font = mc.font;
         boolean mort = boss == null || boss.isDeadOrDying();
         int bw = Math.min(Mth.clamp(Math.round(w * 0.46F), 182, 300), w - 24);
         int x0 = (w - bw) / 2 + (tick - dernierCoup < 3 ? Math.round(Mth.sin(t * 9) * 1.5F) : 0);
         int y = Math.round(10 - (1 - ease) * 22);
         int ph = Mth.clamp(phase, 1, 3);

         // voile sombre en haut de l'écran : la barre reste lisible sur un ciel clair ou dans le blizzard
         voile(g, w, Math.round(120 * ease));

         // trois traits de phase, à droite
         int trait = 9, ecart = 3, yTrait = y + 7;
         int xTraits = x0 + bw - (3 * trait + 2 * ecart);
         for (int i = 0; i < 3; i++) {
            int px = xTraits + i * (trait + ecart);
            boolean allume = i < ph;
            if (allume) g.fill(px - 1, yTrait - 1, px + trait + 1, yTrait + 3, Ui.argb(teinte, 56 * a / 255));
            g.fill(px, yTrait, px + trait, yTrait + 2, allume ? Ui.argb(teinte, a) : Ui.argb(0xFFFFFF, 46 * a / 255));
         }

         // piste : fond sombre, cadre fin, lueur, dégradé du boss, traînée blanche des dégâts, crans des phases
         int by = y + 16, iw = bw - 2;
         g.fill(x0, by, x0 + bw, by + 5, Ui.argb(SOMBRE, 184 * a / 255));
         int cadre = Ui.argb(0xFFFFFF, 36 * a / 255);
         g.fill(x0, by, x0 + bw, by + 1, cadre);
         g.fill(x0, by + 4, x0 + bw, by + 5, cadre);
         g.fill(x0, by + 1, x0 + 1, by + 4, cadre);
         g.fill(x0 + bw - 1, by + 1, x0 + bw, by + 4, cadre);
         int fw = Math.round(iw * vie), tw = Math.round(iw * trainee);
         if (tw > fw) g.fill(x0 + 1 + fw, by + 1, x0 + 1 + tw, by + 4, Ui.argb(0xFFFFFF, 217 * a / 255));
         if (fw > 0) {
            int haut0 = ph == 3 ? Ui.mix(dessus, 0xFFFFFF, (0.5F + 0.5F * Mth.sin(t * 0.35F)) * 0.3F) : dessus;
            g.fill(x0, by, x0 + 2 + fw, by + 5, Ui.argb(teinte, 46 * a / 255));
            g.fillGradient(x0 + 1, by + 1, x0 + 1 + fw, by + 4, Ui.argb(haut0, a), Ui.argb(dessous, a));
            g.fill(x0 + 1, by + 1, x0 + 1 + fw, by + 2, Ui.argb(0xFFFFFF, 64 * a / 255));
            float balayage = (t % 60) / 60F * (iw + 40) - 20;
            int s0 = (int) Math.max(x0 + 1, x0 + 1 + balayage - 8), s1 = (int) Math.min(x0 + 1 + fw, x0 + 1 + balayage + 8);
            if (s1 > s0) g.fill(s0, by + 1, s1, by + 4, Ui.argb(0xFFFFFF, 40 * a / 255));
            if (tick - dernierCoup < 2) g.fill(x0 + 1, by + 1, x0 + 1 + fw, by + 4, Ui.argb(0xFFFFFF, 110 * a / 255));
            g.fillGradient(x0 + 1, by + 5, x0 + 1 + fw, by + 9, Ui.argb(teinte, 44 * a / 255), Ui.argb(teinte, 0));
         }
         for (int i = 1; i <= 2; i++) {
            int cx = x0 + Math.round(bw * i / 3F);
            g.fill(cx, by, cx + 1, by + 5, Ui.argb(SOMBRE, 217 * a / 255));
         }
         float eclair = Mth.clamp(1 - (t - flashPhase) / 16F, 0, 1);
         if (eclair > 0) g.fill(x0 - 1, by - 1, x0 + bw + 1, by + 6, Ui.argb(Ui.mix(teinte, 0xFFFFFF, 0.6F), Math.round(150 * eclair * ease)));

         // textes, en un seul lot : nom (et épithète s'il y a la place), « PHASE II », pourcentage
         if (a < 4) return;
         String phaseTxt = Component.translatable("hud.heraliz_boss.phase", ROMAINS[ph - 1]).getString();
         float phaseW = largeur(font, phaseTxt, MINI, 1, 0.7F);
         float nomW = largeur(font, nom, Ui.BOLD, 1.2F, 2.0F);
         float place = xTraits - 6 - phaseW - 10 - (x0 + nomW + 7);
         boolean avecEpithete = !epithete.isEmpty() && largeur(font, epithete, MINI_GRAS, 1, 0.8F) <= place;
         int p = vie <= 0.0005F ? 0 : Math.max(1, Math.round(vie * 100));
         String pct = p + " %";
         g.drawManaged(() -> {
            caps(g, font, nom, Ui.BOLD, x0, y, 1.2F, 2.0F, Ui.argb(mort ? 0xA8ADB5 : 0xFFFFFF, a), true);
            if (avecEpithete) caps(g, font, epithete, MINI_GRAS, x0 + nomW + 7, y + 4, 1, 0.8F, Ui.argb(teinte, a), true);
            caps(g, font, phaseTxt, MINI, xTraits - 6 - phaseW, y + 4, 1, 0.7F, Ui.argb(Ui.TEXT, 178 * a / 255), true);
            caps(g, font, pct, MINI_GRAS, x0 + bw - largeur(font, pct, MINI_GRAS, 1, 0.65F), by + 6, 1, 0.65F, Ui.argb(0xFFFFFF, a), true);
         });
      }
   }

   /** Voile sombre en haut de l'écran, plus fort au centre (dégradé lisse, sans bandes). */
   private static void voile(GuiGraphics g, int w, int alpha) {
      if (alpha < 4) return;
      int n = 12, hauteur = 72;
      float x0 = w * 0.12F, x1 = w * 0.88F;
      Matrix4f m = g.pose().last().pose();
      VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
      for (int i = 0; i < n; i++) {
         float u0 = (float) i / n, u1 = (float) (i + 1) / n;
         float k0 = 1 - (2 * u0 - 1) * (2 * u0 - 1), k1 = 1 - (2 * u1 - 1) * (2 * u1 - 1);
         float xa = x0 + (x1 - x0) * u0, xb = x0 + (x1 - x0) * u1;
         int a0 = Math.round(alpha * k0 * k0), a1 = Math.round(alpha * k1 * k1);
         vc.vertex(m, xa, 0, 0).color(4, 8, 14, a0).endVertex();
         vc.vertex(m, xa, hauteur, 0).color(4, 8, 14, 0).endVertex();
         vc.vertex(m, xb, hauteur, 0).color(4, 8, 14, 0).endVertex();
         vc.vertex(m, xb, 0, 0).color(4, 8, 14, a1).endVertex();
      }
      g.flush();
   }

   // ------------------------------------------------------------------ carte de titre des cinématiques

   /**
    * Carte de titre (maquette validée) : deux filets qui s'étirent depuis le centre, bandeau en petites capitales, grand
    * titre qui s'écrit lettre par lettre (parasites au début) avec un halo de la couleur du boss, puis le sous-titre.
    * Un titre trop long se resserre puis rétrécit pour tenir dans l'écran ; un sous-titre en phrase (minuscules) reste
    * en phrase.
    *
    * @param tt  ticks depuis l'apparition de la carte
    * @param out 1 puis 0 pendant la disparition
    */
   public static void carteTitre(GuiGraphics g, int w, int h, float tt, float out, String bandeau, String titre, String sousTitre, int accent,
                                 int couleurBandeau) {
      Font font = Minecraft.getInstance().font;
      float cx = w / 2F;
      int y0 = Math.round(Math.min(h * 0.593F, h * 0.88F - 86));
      // filets
      int demi = Math.round(w * 0.235F * Math.min(1, tt / 12F));
      int af = Math.round(200 * out);
      if (demi > 1 && af >= 4) {
         Ui.hairline(g, Math.round(cx) - demi, Math.round(cx) + demi, y0, accent, af, true, true);
         Ui.hairline(g, Math.round(cx) - demi, Math.round(cx) + demi, y0 + 80, accent, af, true, true);
      }
      String haut = bandeau == null ? "" : bandeau.toUpperCase(Locale.ROOT);
      String up = titre == null ? "" : titre.toUpperCase(Locale.ROOT);
      String bas = sousTitre == null ? "" : sousTitre;
      int aBandeau = Math.round(255 * Mth.clamp(tt / 6F, 0, 1) * out);
      int aTitre = Math.round(255 * out);
      int aSous = Math.round(255 * Mth.clamp((tt - 10) / 10F, 0, 1) * out);
      // titre : échelle et espacement qui tiennent dans l'écran
      float espace = 5.7F, maxW = w * 0.9F;
      float largeur1 = largeur(font, up, TITRE, 1, espace);
      float echelle = largeur1 <= 0 ? 1.82F : Math.min(1.82F, maxW / largeur1);
      if (echelle < 1.2F) {
         espace = 2.0F;
         largeur1 = largeur(font, up, TITRE, 1, espace);
         echelle = largeur1 <= 0 ? 1.82F : Math.min(1.82F, maxW / largeur1);
      }
      float yTitre = y0 + 36.75F - 11.85F * echelle;
      int nLettres = Math.min(up.length(), (int) (tt / 1.6F));
      float parasites = tt < 14 ? tt : 0;
      int couleurTitre = Ui.mix(accent, 0xFFFFFF, 0.8F);
      float xTitre = cx - largeur1 * echelle / 2;
      float espaceFinal = espace, echelleFinale = echelle;
      boolean phrase = !bas.equals(bas.toUpperCase(Locale.ROOT));
      g.drawManaged(() -> {
         if (!haut.isEmpty() && aBandeau >= 4) {
            float lw = largeur(font, haut, MINI_GRAS, 1.15F, 2.0F);
            caps(g, font, haut, MINI_GRAS, cx - lw / 2, y0 + 7, 1.15F, 2.0F, Ui.argb(couleurBandeau, aBandeau), true);
         }
         if (nLettres > 0 && aTitre >= 4) {
            lettres(g, font, up, nLettres, xTitre, yTitre, echelleFinale, espaceFinal, couleurTitre, accent, aTitre, parasites);
         }
         if (!bas.isEmpty() && aSous >= 4) {
            int couleur = Ui.mix(accent, 0xFFFFFF, 0.25F);
            if (phrase) {
               MutableComponent c = Ui.lit(bas, Ui.FONT);
               float sw = font.width(c), s = Math.min(1, maxW / Math.max(1, sw));
               g.pose().pushPose();
               g.pose().translate(cx - sw * s / 2, y0 + 61, 0);
               g.pose().scale(s, s, 1);
               g.drawString(font, c, 0, 0, Ui.argb(couleur, aSous), true);
               g.pose().popPose();
            } else {
               float e = 3.0F, sw = largeur(font, bas, Ui.BOLD, 0.95F, e);
               if (sw > maxW) {
                  e = 1.0F;
                  sw = largeur(font, bas, Ui.BOLD, 0.95F, e);
               }
               float s = sw > maxW ? 0.95F * maxW / sw : 0.95F;
               caps(g, font, bas, Ui.BOLD, cx - largeur(font, bas, Ui.BOLD, s, e) / 2, y0 + 61, s, e, Ui.argb(couleur, aSous), true);
            }
         }
      });
   }

   /** Les {@code n} premières lettres du titre, avec halo, ombre et parasites ; la position ne bouge pas pendant l'écriture. */
   private static void lettres(GuiGraphics g, Font font, String up, int n, float x, float y, float echelle, float espace, int couleur, int accent,
                               int alpha, float parasites) {
      g.pose().pushPose();
      g.pose().translate(x, y, 0);
      g.pose().scale(echelle, echelle, 1);
      int halo = alpha / 4, ombre = alpha * 150 / 255, fantome = alpha / 2;
      float cx = 0;
      for (int i = 0, k = 0; i < up.length() && k < n; i = up.offsetByCodePoints(i, 1), k++) {
         MutableComponent c = Ui.lit(new String(Character.toChars(up.codePointAt(i))), TITRE);
         float jx = 0, jy = 0;
         if (parasites > 0 && ((k * 7 + (int) parasites) % 5 == 0)) {
            jx = ((k * 13 + (int) (parasites * 3)) % 5) - 2;
            jy = ((k * 5 + (int) (parasites * 7)) % 3) - 1;
            if (fantome >= 4) {
               dessine(g, font, c, cx + jx - 1, jy, Ui.argb(accent, fantome));
               dessine(g, font, c, cx + jx + 1, jy, Ui.argb(0xFFFFFF, fantome));
            }
         }
         if (halo >= 4) {
            dessine(g, font, c, cx + jx - 0.6F, jy, Ui.argb(accent, halo));
            dessine(g, font, c, cx + jx + 0.6F, jy, Ui.argb(accent, halo));
            dessine(g, font, c, cx + jx, jy - 0.6F, Ui.argb(accent, halo));
            dessine(g, font, c, cx + jx, jy + 0.6F, Ui.argb(accent, halo));
         }
         if (ombre >= 4) dessine(g, font, c, cx + jx + 0.4F, jy + 0.8F, Ui.argb(0x000000, ombre));
         dessine(g, font, c, cx + jx, jy, Ui.argb(couleur, alpha));
         cx += font.width(c) + espace;
      }
      g.pose().popPose();
   }

   private static void dessine(GuiGraphics g, Font font, Component c, float x, float y, int argb) {
      g.pose().pushPose();
      g.pose().translate(x, y, 0);
      g.drawString(font, c, 0, 0, argb, false);
      g.pose().popPose();
   }

   // ------------------------------------------------------------------ textes

   /** Capitales espacées dans la police {@code f}, à l'échelle {@code s} ; x = bord gauche ; ombre douce en option. */
   public static void caps(GuiGraphics g, Font font, String texte, ResourceLocation f, float x, float y, float s, float espace, int argb,
                           boolean ombre) {
      int a = argb >>> 24;
      if (a < 4 || texte.isEmpty()) return;
      g.pose().pushPose();
      g.pose().translate(x, y, 0);
      g.pose().scale(s, s, 1);
      int ao = a * 140 / 255;
      if (ombre && ao >= 4) Ui.spaced(g, font, texte, f, 0.5F, 0.7F, ao << 24, espace);
      Ui.spaced(g, font, texte, f, 0, 0, argb, espace);
      g.pose().popPose();
   }

   /** Texte tel quel (ni capitales ni espacement) dans la police {@code f}, à l'échelle {@code s} ; ombre douce en option. */
   public static void texte(GuiGraphics g, Font font, String texte, ResourceLocation f, float x, float y, float s, int argb, boolean ombre) {
      int a = argb >>> 24;
      if (a < 4 || texte.isEmpty()) return;
      MutableComponent c = Ui.lit(texte, f);
      g.pose().pushPose();
      g.pose().translate(x, y, 0);
      g.pose().scale(s, s, 1);
      int ao = a * 140 / 255;
      if (ombre && ao >= 4) dessine(g, font, c, 0.5F, 0.7F, ao << 24);
      g.drawString(font, c, 0, 0, argb, false);
      g.pose().popPose();
   }

   /** Largeur à l'écran de {@link #texte}. */
   public static float largeurTexte(Font font, String texte, ResourceLocation f, float s) {
      return font.width(Ui.lit(texte, f)) * s;
   }

   /** Largeur à l'écran de {@link #caps}. */
   public static float largeur(Font font, String texte, ResourceLocation f, float s, float espace) {
      return Ui.spacedWidth(font, texte.toUpperCase(Locale.ROOT), f, espace) * s;
   }
}
