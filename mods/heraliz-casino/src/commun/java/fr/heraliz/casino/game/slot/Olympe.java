package fr.heraliz.casino.game.slot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * "Les Portes de l'Olympe" (logique pure) : grille 6 x 5 qui paie partout. 8 symboles identiques ou plus, n'importe
 * ou, gagnent ; ils explosent et les symboles du dessus tombent (cascade), de nouveaux arrivent par le haut, jusqu'a ce
 * qu'il n'y ait plus de gain. Les orbes de Zeus (x2 a x500) presents a la fin d'une cascade gagnante multiplient le gain.
 * 4 Zeus ou plus : 15 tours gratuits ou les orbes s'additionnent dans un multiplicateur total qui ne redescend pas.
 */
public final class Olympe {
    private Olympe() {
    }

    public static final int REELS = 6, ROWS = 5, CELLS = REELS * ROWS;
    /** Symboles : 0-4 gemmes (bleue, verte, jaune, violette, rouge), 5 calice, 6 anneau, 7 sablier, 8 couronne. */
    public static final int REGULAR = 9, ZEUS = 9, ORB = 10;
    public static final int BASE = 0, FREE = 2;
    public static final int EV_FREE = 1, EV_RETRIGGER = 4, EV_MAXWIN = 16, EV_BONUS_END = 32;
    public static final int BUY_FREE = 1;
    public static final int MAX_SPINS = 120, MAX_STEPS = 40;

    public static int cell(int reel, int row) {
        return reel * ROWS + row;
    }

    /** Une etape de cascade : la grille, ses orbes (indice de valeur + 1, 0 = aucun), les cases gagnantes, le gain. */
    public static final class Step {
        public final int[] grid = new int[CELLS];
        public final int[] orb = new int[CELLS];
        public int winMask, winMilli;
    }

    public static final class Spin {
        public int kind, events;
        public final List<Step> steps = new ArrayList<>();
        /** Somme des orbes a la fin, multiplicateur total (tours gratuits) apres ce tour. */
        public int orbSum, totalMult;
        /** Gain credite (cascades x multiplicateur + Zeus), gain des Zeus seuls. */
        public int winMilli, scatterMilli;
        public int freeLeft, freeTotal;

        public Step last() {
            return steps.get(steps.size() - 1);
        }

        /** Gain des cascades avant multiplicateur. */
        public int tumbleMilli() {
            int w = 0;
            for (Step s : steps) w += s.winMilli;
            return w;
        }

        /** Multiplicateur applique au gain des cascades (1 si aucun). */
        public int appliedMult() {
            if (orbSum <= 0 || tumbleMilli() <= 0) return 1;
            return kind == FREE ? Math.max(1, totalMult) : orbSum;
        }
    }

    public static final class Sequence {
        public final List<Spin> spins = new ArrayList<>();
        public long totalMilli;
        public int buy;
    }

    // =================================================================== tirage
    public static Sequence play(OlympeRules r, RandomGenerator rng, int buy) {
        Sequence seq = new Sequence();
        seq.buy = buy;
        double maxMilli = r.maxWin * 1000;
        Spin first = spin(r, rng, false, 0, buy == BUY_FREE ? 4 : 0);
        first.kind = BASE;
        seq.spins.add(first);
        seq.totalMilli += first.winMilli;
        int zeus = count(first.last().grid, ZEUS);
        if (zeus >= 4 && seq.totalMilli < maxMilli) {
            first.events |= EV_FREE;
            int left = r.freeSpins, total = left, mult = 0;
            first.freeLeft = left;
            first.freeTotal = total;
            Spin last = first;
            while (left > 0 && seq.spins.size() < MAX_SPINS && seq.totalMilli < maxMilli) {
                left--;
                Spin s = spin(r, rng, true, mult, 0);
                s.kind = FREE;
                mult = s.totalMult;
                if (count(s.last().grid, ZEUS) >= 3 && r.retrigger > 0) {
                    left += r.retrigger;
                    total += r.retrigger;
                    s.events |= EV_RETRIGGER;
                }
                s.freeLeft = left;
                s.freeTotal = total;
                seq.spins.add(s);
                seq.totalMilli += s.winMilli;
                last = s;
            }
            last.events |= EV_BONUS_END;
        }
        if (seq.totalMilli >= maxMilli) {
            seq.totalMilli = (long) maxMilli;
            seq.spins.get(seq.spins.size() - 1).events |= EV_MAXWIN;
        }
        return seq;
    }

    /** Un tour : grille initiale puis cascades ; forceZeus : nombre minimal de Zeus (achat du bonus). */
    private static Spin spin(OlympeRules r, RandomGenerator rng, boolean free, int mult, int forceZeus) {
        Spin s = new Spin();
        int[] grid = new int[CELLS], orb = new int[CELLS];
        for (int c = 0; c < CELLS; c++) draw(r, rng, free, grid, orb, c);
        if (forceZeus > 0) {
            while (count(grid, ZEUS) < forceZeus) {
                int c = rng.nextInt(CELLS);
                grid[c] = ZEUS;
                orb[c] = 0;
            }
        }
        for (int k = 0; k < MAX_STEPS; k++) {
            Step st = new Step();
            System.arraycopy(grid, 0, st.grid, 0, CELLS);
            System.arraycopy(orb, 0, st.orb, 0, CELLS);
            int[] counts = new int[REGULAR];
            for (int g : grid) if (g < REGULAR) counts[g]++;
            double win = 0;
            int mask = 0;
            for (int sym = 0; sym < REGULAR; sym++) {
                int n = counts[sym];
                if (n < 8) continue;
                win += r.pays[sym][n >= 12 ? 2 : n >= 10 ? 1 : 0] * r.payScale;
                for (int c = 0; c < CELLS; c++) if (grid[c] == sym) mask |= 1 << c;
            }
            st.winMask = mask;
            st.winMilli = (int) Math.round(win * 1000);
            s.steps.add(st);
            if (mask == 0) break;
            collapse(r, rng, free, grid, orb, mask);
        }
        int[] last = s.last().grid, lastOrb = s.last().orb;
        int sum = 0;
        for (int c = 0; c < CELLS; c++) if (last[c] == ORB && lastOrb[c] > 0) sum += r.orbValues[lastOrb[c] - 1];
        s.orbSum = sum;
        int tumble = s.tumbleMilli();
        long credited;
        if (free) {
            s.totalMult = tumble > 0 && sum > 0 ? mult + sum : mult;
            credited = (long) tumble * (tumble > 0 && sum > 0 ? Math.max(1, s.totalMult) : 1);
        } else {
            s.totalMult = 0;
            credited = (long) tumble * (sum > 0 && tumble > 0 ? sum : 1);
        }
        int zeus = count(last, ZEUS);
        if (zeus >= 4) s.scatterMilli = (int) Math.round(r.scatterPays[zeus >= 6 ? 2 : zeus - 4] * r.payScale * 1000);
        s.winMilli = (int) Math.min(Integer.MAX_VALUE / 4, credited + s.scatterMilli);
        return s;
    }

    private static void draw(OlympeRules r, RandomGenerator rng, boolean free, int[] grid, int[] orb, int c) {
        double u = rng.nextDouble();
        double po = free ? r.orbFree : r.orb, pz = free ? r.scatterFree : r.scatter;
        orb[c] = 0;
        if (u < po) {
            grid[c] = ORB;
            orb[c] = 1 + Rotten.pick(r.orbWeights, rng);
        } else if (u < po + pz) {
            grid[c] = ZEUS;
        } else {
            grid[c] = Rotten.pick(r.weights, rng);
        }
    }

    /** Les cases gagnantes disparaissent, le reste de chaque colonne tombe, de nouveaux symboles arrivent en haut. */
    private static void collapse(OlympeRules r, RandomGenerator rng, boolean free, int[] grid, int[] orb, int mask) {
        for (int reel = 0; reel < REELS; reel++) {
            int[] g = new int[ROWS], o = new int[ROWS];
            int n = ROWS - 1;
            for (int row = ROWS - 1; row >= 0; row--) {
                int c = cell(reel, row);
                if ((mask & (1 << c)) != 0) continue;
                g[n] = grid[c];
                o[n] = orb[c];
                n--;
            }
            for (int row = 0; row < ROWS; row++) {
                int c = cell(reel, row);
                if (row <= n) {
                    draw(r, rng, free, grid, orb, c);
                } else {
                    grid[c] = g[row];
                    orb[c] = o[row];
                }
            }
        }
    }

    public static int count(int[] grid, int sym) {
        int n = 0;
        for (int g : grid) if (g == sym) n++;
        return n;
    }

    // =================================================================== suspense
    /**
     * La colonne "reel" de la grille de depart tombe au ralenti : il ne manque plus qu'un Zeus sur les colonnes deja
     * tombees (3 sur 4 en jeu normal, 2 sur 3 pendant les tours gratuits).
     */
    public static boolean tease(Spin s, int reel) {
        if (reel <= 0 || s.steps.isEmpty()) return false;
        int[] grid = s.steps.get(0).grid;
        int zeus = 0;
        for (int c = 0; c < reel * ROWS; c++) if (grid[c] == ZEUS) zeus++;
        return zeus == (s.kind == FREE ? 2 : 3);
    }

    // =================================================================== chronologie (ticks)
    /** Cascade : gain montre, symboles qui explosent, chute ; foudroiement des orbes. */
    public static final int SHOW = 18, POP = 8, FALL = 14, ORB_TICKS = 34;
    /** Annonces : tours gratuits gagnes, tours en plus, fin du bonus. */
    public static final int FREE_T = 64, RETRIGGER_T = 36, END_T = 60;
    /** Chute de la grille de depart : premiere colonne posee, ecart entre deux colonnes, attente apres la derniere. */
    public static final float LAND_FIRST = 17, LAND_STEP = 3.4f;
    public static final int LAND_TAIL = 6;

    /** Instant ou la colonne "reel" de la grille de depart est posee. */
    public static float landTick(Spin s, int reel) {
        float t = LAND_FIRST + reel * LAND_STEP;
        for (int k = 1; k <= reel; k++) if (tease(s, k)) t += Suspense.HOLD_DROP;
        return t;
    }

    /** Duree de la chute de la grille de depart (colonnes en suspens comprises). */
    public static int drop(Spin s) {
        return (int) Math.ceil(landTick(s, REELS - 1)) + LAND_TAIL;
    }

    /** Debut de l'etape k d'un tour (ticks depuis le debut du tour). */
    public static int stepStart(Spin s, int k) {
        return drop(s) + k * (SHOW + POP + FALL);
    }

    /** Fin des cascades (derniere grille posee). */
    public static int settled(Spin s) {
        return stepStart(s, s.steps.size() - 1);
    }

    public static int winShow(Spin s) {
        if (s.winMilli <= 0) return 14;
        return Rotten.bigTier(s.winMilli) > 0 ? Rotten.BIG_WIN_TICKS : 34;
    }

    public static int duration(Spin s) {
        int t = settled(s) + 4;
        if (s.orbSum > 0 && s.tumbleMilli() > 0) t += ORB_TICKS;
        t += winShow(s);
        if ((s.events & EV_FREE) != 0) t += FREE_T;
        if ((s.events & EV_RETRIGGER) != 0) t += RETRIGGER_T;
        if ((s.events & EV_BONUS_END) != 0) t += END_T;
        if ((s.events & EV_MAXWIN) != 0) t += Rotten.BIG_WIN_TICKS;
        return t;
    }

    public static int totalDuration(Sequence seq) {
        int t = 0;
        for (Spin s : seq.spins) t += duration(s);
        return t;
    }

    public static SlotEngine.Status status(Sequence seq, float t) {
        int acc = 0;
        for (Spin s : seq.spins) {
            int d = duration(s);
            if (t < acc + d) return new SlotEngine.Status(s.kind == FREE ? 1 : 0, s.kind == FREE ? 2 : 0, s.freeTotal - s.freeLeft, s.freeTotal, 0);
            acc += d;
        }
        return SlotEngine.Status.NONE;
    }

    /** A l'instant t (ticks depuis le lancement), une colonne tombe-t-elle au ralenti (la borne affiche son annonce) ? */
    public static boolean teasing(Sequence seq, float t) {
        int acc = 0;
        for (Spin s : seq.spins) {
            int d = duration(s);
            if (t < acc + d) {
                float lt = t - acc;
                for (int reel = 1; reel < REELS; reel++) {
                    if (tease(s, reel) && lt >= landTick(s, reel - 1) && lt < landTick(s, reel) + 8) return true;
                }
                return false;
            }
            acc += d;
        }
        return false;
    }

    public static SlotEngine engine(OlympeRules r) {
        return new SlotEngine() {
            @Override
            public Play play(RandomGenerator rng, int buy) {
                Sequence seq = Olympe.play(r, rng, buy);
                return new Play(encode(seq), seq.totalMilli, totalDuration(seq));
            }

            @Override
            public double buyPrice(int buy) {
                return buy == BUY_FREE ? r.buyFree : 0;
            }

            @Override
            public long minBet() {
                return r.minBet;
            }

            @Override
            public long maxBet() {
                return r.maxBet;
            }
        };
    }

    // =================================================================== synchro (tableau d'entiers)
    public static int[] encode(Sequence seq) {
        int n = 2;
        for (Spin s : seq.spins) n += 6 + s.steps.size() * 10;
        int[] out = new int[n];
        out[0] = seq.buy;
        out[1] = (int) Math.min(Integer.MAX_VALUE, seq.totalMilli);
        int o = 2;
        for (Spin s : seq.spins) {
            out[o] = s.kind | s.events << 4 | s.steps.size() << 12;
            out[o + 1] = s.freeLeft | s.freeTotal << 8;
            out[o + 2] = s.orbSum;
            out[o + 3] = s.totalMult;
            out[o + 4] = s.winMilli;
            out[o + 5] = s.scatterMilli;
            o += 6;
            for (Step st : s.steps) {
                for (int c = 0; c < CELLS; c++) {
                    out[o + c / 8] |= (st.grid[c] & 15) << ((c % 8) * 4);
                    out[o + 4 + c / 8] |= (st.orb[c] & 15) << ((c % 8) * 4);
                }
                out[o + 8] = st.winMask;
                out[o + 9] = st.winMilli;
                o += 10;
            }
        }
        return out;
    }

    public static Sequence decode(int[] d) {
        Sequence seq = new Sequence();
        if (d == null || d.length < 2) return seq;
        seq.buy = d[0];
        seq.totalMilli = d[1];
        int o = 2;
        while (o + 6 <= d.length) {
            Spin s = new Spin();
            s.kind = d[o] & 15;
            s.events = (d[o] >> 4) & 255;
            int steps = (d[o] >> 12) & 1023;
            s.freeLeft = d[o + 1] & 255;
            s.freeTotal = (d[o + 1] >> 8) & 255;
            s.orbSum = d[o + 2];
            s.totalMult = d[o + 3];
            s.winMilli = d[o + 4];
            s.scatterMilli = d[o + 5];
            o += 6;
            for (int k = 0; k < steps && o + 10 <= d.length; k++) {
                Step st = new Step();
                for (int c = 0; c < CELLS; c++) {
                    st.grid[c] = (d[o + c / 8] >> ((c % 8) * 4)) & 15;
                    st.orb[c] = (d[o + 4 + c / 8] >> ((c % 8) * 4)) & 15;
                }
                st.winMask = d[o + 8];
                st.winMilli = d[o + 9];
                s.steps.add(st);
                o += 10;
            }
            if (s.steps.isEmpty()) break;
            seq.spins.add(s);
        }
        return seq;
    }
}
