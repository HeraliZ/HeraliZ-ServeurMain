package fr.heraliz.quetes.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Journal des quêtes (/quetes ou touche J), refait le 25/10 d'après la maquette validée
 * (maquettes/quetes/journal_quetes.png, modules/pnj/outils/apercu_journal.py : mêmes mesures) : panneau de verre
 * 560 x 320 comme l'écran du clan ; en-tête (carnet, titre, compteurs, titre porté) ; à gauche les personnages chez qui on
 * a des quêtes (réputation) ; à droite leurs quêtes en cours : difficulté, état (suivie, à rendre), étapes cochées avec
 * leur barre, récompense, « Suivre » et « Abandonner » (deux clics) ; pied avec les touches. Polices à leur vraie taille
 * (titre 14, texte 11, petit 9, micro 7,5 : jamais de texte agrandi), chaque texte posé sur un point de l'écran et centré
 * sur le milieu de ses capitales (Minecraft 1.20.1 pose une lettre TTF à y + ascendante + 2 x décalage - 3 ; DM Sans :
 * ascendante 992 et capitales 700 sur 1302, décalage 1 : milieu = taille x 0,49309 - 1). Les étapes à venir restent
 * cachées (le serveur ne les envoie pas).
 */
public final class EcranJournal extends Screen {
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_quetes", "ui_titre"),
            PETIT_GRAS = new ResourceLocation("heraliz_quetes", "ui_small_bold"), MICRO = new ResourceLocation("heraliz_quetes", "ui_micro");
    private static final int LARGEUR = 560, HAUTEUR = 320, ENTETE = 44, MARGE = 16, LISTE = 172, LIGNE = 34, ECART = 4;
    private static final int BLANC = 0xFFFFFF;
    /** Couleurs des avatars des personnages (Varga : or). */
    private static final int[] TEINTES = {Ui.ACCENT_NEUTRE, Ui.OK, 0xC792EA, 0xFF8A65, 0x4FD1C5, Ui.OR};

    private Reseau.Journal j;
    private int choix;
    private int px, py, pw, ph;
    private double defile;
    private float contenuH;
    /** Défilement de la liste des personnages ; à la première image, la ligne choisie est rendue visible. */
    private double defileListe;
    private boolean voirChoix = true;
    private String confirmer = "";
    private long confirmerA;

    /** Zones cliquables de la page (recalculées à chaque image). */
    private record Bouton(float x, float y, float w, float h, Runnable action) {
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
        super.removed();
    }

    @Override
    public boolean keyPressed(int touche, int scan, int mods) {
        if (ClientQuetes.JOURNAL.matches(touche, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(touche, scan, mods);
    }

    private void action(int type, String cle) {
        SonsUi.clic(0.6F);
        Reseau.CANAL.sendToServer(new Reseau.Action(type, cle));
    }

    // ------------------------------------------------------------------ géométrie (mesures de la maquette)
    private int haut() {
        return py + ENTETE + 10;
    }

    private int hauteurCorps() {
        return ph - ENTETE - 34;
    }

    private int listeHaut() {
        return haut() + 10;
    }

    private int listeBas() {
        return haut() + hauteurCorps();
    }

    private int ligneY(int i) {
        return haut() + 12 + i * (LIGNE + ECART) - (int) defileListe;
    }

    private int maxDefileListe() {
        return Math.max(0, 12 + j.pnj().size() * (LIGNE + ECART) - ECART - hauteurCorps());
    }

    private int droiteX() {
        return px + MARGE + LISTE + 12;
    }

    private int droiteL() {
        return px + pw - MARGE - droiteX();
    }

    private int cartesHaut() {
        return haut() + 20;
    }

    private int cartesBas() {
        return haut() + hauteurCorps();
    }

    // ------------------------------------------------------------------ souris
    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int x0 = px + MARGE;
        for (int i = 0; my >= listeHaut() && my < listeBas() && i < j.pnj().size(); i++) {
            int y = ligneY(i);
            if (mx >= x0 && mx < x0 + LISTE && my >= y && my < y + LIGNE) {
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
        if (mx < droiteX() - 6) {
            defileListe = Mth.clamp(defileListe - delta * (LIGNE + ECART) / 2, 0, maxDefileListe());
            return true;
        }
        defile = Mth.clamp(defile - delta * 16, 0, Math.max(0, contenuH - (cartesBas() - cartesHaut())));
        return true;
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        boutons.clear();
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph, 214);
        entete(g, mx, my);
        liste(g, mx, my);
        int rx = droiteX(), rw = droiteL(), y0 = haut(), h = hauteurCorps();
        Ui.glass(g, rx, y0 - 2, rw, h + 2, 110);
        Reseau.JPnj p = choix < j.pnj().size() ? j.pnj().get(choix) : null;
        if (p != null) entetePnj(g, p, rx, rw, y0);
        if (p == null || p.quetes().isEmpty()) {
            vide(g, rx, rw, p == null ? y0 : y0 + 20, p == null ? h : h - 20, p == null ? t("vide_aide") : t("aucune_quete"));
        } else {
            quetes(g, p, rx, rw, mx, my);
        }
        pied(g);
        super.render(g, mx, my, pt);
    }

    private void entete(GuiGraphics g, int mx, int my) {
        icone(g, px + MARGE, py + 10, Ui.OR);
        float tx = px + MARGE + 32;
        float tw = texte(g, t("titre"), TITRE, tx, py + 9 + cap(TITRE) / 2, Ui.TEXT);
        int enCours = 0;
        for (Reseau.JPnj p : j.pnj()) enCours += p.quetes().size();
        float cw = caps(g, t("sous_titre", enCours, j.finies()), Ui.SMALL, 1.0f, tx, py + 27 + cap(Ui.SMALL) / 2, Ui.MUTED);
        float xd = px + pw - 34;
        if (!j.titres().isEmpty()) {
            // titre porté : le dernier gagné
            String nom = TexteClient.str(j.titres().get(j.titres().size() - 1));
            float place = xd - (tx + Math.max(tw, cw) + 16) - 10;
            String s = couperCaps(t("titre_actif", nom), MICRO, 0.8f, place);
            if (!s.isEmpty()) puce(g, s, xd - capsLargeur(s, MICRO, 0.8f) - 10, py + 16, Ui.OR, false);
        }
        // quêtes du jour (26/10) : sous la croix, à droite de la ligne des compteurs, s'il reste la place
        String jour = TexteClient.tr("gui.heraliz_quetes.jour.bouton_journal");
        if (px + pw - 18 - capsLargeur(jour, PETIT_GRAS, 1.0f) - 16 > tx + cw + 10) {
            bouton(g, jour, px + pw - 18, py + 31, Ui.OR, true, mx, my, py, py + ENTETE, () -> {
                SonsUi.clic(0.6F);
                Reseau.CANAL.sendToServer(new Reseau.JourAction(0, ""));
            });
        }
        float fx = px + pw - 18, fy = py + 16;
        boolean survol = mx >= fx - 8 && mx < fx + 8 && my >= fy - 8 && my < fy + 8;
        centre(g, "×", Ui.BOLD, fx, fy, survol ? Ui.TEXT : Ui.MUTED);
        boutons.add(new Bouton(fx - 8, fy - 8, 16, 16, () -> {
            SonsUi.clic(0.6F);
            onClose();
        }));
        rect(g, px + 8, py + ENTETE, px + pw - 8, py + ENTETE + point(), Ui.argb(BLANC, 40));
    }

    /** Pictogramme du journal : carnet à pince, trois lignes, la première cochée. */
    private void icone(GuiGraphics g, float x, float y, int c) {
        rect(g, x, y, x + 22, y + 26, Ui.argb(c, 40));
        cadre(g, x, y, 22, 26, Ui.argb(c, 200));
        rect(g, x + 7, y - 2, x + 15, y + 2, Ui.argb(c, 230));
        float[] lignes = {y + 9, y + 14.5f, y + 20};
        for (int i = 0; i < lignes.length; i++) {
            float ly = lignes[i];
            rect(g, x + 9, ly - 0.6f, x + 18, ly + 0.6f, Ui.argb(BLANC, 150));
            if (i == 0) coche(g, x + 3.2f, ly - 1.8f, 3.6f, 0.9f, Ui.argb(Ui.OK, 255));
            else cadre(g, x + 3.4f, ly - 1.6f, 3.2f, 3.2f, Ui.argb(BLANC, 110));
        }
    }

    private void liste(GuiGraphics g, int mx, int my) {
        float x0 = px + MARGE, y0 = haut();
        float tw = caps(g, t("pnj"), PETIT_GRAS, 1.0f, x0, y0 + cap(PETIT_GRAS) / 2, Ui.MUTED);
        rect(g, x0 + tw + 6, y0 + 3.5f, x0 + LISTE, y0 + 3.5f + point(), Ui.argb(BLANC, 30));
        if (j.pnj().isEmpty()) {
            List<FormattedCharSequence> l = font.split(Ui.lit(t("aucun_pnj"), Ui.SMALL), LISTE - 4);
            for (int i = 0; i < l.size(); i++) ligne(g, l.get(i), Ui.SMALL, x0, y0 + 14 + i * 9 + cap(Ui.SMALL) / 2, Ui.FAINT);
            return;
        }
        int maxListe = maxDefileListe();
        if (voirChoix) {
            voirChoix = false;
            int haut = 12 + choix * (LIGNE + ECART), bas = haut + LIGNE;
            if (bas > hauteurCorps() + defileListe) defileListe = bas - hauteurCorps();
            if (haut < 12 + defileListe) defileListe = haut - 12;
        }
        defileListe = Mth.clamp(defileListe, 0, maxListe);
        int lHaut = listeHaut(), lBas = listeBas();
        boolean dansListe = my >= lHaut && my < lBas;
        g.flush();
        g.enableScissor(px + MARGE - 2, lHaut, px + MARGE + LISTE + 2, lBas);
        for (int i = 0; i < j.pnj().size(); i++) {
            int y = ligneY(i);
            if (y + LIGNE <= lHaut || y >= lBas) continue;
            Reseau.JPnj p = j.pnj().get(i);
            boolean survol = dansListe && mx >= x0 && mx < x0 + LISTE && my >= y && my < y + LIGNE;
            if (i == choix) {
                rect(g, x0, y, x0 + LISTE, y + LIGNE, Ui.argb(BLANC, 12));
                rect(g, x0, y + 2, x0 + 2, y + LIGNE - 2, Ui.argb(Ui.OR, 230));
            } else if (survol) {
                rect(g, x0, y, x0 + LISTE, y + LIGNE, Ui.argb(BLANC, 7));
            }
            String nom = TexteClient.str(p.nom());
            int c = teinte(p.id());
            rect(g, x0 + 8, y + 5, x0 + 26, y + 23, Ui.argb(c, 46));
            cadre(g, x0 + 8, y + 5, 18, 18, Ui.argb(c, 190));
            centre(g, lettre(nom), Ui.BOLD, x0 + 17, y + 14, Ui.TEXT);
            float cw = 0;
            if (!p.quetes().isEmpty()) {
                String n = Integer.toString(p.quetes().size());
                cw = capsLargeur(n, MICRO, 0.8f) + 10;
                puce(g, n, x0 + LISTE - 4 - cw, y + 11, Ui.OR, true);
            }
            texte(g, couper(nom, Ui.BOLD, LISTE - 36 - cw - 4), Ui.BOLD, x0 + 32, y + 5 + cap(Ui.BOLD) / 2, Ui.TEXT);
            texte(g, couper(TexteClient.str(p.role()), Ui.SMALL, LISTE - 36), Ui.SMALL, x0 + 32, y + 16 + cap(Ui.SMALL) / 2, Ui.MUTED);
            rect(g, x0 + 32, y + 27, x0 + LISTE - 8, y + 28.2f, Ui.argb(BLANC, 26));
            float ratio = p.repSuivant() <= 0 ? 1f : Mth.clamp(p.rep() / (float) p.repSuivant(), 0, 1);
            rect(g, x0 + 32, y + 27, x0 + 32 + (LISTE - 40) * ratio, y + 28.2f, Ui.argb(Ui.OR, 220));
        }
        g.flush();
        g.disableScissor();
        if (maxListe > 0) {
            int zone = lBas - lHaut, barre = Math.max(16, zone * zone / (zone + maxListe));
            float by = lHaut + (float) ((zone - barre) * (defileListe / maxListe));
            rect(g, x0 + LISTE + 4, by, x0 + LISTE + 6, by + barre, Ui.argb(BLANC, 60));
        }
    }

    /** En-tête du personnage choisi : nom, rôle, niveau de réputation et sa barre. */
    private void entetePnj(GuiGraphics g, Reseau.JPnj p, float rx, float rw, float y0) {
        float base = y0 + 6 + cap(Ui.BOLD);
        String niveau = TexteClient.tr("heraliz_quetes.niveau." + Mth.clamp(p.niveau(), 0, 4));
        String rep = p.repSuivant() > 0 ? t("reputation", niveau, p.rep(), p.repSuivant()) : t("reputation_max", niveau, p.rep());
        float repW = capsLargeur(rep, MICRO, 0.8f);
        capsDroite(g, rep, MICRO, 0.8f, rx + rw - 12, y0 + 9.5f, Ui.OR);
        rect(g, rx + rw - 92, y0 + 15, rx + rw - 12, y0 + 16.2f, Ui.argb(BLANC, 26));
        float ratio = p.repSuivant() <= 0 ? 1f : Mth.clamp(p.rep() / (float) p.repSuivant(), 0, 1);
        rect(g, rx + rw - 92, y0 + 15, rx + rw - 92 + 80 * ratio, y0 + 16.2f, Ui.argb(Ui.OR, 220));
        float place = rw - 24 - Math.max(repW, 80) - 12;
        float nw = texte(g, couper(TexteClient.str(p.nom()), Ui.BOLD, place), Ui.BOLD, rx + 12, base - cap(Ui.BOLD) / 2, Ui.TEXT);
        String role = couperCaps(TexteClient.str(p.role()), Ui.SMALL, 1.0f, place - nw - 8);
        // rôle sur la même ligne de base que le nom
        if (!role.isEmpty()) caps(g, role, Ui.SMALL, 1.0f, rx + 12 + nw + 8, base - cap(Ui.SMALL) / 2, Ui.MUTED);
    }

    /** Aucune quête : carnet pâle, phrase, aide. */
    private void vide(GuiGraphics g, float rx, float rw, float y0, float h, String aide) {
        float cx = rx + rw / 2f, cy = y0 + h / 2f - 14;
        icone(g, cx - 11, cy - 30, Ui.FAINT);
        centre(g, t("vide"), Ui.BOLD, cx, cy + 8, Ui.TEXT);
        List<FormattedCharSequence> l = font.split(Ui.lit(aide, Ui.SMALL), (int) rw - 60);
        for (int i = 0; i < l.size(); i++) {
            float w = font.getSplitter().stringWidth(l.get(i));
            ligne(g, l.get(i), Ui.SMALL, cx - w / 2f, cy + 22 + i * 9, Ui.MUTED);
        }
    }

    private void quetes(GuiGraphics g, Reseau.JPnj p, float rx, float rw, int mx, int my) {
        int haut = cartesHaut(), bas = cartesBas();
        float total = 0;
        for (Reseau.JQuete q : p.quetes()) total += hauteur(q, rw - 16) + 6;
        contenuH = total + 4 - 6;
        defile = Mth.clamp(defile, 0, Math.max(0, contenuH - (bas - haut)));
        g.flush();
        g.enableScissor((int) rx + 1, haut, (int) (rx + rw) - 1, bas);
        float y = haut + 4 - (float) defile;
        String nom = TexteClient.str(p.nom());
        for (Reseau.JQuete q : p.quetes()) {
            float h = hauteur(q, rw - 16);
            if (y + h > haut && y < bas) carte(g, q, rx + 8, y, rw - 16, h, nom, mx, my, haut, bas);
            y += h + 6;
        }
        g.flush();
        g.disableScissor();
        if (contenuH > bas - haut) {
            float zone = bas - haut, barre = Math.max(16, zone * zone / contenuH);
            float by = haut + (float) ((zone - barre) * (defile / Math.max(1, contenuH - zone)));
            rect(g, rx + rw - 5, by, rx + rw - 3, by + barre, Ui.argb(BLANC, 60));
        }
    }

    private List<FormattedCharSequence> resume(Reseau.JQuete q, float w) {
        String s = TexteClient.str(q.resume());
        if (s.isEmpty()) return List.of();
        List<FormattedCharSequence> l = font.split(Ui.lit(s, Ui.SMALL), (int) w - 20);
        return l.size() > 2 ? l.subList(0, 2) : l;
    }

    private float hauteur(Reseau.JQuete q, float w) {
        return 30 + 9 * Math.max(0, resume(q, w).size() - 1) + 13 * q.etapes().size() + 22;
    }

    /** Une quête : titre, difficulté, état, ce qu'il faut faire, étapes, récompense, actions. */
    private void carte(GuiGraphics g, Reseau.JQuete q, float x, float y, float w, float h, String nomPnj, int mx, int my, int haut, int bas) {
        boolean aRendre = q.aRendre(), suivie = q.suivie() && !aRendre, marquee = aRendre || suivie;
        int bord = aRendre ? Ui.OK : suivie ? Ui.OR : BLANC;
        rect(g, x, y, x + w, y + h, Ui.argb(BLANC, marquee ? 9 : 5));
        cadre(g, x, y, w, h, Ui.argb(bord, marquee ? 120 : 30));
        if (marquee) rect(g, x, y + 2, x + 2, y + h - 2, Ui.argb(bord, 230));

        // en-tête : titre, difficulté (points), état
        float lm = y + 10.5f;
        float tw = texte(g, couper(TexteClient.str(q.titre()), Ui.BOLD, w - 150), Ui.BOLD, x + 10, lm, Ui.TEXT);
        float dx = x + 10 + tw + 6;
        int diff = Mth.clamp(q.difficulte(), 1, 4);
        for (int k = 0; k < 4; k++) disque(g, dx + 3 + k * 6, lm, 1.8f, Ui.argb(k < diff ? Ui.OR : Ui.FAINT, 255));
        float xd = x + w - 10;
        if (aRendre) {
            String s = t("a_rendre");
            puce(g, s, xd - capsLargeur(s, MICRO, 0.8f) - 10, lm, Ui.OK, true);
        } else if (suivie) {
            String s = t("suivie");
            puce(g, s, xd - capsLargeur(s, MICRO, 0.8f) - 10, lm, Ui.OR, true);
        }
        List<FormattedCharSequence> resume = resume(q, w);
        for (int i = 0; i < resume.size(); i++) ligne(g, resume.get(i), Ui.SMALL, x + 10, y + 20.5f + i * 9, Ui.MUTED);

        // étapes : case (cochée si faite), libellé, « fait / total » et sa barre
        float ly = y + 31 + 9 * Math.max(0, resume.size() - 1);
        for (Reseau.JEtape e : q.etapes()) {
            int total = Math.max(1, e.total());
            boolean fini = e.etat() == 2 || e.fait() >= total;
            int fait = fini ? total : Mth.clamp(e.fait(), 0, total);
            if (fini) {
                rect(g, x + 10, ly - 3.2f, x + 16.4f, ly + 3.2f, Ui.argb(Ui.OK, 60));
                coche(g, x + 10.8f, ly - 2.4f, 4.8f, 1.0f, Ui.argb(Ui.OK, 255));
            } else {
                cadre(g, x + 10, ly - 3.2f, 6.4f, 6.4f, Ui.argb(BLANC, e.etat() == 0 ? 60 : 110));
            }
            ResourceLocation police = fini ? Ui.SMALL : PETIT_GRAS;
            int couleur = fini ? Ui.MUTED : e.etat() == 0 ? Ui.FAINT : Ui.TEXT;
            texte(g, couper(TexteClient.str(e.libelle()), police, w - 100), police, x + 22, ly, couleur);
            droite(g, fait + " / " + total, PETIT_GRAS, x + w - 10, ly, fini ? Ui.OK : Ui.TEXT);
            rect(g, x + w - 70, ly + 4.6f, x + w - 10, ly + 5.6f, Ui.argb(BLANC, 22));
            rect(g, x + w - 70, ly + 4.6f, x + w - 70 + 60f * fait / total, ly + 5.6f, Ui.argb(fini ? Ui.OK : Ui.OR, 220));
            ly += 13;
        }

        // pied de la carte : récompense, actions
        float fy = y + h - 11;
        String rec = TexteClient.str(q.recompense());
        if (!rec.isEmpty()) {
            float rw = caps(g, t("recompense"), MICRO, 0.8f, x + 10, fy, Ui.MUTED);
            texte(g, couper(rec, PETIT_GRAS, w - 200), PETIT_GRAS, x + 10 + rw + 6, fy, Ui.OR);
        }
        float bd = x + w - 8;
        if (aRendre) {
            droite(g, couper(t("retourner", nomPnj), Ui.SMALL, 150), Ui.SMALL, bd, fy, Ui.OK);
            return;
        }
        boolean conf = confirmer.equals(q.cle()) && System.currentTimeMillis() - confirmerA < 4000;
        bd -= bouton(g, conf ? t("confirmer") : t("abandonner"), bd, fy, Ui.DANGER, conf, mx, my, haut, bas, () -> {
            if (confirmer.equals(q.cle()) && System.currentTimeMillis() - confirmerA < 4000) {
                confirmer = "";
                action(3, q.cle());
            } else {
                confirmer = q.cle();
                confirmerA = System.currentTimeMillis();
                SonsUi.clic(0.6F);
            }
        }) + 5;
        bouton(g, suivie ? t("ne_plus_suivre") : t("suivre"), bd, fy, suivie ? BLANC : Ui.OR, !suivie, mx, my, haut, bas,
                () -> action(suivie ? 2 : 1, q.cle()));
    }

    /** Petit bouton aligné à droite sur xd, centré sur cy ; rend sa largeur. */
    private float bouton(GuiGraphics g, String txt, float xd, float cy, int c, boolean plein, int mx, int my, int haut, int bas, Runnable action) {
        float w = capsLargeur(txt, PETIT_GRAS, 1.0f) + 16, x = xd - w;
        boolean survol = mx >= x && mx < xd && my >= cy - 7 && my < cy + 7 && my >= haut && my < bas;
        if (plein) {
            rect(g, x, cy - 7, xd, cy + 7, Ui.argb(c, survol ? 90 : 60));
            cadre(g, x, cy - 7, w, 14, Ui.argb(c, 220));
        } else {
            rect(g, x, cy - 7, xd, cy + 7, Ui.argb(c == BLANC ? BLANC : c, survol ? 20 : 6));
            cadre(g, x, cy - 7, w, 14, Ui.argb(c, survol ? 170 : 90));
        }
        capsCentre(g, txt, PETIT_GRAS, 1.0f, x + w / 2f, cy, plein || c == BLANC ? Ui.TEXT : c);
        float y0 = Math.max(cy - 7, haut), y1 = Math.min(cy + 7, bas);
        if (y1 > y0) boutons.add(new Bouton(x, y0, w, y1 - y0, action));
        return w;
    }

    /** Étiquette en petites capitales sur fond teinté, centrée sur cy ; rend sa largeur. */
    private float puce(GuiGraphics g, String txt, float x, float cy, int c, boolean plein) {
        float w = capsLargeur(txt, MICRO, 0.8f) + 10;
        rect(g, x, cy - 5, x + w, cy + 5, Ui.argb(c, plein ? 70 : 26));
        cadre(g, x, cy - 5, w, 10, Ui.argb(c, 170));
        capsCentre(g, txt, MICRO, 0.8f, x + w / 2f, cy, plein ? Ui.TEXT : c);
        return w;
    }

    /** Pied : touches utiles. */
    private void pied(GuiGraphics g) {
        float y = py + ph - 12;
        rect(g, px + 8, y - 8, px + pw - 8, y - 8 + point(), Ui.argb(BLANC, 22));
        float x = px + MARGE;
        String[][] aides = {
                {ClientQuetes.JOURNAL.getTranslatedKeyMessage().getString(), t("touche.fermer")},
                {t("touche.molette"), t("touche.defiler")},
                {t("touche.clic"), t("touche.choisir")}};
        for (String[] a : aides) {
            float w = capsLargeur(a[0], MICRO, 0.8f) + 8;
            rect(g, x, y - 4.5f, x + w, y + 4.5f, Ui.argb(BLANC, 18));
            cadre(g, x, y - 4.5f, w, 9, Ui.argb(BLANC, 60));
            capsCentre(g, a[0], MICRO, 0.8f, x + w / 2f, y, Ui.TEXT);
            x += w + 4;
            x += texte(g, a[1], Ui.SMALL, x, y, Ui.MUTED) + 12;
        }
    }

    private static int teinte(String id) {
        if ("varga".equals(id)) return Ui.OR;
        return TEINTES[Math.floorMod(id.hashCode(), TEINTES.length - 1)];
    }

    /** Lettre de l'avatar : initiale du dernier mot du nom (« Commandant Varga » : V, « Le Guide » : G). */
    private static String lettre(String nom) {
        String[] mots = nom.trim().split("\\s+");
        String m = mots.length == 0 ? "" : mots[mots.length - 1];
        return m.isEmpty() ? "?" : new String(Character.toChars(m.codePointAt(0))).toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ texte à sa vraie taille, posé net
    private static float taille(ResourceLocation f) {
        return switch (f.getPath()) {
            case "ui_titre" -> 14f;
            case "ui_small", "ui_small_bold" -> 9f;
            case "ui_micro" -> 7.5f;
            default -> 11f;
        };
    }

    /** Milieu des capitales sous le point de dessin (polices DM Sans du mod, décalage 1). */
    private static float milieu(ResourceLocation f) {
        return taille(f) * 0.49309f - 1f;
    }

    /** Hauteur des capitales. */
    private static float cap(ResourceLocation f) {
        return taille(f) * 0.53763f;
    }

    /** Un point de l'écran, en unités d'interface. */
    private static float point() {
        return (float) (1.0 / Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes ; repris du kit des Caisses). */
    private static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    private float largeur(String s, ResourceLocation f) {
        return font.getSplitter().stringWidth(Ui.lit(s, f));
    }

    /** Texte : bord gauche en x, milieu des capitales sur cy ; rend sa largeur. */
    private float texte(GuiGraphics g, String s, ResourceLocation f, float x, float cy, int rgb) {
        if (s.isEmpty()) return 0;
        g.pose().pushPose();
        placer(g, x, cy - milieu(f));
        g.drawString(font, Ui.lit(s, f), 0, 0, Ui.argb(rgb, 255), false);
        g.pose().popPose();
        return largeur(s, f);
    }

    private void centre(GuiGraphics g, String s, ResourceLocation f, float cx, float cy, int rgb) {
        texte(g, s, f, cx - largeur(s, f) / 2f, cy, rgb);
    }

    private void droite(GuiGraphics g, String s, ResourceLocation f, float xd, float cy, int rgb) {
        texte(g, s, f, xd - largeur(s, f), cy, rgb);
    }

    /** Ligne déjà coupée (résumés, aides). */
    private void ligne(GuiGraphics g, FormattedCharSequence l, ResourceLocation f, float x, float cy, int rgb) {
        g.pose().pushPose();
        placer(g, x, cy - milieu(f));
        g.drawString(font, l, 0, 0, Ui.argb(rgb, 255), false);
        g.pose().popPose();
    }

    private float capsLargeur(String s, ResourceLocation f, float esp) {
        String up = s.toUpperCase(Locale.ROOT);
        float w = 0;
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            w += font.getSplitter().stringWidth(Ui.lit(new String(Character.toChars(up.codePointAt(i))), f)) + esp;
        }
        return Math.max(0, w - esp);
    }

    /** Capitales espacées, chaque lettre posée sur un point de l'écran ; bord gauche x, milieu cy ; rend la largeur. */
    private float caps(GuiGraphics g, String s, ResourceLocation f, float esp, float x, float cy, int rgb) {
        String up = s.toUpperCase(Locale.ROOT);
        float cx = x;
        int argb = Ui.argb(rgb, 255);
        for (int i = 0; i < up.length(); i = up.offsetByCodePoints(i, 1)) {
            MutableComponent c = Ui.lit(new String(Character.toChars(up.codePointAt(i))), f);
            g.pose().pushPose();
            placer(g, cx, cy - milieu(f));
            font.drawInBatch(c, 0, 0, argb, false, g.pose().last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0, 15728880);
            g.pose().popPose();
            cx += font.getSplitter().stringWidth(c) + esp;
        }
        g.flush();
        return Math.max(0, cx - esp - x);
    }

    private void capsCentre(GuiGraphics g, String s, ResourceLocation f, float esp, float cx, float cy, int rgb) {
        caps(g, s, f, esp, cx - capsLargeur(s, f, esp) / 2f, cy, rgb);
    }

    private void capsDroite(GuiGraphics g, String s, ResourceLocation f, float esp, float xd, float cy, int rgb) {
        caps(g, s, f, esp, xd - capsLargeur(s, f, esp), cy, rgb);
    }

    /** Coupe le texte à max (points d'interface) avec « … ». */
    private String couper(String s, ResourceLocation f, float max) {
        if (largeur(s, f) <= max) return s;
        String fin = "…";
        int n = s.length();
        while (n > 0 && largeur(s.substring(0, n).stripTrailing() + fin, f) > max) n--;
        return n <= 0 ? "" : s.substring(0, n).stripTrailing() + fin;
    }

    private String couperCaps(String s, ResourceLocation f, float esp, float max) {
        if (max <= 0) return "";
        if (capsLargeur(s, f, esp) <= max) return s;
        int n = s.length();
        while (n > 0 && capsLargeur(s.substring(0, n).stripTrailing() + "…", f, esp) > max) n--;
        return n <= 0 ? "" : s.substring(0, n).stripTrailing() + "…";
    }

    // ------------------------------------------------------------------ formes à la mesure exacte (points d'écran)
    /** Rectangle plein, bords posés sur les points de l'écran (au moins un point de large). */
    private static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float a0 = Math.round(x0 * s) / s, b0 = Math.round(y0 * s) / s, a1 = Math.round(x1 * s) / s, b1 = Math.round(y1 * s) / s;
        if (a1 <= a0) a1 = a0 + 1 / s;
        if (b1 <= b0) b1 = b0 + 1 / s;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int a = argb >>> 24, r = argb >> 16 & 255, v = argb >> 8 & 255, b = argb & 255;
        vc.vertex(m, a0, b0, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a0, b1, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a1, b1, 0).color(r, v, b, a).endVertex();
        vc.vertex(m, a1, b0, 0).color(r, v, b, a).endVertex();
    }

    /** Contour d'un point d'écran à l'intérieur du rectangle. */
    private static void cadre(GuiGraphics g, float x, float y, float w, float h, int argb) {
        float e = point();
        rect(g, x, y, x + w, y + e, argb);
        rect(g, x, y + h - e, x + w, y + h, argb);
        rect(g, x, y + e, x + e, y + h - e, argb);
        rect(g, x + w - e, y + e, x + w, y + h - e, argb);
    }

    /** Disque plein, ligne de points d'écran par ligne. */
    private static void disque(GuiGraphics g, float cx, float cy, float r, int argb) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale(), pas = 1 / s;
        for (float y = (float) Math.floor((cy - r) * s) / s; y < cy + r; y += pas) {
            float d = y + pas / 2 - cy, demi = (float) Math.sqrt(Math.max(0, r * r - d * d));
            if (demi > pas / 3) rect(g, cx - demi, y, cx + demi, y + pas, argb);
        }
    }

    /** Coche (deux traits d'épaisseur e) dans le carré (x, y, s). */
    private static void coche(GuiGraphics g, float x, float y, float s, float e, int argb) {
        trait(g, x, y + s * 0.55f, x + s * 0.38f, y + s * 0.9f, e, argb);
        trait(g, x + s * 0.38f, y + s * 0.9f, x + s, y + s * 0.12f, e, argb);
    }

    private static void trait(GuiGraphics g, float x0, float y0, float x1, float y1, float e, int argb) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l <= 0) return;
        float ux = dx / l * e / 2, uy = dy / l * e / 2, nx = -uy, ny = ux;
        // prolongé d'une demi-épaisseur aux deux bouts : la jointure de la coche reste pleine
        x0 -= ux;
        y0 -= uy;
        x1 += ux;
        y1 += uy;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int a = argb >>> 24, r = argb >> 16 & 255, v = argb >> 8 & 255, b = argb & 255;
        float[][] p = {{x0 + nx, y0 + ny}, {x0 - nx, y0 - ny}, {x1 - nx, y1 - ny}, {x1 + nx, y1 + ny}};
        // les deux sens de rotation : visible quel que soit le sens des sommets
        for (int k = 0; k < 4; k++) vc.vertex(m, p[k][0], p[k][1], 0).color(r, v, b, a).endVertex();
        for (int k = 3; k >= 0; k--) vc.vertex(m, p[k][0], p[k][1], 0).color(r, v, b, a).endVertex();
    }
}
