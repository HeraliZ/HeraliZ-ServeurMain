package fr.heraliz.interfaces.kit;

import fr.heraliz.interfaces.HeralizInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Charte des interfaces HeraliZ, copiee du kit des clans ({@code fr.heralisz.clans.client.gui.kit.Ui}, par la copie
 * autonome de heralisz_bases) : police DM Sans du mod (assets/heraliz_interface/font, licence OFL), couleurs sobres, capitales espacees,
 * filets, infobulles maison, panneaux « verre », pastilles, barres. Aucun cadre ni bouton vanilla.
 * <p>Difference avec l'original : les capitales espacees passent par un cache de glyphes (composant + largeur par
 * caractere, cle = la chaine d'origine) : un texte deja vu ne cree plus aucun objet a l'image. Les ecrans du mod
 * preparent leurs chaines aux changements d'etat, jamais a l'image.
 */
public final class Ui {
    private Ui() {
    }

    /** DM Sans Medium (texte courant), Bold (titres, boutons), Medium petit corps (legendes). */
    public static final ResourceLocation FONT = HeralizInterface.id("ui"), BOLD = HeralizInterface.id("ui_bold"), SMALL = HeralizInterface.id("ui_small");

    public static final int TEXT = 0xECEEF2, MUTED = 0x8F95A0, FAINT = 0x5A5F69, DANGER = 0xFF6B6B, OK = 0x5FD08A;
    public static final int ACCENT_NEUTRE = 0x6FA8FF, OR = 0xE8C36A, AMBRE = 0xE8B34A;
    /** Fond des panneaux verre et son opacite. */
    public static final int VERRE = 0x0D0F14, VERRE_ALPHA = 200;
    /** Marges et espacements de la charte. */
    public static final int MARGE = 16, ESPACE = 8, ESPACE_L = 12;

    /** Police du mod economie (glyphe « héral » U+E000) ; utilisee seulement si le mod est charge. */
    public static final ResourceLocation FONT_ECONOMIE = new ResourceLocation("heralisz_economie", "ui");
    public static final String GLYPHE_HERAL = "";
    private static Boolean economie;

    /** Rouge du « Z » du logo HeraliZ : accent des interfaces du serveur (survol discret, actions dangereuses). */
    public static final int ROUGE = 0xE3201B;
    /** Rouge des actions qui font quitter (« Se deconnecter ») : le rouge du logo, un peu eclairci pour rester lisible. */
    public static final int DANGER_ROUGE = 0xEE3B33;

    /** Couleur d'accent des interfaces : le rouge du logo (reglable dans heraliz_interface-client.toml). */
    public static int accent() {
        return fr.heraliz.interfaces.InterfaceConfig.accent();
    }

    /** Vrai si le mod HeraliZ Economie est charge (son glyphe « héral » est alors disponible). */
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

    /** Mot de la monnaie : « héral » (0 et 1, grammaire francaise) / « hérals » (2 et plus). */
    public static String motHerals(long v) {
        return Component.translatable(Math.abs(v) < 2 ? "gui.heraliz_interface.heral" : "gui.heraliz_interface.herals").getString();
    }

    /** « 12 450 hérals » en texte brut. */
    public static String heralsTexte(long v) {
        return nombre(v) + " " + motHerals(v);
    }

    /** Montant pour l'affichage : nombre + glyphe « héral » (mod economie) ou le mot « hérals ». A construire hors image. */
    public static MutableComponent herals(long v, ResourceLocation font) {
        MutableComponent c = lit(nombre(v), font);
        if (economiePresente()) c.append(lit(" " + GLYPHE_HERAL, FONT_ECONOMIE));
        else c.append(lit(" " + motHerals(v), font));
        return c;
    }

    /**
     * Montant court pour une petite tuile : {@link #nombreCourt} + glyphe « héral » si le mod economie est la (sinon le
     * nombre seul : la legende de la tuile dit deja « hérals »).
     */
    public static MutableComponent heralsCourt(long v, ResourceLocation font) {
        MutableComponent c = lit(nombreCourt(v), font);
        if (economiePresente()) c.append(lit(" " + GLYPHE_HERAL, FONT_ECONOMIE));
        return c;
    }

    /** Heure courte d'un instant (ms epoch) : « 12:34 » aujourd'hui, « 28/09 » sinon. */
    public static String dateCourte(long epochMs, long maintenantMs) {
        ZoneId z = ZoneId.systemDefault();
        LocalDateTime d = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), z);
        LocalDate auj = LocalDateTime.ofInstant(Instant.ofEpochMilli(maintenantMs), z).toLocalDate();
        if (d.toLocalDate().equals(auj)) return String.format(Locale.ROOT, "%02d:%02d", d.getHour(), d.getMinute());
        return String.format(Locale.ROOT, "%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    /** Point d'etat de 5 px. */
    public static void point(GuiGraphics g, int x, int y, int rgb, int alpha) {
        g.fill(x + 1, y, x + 4, y + 5, argb(rgb, alpha));
        g.fill(x, y + 1, x + 5, y + 4, argb(rgb, alpha));
    }

    public static boolean french() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code == null || code.startsWith("fr");
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

    /** Texte traduit (chaine). */
    public static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
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

    // ------------------------------------------------------------------ capitales espacees (cache de glyphes)
    /** Glyphes d'un texte en capitales : un composant et une largeur par caractere. */
    private record Glyphes(Component[] c, float[] w, float largeur) {
    }

    private static final Map<ResourceLocation, Map<String, Glyphes>> GLYPHES = new HashMap<>();

    private static Glyphes glyphes(Font font, String s, ResourceLocation f) {
        Map<String, Glyphes> m = GLYPHES.computeIfAbsent(f, k -> new HashMap<>());
        Glyphes gl = m.get(s);
        if (gl != null) return gl;
        if (m.size() > 1024) m.clear();
        String up = s.toUpperCase(Locale.ROOT);
        int n = up.codePointCount(0, up.length());
        Component[] c = new Component[n];
        float[] w = new float[n];
        float total = 0;
        int k = 0;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            c[k] = lit(new String(Character.toChars(up.codePointAt(i))), f);
            w[k] = font.width(c[k]);
            total += w[k];
            k++;
        }
        gl = new Glyphes(c, w, total);
        m.put(s, gl);
        return gl;
    }

    /** Oublie les glyphes et les chiffres des tuiles (rechargement des ressources). */
    public static void viderCache() {
        GLYPHES.clear();
        TUILES.clear();
    }

    /** Largeur d'un texte en capitales espacees. */
    public static float spacedWidth(Font font, String s, ResourceLocation f, float spacing) {
        Glyphes g = glyphes(font, s, f);
        return Math.max(0, g.largeur + spacing * (g.c.length - 1));
    }

    /** Texte en capitales espacees ; x = bord gauche. */
    public static void spaced(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, int argb, float spacing) {
        Glyphes gl = glyphes(font, s, f);
        float cx = x;
        for (int i = 0; i < gl.c.length; i++) {
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.drawString(font, gl.c[i], 0, 0, argb, false);
            g.pose().popPose();
            cx += gl.w[i] + spacing;
        }
    }

    public static void spacedCentered(GuiGraphics g, Font font, String s, ResourceLocation f, float cx, float y, int argb, float spacing) {
        spaced(g, font, s, f, cx - spacedWidth(font, s, f, spacing) / 2f, y, argb, spacing);
    }

    /** Texte en capitales espacees aligne a droite (x = bord droit). */
    public static void spacedRight(GuiGraphics g, Font font, String s, ResourceLocation f, float xRight, float y, int argb, float spacing) {
        spaced(g, font, s, f, xRight - spacedWidth(font, s, f, spacing), y, argb, spacing);
    }

    /** Texte a une position arrondie au pixel (les glyphes bitmap comme le héral restent nets). */
    public static void at(GuiGraphics g, Font font, Component c, float x, float y, int argb) {
        g.pose().pushPose();
        g.pose().translate(Math.round(x), Math.round(y), 0);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Texte centre sur cx (position arrondie au pixel). */
    public static void centered(GuiGraphics g, Font font, Component c, float cx, float y, int argb) {
        at(g, font, c, cx - font.width(c) / 2f, y, argb);
    }

    public static void right(GuiGraphics g, Font font, Component c, float xRight, float y, int argb) {
        at(g, font, c, xRight - font.width(c), y, argb);
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

    /** Coupe un texte en lignes qui tiennent dans la largeur (au dernier espace). */
    public static List<String> lignes(Font font, String texte, ResourceLocation f, int largeur) {
        List<String> l = new java.util.ArrayList<>();
        String reste = texte.trim();
        while (!reste.isEmpty()) {
            if (font.width(lit(reste, f)) <= largeur) {
                l.add(reste);
                break;
            }
            int coupe = -1;
            for (int i = reste.indexOf(' '); i > 0; i = reste.indexOf(' ', i + 1)) {
                if (font.width(lit(reste.substring(0, i), f)) > largeur) break;
                coupe = i;
            }
            if (coupe < 0) {
                l.add(ellipsis(font, reste, f, largeur));
                break;
            }
            l.add(reste.substring(0, coupe));
            reste = reste.substring(coupe + 1).trim();
        }
        return l;
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

    /** Contour d'un pixel a l'interieur du rectangle (x, y, w, h). */
    public static void cadre(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Contour en tirets (salles verrouillees du plan) : tirets de {@code tiret} px, espaces de {@code vide} px. */
    public static void tirets(GuiGraphics g, int x, int y, int w, int h, int tiret, int vide, int argb) {
        int pas = tiret + vide;
        for (int i = 0; i < w; i += pas) {
            int e = Math.min(x + w, x + i + tiret);
            g.fill(x + i, y, e, y + 1, argb);
            g.fill(x + i, y + h - 1, e, y + h, argb);
        }
        for (int i = 0; i < h; i += pas) {
            int e = Math.min(y + h, y + i + tiret);
            g.fill(x, y + i, x + 1, e, argb);
            g.fill(x + w - 1, y + i, x + w, e, argb);
        }
    }

    /** Panneau « verre » : fond sombre a ~78 %, contour 1 px blanc a 10 %, lisere haut blanc a 16 %, coins adoucis. */
    public static void glass(GuiGraphics g, int x, int y, int w, int h) {
        glass(g, x, y, w, h, VERRE_ALPHA);
    }

    public static void glass(GuiGraphics g, int x, int y, int w, int h, int alpha) {
        soft(g, x, y, w, h, argb(VERRE, alpha));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(0xFFFFFF, 26 * alpha / VERRE_ALPHA));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, argb(0xFFFFFF, 41 * alpha / VERRE_ALPHA));
    }

    /** Fond plein ecran : degrade diagonal 0x8C000000 -> 0xB4000000 (le monde reste lisible), jamais uni. */
    public static void fond(GuiGraphics g, int w, int h) {
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            int x0 = w * i / bands, x1 = w * (i + 1) / bands;
            float t = (i + 0.5f) / bands;
            int top = argb(0, (int) (0x8C + (0xB4 - 0x8C) * t * 0.5f)), bottom = argb(0, (int) (0x8C + (0xB4 - 0x8C) * (0.5f + t * 0.5f)));
            g.fillGradient(x0, 0, x1, h, top, bottom);
        }
    }

    /** Pastille (etiquette) : texte en petites capitales espacees sur un fond teinte. Rend la largeur dessinee. */
    public static int chip(GuiGraphics g, Font font, String texte, int x, int y, int rgb) {
        return chip(g, font, texte, x, y, rgb, 255);
    }

    /** Pastille a l'opacite {@code alpha} (apparition en fondu). */
    public static int chip(GuiGraphics g, Font font, String texte, int x, int y, int rgb, int alpha) {
        int w = chipWidth(font, texte), h = 11;
        soft(g, x, y, w, h, argb(rgb, 34 * alpha / 255));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(rgb, 90 * alpha / 255));
        if (lisible(alpha)) spaced(g, font, texte, SMALL, x + 5, y + 2.5f, argb(mix(rgb, 0xFFFFFF, 0.3f), alpha), 0.6f);
        return w;
    }

    /**
     * Un texte d'opacite {@code alpha} peut-il etre dessine ? Minecraft dessine OPAQUE un texte dont l'opacite est sous 4
     * (au lieu de le cacher) : un fondu qui part de 0 ferait clignoter le texte en plein une image.
     */
    public static boolean lisible(int alpha) {
        return alpha >= 4;
    }

    public static int chipWidth(Font font, String texte) {
        return (int) spacedWidth(font, texte, SMALL, 0.6f) + 10;
    }

    /** Barre de progression fine : piste blanche a 9 %, remplissage de la couleur. ratio 0..1. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float ratio, int rgb) {
        g.fill(x, y, x + w, y + h, argb(0xFFFFFF, 22));
        int fw = Math.round(w * Math.max(0, Math.min(1, ratio)));
        if (fw > 0) g.fill(x, y, x + fw, y + h, argb(rgb, 235));
    }

    /**
     * Anneau (ou disque si {@code rInt} = 0) centre en (cx, cy), du rayon interieur {@code rInt} au rayon exterieur
     * {@code rExt}, rempli de la fraction {@code de} a la fraction {@code a} d'un tour (0 = midi, sens des aiguilles d'une
     * montre). Quads du rendu « gui » (meme sens de rotation que {@code GuiGraphics.fill}) : rien n'est alloue.
     */
    public static void anneau(GuiGraphics g, float cx, float cy, float rInt, float rExt, float de, float a, int argb) {
        float d = Math.max(0f, Math.min(1f, de)), f = Math.max(d, Math.min(1f, a));
        if (f - d <= 0.0005f || (argb >>> 24) == 0) return;
        int n = Math.max(2, (int) Math.ceil(72 * (f - d)));
        float al = (argb >>> 24) / 255f, r = ((argb >> 16) & 255) / 255f, gr = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        org.joml.Matrix4f m = g.pose().last().pose();
        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        double t0 = (d * 2 * Math.PI);
        float s0 = (float) Math.sin(t0), c0 = (float) Math.cos(t0);
        for (int i = 1; i <= n; i++) {
            double t1 = (d + (f - d) * i / n) * 2 * Math.PI;
            float s1 = (float) Math.sin(t1), c1 = (float) Math.cos(t1);
            // exterieur 0, interieur 0, interieur 1, exterieur 1 (sens anti-horaire a l'ecran, comme fill)
            vc.vertex(m, cx + rExt * s0, cy - rExt * c0, 0).color(r, gr, b, al).endVertex();
            vc.vertex(m, cx + rInt * s0, cy - rInt * c0, 0).color(r, gr, b, al).endVertex();
            vc.vertex(m, cx + rInt * s1, cy - rInt * c1, 0).color(r, gr, b, al).endVertex();
            vc.vertex(m, cx + rExt * s1, cy - rExt * c1, 0).color(r, gr, b, al).endVertex();
            s0 = s1;
            c0 = c1;
        }
        g.flush();
    }

    /** Disque plein (fond d'un anneau). */
    public static void disque(GuiGraphics g, float cx, float cy, float r, int argb) {
        anneau(g, cx, cy, 0f, r, 0f, 1f, argb);
    }

    /**
     * Tuile de statistique (kit des clans) : gros chiffre en gras + legende en petites capitales attenuees, sur verre leger.
     * Le chiffre est en DM Sans gras 16 a sa vraie taille (avant : gras 11 agrandi 1,5 fois), pose sur un point de l'ecran ;
     * s'il deborderait de la tuile, il passe a la police d'un cran plus petite (14, 11, puis 9) au lieu d'etre reduit, avec
     * le milieu des capitales la ou l'ancien chiffre reduit l'avait. La legende perd son espacement puis se coupe.
     */
    public static void tile(GuiGraphics g, Font font, int x, int y, int w, int h, Component valeur, String legende, int alpha) {
        soft(g, x, y, w, h, argb(0xFFFFFF, 8 * alpha / 255));
        softOutline(g, x + 1, y + 1, w - 2, h - 2, argb(0xFFFFFF, 14 * alpha / 255));
        int place = w - 14;
        ChiffreTuile chiffre = chiffreTuile(font, valeur, place);
        float e = chiffre.taille() / 11f, cy = y + 7 + (1.5f - e) * 4f + e * milieu(11f);
        g.pose().pushPose();
        placer(g, x + 10, cy - milieu(chiffre.taille()));
        g.drawString(font, chiffre.texte(), 0, 0, argb(TEXT, alpha), false);
        g.pose().popPose();
        float esp = spacedWidth(font, legende, SMALL, 0.8f) > place ? 0f : 0.8f;
        String leg = spacedWidth(font, legende, SMALL, esp) > place ? ellipsis(font, legende.toUpperCase(Locale.ROOT), SMALL, place) : legende;
        spaced(g, font, leg, SMALL, x + 10, y + h - 13, argb(MUTED, alpha), esp);
    }

    /**
     * Polices du chiffre des tuiles, de la plus grande a la plus petite : DM Sans gras 16, 14, 11, 9, avec le glyphe « héral »
     * (jeton.png de heralisz_economie, en bitmap, centre sur les capitales) a la taille de chacune.
     */
    private static final float[] TUILE_TAILLES = {16f, 14f, 11f, 9f};
    private static final ResourceLocation[] TUILE_POLICES = {HeralizInterface.id("ui_tuile_16"), HeralizInterface.id("ui_tuile_14"),
            HeralizInterface.id("ui_tuile_11"), HeralizInterface.id("ui_tuile_9")};

    /** Chiffre d'une tuile mis dans sa police, et la taille de celle-ci. */
    private record ChiffreTuile(Component texte, float taille) {
    }

    private record CleTuile(Component valeur, int place) {
    }

    /** Chiffres deja mis en police (cle = valeur et place) : rien a refaire a l'image tant que la valeur ne change pas. */
    private static final Map<CleTuile, ChiffreTuile> TUILES = new HashMap<>();

    /** La plus grande police ou le chiffre tient dans {@code place} (la plus petite s'il ne tient nulle part). */
    private static ChiffreTuile chiffreTuile(Font font, Component valeur, int place) {
        CleTuile cle = new CleTuile(valeur, place);
        ChiffreTuile c = TUILES.get(cle);
        if (c != null) return c;
        if (TUILES.size() > 256) TUILES.clear();
        int dernier = TUILE_POLICES.length - 1;
        for (int i = 0; i <= dernier; i++) {
            Component t = enPolice(valeur, TUILE_POLICES[i]);
            if (i == dernier || font.getSplitter().stringWidth(t) <= place) {
                c = new ChiffreTuile(t, TUILE_TAILLES[i]);
                break;
            }
        }
        TUILES.put(cle, c);
        return c;
    }

    /** Le meme texte (couleurs comprises), tout entier dans la police {@code f} (glyphe « héral » compris). */
    private static Component enPolice(Component c, ResourceLocation f) {
        MutableComponent r = Component.empty();
        c.visit((style, texte) -> {
            r.append(Component.literal(texte).withStyle(style.withFont(f)));
            return Optional.empty();
        }, Style.EMPTY);
        return r;
    }

    /** Milieu des capitales sous le point de dessin, pour une police DM Sans du mod de cette taille (decalage 1). */
    private static float milieu(float taille) {
        return taille * 0.49309f - 1f;
    }

    /** Pose le dessin qui suit sur le point d'ecran le plus proche (lettres nettes ; repris du kit des Caisses). */
    private static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    /** Texte agrandi (gros chiffres). */
    public static void gros(GuiGraphics g, Font font, Component c, float x, float y, float echelle, int argb) {
        g.pose().pushPose();
        g.pose().translate(Math.round(x), Math.round(y), 0);
        g.pose().scale(echelle, echelle, 1);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Titre de section en capitales espacees avec un filet qui s'efface a droite. */
    public static void section(GuiGraphics g, Font font, String titre, int x, int y, int w) {
        section(g, font, titre, x, y, w, 255);
    }

    /** Titre de section a l'opacite {@code alpha} (apparition en fondu). */
    public static void section(GuiGraphics g, Font font, String titre, int x, int y, int w, int alpha) {
        if (lisible(alpha)) spaced(g, font, titre, SMALL, x, y, argb(MUTED, alpha), 1.0f);
        int tw = (int) spacedWidth(font, titre, SMALL, 1.0f);
        hairline(g, x + tw + 8, x + w, y + 4, 0xFFFFFF, 40 * alpha / 255, false, true);
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
    /** Nombre avec separateur de milliers (espace en francais, virgule en anglais) : 12 450. */
    public static String nombre(long v) {
        String s = String.format(Locale.ROOT, "%,d", v);
        return french() ? s.replace(',', ' ') : s;
    }

    /** Nombre court (arrondi vers le bas, jamais plus que la vraie valeur) : « 9 999 », « 12,4 k », « 124 k », « 1,2 M ». */
    public static String nombreCourt(long v) {
        long a = Math.abs(v);
        if (a < 10_000) return nombre(v);
        String signe = v < 0 ? "-" : "";
        String virgule = t("gui.heraliz_interface.nombre.virgule");
        if (a < 100_000) return t("gui.heraliz_interface.nombre.milliers", signe + a / 1000 + virgule + a / 100 % 10);
        if (a < 1_000_000) return t("gui.heraliz_interface.nombre.milliers", signe + a / 1000);
        if (a < 100_000_000) return t("gui.heraliz_interface.nombre.millions", signe + a / 1_000_000 + virgule + a / 100_000 % 10);
        return t("gui.heraliz_interface.nombre.millions", signe + a / 1_000_000);
    }

    /** Duree courte : « 2 j 3 h », « 7 h 12 », « 3 min », « 12 s ». */
    public static String duree(long ms) {
        long s = Math.max(0, ms / 1000), min = s / 60, h = min / 60, j = h / 24;
        if (j >= 1) return j + t("gui.heraliz_interface.duree.j") + " " + (h % 24) + t("gui.heraliz_interface.duree.h");
        if (h >= 1) return h + t("gui.heraliz_interface.duree.h") + " " + String.format(Locale.ROOT, "%02d", min % 60);
        if (min >= 1) return min + t("gui.heraliz_interface.duree.min");
        return s + t("gui.heraliz_interface.duree.s");
    }

    /** Compte a rebours « 02:14:08 » (ou « 14:08 » sous une heure). */
    public static String chrono(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%02d:%02d", m, sec);
    }

    /** « il y a 12 min ». */
    public static String ilYA(long ms) {
        return t("gui.heraliz_interface.il_y_a", duree(ms));
    }
}
