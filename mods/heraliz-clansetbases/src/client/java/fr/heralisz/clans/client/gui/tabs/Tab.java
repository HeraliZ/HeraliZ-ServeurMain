package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.ClanScreen;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.network.ClansNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Un onglet de l'ecran du clan : recoit le snapshot et la zone de contenu (marge 16 deja retiree), ajoute ses widgets
 * via {@link ClanScreen#ajouter}, dessine ses parties fixes dans {@link #render}. Reconstruit a chaque nouveau snapshot.
 */
public abstract class Tab {
    protected ClanScreen ecran;
    protected ClanSnapshot s;
    protected Font font;
    protected int x, y, w, h, accent;

    public final void init(ClanScreen ecran, ClanSnapshot s, int x, int y, int w, int h) {
        this.ecran = ecran;
        this.s = s;
        this.font = Minecraft.getInstance().font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.accent = s.accent();
        construire();
    }

    /** Ajoute les widgets (champs, boutons, listes). */
    protected abstract void construire();

    /** Parties dessinees a la main (textes, tuiles, barres). Les widgets sont rendus ensuite par l'ecran. */
    public abstract void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    public void tick() {
    }

    /** Clic hors widgets (zones dessinees a la main). */
    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    protected static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected static String tr(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    /** Envoie un paquet au serveur (journal seulement hors connexion : banc d'essai). */
    protected static void envoyer(Object paquet) {
        if (Minecraft.getInstance().getConnection() != null) ClansNet.toServer(paquet);
        else HeraliszClans.LOG.info("[clans] (hors ligne) paquet non envoye : {}", paquet);
    }

    /** Couleur d'un grade : chef dore, haut grade accent, membre texte, recrue attenue. */
    protected int couleurGrade(Grade g) {
        return switch (g) {
            case CHEF -> Ui.OR;
            case HAUT_GRADE -> accent;
            case MEMBRE -> Ui.TEXT;
            case RECRUE -> Ui.MUTED;
        };
    }
}
