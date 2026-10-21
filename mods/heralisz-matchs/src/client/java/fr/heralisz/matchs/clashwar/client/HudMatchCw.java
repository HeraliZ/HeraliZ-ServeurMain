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
 * HUD d'un match du ClashWar (maquettes 1 et 2 validees le 21/10), a l'echelle du joueur, au style des ecrans de clan :
 * <ul>
 *     <li>bandeau en haut au centre (remplace celui du moteur) : couleur et tag de chaque equipe, une pastille par manche
 *     a gagner (pleine = gagnee), au milieu le temps de la manche et « MANCHE n · BO5 » ;</li>
 *     <li>pastille du role (attaque ou defense) dessous, les premieres secondes de chaque manche ;</li>
 *     <li>panneau de fin de match : issue, score, gagnant de chaque manche, eliminations et morts des joueurs, suite et
 *     retour en salle.</li>
 * </ul>
 * Chaque texte est centre dans son conteneur ({@link TexteCentre} : largeur exacte, milieu des capitales). Textes
 * prepares a la reception (et une fois par seconde pour le temps), aucune allocation par image.
 */
public final class HudMatchCw {
    private HudMatchCw() {
    }

    // ------------------------------------------------------------------ etat du bandeau
    private static CwPaquets.MatchHudS2C hud = CwPaquets.MatchHudS2C.AUCUN;
    private static long recuMs;
    private static int mancheVue = -1;
    private static long debutMancheMs;

    private static final TexteCentre TAG_A = new TexteCentre(Ui.BOLD, 0.4f), TAG_B = new TexteCentre(Ui.BOLD, 0.4f);
    private static final TexteCentre TEMPS = new TexteCentre(Ui.BOLD), MANCHE = new TexteCentre(Ui.SMALL, 1.0f);
    private static final TexteCentre ROLE = new TexteCentre(Ui.BOLD, 1.0f), ROLE_AIDE = new TexteCentre(Ui.FONT);
    private static int tempsMontre = Integer.MIN_VALUE;

    /** Bandeau : marges, tailles (pixels d'interface). */
    private static final int Y = 5, H = 22, PAD = 7, ECART = 7, MILIEU_W = 50, PASTILLE = 4, ECART_PASTILLE = 2, CHIP_W = 3, CHIP_H = 11;
    private static final float K_TAG = 0.85f, K_TEMPS = 1.15f, K_MANCHE = 0.62f, K_ROLE = 0.62f;
    /** Pastille du role : duree d'affichage au debut d'une manche, fondu. */
    private static final long ROLE_MS = 9000, FONDU_MS = 600;

    /** Vrai pendant un match du ClashWar (le bandeau du moteur est alors masque). */
    public static boolean actif() {
        return hud.actif() || finActive();
    }

    public static void recevoir(CwPaquets.MatchHudS2C p) {
        hud = p;
        recuMs = Util.getMillis();
        if (!p.actif()) return;
        TAG_A.texte(p.tagA());
        TAG_B.texte(p.tagB());
        MANCHE.texte(Component.translatable("clashwar.hud.manche_bo", p.manche(), p.gagnantes() * 2 - 1).getString());
        if (p.manche() != mancheVue) {
            mancheVue = p.manche();
            debutMancheMs = recuMs;
            fin = null;
        }
        if (p.role() == CwPaquets.ROLE_ATTAQUE) {
            ROLE.texte(Component.translatable("clashwar.hud.attaque").getString());
            ROLE_AIDE.texte(Component.translatable("clashwar.hud.attaque.aide").getString());
        } else if (p.role() == CwPaquets.ROLE_DEFENSE) {
            ROLE.texte(Component.translatable("clashwar.hud.defense").getString());
            ROLE_AIDE.texte(Component.translatable("clashwar.hud.defense.aide").getString());
        }
        tempsMontre = Integer.MIN_VALUE;
    }

    static void vider() {
        hud = CwPaquets.MatchHudS2C.AUCUN;
        fin = null;
        mancheVue = -1;
    }

    // ------------------------------------------------------------------ rendu
    static void rendre(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth();
        long now = Util.getMillis();
        if (fin != null) {
            dessinerFin(g, w, now);
            return;
        }
        if (!hud.actif()) return;
        dessinerBandeau(g, w, now);
        dessinerRole(g, w, now);
    }

    private static void dessinerBandeau(GuiGraphics g, int w, long now) {
        CwPaquets.MatchHudS2C p = hud;
        int n = Math.max(1, p.gagnantes());
        int pastilles = n * PASTILLE + (n - 1) * ECART_PASTILLE;
        float tagMax = Math.max(TAG_A.largeur(K_TAG), TAG_B.largeur(K_TAG));
        // les deux cotes ont la meme largeur : le temps est exactement au milieu de l'ecran
        int cote = Math.round(CHIP_W + 5 + tagMax + 6 + pastilles);
        int total = PAD + cote + ECART + MILIEU_W + ECART + cote + PAD;
        int x0 = w / 2 - total / 2;
        Ui.glass(g, x0, Y, total, H, 150);
        float cy = Y + H / 2f;
        // equipe A (a gauche) : barre de couleur, tag, pastilles vers le milieu
        int xa = x0 + PAD;
        Ui.soft(g, xa, Math.round(cy - CHIP_H / 2f), CHIP_W, CHIP_H, Ui.argb(p.couleurA(), 255));
        TAG_A.gauche(g, xa + CHIP_W + 5, cy, K_TAG, Ui.argb(Ui.TEXT, 255));
        int pa = xa + cote - pastilles;
        for (int i = 0; i < n; i++) pastille(g, pa + i * (PASTILLE + ECART_PASTILLE), cy, p.couleurA(), i < p.scoreA());
        // milieu : temps (ou bombe) et manche
        float mx = w / 2f;
        int secs = secondes(now);
        if (secs >= 0) {
            if (secs != tempsMontre) {
                tempsMontre = secs;
                TEMPS.texte((secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60));
            }
            TEMPS.centre(g, mx, Y + 8.5f, K_TEMPS, Ui.argb(secs <= 10 ? Ui.DANGER : Ui.TEXT, 255));
        } else {
            if (tempsMontre != -1) {
                tempsMontre = -1;
                TEMPS.texte(Component.translatable("clashwar.hud.bombe").getString());
            }
            TEMPS.centre(g, mx, Y + 8.5f, 0.8f, Ui.argb(Ui.DANGER, 255));
        }
        MANCHE.centre(g, mx, Y + 17f, K_MANCHE, Ui.argb(Ui.MUTED, 255));
        // equipe B (a droite) : symetrique
        int xb = x0 + total - PAD;
        Ui.soft(g, xb - CHIP_W, Math.round(cy - CHIP_H / 2f), CHIP_W, CHIP_H, Ui.argb(p.couleurB(), 255));
        TAG_B.droite(g, xb - CHIP_W - 5, cy, K_TAG, Ui.argb(Ui.TEXT, 255));
        int pb = xb - cote;
        for (int i = 0; i < n; i++) pastille(g, pb + i * (PASTILLE + ECART_PASTILLE), cy, p.couleurB(), n - 1 - i < p.scoreB());
    }

    /** Temps restant decompte localement depuis la reception, -1 = aucun (bombe amorcee). */
    private static int secondes(long now) {
        if (hud.secondes() < 0) return -1;
        return Math.max(0, hud.secondes() - (int) ((now - recuMs) / 1000));
    }

    /** Pastille ronde de 4 px centree verticalement sur cy : pleine (manche gagnee) ou creuse. */
    private static void pastille(GuiGraphics g, int x, float cy, int rgb, boolean pleine) {
        int y = Math.round(cy - PASTILLE / 2f);
        if (pleine) {
            g.fill(x + 1, y, x + 3, y + 4, Ui.argb(rgb, 255));
            g.fill(x, y + 1, x + 4, y + 3, Ui.argb(rgb, 255));
        } else {
            int c = Ui.argb(rgb, 150);
            g.fill(x + 1, y, x + 3, y + 1, c);
            g.fill(x + 1, y + 3, x + 3, y + 4, c);
            g.fill(x, y + 1, x + 1, y + 3, c);
            g.fill(x + 3, y + 1, x + 4, y + 3, c);
        }
    }

    /** Pastille du role sous le bandeau, les premieres secondes de la manche. */
    private static void dessinerRole(GuiGraphics g, int w, long now) {
        CwPaquets.MatchHudS2C p = hud;
        if (p.role() == CwPaquets.ROLE_AUCUN) return;
        long age = now - debutMancheMs;
        if (age > ROLE_MS) return;
        float a = Math.min(1f, Math.min(age / (float) FONDU_MS, (ROLE_MS - age) / (float) FONDU_MS));
        if (a < 0.03f) return;
        int couleur = p.role() == CwPaquets.ROLE_ATTAQUE ? Ui.AMBRE : Ui.ACCENT_NEUTRE;
        float lr = ROLE.largeur(K_ROLE), la = ROLE_AIDE.largeur(K_ROLE);
        int ph = 11, point = 3;
        int pw = Math.round(8 + point + 5 + lr + 5 + la + 9);
        int x = w / 2 - pw / 2, y = Y + H + 3;
        float cy = y + ph / 2f;
        Ui.soft(g, x, y, pw, ph, Ui.argb(Ui.VERRE, (int) (140 * a)));
        Ui.softOutline(g, x + 1, y + 1, pw - 2, ph - 2, Ui.argb(couleur, (int) (90 * a)));
        int px = x + 8, py = Math.round(cy - point / 2f);
        g.fill(px, py, px + point, py + point, Ui.argb(couleur, (int) (255 * a)));
        float tx = px + point + 5;
        ROLE.gauche(g, tx, cy, K_ROLE, Ui.argb(Ui.TEXT, (int) (255 * a)));
        ROLE_AIDE.gauche(g, tx + lr + 5, cy, K_ROLE, Ui.argb(0xB7BDC8, (int) (255 * a)));
    }

    // ------------------------------------------------------------------ fin de match
    private static CwPaquets.FinMatchS2C fin;
    private static long finRecuMs;
    private static final TexteCentre F_TOUR = new TexteCentre(Ui.SMALL, 1.2f), F_ISSUE = new TexteCentre(Ui.BOLD, 1.6f);
    private static final TexteCentre F_TAG_A = new TexteCentre(Ui.BOLD, 0.4f), F_TAG_B = new TexteCentre(Ui.BOLD, 0.4f);
    private static final TexteCentre F_SA = new TexteCentre(Ui.BOLD), F_SB = new TexteCentre(Ui.BOLD), F_TIRET = new TexteCentre(Ui.BOLD);
    private static final TexteCentre F_ENTETE_A = new TexteCentre(Ui.SMALL, 0.8f), F_ENTETE_B = new TexteCentre(Ui.SMALL, 0.8f);
    private static final TexteCentre F_EM = new TexteCentre(Ui.SMALL, 0.8f), F_SUITE = new TexteCentre(Ui.FONT);
    private static final TexteCentre[] F_NOMS = new TexteCentre[8], F_CHIFFRES = new TexteCentre[8];
    private static int finSecondeMontree = Integer.MIN_VALUE;
    private static final int FW = 252, F_LIGNE = 10;

    static {
        for (int i = 0; i < 8; i++) {
            F_NOMS[i] = new TexteCentre(Ui.FONT);
            F_CHIFFRES[i] = new TexteCentre(Ui.FONT);
        }
    }

    public static void fin(CwPaquets.FinMatchS2C p) {
        fin = p;
        finRecuMs = Util.getMillis();
        hud = CwPaquets.MatchHudS2C.AUCUN;
        F_TOUR.texte(Component.translatable("clashwar.fin.tour", p.tour(), p.gagnantes() * 2 - 1).getString());
        F_ISSUE.texte(Component.translatable(p.issue() == CwPaquets.FIN_VICTOIRE ? "clashwar.fin.victoire"
                : p.issue() == CwPaquets.FIN_DEFAITE ? "clashwar.fin.defaite" : "clashwar.fin.termine").getString());
        F_TAG_A.texte(p.tagA());
        F_TAG_B.texte(p.tagB());
        F_SA.texte(String.valueOf(p.scoreA()));
        F_SB.texte(String.valueOf(p.scoreB()));
        F_TIRET.texte("—");
        F_ENTETE_A.texte(p.tagA());
        F_ENTETE_B.texte(p.tagB());
        F_EM.texte(Component.translatable("clashwar.fin.em").getString());
        for (int i = 0; i < p.joueurs().size() && i < 8; i++) {
            CwPaquets.JoueurFin j = p.joueurs().get(i);
            F_NOMS[i].texte(Ui.ellipsis(Minecraft.getInstance().font, j.nom(), Ui.FONT, (int) (((FW - 38) / 2 - 30) / 0.75f)));
            F_CHIFFRES[i].texte(j.eliminations() + " / " + j.morts());
        }
        finSecondeMontree = Integer.MIN_VALUE;
    }

    private static boolean finActive() {
        return fin != null && Util.getMillis() - finRecuMs < fin.secondes() * 1000L;
    }

    private static void dessinerFin(GuiGraphics g, int w, long now) {
        CwPaquets.FinMatchS2C p = fin;
        long age = now - finRecuMs, duree = p.secondes() * 1000L;
        if (age >= duree) {
            fin = null;
            return;
        }
        float a = Math.min(1f, Math.min(age / 350f, (duree - age) / 500f));
        if (a < 0.03f) return;
        int reste = (int) Math.max(0, (duree - age + 999) / 1000);
        if (reste != finSecondeMontree) {
            finSecondeMontree = reste;
            F_SUITE.texte(p.suite().getString() + "  ·  " + Component.translatable("clashwar.fin.retour", reste).getString());
        }
        int na = 0, nb = 0;
        for (CwPaquets.JoueurFin j : p.joueurs()) {
            if (j.equipe() == 0) na++;
            else nb++;
        }
        int lignes = Math.max(1, Math.max(na, nb));
        int x = w / 2 - FW / 2, y = 30;
        // lignes de base des rangees (milieux des capitales), du haut du panneau
        final int yTour = 11, yIssue = 24, yScore = 44, yBarres = 55, yEntete = 67, yJoueurs = 78;
        int yFilet = yJoueurs + (lignes - 1) * F_LIGNE + 8, ySuite = yFilet + 10, h = ySuite + 10;
        int al = (int) (255 * a);
        Ui.glass(g, x, y, FW, h, (int) (170 * a));
        float cx = w / 2f;
        int issueCouleur = p.issue() == CwPaquets.FIN_VICTOIRE ? Ui.OR : p.issue() == CwPaquets.FIN_DEFAITE ? Ui.DANGER : Ui.TEXT;
        // tour, issue
        F_TOUR.centre(g, cx, y + yTour, 0.6f, Ui.argb(Ui.MUTED, al));
        F_ISSUE.centre(g, cx, y + yIssue, 1.3f, Ui.argb(issueCouleur, al));
        // score : [couleur][tag A]  3 — 1  [tag B][couleur], le tiret exactement au milieu de l'ecran
        float sy = y + yScore, k = 1.4f, ecartTiret = F_TIRET.largeur(k) / 2f + 6;
        F_TIRET.centre(g, cx, sy, k, Ui.argb(0x5D6573, al));
        F_SA.droite(g, cx - ecartTiret, sy, k, Ui.argb(Ui.TEXT, al));
        F_SB.gauche(g, cx + ecartTiret, sy, k, Ui.argb(Ui.TEXT, al));
        float bordA = cx - ecartTiret - F_SA.largeur(k) - 12, bordB = cx + ecartTiret + F_SB.largeur(k) + 12;
        boolean aGagne = p.scoreA() > p.scoreB(), bGagne = p.scoreB() > p.scoreA();
        F_TAG_A.droite(g, bordA, sy, 0.9f, Ui.argb(bGagne ? 0xB7BDC8 : Ui.TEXT, al));
        F_TAG_B.gauche(g, bordB, sy, 0.9f, Ui.argb(aGagne ? 0xB7BDC8 : Ui.TEXT, al));
        int chipY = Math.round(sy - 6);
        Ui.soft(g, Math.round(bordA - F_TAG_A.largeur(0.9f) - 6 - 4), chipY, 4, 12, Ui.argb(p.couleurA(), al));
        Ui.soft(g, Math.round(bordB + F_TAG_B.largeur(0.9f) + 6), chipY, 4, 12, Ui.argb(p.couleurB(), al));
        // une barre par manche possible (gagnant a sa couleur, non jouee en blanc pale), centrees
        int slots = Math.max(1, p.gagnantes() * 2 - 1), bw = 17, bg = 3;
        int bx = Math.round(cx - (slots * bw + (slots - 1) * bg) / 2f);
        for (int i = 0; i < slots; i++) {
            int c = i < p.manches().size() ? Ui.argb(p.manches().get(i) == 1 ? p.couleurB() : p.couleurA(), al) : Ui.argb(0xFFFFFF, (int) (30 * a));
            Ui.soft(g, bx + i * (bw + bg), y + yBarres, bw, 3, c);
        }
        // joueurs : deux colonnes, nom a gauche, eliminations / morts a droite
        int colW = (FW - 22 - 16) / 2, xa = x + 11, xb = x + 11 + colW + 16;
        F_ENTETE_A.gauche(g, xa, y + yEntete, 0.55f, Ui.argb(Ui.MUTED, al));
        F_EM.droite(g, xa + colW, y + yEntete, 0.55f, Ui.argb(Ui.MUTED, al));
        F_ENTETE_B.gauche(g, xb, y + yEntete, 0.55f, Ui.argb(Ui.MUTED, al));
        F_EM.droite(g, xb + colW, y + yEntete, 0.55f, Ui.argb(Ui.MUTED, al));
        int ia = 0, ib = 0;
        for (int i = 0; i < p.joueurs().size() && i < 8; i++) {
            boolean b = p.joueurs().get(i).equipe() == 1;
            int n = b ? ib++ : ia++;
            float ly = y + yJoueurs + n * F_LIGNE;
            int col = b ? xb : xa;
            int c = Ui.argb((b ? aGagne : bGagne) ? 0xB7BDC8 : Ui.TEXT, al);
            F_NOMS[i].gauche(g, col, ly, 0.75f, c);
            F_CHIFFRES[i].droite(g, col + colW, ly, 0.75f, c);
        }
        // filet, suite
        Ui.hairline(g, x + 12, x + FW - 12, y + yFilet, 0xFFFFFF, (int) (40 * a), true, true);
        F_SUITE.centre(g, cx, y + ySuite, 0.62f, Ui.argb(Ui.MUTED, al));
    }
}
