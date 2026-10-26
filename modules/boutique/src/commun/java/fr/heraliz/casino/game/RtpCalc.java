package fr.heraliz.casino.game;

import fr.heraliz.casino.game.cards.BlackjackRules;
import fr.heraliz.casino.game.crash.CrashRules;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.mines.MinesRules;
import fr.heraliz.casino.game.plinko.PlinkoRules;
import fr.heraliz.casino.game.race.RaceRules;
import fr.heraliz.casino.game.roulette.RouletteRules;
import fr.heraliz.casino.game.slot.LoupRules;
import fr.heraliz.casino.game.slot.OlympeRules;
import fr.heraliz.casino.game.slot.RottenRules;
import fr.heraliz.casino.game.slot.SlotGame;
import fr.heraliz.casino.game.slot.SlotRules;

/**
 * Redistribution theorique (part des mises rendue aux joueurs sur la duree) de chaque jeu selon ses reglages.
 * Au-dessus de 100 %, le casino perd de l'argent. Resultats {minimum, maximum} selon le pari choisi ; pour les
 * machines a sous, mesurees par simulation, un troisieme nombre donne la marge d'erreur (0.01 = plus ou moins 1 point).
 */
public final class RtpCalc {
    private RtpCalc() {
    }

    public static double[] slot(SlotRules r) {
        double v = SlotGame.theoreticalRtp(r)[0] + r.jackpotContribution;
        return new double[]{v, v};
    }

    // ---- machines a sous "a sequence" : simulation en arriere-plan (SimCache), qui s'affine tranche apres tranche
    /**
     * Parties visees : la redistribution d'une machine a sous depend de gros gains tres rares, il en faut des millions
     * pour la connaitre a un point pres. Graine fixe : memes reglages, meme resultat.
     */
    private static final long GOAL_ROTTEN = 3_000_000, GOAL_OLYMPE = 3_000_000, GOAL_LOUP = 6_000_000, CHUNK = 100_000, SEED = 12345;
    /** Reference de Rotten : reglages d'usine simules sur 6 millions de parties (jeu normal). */
    private static final double ROTTEN_REF_RTP = 0.86, ROTTEN_REF_SCALE = 1.075;

    /** Simulation de la machine Rotten avec ces reglages (null tant que la premiere tranche n'est pas finie). */
    public static Simulator.Report report(RottenRules r) {
        return SimCache.get(fields(r), () -> Simulator.rottenSession(copyFields(r, new RottenRules()), GOAL_ROTTEN, SEED), CHUNK);
    }

    public static Simulator.Report report(OlympeRules r) {
        return SimCache.get(fields(r), () -> Simulator.olympeSession(copyFields(r, new OlympeRules()), GOAL_OLYMPE, SEED), CHUNK);
    }

    public static Simulator.Report report(LoupRules r) {
        return SimCache.get(fields(r), () -> Simulator.loupSession(copyFields(r, new LoupRules()), GOAL_LOUP, SEED), CHUNK);
    }

    public static Simulator.Report report(fr.heraliz.casino.game.slot.NicheRules r) {
        return SimCache.get(fields(r), () -> Simulator.nicheSession(copyFields(r, new fr.heraliz.casino.game.slot.NicheRules()), GOAL_LOUP, SEED), CHUNK);
    }

    /** La simulation de ces reglages est-elle terminee ? */
    public static boolean settled(Object rules) {
        return SimCache.done(fields(rules));
    }

    /** {jeu normal, pari le plus genereux (achats de bonus compris), marge d'erreur}. */
    private static double[] measured(Simulator.Report rep, double estimate) {
        if (rep == null || rep.rounds == 0) return new double[]{estimate, estimate, 0.05};
        return new double[]{rep.rtp, Math.max(rep.rtp, rep.worst), rep.margin};
    }

    /** Redistribution de Rotten ; tant que la simulation n'a rien donne, estimation a partir du niveau des gains. */
    public static double[] rotten(RottenRules r) {
        return measured(report(r), ROTTEN_REF_RTP * r.payScale / ROTTEN_REF_SCALE);
    }

    public static double[] olympe(OlympeRules r) {
        return measured(report(r), 0.865 * r.payScale);
    }

    public static double[] loup(LoupRules r) {
        return measured(report(r), 0.896 * r.payScale);
    }

    public static double[] niche(fr.heraliz.casino.game.slot.NicheRules r) {
        return measured(report(r), 0.894 * r.payScale);
    }

    /** Cle de cache : la classe et toutes les valeurs publiques des reglages. */
    private static String fields(Object o) {
        StringBuilder b = new StringBuilder(o.getClass().getSimpleName());
        for (java.lang.reflect.Field f : o.getClass().getFields()) {
            // les limites de mise ne changent rien aux probabilites
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().equals("minBet") || f.getName().equals("maxBet")) continue;
            try {
                Object v = f.get(o);
                b.append('|').append(v instanceof Object[] a ? java.util.Arrays.deepToString(a) : v instanceof int[] a ? java.util.Arrays.toString(a)
                        : v instanceof double[] a ? java.util.Arrays.toString(a) : String.valueOf(v));
            } catch (IllegalAccessException ignored) {
            }
        }
        return b.toString();
    }

    /** Copie independante des reglages (la simulation tourne pendant qu'on les modifie). */
    private static <T> T copyFields(T from, T to) {
        for (java.lang.reflect.Field f : from.getClass().getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                Object v = f.get(from);
                if (v instanceof int[][] a) {
                    int[][] cp = new int[a.length][];
                    for (int i = 0; i < a.length; i++) cp[i] = a[i].clone();
                    v = cp;
                } else if (v instanceof double[][] a) {
                    double[][] cp = new double[a.length][];
                    for (int i = 0; i < a.length; i++) cp[i] = a[i].clone();
                    v = cp;
                } else if (v instanceof int[] a) {
                    v = a.clone();
                } else if (v instanceof double[] a) {
                    v = a.clone();
                }
                f.set(to, v);
            } catch (IllegalAccessException ignored) {
            }
        }
        return to;
    }

    public static double[] plinko(PlinkoRules r) {
        double v = r.rtp();
        return new double[]{v, v};
    }

    /** Estimation (strategie de base, sabot infini) calee sur la simulation : 6:5 + tire sur 17 souple = 98,1 %. */
    public static double[] blackjack(BlackjackRules r) {
        double v = 0.9812 + (r.blackjackPays - 1.2) * (0.0139 / 0.3) + (r.dealerHitsSoft17 ? 0 : 0.002)
                - (r.allowDouble ? 0 : 0.016) - (r.allowSplit ? 0 : 0.004);
        return new double[]{v, v};
    }

    public static double[] roulette(RouletteRules r) {
        double v = r.zeros == 1 ? 36.0 / 37.0 : 36.0 / 38.0;
        return new double[]{v, v};
    }

    public static double[] crash(CrashRules r) {
        double v = 1 - r.houseEdge;
        return new double[]{v, v};
    }

    public static double[] mines(MinesRules r) {
        return new double[]{r.rtp, r.rtp};
    }

    public static double[] race(RaceRules r) {
        return new double[]{r.rtp, r.rtp};
    }

    /** Duel entre joueurs : les joueurs recuperent ensemble le pot moins la commission du casino. */
    public static double[] duel(DuelRules r) {
        return new double[]{1 - r.commission, 1 - r.commission};
    }
}
