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

import java.util.Locale;

/**
 * Menu /warp (style des écrans de clan, comme le menu /kit ; échelle forcée, canevas 640 x 360) : à gauche la liste des
 * warps, à droite le warp choisi (nom, description, accès, bouton). Le menu n'affiche que ce que le serveur envoie et ne
 * fait que DEMANDER le warp : lieu, grade et règles sont décidés par le serveur.
 */
public final class EcranWarps extends Screen {
    private static final int LISTE_L = 190, LIGNE_H = 26, ECART = 4;

    private final ReseauGrades.EtatWarps etat;
    private int choix, defile;
    private HzButton bouton;
    private int px, py, pw, ph;

    private EcranWarps(ReseauGrades.EtatWarps etat) {
        super(Component.translatable("gui.heraliz_grades.warps.titre"));
        this.etat = etat;
    }

    /** Paquet reçu du serveur : ouvre (ou rouvre à jour) le menu. */
    public static void recevoir(ReseauGrades.EtatWarps e) {
        Minecraft mc = Minecraft.getInstance();
        int choix = mc.screen instanceof EcranWarps w ? w.choix : 0;
        if (!(mc.screen instanceof EcranWarps)) SonsUi.ouvrir(0.6F);
        EcranWarps ecran = new EcranWarps(e);
        ecran.choix = Math.min(choix, Math.max(0, e.warps().size() - 1));
        mc.setScreen(ecran);
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.warps." + cle, args).getString();
    }

    private ReseauGrades.VueWarp choisi() {
        return etat.warps().isEmpty() ? null : etat.warps().get(choix);
    }

    private static Grade grade(int rang) {
        for (Grade g : Grade.values()) if (g.type == Grade.Type.BOUTIQUE && g.rang == rang) return g;
        return null;
    }

    private static int couleur(ReseauGrades.VueWarp w) {
        Grade g = grade(w.rang());
        return g == null ? Ui.ACCENT_NEUTRE : g.couleur;
    }

    private int visibles() {
        return Math.max(1, (ph - 34 - 34) / (LIGNE_H + ECART));
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
        bouton = addRenderableWidget(new HzButton(rx + 14, bas - 20, rw - 28, 20, Component.empty(), Ui.ACCENT_NEUTRE, this::aller)
                .look(HzButton.Look.PRIMARY));
        majBouton();
    }

    private void aller() {
        ReseauGrades.VueWarp w = choisi();
        if (w == null || !etat.utilisable() || !w.permis()) return;
        ReseauGrades.CANAL.sendToServer(new ReseauGrades.AllerWarp(w.id()));
        onClose();
    }

    private void majBouton() {
        if (bouton == null) return;
        ReseauGrades.VueWarp w = choisi();
        bouton.visible = w != null;
        if (w == null) return;
        String cle = !etat.utilisable() ? "pas_spawn" : !w.permis() ? "reserve" : "aller";
        bouton.setMessage(Component.literal(t(cle)));
        bouton.active = etat.utilisable() && w.permis();
        bouton.accent(couleur(w));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int lx = px, ly = py + 34;
        for (int i = defile; i < Math.min(etat.warps().size(), defile + visibles()); i++) {
            int y = ly + 26 + (i - defile) * (LIGNE_H + ECART);
            if (mx >= lx + 8 && mx < lx + LISTE_L - 8 && my >= y && my < y + LIGNE_H) {
                if (choix != i) {
                    choix = i;
                    SonsUi.clic(0.6F);
                    majBouton();
                } else if (b == 0 && bouton.active) {
                    aller();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, etat.warps().size() - visibles());
        defile = Math.max(0, Math.min(max, defile - (int) Math.signum(delta)));
        return true;
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

        Component titre = Ui.in(Component.literal(t("titre").toUpperCase(Locale.ROOT)), Ui.BOLD);
        Ui.gros(g, f, titre, px, py + 2, 1.6f, Ui.argb(Ui.TEXT, 255));
        float tw = f.width(titre) * 1.6f;
        Ui.spaced(g, f, t("sous_titre"), Ui.SMALL, px + tw + 12, py + 8, Ui.argb(Ui.MUTED, 255), 1.0f);
        if (!etat.utilisable()) Ui.right(g, f, Ui.lit(t("pas_spawn_long"), Ui.SMALL), px + pw, py + 7, Ui.argb(Ui.AMBRE, 255));
        Ui.hairline(g, px, px + pw, py + 24, 0xFFFFFF, 60, false, true);

        // liste
        int lx = px, ly = py + 34, lh = ph - 34;
        Ui.glass(g, lx, ly, LISTE_L, lh);
        Ui.section(g, f, t("liste"), lx + 10, ly + 10, LISTE_L - 20);
        for (int i = defile; i < Math.min(etat.warps().size(), defile + visibles()); i++) {
            ReseauGrades.VueWarp w = etat.warps().get(i);
            int y = ly + 26 + (i - defile) * (LIGNE_H + ECART), x0 = lx + 8, wl = LISTE_L - 16;
            boolean survol = mx >= x0 && mx < x0 + wl && my >= y && my < y + LIGNE_H;
            if (i == choix) {
                Ui.soft(g, x0, y, wl, LIGNE_H, Ui.argb(0xFFFFFF, 18));
                Ui.softOutline(g, x0 + 1, y + 1, wl - 2, LIGNE_H - 2, Ui.argb(0xFFFFFF, 36));
            } else if (survol) {
                Ui.soft(g, x0, y, wl, LIGNE_H, Ui.argb(0xFFFFFF, 10));
            }
            g.renderItem(w.icone(), x0 + 5, y + 5);
            String nom = Ui.ellipsis(f, w.nom(), Ui.FONT, wl - 80);
            g.drawString(f, Ui.lit(nom, Ui.FONT), x0 + 26, y + 9, Ui.argb(w.permis() ? Ui.TEXT : Ui.MUTED, 255), false);
            if (w.rang() > 0) {
                String chip = t("vip");
                int cw = (int) Ui.spacedWidth(f, chip.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) + 10;
                Ui.chip(g, f, chip, x0 + wl - cw - 6, y + 8, w.permis() ? couleur(w) : Ui.FAINT);
            }
        }
        if (etat.warps().isEmpty()) Ui.spaced(g, f, t("aucun"), Ui.SMALL, lx + 10, ly + 28, Ui.argb(Ui.FAINT, 255), 0.6f);

        // warp choisi
        int rx = px + LISTE_L + 10, rw = pw - LISTE_L - 10;
        Ui.glass(g, rx, ly, rw, lh);
        ReseauGrades.VueWarp w = choisi();
        if (w != null) {
            int c = couleur(w);
            Component nom = Ui.in(Component.literal(Ui.ellipsis(f, w.nom(), Ui.BOLD, (int) ((rw - 60) / 1.4f))), Ui.BOLD);
            g.pose().pushPose();
            g.pose().translate(rx + 14, ly + 12, 0);
            g.pose().scale(1.4f, 1.4f, 1f);
            g.renderItem(w.icone(), 0, -2);
            g.pose().popPose();
            Ui.gros(g, f, nom, rx + 40, ly + 14, 1.4f, Ui.argb(w.rang() > 0 ? c : Ui.TEXT, 255));

            int y = ly + 40;
            if (!w.description().isEmpty()) {
                for (var ligne : f.split(Ui.lit(w.description(), Ui.SMALL), rw - 28)) {
                    g.drawString(f, ligne, rx + 14, y, Ui.argb(Ui.MUTED, 255), false);
                    y += 10;
                }
            }
            Ui.section(g, f, t("acces"), rx + 14, Math.max(y + 8, ly + 70), rw - 28);
            int ya = Math.max(y + 8, ly + 70) + 16;
            Grade gr = grade(w.rang());
            String acces = gr == null ? t("tous") : Component.translatable("gui.heraliz_grades.warps.grade_min", gr.nomSansCouleur()).getString();
            Ui.soft(g, rx + 14, ya + 2, 6, 6, Ui.argb(c, 255));
            g.drawString(f, Ui.lit(acces, Ui.SMALL), rx + 26, ya, Ui.argb(Ui.TEXT, 255), false);
            Ui.chip(g, f, t(w.permis() ? "ouvert" : "ferme"), rx + rw - 14 - (int) Ui.spacedWidth(f, t(w.permis() ? "ouvert" : "ferme")
                    .toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) - 10, ya - 1, w.permis() ? Ui.OK : Ui.FAINT);

            int bas = ly + lh - 14;
            Ui.spaced(g, f, t("regle"), Ui.SMALL, rx + 14, bas - 36, Ui.argb(Ui.FAINT, 255), 0.6f);
        }

        super.render(g, mx, my, pt);
    }
}
