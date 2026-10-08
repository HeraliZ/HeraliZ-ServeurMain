package fr.heraliz.ping.client.gui.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Charte des interfaces (copie du kit de heralisz_clans) : police DM Sans (assets/heraliz_ping/font, licence OFL),
 * couleurs sobres, capitales espacees, filets, infobulles maison, panneaux « verre », pastilles, barres, tuiles.
 * Pas de cadres ni de boutons vanilla. Accent = couleur principale de la banniere du clan (neutre sans clan).
 */
public final class Ui {
    private Ui() {
    }

    /** DM Sans Medium (texte courant), Bold (titres, boutons), Medium petit corps (legendes). */
    public static final ResourceLocation FONT = new ResourceLocation("heraliz_ping", "ui"), BOLD = new ResourceLocation("heraliz_ping", "ui_bold"), SMALL = new ResourceLocation("heraliz_ping", "ui_small");

    public static final int TEXT = 0xECEEF2, MUTED = 0x8F95A0, FAINT = 0x5A5F69, DANGER = 0xFF6B6B, OK = 0x5FD08A;
    public static final int ACCENT_NEUTRE = 0x6FA8FF, OR = 0xE8C36A, AMBRE = 0xE8B34A;
    /** Fond des panneaux verre et son opacite. */
    public static final int VERRE = 0x0D0F14, VERRE_ALPHA = 200;
    /** Marges et espacements de la charte. */
    public static final int MARGE = 16, ESPACE = 8, ESPACE_L = 12;

    /** Police du mod economie (glyphe « jeton » U+E000) ; utilisee seulement si le mod est charge. */
    public static final ResourceLocation FONT_ECONOMIE = new ResourceLocation("heralisz_economie", "ui");
    public static final String GLYPHE_JETON = "";
    private static Boolean economie;

    /** Accent impose par un ecran ou un mode, -1 = aucun. */
    private static int accentForce = -1;
    /** Lecture de l'accent du clan par reflexion (pas de dependance au mod clans) ; null = pas encore cherche. */
    private static java.lang.reflect.Method accentClan;
    private static boolean accentClanAbsent;

    /**
     * Couleur d'accent courante : l'accent force s'il y en a un ({@link #forcerAccent}), sinon celle du clan du joueur
     * (mod heralisz_clans, lu par reflexion), neutre sinon.
     */
    public static int accent() {
        if (accentForce >= 0) return accentForce;
        if (!accentClanAbsent) {
            try {
                if (accentClan == null) accentClan = Class.forName("fr.heralisz.clans.client.ClientClans").getMethod("accent");
                return ((Number) accentClan.invoke(null)).intValue() & 0xFFFFFF;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                accentClanAbsent = true;
            }
        }
        return ACCENT_NEUTRE;
    }

    /** Impose l'accent (RGB) ; -1 rend l'accent du clan. A remettre a -1 en fermant l'ecran. */
    public static void forcerAccent(int rgb) {
        accentForce = rgb < 0 ? -1 : rgb & 0xFFFFFF;
    }

    /** Joue un son d'interface maison (source UI), rien si le son n'est pas enregistre. */
    public static void son(RegistryObject<SoundEvent> son, float volume) {
        if (son != null && son.isPresent()) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(son.get(), volume));
    }

    /** Vrai si le mod HeraliZ Economie est charge (son glyphe « jeton » est alors disponible). */
    public static boolean economiePresente() {
        if (economie == null) {
            try {
                economie = ModList.get() != null && ModList.get().isLoaded("heralisz_economie");
            } catch (Exception ex) {
                economie = false;
            }
        }
        return economie;
    }

    /** Mot de la monnaie : « jeton » / « jetons » (cles gui.jeton / gui.jetons). */
    public static String motJetons(long v) {
        return Component.translatable(Math.abs(v) == 1 ? "gui.jeton" : "gui.jetons").getString();
    }

    /** « 12 450 jetons » en texte brut (infobulles, capitales espacees). */
    public static String jetonsTexte(long v) {
        return nombre(v) + " " + motJetons(v);
    }

    /** « +1 200 jetons » / « -300 jetons » en texte brut. */
    public static String jetonsSigneTexte(long v) {
        return (v >= 0 ? "+" : "-") + nombre(Math.abs(v)) + " " + motJetons(v);
    }

    /**
     * Montant en jetons pour l'affichage : le nombre dans la police demandee suivi du glyphe « jeton » (police du mod
     * economie) si ce mod est present, sinon du mot « jetons ».
     */
    public static MutableComponent jetons(long v, ResourceLocation font) {
        MutableComponent c = lit(nombre(v), font);
        if (economiePresente()) c.append(lit(" " + GLYPHE_JETON, FONT_ECONOMIE));
        else c.append(lit(" " + motJetons(v), font));
        return c;
    }

    /** Montant signe en jetons (journal de la banque). */
    public static MutableComponent jetonsSigne(long v, ResourceLocation font) {
        MutableComponent c = lit((v >= 0 ? "+" : "-") + nombre(Math.abs(v)), font);
        if (economiePresente()) c.append(lit(" " + GLYPHE_JETON, FONT_ECONOMIE));
        else c.append(lit(" " + motJetons(v), font));
        return c;
    }

    /** Date courte d'un instant (ms epoch) : « 12:34 » aujourd'hui, « 28/09 » sinon. */
    public static String dateCourte(long epochMs, long maintenantMs) {
        ZoneId z = ZoneId.systemDefault();
        LocalDateTime d = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), z);
        LocalDate auj = LocalDateTime.ofInstant(Instant.ofEpochMilli(maintenantMs), z).toLocalDate();
        if (d.toLocalDate().equals(auj)) return String.format(Locale.ROOT, "%02d:%02d", d.getHour(), d.getMinute());
        return String.format(Locale.ROOT, "%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    /** Petite pastille ronde-carree (interrupteur) : pleine a l'accent si active, creuse sinon. */
    public static void pastille(GuiGraphics g, int x, int y, int taille, int rgb, boolean active, boolean hover) {
        if (active) {
            soft(g, x, y, taille, taille, argb(rgb, hover ? 255 : 215));
            softOutline(g, x + 1, y + 1, taille - 2, taille - 2, argb(mix(rgb, 0xFFFFFF, 0.35f), 200));
        } else {
            soft(g, x, y, taille, taille, argb(0xFFFFFF, hover ? 22 : 10));
            softOutline(g, x + 1, y + 1, taille - 2, taille - 2, argb(0xFFFFFF, hover ? 90 : 40));
        }
    }

    /** Point d'etat (en ligne / hors ligne) de 5 px. */
    public static void point(GuiGraphics g, int x, int y, int rgb, int alpha) {
        g.fill(x + 1, y, x + 4, y + 5, argb(rgb, alpha));
        g.fill(x, y + 1, x + 5, y + 4, argb(rgb, alpha));
    }

    public static boolean french() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("fr");
    }

    public static MutableComponent lit(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    public static MutableComponent tr(ResourceLocation font, String key, Object... args) {
        return Component.translatable(key, args).withStyle(st -> st.withFont(font));
    }

    /** Le composant dans une police (ses morceaux sans police propre en heritent). */
    public static MutableComponent in(Component c, ResourceLocation font) {
        return Component.empty().withStyle(st -> st.withFont(font)).append(c);
    }

    public static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ capitales espacees
    public static float spacedWidth(Font font, String s, ResourceLocation f, float spacing) {
        float w = 0;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(s.codePointAt(i)));
            w += font.width(lit(ch, f)) + spacing;
        }
        return Math.max(0, w - spacing);
    }

    /** Texte en capitales espacees ; x = bord gauche. */
    public static void spaced(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, int argb, float spacing) {
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(up.codePointAt(i)));
            MutableComponent c = lit(ch, f);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) + spacing;
        }
    }

    public static void spacedCentered(GuiGraphics g, Font font, String s, ResourceLocation f, float cx, float y, int argb, float spacing) {
        float w = spacedWidth(font, s.toUpperCase(Locale.ROOT), f, spacing);
        spaced(g, font, s, f, cx - w / 2f, y, argb, spacing);
    }

    /** Texte en capitales espacees aligne a droite (x = bord droit). */
    public static void spacedRight(GuiGraphics g, Font font, String s, ResourceLocation f, float xRight, float y, int argb, float spacing) {
        float w = spacedWidth(font, s.toUpperCase(Locale.ROOT), f, spacing);
        spaced(g, font, s, f, xRight - w, y, argb, spacing);
    }

    /** Texte centre sur cx (position arrondie au pixel : les glyphes bitmap comme le jeton restent nets). */
    public static void centered(GuiGraphics g, Font font, Component c, float cx, float y, int argb) {
        g.pose().pushPose();
        g.pose().translate(Math.round(cx - font.width(c) / 2f), Math.round(y), 0);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    public static void right(GuiGraphics g, Font font, Component c, float xRight, float y, int argb) {
        g.pose().pushPose();
        g.pose().translate(Math.round(xRight - font.width(c)), Math.round(y), 0);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Texte coupe avec « … » s'il depasse la largeur. */
    public static String ellipsis(Font font, String s, ResourceLocation f, int maxWidth) {
        if (font.width(lit(s, f)) <= maxWidth) return s;
        String dots = "…";
        int dw = font.width(lit(dots, f));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(s.codePointAt(i)));
            if (font.width(lit(sb + ch, f)) + dw > maxWidth) break;
            sb.append(ch);
        }
        return sb.toString().trim() + dots;
    }

    // ------------------------------------------------------------------ formes
    /** Rectangle aux coins adoucis (un pixel retire a chaque coin). */
    public static void soft(GuiGraphics g, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        g.fill(x + 1, y, x + w - 1, y + 1, argb);
        g.fill(x, y + 1, x + w, y + h - 1, argb);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, argb);
    }

    /** Contour d'un pixel aux coins adoucis, juste autour du rectangle (x, y, w, h). */
    public static void softOutline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x + 1, y - 1, x + w - 1, y, argb);
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, argb);
        g.fill(x - 1, y + 1, x, y + h - 1, argb);
        g.fill(x + w, y + 1, x + w + 1, y + h - 1, argb);
    }

    /**
     * Panneau « verre » : fond 0x0D0F14 a ~78 %, contour 1 px blanc a 10 %, lisere haut blanc a 16 %, coins adoucis.
     * (x, y, w, h) = rectangle exterieur.
     */
    public static void glass(GuiGraphics g, int x, int y, int w, int h) {
        glass(g, x, y, w, h, VERRE_ALPHA);
    }

    public static void glass(GuiGraphics g, int x, int y, int w, int h, int alpha) {
        soft(g, x, y, w, h, argb(VERRE, alpha));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(0xFFFFFF, 26));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, argb(0xFFFFFF, 41));
    }

    /** Fond plein ecran : degrade diagonal 0x8C000000 -> 0xB4000000 (le monde reste lisible). */
    public static void fond(GuiGraphics g, int w, int h) {
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            int x0 = w * i / bands, x1 = w * (i + 1) / bands;
            float t = (i + 0.5f) / bands;
            int top = argb(0, (int) (0x8C + (0xB4 - 0x8C) * t * 0.5f)), bottom = argb(0, (int) (0x8C + (0xB4 - 0x8C) * (0.5f + t * 0.5f)));
            g.fillGradient(x0, 0, x1, h, top, bottom);
        }
    }

    /**
     * Pastille (etiquette) : texte en petites capitales espacees sur un fond teinte. Rend la largeur dessinee.
     */
    public static int chip(GuiGraphics g, Font font, String texte, int x, int y, int rgb) {
        int w = (int) spacedWidth(font, texte.toUpperCase(Locale.ROOT), SMALL, 0.6f) + 10, h = 11;
        soft(g, x, y, w, h, argb(rgb, 34));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(rgb, 90));
        spaced(g, font, texte, SMALL, x + 5, y + 2.5f, argb(mix(rgb, 0xFFFFFF, 0.3f), 255), 0.6f);
        return w;
    }

    /** Barre de progression fine : piste blanche a 10 %, remplissage de la couleur. ratio 0..1. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float ratio, int rgb) {
        g.fill(x, y, x + w, y + h, argb(0xFFFFFF, 22));
        int fw = Math.round(w * Math.max(0, Math.min(1, ratio)));
        if (fw > 0) g.fill(x, y, x + fw, y + h, argb(rgb, 235));
    }

    /** Tuile de statistique : gros chiffre en gras + legende en petites capitales espacees atténuees, sur verre leger. */
    public static void tile(GuiGraphics g, Font font, int x, int y, int w, int h, String valeur, String legende) {
        tile(g, font, x, y, w, h, lit(valeur, BOLD), legende);
    }

    /** Tuile dont la valeur est un composant (montant avec glyphe, par exemple). */
    public static void tile(GuiGraphics g, Font font, int x, int y, int w, int h, Component valeur, String legende) {
        soft(g, x, y, w, h, argb(0xFFFFFF, 8));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(0xFFFFFF, 14));
        g.pose().pushPose();
        g.pose().translate(x + 10, y + 8, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(font, valeur, 0, 0, argb(TEXT, 255), false);
        g.pose().popPose();
        spaced(g, font, legende, SMALL, x + 10, y + h - 13, argb(MUTED, 255), 0.8f);
    }

    /** Texte en gras agrandi (gros chiffres). */
    public static void gros(GuiGraphics g, Font font, Component c, float x, float y, float echelle, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(echelle, echelle, 1);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Fond leger pour les petits ecrans (panneau du drapeau) : voile diagonal plus doux, le monde reste net. */
    public static void fondLeger(GuiGraphics g, int w, int h) {
        int bands = 16;
        for (int i = 0; i < bands; i++) {
            int x0 = w * i / bands, x1 = w * (i + 1) / bands;
            float t = (i + 0.5f) / bands;
            int top = argb(0, (int) (0x3C + (0x6E - 0x3C) * t * 0.5f)), bottom = argb(0, (int) (0x3C + (0x6E - 0x3C) * (0.5f + t * 0.5f)));
            g.fillGradient(x0, 0, x1, h, top, bottom);
        }
    }

    /** Titre de section en capitales espacees avec un filet qui s'efface a droite. */
    public static void section(GuiGraphics g, Font font, String titre, int x, int y, int w) {
        spaced(g, font, titre, SMALL, x, y, argb(MUTED, 255), 1.0f);
        int tw = (int) spacedWidth(font, titre.toUpperCase(Locale.ROOT), SMALL, 1.0f);
        hairline(g, x + tw + 8, x + w, y + 4, 0xFFFFFF, 40, false, true);
    }

    // ------------------------------------------------------------------ filets
    /** Filet horizontal d'un pixel qui s'efface vers les deux bouts (ou vers un seul). */
    public static void hairline(GuiGraphics g, int x0, int x1, int y, int rgb, int alpha, boolean fadeLeft, boolean fadeRight) {
        int w = x1 - x0;
        if (w <= 0) return;
        int steps = Math.max(1, w / 2);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            float a = 1;
            if (fadeLeft && fadeRight) a = 1 - Math.abs(t * 2 - 1);
            else if (fadeLeft) a = t;
            else if (fadeRight) a = 1 - t;
            a = a * a * (3 - 2 * a);
            int xa = x0 + i * w / steps, xb = x0 + (i + 1) * w / steps;
            g.fill(xa, y, xb, y + 1, argb(rgb, (int) (alpha * a)));
        }
    }

    /** Bande horizontale qui s'efface vers ses deux bouts sur fade pixels. */
    public static void band(GuiGraphics g, int x0, int y0, int x1, int y1, int rgb, int alpha, int fade) {
        g.fill(x0 + fade, y0, x1 - fade, y1, argb(rgb, alpha));
        int steps = Math.max(1, fade / 2);
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;
            int a = (int) (alpha * t * t * (3 - 2 * t));
            int w0 = i * fade / steps, w1 = (i + 1) * fade / steps;
            g.fill(x0 + w0, y0, x0 + w1, y1, argb(rgb, a));
            g.fill(x1 - w1, y0, x1 - w0, y1, argb(rgb, a));
        }
    }

    // ------------------------------------------------------------------ infobulle
    /** Infobulle sobre : fond sombre, lisere a la couleur d'accent, premiere ligne en titre. */
    public static void tooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY, int accent) {
        if (lines.isEmpty()) return;
        int w = 0;
        for (Component l : lines) w = Math.max(w, font.width(l));
        int h = lines.size() == 1 ? 9 : lines.size() * 10 + 1;
        int x = mouseX + 12, y = mouseY - 10;
        if (x + w + 8 > g.guiWidth()) x = mouseX - w - 14;
        y = Math.max(6, Math.min(y, g.guiHeight() - h - 6));
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(x - 6, y - 5, x + w + 6, y + h + 3, 0xF20B0C0F);
        g.fill(x - 6, y - 5, x + w + 6, y - 4, argb(accent, 200));
        g.fill(x - 6, y - 4, x - 5, y + h + 3, argb(0xFFFFFF, 18));
        g.fill(x + w + 5, y - 4, x + w + 6, y + h + 3, argb(0xFFFFFF, 18));
        g.fill(x - 6, y + h + 2, x + w + 6, y + h + 3, argb(0xFFFFFF, 18));
        int ly = y;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, ly, 0xFFFFFFFF, false);
            ly += i == 0 ? 12 : 10;
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ formats
    /** Nombre avec separateur de milliers (espace fine en francais, virgule en anglais) : 12 450. */
    public static String nombre(long v) {
        String s = String.format(Locale.ROOT, "%,d", v);
        return french() ? s.replace(',', ' ') : s;
    }

    /** Montant avec symbole : « 12 450 $ ». */
    public static String montant(long v, String symbole) {
        return nombre(v) + " " + symbole;
    }

    /** Montant signe : +1 200 $ / -300 $. */
    public static String montantSigne(long v, String symbole) {
        return (v >= 0 ? "+" : "-") + nombre(Math.abs(v)) + " " + symbole;
    }

    /** Ratio a 2 decimales (virgule en francais) : 2,31. */
    public static String ratio(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return french() ? s.replace('.', ',') : s;
    }

    /** Pourcentage lisible : 50, 12,5, 3,33, 0,25, 0,005 (virgule en francais). */
    public static String pct(double v) {
        String s;
        if (v <= 0) s = "0";
        else if (v >= 10) s = String.format(Locale.ROOT, "%.1f", v);
        else if (v >= 0.01) s = String.format(Locale.ROOT, "%.2f", v);
        else s = String.format(Locale.ROOT, "%.3f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        if (french()) s = s.replace('.', ',');
        return s + " %";
    }

    /** Duree courte : « 2 j 3 h », « 7 h 12 », « 3 min », « 12 s » (cles gui.duree.*). */
    public static String duree(long ms) {
        long s = Math.max(0, ms / 1000), min = s / 60, h = min / 60, j = h / 24;
        if (j >= 1) return j + Component.translatable("gui.duree.j").getString() + " " + (h % 24) + Component.translatable("gui.duree.h").getString();
        if (h >= 1) return h + Component.translatable("gui.duree.h").getString() + " " + String.format(Locale.ROOT, "%02d", min % 60);
        if (min >= 1) return min + Component.translatable("gui.duree.min").getString();
        return s + Component.translatable("gui.duree.s").getString();
    }

    /** « il y a 12 min » / « il y a 3 h » (cle gui.il_y_a). */
    public static String ilYA(long ms) {
        return Component.translatable("gui.il_y_a", duree(ms)).getString();
    }

    /** Temps de jeu en ticks -> « 34 h » / « 2 j 3 h ». */
    public static String tempsJeu(long ticks) {
        return duree(ticks * 50L);
    }
}
