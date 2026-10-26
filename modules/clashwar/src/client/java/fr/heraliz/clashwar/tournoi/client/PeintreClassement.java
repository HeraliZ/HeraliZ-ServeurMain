package fr.heraliz.clashwar.tournoi.client;

import fr.heraliz.clashwar.tournoi.net.CwPaquets;
import fr.heraliz.noyau.client.kit.TexteCentre;
import fr.heraliz.noyau.client.kit.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Peintre du panneau « classement ClashWar des clans » (maquette 4 validee le 21/10), sur le canevas 1024 x 512 des
 * murs-ecrans : en-tete (titre, prochain ClashWar), colonnes rang, clan, titres, matchs gagnes, eliminations / morts,
 * K/D, puis les 10 premiers clans (les trois premiers mis en avant), pied (regle du rang, dernier champion). Chaque
 * texte est centre verticalement dans sa ligne et aligne sur sa colonne ({@link TexteCentre#ecrire}). Appele seulement
 * quand le classement change (texture du mur).
 */
public final class PeintreClassement {
    private PeintreClassement() {
    }

    private static final int L = PeintreTableau.L, H = PeintreTableau.H;
    private static final int MARGE = 56, HAUT_LIGNES = 128, LIGNE = 33;
    /** Colonnes : centre du rang, debut du clan, bords droits des chiffres. */
    private static final int X_RANG = MARGE + 22, X_CLAN = MARGE + 56, X_TITRES = 640, X_MATCHS = 780, X_EM = 884, X_KD = L - MARGE - 12;
    private static final int[] PODIUM = {0xE8C36A, 0xB9C0CB, 0xD08A4E};
    private static final int GRIS = 0x6F7886;

    static void peindre(GuiGraphics g, CwPaquets.ClassementS2C c) {
        g.fill(0, 0, L, H, 0xFF0D0F14);
        Ui.softOutline(g, 8, 8, L - 16, H - 16, Ui.argb(0xFFFFFF, 20));
        // en-tete
        espace(g, tr("clashwar.classement.mur.sur"), MARGE, 46, 0.95f, Ui.argb(0x9AA3B2, 255), 2.4f);
        TexteCentre.ecrire(g, lit(tr("clashwar.classement.mur.titre"), Ui.BOLD), MARGE, 76, 2.4f, Ui.argb(Ui.TEXT, 255), -1);
        if (c.prochaineMs() > 0) {
            String prochain = tr("clashwar.classement.mur.prochain", PeintreTableau.date(c.prochaineMs()) + " " + PeintreTableau.heure(c.prochaineMs()));
            TexteCentre.ecrire(g, lit(prochain, Ui.FONT), L - MARGE, 78, 1.15f, Ui.argb(0x9AA3B2, 255), 1);
        }
        // titres des colonnes
        float yc = 112;
        int col = Ui.argb(GRIS, 255);
        espaceCentre(g, tr("clashwar.classement.mur.rang"), X_RANG, yc, col);
        espace(g, tr("clashwar.classement.mur.clan"), X_CLAN, yc, 0.85f, col, 1.6f);
        espaceDroite(g, tr("clashwar.classement.mur.titres"), X_TITRES, yc, col);
        espaceDroite(g, tr("clashwar.classement.mur.matchs"), X_MATCHS, yc, col);
        espaceDroite(g, tr("clashwar.classement.mur.em"), X_EM, yc, col);
        espaceDroite(g, tr("clashwar.classement.mur.kd"), X_KD, yc, col);
        List<CwPaquets.LigneClassement> l = c.lignes();
        if (l.isEmpty()) {
            TexteCentre.ecrire(g, lit(tr("clashwar.classement.vide"), Ui.FONT), L / 2f, HAUT_LIGNES + 5 * LIGNE / 2f, 1.3f, Ui.argb(GRIS, 255), 0);
        }
        for (int i = 0; i < l.size() && i < 10; i++) ligne(g, i, l.get(i));
        // pied
        float yp = H - 34;
        TexteCentre.ecrire(g, lit(tr("clashwar.classement.mur.regle"), Ui.FONT), MARGE, yp, 1.05f, Ui.argb(GRIS, 255), -1);
        if (!c.dernierChampion().isEmpty()) {
            String d = tr("clashwar.classement.mur.champion", "[" + c.dernierChampion() + "]", String.valueOf(c.derniereEdition()));
            TexteCentre.ecrire(g, lit(d, Ui.FONT), L - MARGE, yp, 1.05f, Ui.argb(GRIS, 255), 1);
        }
    }

    private static void ligne(GuiGraphics g, int i, CwPaquets.LigneClassement x) {
        int y = HAUT_LIGNES + i * LIGNE, h = LIGNE - 5;
        float cy = y + h / 2f;
        boolean podium = i < 3;
        if (i == 0) {
            Ui.soft(g, MARGE, y, L - 2 * MARGE, h, Ui.argb(PODIUM[0], 22));
            Ui.softOutline(g, MARGE + 1, y + 1, L - 2 * MARGE - 2, h - 2, Ui.argb(PODIUM[0], 90));
        } else if (podium) {
            Ui.soft(g, MARGE, y, L - 2 * MARGE, h, Ui.argb(0xFFFFFF, 9));
        }
        float k = podium ? 1.45f : 1.25f;
        int texte = Ui.argb(podium ? Ui.TEXT : 0xC9CED6, 255);
        // rang centre dans sa colonne, couleur du podium pour les trois premiers
        TexteCentre.ecrire(g, lit(String.valueOf(i + 1), Ui.BOLD), X_RANG, cy, podium ? 1.7f : 1.35f,
                Ui.argb(podium ? PODIUM[i] : 0x7D8694, 255), 0);
        // clan : barre de couleur centree sur la ligne, tag gras, nom
        int bh = podium ? 18 : 14, bw = podium ? 7 : 5;
        Ui.soft(g, X_CLAN, Math.round(cy - bh / 2f), bw, bh, Ui.argb(x.couleur(), 255));
        MutableComponent tag = lit("[" + x.tag() + "]", Ui.BOLD);
        float xt = X_CLAN + bw + 10;
        TexteCentre.ecrire(g, tag, xt, cy, k, texte, -1);
        float wt = TexteCentre.largeur(tag, k);
        String nom = Ui.ellipsis(net.minecraft.client.Minecraft.getInstance().font, x.nomClan(), Ui.FONT, (int) ((X_TITRES - 90 - xt - wt) / 1.1f));
        TexteCentre.ecrire(g, lit(nom, Ui.FONT), xt + wt + 10, cy, 1.1f, Ui.argb(0x9AA3B2, 255), -1);
        // chiffres alignes a droite de leur colonne
        TexteCentre.ecrire(g, lit(String.valueOf(x.titres()), Ui.BOLD), X_TITRES, cy, k, texte, 1);
        TexteCentre.ecrire(g, lit(x.matchsGagnes() + " / " + x.matchs(), Ui.FONT), X_MATCHS, cy, 1.2f, texte, 1);
        TexteCentre.ecrire(g, lit(x.eliminations() + " / " + x.morts(), Ui.FONT), X_EM, cy, 1.2f, texte, 1);
        TexteCentre.ecrire(g, lit(String.format(Locale.ROOT, "%.2f", x.kd()), Ui.BOLD), X_KD, cy, 1.2f, texte, 1);
    }

    // ------------------------------------------------------------------ aides
    private static final float K_COL = 0.85f, ESP_COL = 1.6f;

    private static void espaceCentre(GuiGraphics g, String s, float cx, float cy, int argb) {
        espace(g, s, cx - largeurEspacee(s, K_COL, ESP_COL) / 2f, cy, K_COL, argb, ESP_COL);
    }

    private static void espaceDroite(GuiGraphics g, String s, float xDroite, float cy, int argb) {
        espace(g, s, xDroite - largeurEspacee(s, K_COL, ESP_COL), cy, K_COL, argb, ESP_COL);
    }

    /** Capitales espacees (petite police), bord gauche en x, milieu des capitales sur cy. */
    private static void espace(GuiGraphics g, String s, float x, float cy, float k, int argb, float espace) {
        String t = s.toUpperCase(Locale.ROOT);
        float cx = x;
        for (int i = 0; i < t.length(); i = t.offsetByCodePoints(i, 1)) {
            MutableComponent c = lit(new String(Character.toChars(t.codePointAt(i))), Ui.SMALL);
            TexteCentre.ecrire(g, c, cx, cy, k, argb, -1);
            cx += TexteCentre.largeur(c, k) + espace * k;
        }
    }

    private static float largeurEspacee(String s, float k, float espace) {
        String t = s.toUpperCase(Locale.ROOT);
        float w = 0;
        int n = 0;
        for (int i = 0; i < t.length(); i = t.offsetByCodePoints(i, 1), n++) {
            w += TexteCentre.largeur(lit(new String(Character.toChars(t.codePointAt(i))), Ui.SMALL), k);
        }
        return n == 0 ? 0 : w + (n - 1) * espace * k;
    }

    private static MutableComponent lit(String s, ResourceLocation police) {
        return Ui.lit(s, police);
    }

    private static String tr(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }
}
