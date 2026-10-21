package fr.heralisz.matchs.clashwar.client;

import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.client.kit.TexteCentre;
import fr.heralisz.matchs.client.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiEvent;

/**
 * Ceremonie du podium (maquette 3 validee le 21/10) : petit titre de verre en haut au centre (« CLASHWAR #12 · 2V2 »,
 * « [NOVA] REMPORTE LE CLASHWAR ») et, en bas, une carte par place dans l'ordre du podium vu des gradins (2e, 1er, 3e) :
 * place et recompense, tag et nom du clan, joueurs. Le podium lui-meme est pose dans la salle par le serveur. Textes
 * centres dans leurs cartes ({@link TexteCentre}), prepares a la reception.
 */
public final class HudPodium {
    private HudPodium() {
    }

    private static CwPaquets.PodiumS2C podium;
    private static long recuMs;
    private static final TexteCentre SUR = new TexteCentre(Ui.SMALL, 1.2f), TITRE = new TexteCentre(Ui.BOLD, 1.0f);
    private static final TexteCentre[] PLACE = new TexteCentre[3], CLAN = new TexteCentre[3], JOUEURS = new TexteCentre[3];
    private static final int[] COULEURS = {0xE8C36A, 0xB9C0CB, 0xD08A4E};
    /** Ordre d'affichage des cartes : 2e, 1er, 3e (comme les marches vues des gradins). */
    private static final int[] ORDRE = {1, 0, 2};
    private static final int CARTE_W = 124, CARTE_H = 36, ECART = 6;

    static {
        for (int i = 0; i < 3; i++) {
            PLACE[i] = new TexteCentre(Ui.SMALL, 1.0f);
            CLAN[i] = new TexteCentre(Ui.BOLD);
            JOUEURS[i] = new TexteCentre(Ui.FONT);
        }
    }

    public static void recevoir(CwPaquets.PodiumS2C p) {
        podium = p;
        recuMs = Util.getMillis();
        SUR.texte("ClashWar #" + p.edition() + " · " + p.format());
        CwPaquets.Marche premier = null;
        for (CwPaquets.Marche m : p.marches()) {
            int i = m.place() - 1;
            if (i < 0 || i > 2) continue;
            if (i == 0) premier = m;
            PLACE[i].texte(Component.translatable("clashwar.podium.place." + m.place()).getString() + " · " + m.recompense().getString());
            // textes bornes a la largeur de la carte (a leur echelle d'affichage)
            net.minecraft.client.gui.Font f = Minecraft.getInstance().font;
            CLAN[i].texte(Ui.ellipsis(f, m.tag() + "  " + m.nomClan(), Ui.BOLD, (int) ((CARTE_W - 14) / 0.75f)));
            JOUEURS[i].texte(Ui.ellipsis(f, m.joueurs(), Ui.FONT, (int) ((CARTE_W - 14) / 0.6f)));
        }
        TITRE.texte(premier == null ? "" : Component.translatable("clashwar.annonce.vainqueur", premier.tag()).getString());
    }

    static void vider() {
        podium = null;
    }

    static void rendre(RenderGuiEvent.Post e) {
        CwPaquets.PodiumS2C p = podium;
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        long age = Util.getMillis() - recuMs, duree = p.secondes() * 1000L;
        if (age >= duree) {
            podium = null;
            return;
        }
        if (mc.options.hideGui || mc.screen != null) return;
        float a = Math.min(1f, Math.min(age / 500f, (duree - age) / 600f));
        if (a < 0.03f) return;
        int al = (int) (255 * a);
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f;
        // titre
        float kt = 0.9f, ks = 0.55f;
        int tw = Math.round(Math.max(TITRE.largeur(kt), SUR.largeur(ks)) + 28), th = 27, ty = 8;
        int tx = Math.round(cx - tw / 2f);
        Ui.glass(g, tx, ty, tw, th, (int) (160 * a));
        Ui.softOutline(g, tx + 1, ty + 1, tw - 2, th - 2, Ui.argb(Ui.OR, (int) (70 * a)));
        SUR.centre(g, cx, ty + 8.5f, ks, Ui.argb(Ui.MUTED, al));
        TITRE.centre(g, cx, ty + 18.5f, kt, Ui.argb(Ui.TEXT, al));
        // cartes des trois premiers, en bas
        int n = 0;
        for (CwPaquets.Marche m : p.marches()) if (m.place() >= 1 && m.place() <= 3) n++;
        if (n == 0) return;
        int total = 3 * CARTE_W + 2 * ECART;
        int x0 = Math.round(cx - total / 2f), y = h - CARTE_H - 14;
        for (int k = 0; k < 3; k++) {
            int i = ORDRE[k];
            if (PLACE[i].source().isEmpty() || !aLaPlace(p, i + 1)) continue;
            int x = x0 + k * (CARTE_W + ECART);
            Ui.glass(g, x, y, CARTE_W, CARTE_H, (int) (160 * a));
            Ui.softOutline(g, x + 1, y + 1, CARTE_W - 2, CARTE_H - 2, Ui.argb(COULEURS[i], (int) ((i == 0 ? 110 : 50) * a)));
            float mx = x + CARTE_W / 2f;
            PLACE[i].centre(g, mx, y + 8.5f, 0.52f, Ui.argb(COULEURS[i], al));
            CLAN[i].centre(g, mx, y + 18.5f, 0.75f, Ui.argb(Ui.TEXT, al));
            JOUEURS[i].centre(g, mx, y + 28f, 0.6f, Ui.argb(Ui.MUTED, al));
        }
    }

    private static boolean aLaPlace(CwPaquets.PodiumS2C p, int place) {
        for (CwPaquets.Marche m : p.marches()) if (m.place() == place) return true;
        return false;
    }
}
