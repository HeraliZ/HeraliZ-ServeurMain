package fr.heraliz.grades.client;

import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.HzButton;
import fr.heraliz.grades.client.kit.SonsUi;
import fr.heraliz.grades.client.kit.Ui;
import fr.heraliz.grades.reseau.ReseauBoutique;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Boutique en jeu (/boutique, maquette validée, style des écrans de clan ; canevas 640 x 360) : solde de Points
 * Boutique en haut à droite, onglets des catégories, cartes des articles, fenêtre de confirmation. L'écran n'affiche que
 * ce que le serveur envoie et ne fait que DEMANDER un achat : prix et solde sont vérifiés par le serveur et le site.
 */
public final class EcranBoutique extends Screen {
    private static final String[] CATEGORIES = {"grades", "cles", "cosmetiques"};
    private static final String LIEN_PB = "https://heraliz.fr/shop";
    private static final int COLS = 4, LIGNES = 2, CARTE_H = 112, ECART = 8;
    private static final int PB = 0xFFC23A;

    private ReseauBoutique.EtatBoutique etat;
    private String onglet = "grades";
    private int page;
    private ReseauBoutique.Offre confirmer;
    private boolean attente;
    private String message = "";
    private boolean messageOk;
    private long messageA;
    private int px, py, pw, ph;
    private HzButton oui, non, acheterPb;

    private EcranBoutique(ReseauBoutique.EtatBoutique e) {
        super(Component.translatable("gui.heraliz_grades.boutique.titre"));
        etat = e;
        if (offres().isEmpty()) {
            for (String c : CATEGORIES) {
                onglet = c;
                if (!offres().isEmpty()) break;
            }
        }
    }

    public static void recevoir(ReseauBoutique.EtatBoutique e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranBoutique b) {
            b.recevoirEtat(e);
        } else if (e.ouvrir()) {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranBoutique(e));
        }
    }

    private void recevoirEtat(ReseauBoutique.EtatBoutique e) {
        long ancien = etat.solde();
        etat = new ReseauBoutique.EtatBoutique(false, e.solde() >= 0 ? e.solde() : ancien, e.offres(), e.message(), e.ok());
        attente = false;
        if (!e.message().isEmpty()) {
            message = e.message();
            messageOk = e.ok();
            messageA = System.currentTimeMillis();
            if (e.ok()) SonsUi.ouvrir(0.8F);
        }
        confirmer = null;
        majBoutons();
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.boutique." + cle, args).getString();
    }

    private List<ReseauBoutique.Offre> offres() {
        List<ReseauBoutique.Offre> l = new ArrayList<>();
        for (ReseauBoutique.Offre o : etat.offres()) if (o.categorie().equals(onglet)) l.add(o);
        return l;
    }

    private static String pb(long v) {
        return Ui.nombre(v) + " PB";
    }

    // ------------------------------------------------------------------ mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(600, width - 32);
        ph = Math.min(330, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int cx = width / 2, cy = height / 2;
        oui = addRenderableWidget(new HzButton(cx - 104, cy + 26, 100, 20, Component.literal(t("confirmer")), PB, this::payer).look(HzButton.Look.PRIMARY));
        non = addRenderableWidget(new HzButton(cx + 4, cy + 26, 100, 20, Component.literal(t("annuler")), Ui.MUTED, () -> {
            confirmer = null;
            majBoutons();
        }));
        acheterPb = addRenderableWidget(new HzButton(px + pw - 150, py + ph - 26, 150, 18, Component.literal(t("acheter_pb")), PB, this::lienPb)
                .look(HzButton.Look.OUTLINE));
        majBoutons();
    }

    private void majBoutons() {
        if (oui == null) return;
        boolean c = confirmer != null;
        oui.visible = c;
        non.visible = c;
        oui.active = c && !attente && (etat.solde() < 0 || etat.solde() >= confirmer.prix());
        acheterPb.visible = !c;
    }

    private void lienPb() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmLinkScreen(ok -> {
            if (ok) Util.getPlatform().openUri(LIEN_PB);
            mc.setScreen(this);
        }, LIEN_PB, true));
    }

    private void payer() {
        if (confirmer == null || attente) return;
        attente = true;
        ReseauBoutique.CANAL.sendToServer(new ReseauBoutique.Acheter(confirmer.id()));
        majBoutons();
    }

    /** Rectangle de la carte n de la page (x, y, l, h). */
    private int[] carte(int n) {
        int gx = px + 14, gy = py + 64, gw = pw - 28;
        int w = (gw - (COLS - 1) * ECART) / COLS;
        return new int[]{gx + (n % COLS) * (w + ECART), gy + (n / COLS) * (CARTE_H + ECART), w, CARTE_H};
    }

    private int pages() {
        return Math.max(1, (offres().size() + COLS * LIGNES - 1) / (COLS * LIGNES));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (confirmer != null) return super.mouseClicked(mx, my, b);
        // onglets
        int ox = px + 14;
        for (String c : CATEGORIES) {
            int w = (int) Ui.spacedWidth(font, t("cat." + c).toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 24;
            if (mx >= ox && mx < ox + w && my >= py + 38 && my < py + 56) {
                if (!onglet.equals(c)) {
                    onglet = c;
                    page = 0;
                    SonsUi.clic(0.6F);
                }
                return true;
            }
            ox += w + 6;
        }
        // pages
        if (pages() > 1 && my >= py + ph - 26 && my < py + ph - 8) {
            if (mx >= px + 14 && mx < px + 34 && page > 0) {
                page--;
                SonsUi.clic(0.6F);
                return true;
            }
            if (mx >= px + 70 && mx < px + 90 && page < pages() - 1) {
                page++;
                SonsUi.clic(0.6F);
                return true;
            }
        }
        // cartes
        List<ReseauBoutique.Offre> l = offres();
        for (int n = 0; n < COLS * LIGNES; n++) {
            int i = page * COLS * LIGNES + n;
            if (i >= l.size()) break;
            int[] r = carte(n);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                confirmer = l.get(i);
                SonsUi.clic(0.7F);
                majBoutons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && confirmer != null) {
            confirmer = null;
            majBoutons();
            return true;
        }
        return super.keyPressed(key, scan, mods);
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
        Ui.glass(g, px, py, pw, ph);

        // en-tête : titre, sous-titre, solde
        Component titre = Ui.in(Component.literal(t("titre").toUpperCase(Locale.ROOT)), Ui.BOLD);
        Ui.gros(g, f, titre, px + 14, py + 12, 1.6f, Ui.argb(Ui.TEXT, 255));
        Ui.spaced(g, f, t("sous_titre"), Ui.SMALL, px + 14 + f.width(titre) * 1.6f + 12, py + 18, Ui.argb(Ui.MUTED, 255), 0.8f);
        String solde = etat.solde() < 0 ? "— PB" : pb(etat.solde());
        Component cs = Ui.in(Component.literal(solde), Ui.BOLD);
        int sw = f.width(cs) + 22, sx = px + pw - 14 - sw;
        Ui.soft(g, sx, py + 10, sw, 18, Ui.argb(PB, 30));
        Ui.softOutline(g, sx + 1, py + 11, sw - 2, 16, Ui.argb(PB, 110));
        g.drawString(f, cs, sx + 11, py + 15, Ui.argb(PB, 255), false);
        Ui.spacedRight(g, f, t("ton_solde"), Ui.SMALL, sx - 8, py + 16, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.hairline(g, px + 14, px + pw - 14, py + 32, 0xFFFFFF, 50, false, true);

        // onglets
        int ox = px + 14;
        for (String c : CATEGORIES) {
            String nom = t("cat." + c).toUpperCase(Locale.ROOT);
            int w = (int) Ui.spacedWidth(f, nom, Ui.SMALL, 0.8f) + 24;
            boolean on = onglet.equals(c), survol = confirmer == null && mx >= ox && mx < ox + w && my >= py + 38 && my < py + 56;
            Ui.soft(g, ox, py + 38, w, 18, Ui.argb(0xFFFFFF, on ? 22 : survol ? 12 : 5));
            if (on) g.fill(ox + 6, py + 55, ox + w - 6, py + 56, Ui.argb(PB, 230));
            Ui.spaced(g, f, nom, Ui.SMALL, ox + 12, py + 44, Ui.argb(on ? Ui.TEXT : Ui.MUTED, 255), 0.8f);
            ox += w + 6;
        }

        // cartes
        List<ReseauBoutique.Offre> l = offres();
        ReseauBoutique.Offre survolee = null;
        for (int n = 0; n < COLS * LIGNES; n++) {
            int i = page * COLS * LIGNES + n;
            if (i >= l.size()) break;
            ReseauBoutique.Offre o = l.get(i);
            int[] r = carte(n);
            boolean survol = confirmer == null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
            if (survol) survolee = o;
            dessinerCarte(g, f, o, r[0], r[1], r[2], r[3], survol);
        }
        if (l.isEmpty()) Ui.spacedCentered(g, f, t("vide"), Ui.SMALL, px + pw / 2f, py + 140, Ui.argb(Ui.FAINT, 255), 0.8f);

        // pied : pages, message, lien
        int bas = py + ph - 26;
        if (pages() > 1) {
            Ui.centered(g, f, Ui.lit("‹", Ui.BOLD), px + 24, bas + 5, Ui.argb(page > 0 ? Ui.TEXT : Ui.FAINT, 255));
            Ui.spacedCentered(g, f, (page + 1) + " / " + pages(), Ui.SMALL, px + 52, bas + 6, Ui.argb(Ui.MUTED, 255), 0.6f);
            Ui.centered(g, f, Ui.lit("›", Ui.BOLD), px + 80, bas + 5, Ui.argb(page < pages() - 1 ? Ui.TEXT : Ui.FAINT, 255));
        }
        long age = System.currentTimeMillis() - messageA;
        if (!message.isEmpty() && age < 6000) {
            int a = (int) (255 * Math.min(1, (6000 - age) / 600f));
            String m = t("msg." + message);
            Ui.spacedCentered(g, f, m, Ui.SMALL, px + pw / 2f, bas + 6, Ui.argb(messageOk ? Ui.OK : Ui.DANGER, Math.max(20, a)), 0.6f);
        } else {
            Ui.spacedCentered(g, f, t("aide"), Ui.SMALL, px + pw / 2f, bas + 6, Ui.argb(Ui.FAINT, 255), 0.6f);
        }

        if (confirmer != null) dessinerConfirmation(g, f);
        super.render(g, mx, my, pt);
        if (survolee != null && confirmer == null) {
            Ui.tooltip(g, f, List.of(Component.literal(survolee.nom()), Component.literal(survolee.description())), mx, my, survolee.couleur());
        }
    }

    private void dessinerCarte(GuiGraphics g, Font f, ReseauBoutique.Offre o, int x, int y, int w, int h, boolean survol) {
        int c = o.couleur();
        Ui.soft(g, x, y, w, h, Ui.argb(0x000000, 70));
        Ui.soft(g, x, y, w, h, Ui.argb(c, survol ? 26 : 12));
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(c, survol ? 150 : 60));
        g.fill(x + 1, y + 1, x + w - 1, y + 3, Ui.argb(c, 200));
        if (o.avant()) Ui.chip(g, f, t("populaire"), x + w - 8 - (int) Ui.spacedWidth(f, t("populaire").toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) - 10, y + 8, PB);
        // icône agrandie
        g.pose().pushPose();
        g.pose().translate(x + 10, y + 10, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.renderItem(o.icone(), 0, 0);
        g.pose().popPose();
        // nom sur deux lignes au plus
        List<net.minecraft.util.FormattedCharSequence> lignes = f.split(Ui.in(Component.literal(o.nom()), Ui.BOLD), w - 20);
        for (int i = 0; i < Math.min(2, lignes.size()); i++) g.drawString(f, lignes.get(i), x + 10, y + 40 + i * 11, Ui.argb(c, 255), false);
        List<net.minecraft.util.FormattedCharSequence> desc = f.split(Ui.lit(o.description(), Ui.SMALL), w - 20);
        for (int i = 0; i < Math.min(2, desc.size()); i++) g.drawString(f, desc.get(i), x + 10, y + 64 + i * 9, Ui.argb(Ui.MUTED, 255), false);
        // prix
        boolean assez = etat.solde() < 0 || etat.solde() >= o.prix();
        int by = y + h - 22;
        Ui.soft(g, x + 8, by, w - 16, 16, Ui.argb(PB, survol ? 46 : 22));
        Ui.softOutline(g, x + 9, by + 1, w - 18, 14, Ui.argb(PB, assez ? 120 : 40));
        Ui.centered(g, f, Ui.in(Component.literal(pb(o.prix())), Ui.BOLD), x + w / 2f, by + 4, Ui.argb(assez ? PB : Ui.MUTED, 255));
    }

    private void dessinerConfirmation(GuiGraphics g, Font f) {
        g.fill(0, 0, width, height, Ui.argb(0x000000, 150));
        int w = 260, h = 124, x = (width - w) / 2, y = (height - h) / 2 - 10;
        Ui.glass(g, x, y, w, h, 235);
        Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(confirmer.couleur(), 120));
        Ui.spacedCentered(g, f, t("confirmation"), Ui.SMALL, width / 2f, y + 12, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.centered(g, f, Ui.in(Component.literal(confirmer.nom()), Ui.BOLD), width / 2f, y + 28, Ui.argb(confirmer.couleur(), 255));
        Ui.centered(g, f, Ui.in(Component.literal(pb(confirmer.prix())), Ui.BOLD), width / 2f, y + 42, Ui.argb(PB, 255));
        String apres = etat.solde() < 0 ? t("solde_inconnu")
                : etat.solde() >= confirmer.prix() ? t("apres", Ui.nombre(etat.solde() - confirmer.prix())) : t("pas_assez", Ui.nombre(confirmer.prix() - etat.solde()));
        Ui.spacedCentered(g, f, apres, Ui.SMALL, width / 2f, y + 56, Ui.argb(etat.solde() >= 0 && etat.solde() < confirmer.prix() ? Ui.DANGER : Ui.MUTED, 255), 0.6f);
        if (attente) Ui.spacedCentered(g, f, t("attente"), Ui.SMALL, width / 2f, y + 70, Ui.argb(Ui.FAINT, 255), 0.6f);
    }
}
