package fr.heraliz.interfaces.kit;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.interfaces.HeralizInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Logos officiels HeraliZ detoures (tools/logo.py) : banniere « HeraliZ » (1024 et 512 px de large) et logo carre
 * (256 et 128 px). La texture la plus proche de la taille affichee en pixels reels est choisie (filtrage lineaire
 * par .png.mcmeta) : net a toutes les echelles d'interface.
 */
public final class Logo {
    private Logo() {
    }

    private static final ResourceLocation BANNIERE_1024 = HeralizInterface.id("textures/gui/logo_banniere_1024.png");
    private static final ResourceLocation BANNIERE_512 = HeralizInterface.id("textures/gui/logo_banniere_512.png");
    private static final ResourceLocation CARRE_256 = HeralizInterface.id("textures/gui/logo_carre_256.png");
    private static final ResourceLocation CARRE_128 = HeralizInterface.id("textures/gui/logo_carre_128.png");
    /** Proportions (hauteur / largeur) des textures. */
    public static final float RATIO_BANNIERE = 344f / 1024f, RATIO_CARRE = 249f / 256f;

    /** Hauteur affichee de la banniere pour une largeur donnee. */
    public static float hauteurBanniere(float largeur) {
        return largeur * RATIO_BANNIERE;
    }

    /** Banniere de {@code largeur} unites d'interface, coin haut gauche en (x, y), opacite 0..1. */
    public static void banniere(GuiGraphics g, float x, float y, float largeur, float alpha) {
        double px = largeur * Minecraft.getInstance().getWindow().getGuiScale();
        if (px > 640) dessiner(g, BANNIERE_1024, 1024, 344, x, y, largeur, alpha);
        else dessiner(g, BANNIERE_512, 512, 172, x, y, largeur, alpha);
    }

    /** Logo carre de {@code largeur} unites. */
    public static void carre(GuiGraphics g, float x, float y, float largeur, float alpha) {
        double px = largeur * Minecraft.getInstance().getWindow().getGuiScale();
        if (px > 160) dessiner(g, CARRE_256, 256, 249, x, y, largeur, alpha);
        else dessiner(g, CARRE_128, 128, 124, x, y, largeur, alpha);
    }

    private static void dessiner(GuiGraphics g, ResourceLocation tex, int tw, int th, float x, float y, float largeur, float alpha) {
        if (alpha <= 0.01f) return;
        // coin pose sur un pixel reel (pas de demi-pixel flou)
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ax = (float) (Math.round(x * s) / s), ay = (float) (Math.round(y * s) / s);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.pose().pushPose();
        g.pose().translate(ax, ay, 0);
        float k = largeur / tw;
        g.pose().scale(k, k, 1f);
        g.blit(tex, 0, 0, 0f, 0f, tw, th, tw, th);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }
}
