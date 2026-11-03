package fr.heraliz.emote.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.ClientRules;
import fr.heraliz.emote.client.DeblocagesClient;
import fr.heraliz.emote.client.gui.kit.Echelle;
import fr.heraliz.emote.client.gui.kit.SonsUi;
import fr.heraliz.emote.client.gui.kit.Ui;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import io.github.kosmx.emotes.arch.gui.EmoteMenuImpl;
import io.github.kosmx.emotes.executor.EmoteInstance;
import io.github.kosmx.emotes.main.EmoteHolder;
import io.github.kosmx.emotes.main.config.ClientConfig;
import io.github.kosmx.emotes.main.screen.widget.ModernChooseWheel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Roue des emotes (touche Y), au style des écrans du clan (03/11, retour du propriétaire : « revoir les UI des emotes,
 * roue des emotes, bien faire clean, alignées aux autres ») ; maquette : rendus du chantier ui-net (6b). Remplace à son
 * ouverture la roue d'Emotecraft ({@code FastChosseScreen}), sans toucher au code d'Emotecraft : mêmes 8 cases par page,
 * mêmes 10 pages, mêmes réglages (les emotes de la roue restent celles choisies dans le menu d'Emotecraft, « Modifier la
 * roue »), même lecture d'une emote.
 * <ul>
 * <li>8 cases de verre en cercle (icône, nom, numéro), une case survolée par l'angle de la souris comme la roue
 * d'origine ; centre : nom de l'emote survolée, page et flèches ← → ; emote à débloquer grisée avec un cadenas.</li>
 * <li>Clic ou touches 1 à 8 : jouer ; molette ou flèches : page ; « Toutes les emotes » : le menu des emotes (touche 0) ;
 * « Modifier la roue » : le menu d'Emotecraft où l'on range les emotes dans les cases.</li>
 * <li>Polices du mod à leur vraie taille, texte posé sur un point de l'écran ; écran « panneau » (Echelle.forcer(écran,
 * l, h)).</li>
 * </ul>
 */
public final class RoueEmotes extends Screen {
    private static final int L = 380, H = 360, RAYON = 112, CASE = 64, PAGES = 10, CASES = 8;
    private static final int BLANC = 0xFFFFFF;
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_emote", "ui_titre");

    @Nullable
    private final Screen parent;
    private float cx, cy;
    private int survol = -1;
    @Nullable
    private Component statut;
    private long statutA;

    private record Bouton(float x, float y, float w, float h, Runnable action) {
    }

    private final List<Bouton> boutons = new ArrayList<>();

    public RoueEmotes(@Nullable Screen parent) {
        super(Component.translatable("heraliz_emote.roue.titre"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Echelle.forcer(this, L, H);
        cx = width / 2f;
        cy = height / 2f - 8;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ données (réglages d'Emotecraft)
    private static int page() {
        int p = ModernChooseWheel.fastMenuPage;
        return p < 0 || p >= PAGES ? 0 : p;
    }

    private static void changerPage(int sens) {
        ModernChooseWheel.fastMenuPage = Math.floorMod(page() + sens, PAGES);
        SonsUi.clic(0.5F);
    }

    /** L'emote de la case {@code k} de la page courante (null : case vide ou emote absente). */
    @Nullable
    private static EmoteHolder emote(int k) {
        if (!(EmoteInstance.config instanceof ClientConfig cfg) || cfg.fastMenuEmotes == null) return null;
        int p = page();
        if (p >= cfg.fastMenuEmotes.length || cfg.fastMenuEmotes[p] == null || k >= cfg.fastMenuEmotes[p].length) return null;
        UUID id = cfg.fastMenuEmotes[p][k];
        return id == null ? null : EmoteHolder.list.get(id);
    }

    private static Component nom(KeyframeAnimation anim) {
        Object brut = anim.extraData.get("name");
        if (!(brut instanceof String s) || s.isBlank()) return Component.literal("?");
        try {
            MutableComponent c = Component.Serializer.fromJson(s);
            return c == null ? Component.literal(s) : c;
        } catch (RuntimeException e) {
            return Component.literal(s);
        }
    }

    private void jouer(int k) {
        EmoteHolder h = emote(k);
        if (h == null) return;
        KeyframeAnimation anim = h.getEmote();
        if (!DeblocagesClient.debloquee(anim.getUuid())) {
            dire(Component.translatable("heraliz_emote.menu.locked_tip"));
            return;
        }
        Component refus = ClientRules.refusal(anim.getUuid());
        if (refus != null) {
            dire(refus);
            return;
        }
        if (ClientEmoteAPI.playEmote(anim)) {
            SonsUi.clic(0.6F);
            onClose();
        } else {
            dire(Component.translatable("heraliz_emote.menu.cannot"));
        }
    }

    private void dire(Component c) {
        statut = c;
        statutA = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ souris et touches
    /** Case sous la souris : par l'angle, hors du disque du milieu et pas trop loin (comme la roue d'Emotecraft). */
    private int caseSous(double mx, double my) {
        double dx = mx - cx, dy = my - cy, d = Math.sqrt(dx * dx + dy * dy);
        if (d < 50 || d > RAYON + CASE * 0.75) return -1;
        double a = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 22.5;
        return Math.floorMod((int) Math.floor(a / 45), CASES);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        for (Bouton bt : new ArrayList<>(boutons)) {
            if (mx >= bt.x() && mx < bt.x() + bt.w() && my >= bt.y() && my < bt.y() + bt.h()) {
                bt.action().run();
                return true;
            }
        }
        int k = caseSous(mx, my);
        if (b == 0 && k >= 0) {
            jouer(k);
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta != 0) changerPage(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int touche, int scan, int mods) {
        if (touche >= GLFW.GLFW_KEY_1 && touche <= GLFW.GLFW_KEY_8) {
            jouer(touche - GLFW.GLFW_KEY_1);
            return true;
        }
        if (touche == GLFW.GLFW_KEY_LEFT || touche == GLFW.GLFW_KEY_RIGHT) {
            changerPage(touche == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        return super.keyPressed(touche, scan, mods);
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        boutons.clear();
        Ui.fond(g, width, height);
        survol = caseSous(mx, my);
        int accent = Ui.accent();
        EmoteHolder montree = survol >= 0 ? emote(survol) : null;

        float haut = cy - RAYON - CASE / 2f;
        centre(g, t("titre"), TITRE, cx, haut - 30, Ui.TEXT);
        capsCentre(g, t("sous_titre", page() + 1, PAGES), Ui.SMALL, 1.0f, cx, haut - 14, Ui.MUTED);
        anneau(g, cx, cy, RAYON, point(), Ui.argb(BLANC, 22));

        for (int k = 0; k < CASES; k++) caseEmote(g, k, accent);

        // milieu
        disque(g, cx, cy, 48, Ui.argb(Ui.VERRE, 235));
        anneau(g, cx, cy, 48, point(), Ui.argb(BLANC, 40));
        if (montree != null) {
            centre(g, couper(nom(montree.getEmote()).getString(), Ui.BOLD, 84), Ui.BOLD, cx, cy - 8, Ui.TEXT);
            capsCentre(g, t("jouer"), Ui.SMALL, 0.8f, cx, cy + 5, Ui.MUTED);
        } else {
            centre(g, t(survol >= 0 ? "vide" : "choisir"), Ui.SMALL, cx, cy - 4, Ui.MUTED);
        }
        fleche(g, "←", cx - 24, cy + 22, mx, my, () -> changerPage(-1));
        capsCentre(g, (page() + 1) + " / " + PAGES, Ui.SMALL, 0.8f, cx, cy + 22, Ui.TEXT);
        fleche(g, "→", cx + 24, cy + 22, mx, my, () -> changerPage(1));

        // boutons
        float by = cy + RAYON + CASE / 2f + 22, bw = 128, bh = 18, ecart = 8;
        bouton(g, t("toutes"), cx - bw - ecart / 2, by, bw, bh, accent, true, mx, my, () -> {
            SonsUi.clic(0.6F);
            Minecraft.getInstance().setScreen(new EmoteBrowserScreen());
        });
        bouton(g, t("modifier"), cx + ecart / 2, by, bw, bh, BLANC, false, mx, my, () -> {
            SonsUi.clic(0.6F);
            Minecraft.getInstance().setScreen(new EmoteMenuImpl(this));
        });

        // message (refus) ou aides
        float y = by + 22;
        if (statut != null && System.currentTimeMillis() - statutA < 4000) {
            centre(g, couper(statut.getString(), Ui.SMALL, L), Ui.SMALL, cx, y, Ui.DANGER);
        } else {
            aides(g, y);
        }
        super.render(g, mx, my, pt);
    }

    private void caseEmote(GuiGraphics g, int k, int accent) {
        double ang = Math.toRadians(-90 + 45 * k);
        float x = (float) (cx + Math.cos(ang) * RAYON - CASE / 2f), y = (float) (cy + Math.sin(ang) * RAYON - CASE / 2f);
        boolean sv = k == survol;
        EmoteHolder h = emote(k);
        rect(g, x, y, x + CASE, y + CASE, Ui.argb(Ui.VERRE, 225));
        if (sv && h != null) {
            rect(g, x, y, x + CASE, y + CASE, Ui.argb(accent, 46));
            cadre(g, x, y, CASE, CASE, Ui.argb(accent, 230));
            rect(g, x, y, x + CASE, y + 2, Ui.argb(accent, 255));
        } else {
            cadre(g, x, y, CASE, CASE, Ui.argb(BLANC, sv ? 70 : 34));
        }
        texte(g, String.valueOf(k + 1), Ui.SMALL, x + 5, y + 8, Ui.FAINT);
        if (h == null) {
            centre(g, t("vide"), Ui.SMALL, x + CASE / 2f, y + CASE / 2f, Ui.FAINT);
            return;
        }
        KeyframeAnimation anim = h.getEmote();
        boolean verrou = !DeblocagesClient.debloquee(anim.getUuid());
        EmoteIcons.Icon icone = EmoteIcons.get(anim);
        int ix = Math.round(x + (CASE - 32) / 2f), iy = Math.round(y + 7);
        if (verrou) RenderSystem.setShaderColor(0.42f, 0.44f, 0.48f, 0.7f);
        if (icone != null) {
            g.blit(icone.texture(), ix, iy, 32, 32, 0, 0, icone.width(), icone.height(), icone.width(), icone.height());
        } else {
            Ui.soft(g, ix, iy, 32, 32, Ui.argb(Ui.mix(accent, Ui.VERRE, 0.45f), 255));
            String n = nom(anim).getString();
            centre(g, n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(Locale.ROOT), Ui.BOLD, ix + 16, iy + 16, Ui.TEXT);
        }
        if (verrou) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            cadenas(g, ix + 25, iy + 23);
        }
        int c = sv ? Ui.TEXT : verrou ? Ui.FAINT : Ui.MUTED;
        centre(g, couper(nom(anim).getString(), Ui.SMALL, CASE - 8), Ui.SMALL, x + CASE / 2f, y + CASE - 10, c);
    }

    private static void cadenas(GuiGraphics g, float x, float y) {
        int c = Ui.argb(0xD8DCE4, 235);
        rect(g, x - 1, y - 1, x + 9, y + 10, Ui.argb(Ui.VERRE, 210));
        cadre(g, x + 1.5f, y, 5, 5, c);
        rect(g, x, y + 4, x + 8, y + 9, c);
    }

    private void fleche(GuiGraphics g, String s, float x, float y, int mx, int my, Runnable action) {
        boolean sv = mx >= x - 8 && mx < x + 8 && my >= y - 7 && my < y + 7;
        centre(g, s, Ui.BOLD, x, y, sv ? Ui.TEXT : Ui.MUTED);
        boutons.add(new Bouton(x - 8, y - 7, 16, 14, action));
    }

    private void bouton(GuiGraphics g, String txt, float x, float cyb, float w, float h, int c, boolean plein, int mx, int my, Runnable action) {
        boolean sv = mx >= x && mx < x + w && my >= cyb - h / 2 && my < cyb + h / 2;
        if (plein) {
            rect(g, x, cyb - h / 2, x + w, cyb + h / 2, Ui.argb(c, sv ? 90 : 60));
            cadre(g, x, cyb - h / 2, w, h, Ui.argb(c, 220));
        } else {
            rect(g, x, cyb - h / 2, x + w, cyb + h / 2, Ui.argb(BLANC, sv ? 20 : 6));
            cadre(g, x, cyb - h / 2, w, h, Ui.argb(BLANC, sv ? 170 : 90));
        }
        capsCentre(g, txt, Ui.SMALL, 1.0f, x + w / 2, cyb, Ui.TEXT);
        boutons.add(new Bouton(x, cyb - h / 2, w, h, action));
    }

    private void aides(GuiGraphics g, float y) {
        String[][] aides = {{t("t.clic"), t("k.jouer")}, {t("t.molette"), t("k.page")}, {t("t.cases"), t("k.case")}, {t("t.echap"), t("k.fermer")}};
        float total = -12;
        for (String[] a : aides) total += capsLargeur(a[0], Ui.SMALL, 0.8f) + 8 + 4 + largeur(a[1], Ui.SMALL) + 12;
        float x = cx - total / 2;
        for (String[] a : aides) {
            float w = capsLargeur(a[0], Ui.SMALL, 0.8f) + 8;
            rect(g, x, y - 5, x + w, y + 5, Ui.argb(BLANC, 18));
            cadre(g, x, y - 5, w, 10, Ui.argb(BLANC, 60));
            capsCentre(g, a[0], Ui.SMALL, 0.8f, x + w / 2, y, Ui.TEXT);
            x += w + 4;
            x += texte(g, a[1], Ui.SMALL, x, y, Ui.MUTED) + 12;
        }
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("heraliz_emote.roue." + cle, args).getString();
    }

    // ------------------------------------------------------------------ dessin net (même méthode que le kit Net des quêtes)
    /** Taille des polices du mod (fichiers font/*.json). */
    private static float taille(ResourceLocation f) {
        return switch (f.getPath()) {
            case "ui_titre" -> 14f;
            case "ui_small" -> 9f;
            case "ui_gros" -> 16f;
            default -> 11f;
        };
    }

    /** Milieu des capitales de DM Sans sous le point de dessin (ascendante 992, capitales 700 sur 1302, décalage 1). */
    private static float milieu(ResourceLocation f) {
        return taille(f) * 0.49309f - 1f;
    }

    private static float point() {
        return (float) (1.0 / Minecraft.getInstance().getWindow().getGuiScale());
    }

    private static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    private float largeur(String s, ResourceLocation f) {
        return font.getSplitter().stringWidth(Ui.lit(s, f));
    }

    private float texte(GuiGraphics g, String s, ResourceLocation f, float x, float cyt, int rgb) {
        if (s.isEmpty()) return 0;
        g.pose().pushPose();
        placer(g, x, cyt - milieu(f));
        g.drawString(font, Ui.lit(s, f), 0, 0, Ui.argb(rgb, 255), false);
        g.pose().popPose();
        return largeur(s, f);
    }

    private void centre(GuiGraphics g, String s, ResourceLocation f, float x, float cyt, int rgb) {
        texte(g, s, f, x - largeur(s, f) / 2f, cyt, rgb);
    }

    private float capsLargeur(String s, ResourceLocation f, float esp) {
        String up = s.toUpperCase(Locale.ROOT);
        float w = 0;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            w += largeur(new String(Character.toChars(up.codePointAt(i))), f) + esp;
        }
        return Math.max(0, w - esp);
    }

    private void capsCentre(GuiGraphics g, String s, ResourceLocation f, float esp, float x, float cyt, int rgb) {
        String up = s.toUpperCase(Locale.ROOT);
        float px = x - capsLargeur(s, f, esp) / 2f;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            String ch = new String(Character.toChars(up.codePointAt(i)));
            texte(g, ch, f, px, cyt, rgb);
            px += largeur(ch, f) + esp;
        }
    }

    private String couper(String s, ResourceLocation f, float max) {
        if (largeur(s, f) <= max) return s;
        int n = s.length();
        while (n > 0 && largeur(s.substring(0, n).stripTrailing() + "…", f) > max) n--;
        return n <= 0 ? "" : s.substring(0, n).stripTrailing() + "…";
    }

    /** Rectangle plein, bords posés sur les points de l'écran (au moins un point). */
    private static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float a0 = Math.round(x0 * s) / s, b0 = Math.round(y0 * s) / s, a1 = Math.round(x1 * s) / s, b1 = Math.round(y1 * s) / s;
        if (a1 <= a0) a1 = a0 + 1 / s;
        if (b1 <= b0) b1 = b0 + 1 / s;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int a = argb >>> 24, r = argb >> 16 & 255, v = argb >> 8 & 255, b = argb & 255;
        vc.vertex(m, a0, b0, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a0, b1, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a1, b1, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a1, b0, 0).color(r, v, b, a).endVertex();
    }

    private static void cadre(GuiGraphics g, float x, float y, float w, float h, int argb) {
        float e = point();
        rect(g, x, y, x + w, y + e, argb);
        rect(g, x, y + h - e, x + w, y + h, argb);
        rect(g, x, y + e, x + e, y + h - e, argb);
        rect(g, x + w - e, y + e, x + w, y + h - e, argb);
    }

    private static void disque(GuiGraphics g, float x, float y, float r, int argb) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale(), pas = 1 / s;
        for (float l = (float) Math.floor((y - r) * s) / s; l < y + r; l += pas) {
            float d = l + pas / 2 - y, demi = (float) Math.sqrt(Math.max(0, r * r - d * d));
            if (demi > pas / 3) rect(g, x - demi, l, x + demi, l + pas, argb);
        }
    }

    private static void anneau(GuiGraphics g, float x, float y, float r, float e, int argb) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale(), pas = 1 / s, ri = r - e;
        for (float l = (float) Math.floor((y - r) * s) / s; l < y + r; l += pas) {
            float d = l + pas / 2 - y, ext = (float) Math.sqrt(Math.max(0, r * r - d * d));
            if (ext <= pas / 3) continue;
            if (Math.abs(d) >= ri) {
                rect(g, x - ext, l, x + ext, l + pas, argb);
            } else {
                float in = (float) Math.sqrt(Math.max(0, ri * ri - d * d));
                rect(g, x - ext, l, x - in, l + pas, argb);
                rect(g, x + in, l, x + ext, l + pas, argb);
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
