package com.heraliz.extraction.client;

import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.item.ExtractionTerminalItem;
import com.heraliz.extraction.reseau.Reseau;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.jetbrains.annotations.Nullable;

/**
 * Chrono d'extraction en haut de l'écran (maquette validée « petites jauges », à la place de l'ancienne barre de boss) :
 * une carte de verre avec un point vert, « EXTRACTION » et le temps qui reste à tenir, une barre verte, puis l'aide
 * (« Reste dans la zone, à ciel ouvert ») et le temps avant la fermeture de la zone. Hors de la zone (tolérance au bord) :
 * point rouge et « Reviens dans la zone ! ». Le serveur envoie le chrono toutes les 4 ticks ({@link Reseau.Progression}) ;
 * entre deux, il avance tout seul.
 */
public final class HudExtraction {
    private HudExtraction() {
    }

    private static final ResourceLocation GRAS = new ResourceLocation(HeralizExtraction.MODID, "ui_bold"),
            MINI = new ResourceLocation(HeralizExtraction.MODID, "ui_mini"), MINI_GRAS = new ResourceLocation(HeralizExtraction.MODID, "ui_mini_bold");
    private static final int VERT = 0x5FD08A, VERT_FONCE = 0x2E8A52, ROUGE = 0xFF4A3D;
    private static final int HAUTEUR = 34;

    /** Dernier chrono reçu (null : aucun) et l'heure du jeu à sa réception. */
    @Nullable
    private static Reseau.Progression dernier;
    private static long recu;
    private static float apparition;
    private static long derniereImage;

    /** Chrono reçu du serveur ({@link Reseau#PROGRESSION_CLIENT}). */
    public static void recevoir(Reseau.Progression p) {
        Minecraft mc = Minecraft.getInstance();
        recu = mc.level == null ? 0 : mc.level.getGameTime();
        if (p.aTenir() > 0) {
            dernier = p;
        } else {
            recu = Long.MIN_VALUE; // fin : la carte s'efface (la dernière valeur reste affichée pendant le fondu)
        }
    }

    /** Déconnexion : plus rien à afficher. */
    public static void vider() {
        dernier = null;
        apparition = 0;
    }

    public static void rendu(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Reseau.Progression p = dernier;
        long nanos = System.nanoTime();
        float dt = derniereImage == 0 ? 0 : Math.min(0.1F, (nanos - derniereImage) / 1e9F);
        derniereImage = nanos;
        if (p == null || mc.level == null || mc.player == null) {
            apparition = 0;
            return;
        }
        long maintenant = mc.level.getGameTime();
        // sans nouvelles depuis 2 s (zone déchargée, paquet perdu) : on efface aussi
        boolean actif = recu != Long.MIN_VALUE && maintenant - recu < 40;
        apparition = Mth.clamp(apparition + (actif ? dt * 4 : -dt * 4), 0, 1);
        if (apparition <= 0) {
            if (!actif) dernier = null;
            return;
        }
        if (mc.options.hideGui) return;
        float ecoule = actif ? Math.max(0, maintenant - recu + partial) : 0;
        float tenus = Math.min(p.aTenir(), p.tenus() + (p.dedans() ? ecoule : 0));
        float f = tenus / p.aTenir();
        int reste = Mth.ceil((p.aTenir() - tenus) / 20.0F);
        int zone = p.zone() < 0 ? -1 : Math.max(0, (int) ((p.zone() - ecoule) / 20));

        float ease = 1 - (1 - apparition) * (1 - apparition);
        int a = Math.round(255 * ease);
        Font font = mc.font;
        // largeur : celle de l'aide dans la zone et du temps de la zone (pas de saut quand on passe au bord)
        MutableComponent aideZone = lit(Component.translatable(p.cielOuvert() ? "hud.heraliz_extraction.reste_ciel" : "hud.heraliz_extraction.reste").getString(), MINI);
        MutableComponent z = zone < 0 ? null : lit(Component.translatable("hud.heraliz_extraction.zone", ExtractionTerminalItem.formatTime(zone)).getString(), MINI);
        int lw = Mth.clamp(14 + font.width(aideZone) + (z == null ? 0 : 12 + font.width(z)), 154, Math.max(154, w - 24));
        int x0 = (w - lw) / 2, y0 = Math.round(12 - (1 - ease) * 10);
        verre(g, x0, y0, lw, HAUTEUR, a);
        // point : vert (il pulse doucement) dans la zone, rouge au bord
        int point = p.dedans() ? VERT : ROUGE;
        float pulse = p.dedans() ? 0.6F + 0.4F * Mth.sin((maintenant + partial) * 0.25F) : 1;
        g.fill(x0 + 6, y0 + 8, x0 + 11, y0 + 13, argb(point, Math.round(60 * pulse * ease)));
        g.fill(x0 + 7, y0 + 9, x0 + 10, y0 + 12, argb(point, a));
        // barre
        int bx = x0 + 7, bw = lw - 14, by = y0 + 17, fw = Math.round(bw * Mth.clamp(f, 0, 1));
        g.fill(bx, by, bx + bw, by + 2, argb(0xFFFFFF, 31 * a / 255));
        if (fw > 0) {
            g.fill(bx, by - 1, bx + fw, by + 3, argb(VERT, 40 * a / 255));
            degrade(g, bx, by, bx + fw, by + 2, argb(VERT_FONCE, a), argb(VERT, a));
        }
        if (a < 4) return;
        // textes
        caps(g, font, Component.translatable("hud.heraliz_extraction.titre").getString(), MINI_GRAS, x0 + 14, y0 + 7, 1.2F, argb(0xFFFFFF, a));
        MutableComponent temps = lit(ExtractionTerminalItem.formatTime(reste), GRAS);
        g.pose().pushPose();
        g.pose().translate(x0 + lw - 7 - font.width(temps) * 1.1F, y0 + 3, 0);
        g.pose().scale(1.1F, 1.1F, 1);
        g.drawString(font, temps, 0, 0, argb(0xFFFFFF, a), false);
        g.pose().popPose();
        if (p.dedans()) {
            g.drawString(font, aideZone, x0 + 7, y0 + 23, argb(0xFFFFFF, 179 * a / 255), false);
        } else {
            g.drawString(font, lit(Component.translatable("hud.heraliz_extraction.reviens").getString(), MINI), x0 + 7, y0 + 23, argb(0xFFB3AC, a), false);
        }
        if (z != null) g.drawString(font, z, x0 + lw - 7 - font.width(z), y0 + 23, argb(0xFFFFFF, 179 * a / 255), false);
    }

    // ------------------------------------------------------------------ dessin

    private static int argb(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static MutableComponent lit(String s, ResourceLocation font) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    /** Capitales espacées (x = bord gauche). */
    private static void caps(GuiGraphics g, Font font, String s, ResourceLocation f, float x, float y, float espace, int argb) {
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            MutableComponent c = lit(new String(Character.toChars(up.codePointAt(i))), f);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.drawString(font, c, 0, 0, argb, false);
            g.pose().popPose();
            cx += font.width(c) + espace;
        }
    }

    /** Panneau de verre : fond sombre translucide, contour fin, liseré haut, coins adoucis. */
    private static void verre(GuiGraphics g, int x, int y, int w, int h, int a) {
        int fond = argb(0x0A0C10, 179 * a / 255), bord = argb(0xFFFFFF, 20 * a / 255);
        g.fill(x + 1, y, x + w - 1, y + 1, fond);
        g.fill(x, y + 1, x + w, y + h - 1, fond);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, fond);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, argb(0xFFFFFF, 31 * a / 255));
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, bord);
        g.fill(x + 1, y + 2, x + 2, y + h - 2, bord);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, bord);
    }

    /** Rectangle en dégradé horizontal (gauche -> droite). */
    private static void degrade(GuiGraphics g, int x0, int y0, int x1, int y1, int gauche, int droite) {
        org.joml.Matrix4f m = g.pose().last().pose();
        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        vc.vertex(m, x0, y0, 0).color(gauche >> 16 & 0xFF, gauche >> 8 & 0xFF, gauche & 0xFF, gauche >>> 24).endVertex();
        vc.vertex(m, x0, y1, 0).color(gauche >> 16 & 0xFF, gauche >> 8 & 0xFF, gauche & 0xFF, gauche >>> 24).endVertex();
        vc.vertex(m, x1, y1, 0).color(droite >> 16 & 0xFF, droite >> 8 & 0xFF, droite & 0xFF, droite >>> 24).endVertex();
        vc.vertex(m, x1, y0, 0).color(droite >> 16 & 0xFF, droite >> 8 & 0xFF, droite & 0xFF, droite >>> 24).endVertex();
        g.flush();
    }
}
