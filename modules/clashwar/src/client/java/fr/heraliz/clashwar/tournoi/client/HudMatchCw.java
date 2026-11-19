package fr.heraliz.clashwar.tournoi.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.client.kit.TexteNet;
import fr.heraliz.clashwar.moteur.client.MoteurClient;
import fr.heraliz.clashwar.tournoi.net.CwPaquets;
import fr.heraliz.noyau.client.kit.Bandeau;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteCentre;
import fr.heraliz.noyau.client.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * HUD d'un match du ClashWar, a l'echelle du joueur :
 * <ul>
 *     <li>tableau de score en haut au centre (maquette validee le 19/11 : maquettes/clashwar/2b ; remplace le bandeau du
 *     moteur) : [plaque][score][temps et phase][score][plaque]. Plaque : trait de la couleur de l'equipe au bord, tag,
 *     une barre par joueur (pleine = en vie). Case du score teintee de la couleur de l'equipe : chiffre, un trait par
 *     manche a gagner (plein = gagnee). Milieu : temps restant, phase (« Choix de classe », « Preparation » en ambre,
 *     « Manche 4 », « Mort subite » en rouge, « Manche a TAG » entre deux manches), barre fine de la phase ; temps en rouge
 *     les 10 dernieres secondes du combat. Mon equipe a gauche (spectateur : equipe A a gauche) ;</li>
 *     <li>en mort subite, hors de la zone : bandeau du noyau sous le tableau (distance au bord de la zone) ;</li>
 *     <li>panneau de fin de match : issue, score, gagnant de chaque manche, eliminations et morts des joueurs, suite et
 *     retour en salle.</li>
 * </ul>
 * Textes du tableau nets a leur vraie taille ({@link TexteNet}), prepares a la reception et a chaque changement de
 * seconde ; formes envoyees au tampon puis un seul vidage, textes ensuite : aucune allocation par image.
 */
public final class HudMatchCw {
    private HudMatchCw() {
    }

    // ------------------------------------------------------------------ etat du tableau
    private static CwPaquets.MatchHudS2C hud = CwPaquets.MatchHudS2C.AUCUN;
    private static long recuMs;
    /** Mon equipe est B : les cotes sont echanges a l'affichage (mon equipe a gauche). */
    private static boolean inverse;

    /** Tableau : mesures en unites d'interface (maquette 2b). */
    private static final float Y = 5f, H = 20f, WP_MIN = 28f, WS = 17f, WC_MIN = 46f, TRAIT = 1f, MARGE = 6f;
    private static final float PIP_W = 2f, PIP_H = 4.5f, PIP_E = 1.5f, PIP_Y = 11.5f;
    private static final float MARQ_W = 3f, MARQ_H = 1f, MARQ_E = 1f, MARQ_Y = 15.5f;
    private static final float CY_TAG = 7f, CY_SCORE = 8.6f, CY_TEMPS = 7f, CY_PHASE = 13.6f, BARRE_Y = 17f, BARRE_H = 0.75f;
    private static final int VERRE_ALPHA = 205, BORD_ALPHA = 20, TEINTE_ALPHA = 46, PISTE_ALPHA = 30, VIDE_ALPHA = 40, MARQ_VIDE = 46;
    /** Couleurs (palette du noyau). */
    private static final int TEXTE = Palette.TEXTE, ATTENUE = Bandeau.ATTENUE, MUTED = Palette.ATTENUE, ROUGE = Ui.DANGER,
            AMBRE = Palette.ALERTE, BARRE = 0xD9DDE4;
    /** Dernieres secondes du combat : le temps passe au rouge. */
    private static final int FIN_COMBAT = 10;

    private static final TexteNet TAG_G = TexteNet.hud(PoliceHud.MINI_GRAS), TAG_D = TexteNet.hud(PoliceHud.MINI_GRAS);
    private static final TexteNet SCORE_G = TexteNet.ui(HeralizClashWar.id("ui_bold_13"), 13),
            SCORE_D = TexteNet.ui(HeralizClashWar.id("ui_bold_13"), 13);
    private static final TexteNet TEMPS = TexteNet.hud(PoliceHud.TEXTE_GRAS), PHASE = TexteNet.hud(PoliceHud.BANDEAU_TEXTE);
    private static int tempsMontre = Integer.MIN_VALUE;
    /** Largeurs fixees a la reception : plaques au tag le plus large, milieu au libelle de phase le plus large. */
    private static float wp = WP_MIN, wc = WC_MIN;
    /** Couleurs du libelle, du temps et de la barre de la phase (fixees a la reception). */
    private static int couleurPhase = ATTENUE, couleurTemps = TEXTE, couleurBarre = BARRE;

    // hors de la zone (mort subite)
    private static final Bandeau HORS_ZONE = new Bandeau();
    private static final long HORS_ZONE_MAJ_MS = 250;
    private static int distanceMontree = Integer.MIN_VALUE;
    private static long distanceMs;
    private static boolean horsZoneVisible;
    private static float horsZoneH;

    /** Vrai pendant un match du ClashWar (le bandeau du moteur est alors masque). */
    public static boolean actif() {
        return hud.actif() || finActive();
    }

    public static void recevoir(CwPaquets.MatchHudS2C p) {
        hud = p;
        recuMs = Util.getMillis();
        if (!p.actif()) return;
        // un nouveau tableau : le panneau de fin d'un match precedent est fini
        fin = null;
        inverse = p.monEquipe() == 1;
        TAG_G.texte(inverse ? p.tagB() : p.tagA());
        TAG_D.texte(inverse ? p.tagA() : p.tagB());
        SCORE_G.texte(Integer.toString(inverse ? p.scoreB() : p.scoreA()));
        SCORE_D.texte(Integer.toString(inverse ? p.scoreA() : p.scoreB()));
        PHASE.texte(libelle(p));
        wp = (float) Math.max(WP_MIN, Math.ceil(Math.max(TAG_G.largeur(), TAG_D.largeur()) + 2 * MARGE));
        // milieu : assez large pour tous les libelles du match, pour qu'il ne change pas de taille d'une phase a l'autre
        float lw = PHASE.largeur();
        lw = Math.max(lw, PHASE.mesurer(I18n.get("clashwar.hud.phase.choix")));
        lw = Math.max(lw, PHASE.mesurer(I18n.get("clashwar.hud.phase.preparation")));
        lw = Math.max(lw, PHASE.mesurer(I18n.get("clashwar.hud.phase.mort_subite")));
        lw = Math.max(lw, PHASE.mesurer(I18n.get("clashwar.hud.phase.pause", p.tagA())));
        lw = Math.max(lw, PHASE.mesurer(I18n.get("clashwar.hud.phase.pause", p.tagB())));
        wc = (float) Math.max(WC_MIN, Math.ceil(lw + 8f));
        switch (p.phase()) {
            case CwPaquets.PHASE_PREPARATION -> {
                couleurPhase = AMBRE;
                couleurTemps = AMBRE;
                couleurBarre = AMBRE;
            }
            case CwPaquets.PHASE_MORT_SUBITE -> {
                couleurPhase = ROUGE;
                couleurTemps = ROUGE;
                couleurBarre = ROUGE;
            }
            case CwPaquets.PHASE_PAUSE -> {
                couleurPhase = p.dernierGagnant() < 0 ? ATTENUE : Palette.clair(p.dernierGagnant() == 1 ? p.couleurB() : p.couleurA(), 0.3f);
                couleurTemps = MUTED;
                couleurBarre = MUTED;
            }
            default -> {
                couleurPhase = ATTENUE;
                couleurTemps = TEXTE;
                couleurBarre = BARRE;
            }
        }
        tempsMontre = Integer.MIN_VALUE;
    }

    /** Libelle de la phase de la manche. */
    private static String libelle(CwPaquets.MatchHudS2C p) {
        return switch (p.phase()) {
            case CwPaquets.PHASE_CHOIX -> I18n.get("clashwar.hud.phase.choix");
            case CwPaquets.PHASE_PREPARATION -> I18n.get("clashwar.hud.phase.preparation");
            case CwPaquets.PHASE_MORT_SUBITE -> I18n.get("clashwar.hud.phase.mort_subite");
            case CwPaquets.PHASE_PAUSE -> p.dernierGagnant() < 0 ? I18n.get("clashwar.hud.phase.manche", p.manche())
                    : I18n.get("clashwar.hud.phase.pause", p.dernierGagnant() == 1 ? p.tagB() : p.tagA());
            case CwPaquets.PHASE_COMBAT -> I18n.get("clashwar.hud.phase.manche", p.manche());
            default -> "";
        };
    }

    static void vider() {
        hud = CwPaquets.MatchHudS2C.AUCUN;
        fin = null;
        horsZoneVisible = false;
        distanceMontree = Integer.MIN_VALUE;
    }

    /**
     * Score du match pour l'en-tete de l'ecran de choix de classe ({@code MoteurClient.score}) : mon equipe d'abord ;
     * null hors match. Appele a la preparation de l'ecran, pas a chaque image.
     */
    @Nullable
    public static MoteurClient.Score scorePourChoix() {
        CwPaquets.MatchHudS2C p = hud;
        if (!p.actif()) return null;
        boolean b = p.monEquipe() == 1;
        return new MoteurClient.Score(b ? p.tagB() : p.tagA(), b ? p.couleurB() : p.couleurA(), b ? p.scoreB() : p.scoreA(),
                b ? p.tagA() : p.tagB(), b ? p.couleurA() : p.couleurB(), b ? p.scoreA() : p.scoreB());
    }

    /**
     * Haut du fil des eliminations du moteur ({@code HudMoteur.eviterFil}) : -1 tant que sa ligne la plus large
     * ({@code largeur}) reste a droite du tableau, sinon sous le tableau (et sous le bandeau « hors de la zone »).
     */
    public static int eviterFil(int w, int largeur) {
        if (!hud.actif() || largeur <= 0) return -1;
        float droite = w / 2f + (2 * wp + 2 * WS + wc) / 2f;
        if (w - 6 - largeur >= droite + 4) return -1;
        return Math.round(Y + H + 4 + (horsZoneVisible ? horsZoneH + 4 : 0));
    }

    // ------------------------------------------------------------------ rendu
    static void rendre(RenderGuiEvent.Post e) {
        horsZoneVisible = false;
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
        dessinerTableau(g, w, now);
        dessinerHorsZone(g, w, now, mc);
    }

    private static void dessinerTableau(GuiGraphics g, int w, long now) {
        CwPaquets.MatchHudS2C p = hud;
        float largeur = 2 * wp + 2 * WS + wc;
        float x0 = Formes.caler(w / 2f - largeur / 2f), y = Y, cx = x0 + largeur / 2f;
        int colG = inverse ? p.couleurB() : p.couleurA(), colD = inverse ? p.couleurA() : p.couleurB();
        int scoreG = inverse ? p.scoreB() : p.scoreA(), scoreD = inverse ? p.scoreA() : p.scoreB();
        int vivG = inverse ? p.vivantsB() : p.vivantsA(), vivD = inverse ? p.vivantsA() : p.vivantsB();
        int totG = inverse ? p.totalB() : p.totalA(), totD = inverse ? p.totalA() : p.totalB();
        // temps restant, decompte local depuis la reception
        long ecoule = now - recuMs;
        int secs = p.secondes() < 0 ? -1 : Math.max(0, p.secondes() - (int) (ecoule / 1000));
        if (secs != tempsMontre) {
            tempsMontre = secs;
            TEMPS.texte(secs < 0 ? "" : (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60));
        }
        boolean finCombat = p.phase() == CwPaquets.PHASE_COMBAT && secs >= 0 && secs <= FIN_COMBAT;
        int ct = finCombat ? ROUGE : couleurTemps, cb = finCombat ? ROUGE : couleurBarre;
        // formes
        VertexConsumer vc = Formes.tampon(g);
        Matrix4f m = g.pose().last().pose();
        float px = Formes.pixel();
        Formes.rect(vc, m, x0, y, x0 + largeur, y + H, Palette.argb(Palette.VERRE, VERRE_ALPHA));
        Formes.bord(vc, m, x0, y, largeur, H, px, Palette.argb(0xFFFFFF, BORD_ALPHA));
        float xa = x0 + wp, xb = x0 + wp + WS + wc;
        Formes.rect(vc, m, xa, y + px, xa + WS, y + H - px, Palette.argb(colG, TEINTE_ALPHA));
        Formes.rect(vc, m, xb, y + px, xb + WS, y + H - px, Palette.argb(colD, TEINTE_ALPHA));
        Formes.rect(vc, m, x0, y, x0 + TRAIT, y + H, Palette.argb(colG, 255));
        Formes.rect(vc, m, x0 + largeur - TRAIT, y, x0 + largeur, y + H, Palette.argb(colD, 255));
        joueurs(vc, m, x0 + MARGE, y + PIP_Y, totG, vivG, colG, false);
        joueurs(vc, m, x0 + largeur - MARGE, y + PIP_Y, totD, vivD, colD, true);
        manches(vc, m, xa + WS / 2f, y + MARQ_Y, p.gagnantes(), scoreG, colG, false);
        manches(vc, m, xb + WS / 2f, y + MARQ_Y, p.gagnantes(), scoreD, colD, true);
        if (p.duree() > 0 && p.secondes() >= 0) {
            float part = Math.max(0f, Math.min(1f, (p.secondes() * 1000f - ecoule) / (p.duree() * 1000f)));
            float bx0 = cx - wc / 2f + 5f, bx1 = cx + wc / 2f - 5f;
            Formes.rect(vc, m, bx0, y + BARRE_Y, bx1, y + BARRE_Y + BARRE_H, Palette.argb(0xFFFFFF, PISTE_ALPHA));
            Formes.rect(vc, m, bx0, y + BARRE_Y, bx0 + (bx1 - bx0) * part, y + BARRE_Y + BARRE_H, Palette.argb(cb, 230));
        }
        g.flush();
        // textes
        TAG_G.ajouter(g, x0 + MARGE, y + CY_TAG, Palette.argb(TEXTE, 255), TexteNet.GAUCHE, true);
        TAG_D.ajouter(g, x0 + largeur - MARGE, y + CY_TAG, Palette.argb(TEXTE, 255), TexteNet.DROITE, true);
        SCORE_G.ajouter(g, xa + WS / 2f, y + CY_SCORE, Palette.argb(TEXTE, 255), TexteNet.CENTRE, true);
        SCORE_D.ajouter(g, xb + WS / 2f, y + CY_SCORE, Palette.argb(TEXTE, 255), TexteNet.CENTRE, true);
        TEMPS.ajouter(g, cx, y + CY_TEMPS, Palette.argb(ct, 255), TexteNet.CENTRE, true);
        PHASE.ajouter(g, cx, y + CY_PHASE, Palette.argb(couleurPhase, 255), TexteNet.CENTRE, true);
        g.flush();
    }

    /** Une barre par joueur engage, pleine = en vie, depuis le bord exterieur (droite : alignees vers la gauche depuis x). */
    private static void joueurs(VertexConsumer vc, Matrix4f m, float x, float y, int total, int vivants, int rgb, boolean droite) {
        int n = Math.min(8, Math.max(0, total));
        int plein = Palette.argb(Palette.clair(rgb, 0.1f), 255), vide = Palette.argb(0xFFFFFF, VIDE_ALPHA);
        for (int i = 0; i < n; i++) {
            float bx = droite ? x - (i + 1) * PIP_W - i * PIP_E : x + i * (PIP_W + PIP_E);
            Formes.rect(vc, m, bx, y, bx + PIP_W, y + PIP_H, i < vivants ? plein : vide);
        }
    }

    /** Un trait par manche a gagner sous le score, plein = gagnee (gauche : de gauche a droite ; droite : l'inverse). */
    private static void manches(VertexConsumer vc, Matrix4f m, float cx, float y, int gagnantes, int score, int rgb, boolean inverse) {
        int n = Math.min(9, Math.max(1, gagnantes));
        float x0 = cx - (n * MARQ_W + (n - 1) * MARQ_E) / 2f;
        int plein = Palette.argb(Palette.clair(rgb, 0.1f), 255), vide = Palette.argb(0xFFFFFF, MARQ_VIDE);
        for (int i = 0; i < n; i++) {
            float x = x0 + (inverse ? n - 1 - i : i) * (MARQ_W + MARQ_E);
            Formes.rect(vc, m, x, y, x + MARQ_W, y + MARQ_H, i < score ? plein : vide);
        }
    }

    /** Mort subite, joueur en vie hors de la zone : bandeau du noyau sous le tableau, distance au bord de la zone. */
    private static void dessinerHorsZone(GuiGraphics g, int w, long now, Minecraft mc) {
        CwPaquets.MatchHudS2C p = hud;
        if (p.phase() != CwPaquets.PHASE_MORT_SUBITE || p.monEquipe() < 0 || MoteurClient.spectateur() || !mc.player.isAlive()) return;
        double d = CwHud.horsZone(mc.player.getX(), mc.player.getZ());
        if (!(d > 0)) return;
        int metres = (int) Math.ceil(d);
        // texte refait au plus 4 fois par seconde (la distance change en courant)
        if (metres != distanceMontree && (distanceMontree == Integer.MIN_VALUE || now - distanceMs >= HORS_ZONE_MAJ_MS)) {
            distanceMontree = metres;
            distanceMs = now;
            HORS_ZONE.poser(I18n.get("clashwar.hud.hors_zone"), I18n.get("clashwar.hud.hors_zone.texte", metres), Bandeau.DANGER);
        }
        float bw = HORS_ZONE.largeur();
        HORS_ZONE.dessiner(g, Formes.caler(w / 2f - bw / 2f), Y + H + 4, bw, 1f);
        horsZoneVisible = true;
        horsZoneH = HORS_ZONE.hauteur();
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
