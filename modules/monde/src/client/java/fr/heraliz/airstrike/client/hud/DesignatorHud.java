package fr.heraliz.airstrike.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.client.CartridgeWheelScreen;
import fr.heraliz.airstrike.client.ClientStrikes;
import fr.heraliz.airstrike.client.DesignatorClient;
import fr.heraliz.airstrike.strike.CartridgeTier;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/**
 * Vue des jumelles (maquette validée) : masque des deux lentilles, réticule blanc à graduations, cadre de visée rouge,
 * boussole en bandeau (points cardinaux en français), mesures à gauche (distance, élévation, grille, azimut, zoom),
 * cartouche chargée et touches à droite, état en bas (vert : prêt ; ambre : recharge, hors de portée, à charger ;
 * rouge : cible marquée, impact).
 */
public final class DesignatorHud {
    private static final ResourceLocation MASK = new ResourceLocation(AirstrikeMod.MODID, "textures/gui/binocular_mask.png");
    private static final ResourceLocation MINI = new ResourceLocation(AirstrikeMod.MODID, "ui_mini"),
            MINI_GRAS = new ResourceLocation(AirstrikeMod.MODID, "ui_mini_bold");
    private static final int ROUGE = 0xFF4A3D, VERT = 0x5FD08A, AMBRE = 0xE8B34A, MUET = 0x8F95A0;
    private static final int TRAIT = 0xC7FFFFFF, TRAIT_OMBRE = 0x40000000;

    private DesignatorHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui || !DesignatorClient.isScoped()) return;
        Font font = mc.font;
        float fade = Mth.clamp((p.getTicksUsingItem() - DesignatorClient.SCOPE_DELAY + pt) / 3.0f, 0f, 1f);
        long gameTime = mc.level.getGameTime();

        // -- masque des lentilles (texture 2:1 à la hauteur de l'écran) + bandes noires sur les côtés
        RenderSystem.enableBlend();
        int mh = h, mw = h * 2, mx = (w - mw) / 2;
        g.blit(MASK, mx, 0, mw, mh, 0f, 0f, 1024, 512, 1024, 512);
        if (mx > 0) {
            g.fill(0, 0, mx, h, 0xFF000000);
            g.fill(mx + mw, 0, w, h, 0xFF000000);
        }
        if (fade < 1f) {
            int a = (int) ((1f - fade) * 255) << 24;
            g.fill(0, 0, w, h, a);
        }

        int cx = w / 2, cy = h / 2;
        drawReticle(g, font, cx, cy, h, gameTime, pt);
        float heading = Mth.wrapDegrees(p.getViewYRot(pt) + 180f);
        if (heading < 0) heading += 360f;
        drawCompass(g, font, cx, Math.max(10, Math.round(h * 0.0667f)), w, heading);

        // -- à gauche : mesures
        int pw = 112;
        int lx = Math.max(8, cx - Math.round(h * 0.675f)), ly = cy + Math.round(h * 0.0926f);
        String dist = DesignatorClient.aimDistance < 0 ? "----" : metres(String.format(Locale.ROOT, "%.0f", DesignatorClient.aimDistance));
        String elev = "----", grille = "----";
        if (DesignatorClient.aimPoint != null) {
            double dy = DesignatorClient.aimPoint.y - p.getEyeY();
            elev = metres(String.format(Locale.ROOT, "%+.0f", dy));
            grille = nombre((int) Math.floor(DesignatorClient.aimPoint.x)) + " · " + nombre((int) Math.floor(DesignatorClient.aimPoint.z));
        }
        String[][] lignes = {
                {"hud.airstrike.mesure.distance", dist},
                {"hud.airstrike.mesure.elevation", elev},
                {"hud.airstrike.mesure.grille", grille},
                {"hud.airstrike.mesure.azimut", String.format(Locale.ROOT, "%.0f°", heading)},
                {"hud.airstrike.mesure.zoom", "×" + zoom(DesignatorClient.zoom())}};
        panneau(g, lx, ly, pw, 5 + lignes.length * 11 + 4, 0x080A0C, 158, 0xFFFFFF, 31);
        for (int i = 0; i < lignes.length; i++) {
            int y = ly + 6 + i * 11;
            caps(g, font, Component.translatable(lignes[i][0]).getString(), MINI, lx + 6, y + 2, 1f, 1.1f, argb(MUET, 255));
            int couleur = i == 0 && !DesignatorClient.aimInRange && DesignatorClient.aimDistance >= 0 ? AMBRE : 0xFFFFFF;
            droite(g, font, lit(lignes[i][1], CartridgeWheelScreen.BOLD), lx + pw - 6, y - 0.5f, 0.9f, argb(couleur, 255));
        }

        // -- à droite : cartouche chargée, réserve, touches
        int rx = Math.min(cx + Math.round(h * 0.397f), w - pw - 8), ry = ly;
        StrikeType type = DesignatorClient.loaded();
        boolean creative = p.getAbilities().instabuild;
        panneau(g, rx, ry, pw, 68, 0x080A0C, 158, 0xFFFFFF, 31);
        caps(g, font, Component.translatable("hud.airstrike.cartouche_chargee").getString(), MINI, rx + 6, ry + 5, 1f, 1.0f, argb(MUET, 255));
        // icône dans son cadre
        g.fillGradient(rx + 6, ry + 14, rx + 21, ry + 29, 0xFF5E6670, 0xFF3A4048);
        cadre(g, rx + 6, ry + 14, 15, 15, argb(0xFFFFFF, 64));
        if (type != null) {
            CartridgeWheelScreen.icon(g, type.ordinal(), rx + 7.5f, ry + 15.5f, 12f, 0xFF000000 | type.uiColor);
            RenderSystem.enableBlend();
            Component nom = lit(Component.translatable(type.translationKey()).getString(), CartridgeWheelScreen.BOLD);
            gauche(g, font, nom, rx + 25, ry + 13, 0.9f, argb(mix(type.uiColor, 0xFFFFFF, 0.25f), 255));
            CartridgeTier tier = type.tier != null ? type.tier : CartridgeTier.HEAVY;
            String niveau = Component.translatable(tier.translationKey()).getString().toUpperCase(Locale.ROOT);
            int cw = Math.round(espaceLargeur(font, niveau, MINI_GRAS, 0.6f)) + 8;
            g.fill(rx + 25, ry + 23, rx + 25 + cw, ry + 31, argb(tier.color, 40));
            cadre(g, rx + 25, ry + 23, cw, 8, argb(tier.color, 110));
            caps(g, font, niveau, MINI_GRAS, rx + 29, ry + 23.5f, 1f, 0.6f, argb(mix(tier.color, 0xFFFFFF, 0.3f), 255));
            String reserve = creative ? "+∞" : Component.translatable("hud.airstrike.en_reserve", DesignatorClient.payloadCount(type)).getString();
            g.drawString(font, lit(reserve, MINI), rx + 25 + cw + 4, ry + 24, argb(MUET, 255), false);
        } else {
            boolean blinkEmpty = (gameTime / 8) % 2 == 0;
            Component q = lit("?", CartridgeWheelScreen.BOLD);
            g.drawString(font, q, rx + 14 - font.width(q) / 2, ry + 17, argb(AMBRE, 255), false);
            gauche(g, font, lit(Component.translatable("hud.airstrike.empty").getString(), CartridgeWheelScreen.BOLD), rx + 25, ry + 16, 0.9f,
                    argb(blinkEmpty ? AMBRE : 0xFFFFFF, 255));
        }
        g.fill(rx + 6, ry + 35, rx + pw - 6, ry + 36, argb(0xFFFFFF, 26));
        String molette = Component.translatable("hud.airstrike.touche.molette").getString();
        indice(g, font, rx + 6, ry + 39, Component.translatable("hud.airstrike.indice.roue").getString(),
                DesignatorClient.CYCLE_KEY.getTranslatedKeyMessage().getString());
        indice(g, font, rx + 6, ry + 48, Component.translatable("hud.airstrike.indice.zoom").getString(), molette);
        indice(g, font, rx + 6, ry + 57, Component.translatable("hud.airstrike.indice.suivante").getString(),
                mc.options.keyShift.getTranslatedKeyMessage().getString(), molette);

        // -- en bas : état
        drawStatus(g, font, p, cx, Math.round(h * 0.861f), gameTime, type != null);
        RenderSystem.disableBlend();
    }

    /** Strike just loaded: a glass label at the bottom of the screen for 2.5 s (icon, name, level of the cartridge). */
    public static void renderToast(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        StrikeType t = DesignatorClient.toastType;
        if (t == null || t.tier == null || mc.level == null || mc.options.hideGui || mc.player == null) return;
        float age = mc.level.getGameTime() - DesignatorClient.toastTime + pt;
        if (age < 0 || age > 50) return;
        float a = Math.min(1f, age / 4f) * (age > 42 ? 1f - (age - 42) / 8f : 1f);
        if (a <= 0.01f) return;
        Font font = mc.font;
        int alpha = (int) (a * 255);
        Component name = Component.literal(Component.translatable(t.translationKey()).getString().toUpperCase(Locale.ROOT))
                .withStyle(st -> st.withFont(CartridgeWheelScreen.BOLD));
        Component tier = Component.literal(Component.translatable("hud.airstrike.toast.cartridge",
                        Component.translatable(t.tier.translationKey())).getString().toUpperCase(Locale.ROOT))
                .withStyle(st -> st.withFont(CartridgeWheelScreen.FONT));
        float tierScale = 0.7f;
        int tw = font.width(name), cw = (int) largeur(font, tier, tierScale) + 10;
        int pw = 26 + tw + 8 + cw + 8;
        int x0 = w / 2 - pw / 2;
        int y0 = h - 64 + (int) ((1f - Math.min(1f, age / 4f)) * 6f);
        RenderSystem.enableBlend();
        g.fill(x0 + 1, y0, x0 + pw - 1, y0 + 22, (int) (alpha * 0.8f) << 24 | 0x0D0F14);
        g.fill(x0, y0 + 1, x0 + 1, y0 + 21, (int) (alpha * 0.8f) << 24 | 0x0D0F14);
        g.fill(x0 + pw - 1, y0 + 1, x0 + pw, y0 + 21, (int) (alpha * 0.8f) << 24 | 0x0D0F14);
        g.fill(x0 + 2, y0, x0 + pw - 2, y0 + 1, alpha << 24 | t.tier.color);
        CartridgeWheelScreen.icon(g, t.ordinal(), x0 + 6, y0 + 4, 14f, alpha << 24 | t.uiColor);
        g.drawString(font, name, x0 + 26, y0 + 7, alpha << 24 | 0xECEEF2, false);
        int cx = x0 + 26 + tw + 8;
        g.fill(cx, y0 + 5, cx + cw, y0 + 17, (alpha * 40 / 255) << 24 | t.tier.color);
        gauche(g, font, tier, cx + 5, y0 + 8, tierScale, alpha << 24 | t.tier.color);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ réticule, boussole, état

    private static void drawReticle(GuiGraphics g, Font font, int cx, int cy, int h, long gameTime, float pt) {
        int len = 140, gap = 10;
        int down = Math.min(77, Math.round(h * 0.2f));
        // traits principaux (blancs, ombre douce)
        trait(g, cx - len, cy, cx - gap, cy + 1);
        trait(g, cx + gap, cy, cx + len, cy + 1);
        trait(g, cx, cy + gap, cx + 1, cy + down);
        trait(g, cx, cy - 50, cx + 1, cy - gap);
        // graduations
        for (int i = 1; i <= 8; i++) {
            int d = gap + i * 15, s = i % 2 == 0 ? 2 : 1;
            if (d >= len) break;
            g.fill(cx - d, cy - s, cx - d + 1, cy + s + 1, 0x99FFFFFF);
            g.fill(cx + d, cy - s, cx + d + 1, cy + s + 1, 0x99FFFFFF);
        }
        for (int i = 1; i <= 4; i++) {
            int d = gap + i * 15, s = i % 2 == 0 ? 3 : 2;
            if (d >= down) break;
            g.fill(cx - s, cy + d, cx + s + 1, cy + d + 1, 0x99FFFFFF);
        }
        // cadre de visée rouge, qui se resserre après un marquage
        float lock = Mth.clamp((gameTime - DesignatorClient.lockTime + pt) / 8.0f, 0f, 1f);
        boolean locking = gameTime - DesignatorClient.lockTime < 60;
        int size = locking ? (int) Mth.lerp(lock, 24, 8) : 8;
        int col = locking && (gameTime / 3) % 2 != 0 ? 0xFFFFFFFF : 0xFF000000 | ROUGE;
        int c = Math.max(4, size * 5 / 8);
        corner(g, cx - size, cy - size, c, 1, 1, col);
        corner(g, cx + size, cy - size, c, -1, 1, col);
        corner(g, cx - size, cy + size, c, 1, -1, col);
        corner(g, cx + size, cy + size, c, -1, -1, col);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF000000 | ROUGE);
        // distance à côté du réticule
        if (DesignatorClient.aimDistance >= 0) {
            String d = metres(String.format(Locale.ROOT, "%.0f", DesignatorClient.aimDistance));
            gauche(g, font, lit(d, MINI_GRAS), cx + 11, cy + 9, 1.2f, argb(DesignatorClient.aimInRange ? 0xFFFFFF : AMBRE, 255));
        }
    }

    private static void trait(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, TRAIT_OMBRE);
        g.fill(x0, y0, x1, y1, TRAIT);
    }

    private static void corner(GuiGraphics g, int x, int y, int len, int dx, int dy, int color) {
        int x2 = x + dx * len, y2 = y + dy * len;
        g.fill(Math.min(x, x2), y, Math.max(x, x2) + 1, y + 1, color);
        g.fill(x, Math.min(y, y2), x + 1, Math.max(y, y2) + 1, color);
    }

    /** Boussole en bandeau : graduations tous les 5°, valeurs tous les 15°, points cardinaux en gras, aiguille rouge. */
    private static void drawCompass(GuiGraphics g, Font font, int cx, int y, int w, float heading) {
        float pxParDegre = 1.4f;
        int demi = Math.min(113, w / 2 - 20);
        panneau(g, cx - demi, y, demi * 2, 16, 0x080A0C, 158, 0xFFFFFF, 31);
        for (int deg = (int) Math.floor(heading - demi / pxParDegre) - 1; deg <= heading + demi / pxParDegre + 1; deg++) {
            if (Math.floorMod(deg, 5) != 0) continue;
            int x = cx + Math.round((deg - heading) * pxParDegre);
            if (x < cx - demi + 3 || x > cx + demi - 3) continue;
            int d = Math.floorMod(deg, 360);
            boolean major = d % 15 == 0;
            g.fill(x, y, x + 1, y + (major ? 4 : 2), 0x8CFFFFFF);
            if (major) {
                boolean cardinal = d % 45 == 0;
                String label = cardinal ? Component.translatable("hud.airstrike.cap." + d).getString() : Integer.toString(d);
                Component c = lit(label, cardinal ? MINI_GRAS : MINI);
                float s = cardinal ? 1.2f : 1f;
                float lw = largeur(font, c, s);
                if (x - lw / 2 < cx - demi + 2 || x + lw / 2 > cx + demi - 2) continue;
                gauche(g, font, c, x - lw / 2, y + 6, s, cardinal ? 0xFFFFFFFF : 0x99FFFFFF);
            }
        }
        g.fill(cx, y - 2, cx + 1, y + 18, 0xFF000000 | ROUGE);
        Component cap = lit(String.format(Locale.ROOT, "%.0f°", heading), MINI_GRAS);
        float cw = largeur(font, cap, 1.2f);
        gauche(g, font, cap, cx + 0.5f - cw / 2, y + 20, 1.2f, 0xFFFFFFFF);
    }

    private static void drawStatus(GuiGraphics g, Font font, LocalPlayer p, int cx, int y, long gameTime, boolean loaded) {
        Component line;
        int teinte;
        float cd = p.getCooldowns().getCooldownPercent(p.getUseItem().getItem(), 0f);
        ClientStrikes.ClientStrike mine = ClientStrikes.myActive();
        boolean blink = (gameTime / 10) % 2 == 0;
        boolean point = true;
        if (gameTime - DesignatorClient.errorTime < 40) {
            line = Component.translatable(DesignatorClient.errorKey);
            teinte = AMBRE;
        } else if (mine != null) {
            double eta = ClientStrikes.myNextImpact();
            line = eta > 0 ? Component.translatable("hud.airstrike.status.inbound", String.format(Locale.ROOT, "%.0f", Math.ceil(eta / 20.0)))
                    : Component.translatable("hud.airstrike.status.impact");
            teinte = ROUGE;
            point = blink; // le laser clignote tant que la cible est tenue
        } else if (cd > 0f) {
            line = Component.translatable("hud.airstrike.status.cooldown");
            teinte = AMBRE;
        } else if (!loaded) {
            line = Component.translatable("hud.airstrike.status.load_hint", DesignatorClient.CYCLE_KEY.getTranslatedKeyMessage());
            teinte = AMBRE;
        } else if (!DesignatorClient.aimInRange) {
            line = Component.translatable("hud.airstrike.status.out_of_range", DesignatorClient.maxRange());
            teinte = AMBRE;
        } else {
            line = Component.translatable("hud.airstrike.status.ready");
            teinte = VERT;
        }
        String texte = line.getString().toUpperCase(Locale.ROOT);
        float tw = espaceLargeur(font, texte, MINI_GRAS, 1.2f);
        int pw = Math.max(153, Math.round(tw) + 30), x0 = cx - pw / 2;
        panneau(g, x0, y, pw, 17, mix(0x080A0C, teinte, 0.12f), 179, teinte, 128);
        float tx = cx - (tw + 9) / 2f;
        if (point) {
            g.fill(Math.round(tx) - 1, y + 6, Math.round(tx) + 4, y + 11, argb(teinte, 60));
            g.fill(Math.round(tx), y + 7, Math.round(tx) + 3, y + 10, argb(teinte, 255));
        }
        caps(g, font, texte, MINI_GRAS, tx + 9, y + 5.5f, 1f, 1.2f, argb(mix(teinte, 0xFFFFFF, 0.6f), 255));
        if (cd > 0f && mine == null && gameTime - DesignatorClient.errorTime >= 40) {
            g.fill(x0 + 6, y + 19, x0 + pw - 6, y + 20, argb(0xFFFFFF, 40));
            g.fill(x0 + 6, y + 19, x0 + 6 + Math.round((pw - 12) * (1f - cd)), y + 20, argb(AMBRE, 255));
        }
    }

    // ------------------------------------------------------------------ dessin

    /** Panneau de verre : fond teinté, contour fin, liseré haut, coins adoucis. */
    private static void panneau(GuiGraphics g, int x, int y, int w, int h, int fond, int alphaFond, int bord, int alphaBord) {
        int f = argb(fond, alphaFond);
        g.fill(x + 1, y, x + w - 1, y + 1, f);
        g.fill(x, y + 1, x + w, y + h - 1, f);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, f);
        cadre(g, x + 1, y + 1, w - 2, h - 2, argb(bord, alphaBord));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, argb(0xFFFFFF, 31));
    }

    /** Contour d'un pixel à l'intérieur du rectangle. */
    private static void cadre(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Une ligne d'aide : touche(s) dans un petit cadre, puis le texte. */
    private static void indice(GuiGraphics g, Font font, int x, int y, String texte, String... touches) {
        int cx = x;
        for (String t : touches) {
            Component c = lit(t, MINI_GRAS);
            int tw = font.width(c) + 6;
            g.fill(cx, y, cx + tw, y + 8, argb(0xFFFFFF, 30));
            cadre(g, cx, y, tw, 8, argb(0xFFFFFF, 64));
            g.drawString(font, c, cx + 3, y, 0xFFFFFFFF, false);
            cx += tw + 3;
        }
        g.drawString(font, lit(texte, MINI), cx + 2, y, argb(0xFFFFFF, 184), false);
    }

    private static int argb(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gr << 8) | bl;
    }

    private static MutableComponent lit(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    /**
     * Capitales espacées, x = bord gauche, à l'échelle s : police du même dessin à la vraie taille (taille de f x s,
     * jamais agrandie par la pose), milieu des capitales à la même place, chaque lettre posée sur un point de l'écran.
     */
    private static void caps(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, float sc, float espace, int argb) {
        String up = s.toUpperCase(Locale.ROOT);
        ResourceLocation p = CartridgeWheelScreen.police(f, sc);
        float yp = y + sc * CartridgeWheelScreen.milieu(CartridgeWheelScreen.taille(f)) - CartridgeWheelScreen.milieu(CartridgeWheelScreen.taille(p));
        float cx = x;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            Component c = lit(new String(Character.toChars(up.codePointAt(i))), p);
            g.pose().pushPose();
            CartridgeWheelScreen.placer(g, cx, yp);
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) + espace * sc;
        }
    }

    private static float espaceLargeur(Font font, String s, ResourceLocation f, float espace) {
        float w = 0;
        for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
            w += font.width(lit(new String(Character.toChars(s.codePointAt(i))), f)) + espace;
        }
        return Math.max(0, w - espace);
    }

    /**
     * Texte (une police du mod, bord gauche en x, haut de la ligne en y) à l'échelle sc : redessiné dans la police du
     * même dessin à la vraie taille (taille x sc, jamais agrandie par la pose), milieu des capitales à la même place,
     * posé sur un point de l'écran.
     */
    private static void gauche(GuiGraphics g, Font font, Component c, float x, float y, float sc, int argb) {
        ResourceLocation f = c.getStyle().getFont(), p = CartridgeWheelScreen.police(f, sc);
        g.pose().pushPose();
        CartridgeWheelScreen.placer(g, x, y + sc * CartridgeWheelScreen.milieu(CartridgeWheelScreen.taille(f)) - CartridgeWheelScreen.milieu(CartridgeWheelScreen.taille(p)));
        g.drawString(font, taillee(c, sc), 0, 0, argb, false);
        g.pose().popPose();
    }

    private static void droite(GuiGraphics g, Font font, Component c, float xDroite, float y, float sc, int argb) {
        gauche(g, font, c, xDroite - largeur(font, c, sc), y, sc, argb);
    }

    /** Le texte dans la police de sa taille à l'échelle sc (lui-même à l'échelle 1). */
    private static Component taillee(Component c, float sc) {
        return sc == 1f ? c : lit(c.getString(), CartridgeWheelScreen.police(c.getStyle().getFont(), sc));
    }

    /** Largeur du texte dessiné par {@link #gauche} à l'échelle sc. */
    private static float largeur(Font font, Component c, float sc) {
        return font.width(taillee(c, sc));
    }

    /** Nombre avec espace fine des milliers (« 1 840 », « -2 210 »). */
    private static String nombre(int v) {
        String s = Integer.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append('\u202f');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    /** Distance déjà formatée suivie de l'unité de la langue du joueur (« 120 m », « 120 м »). */
    private static String metres(String valeur) {
        return Component.translatable("airstrike.hud.metres", valeur).getString();
    }

    /** « 4 », « 2,5 » (virgule en français). */
    private static String zoom(float z) {
        boolean entier = Math.abs(z - Math.round(z)) < 0.05f;
        String s = entier ? Integer.toString(Math.round(z)) : String.format(Locale.ROOT, "%.1f", z);
        return Minecraft.getInstance().getLanguageManager().getSelected().startsWith("fr") ? s.replace('.', ',') : s;
    }
}
