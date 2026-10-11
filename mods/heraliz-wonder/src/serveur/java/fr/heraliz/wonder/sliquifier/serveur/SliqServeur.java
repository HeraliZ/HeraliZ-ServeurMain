package fr.heraliz.wonder.sliquifier.serveur;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.serveur.Degats;
import fr.heraliz.wonder.sliquifier.SliqCalculs;
import fr.heraliz.wonder.sliquifier.SliqConfig;
import fr.heraliz.wonder.sliquifier.net.PaquetFlaque;
import fr.heraliz.wonder.sliquifier.net.PaquetSync;
import fr.heraliz.wonder.sliquifier.net.PaquetTir;
import fr.heraliz.wonder.sliquifier.net.PaquetZombie;
import fr.heraliz.wonder.sliquifier.net.SliqReseau;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Le Sliquifier cote serveur, seul juge : boules de gelee en vol, flaques, glissades des zombies, morts. Tout tient
 * dans des reserves de taille fixe (tableaux paralleles, compactees en place), parcourues une fois par tick :
 * <ul>
 *   <li><b>boules</b> (64 au plus) : un lancer de rayon (blocs) et une recherche d'entites bornee par le trajet du tick ;
 *   coup direct = le zombie fond et laisse une flaque ; bloc = flaque (sur un mur : au pied du mur) ;</li>
 *   <li><b>flaques</b> (une liste, pas de blocs ni d'entites ; {@code flaquesMax} par dimension, la plus vieille part) :
 *   une recherche d'entites bornee par flaque, un tick sur deux (decalees) ; deux flaques trop proches n'en font qu'une ;</li>
 *   <li><b>glissades</b> (128 au plus) : le zombie entre dans une flaque, glisse a la renverse ({@code dureeGlissade}
 *   ticks) puis fond ; il laisse une petite flaque (reaction en chaine, comme dans CoD).</li>
 * </ul>
 * Jamais un joueur (seulement les creatures de la partie en zombies, les monstres ailleurs, {@link Regles#estCible}),
 * jamais un bloc casse. Les points du mode zombies reviennent au tireur (source des degats).
 */
public final class SliqServeur {
    private SliqServeur() {
    }

    /** Type de degats de la gelee ({@code data/heraliz_wonder/damage_type/sliquifie.json}) : ni explosion, ni projectile. */
    public static final ResourceKey<DamageType> SLIQUIFIE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(HeralizWonder.MODID, "sliquifie"));
    /** Marque d'un coup de gelee recent sur un boss (tick serveur), pour ne pas le frapper a chaque tick dans une flaque. */
    private static final String NBT_BOSS = "heraliz_wonder:sliq_t";

    private static long horloge;
    private static int prochainId;

    // ================================================================== boules
    static final int MAX_B = 64;
    private static final ServerLevel[] bNiv = new ServerLevel[MAX_B];
    private static final LivingEntity[] bTireur = new LivingEntity[MAX_B];
    private static final double[] bE = new double[MAX_B * 6];
    private static final int[] bAge = new int[MAX_B], bId = new int[MAX_B];
    private static final byte[] bPap = new byte[MAX_B];
    private static final boolean[] bZombies = new boolean[MAX_B];
    private static int nB;

    // ================================================================== flaques
    static final int MAX_F = 96;
    private static final ServerLevel[] fNiv = new ServerLevel[MAX_F];
    private static final LivingEntity[] fProprio = new LivingEntity[MAX_F];
    private static final int[] fId = new int[MAX_F], fDuree = new int[MAX_F];
    private static final double[] fX = new double[MAX_F], fY = new double[MAX_F], fZ = new double[MAX_F], fR = new double[MAX_F];
    private static final long[] fNaiss = new long[MAX_F], fFin = new long[MAX_F];
    private static final byte[] fPap = new byte[MAX_F];
    private static final boolean[] fZombies = new boolean[MAX_F];
    private static int nF;

    // ================================================================== glissades
    static final int MAX_G = 128;
    private static final LivingEntity[] gE = new LivingEntity[MAX_G], gProprio = new LivingEntity[MAX_G];
    private static final double[] gDx = new double[MAX_G], gDz = new double[MAX_G], gV0 = new double[MAX_G];
    private static final int[] gT = new int[MAX_G];
    private static final byte[] gPap = new byte[MAX_G];
    private static final boolean[] gZombies = new boolean[MAX_G];
    private static int nG;

    private static final double[] E = new double[6];
    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

    // ================================================================== tir
    /** Un tir : {@code depart} = yeux du tireur (depart de la balle TaCZ), {@code dir} = direction de la balle (dispersion comprise). */
    public static void tirer(ServerLevel niveau, LivingEntity tireur, Vec3 depart, Vec3 dir, int pap) {
        long t0 = System.nanoTime();
        if (dir.lengthSqr() < 1.0E-8) dir = tireur.getLookAngle();
        Vec3 d = dir.normalize();
        if (nB >= MAX_B) retirerBoule(0, false);
        double v = SliqCalculs.vitesse(pap, SliqConfig.d(SliqConfig.VITESSE));
        int i = nB++;
        bNiv[i] = niveau;
        bTireur[i] = tireur;
        int o = i * 6;
        bE[o] = depart.x + d.x * 0.25;
        bE[o + 1] = depart.y + d.y * 0.25;
        bE[o + 2] = depart.z + d.z * 0.25;
        bE[o + 3] = d.x * v;
        bE[o + 4] = d.y * v;
        bE[o + 5] = d.z * v;
        bAge[i] = 0;
        bId[i] = ++prochainId;
        bPap[i] = (byte) pap;
        bZombies[i] = Regles.enZombies(tireur);
        SliqReseau.autour(niveau, depart.x, depart.y, depart.z, new PaquetTir(bId[i], tireur.getId(), (byte) pap, (float) bE[o], (float) bE[o + 1],
                (float) bE[o + 2], (float) bE[o + 3], (float) bE[o + 4], (float) bE[o + 5], (float) SliqConfig.d(SliqConfig.GRAVITE)));
        SliqPerf.tir(System.nanoTime() - t0);
    }

    // ================================================================== tick
    public static void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        horloge++;
        if (nB == 0 && nF == 0 && nG == 0) return;
        long t0 = System.nanoTime();
        try {
            tickBoules();
        } catch (RuntimeException ex) {
            HeralizWonder.LOG.error("[sliquifier] boules", ex);
            for (int i = 0; i < nB; i++) {
                bNiv[i] = null;
                bTireur[i] = null;
            }
            nB = 0;
        }
        long t1 = System.nanoTime();
        try {
            tickFlaques();
        } catch (RuntimeException ex) {
            HeralizWonder.LOG.error("[sliquifier] flaques", ex);
        }
        long t2 = System.nanoTime();
        try {
            tickGlissades();
        } catch (RuntimeException ex) {
            HeralizWonder.LOG.error("[sliquifier] glissades", ex);
        }
        SliqPerf.tick(t1 - t0, t2 - t1, System.nanoTime() - t2, nB, nF, nG);
    }

    // ------------------------------------------------------------------ boules
    private static void tickBoules() {
        double gravite = SliqConfig.d(SliqConfig.GRAVITE);
        int vie = SliqConfig.i(SliqConfig.VIE_BOULE);
        for (int i = 0; i < nB; i++) {
            ServerLevel lv = bNiv[i];
            LivingEntity tireur = bTireur[i];
            int o = i * 6;
            Vec3 a = new Vec3(bE[o], bE[o + 1], bE[o + 2]);
            Vec3 b = new Vec3(bE[o] + bE[o + 3], bE[o + 1] + bE[o + 4], bE[o + 2] + bE[o + 5]);
            if (!lv.isLoaded(BlockPos.containing(b)) || b.y < lv.getMinBuildHeight() - 4) {
                retirerBoule(i--, true);
                continue;
            }
            BlockHitResult bh = lv.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tireur));
            Vec3 fin = bh.getType() != HitResult.Type.MISS ? bh.getLocation() : b;
            // creature touchee avant le bloc ?
            LivingEntity touchee = null;
            Vec3 pt = null;
            double best = Double.MAX_VALUE;
            boolean zombies = bZombies[i];
            List<LivingEntity> l = lv.getEntitiesOfClass(LivingEntity.class, new AABB(a, fin).inflate(0.6), e -> Regles.estCible(e, tireur, zombies));
            for (int k = 0; k < l.size(); k++) {
                LivingEntity e = l.get(k);
                Optional<Vec3> c = e.getBoundingBox().inflate(0.25).clip(a, fin);
                if (c.isEmpty() && !e.getBoundingBox().inflate(0.25).contains(a)) continue;
                Vec3 p = c.orElse(a);
                double dd = p.distanceToSqr(a);
                if (dd < best) {
                    best = dd;
                    touchee = e;
                    pt = p;
                }
            }
            if (touchee != null) {
                coupDirect(i, touchee, pt);
                retirerBoule(i--, false);
                continue;
            }
            if (bh.getType() != HitResult.Type.MISS) {
                surBloc(i, bh);
                retirerBoule(i--, false);
                continue;
            }
            E[0] = bE[o];
            E[1] = bE[o + 1];
            E[2] = bE[o + 2];
            E[3] = bE[o + 3];
            E[4] = bE[o + 4];
            E[5] = bE[o + 5];
            SliqCalculs.pas(E, gravite);
            System.arraycopy(E, 0, bE, o, 6);
            if (++bAge[i] > vie) retirerBoule(i--, true);
        }
    }

    /** Retire la boule i (dernier element deplace a sa place) ; {@code prevenir} : la boule finit en l'air, les clients l'effacent. */
    private static void retirerBoule(int i, boolean prevenir) {
        if (prevenir && bNiv[i] != null) {
            int o = i * 6;
            SliqReseau.autour(bNiv[i], bE[o], bE[o + 1], bE[o + 2], new PaquetFlaque(PaquetFlaque.SPLAT_SEUL, 0, (float) bE[o], (float) bE[o + 1],
                    (float) bE[o + 2], 0, 0, 0, bPap[i], PaquetFlaque.S_AUCUN, bId[i], (float) bE[o], (float) bE[o + 1], (float) bE[o + 2], (byte) -1));
        }
        int d = --nB;
        if (i != d) {
            bNiv[i] = bNiv[d];
            bTireur[i] = bTireur[d];
            System.arraycopy(bE, d * 6, bE, i * 6, 6);
            bAge[i] = bAge[d];
            bId[i] = bId[d];
            bPap[i] = bPap[d];
            bZombies[i] = bZombies[d];
        }
        bNiv[d] = null;
        bTireur[d] = null;
    }

    private static void coupDirect(int i, LivingEntity e, Vec3 pt) {
        ServerLevel lv = bNiv[i];
        int pap = bPap[i];
        LivingEntity tireur = bTireur[i];
        boolean zombies = bZombies[i];
        // flaque sous la creature (avant les degats : sa position est encore la bonne)
        double sol = e.onGround() ? e.getY() : solSous(lv, e.getX(), e.getY() + 0.2, e.getZ(), 6);
        frapper(e, tireur, pap, zombies, true);
        if (!Double.isNaN(sol)) {
            creerFlaque(lv, e.getX(), sol, e.getZ(), rayon(pap), duree(pap), tireur, pap, zombies, PaquetFlaque.S_ZOMBIE, bId[i], pt, (byte) -1);
        } else {
            splatSeul(lv, pt, pap, bId[i], PaquetFlaque.S_ZOMBIE, (byte) -1);
        }
    }

    private static void surBloc(int i, BlockHitResult bh) {
        ServerLevel lv = bNiv[i];
        int pap = bPap[i];
        Vec3 p = bh.getLocation();
        Direction face = bh.getDirection();
        byte f = (byte) face.get3DDataValue();
        if (face == Direction.UP) {
            creerFlaque(lv, p.x, p.y, p.z, rayon(pap), duree(pap), bTireur[i], pap, bZombies[i], PaquetFlaque.S_SOL, bId[i], p, f);
            return;
        }
        // mur ou plafond : la gelee coule au pied du mur (devant lui)
        double x = p.x + face.getStepX() * 0.55, z = p.z + face.getStepZ() * 0.55;
        double sol = solSous(lv, x, p.y + (face == Direction.DOWN ? -0.3 : 0.0), z, 6);
        if (!Double.isNaN(sol)) {
            creerFlaque(lv, x, sol, z, rayon(pap) * 0.85, duree(pap), bTireur[i], pap, bZombies[i], PaquetFlaque.S_MUR, bId[i], p, f);
        } else {
            splatSeul(lv, p, pap, bId[i], PaquetFlaque.S_MUR, f);
        }
    }

    private static void splatSeul(ServerLevel lv, Vec3 p, int pap, int boule, byte splat, byte face) {
        SliqReseau.autour(lv, p.x, p.y, p.z, new PaquetFlaque(PaquetFlaque.SPLAT_SEUL, 0, (float) p.x, (float) p.y, (float) p.z, 0, 0, 0, (byte) pap, splat,
                boule, (float) p.x, (float) p.y, (float) p.z, face));
    }

    // ------------------------------------------------------------------ flaques
    static double rayon(int pap) {
        return SliqCalculs.rayonFlaque(pap, SliqConfig.d(SliqConfig.RAYON), SliqConfig.d(SliqConfig.RAYON_PAP));
    }

    static int duree(int pap) {
        return SliqCalculs.dureeFlaque(pap, SliqConfig.d(SliqConfig.DUREE), SliqConfig.d(SliqConfig.DUREE_PAP));
    }

    /**
     * Pose une flaque (ou etale celle qui est deja la) et previent les clients proches. {@code pt} = point d'impact de la
     * boule pour l'eclaboussure (mur, zombie), {@code boule} = numero de la boule qui s'arrete (-1 : aucune).
     */
    static void creerFlaque(ServerLevel lv, double x, double y, double z, double r, int duree, @Nullable LivingEntity proprio, int pap, boolean zombies,
                            byte splat, int boule, @Nullable Vec3 pt, byte face) {
        float ix = pt == null ? (float) x : (float) pt.x, iy = pt == null ? (float) y : (float) pt.y, iz = pt == null ? (float) z : (float) pt.z;
        // fusion avec une flaque deja la (rafale au meme endroit) : elle s'etale et dure plus longtemps
        for (int j = 0; j < nF; j++) {
            if (fNiv[j] != lv || !SliqCalculs.fusion(fX[j], fY[j], fZ[j], fR[j], x, y, z, r)) continue;
            fR[j] = SliqCalculs.rayonFusion(fR[j], r);
            long fin = Math.max(fFin[j], horloge + duree);
            fDuree[j] = (int) (fin - fNaiss[j]);
            fFin[j] = fin;
            if (pap > fPap[j]) fPap[j] = (byte) pap;
            if (proprio != null) fProprio[j] = proprio;
            fZombies[j] |= zombies;
            SliqReseau.autour(lv, x, y, z, new PaquetFlaque(PaquetFlaque.MAJ, fId[j], (float) fX[j], (float) fY[j], (float) fZ[j], (float) fR[j], fDuree[j],
                    (int) (horloge - fNaiss[j]), fPap[j], splat, boule, ix, iy, iz, face));
            return;
        }
        // trop de flaques dans cette dimension : la plus vieille part
        int max = SliqConfig.i(SliqConfig.FLAQUES_MAX), n = 0, vieille = -1;
        for (int j = 0; j < nF; j++) {
            if (fNiv[j] != lv) continue;
            n++;
            if (vieille < 0 || fNaiss[j] < fNaiss[vieille]) vieille = j;
        }
        if (n >= max && vieille >= 0) retirerFlaque(vieille);
        if (nF >= MAX_F) retirerFlaque(0);
        int i = nF++;
        fNiv[i] = lv;
        fProprio[i] = proprio;
        fId[i] = ++prochainId;
        fX[i] = x;
        fY[i] = y;
        fZ[i] = z;
        fR[i] = r;
        fNaiss[i] = horloge;
        fDuree[i] = duree;
        fFin[i] = horloge + duree;
        fPap[i] = (byte) pap;
        fZombies[i] = zombies;
        SliqReseau.autour(lv, x, y, z, new PaquetFlaque(PaquetFlaque.AJOUT, fId[i], (float) x, (float) y, (float) z, (float) r, duree, 0, (byte) pap, splat,
                boule, ix, iy, iz, face));
        SliqPerf.flaque();
    }

    private static void retirerFlaque(int i) {
        if (fNiv[i] != null) SliqReseau.autour(fNiv[i], fX[i], fY[i], fZ[i], PaquetFlaque.retrait(fId[i]));
        // ordre garde (la plus vieille d'abord) : decalage, au plus 96 cases
        int n = nF - 1;
        for (int j = i; j < n; j++) {
            fNiv[j] = fNiv[j + 1];
            fProprio[j] = fProprio[j + 1];
            fId[j] = fId[j + 1];
            fDuree[j] = fDuree[j + 1];
            fX[j] = fX[j + 1];
            fY[j] = fY[j + 1];
            fZ[j] = fZ[j + 1];
            fR[j] = fR[j + 1];
            fNaiss[j] = fNaiss[j + 1];
            fFin[j] = fFin[j + 1];
            fPap[j] = fPap[j + 1];
            fZombies[j] = fZombies[j + 1];
        }
        fNiv[n] = null;
        fProprio[n] = null;
        nF = n;
    }

    private static void tickFlaques() {
        for (int i = 0; i < nF; i++) {
            if (horloge >= fFin[i]) {
                retirerFlaque(i--);
                continue;
            }
            // un tick sur deux par flaque (decalees) : une recherche d'entites bornee par la flaque
            if (((horloge + fId[i]) & 1) != 0) continue;
            ServerLevel lv = fNiv[i];
            double x = fX[i], y = fY[i], z = fZ[i], r = fR[i];
            LivingEntity proprio = fProprio[i];
            boolean zombies = fZombies[i];
            List<LivingEntity> l = lv.getEntitiesOfClass(LivingEntity.class, new AABB(x - r - 0.6, y - 0.6, z - r - 0.6, x + r + 0.6, y + 1.0, z + r + 0.6),
                    e -> Regles.estCible(e, proprio, zombies));
            SliqPerf.recherche(l.size());
            for (int k = 0; k < l.size(); k++) {
                LivingEntity e = l.get(k);
                if (e.noPhysics || glisse(e)) continue;
                if (!SliqCalculs.dansFlaque(e.getX(), e.getY(), e.getZ(), e.getBbWidth() * 0.5, x, y, z, r)) continue;
                entrer(e, proprio, fPap[i], zombies);
            }
        }
    }

    private static boolean glisse(LivingEntity e) {
        for (int i = 0; i < nG; i++) if (gE[i] == e) return true;
        return false;
    }

    /** Une creature entre dans la gelee : elle glisse (un boss prend des degats et passe). */
    private static void entrer(LivingEntity e, @Nullable LivingEntity proprio, int pap, boolean zombies) {
        if (zombies && e.getMaxHealth() > SliqConfig.d(SliqConfig.PV_MAX)) {
            long dernier = e.getPersistentData().getLong(NBT_BOSS);
            if (horloge - dernier < 20 && dernier != 0) return;
            e.getPersistentData().putLong(NBT_BOSS, horloge);
            frapper(e, proprio, pap, true, false);
            return;
        }
        if (nG >= MAX_G) {
            frapper(e, proprio, pap, zombies, true);
            return;
        }
        Vec3 v = e.getDeltaMovement();
        double dx = v.x, dz = v.z, l = Math.sqrt(dx * dx + dz * dz);
        if (l < 0.03) {
            float yaw = e.getYRot() * Mth.DEG_TO_RAD;
            dx = -Mth.sin(yaw);
            dz = Mth.cos(yaw);
            l = 1;
        }
        dx /= l;
        dz /= l;
        // un peu de hasard : on ne glisse jamais tout droit
        double a = (e.getRandom().nextDouble() - 0.5) * 0.9, c = Math.cos(a), s = Math.sin(a);
        double ndx = dx * c - dz * s, ndz = dx * s + dz * c;
        int i = nG++;
        gE[i] = e;
        gProprio[i] = proprio;
        gDx[i] = ndx;
        gDz[i] = ndz;
        gV0[i] = SliqConfig.d(SliqConfig.VITESSE_GLISSADE) * (1 + 0.1 * pap);
        gT[i] = 0;
        gPap[i] = (byte) pap;
        gZombies[i] = zombies;
        // horde figee du banc d'essai : rendue a la physique pour qu'elle glisse
        if (e instanceof Mob m) {
            if (m.isNoAi() && e.getPersistentData().getBoolean("heraliz_wonder:banc")) m.setNoAi(false);
            m.getNavigation().stop();
        }
        SliqReseau.autour((ServerLevel) e.level(), e.getX(), e.getY(), e.getZ(), new PaquetZombie(PaquetZombie.GLISSE, e.getId(), (byte) Math.round(ndx * 127),
                (byte) Math.round(ndz * 127), (byte) pap));
        SliqPerf.glissade();
    }

    // ------------------------------------------------------------------ glissades
    private static void tickGlissades() {
        int duree = SliqConfig.i(SliqConfig.DUREE_GLISSADE);
        boolean chaine = SliqConfig.b(SliqConfig.CHAINE);
        int w = 0;
        for (int i = 0; i < nG; i++) {
            LivingEntity e = gE[i];
            if (e == null || !e.isAlive() || e.isRemoved()) {
                gE[i] = null;
                gProprio[i] = null;
                continue;
            }
            int t = ++gT[i];
            if (t >= duree) {
                ServerLevel lv = (ServerLevel) e.level();
                double x = e.getX(), y = e.getY(), z = e.getZ();
                boolean sol = e.onGround();
                frapper(e, gProprio[i], gPap[i], gZombies[i], true);
                // reaction en chaine : le zombie fondu laisse une petite flaque
                if (chaine && !e.isAlive()) {
                    double s = sol ? y : solSous(lv, x, y + 0.2, z, 4);
                    if (!Double.isNaN(s)) creerFlaque(lv, x, s, z, rayon(gPap[i]) * 0.7, (int) (duree(gPap[i]) * 0.6), gProprio[i], gPap[i], gZombies[i],
                            PaquetFlaque.S_AUCUN, -1, null, (byte) -1);
                }
                gE[i] = null;
                gProprio[i] = null;
                continue;
            }
            double v = SliqCalculs.vitesseGlissade(gV0[i], t);
            Vec3 d = e.getDeltaMovement();
            e.setDeltaMovement(gDx[i] * v, Math.min(d.y, 0.05), gDz[i] * v);
            e.hasImpulse = true;
            e.hurtMarked = true;
            if (e instanceof Mob m) m.getNavigation().stop();
            if (i != w) {
                gE[w] = gE[i];
                gProprio[w] = gProprio[i];
                gDx[w] = gDx[i];
                gDz[w] = gDz[i];
                gV0[w] = gV0[i];
                gT[w] = gT[i];
                gPap[w] = gPap[i];
                gZombies[w] = gZombies[i];
                gE[i] = null;
                gProprio[i] = null;
            }
            w++;
        }
        for (int i = w; i < nG; i++) {
            gE[i] = null;
            gProprio[i] = null;
        }
        nG = w;
    }

    /**
     * La gelee frappe : en zombies une creature ordinaire fond (mort, points au tireur), un boss prend {@code degatsColosse} ;
     * hors zombies, {@code degatsHorsZombies} aux monstres. {@code liquefier} : les clients font fondre le corps.
     */
    private static void frapper(LivingEntity e, @Nullable LivingEntity proprio, int pap, boolean zombies, boolean liquefier) {
        if (!e.isAlive()) return;
        ServerLevel lv = (ServerLevel) e.level();
        float dmg = SliqCalculs.degats(zombies, e.getMaxHealth(), e.getHealth(), SliqConfig.d(SliqConfig.PV_MAX), (float) SliqConfig.d(SliqConfig.DEGATS_HORS_ZOMBIES),
                (float) SliqConfig.d(SliqConfig.DEGATS_COLOSSE), pap);
        DamageSource src = Degats.source(lv, SLIQUIFIE, proprio);
        if (e instanceof Mob m && m.isNoAi() && e.getPersistentData().getBoolean("heraliz_wonder:banc")) m.setNoAi(false);
        Degats.blesser(e, src, dmg, false);
        boolean mort = !e.isAlive();
        if (mort) SliqPerf.mort();
        SliqReseau.autour(lv, e.getX(), e.getY(), e.getZ(), new PaquetZombie(mort && liquefier ? PaquetZombie.LIQUEFIE : PaquetZombie.COLOSSE, e.getId(),
                (byte) 0, (byte) 0, (byte) pap));
    }

    /** Hauteur du sol sous le point (au plus {@code max} blocs plus bas), NaN si rien. Sans allocation. */
    static double solSous(ServerLevel lv, double x, double y, double z, double max) {
        int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
        for (int d = 0; d <= (int) Math.ceil(max); d++) {
            MP.set(bx, by - d, bz);
            if (!lv.isLoaded(MP)) return Double.NaN;
            BlockState s = lv.getBlockState(MP);
            if (s.isAir()) continue;
            VoxelShape sh = s.getCollisionShape(lv, MP);
            if (sh.isEmpty()) continue;
            double top = MP.getY() + sh.max(Direction.Axis.Y);
            if (top <= y + 0.01 && y - top <= max) return top;
        }
        return Double.NaN;
    }

    // ================================================================== joueurs qui arrivent, menage
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    public static void changementDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    public static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    /** Envoie au joueur les reglages de glisse et les flaques de sa dimension. */
    public static void sync(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        int n = 0;
        for (int i = 0; i < nF; i++) if (fNiv[i] == lv) n++;
        n = Math.min(n, PaquetSync.MAX);
        float[] f = new float[n * PaquetSync.CHAMPS];
        int[] ids = new int[n];
        int k = 0;
        for (int i = 0; i < nF && k < n; i++) {
            if (fNiv[i] != lv) continue;
            int o = k * PaquetSync.CHAMPS;
            ids[k] = fId[i];
            f[o] = (float) fX[i];
            f[o + 1] = (float) fY[i];
            f[o + 2] = (float) fZ[i];
            f[o + 3] = (float) fR[i];
            f[o + 4] = fDuree[i];
            f[o + 5] = horloge - fNaiss[i];
            f[o + 6] = fPap[i];
            k++;
        }
        SliqReseau.a(p, new PaquetSync(SliqConfig.b(SliqConfig.GLISSE_JOUEURS), (float) SliqConfig.d(SliqConfig.BOOST_JOUEURS),
                (float) SliqConfig.d(SliqConfig.VITESSE_MAX_JOUEURS), n, f, ids));
    }

    public static void dechargement(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel lv)) return;
        for (int i = 0; i < nB; i++) if (bNiv[i] == lv) retirerBoule(i--, false);
        for (int i = 0; i < nF; i++) if (fNiv[i] == lv) retirerFlaque(i--);
        for (int i = 0; i < nG; i++) if (gE[i] != null && gE[i].level() == lv) gE[i] = null;
    }

    public static void arret(ServerStoppedEvent e) {
        vider();
    }

    /** Efface tout (arret du serveur, commande de menage). */
    public static void vider() {
        for (int i = 0; i < MAX_B; i++) {
            bNiv[i] = null;
            bTireur[i] = null;
        }
        for (int i = 0; i < MAX_F; i++) {
            fNiv[i] = null;
            fProprio[i] = null;
        }
        for (int i = 0; i < MAX_G; i++) {
            gE[i] = null;
            gProprio[i] = null;
        }
        nB = nF = nG = 0;
    }

    /** Retire toutes les flaques (commande {@code /sliq nettoyer}) en prevenant les clients. */
    public static int nettoyer() {
        int n = nF;
        while (nF > 0) retirerFlaque(nF - 1);
        return n;
    }

    public static MutableComponent etat() {
        return Component.translatableWithFallback("heraliz_wonder.banc.sliq_etat", "%s boules en vol, %s flaques, %s glissades",
                String.valueOf(nB), String.valueOf(nF), String.valueOf(nG));
    }
}
