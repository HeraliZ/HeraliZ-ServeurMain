package fr.heralisz.matchs.zombies.jeu.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.HzButton;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Ecran {@code /zombies} : groupe (membres, chef, empechements), choix de la carte avec ses records, difficulte
 * (normale en v1) et bouton LANCER reserve au chef. Panneau verre 440 x 250 sur fond en degrade, echelle forcee
 * (canevas 640 x 360), HUD masque. Textes prepares dans {@link #init()} (aucune allocation par image). Le serveur
 * revalide tout au LANCER.
 */
public final class EcranZombies extends Screen {
    static final int ROUGE = 0xE0473C;
    private static final int PW = 440, PH = 250, CARTES_MAX = 3;

    private final PaquetsZombies.OuvrirS2C donnees;
    private int choix;
    private int px, py;
    private boolean premier = true;
    private boolean hideAvant;

    // textes prepares
    private Component titre, sousTitre, aide, aucuneCarte, pied;
    private String sectionGroupe, sectionCarte, chef, difficulte, normale;
    private Component[] noms = new Component[0];
    private String[] etats = new String[0];
    private boolean[] prets = new boolean[0];
    private Component[] nomsCartes = new Component[0];
    private String[] recordsServeur = new String[0], recordsGroupe = new String[0];
    private boolean pret;

    public EcranZombies(PaquetsZombies.OuvrirS2C donnees) {
        super(Component.translatable("zombies.gui.titre"));
        this.donnees = donnees;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        Minecraft mc = Minecraft.getInstance();
        if (premier) {
            premier = false;
            hideAvant = mc.options.hideGui;
            SonsUi.ouvrir(0.6f);
        }
        mc.options.hideGui = true;
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        preparer(mc.font);
        HzButton lancer = addRenderableWidget(new HzButton(px + PW - 16 - 120, py + PH - 30, 120, 18,
                Component.translatable("zombies.gui.lancer"), ROUGE, this::lancer).look(HzButton.Look.PRIMARY));
        lancer.active = peutLancer();
        addRenderableWidget(new HzButton(px + PW - 16 - 120 - 70, py + PH - 30, 60, 18,
                Component.translatable("zombies.gui.fermer"), ROUGE, this::onClose).look(HzButton.Look.LINK));
    }

    private void preparer(Font font) {
        titre = Ui.lit(tr("zombies.gui.titre").toUpperCase(Locale.ROOT), Ui.BOLD);
        sousTitre = Ui.lit(tr("zombies.gui.sous_titre"), Ui.FONT);
        sectionGroupe = tr("zombies.gui.groupe") + "  " + donnees.membres().size() + "/4";
        sectionCarte = tr("zombies.gui.carte");
        chef = tr("zombies.gui.chef");
        difficulte = tr("zombies.gui.difficulte");
        normale = tr("zombies.gui.normale");
        aide = Ui.lit(Ui.ellipsis(font, tr("zombies.gui.aide_groupe"), Ui.SMALL, 184), Ui.SMALL);
        aucuneCarte = Ui.lit(tr("zombies.gui.aucune_carte"), Ui.FONT);
        int n = donnees.membres().size();
        noms = new Component[n];
        etats = new String[n];
        prets = new boolean[n];
        pret = true;
        for (int i = 0; i < n; i++) {
            PaquetsZombies.Membre m = donnees.membres().get(i);
            noms[i] = Ui.lit(m.nom(), Ui.BOLD);
            prets[i] = m.enLigne() && m.refus().isEmpty();
            etats[i] = prets[i] ? tr("zombies.gui.pret") : tr(m.refus());
            if (m.enLigne() && !m.refus().isEmpty()) pret = false;
        }
        boolean fr = Ui.french();
        int c = Math.min(CARTES_MAX, donnees.cartes().size());
        int cw = PW - 232;
        nomsCartes = new Component[c];
        recordsServeur = new String[c];
        recordsGroupe = new String[c];
        for (int i = 0; i < c; i++) {
            PaquetsZombies.CarteInfo ci = donnees.cartes().get(i);
            nomsCartes[i] = Ui.lit(fr ? ci.nomFr() : ci.nomEn(), Ui.BOLD);
            String rs = ci.recordServeur() > 0
                    ? Component.translatable("zombies.gui.record_serveur", ci.recordServeur()).getString() + "  ·  " + ci.recordServeurNoms()
                    : tr("zombies.gui.record_aucun");
            recordsServeur[i] = Ui.ellipsis(font, rs, Ui.SMALL, cw - 20);
            recordsGroupe[i] = ci.recordGroupe() > 0 ? Component.translatable("zombies.gui.record_groupe", ci.recordGroupe()).getString()
                    : tr("zombies.gui.record_groupe_aucun");
        }
        String p = !donnees.chef() ? tr("zombies.gui.pas_chef") : (!pret ? tr("zombies.gui.pas_pret") : tr("zombies.gui.pied"));
        pied = Ui.lit(Ui.ellipsis(font, p, Ui.FONT, PW - 230), Ui.FONT);
    }

    private boolean peutLancer() {
        return donnees.chef() && pret && !donnees.cartes().isEmpty();
    }

    private void lancer() {
        if (!peutLancer()) return;
        String carte = donnees.cartes().get(Math.min(choix, donnees.cartes().size() - 1)).id();
        MatchsNet.versServeur(new PaquetsZombies.LancerC2S(carte));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        Ui.fond(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Font font = Minecraft.getInstance().font;
        Ui.glass(g, px, py, PW, PH);
        // en-tete : bande rouge discrete + titre
        Ui.band(g, px + 2, py + 2, px + PW - 2, py + 40, ROUGE, 34, 18);
        Ui.gros(g, font, titre, px + 16, py + 12, 1.6f, Ui.argb(Ui.TEXT, 255));
        g.drawString(font, sousTitre, px + 16, py + 30, Ui.argb(Ui.MUTED, 255), false);

        // groupe
        int gx = px + 16, gy = py + 52, gw = 184;
        Ui.section(g, font, sectionGroupe, gx, gy, gw);
        int y = gy + 14;
        for (int i = 0; i < noms.length; i++) {
            Ui.soft(g, gx, y, gw, 26, Ui.argb(0xFFFFFF, 8));
            Ui.point(g, gx + 8, y + 7, prets[i] ? Ui.OK : Ui.DANGER, 255);
            g.drawString(font, noms[i], gx + 16, y + 4, Ui.argb(Ui.TEXT, 255), false);
            if (donnees.membres().get(i).chef()) Ui.chip(g, font, chef, gx + gw - 40, y + 3, ROUGE);
            Ui.spaced(g, font, etats[i], Ui.SMALL, gx + 16, y + 16, Ui.argb(prets[i] ? Ui.OK : Ui.DANGER, 220), 0.6f);
            y += 30;
        }
        g.drawString(font, aide, gx, Math.min(y + 4, py + PH - 48), Ui.argb(Ui.FAINT, 255), false);

        // cartes
        int cx = px + 216, cy = py + 52, cw = PW - 232;
        Ui.section(g, font, sectionCarte, cx, cy, cw);
        if (nomsCartes.length == 0) g.drawString(font, aucuneCarte, cx, cy + 16, Ui.argb(Ui.MUTED, 255), false);
        for (int i = 0; i < nomsCartes.length; i++) {
            int ry = cy + 14 + i * 50;
            boolean sel = i == choix;
            boolean hover = mx >= cx && mx < cx + cw && my >= ry && my < ry + 46;
            Ui.soft(g, cx, ry, cw, 46, Ui.argb(sel ? ROUGE : 0xFFFFFF, sel ? 30 : (hover ? 14 : 8)));
            if (sel) Ui.softOutline(g, cx + 1, ry + 1, cw - 2, 44, Ui.argb(ROUGE, 150));
            g.drawString(font, nomsCartes[i], cx + 10, ry + 6, Ui.argb(Ui.TEXT, 255), false);
            Ui.spaced(g, font, recordsServeur[i], Ui.SMALL, cx + 10, ry + 20, Ui.argb(Ui.MUTED, 255), 0.5f);
            boolean aRecord = donnees.cartes().get(i).recordGroupe() > 0;
            Ui.spaced(g, font, recordsGroupe[i], Ui.SMALL, cx + 10, ry + 31, Ui.argb(aRecord ? Ui.OR : Ui.FAINT, 255), 0.5f);
        }
        int dy = cy + 14 + Math.max(1, nomsCartes.length) * 50 + 4;
        Ui.spaced(g, font, difficulte, Ui.SMALL, cx, dy + 2, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.chip(g, font, normale, cx + 74, dy, ROUGE);

        // pied
        Ui.hairline(g, px + 16, px + PW - 16, py + PH - 40, 0xFFFFFF, 30, true, true);
        g.drawString(font, pied, px + 16, py + PH - 25, Ui.argb(peutLancer() ? Ui.MUTED : Ui.AMBRE, 255), false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int cx = px + 216, cy = py + 52, cw = PW - 232;
        for (int i = 0; i < nomsCartes.length; i++) {
            int ry = cy + 14 + i * 50;
            if (mx >= cx && mx < cx + cw && my >= ry && my < ry + 46) {
                if (choix != i) SonsUi.clic(0.5f);
                choix = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            lancer();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        Minecraft.getInstance().options.hideGui = hideAvant;
        Echelle.restaurer();
        SonsUi.fermer(0.5f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String tr(String cle) {
        return Component.translatable(cle).getString();
    }
}
