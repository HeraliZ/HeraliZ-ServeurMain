package fr.heralisz.clans.client.gui.atelier;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Calque;
import fr.heralisz.clans.model.Formes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;

import java.util.Locale;

/**
 * Toile de l'atelier : la banniere en grand ({@link BannerArt#drawVivante}, {@value #E} px d'interface par unite du
 * canevas 64 x 96, soit 144 x 216), la grille 8 x 12 en surimpression (traits d'un pixel physique), le calque survole
 * cerne et le calque selectionne encadre avec ses poignees.
 * <ul>
 *   <li>coins = taille (coin oppose fixe ; proportions gardees pour les formes « ratio » du catalogue, libres pour les
 *   autres, Maj inverse ; tailles aimantees a la grille si la grille est active et les proportions libres) ;</li>
 *   <li>rond au-dessus = rotation (pas de 15°, Maj = libre) ;</li>
 *   <li>glisser l'interieur = deplacer (bords ou centre aimantes a la grille, Maj = libre).</li>
 * </ul>
 * Un clic choisit le calque visible le plus haut sous la souris, teste sur la forme reelle ({@link BannerArt#formeSous}) ;
 * un clic dans le vide deselectionne. Mesure le cout de drawVivante pendant un geste (journal « [clans] atelier
 * ms/image »).
 */
public final class Toile {
    /** Pixels d'interface par unite du canevas. */
    public static final float E = 2.25f;
    public static final int L = Math.round(Banniere.LARGEUR * E), H = Math.round(Banniere.HAUTEUR * E);
    /** Demi-cote des poignees de coin (px d'interface) et longueur de la tige de rotation. */
    private static final int POIGNEE = 3, TIGE = 12;
    /** Tolerance de prise des poignees (px d'interface). */
    private static final float PRISE = 4.5f;
    /** Les poignees se prennent jusqu'a cette distance hors de la zone (une poignee a cheval sur le bord reste prenable). */
    private static final int MARGE_POIGNEES = 8;

    private enum Geste { AUCUN, DEPLACER, TAILLE, ROTATION }

    private final Atelier at;
    private int x, y, zx, zy, zw, zh;
    private Geste geste = Geste.AUCUN;
    private Calque depart;
    /** Calque vise par le geste et dernier etat ecrit : si l'un des deux change hors du geste (clavier), il s'arrete. */
    private int indexGeste = -1;
    private Calque dernier;
    /** Angle du rond de rotation par rapport au haut du calque (0 au-dessus, 180 replie dessous). */
    private int angleRond;
    private float u0, v0;
    private int sx, sy, groupe;
    private final Quaternionf q = new Quaternionf();
    /** Sortie de {@link #local} (coordonnees dans le repere du calque, unites). */
    private float lx, ly;
    /** Etiquette du geste en cours (« 16 × 24 », « 45° ») : recalculee au changement, jamais a l'image. */
    private Component etiquette;
    private int etiquetteW;
    // mesure du cout de drawVivante pendant un geste
    private long nanosCumul, nanosMax, journalDepuis;
    private int images;

    public Toile(Atelier at) {
        this.at = at;
    }

    /** (x, y) = coin haut gauche de la banniere ; zone = rectangle qui recoit les clics (marges pour les poignees). */
    public void placer(int x, int y, int zx, int zy, int zw, int zh) {
        this.x = x;
        this.y = y;
        this.zx = zx;
        this.zy = zy;
        this.zw = zw;
        this.zh = zh;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public float versU(double mx) {
        return (float) ((mx - x) / E);
    }

    public float versV(double my) {
        return (float) ((my - y) / E);
    }

    /** Coordonnee d'interface d'un point du canevas (banc d'essai). */
    public double ecranX(double u) {
        return x + u * E;
    }

    public double ecranY(double v) {
        return y + v * E;
    }

    public boolean dansZone(double mx, double my) {
        return dansZone(mx, my, 0);
    }

    private boolean dansZone(double mx, double my, int marge) {
        return mx >= zx - marge && mx < zx + zw + marge && my >= zy - marge && my < zy + zh + marge;
    }

    /**
     * Ordonnee locale (px d'interface, repere du calque, y vers le bas) du rond de rotation : au-dessus du calque, ou
     * replie dessous, puis a l'interieur (haut, bas) quand il sortirait de la zone (calque colle au haut du fanion ou
     * plus grand que lui) ; la premiere place visible gagne.
     */
    private int rondY(Calque c) {
        int hh = Math.max(1, Math.round(c.h() * E / 2f));
        double a = Math.toRadians(c.rot());
        float si = (float) Math.sin(a), co = (float) Math.cos(a);
        float ox = x + c.cx() * E, oy = y + c.cy() * E;
        int[] places = PLACES_ROND;
        places[0] = -hh - TIGE;
        places[1] = hh + TIGE;
        places[2] = -hh + TIGE;
        places[3] = hh - TIGE;
        for (int ly : places) {
            // repere du calque -> ecran (inverse de local())
            if (dansZone(ox - ly * si, oy + ly * co)) return ly;
        }
        return places[0];
    }

    private static final int[] PLACES_ROND = new int[4];

    /** Bord du calque d'ou part la tige vers le rond place en ry (px locaux). */
    private static int bordTige(int ry, int hh) {
        return ry == -hh - TIGE || ry == -hh + TIGE ? -hh : hh;
    }

    public boolean enGeste() {
        return geste != Geste.AUCUN;
    }

    // ------------------------------------------------------------------ rendu
    public void render(GuiGraphics g, int mx, int my) {
        Banniere b = at.banniere();
        Ui.soft(g, zx, zy, zw, zh, Ui.argb(0xFFFFFF, 5));
        Ui.softOutline(g, zx + 1, zy + 1, zw - 2, zh - 2, Ui.argb(0xFFFFFF, 10));
        long t0 = System.nanoTime();
        BannerArt.drawVivante(g, b, x, y, L, H);
        if (geste != Geste.AUCUN) mesurer(System.nanoTime() - t0);
        if (at.grille()) grille(g);
        int sel = at.selection();
        if (geste == Geste.AUCUN && dansZone(mx, my)) {
            int s = calqueSous(b, versU(mx), versV(my));
            if (s >= 0 && s != sel) cadre(g, b.calque(s), Ui.argb(0xFFFFFF, 110), false);
        }
        Calque c = b.calque(sel);
        if (c != null) cadre(g, c, 0xFFFFFFFF, true);
        if (geste != Geste.AUCUN && etiquette != null) {
            int w = etiquetteW + 8;
            int ex = Math.min(zx + zw - w - 2, mx + 10), ey = Math.max(zy + 2, my - 16);
            Ui.soft(g, ex, ey, w, 11, Ui.argb(0x0B0C0F, 230));
            g.drawString(at.police(), etiquette, ex + 4, ey + 2, Ui.argb(Ui.TEXT, 255), false);
        }
    }

    /** Grille de 8 unites limitee au fanion (traits d'un pixel physique, axe central plus marque). */
    private void grille(GuiGraphics g) {
        float s = (float) Minecraft.getInstance().getWindow().getGuiScale();
        g.pose().pushPose();
        g.pose().scale(1 / s, 1 / s, 1);
        int clair = Ui.argb(0xFFFFFF, 46), sombre = Ui.argb(0x000000, 40), axe = Ui.argb(0xFFFFFF, 90);
        int g8 = Banniere.GRILLE;
        for (int k = 1; k < Banniere.LARGEUR / g8; k++) {
            float u = k * g8;
            int px = Math.round((x + u * E) * s);
            int y0 = Math.round(y * s), y1 = Math.round((y + basFanion(u) * E) * s);
            g.fill(px, y0, px + 1, y1, k == 4 ? axe : clair);
            g.fill(px + 1, y0, px + 2, y1, sombre);
        }
        for (int k = 1; k < Banniere.HAUTEUR / g8; k++) {
            float v = k * g8, demi = demiLargeur(v);
            if (demi <= 0) continue;
            int py = Math.round((y + v * E) * s);
            int x0 = Math.round((x + (32 - demi) * E) * s), x1 = Math.round((x + (32 + demi) * E) * s);
            g.fill(x0, py, x1, py + 1, clair);
            g.fill(x0, py + 1, x1, py + 2, sombre);
        }
        g.pose().popPose();
    }

    /** Bas du fanion a l'abscisse u (75 sur les bords, 96 au milieu). */
    private static float basFanion(float u) {
        return Banniere.CORPS + (Banniere.HAUTEUR - Banniere.CORPS) * (1 - Math.abs(u - 32) / 32f);
    }

    /** Demi-largeur du fanion a l'ordonnee v. */
    private static float demiLargeur(float v) {
        if (v <= Banniere.CORPS) return 32;
        return 32 * (1 - (v - Banniere.CORPS) / (float) (Banniere.HAUTEUR - Banniere.CORPS));
    }

    /**
     * Cadre tourne d'un calque ; avec poignees (coins + rond de rotation) pour la selection. Trait blanc sur un halo
     * sombre : lisible sur n'importe quel fond (l'accent derive des couleurs de la banniere et disparaissait sur elle, par
     * exemple un cadre vert sur le creeper vert) ; l'accent ne reste qu'au coeur des poignees.
     */
    private void cadre(GuiGraphics g, Calque c, int couleur, boolean poignees) {
        int hw = Math.max(1, Math.round(c.w() * E / 2f)), hh = Math.max(1, Math.round(c.h() * E / 2f));
        int halo = Ui.argb(0x000000, poignees ? 120 : 60), accent = Ui.argb(at.accent(), 255);
        g.pose().pushPose();
        g.pose().translate(x + c.cx() * E, y + c.cy() * E, 0);
        if (c.rot() != 0) g.pose().mulPose(q.rotationZ((float) Math.toRadians(c.rot())));
        anneau(g, -hw - 2, -hh - 2, hw + 2, hh + 2, 3, halo);
        anneau(g, -hw - 1, -hh - 1, hw + 1, hh + 1, 1, couleur);
        if (poignees) {
            int ry = rondY(c), bord = bordTige(ry, hh);
            int t0 = Math.min(ry, bord), t1 = Math.max(ry, bord);
            g.fill(-1, t0, 2, t1, halo);
            g.fill(0, t0, 1, t1, couleur);
            Ui.soft(g, -4, ry - 4, 9, 9, halo);
            Ui.soft(g, -3, ry - 3, 7, 7, 0xFFFFFFFF);
            g.fill(-1, ry - 1, 2, ry + 2, accent);
            for (int i = 0; i < 4; i++) {
                int px = (i & 1) == 0 ? -hw : hw, py = (i & 2) == 0 ? -hh : hh;
                g.fill(px - POIGNEE - 1, py - POIGNEE - 1, px + POIGNEE + 2, py + POIGNEE + 2, halo);
                g.fill(px - POIGNEE, py - POIGNEE, px + POIGNEE + 1, py + POIGNEE + 1, 0xFFFFFFFF);
                g.fill(px - 1, py - 1, px + 2, py + 2, accent);
            }
        }
        g.pose().popPose();
    }

    /** Rectangle creux d'epaisseur e (coordonnees de la pose courante). */
    private static void anneau(GuiGraphics g, int x0, int y0, int x1, int y1, int e, int c) {
        g.fill(x0, y0, x1, y0 + e, c);
        g.fill(x0, y1 - e, x1, y1, c);
        g.fill(x0, y0 + e, x0 + e, y1 - e, c);
        g.fill(x1 - e, y0 + e, x1, y1 - e, c);
    }

    // ------------------------------------------------------------------ souris
    /** Calque visible le plus haut sous le point (u, v) du canevas, -1 si aucun. */
    public static int calqueSous(Banniere b, float u, float v) {
        for (int i = b.nombreCalques() - 1; i >= 0; i--) {
            if (BannerArt.formeSous(b, i, u, v)) return i;
        }
        return -1;
    }

    /** Point (u, v) dans le repere du calque (sans rotation, centre a l'origine) -> lx, ly. */
    private void local(Calque c, float u, float v) {
        float dx = u - c.cx(), dy = v - c.cy();
        double a = Math.toRadians(c.rot());
        float co = (float) Math.cos(a), si = (float) Math.sin(a);
        lx = dx * co + dy * si;
        ly = -dx * si + dy * co;
    }

    public boolean clic(double mx, double my, int bouton) {
        // les poignees du calque selectionne se testent avant le filtre de la zone (marge) : un calque colle au bord
        // garde ses coins et son rond prenables
        if (bouton != 0 || !dansZone(mx, my, MARGE_POIGNEES)) return false;
        Banniere b = at.banniere();
        float u = versU(mx), v = versV(my), tol = PRISE / E;
        Calque c = b.calque(at.selection());
        if (c != null) {
            local(c, u, v);
            float hw = Math.max(1, Math.round(c.w() * E / 2f)) / E, hh = Math.max(1, Math.round(c.h() * E / 2f)) / E;
            // rond de rotation (au-dessus, ou replie la ou il est visible : voir rondY)
            int ry = rondY(c);
            if (Math.abs(lx) <= tol && Math.abs(ly - ry / E) <= tol) {
                angleRond = ry <= 0 ? 0 : 180;
                commencer(Geste.ROTATION, c, u, v);
                return true;
            }
            // coins ; un clic franchement a l'interieur de la boite (ou n'importe ou dedans pour un petit calque, dont
            // les prises de coins se chevauchent au centre) deplace au lieu de redimensionner
            boolean dedans = Math.abs(lx) <= hw && Math.abs(ly) <= hh;
            boolean interieurFranc = Math.abs(lx) <= hw - tol && Math.abs(ly) <= hh - tol;
            boolean petit = Math.min(hw, hh) < 2 * tol;
            if (!interieurFranc && !(dedans && petit)) {
                for (int i = 0; i < 4; i++) {
                    int csx = (i & 1) == 0 ? -1 : 1, csy = (i & 2) == 0 ? -1 : 1;
                    if (Math.abs(lx - csx * hw) <= tol && Math.abs(ly - csy * hh) <= tol) {
                        sx = csx;
                        sy = csy;
                        commencer(Geste.TAILLE, c, u, v);
                        return true;
                    }
                }
            }
        }
        // hors des poignees, seule la zone elle-meme recoit les clics
        if (!dansZone(mx, my)) return false;
        int i = calqueSous(b, u, v);
        if (i >= 0) {
            if (i != at.selection()) at.selectionner(i);
            commencer(Geste.DEPLACER, b.calque(i), u, v);
            return true;
        }
        // interieur de la boite du calque selectionne (forme fine ou ajouree) : on le deplace quand meme
        if (c != null) {
            local(c, u, v);
            if (Math.abs(lx) <= c.w() / 2f && Math.abs(ly) <= c.h() / 2f) {
                commencer(Geste.DEPLACER, c, u, v);
                return true;
            }
        }
        at.selectionner(-1);
        return true;
    }

    private void commencer(Geste g, Calque c, float u, float v) {
        geste = g;
        depart = c;
        dernier = c;
        indexGeste = at.selection();
        u0 = u;
        v0 = v;
        groupe = at.nouveauGeste();
        etiquette = null;
        nanosCumul = 0;
        nanosMax = 0;
        images = 0;
        journalDepuis = System.currentTimeMillis();
    }

    public boolean glisser(double mx, double my) {
        if (geste == Geste.AUCUN) return false;
        Banniere b = at.banniere();
        int sel = at.selection();
        Calque c = b.calque(sel);
        // un raccourci clavier (Tab, Suppr, Ctrl+Z, Page haut/bas...) a change la selection ou le calque pendant le
        // geste : on l'arrete, sinon la copie de depart ecraserait un autre calque
        if (c == null || depart == null || sel != indexGeste || !c.equals(dernier)) {
            relacher();
            return false;
        }
        boolean libre = Screen.hasShiftDown();
        float u = versU(mx), v = versV(my);
        Calque n = switch (geste) {
            case DEPLACER -> deplacement(u, v, libre);
            case TAILLE -> redimensionnement(u, v, libre);
            case ROTATION -> rotation(u, v, libre);
            default -> c;
        };
        if (n != null && !n.equals(c)) {
            at.changer(b.avecCalque(sel, n), groupe);
            dernier = at.banniere().calque(sel);
        }
        return true;
    }

    public void relacher() {
        if (geste != Geste.AUCUN && images > 0) journal(true);
        geste = Geste.AUCUN;
        depart = null;
        dernier = null;
        indexGeste = -1;
        etiquette = null;
    }

    private Calque deplacement(float u, float v, boolean libre) {
        float ncx = depart.cx() + (u - u0), ncy = depart.cy() + (v - v0);
        int icx, icy;
        if (at.grille() && !libre) {
            if (depart.rot() % 90 == 0) {
                boolean couche = depart.rot() % 180 == 90;
                icx = aimanter(ncx, couche ? depart.h() : depart.w());
                icy = aimanter(ncy, couche ? depart.w() : depart.h());
            } else {
                float demi = Banniere.GRILLE / 2f;
                icx = Math.round(Math.round(ncx / demi) * demi);
                icy = Math.round(Math.round(ncy / demi) * demi);
            }
        } else {
            icx = Math.round(ncx);
            icy = Math.round(ncy);
        }
        etiquette(icx + ", " + icy);
        return depart.avecCentre(icx, icy);
    }

    /**
     * Centre aimante sur un axe : le bord bas/gauche, le bord haut/droit ou le centre (demi-case) sur la grille, au plus
     * proche de la position brute.
     */
    static int aimanter(float centre, int etendue) {
        float g = Banniere.GRILLE, demi = etendue / 2f;
        float a = Math.round((centre - demi) / g) * g + demi;
        float b = Math.round((centre + demi) / g) * g - demi;
        float m = Math.round(centre / (g / 2)) * (g / 2);
        float best = a;
        if (Math.abs(b - centre) < Math.abs(best - centre)) best = b;
        if (Math.abs(m - centre) < Math.abs(best - centre)) best = m;
        return (int) Math.floor(best + 0.5f);
    }

    private Calque redimensionnement(float u, float v, boolean inverse) {
        local(depart, u, v);
        float w0 = depart.w(), h0 = depart.h();
        float ax = -sx * w0 / 2f, ay = -sy * h0 / 2f;                       // coin oppose (fixe)
        float nw = Math.max(Calque.TAILLE_MIN, (lx - ax) * sx), nh = Math.max(Calque.TAILLE_MIN, (ly - ay) * sy);
        boolean ratio = Formes.garderRatio(depart.forme()) != inverse;
        int iw, ih;
        if (ratio) {
            // projection sur la diagonale : la poignee suit la souris sans a-coups
            float k = (nw * w0 + nh * h0) / (w0 * w0 + h0 * h0);
            k = Math.max(k, Calque.TAILLE_MIN / Math.min(w0, h0));
            k = Math.min(k, Calque.TAILLE_MAX / Math.max(w0, h0));
            iw = Math.max(Calque.TAILLE_MIN, Math.round(w0 * k));
            ih = Math.max(Calque.TAILLE_MIN, Math.round(h0 * k));
        } else if (at.grille() && depart.rot() % 90 == 0) {
            int g = Banniere.GRILLE;
            iw = Math.max(g, Math.round(nw / g) * g);
            ih = Math.max(g, Math.round(nh / g) * g);
        } else {
            iw = Math.round(nw);
            ih = Math.round(nh);
        }
        iw = Math.max(Calque.TAILLE_MIN, Math.min(Calque.TAILLE_MAX, iw));
        ih = Math.max(Calque.TAILLE_MIN, Math.min(Calque.TAILLE_MAX, ih));
        // nouveau centre : coin oppose + demi-taille, ramene dans le repere de la toile
        float clx = ax + sx * iw / 2f, cly = ay + sy * ih / 2f;
        double a = Math.toRadians(depart.rot());
        float co = (float) Math.cos(a), si = (float) Math.sin(a);
        int ncx = (int) Math.floor(depart.cx() + clx * co - cly * si + 0.5f), ncy = (int) Math.floor(depart.cy() + clx * si + cly * co + 0.5f);
        etiquette(iw + " × " + ih);
        return new Calque(depart.forme(), ncx, ncy, iw, ih, depart.rot(), depart.couleur(), depart.contour(), depart.couleurContour(),
                depart.miroirX(), depart.miroirY());
    }

    private Calque rotation(float u, float v, boolean libre) {
        float dx = u - depart.cx(), dy = v - depart.cy();
        if (dx * dx + dy * dy < 0.25f) return depart;
        // rond replie sous le calque : la souris pointe vers le bas du calque, d'ou le demi-tour
        float deg = (float) Math.toDegrees(Math.atan2(dx, -dy)) - angleRond;
        int r = libre ? Math.round(deg) : Math.round(deg / 15f) * 15;
        r = Math.floorMod(r, 360);
        etiquette(r + "°");
        return depart.avecRotation(r);
    }

    /** Etiquette du geste (calculee au changement, pas a l'image). */
    private void etiquette(String texte) {
        etiquette = Ui.lit(texte, Ui.SMALL);
        etiquetteW = at.police().width(etiquette);
    }

    // ------------------------------------------------------------------ mesure
    private void mesurer(long nanos) {
        nanosCumul += nanos;
        nanosMax = Math.max(nanosMax, nanos);
        images++;
        if (System.currentTimeMillis() - journalDepuis > 2000) journal(false);
    }

    private void journal(boolean fin) {
        HeraliszClans.LOG.info("[clans] atelier ms/image : moyenne {} ms, max {} ms sur {} images{}",
                String.format(Locale.ROOT, "%.2f", nanosCumul / 1e6 / Math.max(1, images)), String.format(Locale.ROOT, "%.2f", nanosMax / 1e6),
                images, fin ? " (fin du geste)" : "");
        nanosCumul = 0;
        nanosMax = 0;
        images = 0;
        journalDepuis = System.currentTimeMillis();
    }
}
