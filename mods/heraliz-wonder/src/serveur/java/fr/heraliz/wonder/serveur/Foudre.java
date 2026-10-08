package fr.heraliz.wonder.serveur;

import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.Calculs;
import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.WonderConfig;
import fr.heraliz.wonder.net.Chaine;
import fr.heraliz.wonder.net.Impact;
import fr.heraliz.wonder.net.Reseau;
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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Foudre en chaine de la Wunderwaffe DG-2 (CoD World at War / Black Ops) : l'eclair part du canon, frappe la premiere
 * cible sur la ligne de tir, puis saute a chaque fois sur la cible la plus proche pas encore touchee (a moins de
 * {@code saut} blocs, en vue directe), jusqu'a {@code cibles} creatures ({@link Calculs#chaine}). Pack-a-Punch (DG-3 JZ) :
 * plus de cibles, sauts plus longs.
 * <p>Le serveur calcule la chaine <b>une seule fois par tir</b> (une recherche d'entites et au plus 3 lancers de rayon par
 * saut, bornes), puis envoie un seul paquet {@link Chaine}. Les degats suivent l'arc : la cible {@code i} est foudroyee
 * {@link Calculs#delaiArc} ticks apres le tir (file fixe, sans allocation). En partie de zombies, une creature normale meurt
 * d'un coup (comme dans CoD) ; un boss prend {@code degatsColosse}. Le tireur n'est jamais touche, les joueurs non plus.
 */
public final class Foudre {
    private Foudre() {
    }

    // ------------------------------------------------------------------ file des degats differes (taille fixe)
    private static final int FILE = 512;
    private static final LivingEntity[] F_CIBLE = new LivingEntity[FILE];
    private static final DamageSource[] F_SRC = new DamageSource[FILE];
    private static final float[] F_DEGATS = new float[FILE];
    private static final long[] F_TICK = new long[FILE];
    private static int fDebut, fTaille;
    private static long horloge;

    public static int cibles(int pap) {
        return Calculs.ciblesFoudre(pap, WonderConfig.i(WonderConfig.DG_CIBLES), WonderConfig.i(WonderConfig.DG_CIBLES_PAP), Chaine.MAX);
    }

    public static double saut(int pap) {
        return Calculs.sautFoudre(pap, WonderConfig.d(WonderConfig.DG_SAUT), WonderConfig.d(WonderConfig.DG_SAUT_PAP));
    }

    /** Le monde vu par la chaine : les creatures valables, numerotees dans l'ordre ou on les rencontre pendant le tir. */
    private static final class MondeEntites implements Calculs.Monde {
        final ServerLevel niveau;
        final LivingEntity tireur;
        final boolean zombies;
        final List<LivingEntity> vues = new ArrayList<>();
        final IdentityHashMap<LivingEntity, Integer> numeros = new IdentityHashMap<>();
        int lancers;

        MondeEntites(ServerLevel niveau, LivingEntity tireur, boolean zombies) {
            this.niveau = niveau;
            this.tireur = tireur;
            this.zombies = zombies;
        }

        int numero(LivingEntity e) {
            Integer n = numeros.get(e);
            if (n == null) {
                n = vues.size();
                vues.add(e);
                numeros.put(e, n);
            }
            return n;
        }

        @Override
        public int candidats(double x, double y, double z, double r, int[] out) {
            List<LivingEntity> l = niveau.getEntitiesOfClass(LivingEntity.class, new AABB(x - r, y - r, z - r, x + r, y + r, z + r),
                    e -> Regles.estCible(e, tireur, zombies));
            int n = Math.min(l.size(), out.length);
            for (int i = 0; i < n; i++) out[i] = numero(l.get(i));
            return n;
        }

        @Override
        public double x(int c) {
            return vues.get(c).getX();
        }

        @Override
        public double y(int c) {
            LivingEntity e = vues.get(c);
            return e.getY() + e.getBbHeight() * 0.55;
        }

        @Override
        public double z(int c) {
            return vues.get(c).getZ();
        }

        @Override
        public boolean voit(double x, double y, double z, int c) {
            lancers++;
            return Eclaboussure.aDecouvert(niveau, new Vec3(x, y, z), vues.get(c), tireur);
        }
    }

    /**
     * Un tir : {@code depart} = point de depart de la balle TaCZ (yeux du tireur), {@code dir} = sa direction (dispersion
     * comprise).
     */
    public static void tirer(ServerLevel niveau, LivingEntity tireur, Vec3 depart, Vec3 dir, int pap) {
        long t0 = System.nanoTime();
        boolean zombies = Regles.enZombies(tireur);
        double portee = WonderConfig.d(WonderConfig.DG_PORTEE);
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? tireur.getLookAngle() : dir.normalize();
        Vec3 fin = depart.add(d.scale(portee));
        BlockHitResult bloc = niveau.clip(new ClipContext(depart, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tireur));
        boolean blocTouche = bloc.getType() != HitResult.Type.MISS;
        if (blocTouche) fin = bloc.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(niveau, tireur, depart, fin, new AABB(depart, fin).inflate(1.0),
                e -> Regles.estCible(e, tireur, zombies), 0.3f);

        MondeEntites monde = new MondeEntites(niveau, tireur, zombies);
        int[] tampon = new int[Calculs.CANDIDATS_MAX];
        int max = cibles(pap);
        int[] ordre = new int[Math.max(1, max)];
        int premiere = -1;
        Vec3 impact;
        if (eh != null && eh.getEntity() instanceof LivingEntity p) {
            impact = eh.getLocation();
            premiere = monde.numero(p);
            blocTouche = false;
        } else {
            impact = fin;
            // eclair qui frappe le sol ou un mur pres de la horde : il saute quand meme sur la plus proche (CoD)
            double rs = WonderConfig.d(WonderConfig.DG_RAYON_SOL);
            if (blocTouche && rs > 0) premiere = Calculs.plusProche(monde, impact.x, impact.y, impact.z, rs, ordre, 0, tampon);
        }
        int n = Calculs.chaine(monde, premiere, max, saut(pap), ordre, tampon);

        // degats differes le long de la chaine, et un seul paquet pour les effets
        DamageSource src = Degats.source(niveau, Degats.FOUDRE, tireur);
        int[] ids = new int[n];
        float[] pos = new float[n * 3];
        double seuil = WonderConfig.d(WonderConfig.DG_PV_MAX_FOUDROYE);
        float hors = (float) WonderConfig.d(WonderConfig.DG_DEGATS_HORS_ZOMBIES), colosse = (float) WonderConfig.d(WonderConfig.DG_DEGATS_COLOSSE);
        for (int i = 0; i < n; i++) {
            LivingEntity e = monde.vues.get(ordre[i]);
            ids[i] = e.getId();
            pos[i * 3] = (float) e.getX();
            pos[i * 3 + 1] = (float) (e.getY() + e.getBbHeight() * 0.55);
            pos[i * 3 + 2] = (float) e.getZ();
            programmer(e, src, Calculs.degatsFoudre(zombies, e.getMaxHealth(), e.getHealth(), seuil, hors, colosse), Calculs.delaiArc(i));
        }
        byte dr = blocTouche ? Chaine.BLOC : 0;
        Reseau.autour(niveau, tireur.getX(), tireur.getY(), tireur.getZ(),
                new Chaine(tireur.getId(), (byte) pap, dr, (float) impact.x, (float) impact.y, (float) impact.z, n, ids, pos));
        if (n == 0) {
            // eclair perdu : juste l'impact au bout
            Reseau.autour(niveau, impact.x, impact.y, impact.z, new Impact(Armes.DG2, (byte) pap, (float) impact.x, (float) impact.y,
                    (float) impact.z, (byte) (blocTouche ? bloc.getDirection().get3DDataValue() : -1), (byte) 0));
        }
        Perf.foudre(System.nanoTime() - t0, n, monde.lancers);
    }

    private static void programmer(LivingEntity e, DamageSource src, float degats, int delai) {
        if (delai <= 0 || fTaille >= FILE) {
            // tout de suite (premiere cible, ou file pleine dans un cas extreme : plutot maintenant que jamais)
            appliquer(e, src, degats);
            return;
        }
        int i = (fDebut + fTaille) % FILE;
        F_CIBLE[i] = e;
        F_SRC[i] = src;
        F_DEGATS[i] = degats;
        F_TICK[i] = horloge + delai;
        fTaille++;
    }

    private static void appliquer(LivingEntity e, DamageSource src, float degats) {
        if (!e.isAlive() || e.isRemoved()) return;
        Degats.blesser(e, src, degats, false);
    }

    /** Fin de tick serveur : degats arrives ; la file est parcourue une fois et recompactee (deux tirs peuvent s'entrelacer). */
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        horloge++;
        if (fTaille == 0) return;
        int n = fTaille, garde = 0;
        for (int k = 0; k < n; k++) {
            int i = (fDebut + k) % FILE;
            LivingEntity c = F_CIBLE[i];
            if (F_TICK[i] <= horloge) {
                try {
                    appliquer(c, F_SRC[i], F_DEGATS[i]);
                } catch (RuntimeException ex) {
                    HeralizWonder.LOG.error("[wonder] degats de foudre", ex);
                }
            } else {
                int j = (fDebut + garde) % FILE;
                F_CIBLE[j] = c;
                F_SRC[j] = F_SRC[i];
                F_DEGATS[j] = F_DEGATS[i];
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
    }
}
