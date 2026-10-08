package fr.heraliz.wonder.jetgun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.wonder.jetgun.JetCalculs;
import fr.heraliz.wonder.jetgun.JetGun;
import fr.heraliz.wonder.jetgun.JetSons;
import fr.heraliz.wonder.jetgun.net.Broyage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Effets du Jet Gun côté client, dans des réserves de taille fixe (tableaux parallèles, aucune allocation par image),
 * dessinés en deux lots ({@link JetFxTypes#FUMEE} translucide, {@link JetFxTypes#LUMIERE} additif) :
 * <ul>
 *   <li><b>tireurs</b> (8 au plus) : repérés par les événements de tir de TaCZ (le sien et ceux des autres joueurs) ;
 *   l'emballement (0..1) monte en 0,45 s quand on tire et redescend en 0,9 s ; la chaleur et la surchauffe se lisent dans
 *   le NBT de l'arme tenue (synchronisé par Minecraft, y compris pour les autres joueurs) ;</li>
 *   <li><b>air aspiré</b> : traits d'air qui spiralent vers l'entrée d'air en accélérant (tourbillon), tourbillon translucide
 *   devant la soufflante, poussière du sol (à sa couleur) et petits débris happés ;</li>
 *   <li><b>zombie déchiqueté</b> (paquet {@link Broyage}) : corps caché, lambeaux et os happés par l'entrée d'air ou projetés,
 *   gouttes et brume rouge, poussière, morceaux de chair et d'os de Minecraft, bruit de broyage ; gerbe à la tuyère ;</li>
 *   <li><b>chaleur</b> : ondulation au-dessus de la section chaude au-delà de 55 %, lueur orange (vue en 3e personne),
 *   étincelles au-delà de 85 % ; <b>surchauffe</b> : détonation, fumée noire et étincelles, panache tant que l'arme est
 *   bloquée ; <b>purge</b> : jets de vapeur des soupapes ;</li>
 *   <li><b>créatures aspirées</b> : elles penchent vers la bouche et tremblent ; <b>caméra</b> du tireur qui vibre.</li>
 * </ul>
 */
public final class JetFx {
    private JetFx() {
    }

    static final RandomSource RNG = RandomSource.create();
    static long tick;
    private static final float DEG = Mth.DEG_TO_RAD;

    // ================================================================== tireurs
    static final int MAX_T = 8;
    static final int[] tId = new int[MAX_T];
    static final boolean[] tLocal = new boolean[MAX_T];
    static final long[] tTir = new long[MAX_T], tPurge = new long[MAX_T], tVerrouDebut = new long[MAX_T];
    static final float[] tSpin = new float[MAX_T], tSpinP = new float[MAX_T], tChaleur = new float[MAX_T];
    static final boolean[] tVerrou = new boolean[MAX_T], tFp = new boolean[MAX_T];
    static final JetSon[] tSon = new JetSon[MAX_T];
    // cadre (monde) : bouche O, axe d'aspiration D (regard), axe de l'arme G, base U/V autour de D ; point chaud H, soupapes S, tuyère X ;
    // R = rayon de l'entrée d'air (blocs)
    static final double[][] tO = new double[MAX_T][3], tH = new double[MAX_T][3], tS = new double[MAX_T][3], tX = new double[MAX_T][3];
    static final float[][] tD = new float[MAX_T][3], tG = new float[MAX_T][3], tU = new float[MAX_T][3], tV = new float[MAX_T][3];
    static final float[] tR = new float[MAX_T];

    static {
        for (int i = 0; i < MAX_T; i++) {
            tId[i] = -1;
            tPurge[i] = -1;
        }
    }

    // ================================================================== traits d'air (en coordonnées du tourbillon de leur tireur)
    private static final int MAX_S = 384;
    private static final int[] sT = new int[MAX_S];
    private static final float[] sS0 = new float[MAX_S], sPhi = new float[MAX_S], sR0 = new float[MAX_S], sT0 = new float[MAX_S], sDur = new float[MAX_S],
            sL = new float[MAX_S], sA = new float[MAX_S], sSens = new float[MAX_S];
    private static final int[] sCoul = new int[MAX_S];
    private static int nS;

    // ================================================================== nuages (poussière, fumée, vapeur, brume, ondulation)
    private static final int MAX_P = 640;
    private static final double[] pX = new double[MAX_P], pY = new double[MAX_P], pZ = new double[MAX_P], pPx = new double[MAX_P], pPy = new double[MAX_P],
            pPz = new double[MAX_P];
    private static final float[] pVx = new float[MAX_P], pVy = new float[MAX_P], pVz = new float[MAX_P], pTaille = new float[MAX_P], pCroit = new float[MAX_P],
            pA = new float[MAX_P], pRot = new float[MAX_P], pFrot = new float[MAX_P], pPoussee = new float[MAX_P];
    private static final int[] pVie = new int[MAX_P], pVieMax = new int[MAX_P], pCoul = new int[MAX_P], pCase = new int[MAX_P], pAttire = new int[MAX_P];
    private static int nP;

    // ================================================================== morceaux (lambeaux, os, gouttes, débris)
    private static final int MAX_M = 320;
    private static final double[] mX = new double[MAX_M], mY = new double[MAX_M], mZ = new double[MAX_M], mPx = new double[MAX_M], mPy = new double[MAX_M],
            mPz = new double[MAX_M];
    private static final float[] mVx = new float[MAX_M], mVy = new float[MAX_M], mVz = new float[MAX_M], mTaille = new float[MAX_M], mRot = new float[MAX_M],
            mVrot = new float[MAX_M];
    private static final int[] mVie = new int[MAX_M], mVieMax = new int[MAX_M], mCoul = new int[MAX_M], mCase = new int[MAX_M], mAttire = new int[MAX_M];
    private static int nM;

    // ================================================================== étincelles et éclats (additif)
    private static final int MAX_E = 256;
    private static final double[] eX = new double[MAX_E], eY = new double[MAX_E], eZ = new double[MAX_E], ePx = new double[MAX_E], ePy = new double[MAX_E],
            ePz = new double[MAX_E];
    private static final float[] eVx = new float[MAX_E], eVy = new float[MAX_E], eVz = new float[MAX_E], eTaille = new float[MAX_E];
    private static final int[] eVie = new int[MAX_E], eVieMax = new int[MAX_E], eCoul = new int[MAX_E], eCase = new int[MAX_E];
    private static int nE;

    // ================================================================== corps cachés (déchiquetés)
    private static final int MAX_C = 64;
    private static final int[] cId = new int[MAX_C];
    private static final long[] cFin = new long[MAX_C];
    private static int nC;

    // ================================================================== caméra
    private static float secAmp, secT0 = -1000;

    // ================================================================== statistiques
    private static long nsRendu, maxNs;
    private static int images, broyes, picS, picP, picM, picE;

    // ------------------------------------------------------------------ point d'écoute du tir (TaCZ)
    /** Un tir de Jet Gun vu par ce client (le sien ou celui d'un autre joueur). */
    public static void tir(LivingEntity tireur) {
        int i = emplacement(tireur, true);
        if (i < 0) return;
        if (tSpin[i] <= 0.02f && tick - tTir[i] > 6) son(tireur, JetSons.DEMARRAGE.get(), 0.95f, 1.0f);
        tTir[i] = tick;
    }

    /** Début d'une purge (rechargement TaCZ du Jet Gun), la sienne ou celle d'un autre joueur. */
    public static void purge(LivingEntity e) {
        int i = emplacement(e, true);
        if (i >= 0) tPurge[i] = tick;
    }

    static int emplacement(LivingEntity e, boolean creer) {
        int libre = -1;
        for (int i = 0; i < MAX_T; i++) {
            if (tId[i] == e.getId()) return i;
            if (tId[i] < 0 && libre < 0) libre = i;
        }
        if (!creer || libre < 0) return -1;
        tId[libre] = e.getId();
        tSpin[libre] = tSpinP[libre] = 0;
        tTir[libre] = -100;
        tPurge[libre] = -1;
        tVerrou[libre] = false;
        tChaleur[libre] = 0;
        Minecraft mc = Minecraft.getInstance();
        tLocal[libre] = mc.player != null && mc.player.getId() == e.getId();
        return libre;
    }

    private static void liberer(int i) {
        tId[i] = -1;
        if (tSon[i] != null) {
            tSon[i].arreter();
            tSon[i] = null;
        }
    }

    static float spin(int i, int id) {
        return i >= 0 && i < MAX_T && tId[i] == id ? tSpin[i] : 0f;
    }

    static float chaleurDe(int i) {
        return tChaleur[i];
    }

    /** Emballement du joueur local (0..1), pour la jauge et la caméra. */
    public static float spinLocal() {
        for (int i = 0; i < MAX_T; i++) if (tId[i] >= 0 && tLocal[i]) return tSpin[i];
        return 0f;
    }

    /** Tick (client) où le joueur local est entré en surchauffe, ou -1. */
    public static long verrouLocal() {
        for (int i = 0; i < MAX_T; i++) if (tId[i] >= 0 && tLocal[i] && tVerrou[i]) return tVerrouDebut[i];
        return -1;
    }

    // ================================================================== tick
    public static void tick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null || mc.player == null) {
            vider();
            return;
        }
        if (mc.isPaused()) return;
        tick++;
        if (JetGun.tientJetGun(mc.player)) emplacement(mc.player, true);
        for (int i = 0; i < MAX_T; i++) {
            if (tId[i] < 0) continue;
            Entity en = lv.getEntity(tId[i]);
            if (!(en instanceof LivingEntity e) || e.isRemoved()) {
                liberer(i);
                continue;
            }
            ItemStack arme = e.getMainHandItem();
            boolean tient = JetGun.estJetGun(arme);
            float ch = tient ? JetGun.chaleur(arme) : 0f;
            boolean verrou = tient && JetGun.surchauffe(arme);
            tChaleur[i] = ch;
            boolean tire = tient && !verrou && tick - tTir[i] <= 3;
            tSpinP[i] = tSpin[i];
            if (tire) tSpin[i] = Math.min(1f, tSpin[i] + 1f / 9f);
            else {
                if (tSpin[i] > 0.45f && tSpin[i] - 1f / 18f <= 0.45f) son(e, JetSons.ARRET.get(), 0.85f, 1.0f);
                tSpin[i] = Math.max(0f, tSpin[i] - 1f / 18f);
            }
            if (!tient && tSpin[i] <= 0 && tPurge[i] < 0) {
                liberer(i);
                continue;
            }
            cadre(i, e, 1f, mc);
            // son du réacteur (boucle) : une instance par séance de tir
            // (sans périphérique audio le moteur de son refuse tout : on ne réessaie qu'une fois par seconde)
            if (tSpin[i] > 0.02f && (tSon[i] == null || tSon[i].isStopped() || tick % 20 == 0 && !mc.getSoundManager().isActive(tSon[i]))) {
                if (tSon[i] != null) tSon[i].arreter();
                tSon[i] = new JetSon(e, i);
                mc.getSoundManager().play(tSon[i]);
            }
            if (tSpin[i] > 0.12f) emettreAir(lv, i, tSpin[i]);
            // surchauffe
            if (verrou && !tVerrou[i]) {
                tVerrouDebut[i] = tick;
                surchauffe(lv, i, e);
            }
            tVerrou[i] = verrou;
            if (verrou) panache(i);
            else if (ch > 0.55f) chaleur(i, ch);
            // purge : jets de vapeur des soupapes de 0,55 s à 1,45 s
            if (tPurge[i] >= 0) {
                long a = tick - tPurge[i];
                if (a >= 11 && a <= 29) vapeur(i, a);
                if (a > 40) tPurge[i] = -1;
            }
        }
        avancer(lv, mc);
        if (secAmp > 0 && tick - secT0 > 30) secAmp = 0;
    }

    // ------------------------------------------------------------------ cadre du tireur
    private static final float[] OFF = new float[3];
    private static final Vector3f TMP = new Vector3f();

    /**
     * Repère de l'arme du tireur i. À la première personne (joueur local) : bouche donnée par TaCZ (décalage du canon), et
     * les autres points déduits par la vue de la hanche du modèle (constantes de jg_modele) ; sinon arme à la hanche droite.
     */
    static void cadre(int i, LivingEntity e, float partial, Minecraft mc) {
        float yr = e.getViewYRot(partial) * DEG, xr = e.getViewXRot(partial) * DEG;
        float dx = -Mth.sin(yr) * Mth.cos(xr), dy = -Mth.sin(xr), dz = Mth.cos(yr) * Mth.cos(xr);
        double ex = Mth.lerp(partial, e.xo, e.getX()), ey = Mth.lerp(partial, e.yo, e.getY()) + e.getEyeHeight(), ez = Mth.lerp(partial, e.zo, e.getZ());
        float[] d = tD[i];
        d[0] = dx;
        d[1] = dy;
        d[2] = dz;
        // base perpendiculaire à D
        float ux, uy, uz;
        if (Math.abs(dy) < 0.95f) {
            ux = dz;
            uy = 0;
            uz = -dx;
        } else {
            ux = 1;
            uy = 0;
            uz = 0;
        }
        float nu = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= nu;
        uy /= nu;
        uz /= nu;
        tU[i][0] = ux;
        tU[i][1] = uy;
        tU[i][2] = uz;
        tV[i][0] = dy * uz - dz * uy;
        tV[i][1] = dz * ux - dx * uz;
        tV[i][2] = dx * uy - dy * ux;
        boolean fp = tLocal[i] && mc.options.getCameraType().isFirstPerson() && JetGun.tacz;
        tFp[i] = false;
        if (fp) {
            Camera cam = mc.gameRenderer.getMainCamera();
            if (JetTaczClient.decalageCanon(cam, OFF)) {
                tFp[i] = true;
                Vec3 c = cam.getPosition();
                tO[i][0] = c.x + OFF[0];
                tO[i][1] = c.y + OFF[1];
                tO[i][2] = c.z + OFF[2];
                float s = JetTaczClient.echelleVue();
                pointVue(cam, c, s, JetVue.H, tH[i]);
                pointVue(cam, c, s, JetVue.S, tS[i]);
                pointVue(cam, c, s, JetVue.X, tX[i]);
                JetVue.versMonde(cam, JetVue.AVANT, TMP);
                tG[i][0] = TMP.x;
                tG[i][1] = TMP.y;
                tG[i][2] = TMP.z;
                tR[i] = 10.35f / 16f * s;
                return;
            }
        }
        // 3e personne : arme à la hanche droite (échelle 0,5 du gunpack)
        float rx = -Mth.cos(yr), rz = -Mth.sin(yr);
        tO[i][0] = ex + dx * 1.2 + rx * 0.32;
        tO[i][1] = ey + dy * 1.2 - 0.48;
        tO[i][2] = ez + dz * 1.2 + rz * 0.32;
        tH[i][0] = ex + dx * 0.32 + rx * 0.32;
        tH[i][1] = ey + dy * 0.32 - 0.3;
        tH[i][2] = ez + dz * 0.32 + rz * 0.32;
        tS[i][0] = tH[i][0] + dx * 0.05 + rx * 0.05;
        tS[i][1] = tH[i][1] + 0.18;
        tS[i][2] = tH[i][2] + dz * 0.05 + rz * 0.05;
        tX[i][0] = ex - dx * 0.05 + rx * 0.32;
        tX[i][1] = ey - dy * 0.05 - 0.48;
        tX[i][2] = ez - dz * 0.05 + rz * 0.32;
        tG[i][0] = dx;
        tG[i][1] = dy;
        tG[i][2] = dz;
        tR[i] = 0.32f;
    }

    private static void pointVue(Camera cam, Vec3 c, float s, float[] delta, double[] out) {
        Vector3f v = JetVue.decalageMonde(cam, delta, s);
        out[0] = c.x + OFF[0] + v.x;
        out[1] = c.y + OFF[1] + v.y;
        out[2] = c.z + OFF[2] + v.z;
    }

    // ------------------------------------------------------------------ émissions
    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();
    private static final float PORTEE_FX = 11f, ANGLE_FX = 24f;

    private static void emettreAir(ClientLevel lv, int i, float spin) {
        float tan = Mth.sin(ANGLE_FX * DEG) / Mth.cos(ANGLE_FX * DEG);
        int n = (int) (spin * 8f + RNG.nextFloat());
        for (int k = 0; k < n && nS < MAX_S; k++) {
            int j = nS++;
            float s0 = 1.2f + (float) Math.pow(RNG.nextFloat(), 0.8) * PORTEE_FX;
            sT[j] = i;
            sS0[j] = s0;
            sPhi[j] = RNG.nextFloat() * Mth.TWO_PI;
            sR0[j] = (0.4f + s0 * tan) * (0.25f + 0.75f * Mth.sqrt(RNG.nextFloat()));
            sT0[j] = tick + RNG.nextFloat();
            sDur[j] = 9f + s0 * 0.75f + RNG.nextFloat() * 4f;
            sL[j] = 0.07f + RNG.nextFloat() * 0.08f;
            sA[j] = 0.55f + RNG.nextFloat() * 0.4f;
            sSens[j] = 1f;
            sCoul[j] = RNG.nextFloat() < 0.25f ? 0xD8E6F2 : 0xF2F6FA;
        }
        if (nS > picS) picS = nS;
        // poussière du sol, happée vers la bouche
        float[] d = tD[i];
        for (int k = 0; k < 2; k++) {
            if (RNG.nextFloat() > spin) continue;
            float s = 2f + RNG.nextFloat() * (PORTEE_FX - 2f);
            float rad = (0.4f + s * tan) * RNG.nextFloat();
            float ang = RNG.nextFloat() * Mth.TWO_PI;
            double px = tO[i][0] + d[0] * s + (tU[i][0] * Mth.cos(ang) + tV[i][0] * Mth.sin(ang)) * rad;
            double py = tO[i][1] + d[1] * s + (tU[i][1] * Mth.cos(ang) + tV[i][1] * Mth.sin(ang)) * rad;
            double pz = tO[i][2] + d[2] * s + (tU[i][2] * Mth.cos(ang) + tV[i][2] * Mth.sin(ang)) * rad;
            int y0 = Mth.floor(py), yMin = y0 - 3;
            for (int y = y0; y >= yMin; y--) {
                MP.set(Mth.floor(px), y, Mth.floor(pz));
                BlockState st = lv.getBlockState(MP);
                if (st.isAir() || st.getCollisionShape(lv, MP).isEmpty()) continue;
                double ys = y + st.getCollisionShape(lv, MP).max(net.minecraft.core.Direction.Axis.Y);
                int coul = melange(st.getMapColor(lv, MP).col, 0xD8CDB8, 0.55f);
                nuage(px + (RNG.nextFloat() - 0.5f) * 0.8, ys + 0.1, pz + (RNG.nextFloat() - 0.5f) * 0.8, 0, 0.05f + RNG.nextFloat() * 0.06f, 0,
                        0.35f + RNG.nextFloat() * 0.35f, 0.03f, 40, i, coul, 0.75f, (k & 1) == 0 ? JetFxTypes.FUMEE_A : JetFxTypes.POUSSIERE, 0.9f, 0f);
                if (RNG.nextFloat() < 0.5f) {
                    morceau(px, ys + 0.1, pz, (RNG.nextFloat() - 0.5f) * 0.05f, 0.12f + RNG.nextFloat() * 0.1f, (RNG.nextFloat() - 0.5f) * 0.05f,
                            0.05f + RNG.nextFloat() * 0.04f, 40, melange(coul, 0x302820, 0.4f), JetFxTypes.DEBRIS, i);
                }
                break;
            }
        }
    }

    /** Échelle des effets près de l'arme : à la première personne l'arme est dessinée tout près de l'œil. */
    private static float ech(int i) {
        return tFp[i] ? 0.38f : 1f;
    }

    private static void chaleur(int i, float ch) {
        float k = (ch - 0.55f) / 0.45f, e = ech(i);
        if (RNG.nextFloat() < 0.15f + 0.35f * k) {
            nuage(tH[i][0] + (RNG.nextFloat() - 0.5f) * 0.25 * e, tH[i][1], tH[i][2] + (RNG.nextFloat() - 0.5f) * 0.25 * e, (RNG.nextFloat() - 0.5f) * 0.006f * e,
                    (0.02f + RNG.nextFloat() * 0.015f) * e, (RNG.nextFloat() - 0.5f) * 0.006f * e, (0.08f + RNG.nextFloat() * 0.05f) * e, 0.01f * e, 20, -1,
                    0xEAE4DC, 0.1f + 0.08f * k, JetFxTypes.VAPEUR, 0.96f, 0.0015f * e);
        }
        if (ch > 0.85f && RNG.nextFloat() < 0.18f) etincelle(tH[i][0], tH[i][1], tH[i][2], 0.06f * e, 0xFFB040, 8, e);
    }

    private static void surchauffe(ClientLevel lv, int i, LivingEntity e) {
        son(e, JetSons.SURCHAUFFE.get(), 1.0f, 1.0f);
        double x = tH[i][0], y = tH[i][1], z = tH[i][2];
        float s = ech(i);
        for (int k = 0; k < 16; k++) {
            float a = RNG.nextFloat() * Mth.TWO_PI;
            nuage(x, y, z, Mth.cos(a) * 0.05f * s, (0.04f + RNG.nextFloat() * 0.08f) * s, Mth.sin(a) * 0.05f * s, (0.22f + RNG.nextFloat() * 0.2f) * s, 0.025f * s,
                    30 + RNG.nextInt(20), -1, k % 3 == 0 ? 0x3A3633 : 0x1E1C1B, 0.85f, (k & 1) == 0 ? JetFxTypes.FUMEE_A : JetFxTypes.FUMEE_B, 0.93f, 0.004f * s);
        }
        for (int k = 0; k < 22; k++) etincelle(x, y, z, 0.18f * s, k % 2 == 0 ? 0xFFC060 : 0xFF7A20, 10 + RNG.nextInt(8), s);
        eclat(x, y, z, 0.7f * s, 0xFF9A40, JetFxTypes.ECLAT, 6);
        eclat(x, y, z, 0.5f * s, 0xFFD8A0, JetFxTypes.ANNEAU, 8);
        if (tLocal[i]) {
            secAmp = 1.6f;
            secT0 = tick;
        }
    }

    private static void panache(int i) {
        float s = ech(i);
        if ((tick & 1) == 0) {
            nuage(tH[i][0] + (RNG.nextFloat() - 0.5f) * 0.15 * s, tH[i][1], tH[i][2] + (RNG.nextFloat() - 0.5f) * 0.15 * s, (RNG.nextFloat() - 0.5f) * 0.015f * s,
                    (0.05f + RNG.nextFloat() * 0.03f) * s, (RNG.nextFloat() - 0.5f) * 0.015f * s, 0.16f * s, 0.025f * s, 34, -1, 0x2A2725, 0.6f,
                    RNG.nextBoolean() ? JetFxTypes.FUMEE_A : JetFxTypes.FUMEE_B, 0.95f, 0.003f * s);
        }
        if (RNG.nextFloat() < 0.12f) etincelle(tH[i][0], tH[i][1], tH[i][2], 0.09f * s, 0xFF9030, 9, s);
        if (RNG.nextFloat() < 0.35f && !tFp[i]) {
            nuage(tX[i][0], tX[i][1], tX[i][2], -tD[i][0] * 0.04f, 0.03f, -tD[i][2] * 0.04f, 0.14f, 0.03f, 26, -1, 0x302C29, 0.5f, JetFxTypes.FUMEE_B, 0.95f,
                    0.002f);
        }
    }

    private static void vapeur(int i, long age) {
        float k = age < 14 ? 1f : 1f - (age - 14) / 18f, s = ech(i);
        for (int n = 0; n < 3; n++) {
            float a = RNG.nextFloat() * Mth.TWO_PI;
            nuage(tS[i][0], tS[i][1], tS[i][2], Mth.cos(a) * 0.03f * s, (0.14f + RNG.nextFloat() * 0.1f * k) * s, Mth.sin(a) * 0.03f * s,
                    (0.08f + RNG.nextFloat() * 0.06f) * s, 0.025f * s, 14 + RNG.nextInt(6), -1, 0xF4F6F8, 0.8f * k, JetFxTypes.VAPEUR, 0.88f, 0.004f * s);
        }
    }

    /** Créatures déchiquetées (paquet du serveur) : corps cachés, lambeaux, brume, bruit. */
    public static void broyage(Broyage m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Entity te = lv.getEntity(m.tireur());
        int i = te instanceof LivingEntity le ? emplacement(le, true) : -1;
        for (int k = 0; k < m.n(); k++) {
            broyes++;
            double x = m.x()[k], y = m.y()[k], z = m.z()[k];
            Entity e = lv.getEntity(m.ids()[k]);
            int teinte = 0x7A1A14;
            float h = 1.8f;
            if (e != null) {
                cacher(e.getId());
                h = e.getBbHeight();
                EntityType<?> t = e.getType();
                if (t == EntityType.HUSK) teinte = 0x8A5A2A;
                else if (t == EntityType.ZOMBIE || t == EntityType.ZOMBIE_VILLAGER || t == EntityType.DROWNED) teinte = 0x5E3A1E;
                else if (t == EntityType.SKELETON || t == EntityType.STRAY) teinte = 0xD8D4C0;
            }
            for (int n = 0; n < 11; n++) {
                boolean happe = i >= 0 && n % 2 == 0;
                float a = RNG.nextFloat() * Mth.TWO_PI;
                float v = 0.08f + RNG.nextFloat() * 0.16f;
                morceau(x + (RNG.nextFloat() - 0.5f) * 0.5, y + (RNG.nextFloat() - 0.5f) * h * 0.6, z + (RNG.nextFloat() - 0.5f) * 0.5, Mth.cos(a) * v,
                        0.1f + RNG.nextFloat() * 0.18f, Mth.sin(a) * v, 0.06f + RNG.nextFloat() * 0.06f, 36 + RNG.nextInt(16),
                        n % 3 == 0 ? melange(teinte, 0x9A2A20, 0.5f) : 0xFFFFFF, n < 8 ? JetFxTypes.CHAIR : JetFxTypes.OS, happe ? i : -1);
            }
            for (int n = 0; n < 5; n++) {
                float a = RNG.nextFloat() * Mth.TWO_PI;
                morceau(x, y, z, Mth.cos(a) * 0.12f, 0.08f + RNG.nextFloat() * 0.15f, Mth.sin(a) * 0.12f, 0.025f + RNG.nextFloat() * 0.02f, 22, 0xFFFFFF,
                        JetFxTypes.GOUTTE, n % 2 == 0 ? i : -1);
            }
            for (int n = 0; n < 4; n++) {
                nuage(x, y, z, (RNG.nextFloat() - 0.5f) * 0.06f, 0.02f, (RNG.nextFloat() - 0.5f) * 0.06f, 0.35f + RNG.nextFloat() * 0.25f, 0.05f, 22, i,
                        n == 0 ? 0x5A0E0A : 0x7A1612, 0.6f, JetFxTypes.BRUME, 0.9f, 0f);
            }
            nuage(x, y - h * 0.3, z, 0, 0.03f, 0, 0.5f, 0.06f, 26, i, melange(teinte, 0xC8BCA8, 0.6f), 0.55f, JetFxTypes.POUSSIERE, 0.9f, 0f);
            eclat(x, y, z, 0.45f, 0xFFE8E0, JetFxTypes.ANNEAU, 5);
            // morceaux de chair et d'os de Minecraft (particules du jeu, avec élan vers la bouche)
            try {
                for (int n = 0; n < 6; n++) {
                    float vx = (float) (i >= 0 ? (tO[i][0] - x) * 0.08 : 0) + (RNG.nextFloat() - 0.5f) * 0.2f;
                    float vz = (float) (i >= 0 ? (tO[i][2] - z) * 0.08 : 0) + (RNG.nextFloat() - 0.5f) * 0.2f;
                    Particle p = mc.particleEngine.createParticle(n < 4 ? CHAIR : OS, x, y, z, vx, 0.15f + RNG.nextFloat() * 0.15f, vz);
                    if (p != null) p.setParticleSpeed(vx, 0.15f + RNG.nextFloat() * 0.15f, vz);
                }
            } catch (RuntimeException ignored) {
            }
            if (k < 4) lv.playLocalSound(x, y, z, JetSons.BROYAGE.get(), SoundSource.HOSTILE, 0.9f, 0.85f + RNG.nextFloat() * 0.3f, false);
        }
        // gerbe à la tuyère (vue des autres et en 3e personne)
        if (i >= 0 && !tFp[i]) {
            for (int n = 0; n < 4; n++) {
                nuage(tX[i][0], tX[i][1], tX[i][2], -tD[i][0] * 0.18f + (RNG.nextFloat() - 0.5f) * 0.05f, 0.02f, -tD[i][2] * 0.18f + (RNG.nextFloat() - 0.5f) * 0.05f,
                        0.18f, 0.05f, 18, -1, 0x5A1410, 0.55f, JetFxTypes.BRUME, 0.9f, 0f);
                morceau(tX[i][0], tX[i][1], tX[i][2], -tD[i][0] * 0.25f + (RNG.nextFloat() - 0.5f) * 0.1f, 0.06f, -tD[i][2] * 0.25f + (RNG.nextFloat() - 0.5f) * 0.1f,
                        0.05f, 20, 0x4A2018, JetFxTypes.DEBRIS, -1);
            }
        }
    }

    private static final ItemParticleOption CHAIR = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH));
    private static final ItemParticleOption OS = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE));

    private static void cacher(int id) {
        for (int k = 0; k < nC; k++) {
            if (cId[k] == id) {
                cFin[k] = tick + 40;
                return;
            }
        }
        if (nC >= MAX_C) return;
        cId[nC] = id;
        cFin[nC] = tick + 40;
        nC++;
    }

    private static boolean cache(int id) {
        for (int k = 0; k < nC; k++) if (cId[k] == id) return true;
        return false;
    }

    // ------------------------------------------------------------------ création dans les réserves
    private static void nuage(double x, double y, double z, float vx, float vy, float vz, float taille, float croit, int vie, int attire, int coul, float a,
                              int cell, float frot, float poussee) {
        if (nP >= MAX_P) return;
        int i = nP++;
        pX[i] = pPx[i] = x;
        pY[i] = pPy[i] = y;
        pZ[i] = pPz[i] = z;
        pVx[i] = vx;
        pVy[i] = vy;
        pVz[i] = vz;
        pTaille[i] = taille;
        pCroit[i] = croit;
        pVie[i] = 0;
        pVieMax[i] = vie;
        pAttire[i] = attire;
        pCoul[i] = coul;
        pA[i] = a;
        pCase[i] = cell;
        pRot[i] = RNG.nextFloat() * Mth.TWO_PI;
        pFrot[i] = frot;
        pPoussee[i] = poussee;
        if (nP > picP) picP = nP;
    }

    private static void morceau(double x, double y, double z, float vx, float vy, float vz, float taille, int vie, int coul, int cell, int attire) {
        if (nM >= MAX_M) return;
        int i = nM++;
        mX[i] = mPx[i] = x;
        mY[i] = mPy[i] = y;
        mZ[i] = mPz[i] = z;
        mVx[i] = vx;
        mVy[i] = vy;
        mVz[i] = vz;
        mTaille[i] = taille;
        mRot[i] = RNG.nextFloat() * Mth.TWO_PI;
        mVrot[i] = (RNG.nextFloat() - 0.5f) * 0.9f;
        mVie[i] = 0;
        mVieMax[i] = vie;
        mCoul[i] = coul;
        mCase[i] = cell;
        mAttire[i] = attire;
        if (nM > picM) picM = nM;
    }

    private static void etincelle(double x, double y, double z, float v, int coul, int vie) {
        etincelle(x, y, z, v, coul, vie, 1f);
    }

    private static void etincelle(double x, double y, double z, float v, int coul, int vie, float s) {
        if (nE >= MAX_E) return;
        int i = nE++;
        eX[i] = ePx[i] = x;
        eY[i] = ePy[i] = y;
        eZ[i] = ePz[i] = z;
        float a = RNG.nextFloat() * Mth.TWO_PI, b = (RNG.nextFloat() - 0.2f) * 1.4f;
        eVx[i] = Mth.cos(a) * Mth.cos(b) * v;
        eVy[i] = Mth.sin(b) * v + 0.04f;
        eVz[i] = Mth.sin(a) * Mth.cos(b) * v;
        eTaille[i] = (0.035f + RNG.nextFloat() * 0.02f) * s;
        eVie[i] = 0;
        eVieMax[i] = vie;
        eCoul[i] = coul;
        eCase[i] = JetFxTypes.ETINCELLE;
        if (nE > picE) picE = nE;
    }

    private static void eclat(double x, double y, double z, float taille, int coul, int cell, int vie) {
        if (nE >= MAX_E) return;
        int i = nE++;
        eX[i] = ePx[i] = x;
        eY[i] = ePy[i] = y;
        eZ[i] = ePz[i] = z;
        eVx[i] = eVy[i] = eVz[i] = 0;
        eTaille[i] = taille;
        eVie[i] = 0;
        eVieMax[i] = vie;
        eCoul[i] = coul;
        eCase[i] = cell;
    }

    // ------------------------------------------------------------------ avance des réserves
    private static void avancer(ClientLevel lv, Minecraft mc) {
        float now = tick;
        int w = 0;
        for (int i = 0; i < nS; i++) {
            int t = sT[i];
            if (tId[t] < 0 || now - sT0[i] > sDur[i]) continue;
            // un tireur qui s'arrête : ses traits finissent leur course (accélérés)
            if (tSpin[t] < 0.3f) sDur[i] = Math.min(sDur[i], now - sT0[i] + 4);
            if (i != w) copierTrait(i, w);
            w++;
        }
        nS = w;
        w = 0;
        for (int i = 0; i < nP; i++) {
            if (++pVie[i] >= pVieMax[i]) continue;
            pPx[i] = pX[i];
            pPy[i] = pY[i];
            pPz[i] = pZ[i];
            int t = pAttire[i];
            if (t >= 0 && tId[t] >= 0 && tSpin[t] > 0.1f) {
                if (attirer(t, pX[i], pY[i], pZ[i], 0.03f + 0.07f * tSpin[t], ACC)) continue;
                pVx[i] += ACC[0];
                pVy[i] += ACC[1];
                pVz[i] += ACC[2];
            } else {
                pVy[i] += pPoussee[i];
            }
            pX[i] += pVx[i];
            pY[i] += pVy[i];
            pZ[i] += pVz[i];
            pVx[i] *= pFrot[i];
            pVy[i] *= pFrot[i];
            pVz[i] *= pFrot[i];
            if (i != w) copierNuage(i, w);
            w++;
        }
        nP = w;
        w = 0;
        for (int i = 0; i < nM; i++) {
            if (++mVie[i] >= mVieMax[i]) continue;
            mPx[i] = mX[i];
            mPy[i] = mY[i];
            mPz[i] = mZ[i];
            int t = mAttire[i];
            if (t >= 0 && tId[t] >= 0 && tSpin[t] > 0.1f && mVie[i] > 3) {
                if (attirer(t, mX[i], mY[i], mZ[i], 0.06f + 0.1f * tSpin[t], ACC)) continue;
                mVx[i] = mVx[i] * 0.86f + ACC[0] * 2.2f;
                mVy[i] = mVy[i] * 0.86f + ACC[1] * 2.2f;
                mVz[i] = mVz[i] * 0.86f + ACC[2] * 2.2f;
            } else {
                mVy[i] -= 0.035f;
                mVx[i] *= 0.97f;
                mVz[i] *= 0.97f;
            }
            mX[i] += mVx[i];
            mY[i] += mVy[i];
            mZ[i] += mVz[i];
            mRot[i] += mVrot[i];
            if (i != w) copierMorceau(i, w);
            w++;
        }
        nM = w;
        w = 0;
        for (int i = 0; i < nE; i++) {
            if (++eVie[i] >= eVieMax[i]) continue;
            ePx[i] = eX[i];
            ePy[i] = eY[i];
            ePz[i] = eZ[i];
            eX[i] += eVx[i];
            eY[i] += eVy[i];
            eZ[i] += eVz[i];
            eVy[i] -= 0.012f;
            eVx[i] *= 0.95f;
            eVz[i] *= 0.95f;
            if (i != w) copierEtincelle(i, w);
            w++;
        }
        nE = w;
        w = 0;
        for (int k = 0; k < nC; k++) {
            if (cFin[k] < tick) continue;
            cId[w] = cId[k];
            cFin[w] = cFin[k];
            w++;
        }
        nC = w;
    }

    private static final float[] ACC = new float[3];

    /** Accélération vers la bouche du tireur t (rend vrai si le point y est arrivé : il disparaît, avalé). */
    private static boolean attirer(int t, double x, double y, double z, float force, float[] out) {
        double[] o = tO[t];
        float[] d = tD[t];
        double rx = x - o[0], ry = y - o[1], rz = z - o[2];
        double s = rx * d[0] + ry * d[1] + rz * d[2];
        double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (dist < 0.35 + tR[t] * 0.5 || s < -0.3) return true;
        double sv = Math.max(0.0, s * 0.3);
        double ax = o[0] + d[0] * sv - x, ay = o[1] + d[1] * sv - y, az = o[2] + d[2] * sv - z;
        double l = Math.sqrt(ax * ax + ay * ay + az * az);
        float k = (float) (force / Math.max(l, 1e-3) * (1.0 + 2.5 / (1.0 + s)));
        out[0] = (float) ax * k;
        out[1] = (float) ay * k;
        out[2] = (float) az * k;
        return false;
    }

    private static void copierTrait(int i, int w) {
        sT[w] = sT[i];
        sS0[w] = sS0[i];
        sPhi[w] = sPhi[i];
        sR0[w] = sR0[i];
        sT0[w] = sT0[i];
        sDur[w] = sDur[i];
        sL[w] = sL[i];
        sA[w] = sA[i];
        sSens[w] = sSens[i];
        sCoul[w] = sCoul[i];
    }

    private static void copierNuage(int i, int w) {
        pX[w] = pX[i];
        pY[w] = pY[i];
        pZ[w] = pZ[i];
        pPx[w] = pPx[i];
        pPy[w] = pPy[i];
        pPz[w] = pPz[i];
        pVx[w] = pVx[i];
        pVy[w] = pVy[i];
        pVz[w] = pVz[i];
        pTaille[w] = pTaille[i];
        pCroit[w] = pCroit[i];
        pA[w] = pA[i];
        pRot[w] = pRot[i];
        pFrot[w] = pFrot[i];
        pPoussee[w] = pPoussee[i];
        pVie[w] = pVie[i];
        pVieMax[w] = pVieMax[i];
        pCoul[w] = pCoul[i];
        pCase[w] = pCase[i];
        pAttire[w] = pAttire[i];
    }

    private static void copierMorceau(int i, int w) {
        mX[w] = mX[i];
        mY[w] = mY[i];
        mZ[w] = mZ[i];
        mPx[w] = mPx[i];
        mPy[w] = mPy[i];
        mPz[w] = mPz[i];
        mVx[w] = mVx[i];
        mVy[w] = mVy[i];
        mVz[w] = mVz[i];
        mTaille[w] = mTaille[i];
        mRot[w] = mRot[i];
        mVrot[w] = mVrot[i];
        mVie[w] = mVie[i];
        mVieMax[w] = mVieMax[i];
        mCoul[w] = mCoul[i];
        mCase[w] = mCase[i];
        mAttire[w] = mAttire[i];
    }

    private static void copierEtincelle(int i, int w) {
        eX[w] = eX[i];
        eY[w] = eY[i];
        eZ[w] = eZ[i];
        ePx[w] = ePx[i];
        ePy[w] = ePy[i];
        ePz[w] = ePz[i];
        eVx[w] = eVx[i];
        eVy[w] = eVy[i];
        eVz[w] = eVz[i];
        eTaille[w] = eTaille[i];
        eVie[w] = eVie[i];
        eVieMax[w] = eVieMax[i];
        eCoul[w] = eCoul[i];
        eCase[w] = eCase[i];
    }

    // ================================================================== rendu
    private static VertexConsumer VC;
    private static Matrix4f M;
    private static double CX, CY, CZ;
    private static float LX, LY, LZ, UX, UY, UZ;
    private static final double[] Q = new double[3], Q2 = new double[3];

    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        boolean actif = nS > 0 || nP > 0 || nM > 0 || nE > 0;
        for (int i = 0; i < MAX_T && !actif; i++) actif = tId[i] >= 0 && (tSpin[i] > 0 || tChaleur[i] > 0.5f);
        if (!actif) return;
        long t0 = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        Camera cam = e.getCamera();
        Vec3 c = cam.getPosition();
        CX = c.x;
        CY = c.y;
        CZ = c.z;
        Vector3f l = cam.getLeftVector(), u = cam.getUpVector();
        LX = l.x();
        LY = l.y();
        LZ = l.z();
        UX = u.x();
        UY = u.y();
        UZ = u.z();
        float partial = e.getPartialTick();
        float now = tick + partial;
        // repères des tireurs à cette image (la bouche suit l'arme image par image)
        for (int i = 0; i < MAX_T; i++) {
            if (tId[i] < 0) continue;
            Entity en = mc.level == null ? null : mc.level.getEntity(tId[i]);
            if (en instanceof LivingEntity le) cadre(i, le, partial, mc);
        }
        M = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VC = buf.getBuffer(JetFxTypes.FUMEE);
        dessinerTourbillons(now, partial, true);
        dessinerTraits(now, true);
        dessinerNuages(partial);
        dessinerMorceaux(partial);
        buf.endBatch(JetFxTypes.FUMEE);
        VC = buf.getBuffer(JetFxTypes.LUMIERE);
        dessinerTourbillons(now, partial, false);
        dessinerTraits(now, false);
        dessinerEtincelles(partial);
        dessinerLueursChaleur(now);
        buf.endBatch(JetFxTypes.LUMIERE);
        VC = null;
        long ns = System.nanoTime() - t0;
        nsRendu += ns;
        images++;
        if (ns > maxNs) maxNs = ns;
    }

    private static void sommet(float x, float y, float z, int coul, int a, float u, float v) {
        VC.vertex(M, x, y, z).color((coul >> 16) & 255, (coul >> 8) & 255, coul & 255, a).uv(u, v).endVertex();
    }

    private static int alpha(float a) {
        return a <= 0 ? 0 : a >= 1 ? 255 : (int) (a * 255);
    }

    /** Position (monde) d'un trait d'air à l'âge donné, dans le tourbillon de son tireur. */
    private static boolean posTrait(int i, float age, double[] out) {
        int t = sT[i];
        float u = Mth.clamp(age / sDur[i], 0f, 1f);
        float s = sS0[i] * (float) Math.pow(1 - u, 1.35);
        float r = 0.06f + (sR0[i] - 0.06f) * (float) Math.pow(s / sS0[i], 0.7);
        float phi = sPhi[i] + sSens[i] * 3.4f * (1 - s / sS0[i]) * (1 + 0.1f * sS0[i]);
        float cs = Mth.cos(phi), sn = Mth.sin(phi);
        double[] o = tO[t];
        float[] d = tD[t], a = tU[t], b = tV[t];
        out[0] = o[0] + d[0] * s + (a[0] * cs + b[0] * sn) * r;
        out[1] = o[1] + d[1] * s + (a[1] * cs + b[1] * sn) * r;
        out[2] = o[2] + d[2] * s + (a[2] * cs + b[2] * sn) * r;
        return true;
    }

    private static void dessinerTraits(float now, boolean voile) {
        for (int i = 0; i < nS; i++) {
            float age = now - sT0[i];
            if (age < 0 || age > sDur[i]) continue;
            float k = age / sDur[i];
            posTrait(i, age, Q);
            posTrait(i, Math.max(0, age - 1.1f), Q2);
            float a = sA[i] * Math.min(1, k * 5) * (1 - k * k * k) * tSpin[sT[i]];
            double qx = Q[0] - tO[sT[i]][0], qy = Q[1] - tO[sT[i]][1], qz = Q[2] - tO[sT[i]][2];
            float pres = (float) Math.min(1.0, Math.sqrt(qx * qx + qy * qy + qz * qz) / 3.0);
            float w = sL[i] * (0.35f + 0.65f * pres) * (tFp[sT[i]] ? 0.8f : 1f);
            float ax = (float) (Q2[0] - CX), ay = (float) (Q2[1] - CY), az = (float) (Q2[2] - CZ);
            float bx = (float) (Q[0] - CX), by = (float) (Q[1] - CY), bz = (float) (Q[2] - CZ);
            if (voile) ruban(ax, ay, az, bx, by, bz, w * 2.4f, w * 1.8f, JetFxTypes.TRAIT, 0xF4F7FA, 0, a * 0.75f);
            else ruban(ax, ay, az, bx, by, bz, w * 0.8f, w * 0.6f, JetFxTypes.TRAIT, sCoul[i], 0, a);
        }
    }

    /** Tourbillon devant la soufflante : deux disques translucides qui tournent, liseré lumineux, selon l'emballement. */
    private static void dessinerTourbillons(float now, float partial, boolean voile) {
        for (int i = 0; i < MAX_T; i++) {
            if (tId[i] < 0) continue;
            float sp = Mth.lerp(partial, tSpinP[i], tSpin[i]);
            if (sp <= 0.03f) continue;
            float[] g = tG[i];
            // base perpendiculaire à l'axe de l'arme
            float ux, uy, uz;
            if (Math.abs(g[1]) < 0.95f) {
                ux = g[2];
                uy = 0;
                uz = -g[0];
            } else {
                ux = 1;
                uy = 0;
                uz = 0;
            }
            float nu = Mth.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= nu;
            uy /= nu;
            uz /= nu;
            float vx = g[1] * uz - g[2] * uy, vy = g[2] * ux - g[0] * uz, vz = g[0] * uy - g[1] * ux;
            float r = tR[i];
            for (int couche = 0; couche < 2; couche++) {
                float ang = now * (0.9f + 0.5f * couche) * sp * (couche == 0 ? 1 : -0.7f);
                float avance = couche == 0 ? -0.04f : -0.12f;
                double cx = tO[i][0] + g[0] * avance * r * 8 - CX, cy = tO[i][1] + g[1] * avance * r * 8 - CY, cz = tO[i][2] + g[2] * avance * r * 8 - CZ;
                float rr = r * (couche == 0 ? 0.92f : 1.25f);
                if (voile) disque(cx, cy, cz, ux, uy, uz, vx, vy, vz, rr, ang, JetFxTypes.TOURBILLON, 0xF2F6FA, sp * (couche == 0 ? 0.7f : 0.38f));
                else disque(cx, cy, cz, ux, uy, uz, vx, vy, vz, rr * (couche == 0 ? 1.0f : 0.7f), -ang * 0.5f, couche == 0 ? JetFxTypes.ANNEAU : JetFxTypes.TOURBILLON,
                        0xDCEEFF, sp * (couche == 0 ? 0.35f : 0.3f));
            }
        }
    }

    private static void disque(double cx, double cy, double cz, float ux, float uy, float uz, float vx, float vy, float vz, float r, float ang, int cell, int coul,
                               float a) {
        int al = alpha(a);
        if (al == 0) return;
        float cs = Mth.cos(ang) * r, sn = Mth.sin(ang) * r;
        float ax = ux * cs + vx * sn, ay = uy * cs + vy * sn, az = uz * cs + vz * sn;
        float bx = -ux * sn + vx * cs, by = -uy * sn + vy * cs, bz = -uz * sn + vz * cs;
        float u0 = JetFxTypes.u0(cell), v0 = JetFxTypes.v0(cell), u1 = JetFxTypes.u1(cell), v1 = JetFxTypes.v1(cell);
        float x = (float) cx, y = (float) cy, z = (float) cz;
        sommet(x - ax - bx, y - ay - by, z - az - bz, coul, al, u0, v1);
        sommet(x + ax - bx, y + ay - by, z + az - bz, coul, al, u1, v1);
        sommet(x + ax + bx, y + ay + by, z + az + bz, coul, al, u1, v0);
        sommet(x - ax + bx, y - ay + by, z - az + bz, coul, al, u0, v0);
    }

    private static void dessinerNuages(float partial) {
        for (int i = 0; i < nP; i++) {
            float k = (pVie[i] + partial) / pVieMax[i];
            if (k >= 1) continue;
            double x = Mth.lerp(partial, pPx[i], pX[i]), y = Mth.lerp(partial, pPy[i], pY[i]), z = Mth.lerp(partial, pPz[i], pZ[i]);
            double cx = x - CX, cy = y - CY, cz = z - CZ;
            if (cx * cx + cy * cy + cz * cz < 0.04) continue;
            float entree = Math.min(1f, k * 6f);
            panneau(cx, cy, cz, pTaille[i] + pCroit[i] * pVie[i], pRot[i] + k * 1.3f, pCase[i], pCoul[i], pA[i] * entree * (1 - k));
        }
    }

    private static void dessinerMorceaux(float partial) {
        for (int i = 0; i < nM; i++) {
            float k = (mVie[i] + partial) / mVieMax[i];
            if (k >= 1) continue;
            double x = Mth.lerp(partial, mPx[i], mX[i]), y = Mth.lerp(partial, mPy[i], mY[i]), z = Mth.lerp(partial, mPz[i], mZ[i]);
            panneau(x - CX, y - CY, z - CZ, mTaille[i], mRot[i] + mVrot[i] * partial, mCase[i], mCoul[i], k > 0.8f ? (1 - k) * 5 : 1f);
        }
    }

    private static void dessinerEtincelles(float partial) {
        for (int i = 0; i < nE; i++) {
            float k = (eVie[i] + partial) / eVieMax[i];
            if (k >= 1) continue;
            double x = Mth.lerp(partial, ePx[i], eX[i]), y = Mth.lerp(partial, ePy[i], eY[i]), z = Mth.lerp(partial, ePz[i], eZ[i]);
            if (eCase[i] == JetFxTypes.ETINCELLE) {
                float bx = (float) (x - CX), by = (float) (y - CY), bz = (float) (z - CZ);
                ruban(bx - eVx[i] * 1.8f, by - eVy[i] * 1.8f, bz - eVz[i] * 1.8f, bx, by, bz, eTaille[i] * 0.4f, eTaille[i], JetFxTypes.ETINCELLE, eCoul[i], 1 - k,
                        1 - k);
            } else {
                panneau(x - CX, y - CY, z - CZ, eTaille[i] * (0.6f + k * 0.8f), k, eCase[i], eCoul[i], (1 - k) * 0.9f);
            }
        }
    }

    /** Lueur orange autour de la section chaude (vue en 3e personne : le modèle n'y est pas animé). */
    private static void dessinerLueursChaleur(float now) {
        for (int i = 0; i < MAX_T; i++) {
            if (tId[i] < 0 || tFp[i] || tChaleur[i] < 0.45f) continue;
            float k = (tChaleur[i] - 0.45f) / 0.55f;
            float pulse = 0.85f + 0.15f * Mth.sin(now * 0.7f + i);
            panneau(tH[i][0] - CX, tH[i][1] - CY, tH[i][2] - CZ, 0.14f + 0.12f * k, 0, JetFxTypes.LUEUR, tVerrou[i] ? 0xFF5A20 : 0xFF8A30, k * 0.4f * pulse);
        }
    }

    private static void panneau(double px, double py, double pz, float r, float rot, int cell, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        float ax = LX * cs + UX * sn, ay = LY * cs + UY * sn, az = LZ * cs + UZ * sn;
        float bx = -LX * sn + UX * cs, by = -LY * sn + UY * cs, bz = -LZ * sn + UZ * cs;
        float u0 = JetFxTypes.u0(cell), v0 = JetFxTypes.v0(cell), u1 = JetFxTypes.u1(cell), v1 = JetFxTypes.v1(cell);
        float x = (float) px, y = (float) py, z = (float) pz;
        sommet(x - ax - bx, y - ay - by, z - az - bz, coul, al, u0, v1);
        sommet(x + ax - bx, y + ay - by, z + az - bz, coul, al, u1, v1);
        sommet(x + ax + bx, y + ay + by, z + az + bz, coul, al, u1, v0);
        sommet(x - ax + bx, y - ay + by, z - az + bz, coul, al, u0, v0);
    }

    private static void ruban(float ax, float ay, float az, float bx, float by, float bz, float w0, float w1, int cell, int coul, float a0, float a1) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        float n = sx * sx + sy * sy + sz * sz;
        if (n < 1.0E-12f) return;
        n = 1f / (float) Math.sqrt(n);
        sx *= n;
        sy *= n;
        sz *= n;
        float h0 = w0 * 0.5f, h1 = w1 * 0.5f;
        int al0 = alpha(a0), al1 = alpha(a1);
        if (al0 == 0 && al1 == 0) return;
        float u0 = JetFxTypes.u0(cell), v0 = JetFxTypes.v0(cell), u1 = JetFxTypes.u1(cell), v1 = JetFxTypes.v1(cell);
        sommet(ax + sx * h0, ay + sy * h0, az + sz * h0, coul, al0, u0, v0);
        sommet(ax - sx * h0, ay - sy * h0, az - sz * h0, coul, al0, u0, v1);
        sommet(bx - sx * h1, by - sy * h1, bz - sz * h1, coul, al1, u1, v1);
        sommet(bx + sx * h1, by + sy * h1, bz + sz * h1, coul, al1, u1, v0);
    }

    // ================================================================== créatures aspirées : elles penchent vers la bouche
    private static final LivingEntity[] PILE = new LivingEntity[8];
    private static int nPile;
    private static final Quaternionf QR = new Quaternionf();

    public static void vivantAvant(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        if (nC > 0 && cache(le.getId())) {
            e.setCanceled(true);
            return;
        }
        if (nPile >= PILE.length) return;
        float partial = e.getPartialTick();
        float tan = Mth.sin(ANGLE_FX * DEG) / Mth.cos(ANGLE_FX * DEG);
        for (int i = 0; i < MAX_T; i++) {
            if (tId[i] < 0 || tId[i] == le.getId()) continue;
            float sp = Mth.lerp(partial, tSpinP[i], tSpin[i]);
            if (sp < 0.15f) continue;
            double h = le.getBbHeight();
            double x = Mth.lerp(partial, le.xo, le.getX()), y = Mth.lerp(partial, le.yo, le.getY()) + h * 0.5, z = Mth.lerp(partial, le.zo, le.getZ());
            double[] o = tO[i];
            float[] d = tD[i];
            double rx = x - o[0], ry = y - o[1], rz = z - o[2];
            double s = rx * d[0] + ry * d[1] + rz * d[2];
            if (s < 0 || s > PORTEE_FX + 1) continue;
            double px = rx - d[0] * s, py = ry - d[1] * s, pz = rz - d[2] * s;
            double perp = Math.sqrt(px * px + py * py + pz * pz);
            if (perp > 0.6 + s * tan + le.getBbWidth()) continue;
            // penche vers la bouche (axe horizontal perpendiculaire à la direction bouche -> créature), tremble
            float tx = (float) -rx, tz = (float) -rz;
            float tl = Mth.sqrt(tx * tx + tz * tz);
            if (tl < 1e-3f) continue;
            tx /= tl;
            tz /= tl;
            float k = (float) Math.pow(Math.max(0, 1 - s / (PORTEE_FX + 1)), 0.6);
            float ang = (0.38f * k + 0.08f) * sp + 0.05f * sp * Mth.sin((tick + partial) * 2.1f + le.getId());
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(0, h * 0.5, 0);
            ps.mulPose(QR.setAngleAxis(ang, tz, 0, -tx));
            ps.translate(0, -h * 0.5, 0);
            PILE[nPile++] = le;
            return;
        }
    }

    public static void vivantApres(RenderLivingEvent.Post<?, ?> e) {
        if (nPile > 0 && PILE[nPile - 1] == e.getEntity()) {
            e.getPoseStack().popPose();
            PILE[--nPile] = null;
        }
    }

    // ================================================================== caméra du tireur : vibration, secousse à la surchauffe
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        float sp = spinLocal();
        float t = tick + (float) e.getPartialTick();
        if (sp > 0.01f) {
            float a = 0.22f * sp;
            e.setPitch(e.getPitch() + a * (Mth.sin(t * 2.7f) * 0.6f + Mth.sin(t * 5.3f) * 0.4f));
            e.setYaw(e.getYaw() + a * 0.6f * Mth.sin(t * 3.4f + 1.1f));
            e.setRoll(e.getRoll() + a * 0.8f * Mth.sin(t * 4.1f + 0.3f));
        }
        if (secAmp > 0) {
            float age = t - secT0;
            if (age >= 0) {
                float k = (float) Math.exp(-age / 4.5f) * secAmp * 3f;
                e.setPitch(e.getPitch() - k * (0.6f + 0.4f * Mth.sin(age * 2.5f)));
                e.setRoll(e.getRoll() + k * 0.9f * Mth.sin(age * 3.7f));
            }
        }
    }

    // ================================================================== divers
    private static void son(Entity e, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(e.getX(), e.getEyeY() - 0.4, e.getZ(), s, SoundSource.PLAYERS, vol, pitch, false);
    }

    private static int melange(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static void dechargement(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) vider();
    }

    private static void vider() {
        nS = nP = nM = nE = nC = 0;
        for (int i = 0; i < MAX_T; i++) if (tId[i] >= 0) liberer(i);
        for (int i = 0; i < nPile; i++) PILE[i] = null;
        nPile = 0;
        secAmp = 0;
    }

    public static String stats() {
        int tireurs = 0;
        for (int i = 0; i < MAX_T; i++) if (tId[i] >= 0) tireurs++;
        String r = String.format(Locale.ROOT,
                "jet gun : rendu moyen %.3f ms (pire %.3f ms) sur %d images ; %d dechiquetes ; en cours : %d tireurs, %d traits, %d nuages, %d morceaux, "
                        + "%d etincelles ; pics : %d/%d traits, %d/%d nuages, %d/%d morceaux, %d/%d etincelles",
                images == 0 ? 0 : nsRendu / 1e6 / images, maxNs / 1e6, images, broyes, tireurs, nS, nP, nM, nE, picS, MAX_S, picP, MAX_P, picM, MAX_M, picE, MAX_E);
        nsRendu = maxNs = 0;
        images = broyes = picS = picP = picM = picE = 0;
        return r;
    }
}
