package fr.heralisz.matchs.zombies.jeu.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.HzButton;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.zombies.jeu.net.PaquetsZombies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Ecran de fin de partie de zombies : manche atteinte en gros (rouge « craie »), duree, records battus, tableau par
 * joueur (points, eliminations, tetes, couteau, reanimations, a terre). Se ferme seul apres 15 s (le Coffre est rendu
 * par le serveur au bout de 10 s). Panneau verre 460 x 250, echelle forcee, HUD masque. Textes prepares dans
 * {@link #init()} ; seul le decompte du pied change (au plus une fois par seconde).
 */
public final class EcranFinZombies extends Screen {
    private static final int PW = 460, PH = 250;
    private static final int[] COLONNES = {150, 210, 262, 306, 352, 404};
    private static final String[] TITRES = {"zombies.fin.points", "zombies.fin.kills", "zombies.fin.tetes", "zombies.fin.couteau",
            "zombies.fin.reanimations", "zombies.fin.a_terre"};

    private final PaquetsZombies.FinS2C fin;
    private final long ouvertMs = System.currentTimeMillis();
    private int px, py;
    private boolean premier = true;
    private boolean hideAvant;

    private String titre, duree, joueur, recordChip, recordServeur;
    private Component manche;
    private final String[] titres = new String[TITRES.length];
    private Component[] noms = new Component[0];
    private Component[][] valeurs = new Component[0][];
    private Component pied;
    private int piedReste = -1;

    public EcranFinZombies(PaquetsZombies.FinS2C fin) {
        super(Component.translatable("zombies.fin.titre"));
        this.fin = fin;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        Minecraft mc = Minecraft.getInstance();
        if (premier) {
            premier = false;
            hideAvant = mc.options.hideGui;
        }
        mc.options.hideGui = true;
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        titre = tr("zombies.fin.titre");
        manche = Ui.lit(Component.translatable("zombies.fin.manche", fin.manche()).getString().toUpperCase(Locale.ROOT), Ui.BOLD);
        duree = Component.translatable("zombies.fin.duree", Ui.duree(fin.dureeMs())).getString();
        joueur = tr("zombies.fin.joueur");
        recordChip = fin.recordServeur() ? tr("zombies.fin.record_serveur") : (fin.recordGroupe() ? tr("zombies.fin.record_groupe") : null);
        recordServeur = fin.recordServeurManche() > 0 && !fin.recordServeur() ?Component.translatable("zombies.gui.record_serveur", fin.recordServeurManche()).getString() : null;
        for (int i = 0; i < TITRES.length; i++) titres[i] = tr(TITRES[i]);
        int n = fin.lignes().size();
        noms = new Component[n];
        valeurs = new Component[n][];
        for (int k = 0; k < n; k++) {
            PaquetsZombies.LigneFin l = fin.lignes().get(k);
            noms[k] = Ui.lit(l.nom(), Ui.BOLD);
            int[] v = {l.points(), l.kills(), l.tetes(), l.couteau(), l.reanimations(), l.aTerre()};
            valeurs[k] = new Component[v.length];
            for (int i = 0; i < v.length; i++) valeurs[k][i] = Ui.lit(Ui.nombre(v[i]), i == 0 ? Ui.BOLD : Ui.FONT);
        }
        piedReste = -1;
        addRenderableWidget(new HzButton(px + PW - 16 - 60, py + PH - 30, 60, 18, Component.translatable("zombies.gui.fermer"),
                EcranZombies.ROUGE, this::onClose).look(HzButton.Look.LINK));
    }

    @Override
    public void tick() {
        long ecoule = System.currentTimeMillis() - ouvertMs;
        if (ecoule > 15_000L) {
            onClose();
            return;
        }
        int reste = Math.max(0, 10 - (int) (ecoule / 1000L));
        if (reste != piedReste) {
            piedReste = reste;
            pied = Ui.lit(reste > 0 ? Component.translatable("zombies.fin.retour", reste).getString() : tr("zombies.fin.retour_fait"), Ui.FONT);
        }
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
        Ui.band(g, px + 2, py + 2, px + PW - 2, py + 64, EcranZombies.ROUGE, 40, 24);
        Ui.spaced(g, font, titre, Ui.SMALL, px + 16, py + 12, Ui.argb(Ui.MUTED, 255), 1.2f);
        Ui.gros(g, font, manche, px + 16, py + 24, 2.4f, Ui.argb(0xC8102E, 255));
        Ui.spaced(g, font, duree, Ui.SMALL, px + 16, py + 52, Ui.argb(Ui.TEXT, 220), 0.8f);
        int cx = px + PW - 16;
        if (recordChip != null) {
            int w = (int) Ui.spacedWidth(font, recordChip.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) + 10;
            Ui.chip(g, font, recordChip, cx - w, py + 14, Ui.OR);
        }
        if (recordServeur != null) Ui.spacedRight(g, font, recordServeur, Ui.SMALL, cx, py + 32, Ui.argb(Ui.MUTED, 255), 0.6f);

        // tableau
        int ty = py + 78;
        Ui.spaced(g, font, joueur, Ui.SMALL, px + 16, ty, Ui.argb(Ui.MUTED, 255), 0.8f);
        for (int i = 0; i < COLONNES.length; i++) {
            Ui.spacedRight(g, font, titres[i], Ui.SMALL, px + COLONNES[i] + 30, ty, Ui.argb(Ui.MUTED, 255), 0.6f);
        }
        Ui.hairline(g, px + 16, px + PW - 16, ty + 10, 0xFFFFFF, 34, false, true);
        int y = ty + 16;
        for (int k = 0; k < noms.length; k++) {
            int couleur = fin.lignes().get(k).couleur();
            Ui.soft(g, px + 12, y - 4, PW - 24, 20, Ui.argb(couleur, 14));
            g.fill(px + 12, y - 4, px + 14, y + 16, Ui.argb(couleur, 220));
            g.drawString(font, noms[k], px + 20, y + 2, Ui.argb(Ui.TEXT, 255), false);
            for (int i = 0; i < valeurs[k].length; i++) {
                Ui.right(g, font, valeurs[k][i], px + COLONNES[i] + 30, y + 2, Ui.argb(i == 0 ? Ui.OR : Ui.TEXT, 255));
            }
            y += 24;
        }
        Ui.hairline(g, px + 16, px + PW - 16, py + PH - 40, 0xFFFFFF, 30, true, true);
        if (pied != null) g.drawString(font, pied, px + 16, py + PH - 25, Ui.argb(Ui.MUTED, 255), false);
        super.render(g, mx, my, pt);
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
