package fr.heralisz.clans.client.banniere;

import com.mojang.blaze3d.platform.Window;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Calque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Outils communs du rendu des bannieres sur le fil de rendu : un {@link Composeur} et des tampons reutilises (pixels,
 * calques emballes, tampon direct d'envoi), l'emballage d'une {@link Banniere} pour le composeur, et l'echelle
 * physique d'un dessin GUI. Tout ici appartient au fil de rendu (le fil de fond de l'atelier a ses propres tampons).
 */
@OnlyIn(Dist.CLIENT)
public final class Rendu {
    private Rendu() {
    }

    /** Plus grand cote d'une texture de banniere (garde-fou : jamais plus, meme a tres grande echelle). */
    public static final int MAX_PX = 1024;
    public static final Composeur COMPOSEUR = new Composeur();
    private static int[] pixels = new int[0];
    private static final int[] CALQUES = new int[Composeur.MAX_CALQUES * Composeur.CHAMPS];
    private static ByteBuffer tampon = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());

    /** Tableau de pixels ARGB d'au moins {@code n} cases (reutilise). */
    public static int[] pixels(int n) {
        if (pixels.length < n) pixels = new int[n];
        return pixels;
    }

    /** Calques de la banniere, emballes dans le tableau partage du fil de rendu ; rend le nombre de calques. */
    public static int emballer(Banniere b) {
        return emballer(b, CALQUES);
    }

    public static int[] calques() {
        return CALQUES;
    }

    /** Emballe les calques de {@code b} dans {@code dest} ({@value Composeur#CHAMPS} entiers par calque, 12 au plus). */
    public static int emballer(Banniere b, int[] dest) {
        int n = Math.min(Composeur.MAX_CALQUES, Math.min(b.calques.size(), dest.length / Composeur.CHAMPS));
        for (int i = 0; i < n; i++) emballer(b.calques.get(i), dest, i * Composeur.CHAMPS);
        return n;
    }

    public static void emballer(Calque c, int[] d, int o) {
        d[o + Composeur.F_FORME] = c.forme();
        d[o + Composeur.F_CX] = c.cx();
        d[o + Composeur.F_CY] = c.cy();
        d[o + Composeur.F_W] = c.w();
        d[o + Composeur.F_H] = c.h();
        d[o + Composeur.F_ROT] = c.rot();
        d[o + Composeur.F_COULEUR] = c.couleur();
        // epaisseur bornee par la taille (celle que valider() gardera) : l'atelier montre ce qui sera enregistre
        d[o + Composeur.F_CONTOUR] = c.contourEffectif();
        d[o + Composeur.F_COULEUR_CONTOUR] = c.couleurContour();
        d[o + Composeur.F_MIROIR_X] = c.miroirX() ? 1 : 0;
        d[o + Composeur.F_MIROIR_Y] = c.miroirY() ? 1 : 0;
    }

    /** Copie {@code n} pixels ARGB dans le tampon direct partage (ABGR, ordre natif), pret pour l'envoi GL. */
    public static ByteBuffer versTampon(int[] argb, int n) {
        tampon = convertir(argb, n, tampon);
        return tampon;
    }

    /** Convertit dans {@code dest} (agrandi si besoin, rendu) ; utilisable par un autre fil avec son propre tampon. */
    public static ByteBuffer convertir(int[] argb, int n, ByteBuffer dest) {
        if (dest == null || dest.capacity() < n * 4) dest = ByteBuffer.allocateDirect(Math.max(n * 4, 4)).order(ByteOrder.nativeOrder());
        IntBuffer ib = dest.asIntBuffer();
        for (int i = 0; i < n; i++) ib.put(i, Composeur.versAbgr(argb[i]));
        return dest;
    }

    /** Pixels physiques par unite GUI pour ce dessin (echelle de la fenetre x echelle de la pile de matrices). */
    public static float echelle(GuiGraphics g) {
        Window w = Minecraft.getInstance().getWindow();
        Matrix4f m = g.pose().last().pose();
        float pose = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        if (!(pose > 0.01f)) pose = 1f;
        return (float) w.getGuiScale() * pose;
    }

    /** Taille physique (px) d'une longueur GUI, bornee a [1, {@value #MAX_PX}]. */
    public static int px(float gui, float echelle) {
        return Math.max(1, Math.min(MAX_PX, Math.round(gui * echelle)));
    }
}
