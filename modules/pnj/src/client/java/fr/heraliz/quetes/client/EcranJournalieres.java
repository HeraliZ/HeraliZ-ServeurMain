package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.Net;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Quêtes du jour (/journalieres, bouton du journal), 26/10, d'après la maquette {@code maquettes/journalieres/}
 * ({@code modules/pnj/outils/apercu_journalieres.py} : mêmes mesures) : panneau de verre 560 x 320 comme le journal ;
 * 1 choix de la difficulté (trois cartes, exemples, récompenses les plus rares), 2 les trois propositions et les
 * récompenses de la difficulté, 3 quête en cours (progression, temps restant, ce qui compte, abandon en deux clics),
 * 4 quête réussie (tirer la récompense), 5 tirage (bande qui défile jusqu'au gagnant choisi par le serveur), 6 journée
 * terminée. Polices à leur vraie taille, texte posé sur un point de l'écran ({@link Net}). Tout est décidé par le serveur :
 * l'écran ne fait qu'envoyer des choix ({@code Reseau.JourAction}).
 */
public final class EcranJournalieres extends Screen {
    private static final ResourceLocation TITRE = Net.TITRE, PETIT_GRAS = Net.PETIT_GRAS, MICRO = Net.MICRO;
    private static final int LARGEUR = 560, HAUTEUR = 320, ENTETE = 44, M = 16, BLANC = 0xFFFFFF;
    private static final int[] RARETE = {0xB9BEC6, 0x5FD08A, 0x5AA9FF, 0xB57BFF, 0xF2B83A, 0xFF5A4E};
    /** Durée de la bande du tirage (ms). */
    private static final long DUREE_TIRAGE = 4800;

    private Reseau.Jour j;
    private long recuA;
    private int px, py, pw, ph;
    /** Tirage en cours d'affichage (null : aucun). */
    private Reseau.JourTirage tirage;
    private long tirageA;
    private boolean tirageFini;
    private long confirmerA;

    private record Bouton(float x, float y, float w, float h, Runnable action) {
    }

    private final List<Bouton> boutons = new ArrayList<>();

    private EcranJournalieres(Reseau.Jour j) {
        super(Component.translatable("gui.heraliz_quetes.jour.titre"));
        this.j = j;
        this.recuA = System.currentTimeMillis();
    }

    static void recevoir(Reseau.Jour j) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranJournalieres e) {
            e.j = j;
            e.recuA = System.currentTimeMillis();
        } else if (j.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranJournalieres(j));
        }
    }

    static void recevoirTirage(Reseau.JourTirage t) {
        if (Minecraft.getInstance().screen instanceof EcranJournalieres e) {
            e.tirage = t;
            e.tirageA = System.currentTimeMillis();
            e.tirageFini = false;
        }
    }

    private static String t(String cle, Object... args) {
        return TexteClient.tr("gui.heraliz_quetes.jour." + cle, args);
    }

    private static void envoyer(int type, String cle) {
        SonsUi.clic(0.6F);
        Reseau.CANAL.sendToServer(new Reseau.JourAction(type, cle));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(LARGEUR, width - 16);
        ph = Math.min(HAUTEUR, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        // la remise d'un tirage en attente se fait tout de suite (serveur)
        Reseau.CANAL.sendToServer(new Reseau.JourAction(6, ""));
        super.removed();
    }

    @Override
    public boolean keyPressed(int touche, int scan, int mods) {
        if (touche == GLFW.GLFW_KEY_SPACE && tirage != null && !tirageFini) {
            tirageA = System.currentTimeMillis() - DUREE_TIRAGE;
            return true;
        }
        return super.keyPressed(touche, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        for (Bouton bt : new ArrayList<>(boutons)) {
            if (mx >= bt.x() && mx < bt.x() + bt.w() && my >= bt.y() && my < bt.y() + bt.h()) {
                bt.action().run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    // ------------------------------------------------------------------ données
    private Reseau.JDiff diff(String id) {
        for (Reseau.JDiff d : j.difficultes()) if (d.id().equals(id)) return d;
        return j.difficultes().isEmpty() ? null : j.difficultes().get(0);
    }

    private static int couleurDiff(String id) {
        return switch (id) {
            case "facile" -> Ui.OK;
            case "difficile" -> Ui.DANGER;
            default -> Ui.OR;
        };
    }

    private static int niveauDiff(String id) {
        return switch (id) {
            case "facile" -> 1;
            case "difficile" -> 3;
            default -> 2;
        };
    }

    private static String nomDiff(String id) {
        return t("d." + id);
    }

    private static int couleurRarete(int r) {
        return r < 0 || r >= RARETE.length ? RARETE[0] : RARETE[r];
    }

    private static String nom(Reseau.JRec r) {
        String n = TexteClient.str(r.nom());
        if (!n.isEmpty()) return n;
        n = r.icone().isEmpty() ? r.id() : r.icone().getHoverName().getString();
        return r.nombre() > 1 ? TexteClient.tr("heraliz_quetes.jour.r.nombre", n, r.nombre()) : n;
    }

    private static String pct(float x) {
        String s = x < 10 ? String.format(Locale.ROOT, "%.1f", x) : Integer.toString(Math.round(x));
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        String sep = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("en") ? "." : ",";
        return s.replace(".", sep) + " %";
    }

    private long reste() {
        return Math.max(0, j.resteMs() - (System.currentTimeMillis() - recuA));
    }

    private static String duree(long ms) {
        long min = (ms + 59_999) / 60_000;
        return min >= 60 ? t("duree_hm", min / 60, String.format(Locale.ROOT, "%02d", min % 60)) : t("duree_m", min);
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        boutons.clear();
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph, 214);
        boolean gain = j.gains() > 0 || j.remise();
        String diffEntete = tirage != null ? tirage.difficulte() : gain && !j.gainDiff().isEmpty() ? j.gainDiff() : j.difficulte();
        entete(g, diffEntete, mx, my);
        if (tirage != null) ecranTirage(g, mx, my);
        else if (gain) ecranReussie(g, mx, my);
        else switch (j.etat()) {
            case 0 -> ecranChoix(g, mx, my);
            case 1 -> ecranPropositions(g, mx, my);
            case 2 -> ecranEnCours(g, mx, my);
            default -> ecranTermine(g);
        }
        super.render(g, mx, my, pt);
    }

    private void entete(GuiGraphics g, String diff, int mx, int my) {
        icone(g, px + M, py + 9, Ui.OR);
        float tx = px + M + 32;
        texte(g, t("titre"), TITRE, tx, py + 9 + cap(TITRE) / 2, Ui.TEXT);
        float place = pw - 150;
        caps(g, couperCaps(t("renouvellement", duree(reste())), Ui.SMALL, 1.0f, place), Ui.SMALL, 1.0f, tx, py + 27 + cap(Ui.SMALL) / 2, Ui.MUTED);
        if (!diff.isEmpty()) {
            String n = nomDiff(diff).toUpperCase(Locale.ROOT);
            float w = Net.capsLargeur(font, n, MICRO, 0.8f) + 10;
            puce(g, n, px + pw - 34 - w, py + 16, couleurDiff(diff), true);
        }
        float fx = px + pw - 18, fy = py + 16;
        boolean survol = mx >= fx - 8 && mx < fx + 8 && my >= fy - 8 && my < fy + 8;
        Net.centre(g, font, "×", Ui.BOLD, fx, fy, survol ? Ui.TEXT : Ui.MUTED);
        boutons.add(new Bouton(fx - 8, fy - 8, 16, 16, () -> {
            SonsUi.clic(0.6F);
            onClose();
        }));
        Net.rect(g, px + 8, py + ENTETE, px + pw - 8, py + ENTETE + Net.point(), Ui.argb(BLANC, 40));
    }

    /** Pictogramme : page de calendrier, anneaux, une case cochée. */
    private void icone(GuiGraphics g, float x, float y, int c) {
        Net.rect(g, x, y + 3, x + 22, y + 26, Ui.argb(c, 36));
        Net.cadre(g, x, y + 3, 22, 23, Ui.argb(c, 200));
        Net.rect(g, x, y + 3, x + 22, y + 9, Ui.argb(c, 150));
        for (float ax : new float[]{x + 6, x + 16}) Net.rect(g, ax - 1, y, ax + 1, y + 5, Ui.argb(BLANC, 220));
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 2; k++) {
                float cx = x + 5 + i * 6, cy = y + 13 + k * 6;
                if (i == 2 && k == 1) Net.coche(g, cx - 1.5f, cy - 2, 4.2f, 0.9f, Ui.argb(Ui.OK, 255));
                else Net.rect(g, cx - 1, cy - 1, cx + 1, cy + 1, Ui.argb(BLANC, 90));
            }
        }
    }

    // --------------------------------------------------------------- 1 : choix de la difficulté
    private void ecranChoix(GuiGraphics g, int mx, int my) {
        float y0 = py + ENTETE + 10;
        texte(g, couper(t("intro_choix"), Ui.SMALL, pw - 2 * M), Ui.SMALL, px + M, y0 + cap(Ui.SMALL) / 2, Ui.MUTED);
        float cy0 = y0 + 14, ch = py + ph - 26 - cy0, cw = (pw - 2 * M - 16) / 3f;
        for (int k = 0; k < j.difficultes().size() && k < 3; k++) {
            Reseau.JDiff d = j.difficultes().get(k);
            int c = couleurDiff(d.id());
            float x = px + M + k * (cw + 8);
            Net.rect(g, x, cy0, x + cw, cy0 + ch, Ui.argb(BLANC, 5));
            Net.cadre(g, x, cy0, cw, ch, Ui.argb(c, 70));
            Net.rect(g, x, cy0, x + cw, cy0 + 2, Ui.argb(c, 230));
            float nw = texte(g, couper(nomDiff(d.id()), Ui.BOLD, cw - 50), Ui.BOLD, x + 12, cy0 + 10 + cap(Ui.BOLD) / 2, Ui.TEXT);
            points(g, x + 12 + nw + 6, cy0 + 14.4f, niveauDiff(d.id()), c);
            caps(g, couperCaps(t("environ", d.duree()), MICRO, 0.8f, cw - 24), MICRO, 0.8f, x + 12, cy0 + 25 + cap(MICRO) / 2, Ui.MUTED);
            Net.rect(g, x + 10, cy0 + 37, x + cw - 10, cy0 + 37 + Net.point(), Ui.argb(BLANC, 30));
            caps(g, t("exemples"), MICRO, 0.8f, x + 12, cy0 + 43 + cap(MICRO) / 2, Ui.FAINT);
            float ly = cy0 + 59;
            for (int i = 0; i < Math.min(3, d.exemples().size()); i++) {
                Net.disque(g, x + 14.5f, ly, 1.3f, Ui.argb(c, 230));
                texte(g, couper(TexteClient.str(d.exemples().get(i)), Ui.SMALL, cw - 30), Ui.SMALL, x + 20, ly, Ui.TEXT);
                ly += 12;
            }
            Net.rect(g, x + 10, ly + 2, x + cw - 10, ly + 2 + Net.point(), Ui.argb(BLANC, 30));
            caps(g, t("plus_rares"), MICRO, 0.8f, x + 12, ly + 8 + cap(MICRO) / 2, Ui.FAINT);
            List<Reseau.JRec> rares = new ArrayList<>(d.recompenses());
            rares.sort(Comparator.comparingDouble(Reseau.JRec::chance));
            float ry = ly + 25;
            for (int i = 0; i < Math.min(5, rares.size()) && ry < cy0 + ch - 30; i++) {
                ligneRecompense(g, rares.get(i), x + 12, ry, cw - 24, false);
                ry += 14;
            }
            String id = d.id();
            bouton(g, t("choisir"), x + 12, cy0 + ch - 16, cw - 24, 16, c, true, mx, my, () -> envoyer(1, id));
        }
        pied(g, new String[][]{{t("touche_echap"), t("k.fermer")}, {t("touche_clic"), t("k.choisir")}}, t("note_choix"));
    }

    // --------------------------------------------------------------- 2 : trois propositions
    private void ecranPropositions(GuiGraphics g, int mx, int my) {
        int c = couleurDiff(j.difficulte());
        float y0 = py + ENTETE + 10;
        texte(g, couper(t("intro_props"), Ui.SMALL, pw - 2 * M), Ui.SMALL, px + M, y0 + cap(Ui.SMALL) / 2, Ui.MUTED);
        float cy0 = y0 + 14, ch = 112, cw = (pw - 2 * M - 16) / 3f;
        for (int k = 0; k < Math.min(3, j.propositions().size()); k++) {
            Reseau.JProp q = j.propositions().get(k);
            float x = px + M + k * (cw + 8);
            Net.rect(g, x, cy0, x + cw, cy0 + ch, Ui.argb(BLANC, 5));
            Net.cadre(g, x, cy0, cw, ch, Ui.argb(BLANC, 34));
            caps(g, couperCaps(t("f." + q.famille()), MICRO, 0.8f, cw - 24), MICRO, 0.8f, x + 12, cy0 + 10 + cap(MICRO) / 2, c);
            float ty = cy0 + 24;
            for (FormattedCharSequence l : lignes(TexteClient.str(q.titre()), Ui.BOLD, cw - 24, 2)) {
                Net.ligne(g, font, l, Ui.BOLD, x + 12, ty + cap(Ui.BOLD) / 2, Ui.TEXT);
                ty += 13;
            }
            for (FormattedCharSequence l : lignes(TexteClient.str(q.aide()), Ui.SMALL, cw - 24, 2)) {
                Net.ligne(g, font, l, Ui.SMALL, x + 12, ty + 2 + cap(Ui.SMALL) / 2, q.carte() ? Ui.OR : Ui.MUTED);
                ty += 10;
            }
            Net.rect(g, x + 12, cy0 + ch - 44, x + cw - 12, cy0 + ch - 43, Ui.argb(BLANC, 22));
            Net.droite(g, font, "0 / " + q.total(), PETIT_GRAS, x + cw - 12, cy0 + ch - 50, Ui.MUTED);
            String id = q.id();
            bouton(g, t("accepter"), x + 12, cy0 + ch - 18, cw - 24, 16, c, true, mx, my, () -> envoyer(2, id));
        }
        Reseau.JDiff d = diff(j.difficulte());
        float gy = cy0 + ch + 8;
        if (d != null) grille(g, d, px + M, gy, pw - 2 * M, py + ph - 26 - gy, t("recompenses_de", nomDiff(d.id())).toUpperCase(Locale.ROOT));
        pied(g, new String[][]{{t("touche_echap"), t("k.fermer")}, {t("touche_clic"), t("k.accepter")}}, t("note_props"));
    }

    // --------------------------------------------------------------- 3 : en cours
    private void ecranEnCours(GuiGraphics g, int mx, int my) {
        float y0 = py + ENTETE + 10, gw = 250, h = py + ph - 26 - y0;
        Reseau.JProp q = j.propositions().isEmpty() ? null : j.propositions().get(0);
        if (q != null) carteQuete(g, q, px + M, y0, gw, 112, false);
        float ix = px + M, iy = y0 + 124;
        boolean zombies = q != null && q.famille().equals("zombies"), fouille = q != null && (q.famille().equals("fouille") || q.famille().equals("salle"));
        String[][] infos = {{t("temps_restant"), t("temps_restant_v", duree(reste()))},
                {t("compte"), zombies ? t("c.zombies") : fouille ? t("c.conteneurs") : t("c.autre")},
                {t("compte_pas"), zombies ? t("n.zombies") : fouille ? t("n.conteneurs") : t("n.autre")}};
        for (int k = 0; k < infos.length; k++) {
            caps(g, couperCaps(infos[k][0], MICRO, 0.8f, gw - 24), MICRO, 0.8f, ix + 12, iy + k * 26 + cap(MICRO) / 2, Ui.FAINT);
            texte(g, couper(infos[k][1], Ui.SMALL, gw - 24), Ui.SMALL, ix + 12, iy + k * 26 + 10 + cap(Ui.SMALL) / 2, Ui.TEXT);
        }
        if (q != null && q.livrer()) {
            bouton(g, t("livrer", j.livrables()), ix, y0 + h - 30, gw, 16, Ui.OR, j.livrables() > 0, mx, my,
                    () -> envoyer(5, ""));
        }
        boolean conf = System.currentTimeMillis() - confirmerA < 4000;
        bouton(g, conf ? t("confirmer") : t("abandonner"), ix, y0 + h - 8, gw, 16, Ui.DANGER, conf, mx, my, () -> {
            if (System.currentTimeMillis() - confirmerA < 4000) {
                confirmerA = 0;
                envoyer(3, "");
            } else {
                confirmerA = System.currentTimeMillis();
                SonsUi.clic(0.6F);
            }
        });
        Reseau.JDiff d = diff(j.difficulte());
        if (d != null) table(g, d, px + M + gw + 12, y0, pw - 2 * M - gw - 12, h);
        pied(g, new String[][]{{t("touche_echap"), t("k.fermer")}, {t("touche_clic2"), t("k.abandonner")}}, t("note_abandon"));
    }

    /** La quête acceptée : « Quête acceptée », titre, aide, progression et sa barre. */
    private void carteQuete(GuiGraphics g, Reseau.JProp q, float x, float y, float w, float h, boolean finie) {
        int c = couleurDiff(j.difficulte());
        Ui.glass(g, (int) x, (int) y, (int) w, (int) h, 110);
        caps(g, t("acceptee"), MICRO, 0.8f, x + 12, y + 10 + cap(MICRO) / 2, c);
        float ty = y + 24;
        for (FormattedCharSequence l : lignes(TexteClient.str(q.titre()), Ui.BOLD, w - 24, 2)) {
            Net.ligne(g, font, l, Ui.BOLD, x + 12, ty + cap(Ui.BOLD) / 2, Ui.TEXT);
            ty += 13;
        }
        texte(g, couper(TexteClient.str(q.aide()), Ui.SMALL, w - 24), Ui.SMALL, x + 12, ty + 1 + cap(Ui.SMALL) / 2, Ui.MUTED);
        int total = Math.max(1, q.total()), fait = finie ? total : Mth.clamp(q.fait(), 0, total);
        boolean fini = fait >= total;
        float by = y + 86;
        caps(g, t("progression"), MICRO, 0.8f, x + 12, by - 12 + cap(MICRO) / 2, Ui.FAINT);
        Net.droite(g, font, fait + " / " + total, Ui.BOLD, x + w - 12, by - 9, fini ? Ui.OK : Ui.TEXT);
        Net.rect(g, x + 12, by, x + w - 12, by + 3, Ui.argb(BLANC, 26));
        Net.rect(g, x + 12, by, x + 12 + (w - 24) * fait / (float) total, by + 3, Ui.argb(fini ? Ui.OK : c, 230));
    }

    // --------------------------------------------------------------- 4 : réussie
    private void ecranReussie(GuiGraphics g, int mx, int my) {
        float y0 = py + ENTETE + 10, gw = 250, h = py + ph - 26 - y0;
        String gd = j.gainDiff().isEmpty() ? j.difficulte() : j.gainDiff();
        Reseau.JProp q = j.propositions().isEmpty() ? null : j.propositions().get(0);
        if (q != null && gd.equals(j.difficulte()) && (j.etat() == 3 || j.etat() == 4)) carteQuete(g, q, px + M, y0, gw, 112, true);
        float cx = px + M + gw / 2f, cy = y0 + 150;
        Net.disque(g, cx, cy, 13, Ui.argb(Ui.OK, 60));
        Net.anneau(g, cx, cy, 13, 1.2f, Ui.argb(Ui.OK, 230));
        Net.coche(g, cx - 6, cy - 6, 12, 2.2f, Ui.argb(Ui.OK, 255));
        Net.centre(g, font, j.etat() == 3 ? t("reussie") : t("gain_attente"), Ui.BOLD, cx, cy + 25, Ui.TEXT);
        Net.centre(g, font, couper(t("attend", nomDiff(gd)), Ui.SMALL, gw - 16), Ui.SMALL, cx, cy + 38, Ui.MUTED);
        Net.centre(g, font, couper(t("case_libre"), Ui.SMALL, gw), Ui.SMALL, cx, y0 + h - 24, Ui.FAINT);
        bouton(g, j.remise() ? t("recuperer") : t("tirer"), px + M, y0 + h - 8, gw, 18, Ui.OR, true, mx, my, () -> envoyer(4, ""));
        Reseau.JDiff d = diff(gd);
        if (d != null) table(g, d, px + M + gw + 12, y0, pw - 2 * M - gw - 12, h);
        pied(g, new String[][]{{t("touche_echap"), t("k.fermer")}, {t("touche_clic"), t("k.tirer")}}, t("note_reussie"));
    }

    // --------------------------------------------------------------- 5 : tirage
    private void ecranTirage(GuiGraphics g, int mx, int my) {
        Reseau.JDiff d = diff(tirage.difficulte());
        if (d == null || tirage.bande().isEmpty()) {
            tirage = null;
            return;
        }
        float y0 = py + ENTETE + 10, milieu = px + pw / 2f;
        capsCentre(g, t("tirage", nomDiff(d.id()).toUpperCase(Locale.ROOT)), PETIT_GRAS, 1.0f, milieu, y0 + 8, Ui.MUTED);
        float sy = y0 + 30, sh = 86, cw = 52, gap = 6;
        Ui.glass(g, px + M, (int) sy, pw - 2 * M, (int) sh, 120);
        // position de la bande : ralentit jusqu'au gagnant (léger décalage dans sa carte, comme la roue des caisses)
        float t = Mth.clamp((System.currentTimeMillis() - tirageA) / (float) DUREE_TIRAGE, 0, 1);
        if (t >= 1 && !tirageFini) {
            tirageFini = true;
            SonsUi.ouvrir(0.8F);
        }
        float ease = 1 - (float) Math.pow(1 - t, 3);
        float cible = tirage.gagnant() * (cw + gap) + cw / 2f - 9;
        float decal = cible * ease;
        g.flush();
        g.enableScissor(px + M + 1, (int) sy + 1, px + pw - M - 1, (int) (sy + sh) - 1);
        for (int k = 0; k < tirage.bande().size(); k++) {
            float x = milieu - decal + k * (cw + gap);
            if (x + cw < px + M || x > px + pw - M) continue;
            int idx = tirage.bande().get(k);
            if (idx < 0 || idx >= d.recompenses().size()) continue;
            Reseau.JRec r = d.recompenses().get(idx);
            int col = couleurRarete(r.rarete());
            boolean gagne = tirageFini && k == tirage.gagnant();
            Net.rect(g, x, sy + 10, x + cw, sy + sh - 10, Ui.argb(col, gagne ? 40 : 16));
            Net.cadre(g, x, sy + 10, cw, sh - 20, Ui.argb(col, gagne ? 230 : 70));
            Net.rect(g, x, sy + sh - 13, x + cw, sy + sh - 10, Ui.argb(col, 220));
            objet(g, r.icone(), x + cw / 2f - 14, sy + 21, 28);
            capsCentre(g, pct(r.chance()), MICRO, 0.8f, x + cw / 2f, sy + sh - 21, gagne ? Ui.TEXT : Ui.MUTED);
        }
        g.flush();
        g.disableScissor();
        Net.rect(g, milieu - 0.6f, sy + 2, milieu + 0.6f, sy + sh - 2, Ui.argb(Ui.OR, 255));
        Net.triangle(g, milieu - 4, sy + 2, milieu + 4, sy + 2, milieu, sy + 7, Ui.argb(Ui.OR, 255));
        Net.triangle(g, milieu - 4, sy + sh - 2, milieu + 4, sy + sh - 2, milieu, sy + sh - 7, Ui.argb(Ui.OR, 255));
        if (tirageFini) {
            int idx = tirage.bande().get(tirage.gagnant());
            Reseau.JRec r = idx >= 0 && idx < d.recompenses().size() ? d.recompenses().get(idx) : null;
            if (r != null) {
                int col = couleurRarete(r.rarete());
                float ry = sy + sh + 26;
                Net.centre(g, font, t("obtiens"), Ui.SMALL, milieu, ry, Ui.MUTED);
                Net.centre(g, font, couper(nom(r), TITRE, pw - 60), TITRE, milieu, ry + 15, col);
                String rar = r.rarete() >= 0 ? t("rarete." + Mth.clamp(r.rarete(), 0, 5)) : "";
                Net.centre(g, font, couper(rar.isEmpty() ? pct(r.chance()) : t("detail", rar, pct(r.chance())), Ui.SMALL, pw - 60), Ui.SMALL,
                        milieu, ry + 32, Ui.TEXT);
            }
            bouton(g, t("fermer"), milieu - 50, py + ph - 40, 100, 16, BLANC, false, mx, my, () -> {
                SonsUi.clic(0.6F);
                tirage = null;
            });
        }
        pied(g, new String[][]{{t("touche_espace"), t("k.passer")}, {t("touche_echap"), t("k.fermer")}}, t("note_tirage"));
    }

    // --------------------------------------------------------------- 6 : journée terminée
    private void ecranTermine(GuiGraphics g) {
        float cx = px + pw / 2f, cy = py + ph / 2f - 10;
        icone(g, cx - 11, cy - 46, Ui.FAINT);
        Net.centre(g, font, t("fini"), Ui.BOLD, cx, cy, Ui.TEXT);
        Net.centre(g, font, couper(t("nouvelles", duree(reste())), Ui.SMALL, pw - 80), Ui.SMALL, cx, cy + 14, Ui.MUTED);
        float w = 250;
        Ui.glass(g, (int) (cx - w / 2), (int) (cy + 30), (int) w, 44, 110);
        caps(g, t("aujourdhui"), MICRO, 0.8f, cx - w / 2 + 12, cy + 38 + cap(MICRO) / 2, Ui.FAINT);
        Reseau.JProp q = j.propositions().isEmpty() ? null : j.propositions().get(0);
        String quete = q == null ? t("e.aucune") : TexteClient.str(q.titre());
        texte(g, couper(quete, PETIT_GRAS, w - 90), PETIT_GRAS, cx - w / 2 + 12, cy + 50 + cap(PETIT_GRAS) / 2, Ui.TEXT);
        boolean reussie = j.etat() == 4;
        if (q != null) capsDroite(g, reussie ? t("e.reussie") : t("e.abandon"), MICRO, 0.8f, cx + w / 2 - 12, cy + 53, reussie ? Ui.OK : Ui.DANGER);
        String res = resultat();
        if (!res.isEmpty()) texte(g, couper(t("recompense_v", res), Ui.SMALL, w - 24), Ui.SMALL, cx - w / 2 + 12, cy + 62 + cap(Ui.SMALL) / 2, Ui.MUTED);
        pied(g, new String[][]{{t("touche_echap"), t("k.fermer")}}, "");
    }

    /** Nom de la récompense tirée aujourd'hui (« difficulté:id »), ou « ». */
    private String resultat() {
        int i = j.resultat().indexOf(':');
        if (i <= 0) return "";
        Reseau.JDiff d = diff(j.resultat().substring(0, i));
        if (d == null) return "";
        for (Reseau.JRec r : d.recompenses()) if (r.id().equals(j.resultat().substring(i + 1))) return nom(r);
        return "";
    }

    // --------------------------------------------------------------- récompenses
    private void ligneRecompense(GuiGraphics g, Reseau.JRec r, float x, float cy, float w, boolean avant) {
        int col = couleurRarete(r.rarete());
        if (avant) Net.rect(g, x - 4, cy - 6.5f, x + w + 4, cy + 6.5f, Ui.argb(col, 22));
        Net.rect(g, x, cy - 5.5f, x + 11, cy + 5.5f, Ui.argb(col, 34));
        Net.rect(g, x, cy + 4.8f, x + 11, cy + 5.5f, Ui.argb(col, 200));
        objet(g, r.icone(), x + 1, cy - 4.5f, 9);
        String c = pct(r.chance());
        float cw = Net.largeur(font, c, PETIT_GRAS);
        texte(g, couper(nom(r), Ui.SMALL, w - 16 - cw - 6), Ui.SMALL, x + 16, cy, Ui.TEXT);
        Net.droite(g, font, c, PETIT_GRAS, x + w, cy, r.chance() <= 5 ? col : Ui.MUTED);
    }

    /** Toutes les récompenses en liste (de la plus courante à la plus rare). */
    private void table(GuiGraphics g, Reseau.JDiff d, float x, float y, float w, float h) {
        Ui.glass(g, (int) x, (int) y, (int) w, (int) h, 110);
        caps(g, couperCaps(t("recompenses"), PETIT_GRAS, 1.0f, w - 20), PETIT_GRAS, 1.0f, x + 10, y + 8 + cap(PETIT_GRAS) / 2, Ui.MUTED);
        texte(g, couper(t("un_tirage"), Ui.SMALL, w - 20), Ui.SMALL, x + 10, y + 20 + cap(Ui.SMALL) / 2, Ui.FAINT);
        List<Reseau.JRec> rec = new ArrayList<>(d.recompenses());
        rec.sort(Comparator.comparingDouble(Reseau.JRec::chance).reversed());
        float pas = Math.min(14.5f, (h - 44) / Math.max(1, rec.size())), cy = y + 38;
        for (Reseau.JRec r : rec) {
            ligneRecompense(g, r, x + 10, cy, w - 20, r.chance() <= 1);
            cy += pas;
        }
    }

    /** Les récompenses en colonnes (écran des propositions). */
    private void grille(GuiGraphics g, Reseau.JDiff d, float x, float y, float w, float h, String titre) {
        Ui.glass(g, (int) x, (int) y, (int) w, (int) h, 110);
        caps(g, couperCaps(titre, PETIT_GRAS, 1.0f, w - 140), PETIT_GRAS, 1.0f, x + 10, y + 8 + cap(PETIT_GRAS) / 2, Ui.MUTED);
        Net.droite(g, font, t("un_tirage"), Ui.SMALL, x + w - 10, y + 11.5f, Ui.FAINT);
        List<Reseau.JRec> rec = new ArrayList<>(d.recompenses());
        rec.sort(Comparator.comparingDouble(Reseau.JRec::chance).reversed());
        int cols = 3, lignes = Math.max(1, (rec.size() + cols - 1) / cols);
        float cw = (w - 20 - (cols - 1) * 14) / cols, pas = (h - 30) / lignes;
        for (int i = 0; i < rec.size(); i++) {
            float cx = x + 10 + (i / lignes) * (cw + 14), cy = y + 30 + (i % lignes) * pas;
            ligneRecompense(g, rec.get(i), cx, cy, cw, rec.get(i).chance() <= 1);
        }
    }

    // --------------------------------------------------------------- briques
    private void points(GuiGraphics g, float x, float cy, int n, int c) {
        for (int k = 0; k < 3; k++) Net.disque(g, x + 3 + k * 6, cy, 1.8f, Ui.argb(k < n ? c : Ui.FAINT, 255));
    }

    private void bouton(GuiGraphics g, String txt, float x, float cy, float w, float h, int c, boolean plein, int mx, int my, Runnable action) {
        boolean survol = mx >= x && mx < x + w && my >= cy - h / 2 && my < cy + h / 2;
        if (plein) {
            Net.rect(g, x, cy - h / 2, x + w, cy + h / 2, Ui.argb(c, survol ? 90 : 60));
            Net.cadre(g, x, cy - h / 2, w, h, Ui.argb(c, 220));
        } else {
            Net.rect(g, x, cy - h / 2, x + w, cy + h / 2, Ui.argb(c == BLANC ? BLANC : c, survol ? 20 : 6));
            Net.cadre(g, x, cy - h / 2, w, h, Ui.argb(c, survol ? 170 : 90));
        }
        String s = couperCaps(txt, PETIT_GRAS, 1.0f, w - 8);
        capsCentre(g, s, PETIT_GRAS, 1.0f, x + w / 2f, cy, plein || c == BLANC ? Ui.TEXT : c);
        boutons.add(new Bouton(x, cy - h / 2, w, h, action));
    }

    private void puce(GuiGraphics g, String txt, float x, float cy, int c, boolean plein) {
        float w = Net.capsLargeur(font, txt, MICRO, 0.8f) + 10;
        Net.rect(g, x, cy - 5, x + w, cy + 5, Ui.argb(c, plein ? 70 : 26));
        Net.cadre(g, x, cy - 5, w, 10, Ui.argb(c, 170));
        capsCentre(g, txt, MICRO, 0.8f, x + w / 2f, cy, plein ? Ui.TEXT : c);
    }

    private void pied(GuiGraphics g, String[][] aides, String note) {
        float y = py + ph - 12;
        Net.rect(g, px + 8, y - 8, px + pw - 8, y - 8 + Net.point(), Ui.argb(BLANC, 22));
        float x = px + M;
        for (String[] a : aides) {
            float w = Net.capsLargeur(font, a[0], MICRO, 0.8f) + 8;
            Net.rect(g, x, y - 4.5f, x + w, y + 4.5f, Ui.argb(BLANC, 18));
            Net.cadre(g, x, y - 4.5f, w, 9, Ui.argb(BLANC, 60));
            capsCentre(g, a[0], MICRO, 0.8f, x + w / 2f, y, Ui.TEXT);
            x += w + 4;
            x += texte(g, a[1], Ui.SMALL, x, y, Ui.MUTED) + 12;
        }
        if (!note.isEmpty()) Net.droite(g, font, couper(note, Ui.SMALL, px + pw - M - x - 8), Ui.SMALL, px + pw - M, y, Ui.FAINT);
    }

    /** Icône d'un objet de taille {@code taille} (le jeu dessine les objets en 16 : agrandi ou réduit, jamais du texte). */
    private void objet(GuiGraphics g, ItemStack s, float x, float y, float taille) {
        if (s.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(taille / 16f, taille / 16f, 1);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
    }

    // --------------------------------------------------------------- texte net (kit Net)
    private static float cap(ResourceLocation f) {
        return Net.cap(f);
    }

    private float texte(GuiGraphics g, String s, ResourceLocation f, float x, float cy, int rgb) {
        return Net.texte(g, font, s, f, x, cy, rgb);
    }

    private float caps(GuiGraphics g, String s, ResourceLocation f, float esp, float x, float cy, int rgb) {
        return Net.caps(g, font, s, f, esp, x, cy, rgb);
    }

    private void capsCentre(GuiGraphics g, String s, ResourceLocation f, float esp, float cx, float cy, int rgb) {
        Net.capsCentre(g, font, s, f, esp, cx, cy, rgb);
    }

    private void capsDroite(GuiGraphics g, String s, ResourceLocation f, float esp, float xd, float cy, int rgb) {
        Net.capsDroite(g, font, s, f, esp, xd, cy, rgb);
    }

    private String couper(String s, ResourceLocation f, float max) {
        return Net.couper(font, s, f, max);
    }

    private String couperCaps(String s, ResourceLocation f, float esp, float max) {
        if (Net.capsLargeur(font, s, f, esp) <= max) return s;
        int n = s.length();
        while (n > 0 && Net.capsLargeur(font, s.substring(0, n).stripTrailing() + "…", f, esp) > max) n--;
        return n <= 0 ? "" : s.substring(0, n).stripTrailing() + "…";
    }

    private List<FormattedCharSequence> lignes(String s, ResourceLocation f, float max, int maxLignes) {
        List<FormattedCharSequence> l = font.split(Ui.lit(s, f), (int) max);
        return l.size() > maxLignes ? l.subList(0, maxLignes) : l;
    }
}
