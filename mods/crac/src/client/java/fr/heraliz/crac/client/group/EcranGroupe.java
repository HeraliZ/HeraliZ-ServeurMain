package fr.heraliz.crac.client.group;

import fr.heraliz.crac.client.kit.EcranCanevas;
import fr.heraliz.crac.client.kit.Ui;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Base des écrans de groupe (maquette validée) : le monde assombri derrière un panneau de verre centré, un filet de
 * couleur en haut, un en-tête en capitales, « Échap fermer » à droite. Les lignes de joueurs, les champs de saisie et
 * les petits boutons sont dessinés ici pour tous les écrans du groupe.
 */
public abstract class EcranGroupe extends EcranCanevas {
    /** Rectangle du panneau. */
    protected float x0, y0, pw, ph;

    protected EcranGroupe(Component titre) {
        super(titre);
    }

    /** Place le panneau (w x h) au centre du canevas. */
    protected void placer(float w, float h) {
        this.pw = w;
        this.ph = h;
        this.x0 = Math.round((this.cw - w) / 2);
        this.y0 = Math.round((this.ch - h) / 2);
    }

    /** Le monde reste visible, assombri, plus sombre en haut et en bas. */
    protected void fond(GuiGraphics g) {
        rect(g, 0, 0, this.cw, this.ch, a(NUIT, 150));
        degradeV(g, 0, 0, this.cw, this.ch * 0.35F, a(0, 70), a(0, 0));
        degradeV(g, 0, this.ch * 0.65F, this.cw, this.ch, a(0, 0), a(0, 90));
    }

    /** Panneau de verre et filet de couleur en haut (qui s'efface aux deux bouts). */
    protected void panneau(GuiGraphics g, int couleur) {
        degradeV(g, this.x0, this.y0 + this.ph, this.x0 + this.pw, this.y0 + this.ph + 8, a(0, 70), a(0, 0));
        verre(g, this.x0, this.y0, this.pw, this.ph, this.px, 214);
        float mid = this.x0 + this.pw / 2;
        degradeH(g, this.x0 + 13.33F, this.y0, mid, this.y0 + 2 * this.px, a(couleur, 0), a(couleur, 255));
        degradeH(g, mid, this.y0, this.x0 + this.pw - 13.33F, this.y0 + 2 * this.px, a(couleur, 255), a(couleur, 0));
    }

    /**
     * En-tête : pastille de couleur (si {@code pastille} >= 0), titre en capitales, sous-titre, et « Échap fermer » à
     * droite. Rend le bas de l'en-tête.
     */
    protected float entete(GuiGraphics g, int pastille, String titre, int couleurTitre, String sousTitre) {
        float x = this.x0 + 13.33F;
        if (pastille >= 0) {
            rect(g, x, this.y0 + 11.33F, x + 15.33F, this.y0 + 26.67F, a(pastille, 255));
            contour(g, x, this.y0 + 11.33F, 15.33F, 15.33F, this.px, a(BLANC, 89));
            x += 15.33F + 5.33F;
        }
        String fermer = Component.translatable("crac.gui.esc_close").getString();
        float fw = largeurCaps(this.font, fermer, MINI, 0.69F);
        caps(g, this.font, fermer, MINI, this.x0 + this.pw - 13.33F - fw, this.y0 + 21.33F - 4.21F, 0.69F, a(ATTENUE, 255));
        float place = this.x0 + this.pw - 13.33F - fw - 8 - x;
        float echelle = 0.93F;
        float tw = largeurCaps(this.font, titre, TITRE, 1.12F / echelle) * echelle;
        if (tw > place) {
            echelle *= place / tw;
        }
        caps(g, this.font, titre, TITRE, x, this.y0 + 14 - 3.91F * echelle, echelle, 1.12F, a(couleurTitre, 255));
        if (!sousTitre.isEmpty()) {
            String s = Ui.ellipsis(this.font, sousTitre, MINI, Math.round(place));
            texte(g, this.font, lit(s, MINI), x, this.y0 + 28.17F - 4.21F, a(ATTENUE, 255));
        }
        return this.y0 + 34;
    }

    /** Tête du joueur (carré de côté s) avec un contour fin. */
    protected void tete(GuiGraphics g, UUID id, float x, float y, float s) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s / 8.0F, s / 8.0F, 1.0F);
        PlayerFaceRenderer.draw(g, GroupManageScreen.skinFor(id), 0, 0, 8);
        g.pose().popPose();
        contour(g, x - this.px, y - this.px, s + 2 * this.px, s + 2 * this.px, this.px, a(BLANC, 36));
    }

    /** Fond d'une ligne survolée de la liste. */
    protected void ligneSurvol(GuiGraphics g, float x, float y, float w, float h) {
        rect(g, x, y, x + w, y + h, a(BLANC, 13));
        contour(g, x, y, w, h, this.px, a(BLANC, 26));
    }

    /** Petit bouton en capitales (maquette « petitBouton ») ; {@code rgb} : couleur du texte et du contour (-1 : neutre). */
    protected void petitBouton(GuiGraphics g, float x, float y, float w, float h, String texte, int rgb, boolean survol, boolean etoile) {
        if (rgb < 0) {
            bouton(g, x, y, w, h, false, survol ? 1 : 0, this.px);
        } else {
            boutonTeinte(g, x, y, w, h, rgb, survol ? 1 : 0, this.px);
        }
        int couleur = a(rgb < 0 ? (survol ? BLANC : BOUTON) : mix(rgb, BLANC, survol ? 0.35F : 0.15F), 255);
        float tw = largeurCaps(this.font, texte, MINI_GRAS, 0.52F);
        float icone = etoile ? 4.67F : 0;
        float tx = x + (w - tw - icone) / 2 + icone;
        if (etoile) {
            etoile(g, tx - 2.67F, y + h / 2, 1.8F, couleur);
        }
        caps(g, this.font, texte, MINI_GRAS, tx, y + h / 2 - 4.21F, 0.52F, couleur);
    }

    protected float largeurPetitBouton(String texte, boolean etoile) {
        return largeurCaps(this.font, texte, MINI_GRAS, 0.52F) + 9.33F + (etoile ? 4.67F : 0);
    }

    /** Grand bouton en capitales ; {@code rgb} : couleur (-1 : neutre, -2 : principal rouge). */
    protected void grandBouton(GuiGraphics g, float x, float y, float w, float h, String texte, int rgb, boolean survol, boolean actif) {
        if (rgb == -2) {
            bouton(g, x, y, w, h, true, actif && survol ? 1 : 0, this.px);
        } else if (rgb < 0) {
            bouton(g, x, y, w, h, false, actif && survol ? 1 : 0, this.px);
        } else {
            boutonTeinte(g, x, y, w, h, rgb, actif && survol ? 1 : 0, this.px);
        }
        int couleur = !actif ? a(PALE, 255) : rgb >= 0 ? a(mix(rgb, BLANC, survol ? 0.35F : 0.15F), 255) : a(rgb == -2 || survol ? BLANC : BOUTON, 255);
        capsCentre(g, this.font, texte, MINI_GRAS, x + w / 2, y + h / 2 - 4.21F, 0.8F, couleur);
    }

    protected float largeurGrandBouton(String texte) {
        return largeurCaps(this.font, texte, MINI_GRAS, 0.8F) + 17.33F;
    }

    /** Champ de saisie : verre clair, contour à l'accent quand il a la main, curseur clignotant. */
    protected void champ(GuiGraphics g, float x, float y, float w, float h, String texte, String vide, boolean focus) {
        rect(g, x, y, x + w, y + h, a(BLANC, focus ? 15 : 10));
        contour(g, x, y, w, h, this.px, focus ? a(ACCENT, 170) : a(BLANC, 36));
        // un texte long défile : on garde la fin (où est le curseur) dans le champ
        String visible = texte;
        while (!visible.isEmpty() && largeur(this.font, visible + "_", MOYEN) > w - 9) {
            visible = visible.substring(1);
        }
        float ty = y + h / 2 - 4.7F;
        if (texte.isEmpty() && !focus) {
            texte(g, this.font, lit(vide, MOYEN), x + 4.67F, ty, a(PALE, 255));
            return;
        }
        if (texte.isEmpty()) {
            texte(g, this.font, lit(vide, MOYEN), x + 4.67F, ty, a(PALE, 255));
        } else {
            texte(g, this.font, lit(visible, MOYEN), x + 4.67F, ty, a(TEXTE, 255));
        }
        if (focus && System.currentTimeMillis() / 500L % 2L == 0L) {
            float cx = x + 4.67F + (texte.isEmpty() ? 0 : largeur(this.font, visible, MOYEN)) + 0.5F;
            rect(g, cx, y + h / 2 - 3.33F, cx + 0.67F, y + h / 2 + 3.33F, a(BLANC, 220));
        }
    }

    /** Libellé de champ en petites capitales. */
    protected void libelle(GuiGraphics g, String texte, float x, float capsTop) {
        caps(g, this.font, texte, MINI, x, capsTop - 2.46F, 0.8F, a(ATTENUE, 255));
    }

    /** Titre de section : capitales espacées et un filet qui s'efface vers la droite. */
    protected void section(GuiGraphics g, String texte, float x, float capsTop, float largeurTotale) {
        float w = caps(g, this.font, texte, MINI, x, capsTop - 2.46F, 0.8F, a(ATTENUE, 255));
        degradeH(g, x + w + 4.67F, capsTop + 1.75F, x + largeurTotale, capsTop + 1.75F + this.px, a(BLANC, 41), a(BLANC, 0));
    }
}
