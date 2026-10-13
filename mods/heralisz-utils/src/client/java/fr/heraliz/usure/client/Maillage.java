package fr.heraliz.usure.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Maillage de quadrilateres colores en coordonnees d'interface flottantes, pour le HUD : {@code colonnes} points le long
 * d'un tour (anneau, contour arrondi ; la derniere colonne reprend la premiere), sur {@code lignes} rangs du dedans vers
 * le dehors, une couleur ARGB par sommet (le jeu passe d'une couleur a l'autre en douceur). Les bords sont adoucis par
 * deux rangs ecartes d'un pixel d'ecran ou l'opacite tombe a zero : net a toute echelle d'interface (un bord tombe sur
 * une limite de pixel reste franc), sans scintillement. Rempli seulement quand ce qu'il montre change
 * ({@link FormesHud}) ; une image ne fait que l'envoyer, sans rien allouer.
 */
final class Maillage {
    private float[] x = new float[0], y = new float[0];
    private int[] argb = new int[0];
    private int colonnes, lignes;

    /** Prepare {@code colonnes} x {@code lignes} sommets (tableaux reutilises). */
    void taille(int colonnes, int lignes) {
        this.colonnes = colonnes;
        this.lignes = lignes;
        int n = colonnes * lignes;
        if (x.length < n) {
            x = new float[n];
            y = new float[n];
            argb = new int[n];
        }
    }

    void sommet(int colonne, int ligne, float sx, float sy, int couleur) {
        int k = colonne * lignes + ligne;
        x[k] = sx;
        y[k] = sy;
        argb[k] = couleur;
    }

    /** Dessine le maillage decale de (ox, oy), comme un {@code fill} du jeu (meme type de rendu, meme ordre des coins). */
    void dessiner(GuiGraphics g, float ox, float oy) {
        if (colonnes < 2 || lignes < 2) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i + 1 < colonnes; i++) {
            for (int j = 0; j + 1 < lignes; j++) {
                int a = i * lignes + j, b = a + 1, c = a + lignes + 1, d = a + lignes;
                if (((argb[a] | argb[b] | argb[c] | argb[d]) >>> 24) == 0) continue;
                // le tour va dans le sens horaire, les rangs vers le dehors : dehors puis dedans de la colonne i, dedans
                // puis dehors de la colonne i + 1 (= haut-gauche, bas-gauche, bas-droit, haut-droit de GuiGraphics.fill,
                // face visible pour le jeu)
                v(vc, m, ox, oy, b);
                v(vc, m, ox, oy, a);
                v(vc, m, ox, oy, d);
                v(vc, m, ox, oy, c);
            }
        }
        g.flush();
    }

    private void v(VertexConsumer vc, Matrix4f m, float ox, float oy, int k) {
        int c = argb[k];
        vc.vertex(m, ox + x[k], oy + y[k], 0).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
    }
}
