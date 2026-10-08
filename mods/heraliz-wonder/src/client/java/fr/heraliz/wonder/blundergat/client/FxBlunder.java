package fr.heraliz.wonder.blundergat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.wonder.blundergat.BlunderArmes;
import fr.heraliz.wonder.blundergat.BlunderSons;
import fr.heraliz.wonder.blundergat.CalculsBlunder;
import fr.heraliz.wonder.blundergat.net.Acide;
import fr.heraliz.wonder.blundergat.net.Eclats;
import fr.heraliz.wonder.client.ClientWonder;
import fr.heraliz.wonder.client.TaczClient;
import fr.heraliz.wonder.client.fx.FxTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
 * Effets du Blundergat et de l'Acid Gat cote client, dans des reserves de taille fixe (tableaux paralleles, aucune
 * allocation par image ni par particule), dessines en deux lots ({@link FxTypes#LUMIERE} additif, {@link FxTypes#FUMEE}
 * translucide) avec la planche des Wonder Weapons :
 * <ul>
 *   <li><b>bouche</b> : boule de feu de poudre, gerbe d'etincelles de bronze, anneau, fumee brune qui roule et monte,
 *   lumiere au sol, secousse de la camera du tireur (predite tout de suite) ;</li>
 *   <li><b>obus traceurs</b> : chaque obus est une tete brulante suivie d'une trainee, sur son vrai trajet et a sa vraie
 *   vitesse ; The Sweeper : obus incandescents (coeur blanc-or), trainee longue, braises semees le long du trajet ;</li>
 *   <li><b>impacts</b> (paquet {@link Eclats}) : etincelles, poussiere a la couleur du bloc, eclats du bloc ; sur une
 *   creature : giclee sombre, etincelles ;</li>
 *   <li><b>Acid Gat</b> (paquet {@link Acide}) : cartouches lumineuses en vol (meme physique que le serveur) qui collent,
 *   clignotent de plus en plus vite et gouttent ; explosion verte : eclair, anneaux, lumiere au sol, gerbe de gouttes
 *   d'acide, nuage toxique ; les creatures touchees bouillonnent, fument et tremblent ;</li>
 *   <li><b>Vitriolic Withering</b> : acide jaune-vert a eclats violets, explosion plus large.</li>
 * </ul>
 */
public final class FxBlunder {
    private FxBlunder() {
    }

    private static final RandomSource RNG = RandomSource.create();
    private static long tick;
    private static float dernierLocal = -1000;
    private static int dernierTireur = -1;
    private static float dernierTir = -1000;

    private static float maintenant() {
        return (float) tick;
    }

    // ------------------------------------------------------------------ couleurs
    static int lueurObus(int pap) {
        return pap > 0 ? 0xFF5418 : 0xFF9A38;
    }

    static int coeurObus(int pap) {
        return pap > 0 ? 0xFFF4CC : 0xFFE0A8;
    }

    static int lueurAcide(int pap) {
        return pap > 0 ? 0xD6FF1E : 0x52FF26;
    }

    static int coeurAcide(int pap) {
        return pap > 0 ? 0xFFFFC4 : 0xD8FF9C;
    }

    static int eclatAcide(int pap) {
        return pap > 0 ? 0xB648FF : 0x9DFF3A;
    }

    // ================================================================== reserves
    // ---- obus traceurs
    private static final int MAX_O = 192;
    private static final double[] oSx = new double[MAX_O], oSy = new double[MAX_O], oSz = new double[MAX_O];
    private static final double[] oEx = new double[MAX_O], oEy = new double[MAX_O], oEz = new double[MAX_O];
    private static final float[] oLen = new float[MAX_O], oVit = new float[MAX_O], oT0 = new float[MAX_O];
    private static final byte[] oPap = new byte[MAX_O];
    private static int nO;

    // ---- lueurs
    private static final int MAX_L = 192;
    private static final double[] lX = new double[MAX_L], lY = new double[MAX_L], lZ = new double[MAX_L];
    private static final float[] lT0 = new float[MAX_L], lDur = new float[MAX_L], lR0 = new float[MAX_L], lR1 = new float[MAX_L], lA = new float[MAX_L];
    private static final int[] lCoul = new int[MAX_L];
    private static final byte[] lGenre = new byte[MAX_L];
    private static int nL;
    private static final byte L_FLASH = 0, L_ANNEAU = 1, L_SOL = 2, L_ETOILE = 3, L_ANNEAU_SOL = 4, L_ONDE = 5;

    // ---- particules
    private static final int MAX_P = 1792;
    private static final double[] pX = new double[MAX_P], pY = new double[MAX_P], pZ = new double[MAX_P];
    private static final double[] pPx = new double[MAX_P], pPy = new double[MAX_P], pPz = new double[MAX_P];
    private static final float[] pVx = new float[MAX_P], pVy = new float[MAX_P], pVz = new float[MAX_P];
    private static final float[] pVie = new float[MAX_P], pVieMax = new float[MAX_P], pTaille = new float[MAX_P], pGrav = new float[MAX_P],
            pFrott = new float[MAX_P], pA = new float[MAX_P];
    private static final int[] pCoul = new int[MAX_P];
    private static final byte[] pGenre = new byte[MAX_P];
    private static int nP, nFumee;
    private static final byte P_ETINCELLE = 0, P_BRAISE = 1, P_FUMEE = 2, P_GOUTTE = 3, P_BULLE = 4, P_FEU = 5;

    // ---- cartouches d'acide
    private static final int MAX_C = 96;
    private static final byte C_VOL = 0, C_ENTITE = 1, C_BLOC = 2;
    private static final int[] cId = new int[MAX_C], cEnt = new int[MAX_C];
    private static final double[] cX = new double[MAX_C], cY = new double[MAX_C], cZ = new double[MAX_C], cPx = new double[MAX_C],
            cPy = new double[MAX_C], cPz = new double[MAX_C];
    private static final double[] cVx = new double[MAX_C], cVy = new double[MAX_C], cVz = new double[MAX_C];
    private static final float[] cOx = new float[MAX_C], cOy = new float[MAX_C], cOz = new float[MAX_C];      // decalage oeil -> canon (s'efface)
    private static final float[] cDx = new float[MAX_C], cDy = new float[MAX_C], cDz = new float[MAX_C];      // orientation
    private static final float[] cT0 = new float[MAX_C], cColle = new float[MAX_C];
    private static final byte[] cEtat = new byte[MAX_C], cPap = new byte[MAX_C];
    private static int nC;

    // ---- creatures qui fondent sous l'acide
    private static final int MAX_F = 64;
    private static final int[] fId = new int[MAX_F];
    private static final float[] fT0 = new float[MAX_F];
    private static final byte[] fPap = new byte[MAX_F];
    private static int nF;

    // ---- secousse de la camera du tireur
    private static float secT0 = -1000, secAmp;

    // ---- statistiques
    private static long nsRendu, maxNs;
    private static int images, picO, picL, picP, picC, picF, tirs, impacts, explosions;

    private static final float[] DEC = new float[3];
    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

    // ================================================================== entrees
    /** Tir du joueur local accepte par TaCZ : la bouche part tout de suite (prediction). */
    public static void tirLocal(LocalPlayer p, byte type, int pap) {
        Vec3 o = p.getEyePosition();
        decalageCanon(p);
        Vec3 d = p.getLookAngle();
        bouche(o.x + DEC[0], o.y + DEC[1], o.z + DEC[2], (float) d.x, (float) d.y, (float) d.z, type, pap, true);
        secT0 = maintenant();
        secAmp = type == BlunderArmes.ACIDGAT ? 1.6f : (pap > 0 ? 3.2f : 2.7f);
        dernierLocal = maintenant();
    }

    /** Un obus (balle TaCZ) arrive chez le client : trajet retrace, traceur dessine a sa vitesse. */
    public static void obus(Entity b, LivingEntity tireur, double sx, double sy, double sz, int pap) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Vec3 v = b.getDeltaMovement();
        double vl = v.length();
        if (vl < 1.0E-6) return;
        double dx = v.x / vl, dy = v.y / vl, dz = v.z / vl;
        // trajet : blocs, puis creatures (48 blocs au plus : la portee de la balle)
        Vec3 s = new Vec3(sx, sy, sz);
        Vec3 e = new Vec3(sx + dx * 48, sy + dy * 48, sz + dz * 48);
        BlockHitResult bh = lv.clip(new ClipContext(s, e, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b));
        if (bh.getType() != HitResult.Type.MISS) e = bh.getLocation();
        EntityHitResult eh = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(lv, tireur != null ? tireur : b, s, e,
                new AABB(s, e).inflate(1.0), x -> x instanceof LivingEntity le && le.isAlive() && x != tireur && !x.isSpectator(), 0.3f);
        if (eh != null) e = eh.getLocation();
        decalageCanon(tireur);
        double ox = sx + DEC[0], oy = sy + DEC[1], oz = sz + DEC[2];
        float now = maintenant();
        // la bouche (gerbe d'etincelles, fumee) une seule fois par tir, si elle n'a pas deja ete predite
        boolean local = tireur != null && tireur == mc.player && now - dernierLocal < 6;
        int id = tireur != null ? tireur.getId() : -1;
        if (!local && (id != dernierTireur || now - dernierTir > 3)) {
            bouche(ox, oy, oz, (float) dx, (float) dy, (float) dz, BlunderArmes.BLUNDERGAT, pap, false);
            dernierTireur = id;
            dernierTir = now;
        }
        if (nO >= MAX_O) return;
        int i = nO++;
        oSx[i] = ox;
        oSy[i] = oy;
        oSz[i] = oz;
        oEx[i] = e.x;
        oEy[i] = e.y;
        oEz[i] = e.z;
        double lx = e.x - ox, ly = e.y - oy, lz = e.z - oz;
        oLen[i] = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        // vitesse DESSINEE : au plus la vraie, mais le trajet dure au moins ~3 ticks (sinon un obus a 8 blocs ne se voit
        // qu'une image) ; l'impact, lui, vient du serveur
        oVit[i] = (float) Math.min(vl, Math.max(2.2, oLen[i] / 3.2));
        oT0[i] = now;
        oPap[i] = (byte) pap;
        if (nO > picO) picO = nO;
        if (pap > 0) {
            // The Sweeper : braises semees le long du trajet
            for (int k = 0; k < 3; k++) {
                float u = 0.15f + RNG.nextFloat() * 0.7f;
                particule(P_BRAISE, ox + lx * u, oy + ly * u, oz + lz * u, (RNG.nextFloat() - 0.5f) * 0.03f, 0.01f + RNG.nextFloat() * 0.02f,
                        (RNG.nextFloat() - 0.5f) * 0.03f, 0.05f + RNG.nextFloat() * 0.04f, 14 + RNG.nextInt(12), lueurObus(pap), -0.001f, 0.94f, 1f);
            }
        }
    }

    /** Flamme de bouche : Blundergat (poudre, etincelles de bronze, fumee) ou Acid Gat (bouffee verte). */
    private static void bouche(double x, double y, double z, float dx, float dy, float dz, byte type, int pap, boolean local) {
        Minecraft mc = Minecraft.getInstance();
        float now = maintenant();
        tirs++;
        if (type == BlunderArmes.ACIDGAT) {
            int c = lueurAcide(pap), k = coeurAcide(pap);
            lueurAjout(L_FLASH, x, y, z, 0.35f, 0.9f, 3, c, 0.7f, now);
            lueurAjout(L_SOL, x, solSous(x, y, z, 3), z, 1.6f, 1.8f, 5, c, 0.3f, now);
            for (int i = 0; i < 10; i++) particuleCone(P_GOUTTE, x, y, z, dx, dy, dz, 0.18f + RNG.nextFloat() * 0.25f, 0.5f, 0.05f + RNG.nextFloat() * 0.05f,
                    10 + RNG.nextInt(8), c, 0.035f, 0.92f, 1f);
            for (int i = 0; i < 6; i++) particuleCone(P_FUMEE, x, y, z, dx, dy, dz, 0.04f + RNG.nextFloat() * 0.06f, 0.6f, 0.22f + RNG.nextFloat() * 0.18f,
                    26 + RNG.nextInt(12), pap > 0 ? 0x9AA05A : 0x6E9A52, -0.002f, 0.93f, 0.5f);
            lueurAjout(L_ANNEAU, x + dx * 0.3, y + dy * 0.3, z + dz * 0.3, 0.15f, 0.7f, 4, k, 0.6f, now);
            return;
        }
        int c = lueurObus(pap), k = coeurObus(pap);
        // boule de feu de poudre noire : grand eclair, coeur blanc, etoile, langues de flamme qui roulent vers l'avant
        lueurAjout(L_FLASH, x + dx * 0.6, y + dy * 0.6, z + dz * 0.6, 1.0f, 2.5f, 4, c, 0.9f, now);
        lueurAjout(L_FLASH, x + dx * 0.3, y + dy * 0.3, z + dz * 0.3, 0.45f, 1.1f, 2.5f, k, 1f, now);
        lueurAjout(L_ETOILE, x + dx * 0.4, y + dy * 0.4, z + dz * 0.4, 2.1f, 0.8f, 3, k, 0.85f, now);
        lueurAjout(L_ANNEAU, x + dx * 0.8, y + dy * 0.8, z + dz * 0.8, 0.3f, 1.6f, 5, c, 0.35f, now);
        double sol = solSous(x, y, z, 3.5);
        lueurAjout(L_SOL, x, sol, z, 3.2f, 3.8f, 7, c, 0.5f, now);
        int nfeu = pap > 0 ? 16 : 12;
        for (int i = 0; i < nfeu; i++) {
            particuleCone(P_FEU, x + dx * 0.3, y + dy * 0.3, z + dz * 0.3, dx, dy, dz, 0.12f + RNG.nextFloat() * 0.38f, 0.42f, 0.32f + RNG.nextFloat() * 0.3f,
                    5 + RNG.nextInt(5), RNG.nextFloat() < 0.5f ? c : 0xFF7A20, -0.004f, 0.8f, 1f);
        }
        // gerbe d'etincelles de bronze dans un cone vers l'avant
        int ne = pap > 0 ? 52 : 40;
        for (int i = 0; i < ne; i++) {
            int col = RNG.nextFloat() < 0.35f ? k : (RNG.nextFloat() < 0.5f ? 0xFFB04A : c);
            particuleCone(P_ETINCELLE, x, y, z, dx, dy, dz, 0.5f + RNG.nextFloat() * 0.8f, 0.5f, 0.06f + RNG.nextFloat() * 0.05f, 8 + RNG.nextInt(9), col,
                    0.03f, 0.87f, 1f);
        }
        for (int i = 0; i < 14; i++) particuleCone(P_BRAISE, x, y, z, dx, dy, dz, 0.12f + RNG.nextFloat() * 0.3f, 0.7f, 0.06f + RNG.nextFloat() * 0.05f,
                16 + RNG.nextInt(16), c, 0.01f, 0.9f, 1f);
        // fumee brune epaisse qui roule vers l'avant puis monte
        int nf = local ? 10 : 14;
        for (int i = 0; i < nf; i++) {
            float av = 0.06f + RNG.nextFloat() * 0.22f;
            particuleCone(P_FUMEE, x + dx * (0.5 + i * 0.14), y + dy * (0.5 + i * 0.14), z + dz * (0.5 + i * 0.14), dx, dy, dz, av, 0.5f,
                    0.45f + RNG.nextFloat() * 0.35f, 45 + RNG.nextInt(30), RNG.nextFloat() < 0.5f ? 0x5A5048 : 0x766A5C, -0.003f, 0.9f, 0.72f);
        }
        if (mc.level != null && sol == sol && Math.abs(sol - y) < 3.2) {
            // poussiere soulevee au sol sous la bouche
            for (int i = 0; i < 4; i++) particule(P_FUMEE, x + dx * 1.5 + (RNG.nextFloat() - 0.5f), sol + 0.15, z + dz * 1.5 + (RNG.nextFloat() - 0.5f),
                    dx * 0.1f, 0.03f, dz * 0.1f, 0.4f + RNG.nextFloat() * 0.3f, 26 + RNG.nextInt(10), 0xB8AC98, -0.001f, 0.9f, 0.45f);
        }
    }

    /** Paquet du serveur : impacts des obus d'un tir. */
    public static void eclats(Eclats m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        float now = maintenant();
        int pap = m.pap();
        int c = lueurObus(pap), k = coeurObus(pap);
        boolean sonBloc = false, sonChair = false;
        for (int i = 0; i < m.n(); i++) {
            double x = m.pos()[i * 3], y = m.pos()[i * 3 + 1], z = m.pos()[i * 3 + 2];
            int face = m.faces()[i];
            boolean mort = (m.drapeaux()[i] & Eclats.MORT) != 0;
            impacts++;
            arreterObus(x, y, z);
            if (face >= 0) {
                Direction d = Direction.from3DDataValue(face);
                float nx = d.getStepX(), ny = d.getStepY(), nz = d.getStepZ();
                lueurAjout(L_FLASH, x, y, z, 0.25f, 0.55f, 3, c, 0.75f, now);
                for (int j = 0; j < 9; j++) {
                    particule(P_ETINCELLE, x, y, z, nx * 0.18f + (RNG.nextFloat() - 0.5f) * 0.42f, ny * 0.18f + RNG.nextFloat() * 0.3f,
                            nz * 0.18f + (RNG.nextFloat() - 0.5f) * 0.42f, 0.03f, 5 + RNG.nextInt(6), RNG.nextBoolean() ? k : 0xFFB04A, 0.04f, 0.86f, 1f);
                }
                // poussiere a la couleur du bloc touche, et eclats du bloc (particules de Minecraft)
                MP.set(Mth.floor(x - nx * 0.1), Mth.floor(y - ny * 0.1), Mth.floor(z - nz * 0.1));
                BlockState st = lv.getBlockState(MP);
                int teinte = st.isAir() ? 0xA09888 : melange(st.getMapColor(lv, MP).col, 0xD0C6B4, 0.45f);
                for (int j = 0; j < 3; j++) {
                    particule(P_FUMEE, x + nx * 0.1, y + ny * 0.1, z + nz * 0.1, nx * 0.04f + (RNG.nextFloat() - 0.5f) * 0.05f, ny * 0.04f + 0.02f,
                            nz * 0.04f + (RNG.nextFloat() - 0.5f) * 0.05f, 0.18f + RNG.nextFloat() * 0.14f, 18 + RNG.nextInt(12), teinte, -0.001f, 0.88f, 0.7f);
                }
                if (!st.isAir() && st.getRenderShape() != RenderShape.INVISIBLE && (i & 1) == 0) {
                    try {
                        for (int j = 0; j < 2; j++) {
                            Particle p = mc.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), x, y, z, 0, 0, 0);
                            if (p != null) p.setParticleSpeed(nx * 0.15 + (RNG.nextDouble() - 0.5) * 0.2, ny * 0.15 + RNG.nextDouble() * 0.2,
                                    nz * 0.15 + (RNG.nextDouble() - 0.5) * 0.2);
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
                if (!sonBloc) {
                    son(BlunderSons.IMPACT.get(), x, y, z, 0.8f, (pap > 0 ? 0.85f : 1f) + RNG.nextFloat() * 0.15f);
                    sonBloc = true;
                }
            } else {
                // creature : giclee sombre et etincelles de l'obus brulant
                lueurAjout(L_FLASH, x, y, z, 0.2f, mort ? 0.7f : 0.45f, 3, c, 0.6f, now);
                int ns = mort ? 4 : 2;
                for (int j = 0; j < ns; j++) {
                    particule(P_FUMEE, x, y, z, (RNG.nextFloat() - 0.5f) * 0.08f, (RNG.nextFloat() - 0.2f) * 0.06f, (RNG.nextFloat() - 0.5f) * 0.08f,
                            0.16f + RNG.nextFloat() * 0.16f, 12 + RNG.nextInt(10), 0x5C1009, 0.004f, 0.86f, 0.8f);
                }
                for (int j = 0; j < 5; j++) {
                    particule(P_ETINCELLE, x, y, z, (RNG.nextFloat() - 0.5f) * 0.4f, RNG.nextFloat() * 0.25f, (RNG.nextFloat() - 0.5f) * 0.4f, 0.025f,
                            4 + RNG.nextInt(5), k, 0.04f, 0.86f, 1f);
                }
                if (!sonChair) {
                    son(BlunderSons.CHAIR.get(), x, y, z, 0.7f, 0.9f + RNG.nextFloat() * 0.2f);
                    sonChair = true;
                }
            }
        }
    }

    /** L'obus traceur le plus proche de l'impact s'arrete la (sinon il continuerait jusqu'au bout de son trajet retrace). */
    private static void arreterObus(double x, double y, double z) {
        float now = maintenant();
        int best = -1;
        double bd = 2.5 * 2.5;
        for (int i = 0; i < nO; i++) {
            double dx = oEx[i] - x, dy = oEy[i] - y, dz = oEz[i] - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (best < 0) return;
        oEx[best] = x;
        oEy[best] = y;
        oEz[best] = z;
        double lx = x - oSx[best], ly = y - oSy[best], lz = z - oSz[best];
        oLen[best] = Math.min(oLen[best], (float) Math.sqrt(lx * lx + ly * ly + lz * lz));
        if ((now - oT0[best]) * oVit[best] > oLen[best]) oT0[best] = now - oLen[best] / Math.max(oVit[best], 0.1f);
    }

    /** Paquet du serveur : cartouches d'acide. */
    public static void acide(Acide m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        float now = maintenant();
        switch (m.genre()) {
            case Acide.TIR -> {
                Entity t = lv.getEntity(m.tireur());
                boolean local = t != null && t == mc.player && now - dernierLocal < 6;
                if (t instanceof LivingEntity le) decalageCanon(le);
                else DEC[0] = DEC[1] = DEC[2] = 0;
                float[] v = m.vec();
                if (!local && m.n() > 0) {
                    double vl = Math.sqrt(v[3] * v[3] + v[4] * v[4] + v[5] * v[5]);
                    if (vl > 1e-6 && t != null) {
                        Vec3 o = t.getEyePosition();
                        bouche(o.x + DEC[0], o.y + DEC[1], o.z + DEC[2], (float) (v[3] / vl), (float) (v[4] / vl), (float) (v[5] / vl), BlunderArmes.ACIDGAT,
                                m.pap(), false);
                    }
                }
                for (int i = 0; i < m.n() && nC < MAX_C; i++) {
                    int c = nC++;
                    cId[c] = m.ids()[i];
                    cEtat[c] = C_VOL;
                    cPap[c] = m.pap();
                    cX[c] = cPx[c] = v[i * 6];
                    cY[c] = cPy[c] = v[i * 6 + 1];
                    cZ[c] = cPz[c] = v[i * 6 + 2];
                    cVx[c] = v[i * 6 + 3];
                    cVy[c] = v[i * 6 + 4];
                    cVz[c] = v[i * 6 + 5];
                    // le serveur part des yeux : la cartouche dessinee part du canon et rejoint sa trajectoire en 6 ticks
                    Vec3 o = t != null ? t.getEyePosition() : new Vec3(cX[c], cY[c], cZ[c]);
                    cOx[c] = (float) (o.x + DEC[0] - cX[c]);
                    cOy[c] = (float) (o.y + DEC[1] - cY[c]);
                    cOz[c] = (float) (o.z + DEC[2] - cZ[c]);
                    double vl = Math.sqrt(cVx[c] * cVx[c] + cVy[c] * cVy[c] + cVz[c] * cVz[c]);
                    cDx[c] = (float) (cVx[c] / Math.max(vl, 1e-6));
                    cDy[c] = (float) (cVy[c] / Math.max(vl, 1e-6));
                    cDz[c] = (float) (cVz[c] / Math.max(vl, 1e-6));
                    cT0[c] = now;
                    cColle[c] = -1;
                    cEnt[c] = -1;
                    if (nC > picC) picC = nC;
                }
            }
            case Acide.COLLE -> {
                int c = trouver(m.ids()[0]);
                if (c < 0) {
                    if (nC >= MAX_C) return;
                    c = nC++;
                    cId[c] = m.ids()[0];
                    cPap[c] = 0;
                    cT0[c] = now;
                    cDx[c] = 0;
                    cDy[c] = -1;
                    cDz[c] = 0;
                }
                float x = m.vec()[0], y = m.vec()[1], z = m.vec()[2];
                cX[c] = cPx[c] = x;
                cY[c] = cPy[c] = y;
                cZ[c] = cPz[c] = z;
                cOx[c] = cOy[c] = cOz[c] = 0;
                cColle[c] = now;
                Entity e = m.entite() >= 0 ? lv.getEntity(m.entite()) : null;
                if (e != null) {
                    cEtat[c] = C_ENTITE;
                    cEnt[c] = e.getId();
                    cVx[c] = x - e.getX();
                    cVy[c] = y - e.getY();
                    cVz[c] = z - e.getZ();
                } else {
                    cEtat[c] = C_BLOC;
                    cEnt[c] = -1;
                }
                int col = lueurAcide(cPap[c]);
                for (int i = 0; i < 6; i++) particuleAleatoire(P_GOUTTE, x, y, z, 0.06f + RNG.nextFloat() * 0.1f, 0.04f + RNG.nextFloat() * 0.03f,
                        10 + RNG.nextInt(8), col, 0.03f, 0.9f);
                lueurAjout(L_FLASH, x, y, z, 0.2f, 0.5f, 3, col, 0.7f, now);
                son(BlunderSons.ACIDE_COLLE.get(), x, y, z, 0.7f, 0.9f + RNG.nextFloat() * 0.25f);
            }
            default -> {
                int c = trouver(m.ids()[0]);
                if (c >= 0) retirerCartouche(c);
                exploserAcide(lv, m.vec()[0], m.vec()[1], m.vec()[2], m.rayon() / 10f, m.pap());
                for (int i = 1; i < m.n(); i++) fondre(m.ids()[i], m.pap(), i);
            }
        }
    }

    private static int trouver(int id) {
        for (int i = 0; i < nC; i++) if (cId[i] == id) return i;
        return -1;
    }

    private static void exploserAcide(ClientLevel lv, double x, double y, double z, float r, int pap) {
        float now = maintenant();
        explosions++;
        int c = lueurAcide(pap), k = coeurAcide(pap), e = eclatAcide(pap);
        r = Math.max(1.0f, r);
        lueurAjout(L_FLASH, x, y, z, r * 0.5f, r * 1.25f, 6, c, 0.95f, now);
        lueurAjout(L_FLASH, x, y, z, r * 0.2f, r * 0.45f, 3, k, 1f, now);
        lueurAjout(L_ETOILE, x, y, z, r * 0.9f, r * 0.5f, 4, k, 0.7f, now);
        lueurAjout(L_ANNEAU, x, y, z, 0.3f, r * 1.1f, 7, c, 0.85f, now);
        lueurAjout(L_ONDE, x, y, z, 0.2f, r * 1.4f, 9, e, 0.6f, now + 1);
        double sol = solSous(x, y + 0.3, z, 3.5);
        lueurAjout(L_SOL, x, sol, z, r * 1.4f, r * 1.7f, 26, c, 0.55f, now);
        if (sol == sol && Math.abs(sol - y) < 1.5) lueurAjout(L_ANNEAU_SOL, x, sol, z, 0.4f, r * 1.2f, 10, c, 0.65f, now);
        // gerbe de gouttes d'acide, eclats et bulles
        for (int i = 0; i < 34; i++) particuleAleatoire(P_GOUTTE, x, y, z, 0.16f + RNG.nextFloat() * 0.32f, 0.06f + RNG.nextFloat() * 0.07f,
                14 + RNG.nextInt(14), RNG.nextFloat() < 0.2f ? e : c, 0.04f, 0.93f);
        for (int i = 0; i < 18; i++) particuleAleatoire(P_ETINCELLE, x, y, z, 0.3f + RNG.nextFloat() * 0.4f, 0.03f, 6 + RNG.nextInt(6), k, 0.03f, 0.87f);
        for (int i = 0; i < 10; i++) particuleAleatoire(P_BULLE, x, y, z, 0.03f + RNG.nextFloat() * 0.06f, 0.07f + RNG.nextFloat() * 0.06f,
                20 + RNG.nextInt(16), k, -0.004f, 0.94f);
        // nuage toxique qui traine
        for (int i = 0; i < 12; i++) {
            float a = RNG.nextFloat() * Mth.TWO_PI, rr = RNG.nextFloat() * r * 0.6f;
            particule(P_FUMEE, x + Mth.cos(a) * rr, y + RNG.nextFloat() * 0.6, z + Mth.sin(a) * rr, Mth.cos(a) * 0.03f, 0.01f + RNG.nextFloat() * 0.02f,
                    Mth.sin(a) * 0.03f, 0.5f + RNG.nextFloat() * 0.5f, 40 + RNG.nextInt(30), pap > 0 ? 0xA8AE4E : 0x5E9640, -0.0006f, 0.95f, 0.55f);
        }
        son(BlunderSons.ACIDE_EXPLOSION.get(), x, y, z, 1.6f, (pap > 0 ? 0.88f : 1f) + RNG.nextFloat() * 0.12f);
    }

    private static void fondre(int id, int pap, int rang) {
        int i = -1;
        for (int j = 0; j < nF; j++) if (fId[j] == id) i = j;
        if (i < 0) {
            if (nF >= MAX_F) return;
            i = nF++;
        }
        fId[i] = id;
        fT0[i] = maintenant();
        fPap[i] = (byte) pap;
        if (nF > picF) picF = nF;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        if (e != null && rang <= 3) son(BlunderSons.ACIDE_GRESILLE.get(), e.getX(), e.getY() + 1, e.getZ(), 0.7f, 0.85f + RNG.nextFloat() * 0.3f);
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
        ClientLevel lv = mc.level;
        // obus : liberes quand la trainee a rejoint le point d'impact
        int w = 0;
        for (int i = 0; i < nO; i++) {
            float d = (now - oT0[i]) * oVit[i];
            if (d - longueurTrainee(oPap[i]) < oLen[i]) copierObus(i, w++);
        }
        nO = w;
        w = 0;
        for (int i = 0; i < nL; i++) if (now - lT0[i] <= lDur[i]) copierLueur(i, w++);
        nL = w;
        tickCartouches(lv, now);
        tickFondus(lv, now);
        tickParticules();
    }

    private static float longueurTrainee(int pap) {
        return pap > 0 ? 5.2f : 3.4f;
    }

    private static void tickCartouches(ClientLevel lv, float now) {
        int w = 0;
        for (int i = 0; i < nC; i++) {
            cPx[i] = cX[i];
            cPy[i] = cY[i];
            cPz[i] = cZ[i];
            int col = lueurAcide(cPap[i]);
            boolean garder = now - cT0[i] < 140;
            switch (cEtat[i]) {
                case C_VOL -> {
                    double[] p = POS, v = VIT;
                    p[0] = cX[i];
                    p[1] = cY[i];
                    p[2] = cZ[i];
                    v[0] = cVx[i];
                    v[1] = cVy[i];
                    v[2] = cVz[i];
                    CalculsBlunder.pas(p, v);
                    if (dansBloc(lv, p[0], p[1], p[2])) {
                        cEtat[i] = C_BLOC;          // arret visuel en attendant le serveur
                        if (cColle[i] < 0) cColle[i] = now;
                    } else {
                        cX[i] = p[0];
                        cY[i] = p[1];
                        cZ[i] = p[2];
                        cVx[i] = v[0];
                        cVy[i] = v[1];
                        cVz[i] = v[2];
                        double vl = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                        if (vl > 1e-6) {
                            cDx[i] = (float) (v[0] / vl);
                            cDy[i] = (float) (v[1] / vl);
                            cDz[i] = (float) (v[2] / vl);
                        }
                        // trainee de gouttelettes
                        if ((tick + i) % 2 == 0) particule(P_BULLE, cX[i], cY[i], cZ[i], (RNG.nextFloat() - 0.5f) * 0.02f, -0.01f,
                                (RNG.nextFloat() - 0.5f) * 0.02f, 0.035f, 10 + RNG.nextInt(6), col, 0.01f, 0.95f, 1f);
                    }
                }
                case C_ENTITE -> {
                    Entity en = lv.getEntity(cEnt[i]);
                    if (en != null && !en.isRemoved()) {
                        cX[i] = en.getX() + cVx[i];
                        cY[i] = en.getY() + cVy[i];
                        cZ[i] = en.getZ() + cVz[i];
                    } else {
                        cEtat[i] = C_BLOC;
                    }
                }
                default -> {
                }
            }
            if (cColle[i] >= 0) {
                float age = now - cColle[i];
                // gouttes qui tombent, bip qui accelere (une cartouche sur trois bipe, pour ne pas saturer)
                if ((tick + i) % 3 == 0) particule(P_GOUTTE, cX[i], cY[i] - 0.05, cZ[i], 0, -0.02f, 0, 0.035f, 14, col, 0.03f, 0.97f, 1f);
                float avant = CalculsBlunder.clignote(age - 1, 30), apres = CalculsBlunder.clignote(age, 30);
                if (apres > avant && cId[i] % 3 == 0 && age < 34) son(BlunderSons.ACIDE_BIP.get(), cX[i], cY[i], cZ[i], 0.45f, 1.0f + Math.min(1, age / 30f) * 0.5f);
            }
            if (garder) copierCartouche(i, w++);
        }
        nC = w;
    }

    private static final double[] POS = new double[3], VIT = new double[3];

    private static void tickFondus(ClientLevel lv, float now) {
        int w = 0;
        for (int i = 0; i < nF; i++) {
            float age = now - fT0[i];
            if (age > 40) continue;
            Entity e = lv.getEntity(fId[i]);
            if (e != null) {
                double h = e.getBbHeight(), wd = e.getBbWidth();
                float k = age / 40f;
                int col = lueurAcide(fPap[i]);
                if (RNG.nextFloat() < 0.8f * (1 - k)) {
                    particule(P_BULLE, e.getX() + (RNG.nextDouble() - 0.5) * wd, e.getY() + RNG.nextDouble() * h, e.getZ() + (RNG.nextDouble() - 0.5) * wd,
                            (RNG.nextFloat() - 0.5f) * 0.02f, 0.02f + RNG.nextFloat() * 0.03f, (RNG.nextFloat() - 0.5f) * 0.02f, 0.05f + RNG.nextFloat() * 0.05f,
                            10 + RNG.nextInt(8), col, -0.002f, 0.95f, 1f);
                }
                if ((tick + i) % 2 == 0) {
                    particule(P_FUMEE, e.getX() + (RNG.nextDouble() - 0.5) * wd, e.getY() + h * (0.3 + RNG.nextDouble() * 0.7), e.getZ() + (RNG.nextDouble() - 0.5) * wd,
                            (RNG.nextFloat() - 0.5f) * 0.01f, 0.03f + RNG.nextFloat() * 0.02f, (RNG.nextFloat() - 0.5f) * 0.01f, 0.25f + RNG.nextFloat() * 0.2f,
                            24 + RNG.nextInt(14), fPap[i] > 0 ? 0x9CA45A : 0x6A9050, -0.0008f, 0.96f, 0.5f);
                }
                if ((tick + i) % 4 == 0) particule(P_GOUTTE, e.getX() + (RNG.nextDouble() - 0.5) * wd, e.getY() + h * RNG.nextDouble(), e.getZ() + (RNG.nextDouble() - 0.5) * wd,
                        0, -0.01f, 0, 0.04f, 12, col, 0.03f, 0.96f, 1f);
            }
            copierFondu(i, w++);
        }
        nF = w;
    }

    // ================================================================== particules
    private static void particuleAleatoire(byte genre, double x, double y, double z, float vitesse, float taille, int vie, int coul, float grav, float frott) {
        double a = RNG.nextDouble() * Math.PI * 2, u = RNG.nextDouble() * 2 - 1, s = Math.sqrt(1 - u * u);
        particule(genre, x, y, z, (float) (Math.cos(a) * s * vitesse), (float) (u * vitesse) + vitesse * 0.3f, (float) (Math.sin(a) * s * vitesse), taille, vie, coul,
                grav, frott, 1f);
    }

    /** Particule lancee dans un cone d'axe (dx, dy, dz) unitaire, d'ouverture {@code ouv} (0 = droit, 1 = 90°). */
    private static void particuleCone(byte genre, double x, double y, double z, float dx, float dy, float dz, float vitesse, float ouv, float taille, int vie,
                                      int coul, float grav, float frott, float a) {
        float rx = (RNG.nextFloat() * 2 - 1) * ouv, ry = (RNG.nextFloat() * 2 - 1) * ouv, rz = (RNG.nextFloat() * 2 - 1) * ouv;
        float vx = dx + rx, vy = dy + ry, vz = dz + rz;
        float n = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (n < 1e-5f) return;
        particule(genre, x, y, z, vx / n * vitesse, vy / n * vitesse, vz / n * vitesse, taille, vie, coul, grav, frott, a);
    }

    private static void particule(byte genre, double x, double y, double z, float vx, float vy, float vz, float taille, int vie, int coul, float grav,
                                  float frott, float a) {
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
        pA[i] = a;
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
                pA[w] = pA[i];
            }
            w++;
        }
        nP = w;
        nFumee = fumee;
    }

    // ================================================================== copies (compactage des reserves)
    private static void copierObus(int i, int w) {
        if (i == w) return;
        oSx[w] = oSx[i];
        oSy[w] = oSy[i];
        oSz[w] = oSz[i];
        oEx[w] = oEx[i];
        oEy[w] = oEy[i];
        oEz[w] = oEz[i];
        oLen[w] = oLen[i];
        oVit[w] = oVit[i];
        oT0[w] = oT0[i];
        oPap[w] = oPap[i];
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

    private static void copierCartouche(int i, int w) {
        if (i == w) return;
        cId[w] = cId[i];
        cEnt[w] = cEnt[i];
        cX[w] = cX[i];
        cY[w] = cY[i];
        cZ[w] = cZ[i];
        cPx[w] = cPx[i];
        cPy[w] = cPy[i];
        cPz[w] = cPz[i];
        cVx[w] = cVx[i];
        cVy[w] = cVy[i];
        cVz[w] = cVz[i];
        cOx[w] = cOx[i];
        cOy[w] = cOy[i];
        cOz[w] = cOz[i];
        cDx[w] = cDx[i];
        cDy[w] = cDy[i];
        cDz[w] = cDz[i];
        cT0[w] = cT0[i];
        cColle[w] = cColle[i];
        cEtat[w] = cEtat[i];
        cPap[w] = cPap[i];
    }

    private static void retirerCartouche(int i) {
        int d = --nC;
        if (i != d) copierCartouche(d, i);
    }

    private static void copierFondu(int i, int w) {
        if (i == w) return;
        fId[w] = fId[i];
        fT0[w] = fT0[i];
        fPap[w] = fPap[i];
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
        lT0[i] = t0;
        if (nL > picL) picL = nL;
    }

    // ================================================================== outils monde
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
            double top = MP.getY() + sh.max(Direction.Axis.Y);
            if (top <= y + 0.01 && y - top <= max) return top;
        }
        return Double.NaN;
    }

    private static int melange(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Decalage oeil -> canon du tireur dans {@link #DEC} (premiere personne : position calculee par TaCZ). */
    private static void decalageCanon(LivingEntity tireur) {
        DEC[0] = DEC[1] = DEC[2] = 0;
        if (tireur == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (tireur == mc.player && ClientWonder.tacz && TaczClient.premierePersonne() && TaczClient.decalageCanon(mc.gameRenderer.getMainCamera(), DEC)) return;
        float yaw = tireur.getYRot() * Mth.DEG_TO_RAD, pitch = tireur.getXRot() * Mth.DEG_TO_RAD;
        float cp = Mth.cos(pitch);
        float lx = -Mth.sin(yaw) * cp, ly = -Mth.sin(pitch), lz = Mth.cos(yaw) * cp;
        float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        DEC[0] = lx * 1.5f + rx * 0.3f;
        DEC[1] = ly * 1.5f - 0.3f;
        DEC[2] = lz * 1.5f + rz * 0.3f;
    }

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
        if (nO == 0 && nL == 0 && nP == 0 && nC == 0) return;
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
        if (nFumee > 0 || nC > 0) {
            VC = buf.getBuffer(FxTypes.FUMEE);
            dessinerParticules(partial, true);
            dessinerCartouches(mc.level, partial, now, true);
            buf.endBatch(FxTypes.FUMEE);
        }
        VC = buf.getBuffer(FxTypes.LUMIERE);
        dessinerLueurs(now);
        dessinerObus(now);
        dessinerCartouches(mc.level, partial, now, false);
        dessinerParticules(partial, false);
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

    private static void panneau(double x, double y, double z, float r, float rot, int cell, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float px = (float) (x - CX), py = (float) (y - CY), pz = (float) (z - CZ);
        float cs = Mth.cos(rot) * r, sn = Mth.sin(rot) * r;
        float ax = LX * cs + UX * sn, ay = LY * cs + UY * sn, az = LZ * cs + UZ * sn;
        float bx = -LX * sn + UX * cs, by = -LY * sn + UY * cs, bz = -LZ * sn + UZ * cs;
        float u0 = FxTypes.u0(cell), v0 = FxTypes.v0(cell), u1 = FxTypes.u1(cell), v1 = FxTypes.v1(cell);
        sommet(px - ax - bx, py - ay - by, pz - az - bz, coul, al, u0, v1);
        sommet(px + ax - bx, py + ay - by, pz + az - bz, coul, al, u1, v1);
        sommet(px + ax + bx, py + ay + by, pz + az + bz, coul, al, u1, v0);
        sommet(px - ax + bx, py - ay + by, pz - az + bz, coul, al, u0, v0);
    }

    private static void disque(double x, double y, double z, float r, int cell, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float px = (float) (x - CX), py = (float) (y - CY), pz = (float) (z - CZ);
        float u0 = FxTypes.u0(cell), v0 = FxTypes.v0(cell), u1 = FxTypes.u1(cell), v1 = FxTypes.v1(cell);
        sommet(px - r, py, pz - r, coul, al, u0, v0);
        sommet(px - r, py, pz + r, coul, al, u0, v1);
        sommet(px + r, py, pz + r, coul, al, u1, v1);
        sommet(px + r, py, pz - r, coul, al, u1, v0);
    }

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
            if (lGenre[i] != L_SOL && lGenre[i] != L_ANNEAU_SOL) {
                double dx = lX[i] - CX, dy = lY[i] - CY, dz = lZ[i] - CZ;
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                a *= Mth.clamp(d / (r * 2.2f + 0.5f), 0.18f, 1f);
            }
            switch (lGenre[i]) {
                case L_FLASH -> panneau(lX[i], lY[i], lZ[i], r, 0, FxTypes.LUEUR, lCoul[i], a * (1 - k));
                case L_ANNEAU -> panneau(lX[i], lY[i], lZ[i], r, 0, FxTypes.ANNEAU, lCoul[i], a);
                case L_SOL -> disque(lX[i], lY[i] + 0.03, lZ[i], r, FxTypes.DISQUE, lCoul[i], a);
                case L_ETOILE -> panneau(lX[i], lY[i], lZ[i], r, now * 0.15f + i, FxTypes.ETOILE, lCoul[i], a);
                case L_ANNEAU_SOL -> disque(lX[i], lY[i] + 0.04, lZ[i], r, FxTypes.ANNEAU, lCoul[i], a);
                case L_ONDE -> panneau(lX[i], lY[i], lZ[i], r, 0, FxTypes.ONDE, lCoul[i], a);
                default -> {
                }
            }
        }
    }

    /** Obus traceurs : tete brulante et trainee qui file a la vitesse de la balle, puis se resorbe sur le point d'impact. */
    private static void dessinerObus(float now) {
        for (int i = 0; i < nO; i++) {
            float len = oLen[i];
            if (len < 0.05f) continue;
            float d = (now - oT0[i]) * oVit[i];
            float L = longueurTrainee(oPap[i]);
            float tete = Math.min(len, d), queue = Math.max(0, d - L);
            if (queue >= len) continue;
            float ix = (float) ((oEx[i] - oSx[i]) / len), iy = (float) ((oEy[i] - oSy[i]) / len), iz = (float) ((oEz[i] - oSz[i]) / len);
            double hx = oSx[i] + ix * tete, hy = oSy[i] + iy * tete, hz = oSz[i] + iz * tete;
            double qx = oSx[i] + ix * queue, qy = oSy[i] + iy * queue, qz = oSz[i] + iz * queue;
            int pap = oPap[i];
            int c = lueurObus(pap), k = coeurObus(pap);
            // lisible de loin : largeur compensee avec la distance
            double ex = hx - CX, ey = hy - CY, ez = hz - CZ;
            float loin = Mth.clamp((float) Math.sqrt(Math.sqrt(ex * ex + ey * ey + ez * ez) / 8.0), 1f, 2f);
            float fort = pap > 0 ? 1f : 0.85f;
            boolean arrive = d >= len;
            float fin = arrive ? Math.max(0, 1 - (d - len) / L) : 1f;
            rubanMonde(qx, qy, qz, hx, hy, hz, 0.05f * loin, (pap > 0 ? 0.3f : 0.22f) * loin, FxTypes.FAISCEAU, c, 0f, 0.85f * fort * fin);
            rubanMonde(qx, qy, qz, hx, hy, hz, 0.015f * loin, (pap > 0 ? 0.09f : 0.065f) * loin, FxTypes.ARC, k, 0f, 1f * fin);
            if (!arrive) {
                panneau(hx, hy, hz, (pap > 0 ? 0.48f : 0.34f) * loin, 0, FxTypes.LUEUR, c, 0.7f * fort);
                panneau(hx, hy, hz, (pap > 0 ? 0.14f : 0.1f) * loin, 0, FxTypes.COEUR, k, 1f);
            }
        }
    }

    /**
     * Cartouches d'acide : passe translucide = culots sombres ; passe additive = corps de verre lumineux, halo qui clignote
     * de plus en plus vite une fois collee.
     */
    private static void dessinerCartouches(ClientLevel lv, float partial, float now, boolean voile) {
        if (lv == null) return;
        for (int i = 0; i < nC; i++) {
            double x = Mth.lerp(partial, cPx[i], cX[i]), y = Mth.lerp(partial, cPy[i], cY[i]), z = Mth.lerp(partial, cPz[i], cZ[i]);
            if (cEtat[i] == C_ENTITE) {
                Entity en = lv.getEntity(cEnt[i]);
                if (en != null) {
                    x = Mth.lerp(partial, en.xo, en.getX()) + cVx[i];
                    y = Mth.lerp(partial, en.yo, en.getY()) + cVy[i];
                    z = Mth.lerp(partial, en.zo, en.getZ()) + cVz[i];
                }
            }
            float age = now - cT0[i];
            float f = Math.max(0, 1 - age / 6f);
            x += cOx[i] * f;
            y += cOy[i] * f;
            z += cOz[i] * f;
            double dx = x - CX, dy = y - CY, dz = z - CZ;
            if (dx * dx + dy * dy + dz * dz < 0.12) continue;
            float L = 0.3f;
            double ax = x - cDx[i] * L, ay = y - cDy[i] * L, az = z - cDz[i] * L, bx = x + cDx[i] * L, by = y + cDy[i] * L, bz = z + cDz[i] * L;
            int pap = cPap[i];
            if (voile) {
                rubanMonde(ax - cDx[i] * 0.08, ay - cDy[i] * 0.08, az - cDz[i] * 0.08, ax + cDx[i] * 0.08, ay + cDy[i] * 0.08, az + cDz[i] * 0.08, 0.28f, 0.28f,
                        FxTypes.FAISCEAU, 0x2A2A22, 0.9f, 0.9f);
                rubanMonde(bx - cDx[i] * 0.08, by - cDy[i] * 0.08, bz - cDz[i] * 0.08, bx + cDx[i] * 0.08, by + cDy[i] * 0.08, bz + cDz[i] * 0.08, 0.28f, 0.28f,
                        FxTypes.FAISCEAU, 0x3A3222, 0.9f, 0.9f);
                continue;
            }
            float clig = cColle[i] >= 0 ? CalculsBlunder.clignote(now - cColle[i], 30) : 0.6f;
            int c = lueurAcide(pap), k = coeurAcide(pap);
            if (cEtat[i] == C_VOL) {
                // trainee de vol
                rubanMonde(x - cDx[i] * 1.3, y - cDy[i] * 1.3, z - cDz[i] * 1.3, x, y, z, 0.03f, 0.22f, FxTypes.FAISCEAU, c, 0f, 0.6f);
            }
            rubanMonde(ax, ay, az, bx, by, bz, 0.24f, 0.24f, FxTypes.FAISCEAU, c, 0.95f, 0.95f);
            rubanMonde(ax, ay, az, bx, by, bz, 0.09f, 0.09f, FxTypes.ARC, k, 0.95f, 0.95f);
            panneau(x, y, z, 0.45f + 0.55f * clig, 0, FxTypes.LUEUR, c, 0.35f + 0.5f * clig);
            panneau(x, y, z, 0.13f + 0.06f * clig, 0, FxTypes.COEUR, k, 0.6f + 0.4f * clig);
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
            // pres de l'oeil (bouche du canon en premiere personne) : la fumee ne voile pas l'ecran
            float pres = d2 < 4 ? (float) (Math.sqrt(d2) / 2) : 1f;
            switch (g) {
                case P_ETINCELLE -> {
                    float L = 1.7f;
                    rubanMonde(x - pVx[i] * L, y - pVy[i] * L, z - pVz[i] * L, x, y, z, pTaille[i] * 0.3f, pTaille[i], FxTypes.ETINCELLE, pCoul[i], 0.0f, (1 - k) * pres);
                }
                case P_BRAISE -> {
                    float sc = 0.75f + 0.25f * Mth.sin((pVie[i] + partial) * 1.7f + i);
                    panneau(x, y, z, pTaille[i] * 1.7f, 0, FxTypes.LUEUR, pCoul[i], (1 - k) * 0.45f * pres);
                    panneau(x, y, z, pTaille[i] * sc, 0, FxTypes.COEUR, 0xFFE8B0, (1 - k) * 0.9f * pres);
                }
                case P_GOUTTE -> {
                    panneau(x, y, z, pTaille[i] * 1.8f * (1 - k * 0.4f), 0, FxTypes.LUEUR, pCoul[i], (1 - k) * 0.55f * pres);
                    panneau(x, y, z, pTaille[i] * 0.7f, 0, FxTypes.COEUR, pCoul[i], (1 - k) * 0.9f * pres);
                }
                case P_BULLE -> panneau(x, y, z, pTaille[i] * (1 + k * 0.6f), 0, FxTypes.ANNEAU, pCoul[i], (1 - k) * 0.8f * pres);
                case P_FEU -> {
                    // langue de flamme : grossit, passe du blanc-jaune a l'orange puis s'eteint
                    float r = pTaille[i] * (0.7f + k * 1.4f);
                    panneau(x, y, z, r, i * 2.1f + k, FxTypes.LUEUR, pCoul[i], (1 - k) * (1 - k) * 0.95f * pres);
                    if (k < 0.5f) panneau(x, y, z, r * 0.45f, 0, FxTypes.COEUR, 0xFFF0C0, (1 - k * 2) * 0.9f * pres);
                }
                case P_FUMEE -> {
                    float entree = Math.min(1f, k * 6f);
                    panneau(x, y, z, pTaille[i] * (1 + k * 1.8f), i * 1.7f + k * 1.1f, (i & 1) == 0 ? FxTypes.FUMEE_A : FxTypes.FUMEE_B, pCoul[i],
                            pA[i] * entree * (1 - k) * pres);
                }
                default -> {
                }
            }
        }
    }

    // ================================================================== creatures qui fondent : tremblement
    private static final LivingEntity[] PILE = new LivingEntity[8];
    private static int nPile;
    private static final Quaternionf Q = new Quaternionf();

    private static int hash(int x) {
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        return (x >>> 16) ^ x;
    }

    private static float h11(int x) {
        return (hash(x) & 0xFFFF) / 32767.5f - 1f;
    }

    public static void vivantAvant(RenderLivingEvent.Pre<?, ?> e) {
        if (nF == 0) return;
        LivingEntity le = e.getEntity();
        int id = le.getId();
        for (int i = 0; i < nF; i++) {
            if (fId[i] != id) continue;
            float now = maintenant() + e.getPartialTick();
            float age = now - fT0[i];
            if (age < 0 || age > 40 || nPile >= PILE.length) return;
            float k = age / 40f;
            float amp = 0.045f * (1 - k) + 0.01f;
            int f = (int) (now * 1.5f) * 131 + id * 7;
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(h11(f) * amp, 0, h11(f + 2) * amp);
            ps.mulPose(Q.rotationYXZ(h11(f + 3) * 0.08f * (1 - k), h11(f + 4) * 0.05f * (1 - k), h11(f + 5) * 0.05f * (1 - k)));
            // l'acide ronge : la creature se tasse un peu
            float tasse = 1 - 0.08f * Math.min(1, age / 20f);
            ps.scale(1, tasse, 1);
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
        if (age > 10) {
            secAmp = 0;
            return;
        }
        float k = (float) Math.exp(-age / 2.2f) * secAmp;
        e.setPitch(e.getPitch() - k * (0.6f + 0.4f * Mth.sin(age * 2.6f)));
        e.setYaw(e.getYaw() + k * 0.4f * Mth.sin(age * 3.4f + 1.1f));
        e.setRoll(e.getRoll() + k * 0.7f * Mth.sin(age * 4.1f + 0.3f));
    }

    // ================================================================== menage, statistiques
    public static void dechargement(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) vider();
    }

    private static void vider() {
        nO = nL = nP = nC = nF = nFumee = 0;
        for (int i = 0; i < nPile; i++) PILE[i] = null;
        nPile = 0;
        secAmp = 0;
    }

    public static String stats() {
        String r = String.format(Locale.ROOT,
                "blundergat : %d tirs, %d impacts, %d explosions d'acide ; rendu moyen %.3f ms (pire %.3f ms) sur %d images ; en cours : %d obus, %d lueurs, "
                        + "%d particules, %d cartouches, %d fondus ; pics : %d/%d obus, %d/%d lueurs, %d/%d particules, %d/%d cartouches, %d/%d fondus",
                tirs, impacts, explosions, images == 0 ? 0 : nsRendu / 1e6 / images, maxNs / 1e6, images, nO, nL, nP, nC, nF, picO, MAX_O, picL, MAX_L, picP, MAX_P,
                picC, MAX_C, picF, MAX_F);
        nsRendu = maxNs = 0;
        images = tirs = impacts = explosions = 0;
        picO = picL = picP = picC = picF = 0;
        return r;
    }
}
