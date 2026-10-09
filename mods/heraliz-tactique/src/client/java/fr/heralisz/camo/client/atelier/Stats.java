package fr.heralisz.camo.client.atelier;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import fr.heralisz.camo.client.kit.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Caractéristiques de l'arme tenue, avec ses accessoires, et ce que changerait l'accessoire choisi. Les chiffres sont ceux
 * des diagrammes de TaCZ (mêmes calculs) : dégâts, cadence, portée, précision en visée, recul vertical, temps de visée.
 */
final class Stats {
   /** Fin des clés de titre des diagrammes de TaCZ (gui.tacz.gun_refit.property_diagrams.*), dans l'ordre affiché. */
   private static final String[] CLES = {"damage", "rpm", "effective_range", "aim_inaccuracy", "pitch", "ads"};
   private static final Pattern NOMBRE = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

   /** Une caractéristique : barre (0..1) avant et après, valeur affichée, écart avec l'accessoire choisi. */
   record Ligne(String cle, float avant, float apres, String valeur, @Nullable String ecart, boolean mieux) {}

   private Stats() {
   }

   /** Les caractéristiques de {@code gun}, et l'écart si {@code avec} (un accessoire) y était posé. */
   static List<Ligne> calculer(ItemStack gun, @Nullable ItemStack avec) {
      IGun iGun = IGun.getIGunOrNull(gun);
      if (iGun == null) return List.of();
      GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).map(CommonGunIndex::getGunData).orElse(null);
      if (data == null) return List.of();
      Map<String, IAttachmentModifier.DiagramsData> avant = diagrammes(gun, data);
      Map<String, IAttachmentModifier.DiagramsData> apres = null;
      if (avec != null && !avec.isEmpty()) {
         ItemStack copie = gun.copy();
         iGun.installAttachment(copie, avec);
         apres = diagrammes(copie, data);
      }
      List<Ligne> out = new ArrayList<>();
      for (String cle : CLES) {
         IAttachmentModifier.DiagramsData d = avant.get(cle);
         if (d == null) continue;
         double valeur = valeur(cle, d);
         float barre = barre(d);
         float barreApres = barre;
         String ecart = null;
         boolean mieux = true;
         IAttachmentModifier.DiagramsData d2 = apres == null ? null : apres.get(cle);
         if (d2 != null) {
            barreApres = barre(d2);
            double delta = d2.modifier().doubleValue() - d.modifier().doubleValue();
            if (Math.abs(delta) > 1e-6) {
               mieux = delta > 0 == d.positivelyBetter();
               double pct = Math.abs(valeur) > 1e-6 ? delta / Math.abs(valeur) * 100.0 : 0.0;
               if (Math.abs(pct) >= 0.5) {
                  ecart = (pct > 0 ? "+" : "-") + Math.round(Math.abs(pct)) + " %";
               } else if (Math.abs(valeur) <= 1e-6) {
                  ecart = (delta > 0 ? "+" : "-") + nombre(Math.abs(delta), 2);
               }
            }
         }
         out.add(new Ligne(cle, barre, barreApres, texte(cle, valeur), ecart, mieux));
      }
      return out;
   }

   /** Diagrammes de TaCZ pour cette arme (accessoires compris), rangés par fin de clé de titre. */
   private static Map<String, IAttachmentModifier.DiagramsData> diagrammes(ItemStack gun, GunData data) {
      AttachmentCacheProperty cache = new AttachmentCacheProperty();
      cache.eval(gun, data);
      Map<String, IAttachmentModifier.DiagramsData> out = new HashMap<>();
      for (IAttachmentModifier<?, ?> m : AttachmentPropertyManager.getModifiers().values()) {
         try {
            for (IAttachmentModifier.DiagramsData d : m.getPropertyDiagramsData(gun, data, cache)) {
               String k = d.titleKey();
               out.put(k.substring(k.lastIndexOf('.') + 1), d);
            }
         } catch (RuntimeException ignored) {
            // un diagramme qui ne se calcule pas pour cette arme (données incomplètes du gunpack) : il n'est pas montré
         }
      }
      return out;
   }

   /** Longueur de la barre (0..1) : valeur de base + effet des accessoires, comme TaCZ. */
   private static float barre(IAttachmentModifier.DiagramsData d) {
      double v = Math.max(0.0, Math.min(1.0, d.defaultPercent())) + d.modifierPercent();
      return (float) Math.max(0.0, Math.min(1.0, v));
   }

   /** Valeur actuelle (accessoires compris) lue dans le texte de TaCZ ; la portée y est écrite sans ses accessoires. */
   private static double valeur(String cle, IAttachmentModifier.DiagramsData d) {
      Matcher m = NOMBRE.matcher(d.defaultString().replaceAll("§.", ""));
      double v = m.find() ? Double.parseDouble(m.group().replace(',', '.')) : 0.0;
      return "effective_range".equals(cle) ? v + d.modifier().doubleValue() : v;
   }

   private static String texte(String cle, double v) {
      return switch (cle) {
         case "rpm" -> Math.round(v) + " " + Component.translatable("gui.heralisz_camo.atelier.unite.min").getString();
         case "effective_range" -> nombre(v, v >= 10 ? 0 : 1) + " m";
         case "aim_inaccuracy" -> nombre(v, 1) + " %";
         case "ads" -> nombre(v, 2) + " s";
         case "damage" -> nombre(v, 1);
         default -> nombre(v, 2);
      };
   }

   /** Nombre à {@code decimales} au plus, sans zéros inutiles, virgule en français. */
   private static String nombre(double v, int decimales) {
      String s = String.format(Locale.ROOT, "%." + decimales + "f", v);
      if (s.contains(".")) {
         s = s.replaceAll("0+$", "");
         if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
      }
      return Ui.french() ? s.replace('.', ',') : s;
   }

   // ------------------------------------------------------------------ dessin
   /** Panneau « Caractéristiques » : 3 colonnes, 2 rangées ; {@code avec} = nom de l'accessoire choisi (ou null). */
   static void dessiner(GuiGraphics g, Font font, List<Ligne> lignes, @Nullable String avec, int x, int y, int w, int h, int accent) {
      Ui.glass(g, x, y, w, h);
      String titre = Component.translatable("gui.heralisz_camo.atelier.stats").getString();
      Ui.spaced(g, font, titre, Ui.SMALL, x + 12, y + 9, Ui.argb(Ui.MUTED, 255), 1.0f);
      int tw = (int) Ui.spacedWidth(font, titre.toUpperCase(Locale.ROOT), Ui.SMALL, 1.0f);
      int filetFin = x + w - 12;
      if (avec != null) {
         String t = Component.translatable("gui.heralisz_camo.atelier.avec", avec).getString();
         t = Dessins.couperEspace(font, t, Ui.SMALL, 0.8f, w / 2f - 12);
         float aw = Ui.spacedWidth(font, t.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f);
         Ui.spacedRight(g, font, t, Ui.SMALL, x + w - 12, y + 9, Ui.argb(accent, 255), 0.8f);
         filetFin = (int) (x + w - 12 - aw - 8);
      }
      Ui.hairline(g, x + 12 + tw + 8, filetFin, y + 13, 0xFFFFFF, 40, false, true);
      if (lignes.isEmpty()) {
         Ui.centered(g, font, Ui.tr(Ui.SMALL, "gui.heralisz_camo.atelier.stats_aucune"), x + w / 2f, y + h / 2f, Ui.argb(Ui.FAINT, 255));
         return;
      }
      int colW = (w - 24 - 24) / 3;
      for (int i = 0; i < Math.min(6, lignes.size()); i++) {
         Ligne l = lignes.get(i);
         int cx = x + 12 + (i % 3) * (colW + 12);
         int cy = y + 22 + (i / 3) * 17;
         g.drawString(font, Ui.tr(Ui.SMALL, "gui.heralisz_camo.atelier.stat." + l.cle()), cx, cy + 1, Ui.argb(Ui.MUTED, 255), false);
         float droite = cx + colW;
         if (l.ecart() != null) {
            Component e = Ui.lit(l.ecart(), Ui.SMALL);
            Ui.right(g, font, e, droite, cy + 1, Ui.argb(l.mieux() ? Ui.OK : Ui.DANGER, 255));
            droite -= font.width(e) + 4;
         }
         Ui.right(g, font, Ui.lit(l.valeur(), Ui.FONT), droite, cy, Ui.argb(Ui.TEXT, 255));
         // barre : la valeur actuelle en clair, l'écart avec l'accessoire choisi en vert (mieux) ou en rouge (moins bien)
         int by = cy + 10;
         g.fill(cx, by, cx + colW, by + 2, Ui.argb(0xFFFFFF, 22));
         int a = Math.round(colW * Math.min(l.avant(), l.apres()));
         int b = Math.round(colW * Math.max(l.avant(), l.apres()));
         if (a > 0) g.fill(cx, by, cx + a, by + 2, Ui.argb(0xFFFFFF, 190));
         if (b > a) g.fill(cx + a, by, cx + b, by + 2, Ui.argb(l.mieux() ? Ui.OK : Ui.DANGER, 235));
      }
   }
}
