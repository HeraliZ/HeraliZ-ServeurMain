package fr.heralisz.economie.client;

import fr.heralisz.economie.client.gui.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * HUD epure du solde. A chaque variation, une petite pastille « verre » apparait en haut a droite pendant 3 s :
 * « +250 hérals · Quête » puis le nouveau total en dessous en attenue, avec un fondu. Si hud_permanent, le total
 * reste affiche en petit (capitales espacees) dans le coin quand la pastille n'est pas la. Rien n'est alloue par
 * image en dehors des composants de texte de la pastille (3 s de temps en temps) ; au repos sans HUD permanent,
 * le rendu sort tout de suite.
 */
public final class SoldeHud {
    private SoldeHud() {
    }

    private static final int MARGE = 8;

    public static void render(GuiGraphics g, int largeur, int hauteur) {
        if (!ClientSolde.connu()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.options.renderDebug || mc.player == null) return;
        Font font = mc.font;
        long age = ClientSolde.ageVariation();
        float alpha = ClientSolde.alphaPastille(age);
        if (alpha > 0 && ClientSolde.delta() != 0) {
            pastille(g, font, largeur, age, alpha);
        } else if (ClientSolde.hudPermanent()) {
            permanent(g, font, largeur);
        }
    }

    /** Pastille : ligne 1 = variation (vert/rouge, gras) · motif (attenue) ; ligne 2 = « solde 1 250 hérals » petit. */
    private static void pastille(GuiGraphics g, Font font, int largeur, long age, float alpha) {
        long delta = ClientSolde.delta();
        String motif = ClientSolde.motifLisible(ClientSolde.motif());
        MutableComponent variation = Ui.lit(ClientSolde.formatSigne(delta), Ui.BOLD);
        MutableComponent sep = motif.isEmpty() ? null : Ui.lit(" · " + motif, Ui.FONT);
        MutableComponent total = Ui.tr(Ui.SMALL, "economie.hud.solde", ClientSolde.format(ClientSolde.solde()));
        int w1 = font.width(variation) + (sep == null ? 0 : font.width(sep));
        int w2 = font.width(total);
        int w = Math.max(w1, w2) + 2 * Ui.ESPACE_L, h = 32;
        int x = largeur - MARGE - w;
        int y = Math.round(MARGE + ClientSolde.glissement(age));
        int a = Math.round(255 * alpha);

        Ui.glass(g, x, y, w, h, alpha);
        // lisere vertical a gauche : vert si credit, rouge si debit
        int couleur = delta >= 0 ? Ui.OK : Ui.DANGER;
        g.fill(x + 1, y + 6, x + 2, y + h - 6, Ui.argb(couleur, Math.round(200 * alpha)));

        float tx = x + Ui.ESPACE_L, ty = y + 7;
        Ui.texte(g, font, variation, tx, ty, couleur, a);
        if (sep != null) Ui.texte(g, font, sep, tx + font.width(variation), ty, Ui.MUTED, a);
        Ui.texte(g, font, total, tx, ty + 13, Ui.MUTED, Math.round(a * 0.85f));
    }

    /** Solde permanent : « 1 250 HÉRALS » en petites capitales espacees, attenue, dans le coin haut droit. */
    private static void permanent(GuiGraphics g, Font font, int largeur) {
        long solde = ClientSolde.solde();
        String chiffres = fr.heralisz.economie.api.EconomieApi.nombre(solde);
        String unite = ClientSolde.unite(solde);
        float wu = Ui.spacedWidth(font, unite.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 0.8f);
        Component c = Ui.lit(chiffres, Ui.BOLD);
        int wc = font.width(c);
        int xRight = largeur - MARGE;
        float y = MARGE;
        Ui.spacedRight(g, font, unite, Ui.SMALL, xRight, y + 1.5f, Ui.argb(Ui.MUTED, 200), 0.8f);
        Ui.texte(g, font, c, xRight - wu - 4 - wc, y, Ui.TEXT, 220);
    }
}
