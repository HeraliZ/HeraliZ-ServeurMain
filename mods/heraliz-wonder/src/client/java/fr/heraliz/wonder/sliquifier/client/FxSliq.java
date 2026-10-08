package fr.heraliz.wonder.sliquifier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.client.ClientWonder;
import fr.heraliz.wonder.client.TaczClient;
import fr.heraliz.wonder.sliquifier.SliqCalculs;
import fr.heraliz.wonder.sliquifier.SliqSons;
import fr.heraliz.wonder.sliquifier.net.PaquetFlaque;
import fr.heraliz.wonder.sliquifier.net.PaquetSync;
import fr.heraliz.wonder.sliquifier.net.PaquetTir;
import fr.heraliz.wonder.sliquifier.net.PaquetZombie;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Effets du Sliquifier cote client, dans des reserves de taille fixe (tableaux paralleles, aucune allocation par image ni
 * par particule), dessines en deux lots ({@link SliqTypes#GELEE} translucide eclaire par le monde, {@link SliqTypes#LUEUR}
 * additif) avec une seule planche de textures :
 * <ul>
 *   <li><b>boules de gelee</b> : le vol est refait exactement comme le serveur ({@link SliqCalculs#pas}) ; corps etire le
 *   long de la vitesse qui tremblote, petite boule satellite, trainee ondulante, gouttes qui tombent, halo et lueur au
 *   sol ; elles partent de la bouche de la buse et rejoignent leur vrai trajet en quelques blocs ; le tir du joueur local
 *   part tout de suite (prediction), le paquet du serveur le recale ;</li>
 *   <li><b>flaques</b> : maillage plat de 8 x 8 cases qui epouse le sol (hauteurs lues une fois a la creation : marches,
 *   dalles, bords), forme de flaque irreguliere qui « respire », reflets mouvants (4 images en fondu), lueur rose au sol,
 *   bulles qui eclatent ; apparition en rebond, sechage en se retractant ;</li>
 *   <li><b>eclaboussures</b> : gouttes projetees, etoile de gelee au sol ou sur le mur (qui coule le long du mur), anneau ;</li>
 *   <li><b>zombies</b> : glissade (ils partent a la renverse, pieds en avant), puis ils <b>fondent</b> (le corps s'affaisse et
 *   s'etale, sans l'animation de mort rouge de Minecraft) dans une bouffee rose et un bouillonnement.</li>
 * </ul>
 * La glisse du joueur local est dans {@link GlisseJoueur} (elle lit les flaques d'ici).
 */
public final class FxSliq {
    private FxSliq() {
    }

    private static long tick;
    private static final RandomSource RNG = RandomSource.create();

    // reglages recus du serveur (PaquetSync) ; valeurs par defaut = celles du fichier de reglages
    static boolean glisseJoueurs = true;
    static float boost = 1.6f, vitesseMax = 0.7f;
    static float vitesseTir = 1.55f, graviteTir = 0.022f;

    private static float maintenant() {
        return tick;
    }

    static float tickCourant() {
        return tick;
    }

    static int papFlaque(int i) {
        return fPap[i];
    }

    // ------------------------------------------------------------------ couleurs (normale / Sliquidator)
    static int coul(int pap) {
        return pap > 0 ? 0xE83CF0 : 0xFF4DB2;
    }

    static int clair(int pap) {
        return pap > 0 ? 0xFFD2FF : 0xFFC6E8;
    }

    static int sombre(int pap) {
        return pap > 0 ? 0x7A1CD0 : 0xB8166E;
    }

    // ================================================================== reserves
    // ---- boules
    private static final int MAX_B = 64, TR = 8;
    private static final int[] bId = new int[MAX_B], bTireur = new int[MAX_B], bAge = new int[MAX_B], bFin = new int[MAX_B], bTrN = new int[MAX_B];
    private static final boolean[] bLocal = new boolean[MAX_B], bArret = new boolean[MAX_B];
    private static final byte[] bPap = new byte[MAX_B];
    private static final double[] bE = new double[MAX_B * 6], bP = new double[MAX_B * 3], bTr = new double[MAX_B * TR * 3];
    private static final float[] bOx = new float[MAX_B], bOy = new float[MAX_B], bOz = new float[MAX_B], bOk = new float[MAX_B], bG = new float[MAX_B];
    private static final double[] bSol = new double[MAX_B];
    private static int nB;

    // ---- flaques
    static final int MAX_F = 96;
    private static final int N = 8, NC = (N + 1) * (N + 1);
    static final int[] fId = new int[MAX_F];
    static final double[] fX = new double[MAX_F], fY = new double[MAX_F], fZ = new double[MAX_F];
    static final float[] fR = new float[MAX_F];
    private static final float[] fRg = new float[MAX_F], fNaiss = new float[MAX_F], fFin = new float[MAX_F], fRot = new float[MAX_F], fRet = new float[MAX_F];
    private static final float[] fH = new float[MAX_F * NC], fA = new float[MAX_F * NC];
    private static final byte[] fPap = new byte[MAX_F];
    private static final int[] fLum = new int[MAX_F], fSeed = new int[MAX_F];
    private static final AABB[] fBoite = new AABB[MAX_F];
    static int nF;

    // ---- particules
    private static final int MAX_P = 1024;
    private static final double[] pX = new double[MAX_P], pY = new double[MAX_P], pZ = new double[MAX_P], pPx = new double[MAX_P], pPy = new double[MAX_P],
            pPz = new double[MAX_P], pSol = new double[MAX_P];
    private static final float[] pVx = new float[MAX_P], pVy = new float[MAX_P], pVz = new float[MAX_P], pVie = new float[MAX_P], pVieMax = new float[MAX_P],
            pTaille = new float[MAX_P], pGrav = new float[MAX_P], pFrott = new float[MAX_P], pRot = new float[MAX_P];
    private static final int[] pCoul = new int[MAX_P], pLum = new int[MAX_P];
    private static final byte[] pGenre = new byte[MAX_P];
    private static int nP;
    private static final byte P_GOUTTE = 0, P_BOUFFEE = 1, P_BULLE = 2, P_ANNEAU = 3, P_ETOILE = 4;

    // ---- eclats sur les murs (decal oriente + coulure qui descend)
    private static final int MAX_M = 48;
    private static final double[] mX = new double[MAX_M], mY = new double[MAX_M], mZ = new double[MAX_M];
    private static final float[] mT0 = new float[MAX_M], mRot = new float[MAX_M], mTaille = new float[MAX_M];
    private static final byte[] mFace = new byte[MAX_M], mPap = new byte[MAX_M];
    private static final int[] mLum = new int[MAX_M];
    private static int nM;
    private static final float DUREE_MUR = 70;

    // ---- zombies qui glissent / qui fondent
    private static final int MAX_Z = 96;
    private static final int[] zId = new int[MAX_Z];
    private static final float[] zT0 = new float[MAX_Z], zDx = new float[MAX_Z], zDz = new float[MAX_Z], zFonte = new float[MAX_Z];
    private static final byte[] zPap = new byte[MAX_Z];
    private static int nZ;
    /** Duree de la fonte (ticks) : le corps s'affaisse puis n'est plus dessine (le serveur le retire a 20 ticks). */
    private static final float FONTE = 10f;

    // ---- statistiques
    private static long nsRendu, images, maxNs;
    private static int picB, picF, picP, picZ;

    private static final float[] DEC = new float[3];
    private static final double[] E = new double[6];
    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();
    private static final int PLEINE = 0xF000F0;

    // ================================================================== entrees
    /** Tir du joueur local (prediction, des que TaCZ accepte le tir) : la boule part tout de suite de la buse. */
    public static void tirLocal(LocalPlayer p, ItemStack arme) {
        if (panne) return;
        try {
            tirLocalSur(p, arme);
        } catch (RuntimeException ex) {
            panne("tir local", ex);
        }
    }

    private static void tirLocalSur(LocalPlayer p, ItemStack arme) {
        int pap = Armes.pap(arme);
        Vec3 o = p.getEyePosition();
        Vec3 d = p.getLookAngle();
        double v = SliqCalculs.vitesse(pap, vitesseTir);
        int i = nouvelleBoule(-1, p.getId(), pap, o.x + d.x * 0.25, o.y + d.y * 0.25, o.z + d.z * 0.25, d.x * v, d.y * v, d.z * v, graviteTir, p);
        if (i >= 0) bLocal[i] = true;
    }

    /** Paquet du serveur : une boule part (ou recale la boule predite du joueur local). */
    public static void tir(PaquetTir m) {
        if (panne) return;
        try {
            tirSur(m);
        } catch (RuntimeException ex) {
            panne("paquet tir", ex);
        }
    }

    private static void tirSur(PaquetTir m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.player != null && m.tireur() == mc.player.getId()) {
            for (int i = 0; i < nB; i++) {
                if (!bLocal[i] || bId[i] != -1 || bAge[i] > 20) continue;
                // recalage : meme instant sur le vrai trajet, sans saut visible (l'ecart s'efface en quelques ticks)
                int o = i * 6;
                double vx = bE[o], vy = bE[o + 1], vz = bE[o + 2];
                double ax = vx + bOx[i] * bOk[i], ay = vy + bOy[i] * bOk[i], az = vz + bOz[i] * bOk[i];
                E[0] = m.ox();
                E[1] = m.oy();
                E[2] = m.oz();
                E[3] = m.vx();
                E[4] = m.vy();
                E[5] = m.vz();
                for (int k = 0; k < bAge[i]; k++) SliqCalculs.pas(E, m.gravite());
                System.arraycopy(E, 0, bE, o, 6);
                bOx[i] = (float) (ax - E[0]);
                bOy[i] = (float) (ay - E[1]);
                bOz[i] = (float) (az - E[2]);
                bOk[i] = 1f;
                bId[i] = m.boule();
                bG[i] = m.gravite();
                if (bArret[i] && bFin[i] < 3) bArret[i] = false;
                return;
            }
        }
        Entity t = mc.level.getEntity(m.tireur());
        nouvelleBoule(m.boule(), m.tireur(), m.pap(), m.ox(), m.oy(), m.oz(), m.vx(), m.vy(), m.vz(), m.gravite(), t instanceof LivingEntity le ? le : null);
    }

    private static int nouvelleBoule(int id, int tireur, int pap, double ox, double oy, double oz, double vx, double vy, double vz, float g, LivingEntity le) {
        if (nB >= MAX_B) retirerBoule(0);
        int i = nB++;
        bId[i] = id;
        bTireur[i] = tireur;
        bPap[i] = (byte) pap;
        bLocal[i] = false;
        bArret[i] = false;
        bAge[i] = 0;
        bFin[i] = 0;
        bTrN[i] = 0;
        bG[i] = g;
        int o = i * 6;
        bE[o] = ox;
        bE[o + 1] = oy;
        bE[o + 2] = oz;
        bE[o + 3] = vx;
        bE[o + 4] = vy;
        bE[o + 5] = vz;
        bP[i * 3] = ox;
        bP[i * 3 + 1] = oy;
        bP[i * 3 + 2] = oz;
        decalageCanon(le);
        bOx[i] = DEC[0];
        bOy[i] = DEC[1];
        bOz[i] = DEC[2];
        bOk[i] = 1f;
        bSol[i] = Double.NaN;
        if (nB > picB) picB = nB;
        // la buse crache : eclat de gelee et gouttes a la bouche
        double mx = ox + DEC[0], my = oy + DEC[1], mz = oz + DEC[2];
        int lum = lumiere(mx, my, mz);
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        for (int k = 0; k < 7; k++) {
            float s = 0.04f + RNG.nextFloat() * 0.05f;
            particule(P_GOUTTE, mx, my, mz, (float) (vx / vl * 0.25 + (RNG.nextFloat() - 0.5) * 0.18), (float) (vy / vl * 0.25 + RNG.nextFloat() * 0.12),
                    (float) (vz / vl * 0.25 + (RNG.nextFloat() - 0.5) * 0.18), s, 10 + RNG.nextInt(8), RNG.nextBoolean() ? coul(pap) : clair(pap), 0.035f, 0.93f, lum);
        }
        return i;
    }

    /** Paquet du serveur : flaque posee / etalee / retiree, eclaboussure. */
    public static void flaque(PaquetFlaque m) {
        if (panne) return;
        try {
            flaqueSur(m);
        } catch (RuntimeException ex) {
            panne("paquet flaque", ex);
        }
    }

    private static void flaqueSur(PaquetFlaque m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        if (m.action() == PaquetFlaque.RETRAIT) {
            for (int i = 0; i < nF; i++) if (fId[i] == m.id() && fRet[i] < 0) fRet[i] = maintenant();
            return;
        }
        if (m.boule() >= 0) arreterBoule(m.boule(), m.splat() == PaquetFlaque.S_AUCUN && m.action() == PaquetFlaque.SPLAT_SEUL);
        if (m.action() == PaquetFlaque.AJOUT) {
            ajouterFlaque(lv, m.id(), m.x(), m.y(), m.z(), m.rayon(), m.duree(), m.age(), m.pap());
            if (m.splat() != PaquetFlaque.S_AUCUN) son(SliqSons.FLAQUE.get(), m.x(), m.y(), m.z(), 0.55f, 0.9f + RNG.nextFloat() * 0.2f);
        } else if (m.action() == PaquetFlaque.MAJ) {
            for (int i = 0; i < nF; i++) {
                if (fId[i] != m.id()) continue;
                fR[i] = m.rayon();
                fFin[i] = maintenant() - m.age() + m.duree();
                fPap[i] = m.pap();
                if (m.rayon() > fRg[i] * 1.05f) grille(lv, i);
            }
        }
        if (m.splat() != PaquetFlaque.S_AUCUN) splat(lv, m);
    }

    /** Paquet du serveur : zombie qui glisse, qui fond, boss touche. */
    public static void zombie(PaquetZombie m) {
        if (panne) return;
        try {
            zombieSur(m);
        } catch (RuntimeException ex) {
            panne("paquet zombie", ex);
        }
    }

    private static void zombieSur(PaquetZombie m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Entity e = lv.getEntity(m.id());
        float now = maintenant();
        int pap = m.pap();
        if (m.action() == PaquetZombie.GLISSE) {
            int i = indexZ(m.id(), true);
            if (i < 0) return;
            zT0[i] = now;
            zDx[i] = m.dx() / 127f;
            zDz[i] = m.dz() / 127f;
            zPap[i] = (byte) pap;
            zFonte[i] = -1;
            if (e != null) {
                son(SliqSons.GLISSE.get(), e.getX(), e.getY(), e.getZ(), 0.8f, 0.9f + RNG.nextFloat() * 0.25f);
                eclabousser(e.getX(), e.getY() + 0.1, e.getZ(), 10, 0.16f, pap, lumiere(e.getX(), e.getY() + 0.5, e.getZ()), 0.5f);
            }
        } else if (m.action() == PaquetZombie.LIQUEFIE) {
            int i = indexZ(m.id(), true);
            if (i < 0) return;
            boolean glissait = zFonte[i] < 0 && zDx[i] * zDx[i] + zDz[i] * zDz[i] > 0.01f && now - zT0[i] < 80;
            if (!glissait) {
                // coup direct : fonte debout (pas de chute)
                zT0[i] = now;
                zDx[i] = zDz[i] = 0;
            }
            zFonte[i] = now;
            zPap[i] = (byte) pap;
            if (e != null) {
                double h = e.getBbHeight(), w = e.getBbWidth();
                int lum = lumiere(e.getX(), e.getY() + h * 0.5, e.getZ());
                son(SliqSons.LIQUEFIE.get(), e.getX(), e.getY(), e.getZ(), 1.0f, 0.88f + RNG.nextFloat() * 0.24f);
                for (int k = 0; k < 9; k++) {
                    particule(P_BOUFFEE, e.getX() + (RNG.nextDouble() - 0.5) * w, e.getY() + RNG.nextDouble() * h * 0.8, e.getZ() + (RNG.nextDouble() - 0.5) * w,
                            (RNG.nextFloat() - 0.5f) * 0.05f, 0.02f + RNG.nextFloat() * 0.04f, (RNG.nextFloat() - 0.5f) * 0.05f, 0.35f + RNG.nextFloat() * 0.3f,
                            16 + RNG.nextInt(10), k % 3 == 0 ? clair(pap) : coul(pap), -0.001f, 0.92f, lum);
                }
                eclabousser(e.getX(), e.getY() + h * 0.45, e.getZ(), 26, 0.3f, pap, lum, 1f);
            }
        } else {
            if (e != null) {
                eclabousser(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 16, 0.26f, pap, lumiere(e.getX(), e.getY() + 1, e.getZ()), 1f);
                son(SliqSons.SPLAT.get(), e.getX(), e.getY() + 1, e.getZ(), 0.9f, 0.9f + RNG.nextFloat() * 0.2f);
            }
        }
    }

    /** Paquet du serveur : reglages de glisse et flaques de la dimension (connexion, changement de dimension). */
    public static void sync(PaquetSync m) {
        if (panne) return;
        try {
            syncSur(m);
        } catch (RuntimeException ex) {
            panne("paquet sync", ex);
        }
    }

    private static void syncSur(PaquetSync m) {
        glisseJoueurs = m.glisseJoueurs();
        boost = m.boost();
        vitesseMax = m.vitesseMax();
        Minecraft mc = Minecraft.getInstance();
        nF = 0;
        if (mc.level == null) return;
        for (int k = 0; k < m.n(); k++) {
            int o = k * PaquetSync.CHAMPS;
            ajouterFlaque(mc.level, m.ids()[k], m.f()[o], m.f()[o + 1], m.f()[o + 2], m.f()[o + 3], (int) m.f()[o + 4], (int) m.f()[o + 5], (int) m.f()[o + 6]);
        }
    }

    // ================================================================== flaques
    private static void ajouterFlaque(ClientLevel lv, int id, double x, double y, double z, float r, int duree, int age, int pap) {
        for (int i = 0; i < nF; i++) if (fId[i] == id) return;
        if (nF >= MAX_F) retirerFlaque(0);
        int i = nF++;
        fId[i] = id;
        fX[i] = x;
        fY[i] = y;
        fZ[i] = z;
        fR[i] = r;
        fNaiss[i] = maintenant() - age;
        fFin[i] = fNaiss[i] + duree;
        fRet[i] = -1;
        fPap[i] = (byte) pap;
        fRot[i] = RNG.nextFloat() * Mth.TWO_PI;
        fSeed[i] = RNG.nextInt(1000);
        grille(lv, i);
        if (nF > picF) picF = nF;
    }

    /**
     * Hauteurs des coins du maillage (8 x 8 cases) lues une fois : pour chaque coin, le dessus du sol le plus haut entre
     * 1,2 bloc sous la flaque et 0,55 au-dessus (marches, dalles) ; rien (trou, mur) -> coin transparent.
     */
    private static void grille(ClientLevel lv, int i) {
        float rg = fR[i] * 1.12f;
        fRg[i] = fR[i];
        float c = Mth.cos(fRot[i]), s = Mth.sin(fRot[i]);
        double y0 = fY[i];
        int base = i * NC;
        for (int a = 0; a <= N; a++) {
            for (int b = 0; b <= N; b++) {
                float u = a / (float) N * 2 - 1, v = b / (float) N * 2 - 1;
                double wx = fX[i] + (u * c - v * s) * rg, wz = fZ[i] + (u * s + v * c) * rg;
                double h = surface(lv, wx, y0, wz);
                int k = base + a * (N + 1) + b;
                if (Double.isNaN(h)) {
                    fH[k] = 0;
                    fA[k] = 0;
                } else {
                    fH[k] = (float) (h - y0);
                    fA[k] = 1;
                }
            }
        }
        fBoite[i] = new AABB(fX[i] - rg - 0.5, y0 - 1.5, fZ[i] - rg - 0.5, fX[i] + rg + 0.5, y0 + 1.0, fZ[i] + rg + 0.5);
        fLum[i] = lumiere(fX[i], y0 + 0.3, fZ[i]);
    }

    private static double surface(ClientLevel lv, double x, double y0, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y0 + 0.5);
        for (int d = 0; d <= 2; d++) {
            MP.set(bx, by - d, bz);
            BlockState st = lv.getBlockState(MP);
            if (st.isAir()) continue;
            VoxelShape sh = st.getCollisionShape(lv, MP);
            if (sh.isEmpty()) continue;
            double top = MP.getY() + sh.max(Direction.Axis.Y);
            if (top <= y0 + 0.55 && top >= y0 - 1.2) return top;
            if (top > y0 + 0.55) return Double.NaN;          // mur : la gelee ne monte pas dessus
        }
        return Double.NaN;
    }

    private static void retirerFlaque(int i) {
        int d = --nF;
        if (i != d) {
            fId[i] = fId[d];
            fX[i] = fX[d];
            fY[i] = fY[d];
            fZ[i] = fZ[d];
            fR[i] = fR[d];
            fRg[i] = fRg[d];
            fNaiss[i] = fNaiss[d];
            fFin[i] = fFin[d];
            fRot[i] = fRot[d];
            fRet[i] = fRet[d];
            fPap[i] = fPap[d];
            fLum[i] = fLum[d];
            fSeed[i] = fSeed[d];
            fBoite[i] = fBoite[d];
            System.arraycopy(fH, d * NC, fH, i * NC, NC);
            System.arraycopy(fA, d * NC, fA, i * NC, NC);
        }
        fBoite[d] = null;
    }

    /** Rayon affiche de la flaque i a l'instant t (rebond a l'apparition, se retracte en sechant), 0 si finie. */
    static float rayonAffiche(int i, float t) {
        float age = t - fNaiss[i];
        if (age < 0) return 0;
        float s = age < 9 ? retour(age / 9f) : 1f;
        float reste = fFin[i] - t;
        if (reste < 30) s *= Math.max(0, reste / 30f);
        if (fRet[i] >= 0) s *= Math.max(0, 1 - (t - fRet[i]) / 10f);
        return fR[i] * s;
    }

    /** Sortie avec rebond (easeOutBack). */
    private static float retour(float u) {
        float c1 = 1.9f, c3 = c1 + 1;
        float v = u - 1;
        return 1 + c3 * v * v * v + c1 * v * v;
    }

    // ================================================================== eclaboussures
    private static void splat(ClientLevel lv, PaquetFlaque m) {
        int pap = m.pap();
        byte g = m.splat();
        double x = g == PaquetFlaque.S_SOL ? m.x() : m.ix(), y = g == PaquetFlaque.S_SOL ? m.y() : m.iy(), z = g == PaquetFlaque.S_SOL ? m.z() : m.iz();
        int lum = lumiere(x, y + 0.2, z);
        son(SliqSons.SPLAT.get(), x, y, z, 1.0f, (pap > 0 ? 0.85f : 0.95f) + RNG.nextFloat() * 0.2f);
        float now = maintenant();
        if (g == PaquetFlaque.S_SOL || g == PaquetFlaque.S_ZOMBIE && m.action() != PaquetFlaque.SPLAT_SEUL) {
            double ys = g == PaquetFlaque.S_SOL ? y : m.y();
            double xs = g == PaquetFlaque.S_SOL ? x : m.x(), zs = g == PaquetFlaque.S_SOL ? z : m.z();
            particule(P_ETOILE, xs, ys + 0.03, zs, 0, 0, 0, m.rayon() * 0.9f, 14, coul(pap), 0, 1, lum);
            particule(P_ANNEAU, xs, ys + 0.05, zs, 0, 0, 0, m.rayon() * 1.5f, 9, clair(pap), 0, 1, lum);
        }
        if (g == PaquetFlaque.S_MUR && m.face() >= 0 && m.face() < 6 && nM < MAX_M) {
            int i = nM++;
            mX[i] = x;
            mY[i] = y;
            mZ[i] = z;
            mT0[i] = now;
            mRot[i] = RNG.nextFloat() * Mth.TWO_PI;
            mTaille[i] = 0.55f + RNG.nextFloat() * 0.2f;
            mFace[i] = m.face();
            mPap[i] = (byte) pap;
            mLum[i] = lum;
        }
        eclabousser(x, y + 0.08, z, g == PaquetFlaque.S_ZOMBIE ? 22 : 26, g == PaquetFlaque.S_MUR ? 0.22f : 0.3f, pap, lum, 1f);
    }

    /** Gouttes projetees autour du point (hemisphere du haut), quelques grosses et beaucoup de petites. */
    private static void eclabousser(double x, double y, double z, int n, float force, int pap, int lum, float taille) {
        for (int k = 0; k < n; k++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            float h = RNG.nextFloat();
            float sp = force * (0.35f + RNG.nextFloat() * 0.75f);
            float s = (0.035f + RNG.nextFloat() * (k < n / 5 ? 0.1f : 0.05f)) * taille;
            particule(P_GOUTTE, x, y, z, (float) Math.cos(a) * sp * (1 - h * 0.5f), (0.1f + h * 0.7f) * sp * 1.4f, (float) Math.sin(a) * sp * (1 - h * 0.5f), s,
                    14 + RNG.nextInt(14), k % 4 == 0 ? clair(pap) : (k % 4 == 1 ? sombre(pap) : coul(pap)), 0.045f, 0.95f, lum);
        }
    }

    // ================================================================== tick
    // ================================================================== garde-fous (aucun test en jeu : une erreur d'effet ne
    // doit jamais faire planter le jeu ; elle est notee une fois dans le journal et les effets se coupent jusqu'au
    // prochain monde)
    private static boolean panne;

    private static void panne(String ou, RuntimeException ex) {
        if (!panne) fr.heraliz.wonder.HeralizWonder.LOG.error("[sliquifier] effets coupes apres une erreur ({})", ou, ex);
        panne = true;
        try {
            vider();
        } catch (RuntimeException ignore) {
            // rien
        }
    }

    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            vider();
            panne = false;
            return;
        }
        if (mc.isPaused() || panne) return;
        try {
            tickSur(mc);
        } catch (RuntimeException ex) {
            panne("tick", ex);
        }
    }

    private static void tickSur(Minecraft mc) {
        tick++;
        float now = maintenant();
        ClientLevel lv = mc.level;
        tickBoules(lv);
        // flaques : fin, lumiere (toutes les 10 ticks), bulles
        for (int i = 0; i < nF; i++) {
            if (now > fFin[i] + 1 || (fRet[i] >= 0 && now - fRet[i] > 10)) {
                retirerFlaque(i--);
                continue;
            }
            if ((tick + i) % 10 == 0) fLum[i] = lumiere(fX[i], fY[i] + 0.3, fZ[i]);
            float r = rayonAffiche(i, now);
            if (r > 0.3f && RNG.nextFloat() < 0.18f + 0.06f * r) {
                // bulle qui eclate a la surface (sur un coin porte)
                int a = 1 + RNG.nextInt(N - 1), b = 1 + RNG.nextInt(N - 1);
                int k = i * NC + a * (N + 1) + b;
                float u = a / (float) N * 2 - 1, v = b / (float) N * 2 - 1;
                if (fA[k] > 0 && u * u + v * v < 0.55f) {
                    float c = Mth.cos(fRot[i]), s = Mth.sin(fRot[i]), rg = r * 1.12f;
                    particule(P_BULLE, fX[i] + (u * c - v * s) * rg, fY[i] + fH[k] + 0.035, fZ[i] + (u * s + v * c) * rg, 0, 0, 0, 0.1f + RNG.nextFloat() * 0.08f,
                            7 + RNG.nextInt(4), clair(fPap[i]), 0, 1, fLum[i]);
                }
            }
        }
        // zombies : gouttes de la glissade, bouillonnement de la fonte
        int w = 0;
        for (int i = 0; i < nZ; i++) {
            Entity en = lv.getEntity(zId[i]);
            boolean fond = zFonte[i] >= 0;
            float age = now - (fond ? zFonte[i] : zT0[i]);
            if (en == null || (fond && age > 22) || (!fond && age > 60)) continue;
            if (fond) {
                if (age < FONTE + 4) {
                    for (int k = 0; k < 2; k++) {
                        particule(P_GOUTTE, en.getX() + (RNG.nextDouble() - 0.5) * 0.8, en.getY() + 0.15 + RNG.nextDouble() * 0.6 * (1 - age / FONTE),
                                en.getZ() + (RNG.nextDouble() - 0.5) * 0.8, (RNG.nextFloat() - 0.5f) * 0.08f, 0.05f + RNG.nextFloat() * 0.12f,
                                (RNG.nextFloat() - 0.5f) * 0.08f, 0.04f + RNG.nextFloat() * 0.05f, 12 + RNG.nextInt(8), coul(zPap[i]), 0.04f, 0.94f,
                                lumiere(en.getX(), en.getY() + 0.5, en.getZ()));
                    }
                    if ((tick & 1) == 0) particule(P_BULLE, en.getX() + (RNG.nextDouble() - 0.5) * 0.9, en.getY() + 0.04, en.getZ() + (RNG.nextDouble() - 0.5) * 0.9,
                            0, 0, 0, 0.14f + RNG.nextFloat() * 0.1f, 8, clair(zPap[i]), 0, 1, lumiere(en.getX(), en.getY() + 0.5, en.getZ()));
                }
            } else if ((tick & 1) == 0) {
                particule(P_GOUTTE, en.getX(), en.getY() + 0.1, en.getZ(), (RNG.nextFloat() - 0.5f) * 0.15f, 0.08f + RNG.nextFloat() * 0.1f,
                        (RNG.nextFloat() - 0.5f) * 0.15f, 0.04f + RNG.nextFloat() * 0.04f, 10 + RNG.nextInt(6), coul(zPap[i]), 0.04f, 0.94f,
                        lumiere(en.getX(), en.getY() + 0.5, en.getZ()));
            }
            copierZ(i, w++);
        }
        nZ = w;
        // eclats des murs
        w = 0;
        for (int i = 0; i < nM; i++) {
            if (now - mT0[i] > DUREE_MUR) continue;
            if (i != w) {
                mX[w] = mX[i];
                mY[w] = mY[i];
                mZ[w] = mZ[i];
                mT0[w] = mT0[i];
                mRot[w] = mRot[i];
                mTaille[w] = mTaille[i];
                mFace[w] = mFace[i];
                mPap[w] = mPap[i];
                mLum[w] = mLum[i];
            }
            w++;
        }
        nM = w;
        tickParticules();
        GlisseJoueur.tick(mc);
    }

    private static void tickBoules(ClientLevel lv) {
        for (int i = 0; i < nB; i++) {
            int o = i * 6;
            bP[i * 3] = bE[o];
            bP[i * 3 + 1] = bE[o + 1];
            bP[i * 3 + 2] = bE[o + 2];
            if (bArret[i]) {
                pousserTrainee(i, bE[o], bE[o + 1], bE[o + 2]);
                // la boule predite attend le paquet du serveur ; sans nouvelles, elle s'efface
                if (++bFin[i] > (bId[i] < 0 ? 30 : TR)) retirerBoule(i--);
                continue;
            }
            double x0 = bE[o], y0 = bE[o + 1], z0 = bE[o + 2];
            System.arraycopy(bE, o, E, 0, 6);
            SliqCalculs.pas(E, bG[i]);
            // collision prevue avec un bloc (le serveur confirmera) : la boule s'arrete au point touche
            int pas = Math.min(40, (int) (Math.sqrt((E[0] - x0) * (E[0] - x0) + (E[1] - y0) * (E[1] - y0) + (E[2] - z0) * (E[2] - z0)) * 3) + 1);
            boolean stop = false;
            for (int s = 1; s <= pas; s++) {
                double t = s / (double) pas;
                double x = x0 + (E[0] - x0) * t, y = y0 + (E[1] - y0) * t, z = z0 + (E[2] - z0) * t;
                if (dansBloc(lv, x, y, z)) {
                    double tt = (s - 1) / (double) pas;
                    bE[o] = x0 + (E[0] - x0) * tt;
                    bE[o + 1] = y0 + (E[1] - y0) * tt;
                    bE[o + 2] = z0 + (E[2] - z0) * tt;
                    bArret[i] = true;
                    stop = true;
                    break;
                }
            }
            if (!stop) System.arraycopy(E, 0, bE, o, 6);
            bAge[i]++;
            bOk[i] *= 0.78f;
            pousserTrainee(i, bE[o] + bOx[i] * bOk[i], bE[o + 1] + bOy[i] * bOk[i], bE[o + 2] + bOz[i] * bOk[i]);
            if ((tick + i) % 2 == 0) bSol[i] = solSous(bE[o], bE[o + 1], bE[o + 2], 5);
            // gouttes qui tombent de la boule
            if (RNG.nextFloat() < 0.7f) {
                particule(P_GOUTTE, bE[o] + (RNG.nextDouble() - 0.5) * 0.15, bE[o + 1] - 0.08, bE[o + 2] + (RNG.nextDouble() - 0.5) * 0.15, (float) bE[o + 3] * 0.15f,
                        -0.02f, (float) bE[o + 5] * 0.15f, 0.03f + RNG.nextFloat() * 0.035f, 12 + RNG.nextInt(8), coul(bPap[i]), 0.04f, 0.96f,
                        lumiere(bE[o], bE[o + 1], bE[o + 2]));
            }
            if (bAge[i] > 80) retirerBoule(i--);
        }
    }

    private static void pousserTrainee(int i, double x, double y, double z) {
        int base = i * TR * 3;
        int n = Math.min(bTrN[i], TR - 1);
        System.arraycopy(bTr, base, bTr, base + 3, n * 3);
        bTr[base] = x;
        bTr[base + 1] = y;
        bTr[base + 2] = z;
        bTrN[i] = n + 1;
    }

    private static void arreterBoule(int id, boolean silencieuse) {
        for (int i = 0; i < nB; i++) {
            if (bId[i] != id) continue;
            if (silencieuse) {
                int o = i * 6;
                for (int k = 0; k < 5; k++) {
                    particule(P_GOUTTE, bE[o], bE[o + 1], bE[o + 2], (RNG.nextFloat() - 0.5f) * 0.1f, RNG.nextFloat() * 0.05f, (RNG.nextFloat() - 0.5f) * 0.1f, 0.05f,
                            14, coul(bPap[i]), 0.04f, 0.95f, lumiere(bE[o], bE[o + 1], bE[o + 2]));
                }
            }
            retirerBoule(i);
            return;
        }
    }

    private static void retirerBoule(int i) {
        int d = --nB;
        if (i != d) {
            bId[i] = bId[d];
            bTireur[i] = bTireur[d];
            bAge[i] = bAge[d];
            bFin[i] = bFin[d];
            bTrN[i] = bTrN[d];
            bLocal[i] = bLocal[d];
            bArret[i] = bArret[d];
            bPap[i] = bPap[d];
            System.arraycopy(bE, d * 6, bE, i * 6, 6);
            System.arraycopy(bP, d * 3, bP, i * 3, 3);
            System.arraycopy(bTr, d * TR * 3, bTr, i * TR * 3, TR * 3);
            bOx[i] = bOx[d];
            bOy[i] = bOy[d];
            bOz[i] = bOz[d];
            bOk[i] = bOk[d];
            bG[i] = bG[d];
            bSol[i] = bSol[d];
        }
    }

    private static int indexZ(int id, boolean creer) {
        for (int i = 0; i < nZ; i++) if (zId[i] == id) return i;
        if (!creer) return -1;
        if (nZ >= MAX_Z) return -1;
        int i = nZ++;
        zId[i] = id;
        zFonte[i] = -1;
        zDx[i] = zDz[i] = 0;
        if (nZ > picZ) picZ = nZ;
        return i;
    }

    private static void copierZ(int i, int w) {
        if (i == w) return;
        zId[w] = zId[i];
        zT0[w] = zT0[i];
        zDx[w] = zDx[i];
        zDz[w] = zDz[i];
        zFonte[w] = zFonte[i];
        zPap[w] = zPap[i];
    }

    // ================================================================== particules
    private static void particule(byte genre, double x, double y, double z, float vx, float vy, float vz, float taille, int vie, int coul, float grav,
                                  float frott, int lum) {
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
        pLum[i] = lum;
        pRot[i] = RNG.nextFloat() * Mth.TWO_PI;
        pSol[i] = genre == P_GOUTTE ? solSous(x, y, z, 6) : Double.NaN;
        if (nP > picP) picP = nP;
    }

    private static void tickParticules() {
        int w = 0;
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
            // une goutte qui touche le sol s'ecrase (petit anneau une fois sur quatre)
            if (pGenre[i] == P_GOUTTE && !Double.isNaN(pSol[i]) && pY[i] <= pSol[i] + 0.02) {
                if (pTaille[i] > 0.06f && (i & 3) == 0 && nP < MAX_P - 1) {
                    pGenre[i] = P_BULLE;
                    pY[i] = pPy[i] = pSol[i] + 0.03;
                    pVx[i] = pVy[i] = pVz[i] = 0;
                    pGrav[i] = 0;
                    pTaille[i] *= 1.6f;
                    pVie[i] = 0;
                    pVieMax[i] = 6;
                } else {
                    continue;
                }
            }
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
                pLum[w] = pLum[i];
                pRot[w] = pRot[i];
                pSol[w] = pSol[i];
            }
            w++;
        }
        nP = w;
    }

    /** Gouttes sous les pieds du joueur local qui glisse (appele par {@link GlisseJoueur}). */
    static void gouttesPieds(double x, double y, double z, float vx, float vz, int pap) {
        int lum = lumiere(x, y + 0.3, z);
        particule(P_GOUTTE, x + (RNG.nextDouble() - 0.5) * 0.5, y + 0.05, z + (RNG.nextDouble() - 0.5) * 0.5, -vx * 0.3f + (RNG.nextFloat() - 0.5f) * 0.1f,
                0.08f + RNG.nextFloat() * 0.1f, -vz * 0.3f + (RNG.nextFloat() - 0.5f) * 0.1f, 0.035f + RNG.nextFloat() * 0.04f, 10 + RNG.nextInt(6),
                RNG.nextBoolean() ? coul(pap) : clair(pap), 0.045f, 0.94f, lum);
    }

    // ================================================================== origine du tir (buse)
    private static void decalageCanon(LivingEntity tireur) {
        DEC[0] = DEC[1] = DEC[2] = 0;
        if (tireur == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (tireur == mc.player && ClientWonder.tacz && TaczClient.premierePersonne() && TaczClient.decalageCanon(mc.gameRenderer.getMainCamera(), DEC)) return;
        float yaw = tireur.getYRot() * Mth.DEG_TO_RAD, pitch = tireur.getXRot() * Mth.DEG_TO_RAD;
        float cp = Mth.cos(pitch);
        float lx = -Mth.sin(yaw) * cp, ly = -Mth.sin(pitch), lz = Mth.cos(yaw) * cp;
        float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        DEC[0] = lx * 0.8f + rx * 0.32f;
        DEC[1] = ly * 0.8f - 0.28f;
        DEC[2] = lz * 0.8f + rz * 0.32f;
    }

    // ================================================================== monde
    private static boolean dansBloc(ClientLevel lv, double x, double y, double z) {
        MP.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockState s = lv.getBlockState(MP);
        if (s.isAir()) return false;
        VoxelShape sh = s.getCollisionShape(lv, MP);
        if (sh.isEmpty()) return false;
        double lx = x - MP.getX(), ly = y - MP.getY(), lz = z - MP.getZ();
        return lx >= sh.min(Direction.Axis.X) && lx <= sh.max(Direction.Axis.X) && ly >= sh.min(Direction.Axis.Y) && ly <= sh.max(Direction.Axis.Y)
                && lz >= sh.min(Direction.Axis.Z) && lz <= sh.max(Direction.Axis.Z);
    }

    private static double solSous(double x, double y, double z, double max) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) return Double.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
        for (int d = 0; d <= (int) Math.ceil(max); d++) {
            MP.set(bx, by - d, bz);
            BlockState s = lv.getBlockState(MP);
            if (s.isAir()) continue;
            VoxelShape sh = s.getCollisionShape(lv, MP);
            if (sh.isEmpty()) continue;
            double top = MP.getY() + sh.max(Direction.Axis.Y);
            if (top <= y + 0.01 && y - top <= max) return top;
        }
        return Double.NaN;
    }

    /**
     * Lumiere du monde au point (bloc + ciel, valeur de lightmap), avec un plancher de lumiere de bloc : la gelee luit un
     * peu par elle-meme (on la voit la nuit, sans qu'elle eclaire comme une lampe).
     */
    private static int lumiere(double x, double y, double z) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) return PLEINE;
        MP.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int l = LevelRenderer.getLightColor(lv, MP);
        int bloc = (l >> 4) & 0xF, ciel = (l >> 20) & 0xF;
        return (ciel << 20) | (Math.max(bloc, LUEUR_PROPRE) << 4);
    }

    /** Lumiere de bloc minimale de la gelee (0..15). */
    private static final int LUEUR_PROPRE = 9;

    private static void son(SoundEvent s, double x, double y, double z, float vol, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.playLocalSound(x, y, z, s, SoundSource.PLAYERS, vol, pitch, false);
    }

    static void sonPas(double x, double y, double z, float vol) {
        son(SliqSons.PAS.get(), x, y, z, vol, 0.9f + RNG.nextFloat() * 0.25f);
    }

    // ================================================================== rendu
    private static VertexConsumer VC;
    private static boolean LIT;
    private static Matrix4f M;
    private static double CX, CY, CZ;
    private static float LX, LY, LZ, UX, UY, UZ;

    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || panne) return;
        if (nB == 0 && nF == 0 && nP == 0 && nM == 0) return;
        try {
            renduSur(e);
        } catch (RuntimeException ex) {
            VC = null;
            try {
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            } catch (RuntimeException ignore) {
                // rien
            }
            panne("rendu", ex);
        }
    }

    private static void renduSur(RenderLevelStageEvent e) {
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
        Frustum fr = e.getFrustum();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        // lot 1 : gelee (translucide, eclairee par le monde)
        VC = buf.getBuffer(SliqTypes.GELEE);
        LIT = true;
        dessinerFlaques(now, fr, false);
        dessinerMurs(now);
        dessinerBoules(partial, now, false);
        dessinerParticules(partial, false);
        buf.endBatch(SliqTypes.GELEE);
        // lot 2 : lueurs (additif)
        VC = buf.getBuffer(SliqTypes.LUEUR);
        LIT = false;
        dessinerFlaques(now, fr, true);
        dessinerBoules(partial, now, true);
        dessinerParticules(partial, true);
        buf.endBatch(SliqTypes.LUEUR);
        VC = null;
        long ns = System.nanoTime() - t0;
        nsRendu += ns;
        images++;
        if (ns > maxNs) maxNs = ns;
    }

    private static int alpha(float a) {
        return a <= 0 ? 0 : a >= 1 ? 255 : (int) (a * 255);
    }

    private static void sommet(float x, float y, float z, int coul, int a, float u, float v, int lum) {
        VC.vertex(M, x, y, z).color((coul >> 16) & 255, (coul >> 8) & 255, coul & 255, a).uv(u, v);
        if (LIT) VC.uv2(lum);
        VC.endVertex();
    }

    /** Carre face a la camera (coordonnees monde), tourne de {@code rot}. */
    private static void panneau(double x, double y, double z, float r, float rot, int cell, int coul, float a, int lum) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float px = (float) (x - CX), py = (float) (y - CY), pz = (float) (z - CZ);
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        float ax = LX * cs + UX * sn, ay = LY * cs + UY * sn, az = LZ * cs + UZ * sn;
        float bx = -LX * sn + UX * cs, by = -LY * sn + UY * cs, bz = -LZ * sn + UZ * cs;
        float u0 = SliqTypes.u0(cell), v0 = SliqTypes.v0(cell), u1 = SliqTypes.u1(cell), v1 = SliqTypes.v1(cell);
        sommet(px - ax - bx, py - ay - by, pz - az - bz, coul, al, u0, v1, lum);
        sommet(px + ax - bx, py + ay - by, pz + az - bz, coul, al, u1, v1, lum);
        sommet(px + ax + bx, py + ay + by, pz + az + bz, coul, al, u1, v0, lum);
        sommet(px - ax + bx, py - ay + by, pz - az + bz, coul, al, u0, v0, lum);
    }

    /** Disque horizontal. */
    private static void disque(double x, double y, double z, float r, float rot, int cell, int coul, float a, int lum) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float px = (float) (x - CX), py = (float) (y - CY), pz = (float) (z - CZ);
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        float u0 = SliqTypes.u0(cell), v0 = SliqTypes.v0(cell), u1 = SliqTypes.u1(cell), v1 = SliqTypes.v1(cell);
        sommet(px - cs + sn, py, pz - sn - cs, coul, al, u0, v0, lum);
        sommet(px - cs - sn, py, pz - sn + cs, coul, al, u0, v1, lum);
        sommet(px + cs - sn, py, pz + sn + cs, coul, al, u1, v1, lum);
        sommet(px + cs + sn, py, pz + sn - cs, coul, al, u1, v0, lum);
    }

    /** Ruban face a la camera de A a B (relatif a la camera). */
    private static void ruban(float ax, float ay, float az, float bx, float by, float bz, float w0, float w1, int cell, int coul, float a0, float a1, int lum) {
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
        float u0 = SliqTypes.u0(cell), v0 = SliqTypes.v0(cell), u1 = SliqTypes.u1(cell), v1 = SliqTypes.v1(cell);
        sommet(ax + sx * h0, ay + sy * h0, az + sz * h0, coul, al0, u0, v0, lum);
        sommet(ax - sx * h0, ay - sy * h0, az - sz * h0, coul, al0, u0, v1, lum);
        sommet(bx - sx * h1, by - sy * h1, bz - sz * h1, coul, al1, u1, v1, lum);
        sommet(bx + sx * h1, by + sy * h1, bz + sz * h1, coul, al1, u1, v0, lum);
    }

    private static void dessinerFlaques(float now, Frustum fr, boolean lueur) {
        for (int i = 0; i < nF; i++) {
            float r = rayonAffiche(i, now);
            if (r < 0.04f || fBoite[i] == null) continue;
            double dx = fX[i] - CX, dy = fY[i] - CY, dz = fZ[i] - CZ;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 80 * 80 || (fr != null && !fr.isVisible(fBoite[i]))) continue;
            int pap = fPap[i];
            // la flaque « respire » : rotation lente et legere ovalisation
            float t = now * 0.05f + fSeed[i];
            float rot = fRot[i] + 0.04f * Mth.sin(t * 0.7f);
            float sx = r * 1.12f * (1 + 0.035f * Mth.sin(t * 1.3f)), sz = r * 1.12f * (1 - 0.035f * Mth.sin(t * 1.3f));
            float fondu = 1f;
            float reste = fFin[i] - now;
            if (reste < 30) fondu = Math.max(0, reste / 30f);
            if (!lueur) {
                grilleFlaque(i, rot, sx, sz, SliqTypes.FLAQUE, coul(pap), 0.94f * Math.min(1f, fondu * 1.5f), 0.022f, fLum[i]);
            } else {
                // reflets mouvants : deux images voisines en fondu (4 images en boucle) ; lueur rose au sol
                if (d2 < 48 * 48) {
                    float f = now * 0.11f + fSeed[i] * 0.37f;
                    int a = ((int) Math.floor(f)) & 3;
                    float k = f - (float) Math.floor(f);
                    grilleFlaque(i, rot, sx, sz, SliqTypes.REFLET_0 + a, clair(pap), (pap > 0 ? 0.5f : 0.4f) * (1 - k) * fondu, 0.03f, 0);
                    grilleFlaque(i, rot, sx, sz, SliqTypes.REFLET_0 + ((a + 1) & 3), clair(pap), (pap > 0 ? 0.5f : 0.4f) * k * fondu, 0.031f, 0);
                }
                disque(fX[i], fY[i] + 0.05, fZ[i], r * 1.45f, rot, SliqTypes.HALO, coul(pap), (pap > 0 ? 0.26f : 0.17f) * fondu, 0);
            }
        }
    }

    /** Maillage de la flaque i (8 x 8 cases sur les hauteurs lues), texture {@code cell} etiree sur tout le disque. */
    private static void grilleFlaque(int i, float rot, float sx, float sz, int cell, int coul, float a, float leve, int lum) {
        if (a <= 0.004f) return;
        float c = Mth.cos(rot), s = Mth.sin(rot);
        float ox = (float) (fX[i] - CX), oy = (float) (fY[i] - CY) + leve, oz = (float) (fZ[i] - CZ);
        float u0 = SliqTypes.u0(cell), v0 = SliqTypes.v0(cell), du = SliqTypes.u1(cell) - u0, dv = SliqTypes.v1(cell) - v0;
        int base = i * NC;
        for (int ia = 0; ia < N; ia++) {
            for (int ib = 0; ib < N; ib++) {
                int k00 = base + ia * (N + 1) + ib, k10 = k00 + (N + 1), k01 = k00 + 1, k11 = k10 + 1;
                if (fA[k00] + fA[k10] + fA[k01] + fA[k11] == 0) continue;
                coin(ia, ib, k00, c, s, sx, sz, ox, oy, oz, u0, v0, du, dv, coul, a, lum);
                coin(ia, ib + 1, k01, c, s, sx, sz, ox, oy, oz, u0, v0, du, dv, coul, a, lum);
                coin(ia + 1, ib + 1, k11, c, s, sx, sz, ox, oy, oz, u0, v0, du, dv, coul, a, lum);
                coin(ia + 1, ib, k10, c, s, sx, sz, ox, oy, oz, u0, v0, du, dv, coul, a, lum);
            }
        }
    }

    private static void coin(int ia, int ib, int k, float c, float s, float sx, float sz, float ox, float oy, float oz, float u0, float v0, float du, float dv,
                             int coul, float a, int lum) {
        float u = ia / (float) N, v = ib / (float) N;
        float lu = (u * 2 - 1) * sx, lv = (v * 2 - 1) * sz;
        sommet(ox + lu * c - lv * s, oy + fH[k], oz + lu * s + lv * c, coul, alpha(a * fA[k]), u0 + u * du, v0 + v * dv, lum);
    }

    /** Etoiles de gelee sur les murs : decal sur la face du bloc, qui coule vers le bas (coulure qui s'allonge). */
    private static void dessinerMurs(float now) {
        for (int i = 0; i < nM; i++) {
            float age = now - mT0[i];
            float k = age / DUREE_MUR;
            float a = (age < 2 ? age / 2 : 1) * (k > 0.6f ? (1 - k) / 0.4f : 1f);
            Direction f = Direction.from3DDataValue(mFace[i]);
            float nx = f.getStepX(), ny = f.getStepY(), nz = f.getStepZ();
            // axes du plan du mur
            float ax, ay, az, bx, by, bz;
            if (Math.abs(ny) > 0.5f) {
                ax = 1;
                ay = 0;
                az = 0;
                bx = 0;
                by = 0;
                bz = 1;
            } else {
                ax = -nz;
                ay = 0;
                az = nx;
                bx = 0;
                by = 1;
                bz = 0;
            }
            float px = (float) (mX[i] - CX) + nx * 0.012f, py = (float) (mY[i] - CY) + ny * 0.012f, pz = (float) (mZ[i] - CZ) + nz * 0.012f;
            float sz = mTaille[i] * (age < 3 ? 0.6f + 0.4f * age / 3 : 1f);
            float cs = Mth.cos(mRot[i]) * sz, sn = Mth.sin(mRot[i]) * sz;
            float qx = ax * cs + bx * sn, qy = ay * cs + by * sn, qz = az * cs + bz * sn;
            float rx = -ax * sn + bx * cs, ry = -ay * sn + by * cs, rz = -az * sn + bz * cs;
            int pap = mPap[i];
            int al = alpha(0.95f * a);
            float u0 = SliqTypes.u0(SliqTypes.ECLAT), v0 = SliqTypes.v0(SliqTypes.ECLAT), u1 = SliqTypes.u1(SliqTypes.ECLAT), v1 = SliqTypes.v1(SliqTypes.ECLAT);
            sommet(px - qx - rx, py - qy - ry, pz - qz - rz, coul(pap), al, u0, v1, mLum[i]);
            sommet(px + qx - rx, py + qy - ry, pz + qz - rz, coul(pap), al, u1, v1, mLum[i]);
            sommet(px + qx + rx, py + qy + ry, pz + qz + rz, coul(pap), al, u1, v0, mLum[i]);
            sommet(px - qx + rx, py - qy + ry, pz - qz + rz, coul(pap), al, u0, v0, mLum[i]);
            if (Math.abs(ny) < 0.5f) {
                // coulure : une bande qui descend le long du mur
                float L = Math.min(1.4f, age * 0.035f) + 0.1f, w = sz * 0.35f;
                float cu0 = SliqTypes.u0(SliqTypes.COULURE), cv0 = SliqTypes.v0(SliqTypes.COULURE), cu1 = SliqTypes.u1(SliqTypes.COULURE), cv1 = SliqTypes.v1(SliqTypes.COULURE);
                float hx = ax * w, hz = az * w;
                sommet(px - hx, py, pz - hz, coul(pap), al, cu0, cv0, mLum[i]);
                sommet(px + hx, py, pz + hz, coul(pap), al, cu1, cv0, mLum[i]);
                sommet(px + hx, py - L, pz + hz, coul(pap), al, cu1, cv1, mLum[i]);
                sommet(px - hx, py - L, pz - hz, coul(pap), al, cu0, cv1, mLum[i]);
            }
        }
    }

    private static void dessinerBoules(float partial, float now, boolean lueur) {
        for (int i = 0; i < nB; i++) {
            int o = i * 6, pap = bPap[i];
            float ok = bOk[i] * (bArret[i] ? 1 : (float) Math.pow(0.78, partial));
            double x = Mth.lerp(partial, bP[i * 3], bE[o]) + bOx[i] * ok, y = Mth.lerp(partial, bP[i * 3 + 1], bE[o + 1]) + bOy[i] * ok,
                    z = Mth.lerp(partial, bP[i * 3 + 2], bE[o + 2]) + bOz[i] * ok;
            double vx = bE[o + 3], vy = bE[o + 4], vz = bE[o + 5];
            double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (vl < 1.0E-6) continue;
            double dx = vx / vl, dy = vy / vl, dz = vz / vl;
            float age = bAge[i] + partial;
            float fin = bArret[i] ? Math.max(0, 1 - (bFin[i] + partial) / 3f) : 1f;
            // de loin la boule reste lisible
            double ex = x - CX, ey = y - CY, ez = z - CZ;
            double dist = Math.sqrt(ex * ex + ey * ey + ez * ez);
            float big = (pap > 0 ? 1.15f : 1f) * Mth.clamp((float) Math.sqrt(dist / 10.0), 1f, 1.8f);
            // pres de l'oeil (bouche de la buse en premiere personne) : la boule ne bouche pas l'ecran
            float pres = Mth.clamp((float) (dist - 0.35) / 0.9f, 0.15f, 1f);
            int base = i * TR * 3;
            if (!lueur) {
                // trainee ondulante (gelee etiree)
                int n = bTrN[i];
                double px = x, py = y, pz = z;
                for (int s = 1; s < n; s++) {
                    double qx = bTr[base + s * 3], qy = bTr[base + s * 3 + 1], qz = bTr[base + s * 3 + 2];
                    float k0 = (s - 1f) / (TR - 1f), k1 = s / (TR - 1f);
                    float on0 = 1 + 0.25f * Mth.sin(age * 1.7f - s * 1.3f), on1 = 1 + 0.25f * Mth.sin(age * 1.7f - (s + 1) * 1.3f);
                    ruban((float) (px - CX), (float) (py - CY), (float) (pz - CZ), (float) (qx - CX), (float) (qy - CY), (float) (qz - CZ),
                            0.3f * big * (1 - k0) * on0, 0.3f * big * (1 - k1) * on1, SliqTypes.TRAINEE, coul(pap), 0.75f * (1 - k0) * fin * pres,
                            0.75f * (1 - k1) * fin * pres, PLEINE);
                    px = qx;
                    py = qy;
                    pz = qz;
                }
                if (bArret[i]) continue;
                // corps : goutte etiree le long de la vitesse, qui tremblote ; boule satellite plus claire
                float L = (0.32f + 0.05f * Mth.sin(age * 1.9f)) * big, W = (0.46f + 0.07f * Mth.sin(age * 2.3f + 1f)) * big;
                ruban((float) (ex - dx * L), (float) (ey - dy * L), (float) (ez - dz * L), (float) (ex + dx * L), (float) (ey + dy * L), (float) (ez + dz * L),
                        W, W * 0.92f, SliqTypes.GOUTTE, coul(pap), 0.98f * pres, 0.98f * pres, PLEINE);
                float orb = age * 0.9f;
                panneau(x + Mth.cos(orb) * 0.14 * big - dx * 0.25 * big, y + Mth.sin(orb * 1.3f) * 0.1 * big, z + Mth.sin(orb) * 0.14 * big - dz * 0.25 * big,
                        0.11f * big, 0, SliqTypes.GOUTTE, clair(pap), 0.95f * pres, PLEINE);
            } else {
                if (bArret[i]) continue;
                panneau(x, y, z, 0.95f * big, 0, SliqTypes.HALO, coul(pap), 0.32f * pres, 0);
                panneau(x - dx * 0.05, y - dy * 0.05, z - dz * 0.05, 0.16f * big, age * 0.2f, SliqTypes.COEUR, clair(pap), 0.55f * pres, 0);
                double sol = bSol[i];
                if (!Double.isNaN(sol)) {
                    float h = (float) (y - sol);
                    if (h >= 0 && h < 5f) disque(x, sol + 0.04, z, 1.25f * big, 0, SliqTypes.HALO, coul(pap), 0.22f * (1 - h / 5f), 0);
                }
            }
        }
    }

    private static void dessinerParticules(float partial, boolean lueur) {
        for (int i = 0; i < nP; i++) {
            byte g = pGenre[i];
            if ((g == P_ANNEAU) != lueur) continue;
            float k = (pVie[i] + partial) / pVieMax[i];
            if (k >= 1) continue;
            double x = Mth.lerp(partial, pPx[i], pX[i]), y = Mth.lerp(partial, pPy[i], pY[i]), z = Mth.lerp(partial, pPz[i], pZ[i]);
            double cx = x - CX, cy = y - CY, cz = z - CZ;
            if (cx * cx + cy * cy + cz * cz < 0.04) continue;
            switch (g) {
                case P_GOUTTE -> {
                    // goutte allongee dans le sens de sa chute
                    float vx = pVx[i], vy = pVy[i], vz = pVz[i];
                    float v2 = vx * vx + vy * vy + vz * vz;
                    if (v2 > 0.004f) {
                        float L = 1.2f;
                        ruban((float) (cx - vx * L), (float) (cy - vy * L), (float) (cz - vz * L), (float) cx, (float) cy, (float) cz, pTaille[i] * 0.9f,
                                pTaille[i] * 1.6f, SliqTypes.GOUTTE, pCoul[i], 0.9f * (1 - k * 0.3f), 0.95f * (1 - k * 0.3f), pLum[i]);
                    } else {
                        panneau(x, y, z, pTaille[i], pRot[i], SliqTypes.GOUTTE, pCoul[i], 0.95f * (1 - k * 0.3f), pLum[i]);
                    }
                }
                case P_BOUFFEE -> {
                    float entree = Math.min(1f, k * 5f);
                    panneau(x, y, z, pTaille[i] * (1 + k * 1.4f), pRot[i] + k * 1.1f, SliqTypes.BOUFFEE, pCoul[i], 0.6f * entree * (1 - k), pLum[i]);
                }
                case P_BULLE -> disque(x, y, z, pTaille[i] * (0.6f + k * 0.8f), pRot[i], SliqTypes.BULLE, pCoul[i], 0.85f * (1 - k), pLum[i]);
                case P_ETOILE -> disque(x, y, z, pTaille[i] * (0.8f + 0.35f * k), pRot[i], SliqTypes.ECLAT, pCoul[i], 0.9f * (1 - k * k), pLum[i]);
                case P_ANNEAU -> disque(x, y, z, pTaille[i] * (0.25f + 0.75f * (1 - (1 - k) * (1 - k))), pRot[i], SliqTypes.ANNEAU, pCoul[i], 0.7f * (1 - k), 0);
                default -> {
                }
            }
        }
    }

    // ================================================================== zombies : glissade (a la renverse) et fonte
    private static final LivingEntity[] PILE = new LivingEntity[8];
    private static final int[] PILE_MORT = new int[8], PILE_BLESSE = new int[8];
    private static int nPile;
    private static final Quaternionf Q = new Quaternionf();

    public static void vivantAvant(RenderLivingEvent.Pre<?, ?> e) {
        if (nZ == 0) return;
        LivingEntity le = e.getEntity();
        int id = le.getId();
        for (int i = 0; i < nZ; i++) {
            if (zId[i] != id) continue;
            float now = maintenant() + e.getPartialTick();
            boolean fond = zFonte[i] >= 0;
            float kf = fond ? Mth.clamp((now - zFonte[i]) / FONTE, 0, 1) : 0;
            if (fond && kf >= 0.97f) {
                // fondu : plus rien a dessiner (le serveur retire le corps a 20 ticks)
                e.setCanceled(true);
                return;
            }
            if (nPile >= PILE.length) return;
            float ga = Math.max(0, now - zT0[i]);
            // chute a la renverse : les pieds partent vers l'avant (sens de la glissade), le corps bascule en arriere
            float chute = zDx[i] * zDx[i] + zDz[i] * zDz[i] > 0.01f ? Math.min(1f, ga / 5f) : 0f;
            chute = chute * chute * (3 - 2 * chute);
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            if (chute > 0) {
                float ang = -1.25f * chute + 0.08f * Mth.sin(ga * 0.9f) * chute;
                // axe horizontal perpendiculaire a la glissade (pivot aux pieds)
                ps.mulPose(Q.setAngleAxis(ang, -zDz[i], 0, zDx[i]));
                ps.translate(0, 0.12f * chute, 0);
            }
            if (fond) {
                // fonte : le corps s'affaisse et s'etale (pas d'animation de mort rouge)
                float s = kf * kf * (3 - 2 * kf);
                ps.scale(1 + 0.7f * s, Math.max(0.03f, 1 - 0.97f * s), 1 + 0.7f * s);
                PILE_MORT[nPile] = le.deathTime;
                PILE_BLESSE[nPile] = le.hurtTime;
                le.deathTime = 0;
                le.hurtTime = 0;
            } else {
                PILE_MORT[nPile] = -1;
            }
            PILE[nPile++] = le;
            return;
        }
    }

    public static void vivantApres(RenderLivingEvent.Post<?, ?> e) {
        if (nPile > 0 && PILE[nPile - 1] == e.getEntity()) {
            e.getPoseStack().popPose();
            nPile--;
            if (PILE_MORT[nPile] >= 0) {
                e.getEntity().deathTime = PILE_MORT[nPile];
                e.getEntity().hurtTime = PILE_BLESSE[nPile];
            }
            PILE[nPile] = null;
        }
    }

    // ================================================================== menage, statistiques
    public static void dechargement(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) vider();
    }

    private static void vider() {
        nB = nF = nP = nM = nZ = 0;
        for (int i = 0; i < MAX_F; i++) fBoite[i] = null;
        for (int i = 0; i < nPile; i++) PILE[i] = null;
        nPile = 0;
    }

    public static String stats() {
        String r = String.format(Locale.ROOT,
                "rendu moyen %.3f ms (pire %.3f ms) sur %d images ; en cours : %d boules, %d flaques, %d particules, %d eclats de mur, %d zombies ; "
                        + "pics : %d/%d boules, %d/%d flaques, %d/%d particules, %d/%d zombies",
                images == 0 ? 0 : nsRendu / 1e6 / images, maxNs / 1e6, images, nB, nF, nP, nM, nZ, picB, MAX_B, picF, MAX_F, picP, MAX_P, picZ, MAX_Z);
        nsRendu = images = maxNs = 0;
        picB = picF = picP = picZ = 0;
        return r;
    }
}
