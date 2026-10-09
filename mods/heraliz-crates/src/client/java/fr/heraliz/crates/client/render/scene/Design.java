package fr.heraliz.crates.client.render.scene;

import fr.heraliz.crates.registry.Snd;

import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.client.render.mesh.Mesh;

import java.util.Random;

/**
 * Une caisse : modele 3D (statique + parties animees), cadenas, cle, effets et choregraphie d'ouverture.
 * Repere : pixels (1/16 bloc), origine au coin du bloc principal, face avant vers +Z.
 * La caisse occupe x,z dans [-8, 24] (2 x 2 blocs) et y dans [0, 24] (1.5 bloc) ; les decors peuvent deborder.
 */
public abstract class Design {
    public static final float CX = 8, CZ = 8;

    public final String id;
    /** Couleur principale (lumiere, particules) et secondaire. */
    public final int glow, accent;
    /** Lueur de la cle (par defaut la couleur principale). */
    public int keyRgb;
    /** Couleurs des particules qui jaillissent de la cavite (par defaut celles de la caisse). */
    public int cavityRgb, cavityAccent;
    /** Roue d'objets (px). */
    public float ringY = 36, ringR = 22, revealY = 41, riseFromY = 12;
    /** Rayon du cercle runique au sol (px). */
    public float runeRadius = 25.6f;
    /** Hauteur du sol sur lequel tombe le cadenas (px). */
    public float lockFloorY = 0;
    public Padlock lock;
    public Mesh key = Mesh.EMPTY, keyGlow = Mesh.EMPTY;
    /** Niveau de detail (1 = plein, ~0.5 = eloigne) : multiplie les subdivisions. */
    public float detail = 1f;
    /**
     * Echelle de la caisse autour du centre du bloc, pieds au sol : modele, cadenas, effets, cercle runique et
     * particules. Tout le code d'une caisse reste ecrit en px "modele" ; seule la roue n'est pas mise a l'echelle
     * (ringY, ringR, revealY sont en px du monde). Pas d'echelle dans l'inventaire.
     */
    public float size = 1f;

    private final Pose pose = new Pose();
    private volatile boolean built;

    protected Design(String id, int glow, int accent) {
        this.id = id;
        this.glow = glow;
        this.accent = accent;
        this.keyRgb = glow;
        this.cavityRgb = glow;
        this.cavityAccent = accent;
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

    /** Construit les maillages statiques, le cadenas et la cle. */
    protected abstract void build();

    /** Modele de la caisse (corps, couvercle, parties animees). */
    protected abstract void model(CrateState s, Pose p, DrawList out);

    /** Place la pose au point d'accroche du cadenas (sommet de l'anse, face avant vers +Z). */
    protected abstract void lockPose(CrateState s, Pose p);

    /**
     * La caisse a-t-elle des parties animees au repos ? Au repos la caisse est figee (cuite une fois) ; ces parties-la
     * sont exclues de la cuisson (CrateState.frozen) et dessinees a chaque image par idle.
     */
    public boolean animatedAtRest() {
        return false;
    }

    /** Parties animees au repos (repere du bloc, echelle de la caisse a appliquer), dessinees par-dessus la caisse figee. */
    public void idle(CrateState s, DrawList out) {
    }

    /** Effets lumineux propres a la caisse (sprites, decors en orbite...). */
    protected void effects(CrateState s, Pose p, DrawList out) {
    }

    /**
     * Tremblement de camera des joueurs proches pour cette image (0 = aucun, 1 = impact fort). Par defaut : l'eclair
     * de l'ouverture et l'arret de la roue ; les caisses le redefinissent pour leurs propres impacts.
     */
    public float shake(CrateState s) {
        if (!s.active) return 0;
        float open = s.t >= CrateAnim.OPEN ? 0.3f * (float) Math.exp(-(s.t - CrateAnim.OPEN) * 0.3f) : 0;
        return Math.max(open, 0.25f * Seq.stopFlash(s));
    }

    /** Intensite du calque lumineux de l'atlas pour cette image. */
    protected float glowLevel(CrateState s) {
        return (0.7f + 0.3f * s.pulse(0.075f)) * (1f + 0.35f * Seq.excite(s));
    }

    /** Image complete. */
    public final void render(CrateState s, DrawList out) {
        ensureBuilt();
        Pose p = pose.reset();
        p.push().translate(Seq.shake(s), 0, 0);
        if (!s.item) scaleFrame(p);
        model(s, p, out);
        p.push();
        lockPose(s, p);
        float hangY = p.apply(0, 0, 0)[1];
        lock.draw(s, p, out, key, keyGlow, glow, keyRgb, glowLevel(s), lockFloorY * (s.item ? 1 : size), hangY);
        p.pop();
        p.pop();
        p.push();
        if (!s.item) {
            scaleFrame(p);
            CommonFx.ground(s, p, out, this);
            CommonFx.halo(s, p, out, this);
        }
        effects(s, p, out);
        p.pop();
        if (!s.item) {
            CommonFx.ringFx(s, p, out, this);
            CommonFx.reveal(s, p, out, this);
        }
    }

    /** Applique l'echelle de la caisse (autour du centre du bloc, au sol). */
    public void scaleFrame(Pose p) {
        if (size != 1f) p.translate(CX, 0, CZ).scale(size).translate(-CX, 0, -CZ);
    }

    /** Point du repere du modele -> px du monde (echelle de la caisse). */
    public float[] toWorld(float[] q) {
        return new float[]{CX + (q[0] - CX) * size, q[1] * size, CZ + (q[2] - CZ) * size};
    }

    /** Emission de particules et de sons ecrite dans le repere du modele, convertie a l'echelle de la caisse. */
    public FxSink scaledFx(FxSink fx) {
        if (size == 1f) return fx;
        final float k = size;
        return new FxSink() {
            @Override
            public void particle(int kind, float x, float y, float z, float vx, float vy, float vz, int rgb, float sz, int life) {
                fx.particle(kind, CX + (x - CX) * k, y * k, CZ + (z - CZ) * k, vx * k, vy * k, vz * k, rgb, sz * k, life);
            }

            @Override
            public void sound(Snd sound, float x, float y, float z, float volume, float pitch) {
                fx.sound(sound, CX + (x - CX) * k, y * k, CZ + (z - CZ) * k, volume, pitch);
            }
        };
    }

    /** Cle en tant qu'objet (inventaire) : pointe en bas, tete en haut. */
    public void renderKey(CrateState s, DrawList out) {
        ensureBuilt();
        Pose p = pose.reset();
        float pulse = s.pulse(0.12f);
        out.solid(p, key, 0.85f + 0.15f * pulse);
        out.additive(p, keyGlow, keyRgb, 0.25f + 0.25f * pulse);
    }

    // =================================================================== particules et sons
    /** Ambiance (appele a chaque tick client). */
    public void idleFx(CrateState s, FxSink fx, Random r) {
    }

    /** Position de l'entree de serrure (repere du modele). */
    public float[] keyholePos(CrateState s) {
        Pose p = new Pose();
        lockPose(s, p);
        float[] k = lock.keyhole();
        return p.apply(k[0], k[1], k[2]);
    }

    /** Tick k de la sequence d'ouverture (appele une fois par tick). */
    public void seqFx(CrateState s, int k, FxSink raw, Random r) {
        // "fx" : repere de la caisse (mis a l'echelle) ; "raw" : repere du monde (roue, objet gagne)
        FxSink fx = scaledFx(raw);
        float[] kh = keyholePos(s);
        float[] cav = cavityPos();
        if (k == 0) fx.sound(Snd.WHOOSH, kh[0], kh[1], kh[2], 0.5f, 1.4f);
        if (k >= CrateAnim.KEY_APPEAR && k < CrateAnim.KEY_IN) {
            float u = k / (float) CrateAnim.KEY_IN;
            float x = kh[0] + (1 - u) * 4f * (float) Math.sin(u * Math.PI * 2), y = kh[1] + 14 * (1 - u), z = kh[2] + 20 * (1 - u);
            for (int i = 0; i < 3; i++) {
                fx.particle(FxSink.SPARK, x + rnd(r, 1.5f), y + rnd(r, 1.5f), z + rnd(r, 1.5f), rnd(r, 0.15f), 0.15f, rnd(r, 0.15f),
                        i == 0 ? accent : glow, 1.9f, 14);
            }
        }
        if (k == CrateAnim.KEY_IN) fx.sound(Snd.KEY_INSERT, kh[0], kh[1], kh[2], 1f, 1f);
        if (k == CrateAnim.KEY_TURN) fx.sound(Snd.KEY_TURN, kh[0], kh[1], kh[2], 1f, 1f);
        if (k == CrateAnim.UNLOCK) {
            fx.sound(Snd.UNLOCK, kh[0], kh[1], kh[2], 1f, 1f);
            burst(fx, r, kh[0], kh[1], kh[2], glow, accent, 26, 1.9f, 2.2f);
        }
        if (k == CrateAnim.UNLOCK + 9) fx.sound(Snd.PADLOCK_DROP, kh[0], lockFloorY + 2, kh[2], 0.9f, 1f);
        if (k == CrateAnim.UNLOCK + 19) {
            for (int i = 0; i < 14; i++) {
                fx.particle(FxSink.SPARK, kh[0] + rnd(r, 3f), lockFloorY + 1.5f + r.nextFloat() * 3, kh[2] + rnd(r, 3f),
                        rnd(r, 0.3f), 0.2f + r.nextFloat() * 0.3f, rnd(r, 0.3f), r.nextBoolean() ? glow : 0xFFFFFF, 1.6f, 16);
            }
        }
        if (k == CrateAnim.OPEN) fx.sound(Snd.OPEN, cav[0], cav[1], cav[2], 1f, 1f);
        if (k == CrateAnim.OPEN + 8) {
            for (int i = 0; i < 50; i++) {
                fx.particle(r.nextBoolean() ? FxSink.SPARK : FxSink.DOT, cav[0] + rnd(r, 10f), cav[1] + rnd(r, 3f), cav[2] + rnd(r, 9f),
                        rnd(r, 1f), 1.9f + r.nextFloat() * 2.9f, rnd(r, 1f), r.nextInt(3) == 0 ? cavityAccent : cavityRgb, 2f + r.nextFloat() * 2.2f, 30 + r.nextInt(20));
            }
        }
        if (k > CrateAnim.OPEN + 8 && k < CrateAnim.CLOSE && r.nextFloat() < 0.8f) {
            fx.particle(FxSink.DOT, cav[0] + rnd(r, 10f), cav[1], cav[2] + rnd(r, 9f), 0, 0.8f + r.nextFloat() * 0.8f, 0, cavityRgb, 1.6f + r.nextFloat() * 1.6f, 30);
        }
        if (k == CrateAnim.RISE) fx.sound(Snd.ROLL_START, cav[0], cav[1], cav[2], 1f, 1f);
        int n = s.ringSize;
        if (n > 0 && k > CrateAnim.SPIN && k <= CrateAnim.STOP) {
            float slot = 360f / n;
            int a = (int) Math.floor((CrateAnim.ringAngle(k, n, s.winIndex) + slot / 2) / slot);
            int b = (int) Math.floor((CrateAnim.ringAngle(k - 1, n, s.winIndex) + slot / 2) / slot);
            if (a != b) {
                float prog = (k - CrateAnim.SPIN) / (float) (CrateAnim.STOP - CrateAnim.SPIN);
                raw.sound(Snd.ROLL_TICK, CX, ringY, CZ + ringR, 0.6f, 1.3f - 0.35f * prog + r.nextFloat() * 0.05f);
                raw.particle(FxSink.SPARK, CX, ringY + 8, CZ + ringR, 0, 0.3f, 0, glow, 1.6f, 8);
            }
        }
        // presage d'un objet epique ou mieux : battements de coeur pendant les derniers crans
        if (CommonFx.tease(s) > 0.2f && k < CrateAnim.STOP) {
            double a = (k - 1) * 0.55 - Math.PI / 2, b = k * 0.55 - Math.PI / 2;
            if (Math.floor(a / (2 * Math.PI)) != Math.floor(b / (2 * Math.PI))) {
                raw.sound(Snd.HEARTBEAT, CX, ringY, CZ + ringR, 0.9f, 1f);
            }
        }
        if (k == CrateAnim.STOP) {
            raw.sound(Snd.ROLL_STOP, CX, ringY, CZ + ringR, 1f, 1f);
            burst(raw, r, CX, ringY + 1.6f, CZ + ringR, s.rarityRgb, 0xFFFFFF, 60, 3.5f, 2.6f);
        }
        if (k == CrateAnim.STOP + 2) {
            Snd snd = s.rarityTier == 2 ? Snd.REWARD_LEGENDARY : (s.rarityTier == 1 ? Snd.REWARD_RARE : Snd.REWARD_COMMON);
            raw.sound(snd, CX, ringY, CZ + ringR, 1f, 1f);
        }
        if (k > CrateAnim.STOP + 4 && k < CrateAnim.FLY) {
            float[] w = new float[6];
            Roulette.winner(s, this, w);
            int count = s.rarityTier == 2 ? 4 : 2;
            for (int i = 0; i < count + 1; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double rad = 6.4 + r.nextDouble() * 6.4;
                raw.particle(FxSink.SPARK, w[0] + (float) (Math.cos(a) * rad), w[1] - 4.8f + r.nextFloat() * 3.2f, w[2] + (float) (Math.sin(a) * rad),
                        0, 0.5f + r.nextFloat() * 0.5f, 0, r.nextInt(3) == 0 ? 0xFFFFFF : s.rarityRgb, 1.4f + r.nextFloat() * 1.6f, 24);
            }
        }
        if (k >= CrateAnim.FLY && k < CrateAnim.REWARD) {
            CrateState c = s.copy();
            c.t = k;
            float[] w = new float[6];
            Roulette.winner(c, this, w);
            for (int i = 0; i < 4; i++) {
                raw.particle(FxSink.SPARK, w[0] + rnd(r, 1.6f), w[1] + rnd(r, 1.6f), w[2] + rnd(r, 1.6f), 0, 0, 0, s.rarityRgb, 1.9f, 12);
            }
            if (k == CrateAnim.REWARD - 1) {
                raw.sound(Snd.RECEIVE, w[0], w[1], w[2], 0.8f, 1f);
            }
        }
        if (k == CrateAnim.CLOSE + 11) fx.sound(Snd.CLOSE, cav[0], cav[1], cav[2], 1f, 1f);
        if (k == CrateAnim.SLAM) burst(fx, r, cav[0], cav[1] + 9.6f, cav[2], glow, accent, 16, 1.3f, 1.6f);
        if (k == CrateAnim.SLAM + 4) {
            fx.sound(Snd.KEY_TURN, kh[0], kh[1], kh[2], 0.6f, 1.5f);
            burst(fx, r, kh[0], kh[1], kh[2], glow, 0xFFFFFF, 14, 1.2f, 1.4f);
        }
        themeFx(s, k, fx, r);
    }

    /** Effets propres a la caisse pendant la sequence. */
    protected void themeFx(CrateState s, int k, FxSink fx, Random r) {
    }

    /** Fond de la cavite (px) : lumiere et particules a l'ouverture. */
    public float[] cavityPos() {
        return new float[]{CX, 6, CZ};
    }

    // =================================================================== utilitaires
    /** Nombre de subdivisions selon le niveau de detail. */
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

    /** Taille des objets en orbite : ils n'apparaissent que pendant l'ouverture (0 au repos). */
    protected static float orbitScale(CrateState s) {
        if (!s.active) return 0;
        float t = s.t;
        return Ease.outBack(Ease.range(t, CrateAnim.UNLOCK, CrateAnim.OPEN + 6), 1.6f) * (1 - Ease.inCubic(Ease.range(t, CrateAnim.CLOSE, CrateAnim.END - 4)));
    }

    /** Pose de rotation autour du centre de la caisse. */
    protected static void orbit(Pose p, float angleDeg, float radius, float y) {
        p.translate(CX, y, CZ).rotY(angleDeg).translate(radius, 0, 0);
    }
}
