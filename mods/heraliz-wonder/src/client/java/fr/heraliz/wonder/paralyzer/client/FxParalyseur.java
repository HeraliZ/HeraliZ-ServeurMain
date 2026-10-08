package fr.heraliz.wonder.paralyzer.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.client.ClientWonder;
import fr.heraliz.wonder.client.TaczClient;
import fr.heraliz.wonder.client.fx.FxTypes;
import fr.heraliz.wonder.paralyzer.CalculsParalyseur;
import fr.heraliz.wonder.paralyzer.ReseauParalyseur;
import fr.heraliz.wonder.paralyzer.SonsParalyseur;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Effets du Paralyzer cote client, dans des reserves de taille fixe (tableaux paralleles, aucune allocation par image ni
 * par particule), dessines en un lot additif ({@link FxTypes#LUMIERE}) et un lot translucide ({@link FxTypes#FUMEE}) avec
 * la planche de textures du mod.
 * <ul>
 *   <li><b>Rayon</b> (un par tireur, d'apres les tirs que TaCZ annonce : aucun paquet a nous) : coeur blanc, gaine
 *   coloree, deux filaments qui ondulent en helice, anneaux d'energie qui filent vers le bout, eclat a la bouche, eclat et
 *   etincelles au point d'impact ; il s'allume en 0,08 s et s'eteint en 0,15 s. Jaune pale / blanc ; Petrifier :
 *   turquoise. Bout du rayon : un lancer contre les blocs par tick et par tireur (pas par image).</li>
 *   <li><b>Creatures figees</b> (paquet {@link ReseauParalyseur.Etat}) : leur pose est arretee net (animation de marche
 *   gelee a l'image du gel), elles luisent et crepitent ;</li>
 *   <li><b>Poussiere</b> : la creature disparait dans un nuage de poussiere et d'etincelles qui retombe ;</li>
 *   <li><b>Flotter</b> : le tireur qui vise le sol sous ses pieds remonte doucement a la hauteur donnee par le serveur.</li>
 * </ul>
 */
public final class FxParalyseur {
    private FxParalyseur() {
    }

    private static final net.minecraft.util.RandomSource RNG = net.minecraft.util.RandomSource.create();

    // ================================================================== rayons (un par tireur)
    private static final int MAX_R = 16;
    private static final LivingEntity[] rE = new LivingEntity[MAX_R];
    private static final byte[] rPap = new byte[MAX_R];
    private static final long[] rDernier = new long[MAX_R], rDebut = new long[MAX_R];
    private static final float[] rLong = new float[MAX_R];
    private static final boolean[] rSol = new boolean[MAX_R], rArrete = new boolean[MAX_R];
    private static final double[] rBx = new double[MAX_R], rBy = new double[MAX_R], rBz = new double[MAX_R];
    private static final SonRayon[] rSon = new SonRayon[MAX_R];
    private static int nR;
    /** Une impulsion toutes les 0,1 s : sans nouvelle depuis 0,16 s, le rayon s'eteint (en 0,15 s). */
    private static final long VIE_MS = 160, FONDU_MS = 150, ALLUMAGE_MS = 80;

    // ================================================================== creatures figees / en poussiere
    private static final int MAX_G = 128;
    private static final int[] gId = new int[MAX_G];
    private static final byte[] gEtat = new byte[MAX_G], gPap = new byte[MAX_G];
    private static final long[] gT0 = new long[MAX_G];
    private static final float[] gPos = new float[MAX_G], gVit = new float[MAX_G];
    private static final boolean[] gPose = new boolean[MAX_G];
    private static int nG;

    // ================================================================== particules
    private static final int MAX_P = 1536;
    private static final double[] pX = new double[MAX_P], pY = new double[MAX_P], pZ = new double[MAX_P];
    private static final double[] pXo = new double[MAX_P], pYo = new double[MAX_P], pZo = new double[MAX_P];
    private static final float[] pVx = new float[MAX_P], pVy = new float[MAX_P], pVz = new float[MAX_P], pG = new float[MAX_P];
    private static final float[] pR = new float[MAX_P], pA = new float[MAX_P];
    private static final int[] pVie = new int[MAX_P], pAge = new int[MAX_P], pCoul = new int[MAX_P], pCell = new int[MAX_P];
    private static final boolean[] pFumee = new boolean[MAX_P];
    private static int nP, nFumee;

    // ================================================================== flotter
    private static float flotteHauteur;
    private static long flotteJusqua;

    // ================================================================== statistiques
    private static long nsRendu, maxNs;
    private static int images, picR, picG, picP;

    // ------------------------------------------------------------------ entrees
    /** Une impulsion du rayon de ce tireur (TaCZ l'annonce chez chaque client : tireur local ou joueur proche). */
    public static void impulsion(LivingEntity tireur, int pap) {
        long now = Util.getMillis();
        int k = -1;
        for (int i = 0; i < nR; i++) {
            if (rE[i] == tireur) {
                k = i;
                break;
            }
        }
        if (k < 0) {
            if (nR >= MAX_R) return;
            k = nR++;
            rE[k] = tireur;
            rDebut[k] = now;
            rArrete[k] = false;
            rLong[k] = 0;
            rPap[k] = (byte) pap;
            demarrer(k, tireur, pap);
        } else if (rArrete[k] || now - rDernier[k] > VIE_MS) {
            rDebut[k] = now;
            rArrete[k] = false;
            demarrer(k, tireur, pap);
        }
        rPap[k] = (byte) pap;
        rDernier[k] = now;
        if (nR > picR) picR = nR;
    }

    private static void demarrer(int k, LivingEntity tireur, int pap) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(tireur.getX(), tireur.getEyeY(), tireur.getZ(), SonsParalyseur.DEBUT.get(), SoundSource.PLAYERS, 0.9f,
                pap > 0 ? 0.92f : 1.0f, false);
        if (rSon[k] == null || rSon[k].isStopped()) {
            rSon[k] = new SonRayon(tireur, pap);
            mc.getSoundManager().play(rSon[k]);
        }
    }

    /** Volume du bourdon du rayon de ce tireur (0 = eteint : le son s'arrete). */
    static float intensiteSon(LivingEntity tireur) {
        long now = Util.getMillis();
        for (int k = 0; k < nR; k++) {
            if (rE[k] == tireur) return now - rDernier[k] > VIE_MS + FONDU_MS ? 0f : Math.max(0.01f, intensite(k, now));
        }
        return 0f;
    }

    private static float intensite(int k, long now) {
        float allume = Mth.clamp((now - rDebut[k]) / (float) ALLUMAGE_MS, 0f, 1f);
        long apres = now - rDernier[k] - VIE_MS;
        float eteint = apres <= 0 ? 1f : 1f - Mth.clamp(apres / (float) FONDU_MS, 0f, 1f);
        return allume * eteint;
    }

    /** Paquet du serveur : une creature se fige, degele, tombe en poussiere, ou un boss est touche. */
    public static void etat(ReseauParalyseur.Etat m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(m.id());
        int k = chercher(m.id());
        switch (m.etat()) {
            case ReseauParalyseur.GEL -> {
                if (k < 0) {
                    if (nG >= MAX_G) return;
                    k = nG++;
                    gId[k] = m.id();
                }
                gEtat[k] = ReseauParalyseur.GEL;
                gPap[k] = m.pap();
                gT0[k] = Util.getMillis();
                gPose[k] = false;
                if (e instanceof LivingEntity le) {
                    // pose arretee net : position et vitesse de l'animation de marche au moment du gel
                    gPos[k] = le.walkAnimation.position();
                    gVit[k] = le.walkAnimation.speed();
                    gPose[k] = true;
                    eclat(le, m.pap(), 18, false);
                }
                if (nG > picG) picG = nG;
            }
            case ReseauParalyseur.DEGEL -> {
                if (k >= 0) retirer(k);
            }
            case ReseauParalyseur.POUSSIERE -> {
                if (k < 0) {
                    if (nG >= MAX_G) return;
                    k = nG++;
                    gId[k] = m.id();
                    gPose[k] = false;
                }
                gEtat[k] = ReseauParalyseur.POUSSIERE;
                gPap[k] = m.pap();
                gT0[k] = Util.getMillis();
                if (e instanceof LivingEntity le) eclat(le, m.pap(), 90, true);
            }
            case ReseauParalyseur.COLOSSE -> {
                if (e instanceof LivingEntity le) eclat(le, m.pap(), 6, false);
            }
            default -> {
            }
        }
    }

    /** Paquet du serveur (au tireur) : flotter a cette hauteur au-dessus du sol pendant 0,3 s. */
    public static void flotte(float hauteur) {
        flotteHauteur = hauteur;
        flotteJusqua = Util.getMillis() + 300;
    }

    private static int chercher(int id) {
        for (int i = 0; i < nG; i++) if (gId[i] == id) return i;
        return -1;
    }

    private static void retirer(int k) {
        nG--;
        gId[k] = gId[nG];
        gEtat[k] = gEtat[nG];
        gPap[k] = gPap[nG];
        gT0[k] = gT0[nG];
        gPos[k] = gPos[nG];
        gVit[k] = gVit[nG];
        gPose[k] = gPose[nG];
    }

    // ------------------------------------------------------------------ tick : bout des rayons, flotter, particules
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        if (e.phase == TickEvent.Phase.START) {
            flotter(mc);
            return;
        }
        long now = Util.getMillis();
        // rayons : bout (un lancer contre les blocs par tick et par tireur), etincelles d'impact, fin
        for (int k = nR - 1; k >= 0; k--) {
            LivingEntity t = rE[k];
            boolean mort = t == null || t.isRemoved() || t.level() != mc.level;
            if (!mort && now - rDernier[k] <= VIE_MS + FONDU_MS) {
                if (!rArrete[k] && now - rDernier[k] > VIE_MS) {
                    rArrete[k] = true;
                    mc.level.playLocalSound(t.getX(), t.getEyeY(), t.getZ(), SonsParalyseur.FIN.get(), SoundSource.PLAYERS, 0.8f,
                            rPap[k] > 0 ? 0.92f : 1.0f, false);
                }
                bout(k, t);
                if (rSol[k] && !rArrete[k]) {
                    int c = CalculsParalyseur.lueur(rPap[k]);
                    for (int j = 0; j < 2; j++) {
                        particule(rBx[k], rBy[k], rBz[k], (RNG.nextFloat() - 0.5f) * 0.25f, RNG.nextFloat() * 0.18f, (RNG.nextFloat() - 0.5f) * 0.25f, 0.03f,
                                0.05f + RNG.nextFloat() * 0.05f, 6 + RNG.nextInt(6), c, FxTypes.ETINCELLE, 1f, false);
                    }
                }
                continue;
            }
            if (!rArrete[k] && t != null && !mort) {
                mc.level.playLocalSound(t.getX(), t.getEyeY(), t.getZ(), SonsParalyseur.FIN.get(), SoundSource.PLAYERS, 0.8f, 1.0f, false);
            }
            // fin du rayon : l'entree k est remplacee par la derniere (le son de la derniere suit son nouvel indice)
            nR--;
            rE[k] = rE[nR];
            rPap[k] = rPap[nR];
            rDernier[k] = rDernier[nR];
            rDebut[k] = rDebut[nR];
            rLong[k] = rLong[nR];
            rSol[k] = rSol[nR];
            rArrete[k] = rArrete[nR];
            rBx[k] = rBx[nR];
            rBy[k] = rBy[nR];
            rBz[k] = rBz[nR];
            rSon[k] = rSon[nR];
            rE[nR] = null;
            rSon[nR] = null;
        }
        // creatures figees : etincelles qui crepitent ; poussiere : on oublie au bout de 3 s ; disparues : on oublie
        for (int k = nG - 1; k >= 0; k--) {
            Entity en = mc.level.getEntity(gId[k]);
            long age = now - gT0[k];
            if (gEtat[k] == ReseauParalyseur.POUSSIERE ? age > 3000 : (en == null || !en.isAlive() || age > 120_000)) {
                retirer(k);
                continue;
            }
            if (gEtat[k] == ReseauParalyseur.GEL && en instanceof LivingEntity le && RNG.nextInt(3) == 0) {
                double x = le.getX() + (RNG.nextDouble() - 0.5) * le.getBbWidth() * 1.1;
                double y = le.getY() + RNG.nextDouble() * le.getBbHeight();
                double z = le.getZ() + (RNG.nextDouble() - 0.5) * le.getBbWidth() * 1.1;
                particule(x, y, z, 0, 0.01f, 0, 0f, 0.04f + RNG.nextFloat() * 0.05f, 4 + RNG.nextInt(5), CalculsParalyseur.lueur(gPap[k]), FxTypes.ETINCELLE,
                        0.9f, false);
            }
        }
        // particules : mouvement (reserve compactee)
        int w = 0;
        nFumee = 0;
        for (int i = 0; i < nP; i++) {
            if (++pAge[i] >= pVie[i]) continue;
            pXo[i] = pX[i];
            pYo[i] = pY[i];
            pZo[i] = pZ[i];
            pX[i] += pVx[i];
            pY[i] += pVy[i];
            pZ[i] += pVz[i];
            pVy[i] -= pG[i];
            pVx[i] *= 0.9f;
            pVz[i] *= 0.9f;
            pVy[i] *= 0.94f;
            if (w != i) copier(i, w);
            if (pFumee[w]) nFumee++;
            w++;
        }
        nP = w;
    }

    /** Bout du rayon k : un lancer contre les blocs (portee par defaut du reglage ; le serveur seul decide des effets). */
    private static void bout(int k, LivingEntity t) {
        Vec3 o = t.getEyePosition();
        Vec3 d = t.getViewVector(1f);
        double portee = CalculsParalyseur.portee(rPap[k], 24, 30);
        BlockHitResult h = t.level().clip(new ClipContext(o, o.add(d.scale(portee)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t));
        rSol[k] = h.getType() == HitResult.Type.BLOCK;
        rLong[k] = (float) (rSol[k] ? h.getLocation().distanceTo(o) : portee);
        rBx[k] = o.x + d.x * rLong[k];
        rBy[k] = o.y + d.y * rLong[k];
        rBz[k] = o.z + d.z * rLong[k];
    }

    private static void flotter(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || Util.getMillis() > flotteJusqua || p.getAbilities().flying || p.isPassenger()) return;
        Vec3 pieds = p.position();
        BlockHitResult h = p.level().clip(new ClipContext(pieds, pieds.add(0, -6.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        double hauteur = h.getType() == HitResult.Type.MISS ? 6.0 : pieds.y - h.getLocation().y;
        Vec3 v = p.getDeltaMovement();
        double vy = CalculsParalyseur.vitesseFlotte(hauteur, flotteHauteur, v.y);
        if (vy != v.y) p.setDeltaMovement(v.x, vy, v.z);
        p.resetFallDistance();
    }

    private static void particule(double x, double y, double z, float vx, float vy, float vz, float g, float r, int vie, int coul, int cell, float a,
                                  boolean fumee) {
        if (nP >= MAX_P) return;
        int i = nP++;
        pX[i] = pXo[i] = x;
        pY[i] = pYo[i] = y;
        pZ[i] = pZo[i] = z;
        pVx[i] = vx;
        pVy[i] = vy;
        pVz[i] = vz;
        pG[i] = g;
        pR[i] = r;
        pA[i] = a;
        pVie[i] = vie;
        pAge[i] = 0;
        pCoul[i] = coul;
        pCell[i] = cell;
        pFumee[i] = fumee;
        if (fumee) nFumee++;
        if (nP > picP) picP = nP;
    }

    private static void copier(int i, int j) {
        pX[j] = pX[i];
        pY[j] = pY[i];
        pZ[j] = pZ[i];
        pXo[j] = pXo[i];
        pYo[j] = pYo[i];
        pZo[j] = pZo[i];
        pVx[j] = pVx[i];
        pVy[j] = pVy[i];
        pVz[j] = pVz[i];
        pG[j] = pG[i];
        pR[j] = pR[i];
        pA[j] = pA[i];
        pVie[j] = pVie[i];
        pAge[j] = pAge[i];
        pCoul[j] = pCoul[i];
        pCell[j] = pCell[i];
        pFumee[j] = pFumee[i];
    }

    /** Eclat sur une creature : etincelles (gel), ou poussiere et etincelles (desintegration). */
    private static void eclat(LivingEntity le, int pap, int n, boolean poussiere) {
        int c = CalculsParalyseur.lueur(pap);
        float w = le.getBbWidth(), h = le.getBbHeight();
        for (int i = 0; i < n; i++) {
            double x = le.getX() + (RNG.nextDouble() - 0.5) * w;
            double y = le.getY() + RNG.nextDouble() * h;
            double z = le.getZ() + (RNG.nextDouble() - 0.5) * w;
            float vx = (float) ((x - le.getX()) * 0.12 + (RNG.nextFloat() - 0.5f) * 0.06f);
            float vz = (float) ((z - le.getZ()) * 0.12 + (RNG.nextFloat() - 0.5f) * 0.06f);
            if (poussiere) {
                // grains de poussiere (fumee claire qui s'envole un peu puis retombe) et quelques braises lumineuses
                int gris = RNG.nextInt(3) == 0 ? (pap > 0 ? 0x9FD8CF : 0xD8CFAE) : (pap > 0 ? 0x7FA8A2 : 0xA89F86);
                particule(x, y, z, vx, 0.03f + RNG.nextFloat() * 0.05f, vz, 0.004f, 0.12f + RNG.nextFloat() * 0.14f, 18 + RNG.nextInt(16), gris,
                        RNG.nextBoolean() ? FxTypes.FUMEE_A : FxTypes.FUMEE_B, 0.75f, true);
                if (i % 3 == 0) {
                    particule(x, y, z, vx * 1.5f, 0.02f + RNG.nextFloat() * 0.06f, vz * 1.5f, 0.006f, 0.05f + RNG.nextFloat() * 0.04f, 10 + RNG.nextInt(12), c,
                            FxTypes.ETINCELLE, 1f, false);
                }
            } else {
                particule(x, y, z, vx, 0.02f + RNG.nextFloat() * 0.04f, vz, 0.005f, 0.05f + RNG.nextFloat() * 0.05f, 6 + RNG.nextInt(8), c,
                        FxTypes.ETINCELLE, 1f, false);
            }
        }
        if (poussiere) {
            particule(le.getX(), le.getY() + h * 0.5, le.getZ(), 0, 0, 0, 0, h * 0.9f, 7, CalculsParalyseur.coeur(pap), FxTypes.LUEUR, 0.9f, false);
        }
    }

    // ------------------------------------------------------------------ creatures figees : pose arretee, poussiere cachee
    public static void vivantAvant(RenderLivingEvent.Pre<?, ?> e) {
        if (nG == 0) return;
        LivingEntity le = e.getEntity();
        int k = chercher(le.getId());
        if (k < 0) return;
        if (gEtat[k] == ReseauParalyseur.POUSSIERE) {
            // le corps est parti en poussiere : on ne dessine plus la creature mourante
            e.setCanceled(true);
            return;
        }
        if (gPose[k]) {
            // impose (position, vitesse) de l'animation de marche : trois pas exacts, la pose reste figee
            WalkAnimationState w = le.walkAnimation;
            float s = gVit[k];
            w.update(gPos[k] - w.position() - 2 * s, 1f);
            w.update(s, 1f);
            w.update(s, 1f);
        }
    }

    // ------------------------------------------------------------------ rendu
    private static VertexConsumer VC;
    private static Matrix4f M;
    private static double CX, CY, CZ;
    private static float LX, LY, LZ, UX, UY, UZ;
    private static final float[] CANON = new float[3];

    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (nR == 0 && nG == 0 && nP == 0) return;
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
        long now = Util.getMillis();
        float temps = (now % 100_000L) / 1000f;
        M = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VC = buf.getBuffer(FxTypes.LUMIERE);
        for (int k = 0; k < nR; k++) dessinerRayon(mc, cam, k, partial, now, temps);
        dessinerFiges(mc, partial, now, temps);
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

    private static void dessinerRayon(Minecraft mc, Camera cam, int k, float partial, long now, float temps) {
        LivingEntity t = rE[k];
        if (t == null || rLong[k] <= 0.05f) return;
        float a = intensite(k, now);
        if (a <= 0.004f) return;
        int pap = rPap[k];
        int lueur = CalculsParalyseur.lueur(pap), coeur = CalculsParalyseur.coeur(pap);
        // direction : regard du tireur (image courante) ; depart : la bouche de l'emetteur
        Vec3 d = t.getViewVector(partial);
        double dx = d.x, dy = d.y, dz = d.z;
        double ox, oy, oz;
        boolean fp = t == mc.player && mc.options.getCameraType().isFirstPerson();
        if (fp && ClientWonder.tacz && TaczClient.decalageCanon(cam, CANON)) {
            // bouche de l'emetteur telle que TaCZ la dessine ; ramenee a 1,1 bloc de l'oeil au plus, sur la meme ligne de
            // visee (meme place a l'ecran) : le rayon ne part pas derriere une creature toute proche
            float lg = (float) Math.sqrt(CANON[0] * CANON[0] + CANON[1] * CANON[1] + CANON[2] * CANON[2]);
            float kk = lg > 1.1f ? 1.1f / lg : 1f;
            ox = CX + CANON[0] * kk;
            oy = CY + CANON[1] * kk;
            oz = CZ + CANON[2] * kk;
        } else if (fp) {
            // repli : la bouche telle que la vue a la hanche de TaCZ la montre (a droite et sous la mire)
            ox = CX + dx * 1.0 - LX * 0.18 - UX * 0.18;
            oy = CY + dy * 1.0 - LY * 0.18 - UY * 0.18;
            oz = CZ + dz * 1.0 - LZ * 0.18 - UZ * 0.18;
        } else {
            double ex = Mth.lerp(partial, t.xo, t.getX()), ey = Mth.lerp(partial, t.yo, t.getY()) + t.getEyeHeight(), ez = Mth.lerp(partial, t.zo, t.getZ());
            // a droite du regard (horizontal), un peu plus bas, un peu devant
            double rx = -dz, rz = dx, rl = Math.sqrt(rx * rx + rz * rz);
            if (rl > 1e-4) {
                rx /= rl;
                rz /= rl;
            }
            ox = ex + dx * 0.9 + rx * 0.32;
            oy = ey + dy * 0.9 - 0.28;
            oz = ez + dz * 0.9 + rz * 0.32;
        }
        double bx = rBx[k], by = rBy[k], bz = rBz[k];
        if (fp || t == mc.player) {
            // bout recalcule avec le regard de l'image (le lancer du tick suffit pour la longueur)
            Vec3 e0 = t.getEyePosition(partial);
            bx = e0.x + dx * rLong[k];
            by = e0.y + dy * rLong[k];
            bz = e0.z + dz * rLong[k];
        }
        double lx = bx - ox, ly = by - oy, lz = bz - oz;
        double L = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (L < 0.1) return;
        float fx = (float) (lx / L), fy = (float) (ly / L), fz = (float) (lz / L);
        // axes perpendiculaires au rayon (U, V)
        float ux = -fz, uy = 0, uz = fx;
        float un = (float) Math.sqrt(ux * ux + uz * uz);
        if (un < 1e-3f) {
            ux = 1;
            uz = 0;
            un = 1;
        }
        ux /= un;
        uz /= un;
        float vx = fy * uz - fz * uy, vy = fz * ux - fx * uz, vz = fx * uy - fy * ux;
        // gaine, coeur
        rubanMonde(ox, oy, oz, bx, by, bz, 0.30f, 0.42f, FxTypes.FAISCEAU, lueur, 0.55f * a, 0.4f * a);
        rubanMonde(ox, oy, oz, bx, by, bz, 0.07f, 0.09f, FxTypes.COEUR, coeur, 0.95f * a, 0.8f * a);
        // deux filaments en helice qui ondulent (rayon qui varie le long du faisceau et dans le temps)
        int seg = Mth.clamp((int) (L * 2.2), 8, 48);
        for (int h = 0; h < 2; h++) {
            double px = ox, py = oy, pz = oz;
            for (int j = 1; j <= seg; j++) {
                float s = (float) (L * j / seg);
                float phi = s * 3.1f - temps * 15f + h * Mth.PI;
                float rad = (0.10f + 0.05f * Mth.sin(s * 1.7f - temps * 9f + h)) * Mth.clamp(s * 2f, 0f, 1f);
                float cu = Mth.cos(phi) * rad, cv = Mth.sin(phi) * rad;
                double qx = ox + fx * s + ux * cu + vx * cv, qy = oy + fy * s + uy * cu + vy * cv, qz = oz + fz * s + uz * cu + vz * cv;
                float fin = 1f - Mth.clamp((s - (float) L + 1.5f) / 1.5f, 0f, 0.6f);
                rubanMonde(px, py, pz, qx, qy, qz, 0.05f, 0.05f, FxTypes.FAISCEAU, h == 0 ? lueur : coeur, 0.8f * a * fin, 0.8f * a * fin);
                px = qx;
                py = qy;
                pz = qz;
            }
        }
        // anneaux d'energie qui filent vers le bout (22 blocs/s, tous les 1,6 bloc)
        float pas = 1.6f, vitesse = 22f;
        float decal = (temps * vitesse) % pas;
        for (float s = decal + 0.6f; s < L - 0.2f; s += pas) {
            float r = 0.2f + 0.06f * Mth.sin(s * 2.3f + temps * 6f);
            float bord = Mth.clamp((float) (L - s) / 1.2f, 0f, 1f) * Mth.clamp((s - 0.6f) / 0.8f, 0f, 1f);
            cercle(ox + fx * s, oy + fy * s, oz + fz * s, ux, uy, uz, vx, vy, vz, r, lueur, 0.7f * a * bord);
        }
        // eclat a la bouche (scintille) et au bout
        float sc = 0.85f + 0.15f * Mth.sin(temps * 47f) + 0.1f * Mth.sin(temps * 83f + 1f);
        panneau(ox, oy, oz, (fp ? 0.12f : 0.22f) * sc, temps * 2f, FxTypes.LUEUR, coeur, 0.9f * a);
        panneau(ox, oy, oz, (fp ? 0.22f : 0.4f) * sc, 0, FxTypes.LUEUR, lueur, 0.5f * a);
        if (rSol[k]) {
            panneau(bx - fx * 0.05, by - fy * 0.05, bz - fz * 0.05, 0.55f * sc, temps * 3f, FxTypes.ETOILE, lueur, 0.8f * a);
            panneau(bx - fx * 0.05, by - fy * 0.05, bz - fz * 0.05, 0.9f * sc, 0, FxTypes.LUEUR, lueur, 0.45f * a);
        }
    }

    private static void dessinerFiges(Minecraft mc, float partial, long now, float temps) {
        for (int k = 0; k < nG; k++) {
            if (gEtat[k] != ReseauParalyseur.GEL) continue;
            Entity en = mc.level.getEntity(gId[k]);
            if (!(en instanceof LivingEntity le)) continue;
            float age = (now - gT0[k]) / 1000f;
            float monte = Mth.clamp(age / 0.25f, 0f, 1f);
            int c = CalculsParalyseur.lueur(gPap[k]);
            double x = Mth.lerp(partial, le.xo, le.getX()), y = Mth.lerp(partial, le.yo, le.getY()), z = Mth.lerp(partial, le.zo, le.getZ());
            float h = le.getBbHeight(), w = le.getBbWidth();
            float pouls = 0.75f + 0.25f * Mth.sin(temps * 9f + k);
            // halo autour du corps (tete et torse)
            panneau(x, y + h * 0.55, z, h * 0.62f * pouls, 0, FxTypes.LUEUR, c, 0.32f * monte);
            panneau(x, y + h * 0.85, z, w * 0.9f, temps * 1.5f, FxTypes.ETOILE, CalculsParalyseur.coeur(gPap[k]), 0.25f * monte * pouls);
            // petits arcs qui crepitent sur le corps (forme tiree toutes les 3 images, sans allocation)
            int graine = (int) (now / 50) * 31 + gId[k] * 17;
            for (int j = 0; j < 3; j++) {
                double ax = x + (h01(graine + j * 5) - 0.5) * w, ay = y + h01(graine + j * 5 + 1) * h, az = z + (h01(graine + j * 5 + 2) - 0.5) * w;
                double qx = ax, qy = ay, qz = az;
                for (int s = 0; s < 3; s++) {
                    double nx = qx + (h01(graine + j * 11 + s * 3) - 0.5) * 0.35, ny = qy + (h01(graine + j * 11 + s * 3 + 1) - 0.5) * 0.35,
                            nz = qz + (h01(graine + j * 11 + s * 3 + 2) - 0.5) * 0.35;
                    rubanMonde(qx, qy, qz, nx, ny, nz, 0.03f, 0.02f, FxTypes.COEUR, c, 0.9f * monte, 0.6f * monte);
                    qx = nx;
                    qy = ny;
                    qz = nz;
                }
            }
        }
    }

    private static void dessinerParticules(float partial, boolean fumee) {
        for (int i = 0; i < nP; i++) {
            if (pFumee[i] != fumee) continue;
            float k = (pAge[i] + partial) / pVie[i];
            if (k >= 1f) continue;
            double x = Mth.lerp(partial, pXo[i], pX[i]), y = Mth.lerp(partial, pYo[i], pY[i]), z = Mth.lerp(partial, pZo[i], pZ[i]);
            float a = pA[i] * (fumee ? (1 - k) * Math.min(1f, k * 6f) : (1 - k * k));
            float r = pR[i] * (fumee ? 0.6f + 0.8f * k : 1f - 0.4f * k);
            panneau(x, y, z, r, i * 0.7f, pCell[i], pCoul[i], a);
        }
    }

    private static float h01(int n) {
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
    }

    // ------------------------------------------------------------------ primitives (comme Fx)
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

    /** Anneau oriente (dans le plan U, V : perpendiculaire au rayon). */
    private static void cercle(double x, double y, double z, float ux, float uy, float uz, float vx, float vy, float vz, float r, int coul, float a) {
        int al = alpha(a);
        if (al == 0 || r <= 0) return;
        float px = (float) (x - CX), py = (float) (y - CY), pz = (float) (z - CZ);
        float u0 = FxTypes.u0(FxTypes.ANNEAU), v0 = FxTypes.v0(FxTypes.ANNEAU), u1 = FxTypes.u1(FxTypes.ANNEAU), v1 = FxTypes.v1(FxTypes.ANNEAU);
        float ax = ux * r, ay = uy * r, az = uz * r, bx = vx * r, by = vy * r, bz = vz * r;
        sommet(px - ax - bx, py - ay - by, pz - az - bz, coul, al, u0, v1);
        sommet(px + ax - bx, py + ay - by, pz + az - bz, coul, al, u1, v1);
        sommet(px + ax + bx, py + ay + by, pz + az + bz, coul, al, u1, v0);
        sommet(px - ax + bx, py - ay + by, pz - az + bz, coul, al, u0, v0);
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

    // ------------------------------------------------------------------ menage, statistiques
    public static void dechargement(LevelEvent.Unload e) {
        if (!e.getLevel().isClientSide()) return;
        for (int i = 0; i < nR; i++) {
            rE[i] = null;
            rSon[i] = null;
        }
        nR = nG = nP = nFumee = 0;
        flotteJusqua = 0;
    }

    public static String stats() {
        String r = String.format(Locale.ROOT, "paralyzer : rendu moyen %.3f ms (pire %.3f ms) sur %d images ; en cours %d rayons, %d creatures, %d particules ; "
                + "pics %d/%d, %d/%d, %d/%d", images == 0 ? 0 : nsRendu / 1e6 / images, maxNs / 1e6, images, nR, nG, nP, picR, MAX_R, picG, MAX_G, picP, MAX_P);
        nsRendu = maxNs = 0;
        images = picR = picG = picP = 0;
        return r;
    }

    static boolean armeTenue(LivingEntity e) {
        return Armes.type(e.getMainHandItem()) == Armes.PARALYSEUR;
    }
}
