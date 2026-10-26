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
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;

/**
 * Menu /warp (style des écrans de clan, comme le menu /kit ; échelle forcée, canevas 640 x 360) : à gauche la liste des
 * warps, à droite le warp choisi (nom, description, accès, bouton), ou un état vide centré quand il n'y a aucun warp. Le
 * menu n'affiche que ce que le serveur envoie et ne fait que DEMANDER le warp : lieu, grade et règles sont décidés par le
 * serveur. Grands textes dans des polices à leur vraie taille, posés sur un point de l'écran (règle du 23/10 : jamais de
 * texte agrandi, il deviendrait flou).
 */
public final class EcranWarps extends Screen {
    private static final int LISTE_L = 190, LIGNE_H = 26, ECART = 4;
    /** Polices grasses à leur vraie taille : titre de l'écran 18, nom du warp choisi 15. */
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_grades", "ui_titre"),
            NOM = new ResourceLocation("heraliz_grades", "ui_nom");

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

        // titre en gras 18 : même milieu des capitales que l'ancien texte 11 agrandi 1,6 fois depuis py + 2
        Component titre = Ui.lit(t("titre").toUpperCase(Locale.ROOT), TITRE);
        net(g, f, titre, px, py + 2 + 1.6f * milieu(11) - milieu(18), Ui.argb(Ui.TEXT, 255));
        float tw = f.width(titre);
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
            // nom en gras 15, coupé à la même largeur qu'avant (rw - 60)
            Component nom = Ui.lit(Ui.ellipsis(f, w.nom(), NOM, rw - 60), NOM);
            g.pose().pushPose();
            g.pose().translate(rx + 14, ly + 12, 0);
            g.pose().scale(1.4f, 1.4f, 1f);
            g.renderItem(w.icone(), 0, -2);
            g.pose().popPose();
            // même milieu des capitales que l'ancien texte 11 agrandi 1,4 fois depuis ly + 14
            net(g, f, nom, rx + 40, ly + 14 + 1.4f * milieu(11) - milieu(15), Ui.argb(w.rang() > 0 ? c : Ui.TEXT, 255));

            int y = ly + 40;
            if (!w.description().isEmpty()) {
                // lignes plafonnees : l'acces, la regle et le bouton restent a leur place (« … » sur la derniere)
                List<FormattedText> lignes = f.getSplitter().splitLines(Ui.lit(w.description(), Ui.SMALL), rw - 28, Style.EMPTY);
                int maxLignes = Math.max(1, (lh - 126) / 10);
                for (int i = 0; i < Math.min(lignes.size(), maxLignes); i++) {
                    String s = lignes.get(i).getString();
                    if (i == maxLignes - 1 && lignes.size() > maxLignes) s = Ui.ellipsis(f, s + " " + lignes.get(i + 1).getString(), Ui.SMALL, rw - 28);
                    g.drawString(f, Ui.lit(s, Ui.SMALL), rx + 14, y, Ui.argb(Ui.MUTED, 255), false);
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
        } else {
            vide(g, f, rx, ly, rw, lh);
        }

        super.render(g, mx, my, pt);
    }

    /**
     * Aucun warp : état vide centré dans le panneau de droite, titre en gras et une ligne d'aide en petit gris (pour le
     * staff, la commande qui pose un warp ; le serveur garde seul le droit de l'utiliser).
     */
    private void vide(GuiGraphics g, Font f, int x, int y, int w, int h) {
        boolean staff = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
        List<FormattedCharSequence> aide = f.split(Ui.lit(t(staff ? "vide_aide_staff" : "vide_aide"), Ui.SMALL), w - 60);
        // bloc centré : titre (milieu des capitales) 14 au-dessus de la première ligne d'aide, lignes de 10
        float cx = x + w / 2f, cyTitre = y + h / 2f - 7 - (aide.size() - 1) * 5f;
        Component titre = Ui.lit(t("vide_titre"), Ui.BOLD);
        net(g, f, titre, cx - f.width(titre) / 2f, cyTitre - milieu(11), Ui.argb(Ui.TEXT, 255));
        for (int i = 0; i < aide.size(); i++) {
            FormattedCharSequence l = aide.get(i);
            net(g, f, l, cx - f.width(l) / 2f, cyTitre + 14 + i * 10 - milieu(9), Ui.argb(Ui.MUTED, 255));
        }
    }

    // ------------------------------------------------------------------ texte à sa vraie taille, posé net
    /** Milieu des capitales sous le point de dessin, polices DM Sans du mod (décalage 1) : taille x 0,49309 - 1. */
    private static float milieu(float taille) {
        return taille * 0.49309f - 1f;
    }

    /** Texte dessiné à sa vraie taille, posé sur un point entier de l'écran ; (x, y) = point de dessin. */
    private static void net(GuiGraphics g, Font f, Component c, float x, float y, int argb) {
        g.pose().pushPose();
        Ui.placer(g, x, y);
        g.drawString(f, c, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** Ligne déjà coupée, posée comme {@link #net(GuiGraphics, Font, Component, float, float, int)}. */
    private static void net(GuiGraphics g, Font f, FormattedCharSequence l, float x, float y, int argb) {
        g.pose().pushPose();
        Ui.placer(g, x, y);
        g.drawString(f, l, 0, 0, argb, false);
        g.pose().popPose();
    }
}
