package fr.heralisz.matchs.zombies.jeu.client;

import fr.heralisz.matchs.client.kit.Echelle;
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
 * Etabli de fabrication (ZOMBIES_V2_SPEC §2), interface epuree : panneau verre etroit, une ligne par recette (nom, effet,
 * cout en materiaux, quantite portee), clic ou touches 1-9 pour fabriquer, Echap ou F pour fermer. La partie continue
 * derriere (pas de pause). Le serveur revalide tout et renvoie l'etat a jour ({@link #maj}). Textes prepares a chaque
 * mise a jour (aucune allocation par image).
 */
public final class EcranAtelier extends Screen {
    private static final int ORANGE = 0xE8A33C;
    private static final int PW = 300, LIGNE = 34;

    private PaquetsZombies.AtelierS2C donnees;
    private int px, py, ph;
    private boolean premier = true;
    private boolean hideAvant;
    private long ouvertMs;

    private Component titre, materiaux, aide;
    private Component[] noms = new Component[0];
    private String[] descriptions = new String[0], couts = new String[0], portes = new String[0];
    private boolean[] possibles = new boolean[0];

    public EcranAtelier(PaquetsZombies.AtelierS2C donnees) {
        super(Component.translatable("zombies.jeu.atelier.titre"));
        this.donnees = donnees;
    }

    /** Index de l'etabli affiche. */
    int atelier() {
        return donnees.atelier();
    }

    /** Nouvel etat envoye par le serveur (apres une fabrication). */
    void maj(PaquetsZombies.AtelierS2C d) {
        this.donnees = d;
        preparer(Minecraft.getInstance().font);
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        Minecraft mc = Minecraft.getInstance();
        if (premier) {
            premier = false;
            hideAvant = mc.options.hideGui;
            ouvertMs = System.currentTimeMillis();
            SonsUi.ouvrir(0.5f);
        }
        mc.options.hideGui = true;
        preparer(mc.font);
    }

    private void preparer(Font font) {
        int n = donnees.recettes().size();
        ph = 58 + n * LIGNE + 22;
        px = (width - PW) / 2;
        py = (height - ph) / 2;
        titre = Ui.lit(tr("zombies.jeu.atelier.titre").toUpperCase(Locale.ROOT), Ui.BOLD);
        materiaux = Ui.lit("⚙ " + donnees.materiaux() + "  " + tr("zombies.jeu.atelier.materiaux").toUpperCase(Locale.ROOT), Ui.BOLD);
        aide = Ui.lit(tr("zombies.jeu.atelier.aide"), Ui.SMALL);
        noms = new Component[n];
        descriptions = new String[n];
        couts = new String[n];
        portes = new String[n];
        possibles = new boolean[n];
        for (int i = 0; i < n; i++) {
            PaquetsZombies.Recette r = donnees.recettes().get(i);
            noms[i] = Ui.lit((i + 1) + "  " + r.nom().getString(), Ui.BOLD);
            descriptions[i] = Ui.ellipsis(font, r.description().getString(), Ui.SMALL, PW - 120);
            couts[i] = "⚙ " + r.cout();
            portes[i] = r.max() > 0 ? r.possede() + "/" + r.max() : (r.possede() > 0 ? "×" + r.possede() : "");
            possibles[i] = donnees.materiaux() >= r.cout() && (r.max() <= 0 || r.possede() < r.max());
        }
    }

    private void fabriquer(int i) {
        if (i < 0 || i >= donnees.recettes().size()) return;
        if (!possibles[i]) {
            SonsUi.clic(0.3f);
            return;
        }
        SonsUi.clic(0.6f);
        MatchsNet.versServeur(new PaquetsZombies.FabriquerC2S(donnees.atelier(), donnees.recettes().get(i).id()));
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        Ui.fondLeger(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Font font = Minecraft.getInstance().font;
        float a = Math.min(1f, (System.currentTimeMillis() - ouvertMs) / 140f);
        int al = (int) (255 * a);
        Ui.glass(g, px, py, PW, ph, (int) (Ui.VERRE_ALPHA * a));
        Ui.band(g, px + 2, py + 2, px + PW - 2, py + 34, ORANGE, (int) (30 * a), 14);
        Ui.gros(g, font, titre, px + 14, py + 11, 1.4f, Ui.argb(Ui.TEXT, al));
        Ui.right(g, font, materiaux, px + PW - 14, py + 14, Ui.argb(ORANGE, al));
        int y = py + 44;
        for (int i = 0; i < noms.length; i++) {
            boolean hover = mx >= px + 10 && mx < px + PW - 10 && my >= y && my < y + LIGNE - 4;
            int fond = possibles[i] ? (hover ? 26 : 12) : 6;
            Ui.soft(g, px + 10, y, PW - 20, LIGNE - 4, Ui.argb(possibles[i] && hover ? ORANGE : 0xFFFFFF, fond));
            if (possibles[i] && hover) Ui.softOutline(g, px + 11, y + 1, PW - 22, LIGNE - 6, Ui.argb(ORANGE, 140));
            int coul = possibles[i] ? Ui.TEXT : Ui.FAINT;
            g.drawString(font, noms[i], px + 20, y + 5, Ui.argb(coul, al), false);
            Ui.spaced(g, font, descriptions[i], Ui.SMALL, px + 20, y + 17, Ui.argb(possibles[i] ? Ui.MUTED : Ui.FAINT, al), 0.3f);
            Ui.spacedRight(g, font, couts[i], Ui.BOLD, px + PW - 20, y + 5, Ui.argb(possibles[i] ? ORANGE : Ui.DANGER, al), 0.4f);
            if (!portes[i].isEmpty()) Ui.spacedRight(g, font, portes[i], Ui.SMALL, px + PW - 20, y + 17, Ui.argb(Ui.MUTED, al), 0.4f);
            y += LIGNE;
        }
        Ui.hairline(g, px + 14, px + PW - 14, py + ph - 20, 0xFFFFFF, 26, true, true);
        g.drawString(font, aide, px + 14, py + ph - 14, Ui.argb(Ui.FAINT, al), false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int y = py + 44;
        for (int i = 0; i < noms.length; i++) {
            if (mx >= px + 10 && mx < px + PW - 10 && my >= y && my < y + LIGNE - 4) {
                fabriquer(i);
                return true;
            }
            y += LIGNE;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            fabriquer(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (Minecraft.getInstance().options.keySwapOffhand.matches(key, scan) || Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        Minecraft.getInstance().options.hideGui = hideAvant;
        Echelle.restaurer();
        SonsUi.fermer(0.4f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String tr(String cle) {
        return Component.translatable(cle).getString();
    }
}
