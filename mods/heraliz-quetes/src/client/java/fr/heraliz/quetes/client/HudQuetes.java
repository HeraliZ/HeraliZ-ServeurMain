package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * À l'écran, sans rien ouvrir : la quête suivie, en petit à droite (choisie avec « Suivre » dans /quetes), et les
 * notifications courtes en haut à droite (« Étape terminée », « Quête terminée : +300 hérals »).
 */
public final class HudQuetes {
    private HudQuetes() {
    }

    private static final int LARGEUR = 150;
    /** Taille du suivi à l'écran (1 = taille de l'interface du jeu : jugée beaucoup trop grosse). */
    private static final float ECHELLE = 0.7F;
    /** Taille des notifications en haut à droite. */
    private static final float ECHELLE_NOTIFS = 0.8F;
    private static Reseau.Suivi suivi = new Reseau.Suivi(fr.heraliz.quetes.Texte.VIDE, fr.heraliz.quetes.Texte.VIDE, List.of());

    /** Une notification ; ses lignes coupées à la largeur sont calculées à sa première image, pas à chaque image. */
    private static final class Notif {
        final String texte;
        final int couleur;
        final long debut;
        List<FormattedCharSequence> lignes;
        int largeur;

        Notif(String texte, int couleur, long debut) {
            this.texte = texte;
            this.couleur = couleur;
            this.debut = debut;
        }
    }

    /**
     * Mise en page de la quête suivie (lignes coupées, textes de droite, hauteur) : refaite seulement quand le serveur
     * envoie un nouveau suivi ou que la langue change. Avant, tout était recoupé (Font.split) à chaque image tant qu'une
     * quête était suivie.
     */
    private record Page(Reseau.Suivi suivi, String langue, String pnj, List<FormattedCharSequence> titre,
                        List<List<FormattedCharSequence>> lignes, List<String> droites, int hauteur) {
    }

    private static Page page;

    private static final List<Notif> NOTIFS = new ArrayList<>();
    private static final long NOTIF_MS = 4500;

    static void suivi(Reseau.Suivi s) {
        suivi = s;
    }

    static void notif(Reseau.Notif n) {
        String s = TexteClient.str(n.texte());
        if (s.isEmpty()) return;
        NOTIFS.add(new Notif(s, n.couleur(), System.currentTimeMillis()));
        while (NOTIFS.size() > 4) NOTIFS.remove(0);
        SonsUi.clic(0.35F);
    }

    static void vider() {
        suivi = new Reseau.Suivi(fr.heraliz.quetes.Texte.VIDE, fr.heraliz.quetes.Texte.VIDE, List.of());
        page = null;
        NOTIFS.clear();
    }

    static void rendu(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        if (NOTIFS.isEmpty() && suivi.titre().vide()) return;
        Font f = mc.font;
        // rien que des aplats et du texte : envoyés en un seul lot (sinon un dessin par aplat et par texte, à chaque image)
        g.drawManaged(() -> {
            g.pose().pushPose();
            g.pose().scale(ECHELLE_NOTIFS, ECHELLE_NOTIFS, 1);
            notifs(g, f, (int) (w / ECHELLE_NOTIFS));
            g.pose().popPose();
            if (suivi.titre().vide()) return;
            g.pose().pushPose();
            g.pose().scale(ECHELLE, ECHELLE, 1);
            dessinerSuivi(g, f, (int) (w / ECHELLE), (int) (h / ECHELLE));
            g.pose().popPose();
        });
    }

    private static Page page(Font f) {
        Reseau.Suivi s = suivi;
        String langue = fr.heraliz.quetes.Langue.code();
        Page p = page;
        if (p != null && p.suivi() == s && p.langue().equals(langue)) return p;
        List<FormattedCharSequence> titre = f.split(Ui.lit(TexteClient.str(s.titre()), Ui.BOLD), LARGEUR - 16);
        List<List<FormattedCharSequence>> lignes = new ArrayList<>();
        List<String> droites = new ArrayList<>();
        for (Reseau.SLigne l : s.lignes()) {
            String droite = "";
            if (l.total() > 1) droite = l.fait() + "/" + l.total();
            if (l.distance() >= 0) droite = (droite.isEmpty() ? "" : droite + " · ") + TexteClient.distance(l.distance(), l.approx());
            droites.add(droite);
            lignes.add(f.split(Ui.lit(TexteClient.str(l.libelle()), Ui.FONT), LARGEUR - 22));
        }
        int hauteur = 14 + titre.size() * 10 + 4;
        for (int i = 0; i < lignes.size(); i++) hauteur += lignes.get(i).size() * 10 + (droites.get(i).isEmpty() ? 0 : 9) + 3;
        p = new Page(s, langue, TexteClient.str(s.pnj()), titre, lignes, droites, hauteur);
        page = p;
        return p;
    }

    /** La quête suivie, dessinée dans un repère réduit (w, h : taille de l'écran dans ce repère). */
    private static void dessinerSuivi(GuiGraphics g, Font f, int w, int h) {

        // quête suivie : à droite, au tiers de la hauteur (mise en page gardée tant que le suivi ne change pas)
        Page page = page(f);
        Reseau.Suivi suivi = page.suivi();
        List<FormattedCharSequence> titre = page.titre();
        List<List<FormattedCharSequence>> lignes = page.lignes();
        List<String> droites = page.droites();
        int hauteur = page.hauteur();
        int x = w - LARGEUR - 6, y = Math.max(40, h / 3 - hauteur / 2);
        Ui.glass(g, x, y, LARGEUR, hauteur, 120);
        g.fill(x + 1, y + 3, x + 3, y + hauteur - 3, Ui.argb(Ui.OR, 200));
        Ui.spaced(g, f, page.pnj(), Ui.SMALL, x + 9, y + 6, Ui.argb(Ui.OR, 230), 0.8f);
        int cy = y + 16;
        for (FormattedCharSequence l : titre) {
            g.drawString(f, l, x + 9, cy, Ui.argb(Ui.TEXT, 255), false);
            cy += 10;
        }
        cy += 4;
        for (int i = 0; i < lignes.size(); i++) {
            Reseau.SLigne l = suivi.lignes().get(i);
            // etat : 0 à surveiller (ambre), 1 en cours, 2 fait (grisé : étape d'avant du tutoriel des bases)
            int couleur = l.etat() == 0 ? Ui.AMBRE : l.etat() == 2 ? Ui.MUTED : Ui.TEXT;
            Ui.point(g, x + 9, cy + 2, l.etat() == 0 ? Ui.AMBRE : Ui.OK, 230);
            for (FormattedCharSequence s : lignes.get(i)) {
                g.drawString(f, s, x + 18, cy, Ui.argb(couleur, 235), false);
                cy += 10;
            }
            if (!droites.get(i).isEmpty()) {
                Ui.spaced(g, f, droites.get(i), Ui.SMALL, x + 18, cy, Ui.argb(Ui.MUTED, 255), 0.5f);
                cy += 9;
            }
            if (l.total() > 1) Ui.bar(g, x + 18, cy, LARGEUR - 28, 1, l.fait() / (float) l.total(), Ui.OK);
            cy += 3;
        }
    }

    private static void notifs(GuiGraphics g, Font f, int w) {
        long maintenant = System.currentTimeMillis();
        NOTIFS.removeIf(n -> maintenant - n.debut > NOTIF_MS);
        int y = 8;
        for (int i = NOTIFS.size() - 1; i >= 0; i--) {
            Notif n = NOTIFS.get(i);
            long age = maintenant - n.debut;
            float a = age < 200 ? age / 200f : age > NOTIF_MS - 500 ? (NOTIF_MS - age) / 500f : 1f;
            if (n.lignes == null) {
                n.lignes = f.split(Ui.lit(n.texte, Ui.FONT), 190);
                int plus = 0;
                for (FormattedCharSequence s : n.lignes) plus = Math.max(plus, f.width(s));
                n.largeur = plus;
            }
            List<FormattedCharSequence> l = n.lignes;
            int lw = n.largeur;
            int bw = lw + 20, bh = l.size() * 10 + 10, x = w - bw - 8 + (int) ((1 - a) * 20);
            Ui.glass(g, x, y, bw, bh, (int) (190 * a));
            g.fill(x + 1, y + 2, x + 3, y + bh - 2, Ui.argb(n.couleur, (int) (230 * a)));
            int cy = y + 5;
            for (FormattedCharSequence s : l) {
                g.drawString(f, s, x + 11, cy, Ui.argb(Ui.TEXT, Math.max(8, (int) (255 * a))), false);
                cy += 10;
            }
            y += bh + 4;
        }
    }

    static String majuscules(String s) {
        return s.toUpperCase(Locale.ROOT);
    }
}
