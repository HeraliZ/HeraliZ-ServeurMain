package fr.heraliz.quetes.client;

import fr.heraliz.quetes.bloc.MenuRecycleur;
import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.Net;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

/**
 * Écran du recycleur (25/10, maquette validée : maquettes/recycleur/, modules/pnj/outils/apercu_recycleur.py, mêmes
 * mesures) : à gauche « À recycler », au milieu l'objet en cours et sa barre qui avance, à droite « Récupéré » et « Tout
 * prendre » ; en bas l'inventaire et l'aide ; à droite l'aperçu de l'objet survolé (ce qu'il peut donner, chances,
 * moyenne, hérals si rien ne sort, ou pourquoi il est refusé). Le serveur décide de tout ({@code MenuRecycleurServeur}) :
 * l'écran lit l'état synchronisé du menu et l'aperçu envoyé par le serveur. Panneau 560 x 324 au style des écrans du clan,
 * polices à leur vraie taille.
 */
public final class EcranRecycleur extends AbstractContainerScreen<MenuRecycleur> {
    private static final int L = 560, H = 324, ENTETE = 44, M = 16, GAUCHE = 360, CASE = MenuRecycleur.CASE;
    private static final int BLANC = 0xFFFFFF;
    private static final int[] RARETES = {0xAAAAAA, 0x5FD06E, 0x508CFF, 0xB96EF0, 0xF0BE46, 0xEB4646};
    private static final int[] ETATS = {Ui.OK, Ui.FAINT, Ui.DANGER, Ui.ACCENT_NEUTRE, Ui.AMBRE};
    private static final ResourceLocation TITRE = Net.TITRE, PG = Net.PETIT_GRAS, MICRO = Net.MICRO;

    /** Dernier aperçu reçu (celui du menu ouvert : même numéro de conteneur). */
    private static Reseau.ApercuRecyclage apercu;

    public EcranRecycleur(MenuRecycleur menu, Inventory inv, Component titre) {
        super(menu, inv, titre);
        imageWidth = L;
        imageHeight = H;
    }

    static void recevoir(Reseau.ApercuRecyclage a) {
        apercu = a;
    }

    private static String t(String cle, Object... args) {
        return TexteClient.tr("gui.heraliz_quetes.recycleur." + cle, args);
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        super.init();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        super.render(g, mx, my, pt);
        apercu(g, mx, my);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // tout est dessiné par renderBg et apercu (polices du mod, places de la maquette)
    }

    // ------------------------------------------------------------------ fond, en-tête, machine, inventaire, pied
    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        Font f = font;
        int x = leftPos, y = topPos;
        Ui.glass(g, x, y, L, H, 214);
        entete(g, f, x, y, mx, my);
        for (int k = 0; k < menu.slots.size(); k++) {
            Slot s = menu.slots.get(k);
            float cx = x + s.x - 2, cy = y + s.y - 2;
            Net.rect(g, cx, cy, cx + CASE, cy + CASE, Ui.argb(0x000000, 90));
            boolean survol = hoveredSlot == s;
            Net.cadre(g, cx, cy, CASE, CASE, survol ? Ui.argb(Ui.OR, 200) : Ui.argb(BLANC, 34));
        }
        int y0 = y + ENTETE + 10;
        machine(g, f, x, y0, mx, my);
        inventaire(g, f, x, y0 + 96);
        pied(g, f, x, y);
    }

    private void entete(GuiGraphics g, Font f, int x, int y, int mx, int my) {
        icone(g, x + M, y + 10, Ui.OR);
        float tx = x + M + 34;
        Net.texte(g, f, TexteClient.tr("heraliz_quetes.recycleur.titre"), TITRE, tx, y + 9 + Net.cap(TITRE) / 2, Ui.TEXT);
        Net.texte(g, f, Net.couper(f, t("sous_titre"), Ui.SMALL, 300), Ui.SMALL, tx, y + 27 + Net.cap(Ui.SMALL) / 2, Ui.MUTED);
        float xd = x + L - 34;
        String par = t("par_objet", dixiemes(menu.donnee(MenuRecycleur.D_DIXIEMES)));
        puce(g, f, par, xd - Net.capsLargeur(f, par, MICRO, 0.8f) - 10, y + 16, Ui.MUTED, false);
        float fx = x + L - 18, fy = y + 16;
        boolean survol = mx >= fx - 8 && mx < fx + 8 && my >= fy - 8 && my < fy + 8;
        Net.centre(g, f, "×", Ui.BOLD, fx, fy, survol ? Ui.TEXT : Ui.MUTED);
        Net.rect(g, x + 8, y + ENTETE, x + L - 8, y + ENTETE + Net.point(), Ui.argb(BLANC, 40));
    }

    /** Pictogramme : engrenage dans un anneau, deux pointes de flèche vertes. */
    private static void icone(GuiGraphics g, float x, float y, int c) {
        float cx = x + 12, cy = y + 12;
        Net.disque(g, cx, cy, 11, Ui.argb(c, 40));
        Net.anneau(g, cx, cy, 11, 1.1f, Ui.argb(c, 200));
        Net.anneau(g, cx, cy, 4.2f, 1.4f, Ui.argb(c, 220));
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            Net.trait(g, cx + 5.4f * (float) Math.cos(a), cy + 5.4f * (float) Math.sin(a), cx + 7f * (float) Math.cos(a),
                    cy + 7f * (float) Math.sin(a), 1.6f, Ui.argb(c, 220));
        }
        Net.triangle(g, cx + 9, cy - 9.5f, cx + 12.5f, cy - 4, cx + 6, cy - 4.5f, Ui.argb(Ui.OK, 255));
        Net.triangle(g, cx - 9, cy + 9.5f, cx - 12.5f, cy + 4, cx - 6, cy + 4.5f, Ui.argb(Ui.OK, 255));
    }

    private void libelle(GuiGraphics g, Font f, String txt, float x, float y, float largeur) {
        float w = Net.caps(g, f, txt, PG, 1.0f, x, y + Net.cap(PG) / 2, Ui.MUTED);
        Net.rect(g, x + w + 6, y + 2.6f, x + largeur, y + 2.6f + Net.point(), Ui.argb(BLANC, 30));
    }

    private static void chevrons(GuiGraphics g, float x, float cy, int c, boolean actif) {
        for (int k = 0; k < 3; k++) {
            float xx = x + k * 4;
            int a = (actif ? 150 : 90) - k * 35;
            Net.trait(g, xx, cy - 3, xx + 2.6f, cy, 0.9f, Ui.argb(c, a));
            Net.trait(g, xx + 2.6f, cy, xx, cy + 3, 0.9f, Ui.argb(c, a));
        }
    }

    private void machine(GuiGraphics g, Font f, int x, int y0, int mx, int my) {
        int x0 = x + M;
        int etat = menu.donnee(MenuRecycleur.D_ETAT), caseEnCours = menu.donnee(MenuRecycleur.D_CASE);
        boolean actif = etat == MenuRecycleur.EN_COURS;
        libelle(g, f, t("entree"), x0, y0, 66);
        float mx0 = x0 + 84, mx1 = x0 + GAUCHE - 84;
        chevrons(g, x0 + 70, y0 + 33, actif ? Ui.OR : Ui.FAINT, actif);
        chevrons(g, mx1 + 6, y0 + 33, actif ? Ui.OR : Ui.FAINT, actif);
        libelle(g, f, t("en_cours"), mx0, y0, mx1 - mx0);
        ItemStack enCours = caseEnCours >= 0 && caseEnCours < MenuRecycleur.ENTREES ? menu.slots.get(caseEnCours).getItem() : ItemStack.EMPTY;
        float by = y0 + 47;
        if ((etat == MenuRecycleur.EN_COURS || etat == MenuRecycleur.PLEIN) && !enCours.isEmpty()) {
            int rang = rang(caseEnCours);
            float bx = mx0, bxy = y0 + 13;
            Net.rect(g, bx, bxy, bx + 26, bxy + 26, Ui.argb(0x000000, 90));
            if (rang >= 0) {
                Net.degrade(g, bx, bxy + 9, bx + 26, bxy + 26, Ui.argb(RARETES[rang], 0), Ui.argb(RARETES[rang], 60));
                Net.rect(g, bx + 2, bxy + 26 - 1.4f, bx + 24, bxy + 26 - 0.6f, Ui.argb(RARETES[rang], 230));
            }
            Net.cadre(g, bx, bxy, 26, 26, Ui.argb(BLANC, 34));
            g.flush();
            g.renderItem(enCours, (int) bx + 5, (int) bxy + 5);
            float nomX = mx0 + 32;
            Net.texte(g, f, Net.couper(f, enCours.getHoverName().getString(), Ui.BOLD, mx1 - nomX), Ui.BOLD, nomX, y0 + 15 + Net.cap(Ui.BOLD) / 2, Ui.TEXT);
            if (rang >= 0) {
                Net.texte(g, f, TexteClient.tr("gui.heraliz_quetes.rarete." + rang), Ui.SMALL, nomX, y0 + 28 + Net.cap(Ui.SMALL) / 2, RARETES[rang]);
            }
            float p = Math.min(1f, menu.donnee(MenuRecycleur.D_PROGRES) / 1000f);
            Net.rect(g, mx0, by, mx1, by + 2.4f, Ui.argb(BLANC, 26));
            Net.rect(g, mx0, by, mx0 + (mx1 - mx0) * p, by + 2.4f, Ui.argb(actif ? Ui.OR : Ui.FAINT, 235));
            Net.disque(g, mx0 + (mx1 - mx0) * p, by + 1.2f, 2.4f, Ui.argb(actif ? Ui.OR : Ui.FAINT, 255));
            if (actif) {
                Net.texte(g, f, t("progression", Math.round(p * 100)), Ui.SMALL, mx0, by + 9, Ui.MUTED);
                float reste = (1 - p) * menu.donnee(MenuRecycleur.D_DIXIEMES) / 10f;
                Net.droite(g, f, String.format(Locale.ROOT, "%.1f s", reste).replace('.', ','), PG, mx1, by + 9, Ui.TEXT);
                int attente = menu.donnee(MenuRecycleur.D_ATTENTE);
                if (attente > 0) Net.texte(g, f, t("attente", attente), Ui.SMALL, mx0, by + 20, Ui.FAINT);
            } else {
                Net.texte(g, f, Net.couper(f, t("plein.1") + " " + t("plein.2"), Ui.SMALL, mx1 - mx0), Ui.SMALL, mx0, by + 9, Ui.AMBRE);
            }
        } else {
            String l1 = etat == MenuRecycleur.RIEN ? t("rien.1") : t("vide.1"), l2 = etat == MenuRecycleur.RIEN ? t("rien.2") : t("vide.2");
            Net.centre(g, f, Net.couper(f, l1, Ui.SMALL, mx1 - mx0), Ui.SMALL, (mx0 + mx1) / 2, y0 + 26, Ui.MUTED);
            Net.centre(g, f, Net.couper(f, l2, Ui.SMALL, mx1 - mx0), Ui.SMALL, (mx0 + mx1) / 2, y0 + 36, Ui.MUTED);
            Net.rect(g, mx0, by, mx1, by + 2.4f, Ui.argb(BLANC, 18));
        }
        float ox = x0 + GAUCHE - 66;
        libelle(g, f, t("sortie"), ox, y0, 66);
        boolean sortie = false;
        for (int i = 0; i < MenuRecycleur.SORTIES; i++) sortie |= menu.slots.get(MenuRecycleur.ENTREES + i).hasItem();
        float bx = ox - 8, bcy = y0 + 66, bw = 72;
        boolean survol = sortie && mx >= bx && mx < bx + bw && my >= bcy - 7 && my < bcy + 7;
        Net.rect(g, bx, bcy - 7, bx + bw, bcy + 7, Ui.argb(Ui.OR, sortie ? (survol ? 90 : 60) : 20));
        Net.cadre(g, bx, bcy - 7, bw, 14, Ui.argb(Ui.OR, sortie ? 220 : 80));
        Net.capsCentre(g, f, t("tout_prendre"), PG, 1.0f, bx + bw / 2, bcy, sortie ? Ui.TEXT : Ui.FAINT);
        int herals = menu.donnee(MenuRecycleur.D_HERALS);
        if (herals > 0) {
            String h = Net.couper(f, herals == 1 ? t("herals_un") : t("herals", herals), Ui.SMALL, 96);
            Net.centre(g, f, h, Ui.SMALL, ox + 28, y0 + 81, Ui.OR);
        }
    }

    private void inventaire(GuiGraphics g, Font f, int x, int y0) {
        int x0 = x + M;
        libelle(g, f, t("inventaire"), x0, y0, 9 * (CASE + 2) - 2);
        float ax = x0 + 9 * (CASE + 2) + 10, aw = x + M + GAUCHE - ax;
        String[][] aides = {{t("touche.maj"), t("aide.maj")}, {t("touche.survol"), t("aide.survol")}, {t("touche.echap"), t("aide.echap")}};
        float yy = y0 + 18;
        for (String[] a : aides) {
            touche(g, f, a[0], ax, yy);
            List<FormattedCharSequence> l = f.split(Ui.lit(a[1], Ui.SMALL), (int) aw);
            for (int i = 0; i < Math.min(2, l.size()); i++) Net.ligne(g, f, l.get(i), Ui.SMALL, ax, yy + 7 + Net.cap(Ui.SMALL) / 2 + i * 9, Ui.MUTED);
            yy += 30;
        }
        Net.texte(g, f, Net.couper(f, t("note_achetes"), Ui.SMALL, aw), Ui.SMALL, ax, y0 + 12 + 4 * (CASE + 2) - 4 + Net.cap(Ui.SMALL) / 2, Ui.FAINT);
    }

    private float touche(GuiGraphics g, Font f, String txt, float x, float cy) {
        float w = Net.capsLargeur(f, txt, MICRO, 0.8f) + 8;
        Net.rect(g, x, cy - 4.5f, x + w, cy + 4.5f, Ui.argb(BLANC, 18));
        Net.cadre(g, x, cy - 4.5f, w, 9, Ui.argb(BLANC, 60));
        Net.capsCentre(g, f, txt, MICRO, 0.8f, x + w / 2, cy, Ui.TEXT);
        return w;
    }

    private void pied(GuiGraphics g, Font f, int x, int y) {
        float cy = y + H - 12;
        Net.rect(g, x + 8, cy - 8, x + L - 8, cy - 8 + Net.point(), Ui.argb(BLANC, 22));
        float px = x + M;
        String[][] aides = {{t("touche.echap"), t("pied.fermer")}, {t("touche.maj"), t("pied.mettre")}, {t("touche.clic"), t("pied.prendre")}};
        for (String[] a : aides) {
            px += touche(g, f, a[0], px, cy) + 4;
            px += Net.texte(g, f, a[1], Ui.SMALL, px, cy, Ui.MUTED) + 12;
        }
    }

    private float puce(GuiGraphics g, Font f, String txt, float x, float cy, int c, boolean plein) {
        float w = Net.capsLargeur(f, txt, MICRO, 0.8f) + 10;
        Net.rect(g, x, cy - 5, x + w, cy + 5, Ui.argb(c, plein ? 70 : 26));
        Net.cadre(g, x, cy - 5, w, 10, Ui.argb(c, 170));
        Net.capsCentre(g, f, txt, MICRO, 0.8f, x + w / 2, cy, plein ? Ui.TEXT : c);
        return w;
    }

    // ------------------------------------------------------------------ aperçu
    /** Aperçu de la case survolée, sinon de l'objet en cours. */
    private void apercu(GuiGraphics g, int mx, int my) {
        Font f = font;
        int y0 = topPos + ENTETE + 10, h = H - ENTETE - 34;
        float x = leftPos + M + GAUCHE + 12, w = leftPos + L - M - x, xi = x + 10;
        Ui.glass(g, (int) x, y0 - 2, (int) w, h + 2, 110);
        libelle(g, f, t("apercu"), xi, y0 + 6, w - 20);
        int index = -1;
        if (hoveredSlot != null && hoveredSlot.hasItem() && !MenuRecycleur.estSortie(hoveredSlot.index)) index = hoveredSlot.index;
        else if (menu.donnee(MenuRecycleur.D_CASE) >= 0 && menu.donnee(MenuRecycleur.D_ETAT) != MenuRecycleur.VIDE) index = menu.donnee(MenuRecycleur.D_CASE);
        Reseau.ApercuCase a = index >= 0 ? cas(index) : null;
        if (a == null || !menu.slots.get(index).hasItem()) {
            List<FormattedCharSequence> l = f.split(Ui.lit(t("apercu_vide"), Ui.SMALL), (int) w - 20);
            for (int i = 0; i < l.size(); i++) {
                float lw = f.getSplitter().stringWidth(l.get(i));
                Net.ligne(g, f, l.get(i), Ui.SMALL, x + w / 2 - lw / 2, y0 + h / 2f - 4 + i * 9, Ui.FAINT);
            }
            return;
        }
        ItemStack objet = menu.slots.get(index).getItem();
        int rang = Math.max(0, Math.min(5, a.rang()));
        float iy = y0 + 18;
        Net.rect(g, xi, iy, xi + 28, iy + 28, Ui.argb(0x000000, 90));
        Net.degrade(g, xi, iy + 10, xi + 28, iy + 28, Ui.argb(RARETES[rang], 0), Ui.argb(RARETES[rang], 60));
        Net.rect(g, xi + 2, iy + 28 - 1.4f, xi + 26, iy + 28 - 0.6f, Ui.argb(RARETES[rang], 230));
        Net.cadre(g, xi, iy, 28, 28, Ui.argb(BLANC, 34));
        g.flush();
        g.renderItem(objet, (int) xi + 6, (int) iy + 6);
        float nx = xi + 34, nw = x + w - 10 - nx;
        Net.texte(g, f, Net.couper(f, objet.getHoverName().getString(), Ui.BOLD, nw), Ui.BOLD, nx, y0 + 21 + Net.cap(Ui.BOLD) / 2, Ui.TEXT);
        String sous = TexteClient.tr("gui.heraliz_quetes.rarete." + rang) + (a.par() > 1 ? " · " + t("paquet", a.par()) : "")
                + (a.usure() < 100 ? " · " + t("etat_objet", a.usure()) : "");
        Net.texte(g, f, Net.couper(f, sous, Ui.SMALL, nw), Ui.SMALL, nx, y0 + 34 + Net.cap(Ui.SMALL) / 2, RARETES[rang]);
        float y = y0 + 58;
        // objet abîmé : chances réduites selon son état (26/10, facteur envoyé par le serveur, en millièmes)
        float chance = (apercu == null ? 0.1f : apercu.chance()) * Math.max(0, Math.min(1000, a.facteur())) / 1000f;
        int max = apercu == null ? 2 : apercu.maxPieces();
        if (!a.composants().isEmpty() && a.etat() != 1 && a.etat() != 3) {
            libelle(g, f, t("peut_donner"), xi, y, w - 20);
            y += 14;
            int lignes = Math.min(a.composants().size(), 5);
            for (int i = 0; i < lignes; i++) {
                ItemStack c = composant(a.composants().get(i));
                int n = a.nombres().get(i);
                int col = couleur(c);
                Net.rect(g, xi, y - 1, xi + 14, y + 13, Ui.argb(0x000000, 80));
                if (col != 0) Net.rect(g, xi + 2, y + 11.6f, xi + 12, y + 12.4f, Ui.argb(col, 220));
                g.flush();
                g.pose().pushPose();
                g.pose().translate(xi + 1, y, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.renderItem(c, 0, 0);
                g.pose().popPose();
                Net.texte(g, f, Net.couper(f, c.getHoverName().getString(), Ui.SMALL, w - 70), Ui.SMALL, xi + 19, y + 6, Ui.TEXT);
                int pct = Math.round((float) (1 - Math.pow(1 - chance, n)) * 100);
                Net.droite(g, f, pct + " %", PG, x + w - 10, y + 6, Ui.TEXT);
                y += 17;
            }
            List<FormattedCharSequence> l = f.split(Ui.lit(t("chance", Math.round(1 / Math.max(0.001f, chance)), max), Ui.SMALL), (int) w - 20);
            for (int i = 0; i < Math.min(2, l.size()); i++) Net.ligne(g, f, l.get(i), Ui.SMALL, xi, y + 3 + i * 9, Ui.MUTED);
            y += 4 + Math.min(2, l.size()) * 9 + 3;
            Net.rect(g, xi, y, x + w - 10, y + Net.point(), Ui.argb(BLANC, 26));
            y += 7;
        }
        if (a.etat() == 0 || a.etat() == 4) {
            int total = 0;
            for (int n : a.nombres()) total += n;
            double rien = Math.pow(1 - chance, total), moyenne = moyenne(total, chance, max);
            Net.texte(g, f, t("moyenne"), Ui.SMALL, xi, y + 4, Ui.MUTED);
            Net.droite(g, f, t("pieces", String.format(Locale.ROOT, "%.1f", moyenne).replace('.', ',')), PG, x + w - 10, y + 4, Ui.OK);
            Net.texte(g, f, Net.couper(f, t("rien_du_tout", Math.round(rien * 100)), Ui.SMALL, w - 70), Ui.SMALL, xi, y + 16, Ui.MUTED);
            Net.droite(g, f, a.herals() == 1 ? t("herals_n_un") : t("herals_n", a.herals()), PG, x + w - 10, y + 16, Ui.OR);
            if (a.etat() == 4) {
                List<FormattedCharSequence> l = f.split(Ui.lit(t("raison.4", a.par()), Ui.SMALL), (int) w - 20);
                for (int i = 0; i < Math.min(2, l.size()); i++) Net.ligne(g, f, l.get(i), Ui.SMALL, xi, y + 30 + i * 9, Ui.AMBRE);
            }
        } else {
            List<FormattedCharSequence> l = f.split(Ui.lit(t("raison." + a.etat()), Ui.SMALL), (int) w - 20);
            for (int i = 0; i < Math.min(4, l.size()); i++) Net.ligne(g, f, l.get(i), Ui.SMALL, xi, y + 4 + i * 9, a.etat() == 2 ? Ui.DANGER : Ui.MUTED);
        }
        int etat = Math.max(0, Math.min(4, a.etat()));
        puce(g, f, t("etat." + etat), xi, y0 + h - 14, ETATS[etat], true);
    }

    private static Reseau.ApercuCase cas(int index) {
        if (apercu == null || Minecraft.getInstance().player == null
                || apercu.conteneur() != Minecraft.getInstance().player.containerMenu.containerId) return null;
        for (Reseau.ApercuCase c : apercu.cases()) if (c.index() == index) return c;
        return null;
    }

    private static int rang(int index) {
        Reseau.ApercuCase c = cas(index);
        return c == null ? -1 : Math.max(0, Math.min(5, c.rang()));
    }

    private static ItemStack composant(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
    }

    /** Couleur de rareté d'un objet (HeraliZ Loot, par réflexion ; 0 : aucune). */
    private static java.lang.reflect.Method couleurRarete;
    private static boolean couleurCherchee;

    private static int couleur(ItemStack s) {
        if (!couleurCherchee) {
            couleurCherchee = true;
            try {
                couleurRarete = Class.forName("fr.heraliz.loot.client.RaretesClient").getMethod("couleur", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError ignore) {
                couleurRarete = null;
            }
        }
        if (couleurRarete == null) return 0;
        try {
            return (int) couleurRarete.invoke(null, s);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return 0;
        }
    }

    /** Nombre moyen de pièces : loi binomiale (total, chance) plafonnée à max. */
    private static double moyenne(int total, double chance, int max) {
        double m = 0, p = Math.pow(1 - chance, total), q = chance / (1 - chance);
        for (int k = 0; k <= total; k++) {
            m += Math.min(k, max) * p;
            p = p * (total - k) / (k + 1) * q;
        }
        return m;
    }

    private static String dixiemes(int d) {
        return d % 10 == 0 ? Integer.toString(d / 10) : String.format(Locale.ROOT, "%.1f", d / 10f).replace('.', ',');
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int x0 = leftPos + M, y0 = topPos + ENTETE + 10;
        float ox = x0 + GAUCHE - 66, bx = ox - 8, bcy = y0 + 66;
        if (b == 0 && mx >= bx && mx < bx + 72 && my >= bcy - 7 && my < bcy + 7) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MenuRecycleur.BOUTON_TOUT_PRENDRE);
                SonsUi.clic(0.6F);
            }
            return true;
        }
        float fx = leftPos + L - 18, fy = topPos + 16;
        if (b == 0 && mx >= fx - 8 && mx < fx + 8 && my >= fy - 8 && my < fy + 8) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }
}
