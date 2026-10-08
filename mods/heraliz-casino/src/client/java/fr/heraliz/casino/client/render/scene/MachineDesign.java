package fr.heraliz.casino.client.render.scene;

import java.util.Random;

/**
 * Modele 3D d'une machine (Java pur : partage par le jeu et l'apercu hors-jeu).
 * Repere : pixels (1/16 bloc), origine au coin du bloc principal, face avant vers +Z, +X a droite du joueur.
 * La carrosserie (body) est fixe et "cuite" une fois ; les parties animees sont redessinees a chaque image
 * a partir de la "vue" de la machine (etat du jeu synchronise).
 *
 * @param <V> vue (etat du jeu) lue par le modele
 */
public abstract class MachineDesign<V> {
    /** Identifiant du modele (caches, reglages). */
    public final String id;
    /** Atlas de textures : textures/machine/&lt;atlas&gt;.png (+ _glow.png) ; le meme que l'identifiant, sauf atlas partage. */
    public final String atlas;
    public final int glow, accent;
    /** Niveau de detail (1 = plein, ~0.5 = eloigne). */
    public float detail = 1f;
    /** Hauteur du texte flottant (px). */
    public float labelY = 44;
    /** Echelle du modele dans l'inventaire (bloc = 1). */
    public float itemScale = 0.55f;

    private final Pose pose = new Pose();
    private volatile boolean built;

    protected MachineDesign(String id, int glow, int accent) {
        this(id, id, glow, accent);
    }

    /** Modele dont les textures sont dans un atlas partage avec d'autres (elements de decor). */
    protected MachineDesign(String id, String atlas, int glow, int accent) {
        this.id = id;
        this.atlas = atlas;
        this.glow = glow;
        this.accent = accent;
    }

    public final void ensureBuilt() {
        if (!built) {
            synchronized (this) {
                if (!built) {
                    build();
                    built = true;
                }
            }
        }
    }

    public boolean isBuilt() {
        return built;
    }

    /** Construit les maillages. */
    protected abstract void build();

    /** Carrosserie fixe. */
    protected abstract void body(Pose p, DrawList out);

    /** Parties animees (chaque image). */
    protected abstract void animate(Scene s, V v, Pose p, DrawList out);

    /** Vue de repos (inventaire, apercu, machine jamais utilisee). */
    public abstract V newView();

    /**
     * Apercu hors-jeu : vue d'une partie de demonstration commencee au tick DEMO_START (la scene est a DEMO_START + t).
     */
    public V demoView(float t) {
        return newView();
    }

    /** Duree de la demonstration (ticks). */
    public int demoLength() {
        return 100;
    }

    public static final long DEMO_START = 1000;

    /** Particules et sons (une fois par tick client). */
    public void tickFx(Scene s, V v, FxSink fx, Random r) {
    }

    // =================================================================== camera et zones cliquables
    /** Plans de camera demandes par l'ecran de jeu : attente / mises, partie en cours, resultat. */
    public static final int CAM_MENU = 0, CAM_PLAY = 1, CAM_RESULT = 2;

    /** Plan de camera (repere du modele, px) : position de l'oeil, point vise, champ de vision vertical (degres). */
    public static final class Shot {
        public float ex, ey, ez, tx, ty, tz, fov = 50;

        public Shot set(float ex, float ey, float ez, float tx, float ty, float tz, float fov) {
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.fov = fov;
            return this;
        }
    }

    /** Camera du joueur quand il joue sur la machine. Par defaut : vue de face cadrant la machine. */
    public void camera(V v, Scene s, int mode, Shot out) {
        out.set(8, labelY * 0.6f, 12 + labelY * 1.05f, 8, labelY * 0.45f, 8, 50);
    }

    /** La camera est loin des yeux du joueur local et doit le montrer (duels : on se voit face a l'adversaire). */
    public boolean showsLocalPlayer(V v, Scene s) {
        return false;
    }

    /** La machine a un ecran qu'on peut agrandir (zoom du joueur). */
    public boolean zoomable() {
        return false;
    }

    /**
     * Image de l'ecran de la machine (repere du modele, px), face a +Z : {x0, y0, x1, y1, z}. Le zoom du joueur ("la
     * tete dans l'ecran") et l'intro la cadrent en entier dans l'image ; null si la machine n'a pas d'ecran ou si un
     * plan cinematique est en cours (roue, agrandissement...).
     */
    public float[] screenRect(V v, Scene s) {
        return null;
    }

    @SuppressWarnings("unchecked")
    public final float[] screenRectRaw(Object v, Scene s) {
        try {
            return screenRect((V) v, s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Cadrage de l'ecran impose par la machine (0..1) : l'intro de la machine se joue la camera devant l'ecran. */
    public float screenFocus(V v, Scene s) {
        return 0;
    }

    @SuppressWarnings("unchecked")
    public final float screenFocusRaw(Object v, Scene s) {
        try {
            return screenFocus((V) v, s);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * Ecran de fin de partie affiche (gain total d'un bonus), 0..1 : la partie se termine, les boutons du joueur
     * reviennent par-dessus. 0 par defaut.
     */
    public float summary(V v, Scene s) {
        return 0;
    }

    @SuppressWarnings("unchecked")
    public final float summaryRaw(Object v, Scene s) {
        try {
            return summary((V) v, s);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public final boolean showsLocalPlayerRaw(Object v, Scene s) {
        return showsLocalPlayer((V) v, s);
    }

    /** Zones cliquables (cases, dalles, cartes...) ; aucune par defaut. */
    public void hotspots(V v, Scene s, Hotspots out) {
    }

    @SuppressWarnings("unchecked")
    public final void cameraRaw(Object v, Scene s, int mode, Shot out) {
        ensureBuilt();
        try {
            camera((V) v, s, mode, out);
        } catch (RuntimeException e) {
            // donnees de partie illisibles : camera par defaut plutot qu'un plantage
            out.set(8, 30, 60, 8, 20, 8, 50);
        }
    }

    @SuppressWarnings("unchecked")
    public final void hotspotsRaw(Object v, Scene s, Hotspots out) {
        out.clear();
        try {
            hotspots((V) v, s, out);
        } catch (RuntimeException e) {
            out.clear();
        }
    }

    // =================================================================== points d'entree
    public final void renderBody(DrawList out) {
        ensureBuilt();
        body(pose.reset(), out);
    }

    public final void renderAnimated(Scene s, V v, DrawList out) {
        ensureBuilt();
        animate(s, v, pose.reset(), out);
    }

    @SuppressWarnings("unchecked")
    public final void renderAnimatedRaw(Scene s, Object v, DrawList out) {
        renderAnimated(s, (V) v, out);
    }

    @SuppressWarnings("unchecked")
    public final void tickFxRaw(Scene s, Object v, FxSink fx, Random r) {
        try {
            tickFx(s, (V) v, fx, r);
        } catch (RuntimeException ignored) {
            // un effet rate ne doit jamais faire planter le jeu
        }
    }

    // =================================================================== utilitaires
    protected int seg(int n) {
        return Math.max(3, Math.round(n * detail));
    }

    protected static float rnd(Random r, float s) {
        return (r.nextFloat() - 0.5f) * 2 * s;
    }

    protected static void burst(FxSink fx, Random r, float x, float y, float z, int c1, int c2, int count, float speed, float size) {
        for (int i = 0; i < count; i++) {
            double th = r.nextDouble() * Math.PI * 2;
            double ph = Math.acos(2 * r.nextDouble() - 1);
            double sp = speed * (0.4 + r.nextDouble() * 0.8);
            fx.particle(FxSink.SPARK, x, y, z, (float) (Math.sin(ph) * Math.cos(th) * sp), (float) (Math.cos(ph) * sp),
                    (float) (Math.sin(ph) * Math.sin(th) * sp), r.nextInt(3) == 0 ? c2 : c1, size * (0.6f + r.nextFloat() * 0.8f), 18 + r.nextInt(16));
        }
    }

    /** Pluie de pieces / confettis au-dessus d'un point (gros gains). */
    protected static void fountain(FxSink fx, Random r, float x, float y, float z, int kind, int[] colors, int count, float up) {
        for (int i = 0; i < count; i++) {
            // confettis fins : la camera du joueur est tout pres de l'ecran de la machine
            fx.particle(kind, x + rnd(r, 2f), y, z + rnd(r, 2f), rnd(r, 0.5f), up * (0.6f + r.nextFloat() * 0.6f), rnd(r, 0.5f),
                    colors[r.nextInt(colors.length)], 0.45f + r.nextFloat() * 0.45f, 30 + r.nextInt(25));
        }
    }

    /** Evenement ponctuel : vrai au tick "k" (entier) de l'animation. */
    protected static boolean at(float t, int k) {
        return t >= k && t < k + 1;
    }

    /** Largeur approchee d'un texte dans la police du jeu (unites de la police : capitales = 7). */
    public static float textUnits(String s, boolean bold) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int adv = switch (c) {
                case 'i', '.', ',', ':', ';', '!', '|', '\'' -> 2;
                case 'l', '`' -> 3;
                case 't', 'I', ' ', '"', '(', ')', '[', ']', '*' -> 4;
                case 'f', 'k', '<', '>', '{', '}' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
            w += adv + (bold ? 1 : 0);
        }
        return Math.max(0, w - 1);
    }

    /** Taille (hauteur des capitales, px) pour que le texte tienne dans "maxWidth" px. */
    public static float fit(String s, float size, float maxWidth, boolean bold) {
        float w = textUnits(s, bold) * size / 7f;
        return w <= maxWidth ? size : size * maxWidth / w;
    }

    /** Texte des montants : 12500 -> "12 500". */
    public static String fmt(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            b.append(s.charAt(i));
            int left = len - i - 1;
            if (left > 0 && left % 3 == 0) b.append(' ');
        }
        return (n < 0 ? "-" : "") + b;
    }
}
