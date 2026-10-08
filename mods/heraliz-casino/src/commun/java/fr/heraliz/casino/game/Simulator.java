package fr.heraliz.casino.game;

import fr.heraliz.casino.game.cards.BlackjackRules;
import fr.heraliz.casino.game.cards.BlackjackTable;
import fr.heraliz.casino.game.cards.Cards;
import fr.heraliz.casino.game.crash.CrashRules;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.mines.MinesRules;
import fr.heraliz.casino.game.plinko.PlinkoRules;
import fr.heraliz.casino.game.race.RaceGame;
import fr.heraliz.casino.game.race.RaceRules;
import fr.heraliz.casino.game.roulette.RouletteRules;
import fr.heraliz.casino.game.slot.Loup;
import fr.heraliz.casino.game.slot.LoupRules;
import fr.heraliz.casino.game.slot.Olympe;
import fr.heraliz.casino.game.slot.OlympeRules;
import fr.heraliz.casino.game.slot.Rotten;
import fr.heraliz.casino.game.slot.RottenRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Simulation d'une machine avec des reglages donnes (commande /casino roll, page "Probabilites" de la Tablette du
 * Directeur) : redistribution, frequence de chaque bonus ("1 partie sur N"), gains moyens, gros gains. Logique pure :
 * le rapport est une liste de lignes (cle de traduction + valeurs deja mises en forme).
 */
public final class Simulator {
    private Simulator() {
    }

    /**
     * Une ligne du rapport. key : suffixe de "roll.heralizcasino." ; args : valeurs deja mises en forme, ou "@nom"
     * pour un nom a traduire ("roll.heralizcasino.name.nom").
     */
    public record Line(String key, String... args) {
    }

    public static final class Report {
        public final List<Line> lines = new ArrayList<>();
        /** Parties simulees (0 : calcul exact, sans tirage). */
        public long rounds;
        /** Redistribution mesuree. */
        public double rtp;
        /** Marge d'erreur de la redistribution mesuree (intervalle a 95 %, 0.02 = plus ou moins 2 points) ; 0 : exacte. */
        public double margin;
        /** Redistribution du pari le plus genereux (jeu normal ou achat d'un bonus). */
        public double worst;

        Report add(String key, String... args) {
            lines.add(new Line(key, args));
            return this;
        }
    }

    /**
     * Simulation qu'on avance par tranches (la tablette affiche un rapport qui s'affine, et l'abandonne des que les
     * reglages changent). Un seul fil la fait avancer ; les autres ne lisent que les rapports publies.
     */
    public abstract static class Session {
        private volatile long rounds;
        private final long goal;

        /** @param goal parties au bout desquelles la simulation est consideree comme assez precise */
        protected Session(long goal) {
            this.goal = goal;
        }

        public final long rounds() {
            return rounds;
        }

        public final boolean done() {
            return rounds >= goal;
        }

        /** Simule une tranche de plus (au plus n parties) et rend le rapport de tout ce qui a ete simule. */
        public final Report advance(long n) {
            long todo = Math.max(1, Math.min(n, goal - rounds));
            step(todo);
            rounds += todo;
            return report();
        }

        protected abstract void step(long n);

        protected abstract Report report();
    }

    /** Calcul fait en une fois (probabilites exactes, simulations courtes). */
    public static Session once(java.util.function.Supplier<Report> compute) {
        return new Session(1) {
            private Report rep;

            @Override
            protected void step(long n) {
                rep = compute.get();
            }

            @Override
            protected Report report() {
                return rep;
            }
        };
    }

    // =================================================================== mise en forme
    /** Entier avec des espaces entre les milliers. */
    public static String num(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    /** Nombre a virgule (au plus "digits" decimales, sans zeros inutiles). */
    public static String dec(double v, int digits) {
        String s = String.format(Locale.ROOT, "%." + digits + "f", v);
        if (s.indexOf('.') >= 0) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.replace('.', ',');
    }

    /** Pourcentage d'une probabilite (0..1), plus precis quand elle est petite. */
    public static String pct(double p) {
        double v = p * 100;
        if (v <= 0) return "0";
        return v >= 10 ? dec(v, 1) : v >= 0.1 ? dec(v, 2) : v >= 0.001 ? dec(v, 3) : dec(v, 5);
    }

    /** Marge d'erreur a 95 % de la moyenne de n tirages (somme "sum", somme des carres "sq"). */
    private static double margin(double sum, double sq, long n) {
        if (n < 2) return 0;
        double mean = sum / n;
        return 1.96 * Math.sqrt(Math.max(0, sq / n - mean * mean) / n);
    }

    /** "N" de "1 sur N" pour une probabilite. */
    public static String oneIn(double p) {
        if (p <= 0) return "∞";
        double n = 1 / p;
        return n < 10 ? dec(n, 1) : num(Math.round(n));
    }

    /** "N" de "1 sur N" pour "count" occurrences sur n essais ; jamais vu : "plus de n". */
    private static String oneIn(long count, long n) {
        return count <= 0 ? "> " + num(n) : oneIn((double) count / n);
    }

    public static String mult(double x) {
        return x >= 100 ? num(Math.round(x)) : x >= 10 ? dec(x, 1) : dec(x, 2);
    }

    /** Pourcentage negatif possible (avantage du casino quand la machine perd de l'argent). */
    private static String edge(double rtp) {
        return rtp > 1 ? "-" + pct(rtp - 1) : pct(1 - rtp);
    }

    /** Nombre de parties a jouer pour avoir vu l'evenement avec la probabilite q. */
    public static long before(double p, double q) {
        if (p >= 1) return 1;
        if (p <= 0) return Long.MAX_VALUE;
        return Math.max(1, (long) Math.ceil(Math.log(1 - q) / Math.log(1 - p)));
    }

    // =================================================================== machines a sous : compteurs communs
    private static final class Feat {
        long count;
        double sum;

        void hit(double gain) {
            count++;
            sum += gain;
        }
    }

    private static final class Base {
        long n, wins, even, big20, big50, big150, capped;
        double total, sq, max;

        void add(double gain, boolean cap) {
            n++;
            total += gain;
            sq += gain * gain;
            if (gain > 0) wins++;
            if (gain >= 1) even++;
            if (gain >= 20) big20++;
            if (gain >= 50) big50++;
            if (gain >= 150) big150++;
            if (cap) capped++;
            if (gain > max) max = gain;
        }

        void head(Report rep) {
            rep.rounds = n;
            rep.rtp = total / n;
            rep.worst = rep.rtp;
            rep.margin = margin(total, sq, n);
            rep.add("rtp_sim", pct(rep.rtp), dec(rep.margin * 100, 1), edge(rep.rtp));
            rep.add("hit", pct((double) wins / n), oneIn(wins, n), pct((double) even / n));
        }

        void tail(Report rep, double maxWin) {
            rep.add("big", oneIn(big20, n), oneIn(big50, n), oneIn(big150, n));
            rep.add("max", mult(max));
            if (capped > 0) rep.add("capped", mult(maxWin), num(capped), oneIn(capped, n));
        }
    }

    /** Frequence d'un bonus : "1 partie sur N", delais, gain moyen de la partie. */
    private static void feature(Report rep, String name, Feat f, long n) {
        if (f.count == 0) {
            rep.add("never", "@" + name, num(n));
            return;
        }
        double p = (double) f.count / n;
        rep.add("feature", "@" + name, oneIn(p), num(before(p, 0.5)), num(before(p, 0.9)), mult(f.sum / f.count));
    }

    /** Achat d'un bonus : parties achetees, gain total, achats rentables. */
    private static final class Buy {
        final String name;
        final double price;
        long m, good;
        double sum;

        Buy(String name, double price) {
            this.name = name;
            this.price = price;
        }

        void run(long count, DoubleSupplier play) {
            if (price <= 0) return;
            for (long i = 0; i < count; i++) {
                double g = play.getAsDouble();
                sum += g;
                if (g >= price) good++;
            }
            m += count;
        }

        void line(Report rep) {
            if (price <= 0 || m <= 0) return;
            double rtp = sum / m / price;
            rep.worst = Math.max(rep.worst, rtp);
            rep.add("buy", "@" + name, mult(price), pct(rtp), mult(sum / m), pct((double) good / m));
        }
    }

    private static double[] shares(int[] weights) {
        double total = 0;
        for (int w : weights) total += Math.max(0, w);
        double[] out = new double[weights.length];
        for (int i = 0; i < out.length; i++) out[i] = total > 0 ? Math.max(0, weights[i]) / total : (i == 0 ? 1 : 0);
        return out;
    }

    // =================================================================== Souffle du Loup
    /** Chance de k scies a l'ecran (une au plus par rouleau). */
    private static double loupSaws(LoupRules r, int k) {
        double c = 1;
        for (int i = 0; i < k; i++) c = c * (Loup.REELS - i) / (i + 1);
        return c * Math.pow(r.saw, k) * Math.pow(1 - r.saw, Loup.REELS - k);
    }

    /** Chance qu'une partie tourne une roue (3 scies ou plus, la scie d'or comprise). */
    public static double loupWheel(LoupRules r) {
        double p = 0;
        for (int k = 3; k <= Loup.REELS; k++) p += loupSaws(r, k);
        return p;
    }

    /** Chance qu'une partie tourne la roue normale (3 scies, aucune d'or). */
    public static double loupClassic(LoupRules r) {
        double p = 0;
        for (int k = 3; k <= Loup.REELS; k++) p += loupSaws(r, k) * Math.pow(1 - r.gold, k);
        return p;
    }

    /** Chance qu'une partie tourne la super roue (scie d'or avec 2 bleues, ou case SUPER ROUE de la roue). */
    public static double loupSuper(LoupRules r) {
        return loupWheel(r) - loupClassic(r) + loupClassic(r) * loupWheelShares(r)[7];
    }

    /** Part de chaque issue de la roue (MINI, MINOR, MAJOR, GRAND, SCIES, MEGA CHAPEAU, MANOIR, SUPER ROUE). */
    public static double[] loupWheelShares(LoupRules r) {
        return shares(r.wheel);
    }

    /** Part de chaque issue de la super roue (MAJOR, GRAND, SUPER, SUPER SCIES, SUPER MEGA CHAPEAU, SUPER MANOIR). */
    public static double[] loupSuperShares(LoupRules r) {
        return shares(r.superWheel);
    }

    /** Chance par partie de gagner chaque jackpot (MINI, MINOR, MAJOR, GRAND, SUPER) a la roue ou a la super roue. */
    public static double[] loupJackpots(LoupRules r) {
        double pc = loupClassic(r), ps = loupSuper(r);
        double[] w = loupWheelShares(r), s = loupSuperShares(r);
        return new double[]{pc * w[0], pc * w[1], pc * w[2] + ps * s[0], pc * w[3] + ps * s[1], ps * s[2]};
    }

    /** Simulation progressive du Souffle du Loup (r ne doit plus etre modifie : en passer une copie). */
    public static Session loupSession(LoupRules r, long goal, long seed) {
        return new Session(goal) {
            private final SplittableRandom rng = new SplittableRandom(seed);
            private final Base b = new Base();
            private final Feat wheel = new Feat(), sup = new Feat(), hats = new Feat(), bonus = new Feat();
            private final Buy buy = new Buy("loup.wheel", r.buyWheel);
            private final long[] houseJack = new long[6];

            @Override
            protected void step(long n) {
                for (long i = 0; i < n; i++) {
                    Loup.Sequence s = Loup.play(r, rng, 0);
                    double g = s.totalMilli / 1000.0;
                    Loup.Spin f = s.spins.get(0);
                    boolean maxed = false;
                    for (Loup.Spin sp : s.spins) {
                        maxed |= (sp.events & Loup.EV_MAXWIN) != 0;
                        if ((sp.events & Loup.EV_BONUS_END) == 0) continue;
                        for (int c = 0; c < Loup.BCELLS; c++) if (sp.tier[c] > 0 && sp.reward[c] >= Loup.R_MINI) houseJack[sp.reward[c] - Loup.R_MINI]++;
                    }
                    b.add(g, maxed);
                    if ((f.events & (Loup.EV_WHEEL | Loup.EV_SUPER)) != 0) wheel.hit(g);
                    if ((f.events & Loup.EV_SUPER) != 0) sup.hit(g);
                    if ((f.events & Loup.EV_HATS) != 0) hats.hit(g);
                    if ((f.events & Loup.EV_BONUS) != 0) bonus.hit(g);
                }
                buy.run(Math.max(500, n / 40), () -> Loup.play(r, rng, Loup.BUY_WHEEL).totalMilli / 1000.0);
            }

            @Override
            protected Report report() {
                Report rep = new Report();
                b.head(rep);
                feature(rep, "loup.wheel", wheel, b.n);
                feature(rep, "loup.super", sup, b.n);
                feature(rep, "loup.hats", hats, b.n);
                feature(rep, "loup.bonus", bonus, b.n);
                double[] w = loupWheelShares(r), s = loupSuperShares(r), j = loupJackpots(r);
                rep.add("loup.wheel_odds", pct(w[0]), pct(w[1]), pct(w[2]), pct(w[3]), pct(w[4]), pct(w[5]), pct(w[6]), pct(w[7]));
                rep.add("loup.super_odds", pct(s[0]), pct(s[1]), pct(s[2]), pct(s[3]), pct(s[4]), pct(s[5]));
                rep.add("loup.jackpots", oneIn(j[0]), oneIn(j[1]), oneIn(j[2]), oneIn(j[3]), oneIn(j[4]));
                if (b.n > 0) {
                    rep.add("loup.houses", oneIn(houseJack[0] + houseJack[1], b.n), oneIn(houseJack[2], b.n), oneIn(houseJack[3], b.n), oneIn(houseJack[4], b.n),
                            oneIn(houseJack[5], b.n));
                }
                b.tail(rep, r.maxWin);
                buy.line(rep);
                return rep;
            }
        };
    }

    public static Report loup(LoupRules r, long n, long seed) {
        return loupSession(r, n, seed).advance(n);
    }

    // =================================================================== La Niche
    /** Chance que les tours gratuits tombent (un BONUS sur chacun des rouleaux 1, 3 et 5). */
    public static double nicheFree(fr.heraliz.casino.game.slot.NicheRules r) {
        return r.bonus * r.bonus * r.bonus;
    }

    /** Simulation progressive de La Niche (r ne doit plus etre modifie : en passer une copie). */
    public static Session nicheSession(fr.heraliz.casino.game.slot.NicheRules r, long goal, long seed) {
        return new Session(goal) {
            private final SplittableRandom rng = new SplittableRandom(seed);
            private final Base b = new Base();
            private final Feat free = new Feat();
            private final Buy buy = new Buy("niche.free", r.buyFree);
            private final long[] grids = new long[5];
            private double stickies;

            @Override
            protected void step(long n) {
                for (long i = 0; i < n; i++) {
                    fr.heraliz.casino.game.slot.Niche.Sequence s = fr.heraliz.casino.game.slot.Niche.play(r, rng, 0);
                    double g = s.totalMilli / 1000.0;
                    fr.heraliz.casino.game.slot.Niche.Spin f = s.spins.get(0), l = s.spins.get(s.spins.size() - 1);
                    boolean maxed = false;
                    for (fr.heraliz.casino.game.slot.Niche.Spin sp : s.spins) maxed |= (sp.events & fr.heraliz.casino.game.slot.Niche.EV_MAXWIN) != 0;
                    b.add(g, maxed);
                    if ((f.events & fr.heraliz.casino.game.slot.Niche.EV_TRIGGER) != 0) {
                        free.hit(g);
                        grids[l.grids]++;
                        for (int k = 0; k < fr.heraliz.casino.game.slot.Niche.GRIDS; k++) stickies += Integer.bitCount(l.sticky[k]);
                    }
                }
                buy.run(Math.max(500, n / 40), () -> fr.heraliz.casino.game.slot.Niche.play(r, rng, fr.heraliz.casino.game.slot.Niche.BUY_FREE).totalMilli / 1000.0);
            }

            @Override
            protected Report report() {
                Report rep = new Report();
                b.head(rep);
                feature(rep, "niche.free", free, b.n);
                if (free.count > 0) {
                    double c = free.count;
                    rep.add("niche.grids", pct(grids[1] / c), pct(grids[2] / c), pct(grids[3] / c), pct(grids[4] / c), dec(stickies / c, 1));
                }
                b.tail(rep, r.maxWin);
                buy.line(rep);
                return rep;
            }
        };
    }

    public static Report niche(fr.heraliz.casino.game.slot.NicheRules r, long n, long seed) {
        return nicheSession(r, n, seed).advance(n);
    }

    // =================================================================== Rotten
    /**
     * Chance par partie de declencher {le Savant Fou, l'Invasion Totale} : chaque rouleau porte au plus un symbole de
     * bonus ; 3 INVASION ou plus l'emportent sur les SAVANT.
     */
    public static double[] rottenBonus(RottenRules r) {
        double ps = Math.max(0, r.scatterSavant), pi = Math.max(0, Math.min(1 - ps, r.scatterInvasion)), p0 = Math.max(0, 1 - ps - pi);
        double savant = 0, invasion = 0;
        int n = Rotten.REELS;
        for (int s = 0; s <= n; s++) {
            for (int i = 0; s + i <= n; i++) {
                double p = PlinkoRules.binom(n, s) * PlinkoRules.binom(n - s, i) * Math.pow(ps, s) * Math.pow(pi, i) * Math.pow(p0, n - s - i);
                if (i >= 3) invasion += p;
                else if (s >= 3) savant += p;
            }
        }
        return new double[]{savant, invasion};
    }

    /** Simulation progressive de Rotten (r ne doit plus etre modifie : en passer une copie). */
    public static Session rottenSession(RottenRules r, long goal, long seed) {
        return new Session(goal) {
            private final SplittableRandom rng = new SplittableRandom(seed);
            private final Base b = new Base();
            private final Feat mutation = new Feat(), savant = new Feat(), invasion = new Feat();
            private final Buy buySavant = new Buy("rotten.savant", r.buySavant), buyInvasion = new Buy("rotten.invasion", r.buyInvasion);
            private long upgraded;

            @Override
            protected void step(long n) {
                for (long i = 0; i < n; i++) {
                    Rotten.Sequence s = Rotten.play(r, rng, Rotten.BUY_NONE);
                    double g = s.totalMilli / 1000.0;
                    Rotten.Spin f = s.spins.get(0), l = s.spins.get(s.spins.size() - 1);
                    b.add(g, (l.events & Rotten.EV_MAXWIN) != 0);
                    if ((f.events & Rotten.EV_MUTATION) != 0) mutation.hit(g);
                    if ((f.events & Rotten.EV_INVASION) != 0) {
                        invasion.hit(g);
                    } else if ((f.events & Rotten.EV_SAVANT) != 0) {
                        savant.hit(g);
                        for (int k = 1; k < s.spins.size(); k++) {
                            if ((s.spins.get(k).events & Rotten.EV_INVASION) != 0) {
                                upgraded++;
                                break;
                            }
                        }
                    }
                }
                long m = Math.max(400, n / 60);
                buySavant.run(m, () -> Rotten.play(r, rng, Rotten.BUY_SAVANT).totalMilli / 1000.0);
                buyInvasion.run(m, () -> Rotten.play(r, rng, Rotten.BUY_INVASION).totalMilli / 1000.0);
            }

            @Override
            protected Report report() {
                Report rep = new Report();
                b.head(rep);
                feature(rep, "rotten.mutation", mutation, b.n);
                feature(rep, "rotten.savant", savant, b.n);
                feature(rep, "rotten.invasion", invasion, b.n);
                if (savant.count > 0) rep.add("rotten.upgrade", pct((double) upgraded / savant.count));
                b.tail(rep, r.maxWin);
                buySavant.line(rep);
                buyInvasion.line(rep);
                return rep;
            }
        };
    }

    public static Report rotten(RottenRules r, long n, long seed) {
        return rottenSession(r, n, seed).advance(n);
    }

    // =================================================================== Portes de l'Olympe
    /** Simulation progressive des Portes de l'Olympe (r ne doit plus etre modifie : en passer une copie). */
    public static Session olympeSession(OlympeRules r, long goal, long seed) {
        return new Session(goal) {
            private final SplittableRandom rng = new SplittableRandom(seed);
            private final Base b = new Base();
            private final Feat free = new Feat(), orb = new Feat();
            private final Buy buy = new Buy("olympe.free", r.buyFree);
            private long retrig;

            @Override
            protected void step(long n) {
                for (long i = 0; i < n; i++) {
                    Olympe.Sequence s = Olympe.play(r, rng, 0);
                    double g = s.totalMilli / 1000.0;
                    Olympe.Spin f = s.spins.get(0), l = s.spins.get(s.spins.size() - 1);
                    b.add(g, (l.events & Olympe.EV_MAXWIN) != 0);
                    if (f.orbSum > 0 && f.tumbleMilli() > 0) orb.hit(g);
                    if ((f.events & Olympe.EV_FREE) != 0) {
                        free.hit(g);
                        for (int k = 1; k < s.spins.size(); k++) {
                            if ((s.spins.get(k).events & Olympe.EV_RETRIGGER) != 0) {
                                retrig++;
                                break;
                            }
                        }
                    }
                }
                buy.run(Math.max(400, n / 60), () -> Olympe.play(r, rng, Olympe.BUY_FREE).totalMilli / 1000.0);
            }

            @Override
            protected Report report() {
                Report rep = new Report();
                b.head(rep);
                feature(rep, "olympe.free", free, b.n);
                if (free.count > 0) rep.add("olympe.retrigger", pct((double) retrig / free.count));
                feature(rep, "olympe.orb", orb, b.n);
                b.tail(rep, r.maxWin);
                buy.line(rep);
                return rep;
            }
        };
    }

    public static Report olympe(OlympeRules r, long n, long seed) {
        return olympeSession(r, n, seed).advance(n);
    }

    // =================================================================== Plinko (calcul exact : loi binomiale)
    public static Report plinko(PlinkoRules r) {
        Report rep = new Report();
        rep.rtp = r.rtp();
        rep.add("rtp", pct(rep.rtp), edge(rep.rtp));
        int rows = PlinkoRules.ROWS;
        double all = Math.pow(2, rows), win = 0, even = 0;
        // les paniers vont par deux (gauche / droite) : une ligne par multiplicateur, du bord vers le centre
        for (int k = 0; k <= rows / 2; k++) {
            double p = PlinkoRules.binom(rows, k) / all;
            boolean pair = k != rows - k && Math.abs(r.multipliers[k] - r.multipliers[rows - k]) < 1e-9;
            if (pair) {
                rep.add("plinko.bucket", mult(r.multipliers[k]), pct(2 * p), oneIn(2 * p));
            } else {
                rep.add("plinko.bucket", mult(r.multipliers[k]), pct(p), oneIn(p));
                if (k != rows - k) rep.add("plinko.bucket", mult(r.multipliers[rows - k]), pct(p), oneIn(p));
            }
        }
        for (int k = 0; k <= rows; k++) {
            double p = PlinkoRules.binom(rows, k) / all;
            if (r.multipliers[k] > 1) win += p;
            if (r.multipliers[k] >= 1) even += p;
        }
        rep.add("plinko.win", pct(win), pct(1 - even));
        return rep;
    }

    // =================================================================== Roulette (calcul exact)
    public static Report roulette(RouletteRules r) {
        Report rep = new Report();
        int pockets = r.zeros == 1 ? 37 : 38;
        rep.rtp = 36.0 / pockets;
        rep.add("rtp", pct(rep.rtp), edge(rep.rtp));
        rep.add("roulette.straight", num(pockets), pct(1.0 / pockets));
        rep.add("roulette.dozen", pct(12.0 / pockets), oneIn(12.0 / pockets));
        rep.add("roulette.zero", num(r.zeros), pct((double) r.zeros / pockets));
        return rep;
    }

    // =================================================================== La Fusee (calcul exact)
    public static Report crash(CrashRules r) {
        Report rep = new Report();
        rep.rtp = 1 - r.houseEdge;
        rep.add("rtp", pct(rep.rtp), pct(r.houseEdge));
        // explosion avant x1,01 : le joueur n'a le temps de rien encaisser
        rep.add("crash.instant", pct(1 - reach(r, 1.01)));
        rep.add("crash.reach", pct(reach(r, 1.5)), pct(reach(r, 2)), pct(reach(r, 3)), pct(reach(r, 5)), pct(reach(r, 10)));
        rep.add("crash.high", oneIn(reach(r, 20)), oneIn(reach(r, 50)), oneIn(reach(r, 100)), mult(r.maxMultiplier), oneIn(reach(r, r.maxMultiplier)));
        return rep;
    }

    /** Chance que la fusee atteigne le multiplicateur x. */
    private static double reach(CrashRules r, double x) {
        return x > r.maxMultiplier ? 0 : Math.min(1, (1 - r.houseEdge) / x);
    }

    // =================================================================== Le Demineur (calcul exact)
    public static Report mines(MinesRules r) {
        Report rep = new Report();
        rep.rtp = r.rtp;
        rep.add("rtp", pct(r.rtp), edge(r.rtp));
        for (int m : new int[]{1, 3, 5, 10, 20}) {
            String[] a = new String[7];
            a[0] = num(m);
            int[] picks = {1, 3, 5};
            for (int k = 0; k < 3; k++) {
                int q = Math.min(picks[k], MinesRules.TILES - m);
                double p = 1;
                for (int i = 0; i < q; i++) p *= (double) (MinesRules.TILES - m - i) / (MinesRules.TILES - i);
                a[1 + k * 2] = pct(p);
                a[2 + k * 2] = mult(r.multiplier(m, q));
            }
            rep.add("mines.row", a);
        }
        return rep;
    }

    // =================================================================== Course de chevaux
    public static Report race(RaceRules r, long n, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        Report rep = new Report();
        double back = 0, favWin = 0, favOdds = 0, outWin = 0, outOdds = 0, favBack = 0, outBack = 0;
        for (long i = 0; i < n; i++) {
            double[] c = RaceGame.randomChances(rng), o = RaceGame.odds(c, r.rtp);
            int fav = 0, out = 0;
            for (int h = 0; h < c.length; h++) {
                back += c[h] * o[h] / c.length;
                if (c[h] > c[fav]) fav = h;
                if (c[h] < c[out]) out = h;
            }
            favWin += c[fav];
            favOdds += o[fav];
            favBack += c[fav] * o[fav];
            outWin += c[out];
            outOdds += o[out];
            outBack += c[out] * o[out];
        }
        rep.rounds = n;
        rep.rtp = back / n;
        rep.add("rtp", pct(rep.rtp), edge(rep.rtp));
        rep.add("race.favorite", pct(favWin / n), mult(favOdds / n), pct(favBack / n));
        rep.add("race.outsider", pct(outWin / n), mult(outOdds / n), pct(outBack / n));
        return rep;
    }

    // =================================================================== Blackjack (strategie de base, sabot infini)
    public static Report blackjack(BlackjackRules r, long n, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        IntSupplier draw = () -> rng.nextInt(52);
        Report rep = new Report();
        BlackjackTable t = new BlackjackTable(r);
        long bet = 0, paid = 0, naturals = 0, won = 0, push = 0, lost = 0, bust = 0, hands = 0, doubles = 0, splits = 0;
        double sum = 0, sq = 0;
        for (long i = 0; i < n; i++) {
            long now = 0;
            t.start(100, now, draw);
            int guard = 0;
            while (t.phase == BlackjackTable.PLAYER && guard++ < 40) {
                now = Math.max(now, t.busyUntil);
                int[] hand = t.hands[t.active];
                int size = t.handSize[t.active];
                int total = Cards.total(hand, size), up = Cards.bjValue(t.dealer[0]);
                boolean soft = Cards.soft(hand, size);
                if (t.canSplit(now) && wantSplit(Cards.bjValue(hand[0]), up)) {
                    t.split(now, draw);
                    splits++;
                } else {
                    int act = decide(total, soft, up, t.canDouble(now));
                    if (act == 2) {
                        t.doubleDown(now, draw);
                        doubles++;
                    } else if (act == 1) {
                        t.hit(now, draw);
                    } else {
                        t.stand(now, draw);
                    }
                }
            }
            bet += t.totalBet();
            paid += t.totalPayout();
            // resultat net de la main, en mises de depart (pour la marge d'erreur)
            double net = (t.totalPayout() - t.totalBet()) / 100.0;
            sum += net;
            sq += net * net;
            for (int h = 0; h < t.handCount; h++) {
                hands++;
                switch (t.results[h]) {
                    case BlackjackTable.R_BLACKJACK -> {
                        naturals++;
                        won++;
                    }
                    case BlackjackTable.R_WIN -> won++;
                    case BlackjackTable.R_PUSH -> push++;
                    case BlackjackTable.R_BUST -> {
                        bust++;
                        lost++;
                    }
                    default -> lost++;
                }
            }
        }
        rep.rounds = n;
        rep.rtp = bet > 0 ? (double) paid / bet : 0;
        rep.margin = margin(sum, sq, n);
        rep.add("rtp_sim", pct(rep.rtp), dec(rep.margin * 100, 1), edge(rep.rtp));
        rep.add("bj.hands", pct((double) won / hands), pct((double) push / hands), pct((double) lost / hands), pct((double) bust / hands));
        rep.add("bj.natural", oneIn(naturals, n), pct((double) doubles / n), pct((double) splits / n));
        rep.add("bj.note");
        return rep;
    }

    /** Partage d'une paire (valeur v : 1 = As, 10 = buche) face a la carte du croupier. */
    private static boolean wantSplit(int v, int up) {
        return switch (v) {
            case 1, 8 -> true;
            case 2, 3, 7 -> up >= 2 && up <= 7;
            case 6 -> up >= 2 && up <= 6;
            case 9 -> up >= 2 && up <= 9 && up != 7;
            default -> false;
        };
    }

    /** Strategie de base : 0 rester, 1 tirer, 2 doubler. up : carte du croupier (1 = As). */
    private static int decide(int total, boolean soft, int up, boolean canDouble) {
        boolean weak = up >= 2 && up <= 6;
        if (soft) {
            if (total >= 19) return 0;
            if (total == 18) return up >= 3 && up <= 6 && canDouble ? 2 : up == 2 || up == 7 || up == 8 || weak ? 0 : 1;
            return (up == 5 || up == 6 || (up == 4 && total >= 15)) && canDouble ? 2 : 1;
        }
        if (total >= 17) return 0;
        if (total >= 13) return weak ? 0 : 1;
        if (total == 12) return up >= 4 && up <= 6 ? 0 : 1;
        if (total == 11) return canDouble && up != 1 ? 2 : 1;
        if (total == 10) return canDouble && up >= 2 && up <= 9 ? 2 : 1;
        if (total == 9) return canDouble && up >= 3 && up <= 6 ? 2 : 1;
        return 1;
    }

    // =================================================================== duels entre joueurs
    public static Report duel(DuelRules r) {
        Report rep = new Report();
        rep.rtp = 1 - r.commission;
        rep.add("duel", pct(r.commission), pct(rep.rtp));
        return rep;
    }
}
