package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.InterfaceConfig;
import fr.heraliz.interfaces.kit.Anim;
import fr.heraliz.interfaces.kit.Echelle;
import fr.heraliz.interfaces.kit.Fond;
import fr.heraliz.interfaces.kit.HzIcon;
import fr.heraliz.interfaces.kit.HzScroll;
import fr.heraliz.interfaces.kit.Icons;
import fr.heraliz.interfaces.kit.Logo;
import fr.heraliz.interfaces.kit.Onglets;
import fr.heraliz.interfaces.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ecran Informations (depuis le menu Echap) au style des clans : panneau de verre min(560, l-32) x min(320, h-24),
 * en-tete (logo HeraliZ, titre, phrase d'accueil, croix), onglets HISTOIRE · COMMANDES · REGLES, contenu defilant avec
 * marge 16. Le texte vient de config/heraliz_interface/informations.json ({@link Informations}), relu a chaque
 * ouverture. Echap ou la croix ramenent au menu Echap.
 */
public class EcranInformations extends Screen {
    private static final int ENTETE = 42, BARRE = 18;
    private static int dernierOnglet;
    private final Screen parent;
    private Informations.Contenu contenu;
    private int px, py, pw, ph;
    private int onglet = dernierOnglet;
    private long ouvert = -1;
    @Nullable
    private Zone zone;

    public EcranInformations(Screen parent) {
        super(Component.translatable("gui.heraliz_interface.info.titre"));
        this.parent = parent;
    }

    /** Banc d'essai : change d'onglet. */
    public void onglet(int i) {
        onglet = Math.max(0, Math.min(2, i));
        dernierOnglet = onglet;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (ouvert < 0) {
            ouvert = Util.getMillis();
            contenu = Informations.charger();
        }
        if (InterfaceConfig.oui(InterfaceConfig.ECHELLE_AUTO, true)) Echelle.forcer(this);
        pw = Math.min(560, width - 32);
        ph = Math.min(320, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int accent = Ui.accent();
        addRenderableWidget(new HzIcon(px + pw - 24, py + 12, 12, Icons.CROIX, Component.translatable("gui.heraliz_interface.info.fermer"), this::onClose));
        addRenderableWidget(new Onglets(px + Ui.MARGE, py + ENTETE + 6,
                List.of(Component.translatable("gui.heraliz_interface.info.onglet.histoire"), Component.translatable("gui.heraliz_interface.info.onglet.commandes"),
                        Component.translatable("gui.heraliz_interface.info.onglet.regles")),
                List.of(Icons.LIVRE, Icons.TERMINAL, Icons.BOUCLIER), onglet, accent, this::onglet));
        int cy = py + ENTETE + BARRE + 12;
        zone = new Zone(px + Ui.MARGE, cy, pw - 2 * Ui.MARGE, py + ph - 12 - cy, onglet);
        zone.accent(accent);
        addRenderableWidget(zone);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        try {
            float t = Anim.sortie(Anim.progression(ouvert, 0, 220));
            Fond.dessiner(g, width, height, parent instanceof EcranPause ? 1f : t);
            Fond.lueur(g, width / 2f, py + ph * 0.55f, pw * 0.6f, ph * 0.62f, 0x000000, Math.round(90 * t));
            Ui.glass(g, px, py, pw, ph, Math.round(Ui.VERRE_ALPHA * t));
            Ui.hairline(g, px + 24, px + pw - 24, py, Ui.accent(), Math.round(160 * t), true, true);
            // en-tete
            // logo officiel (banniere) a gauche, titre a sa droite apres un filet vertical
            Logo.banniere(g, px + Ui.MARGE, py + 9, 72, t);
            int tx = px + Ui.MARGE + 72 + 14;
            g.fill(tx - 8, py + 10, tx - 7, py + 33, Ui.argb(0xFFFFFF, Math.round(30 * t)));
            Ui.spaced(g, font, title.getString(), Ui.BOLD, tx, py + 11, Ui.argb(Ui.TEXT, Math.round(255 * t)), 1.6f);
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.heraliz_interface.info.sous_titre"), tx, py + 25, Ui.argb(Ui.MUTED, Math.round(255 * t)), false);
            Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE, 0xFFFFFF, 40, true, true);
            Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE + BARRE + 4, 0xFFFFFF, 22, true, true);
            super.render(g, mouseX, mouseY, partialTick);
        } catch (Throwable ex) {
            fr.heraliz.interfaces.HeralizInterface.LOG.error("[interface] ecran Informations en panne", ex);
            Minecraft.getInstance().setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    // ------------------------------------------------------------------ contenu defilant
    /** Contenu d'un onglet, dessine a la main en coordonnees de contenu (mise en page calculee une fois). */
    private final class Zone extends HzScroll {
        private final int mode;
        private final Font f = Minecraft.getInstance().font;
        /** Histoire : colonne des reperes et paragraphes coupes en lignes. */
        private static final int COL_REPERES = 124, INTER = 11;
        private final List<List<String>> paragraphes = new ArrayList<>();
        private final List<List<String>> regles = new ArrayList<>();
        private final List<String> note = new ArrayList<>();
        private int colCommande;

        Zone(int x, int y, int w, int h, int mode) {
            super(x, y, w, h);
            this.mode = mode;
            int cw = w - 6;
            int haut = 0;
            if (mode == 0) {
                int tuiles = contenu.chiffres().isEmpty() ? 0 : 40 + 16;
                int largeur = cw - COL_REPERES - 18;
                int hy = tuiles + 16;
                for (String p : contenu.histoire()) {
                    List<String> l = Ui.lignes(f, p, Ui.FONT, largeur);
                    paragraphes.add(l);
                    hy += l.size() * INTER + 7;
                }
                int ry = tuiles + 16 + contenu.reperes().size() * 32;
                haut = Math.max(hy, ry);
            } else if (mode == 1) {
                int maxCmd = 0;
                for (Informations.Commande c : contenu.commandes()) {
                    int wc = f.width(Ui.lit(c.commande(), Ui.BOLD)) + 16;
                    if (!c.touche().isEmpty()) wc += Ui.chipWidth(f, Component.translatable("gui.heraliz_interface.info.touche", c.touche()).getString()) + 6;
                    maxCmd = Math.max(maxCmd, wc);
                }
                colCommande = Math.max(130, maxCmd + 18);
                haut = 16 + contenu.commandes().size() * 24;
            } else {
                int hy = 16;
                for (String r : contenu.regles()) {
                    List<String> l = Ui.lignes(f, r, Ui.FONT, cw - 40);
                    regles.add(l);
                    hy += Math.max(16, l.size() * INTER + 3) + 9;
                }
                if (!contenu.noteRegles().isEmpty()) {
                    note.addAll(Ui.lignes(f, contenu.noteRegles(), Ui.SMALL, cw - 8));
                    hy += 6 + note.size() * 10;
                }
                haut = hy;
            }
            setContentHeight(haut);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), cw = getWidth() - 6;
            int accent = Ui.accent();
            switch (mode) {
                case 0 -> histoire(g, x, y, cw, accent);
                case 1 -> commandes(g, x, y, cw, mouseX, mouseY, accent);
                default -> regles(g, x, y, cw, accent);
            }
        }

        private void histoire(GuiGraphics g, int x, int y, int cw, int accent) {
            int n = contenu.chiffres().size();
            if (n > 0) {
                int tw = (cw - (n - 1) * 8) / n;
                for (int i = 0; i < n; i++) {
                    Informations.Chiffre c = contenu.chiffres().get(i);
                    int tx = x + i * (tw + 8);
                    Ui.tile(g, f, tx, y, tw, 40, Ui.lit(c.valeur(), Ui.BOLD), c.legende(), 255);
                    g.fill(tx + 1, y + 9, tx + 3, y + 21, Ui.argb(accent, i == 0 ? 230 : 120));
                }
                y += 40 + 16;
            }
            // reperes : frise verticale
            Ui.section(g, f, Component.translatable("gui.heraliz_interface.info.reperes").getString(), x, y, COL_REPERES - 8);
            int ry = y + 16;
            int nr = contenu.reperes().size();
            if (nr > 1) g.fill(x + 3, ry + 4, x + 4, ry + (nr - 1) * 32 + 4, Ui.argb(0xFFFFFF, 34));
            for (int i = 0; i < nr; i++) {
                Informations.Repere r = contenu.reperes().get(i);
                boolean dernier = i == nr - 1;
                int c = dernier ? accent : Ui.MUTED;
                Ui.point(g, x + 1, ry + 2, c, 255);
                if (dernier) Ui.softOutline(g, x + 1, ry + 2, 5, 5, Ui.argb(accent, 90));
                g.drawString(f, Ui.lit(r.annee(), Ui.BOLD), x + 14, ry, Ui.argb(dernier ? Ui.TEXT : Ui.mix(Ui.TEXT, Ui.MUTED, 0.3f), 255), false);
                g.drawString(f, Ui.lit(Ui.ellipsis(f, r.titre(), Ui.SMALL, COL_REPERES - 18), Ui.SMALL), x + 14, ry + 12, Ui.argb(Ui.MUTED, 255), false);
                ry += 32;
            }
            // recit
            int rx = x + COL_REPERES + 18, rw = cw - COL_REPERES - 18;
            Ui.section(g, f, Component.translatable("gui.heraliz_interface.info.recit").getString(), rx, y, rw);
            int py2 = y + 16;
            for (int p = 0; p < paragraphes.size(); p++) {
                int couleur = p == 0 ? Ui.TEXT : Ui.mix(Ui.TEXT, Ui.MUTED, 0.3f);
                for (String l : paragraphes.get(p)) {
                    g.drawString(f, Ui.lit(l, Ui.FONT), rx, py2, Ui.argb(couleur, 255), false);
                    py2 += INTER;
                }
                py2 += 7;
            }
        }

        private void commandes(GuiGraphics g, int x, int y, int cw, int mouseX, int mouseY, int accent) {
            Ui.section(g, f, Component.translatable("gui.heraliz_interface.info.commandes_titre").getString(), x, y, cw);
            int ry = y + 16;
            for (Informations.Commande c : contenu.commandes()) {
                boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= ry && mouseY < ry + 22;
                Ui.soft(g, x, ry, cw, 22, Ui.argb(0xFFFFFF, hover ? 13 : 6));
                if (hover) g.fill(x + 1, ry + 5, x + 3, ry + 17, Ui.argb(accent, 230));
                Component cmd = Ui.lit(c.commande(), Ui.BOLD);
                g.drawString(f, cmd, x + 10, ry + 7, Ui.argb(0xFFFFFF, 255), false);
                if (!c.touche().isEmpty()) {
                    Ui.chip(g, f, Component.translatable("gui.heraliz_interface.info.touche", c.touche()).getString(), x + 10 + f.width(cmd) + 8, ry + 6,
                            accent);
                }
                String d = Ui.ellipsis(f, c.description(), Ui.FONT, cw - colCommande - 10);
                g.drawString(f, Ui.lit(d, Ui.FONT), x + colCommande, ry + 7, Ui.argb(hover ? Ui.TEXT : Ui.mix(Ui.TEXT, Ui.MUTED, 0.4f), 255), false);
                ry += 24;
            }
        }

        private void regles(GuiGraphics g, int x, int y, int cw, int accent) {
            Ui.section(g, f, Component.translatable("gui.heraliz_interface.info.regles_titre").getString(), x, y, cw);
            int ry = y + 16;
            for (int i = 0; i < regles.size(); i++) {
                List<String> l = regles.get(i);
                Ui.gros(g, f, Ui.lit(String.format(Locale.ROOT, "%02d", i + 1), Ui.BOLD), x + 2, ry - 1, 1.25f, Ui.argb(accent, 235));
                int ly = ry + 1;
                for (String s : l) {
                    g.drawString(f, Ui.lit(s, Ui.FONT), x + 34, ly, Ui.argb(Ui.TEXT, 255), false);
                    ly += INTER;
                }
                int hgt = Math.max(16, l.size() * INTER + 3);
                ry += hgt + 9;
                if (i < regles.size() - 1) Ui.hairline(g, x + 34, x + cw, ry - 5, 0xFFFFFF, 22, false, true);
            }
            if (!note.isEmpty()) {
                ry += 6;
                for (String s : note) {
                    g.drawString(f, Ui.lit(s, Ui.SMALL), x + 34, ry, Ui.argb(Ui.FAINT, 255), false);
                    ry += 10;
                }
            }
        }
    }
}
