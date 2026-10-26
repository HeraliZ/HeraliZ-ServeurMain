package fr.heralisz.camo.client.atelier;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import fr.heraliz.tactique.armes.DegatsJoueurs;
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
 * des diagrammes de TaCZ (mêmes calculs), sauf les dégâts : ceux contre un joueur (armes.DegatsJoueurs, réglage du
 * serveur). 14/10 : onze caractéristiques (dégâts, cadence, portée, chargeur, dispersion au jugé et en visée, recul
 * vertical et horizontal, temps de visée, poids, vitesse des balles) sur 4 colonnes, et l'effet chiffré de chaque
 * accessoire sur l'arme ({@link #effets}).
 */
final class Stats {
   /** Fin des clés de titre des diagrammes de TaCZ (gui.tacz.gun_refit.property_diagrams.*), dans l'ordre affiché. */
   private static final String[] CLES = {"damage", "rpm", "effective_range", "ammo_capacity", "hipfire_inaccuracy", "aim_inaccuracy",
      "pitch", "yaw", "ads", "weight", "ammo_speed"};
   /** Chargeur : pas un diagramme de TaCZ, lu à part (AttachmentDataUtils). */
   private static final String CAPACITE = "ammo_capacity";
   static final int COLONNES = 4, RANGEES = 3;
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
      ItemStack copie = null;
      if (avec != null && !avec.isEmpty()) {
         copie = gun.copy();
         iGun.installAttachment(copie, avec);
         apres = diagrammes(copie, data);
      }
      float origine = data.getBulletData() == null ? 0F : data.getBulletData().getDamageAmount();
      // dégâts contre un joueur : même proportion que le serveur (arme sans réglage, ou à 0 dégât chez TaCZ : chiffres de TaCZ)
      double facteurJoueur = DegatsJoueurs.de(iGun.getGunId(gun)) == null || origine <= 0.01F ? 1.0
         : DegatsJoueurs.ajuster(iGun.getGunId(gun), 1.0, origine);
      List<Ligne> out = new ArrayList<>();
      for (String cle : CLES) {
         if (CAPACITE.equals(cle)) {
            out.add(capacite(gun, copie, data));
            continue;
         }
         IAttachmentModifier.DiagramsData d = avant.get(cle);
         if (d == null) continue;
         double valeur = valeur(cle, d) * ("damage".equals(cle) ? facteurJoueur : 1.0);
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
               double base = "damage".equals(cle) ? valeur / facteurJoueur : valeur;
               double pct = Math.abs(base) > 1e-6 ? delta / Math.abs(base) * 100.0 : 0.0;
               if ("ads".equals(cle)) {
                  ecart = (delta > 0 ? "+" : "-") + nombre(Math.abs(delta), 2) + " s";
               } else if (Math.abs(pct) >= 0.5) {
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

   /** Chargeur : nombre de munitions avec les accessoires, et avec l'accessoire choisi. */
   private static Ligne capacite(ItemStack gun, @Nullable ItemStack copie, GunData data) {
      int n = AttachmentDataUtils.getAmmoCountWithAttachment(gun, data);
      int m = copie == null ? n : AttachmentDataUtils.getAmmoCountWithAttachment(copie, data);
      String ecart = m == n ? null : (m > n ? "+" : "-") + Math.abs(m - n);
      return new Ligne(CAPACITE, Math.min(1f, n / 100f), Math.min(1f, m / 100f), String.valueOf(n), ecart, m >= n);
   }

   /** Effet chiffré d'un accessoire sur l'arme (une ligne par caractéristique changée). */
   record Effet(String cle, String texte, boolean mieux, double force) {}

   /**
    * Ce que l'accessoire {@code s} change sur {@code gun} : l'arme avec l'emplacement vide, puis avec lui. Les écarts en %
    * de la valeur de l'arme (« -20 % » de recul), ou en unités pour les petites valeurs (« -0,03 s » de visée) ; les plus
    * forts d'abord. Liste vide si l'accessoire ne va pas sur l'arme.
    */
   static List<Effet> effets(ItemStack gun, ItemStack s) {
      IGun iGun = IGun.getIGunOrNull(gun);
      IAttachment ia = IAttachment.getIAttachmentOrNull(s);
      if (iGun == null || ia == null) return List.of();
      GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).map(CommonGunIndex::getGunData).orElse(null);
      if (data == null) return List.of();
      ItemStack sans = gun.copy();
      iGun.unloadAttachment(sans, ia.getType(s));
      List<Ligne> l = calculer(sans, s);
      List<Effet> out = new ArrayList<>();
      for (Ligne x : l) {
         if (x.ecart() == null) continue;
         double force = Math.abs(x.apres() - x.avant()) + 0.001;
         out.add(new Effet(x.cle(), x.ecart(), x.mieux(), force));
      }
      out.sort((a, b) -> Double.compare(b.force(), a.force()));
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
         case "hipfire_inaccuracy" -> nombre(v, 1);
         case "weight" -> nombre(v, 1) + " kg";
         case "ammo_speed" -> Math.round(v) + " m/s";
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
   /** Panneau « Caractéristiques » : 4 colonnes, 3 rangées ; {@code avec} = nom de l'accessoire choisi (ou null). */
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
      int colW = (w - 24 - 12 * (COLONNES - 1)) / COLONNES;
      for (int i = 0; i < Math.min(COLONNES * RANGEES, lignes.size()); i++) {
         Ligne l = lignes.get(i);
         int cx = x + 12 + (i % COLONNES) * (colW + 12);
         int cy = y + 22 + (i / COLONNES) * 17;
         float droite = cx + colW;
         if (l.ecart() != null) {
            Component e = Ui.lit(l.ecart(), Ui.SMALL);
            Ui.right(g, font, e, droite, cy + 1, Ui.argb(l.mieux() ? Ui.OK : Ui.DANGER, 255));
            droite -= font.width(e) + 4;
         }
         Component v = Ui.lit(l.valeur(), Ui.FONT);
         Ui.right(g, font, v, droite, cy, Ui.argb(Ui.TEXT, 255));
         // nom coupé avant la valeur et l'écart (russe : « Скорострельность » + « 600 / мин »)
         String nom = Component.translatable("gui.heralisz_camo.atelier.stat." + l.cle()).getString();
         nom = Ui.ellipsis(font, nom, Ui.SMALL, (int) (droite - font.width(v) - 6 - cx));
         g.drawString(font, Ui.lit(nom, Ui.SMALL), cx, cy + 1, Ui.argb(Ui.MUTED, 255), false);
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
