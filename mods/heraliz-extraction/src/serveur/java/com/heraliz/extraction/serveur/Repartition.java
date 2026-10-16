package com.heraliz.extraction.serveur;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tirage des zones à ouvrir : au hasard, mais assez loin les unes des autres (distance minimale à plat entre deux zones
 * ouvertes en même temps, réglage rotation.minDistance) et réparties sur la carte (au plus une par quart, réglage
 * rotation.spread), en évitant les zones à ne pas reprendre (vague précédente). Si c'est impossible, les règles sont
 * relâchées dans cet ordre : les quarts, puis la distance (baissée peu à peu), et seulement à la fin les zones évitées.
 * Le résultat dit ce qui a été tenu, pour le journal.
 */
final class Repartition {
    /** Mélanges essayés à chaque niveau de règles (une quarantaine de zones : presque rien à calculer). */
    private static final int ESSAIS = 64;
    /** En dessous, la distance passe directement à 0. */
    private static final int DISTANCE_PLANCHER = 50;

    /**
     * @param zones           zones tirées (moins que demandé si les candidates manquent)
     * @param distance        distance minimale tenue (blocs)
     * @param quarts          répartition par quarts tenue
     * @param evitees         zones à éviter toutes évitées
     */
    record Choix(List<ExtractionZones.Zone> zones, int distance, boolean quarts, boolean evitees) {
    }

    private Repartition() {
    }

    /**
     * @param pool       toutes les zones de la rotation (pour le centre de la carte de chaque dimension)
     * @param ouvertes   zones déjà ouvertes (toutes, permanentes comprises) : les tirées doivent s'en tenir loin aussi
     * @param candidates zones qu'on peut ouvrir
     * @param eviter     noms des zones à ne pas reprendre si possible
     * @param n          nombre de zones voulues
     */
    static Choix choisir(List<ExtractionZones.Zone> pool, List<ExtractionZones.Zone> ouvertes,
                         List<ExtractionZones.Zone> candidates, Set<String> eviter, int n, int distanceMin,
                         boolean repartir, RandomSource random) {
        if (n <= 0 || candidates.isEmpty()) {
            return new Choix(List.of(), Math.max(0, distanceMin), repartir, true);
        }
        Map<ResourceLocation, double[]> centres = centres(pool);
        int total = ouvertes.size() + n;
        int parQuart = Math.max(1, (total + 3) / 4);

        List<ExtractionZones.Zone> sansEvitees = new ArrayList<>();
        for (ExtractionZones.Zone z : candidates) {
            if (!eviter.contains(z.name())) {
                sansEvitees.add(z);
            }
        }
        List<Integer> distances = new ArrayList<>();
        for (int d = Math.max(0, distanceMin); d > 0; d = d * 85 / 100) {
            if (d < DISTANCE_PLANCHER) {
                break;
            }
            distances.add(d);
        }
        distances.add(0);

        List<ExtractionZones.Zone> meilleur = List.of();
        Choix meilleurChoix = null;
        boolean[] avecEvitement = sansEvitees.size() < candidates.size() ? new boolean[]{true, false} : new boolean[]{false};
        for (boolean evite : avecEvitement) {
            List<ExtractionZones.Zone> liste = evite ? sansEvitees : candidates;
            for (int d : distances) {
                for (boolean q : repartir ? new boolean[]{true, false} : new boolean[]{false}) {
                    List<ExtractionZones.Zone> tirees = essayer(liste, ouvertes, n, d, q, parQuart, centres, random);
                    if (tirees.size() >= n) {
                        return new Choix(tirees, d, q || !repartir, evite || sansEvitees.size() == candidates.size());
                    }
                    if (tirees.size() > meilleur.size()) {
                        meilleur = tirees;
                        meilleurChoix = new Choix(tirees, d, q || !repartir, evite || sansEvitees.size() == candidates.size());
                    }
                }
            }
        }
        // pas assez de candidates, quelles que soient les règles : le plus grand tirage trouvé
        return meilleurChoix != null ? meilleurChoix : new Choix(List.of(), 0, false, false);
    }

    /** Plusieurs tirages gloutons sur des ordres mélangés : le premier complet, sinon le plus grand. */
    private static List<ExtractionZones.Zone> essayer(List<ExtractionZones.Zone> liste, List<ExtractionZones.Zone> ouvertes,
                                                      int n, int distance, boolean quarts, int parQuart,
                                                      Map<ResourceLocation, double[]> centres, RandomSource random) {
        List<ExtractionZones.Zone> meilleur = new ArrayList<>();
        List<ExtractionZones.Zone> ordre = new ArrayList<>(liste);
        for (int essai = 0; essai < ESSAIS; essai++) {
            melanger(ordre, random);
            List<ExtractionZones.Zone> tirees = new ArrayList<>();
            List<ExtractionZones.Zone> placees = new ArrayList<>(ouvertes);
            for (ExtractionZones.Zone z : ordre) {
                if (tirees.size() >= n) {
                    break;
                }
                if (compatible(z, placees, distance, quarts, parQuart, centres)) {
                    tirees.add(z);
                    placees.add(z);
                }
            }
            if (tirees.size() >= n) {
                return tirees;
            }
            if (tirees.size() > meilleur.size()) {
                meilleur = tirees;
            }
            if (distance == 0 && !quarts) {
                break; // sans règle, un seul ordre suffit
            }
        }
        return meilleur;
    }

    private static boolean compatible(ExtractionZones.Zone z, List<ExtractionZones.Zone> placees, int distance, boolean quarts,
                                      int parQuart, Map<ResourceLocation, double[]> centres) {
        double d2 = (double) distance * distance;
        int memeQuart = 0;
        int q = quart(z, centres);
        for (ExtractionZones.Zone o : placees) {
            if (!o.dimension().equals(z.dimension())) {
                continue; // une autre dimension est toujours « loin »
            }
            double dx = o.pos().x - z.pos().x;
            double dz = o.pos().z - z.pos().z;
            if (distance > 0 && dx * dx + dz * dz < d2) {
                return false;
            }
            if (quarts && quart(o, centres) == q) {
                memeQuart++;
            }
        }
        return !quarts || memeQuart < parQuart;
    }

    /** Quart de la carte (0 à 3) autour du centre des zones de la rotation de la même dimension. */
    private static int quart(ExtractionZones.Zone z, Map<ResourceLocation, double[]> centres) {
        double[] c = centres.get(z.dimension());
        if (c == null) {
            return 0;
        }
        return (z.pos().x >= c[0] ? 1 : 0) + (z.pos().z >= c[1] ? 2 : 0);
    }

    /** Centre de chaque dimension : médiane des x et des z de ses zones (autant de zones de chaque côté). */
    static Map<ResourceLocation, double[]> centres(Collection<ExtractionZones.Zone> pool) {
        Map<ResourceLocation, List<ExtractionZones.Zone>> parDim = new HashMap<>();
        for (ExtractionZones.Zone z : pool) {
            parDim.computeIfAbsent(z.dimension(), k -> new ArrayList<>()).add(z);
        }
        Map<ResourceLocation, double[]> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<ExtractionZones.Zone>> e : parDim.entrySet()) {
            List<ExtractionZones.Zone> l = e.getValue();
            double[] xs = new double[l.size()];
            double[] zs = new double[l.size()];
            for (int i = 0; i < l.size(); i++) {
                xs[i] = l.get(i).pos().x;
                zs[i] = l.get(i).pos().z;
            }
            out.put(e.getKey(), new double[]{mediane(xs), mediane(zs)});
        }
        return out;
    }

    private static double mediane(double[] v) {
        Arrays.sort(v);
        int m = v.length / 2;
        return v.length % 2 == 1 ? v[m] : (v[m - 1] + v[m]) / 2.0D;
    }

    private static <T> void melanger(List<T> l, RandomSource random) {
        for (int i = l.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T t = l.get(i);
            l.set(i, l.get(j));
            l.set(j, t);
        }
    }
}
