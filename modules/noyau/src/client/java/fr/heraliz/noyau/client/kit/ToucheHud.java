package fr.heraliz.noyau.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.settings.KeyModifier;
import org.joml.Matrix4f;

/**
 * Touche dans un HUD (refonte du 25/10, docs/HUD_REFONTE.md) : petit cadre clair de {@link #HAUTEUR} unités, nom de la
 * touche réglée par le joueur en DM Sans gras 6,5 ({@link PoliceHud#MINI_GRAS}), marges de {@link #PAD}. Le nom n'est
 * relu que si la touche, son modificateur ou la langue changent (aucune allocation par image). Remplissage optionnel
 * (touche « maintenir » : abandon, exécution). Mêmes mesures dans {@code apercu_hud.py}.
 */
public final class ToucheHud {
    /** Hauteur du cadre et marge de chaque côté du nom. */
    public static final float HAUTEUR = 9f, PAD = 3f;
    /** Opacités : fond, contour, texte. */
    private static final int FOND_ALPHA = 30, BORD_ALPHA = 70;

    private final KeyMapping touche;
    private final TexteHud nom = new TexteHud(PoliceHud.MINI_GRAS);
    private InputConstants.Key cle;
    private KeyModifier modificateur;
    private String langue;

    public ToucheHud(KeyMapping touche) {
        this.touche = touche;
    }

    /** Relit le nom de la touche si elle a changé (ou la langue). */
    public ToucheHud maj() {
        String l = Minecraft.getInstance().getLanguageManager().getSelected();
        InputConstants.Key k = touche.getKey();
        KeyModifier mod = touche.getKeyModifier();
        if (k != cle || mod != modificateur || !l.equals(langue)) {
            cle = k;
            modificateur = mod;
            langue = l;
            nom.texte(touche.getTranslatedKeyMessage().getString());
        }
        return this;
    }

    public float largeur() {
        return nom.largeur() + 2f * PAD;
    }

    /** Cadre de la touche (formes, sans vidage) ; {@code remplissage} 0..1 de la couleur {@code rgb} (maintien). */
    public void fond(VertexConsumer vc, Matrix4f m, float x, float cy, float remplissage, int rgb, float opacite) {
        float y = cy - HAUTEUR / 2f, w = largeur();
        Formes.rect(vc, m, x, y, x + w, y + HAUTEUR, Palette.argb(0xFFFFFF, Math.round(FOND_ALPHA * opacite)));
        if (remplissage > 0f) {
            Formes.rect(vc, m, x, y, x + w * Math.min(1f, remplissage), y + HAUTEUR, Palette.argb(rgb, Math.round(200 * opacite)));
        }
        Formes.bord(vc, m, x, y, w, HAUTEUR, Formes.pixel(), Palette.argb(0xFFFFFF, Math.round(BORD_ALPHA * opacite)));
    }

    /** Nom de la touche (texte, sans vidage). */
    public void texte(GuiGraphics g, float x, float cy, float opacite) {
        nom.ajouter(g, x + PAD, cy, Palette.argb(0xFFFFFF, opacite), TexteHud.GAUCHE, false);
    }
}
