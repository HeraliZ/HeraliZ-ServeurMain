package fr.heraliz.wonder.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.Sons;
import fr.heraliz.wonder.client.ClientWonder;
import fr.heraliz.wonder.client.TaczClient;
import fr.heraliz.wonder.net.Souffle;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
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
 * Effets du Thundergun cote client (1.0.2), dans des reserves de taille fixe (tableaux paralleles, aucune allocation par
 * image), dessines en deux lots ({@link FxTypes#LUMIERE} additif, {@link FxTypes#FUMEE} translucide) avec la planche de
 * {@link Fx} :
 * <ul>
 *   <li><b>onde de choc</b> : six anneaux d'air ondulants (voile laiteux + liseré lumineux) lances le long du cone, qui
 *   s'elargissent en suivant son ouverture et ralentissent en s'eteignant ; flash et anneau a la bouche ;</li>
 *   <li><b>vent</b> : traits de vent tres rapides dans le cone ;</li>
 *   <li><b>poussiere et debris</b> : nuages souleves au sol au passage du front (teinte du sol), eclats du bloc du sol
 *   (particules de bloc de Minecraft) projetes vers l'avant ;</li>
 *   <li><b>creatures soufflees</b> : elles culbutent en vol (rendu tourne), bouffee de poussiere et anneau a l'impact ;</li>
 *   <li><b>tireur</b> : secousse de la camera ; son tir et l'onde partent tout de suite (prediction), le paquet du serveur
 *   n'apporte plus que les creatures soufflees ;</li>
 *   <li><b>Zeus Cannon</b> (Pack-a-Punch) : anneaux dores a liseré violet, eclairs qui rampent dans le cone.</li>
 * </ul>
 */
public final class FxVent {
    private FxVent() {
    }

    private static final RandomSource RNG = RandomSource.create();
    private static long tick;
    private static float dernierLocal = -1000;

    private static float maintenant() {
        return (float) tick;
    }

    // ---- anneaux (onde de choc, bouche, impact sur une creature)
    private static final byte A_ONDE = 0, A_BOUCHE = 1, A_IMPACT = 2;
    private static final int MAX_R = 64, SEG = 32;
    private static final double[] rOx = new double[MAX_R], rOy = new double[MAX_R], rOz = new double[MAX_R];
    private static final float[] rDx = new float[MAX_R], rDy = new float[MAX_R], rDz = new float[MAX_R];
    private static final float[] rUx = new float[MAX_R], rUy = new float[MAX_R], rUz = new float[MAX_R];
    private static final float[] rVx = new float[MAX_R], rVy = new float[MAX_R], rVz = new float[MAX_R];
    private static final float[] rT0 = new float[MAX_R], rDur = new float[MAX_R], rPortee = new float[MAX_R], rTan = new float[MAX_R],
            rR0 = new float[MAX_R], rA = new float[MAX_R];
    private static final int[] rCoul = new int[MAX_R], rBord = new int[MAX_R], rSeed = new int[MAX_R];
    private static final byte[] rGenre = new byte[MAX_R];
    private static int nR;

    // ---- traits de vent
    private static final int MAX_S = 384;
    private static final double[] sX = new double[MAX_S], sY = new double[MAX_S], sZ = new double[MAX_S];
    private static final float[] sDx = new float[MAX_S], sDy = new float[MAX_S], sDz = new float[MAX_S], sV = new float[MAX_S], sT0 = new float[MAX_S],
            sDur = new float[MAX_S], sL = new float[MAX_S], sW = new float[MAX_S], sA = new float[MAX_S];
    private static final int[] sCoul = new int[MAX_S];
    private static int nS;

    // ---- poussiere (translucide)
    private static final int MAX_D = 512;
    private static final double[] dX = new double[MAX_D], dY = new double[MAX_D], dZ = new double[MAX_D], dPx = new double[MAX_D],
            dPy = new double[MAX_D], dPz = new double[MAX_D];
    private static final float[] dVx = new float[MAX_D], dVy = new float[MAX_D], dVz = new float[MAX_D], dTaille = new float[MAX_D], dA = new float[MAX_D];
    private static final int[] dVie = new int[MAX_D], dVieMax = new int[MAX_D], dDelai = new int[MAX_D], dCoul = new int[MAX_D];
    private static int nD;

    // ---- debris en attente (particules de bloc de Minecraft emises au passage du front)
    private static final int MAX_B = 128;
    private static final double[] bX = new double[MAX_B], bY = new double[MAX_B], bZ = new double[MAX_B];
    private static final float[] bVx = new float[MAX_B], bVy = new float[MAX_B], bVz = new float[MAX_B];
    private static final long[] bTick = new long[MAX_B];
    private static final BlockState[] bEtat = new BlockState[MAX_B];
    private static int nBd;

    // ---- eclairs du Zeus Cannon
    private static final int MAX_E = 64, SEG_E = 7;
    private static final double[] eAx = new double[MAX_E], eAy = new double[MAX_E], eAz = new double[MAX_E], eBx = new double[MAX_E],
            eBy = new double[MAX_E], eBz = new double[MAX_E];
    private static final float[] eT0 = new float[MAX_E], eDur = new float[MAX_E], eAmp = new float[MAX_E];
    private static final int[] eSeed = new int[MAX_E], eCoul = new int[MAX_E];
    private static int nE;

    // ---- creatures qui culbutent
    private static final int MAX_T = 96;
    private static final int[] tId = new int[MAX_T];
    private static final float[] tT0 = new float[MAX_T], tAx = new float[MAX_T], tAy = new float[MAX_T], tAz = new float[MAX_T], tVit = new float[MAX_T],
            tDur = new float[MAX_T];
    private static int nT;

    // ---- secousse de la camera du tireur, flash blanc d'un instant
    private static float secT0 = -1000, secAmp;
    private static float flash;
    private static int flashCoul;

    // ---- statistiques
    private static long nsRendu, maxNs;
    private static int images, ondes, picR, picS, picD, picT;

    // ================================================================== entrees
    /** Tir du joueur local (prediction, des que TaCZ accepte le tir) : onde, vent, poussiere, secousse et son. */
    public static void tirLocal(LocalPlayer p, ItemStack arme) {
        int pap = Armes.pap(arme);
        Minecraft mc = Minecraft.getInstance();
        Vec3 oeil = p.getEyePosition();
        Vec3 d = p.getLookAngle();
        float[] dec = new float[3];
        double ox, oy, oz;
        if (ClientWonder.tacz && TaczClient.premierePersonne() && TaczClient.decalageCanon(mc.gameRenderer.getMainCamera(), dec)) {
            ox = oeil.x + dec[0];
            oy = oeil.y + dec[1];
            oz = oeil.z + dec[2];
        } else {
            ox = oeil.x + d.x * 1.1;
            oy = oeil.y + d.y * 1.1 - 0.35;
            oz = oeil.z + d.z * 1.1;
        }
        double portee = pap > 0 ? 26 + 2 * (pap - 1) : 20, angle = pap > 0 ? 30 + 2.5 * (pap - 1) : 22.5;
        onde(ox, oy, oz, (float) d.x, (float) d.y, (float) d.z, (float) portee, (float) angle, 1.3f, pap, oeil.x, oeil.y, oeil.z);
        secT0 = maintenant();
        secAmp = pap > 0 ? 3.4f : 2.6f;
        flash = pap > 0 ? 0.26f : 0.2f;
        flashCoul = pap > 0 ? 0xFFE2A0 : 0xDCEBFF;
        dernierLocal = maintenant();
    }

    /** Paquet du serveur : l'onde (sauf si c'est le tir du joueur local, deja dessine) et les creatures soufflees. */
    public static void souffle(Souffle m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        boolean local = mc.player != null && m.tireur() == mc.player.getId() && maintenant() - dernierLocal < 14;
        if (!local) {
            Entity t = lv.getEntity(m.tireur());
            double ox = m.ox() + m.dx() * 1.1, oy = m.oy() + m.dy() * 1.1 - 0.35, oz = m.oz() + m.dz() * 1.1;
            if (t != null) {
                // en troisieme personne l'arme est tenue a la hanche, a droite
                float yaw = t.getYRot() * Mth.DEG_TO_RAD;
                ox += -Mth.cos(yaw) * 0.32;
                oz += -Mth.sin(yaw) * 0.32;
            }
            onde(ox, oy, oz, m.dx(), m.dy(), m.dz(), m.portee(), m.demiAngle(), m.r0(), m.pap(), m.ox(), m.oy(), m.oz());
        }
        for (int i = 0; i < m.n(); i++) {
            Entity e = lv.getEntity(m.ids()[i]);
            if (e == null) continue;
            double s = (e.getX() - m.ox()) * m.dx() + (e.getY() + e.getBbHeight() * 0.5 - m.oy()) * m.dy() + (e.getZ() - m.oz()) * m.dz();
            cible(lv, e, m.dx(), m.dy(), m.dz(), m.pap(), i, fr.heraliz.wonder.Calculs.arriveeSouffle(s, m.portee()));
        }
    }

    // ================================================================== creation
    private static void onde(double ox, double oy, double oz, float dx, float dy, float dz, float portee, float demiAngle, float r0, int pap,
                             double yx, double yy, double yz) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        ondes++;
        float now = maintenant();
        float tan = Mth.sin(demiAngle * Mth.DEG_TO_RAD) / Mth.cos(demiAngle * Mth.DEG_TO_RAD);
        // base orthonormee perpendiculaire a la direction
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
        boolean zeus = pap > 0;
        int coul = zeus ? 0xFFE7B0 : 0xE4F2FF, bord = zeus ? 0xC88CFF : 0x8FD2FF;
        // anneaux d'air : quatre fronts d'onde, le premier le plus franc
        for (int k = 0; k < 4; k++) {
            int i = anneau(A_ONDE, ox, oy, oz, dx, dy, dz, ux, uy, uz, vx, vy, vz, now + k * 1.1f, 11f + k * 1.2f, portee * (1 - k * 0.06f), tan, r0 * 0.6f,
                    coul, bord, 0.62f - k * 0.1f);
            if (i < 0) break;
        }
        // bouffée d'air blanc à la bouche (très visible pour le tireur)
        for (int k = 0; k < 18; k++) {
            float psi = RNG.nextFloat() * Mth.TWO_PI, rr = RNG.nextFloat();
            float ex = ux * Mth.cos(psi) + vx * Mth.sin(psi), ey = uy * Mth.cos(psi) + vy * Mth.sin(psi), ez = uz * Mth.cos(psi) + vz * Mth.sin(psi);
            float av = 0.25f + RNG.nextFloat() * 0.45f, lat = 0.06f + rr * 0.2f, dep = 0.8f + (k % 3) * 1.1f;
            poussiere(ox + dx * dep + ex * rr * 0.5, oy + dy * dep + ey * rr * 0.5, oz + dz * dep + ez * rr * 0.5, dx * av + ex * lat, dy * av + ey * lat + 0.01f,
                    dz * av + ez * lat, 0.7f + RNG.nextFloat() * 0.5f, 14 + RNG.nextInt(8), 0, zeus ? 0xFFF0D8 : 0xF4F6FA, 0.85f);
        }
        // flash et anneau a la bouche
        anneau(A_BOUCHE, ox + dx * 0.6, oy + dy * 0.6, oz + dz * 0.6, dx, dy, dz, ux, uy, uz, vx, vy, vz, now, 4.5f, 3.2f, 0, 0.35f, coul, bord, 0.9f);
        // vent
        int nTraits = zeus ? 64 : 46;
        for (int k = 0; k < nTraits && nS < MAX_S; k++) {
            float phi = (float) Math.sqrt(RNG.nextFloat()) * demiAngle * 0.92f * Mth.DEG_TO_RAD;
            float psi = RNG.nextFloat() * Mth.TWO_PI;
            float sp = Mth.sin(phi), cpp = Mth.cos(phi);
            float cx = Mth.cos(psi) * sp, cy = Mth.sin(psi) * sp;
            float wx = dx * cpp + (ux * cx + vx * cy), wy = dy * cpp + (uy * cx + vy * cy), wz = dz * cpp + (uz * cx + vz * cy);
            float dep = 0.4f + RNG.nextFloat() * 2.2f;
            float lat = RNG.nextFloat() * r0 * 0.8f, psi2 = RNG.nextFloat() * Mth.TWO_PI;
            int i = nS++;
            sX[i] = ox + dx * dep + (ux * Mth.cos(psi2) + vx * Mth.sin(psi2)) * lat;
            sY[i] = oy + dy * dep + (uy * Mth.cos(psi2) + vy * Mth.sin(psi2)) * lat;
            sZ[i] = oz + dz * dep + (uz * Mth.cos(psi2) + vz * Mth.sin(psi2)) * lat;
            sDx[i] = wx;
            sDy[i] = wy;
            sDz[i] = wz;
            sV[i] = 2.4f + RNG.nextFloat() * 1.4f;
            sT0[i] = now + RNG.nextFloat() * 2.5f;
            sDur[i] = portee * (0.45f + RNG.nextFloat() * 0.5f) / sV[i];
            sL[i] = 1.2f + RNG.nextFloat() * 1.8f;
            sW[i] = 0.05f + RNG.nextFloat() * 0.08f;
            sA[i] = 0.35f + RNG.nextFloat() * 0.3f;
            sCoul[i] = zeus ? (RNG.nextFloat() < 0.4f ? 0xD8A8FF : 0xFFE9B8) : 0xE8F6FF;
        }
        if (nS > picS) picS = nS;
        // poussiere et debris au sol, au passage du front
        float hx = dx, hz = dz;
        float hl = Mth.sqrt(hx * hx + hz * hz);
        if (hl > 1e-3f) {
            hx /= hl;
            hz /= hl;
        } else {
            hx = 1;
            hz = 0;
        }
        float lx = -hz, lz = hx;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int debris = 0, poussieres = 0;
        for (float s = 1.4f; s <= portee; s += 1.35f) {
            float rad = r0 + s * tan;
            int delai = fr.heraliz.wonder.Calculs.arriveeSouffle(s, portee);
            for (int c = -1; c <= 1; c++) {
                float lat = c * rad * (0.45f + RNG.nextFloat() * 0.3f);
                double px = ox + dx * s + lx * lat, py = oy + dy * s, pz = oz + dz * s + lz * lat;
                // sol sous le point : dans le cone (a moins de rad + 1,5 bloc sous l'axe)
                int y0 = Mth.floor(py + 0.5), ymin = Mth.floor(py - rad - 1.5);
                BlockState sol = null;
                double ys = 0;
                for (int y = y0; y >= ymin; y--) {
                    mp.set(Mth.floor(px), y, Mth.floor(pz));
                    BlockState st = lv.getBlockState(mp);
                    if (!st.isAir() && !st.getCollisionShape(lv, mp).isEmpty()) {
                        sol = st;
                        ys = y + st.getCollisionShape(lv, mp).max(net.minecraft.core.Direction.Axis.Y);
                        break;
                    }
                }
                if (sol == null) continue;
                int teinte = melange(sol.getMapColor(lv, mp).col, 0xDDD2BE, 0.7f);
                float force = 1 - 0.55f * s / portee;
                for (int k = 0; k < (c == 0 ? 3 : 2) && poussieres < 90; k++) {
                    poussieres++;
                    poussiere(px + (RNG.nextFloat() - 0.5f) * 0.9f, ys + 0.2f, pz + (RNG.nextFloat() - 0.5f) * 0.9f,
                            hx * (0.25f + RNG.nextFloat() * 0.3f) * force + lx * (RNG.nextFloat() - 0.5f) * 0.16f, 0.07f + RNG.nextFloat() * 0.1f,
                            hz * (0.25f + RNG.nextFloat() * 0.3f) * force + lz * (RNG.nextFloat() - 0.5f) * 0.16f, 0.85f + RNG.nextFloat() * 0.8f,
                            26 + RNG.nextInt(18), delai, teinte, 0.85f);
                }
                if (c == 0 && debris < 40 && sol.getRenderShape() != RenderShape.INVISIBLE) {
                    for (int k = 0; k < 2 && nBd < MAX_B; k++) {
                        debris++;
                        int i = nBd++;
                        bX[i] = px + (RNG.nextFloat() - 0.5f);
                        bY[i] = ys + 0.1f;
                        bZ[i] = pz + (RNG.nextFloat() - 0.5f);
                        bVx[i] = hx * (0.25f + RNG.nextFloat() * 0.35f) * force;
                        bVy[i] = 0.28f + RNG.nextFloat() * 0.35f;
                        bVz[i] = hz * (0.25f + RNG.nextFloat() * 0.35f) * force;
                        bTick[i] = tick + delai;
                        bEtat[i] = sol;
                    }
                }
            }
        }
        // eclairs du Zeus Cannon
        if (zeus) {
            for (int k = 0; k < 10 && nE < MAX_E; k++) {
                float a = 0.8f + RNG.nextFloat() * portee * 0.35f, b = a + 3 + RNG.nextFloat() * portee * 0.45f;
                float psa = RNG.nextFloat() * Mth.TWO_PI, psb = psa + (RNG.nextFloat() - 0.5f) * 1.6f;
                float ra = (r0 + a * tan) * (0.3f + 0.6f * RNG.nextFloat()), rb = (r0 + b * tan) * (0.3f + 0.6f * RNG.nextFloat());
                int i = nE++;
                eAx[i] = ox + dx * a + (ux * Mth.cos(psa) + vx * Mth.sin(psa)) * ra;
                eAy[i] = oy + dy * a + (uy * Mth.cos(psa) + vy * Mth.sin(psa)) * ra;
                eAz[i] = oz + dz * a + (uz * Mth.cos(psa) + vz * Mth.sin(psa)) * ra;
                eBx[i] = ox + dx * b + (ux * Mth.cos(psb) + vx * Mth.sin(psb)) * rb;
                eBy[i] = oy + dy * b + (uy * Mth.cos(psb) + vy * Mth.sin(psb)) * rb;
                eBz[i] = oz + dz * b + (uz * Mth.cos(psb) + vz * Mth.sin(psb)) * rb;
                eT0[i] = now + RNG.nextFloat() * 3;
                eDur[i] = 5 + RNG.nextFloat() * 5;
                eAmp[i] = 0.25f + 0.04f * (b - a);
                eSeed[i] = RNG.nextInt();
                eCoul[i] = RNG.nextFloat() < 0.5f ? 0xC88CFF : 0xFFD27A;
            }
        }
        // son du souffle qui balaie le cone (le son du tir lui-meme est celui de TaCZ)
        float m = Math.min(8f, portee * 0.4f);
        lv.playLocalSound(ox + dx * m, oy + dy * m, oz + dz * m, Sons.SOUFFLE.get(), SoundSource.PLAYERS, 1.6f, zeus ? 0.86f : 1.0f, false);
    }

    private static int anneau(byte genre, double ox, double oy, double oz, float dx, float dy, float dz, float ux, float uy, float uz, float vx, float vy,
                              float vz, float t0, float dur, float portee, float tan, float r0, int coul, int bord, float a) {
        if (nR >= MAX_R) return -1;
        int i = nR++;
        rGenre[i] = genre;
        rOx[i] = ox;
        rOy[i] = oy;
        rOz[i] = oz;
        rDx[i] = dx;
        rDy[i] = dy;
        rDz[i] = dz;
        rUx[i] = ux;
        rUy[i] = uy;
        rUz[i] = uz;
        rVx[i] = vx;
        rVy[i] = vy;
        rVz[i] = vz;
        rT0[i] = t0;
        rDur[i] = dur;
        rPortee[i] = portee;
        rTan[i] = tan;
        rR0[i] = r0;
        rCoul[i] = coul;
        rBord[i] = bord;
        rA[i] = a;
        rSeed[i] = RNG.nextInt(1000);
        if (nR > picR) picR = nR;
        return i;
    }

    private static void poussiere(double x, double y, double z, float vx, float vy, float vz, float taille, int vie, int delai, int coul, float a) {
        if (nD >= MAX_D) return;
        int i = nD++;
        dX[i] = dPx[i] = x;
        dY[i] = dPy[i] = y;
        dZ[i] = dPz[i] = z;
        dVx[i] = vx;
        dVy[i] = vy;
        dVz[i] = vz;
        dTaille[i] = taille;
        dVie[i] = 0;
        dVieMax[i] = vie;
        dDelai[i] = delai;
        dCoul[i] = coul;
        dA[i] = a;
        if (nD > picD) picD = nD;
    }

    /** Creature soufflee : elle culbute, bouffee de poussiere et petit anneau d'air, bruit sourd. */
    private static void cible(ClientLevel lv, Entity e, float dx, float dy, float dz, int pap, int rang, int delai) {
        float now = maintenant() + delai;
        double h = e.getBbHeight();
        if (nT < MAX_T && e instanceof LivingEntity) {
            int i = nT++;
            tId[i] = e.getId();
            tT0[i] = now;
            // axe de culbute : horizontal, perpendiculaire au souffle (la tete part vers l'arriere), un peu de hasard
            float ax = -dz + (RNG.nextFloat() - 0.5f) * 0.5f, az = dx + (RNG.nextFloat() - 0.5f) * 0.5f, ay = (RNG.nextFloat() - 0.5f) * 0.6f;
            float n = Mth.sqrt(ax * ax + ay * ay + az * az);
            tAx[i] = ax / n;
            tAy[i] = ay / n;
            tAz[i] = az / n;
            tVit[i] = 0.32f + RNG.nextFloat() * 0.3f;
            tDur[i] = 34;
            if (nT > picT) picT = nT;
        }
        for (int k = 0; k < 5; k++) {
            float a = RNG.nextFloat() * Mth.TWO_PI;
            poussiere(e.getX(), e.getY() + 0.2 + RNG.nextFloat() * h * 0.6, e.getZ(), Mth.cos(a) * 0.08f + dx * 0.2f, 0.04f + RNG.nextFloat() * 0.05f,
                    Mth.sin(a) * 0.08f + dz * 0.2f, 0.6f + RNG.nextFloat() * 0.5f, 18 + RNG.nextInt(10), delai, 0xD2C7B4, 0.7f);
        }
        int coul = pap > 0 ? 0xFFE7B0 : 0xE4F2FF;
        float ux = -dz, uz = dx;
        float nu = Mth.sqrt(ux * ux + uz * uz);
        if (nu < 1e-3f) {
            ux = 1;
            uz = 0;
            nu = 1;
        }
        ux /= nu;
        uz /= nu;
        float vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux;
        anneau(A_IMPACT, e.getX(), e.getY() + h * 0.55, e.getZ(), dx, dy, dz, ux, 0, uz, vx, vy, vz, now, 5, 1.6f, 0, 0.3f, coul, pap > 0 ? 0xC88CFF : 0x8FD2FF,
                0.5f);
        if (rang < 6) {
            lv.playLocalSound(e.getX(), e.getY(), e.getZ(), Sons.SOUFFLE_CORPS.get(), SoundSource.HOSTILE, 0.7f, 0.85f + RNG.nextFloat() * 0.3f, false);
        }
    }

    private static int melange(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ================================================================== tick
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
        if (flash > 0) flash = flash < 0.01f ? 0 : flash * 0.55f;
        int w = 0;
        for (int i = 0; i < nR; i++) if (now - rT0[i] <= rDur[i]) copierAnneau(i, w++);
        nR = w;
        w = 0;
        for (int i = 0; i < nS; i++) if (now - sT0[i] <= sDur[i]) copierTrait(i, w++);
        nS = w;
        w = 0;
        for (int i = 0; i < nE; i++) if (now - eT0[i] <= eDur[i]) copierEclair(i, w++);
        nE = w;
        w = 0;
        for (int i = 0; i < nT; i++) {
            Entity en = mc.level.getEntity(tId[i]);
            if (en != null && !en.isRemoved() && now - tT0[i] <= tDur[i]) copierCulbute(i, w++);
        }
        nT = w;
        // poussiere : mouvement, frottement de l'air, elle monte un peu
        w = 0;
        for (int i = 0; i < nD; i++) {
            if (dDelai[i] > 0) {
                dDelai[i]--;
                copierPoussiere(i, w++);
                continue;
            }
            if (++dVie[i] >= dVieMax[i]) continue;
            dPx[i] = dX[i];
            dPy[i] = dY[i];
            dPz[i] = dZ[i];
            dX[i] += dVx[i];
            dY[i] += dVy[i];
            dZ[i] += dVz[i];
            dVx[i] *= 0.86f;
            dVy[i] = dVy[i] * 0.88f + 0.004f;
            dVz[i] *= 0.86f;
            copierPoussiere(i, w++);
        }
        nD = w;
        // debris : particules de bloc de Minecraft au passage du front
        w = 0;
        for (int i = 0; i < nBd; i++) {
            if (bTick[i] <= tick) {
                BlockState st = bEtat[i];
                bEtat[i] = null;
                try {
                    Particle p = mc.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), bX[i], bY[i], bZ[i], 0, 0, 0);
                    if (p != null) p.setParticleSpeed(bVx[i], bVy[i], bVz[i]);
                } catch (RuntimeException ignored) {
                }
            } else {
                copierDebris(i, w++);
            }
        }
        for (int i = w; i < nBd; i++) bEtat[i] = null;
        nBd = w;
    }

    private static void copierAnneau(int i, int w) {
        if (i == w) return;
        rGenre[w] = rGenre[i];
        rOx[w] = rOx[i];
        rOy[w] = rOy[i];
        rOz[w] = rOz[i];
        rDx[w] = rDx[i];
        rDy[w] = rDy[i];
        rDz[w] = rDz[i];
        rUx[w] = rUx[i];
        rUy[w] = rUy[i];
        rUz[w] = rUz[i];
        rVx[w] = rVx[i];
        rVy[w] = rVy[i];
        rVz[w] = rVz[i];
        rT0[w] = rT0[i];
        rDur[w] = rDur[i];
        rPortee[w] = rPortee[i];
        rTan[w] = rTan[i];
        rR0[w] = rR0[i];
        rCoul[w] = rCoul[i];
        rBord[w] = rBord[i];
        rA[w] = rA[i];
        rSeed[w] = rSeed[i];
    }

    private static void copierTrait(int i, int w) {
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

    private static void copierEclair(int i, int w) {
        if (i == w) return;
        eAx[w] = eAx[i];
        eAy[w] = eAy[i];
        eAz[w] = eAz[i];
        eBx[w] = eBx[i];
        eBy[w] = eBy[i];
        eBz[w] = eBz[i];
        eT0[w] = eT0[i];
        eDur[w] = eDur[i];
        eAmp[w] = eAmp[i];
        eSeed[w] = eSeed[i];
        eCoul[w] = eCoul[i];
    }

    private static void copierCulbute(int i, int w) {
        if (i == w) return;
        tId[w] = tId[i];
        tT0[w] = tT0[i];
        tAx[w] = tAx[i];
        tAy[w] = tAy[i];
        tAz[w] = tAz[i];
        tVit[w] = tVit[i];
        tDur[w] = tDur[i];
    }

    private static void copierPoussiere(int i, int w) {
        if (i == w) return;
        dX[w] = dX[i];
        dY[w] = dY[i];
        dZ[w] = dZ[i];
        dPx[w] = dPx[i];
        dPy[w] = dPy[i];
        dPz[w] = dPz[i];
        dVx[w] = dVx[i];
        dVy[w] = dVy[i];
        dVz[w] = dVz[i];
        dTaille[w] = dTaille[i];
        dA[w] = dA[i];
        dVie[w] = dVie[i];
        dVieMax[w] = dVieMax[i];
        dDelai[w] = dDelai[i];
        dCoul[w] = dCoul[i];
    }

    private static void copierDebris(int i, int w) {
        if (i == w) return;
        bX[w] = bX[i];
        bY[w] = bY[i];
        bZ[w] = bZ[i];
        bVx[w] = bVx[i];
        bVy[w] = bVy[i];
        bVz[w] = bVz[i];
        bTick[w] = bTick[i];
        bEtat[w] = bEtat[i];
    }

    // ================================================================== rendu
    private static VertexConsumer VC;
    private static Matrix4f M;
    private static double CX, CY, CZ;
    private static float LX, LY, LZ, UX, UY, UZ;

    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (nR == 0 && nS == 0 && nD == 0 && nE == 0) return;
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
        M = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        // voiles translucides : corps des anneaux, traits de vent, poussiere (visibles de jour comme de nuit)
        VC = buf.getBuffer(FxTypes.FUMEE);
        dessinerAnneaux(now, true);
        dessinerTraits(now, true);
        dessinerPoussiere(partial);
        buf.endBatch(FxTypes.FUMEE);
        // lumiere additive : liserés, bouche, vent, eclairs
        VC = buf.getBuffer(FxTypes.LUMIERE);
        dessinerAnneaux(now, false);
        dessinerTraits(now, false);
        dessinerEclairs(now);
        buf.endBatch(FxTypes.LUMIERE);
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

    /**
     * Anneaux d'air : {@code voile} = passe translucide (voile laiteux large d'un tiers du rayon, et pour les fronts d'onde
     * une bande sombre juste à l'intérieur : la « distorsion » se lit aussi sur un ciel clair) ; sinon passe additive (fin
     * liseré lumineux, flash de la bouche).
     */
    private static void dessinerAnneaux(float now, boolean voile) {
        for (int i = 0; i < nR; i++) {
            float age = now - rT0[i];
            if (age < 0 || age > rDur[i]) continue;
            float k = age / rDur[i];
            float s, r, larg, a;
            switch (rGenre[i]) {
                case A_ONDE -> {
                    float e = 1 - (float) Math.pow(1 - k, 2.2);
                    s = rPortee[i] * e;
                    r = rR0[i] + s * rTan[i];
                    larg = 0.18f + 0.035f * s;
                    a = rA[i] * (float) Math.pow(1 - k, 1.25) * Math.min(1, k * 10);
                }
                case A_BOUCHE -> {
                    float e = 1 - (1 - k) * (1 - k);
                    s = 0;
                    r = 0.25f + rPortee[i] * e;
                    larg = 0.14f + 0.25f * e;
                    a = rA[i] * (1 - k);
                }
                default -> {
                    float e = 1 - (1 - k) * (1 - k);
                    s = 0;
                    r = 0.2f + rPortee[i] * e;
                    larg = 0.12f + 0.14f * e;
                    a = rA[i] * (1 - k);
                }
            }
            double cx = rOx[i] + rDx[i] * s - CX, cy = rOy[i] + rDy[i] * s - CY, cz = rOz[i] + rDz[i] * s - CZ;
            if (voile) {
                segments(i, cx, cy, cz, r, larg * 1.4f + r * 0.3f, rCoul[i], alpha(a * 0.8f), age);
                if (rGenre[i] == A_ONDE) segments(i, cx, cy, cz, r * 0.8f, r * 0.16f + larg * 0.5f, 0x5A6A80, alpha(a * 0.22f), age);
            } else {
                if (rGenre[i] == A_BOUCHE) panneau(cx, cy, cz, 0.6f + 1.8f * k, k * 2, FxTypes.LUEUR, rCoul[i], (1 - k) * 0.55f);
                segments(i, cx, cy, cz, r, larg * 0.5f, rBord[i], alpha(a * (rGenre[i] == A_BOUCHE ? 0.9f : 0.6f)), age);
            }
        }
    }

    /** SEG quads ondulés entre r - w/2 et r + w/2, dans le plan perpendiculaire à l'anneau i (texture « faisceau »). */
    private static void segments(int i, double cx, double cy, double cz, float r, float w, int coul, int al8, float age) {
        if (al8 == 0 || r <= 0) return;
        float u0 = FxTypes.u0(FxTypes.FAISCEAU), u1 = FxTypes.u1(FxTypes.FAISCEAU), v0 = FxTypes.v0(FxTypes.FAISCEAU), v1 = FxTypes.v1(FxTypes.FAISCEAU);
        int seed = rSeed[i];
        float ph = age * 0.55f;
        float pa = 0, pc = 1, ps = 0;
        for (int j = 0; j <= SEG; j++) {
            float ang = Mth.TWO_PI * j / SEG;
            float co = Mth.cos(ang), si = Mth.sin(ang);
            float ond = 1 + 0.055f * Mth.sin(3 * ang + seed + ph) + 0.03f * Mth.sin(7 * ang - seed * 0.37f - ph * 1.7f);
            if (j > 0) {
                float ri0 = r * pa - w * 0.5f, re0 = r * pa + w * 0.5f, ri1 = r * ond - w * 0.5f, re1 = r * ond + w * 0.5f;
                ri0 = Math.max(0, ri0);
                ri1 = Math.max(0, ri1);
                float ax = rUx[i] * pc + rVx[i] * ps, ay = rUy[i] * pc + rVy[i] * ps, az = rUz[i] * pc + rVz[i] * ps;
                float bx = rUx[i] * co + rVx[i] * si, by = rUy[i] * co + rVy[i] * si, bz = rUz[i] * co + rVz[i] * si;
                float uu0 = u0 + (u1 - u0) * ((j - 1) % 4) / 4f, uu1 = uu0 + (u1 - u0) / 4f;
                sommet((float) (cx + ax * ri0), (float) (cy + ay * ri0), (float) (cz + az * ri0), coul, al8, uu0, v0);
                sommet((float) (cx + ax * re0), (float) (cy + ay * re0), (float) (cz + az * re0), coul, al8, uu0, v1);
                sommet((float) (cx + bx * re1), (float) (cy + by * re1), (float) (cz + bz * re1), coul, al8, uu1, v1);
                sommet((float) (cx + bx * ri1), (float) (cy + by * ri1), (float) (cz + bz * ri1), coul, al8, uu1, v0);
            }
            pa = ond;
            pc = co;
            ps = si;
        }
    }

    private static void dessinerTraits(float now, boolean voile) {
        for (int i = 0; i < nS; i++) {
            float age = now - sT0[i];
            if (age < 0 || age > sDur[i]) continue;
            float k = age / sDur[i];
            float d = sV[i] * age;
            double bx = sX[i] + sDx[i] * d, by = sY[i] + sDy[i] * d, bz = sZ[i] + sDz[i] * d;
            float L = sL[i] * Math.min(1, age * 1.5f + 0.2f);
            double ax = bx - sDx[i] * L, ay = by - sDy[i] * L, az = bz - sDz[i] * L;
            float a = sA[i] * (1 - k) * Math.min(1, k * 6 + 0.3f);
            if (voile) {
                ruban((float) (ax - CX), (float) (ay - CY), (float) (az - CZ), (float) (bx - CX), (float) (by - CY), (float) (bz - CZ), sW[i] * 0.8f, sW[i] * 2.2f,
                        FxTypes.FAISCEAU, 0xF4F7FC, 0, a * 0.75f);
            } else {
                ruban((float) (ax - CX), (float) (ay - CY), (float) (az - CZ), (float) (bx - CX), (float) (by - CY), (float) (bz - CZ), sW[i] * 0.3f, sW[i],
                        FxTypes.ETINCELLE, sCoul[i], 0, a);
            }
        }
    }

    private static final float[] PE = new float[(SEG_E + 1) * 3];

    private static void dessinerEclairs(float now) {
        for (int i = 0; i < nE; i++) {
            float age = now - eT0[i];
            if (age < 0 || age > eDur[i]) continue;
            float k = age / eDur[i];
            int f = (int) (age / 1.5f) + eSeed[i];
            float ax = (float) (eAx[i] - CX), ay = (float) (eAy[i] - CY), az = (float) (eAz[i] - CZ);
            float bx = (float) (eBx[i] - CX), by = (float) (eBy[i] - CY), bz = (float) (eBz[i] - CZ);
            for (int j = 0; j <= SEG_E; j++) {
                float t = (float) j / SEG_E;
                float amp = (j == 0 || j == SEG_E) ? 0 : eAmp[i];
                PE[j * 3] = ax + (bx - ax) * t + h11(f * 31 + j * 3) * amp;
                PE[j * 3 + 1] = ay + (by - ay) * t + h11(f * 31 + j * 3 + 1) * amp;
                PE[j * 3 + 2] = az + (bz - az) * t + h11(f * 31 + j * 3 + 2) * amp;
            }
            float a = (1 - k) * (0.6f + 0.4f * (h11(f * 7) * 0.5f + 0.5f));
            for (int j = 0; j < SEG_E; j++) {
                ruban(PE[j * 3], PE[j * 3 + 1], PE[j * 3 + 2], PE[j * 3 + 3], PE[j * 3 + 4], PE[j * 3 + 5], 0.22f, 0.22f, FxTypes.ARC, eCoul[i], a, a);
                ruban(PE[j * 3], PE[j * 3 + 1], PE[j * 3 + 2], PE[j * 3 + 3], PE[j * 3 + 4], PE[j * 3 + 5], 0.07f, 0.07f, FxTypes.ARC, 0xFFFFFF, a, a);
            }
        }
    }

    private static void dessinerPoussiere(float partial) {
        for (int i = 0; i < nD; i++) {
            if (dDelai[i] > 0) continue;
            float k = (dVie[i] + partial) / dVieMax[i];
            if (k >= 1) continue;
            double x = Mth.lerp(partial, dPx[i], dX[i]), y = Mth.lerp(partial, dPy[i], dY[i]), z = Mth.lerp(partial, dPz[i], dZ[i]);
            double cx = x - CX, cy = y - CY, cz = z - CZ;
            if (cx * cx + cy * cy + cz * cz < 0.25) continue;
            float entree = Math.min(1f, k * 7f);
            panneau(cx, cy, cz, dTaille[i] * (1 + k * 1.8f), i * 1.9f + k * 0.9f, (i & 1) == 0 ? FxTypes.FUMEE_A : FxTypes.FUMEE_B, dCoul[i],
                    dA[i] * entree * (1 - k) * (1 - k * 0.3f));
        }
    }

    /** Carre face a la camera (coordonnees relatives a la camera), tourne de {@code rot}. */
    private static void panneau(double px, double py, double pz, float r, float rot, int cell, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        float ax = LX * cs + UX * sn, ay = LY * cs + UY * sn, az = LZ * cs + UZ * sn;
        float bx = -LX * sn + UX * cs, by = -LY * sn + UY * cs, bz = -LZ * sn + UZ * cs;
        float u0 = FxTypes.u0(cell), v0 = FxTypes.v0(cell), u1 = FxTypes.u1(cell), v1 = FxTypes.v1(cell);
        float x = (float) px, y = (float) py, z = (float) pz;
        sommet(x - ax - bx, y - ay - by, z - az - bz, coul, al, u0, v1);
        sommet(x + ax - bx, y + ay - by, z + az - bz, coul, al, u1, v1);
        sommet(x + ax + bx, y + ay + by, z + az + bz, coul, al, u1, v0);
        sommet(x - ax + bx, y - ay + by, z - az + bz, coul, al, u0, v0);
    }

    /** Ruban face a la camera de A a B (coordonnees relatives a la camera), largeurs w0 / w1. */
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
        float u0 = FxTypes.u0(cell), v0 = FxTypes.v0(cell), u1 = FxTypes.u1(cell), v1 = FxTypes.v1(cell);
        sommet(ax + sx * h0, ay + sy * h0, az + sz * h0, coul, al0, u0, v0);
        sommet(ax - sx * h0, ay - sy * h0, az - sz * h0, coul, al0, u0, v1);
        sommet(bx - sx * h1, by - sy * h1, bz - sz * h1, coul, al1, u1, v1);
        sommet(bx + sx * h1, by + sy * h1, bz + sz * h1, coul, al1, u1, v0);
    }

    private static int hash(int x) {
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        return (x >>> 16) ^ x;
    }

    private static float h11(int x) {
        return (hash(x) & 0xFFFF) / 32767.5f - 1f;
    }

    // ================================================================== creatures soufflees : culbute
    private static final LivingEntity[] PILE = new LivingEntity[8];
    private static int nPile;
    private static final Quaternionf Q = new Quaternionf();

    public static void vivantAvant(RenderLivingEvent.Pre<?, ?> e) {
        if (nT == 0) return;
        LivingEntity le = e.getEntity();
        int id = le.getId();
        for (int i = 0; i < nT; i++) {
            if (tId[i] != id) continue;
            float age = maintenant() + e.getPartialTick() - tT0[i];
            if (age < 0 || age > tDur[i] || nPile >= PILE.length) return;
            // culbute rapide au debut, qui ralentit (la creature retombe)
            float ang = tVit[i] * (age - age * age / (2.6f * tDur[i]));
            float h = le.getBbHeight() * 0.5f;
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(0, h, 0);
            ps.mulPose(Q.setAngleAxis(ang, tAx[i], tAy[i], tAz[i]));
            ps.translate(0, -h, 0);
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

    // ================================================================== secousse de la camera du tireur
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (secAmp <= 0) return;
        float age = maintenant() + (float) e.getPartialTick() - secT0;
        if (age < 0) return;
        if (age > 14) {
            secAmp = 0;
            return;
        }
        float k = (float) Math.exp(-age / 3.6f) * secAmp;
        e.setPitch(e.getPitch() - k * (0.55f + 0.45f * Mth.sin(age * 2.3f)));
        e.setYaw(e.getYaw() + k * 0.5f * Mth.sin(age * 3.1f + 1.3f));
        e.setRoll(e.getRoll() + k * 0.8f * Mth.sin(age * 3.9f + 0.4f));
    }

    public static void ecran(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        if (flash <= 0.004f) return;
        Minecraft mc = Minecraft.getInstance();
        e.getGuiGraphics().fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), (alpha(flash) << 24) | (flashCoul & 0xFFFFFF));
    }

    // ================================================================== menage, statistiques
    public static void dechargement(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) vider();
    }

    private static void vider() {
        nR = nS = nD = nE = nT = 0;
        for (int i = 0; i < nBd; i++) bEtat[i] = null;
        nBd = 0;
        for (int i = 0; i < nPile; i++) PILE[i] = null;
        nPile = 0;
        secAmp = 0;
        flash = 0;
    }

    public static String stats() {
        String r = String.format(Locale.ROOT,
                "thundergun : %d ondes ; rendu moyen %.3f ms (pire %.3f ms) sur %d images ; en cours : %d anneaux, %d traits, %d poussieres, %d culbutes ; "
                        + "pics : %d/%d anneaux, %d/%d traits, %d/%d poussieres, %d/%d culbutes",
                ondes, images == 0 ? 0 : nsRendu / 1e6 / images, maxNs / 1e6, images, nR, nS, nD, nT, picR, MAX_R, picS, MAX_S, picD, MAX_D, picT, MAX_T);
        nsRendu = maxNs = 0;
        images = 0;
        ondes = picR = picS = picD = picT = 0;
        return r;
    }
}
