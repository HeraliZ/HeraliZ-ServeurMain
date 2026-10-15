package fr.heraliz.grades.client;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.HzButton;
import fr.heraliz.grades.client.kit.SonsUi;
import fr.heraliz.grades.client.kit.Ui;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Menu /kit (maquette validée, style des écrans de clan ; échelle forcée, canevas 640 x 360) : à gauche la liste des
 * kits, à droite le kit choisi (nom en couleur du grade, contenu, recharge, bouton). Le menu n'affiche que ce que le
 * serveur envoie et ne fait que DEMANDER le kit : le serveur décide de tout.
 */
public final class EcranKits extends Screen {
    private static final int LISTE_L = 170, LIGNE_H = 26, CASE = 22, ECART_CASE = 4;

    private ReseauGrades.EtatKits etat;
    private long recuA;
    private int choix;
    private boolean attente;
    private HzButton bouton;
    private int px, py, pw, ph;
    private String etatBouton = "";

    private EcranKits(ReseauGrades.EtatKits etat) {
        super(Component.translatable("gui.heraliz_grades.kits.titre"));
        recevoirEtat(etat);
        for (int i = 0; i < etat.kits().size(); i++) {
            if (etat.kits().get(i).id().equals(etat.kitJoueur())) choix = i;
        }
    }

    /** Paquet reçu du serveur : ouvre le menu, ou met à jour celui qui est ouvert. */
    public static void recevoir(ReseauGrades.EtatKits e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranKits k) {
            k.recevoirEtat(e);
            k.majBouton();
        } else if (e.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranKits(e));
        }
    }

    private void recevoirEtat(ReseauGrades.EtatKits e) {
        etat = e;
        recuA = System.currentTimeMillis();
        attente = false;
        if (choix >= e.kits().size()) choix = 0;
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.kits." + cle, args).getString();
    }

    private ReseauGrades.VueKit choisi() {
        return etat.kits().isEmpty() ? null : etat.kits().get(choix);
    }

    private boolean estLeSien(ReseauGrades.VueKit k) {
        return k != null && k.id().equals(etat.kitJoueur());
    }

    private long reste() {
        return Math.max(0, etat.resteMs() - (System.currentTimeMillis() - recuA));
    }

    private static int couleur(String id) {
        Grade g = Grade.parId(id);
        return g == null ? Ui.ACCENT_NEUTRE : g.couleur;
    }

    private static Component nom(String id) {
        Grade g = Grade.parId(id);
        return g == null ? Component.literal(id) : g.nom();
    }

    /** Durée lisible : « 1 j 3 h », « 7 h 05 », « 12 min », « 40 s ». */
    private static String duree(long ms) {
        long s = Math.max(0, (ms + 999) / 1000), min = s / 60, h = min / 60, j = h / 24;
        if (j >= 1) return Component.translatable("gui.heraliz_grades.duree.jh", j, h % 24).getString();
        if (h >= 1) return Component.translatable("gui.heraliz_grades.duree.hm", h, String.format(java.util.Locale.ROOT, "%02d", min % 60)).getString();
        if (min >= 1) return Component.translatable("gui.heraliz_grades.duree.min", min).getString();
        return Component.translatable("gui.heraliz_grades.duree.s", s).getString();
    }

    // ------------------------------------------------------------------ mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(560, width - 32);
        ph = Math.min(300, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int rx = px + LISTE_L + 10, rw = pw - LISTE_L - 10, bas = py + ph - 14;
        bouton = addRenderableWidget(new HzButton(rx + 14, bas - 20, rw - 28, 20, Component.empty(), Ui.ACCENT_NEUTRE, this::demander)
                .look(HzButton.Look.PRIMARY));
        etatBouton = "";
        majBouton();
    }

    private void demander() {
        ReseauGrades.VueKit k = choisi();
        if (k == null || attente) return;
        attente = true;
        ReseauGrades.CANAL.sendToServer(new ReseauGrades.Recuperer(k.id()));
        majBouton();
    }

    /** Libellé et état du bouton (appelé au changement de kit, à chaque tick pour la fin de la recharge). */
    private void majBouton() {
        if (bouton == null) return;
        ReseauGrades.VueKit k = choisi();
        bouton.visible = k != null;
        if (k == null) return;
        boolean sien = estLeSien(k), pret = sien && reste() <= 0;
        String cle = !sien ? "pas_le_tien" : attente ? "attente_serveur" : pret ? "recuperer" : "en_recharge";
        String nouvel = cle + ":" + k.id();
        if (nouvel.equals(etatBouton)) return;
        etatBouton = nouvel;
        bouton.setMessage(Component.literal(t(cle)));
        bouton.active = pret && !attente;
        bouton.accent(couleur(k.id()));
    }

    @Override
    public void tick() {
        super.tick();
        majBouton();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int lx = px, ly = py + 34;
        for (int i = 0; i < etat.kits().size(); i++) {
            int y = ly + 26 + i * (LIGNE_H + 4);
            if (mx >= lx + 8 && mx < lx + LISTE_L - 8 && my >= y && my < y + LIGNE_H) {
                if (choix != i) {
                    choix = i;
                    SonsUi.clic(0.6F);
                    majBouton();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
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

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);

        // en-tête
        Ui.gros(g, f, Ui.in(Component.literal(t("titre").toUpperCase(java.util.Locale.ROOT)), Ui.BOLD), px, py + 2, 1.6f, Ui.argb(Ui.TEXT, 255));
        float tw = f.width(Ui.in(Component.literal(t("titre").toUpperCase(java.util.Locale.ROOT)), Ui.BOLD)) * 1.6f;
        Ui.spaced(g, f, t("sous_titre"), Ui.SMALL, px + tw + 12, py + 8, Ui.argb(Ui.MUTED, 255), 1.0f);
        if (!etat.kitJoueur().isEmpty()) {
            Component droite = Ui.in(Component.literal(t("ton_kit").toUpperCase(java.util.Locale.ROOT) + "  ·  "), Ui.SMALL)
                    .append(Ui.in(nom(etat.kitJoueur()).copy(), Ui.BOLD));
            Ui.right(g, f, droite, px + pw, py + 7, Ui.argb(Ui.MUTED, 255));
        }
        Ui.hairline(g, px, px + pw, py + 24, 0xFFFFFF, 60, false, true);

        // liste
        int lx = px, ly = py + 34, lh = ph - 34;
        Ui.glass(g, lx, ly, LISTE_L, lh);
        Ui.section(g, f, t("liste"), lx + 10, ly + 10, LISTE_L - 20);
        for (int i = 0; i < etat.kits().size(); i++) {
            ReseauGrades.VueKit k = etat.kits().get(i);
            int y = ly + 26 + i * (LIGNE_H + 4), x0 = lx + 8, w = LISTE_L - 16;
            boolean survol = mx >= x0 && mx < x0 + w && my >= y && my < y + LIGNE_H;
            if (i == choix) {
                Ui.soft(g, x0, y, w, LIGNE_H, Ui.argb(0xFFFFFF, 18));
                Ui.softOutline(g, x0 + 1, y + 1, w - 2, LIGNE_H - 2, Ui.argb(0xFFFFFF, 36));
            } else if (survol) {
                Ui.soft(g, x0, y, w, LIGNE_H, Ui.argb(0xFFFFFF, 10));
            }
            Ui.soft(g, x0 + 8, y + 10, 6, 6, Ui.argb(couleur(k.id()), 255));
            boolean sien = estLeSien(k);
            Grade gk = Grade.parId(k.id());
            g.drawString(f, Ui.in(gk == null ? Component.literal(k.id()) : gk.nomSansCouleur(), Ui.FONT), x0 + 20, y + 9,
                    Ui.argb(sien ? Ui.TEXT : Ui.MUTED, 255), false);
            if (sien) {
                boolean pret = reste() <= 0;
                String chip = t(pret ? "pret" : "attente");
                int cw = (int) Ui.spacedWidth(f, chip.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 0.6f) + 10;
                Ui.chip(g, f, chip, x0 + w - cw - 6, y + 8, pret ? Ui.OK : Ui.AMBRE);
            }
        }
        if (etat.kits().isEmpty()) Ui.spaced(g, f, t("aucun"), Ui.SMALL, lx + 10, ly + 28, Ui.argb(Ui.FAINT, 255), 0.6f);

        // kit choisi
        int rx = px + LISTE_L + 10, rw = pw - LISTE_L - 10;
        Ui.glass(g, rx, ly, rw, lh);
        ReseauGrades.VueKit k = choisi();
        ItemStack survole = ItemStack.EMPTY;
        if (k != null) {
            int c = couleur(k.id());
            boolean sien = estLeSien(k);
            Ui.gros(g, f, Ui.in(nom(k.id()).copy(), Ui.BOLD), rx + 14, ly + 12, 1.4f, Ui.argb(c, 255));
            int nw = (int) (f.width(Ui.in(nom(k.id()).copy(), Ui.BOLD)) * 1.4f);
            Ui.chip(g, f, t(sien ? "ton_kit" : "autre_grade"), rx + 14 + nw + 10, ly + 13, sien ? Ui.OK : Ui.MUTED);
            String desc = sien ? t("desc_sien") : Component.translatable("gui.heraliz_grades.kits.desc_autre", nom(k.id())).getString();
            g.drawString(f, Ui.lit(desc, Ui.SMALL), rx + 14, ly + 30, Ui.argb(Ui.MUTED, 255), false);

            Ui.section(g, f, t("contenu"), rx + 14, ly + 48, rw - 28);
            int colonnes = Math.max(1, (rw - 28 + ECART_CASE) / (CASE + ECART_CASE));
            for (int i = 0; i < k.objets().size(); i++) {
                int sx = rx + 14 + (i % colonnes) * (CASE + ECART_CASE), sy = ly + 62 + (i / colonnes) * (CASE + ECART_CASE);
                boolean survol = mx >= sx && mx < sx + CASE && my >= sy && my < sy + CASE;
                Ui.soft(g, sx, sy, CASE, CASE, Ui.argb(0xFFFFFF, survol ? 16 : 8));
                Ui.softOutline(g, sx + 1, sy + 1, CASE - 2, CASE - 2, Ui.argb(0xFFFFFF, survol ? 50 : 22));
                ItemStack s = k.objets().get(i);
                g.renderItem(s, sx + 3, sy + 3);
                g.renderItemDecorations(f, s, sx + 3, sy + 3);
                if (survol) survole = s;
            }

            // recharge
            int bas = ly + lh - 14;
            long reste = reste();
            String gauche, droite = "";
            float ratio;
            if (!sien) {
                gauche = Component.translatable("gui.heraliz_grades.kits.reserve", nom(k.id())).getString();
                ratio = 0;
            } else if (reste <= 0) {
                gauche = t("disponible");
                droite = Component.translatable("gui.heraliz_grades.kits.recharge", duree(k.rechargeMs())).getString();
                ratio = 1;
            } else {
                gauche = t("prochain");
                droite = duree(reste);
                ratio = k.rechargeMs() <= 0 ? 0 : 1f - (float) reste / k.rechargeMs();
            }
            Ui.spaced(g, f, gauche, Ui.SMALL, rx + 14, bas - 42, Ui.argb(Ui.MUTED, 255), 0.6f);
            if (!droite.isEmpty()) Ui.spacedRight(g, f, droite, Ui.SMALL, rx + rw - 14, bas - 42, Ui.argb(Ui.TEXT, 255), 0.6f);
            Ui.bar(g, rx + 14, bas - 30, rw - 28, 3, ratio, c);
        }

        super.render(g, mx, my, pt);
        if (!survole.isEmpty()) g.renderTooltip(f, survole, mx, my);
    }
}
