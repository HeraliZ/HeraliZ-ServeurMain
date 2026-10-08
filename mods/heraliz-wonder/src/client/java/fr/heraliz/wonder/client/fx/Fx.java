package fr.heraliz.wonder.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.Sons;
import fr.heraliz.wonder.client.ClientWonder;
import fr.heraliz.wonder.client.TaczClient;
import fr.heraliz.wonder.net.Chaine;
import fr.heraliz.wonder.net.Impact;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Tous les effets des Wonder Weapons cote client, dans des reserves de taille fixe (tableaux paralleles, aucune
 * allocation par image ni par particule) et dessines en deux lots ({@link FxTypes#LUMIERE} additif, {@link FxTypes#FUMEE}).
 * <ul>
 *   <li><b>Balles suivies</b> : chaque balle TaCZ de Pistolet a rayons / Mk II est dessinee en rayon d'energie (coeur
 *   blanc, halo colore, trainee, lueur portee au sol), part du canon et rejoint sa vraie trajectoire en quelques blocs ;</li>
 *   <li><b>Ondes</b> (Pistolet a rayons, facon Black Ops 2) : cercles fins perpendiculaires au trajet, semes tous les
 *   1,35 bloc, qui s'elargissent et s'eteignent en un tiers de seconde ; salve d'ondes a l'impact ; rouges au Pack-a-Punch ;</li>
 *   <li><b>Arcs</b> : eclairs brises qui scintillent (forme retiree toutes les 1,5 tick), avec branches ;</li>
 *   <li><b>Lueurs</b> : flashs, anneaux d'onde, lumiere au sol ;</li>
 *   <li><b>Particules</b> : etincelles, gouttes de plasma, poussieres lumineuses, fumee ;</li>
 *   <li><b>Electrocutes</b> : les creatures foudroyees tremblent (rendu decale), crepitent et fument.</li>
 * </ul>
 */
public final class Fx {
    private Fx() {
    }

    private static long tick;
    private static final net.minecraft.util.RandomSource RNG = net.minecraft.util.RandomSource.create();

    // ------------------------------------------------------------------ couleurs (lueur, coeur) par arme et Pack-a-Punch
    static int lueur(byte type, int pap) {
        return switch (type) {
            case Armes.RAYON -> pap > 0 ? 0xFF2A1E : 0x3CFF2E;
            case Armes.MK2 -> pap > 0 ? 0xFF38D8 : 0xFF3A12;
            default -> pap > 0 ? 0xFF5A2A : 0x6CB8FF;
        };
    }

    static int coeur(byte type, int pap) {
        return switch (type) {
            case Armes.RAYON -> pap > 0 ? 0xFFDCD2 : 0xDCFFD2;
            case Armes.MK2 -> pap > 0 ? 0xFFE2FA : 0xFFE8DA;
            default -> pap > 0 ? 0xFFEADC : 0xEEF6FF;
        };
    }

    // ================================================================== reserves
    // ---- balles suivies
    private static final int MAX_B = 96, TRAINEE = 6;
    private static final Entity[] bE = new Entity[MAX_B];
    private static final byte[] bType = new byte[MAX_B], bPap = new byte[MAX_B];
    private static final float[] bOx = new float[MAX_B], bOy = new float[MAX_B], bOz = new float[MAX_B];
    private static final double[] bSx = new double[MAX_B], bSy = new double[MAX_B], bSz = new double[MAX_B];
    private static final double[] bTr = new double[MAX_B * TRAINEE * 3];
    private static final int[] bTrN = new int[MAX_B], bFin = new int[MAX_B];
    private static final boolean[] bArret = new boolean[MAX_B];
    private static final double[] bAx = new double[MAX_B], bAy = new double[MAX_B], bAz = new double[MAX_B];
    private static final double[] bSol = new double[MAX_B];
    /** Derniere position de la tete (ondes du Pistolet a rayons) et distance parcourue depuis la derniere onde. */
    private static final double[] bHx = new double[MAX_B], bHy = new double[MAX_B], bHz = new double[MAX_B], bOnde = new double[MAX_B];
    private static int nB;

    // ---- ondes du Pistolet a rayons (Black Ops 2) : cercles fins perpendiculaires au trajet, qui s'elargissent et s'eteignent
    private static final int MAX_O = 512;
    private static final double[] oX = new double[MAX_O], oY = new double[MAX_O], oZ = new double[MAX_O];
    private static final float[] oDx = new float[MAX_O], oDy = new float[MAX_O], oDz = new float[MAX_O];
    private static final float[] oT0 = new float[MAX_O], oDur = new float[MAX_O], oR0 = new float[MAX_O], oR1 = new float[MAX_O], oA = new float[MAX_O];
    private static final int[] oCoul = new int[MAX_O];
    private static int nO;
    /** Ecart entre deux ondes le long du trajet (blocs) : la balle parcourt 4 blocs par tick, soit 3 ondes par tick. */
    private static final double ONDE_PAS = 1.35;

    // ---- arcs
    private static final int MAX_A = 256, SEG = 20;
    private static final double[] aAx = new double[MAX_A], aAy = new double[MAX_A], aAz = new double[MAX_A];
    private static final double[] aBx = new double[MAX_A], aBy = new double[MAX_A], aBz = new double[MAX_A];
    private static final float[] aT0 = new float[MAX_A], aDur = new float[MAX_A], aLarg = new float[MAX_A];
    private static final int[] aSeed = new int[MAX_A], aCoul = new int[MAX_A], aCoeur = new int[MAX_A];
    private static final byte[] aGenre = new byte[MAX_A];
    private static int nA;
    private static final byte ARC_PRINCIPAL = 0, ARC_CHAINE = 1, ARC_MICRO = 2, ARC_TRAIT = 3;

    // ---- lueurs
    private static final int MAX_L = 224;
    private static final double[] lX = new double[MAX_L], lY = new double[MAX_L], lZ = new double[MAX_L];
    private static final float[] lT0 = new float[MAX_L], lDur = new float[MAX_L], lR0 = new float[MAX_L], lR1 = new float[MAX_L], lA = new float[MAX_L];
    private static final int[] lCoul = new int[MAX_L];
    private static final byte[] lGenre = new byte[MAX_L];
    private static int nL;
    private static final byte L_FLASH = 0, L_ANNEAU = 1, L_SOL = 2, L_ETOILE = 3, L_ANNEAU_SOL = 4, L_ONDE = 5;

    // ---- particules
    private static final int MAX_P = 2048;
    private static final double[] pX = new double[MAX_P], pY = new double[MAX_P], pZ = new double[MAX_P];
    private static final double[] pPx = new double[MAX_P], pPy = new double[MAX_P], pPz = new double[MAX_P];
    private static final float[] pVx = new float[MAX_P], pVy = new float[MAX_P], pVz = new float[MAX_P];
    private static final float[] pVie = new float[MAX_P], pVieMax = new float[MAX_P], pTaille = new float[MAX_P], pGrav = new float[MAX_P],
            pFrott = new float[MAX_P];
    private static final int[] pCoul = new int[MAX_P];
    private static final byte[] pGenre = new byte[MAX_P];
    private static int nP, nFumee;
    private static final byte P_ETINCELLE = 0, P_POUSSIERE = 1, P_FUMEE = 2, P_GOUTTE = 3;

    // ---- electrocutes
    private static final int MAX_E = 64;
    private static final int[] eId = new int[MAX_E];
    private static final float[] eT0 = new float[MAX_E], eDur = new float[MAX_E], eH = new float[MAX_E], eW = new float[MAX_E];
    private static final double[] eX = new double[MAX_E], eY = new double[MAX_E], eZ = new double[MAX_E];
    private static final byte[] ePap = new byte[MAX_E];
    private static int nE;

    // ---- evenements differes (arrivee d'un arc sur une cible)
    private static final int MAX_V = 128;
    private static final float[] vT = new float[MAX_V];
    private static final double[] vX = new double[MAX_V], vY = new double[MAX_V], vZ = new double[MAX_V];
    private static final int[] vId = new int[MAX_V], vRang = new int[MAX_V];
    private static final byte[] vPap = new byte[MAX_V];
    private static int nV;

    // ---- flash d'ecran (tireur de la Wunderwaffe)
    private static float flashEcran;
    private static int flashCoul;

    // ---- statistiques
    private static long nsRendu, images, maxNs;
    private static int picB, picA, picL, picP, picE, picO;

    private static final float[] DEC = new float[3];

    // ================================================================== entrees (paquets, balles)
    private static float maintenant() {
        return tick;
    }

    /** Balle TaCZ d'un rayon qui arrive chez le client. */
    public static void balle(Entity b, byte type, int pap, LivingEntity tireur, double sx, double sy, double sz) {
        if (nB >= MAX_B) return;
        int i = nB++;
        bE[i] = b;
        bType[i] = type;
        bPap[i] = (byte) pap;
        bSx[i] = sx;
        bSy[i] = sy;
        bSz[i] = sz;
        bTrN[i] = 0;
        bFin[i] = 0;
        bArret[i] = false;
        bSol[i] = Double.NaN;
        decalageCanon(tireur);
        bOx[i] = DEC[0];
        bOy[i] = DEC[1];
        bOz[i] = DEC[2];
        if (nB > picB) picB = nB;
        // eclair du canon (la vraie lumiere verte / rouge au depart du tir)
        double mx = sx + DEC[0], my = sy + DEC[1], mz = sz + DEC[2];
        bHx[i] = mx;
        bHy[i] = my;
        bHz[i] = mz;
        bOnde[i] = ONDE_PAS * 0.5;
        int c = lueur(type, pap);
        lueurAjout(L_FLASH, mx, my, mz, 0.35f, type == Armes.RAYON ? 0.9f : 0.7f, 3, c, 0.55f, 0);
        lueurAjout(L_SOL, mx, solSous(mx, my, mz, 3), mz, 1.6f, 1.6f, 4, c, 0.25f, 0);
        if (type == Armes.RAYON) {
            // premiere onde a la bouche du canon, un peu plus large (le « wouf » du Pistolet a rayons de Black Ops 2)
            Vec3 v = b.getDeltaMovement();
            double vl = v.length();
            if (vl > 1.0E-6) {
                float big = pap > 0 ? 1.2f : 1f;
                ondeAjout(mx, my, mz, (float) (v.x / vl), (float) (v.y / vl), (float) (v.z / vl), 0.06f * big, 0.42f * big, 5, c, 0.9f, maintenant());
                ondeAjout(mx, my, mz, (float) (v.x / vl), (float) (v.y / vl), (float) (v.z / vl), 0.04f * big, 0.24f * big, 4, coeur(type, pap), 0.8f, maintenant());
            }
        }
    }

    /** Onde orientee (cercle fin dans le plan perpendiculaire a {@code d}, unitaire), qui passe de r0 a r1 en {@code dur} ticks. */
    private static void ondeAjout(double x, double y, double z, float dx, float dy, float dz, float r0, float r1, float dur, int coul, float alpha, float t0) {
        if (nO >= MAX_O) return;
        int i = nO++;
        oX[i] = x;
        oY[i] = y;
        oZ[i] = z;
        oDx[i] = dx;
        oDy[i] = dy;
        oDz[i] = dz;
        oR0[i] = r0;
        oR1[i] = r1;
        oDur[i] = dur;
        oCoul[i] = coul;
        oA[i] = alpha;
        oT0[i] = t0;
        if (nO > picO) picO = nO;
    }

    private static void copierOnde(int i, int w) {
        if (i == w) return;
        oX[w] = oX[i];
        oY[w] = oY[i];
        oZ[w] = oZ[i];
        oDx[w] = oDx[i];
        oDy[w] = oDy[i];
        oDz[w] = oDz[i];
        oR0[w] = oR0[i];
        oR1[w] = oR1[i];
        oDur[w] = oDur[i];
        oCoul[w] = oCoul[i];
        oA[w] = oA[i];
        oT0[w] = oT0[i];
    }

    /**
     * Ondes semees le long du trajet de la tete pendant ce tick (de la position precedente a {@code (x, y, z)}), une tous
     * les {@link #ONDE_PAS} blocs ; chacune est datee de l'instant ou la tete DESSINEE y passe (le rendu interpole la balle
     * entre sa position du tick precedent et celle-ci : le point t du trajet est atteint a maintenant + t), donc une onde
     * n'apparait jamais devant le projectile et leurs expansions sont decalees, sans a-coup.
     */
    private static void ondesTrajet(int i, double x, double y, double z) {
        double px = bHx[i], py = bHy[i], pz = bHz[i];
        double dx = x - px, dy = y - py, dz = z - pz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        bHx[i] = x;
        bHy[i] = y;
        bHz[i] = z;
        if (len < 1.0E-4) return;
        float ux = (float) (dx / len), uy = (float) (dy / len), uz = (float) (dz / len);
        int c = lueur(Armes.RAYON, bPap[i]), k = coeur(Armes.RAYON, bPap[i]);
        float big = bPap[i] > 0 ? 1.2f : 1f;
        float now = maintenant();
        double d = ONDE_PAS - bOnde[i];
        int n = 0;
        while (d <= len && n++ < 8) {
            double t = d / len;
            float t0 = now + (float) t;
            ondeAjout(px + dx * t, py + dy * t, pz + dz * t, ux, uy, uz, 0.1f * big, 0.62f * big, 7, c, 0.95f, t0);
            // un cercle sur deux double d'un liseré clair : le battement de l'onde
            if ((n & 1) == 0) ondeAjout(px + dx * t, py + dy * t, pz + dz * t, ux, uy, uz, 0.06f * big, 0.34f * big, 5, k, 0.55f, t0);
            d += ONDE_PAS;
        }
        bOnde[i] = len - (d - ONDE_PAS);
    }

    /**
     * Eclair de Mk II : trait droit et tres brillant du canon au point touche (calcule cote client sur le vrai trajet de la
     * balle, dispersion comprise), quasi instantane comme dans CoD ; il s'eteint en quelques ticks.
     */
    public static void trait(double sx, double sy, double sz, double ex, double ey, double ez, int pap, LivingEntity tireur) {
        decalageCanon(tireur);
        int c = lueur(Armes.MK2, pap), k = coeur(Armes.MK2, pap);
        float now = maintenant();
        double ox = sx + DEC[0], oy = sy + DEC[1], oz = sz + DEC[2];
        arcAjout(ARC_TRAIT, ox, oy, oz, ex, ey, ez, now, 5f, pap > 0 ? 1.25f : 1f, c, k);
        lueurAjout(L_FLASH, ox, oy, oz, 0.3f, 0.6f, 3, c, 0.6f, now);
        lueurAjout(L_SOL, ox, solSous(ox, oy, oz, 3), oz, 1.4f, 1.4f, 4, c, 0.3f, now);
    }

    /** Impact d'un rayon (eclaboussure, eclair). */
    public static void impact(Impact m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double x = m.x(), y = m.y(), z = m.z();
        byte type = m.arme();
        int pap = m.pap();
        // la balle suivie la plus proche s'arrete ici (sinon elle continuerait le temps que le serveur la retire)
        int bi = arreterBalle(type, x, y, z);
        int c = lueur(type, pap), k = coeur(type, pap);
        float now = maintenant();
        if (type == Armes.RAYON) {
            float r = Math.max(1.0f, m.rayon() / 10f);
            // salve d'ondes a l'impact (Black Ops 2) : cercles orientes sur le trajet de la balle s'il est connu, plus des
            // cercles face a la camera (lisibles sous tous les angles)
            if (bi >= 0) {
                Vec3 v = bE[bi].getDeltaMovement();
                double vl = v.length();
                if (vl > 1.0E-6) {
                    float ux = (float) (v.x / vl), uy = (float) (v.y / vl), uz = (float) (v.z / vl);
                    ondeAjout(x, y, z, ux, uy, uz, 0.2f, r * 1.7f, 8, c, 1f, now);
                    ondeAjout(x, y, z, ux, uy, uz, 0.1f, r * 1.05f, 6, k, 0.9f, now);
                    ondeAjout(x, y, z, ux, uy, uz, 0.3f, r * 2.3f, 10, c, 0.7f, now + 2);
                }
            }
            lueurAjout(L_ONDE, x, y, z, 0.25f, r * 1.55f, 8, c, 0.9f, now);
            lueurAjout(L_ONDE, x, y, z, 0.2f, r * 2.0f, 10, c, 0.55f, now + 2);
            lueurAjout(L_FLASH, x, y, z, r * 0.7f, r * 1.4f, 7, c, 0.95f, now);
            lueurAjout(L_FLASH, x, y, z, r * 0.3f, r * 0.55f, 4, k, 1f, now);
            lueurAjout(L_ETOILE, x, y, z, r * 1.2f, r * 0.7f, 4, k, 0.75f, now);
            lueurAjout(L_ANNEAU, x, y, z, 0.3f, r * 1.25f, 8, c, 0.9f, now);
            lueurAjout(L_ANNEAU, x, y, z, 0.2f, r * 0.8f, 6, k, 0.6f, now);
            double sol = solSous(x, y + 0.2, z, 3);
            lueurAjout(L_SOL, x, sol, z, r * 1.8f, r * 2.1f, 16, c, 0.6f, now);
            if (Math.abs(sol - y) < 0.6) lueurAjout(L_ANNEAU_SOL, x, sol, z, 0.4f, r * 1.4f, 9, c, 0.6f, now);
            for (int i = 0; i < 26; i++) particuleAleatoire(P_ETINCELLE, x, y, z, 0.25f + RNG.nextFloat() * 0.4f, 0.05f, 8 + RNG.nextInt(8), c, 0.06f, 0.04f, 0.88f);
            for (int i = 0; i < 12; i++) particuleAleatoire(P_GOUTTE, x, y, z, 0.08f + RNG.nextFloat() * 0.16f, 0.16f + RNG.nextFloat() * 0.12f, 12 + RNG.nextInt(10), c, 0.0f, 0.02f, 0.9f);
            for (int i = 0; i < 14; i++) particuleAleatoire(P_POUSSIERE, x, y, z, 0.03f + RNG.nextFloat() * 0.06f, 0.07f + RNG.nextFloat() * 0.06f, 16 + RNG.nextInt(14), k, -0.004f, 0, 0.92f);
            son(Sons.RAYON_IMPACT.get(), x, y, z, 1.0f, (pap > 0 ? 0.86f : 1.0f) + RNG.nextFloat() * 0.12f);
        } else if (type == Armes.MK2) {
            lueurAjout(L_FLASH, x, y, z, 0.6f, 1.2f, 5, c, 0.95f, now);
            lueurAjout(L_FLASH, x, y, z, 0.25f, 0.45f, 3, k, 1f, now);
            lueurAjout(L_ETOILE, x, y, z, 0.8f, 0.4f, 3, k, 0.6f, now);
            lueurAjout(L_SOL, x, solSous(x, y + 0.2, z, 2.5), z, 1.2f, 1.2f, 6, c, 0.3f, now);
            for (int i = 0; i < 12; i++) particuleAleatoire(P_ETINCELLE, x, y, z, 0.2f + RNG.nextFloat() * 0.35f, 0.04f, 6 + RNG.nextInt(6), c, 0.05f, 0.04f, 0.86f);
            son(Sons.MK2_IMPACT.get(), x, y, z, 0.7f, 0.95f + RNG.nextFloat() * 0.15f);
        } else {
            lueurAjout(L_FLASH, x, y, z, 0.6f, 1.4f, 5, c, 0.9f, now);
            lueurAjout(L_SOL, x, solSous(x, y + 0.2, z, 3), z, 2.0f, 2.2f, 8, c, 0.4f, now);
            for (int i = 0; i < 16; i++) particuleAleatoire(P_ETINCELLE, x, y, z, 0.25f + RNG.nextFloat() * 0.4f, 0.04f, 6 + RNG.nextInt(8), k, 0.05f, 0.04f, 0.86f);
            for (int a = 0; a < 3; a++) {
                double dx = (RNG.nextDouble() - 0.5) * 2.2, dy = RNG.nextDouble() * 1.2, dz = (RNG.nextDouble() - 0.5) * 2.2;
                arcAjout(ARC_MICRO, x, y, z, x + dx, y + dy, z + dz, now, 4, 0.5f, c, k);
            }
            son(Sons.FOUDRE_ARC.get(), x, y, z, 1.0f, 1.0f);
        }
    }

    /** Foudre en chaine (Wunderwaffe). */
    public static void chaine(Chaine m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        int pap = m.pap();
        int c = lueur(Armes.DG2, pap), k = coeur(Armes.DG2, pap);
        float now = maintenant();
        Entity tireur = lv.getEntity(m.tireur());
        double ox, oy, oz;
        if (tireur instanceof LivingEntity le) {
            decalageCanon(le);
            Vec3 o = le.getEyePosition();
            ox = o.x + DEC[0];
            oy = o.y + DEC[1];
            oz = o.z + DEC[2];
            if (le == mc.player) {
                flashEcran = pap > 0 ? 0.20f : 0.17f;
                flashCoul = k;
            }
        } else {
            ox = m.ix();
            oy = m.iy() + 1.5;
            oz = m.iz();
        }
        // eclair du canon, tonnerre
        lueurAjout(L_FLASH, ox, oy, oz, 0.6f, 1.3f, 4, c, 0.9f, now);
        lueurAjout(L_ETOILE, ox, oy, oz, 1.0f, 0.5f, 3, k, 0.7f, now);
        lueurAjout(L_SOL, ox, solSous(ox, oy, oz, 3), oz, 3.0f, 3.2f, 6, c, 0.35f, now);
        son(Sons.FOUDRE_TONNERRE.get(), ox, oy, oz, 2.6f, (pap > 0 ? 0.88f : 1.0f) + RNG.nextFloat() * 0.08f);
        int n = m.n();
        double ax = ox, ay = oy, az = oz;
        boolean bloc = (m.drapeaux() & Chaine.BLOC) != 0;
        if (n == 0 || bloc) {
            // premier eclair jusqu'au point d'impact
            arcAjout(ARC_PRINCIPAL, ox, oy, oz, m.ix(), m.iy(), m.iz(), now, 7, pap > 0 ? 1.25f : 1.0f, c, k);
            ax = m.ix();
            ay = m.iy();
            az = m.iz();
        }
        for (int i = 0; i < n; i++) {
            double tx = m.pos()[i * 3], ty = m.pos()[i * 3 + 1], tz = m.pos()[i * 3 + 2];
            float t = now + fr.heraliz.wonder.Calculs.arriveeArc(i);
            boolean premier = i == 0 && !bloc;
            arcAjout(premier ? ARC_PRINCIPAL : ARC_CHAINE, ax, ay, az, tx, ty, tz, t, premier ? 7 : 8, pap > 0 ? 1.2f : 1.0f, c, k);
            // une seconde branche plus fine sur le meme trajet : l'eclair « double » des Wunderwaffe
            arcAjout(ARC_MICRO, ax, ay, az, tx, ty, tz, t + 0.5f, 5, 1.6f, c, k);
            if (i == 0) arriveeArc(lv, tx, ty, tz, m.ids()[i], i, (byte) pap);
            else evenement(t, tx, ty, tz, m.ids()[i], i, (byte) pap);
            ax = tx;
            ay = ty;
            az = tz;
        }
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
        if (flashEcran > 0) flashEcran = flashEcran < 0.01f ? 0 : flashEcran * 0.5f;
        tickBalles(mc.level);
        tickEvenements(mc.level, now);
        tickElectrocutes(mc.level, now);
        tickParticules();
        // arcs et lueurs finis : compactage
        int w = 0;
        for (int i = 0; i < nA; i++) if (now - aT0[i] <= aDur[i]) copierArc(i, w++);
        nA = w;
        w = 0;
        for (int i = 0; i < nL; i++) if (now - lT0[i] <= lDur[i]) copierLueur(i, w++);
        nL = w;
        w = 0;
        for (int i = 0; i < nO; i++) if (now - oT0[i] <= oDur[i]) copierOnde(i, w++);
        nO = w;
    }

    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

    private static void tickBalles(ClientLevel lv) {
        for (int i = 0; i < nB; i++) {
            Entity b = bE[i];
            if (!bArret[i]) {
                if (b.isRemoved()) {
                    arret(i, b.getX(), b.getY(), b.getZ());
                } else {
                    // collision prevue avec un bloc sur le trajet du tick (le serveur confirmera par un paquet d'impact)
                    double x0 = b.xo, y0 = b.yo, z0 = b.zo, x1 = b.getX(), y1 = b.getY(), z1 = b.getZ();
                    double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
                    int pas = Math.min(60, (int) (len * 2) + 1);
                    boolean stop = false;
                    for (int s = 1; s <= pas; s++) {
                        double t = s / (double) pas;
                        double x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t, z = z0 + (z1 - z0) * t;
                        if (dansBloc(lv, x, y, z)) {
                            double tt = (s - 0.5) / pas;
                            arret(i, x0 + (x1 - x0) * tt, y0 + (y1 - y0) * tt, z0 + (z1 - z0) * tt);
                            stop = true;
                            break;
                        }
                    }
                    if (!stop) {
                        double f = facteurCanon(i, x1, y1, z1);
                        pousserTrainee(i, x1 + bOx[i] * f, y1 + bOy[i] * f, z1 + bOz[i] * f);
                        if (bType[i] == Armes.RAYON) {
                            ondesTrajet(i, x1 + bOx[i] * f, y1 + bOy[i] * f, z1 + bOz[i] * f);
                            if ((tick & 1) == 0) bSol[i] = solSous(x1, y1, z1, 4.5);
                            int c = coeur(Armes.RAYON, bPap[i]);
                            for (int k = 0; k < 2; k++) {
                                particule(P_POUSSIERE, x1 + (RNG.nextDouble() - 0.5) * 0.25, y1 + (RNG.nextDouble() - 0.5) * 0.25, z1 + (RNG.nextDouble() - 0.5) * 0.25,
                                        (RNG.nextFloat() - 0.5f) * 0.02f, (RNG.nextFloat() - 0.5f) * 0.02f, (RNG.nextFloat() - 0.5f) * 0.02f,
                                        0.05f + RNG.nextFloat() * 0.05f, 8 + RNG.nextInt(6), c, 0, 0.9f);
                            }
                        } else if ((tick & 1) == 0) {
                            particule(P_ETINCELLE, x1, y1, z1, (RNG.nextFloat() - 0.5f) * 0.1f, (RNG.nextFloat() - 0.5f) * 0.1f, (RNG.nextFloat() - 0.5f) * 0.1f,
                                    0.03f, 4, lueur(Armes.MK2, bPap[i]), 0.02f, 0.85f);
                        }
                        continue;
                    }
                }
            }
            // balle arretee : la trainee se resorbe sur le point d'arret, puis on libere la place
            pousserTrainee(i, bAx[i], bAy[i], bAz[i]);
            if (++bFin[i] > TRAINEE) {
                retirerBalle(i);
                i--;
            }
        }
    }

    private static double facteurCanon(int i, double x, double y, double z) {
        double dx = x - bSx[i], dy = y - bSy[i], dz = z - bSz[i];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double conv = bType[i] == Armes.RAYON ? 9.0 : 6.0;
        return Math.max(0, 1 - d / conv);
    }

    private static boolean dansBloc(ClientLevel lv, double x, double y, double z) {
        MP.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockState s = lv.getBlockState(MP);
        if (s.isAir()) return false;
        VoxelShape sh = s.getCollisionShape(lv, MP);
        if (sh.isEmpty()) return false;
        double lx = x - MP.getX(), ly = y - MP.getY(), lz = z - MP.getZ();
        return lx >= sh.min(net.minecraft.core.Direction.Axis.X) && lx <= sh.max(net.minecraft.core.Direction.Axis.X)
                && ly >= sh.min(net.minecraft.core.Direction.Axis.Y) && ly <= sh.max(net.minecraft.core.Direction.Axis.Y)
                && lz >= sh.min(net.minecraft.core.Direction.Axis.Z) && lz <= sh.max(net.minecraft.core.Direction.Axis.Z);
    }

    /** Hauteur du sol sous le point (au plus {@code max} blocs plus bas), NaN si rien. Sans allocation. */
    private static double solSous(double x, double y, double z, double max) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return Double.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
        for (int d = 0; d <= (int) Math.ceil(max); d++) {
            MP.set(bx, by - d, bz);
            BlockState s = lv.getBlockState(MP);
            if (s.isAir()) continue;
            VoxelShape sh = s.getCollisionShape(lv, MP);
            if (sh.isEmpty()) continue;
            double top = MP.getY() + sh.max(net.minecraft.core.Direction.Axis.Y);
            if (top <= y + 0.01 && y - top <= max) return top;
        }
        return Double.NaN;
    }

    private static void arret(int i, double x, double y, double z) {
        bArret[i] = true;
        double f = facteurCanon(i, x, y, z);
        bAx[i] = x + bOx[i] * f;
        bAy[i] = y + bOy[i] * f;
        bAz[i] = z + bOz[i] * f;
        if (bType[i] == Armes.RAYON) ondesTrajet(i, bAx[i], bAy[i], bAz[i]);
    }

    /** Arrete la balle suivie la plus proche du point (rend son indice, ou -1). */
    private static int arreterBalle(byte type, double x, double y, double z) {
        int best = -1;
        double bd = 12 * 12;
        for (int i = 0; i < nB; i++) {
            if (bType[i] != type || bArret[i]) continue;
            Entity b = bE[i];
            double dx = b.getX() - x, dy = b.getY() - y, dz = b.getZ() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (best >= 0) {
            if (type == Armes.RAYON && !bArret[best]) ondesTrajet(best, x, y, z);
            bArret[best] = true;
            bAx[best] = x;
            bAy[best] = y;
            bAz[best] = z;
        }
        return best;
    }

    private static void pousserTrainee(int i, double x, double y, double z) {
        int base = i * TRAINEE * 3;
        int n = Math.min(bTrN[i], TRAINEE - 1);
        System.arraycopy(bTr, base, bTr, base + 3, n * 3);
        bTr[base] = x;
        bTr[base + 1] = y;
        bTr[base + 2] = z;
        bTrN[i] = n + 1;
    }

    private static void retirerBalle(int i) {
        int d = --nB;
        if (i != d) {
            bE[i] = bE[d];
            bType[i] = bType[d];
            bPap[i] = bPap[d];
            bOx[i] = bOx[d];
            bOy[i] = bOy[d];
            bOz[i] = bOz[d];
            bSx[i] = bSx[d];
            bSy[i] = bSy[d];
            bSz[i] = bSz[d];
            System.arraycopy(bTr, d * TRAINEE * 3, bTr, i * TRAINEE * 3, TRAINEE * 3);
            bTrN[i] = bTrN[d];
            bFin[i] = bFin[d];
            bArret[i] = bArret[d];
            bAx[i] = bAx[d];
            bAy[i] = bAy[d];
            bAz[i] = bAz[d];
            bSol[i] = bSol[d];
            bHx[i] = bHx[d];
            bHy[i] = bHy[d];
            bHz[i] = bHz[d];
            bOnde[i] = bOnde[d];
        }
        bE[d] = null;
    }

    private static void evenement(float t, double x, double y, double z, int id, int rang, byte pap) {
        if (nV >= MAX_V) return;
        int i = nV++;
        vT[i] = t;
        vX[i] = x;
        vY[i] = y;
        vZ[i] = z;
        vId[i] = id;
        vRang[i] = rang;
        vPap[i] = pap;
    }

    private static void tickEvenements(ClientLevel lv, float now) {
        int w = 0;
        for (int i = 0; i < nV; i++) {
            if (vT[i] <= now + 0.5f) {
                arriveeArc(lv, vX[i], vY[i], vZ[i], vId[i], vRang[i], vPap[i]);
            } else {
                vT[w] = vT[i];
                vX[w] = vX[i];
                vY[w] = vY[i];
                vZ[w] = vZ[i];
                vId[w] = vId[i];
                vRang[w] = vRang[i];
                vPap[w] = vPap[i];
                w++;
            }
        }
        nV = w;
    }

    /** L'arc atteint sa cible : flash bleu-blanc, lumiere au sol, etincelles, crepitement, debut de l'electrocution. */
    private static void arriveeArc(ClientLevel lv, double x, double y, double z, int id, int rang, byte pap) {
        float now = maintenant();
        int c = lueur(Armes.DG2, pap), k = coeur(Armes.DG2, pap);
        lueurAjout(L_FLASH, x, y, z, 0.9f, 2.0f, 6, c, 0.95f, now);
        lueurAjout(L_FLASH, x, y, z, 0.35f, 0.7f, 3, k, 1f, now);
        lueurAjout(L_ETOILE, x, y, z, 1.3f, 0.7f, 3, k, 0.7f, now);
        lueurAjout(L_SOL, x, solSous(x, y, z, 3), z, 2.4f, 2.6f, 9, c, 0.38f, now);
        for (int i = 0; i < 10; i++) particuleAleatoire(P_ETINCELLE, x, y, z, 0.2f + RNG.nextFloat() * 0.35f, 0.04f, 6 + RNG.nextInt(7), k, 0.05f, 0.04f, 0.86f);
        if (rang < 4 || rang % 3 == 0) son(Sons.FOUDRE_ARC.get(), x, y, z, 1.1f, 0.85f + RNG.nextFloat() * 0.35f);
        if (rang < 2) son(Sons.ELECTROCUTION.get(), x, y, z, 0.8f, 0.9f + RNG.nextFloat() * 0.2f);
        Entity e = lv.getEntity(id);
        electrocuter(id, e, x, y, z, now, pap);
    }

    private static void electrocuter(int id, Entity e, double x, double y, double z, float now, byte pap) {
        int i = -1;
        for (int j = 0; j < nE; j++) if (eId[j] == id) i = j;
        if (i < 0) {
            if (nE >= MAX_E) return;
            i = nE++;
        }
        eId[i] = id;
        eT0[i] = now;
        eDur[i] = 34;
        ePap[i] = pap;
        eX[i] = e != null ? e.getX() : x;
        eY[i] = e != null ? e.getY() : y - 0.9;
        eZ[i] = e != null ? e.getZ() : z;
        eH[i] = e != null ? e.getBbHeight() : 1.9f;
        eW[i] = e != null ? e.getBbWidth() : 0.6f;
        if (nE > picE) picE = nE;
    }

    private static void tickElectrocutes(ClientLevel lv, float now) {
        int w = 0;
        for (int i = 0; i < nE; i++) {
            float age = now - eT0[i];
            if (age > eDur[i]) continue;
            Entity e = lv.getEntity(eId[i]);
            if (e != null && !e.isRemoved()) {
                eX[i] = e.getX();
                eY[i] = e.getY();
                eZ[i] = e.getZ();
            }
            float k = age / eDur[i];
            int c = lueur(Armes.DG2, ePap[i]), co = coeur(Armes.DG2, ePap[i]);
            double h = eH[i], wd = eW[i];
            // arcs qui courent sur le corps, de moins en moins souvent
            if (RNG.nextFloat() < 0.75f * (1 - k) + 0.1f) {
                double x0 = eX[i] + (RNG.nextDouble() - 0.5) * wd, y0 = eY[i] + RNG.nextDouble() * h, z0 = eZ[i] + (RNG.nextDouble() - 0.5) * wd;
                double x1 = eX[i] + (RNG.nextDouble() - 0.5) * wd * 1.4, y1 = eY[i] + RNG.nextDouble() * h, z1 = eZ[i] + (RNG.nextDouble() - 0.5) * wd * 1.4;
                arcAjout(ARC_MICRO, x0, y0, z0, x1, y1, z1, now, 2.5f, 0.55f, c, co);
            }
            if (RNG.nextFloat() < 0.6f * (1 - k)) {
                particule(P_ETINCELLE, eX[i] + (RNG.nextDouble() - 0.5) * wd, eY[i] + RNG.nextDouble() * h, eZ[i] + (RNG.nextDouble() - 0.5) * wd,
                        (RNG.nextFloat() - 0.5f) * 0.25f, RNG.nextFloat() * 0.2f, (RNG.nextFloat() - 0.5f) * 0.25f, 0.03f, 5 + RNG.nextInt(4), co, 0.05f, 0.88f);
            }
            // fumee de chair grillee, plus dense a la fin
            if ((tick + i) % 2 == 0) {
                particule(P_FUMEE, eX[i] + (RNG.nextDouble() - 0.5) * wd, eY[i] + h * (0.3 + RNG.nextDouble() * 0.7), eZ[i] + (RNG.nextDouble() - 0.5) * wd,
                        (RNG.nextFloat() - 0.5f) * 0.01f, 0.025f + RNG.nextFloat() * 0.02f, (RNG.nextFloat() - 0.5f) * 0.01f,
                        0.28f + RNG.nextFloat() * 0.22f, 26 + RNG.nextInt(16), 0x8A8A92, -0.0008f, 0.96f);
            }
            if (k < 0.5f && (tick + i) % 3 == 0) lueurAjout(L_FLASH, eX[i], eY[i] + h * 0.55, eZ[i], 0.5f, 0.9f, 3, c, 0.45f * (1 - k), now);
            copierElectro(i, w++);
        }
        nE = w;
    }

    private static void copierElectro(int i, int w) {
        if (i == w) return;
        eId[w] = eId[i];
        eT0[w] = eT0[i];
        eDur[w] = eDur[i];
        eH[w] = eH[i];
        eW[w] = eW[i];
        eX[w] = eX[i];
        eY[w] = eY[i];
        eZ[w] = eZ[i];
        ePap[w] = ePap[i];
    }

    // ================================================================== particules
    private static void particuleAleatoire(byte genre, double x, double y, double z, float vitesse, float taille, int vie, int coul, float grav,
                                           float monte, float frott) {
        double a = RNG.nextDouble() * Math.PI * 2, u = RNG.nextDouble() * 2 - 1, s = Math.sqrt(1 - u * u);
        particule(genre, x, y, z, (float) (Math.cos(a) * s * vitesse), (float) (u * vitesse) + monte, (float) (Math.sin(a) * s * vitesse), taille,
                vie, coul, grav, frott);
    }

    private static void particule(byte genre, double x, double y, double z, float vx, float vy, float vz, float taille, int vie, int coul, float grav,
                                  float frott) {
        if (nP >= MAX_P) return;
        int i = nP++;
        pX[i] = pPx[i] = x;
        pY[i] = pPy[i] = y;
        pZ[i] = pPz[i] = z;
        pVx[i] = vx;
        pVy[i] = vy;
        pVz[i] = vz;
        pTaille[i] = taille;
        pVie[i] = 0;
        pVieMax[i] = vie;
        pCoul[i] = coul;
        pGrav[i] = grav;
        pFrott[i] = frott;
        pGenre[i] = genre;
        if (nP > picP) picP = nP;
    }

    private static void tickParticules() {
        int w = 0, fumee = 0;
        for (int i = 0; i < nP; i++) {
            if (++pVie[i] >= pVieMax[i]) continue;
            pPx[i] = pX[i];
            pPy[i] = pY[i];
            pPz[i] = pZ[i];
            pVx[i] *= pFrott[i];
            pVy[i] = pVy[i] * pFrott[i] - pGrav[i];
            pVz[i] *= pFrott[i];
            pX[i] += pVx[i];
            pY[i] += pVy[i];
            pZ[i] += pVz[i];
            if (pGenre[i] == P_FUMEE) fumee++;
            if (i != w) {
                pX[w] = pX[i];
                pY[w] = pY[i];
                pZ[w] = pZ[i];
                pPx[w] = pPx[i];
                pPy[w] = pPy[i];
                pPz[w] = pPz[i];
                pVx[w] = pVx[i];
                pVy[w] = pVy[i];
                pVz[w] = pVz[i];
                pVie[w] = pVie[i];
                pVieMax[w] = pVieMax[i];
                pTaille[w] = pTaille[i];
                pGrav[w] = pGrav[i];
                pFrott[w] = pFrott[i];
                pCoul[w] = pCoul[i];
                pGenre[w] = pGenre[i];
            }
            w++;
        }
        nP = w;
        nFumee = fumee;
    }

    // ================================================================== arcs, lueurs
    private static void arcAjout(byte genre, double ax, double ay, double az, double bx, double by, double bz, float t0, float dur, float larg, int coul,
                                 int coeur) {
        if (nA >= MAX_A) return;
        int i = nA++;
        aAx[i] = ax;
        aAy[i] = ay;
        aAz[i] = az;
        aBx[i] = bx;
        aBy[i] = by;
        aBz[i] = bz;
        aT0[i] = t0;
        aDur[i] = dur;
        aLarg[i] = larg;
        aSeed[i] = RNG.nextInt();
        aCoul[i] = coul;
        aCoeur[i] = coeur;
        aGenre[i] = genre;
        if (nA > picA) picA = nA;
    }

    private static void copierArc(int i, int w) {
        if (i == w) return;
        aAx[w] = aAx[i];
        aAy[w] = aAy[i];
        aAz[w] = aAz[i];
        aBx[w] = aBx[i];
        aBy[w] = aBy[i];
        aBz[w] = aBz[i];
        aT0[w] = aT0[i];
        aDur[w] = aDur[i];
        aLarg[w] = aLarg[i];
        aSeed[w] = aSeed[i];
        aCoul[w] = aCoul[i];
        aCoeur[w] = aCoeur[i];
        aGenre[w] = aGenre[i];
    }

    private static void lueurAjout(byte genre, double x, double y, double z, float r0, float r1, float dur, int coul, float alpha, float t0) {
        if (nL >= MAX_L || Double.isNaN(y)) return;
        int i = nL++;
        lGenre[i] = genre;
        lX[i] = x;
        lY[i] = y;
        lZ[i] = z;
        lR0[i] = r0;
        lR1[i] = r1;
        lDur[i] = dur;
        lCoul[i] = coul;
        lA[i] = alpha;
        lT0[i] = t0 == 0 ? maintenant() : t0;
        if (nL > picL) picL = nL;
    }

    private static void copierLueur(int i, int w) {
        if (i == w) return;
        lGenre[w] = lGenre[i];
        lX[w] = lX[i];
        lY[w] = lY[i];
        lZ[w] = lZ[i];
        lR0[w] = lR0[i];
        lR1[w] = lR1[i];
        lDur[w] = lDur[i];
        lCoul[w] = lCoul[i];
        lA[w] = lA[i];
        lT0[w] = lT0[i];
    }

    // ================================================================== origine du tir (canon)
    /** Decalage oeil -> canon du tireur dans {@link #DEC} (vue a la premiere personne : position calculee par TaCZ). */
    private static void decalageCanon(LivingEntity tireur) {
        DEC[0] = DEC[1] = DEC[2] = 0;
        if (tireur == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (tireur == mc.player && ClientWonder.tacz && TaczClient.premierePersonne()
                && TaczClient.decalageCanon(mc.gameRenderer.getMainCamera(), DEC)) return;
        float yaw = tireur.getYRot() * Mth.DEG_TO_RAD, pitch = tireur.getXRot() * Mth.DEG_TO_RAD;
        float cp = Mth.cos(pitch);
        float lx = -Mth.sin(yaw) * cp, ly = -Mth.sin(pitch), lz = Mth.cos(yaw) * cp;
        float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        DEC[0] = lx * 0.6f + rx * 0.32f;
        DEC[1] = ly * 0.6f - 0.24f;
        DEC[2] = lz * 0.6f + rz * 0.32f;
    }

    // ================================================================== sons
    private static void son(SoundEvent s, double x, double y, double z, float vol, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.playLocalSound(x, y, z, s, SoundSource.PLAYERS, vol, pitch, false);
    }

    // ================================================================== rendu
    private static VertexConsumer VC;
    private static Matrix4f M;
    private static double CX, CY, CZ;
    private static float LX, LY, LZ, UX, UY, UZ;

    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (nB == 0 && nA == 0 && nL == 0 && nP == 0 && nO == 0) return;
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
        VC = buf.getBuffer(FxTypes.LUMIERE);
        dessinerLueurs(now);
        dessinerOndes(now);
        dessinerBalles(partial);
        dessinerArcs(now);
        dessinerParticules(partial, false);
        buf.endBatch(FxTypes.LUMIERE);
        if (nFumee > 0) {
            VC = buf.getBuffer(FxTypes.FUMEE);
            dessinerParticules(partial, true);
            buf.endBatch(FxTypes.FUMEE);
        }
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

    /** Carre face a la camera, tourne de {@code rot}. */
    private static void panneau(double x, double y, double z, float r, float rot, int cell, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float px = (float) (x - CX), py = (float) (y - CY), pz = (float) (z - CZ);
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        // axes du panneau : gauche et haut de la camera, tournes
        float ax = LX * cs + UX * sn, ay = LY * cs + UY * sn, az = LZ * cs + UZ * sn;
        float bx = -LX * sn + UX * cs, by = -LY * sn + UY * cs, bz = -LZ * sn + UZ * cs;
        float u0 = FxTypes.u0(cell), v0 = FxTypes.v0(cell), u1 = FxTypes.u1(cell), v1 = FxTypes.v1(cell);
        sommet(px - ax - bx, py - ay - by, pz - az - bz, coul, al, u0, v1);
        sommet(px + ax - bx, py + ay - by, pz + az - bz, coul, al, u1, v1);
        sommet(px + ax + bx, py + ay + by, pz + az + bz, coul, al, u1, v0);
        sommet(px - ax + bx, py - ay + by, pz - az + bz, coul, al, u0, v0);
    }

    /** Disque horizontal (lumiere portee au sol, onde au sol). */
    private static void disque(double x, double y, double z, float r, float rot, int cell, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float px = (float) (x - CX), py = (float) (y - CY), pz = (float) (z - CZ);
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        float u0 = FxTypes.u0(cell), v0 = FxTypes.v0(cell), u1 = FxTypes.u1(cell), v1 = FxTypes.v1(cell);
        sommet(px - cs + sn, py, pz - sn - cs, coul, al, u0, v0);
        sommet(px - cs - sn, py, pz - sn + cs, coul, al, u0, v1);
        sommet(px + cs - sn, py, pz + sn + cs, coul, al, u1, v1);
        sommet(px + cs + sn, py, pz + sn - cs, coul, al, u1, v0);
    }

    /**
     * Ruban face a la camera de A a B (coordonnees relatives a la camera), largeurs w0 / w1 aux deux bouts ; la texture
     * varie en travers (v) et s'etire en long (u).
     */
    private static void ruban(float ax, float ay, float az, float bx, float by, float bz, float w0, float w1, int cell, int coul, float a0, float a1) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        // cote = d x (milieu - camera) ; la camera est a l'origine
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

    private static void rubanMonde(double ax, double ay, double az, double bx, double by, double bz, float w0, float w1, int cell, int coul, float a0, float a1) {
        ruban((float) (ax - CX), (float) (ay - CY), (float) (az - CZ), (float) (bx - CX), (float) (by - CY), (float) (bz - CZ), w0, w1, cell, coul, a0, a1);
    }

    private static void dessinerLueurs(float now) {
        for (int i = 0; i < nL; i++) {
            float age = now - lT0[i];
            if (age < 0 || age > lDur[i]) continue;
            float k = age / lDur[i];
            float e = 1 - (1 - k) * (1 - k);
            float r = lR0[i] + (lR1[i] - lR0[i]) * e;
            float a = lA[i] * (1 - k);
            if (lGenre[i] == L_FLASH || lGenre[i] == L_ETOILE || lGenre[i] == L_ANNEAU || lGenre[i] == L_ONDE) {
                double dx = lX[i] - CX, dy = lY[i] - CY, dz = lZ[i] - CZ;
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                a *= Mth.clamp(d / (r * 2.2f + 0.5f), 0.18f, 1f);
            }
            switch (lGenre[i]) {
                case L_FLASH -> panneau(lX[i], lY[i], lZ[i], r, 0, FxTypes.LUEUR, lCoul[i], a * (1 - k));
                case L_ANNEAU -> panneau(lX[i], lY[i], lZ[i], r, 0, FxTypes.ANNEAU, lCoul[i], a);
                case L_SOL -> disque(lX[i], lY[i] + 0.03, lZ[i], r, 0, FxTypes.DISQUE, lCoul[i], a);
                case L_ETOILE -> panneau(lX[i], lY[i], lZ[i], r, now * 0.15f + i, FxTypes.ETOILE, lCoul[i], a);
                case L_ANNEAU_SOL -> disque(lX[i], lY[i] + 0.04, lZ[i], r, 0, FxTypes.ANNEAU, lCoul[i], a);
                case L_ONDE -> panneau(lX[i], lY[i], lZ[i], r, 0, FxTypes.ONDE, lCoul[i], a);
                default -> {
                }
            }
        }
    }

    /**
     * Ondes du Pistolet a rayons : cercle fin oriente (perpendiculaire au trajet) qui s'elargit vite et s'eteint. Vu par la
     * tranche, un cercle plat devient un trait invisible : un trait lumineux le long de son grand axe prend alors le relais.
     */
    private static void dessinerOndes(float now) {
        for (int i = 0; i < nO; i++) {
            float age = now - oT0[i];
            if (age < 0 || age > oDur[i]) continue;
            float k = age / oDur[i];
            float e = 1 - (1 - k) * (1 - k) * (1 - k);
            float r = oR0[i] + (oR1[i] - oR0[i]) * e;
            float a = oA[i] * (1 - k) * (1 - 0.4f * k);
            float px = (float) (oX[i] - CX), py = (float) (oY[i] - CY), pz = (float) (oZ[i] - CZ);
            float d = (float) Math.sqrt(px * px + py * py + pz * pz);
            // pres de l'oeil (bouche du canon en vue a la premiere personne) : l'onde ne voile pas l'ecran
            a *= Mth.clamp((d - 0.3f) / (r * 1.5f + 0.35f), 0f, 1f);
            if (a <= 0.004f) continue;
            cercle(px, py, pz, oDx[i], oDy[i], oDz[i], r, oCoul[i], a);
            float face = d > 1.0E-4f ? Math.abs((px * oDx[i] + py * oDy[i] + pz * oDz[i]) / d) : 1f;
            if (face < 0.45f) {
                // grand axe de l'ellipse a l'ecran = d x (point - camera)
                float mx = oDy[i] * pz - oDz[i] * py, my = oDz[i] * px - oDx[i] * pz, mz = oDx[i] * py - oDy[i] * px;
                float mn = (float) Math.sqrt(mx * mx + my * my + mz * mz);
                if (mn > 1.0E-6f) {
                    float s = r / mn;
                    float g = (1 - face / 0.45f);
                    float wd = 0.05f + r * 0.11f;
                    ruban(px - mx * s, py - my * s, pz - mz * s, px + mx * s, py + my * s, pz + mz * s, wd, wd, FxTypes.FAISCEAU, oCoul[i],
                            a * 0.95f * g, a * 0.95f * g);
                }
            }
        }
    }

    /** Cercle oriente : quad de cote 2r dans le plan perpendiculaire a (dx, dy, dz) (unitaire), centre relatif a la camera. */
    private static void cercle(float px, float py, float pz, float dx, float dy, float dz, float r, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float ux, uy, uz;
        if (Math.abs(dy) < 0.9f) {
            ux = -dz;
            uy = 0;
            uz = dx;
        } else {
            ux = 0;
            uy = dz;
            uz = -dy;
        }
        float n = r / (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux *= n;
        uy *= n;
        uz *= n;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        float u0 = FxTypes.u0(FxTypes.ONDE), v0 = FxTypes.v0(FxTypes.ONDE), u1 = FxTypes.u1(FxTypes.ONDE), v1 = FxTypes.v1(FxTypes.ONDE);
        sommet(px - ux - vx, py - uy - vy, pz - uz - vz, coul, al, u0, v1);
        sommet(px + ux - vx, py + uy - vy, pz + uz - vz, coul, al, u1, v1);
        sommet(px + ux + vx, py + uy + vy, pz + uz + vz, coul, al, u1, v0);
        sommet(px - ux + vx, py - uy + vy, pz - uz + vz, coul, al, u0, v0);
    }

    private static void dessinerBalles(float partial) {
        for (int i = 0; i < nB; i++) {
            byte type = bType[i];
            int pap = bPap[i];
            int c = lueur(type, pap), k = coeur(type, pap);
            float big = pap > 0 ? 1.2f : 1f;
            Entity b = bE[i];
            int base = i * TRAINEE * 3;
            double hx, hy, hz;
            float fin = 1f;
            if (bArret[i]) {
                hx = bAx[i];
                hy = bAy[i];
                hz = bAz[i];
                fin = Math.max(0, 1 - (bFin[i] + partial) / TRAINEE);
            } else {
                double x = Mth.lerp(partial, b.xo, b.getX()), y = Mth.lerp(partial, b.yo, b.getY()), z = Mth.lerp(partial, b.zo, b.getZ());
                double f = facteurCanon(i, x, y, z);
                hx = x + bOx[i] * f;
                hy = y + bOy[i] * f;
                hz = z + bOz[i] * f;
            }
            Vec3 v = b.getDeltaMovement();
            double vl = v.length();
            if (vl < 1.0E-6) continue;
            // un rayon lointain reste lisible : taille compensee avec la distance a la camera
            double ex = hx - CX, ey = hy - CY, ez = hz - CZ;
            big *= Mth.clamp((float) Math.sqrt(Math.sqrt(ex * ex + ey * ey + ez * ez) / 8.0), 1f, 2f);
            double dx = v.x / vl, dy = v.y / vl, dz = v.z / vl;
            // trainee : de la tete aux positions des ticks precedents
            int n = bTrN[i];
            double px = hx, py = hy, pz = hz;
            for (int s = 1; s < n; s++) {
                double qx = bTr[base + s * 3], qy = bTr[base + s * 3 + 1], qz = bTr[base + s * 3 + 2];
                float k0 = (s - 1f) / (TRAINEE - 1f), k1 = s / (TRAINEE - 1f);
                if (type == Armes.RAYON) {
                    rubanMonde(px, py, pz, qx, qy, qz, 0.3f * big * (1 - k0), 0.3f * big * (1 - k1), FxTypes.FAISCEAU, c, 0.38f * (1 - k0) * fin, 0.38f * (1 - k1) * fin);
                } else {
                    rubanMonde(px, py, pz, qx, qy, qz, 0.2f * big * (1 - k0), 0.2f * big * (1 - k1), FxTypes.FAISCEAU, c, 0.4f * (1 - k0) * fin, 0.4f * (1 - k1) * fin);
                }
                px = qx;
                py = qy;
                pz = qz;
            }
            if (bArret[i]) continue;
            if (type == Armes.RAYON) {
                // projectile d'energie : halo, gaine coloree, coeur blanc, lumiere portee au sol
                panneau(hx, hy, hz, 0.8f * big, 0, FxTypes.LUEUR, c, 0.26f);
                rubanMonde(hx - dx * 1.25, hy - dy * 1.25, hz - dz * 1.25, hx + dx * 0.3, hy + dy * 0.3, hz + dz * 0.3, 0.42f * big, 0.42f * big,
                        FxTypes.FAISCEAU, c, 0.15f, 0.7f);
                rubanMonde(hx - dx * 0.8, hy - dy * 0.8, hz - dz * 0.8, hx + dx * 0.15, hy + dy * 0.15, hz + dz * 0.15, 0.13f * big, 0.16f * big,
                        FxTypes.ARC, k, 0.5f, 1f);
                panneau(hx, hy, hz, 0.22f * big, 0, FxTypes.COEUR, k, 0.9f);
                double sol = bSol[i];
                if (!Double.isNaN(sol)) {
                    float h = (float) (hy - sol);
                    if (h >= 0 && h < 4.5f) disque(hx, sol + 0.03, hz, 1.7f * big, 0, FxTypes.DISQUE, c, 0.32f * (1 - h / 4.5f));
                }
            } else {
                // eclair quasi instantane : trait court et brillant derriere la tete
                double sx = hx - bSx[i], sy = hy - bSy[i], sz = hz - bSz[i];
                double parcouru = Math.sqrt(sx * sx + sy * sy + sz * sz);
                double L = Math.min(parcouru, 4.5);
                rubanMonde(hx - dx * L, hy - dy * L, hz - dz * L, hx, hy, hz, 0.12f * big, 0.3f * big, FxTypes.FAISCEAU, c, 0.0f, 0.8f);
                rubanMonde(hx - dx * L * 0.8, hy - dy * L * 0.8, hz - dz * L * 0.8, hx, hy, hz, 0.03f * big, 0.07f * big, FxTypes.ARC, k, 0.2f, 1f);
                panneau(hx, hy, hz, 0.42f * big, 0, FxTypes.LUEUR, c, 0.45f);
                panneau(hx, hy, hz, 0.12f * big, 0, FxTypes.COEUR, k, 0.9f);
            }
        }
    }

    private static final float[] PT = new float[(SEG + 1) * 3];

    private static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /** [-1, 1] */
    private static float h11(int x) {
        return (hash(x) & 0xFFFFFF) / (float) 0x7FFFFF - 1f;
    }

    private static void dessinerArcs(float now) {
        for (int i = 0; i < nA; i++) {
            float age = now - aT0[i];
            if (age < 0 || age > aDur[i]) continue;
            float k = age / aDur[i];
            float fondu = k < 0.35f ? 1f : 1f - (k - 0.35f) / 0.65f;
            int image = (int) (now / 1.5f);
            int s = aSeed[i] * 31 + image * 7919;
            float a = fondu * (0.62f + 0.38f * (h11(s) * 0.5f + 0.5f));
            float ax = (float) (aAx[i] - CX), ay = (float) (aAy[i] - CY), az = (float) (aAz[i] - CZ);
            float bx = (float) (aBx[i] - CX), by = (float) (aBy[i] - CY), bz = (float) (aBz[i] - CZ);
            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.05f) continue;
            float ix = dx / len, iy = dy / len, iz = dz / len;
            // base perpendiculaire (u, v)
            float rx = Math.abs(iy) < 0.9f ? 0 : 1, ry = Math.abs(iy) < 0.9f ? 1 : 0;
            float ux = iy * 0 - iz * ry, uy = iz * rx - ix * 0, uz = ix * ry - iy * rx;
            float un = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= un;
            uy /= un;
            uz /= un;
            float vx = iy * uz - iz * uy, vy = iz * ux - ix * uz, vz = ix * uy - iy * ux;
            byte g = aGenre[i];
            if (g == ARC_TRAIT) {
                float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
                float loin = Mth.clamp((float) Math.sqrt(Math.sqrt(mx * mx + my * my + mz * mz) / 9f), 1f, 2f) * aLarg[i];
                // trace laissee dans l'air : bien visible pendant 1,5 tick, puis elle s'eteint
                float f = k < 0.3f ? 1f : 1f - (k - 0.3f) / 0.7f;
                ruban(ax, ay, az, bx, by, bz, 0.1f * loin, 0.36f * loin * f, FxTypes.FAISCEAU, aCoul[i], 0.35f * f, 0.8f * f);
                ruban(ax, ay, az, bx, by, bz, 0.02f * loin, 0.07f * loin * f, FxTypes.ARC, aCoeur[i], 0.4f * f, 0.75f * f);
                // l'eclair lui-meme : 5 blocs de lumiere qui filent a 20 blocs par tick jusqu'a la cible
                float tete = Math.min(len, age * 20f + 1.5f), queue = Math.max(0f, tete - 5f);
                if (queue < len - 0.05f) {
                    float hx = ax + ix * tete, hy = ay + iy * tete, hz = az + iz * tete;
                    float qx = ax + ix * queue, qy = ay + iy * queue, qz = az + iz * queue;
                    ruban(qx, qy, qz, hx, hy, hz, 0.12f * loin, 0.42f * loin, FxTypes.FAISCEAU, aCoul[i], 0.2f, 0.95f);
                    ruban(qx, qy, qz, hx, hy, hz, 0.03f * loin, 0.1f * loin, FxTypes.ARC, aCoeur[i], 0.4f, 1f);
                }
                continue;
            }
            float amp = Math.min(1.1f, 0.1f * len + 0.12f) * (g == ARC_MICRO ? 0.6f : 1f);
            int seg = Mth.clamp((int) (len * 1.4f) + 4, 4, SEG);
            for (int p = 0; p <= seg; p++) {
                float t = p / (float) seg;
                float env = p == 0 || p == seg ? 0 : (float) Math.pow(Mth.sin((float) Math.PI * t), 0.75);
                float r1 = h11(s + p * 2) * amp * env, r2 = h11(s + p * 2 + 1) * amp * env;
                PT[p * 3] = ax + dx * t + ux * r1 + vx * r2;
                PT[p * 3 + 1] = ay + dy * t + uy * r1 + vy * r2;
                PT[p * 3 + 2] = az + dz * t + uz * r1 + vz * r2;
            }
            // de loin l'eclair reste lisible (CoD) : largeur compensee avec la distance a la camera
            float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
            float loin = Mth.clamp((float) Math.sqrt(Math.sqrt(mx * mx + my * my + mz * mz) / 9f), 1f, 2.2f);
            float larg = aLarg[i] * loin;
            float wGlow = (g == ARC_MICRO ? 0.18f : g == ARC_PRINCIPAL ? 0.6f : 0.5f) * larg;
            float wCore = (g == ARC_MICRO ? 0.045f : g == ARC_PRINCIPAL ? 0.12f : 0.1f) * larg;
            int c = aCoul[i], co = aCoeur[i];
            for (int p = 0; p < seg; p++) {
                // le premier eclair s'affine pres du canon (il ne bouche pas la vue du tireur)
                float m0 = g == ARC_PRINCIPAL ? Math.min(1f, 0.3f + p / (float) seg * 3f) : 1f;
                float m1 = g == ARC_PRINCIPAL ? Math.min(1f, 0.3f + (p + 1) / (float) seg * 3f) : 1f;
                float x0 = PT[p * 3], y0 = PT[p * 3 + 1], z0 = PT[p * 3 + 2], x1 = PT[p * 3 + 3], y1 = PT[p * 3 + 4], z1 = PT[p * 3 + 5];
                if (g != ARC_MICRO) ruban(x0, y0, z0, x1, y1, z1, wGlow * 2.6f * m0, wGlow * 2.6f * m1, FxTypes.FAISCEAU, c, 0.16f * a, 0.16f * a);
                ruban(x0, y0, z0, x1, y1, z1, wGlow * m0, wGlow * m1, FxTypes.FAISCEAU, c, 0.7f * a, 0.7f * a);
                ruban(x0, y0, z0, x1, y1, z1, wCore * m0, wCore * m1, FxTypes.ARC, co, a, a);
            }
            if (g == ARC_MICRO) continue;
            // branches : deux fourches courtes qui partent d'un point de l'eclair
            for (int br = 0; br < 2; br++) {
                int sb = s + 1000 + br * 97;
                int j = 1 + (hash(sb) & 0x7FFF) % Math.max(1, seg - 2);
                float bxs = PT[j * 3], bys = PT[j * 3 + 1], bzs = PT[j * 3 + 2];
                float lb = len * (0.12f + 0.15f * (h11(sb + 1) * 0.5f + 0.5f));
                float ddx = ix + (ux * h11(sb + 2) + vx * h11(sb + 3)) * 0.9f, ddy = iy + (uy * h11(sb + 2) + vy * h11(sb + 3)) * 0.9f,
                        ddz = iz + (uz * h11(sb + 2) + vz * h11(sb + 3)) * 0.9f;
                float dn = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                ddx /= dn;
                ddy /= dn;
                ddz /= dn;
                float qx = bxs, qy = bys, qz = bzs;
                for (int p = 1; p <= 3; p++) {
                    float t = p / 3f;
                    float jx = (ux * h11(sb + 10 + p) + vx * h11(sb + 20 + p)) * lb * 0.18f;
                    float jy = (uy * h11(sb + 10 + p) + vy * h11(sb + 20 + p)) * lb * 0.18f;
                    float jz = (uz * h11(sb + 10 + p) + vz * h11(sb + 20 + p)) * lb * 0.18f;
                    float nx = bxs + ddx * lb * t + jx, ny = bys + ddy * lb * t + jy, nz = bzs + ddz * lb * t + jz;
                    float fa = a * 0.7f * (1 - (p - 1) / 3f);
                    ruban(qx, qy, qz, nx, ny, nz, wGlow * 0.55f * (1 - (p - 1) / 3f), wGlow * 0.55f * (1 - p / 3f), FxTypes.FAISCEAU, c, fa * 0.6f, fa * 0.3f);
                    ruban(qx, qy, qz, nx, ny, nz, wCore * 0.6f * (1 - (p - 1) / 3f), wCore * 0.6f * (1 - p / 3f), FxTypes.ARC, co, fa, fa * 0.5f);
                    qx = nx;
                    qy = ny;
                    qz = nz;
                }
            }
        }
    }

    private static void dessinerParticules(float partial, boolean fumee) {
        for (int i = 0; i < nP; i++) {
            byte g = pGenre[i];
            if ((g == P_FUMEE) != fumee) continue;
            float k = (pVie[i] + partial) / pVieMax[i];
            if (k >= 1) continue;
            double x = Mth.lerp(partial, pPx[i], pX[i]), y = Mth.lerp(partial, pPy[i], pY[i]), z = Mth.lerp(partial, pPz[i], pZ[i]);
            double cx = x - CX, cy = y - CY, cz = z - CZ;
            double d2 = cx * cx + cy * cy + cz * cz;
            if (d2 < 0.09) continue;
            if (d2 < 4) k = 1 - (1 - k) * (float) (Math.sqrt(d2) / 2);
            switch (g) {
                case P_ETINCELLE -> {
                    float L = 1.6f;
                    rubanMonde(x - pVx[i] * L, y - pVy[i] * L, z - pVz[i] * L, x, y, z, pTaille[i] * 0.3f, pTaille[i], FxTypes.ETINCELLE, pCoul[i], 0.0f, 1 - k);
                }
                case P_POUSSIERE -> panneau(x, y, z, pTaille[i] * (1 - k * 0.5f), 0, FxTypes.COEUR, pCoul[i], (1 - k) * 0.9f);
                case P_GOUTTE -> {
                    panneau(x, y, z, pTaille[i] * 1.8f * (1 - k * 0.4f), 0, FxTypes.LUEUR, pCoul[i], (1 - k) * 0.5f);
                    panneau(x, y, z, pTaille[i] * 0.6f, 0, FxTypes.COEUR, 0xFFFFFF, (1 - k) * 0.7f);
                }
                case P_FUMEE -> {
                    float entree = Math.min(1f, k * 6f);
                    panneau(x, y, z, pTaille[i] * (1 + k * 1.6f), i * 1.7f + k * 1.2f, (i & 1) == 0 ? FxTypes.FUMEE_A : FxTypes.FUMEE_B, pCoul[i],
                            0.55f * entree * (1 - k));
                }
                default -> {
                }
            }
        }
    }

    // ================================================================== creatures electrocutees : tremblement
    private static final LivingEntity[] PILE = new LivingEntity[8];
    private static int nPile;
    private static final Quaternionf Q = new Quaternionf();

    public static void vivantAvant(RenderLivingEvent.Pre<?, ?> e) {
        if (nE == 0) return;
        LivingEntity le = e.getEntity();
        int id = le.getId();
        for (int i = 0; i < nE; i++) {
            if (eId[i] != id) continue;
            float now = maintenant() + e.getPartialTick();
            float age = now - eT0[i];
            if (age < 0 || age > eDur[i] || nPile >= PILE.length) return;
            float k = age / eDur[i];
            float amp = 0.075f * (1 - k) + 0.015f;
            int f = (int) (now * 2.5f) * 131 + id * 7;
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(h11(f) * amp, h11(f + 1) * amp * 0.35f, h11(f + 2) * amp);
            ps.mulPose(Q.rotationYXZ(h11(f + 3) * 0.14f * (1 - k), h11(f + 4) * 0.06f * (1 - k), h11(f + 5) * 0.08f * (1 - k)));
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

    // ================================================================== flash d'ecran, menage, statistiques
    public static void ecran(RenderGuiEvent.Post e) {
        if (flashEcran <= 0.004f) return;
        Minecraft mc = Minecraft.getInstance();
        int a = alpha(flashEcran);
        e.getGuiGraphics().fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), (a << 24) | (flashCoul & 0xFFFFFF));
    }

    public static void dechargement(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) vider();
    }

    private static void vider() {
        for (int i = 0; i < nB; i++) bE[i] = null;
        nB = nA = nL = nP = nE = nV = nFumee = nO = 0;
        for (int i = 0; i < nPile; i++) PILE[i] = null;
        nPile = 0;
        flashEcran = 0;
    }

    public static String stats() {
        String r = String.format(Locale.ROOT,
                "rendu moyen %.3f ms (pire %.3f ms) sur %d images ; en cours : %d balles, %d arcs, %d lueurs, %d ondes, %d particules, %d electrocutes ; "
                        + "pics : %d/%d balles, %d/%d arcs, %d/%d lueurs, %d/%d ondes, %d/%d particules, %d/%d electrocutes",
                images == 0 ? 0 : nsRendu / 1e6 / images, maxNs / 1e6, images, nB, nA, nL, nO, nP, nE, picB, MAX_B, picA, MAX_A, picL, MAX_L, picO, MAX_O,
                picP, MAX_P, picE, MAX_E);
        nsRendu = images = maxNs = 0;
        picB = picA = picL = picP = picE = picO = 0;
        return r;
    }
}
