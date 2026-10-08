package fr.heraliz.ping.client.hud;

import fr.heraliz.ping.PingConfig;
import fr.heraliz.ping.client.PingsClient;
import fr.heraliz.ping.client.PingsClient.Visuel;
import fr.heraliz.ping.client.gui.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Marqueurs des pings dans le HUD : la position 3D est projetee a l'ecran avec les matrices de la derniere image du
 * monde, donc toujours visible (a travers les murs) et toujours de la meme taille lisible.
 * <ul>
 *     <li>A l'ecran : point d'ancrage exact, tige fine, pastille de verre a l'anneau et a l'icone du type (onde a
 *     l'arrivee), et a droite la distance puis le pseudo de l'auteur a sa couleur (clan ou equipe) : pseudo au debut,
 *     sous le viseur, ou toujours (reglage).</li>
 *     <li>Hors champ (ou derriere) : pastille au bord de l'ecran (hors barre d'objets) avec une fleche vers le ping et
 *     la distance.</li>
 * </ul>
 * Les plus lointains d'abord (les proches passent devant), au plus {@code max_visibles} (les plus proches). Tout part
 * en un seul lot ({@link Lot}) ; aucune allocation par image (matrices, vecteur, ordre et texte reutilises).
 */
public final class RenduPings {
    private RenduPings() {
    }

    private static final Matrix4f VUE = new Matrix4f(), PROJ = new Matrix4f();
    private static final Vector4f V = new Vector4f();
    private static final int[] ORDRE = new int[PingsClient.CAPACITE];
    private static final StringBuilder SB = new StringBuilder(16);
    private static double camX, camY, camZ;
    private static boolean matrices;
    private static float attenuation = 1f;
    /** Fleches deja placees dans cette image (pour ne pas les superposer). */
    private static final float[] PLACES_X = new float[PingsClient.CAPACITE], PLACES_Y = new float[PingsClient.CAPACITE];
    private static int nPlaces;
    /** Mesures du banc d'essai : marqueurs et fleches de la derniere image. */
    public static int derniersMarqueurs, dernieresFleches;

    /** Fin du rendu du monde : matrices de vue et de projection de cette image (recopiees, aucune allocation). */
    public static void capturer(Matrix4f vue, Matrix4f proj, Camera camera) {
        VUE.set(vue);
        PROJ.set(proj);
        camX = camera.getPosition().x;
        camY = camera.getPosition().y;
        camZ = camera.getPosition().z;
        matrices = true;
    }

    public static void oublierMatrices() {
        matrices = false;
    }

    public static void dessiner(Matrix4f pose, float pt, int largeur, int hauteur) {
        if (!Mesure.active) {
            dessiner0(pose, pt, largeur, hauteur);
            return;
        }
        Mesure.debut();
        dessiner0(pose, pt, largeur, hauteur);
        Mesure.fin();
    }

    private static void dessiner0(Matrix4f pose, float pt, int largeur, int hauteur) {
        derniersMarqueurs = dernieresFleches = 0;
        long maintenant = Util.getMillis();
        PingsClient.expirer(maintenant);
        int n = PingsClient.n;
        if (n == 0 || !matrices || !Atlas.pret()) return;
        Minecraft mc = Minecraft.getInstance();
        Player joueur = mc.player;
        if (joueur == null || mc.level == null) return;
        double px = Mth.lerp(pt, joueur.xo, joueur.getX()), py = Mth.lerp(pt, joueur.yo, joueur.getY()) + joueur.getEyeHeight(),
                pz = Mth.lerp(pt, joueur.zo, joueur.getZ());
        float cx = largeur * 0.5f, cy = hauteur * 0.5f;
        Visuel focus = null;
        float focusD = 30f * 30f;
        for (int i = 0; i < n; i++) {
            Visuel v = PingsClient.PINGS[i];
            suivre(mc, v, pt, maintenant);
            V.set((float) (v.x - camX), (float) (v.y - camY), (float) (v.z - camZ), 1f);
            V.mul(VUE);
            V.mul(PROJ);
            v.clipX = V.x;
            v.clipY = V.y;
            v.clipW = V.w;
            double dx = v.x - px, dy = v.y - py, dz = v.z - pz;
            v.dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            v.enEcran = false;
            if (V.w > 0.05f) {
                v.sx = (V.x / V.w * 0.5f + 0.5f) * largeur;
                v.sy = (0.5f - V.y / V.w * 0.5f) * hauteur;
                v.enEcran = v.sx >= 4 && v.sx <= largeur - 4 && v.sy >= 4 && v.sy <= hauteur - 4;
                if (v.enEcran) {
                    float fx = v.sx - cx, fy = v.sy - cy, d = fx * fx + fy * fy;
                    if (d < focusD) {
                        focusD = d;
                        focus = v;
                    }
                }
            }
            // tri par insertion, du plus loin au plus proche
            int k = i;
            while (k > 0 && PingsClient.PINGS[ORDRE[k - 1]].dist < v.dist) {
                ORDRE[k] = ORDRE[k - 1];
                k--;
            }
            ORDRE[k] = i;
        }
        float echelle = PingConfig.TAILLE.get() / 100f;
        boolean nomsToujours = PingConfig.NOMS_TOUJOURS.get(), fleches = PingConfig.FLECHES.get();
        boolean fr = Ui.french();
        // roue ouverte : les marqueurs s'effacent derriere elle (rien ne gene le choix)
        attenuation = Roue.ouverte() ? 0.3f : 1f;
        int debut = Math.max(0, n - PingConfig.MAX_VISIBLES.get());
        nPlaces = 0;
        if (fleches) {
            for (int k = n - 1; k >= debut; k--) { // les plus proches d'abord : ils gardent leur vraie place
                Visuel v = PingsClient.PINGS[ORDRE[k]];
                if (!v.enEcran) placer(v, echelle, largeur, hauteur);
            }
        }
        Lot.debut(pose);
        for (int k = debut; k < n; k++) {
            Visuel v = PingsClient.PINGS[ORDRE[k]];
            if (v.enEcran) {
                marqueur(v, v == focus, nomsToujours, echelle, maintenant, fr);
                derniersMarqueurs++;
            } else if (fleches) {
                fleche(v, echelle, largeur, maintenant, fr);
                dernieresFleches++;
            }
        }
        Lot.fin();
    }

    /** Ping sur une entite : sa place interpolee si le client la connait (recherche par id, 4 fois par seconde au plus). */
    private static void suivre(Minecraft mc, Visuel v, float pt, long maintenant) {
        if (v.entiteId < 0) return;
        Entity e = v.entite;
        if (e != null && e.isRemoved()) e = v.entite = null;
        if (e == null && maintenant >= v.prochaineRecherche) {
            v.prochaineRecherche = maintenant + 250;
            Entity t = mc.level.getEntity(v.entiteId);
            if (t != null && !t.isRemoved()) e = v.entite = t;
        }
        if (e == null) return;
        v.x = Mth.lerp(pt, e.xo, e.getX());
        v.y = Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight();
        v.z = Mth.lerp(pt, e.zo, e.getZ());
    }

    /** Opacite : apparition en 140 ms, fondu sur la derniere seconde. */
    private static float opacite(Visuel v, long maintenant) {
        float age = maintenant - v.naissance, reste = v.fin - maintenant;
        return Mth.clamp(age / 140f, 0f, 1f) * Mth.clamp(reste / 900f, 0f, 1f) * attenuation;
    }

    /** Un peu plus petit au loin (jusqu'a -22 % a 250 m). */
    private static float echelleDistance(float d) {
        return 1f - 0.22f * Mth.clamp((d - 24f) / 220f, 0f, 1f);
    }

    private static float sortie(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static void marqueur(Visuel v, boolean focus, boolean nomsToujours, float echelle, long maintenant, boolean fr) {
        float a = opacite(v, maintenant);
        if (a <= 0.004f) return;
        float age = maintenant - v.naissance;
        float s = echelle * echelleDistance(v.dist) * (focus ? 1.12f : 1f);
        float pop = age < 240f ? sortie(age / 240f) : 1f;
        float R = 7.5f * s * (0.55f + 0.45f * pop);
        float ax = v.sx, ay = v.sy;
        float cx = ax, cy = ay - R - 6f * s;
        int col = v.type.couleur;
        // ancrage exact et tige
        Lot.carre(Atlas.DISQUE, ax, ay, 1.8f * s, Lot.argb(col, a));
        Lot.rect(ax - 0.5f, cy + R - 0.5f, ax + 0.5f, ay - 1.4f * s, Lot.argb(col, 0.6f * a));
        // onde d'arrivee
        if (age < 800f) {
            float e = age / 800f, oe = 1f - (1f - e) * (1f - e);
            Lot.carre(Atlas.ANNEAU_FIN, cx, cy, R * (1f + 1.7f * oe), Lot.argb(col, 0.8f * a * (1f - e)));
        }
        if (focus) Lot.carre(Atlas.HALO, cx, cy, R * 2.4f, Lot.argb(col, 0.28f * a));
        // pastille
        Lot.carre(Atlas.DISQUE, cx, cy, R, Lot.argb(Ui.VERRE, 0.82f * a));
        Lot.carre(Atlas.ANNEAU, cx, cy, R, Lot.argb(col, a));
        Lot.carre(Atlas.ICONES[v.type.ordinal()], cx, cy, R * 0.6f, Lot.argb(col, a));
        // etiquette : distance, puis pseudo
        boolean nom = !v.moi && (nomsToujours || focus || age < 3000f);
        Texte.distance(SB, v.dist, fr);
        float t1 = 5.4f * s, t2 = 4.4f * s, esp1 = 0.35f * s, esp2 = 0.25f * s;
        float w1 = Texte.largeur(SB, t1, esp1);
        float w2 = nom ? v.largeurNom * t2 + esp2 * Math.max(0, v.nom.length() - 1) : 0f;
        float hBloc = nom ? t1 + 3.2f * s + t2 : t1;
        float lx = cx + R + 4.5f * s, top = cy - hBloc * 0.5f;
        Lot.pilule(lx - 3.5f * s, top - 3f * s, lx + Math.max(w1, w2) + 3.5f * s, top + hBloc + 3f * s, Lot.argb(Ui.VERRE, 0.66f * a));
        Texte.dessiner(SB, lx, top, t1, Lot.argb(Ui.TEXT, a), esp1);
        if (nom) Texte.dessiner(v.nom, lx, top + t1 + 3.2f * s, t2, Lot.argb(Lot.melange(v.couleur, 0xFFFFFF, 0.2f), a), esp2);
    }

    /**
     * Place la fleche d'un ping hors champ au bord de l'ecran (hors barre d'objets), dans sa direction. Appele du plus
     * proche au plus lointain : si la place est prise, la fleche glisse le long du bord (jamais l'une sur l'autre).
     */
    private static void placer(Visuel v, float s, int largeur, int hauteur) {
        // direction a l'ecran : coordonnees de decoupe brutes (le signe reste juste derriere la camera)
        float dx = v.clipX * largeur, dy = -v.clipY * hauteur;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1.0E-4f) {
            dx = 0f;
            dy = 1f;
            l = 1f;
        }
        float ux = dx / l, uy = dy / l;
        float marge = 24f * s;
        float gauche = marge, droite = largeur - marge, haut = marge, bas = hauteur - 52f - 8f * s;
        float ox = (gauche + droite) * 0.5f, oy = (haut + bas) * 0.5f;
        float tx = Float.MAX_VALUE, ty = Float.MAX_VALUE;
        if (ux > 1.0E-4f) tx = (droite - ox) / ux;
        else if (ux < -1.0E-4f) tx = (gauche - ox) / ux;
        if (uy > 1.0E-4f) ty = (bas - oy) / uy;
        else if (uy < -1.0E-4f) ty = (haut - oy) / uy;
        float t = Math.min(tx, ty);
        float ix = ox + ux * t, iy = oy + uy * t;
        boolean bordVertical = tx <= ty;
        float pas = 6.6f * s * 2f + 16f * s, ecart = pas * pas;
        for (int essai = 0; essai < 8; essai++) {
            boolean libre = true;
            for (int k = 0; k < nPlaces && libre; k++) {
                float ex = PLACES_X[k] - ix, ey = PLACES_Y[k] - iy;
                libre = ex * ex + ey * ey >= ecart;
            }
            if (libre) break;
            if (bordVertical) iy = Mth.clamp(iy + (iy < oy ? pas : -pas), haut, bas);
            else ix = Mth.clamp(ix + (ix < ox ? pas : -pas), gauche, droite);
        }
        if (nPlaces < PLACES_X.length) {
            PLACES_X[nPlaces] = ix;
            PLACES_Y[nPlaces++] = iy;
        }
        v.fx = ix;
        v.fy = iy;
        v.fux = ux;
        v.fuy = uy;
    }

    /** Ping hors champ : pastille a sa place au bord ({@link #placer}), fleche vers lui, distance du cote interieur. */
    private static void fleche(Visuel v, float echelle, int largeur, long maintenant, boolean fr) {
        float a = opacite(v, maintenant);
        if (a <= 0.004f) return;
        float s = echelle, ix = v.fx, iy = v.fy, ux = v.fux, uy = v.fuy;
        int col = v.type.couleur;
        float R = 6.6f * s;
        Lot.carre(Atlas.DISQUE, ix, iy, R, Lot.argb(Ui.VERRE, 0.82f * a));
        Lot.carre(Atlas.ANNEAU, ix, iy, R, Lot.argb(col, a));
        Lot.carre(Atlas.ICONES[v.type.ordinal()], ix, iy, R * 0.6f, Lot.argb(col, a));
        float px = ix + ux * (R + 4.2f * s), py = iy + uy * (R + 4.2f * s), h = 3.6f * s;
        Lot.tourne(Atlas.FLECHE, px, py, -h, -h, h, h, ux, uy, Lot.argb(col, a));
        // distance vers l'interieur de l'ecran
        Texte.distance(SB, v.dist, fr);
        float tt = 4.4f * s, w = Texte.largeur(SB, tt, 0.3f * s);
        float tx = ix - ux * (R + 5f * s + w * 0.5f * Math.abs(ux)), ty = iy - uy * (R + 5f * s + tt * 0.5f * Math.abs(uy)) - tt * 0.5f;
        tx = Mth.clamp(tx, w * 0.5f + 2f, largeur - w * 0.5f - 2f);
        Lot.pilule(tx - w * 0.5f - 3f * s, ty - 2.5f * s, tx + w * 0.5f + 3f * s, ty + tt + 2.5f * s, Lot.argb(Ui.VERRE, 0.6f * a));
        Texte.centre(SB, tx, ty, tt, Lot.argb(Ui.TEXT, 0.92f * a), 0.3f * s);
    }
}
