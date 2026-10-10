package fr.heraliz.wonder.jetgun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.jetgun.JetGun;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Jauge de chaleur du Jet Gun (maquette validée « petites jauges » ; à la place du compteur de munitions et de la barre de
 * chaleur de TaCZ, masqués tant que le Jet Gun est en main) :
 * <ul>
 *   <li>en bas à droite, une carte de verre : « CHALEUR » et le pourcentage, une barre de 12 segments (blanc, ambre,
 *   rouge), la touche pour purger ; en surchauffe, « SURCHAUFFE » et 100 % en rouge, segments rouges et
 *   « Refroidissement… » ;</li>
 *   <li>sous le viseur, un arc de 21 points qui se remplit avec la chaleur (visible dès qu'elle monte), rouge au-delà
 *   de 85 %.</li>
 * </ul>
 * Presque aucune allocation par image : pourcentages et libellés préparés, refaits seulement si la langue change.
 */
public final class JetHud {
    private JetHud() {
    }

    private static final ResourceLocation TACZ_HUD = new ResourceLocation("tacz", "tac_gun_hud_overlay");
    private static final ResourceLocation TACZ_CHALEUR = new ResourceLocation("tacz", "tac_heat_bar");
    private static final ResourceLocation GRAS = new ResourceLocation(HeralizWonder.MODID, "ui_bold"),
            MINI = new ResourceLocation(HeralizWonder.MODID, "ui_mini"), MINI_GRAS = new ResourceLocation(HeralizWonder.MODID, "ui_mini_bold");
    private static final int LARGEUR = 94, HAUTEUR = 33, ROUGE = 0xFF4A3D;
    private static final Component[] POURCENTS = new Component[101];
    private static KeyMapping touchePurge;
    private static boolean toucheCherchee;
    private static String langue = "", inviteTouche = "";
    private static Component touche, purger, refroidissement;
    /** « CHALEUR » / « SURCHAUFFE » en capitales espacées : lettres et positions préparées. */
    private static Component[] lettresChaleur, lettresSurchauffe;
    private static float[] xChaleur, xSurchauffe;
    private static float affichee;

    static {
        for (int i = 0; i <= 100; i++) POURCENTS[i] = Component.literal(i + " %").withStyle(st -> st.withFont(GRAS));
    }

    /** TaCZ : compteur de munitions et barre de chaleur masqués tant que le Jet Gun est en main. */
    public static void masquerTacz(RenderGuiOverlayEvent.Pre e) {
        ResourceLocation id = e.getOverlay().id();
        if (!TACZ_HUD.equals(id) && !TACZ_CHALEUR.equals(id)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && JetGun.estJetGun(mc.player.getMainHandItem())) e.setCanceled(true);
    }

    public static void rendu(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ItemStack arme = mc.player.getMainHandItem();
        if (!JetGun.estJetGun(arme)) {
            affichee = 0;
            return;
        }
        float ch = JetGun.chaleur(arme);
        affichee += (ch - affichee) * 0.25f;
        if (Math.abs(ch - affichee) < 0.002f) affichee = ch;
        boolean verrou = JetGun.surchauffe(arme);
        int tick = gui.getGuiTicks();
        Font font = mc.font;
        int pct = Mth.clamp(Math.round(affichee * 100), 0, 100);
        // ---- en bas à droite : carte de verre
        preparer(font);
        int x0 = w - LARGEUR - 8, y0 = h - HAUTEUR - 8;
        verre(g, x0, y0);
        libelle(g, font, verrou ? lettresSurchauffe : lettresChaleur, verrou ? xSurchauffe : xChaleur, x0 + 5, y0 + 6,
                verrou ? 0xFFFF8A80 : 0xFFC9CDD4);
        Component valeur = POURCENTS[pct];
        g.drawString(font, valeur, x0 + LARGEUR - 5 - font.width(valeur), y0 + 2, verrou ? 0xFF000000 | ROUGE : 0xFFFFFFFF, false);
        // barre de 12 segments
        int sx = x0 + 5, sy = y0 + 15, seg = 12;
        for (int k = 0; k < seg; k++) {
            float a = (k + 1) / (float) seg;
            boolean allume = affichee >= a - 0.5f / seg;
            int c = !allume ? 0x24FFFFFF : verrou ? 0xFF000000 | ROUGE : k < 7 ? 0xFFF2F4F6 : k < 10 ? 0xFFFFB030 : 0xFFFF4A26;
            int px = sx + k * 7;
            if (allume && verrou) g.fill(px - 1, sy - 1, px + 6, sy + 3, 0x50FF4A3D);
            g.fill(px, sy, px + 5, sy + 2, c);
        }
        if (verrou) {
            g.drawString(font, refroidissement, x0 + 5, y0 + 21, 0xA6FFFFFF, false);
        } else if (affichee >= JetGun.SEUIL_PURGE) {
            int kw = font.width(touche) + 6;
            g.fill(x0 + 6, y0 + 21, x0 + 4 + kw, y0 + 22, 0x20FFFFFF);
            g.fill(x0 + 5, y0 + 22, x0 + 5 + kw, y0 + 29, 0x20FFFFFF);
            g.fill(x0 + 6, y0 + 29, x0 + 4 + kw, y0 + 30, 0x20FFFFFF);
            g.drawString(font, touche, x0 + 8, y0 + 21, 0xFFFFFFFF, false);
            g.drawString(font, purger, x0 + 5 + kw + 4, y0 + 21, 0xA6FFFFFF, false);
        }
        // ---- arc sous le viseur
        float sp = JetFx.spinLocal();
        if (affichee > 0.02f || sp > 0.05f || verrou) {
            int cx = w / 2, cy = h / 2;
            int n = 21;
            for (int k = 0; k < n; k++) {
                float a = k / (float) (n - 1);
                float ang = Mth.PI * (0.15f + 0.7f * (1 - a));
                int px = cx + Math.round(Mth.cos(ang) * 17), py = cy + 6 + Math.round(Mth.sin(ang) * 12);
                boolean plein = affichee >= a - 0.02f;
                int c = plein ? couleur(a, verrou, tick) : 0x40FFFFFF;
                g.fill(px - 1, py - 1, px + 1, py + 1, c);
            }
        }
    }

    private static int couleur(float a, boolean verrou, int tick) {
        if (verrou) return 0xFF000000 | ROUGE;
        if (a < 0.55f) return 0xFFF2F4F6;
        if (a < 0.8f) return 0xFFFFB030;
        return 0xFFFF4A26;
    }

    /** Libellés et touche de purge, refaits seulement quand la langue ou la touche changent. */
    private static void preparer(Font font) {
        Minecraft mc = Minecraft.getInstance();
        String l = mc.getLanguageManager().getSelected();
        if (!toucheCherchee) {
            toucheCherchee = true;
            for (KeyMapping k : mc.options.keyMappings) {
                if ("key.tacz.reload.desc".equals(k.getName())) {
                    touchePurge = k;
                    break;
                }
            }
        }
        String t = touchePurge == null ? "R" : touchePurge.getTranslatedKeyMessage().getString();
        if (l.equals(langue) && t.equals(inviteTouche) && touche != null) return;
        langue = l;
        inviteTouche = t;
        touche = Component.literal(t).withStyle(st -> st.withFont(MINI_GRAS));
        purger = Component.translatable("heraliz_wonder.jetgun.purger").withStyle(st -> st.withFont(MINI));
        refroidissement = Component.translatable("heraliz_wonder.jetgun.refroidissement").withStyle(st -> st.withFont(MINI));
        String c = Component.translatable("heraliz_wonder.jetgun.chaleur").getString().toUpperCase(java.util.Locale.ROOT);
        String sc = Component.translatable("heraliz_wonder.jetgun.surchauffe").getString().toUpperCase(java.util.Locale.ROOT);
        lettresChaleur = lettres(c);
        lettresSurchauffe = lettres(sc);
        xChaleur = positions(font, lettresChaleur);
        xSurchauffe = positions(font, lettresSurchauffe);
    }

    private static Component[] lettres(String s) {
        int[] cps = s.codePoints().toArray();
        Component[] r = new Component[cps.length];
        for (int i = 0; i < cps.length; i++) r[i] = Component.literal(new String(Character.toChars(cps[i]))).withStyle(st -> st.withFont(MINI_GRAS));
        return r;
    }

    /** Position de chaque lettre (capitales espacées de 1 px). */
    private static float[] positions(Font font, Component[] l) {
        float[] x = new float[l.length];
        float cx = 0;
        for (int i = 0; i < l.length; i++) {
            x[i] = cx;
            cx += font.width(l[i]) + 1.0f;
        }
        return x;
    }

    private static void libelle(GuiGraphics g, Font font, Component[] l, float[] xs, int x, int y, int argb) {
        PoseStack ps = g.pose();
        for (int i = 0; i < l.length; i++) {
            ps.pushPose();
            ps.translate(x + xs[i], y, 0);
            g.drawString(font, l[i], 0, 0, argb, false);
            ps.popPose();
        }
    }

    /** Panneau de verre (fond sombre translucide, contour fin, liseré haut, coins adoucis). */
    private static void verre(GuiGraphics g, int x, int y) {
        int w = LARGEUR, h = HAUTEUR;
        g.fill(x + 1, y, x + w - 1, y + 1, 0xB30A0C10);
        g.fill(x, y + 1, x + w, y + h - 1, 0xB30A0C10);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, 0xB30A0C10);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x1FFFFFFF);
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0x14FFFFFF);
        g.fill(x + 1, y + 2, x + 2, y + h - 2, 0x14FFFFFF);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, 0x14FFFFFF);
    }
}
