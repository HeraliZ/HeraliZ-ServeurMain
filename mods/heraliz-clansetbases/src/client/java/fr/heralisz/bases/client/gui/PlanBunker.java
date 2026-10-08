package fr.heralisz.bases.client.gui;

import fr.heralisz.bases.client.gui.kit.Icons;
import fr.heralisz.bases.client.gui.kit.Ui;
import fr.heralisz.bases.model.BaseVue;
import fr.heralisz.bases.model.Boite;
import fr.heralisz.bases.model.Plan;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plan du bunker vu de dessus (BASES_SPEC §6), dessine a partir du {@link Plan} recu du serveur (bunker.json) : nord en
 * haut, x vers la droite, z vers le bas. Murs de coque (chaque volume agrandi d'un bloc, gris fonce), hall et couloirs
 * (gris clair), sas (cases d'arrivee) avec leur porte a l'accent, 6 salles : ouvertes = teinte d'accent, verrouillees =
 * sombres a contour en tirets, cadenas et prix ; cloisons des salles fermees en barre de coque. Survol et selection.
 * <p>Tout est calcule a la construction ({@link #placer}) : rectangles en pixels, libelles et prix. Le rendu ne fait que
 * des aplats et des textes deja prets (aucune allocation par image).
 */
public final class PlanBunker {
    /** Rectangle en pixels d'interface (x, y, largeur, hauteur). */
    private record R(int x, int y, int w, int h) {
        boolean dans(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int cx() {
            return x + w / 2;
        }

        int cy() {
            return y + h / 2;
        }
    }

    /** Une salle du plan : piece principale (plus grande boite), passages (autres boites + cloisons), textes. */
    private static final class SalleP {
        final String id;
        final R piece;
        final List<R> passages = new ArrayList<>();
        final String nom;
        final String largeurs;
        boolean ouverte;
        boolean depart;
        /** Espacement des capitales du nom (resserre si la piece est etroite) ; dimensions affichees seulement si elles tiennent. */
        float espacement = 0.8f;
        boolean largeursVisibles = true;
        long cout;
        @Nullable
        Component prix;

        SalleP(String id, R piece, String nom, String largeurs) {
            this.id = id;
            this.piece = piece;
            this.nom = nom;
            this.largeurs = largeurs;
        }
    }

    private final Plan plan;
    private int ox, oy, largeur, hauteur;
    private float s;
    private int minX, minZ;
    private final List<R> communs = new ArrayList<>();
    private final List<R> murs = new ArrayList<>();
    private final List<R> sas = new ArrayList<>();
    private final List<R> portes = new ArrayList<>();
    private final List<String> sasNoms = new ArrayList<>();
    private final List<SalleP> salles = new ArrayList<>();
    private R hall;
    private R zoneTotale;
    private String libelleHall = "", libelleNord = "", libelleSud = "";

    public PlanBunker(Plan plan) {
        this.plan = plan;
    }

    public Plan plan() {
        return plan;
    }

    // ------------------------------------------------------------------ construction
    /**
     * Place le plan dans la zone (x, y, maxL, maxH) en gardant les proportions (10 px reserves en haut et en bas pour
     * NORD / SUD), centre horizontalement. Rend la largeur reellement occupee.
     */
    public int placer(Font font, int x, int y, int maxL, int maxH, @Nullable BaseVue vue) {
        List<Boite> toutes = new ArrayList<>();
        toutes.addAll(plan.hall);
        toutes.addAll(plan.couloirs);
        toutes.addAll(plan.interieur);
        for (Plan.Salle sa : plan.salles) toutes.addAll(sa.interieur());
        for (Plan.Entree e : plan.entrees.values()) {
            if (e.sas() != null) toutes.add(e.sas());
            if (e.porte() != null) toutes.add(e.porte());
        }
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Boite b : toutes) {
            x0 = Math.min(x0, b.x0());
            z0 = Math.min(z0, b.z0());
            x1 = Math.max(x1, b.x1());
            z1 = Math.max(z1, b.z1());
        }
        if (toutes.isEmpty()) {
            x0 = z0 = 0;
            x1 = Math.max(1, plan.tx) - 1;
            z1 = Math.max(1, plan.tz) - 1;
        }
        // un bloc de mur autour de tout
        minX = x0 - 1;
        minZ = z0 - 1;
        int nx = x1 - x0 + 3, nz = z1 - z0 + 3;
        int haut = maxH - 20;
        s = Math.min(maxL / (float) nx, haut / (float) nz);
        largeur = Math.round(nx * s);
        hauteur = Math.round(nz * s);
        ox = x + (maxL - largeur) / 2;
        oy = y + 10 + (haut - hauteur) / 2;
        zoneTotale = new R(ox, oy, largeur, hauteur);

        communs.clear();
        murs.clear();
        sas.clear();
        portes.clear();
        sasNoms.clear();
        salles.clear();
        // murs : chaque volume agrandi d'un bloc
        for (Boite b : toutes) murs.add(rect(b.x0() - 1, b.z0() - 1, b.x1() + 1, b.z1() + 1));
        List<Boite> com = new ArrayList<>(plan.hall);
        com.addAll(plan.couloirs);
        if (com.isEmpty()) com.addAll(plan.interieur);
        for (Boite b : com) communs.add(rect(b));
        hall = plan.hall.isEmpty() ? (communs.isEmpty() ? null : communs.get(0)) : rect(plan.hall.get(0));
        for (Plan.Entree e : plan.entrees.values()) {
            if (e.sas() != null) {
                sas.add(rect(e.sas()));
                sasNoms.add(e.id());
            }
            if (e.porte() != null) portes.add(rect(e.porte()));
        }
        boolean en = !Ui.french();
        for (Plan.Salle sa : plan.salles) {
            Boite grande = null;
            for (Boite b : sa.interieur()) if (grande == null || b.dx() * b.dz() > grande.dx() * grande.dz()) grande = b;
            if (grande == null) continue;
            String nom = sa.nom(en ? "en" : "fr");
            SalleP p = new SalleP(sa.id(), rect(grande), nom, grande.dx() + " × " + grande.dz());
            for (Boite b : sa.interieur()) if (b != grande) p.passages.add(rect(b));
            for (Boite b : sa.cloison()) {
                R r = rect(b);
                if (!p.passages.contains(r)) p.passages.add(r);
            }
            int utile = p.piece.w - 6;
            if (Ui.spacedWidth(font, nom, Ui.SMALL, 0.8f) > utile) p.espacement = Ui.spacedWidth(font, nom, Ui.SMALL, 0.2f) > utile ? 0f : 0.2f;
            p.largeursVisibles = Ui.spacedWidth(font, p.largeurs, Ui.SMALL, 0.4f) <= utile && p.piece.h >= 36;
            p.depart = sa.depart();
            p.ouverte = sa.depart();
            p.cout = sa.cout();
            salles.add(p);
        }
        libelleHall = Component.translatable("gui.heralisz_bases.zone.hall").getString();
        libelleNord = Component.translatable("gui.heralisz_bases.plan.nord").getString();
        libelleSud = Component.translatable("gui.heralisz_bases.plan.sud").getString();
        etat(vue);
        return largeur;
    }

    /** Met a jour l'etat des salles (ouverte, cout) depuis la vue du serveur. */
    public void etat(@Nullable BaseVue vue) {
        for (SalleP p : salles) {
            BaseVue.SalleVue sv = null;
            if (vue != null) for (BaseVue.SalleVue x : vue.salles()) if (x.id().equalsIgnoreCase(p.id)) sv = x;
            if (sv != null) {
                p.ouverte = sv.ouverte() || sv.depart();
                p.depart = sv.depart();
                p.cout = sv.cout();
            }
            p.prix = p.ouverte ? null : Ui.jetons(p.cout, Ui.SMALL);
        }
    }

    private R rect(Boite b) {
        return rect(b.x0(), b.z0(), b.x1(), b.z1());
    }

    /** Boite de blocs inclusive (x0..x1, z0..z1) -> pixels. */
    private R rect(int x0, int z0, int x1, int z1) {
        int a = ox + Math.round((x0 - minX) * s), b = oy + Math.round((z0 - minZ) * s);
        int c = ox + Math.round((x1 + 1 - minX) * s), d = oy + Math.round((z1 + 1 - minZ) * s);
        return new R(a, b, Math.max(1, c - a), Math.max(1, d - b));
    }

    // ------------------------------------------------------------------ lecture
    /** Id de la salle sous la souris, null sinon. */
    @Nullable
    public String salleSous(double mx, double my) {
        for (SalleP p : salles) if (p.piece.dans(mx, my)) return p.id;
        return null;
    }

    public boolean dans(double mx, double my) {
        return zoneTotale != null && zoneTotale.dans(mx, my);
    }

    public int x() {
        return ox;
    }

    public int y() {
        return oy;
    }

    public int largeur() {
        return largeur;
    }

    public int hauteur() {
        return hauteur;
    }

    /** Centre (pixels) d'une salle, pour le banc (clic sur une salle). */
    @Nullable
    public int[] centreSalle(String id) {
        for (SalleP p : salles) if (p.id.equalsIgnoreCase(id)) return new int[]{p.piece.cx(), p.piece.cy()};
        return null;
    }

    // ------------------------------------------------------------------ rendu
    /**
     * Dessine le plan. {@code alpha} 0..1 (plan grise pendant la pose ou en apercu), {@code selection} id de salle
     * cerclee, {@code ici} zone du joueur dans sa base (id de salle, hall, couloir, sas_nord...) ou null, {@code apercu}
     * = ecran sans base (seules les salles de depart sont teintees, aucun prix).
     */
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, int accent, float alpha, @Nullable String selection,
                       @Nullable String ici, boolean apercu) {
        int a = Math.round(255 * alpha);
        // fond « papier calque » + quadrillage tous les 4 blocs
        Ui.soft(g, ox - 4, oy - 4, largeur + 8, hauteur + 8, Ui.argb(0xFFFFFF, 6 * a / 255));
        int pas4 = Math.max(4, Math.round(4 * s));
        for (int gx = ox + pas4; gx < ox + largeur; gx += pas4) g.fill(gx, oy, gx + 1, oy + hauteur, Ui.argb(0xFFFFFF, 7 * a / 255));
        for (int gy = oy + pas4; gy < oy + hauteur; gy += pas4) g.fill(ox, gy, ox + largeur, gy + 1, Ui.argb(0xFFFFFF, 7 * a / 255));
        // murs de coque
        int mur = Ui.argb(0x2A2D36, 235 * a / 255);
        for (R r : murs) g.fill(r.x, r.y, r.x + r.w, r.y + r.h, mur);
        // parties communes
        int sol = Ui.argb(0x4A505C, 235 * a / 255);
        for (R r : communs) g.fill(r.x, r.y, r.x + r.w, r.y + r.h, sol);
        for (R r : portes) g.fill(r.x, r.y, r.x + r.w, r.y + r.h, sol);
        // salles
        long now = Util.getMillis();
        for (SalleP p : salles) {
            boolean ouverte = p.ouverte;
            boolean teinte = apercu ? p.depart : ouverte;
            R r = p.piece;
            boolean survol = !apercu && r.dans(mouseX, mouseY);
            boolean choisie = !apercu && selection != null && selection.equalsIgnoreCase(p.id);
            if (teinte) {
                int fond = Ui.mix(0x4A505C, accent, 0.38f);
                g.fill(r.x, r.y, r.x + r.w, r.y + r.h, Ui.argb(fond, 240 * a / 255));
                for (R q : p.passages) g.fill(q.x, q.y, q.x + q.w, q.y + q.h, Ui.argb(fond, 240 * a / 255));
                Ui.cadre(g, r.x, r.y, r.w, r.h, Ui.argb(accent, (survol || choisie ? 230 : 120) * a / 255));
            } else {
                g.fill(r.x, r.y, r.x + r.w, r.y + r.h, Ui.argb(0x15171D, 235 * a / 255));
                // cloison : barre de coque qui ferme le passage
                for (R q : p.passages) g.fill(q.x, q.y, q.x + q.w, q.y + q.h, Ui.argb(0x2A2D36, 255 * a / 255));
                Ui.tirets(g, r.x + 1, r.y + 1, r.w - 2, r.h - 2, 3, 2, Ui.argb(0xFFFFFF, (survol || choisie ? 120 : 46) * a / 255));
            }
            if (choisie) {
                // coins de selection
                int c = Ui.argb(0xFFFFFF, 230 * a / 255), l = 5;
                g.fill(r.x - 2, r.y - 2, r.x - 2 + l, r.y - 1, c);
                g.fill(r.x - 2, r.y - 2, r.x - 1, r.y - 2 + l, c);
                g.fill(r.x + r.w + 2 - l, r.y - 2, r.x + r.w + 2, r.y - 1, c);
                g.fill(r.x + r.w + 1, r.y - 2, r.x + r.w + 2, r.y - 2 + l, c);
                g.fill(r.x - 2, r.y + r.h + 1, r.x - 2 + l, r.y + r.h + 2, c);
                g.fill(r.x - 2, r.y + r.h + 2 - l, r.x - 1, r.y + r.h + 2, c);
                g.fill(r.x + r.w + 2 - l, r.y + r.h + 1, r.x + r.w + 2, r.y + r.h + 2, c);
                g.fill(r.x + r.w + 1, r.y + r.h + 2 - l, r.x + r.w + 2, r.y + r.h + 2, c);
            }
            // libelles
            int txt = Ui.argb(teinte ? Ui.TEXT : Ui.MUTED, a);
            if (!teinte && !apercu) {
                Icons.draw(g, Icons.CADENAS, r.cx() - 5, r.cy() - 17, 10, survol || choisie ? Ui.TEXT : Ui.MUTED, a);
                Ui.spacedCentered(g, font, p.nom, Ui.SMALL, r.cx(), r.cy() - 3, txt, p.espacement);
                if (p.prix != null) Ui.centered(g, font, p.prix, r.cx(), r.cy() + 7, Ui.argb(Ui.AMBRE, 220 * a / 255));
            } else {
                Ui.spacedCentered(g, font, p.nom, Ui.SMALL, r.cx(), r.cy() - (p.largeursVisibles ? 6 : 3), txt, p.espacement);
                if (p.largeursVisibles) Ui.spacedCentered(g, font, p.largeurs, Ui.SMALL, r.cx(), r.cy() + 4, Ui.argb(Ui.MUTED, 160 * a / 255), 0.4f);
            }
            if (ici != null && ici.equalsIgnoreCase(p.id)) vous(g, font, r.x + 6, r.y + 6, now, a);
        }
        // sas et portes
        for (int i = 0; i < sas.size(); i++) {
            R r = sas.get(i);
            g.fill(r.x, r.y, r.x + r.w, r.y + r.h, Ui.argb(0x3A3F4A, 240 * a / 255));
            Ui.cadre(g, r.x, r.y, r.w, r.h, Ui.argb(0xFFFFFF, 40 * a / 255));
            if (ici != null && ici.equalsIgnoreCase("sas_" + sasNoms.get(i))) vous(g, font, r.cx() - 2, r.cy() - 2, now, a);
        }
        for (R r : portes) {
            // porte : trait d'accent au bout du couloir (ouverture 3 x 3 a proteger)
            g.fill(r.x, r.y, r.x + r.w, r.y + r.h, Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.2f), 150 * a / 255));
            Ui.cadre(g, r.x, r.y, r.w, r.h, Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.4f), 230 * a / 255));
        }
        if (hall != null) {
            Ui.spacedCentered(g, font, libelleHall, Ui.SMALL, hall.cx(), hall.cy() - 3, Ui.argb(Ui.TEXT, 200 * a / 255), 1.4f);
            if (ici != null && (ici.equals("hall") || ici.equals("couloir"))) vous(g, font, hall.cx() - 2, hall.cy() + 8, now, a);
        }
        Ui.spacedCentered(g, font, libelleNord, Ui.SMALL, ox + largeur / 2f, oy - 11, Ui.argb(Ui.MUTED, 200 * a / 255), 1.2f);
        Ui.spacedCentered(g, font, libelleSud, Ui.SMALL, ox + largeur / 2f, oy + hauteur + 4, Ui.argb(Ui.MUTED, 200 * a / 255), 1.2f);
    }

    /** Marqueur « vous etes ici » : point blanc qui pulse doucement. */
    private static void vous(GuiGraphics g, Font font, int x, int y, long now, int a) {
        float p = (float) (0.5 + 0.5 * Math.sin(now / 220.0));
        int halo = Math.round((40 + 60 * p) * a / 255);
        g.fill(x - 2, y - 2, x + 7, y + 7, Ui.argb(0xFFFFFF, halo / 3));
        Ui.point(g, x, y, 0xFFFFFF, a);
    }

    /** Libelle de zone pour le HUD et le plan : salle (nom du plan) ou zone commune traduite. */
    public static String nomZone(@Nullable Plan plan, String zone) {
        if (zone == null || zone.isEmpty()) return "";
        if (plan != null) {
            Plan.Salle sa = plan.salle(zone);
            if (sa != null) return sa.nom(Ui.french() ? "fr" : "en");
        }
        String cle = "gui.heralisz_bases.zone." + zone.toLowerCase(Locale.ROOT);
        Component c = Component.translatable(cle);
        String t = c.getString();
        return t.equals(cle) ? zone : t;
    }
}
