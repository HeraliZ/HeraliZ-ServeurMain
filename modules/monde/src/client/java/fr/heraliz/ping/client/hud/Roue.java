package fr.heraliz.ping.client.hud;

import com.mojang.blaze3d.platform.Window;
import fr.heraliz.ping.PingType;
import fr.heraliz.ping.client.gui.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Roue des pings (touche maintenue) : 6 secteurs de verre autour du viseur, icone et couleur de chaque type, nom du
 * type survole au centre, rappel discret en dessous. Pas d'ecran : on continue de se deplacer pendant le choix. La
 * souris est liberee (curseur cache) le temps de la roue : la camera ne bouge pas et la position du curseur donne
 * la direction choisie ; elle est reprise a la fermeture. Clic gauche = valider, clic droit = annuler, relacher la
 * touche = valider (ou annuler si le curseur est reste au centre).
 */
public final class Roue {
    private Roue() {
    }

    /** Rayon exterieur de la roue et zone morte au centre (pixels d'interface). */
    public static final float RAYON = 74f, ZONE_MORTE = 13f;
    private static final float[] COS = new float[6], SIN = new float[6];

    static {
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60.0);
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    private static boolean ouverte, relachee;
    /** Banc d'essai : le curseur est place par le scenario ({@link #forcerCurseur}), pas lu dans la souris. */
    public static boolean banc;
    private static long ouverture;
    /** Accent du clan, lu a l'ouverture (lecture par reflexion : pas a chaque image). */
    private static int accent = Ui.ACCENT_NEUTRE;
    private static int choix = -1;
    private static float curseurX, curseurY;
    /** Libelles mis en cache a chaque changement de langue (aucune chaine par image). */
    private static final String[] NOMS = new String[6];
    /** Meme nom coupe en deux lignes a l'espace le plus central (null : un seul mot). */
    private static final String[] LIGNE1 = new String[6], LIGNE2 = new String[6];
    private static String rappel = "", annuler = "", langue = "";

    public static boolean ouverte() {
        return ouverte;
    }

    /** Secteur survole (indice de {@link PingType}), -1 = centre. */
    public static int choix() {
        return choix;
    }

    public static void ouvrir() {
        Minecraft mc = Minecraft.getInstance();
        ouverte = true;
        ouverture = Util.getMillis();
        choix = -1;
        curseurX = curseurY = 0;
        relachee = false;
        accent = Ui.accent();
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse(); // curseur au centre, camera immobile
            GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            relachee = true;
        }
    }

    /**
     * Ferme la roue et rend la souris au jeu. {@code ecranOuvert} : un ecran vient de s'ouvrir (Echap, inventaire) ;
     * il gere lui-meme la souris, on rend juste un curseur visible.
     */
    public static void fermer() {
        if (!ouverte) return;
        ouverte = false;
        choix = -1;
        Minecraft mc = Minecraft.getInstance();
        if (relachee) {
            relachee = false;
            if (mc.screen == null && mc.isWindowActive()) {
                mc.mouseHandler.grabMouse();
            } else {
                GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        }
    }

    /** Lit le curseur (chaque image) : direction depuis le centre, en pixels d'interface ; il est retenu au bord. */
    public static void suivreCurseur() {
        if (!ouverte || banc) return;
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        double k = w.getGuiScale();
        double cx = w.getScreenWidth() / 2.0, cy = w.getScreenHeight() / 2.0;
        float dx = (float) ((mc.mouseHandler.xpos() - cx) / k), dy = (float) ((mc.mouseHandler.ypos() - cy) / k);
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float max = RAYON * 1.1f;
        if (d > max && relachee) { // le curseur cache reste pres de la roue (il ne sort pas de la fenetre)
            dx *= max / d;
            dy *= max / d;
            d = max;
            GLFW.glfwSetCursorPos(w.getWindow(), cx + dx * k, cy + dy * k);
        }
        curseurX = dx;
        curseurY = dy;
        choix = d < ZONE_MORTE ? -1 : secteur(dx, dy);
    }

    /** Banc d'essai : place le curseur (pixels d'interface depuis le centre). */
    public static void forcerCurseur(float dx, float dy) {
        curseurX = dx;
        curseurY = dy;
        choix = (float) Math.sqrt(dx * dx + dy * dy) < ZONE_MORTE ? -1 : secteur(dx, dy);
    }

    /** Secteur d'une direction ecran (y vers le bas) : 0 en haut, puis sens horaire. */
    public static int secteur(float dx, float dy) {
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += Math.PI * 2;
        return (int) Math.floor((a + Math.PI / 6) / (Math.PI / 3)) % 6;
    }

    private static void libelles() {
        String l = Minecraft.getInstance().getLanguageManager().getSelected();
        if (l.equals(langue)) return;
        langue = l;
        for (PingType t : PingType.values()) {
            int i = t.ordinal();
            String nom = I18n.get(t.cle()).toUpperCase(java.util.Locale.ROOT);
            NOMS[i] = nom;
            int coupure = -1;
            for (int k = 0; k < nom.length(); k++) {
                if (nom.charAt(k) == ' ' && (coupure < 0 || Math.abs(2 * k - nom.length()) < Math.abs(2 * coupure - nom.length()))) coupure = k;
            }
            LIGNE1[i] = coupure < 0 ? null : nom.substring(0, coupure);
            LIGNE2[i] = coupure < 0 ? null : nom.substring(coupure + 1);
        }
        rappel = I18n.get("heraliz_ping.roue.rappel");
        annuler = I18n.get("heraliz_ping.roue.annuler").toUpperCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------ rendu
    public static void dessiner(Matrix4f pose, int largeur, int hauteur) {
        if (!ouverte || !Atlas.pret()) return;
        if (!Mesure.active) {
            dessiner0(pose, largeur, hauteur);
            return;
        }
        Mesure.debut();
        dessiner0(pose, largeur, hauteur);
        Mesure.fin();
    }

    private static void dessiner0(Matrix4f pose, int largeur, int hauteur) {
        libelles();
        long t = Util.getMillis() - ouverture;
        float apparition = Math.min(1f, t / 130f);
        float e = 1f - (1f - apparition) * (1f - apparition) * (1f - apparition);
        float a = e;
        float R = RAYON * (0.88f + 0.12f * e), Ri = R * Atlas.rayonInterieur;
        float cx = largeur / 2f, cy = hauteur / 2f;
        Lot.debut(pose);
        // voile doux sous la roue (jamais de fond uni)
        Lot.carre(Atlas.HALO, cx, cy, R * 2.3f, Lot.argb(0x000000, 0.55f * a));
        for (int i = 0; i < 6; i++) {
            PingType type = PingType.parId(i);
            boolean survol = i == choix;
            int fond = survol ? Lot.melange(Ui.VERRE, type.couleur, 0.42f) : Ui.VERRE;
            float[] g = Atlas.GEO_SECTEUR;
            Lot.tourne(Atlas.SECTEUR, cx, cy, g[0] * R, g[1] * R, g[2] * R, g[3] * R, COS[i], SIN[i], Lot.argb(fond, (survol ? 0.92f : 0.82f) * a));
            if (survol) {
                float[] b = Atlas.GEO_BORD;
                Lot.tourne(Atlas.SECTEUR_BORD, cx, cy, b[0] * R, b[1] * R, b[2] * R, b[3] * R, COS[i], SIN[i], Lot.argb(type.couleur, a));
            }
            float rm = (R + Ri) * 0.5f * (survol ? 1.03f : 1f);
            float ix = cx + SIN[i] * rm, iy = cy - COS[i] * rm;
            float taille = survol ? 11.5f : 9.5f;
            if (survol) Lot.carre(Atlas.HALO, ix, iy, taille * 2.2f, Lot.argb(type.couleur, 0.35f * a));
            int c = survol ? type.couleur : Lot.melange(type.couleur, 0xFFFFFF, 0.18f);
            Lot.carre(Atlas.ICONES[i], ix, iy, taille, Lot.argb(c, (survol ? 1f : 0.82f) * a));
        }
        // centre : disque de verre, liseré a l'accent du clan, nom du type survole
        float rc = Ri - 4f;
        Lot.carre(Atlas.DISQUE, cx, cy, rc, Lot.argb(Ui.VERRE, 0.86f * a));
        Lot.carre(Atlas.ANNEAU_FIN, cx, cy, rc, Lot.argb(accent, 0.55f * a));
        if (choix >= 0) {
            PingType type = PingType.parId(choix);
            String nom = NOMS[choix];
            float taille = 6.2f, max = rc * 1.7f;
            int couleur = Lot.argb(Lot.melange(type.couleur, 0xFFFFFF, 0.12f), a);
            float w = Texte.largeur(nom, taille, 0.9f);
            if (w > max && LIGNE1[choix] != null) {
                // trop large sur une ligne : deux lignes plutot qu'un texte minuscule (russe, « Besoin d'aide »)
                float w2 = Math.max(Texte.largeur(LIGNE1[choix], taille, 0.9f), Texte.largeur(LIGNE2[choix], taille, 0.9f));
                if (w2 > max) taille *= max / w2;
                float esp = 0.9f * taille / 6.2f, ecart = taille * 0.5f, haut = cy - taille - ecart / 2f;
                Texte.centre(LIGNE1[choix], cx, haut, taille, couleur, esp);
                Texte.centre(LIGNE2[choix], cx, haut + taille + ecart, taille, couleur, esp);
            } else {
                if (w > max) {
                    taille *= max / w;
                }
                Texte.centre(nom, cx, cy - taille / 2f, taille, couleur, 0.9f * taille / 6.2f);
            }
        } else {
            Texte.centre(annuler, cx, cy - 2.5f, 5f, Lot.argb(Ui.MUTED, a), 0.8f);
        }
        // pointeur : petit point a l'accent du clan, sur le bord interieur, dans la direction du curseur
        float d = (float) Math.sqrt(curseurX * curseurX + curseurY * curseurY);
        if (d >= ZONE_MORTE * 0.5f) {
            float ux = curseurX / d, uy = curseurY / d;
            Lot.carre(Atlas.DISQUE, cx + ux * (Ri - 1f), cy + uy * (Ri - 1f), 2.4f, Lot.argb(accent, a));
        }
        // rappel discret sous la roue
        float ty = cy + R + 9f;
        float tw = Texte.largeur(rappel, 4.6f, 0.4f);
        Lot.pilule(cx - tw / 2f - 7f, ty - 4f, cx + tw / 2f + 7f, ty + 8.6f, Lot.argb(Ui.VERRE, 0.62f * a));
        Texte.centre(rappel, cx, ty, 4.6f, Lot.argb(Ui.TEXT, 0.85f * a), 0.4f);
        Lot.fin();
    }
}
