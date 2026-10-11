package fr.heraliz.wonder.jetgun.serveur;

import fr.heraliz.wonder.HeralizWonder;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.jetgun.JetCalculs;
import fr.heraliz.wonder.jetgun.JetConfig;
import fr.heraliz.wonder.jetgun.net.Broyage;
import fr.heraliz.wonder.jetgun.net.JetReseau;
import fr.heraliz.wonder.serveur.Degats;
import fr.heraliz.wonder.serveur.Perf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Locale;

/**
 * Aspiration du Jet Gun, côté serveur (seul juge). Chaque « tir » TaCZ (10 par seconde tant que la détente est tenue et
 * que l'arme n'a pas calé) entretient l'aspiration du tireur ; elle s'arrête 3 ticks après la dernière impulsion.
 * <p>À chaque tick, pour chaque tireur actif (16 au plus, tableaux fixes) :
 * <ul>
 *   <li>toutes les 3 ticks, une recherche d'entités bornée par la boîte du cône, un test de cône par candidate, un lancer de
 *   rayon par créature retenue (vue directe depuis les yeux), les plus proches d'abord (au plus {@code cibles}) ;</li>
 *   <li>chaque créature en cache est tirée vers l'axe et vers la bouche (plus fort près de la bouche, un peu de tourbillon,
 *   un peu de portance pour qu'elle décolle et vole vers l'entrée d'air) ; un boss résiste ;</li>
 *   <li>celles qui atteignent la bouche sont déchiquetées : en partie de zombies, mort (les points reviennent au tireur
 *   comme pour toute Wonder Weapon) ; un boss prend des dégâts toutes les 5 ticks ; hors partie, dégâts aux monstres
 *   toutes les 4 ticks. Au plus {@code broyagesParTick} morts par tick et par tireur : le coût d'une horde est étalé.</li>
 * </ul>
 * <b>Jamais de bloc cassé, jamais un joueur touché</b> ({@link Regles#estCible}). Un paquet {@link Broyage} par tick où des
 * créatures sont déchiquetées.
 */
public final class Aspiration {
    private Aspiration() {
    }

    public static final ResourceKey<DamageType> ASPIRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(HeralizWonder.MODID, "aspiration"));
    /** Étiquette posée par les hordes du banc d'essai (figées sans IA : rendues à la physique quand elles sont aspirées). */
    public static final String TAG_BANC = "heraliz_wonder:banc";

    private static final int MAX_T = 16, MAX_C = 64, MAX_TRI = 256, MAX_VUE = 48;
    private static final LivingEntity[] tTireur = new LivingEntity[MAX_T];
    private static final ServerLevel[] tNiveau = new ServerLevel[MAX_T];
    private static final long[] tDernier = new long[MAX_T], tRecherche = new long[MAX_T];
    private static final int[] tPap = new int[MAX_T], tCle = new int[MAX_T], tNb = new int[MAX_T];
    private static final boolean[] tZombies = new boolean[MAX_T];
    private static final double[][] tAxe = new double[MAX_T][6];
    private static LivingEntity[][] tCible = new LivingEntity[MAX_T][MAX_C];
    private static long[][] tCoup = new long[MAX_T][MAX_C];
    private static int nT;
    private static long horloge;
    // tampons réutilisés (fil serveur unique)
    private static final LivingEntity[] TRI_E = new LivingEntity[MAX_TRI];
    private static final double[] TRI_S = new double[MAX_TRI];
    private static final LivingEntity[] NOUV_E = new LivingEntity[MAX_C];
    private static final long[] NOUV_C = new long[MAX_C];
    private static final int[] B_ID = new int[Broyage.MAX];
    private static final float[] B_X = new float[Broyage.MAX], B_Y = new float[Broyage.MAX], B_Z = new float[Broyage.MAX];
    private static int nB;
    // mesures
    private static long nsTotal, nsMax, nsRecherche;
    private static int nImpulsions, nRecherches, nVues, nTraites, nBroyes, picTireurs, picCibles, nTicksActifs;

    // ================================================================== entrées
    /** Un « tir » du Jet Gun (balle TaCZ annulée à son arrivée) : l'aspiration du tireur continue. */
    public static void impulsion(ServerLevel niveau, LivingEntity tireur, int pap) {
        nImpulsions++;
        int i = trouver(tireur, -1);
        if (i < 0) {
            if (nT >= MAX_T) return;
            i = nT++;
            tTireur[i] = tireur;
            tCle[i] = -1;
            tNb[i] = 0;
            tRecherche[i] = -100;
        }
        tNiveau[i] = niveau;
        tDernier[i] = horloge;
        tPap[i] = pap;
        tZombies[i] = Regles.enZombies(tireur);
        if (nT > picTireurs) picTireurs = nT;
    }

    /** Banc ({@code /jetgun stress}) : un tireur virtuel (origine et direction données), au nom du joueur. */
    public static void impulsionVirtuelle(ServerLevel niveau, LivingEntity joueur, int cle, double ox, double oy, double oz, double dx, double dy, double dz,
                                          int pap) {
        nImpulsions++;
        int i = trouver(joueur, cle);
        if (i < 0) {
            if (nT >= MAX_T) return;
            i = nT++;
            tTireur[i] = joueur;
            tCle[i] = cle;
            tNb[i] = 0;
            tRecherche[i] = -100;
        }
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6) return;
        double[] a = tAxe[i];
        a[0] = ox;
        a[1] = oy;
        a[2] = oz;
        a[3] = dx / l;
        a[4] = dy / l;
        a[5] = dz / l;
        tNiveau[i] = niveau;
        tDernier[i] = horloge;
        tPap[i] = pap;
        tZombies[i] = Regles.enZombies(joueur);
        if (nT > picTireurs) picTireurs = nT;
    }

    private static int trouver(LivingEntity t, int cle) {
        for (int i = 0; i < nT; i++) if (tTireur[i] == t && tCle[i] == cle) return i;
        return -1;
    }

    /** Le tireur aspire-t-il en ce moment (lu par les commandes de banc) ? */
    public static boolean actif(LivingEntity t) {
        return trouver(t, -1) >= 0;
    }

    // ================================================================== tick
    public static void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        horloge++;
        if (nT == 0) return;
        long t0 = System.nanoTime();
        int w = 0;
        for (int i = 0; i < nT; i++) {
            LivingEntity t = tTireur[i];
            boolean garde = t != null && t.isAlive() && !t.isRemoved() && horloge - tDernier[i] <= 3 && tNiveau[i] == t.level();
            if (!garde) {
                vider(i);
                continue;
            }
            if (i != w) deplacer(i, w);
            try {
                traiter(w);
            } catch (RuntimeException ex) {
                HeralizWonder.LOG.error("[wonder] aspiration du Jet Gun", ex);
            }
            w++;
        }
        for (int i = w; i < nT; i++) {
            tTireur[i] = null;
            tNiveau[i] = null;
        }
        nT = w;
        long ns = System.nanoTime() - t0;
        nsTotal += ns;
        nTicksActifs++;
        if (ns > nsMax) nsMax = ns;
    }

    private static void vider(int i) {
        LivingEntity[] c = tCible[i];
        for (int k = 0; k < tNb[i]; k++) c[k] = null;
        tNb[i] = 0;
        tTireur[i] = null;
        tNiveau[i] = null;
    }

    private static void deplacer(int i, int w) {
        tTireur[w] = tTireur[i];
        tNiveau[w] = tNiveau[i];
        tDernier[w] = tDernier[i];
        tRecherche[w] = tRecherche[i];
        tPap[w] = tPap[i];
        tCle[w] = tCle[i];
        tNb[w] = tNb[i];
        tZombies[w] = tZombies[i];
        double[] a = tAxe[w];
        tAxe[w] = tAxe[i];
        tAxe[i] = a;
        LivingEntity[] c = tCible[w];
        tCible[w] = tCible[i];
        tCible[i] = c;
        long[] k = tCoup[w];
        tCoup[w] = tCoup[i];
        tCoup[i] = k;
        tTireur[i] = null;
        tNiveau[i] = null;
        tNb[i] = 0;
    }

    private static void traiter(int i) {
        ServerLevel lv = tNiveau[i];
        LivingEntity t = tTireur[i];
        double ox, oy, oz, dx, dy, dz;
        if (tCle[i] >= 0) {
            double[] a = tAxe[i];
            ox = a[0];
            oy = a[1];
            oz = a[2];
            dx = a[3];
            dy = a[4];
            dz = a[5];
        } else {
            double yr = Math.toRadians(t.getYRot()), xr = Math.toRadians(t.getXRot());
            dx = -Math.sin(yr) * Math.cos(xr);
            dy = -Math.sin(xr);
            dz = Math.cos(yr) * Math.cos(xr);
            // bouche de l'arme : un peu devant et sous les yeux (l'arme est tenue à la hanche)
            ox = t.getX() + dx * 0.9;
            oy = t.getEyeY() - 0.3 + dy * 0.9;
            oz = t.getZ() + dz * 0.9;
        }
        double portee = JetConfig.portee(), angle = JetConfig.demiAngle(), r0 = JetConfig.rayonBouche();
        if (horloge - tRecherche[i] >= 3) rechercher(i, lv, t, ox, oy, oz, dx, dy, dz, portee, angle, r0);
        double force = JetConfig.force(), dBroy = JetConfig.distanceBroyage(), seuil = JetConfig.pvMax();
        float colosseDegats = (float) JetConfig.degatsColosse(), hors = (float) JetConfig.degatsHorsZombies();
        int maxBroy = JetConfig.broyagesParTick(), broyes = 0;
        boolean zombies = tZombies[i];
        nB = 0;
        DamageSource src = null;
        LivingEntity[] c = tCible[i];
        long[] coup = tCoup[i];
        int n = tNb[i];
        for (int k = 0; k < n; k++) {
            LivingEntity e = c[k];
            if (e == null) continue;
            if (!e.isAlive() || e.isRemoved() || e.level() != lv) {
                c[k] = null;
                continue;
            }
            double cx = e.getX(), cy = e.getY() + e.getBbHeight() * 0.5, cz = e.getZ();
            double rx = cx - ox, ry = cy - oy, rz = cz - oz;
            double s = rx * dx + ry * dy + rz * dz;
            double px = rx - dx * s, py = ry - dy * s, pz = rz - dz * s;
            double perp = Math.sqrt(px * px + py * py + pz * pz);
            double marge = e.getBbWidth() * 0.5 + 0.4;
            if (s < -1.0 || s > portee + 2 || perp > JetCalculs.rayon(s, r0, angle) * 1.3 + marge + 0.5) {
                c[k] = null;
                continue;
            }
            nTraites++;
            boolean colosse = zombies && e.getMaxHealth() > seuil;
            if (JetCalculs.dansBouche(s, perp, dBroy, r0)) {
                boolean repete = colosse || !zombies;
                int periode = colosse ? 5 : 4;
                if ((!repete || horloge - coup[k] >= periode) && broyes < maxBroy) {
                    if (src == null) src = Degats.source(lv, ASPIRATION, t);
                    float dmg = JetCalculs.degats(zombies, e.getMaxHealth(), e.getHealth(), seuil, hors, colosseDegats, tPap[i]);
                    coup[k] = horloge;
                    reveiller(e);
                    Degats.blesser(e, src, dmg, false);
                    broyes++;
                    if (!e.isAlive()) {
                        noter(e.getId(), cx, cy, cz);
                        c[k] = null;
                        nBroyes++;
                        // déchiqueté : le corps disparaît tout de suite (ni animation de mort ni nuage de Minecraft, les
                        // lambeaux du client le remplacent) ; la mort elle-même (points, butin, mode zombies) a déjà eu lieu
                        e.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                        continue;
                    }
                }
            }
            // aspiration vers l'axe et vers la bouche
            double resist = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null ? e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
            if (colosse) resist = Math.max(resist, 0.9);
            double v = JetCalculs.vitesse(s, portee, force, resist);
            double sv = Math.max(0.2, s * 0.35);
            double tx = ox + dx * sv - cx, ty = oy + dy * sv - cy, tz = oz + dz * sv - cz;
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-4) continue;
            tx /= tl;
            ty /= tl;
            tz /= tl;
            double sx = dy * pz - dz * py, sy = dz * px - dx * pz, sz = dx * py - dy * px;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            double tour = sl > 1e-4 ? v * 0.22 * Math.min(1, perp / 1.5) / sl : 0;
            double vx = tx * v + sx * tour, vy = ty * v + sy * tour + (colosse ? 0 : 0.07), vz = tz * v + sz * tour;
            Vec3 cur = e.getDeltaMovement();
            double a = colosse ? 0.25 : 0.5;
            reveiller(e);
            e.setDeltaMovement(cur.x + (vx - cur.x) * a, cur.y + (vy - cur.y) * a, cur.z + (vz - cur.z) * a);
            e.hasImpulse = true;
            e.fallDistance = 0;
        }
        // cache compacté (créatures mortes ou sorties du cône retirées)
        int w = 0;
        for (int k = 0; k < n; k++) {
            if (c[k] == null) continue;
            if (k != w) {
                c[w] = c[k];
                coup[w] = coup[k];
                c[k] = null;
            }
            w++;
        }
        tNb[i] = w;
        if (nB > 0) {
            int[] ids = new int[nB];
            float[] x = new float[nB], y = new float[nB], z = new float[nB];
            System.arraycopy(B_ID, 0, ids, 0, nB);
            System.arraycopy(B_X, 0, x, 0, nB);
            System.arraycopy(B_Y, 0, y, 0, nB);
            System.arraycopy(B_Z, 0, z, 0, nB);
            JetReseau.autour(lv, ox, oy, oz, new Broyage(t.getId(), nB, ids, x, y, z));
        }
    }

    private static void noter(int id, double x, double y, double z) {
        if (nB >= Broyage.MAX) return;
        B_ID[nB] = id;
        B_X[nB] = (float) x;
        B_Y[nB] = (float) y;
        B_Z[nB] = (float) z;
        nB++;
    }

    /** Une créature du banc, figée sans IA, est rendue à la physique quand elle est aspirée (sinon elle ne bouge pas). */
    private static void reveiller(LivingEntity e) {
        if (e instanceof Mob m && m.isNoAi() && e.getPersistentData().getBoolean(TAG_BANC)) m.setNoAi(false);
    }

    /** Recherche bornée : boîte du cône, test de cône, tri par distance, vue directe (au plus {@link #MAX_VUE} lancers). */
    private static void rechercher(int i, ServerLevel lv, LivingEntity t, double ox, double oy, double oz, double dx, double dy, double dz, double portee,
                                   double angle, double r0) {
        long t0 = System.nanoTime();
        tRecherche[i] = horloge;
        nRecherches++;
        boolean zombies = tZombies[i];
        double rmax = JetCalculs.rayon(portee, r0, angle) + 1.0;
        AABB boite = new AABB(ox, oy, oz, ox + dx * portee, oy + dy * portee, oz + dz * portee).inflate(rmax);
        List<LivingEntity> l = lv.getEntitiesOfClass(LivingEntity.class, boite, e -> Regles.estCible(e, t, zombies) && !e.noPhysics);
        int n = 0;
        for (int j = 0; j < l.size() && n < MAX_TRI; j++) {
            LivingEntity e = l.get(j);
            double cy = e.getY() + e.getBbHeight() * 0.5;
            double marge = e.getBbWidth() * 0.5 + Math.min(e.getBbHeight() * 0.25, 0.6);
            double s = JetCalculs.dansCone(e.getX() - ox, cy - oy, e.getZ() - oz, dx, dy, dz, portee, angle, r0, marge);
            if (s < 0) continue;
            int p = n++;
            while (p > 0 && TRI_S[p - 1] > s) {
                TRI_S[p] = TRI_S[p - 1];
                TRI_E[p] = TRI_E[p - 1];
                p--;
            }
            TRI_S[p] = s;
            TRI_E[p] = e;
        }
        int max = Math.min(JetConfig.cibles(), MAX_C), m = 0, vues = 0;
        LivingEntity[] ancien = tCible[i];
        long[] ancienCoup = tCoup[i];
        int nAncien = tNb[i];
        Vec3 oeil = t.getEyePosition();
        for (int j = 0; j < n && m < max; j++) {
            LivingEntity e = TRI_E[j];
            TRI_E[j] = null;
            // déjà en cache (vue vérifiée il y a peu) : gardée sans nouveau lancer de rayon si elle est près de l'axe
            long dernierCoup = -100;
            boolean connue = false;
            for (int q = 0; q < nAncien; q++) {
                if (ancien[q] == e) {
                    dernierCoup = ancienCoup[q];
                    connue = true;
                    break;
                }
            }
            if (!connue) {
                if (vues >= MAX_VUE) continue;
                vues++;
                if (!voit(lv, oeil, e, t)) continue;
            }
            NOUV_E[m] = e;
            NOUV_C[m] = dernierCoup;
            m++;
        }
        for (int j = 0; j < n; j++) TRI_E[j] = null;
        for (int q = 0; q < nAncien; q++) ancien[q] = null;
        for (int q = 0; q < m; q++) {
            ancien[q] = NOUV_E[q];
            ancienCoup[q] = NOUV_C[q];
            NOUV_E[q] = null;
        }
        tNb[i] = m;
        nVues += vues;
        if (m > picCibles) picCibles = m;
        nsRecherche += System.nanoTime() - t0;
    }

    private static boolean voit(ServerLevel lv, Vec3 oeil, LivingEntity e, LivingEntity t) {
        Vec3 c = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ());
        if (lv.clip(new ClipContext(oeil, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t)).getType() == HitResult.Type.MISS) return true;
        Vec3 h = new Vec3(e.getX(), e.getEyeY(), e.getZ());
        return lv.clip(new ClipContext(oeil, h, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t)).getType() == HitResult.Type.MISS;
    }

    public static void dechargement(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) return;
        for (int i = 0; i < nT; i++) if (tNiveau[i] == e.getLevel()) tDernier[i] = -1000;
    }

    // ================================================================== mesures
    /** Resume depuis le dernier releve : message traduit du staff ; {@link fr.heraliz.wonder.serveur.Perf#journal} pour le journal. */
    public static MutableComponent resume(boolean raz) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        double mspt = s == null ? 0 : s.getAverageTickTime();
        MutableComponent r = Component.translatableWithFallback("heraliz_wonder.perf.jetgun",
                "jet gun : %s ticks actifs, %s ms en tout (%s ms par tick actif, pire %s ms), dont recherches %s ms (%s, %s lancers de rayon) ; "
                        + "%s impulsions, %s créatures aspirées (tick-créature), %s déchiquetées ; pics : %s tireurs, %s cibles ; tick serveur moyen %s ms",
                Perf.n(nTicksActifs), Perf.ms(nsTotal / 1e6), String.format(Locale.ROOT, "%.4f", nTicksActifs == 0 ? 0 : nsTotal / 1e6 / nTicksActifs),
                Perf.ms(nsMax / 1e6), Perf.ms(nsRecherche / 1e6), Perf.n(nRecherches), Perf.n(nVues), Perf.n(nImpulsions), Perf.n(nTraites),
                Perf.n(nBroyes), Perf.n(picTireurs), Perf.n(picCibles), String.format(Locale.ROOT, "%.2f", mspt));
        if (raz) {
            nsTotal = nsMax = nsRecherche = 0;
            nImpulsions = nRecherches = nVues = nTraites = nBroyes = picTireurs = picCibles = nTicksActifs = 0;
        }
        return r;
    }
}
