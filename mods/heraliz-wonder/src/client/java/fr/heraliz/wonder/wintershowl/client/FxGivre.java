package fr.heraliz.wonder.wintershowl.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.client.ClientWonder;
import fr.heraliz.wonder.client.TaczClient;
import fr.heraliz.wonder.wintershowl.CalculsGivre;
import fr.heraliz.wonder.wintershowl.ReseauGivre;
import fr.heraliz.wonder.wintershowl.SonsGivre;
import fr.heraliz.wonder.wintershowl.WintersHowl;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Effets du Winter's Howl cote client, dans des reserves de taille fixe (tableaux paralleles, aucune allocation par image),
 * dessines en deux lots ({@link TypesGivre#BRUME} translucide, {@link TypesGivre#LUMIERE} additif) avec une seule texture :
 * <ul>
 *   <li><b>souffle</b> : jet de brume d'azote qui part de la bouche et s'etale dans le cone (les bouffees de tete arrivent au
 *   bout au tick ou le serveur gele : {@link CalculsGivre#arrivee}), cristaux de glace qui scintillent et derivent, flocons
 *   qui tombent en tournant, traits de froid, anneau givre a la bouche, plaques de givre qui se posent au sol sous le cone ;</li>
 *   <li><b>creatures givrees</b> : couche de glace ({@link CoucheGivre}), pointes de cristal sur les gelees, brume froide qui
 *   tombe d'elles, tremblement et craquements juste avant l'eclatement ;</li>
 *   <li><b>eclatement</b> : eclats de glace en 3D qui volent, tournent, rebondissent au sol et s'eteignent ; poussiere de
 *   glace, etincelles, anneau au sol ; le corps disparait ;</li>
 *   <li><b>arme tenue</b> : vapeur froide qui coule de l'emetteur ; voile de givre sur les bords de l'ecran du tireur ;</li>
 *   <li><b>Winter's Fury</b> : cone plus grand, brume plus bleue, cristaux plus vifs (veines indigo), anneau plus large.</li>
 * </ul>
 */
public final class FxGivre {
    private FxGivre() {
    }

    private static final RandomSource RNG = RandomSource.create();
    private static long tick;
    private static float dernierLocal = -1000;

    static float maintenant() {
        return (float) tick;
    }

    // ---- brume (translucide)
    private static final int MAX_B = 896;
    private static final double[] bX = new double[MAX_B], bY = new double[MAX_B], bZ = new double[MAX_B], bPx = new double[MAX_B], bPy = new double[MAX_B],
            bPz = new double[MAX_B];
    private static final float[] bVx = new float[MAX_B], bVy = new float[MAX_B], bVz = new float[MAX_B], bT = new float[MAX_B], bCr = new float[MAX_B],
            bA = new float[MAX_B], bRot = new float[MAX_B], bFr = new float[MAX_B];
    private static final int[] bVie = new int[MAX_B], bVieMax = new int[MAX_B], bDelai = new int[MAX_B], bCoul = new int[MAX_B];
    private static int nB;

    // ---- cristaux, flocons, poussiere (additif)
    private static final int MAX_C = 1024;
    private static final double[] cX = new double[MAX_C], cY = new double[MAX_C], cZ = new double[MAX_C], cPx = new double[MAX_C], cPy = new double[MAX_C],
            cPz = new double[MAX_C];
    private static final float[] cVx = new float[MAX_C], cVy = new float[MAX_C], cVz = new float[MAX_C], cT = new float[MAX_C], cA = new float[MAX_C],
            cRot = new float[MAX_C], cVrot = new float[MAX_C], cPh = new float[MAX_C], cFr = new float[MAX_C], cG = new float[MAX_C];
    private static final int[] cVie = new int[MAX_C], cVieMax = new int[MAX_C], cDelai = new int[MAX_C], cCoul = new int[MAX_C];
    private static final byte[] cCase = new byte[MAX_C];
    private static int nC;

    // ---- eclats de glace en 3D (translucide)
    private static final int MAX_E = 768;
    private static final double[] eX = new double[MAX_E], eY = new double[MAX_E], eZ = new double[MAX_E], ePx = new double[MAX_E], ePy = new double[MAX_E],
            ePz = new double[MAX_E], eSol = new double[MAX_E];
    private static final float[] eVx = new float[MAX_E], eVy = new float[MAX_E], eVz = new float[MAX_E], eAx = new float[MAX_E], eAy = new float[MAX_E],
            eAz = new float[MAX_E], eAng = new float[MAX_E], ePang = new float[MAX_E], eVang = new float[MAX_E], eT = new float[MAX_E];
    private static final int[] eVie = new int[MAX_E], eVieMax = new int[MAX_E], eCoul = new int[MAX_E];
    private static final byte[] eCase = new byte[MAX_E];
    private static int nE;

    // ---- anneaux (additif) : bouche (face au tir), sol (a plat)
    private static final int MAX_R = 64;
    private static final double[] rX = new double[MAX_R], rY = new double[MAX_R], rZ = new double[MAX_R];
    private static final float[] rUx = new float[MAX_R], rUy = new float[MAX_R], rUz = new float[MAX_R], rVx = new float[MAX_R], rVy = new float[MAX_R],
            rVz = new float[MAX_R], rR0 = new float[MAX_R], rR1 = new float[MAX_R], rT0 = new float[MAX_R], rDur = new float[MAX_R], rA = new float[MAX_R];
    private static final int[] rCoul = new int[MAX_R];
    private static int nR;

    // ---- plaques de givre au sol (translucide)
    private static final int MAX_P = 160;
    private static final double[] pX = new double[MAX_P], pY = new double[MAX_P], pZ = new double[MAX_P];
    private static final float[] pT = new float[MAX_P], pRot = new float[MAX_P], pT0 = new float[MAX_P], pDur = new float[MAX_P], pA = new float[MAX_P];
    private static int nP;

    // ---- traits de froid (additif)
    private static final int MAX_S = 256;
    private static final double[] sX = new double[MAX_S], sY = new double[MAX_S], sZ = new double[MAX_S];
    private static final float[] sDx = new float[MAX_S], sDy = new float[MAX_S], sDz = new float[MAX_S], sV = new float[MAX_S], sT0 = new float[MAX_S],
            sDur = new float[MAX_S], sL = new float[MAX_S], sW = new float[MAX_S], sA = new float[MAX_S];
    private static final int[] sCoul = new int[MAX_S];
    private static int nS;

    // ---- arrivees du front sur les creatures (son, bouffee de givre a l'impact)
    private static final int MAX_A = 160;
    private static final int[] aId = new int[MAX_A];
    private static final float[] aT = new float[MAX_A];
    private static final boolean[] aGel = new boolean[MAX_A];
    private static final byte[] aPap = new byte[MAX_A];
    private static int nA;

    // ---- corps eclates (caches)
    private static final int MAX_K = 96;
    private static final int[] kId = new int[MAX_K];
    private static final float[] kFin = new float[MAX_K];
    private static int nK;

    // ---- degazage de la cartouche au rechargement (tick prevu, force)
    private static final int MAX_V = 8;
    private static final float[] vT = new float[MAX_V], vF = new float[MAX_V];
    private static int nV;

    // ---- voile de givre a l'ecran du tireur
    private static float voile, voilePrec;

    // ---- statistiques
    private static long nsRendu, maxNs;
    private static int images, tirs, eclatsRecus, picB, picC, picE, picP;

    private static final int GLACE = 0xE6F4FF, GLACE_F = 0xD2E2FF, CRISTAL = 0xCFF4FF, VIOLET = 0xC8B4FF;

    // ================================================================== entrees
    /** Tir du joueur local (prediction, des que TaCZ accepte le tir). */
    public static void tirLocal(LocalPlayer p, ItemStack arme) {
        int pap = Armes.pap(arme);
        Minecraft mc = Minecraft.getInstance();
        Vec3 oeil = p.getEyePosition();
        Vec3 d = p.getLookAngle();
        double[] o = bouche(p, oeil, d, true);
        double portee = CalculsGivre.portee(pap, 11, 15), angle = CalculsGivre.angle(pap, 16, 22);
        cone(o[0], o[1], o[2], (float) d.x, (float) d.y, (float) d.z, (float) portee, (float) angle, 0.9f, pap);
        voile = Math.max(voile, pap > 0 ? 0.4f : 0.32f);
        dernierLocal = maintenant();
        tirs++;
    }

    private static final float[] DEC = new float[3];
    private static final double[] BOUCHE = new double[3];

    /**
     * Bouche de l'arme (dans un tampon reutilise) : en vue a la 1re personne, la position que TaCZ a calculee ; sinon a la
     * main droite.
     */
    private static double[] bouche(Entity t, Vec3 oeil, Vec3 d, boolean local) {
        Minecraft mc = Minecraft.getInstance();
        if (local && ClientWonder.tacz && TaczClient.premierePersonne() && TaczClient.decalageCanon(mc.gameRenderer.getMainCamera(), DEC)) {
            BOUCHE[0] = oeil.x + DEC[0];
            BOUCHE[1] = oeil.y + DEC[1];
            BOUCHE[2] = oeil.z + DEC[2];
            return BOUCHE;
        }
        float yaw = t.getYRot() * Mth.DEG_TO_RAD;
        BOUCHE[0] = oeil.x + d.x * 0.9 - Mth.cos(yaw) * 0.34;
        BOUCHE[1] = oeil.y + d.y * 0.9 - 0.42;
        BOUCHE[2] = oeil.z + d.z * 0.9 - Mth.sin(yaw) * 0.34;
        return BOUCHE;
    }

    /** Paquet du serveur : le cone (sauf tir local deja dessine) et les creatures touchees (gel, arrivee du front). */
    public static void souffle(ReseauGivre.Souffle m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        boolean local = mc.player != null && m.tireur() == mc.player.getId() && maintenant() - dernierLocal < 10;
        if (!local) {
            Entity t = lv.getEntity(m.tireur());
            Vec3 oeil = new Vec3(m.ox(), m.oy(), m.oz());
            Vec3 d = new Vec3(m.dx(), m.dy(), m.dz());
            if (t != null) {
                double[] o = bouche(t, oeil, d, false);
                cone(o[0], o[1], o[2], m.dx(), m.dy(), m.dz(), m.portee(), m.demiAngle(), m.r0(), m.pap());
            } else {
                cone(m.ox() + m.dx(), m.oy() + m.dy() - 0.3, m.oz() + m.dz(), m.dx(), m.dy(), m.dz(), m.portee(), m.demiAngle(), m.r0(), m.pap());
            }
        }
        float now = maintenant();
        for (int i = 0; i < m.n(); i++) {
            int code = m.gels()[i] & 255;
            float t = now + m.arrivees()[i];
            EtatsGivre.poser(m.ids()[i], code / 254f, code == 255, t, m.delaiEclat(), m.pap());
            if (nA < MAX_A) {
                int k = nA++;
                aId[k] = m.ids()[i];
                aT[k] = t;
                aGel[k] = code == 255;
                aPap[k] = m.pap();
            }
        }
    }

    /** Une creature gelee vole en eclats. */
    public static void eclat(ReseauGivre.Eclat m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        eclatsRecus++;
        EtatsGivre.retirerId(m.id());
        if (nK < MAX_K) {
            kId[nK] = m.id();
            kFin[nK] = maintenant() + 60;
            nK++;
        }
        boolean fury = m.pap() > 0;
        float h = Math.max(0.5f, m.haut()), w = Math.max(0.4f, m.larg());
        double pieds = m.y() - h * 0.5;
        int nb = (int) (14 + h * w * 8) + (fury ? 4 : 0);
        float now = maintenant();
        for (int k = 0; k < nb && nE < MAX_E; k++) {
            int i = nE++;
            double ox = (RNG.nextFloat() - 0.5f) * w, oy = (RNG.nextFloat() - 0.5f) * h * 0.95, oz = (RNG.nextFloat() - 0.5f) * w;
            eX[i] = ePx[i] = m.x() + ox;
            eY[i] = ePy[i] = m.y() + oy;
            eZ[i] = ePz[i] = m.z() + oz;
            eSol[i] = pieds + 0.02 + RNG.nextFloat() * 0.03;
            float rx = (float) ox, rz = (float) oz, rl = Mth.sqrt(rx * rx + rz * rz) + 0.05f;
            boolean bloc = k < 4;                 // gros morceaux du corps : tombent presque sur place
            float v = bloc ? 0.03f + RNG.nextFloat() * 0.04f : 0.06f + RNG.nextFloat() * 0.13f;
            eVx[i] = rx / rl * v + (RNG.nextFloat() - 0.5f) * 0.05f;
            eVy[i] = bloc ? 0.02f + RNG.nextFloat() * 0.05f : 0.08f + RNG.nextFloat() * 0.16f + (float) oy * 0.08f;
            eVz[i] = rz / rl * v + (RNG.nextFloat() - 0.5f) * 0.05f;
            float ax = RNG.nextFloat() - 0.5f, ay = RNG.nextFloat() - 0.5f, az = RNG.nextFloat() - 0.5f, an = Mth.sqrt(ax * ax + ay * ay + az * az) + 1e-4f;
            eAx[i] = ax / an;
            eAy[i] = ay / an;
            eAz[i] = az / an;
            eAng[i] = ePang[i] = RNG.nextFloat() * Mth.TWO_PI;
            eVang[i] = (RNG.nextFloat() - 0.5f) * (bloc ? 0.4f : 1.0f);
            eT[i] = bloc ? 0.17f + RNG.nextFloat() * 0.07f : 0.05f + RNG.nextFloat() * RNG.nextFloat() * 0.13f;
            eVie[i] = 0;
            eVieMax[i] = 40 + RNG.nextInt(30);
            eCase[i] = (byte) (RNG.nextBoolean() ? TypesGivre.ECLAT_A : TypesGivre.ECLAT_B);
            eCoul[i] = fury && RNG.nextFloat() < 0.35f ? 0xBFD0FF : (RNG.nextFloat() < 0.5f ? 0xFFFFFF : 0xD8F0FF);
        }
        if (nE > picE) picE = nE;
        for (int k = 0; k < 10; k++) {
            float a = RNG.nextFloat() * Mth.TWO_PI, v = 0.03f + RNG.nextFloat() * 0.06f;
            brume(m.x() + (RNG.nextFloat() - 0.5f) * w, m.y() + (RNG.nextFloat() - 0.5f) * h * 0.8, m.z() + (RNG.nextFloat() - 0.5f) * w, Mth.cos(a) * v,
                    0.01f + RNG.nextFloat() * 0.03f, Mth.sin(a) * v, 0.25f + RNG.nextFloat() * 0.2f, 0.025f, 16 + RNG.nextInt(12), 0, fury ? GLACE_F : GLACE, 0.55f,
                    0.88f);
        }
        for (int k = 0; k < 26; k++) {
            float a = RNG.nextFloat() * Mth.TWO_PI, v = 0.06f + RNG.nextFloat() * 0.16f;
            cristal(m.x(), m.y() + (RNG.nextFloat() - 0.5f) * h * 0.7, m.z(), Mth.cos(a) * v, 0.04f + RNG.nextFloat() * 0.18f, Mth.sin(a) * v,
                    0.07f + RNG.nextFloat() * 0.1f, 14 + RNG.nextInt(16), 0, k % 4 == 0 ? TypesGivre.POUSSIERE : TypesGivre.ETINCELLE,
                    fury && k % 3 == 0 ? VIOLET : CRISTAL, 0.9f, 0.9f, 0.025f);
        }
        anneauSol(m.x(), pieds + 0.04, m.z(), 0.25f, 0.8f + w * 0.6f, now, 7, fury ? 0xC9D4FF : 0xDDF6FF, 0.35f);
        lv.playLocalSound(m.x(), m.y(), m.z(), SonsGivre.ECLAT.get(), SoundSource.HOSTILE, 1.0f, 0.9f + RNG.nextFloat() * 0.25f, false);
    }

    /**
     * Rechargement du joueur local (TaCZ), cale sur les animations HD (tools/wonder/wh_anims_hd.py) :
     * a vide, la cartouche saute en degazant (0,52 s), souffle a l'enfoncement (1,68 s), sifflement de la vanne (2,18 s) ;
     * tactique, petit souffle au decrochage (0,56 s), a l'enfoncement (1,62 s) et a la remise en pression (1,86 s).
     */
    public static void recharge(boolean vide) {
        float now = maintenant();
        if (nV + 3 > MAX_V) return;
        float[][] ev = vide ? new float[][]{{10, 1f}, {34, 0.45f}, {44, 0.7f}} : new float[][]{{11, 0.35f}, {32, 0.45f}, {37, 0.3f}};
        for (float[] e : ev) {
            vT[nV] = now + e[0];
            vF[nV++] = e[1];
        }
    }

    private static void degazer(Minecraft mc, float force) {
        LocalPlayer p = mc.player;
        if (p == null || !WintersHowl.est(p.getMainHandItem())) return;
        Vec3 oeil = p.getEyePosition();
        Vec3 d = p.getLookAngle();
        double[] o = bouche(p, oeil, d, true);
        // la cartouche est sur le flanc gauche, a mi-chemin entre la bouche et l'oeil
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        float gx = Mth.cos(yaw), gz = Mth.sin(yaw);       // vers la gauche du joueur
        double cx = o[0] * 0.55 + oeil.x * 0.45 + gx * 0.12, cy = o[1] * 0.55 + oeil.y * 0.45 - 0.08, cz = o[2] * 0.55 + oeil.z * 0.45 + gz * 0.12;
        int n = (int) (14 * force);
        for (int k = 0; k < n; k++) {
            float v = 0.02f + RNG.nextFloat() * 0.05f;
            brume(cx, cy, cz, gx * v + (RNG.nextFloat() - 0.5f) * 0.02f, 0.01f + RNG.nextFloat() * 0.025f, gz * v + (RNG.nextFloat() - 0.5f) * 0.02f,
                    0.05f + RNG.nextFloat() * 0.05f, 0.012f, 18 + RNG.nextInt(12), RNG.nextInt(3), 0xF2F8FF, 0.5f * force + 0.15f, 0.93f);
        }
        for (int k = 0; k < (int) (8 * force); k++) {
            cristal(cx, cy, cz, gx * 0.03f + (RNG.nextFloat() - 0.5f) * 0.03f, 0.02f, gz * 0.03f + (RNG.nextFloat() - 0.5f) * 0.03f, 0.012f + RNG.nextFloat() * 0.01f,
                    16 + RNG.nextInt(10), RNG.nextInt(3), TypesGivre.ETINCELLE, CRISTAL, 0.85f, 0.92f, 0.002f);
        }
    }

    public static void degel(ReseauGivre.Degel m) {
        EtatsGivre.retirerId(m.id());
    }

    // ================================================================== creation du souffle
    private static void cone(double ox, double oy, double oz, float dx, float dy, float dz, float portee, float demiAngle, float r0, int pap) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        float now = maintenant();
        boolean fury = pap > 0;
        float tan = Mth.sin(demiAngle * Mth.DEG_TO_RAD) / Mth.cos(demiAngle * Mth.DEG_TO_RAD);
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
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        int coul = fury ? GLACE_F : GLACE;
        // jet de brume : bouffees de tete (arrivent au bout au tick du gel serveur), corps du jet, bouffee dense a la bouche
        int nJet = fury ? 64 : 48;
        for (int k = 0; k < nJet; k++) {
            boolean tete = k < nJet / 4;
            float phi = (float) Math.sqrt(RNG.nextFloat()) * demiAngle * 0.92f * Mth.DEG_TO_RAD;
            float psi = RNG.nextFloat() * Mth.TWO_PI;
            float sp = Mth.sin(phi), cp = Mth.cos(phi);
            float cx = Mth.cos(psi) * sp, cy = Mth.sin(psi) * sp;
            float wx = dx * cp + ux * cx + vx * cy, wy = dy * cp + uy * cx + vy * cy, wz = dz * cp + uz * cx + vz * cy;
            float v0 = portee * (tete ? 0.25f * (0.92f + RNG.nextFloat() * 0.12f) : 0.06f + RNG.nextFloat() * 0.17f);
            float lat = RNG.nextFloat() * r0 * 0.5f, psi2 = RNG.nextFloat() * Mth.TWO_PI;
            double px = ox + (ux * Mth.cos(psi2) + vx * Mth.sin(psi2)) * lat, py = oy + (uy * Mth.cos(psi2) + vy * Mth.sin(psi2)) * lat,
                    pz = oz + (uz * Mth.cos(psi2) + vz * Mth.sin(psi2)) * lat;
            brume(px, py, pz, wx * v0, wy * v0, wz * v0, 0.18f + RNG.nextFloat() * 0.12f, 0.035f + RNG.nextFloat() * 0.03f, 20 + RNG.nextInt(14),
                    tete ? 0 : RNG.nextInt(3), RNG.nextFloat() < 0.25f ? 0xF4FAFF : (fury ? 0xC8DAFF : 0xD6ECFF), tete ? 0.72f : 0.58f, 0.82f);
        }
        for (int k = 0; k < (fury ? 18 : 14); k++) {
            float phi = (float) Math.sqrt(RNG.nextFloat()) * demiAngle * 0.35f * Mth.DEG_TO_RAD;
            float psi = RNG.nextFloat() * Mth.TWO_PI;
            float sp = Mth.sin(phi), cp = Mth.cos(phi);
            float cx = Mth.cos(psi) * sp, cy = Mth.sin(psi) * sp;
            float wx = dx * cp + ux * cx + vx * cy, wy = dy * cp + uy * cx + vy * cy, wz = dz * cp + uz * cx + vz * cy;
            float v0 = portee * (0.10f + RNG.nextFloat() * 0.15f);
            brume(ox + dx * 0.6, oy + dy * 0.6, oz + dz * 0.6, wx * v0, wy * v0, wz * v0, 0.16f + RNG.nextFloat() * 0.1f, 0.03f + RNG.nextFloat() * 0.02f,
                    16 + RNG.nextInt(10), RNG.nextInt(2), fury ? 0xBCD2FF : 0xCBE6FF, 0.8f, 0.82f);
        }
        for (int k = 0; k < 12; k++) {
            float a = RNG.nextFloat() * Mth.TWO_PI, rr = RNG.nextFloat() * 0.12f;
            float ex = ux * Mth.cos(a) + vx * Mth.sin(a), ey = uy * Mth.cos(a) + vy * Mth.sin(a), ez = uz * Mth.cos(a) + vz * Mth.sin(a);
            float av = 0.12f + RNG.nextFloat() * 0.18f;
            brume(ox + dx * 0.3, oy + dy * 0.3, oz + dz * 0.3, dx * av + ex * rr, dy * av + ey * rr, dz * av + ez * rr, 0.18f + RNG.nextFloat() * 0.1f,
                    0.05f, 12 + RNG.nextInt(8), 0, 0xFFFFFF, 0.75f, 0.84f);
        }
        if (nB > picB) picB = nB;
        // cœur lumineux du jet (additif) : lueurs bleu glacier qui filent avec les bouffées de tête
        for (int k = 0; k < (fury ? 34 : 26); k++) {
            float phi = (float) Math.sqrt(RNG.nextFloat()) * demiAngle * 0.6f * Mth.DEG_TO_RAD;
            float psi = RNG.nextFloat() * Mth.TWO_PI;
            float sp = Mth.sin(phi), cp = Mth.cos(phi);
            float cx = Mth.cos(psi) * sp, cy = Mth.sin(psi) * sp;
            float wx = dx * cp + ux * cx + vx * cy, wy = dy * cp + uy * cx + vy * cy, wz = dz * cp + uz * cx + vz * cy;
            float v0 = portee * (0.12f + RNG.nextFloat() * 0.14f);
            cristal(ox, oy, oz, wx * v0, wy * v0, wz * v0, 0.16f + RNG.nextFloat() * 0.16f, 9 + RNG.nextInt(7), RNG.nextInt(2), TypesGivre.LUEUR,
                    fury ? (k % 3 == 0 ? 0x9C7CFF : 0x7FB8FF) : 0x8FD8FF, 0.32f, 0.82f, 0f);
        }
        // cristaux qui suivent le flux puis derivent et tombent ; flocons plus gros qui tournent
        int nCr = fury ? 110 : 76;
        for (int k = 0; k < nCr; k++) {
            float phi = (float) Math.sqrt(RNG.nextFloat()) * demiAngle * Mth.DEG_TO_RAD;
            float psi = RNG.nextFloat() * Mth.TWO_PI;
            float sp = Mth.sin(phi), cp = Mth.cos(phi);
            float cx = Mth.cos(psi) * sp, cy = Mth.sin(psi) * sp;
            float wx = dx * cp + ux * cx + vx * cy, wy = dy * cp + uy * cx + vy * cy, wz = dz * cp + uz * cx + vz * cy;
            float v0 = portee * (0.05f + RNG.nextFloat() * 0.21f);
            boolean flocon = k % 6 == 0;
            int c = fury ? (k % 3 == 0 ? VIOLET : (k % 3 == 1 ? 0x9FE8FF : 0xFFFFFF)) : (k % 2 == 0 ? CRISTAL : 0xFFFFFF);
            cristal(ox, oy, oz, wx * v0, wy * v0, wz * v0, flocon ? 0.11f + RNG.nextFloat() * 0.09f : 0.05f + RNG.nextFloat() * 0.08f,
                    flocon ? 40 + RNG.nextInt(24) : 18 + RNG.nextInt(26), RNG.nextInt(3), flocon ? TypesGivre.FLOCON : TypesGivre.ETINCELLE, c,
                    flocon ? 0.7f : 0.95f, 0.84f, flocon ? 0.006f : 0.012f);
        }
        if (nC > picC) picC = nC;
        // traits de froid
        int nTr = fury ? 26 : 18;
        for (int k = 0; k < nTr && nS < MAX_S; k++) {
            float phi = (float) Math.sqrt(RNG.nextFloat()) * demiAngle * 0.85f * Mth.DEG_TO_RAD;
            float psi = RNG.nextFloat() * Mth.TWO_PI;
            float sp = Mth.sin(phi), cp = Mth.cos(phi);
            float cx = Mth.cos(psi) * sp, cy = Mth.sin(psi) * sp;
            int i = nS++;
            sX[i] = ox;
            sY[i] = oy;
            sZ[i] = oz;
            sDx[i] = dx * cp + ux * cx + vx * cy;
            sDy[i] = dy * cp + uy * cx + vy * cy;
            sDz[i] = dz * cp + uz * cx + vz * cy;
            sV[i] = 1.6f + RNG.nextFloat() * 1.2f;
            sT0[i] = now + RNG.nextFloat() * 1.5f;
            sDur[i] = portee * (0.5f + RNG.nextFloat() * 0.45f) / sV[i];
            sL[i] = 0.9f + RNG.nextFloat() * 1.4f;
            sW[i] = 0.04f + RNG.nextFloat() * 0.05f;
            sA[i] = 0.35f + RNG.nextFloat() * 0.3f;
            sCoul[i] = fury && k % 3 == 0 ? VIOLET : 0xDDF6FF;
        }
        // anneau givre a la bouche
        anneau(ox + dx * 0.5, oy + dy * 0.5, oz + dz * 0.5, ux, uy, uz, vx, vy, vz, 0.08f, fury ? 0.62f : 0.46f, now, 5, fury ? 0xCFD8FF : 0xE0F6FF, 0.5f);
        // plaques de givre au sol sous le cone, posees a l'arrivee du front
        float hx = dx, hz = dz, hl = Mth.sqrt(hx * hx + hz * hz);
        if (hl > 1e-3f) {
            hx /= hl;
            hz /= hl;
        } else {
            hx = 1;
            hz = 0;
        }
        float lx = -hz, lz = hx;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int poses = 0;
        for (float s = 1.2f; s <= portee && poses < 16; s += 1.45f) {
            float rad = r0 + s * tan;
            float delai = CalculsGivre.arrivee(s, portee);
            for (int c = 0; c < 2 && poses < 16; c++) {
                float lat = (RNG.nextFloat() * 2 - 1) * rad * 0.7f;
                double px = ox + dx * s + lx * lat, py = oy + dy * s, pz = oz + dz * s + lz * lat;
                int y0 = Mth.floor(py + 0.5), ymin = Mth.floor(py - rad - 2.0);
                double ys = Double.NaN;
                for (int y = y0; y >= ymin; y--) {
                    mp.set(Mth.floor(px), y, Mth.floor(pz));
                    BlockState st = lv.getBlockState(mp);
                    if (!st.isAir() && !st.getCollisionShape(lv, mp).isEmpty()) {
                        ys = y + st.getCollisionShape(lv, mp).max(net.minecraft.core.Direction.Axis.Y);
                        break;
                    }
                }
                if (Double.isNaN(ys) || nP >= MAX_P) continue;
                int i = nP++;
                pX[i] = px;
                pY[i] = ys + 0.015 + poses * 0.0015;
                pZ[i] = pz;
                pT[i] = 0.45f + s * 0.11f + RNG.nextFloat() * 0.35f;
                pRot[i] = RNG.nextFloat() * Mth.TWO_PI;
                pT0[i] = now + delai;
                pDur[i] = 70 + RNG.nextInt(30);
                pA[i] = 0.55f + RNG.nextFloat() * 0.25f;
                poses++;
            }
        }
        if (nP > picP) picP = nP;
        float m = Math.min(4f, portee * 0.35f);
        lv.playLocalSound(ox + dx * m, oy + dy * m, oz + dz * m, SonsGivre.SOUFFLE.get(), SoundSource.PLAYERS, 1.3f, fury ? 0.88f : 1.0f, false);
    }

    private static void brume(double x, double y, double z, float vx, float vy, float vz, float taille, float croissance, int vie, int delai, int coul, float a,
                              float frottement) {
        if (nB >= MAX_B) return;
        int i = nB++;
        bX[i] = bPx[i] = x;
        bY[i] = bPy[i] = y;
        bZ[i] = bPz[i] = z;
        bVx[i] = vx;
        bVy[i] = vy;
        bVz[i] = vz;
        bT[i] = taille;
        bCr[i] = croissance;
        bVie[i] = 0;
        bVieMax[i] = vie;
        bDelai[i] = delai;
        bCoul[i] = coul;
        bA[i] = a;
        bRot[i] = RNG.nextFloat() * Mth.TWO_PI;
        bFr[i] = frottement;
    }

    private static void cristal(double x, double y, double z, float vx, float vy, float vz, float taille, int vie, int delai, int cas, int coul, float a,
                                float frottement, float gravite) {
        if (nC >= MAX_C) return;
        int i = nC++;
        cX[i] = cPx[i] = x;
        cY[i] = cPy[i] = y;
        cZ[i] = cPz[i] = z;
        cVx[i] = vx;
        cVy[i] = vy;
        cVz[i] = vz;
        cT[i] = taille;
        cVie[i] = 0;
        cVieMax[i] = vie;
        cDelai[i] = delai;
        cCase[i] = (byte) cas;
        cCoul[i] = coul;
        cA[i] = a;
        cRot[i] = RNG.nextFloat() * Mth.TWO_PI;
        cVrot[i] = (RNG.nextFloat() - 0.5f) * 0.3f;
        cPh[i] = RNG.nextFloat() * 6.28f;
        cFr[i] = frottement;
        cG[i] = gravite;
    }

    private static void anneau(double x, double y, double z, float ux, float uy, float uz, float vx, float vy, float vz, float r0, float r1, float t0, float dur,
                               int coul, float a) {
        if (nR >= MAX_R) return;
        int i = nR++;
        rX[i] = x;
        rY[i] = y;
        rZ[i] = z;
        rUx[i] = ux;
        rUy[i] = uy;
        rUz[i] = uz;
        rVx[i] = vx;
        rVy[i] = vy;
        rVz[i] = vz;
        rR0[i] = r0;
        rR1[i] = r1;
        rT0[i] = t0;
        rDur[i] = dur;
        rCoul[i] = coul;
        rA[i] = a;
    }

    private static void anneauSol(double x, double y, double z, float r0, float r1, float t0, float dur, int coul, float a) {
        anneau(x, y, z, 1, 0, 0, 0, 0, 1, r0, r1, t0, dur, coul, a);
    }

    // ================================================================== tick
    private static int vapeurTick;

    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            vider();
            return;
        }
        if (mc.isPaused()) return;
        tick++;
        float now = maintenant();
        voilePrec = voile;
        if (voile > 0) voile = voile < 0.01f ? 0 : voile * 0.88f;
        EtatsGivre.tick(now, 60, 0.35 / 20);
        // arrivees du front : bouffee de givre, son du gel
        int w = 0;
        for (int i = 0; i < nA; i++) {
            if (aT[i] > now) {
                if (i != w) {
                    aId[w] = aId[i];
                    aT[w] = aT[i];
                    aGel[w] = aGel[i];
                    aPap[w] = aPap[i];
                }
                w++;
                continue;
            }
            Entity en = mc.level.getEntity(aId[i]);
            if (en != null) impact(mc.level, en, aGel[i], aPap[i] > 0);
        }
        nA = w;
        // corps eclates caches
        w = 0;
        for (int i = 0; i < nK; i++) {
            if (kFin[i] > now) {
                kId[w] = kId[i];
                kFin[w] = kFin[i];
                w++;
            }
        }
        nK = w;
        // creatures gelees : brume froide qui coule, craquements avant l'eclatement
        for (int i = 0; i < EtatsGivre.n; i++) {
            if (!EtatsGivre.gelee(i, now)) continue;
            Entity en = mc.level.getEntity(EtatsGivre.ID[i]);
            if (en == null) continue;
            if ((tick + EtatsGivre.ID[i]) % 4 == 0) {
                AABB b = en.getBoundingBox();
                brume(b.minX + RNG.nextFloat() * (b.maxX - b.minX), b.minY + RNG.nextFloat() * (b.maxY - b.minY) * 0.9, b.minZ + RNG.nextFloat() * (b.maxZ - b.minZ),
                        (RNG.nextFloat() - 0.5f) * 0.02f, -0.012f - RNG.nextFloat() * 0.01f, (RNG.nextFloat() - 0.5f) * 0.02f, 0.18f + RNG.nextFloat() * 0.12f,
                        0.02f, 22 + RNG.nextInt(10), 0, 0xF0F8FF, 0.35f, 0.96f);
            }
            if (!EtatsGivre.CRAQUE[i] && EtatsGivre.avancement(i, now) > 0.7f) {
                EtatsGivre.CRAQUE[i] = true;
                mc.level.playLocalSound(en.getX(), en.getY() + 1, en.getZ(), SonsGivre.CRAQUE.get(), SoundSource.HOSTILE, 0.8f, 0.9f + RNG.nextFloat() * 0.2f,
                        false);
            }
        }
        // degazage du rechargement
        if (nV > 0) {
            int wv = 0;
            for (int i = 0; i < nV; i++) {
                if (vT[i] <= now) {
                    degazer(mc, vF[i]);
                } else {
                    vT[wv] = vT[i];
                    vF[wv++] = vF[i];
                }
            }
            nV = wv;
        }
        // vapeur froide qui coule de l'emetteur de l'arme tenue (joueur local et joueurs proches)
        if (++vapeurTick % 3 == 0) vapeur(mc);
        avancer();
    }

    private static void impact(ClientLevel lv, Entity en, boolean gel, boolean fury) {
        AABB b = en.getBoundingBox();
        double h = b.maxY - b.minY;
        for (int k = 0; k < (gel ? 8 : 4); k++) {
            float a = RNG.nextFloat() * Mth.TWO_PI, v = 0.02f + RNG.nextFloat() * 0.04f;
            brume(en.getX() + Mth.cos(a) * 0.3f, b.minY + RNG.nextFloat() * h, en.getZ() + Mth.sin(a) * 0.3f, Mth.cos(a) * v, 0.01f, Mth.sin(a) * v,
                    0.28f + RNG.nextFloat() * 0.2f, 0.03f, 16 + RNG.nextInt(10), 0, fury ? GLACE_F : GLACE, 0.6f, 0.9f);
        }
        for (int k = 0; k < (gel ? 14 : 5); k++) {
            cristal(b.minX + RNG.nextFloat() * (b.maxX - b.minX), b.minY + RNG.nextFloat() * h, b.minZ + RNG.nextFloat() * (b.maxZ - b.minZ),
                    (RNG.nextFloat() - 0.5f) * 0.05f, 0.02f + RNG.nextFloat() * 0.03f, (RNG.nextFloat() - 0.5f) * 0.05f, 0.06f + RNG.nextFloat() * 0.07f,
                    14 + RNG.nextInt(14), 0, TypesGivre.ETINCELLE, fury ? VIOLET : CRISTAL, 0.95f, 0.9f, 0.004f);
        }
        if (gel) {
            lv.playLocalSound(en.getX(), en.getY() + h * 0.5, en.getZ(), SonsGivre.GEL.get(), SoundSource.HOSTILE, 0.9f, 0.9f + RNG.nextFloat() * 0.25f, false);
        }
    }

    private static void vapeur(Minecraft mc) {
        ClientLevel lv = mc.level;
        if (lv == null) return;
        java.util.List<? extends Player> joueurs = lv.players();
        for (int j = 0; j < joueurs.size(); j++) {
            Player p = joueurs.get(j);
            if (!WintersHowl.est(p.getMainHandItem()) || p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 32 * 32) continue;
            boolean local = p == mc.player;
            Vec3 oeil = p.getEyePosition();
            Vec3 d = p.getLookAngle();
            double[] o = bouche(p, oeil, d, local);
            boolean fury = Armes.pap(p.getMainHandItem()) > 0;
            // la vapeur d'azote est plus lourde que l'air : elle coule de la bouche vers le sol
            brume(o[0] + d.x * 0.08, o[1] - 0.02, o[2] + d.z * 0.08, (float) d.x * 0.012f + (RNG.nextFloat() - 0.5f) * 0.006f, -0.006f - RNG.nextFloat() * 0.006f,
                    (float) d.z * 0.012f + (RNG.nextFloat() - 0.5f) * 0.006f, local ? 0.045f : 0.09f, local ? 0.006f : 0.012f, 26 + RNG.nextInt(12), 0,
                    fury ? 0xE4ECFF : 0xF2F8FF, local ? 0.32f : 0.38f, 0.97f);
            if (RNG.nextFloat() < 0.3f) {
                cristal(o[0], o[1], o[2], (RNG.nextFloat() - 0.5f) * 0.01f, -0.004f, (RNG.nextFloat() - 0.5f) * 0.01f, local ? 0.012f : 0.03f, 20 + RNG.nextInt(10), 0,
                        TypesGivre.ETINCELLE, fury ? VIOLET : CRISTAL, 0.8f, 0.97f, 0.0015f);
            }
        }
    }

    private static void avancer() {
        float now = maintenant();
        int w = 0;
        for (int i = 0; i < nB; i++) {
            if (bDelai[i] > 0) {
                bDelai[i]--;
                copierB(i, w++);
                continue;
            }
            if (++bVie[i] >= bVieMax[i]) continue;
            bPx[i] = bX[i];
            bPy[i] = bY[i];
            bPz[i] = bZ[i];
            bX[i] += bVx[i];
            bY[i] += bVy[i];
            bZ[i] += bVz[i];
            bVx[i] *= bFr[i];
            bVz[i] *= bFr[i];
            // l'air glace retombe doucement
            bVy[i] = bVy[i] * bFr[i] - 0.0035f;
            bT[i] += bCr[i];
            copierB(i, w++);
        }
        nB = w;
        w = 0;
        for (int i = 0; i < nC; i++) {
            if (cDelai[i] > 0) {
                cDelai[i]--;
                copierC(i, w++);
                continue;
            }
            if (++cVie[i] >= cVieMax[i]) continue;
            cPx[i] = cX[i];
            cPy[i] = cY[i];
            cPz[i] = cZ[i];
            cX[i] += cVx[i];
            cY[i] += cVy[i];
            cZ[i] += cVz[i];
            cVx[i] = cVx[i] * cFr[i] + (cCase[i] == TypesGivre.FLOCON ? Mth.sin(cVie[i] * 0.3f + cPh[i]) * 0.004f : 0);
            cVz[i] = cVz[i] * cFr[i] + (cCase[i] == TypesGivre.FLOCON ? Mth.cos(cVie[i] * 0.27f + cPh[i]) * 0.004f : 0);
            cVy[i] = cVy[i] * cFr[i] - cG[i];
            cRot[i] += cVrot[i];
            copierC(i, w++);
        }
        nC = w;
        w = 0;
        for (int i = 0; i < nE; i++) {
            if (++eVie[i] >= eVieMax[i]) continue;
            ePx[i] = eX[i];
            ePy[i] = eY[i];
            ePz[i] = eZ[i];
            ePang[i] = eAng[i];
            eX[i] += eVx[i];
            eY[i] += eVy[i];
            eZ[i] += eVz[i];
            eVy[i] -= 0.045f;
            eVx[i] *= 0.98f;
            eVz[i] *= 0.98f;
            eAng[i] += eVang[i];
            if (eY[i] < eSol[i]) {
                eY[i] = eSol[i];
                if (eVy[i] < -0.05f) {
                    eVy[i] = -eVy[i] * 0.32f;
                    eVx[i] *= 0.55f;
                    eVz[i] *= 0.55f;
                    eVang[i] *= 0.5f;
                } else {
                    eVy[i] = 0;
                    eVx[i] *= 0.7f;
                    eVz[i] *= 0.7f;
                    eVang[i] *= 0.7f;
                }
            }
            copierE(i, w++);
        }
        nE = w;
        w = 0;
        for (int i = 0; i < nR; i++) if (now - rT0[i] <= rDur[i]) copierR(i, w++);
        nR = w;
        w = 0;
        for (int i = 0; i < nP; i++) if (now - pT0[i] <= pDur[i]) copierP(i, w++);
        nP = w;
        w = 0;
        for (int i = 0; i < nS; i++) if (now - sT0[i] <= sDur[i]) copierS(i, w++);
        nS = w;
    }

    private static void copierB(int i, int w) {
        if (i == w) return;
        bX[w] = bX[i];
        bY[w] = bY[i];
        bZ[w] = bZ[i];
        bPx[w] = bPx[i];
        bPy[w] = bPy[i];
        bPz[w] = bPz[i];
        bVx[w] = bVx[i];
        bVy[w] = bVy[i];
        bVz[w] = bVz[i];
        bT[w] = bT[i];
        bCr[w] = bCr[i];
        bA[w] = bA[i];
        bRot[w] = bRot[i];
        bFr[w] = bFr[i];
        bVie[w] = bVie[i];
        bVieMax[w] = bVieMax[i];
        bDelai[w] = bDelai[i];
        bCoul[w] = bCoul[i];
    }

    private static void copierC(int i, int w) {
        if (i == w) return;
        cX[w] = cX[i];
        cY[w] = cY[i];
        cZ[w] = cZ[i];
        cPx[w] = cPx[i];
        cPy[w] = cPy[i];
        cPz[w] = cPz[i];
        cVx[w] = cVx[i];
        cVy[w] = cVy[i];
        cVz[w] = cVz[i];
        cT[w] = cT[i];
        cA[w] = cA[i];
        cRot[w] = cRot[i];
        cVrot[w] = cVrot[i];
        cPh[w] = cPh[i];
        cFr[w] = cFr[i];
        cG[w] = cG[i];
        cVie[w] = cVie[i];
        cVieMax[w] = cVieMax[i];
        cDelai[w] = cDelai[i];
        cCoul[w] = cCoul[i];
        cCase[w] = cCase[i];
    }

    private static void copierE(int i, int w) {
        if (i == w) return;
        eX[w] = eX[i];
        eY[w] = eY[i];
        eZ[w] = eZ[i];
        ePx[w] = ePx[i];
        ePy[w] = ePy[i];
        ePz[w] = ePz[i];
        eSol[w] = eSol[i];
        eVx[w] = eVx[i];
        eVy[w] = eVy[i];
        eVz[w] = eVz[i];
        eAx[w] = eAx[i];
        eAy[w] = eAy[i];
        eAz[w] = eAz[i];
        eAng[w] = eAng[i];
        ePang[w] = ePang[i];
        eVang[w] = eVang[i];
        eT[w] = eT[i];
        eVie[w] = eVie[i];
        eVieMax[w] = eVieMax[i];
        eCoul[w] = eCoul[i];
        eCase[w] = eCase[i];
    }

    private static void copierR(int i, int w) {
        if (i == w) return;
        rX[w] = rX[i];
        rY[w] = rY[i];
        rZ[w] = rZ[i];
        rUx[w] = rUx[i];
        rUy[w] = rUy[i];
        rUz[w] = rUz[i];
        rVx[w] = rVx[i];
        rVy[w] = rVy[i];
        rVz[w] = rVz[i];
        rR0[w] = rR0[i];
        rR1[w] = rR1[i];
        rT0[w] = rT0[i];
        rDur[w] = rDur[i];
        rA[w] = rA[i];
        rCoul[w] = rCoul[i];
    }

    private static void copierP(int i, int w) {
        if (i == w) return;
        pX[w] = pX[i];
        pY[w] = pY[i];
        pZ[w] = pZ[i];
        pT[w] = pT[i];
        pRot[w] = pRot[i];
        pT0[w] = pT0[i];
        pDur[w] = pDur[i];
        pA[w] = pA[i];
    }

    private static void copierS(int i, int w) {
        if (i == w) return;
        sX[w] = sX[i];
        sY[w] = sY[i];
        sZ[w] = sZ[i];
        sDx[w] = sDx[i];
        sDy[w] = sDy[i];
        sDz[w] = sDz[i];
        sV[w] = sV[i];
        sT0[w] = sT0[i];
        sDur[w] = sDur[i];
        sL[w] = sL[i];
        sW[w] = sW[i];
        sA[w] = sA[i];
        sCoul[w] = sCoul[i];
    }

    // ================================================================== rendu
    private static VertexConsumer VC;
    private static Matrix4f M;
    private static double CX, CY, CZ;
    private static float LX, LY, LZ, UX, UY, UZ, AMB;

    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        boolean gelees = false;
        for (int i = 0; i < EtatsGivre.n && !gelees; i++) gelees = EtatsGivre.T_GEL[i] >= 0;
        if (nB == 0 && nC == 0 && nE == 0 && nR == 0 && nP == 0 && nS == 0 && !gelees) return;
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
        float now = maintenant() + partial;
        // lumiere ambiante au point de vue (une lecture par image) : la brume ne luit pas dans le noir
        AMB = ambiante(mc, c);
        M = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VC = buf.getBuffer(TypesGivre.BRUME);
        dessinerPlaques(now);
        dessinerEclats(partial);
        if (gelees) dessinerPointes(mc, now, partial);
        dessinerBrume(partial);
        buf.endBatch(TypesGivre.BRUME);
        VC = buf.getBuffer(TypesGivre.LUMIERE);
        dessinerCristaux(partial, now);
        dessinerTraits(now);
        dessinerAnneaux(now);
        buf.endBatch(TypesGivre.LUMIERE);
        VC = null;
        long ns = System.nanoTime() - t0;
        nsRendu += ns;
        images++;
        if (ns > maxNs) maxNs = ns;
    }

    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

    private static float ambiante(Minecraft mc, Vec3 c) {
        ClientLevel lv = mc.level;
        if (lv == null) return 1f;
        MP.set(Mth.floor(c.x), Mth.floor(c.y), Mth.floor(c.z));
        int bloc = lv.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, MP);
        int ciel = lv.getBrightness(net.minecraft.world.level.LightLayer.SKY, MP);
        float sombre = lv.getSkyDarken() / 11f;   // 0 le jour -> ~1 la nuit
        float k = Math.max(bloc / 15f, ciel / 15f * (1f - 0.78f * Math.min(1f, sombre)));
        return 0.16f + 0.84f * Math.min(1f, k);
    }

    private static void sommet(float x, float y, float z, int coul, int a, float u, float v) {
        VC.vertex(M, x, y, z).color((coul >> 16) & 255, (coul >> 8) & 255, coul & 255, a).uv(u, v).endVertex();
    }

    private static int sombre(int coul, float k) {
        int r = (int) (((coul >> 16) & 255) * k), g = (int) (((coul >> 8) & 255) * k), b = (int) ((coul & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static int alpha(float a) {
        return a <= 0 ? 0 : a >= 1 ? 255 : (int) (a * 255);
    }

    /** Carre face a la camera (coordonnees relatives a la camera), tourne de {@code rot}. */
    private static void panneau(float x, float y, float z, float r, float rot, int cell, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        float ax = LX * cs + UX * sn, ay = LY * cs + UY * sn, az = LZ * cs + UZ * sn;
        float bx = -LX * sn + UX * cs, by = -LY * sn + UY * cs, bz = -LZ * sn + UZ * cs;
        float u0 = TypesGivre.u0(cell), v0 = TypesGivre.v0(cell), u1 = TypesGivre.u1(cell), v1 = TypesGivre.v1(cell);
        sommet(x - ax - bx, y - ay - by, z - az - bz, coul, al, u0, v1);
        sommet(x + ax - bx, y + ay - by, z + az - bz, coul, al, u1, v1);
        sommet(x + ax + bx, y + ay + by, z + az + bz, coul, al, u1, v0);
        sommet(x - ax + bx, y - ay + by, z - az + bz, coul, al, u0, v0);
    }

    /** Ruban face a la camera de A a B (coordonnees relatives a la camera). */
    private static void ruban(float ax, float ay, float az, float bx, float by, float bz, float w0, float w1, int cell, int coul, float a0, float a1) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        float n = sx * sx + sy * sy + sz * sz;
        if (n < 1.0E-10f) return;
        n = 1f / (float) Math.sqrt(n);
        sx *= n;
        sy *= n;
        sz *= n;
        float h0 = w0 * 0.5f, h1 = w1 * 0.5f;
        int al0 = alpha(a0), al1 = alpha(a1);
        if (al0 == 0 && al1 == 0) return;
        float u0 = TypesGivre.u0(cell), v0 = TypesGivre.v0(cell), u1 = TypesGivre.u1(cell), v1 = TypesGivre.v1(cell);
        sommet(ax + sx * h0, ay + sy * h0, az + sz * h0, coul, al0, u0, v0);
        sommet(ax - sx * h0, ay - sy * h0, az - sz * h0, coul, al0, u0, v1);
        sommet(bx - sx * h1, by - sy * h1, bz - sz * h1, coul, al1, u1, v1);
        sommet(bx + sx * h1, by + sy * h1, bz + sz * h1, coul, al1, u1, v0);
    }

    private static void dessinerBrume(float partial) {
        for (int i = 0; i < nB; i++) {
            if (bDelai[i] > 0) continue;
            float k = (bVie[i] + partial) / bVieMax[i];
            if (k >= 1) continue;
            float x = (float) (Mth.lerp(partial, bPx[i], bX[i]) - CX), y = (float) (Mth.lerp(partial, bPy[i], bY[i]) - CY),
                    z = (float) (Mth.lerp(partial, bPz[i], bZ[i]) - CZ);
            if (x * x + y * y + z * z < 0.04f) continue;
            float entree = Math.min(1f, k * 6f);
            panneau(x, y, z, bT[i] + bCr[i] * partial, bRot[i] + k * 0.8f, (i & 1) == 0 ? TypesGivre.BRUME_A : TypesGivre.BRUME_B, sombre(bCoul[i], AMB),
                    bA[i] * entree * (1 - k) * (1 - k * 0.4f) * (0.45f + 0.55f * AMB));
        }
    }

    private static void dessinerCristaux(float partial, float now) {
        for (int i = 0; i < nC; i++) {
            if (cDelai[i] > 0) continue;
            float k = (cVie[i] + partial) / cVieMax[i];
            if (k >= 1) continue;
            float x = (float) (Mth.lerp(partial, cPx[i], cX[i]) - CX), y = (float) (Mth.lerp(partial, cPy[i], cY[i]) - CY),
                    z = (float) (Mth.lerp(partial, cPz[i], cZ[i]) - CZ);
            float sc = cCase[i] == TypesGivre.FLOCON ? 1f : 0.55f + 0.45f * Mth.sin(now * 0.9f + cPh[i]);
            panneau(x, y, z, cT[i] * (0.8f + 0.4f * sc), cRot[i] + cVrot[i] * partial, cCase[i], cCoul[i], cA[i] * sc * (1 - k * k) * Math.min(1f, k * 8f));
        }
    }

    private static void dessinerTraits(float now) {
        for (int i = 0; i < nS; i++) {
            float age = now - sT0[i];
            if (age < 0 || age > sDur[i]) continue;
            float k = age / sDur[i];
            float d = sV[i] * age * (1 - 0.35f * k);
            double bx = sX[i] + sDx[i] * d, by = sY[i] + sDy[i] * d, bz = sZ[i] + sDz[i] * d;
            float L = sL[i] * Math.min(1, age * 1.5f + 0.2f);
            double ax = bx - sDx[i] * L, ay = by - sDy[i] * L, az = bz - sDz[i] * L;
            float a = sA[i] * (1 - k) * Math.min(1, k * 6 + 0.3f);
            ruban((float) (ax - CX), (float) (ay - CY), (float) (az - CZ), (float) (bx - CX), (float) (by - CY), (float) (bz - CZ), sW[i] * 0.4f, sW[i],
                    TypesGivre.TRAIT, sCoul[i], 0, a);
        }
    }

    private static void dessinerAnneaux(float now) {
        for (int i = 0; i < nR; i++) {
            float age = now - rT0[i];
            if (age < 0 || age > rDur[i]) continue;
            float k = age / rDur[i];
            float e = 1 - (1 - k) * (1 - k);
            float r = rR0[i] + (rR1[i] - rR0[i]) * e;
            float a = rA[i] * (1 - k);
            int al = alpha(a);
            if (al == 0) continue;
            float x = (float) (rX[i] - CX), y = (float) (rY[i] - CY), z = (float) (rZ[i] - CZ);
            float ax = rUx[i] * r, ay = rUy[i] * r, az = rUz[i] * r, bx = rVx[i] * r, by = rVy[i] * r, bz = rVz[i] * r;
            float u0 = TypesGivre.u0(TypesGivre.ANNEAU), v0 = TypesGivre.v0(TypesGivre.ANNEAU), u1 = TypesGivre.u1(TypesGivre.ANNEAU), v1 = TypesGivre.v1(TypesGivre.ANNEAU);
            sommet(x - ax - bx, y - ay - by, z - az - bz, rCoul[i], al, u0, v1);
            sommet(x + ax - bx, y + ay - by, z + az - bz, rCoul[i], al, u1, v1);
            sommet(x + ax + bx, y + ay + by, z + az + bz, rCoul[i], al, u1, v0);
            sommet(x - ax + bx, y - ay + by, z - az + bz, rCoul[i], al, u0, v0);
        }
    }

    private static void dessinerPlaques(float now) {
        float u0 = TypesGivre.u0(TypesGivre.GIVRE_SOL), v0 = TypesGivre.v0(TypesGivre.GIVRE_SOL), u1 = TypesGivre.u1(TypesGivre.GIVRE_SOL),
                v1 = TypesGivre.v1(TypesGivre.GIVRE_SOL);
        int coul = sombre(0xF4FAFF, Math.max(0.3f, AMB));
        for (int i = 0; i < nP; i++) {
            float age = now - pT0[i];
            if (age < 0 || age > pDur[i]) continue;
            float k = age / pDur[i];
            float a = pA[i] * Math.min(1f, age / 3f) * (k > 0.75f ? (1 - k) / 0.25f : 1f);
            int al = alpha(a);
            if (al == 0) continue;
            float r = pT[i] * (0.6f + 0.4f * Math.min(1f, age / 4f));
            float cs = Mth.cos(pRot[i]) * r, sn = Mth.sin(pRot[i]) * r;
            float x = (float) (pX[i] - CX), y = (float) (pY[i] - CY), z = (float) (pZ[i] - CZ);
            sommet(x - cs + sn, y, z - sn - cs, coul, al, u0, v1);
            sommet(x + cs + sn, y, z + sn - cs, coul, al, u1, v1);
            sommet(x + cs - sn, y, z + sn + cs, coul, al, u1, v0);
            sommet(x - cs - sn, y, z - sn + cs, coul, al, u0, v0);
        }
    }

    /** Eclat en 3D : deux quads croises dans le repere de l'eclat, tourne autour de son axe (Rodrigues), qui s'eteint. */
    private static void dessinerEclats(float partial) {
        for (int i = 0; i < nE; i++) {
            float k = (eVie[i] + partial) / eVieMax[i];
            if (k >= 1) continue;
            float x = (float) (Mth.lerp(partial, ePx[i], eX[i]) - CX), y = (float) (Mth.lerp(partial, ePy[i], eY[i]) - CY),
                    z = (float) (Mth.lerp(partial, ePz[i], eZ[i]) - CZ);
            float ang = Mth.lerp(partial, ePang[i], eAng[i]);
            float c = Mth.cos(ang), s = Mth.sin(ang), t = 1 - c;
            float ax = eAx[i], ay = eAy[i], az = eAz[i];
            // colonnes de la rotation (repere local de l'eclat)
            float r00 = c + ax * ax * t, r10 = ay * ax * t + az * s, r20 = az * ax * t - ay * s;
            float r01 = ax * ay * t - az * s, r11 = c + ay * ay * t, r21 = az * ay * t + ax * s;
            float r02 = ax * az * t + ay * s, r12 = ay * az * t - ax * s, r22 = c + az * az * t;
            float h = eT[i];
            int coul = sombre(eCoul[i], Math.max(0.3f, AMB));
            float a = 0.95f * (k > 0.7f ? (1 - k) / 0.3f : 1f);
            int al = alpha(a);
            int cell = eCase[i];
            float u0 = TypesGivre.u0(cell), v0 = TypesGivre.v0(cell), u1 = TypesGivre.u1(cell), v1 = TypesGivre.v1(cell);
            // quad 1 : plan (x, y) local ; quad 2 : plan (z, y) local
            for (int q = 0; q < 2; q++) {
                float px = q == 0 ? r00 : r02, py = q == 0 ? r10 : r12, pz = q == 0 ? r20 : r22;
                float qx = r01 * 1.4f, qy = r11 * 1.4f, qz = r21 * 1.4f;
                sommet(x + (-px - qx) * h, y + (-py - qy) * h, z + (-pz - qz) * h, coul, al, u0, v1);
                sommet(x + (px - qx) * h, y + (py - qy) * h, z + (pz - qz) * h, coul, al, u1, v1);
                sommet(x + (px + qx) * h, y + (py + qy) * h, z + (pz + qz) * h, coul, al, u1, v0);
                sommet(x + (-px + qx) * h, y + (-py + qy) * h, z + (-pz + qz) * h, coul, al, u0, v0);
            }
        }
    }

    /** Pointes de cristal sur les creatures gelees (fixes pour une creature donnee : directions tirees de son id). */
    private static void dessinerPointes(Minecraft mc, float now, float partial) {
        ClientLevel lv = mc.level;
        if (lv == null) return;
        for (int i = 0; i < EtatsGivre.n; i++) {
            if (!EtatsGivre.gelee(i, now)) continue;
            Entity en = lv.getEntity(EtatsGivre.ID[i]);
            if (!(en instanceof LivingEntity)) continue;
            float pousse = Math.min(1f, (now - EtatsGivre.T_GEL[i]) / 5f);
            double ex = Mth.lerp(partial, en.xo, en.getX()) - CX, ey = Mth.lerp(partial, en.yo, en.getY()) - CY, ez = Mth.lerp(partial, en.zo, en.getZ()) - CZ;
            float w = en.getBbWidth() * 0.5f, h = en.getBbHeight();
            int id = EtatsGivre.ID[i];
            boolean fury = EtatsGivre.PAP[i] > 0;
            int coul = sombre(fury ? 0xC8D8FF : 0xE6F8FF, Math.max(0.35f, AMB));
            int nb = 9;
            for (int k = 0; k < nb; k++) {
                float a = h11(id * 31 + k * 7) * Mth.PI, hy = 0.15f + 0.8f * (h11(id * 17 + k * 3) * 0.5f + 0.5f);
                float dxp = Mth.cos(a), dzp = Mth.sin(a), dyp = 0.35f + 0.5f * (h11(id * 13 + k) * 0.5f + 0.5f);
                float L = (0.22f + 0.28f * (h11(id * 7 + k * 11) * 0.5f + 0.5f)) * pousse;
                float bx = (float) ex + dxp * w * 0.9f, by = (float) ey + hy * h, bz = (float) ez + dzp * w * 0.9f;
                float tx = bx + dxp * L * 0.75f, ty = by + dyp * L, tz = bz + dzp * L * 0.75f;
                ruban(bx, by, bz, tx, ty, tz, 0.14f * pousse, 0.02f, TypesGivre.POINTE, coul, 0.92f, 0.92f);
            }
        }
    }

    private static int hash(int x) {
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        return (x >>> 16) ^ x;
    }

    private static float h11(int x) {
        return (hash(x) & 0xFFFF) / 32767.5f - 1f;
    }

    // ================================================================== creatures : corps eclates caches, tremblement avant l'eclatement
    private static final LivingEntity[] PILE = new LivingEntity[8];
    private static int nPile;

    /** Priorite haute : le corps d'une creature eclatee n'est plus dessine (avant que d'autres effets n'empilent une pose). */
    public static void cacher(RenderLivingEvent.Pre<?, ?> e) {
        if (nK == 0) return;
        int id = e.getEntity().getId();
        for (int i = 0; i < nK; i++) {
            if (kId[i] == id) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /** Priorite basse : une creature gelee tremble juste avant d'eclater. */
    public static void vivantAvant(RenderLivingEvent.Pre<?, ?> e) {
        if (EtatsGivre.n == 0) return;
        LivingEntity le = e.getEntity();
        int id = le.getId();
        int i = EtatsGivre.chercher(id);
        if (i < 0 || nPile >= PILE.length) return;
        float now = maintenant() + e.getPartialTick();
        float av = EtatsGivre.avancement(i, now);
        if (!EtatsGivre.gelee(i, now) || av < 0.72f) return;
        float amp = 0.012f + 0.03f * (av - 0.72f) / 0.28f;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(h11((int) (now * 7) + id) * amp, 0, h11((int) (now * 7) + id * 3 + 1) * amp);
        PILE[nPile++] = le;
    }

    public static void vivantApres(RenderLivingEvent.Post<?, ?> e) {
        if (nPile > 0 && PILE[nPile - 1] == e.getEntity()) {
            e.getPoseStack().popPose();
            PILE[--nPile] = null;
        }
    }

    // ================================================================== voile de givre a l'ecran
    public static void ecran(RenderGuiEvent.Post e) {
        float v = Mth.lerp(e.getPartialTick(), voilePrec, voile);
        if (v <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // la nuit, le givre de l'ecran est sombre lui aussi (lumiere ambiante relevee a chaque image)
        float k = Math.max(0.25f, AMB);
        e.getGuiGraphics().setColor(k, k, k, Math.min(1f, v * (0.5f + 0.5f * AMB)));
        e.getGuiGraphics().blit(TypesGivre.ECRAN, 0, 0, w, h, 0, 0, 256, 144, 256, 144);
        e.getGuiGraphics().setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ================================================================== menage, statistiques
    public static void dechargement(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) vider();
    }

    private static void vider() {
        nB = nC = nE = nR = nP = nS = nA = nK = nV = 0;
        EtatsGivre.vider();
        for (int i = 0; i < nPile; i++) PILE[i] = null;
        nPile = 0;
        voile = voilePrec = 0;
    }

    public static String stats() {
        String r = String.format(Locale.ROOT,
                "givre : %d tirs, %d eclatements ; rendu moyen %.3f ms (pire %.3f ms) sur %d images ; en cours : %d brumes, %d cristaux, %d eclats, %d plaques, "
                        + "%d creatures givrees ; pics : %d/%d brumes, %d/%d cristaux, %d/%d eclats, %d/%d plaques",
                tirs, eclatsRecus, images == 0 ? 0 : nsRendu / 1e6 / images, maxNs / 1e6, images, nB, nC, nE, nP, EtatsGivre.n, picB, MAX_B, picC, MAX_C, picE, MAX_E,
                picP, MAX_P);
        nsRendu = maxNs = 0;
        images = 0;
        tirs = eclatsRecus = picB = picC = picE = picP = 0;
        return r;
    }
}
