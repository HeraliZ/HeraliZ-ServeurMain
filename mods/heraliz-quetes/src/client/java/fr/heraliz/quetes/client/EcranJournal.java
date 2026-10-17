package fr.heraliz.quetes.client;

import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Journal des quêtes (/quetes ou touche J ; style des écrans de clan, canevas 640 x 360) : à gauche les PNJ chez qui on a
 * des quêtes (réputation), à droite leurs quêtes en cours, étape par étape, avec « Suivre » (la quête s'affiche à droite
 * de l'écran) et « Abandonner » (deux clics). Les étapes à venir restent cachées.
 */
public final class EcranJournal extends Screen {
    private static final int LISTE_L = 168, LIGNE_PNJ = 34;

    private Reseau.Journal j;
    private int choix;
    private int px, py, pw, ph;
    private double defile;
    private int contenuH;
    private String confirmer = "";
    private long confirmerA;

    /** Zones cliquables de la page (recalculées à chaque image). */
    private record Bouton(int x, int y, int w, int h, Runnable action) {
    }

    private final List<Bouton> boutons = new ArrayList<>();

    private EcranJournal(Reseau.Journal j) {
        super(Component.translatable("gui.heraliz_quetes.journal"));
        this.j = j;
        for (int i = 0; i < j.pnj().size(); i++) {
            for (Reseau.JQuete q : j.pnj().get(i).quetes()) if (q.suivie()) choix = i;
        }
    }

    static void recevoir(Reseau.Journal j) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranJournal e) {
            String id = e.choix < e.j.pnj().size() ? e.j.pnj().get(e.choix).id() : "";
            e.j = j;
            e.choix = 0;
            for (int i = 0; i < j.pnj().size(); i++) if (j.pnj().get(i).id().equals(id)) e.choix = i;
        } else if (j.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranJournal(j));
        }
    }

    private static String t(String cle, Object... args) {
        return TexteClient.tr("gui.heraliz_quetes.journal." + cle, args);
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(580, width - 32);
        ph = Math.min(320, height - 24);
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
        super.removed();
    }

    private void action(int type, String cle) {
        SonsUi.clic(0.6F);
        Reseau.CANAL.sendToServer(new Reseau.Action(type, cle));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int ly = py + 34;
        for (int i = 0; i < j.pnj().size(); i++) {
            int y = ly + 24 + i * (LIGNE_PNJ + 4);
            if (mx >= px + 8 && mx < px + LISTE_L - 8 && my >= y && my < y + LIGNE_PNJ) {
                if (choix != i) {
                    choix = i;
                    defile = 0;
                    SonsUi.clic(0.6F);
                }
                return true;
            }
        }
        for (Bouton bt : new ArrayList<>(boutons)) {
            if (mx >= bt.x() && mx < bt.x() + bt.w() && my >= bt.y() && my < bt.y() + bt.h()) {
                bt.action().run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        defile = Mth.clamp(defile - delta * 16, 0, Math.max(0, contenuH - (ph - 50)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        boutons.clear();
        Ui.fond(g, width, height);

        // en-tête
        String titre = t("titre").toUpperCase(Locale.ROOT);
        Ui.gros(g, f, Ui.lit(titre, Ui.BOLD), px, py + 2, 1.6f, Ui.argb(Ui.TEXT, 255));
        int tw = (int) (f.width(Ui.lit(titre, Ui.BOLD)) * 1.6f);
        int enCours = 0;
        for (Reseau.JPnj p : j.pnj()) enCours += p.quetes().size();
        Ui.spaced(g, f, t("sous_titre", enCours, j.finies()), Ui.SMALL, px + tw + 12, py + 8, Ui.argb(Ui.MUTED, 255), 1.0f);
        if (!j.titres().isEmpty()) {
            List<String> l = new ArrayList<>();
            for (Texte x : j.titres()) l.add(TexteClient.str(x));
            String s = Ui.ellipsis(f, t("titres") + "  ·  " + String.join(", ", l), Ui.SMALL, pw / 2);
            Ui.right(g, f, Ui.lit(s, Ui.SMALL), px + pw, py + 8, Ui.argb(Ui.OR, 255));
        }
        Ui.hairline(g, px, px + pw, py + 24, 0xFFFFFF, 60, false, true);

        // liste des PNJ
        int ly = py + 34, lh = ph - 34;
        Ui.glass(g, px, ly, LISTE_L, lh);
        Ui.section(g, f, t("pnj"), px + 10, ly + 10, LISTE_L - 20);
        for (int i = 0; i < j.pnj().size(); i++) {
            Reseau.JPnj p = j.pnj().get(i);
            int y = ly + 24 + i * (LIGNE_PNJ + 4), x0 = px + 8, w = LISTE_L - 16;
            if (y + LIGNE_PNJ > ly + lh - 4) break;
            boolean survol = mx >= x0 && mx < x0 + w && my >= y && my < y + LIGNE_PNJ;
            if (i == choix) {
                Ui.soft(g, x0, y, w, LIGNE_PNJ, Ui.argb(0xFFFFFF, 18));
                Ui.softOutline(g, x0 + 1, y + 1, w - 2, LIGNE_PNJ - 2, Ui.argb(0xFFFFFF, 36));
            } else if (survol) Ui.soft(g, x0, y, w, LIGNE_PNJ, Ui.argb(0xFFFFFF, 10));
            g.drawString(f, Ui.lit(Ui.ellipsis(f, TexteClient.str(p.nom()), Ui.BOLD, w - 40), Ui.BOLD), x0 + 8, y + 5, Ui.argb(Ui.TEXT, 255), false);
            Ui.spaced(g, f, TexteClient.tr("heraliz_quetes.niveau." + Mth.clamp(p.niveau(), 0, 4)), Ui.SMALL, x0 + 8, y + 17,
                    Ui.argb(Ui.MUTED, 255), 0.6f);
            float ratio = p.repSuivant() <= 0 ? 1f : p.rep() / (float) p.repSuivant();
            Ui.bar(g, x0 + 8, y + 27, w - 16, 2, ratio, Ui.OR);
            if (!p.quetes().isEmpty()) {
                String n = Integer.toString(p.quetes().size());
                int cw = (int) Ui.spacedWidth(f, n, Ui.SMALL, 0.6f) + 10;
                Ui.chip(g, f, n, x0 + w - cw - 4, y + 4, Ui.OR);
            }
        }
        if (j.pnj().isEmpty()) {
            for (FormattedCharSequence l : f.split(Ui.lit(t("aucun_pnj"), Ui.SMALL), LISTE_L - 20)) {
                g.drawString(f, l, px + 10, ly + 28, Ui.argb(Ui.FAINT, 255), false);
                ly += 9;
            }
        }

        // quêtes du PNJ choisi
        int rx = px + LISTE_L + 10, rw = pw - LISTE_L - 10, ry = py + 34;
        Ui.glass(g, rx, ry, rw, lh);
        if (choix < j.pnj().size()) quetes(g, f, j.pnj().get(choix), rx, ry, rw, lh, mx, my);
        super.render(g, mx, my, pt);
    }

    private void quetes(GuiGraphics g, Font f, Reseau.JPnj p, int rx, int ry, int rw, int rh, int mx, int my) {
        String role = TexteClient.str(p.role());
        Ui.gros(g, f, Ui.lit(TexteClient.str(p.nom()), Ui.BOLD), rx + 14, ry + 10, 1.3f, Ui.argb(Ui.TEXT, 255));
        if (!role.isEmpty()) {
            int nw = (int) (f.width(Ui.lit(TexteClient.str(p.nom()), Ui.BOLD)) * 1.3f);
            Ui.spaced(g, f, role, Ui.SMALL, rx + 14 + nw + 10, ry + 13, Ui.argb(Ui.MUTED, 255), 0.8f);
        }
        String rep = p.repSuivant() > 0 ? t("reputation", p.rep(), p.repSuivant()) : t("reputation_max", p.rep());
        Ui.spacedRight(g, f, rep, Ui.SMALL, rx + rw - 14, ry + 13, Ui.argb(Ui.OR, 255), 0.6f);

        int haut = ry + 30, bas = ry + rh - 8;
        g.enableScissor(rx + 2, haut, rx + rw - 2, bas);
        int y = haut + 4 - (int) defile, debutY = y;
        if (p.quetes().isEmpty()) {
            for (FormattedCharSequence l : f.split(Ui.lit(t("aucune_quete"), Ui.FONT), rw - 28)) {
                g.drawString(f, l, rx + 14, y + 4, Ui.argb(Ui.MUTED, 255), false);
                y += 10;
            }
        }
        for (Reseau.JQuete q : p.quetes()) y = carte(g, f, q, rx + 10, y, rw - 20, mx, my, haut, bas) + 8;
        contenuH = y - debutY + (int) defile;
        g.disableScissor();
        if (contenuH > bas - haut) {
            int barre = Math.max(16, (bas - haut) * (bas - haut) / contenuH);
            int by = haut + (int) ((bas - haut - barre) * (defile / Math.max(1, contenuH - (bas - haut))));
            g.fill(rx + rw - 4, by, rx + rw - 2, by + barre, Ui.argb(0xFFFFFF, 60));
        }
    }

    /** Une quête : titre, difficulté, résumé, étapes, récompense, boutons. Rend le bas de la carte. */
    private int carte(GuiGraphics g, Font f, Reseau.JQuete q, int x, int y, int w, int mx, int my, int haut, int bas) {
        int y0 = y;
        List<FormattedCharSequence> resume = f.split(Ui.lit(TexteClient.str(q.resume()), Ui.SMALL), w - 24);
        if (resume.size() > 3) resume = resume.subList(0, 3);
        int h = 22 + resume.size() * 9 + 6 + q.etapes().size() * 12 + 34;
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, q.suivie() ? 14 : 7));
        if (q.suivie()) Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(Ui.OR, 90));

        String titre = TexteClient.str(q.titre());
        g.drawString(f, Ui.lit(Ui.ellipsis(f, titre, Ui.BOLD, w - 120), Ui.BOLD), x + 10, y + 7, Ui.argb(Ui.TEXT, 255), false);
        int cx = x + 10 + f.width(Ui.lit(Ui.ellipsis(f, titre, Ui.BOLD, w - 120), Ui.BOLD)) + 8;
        g.drawString(f, Ui.lit("★".repeat(Mth.clamp(q.difficulte(), 1, 4)), Ui.FONT), cx, y + 7, Ui.argb(Ui.OR, 255), false);
        String chip = q.contrat() ? t("contrat") : t("suite");
        int cw = (int) Ui.spacedWidth(f, chip.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) + 10;
        Ui.chip(g, f, chip, x + w - cw - 8, y + 6, q.contrat() ? Ui.ACCENT_NEUTRE : Ui.OR);
        if (q.suivie()) {
            String s = t("suivie");
            int sw = (int) Ui.spacedWidth(f, s.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) + 10;
            Ui.chip(g, f, s, x + w - cw - sw - 12, y + 6, Ui.OK);
        }
        int cy = y + 22;
        for (FormattedCharSequence l : resume) {
            g.drawString(f, l, x + 10, cy, Ui.argb(Ui.MUTED, 255), false);
            cy += 9;
        }
        cy += 6;
        for (Reseau.JEtape e : q.etapes()) {
            int c = e.etat() == 2 ? Ui.OK : e.etat() == 1 ? Ui.TEXT : Ui.FAINT;
            String icone = e.etat() == 2 ? "✔" : e.etat() == 1 ? "●" : "○";
            g.drawString(f, Ui.lit(icone, Ui.FONT), x + 12, cy, Ui.argb(e.etat() == 1 ? Ui.AMBRE : c, 255), false);
            String lib = Ui.ellipsis(f, TexteClient.str(e.libelle()), Ui.FONT, w - 80);
            g.drawString(f, Ui.lit(lib, Ui.FONT), x + 24, cy, Ui.argb(c, 255), false);
            if (e.etat() == 1 && e.total() > 1) Ui.right(g, f, Ui.lit(e.fait() + "/" + e.total(), Ui.SMALL), x + w - 10, cy + 1, Ui.argb(Ui.MUTED, 255));
            cy += 12;
        }
        cy += 4;
        String rec = TexteClient.str(q.recompense());
        if (!rec.isEmpty()) Ui.spaced(g, f, rec, Ui.SMALL, x + 10, cy + 6, Ui.argb(Ui.OR, 255), 0.6f);

        // boutons
        int bh = 14, by = cy + 2;
        String suivre = q.suivie() ? t("ne_plus_suivre") : t("suivre");
        int sw = (int) Ui.spacedWidth(f, suivre.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 18;
        boolean conf = confirmer.equals(q.cle()) && System.currentTimeMillis() - confirmerA < 4000;
        String ab = conf ? t("confirmer") : t("abandonner");
        int aw = (int) Ui.spacedWidth(f, ab.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 18;
        int ax = x + w - aw - 8, sx = ax - sw - 6;
        bouton(g, f, sx, by, sw, bh, suivre, q.suivie() ? Ui.MUTED : Ui.OK, mx, my, haut, bas,
                () -> action(q.suivie() ? 2 : 1, q.cle()));
        bouton(g, f, ax, by, aw, bh, ab, Ui.DANGER, mx, my, haut, bas, () -> {
            if (confirmer.equals(q.cle()) && System.currentTimeMillis() - confirmerA < 4000) {
                confirmer = "";
                action(3, q.cle());
            } else {
                confirmer = q.cle();
                confirmerA = System.currentTimeMillis();
                SonsUi.clic(0.6F);
            }
        });
        return y0 + Math.max(h, by + bh + 6 - y0);
    }

    private void bouton(GuiGraphics g, Font f, int x, int y, int w, int h, String texte, int c, int mx, int my, int haut, int bas, Runnable action) {
        boolean survol = mx >= x && mx < x + w && my >= y && my < y + h && my >= haut && my < bas;
        Ui.soft(g, x, y, w, h, Ui.argb(c, survol ? 44 : 22));
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(c, survol ? 150 : 70));
        Ui.spacedCentered(g, f, texte, Ui.SMALL, x + w / 2f, y + (h - 6) / 2f, Ui.argb(Ui.mix(c, 0xFFFFFF, 0.35f), 255), 0.8f);
        if (y + h > haut && y < bas) boutons.add(new Bouton(x, Math.max(y, haut), w, Math.min(y + h, bas) - Math.max(y, haut), action));
    }
}
