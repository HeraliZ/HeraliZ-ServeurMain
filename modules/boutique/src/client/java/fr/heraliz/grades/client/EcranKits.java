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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Menu /kit (maquette validée, style des écrans de clan ; échelle forcée, canevas 640 x 360) : à gauche la liste des
 * kits, à droite le kit choisi (nom en couleur du grade, contenu, recharge, bouton). Le menu n'affiche que ce que le
 * serveur envoie et ne fait que DEMANDER le kit : le serveur décide de tout. Grands textes dans des polices à leur vraie
 * taille, posés sur un point de l'écran (règle du 23/10 : jamais de texte agrandi, il deviendrait flou).
 */
public final class EcranKits extends Screen {
    private static final int LISTE_L = 170, LIGNE_H = 32, CASE = 18, LIGNE_OBJET = 22;
    /** Polices grasses à leur vraie taille : titre de l'écran 18, nom du kit choisi 15. */
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_grades", "ui_titre"),
            NOM = new ResourceLocation("heraliz_grades", "ui_nom");

    private ReseauGrades.EtatKits etat;
    private long recuA;
    private int choix, defile;
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

    private static boolean soutien(ReseauGrades.VueKit k) {
        return k != null && Grade.SOUTIEN.id.equals(k.grade());
    }

    /** Titre d'un kit : son nom (réglé dans l'éditeur des grades), sinon le nom du grade. */
    private static Component titre(ReseauGrades.VueKit k) {
        return k.nom() == null || k.nom().isBlank() ? nom(k.grade()) : Component.literal(k.nom());
    }

    private long reste(ReseauGrades.VueKit k) {
        return Math.max(0, k.resteMs() - (System.currentTimeMillis() - recuA));
    }

    private static int couleur(String id) {
        Grade g = Grade.parId(id);
        return g == null ? Ui.ACCENT_NEUTRE : g.couleur;
    }

    private static Component nom(String id) {
        Grade g = Grade.parId(id);
        return g == null ? Component.literal(id) : g.nom();
    }

    /** Durée lisible : « 1 j 3 h », « 2 j », « 7 h 05 », « 7 h », « 12 min », « 40 s ». */
    private static String duree(long ms) {
        long s = Math.max(0, (ms + 999) / 1000), min = s / 60, h = min / 60, j = h / 24;
        if (j >= 1 && h % 24 == 0) return Component.translatable("item.heraliz_grades.bon.duree.j", j).getString();
        if (j >= 1) return Component.translatable("gui.heraliz_grades.duree.jh", j, h % 24).getString();
        if (h >= 1 && min % 60 == 0) return Component.translatable("item.heraliz_grades.bon.duree.h", h).getString();
        if (h >= 1) return Component.translatable("gui.heraliz_grades.duree.hm", h, String.format(java.util.Locale.ROOT, "%02d", min % 60)).getString();
        if (min >= 1) return Component.translatable("gui.heraliz_grades.duree.min", min).getString();
        return Component.translatable("gui.heraliz_grades.duree.s", s).getString();
    }

    /** Lignes de kits qui tiennent dans la liste (le reste a la molette, comme le menu /warp). */
    private int visibles() {
        return Math.max(1, (ph - 34 - 34) / (LIGNE_H + 4));
    }

    // ------------------------------------------------------------------ mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(560, width - 32);
        ph = Math.min(300, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        // le kit choisi reste visible
        int max = Math.max(0, etat.kits().size() - visibles());
        if (choix < defile) defile = choix;
        else if (choix >= defile + visibles()) defile = choix - visibles() + 1;
        defile = Math.max(0, Math.min(max, defile));
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
        boolean sien = k.debloque(), pret = sien && reste(k) <= 0;
        String cle = !sien ? "non_debloque" : attente ? "attente_serveur" : pret ? "recuperer" : "en_recharge";
        String nouvel = cle + ":" + k.id();
        if (nouvel.equals(etatBouton)) return;
        etatBouton = nouvel;
        bouton.setMessage(Component.literal(t(cle)));
        bouton.active = pret && !attente;
        bouton.accent(couleur(k.grade()));
    }

    @Override
    public void tick() {
        super.tick();
        majBouton();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int lx = px, ly = py + 34;
        for (int i = defile; i < Math.min(etat.kits().size(), defile + visibles()); i++) {
            int y = ly + 26 + (i - defile) * (LIGNE_H + 4);
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, etat.kits().size() - visibles());
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

        // en-tête
        // titre en gras 18 : même milieu des capitales que l'ancien texte 11 agrandi 1,6 fois depuis py + 2
        Component entete = Ui.lit(t("titre").toUpperCase(java.util.Locale.ROOT), TITRE);
        net(g, f, entete, px, py + 2 + 1.6f * milieu(11) - milieu(18), Ui.argb(Ui.TEXT, 255));
        float tw = f.width(entete);
        Ui.spaced(g, f, t("sous_titre"), Ui.SMALL, px + tw + 12, py + 8, Ui.argb(Ui.MUTED, 255), 1.0f);
        if (!etat.kitJoueur().isEmpty()) {
            Component droite = Ui.in(Component.literal(t("ton_kit").toUpperCase(java.util.Locale.ROOT) + "  ·  "), Ui.SMALL)
                    .append(Ui.in(nom(etat.kitJoueur().split("/")[0]).copy(), Ui.BOLD));
            Ui.right(g, f, droite, px + pw, py + 7, Ui.argb(Ui.MUTED, 255));
        }
        Ui.hairline(g, px, px + pw, py + 24, 0xFFFFFF, 60, false, true);

        // liste
        int lx = px, ly = py + 34, lh = ph - 34;
        Ui.glass(g, lx, ly, LISTE_L, lh);
        Ui.section(g, f, t("liste"), lx + 10, ly + 10, LISTE_L - 20);
        int max = Math.max(0, etat.kits().size() - visibles());
        defile = Math.max(0, Math.min(max, defile));
        for (int i = defile; i < Math.min(etat.kits().size(), defile + visibles()); i++) {
            ReseauGrades.VueKit k = etat.kits().get(i);
            int y = ly + 26 + (i - defile) * (LIGNE_H + 4), x0 = lx + 8, w = LISTE_L - 16;
            boolean survol = mx >= x0 && mx < x0 + w && my >= y && my < y + LIGNE_H;
            if (i == choix) {
                Ui.soft(g, x0, y, w, LIGNE_H, Ui.argb(0xFFFFFF, 18));
                Ui.softOutline(g, x0 + 1, y + 1, w - 2, LIGNE_H - 2, Ui.argb(0xFFFFFF, 36));
            } else if (survol) {
                Ui.soft(g, x0, y, w, LIGNE_H, Ui.argb(0xFFFFFF, 10));
            }
            Ui.soft(g, x0 + 8, y + 9, 6, 6, Ui.argb(couleur(k.grade()), 255));
            boolean sien = k.debloque();
            long resteK = reste(k);
            boolean pret = sien && resteK <= 0;
            // 25/10 (audit des écrans) : nom entier sur sa ligne (avant : coupé par la pastille, « Kit Roi de la Zo… »),
            // état en petites capitales dessous, avec le temps restant pendant la recharge
            net(g, f, Ui.in(Component.literal(Ui.ellipsis(f, titre(k).getString(), Ui.FONT, w - 28)), Ui.FONT), x0 + 20, y + 11 - milieu(11),
                    Ui.argb(sien ? Ui.TEXT : Ui.MUTED, 255));
            String etatKit = t(!sien ? "verrou" : pret ? "pret" : "attente") + (sien && !pret ? "  ·  " + duree(resteK) : "");
            Ui.spaced(g, f, Ui.ellipsisSpaced(f, etatKit, Ui.SMALL, 0.6f, w - 28), Ui.SMALL, x0 + 20, y + 19,
                    Ui.argb(!sien ? Ui.FAINT : pret ? Ui.OK : Ui.AMBRE, 255), 0.6f);
        }
        // barre de defilement de la liste
        if (max > 0) {
            int hh = visibles() * (LIGNE_H + 4) - 4, th = Math.max(10, hh * visibles() / etat.kits().size());
            int ty = ly + 26 + (hh - th) * defile / max;
            g.fill(lx + LISTE_L - 4, ty, lx + LISTE_L - 3, ty + th, Ui.argb(0xFFFFFF, 90));
        }
        if (etat.kits().isEmpty()) Ui.spaced(g, f, t("aucun"), Ui.SMALL, lx + 10, ly + 28, Ui.argb(Ui.FAINT, 255), 0.6f);

        // kit choisi
        int rx = px + LISTE_L + 10, rw = pw - LISTE_L - 10;
        Ui.glass(g, rx, ly, rw, lh);
        ReseauGrades.VueKit k = choisi();
        ItemStack survole = ItemStack.EMPTY;
        if (k != null) {
            int c = couleur(k.grade());
            boolean sien = k.debloque();
            // nom en gras 15 : même milieu des capitales que l'ancien texte 11 agrandi 1,4 fois depuis ly + 12
            Component nomKit = Ui.in(titre(k).copy(), NOM);
            net(g, f, nomKit, rx + 14, ly + 12 + 1.4f * milieu(11) - milieu(15), Ui.argb(c, 255));
            int nw = f.width(nomKit);
            Ui.chip(g, f, t(sien ? "debloque" : "non_debloque"), rx + 14 + nw + 10, ly + 13, sien ? Ui.OK : Ui.MUTED);
            String desc = soutien(k) ? t("desc_soutien") : sien ? t("desc_sien") : Component.translatable("gui.heraliz_grades.kits.desc_autre", nom(k.grade())).getString();
            g.drawString(f, Ui.lit(Ui.ellipsis(f, desc, Ui.SMALL, rw - 28), Ui.SMALL), rx + 14, ly + 30, Ui.argb(Ui.MUTED, 255), false);

            // contenu en liste (25/10, audit des écrans : des icônes seules ne disent pas ce qu'il y a) : case, nom,
            // quantité ; deux colonnes s'il ne tient pas en une, « + N autres » au-delà
            Ui.section(g, f, t("contenu"), rx + 14, ly + 48, rw - 28);
            int haut = ly + 60, parColonne = Math.max(1, (ly + lh - 14 - 52 - haut) / LIGNE_OBJET);
            int colonnes = k.objets().size() > parColonne ? 2 : 1, cl = colonnes == 2 ? (rw - 28 - 12) / 2 : rw - 28;
            int montres = Math.min(k.objets().size(), parColonne * colonnes);
            if (montres < k.objets().size()) montres--;
            for (int i = 0; i < montres; i++) {
                int sx = rx + 14 + (i / parColonne) * (cl + 12), sy = haut + (i % parColonne) * LIGNE_OBJET;
                boolean survol = mx >= sx && mx < sx + cl && my >= sy && my < sy + CASE;
                if (survol) Ui.soft(g, sx - 3, sy - 1, cl + 6, CASE + 2, Ui.argb(0xFFFFFF, 8));
                Ui.soft(g, sx, sy, CASE, CASE, Ui.argb(0x000000, 90));
                Ui.softOutline(g, sx + 1, sy + 1, CASE - 2, CASE - 2, Ui.argb(0xFFFFFF, survol ? 60 : 26));
                ItemStack s = k.objets().get(i);
                g.renderItem(s, sx + 1, sy + 1);
                String quantite = s.getCount() > 1 ? "×" + s.getCount() : "";
                float qw = quantite.isEmpty() ? 0 : f.width(Ui.lit(quantite, Ui.BOLD)) + 6;
                net(g, f, Ui.lit(Ui.ellipsis(f, s.getHoverName().getString(), Ui.FONT, (int) (cl - CASE - 8 - qw)), Ui.FONT), sx + CASE + 7,
                        sy + CASE / 2f - milieu(11), Ui.argb(Ui.TEXT, 255));
                if (!quantite.isEmpty()) {
                    net(g, f, Ui.lit(quantite, Ui.BOLD), sx + cl - f.width(Ui.lit(quantite, Ui.BOLD)), sy + CASE / 2f - milieu(11), Ui.argb(Ui.MUTED, 255));
                }
                if (survol) survole = s;
            }
            if (montres < k.objets().size()) {
                int i = montres, sx = rx + 14 + (i / parColonne) * (cl + 12), sy = haut + (i % parColonne) * LIGNE_OBJET;
                net(g, f, Ui.lit(Component.translatable("gui.heraliz_grades.kits.autres", k.objets().size() - montres).getString(), Ui.SMALL),
                        sx + CASE + 7, sy + CASE / 2f - milieu(9), Ui.argb(Ui.MUTED, 255));
            }

            // recharge
            int bas = ly + lh - 14;
            long reste = reste(k);
            String gauche, droite = "";
            float ratio;
            if (!sien) {
                gauche = soutien(k) ? t("reserve_soutien") : Component.translatable("gui.heraliz_grades.kits.reserve", nom(k.grade())).getString();
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
}
