package fr.heraliz.wonder.blundergat.serveur;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.blundergat.BlunderArmes;
import fr.heraliz.wonder.blundergat.BlunderConfig;
import fr.heraliz.wonder.blundergat.CalculsBlunder;
import fr.heraliz.wonder.blundergat.net.Acide;
import fr.heraliz.wonder.blundergat.net.BlunderReseau;
import fr.heraliz.wonder.serveur.Degats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.util.List;

/**
 * Cartouches d'acide de l'Acid Gat (CoD : trois cartouches par tir, qui collent aux zombies ou aux murs puis explosent
 * apres un court delai). Simulees par le serveur seul dans une reserve fixe (tableaux paralleles, aucune entite creee) :
 * <ul>
 *   <li>vol : un pas de {@link CalculsBlunder#pas} par tick, un lancer de rayon sur les blocs et une recherche d'entites
 *   bornee au segment du tick ; la premiere creature valable touchee ({@link Regles#estCible} : jamais un joueur) ou le
 *   premier bloc arrete la cartouche, qui colle ;</li>
 *   <li>collee : elle suit la creature (decalage fixe) ; si la creature meurt avant, elle tombe ; elle explose
 *   {@code delaiAcide} ticks apres avoir colle (en l'air apres {@code volMaxAcide} ticks si elle ne touche rien) ;</li>
 *   <li>explosion : une recherche d'entites bornee par le rayon, un lancer de rayon par creature (vue directe ; pas pour
 *   la porteuse), au plus {@code ciblesAcide} creatures, degats degressifs ({@link CalculsBlunder#degatsAcide}) du type
 *   {@code heraliz_wonder:acide} (explosion : le mode zombies applique le Pack-a-Punch et paie les kills au tireur).
 *   <b>Aucun bloc casse.</b></li>
 * </ul>
 * Trois paquets {@link Acide} au plus par cartouche (tir groupe, collee, explosion).
 */
public final class Cartouches {
    private Cartouches() {
    }

    private static final int MAX = 192;
    private static final byte VOL = 0, ENTITE = 1, BLOC = 2, CHUTE = 3;
    private static final double[] X = new double[MAX], Y = new double[MAX], Z = new double[MAX];
    private static final double[] VX = new double[MAX], VY = new double[MAX], VZ = new double[MAX];
    private static final double[] OX = new double[MAX], OY = new double[MAX], OZ = new double[MAX];
    private static final LivingEntity[] TIREUR = new LivingEntity[MAX];
    private static final LivingEntity[] SUR = new LivingEntity[MAX];
    private static final ServerLevel[] NIVEAU = new ServerLevel[MAX];
    private static final byte[] PAP = new byte[MAX], ETAT = new byte[MAX];
    private static final boolean[] ZOMBIES = new boolean[MAX];
    private static final int[] AGE = new int[MAX], COLLEE = new int[MAX], ID = new int[MAX];
    private static int n;
    private static int prochainId = 1;

    // tampons reutilises (fil serveur unique)
    private static final double[] P = new double[3], V = new double[3], DIR = new double[3];
    private static final int MAX_CIBLES = 128;
    private static final LivingEntity[] C_E = new LivingEntity[MAX_CIBLES];
    private static final double[] C_D = new double[MAX_CIBLES];
    private static final int[] TOUCHEES = new int[Acide.MAX];

    /** Un tir : {@code depart} = yeux du tireur, {@code dir} = regard (unitaire). Trois cartouches en eventail. */
    public static void tirer(ServerLevel niveau, LivingEntity tireur, Vec3 depart, Vec3 dir, int pap) {
        long t0 = System.nanoTime();
        boolean zombies = Regles.enZombies(tireur);
        double vit = BlunderConfig.vitesseAcide(), ecart = BlunderConfig.ecartAcide();
        int nb = 3;
        int[] ids = new int[nb];
        float[] pv = new float[nb * 6];
        int k = 0;
        for (int c = 0; c < nb; c++) {
            if (n >= MAX) break;
            CalculsBlunder.directionCartouche(dir.x, dir.y, dir.z, c, nb, ecart, DIR);
            // legere dispersion verticale (les trois ne sont jamais parfaitement alignees)
            double jitter = (tireur.getRandom().nextDouble() - 0.5) * 0.03;
            int i = n++;
            NIVEAU[i] = niveau;
            TIREUR[i] = tireur;
            SUR[i] = null;
            PAP[i] = (byte) pap;
            ETAT[i] = VOL;
            ZOMBIES[i] = zombies;
            AGE[i] = 0;
            COLLEE[i] = -1;
            ID[i] = prochainId;
            prochainId = prochainId >= (1 << 20) ? 1 : prochainId + 1;
            X[i] = depart.x + DIR[0] * 0.5;
            Y[i] = depart.y + DIR[1] * 0.5 - 0.12;
            Z[i] = depart.z + DIR[2] * 0.5;
            VX[i] = DIR[0] * vit;
            // un peu d'elan vers le haut : la cartouche suit une cloche et colle au torse des zombies (pas a leurs pieds)
            VY[i] = (DIR[1] + jitter) * vit + 0.08;
            VZ[i] = DIR[2] * vit;
            ids[k] = ID[i];
            pv[k * 6] = (float) X[i];
            pv[k * 6 + 1] = (float) Y[i];
            pv[k * 6 + 2] = (float) Z[i];
            pv[k * 6 + 3] = (float) VX[i];
            pv[k * 6 + 4] = (float) VY[i];
            pv[k * 6 + 5] = (float) VZ[i];
            k++;
        }
        if (k > 0) BlunderReseau.autour(niveau, tireur.getX(), tireur.getY(), tireur.getZ(), Acide.tir(tireur.getId(), (byte) pap, k, ids, pv));
        BlunderPerf.tir(System.nanoTime() - t0, k);
    }

    /** Fin de tick serveur : vol, suivi des porteuses, explosions (reserve parcourue une fois et recompactee). */
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || n == 0) return;
        long t0 = System.nanoTime();
        int delai = BlunderConfig.delaiAcide(), volMax = BlunderConfig.volMaxAcide();
        int w = 0;
        for (int i = 0; i < n; i++) {
            boolean garder;
            try {
                garder = avancer(i, delai, volMax);
            } catch (RuntimeException ex) {
                HeralizWonder.LOG.error("[wonder] cartouche d'acide", ex);
                garder = false;
            }
            if (garder) copier(i, w++);
        }
        for (int i = w; i < n; i++) {
            TIREUR[i] = null;
            SUR[i] = null;
            NIVEAU[i] = null;
        }
        n = w;
        BlunderPerf.cartouches(System.nanoTime() - t0, n);
    }

    /** Fait avancer la cartouche i d'un tick ; rend faux si elle a explose (place liberee). */
    private static boolean avancer(int i, int delai, int volMax) {
        ServerLevel niveau = NIVEAU[i];
        AGE[i]++;
        switch (ETAT[i]) {
            case VOL -> {
                P[0] = X[i];
                P[1] = Y[i];
                P[2] = Z[i];
                V[0] = VX[i];
                V[1] = VY[i];
                V[2] = VZ[i];
                Vec3 a = new Vec3(X[i], Y[i], Z[i]);
                CalculsBlunder.pas(P, V);
                Vec3 b = new Vec3(P[0], P[1], P[2]);
                LivingEntity tireur = TIREUR[i];
                Entity contexte = tireur != null ? tireur : null;
                BlockHitResult bh = niveau.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, contexte));
                Vec3 fin = bh.getType() == HitResult.Type.MISS ? b : bh.getLocation();
                boolean z = ZOMBIES[i];
                EntityHitResult eh = ProjectileUtil.getEntityHitResult(niveau, tireur, a, fin, new AABB(a, fin).inflate(0.6),
                        x -> Regles.estCible(x, tireur, z), 0.25f);
                if (eh != null && eh.getEntity() instanceof LivingEntity le) {
                    Vec3 h = eh.getLocation();
                    SUR[i] = le;
                    ETAT[i] = ENTITE;
                    COLLEE[i] = AGE[i];
                    OX[i] = h.x - le.getX();
                    OY[i] = Math.max(0.2, Math.min(le.getBbHeight() - 0.15, h.y - le.getY()));
                    OZ[i] = h.z - le.getZ();
                    X[i] = le.getX() + OX[i];
                    Y[i] = le.getY() + OY[i];
                    Z[i] = le.getZ() + OZ[i];
                    BlunderReseau.autour(niveau, X[i], Y[i], Z[i], Acide.colle(ID[i], le.getId(), (float) X[i], (float) Y[i], (float) Z[i]));
                } else if (bh.getType() != HitResult.Type.MISS) {
                    Vec3 h = bh.getLocation();
                    X[i] = h.x + bh.getDirection().getStepX() * 0.08;
                    Y[i] = h.y + bh.getDirection().getStepY() * 0.08;
                    Z[i] = h.z + bh.getDirection().getStepZ() * 0.08;
                    ETAT[i] = BLOC;
                    COLLEE[i] = AGE[i];
                    BlunderReseau.autour(niveau, X[i], Y[i], Z[i], Acide.colle(ID[i], -1, (float) X[i], (float) Y[i], (float) Z[i]));
                } else {
                    X[i] = P[0];
                    Y[i] = P[1];
                    Z[i] = P[2];
                    VX[i] = V[0];
                    VY[i] = V[1];
                    VZ[i] = V[2];
                    if (AGE[i] >= volMax || Y[i] < niveau.getMinBuildHeight() - 8) {
                        exploser(i);
                        return false;
                    }
                }
            }
            case ENTITE -> {
                LivingEntity le = SUR[i];
                if (le != null && le.isAlive() && !le.isRemoved()) {
                    X[i] = le.getX() + OX[i];
                    Y[i] = le.getY() + OY[i];
                    Z[i] = le.getZ() + OZ[i];
                } else {
                    // la porteuse est morte : la cartouche tombe au sol
                    SUR[i] = null;
                    ETAT[i] = CHUTE;
                    VX[i] = VZ[i] = 0;
                    VY[i] = 0;
                }
            }
            case CHUTE -> {
                Vec3 a = new Vec3(X[i], Y[i], Z[i]);
                VY[i] = Math.max(-1.2, VY[i] - CalculsBlunder.GRAVITE * 2);
                Vec3 b = new Vec3(X[i], Y[i] + VY[i], Z[i]);
                BlockHitResult bh = niveau.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
                if (bh.getType() != HitResult.Type.MISS) {
                    Y[i] = bh.getLocation().y + 0.08;
                    ETAT[i] = BLOC;
                } else {
                    Y[i] = b.y;
                }
            }
            default -> {
            }
        }
        if (COLLEE[i] >= 0 && AGE[i] - COLLEE[i] >= delai) {
            exploser(i);
            return false;
        }
        return true;
    }

    /** Explosion d'acide de la cartouche i : degats aux creatures valables du rayon, en vue directe ; un paquet. */
    private static void exploser(int i) {
        long t0 = System.nanoTime();
        ServerLevel niveau = NIVEAU[i];
        LivingEntity tireur = TIREUR[i];
        boolean zombies = ZOMBIES[i];
        int pap = PAP[i];
        double r = BlunderConfig.rayonAcide(pap);
        double x = X[i], y = Y[i], z = Z[i];
        Vec3 centre = new Vec3(x, y, z);
        List<LivingEntity> l = niveau.getEntitiesOfClass(LivingEntity.class, new AABB(x - r, y - r, z - r, x + r, y + r, z + r),
                e -> Regles.estCible(e, tireur, zombies));
        // les plus proches d'abord (tri par insertion dans des tampons fixes)
        int nc = 0;
        for (int k = 0; k < l.size() && nc < MAX_CIBLES; k++) {
            LivingEntity e = l.get(k);
            double d = distanceBoite(e.getBoundingBox(), x, y, z);
            if (d > r) continue;
            int j = nc++;
            while (j > 0 && C_D[j - 1] > d) {
                C_D[j] = C_D[j - 1];
                C_E[j] = C_E[j - 1];
                j--;
            }
            C_D[j] = d;
            C_E[j] = e;
        }
        int max = Math.min(BlunderConfig.ciblesAcide(), TOUCHEES.length);
        DamageSource src = Degats.source(niveau, BlunderArmes.ACIDE, tireur);
        double centreDegats = zombies ? BlunderConfig.degatsAcide() : BlunderConfig.degatsAcideHorsZombies() * CalculsBlunder.facteurPapHors(pap);
        int touchees = 0, lancers = 0;
        for (int k = 0; k < nc; k++) {
            LivingEntity e = C_E[k];
            C_E[k] = null;
            if (touchees >= max || !e.isAlive()) continue;
            if (e != SUR[i]) {
                lancers++;
                if (!aDecouvert(niveau, centre, e)) continue;
            }
            float dmg = CalculsBlunder.degatsAcide(C_D[k], r, centreDegats);
            if (Degats.blesser(e, src, dmg, false)) TOUCHEES[touchees++] = e.getId();
        }
        BlunderReseau.autour(niveau, x, y, z, Acide.explose(ID[i], (byte) pap, (float) x, (float) y, (float) z, (byte) Math.min(127, Math.round(r * 10)),
                touchees, TOUCHEES));
        BlunderPerf.explosion(System.nanoTime() - t0, touchees, lancers);
    }

    private static double distanceBoite(AABB b, double x, double y, double z) {
        double dx = Math.max(Math.max(b.minX - x, 0), x - b.maxX);
        double dy = Math.max(Math.max(b.minY - y, 0), y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - z, 0), z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Pas de bloc plein entre le centre de l'explosion et le milieu de la creature. */
    private static boolean aDecouvert(ServerLevel niveau, Vec3 depuis, Entity e) {
        Vec3 vers = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
        if (depuis.distanceToSqr(vers) < 0.36) return true;
        return niveau.clip(new ClipContext(depuis, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS;
    }

    private static void copier(int i, int w) {
        if (i == w) return;
        X[w] = X[i];
        Y[w] = Y[i];
        Z[w] = Z[i];
        VX[w] = VX[i];
        VY[w] = VY[i];
        VZ[w] = VZ[i];
        OX[w] = OX[i];
        OY[w] = OY[i];
        OZ[w] = OZ[i];
        TIREUR[w] = TIREUR[i];
        SUR[w] = SUR[i];
        NIVEAU[w] = NIVEAU[i];
        PAP[w] = PAP[i];
        ETAT[w] = ETAT[i];
        ZOMBIES[w] = ZOMBIES[i];
        AGE[w] = AGE[i];
        COLLEE[w] = COLLEE[i];
        ID[w] = ID[i];
    }

    /** Monde decharge : ses cartouches disparaissent sans exploser. */
    public static void dechargement(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) return;
        int w = 0;
        for (int i = 0; i < n; i++) if (NIVEAU[i] != e.getLevel()) copier(i, w++);
        for (int i = w; i < n; i++) {
            TIREUR[i] = null;
            SUR[i] = null;
            NIVEAU[i] = null;
        }
        n = w;
    }

    /** Cartouches en cours (statistiques). */
    public static int enCours() {
        return n;
    }
}
