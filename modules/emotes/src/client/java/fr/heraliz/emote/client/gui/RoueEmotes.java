package fr.heraliz.emote.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.ClientRules;
import fr.heraliz.emote.client.DeblocagesClient;
import fr.heraliz.emote.client.gui.kit.Echelle;
import fr.heraliz.emote.client.gui.kit.Net;
import fr.heraliz.emote.client.gui.kit.SonsUi;
import fr.heraliz.emote.client.gui.kit.Ui;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import io.github.kosmx.emotes.executor.EmoteInstance;
import io.github.kosmx.emotes.main.EmoteHolder;
import io.github.kosmx.emotes.main.config.ClientConfig;
import io.github.kosmx.emotes.main.screen.widget.ModernChooseWheel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
 * « Modifier la roue » : notre écran d'édition ({@link EditionRoue}, 04/11 ; avant : le menu d'Emotecraft).</li>
 * <li>Polices du mod à leur vraie taille, texte posé sur un point de l'écran ; écran « panneau » (Echelle.forcer(écran,
 * l, h)).</li>
 * <li>18/11 (retour du propriétaire : « pas besoin d'autant de texte, c'est intuitif ») : plus de sous-titre « Roue rapide ·
 * page » (la page est au milieu), de « Clic : jouer », de « Vise une case » ni de pied d'aide des touches ; seul un refus
 * s'écrit sous les boutons.</li>
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

        // 18/11 (retour du propriétaire : pas de texte d'aide) : le titre seul au-dessus de la roue (la page est au milieu)
        float haut = cy - RAYON - CASE / 2f;
        centre(g, t("titre"), TITRE, cx, haut - 20, Ui.TEXT);
        anneau(g, cx, cy, RAYON, point(), Ui.argb(BLANC, 22));

        for (int k = 0; k < CASES; k++) caseEmote(g, k, accent);

        // milieu : le nom de l'emote visée (ou « Vide »), sans « Clic : jouer » ni « Vise une case »
        disque(g, cx, cy, 48, Ui.argb(Ui.VERRE, 235));
        anneau(g, cx, cy, 48, point(), Ui.argb(BLANC, 40));
        if (montree != null) {
            centre(g, couper(nom(montree.getEmote()).getString(), Ui.BOLD, 84), Ui.BOLD, cx, cy - 4, Ui.TEXT);
        } else if (survol >= 0) {
            centre(g, t("vide"), Ui.SMALL, cx, cy - 4, Ui.MUTED);
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
            // 04/11 : notre écran (maquette validée) à la place du menu d'Emotecraft
            Minecraft.getInstance().setScreen(new EditionRoue(this));
        });

        // message (refus) seulement ; 18/11 : plus de pied d'aide (CLIC jouer, MOLETTE page, 1-8 case, ÉCHAP fermer)
        float y = by + 22;
        if (statut != null && System.currentTimeMillis() - statutA < 4000) {
            centre(g, couper(statut.getString(), Ui.SMALL, L), Ui.SMALL, cx, y, Ui.DANGER);
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

    private static String t(String cle, Object... args) {
        return Component.translatable("heraliz_emote.roue." + cle, args).getString();
    }

    // ------------------------------------------------------------------ dessin net (kit.Net, partagé avec l'édition de la roue)
    private static float point() {
        return Net.point();
    }

    private float texte(GuiGraphics g, String s, ResourceLocation f, float x, float cyt, int rgb) {
        return Net.texte(g, font, s, f, x, cyt, rgb);
    }

    private void centre(GuiGraphics g, String s, ResourceLocation f, float x, float cyt, int rgb) {
        Net.centre(g, font, s, f, x, cyt, rgb);
    }

    private void capsCentre(GuiGraphics g, String s, ResourceLocation f, float esp, float x, float cyt, int rgb) {
        Net.capsCentre(g, font, s, f, esp, x, cyt, rgb);
    }

    private String couper(String s, ResourceLocation f, float max) {
        return Net.couper(font, s, f, max);
    }

    private static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        Net.rect(g, x0, y0, x1, y1, argb);
    }

    private static void cadre(GuiGraphics g, float x, float y, float w, float h, int argb) {
        Net.cadre(g, x, y, w, h, argb);
    }

    private static void disque(GuiGraphics g, float x, float y, float r, int argb) {
        Net.disque(g, x, y, r, argb);
    }

    private static void anneau(GuiGraphics g, float x, float y, float r, float e, int argb) {
        Net.anneau(g, x, y, r, e, argb);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
