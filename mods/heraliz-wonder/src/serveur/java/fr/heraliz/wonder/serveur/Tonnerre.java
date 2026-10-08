package fr.heraliz.wonder.serveur;

import fr.heraliz.wonder.Calculs;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.WonderConfig;
import fr.heraliz.wonder.net.Reseau;
import fr.heraliz.wonder.net.Souffle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Onde de choc du Thundergun (Call of Duty : Black Ops, Kino der Toten) : un cone d'air de {@code portee} blocs qui
 * s'ouvre de {@code demiAngle} degres devant le tireur. Chaque creature valable dans le cone et en vue directe des yeux
 * du tireur est soufflee : projetee en l'air et au loin (plus fort pres de la bouche), et en partie de zombies elle
 * meurt (CoD : un tir = une horde balayee) ; un boss prend {@code degatsColosse} et un recul reduit par sa resistance.
 * <p><b>Aucun bloc casse.</b> Jamais un joueur (ni le tireur) : seulement les creatures de la partie en zombies, les
 * monstres ailleurs ({@link Regles#estCible}). Decide par le serveur seul, <b>une fois par tir</b> : une recherche
 * d'entites bornee par la boite du cone, un test de cone par candidate, un lancer de rayon par creature retenue (au plus
 * {@code cibles}), puis un seul paquet {@link Souffle}. Chaque creature est frappee quand le front d'onde l'atteint
 * (0 a 11 ticks selon la distance, file fixe) : synchro avec l'effet, et le cout des morts est etale.
 * <p>Pack-a-Punch (Zeus Cannon) : cone plus long et plus ouvert, degats des boss augmentes.
 */
public final class Tonnerre {
    private Tonnerre() {
    }

    private static final int MAX_CANDIDATS = 256;
    // file des coups differes (le front d'onde met jusqu'a 11 ticks pour parcourir le cone) : taille fixe, sans allocation
    private static final int FILE = 512;
    private static final LivingEntity[] F_CIBLE = new LivingEntity[FILE];
    private static final DamageSource[] F_SRC = new DamageSource[FILE];
    private static final float[] F_DEGATS = new float[FILE];
    private static final double[] F_VX = new double[FILE], F_VY = new double[FILE], F_VZ = new double[FILE];
    private static final long[] F_TICK = new long[FILE];
    private static int fDebut, fTaille;
    private static long horloge;
    // tampons reutilises (fil serveur unique)
    private static final LivingEntity[] C_E = new LivingEntity[MAX_CANDIDATS];
    private static final double[] C_S = new double[MAX_CANDIDATS];

    public static double portee(int pap) {
        return Calculs.porteeSouffle(pap, WonderConfig.d(WonderConfig.TG_PORTEE), WonderConfig.d(WonderConfig.TG_PORTEE_PAP));
    }

    public static double angle(int pap) {
        return Calculs.angleSouffle(pap, WonderConfig.d(WonderConfig.TG_ANGLE), WonderConfig.d(WonderConfig.TG_ANGLE_PAP));
    }

    /** Un tir : {@code depart} = yeux du tireur (depart de la balle TaCZ), {@code dir} = direction du regard. */
    public static void tirer(ServerLevel niveau, LivingEntity tireur, Vec3 depart, Vec3 dir, int pap) {
        long t0 = System.nanoTime();
        boolean zombies = Regles.enZombies(tireur);
        double portee = portee(pap), angle = angle(pap), r0 = WonderConfig.d(WonderConfig.TG_BOUCHE);
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? tireur.getLookAngle() : dir.normalize();
        double rmax = r0 + portee * Math.tan(Math.toRadians(angle));
        AABB boite = new AABB(depart, depart.add(d.scale(portee))).inflate(rmax + 1.0);
        List<LivingEntity> l = niveau.getEntitiesOfClass(LivingEntity.class, boite, e -> Regles.estCible(e, tireur, zombies));
        // dans le cone : distance le long de l'axe, tri par insertion (les plus proches d'abord)
        int n = 0;
        for (int i = 0; i < l.size() && n < MAX_CANDIDATS; i++) {
            LivingEntity e = l.get(i);
            double cy = e.getY() + e.getBbHeight() * 0.5;
            double marge = e.getBbWidth() * 0.5 + Math.min(e.getBbHeight() * 0.25, 0.6);
            double s = Calculs.dansCone(e.getX() - depart.x, cy - depart.y, e.getZ() - depart.z, d.x, d.y, d.z, portee, angle, r0, marge);
            if (s < 0) continue;
            int j = n++;
            while (j > 0 && C_S[j - 1] > s) {
                C_S[j] = C_S[j - 1];
                C_E[j] = C_E[j - 1];
                j--;
            }
            C_S[j] = s;
            C_E[j] = e;
        }
        int max = Math.min(WonderConfig.i(WonderConfig.TG_CIBLES), Souffle.MAX);
        DamageSource src = Degats.source(niveau, Degats.SOUFFLE, tireur);
        double seuil = WonderConfig.d(WonderConfig.TG_PV_MAX), force = WonderConfig.d(WonderConfig.TG_POUSSEE);
        float hors = (float) WonderConfig.d(WonderConfig.TG_DEGATS_HORS_ZOMBIES), colosse = (float) WonderConfig.d(WonderConfig.TG_DEGATS_COLOSSE);
        int[] ids = new int[Math.min(n, max)];
        int touchees = 0, lancers = 0;
        for (int k = 0; k < n && touchees < max; k++) {
            LivingEntity e = C_E[k];
            C_E[k] = null;
            if (!e.isAlive()) continue;
            lancers++;
            if (!Eclaboussure.aDecouvert(niveau, depart, e, tireur)) continue;
            double s = C_S[k];
            // souffle : loin du tireur (axe du tir + un peu de radial), et vers le haut
            double rx = e.getX() - depart.x, rz = e.getZ() - depart.z;
            double rl = Math.sqrt(rx * rx + rz * rz);
            double hx = d.x * 0.7 + (rl > 1e-4 ? rx / rl : d.x) * 0.3, hz = d.z * 0.7 + (rl > 1e-4 ? rz / rl : d.z) * 0.3;
            double hl = Math.sqrt(hx * hx + hz * hz);
            if (hl > 1e-4) {
                hx /= hl;
                hz /= hl;
            }
            double resist = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null ? e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
            double v = Calculs.pousseeSouffle(s, portee, force, resist);
            // en cloche : la creature monte franchement puis retombe loin (on la voit voler)
            double vy = Math.max(0.55, 0.75 + 0.35 * (1 - s / portee) + d.y * 0.5) * (v / Math.max(force, 1e-6));
            float degats = Calculs.degatsSouffle(zombies, e.getMaxHealth(), e.getHealth(), seuil, hors, colosse, pap);
            // le coup tombe quand le front d'onde arrive (les plus loin un peu apres) : synchro avec l'effet, et le cout
            // des morts est etale sur plusieurs ticks au lieu d'un seul
            programmer(e, src, degats, hx * v, Math.min(1.4, vy), hz * v, Calculs.arriveeSouffle(s, portee));
            ids[touchees++] = e.getId();
        }
        for (int k = 0; k < n; k++) C_E[k] = null;
        Reseau.autour(niveau, tireur.getX(), tireur.getY(), tireur.getZ(), new Souffle(tireur.getId(), (byte) pap, (float) depart.x, (float) depart.y,
                (float) depart.z, (float) d.x, (float) d.y, (float) d.z, (float) portee, (float) angle, (float) r0, touchees, ids));
        Perf.souffle(System.nanoTime() - t0, touchees, lancers);
    }

    private static void programmer(LivingEntity e, DamageSource src, float degats, double vx, double vy, double vz, int delai) {
        if (delai <= 0 || fTaille >= FILE) {
            appliquer(e, src, degats, vx, vy, vz);
            return;
        }
        int i = (fDebut + fTaille) % FILE;
        F_CIBLE[i] = e;
        F_SRC[i] = src;
        F_DEGATS[i] = degats;
        F_VX[i] = vx;
        F_VY[i] = vy;
        F_VZ[i] = vz;
        F_TICK[i] = horloge + delai;
        fTaille++;
    }

    /** Le souffle frappe : degats (sans le recul de Minecraft) puis la creature est projetee. */
    private static void appliquer(LivingEntity e, DamageSource src, float degats, double vx, double vy, double vz) {
        if (!e.isAlive() || e.isRemoved()) return;
        Degats.blesser(e, src, degats, false);
        // une creature figee (sans IA) ne bouge pas : corps d'une creature tuee, ou horde du banc d'essai, rendus a la physique
        if (e instanceof net.minecraft.world.entity.Mob m && m.isNoAi() && (!e.isAlive() || e.getPersistentData().getBoolean("heraliz_wonder:banc"))) {
            m.setNoAi(false);
        }
        e.setDeltaMovement(vx, vy, vz);
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    /** Fin de tick serveur : coups arrives (la file est parcourue une fois et recompactee). */
    public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent ev) {
        if (ev.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        horloge++;
        if (fTaille == 0) return;
        long t0 = System.nanoTime();
        int n = fTaille, garde = 0;
        for (int k = 0; k < n; k++) {
            int i = (fDebut + k) % FILE;
            LivingEntity c = F_CIBLE[i];
            if (F_TICK[i] <= horloge) {
                try {
                    appliquer(c, F_SRC[i], F_DEGATS[i], F_VX[i], F_VY[i], F_VZ[i]);
                } catch (RuntimeException ex) {
                    fr.heraliz.wonder.HeralizWonder.LOG.error("[wonder] souffle du Thundergun", ex);
                }
            } else {
                int j = (fDebut + garde) % FILE;
                F_CIBLE[j] = c;
                F_SRC[j] = F_SRC[i];
                F_DEGATS[j] = F_DEGATS[i];
                F_VX[j] = F_VX[i];
                F_VY[j] = F_VY[i];
                F_VZ[j] = F_VZ[i];
                F_TICK[j] = F_TICK[i];
                garde++;
            }
        }
        for (int k = garde; k < n; k++) {
            int i = (fDebut + k) % FILE;
            F_CIBLE[i] = null;
            F_SRC[i] = null;
        }
        fTaille = garde;
        Perf.souffleDiffere(System.nanoTime() - t0);
    }
}
