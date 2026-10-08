package fr.heraliz.casino.game.slot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * "La Niche" (logique pure), sur le modele de The Dog House Multihold : 5 rouleaux x 3 rangees, 20 lignes.
 * <ul>
 * <li>WILD (la niche) : rouleaux 2 a 4, toujours x2 ou x3 ; il remplace les symboles de paiement (jamais le BONUS) ;
 * les multiplicateurs des wilds d'une meme ligne s'ADDITIONNENT (x2 + x3 = x5).</li>
 * <li>BONUS (la patte) : rouleaux 1, 3 et 5 ; les 3 a la fois = 7 tours gratuits et 2 fois la mise tout de suite.</li>
 * <li>Tours gratuits "Multihold" : 4 emplacements de grille, debloques dans l'ordre (haut gauche, haut droite, bas
 * gauche, bas droite). Seule la derniere grille debloquee recoit des wilds (qui deviennent collants jusqu'a la fin) et
 * des BONUS : 3 BONUS y debloquent la grille suivante, qui recoit une copie de tous ses wilds collants (memes cases,
 * memes multiplicateurs) et +1 a +3 tours ; elle tourne aussitot (deux deblocages dans le meme tour sont possibles).
 * Chaque grille paie ses 20 lignes separement.</li>
 * </ul>
 */
public final class Niche {
    private Niche() {
    }

    public static final int REELS = 5, ROWS = 3, CELLS = REELS * ROWS, GRIDS = 4;
    /** Symboles : 0-4 cartes, os, collier, chiens 1 a 4, WILD, BONUS. */
    public static final int BONE = 5, COLLAR = 6, DOG1 = 7, DOG4 = 10, WILD = 11, BONUS = 12, PAY_SYMBOLS = 11;
    public static final int BASE = 0, FREE = 1;
    public static final int EV_TRIGGER = 1, EV_UNLOCK = 2, EV_LAST = 4, EV_END = 8, EV_MAXWIN = 16;
    public static final int BUY_FREE = 1;
    public static final int MAX_SPINS = 60;

    /** 20 lignes : rangee (0 haut) sur chaque rouleau. */
    public static final int[][] LINES = {
            {1, 1, 1, 1, 1}, {0, 0, 0, 0, 0}, {2, 2, 2, 2, 2}, {0, 1, 2, 1, 0}, {2, 1, 0, 1, 2}, {1, 0, 0, 0, 1}, {1, 2, 2, 2, 1},
            {0, 0, 1, 2, 2}, {2, 2, 1, 0, 0}, {1, 2, 1, 0, 1}, {1, 0, 1, 2, 1}, {0, 1, 1, 1, 0}, {2, 1, 1, 1, 2}, {0, 1, 0, 1, 0},
            {2, 1, 2, 1, 2}, {1, 1, 0, 1, 1}, {1, 1, 2, 1, 1}, {0, 0, 2, 0, 0}, {2, 2, 0, 2, 2}, {0, 2, 2, 2, 0},
    };

    public static int cell(int reel, int row) {
        return reel * ROWS + row;
    }

    public static final class Spin {
        public int kind, events;
        /** Grilles debloquees au debut et a la fin de ce tour (1 en jeu normal). */
        public int startGrids = 1, grids = 1;
        /** Symboles et multiplicateurs (2 ou 3 pour un wild) de chaque grille. */
        public final int[][] sym = new int[GRIDS][CELLS];
        public final int[][] mult = new int[GRIDS][CELLS];
        /** Wilds collants de chaque grille apres ce tour ; wilds tombes pendant ce tour (derniere grille). */
        public final int[] sticky = new int[GRIDS];
        public final int[] fresh = new int[GRIDS];
        /** Lignes gagnantes et gain (millemes) de chaque grille. */
        public final int[] lines = new int[GRIDS];
        public final int[] gridMilli = new int[GRIDS];
        /** Tours gagnes a chaque deblocage de ce tour (dans l'ordre). */
        public final int[] unlockAdd = new int[GRIDS - 1];
        public int unlocked, spinsLeft;
        /** Gain du tour (lignes de toutes les grilles + gain du declenchement). */
        public int winMilli, bonusMilli;

        public int count(int g, int s) {
            int n = 0;
            for (int c : sym[g]) if (c == s) n++;
            return n;
        }
    }

    public static final class Sequence {
        public final List<Spin> spins = new ArrayList<>();
        public long totalMilli;
        public int buy;
    }

    // =================================================================== gains
    /** Gain d'une grille (x la mise, millemes) ; lines[0] recoit le masque des lignes gagnantes. */
    public static long gridMilli(NicheRules r, int[] sym, int[] mult, int[] lines) {
        long total = 0;
        int mask = 0;
        for (int l = 0; l < LINES.length; l++) {
            long w = lineMilli(r, sym, mult, LINES[l]);
            if (w > 0) {
                total += w;
                mask |= 1 << l;
            }
        }
        if (lines != null) lines[0] = mask;
        return total;
    }

    /** Gain d'une ligne : symboles identiques depuis le rouleau 1, wilds compris, multiplicateurs des wilds additionnes. */
    public static long lineMilli(NicheRules r, int[] sym, int[] mult, int[] line) {
        int s = sym[cell(0, line[0])];
        if (s >= PAY_SYMBOLS) return 0;
        int count = 1, wilds = 0;
        for (int reel = 1; reel < REELS; reel++) {
            int c = cell(reel, line[reel]);
            if (sym[c] == s) {
                count++;
            } else if (sym[c] == WILD) {
                count++;
                wilds += mult[c];
            } else {
                break;
            }
        }
        if (count < 3) return 0;
        return Math.round(r.pays[s][count - 3] * Math.max(1, wilds) * r.payScale * 1000);
    }

    /** Nombre de cases d'une ligne qui gagnent (pour l'affichage). */
    public static int lineLength(int[] sym, int[] line) {
        int s = sym[cell(0, line[0])];
        if (s >= PAY_SYMBOLS) return 0;
        int count = 1;
        for (int reel = 1; reel < REELS; reel++) {
            int c = sym[cell(reel, line[reel])];
            if (c == s || c == WILD) count++;
            else break;
        }
        return count;
    }

    // =================================================================== tirage
    private static final class Game {
        final NicheRules r;
        final RandomGenerator rng;
        final Sequence seq = new Sequence();
        final long max;
        boolean capped;

        Game(NicheRules r, RandomGenerator rng) {
            this.r = r;
            this.rng = rng;
            this.max = Math.round(r.maxWin * 1000);
        }

        void win(Spin s, long milli) {
            if (milli <= 0 || capped) return;
            long add = Math.min(milli, max - seq.totalMilli);
            s.winMilli = (int) Math.min(Integer.MAX_VALUE / 4, s.winMilli + add);
            seq.totalMilli += add;
            if (seq.totalMilli >= max) {
                capped = true;
                s.events |= EV_MAXWIN;
            }
        }
    }

    public static Sequence play(NicheRules r, RandomGenerator rng, int buy) {
        Game g = new Game(r, rng);
        g.seq.buy = buy;
        Spin first = new Spin();
        first.kind = BASE;
        fill(g, first, 0, r.wild, buy == BUY_FREE ? 2 : r.bonus, buy == BUY_FREE);
        first.sticky[0] = 0;
        g.seq.spins.add(first);
        int[] lm = new int[1];
        long w = gridMilli(r, first.sym[0], first.mult[0], lm);
        first.lines[0] = lm[0];
        first.gridMilli[0] = (int) w;
        g.win(first, w);
        if (bonusCount(first.sym[0]) >= 3 && !g.capped) {
            first.events |= EV_TRIGGER;
            first.bonusMilli = (int) Math.round(r.scatterPay * r.payScale * 1000);
            g.win(first, first.bonusMilli);
            if (!g.capped) free(g);
        }
        return g.seq;
    }

    public static int bonusCount(int[] sym) {
        int n = 0;
        for (int reel = 0; reel < REELS; reel += 2) {
            for (int row = 0; row < ROWS; row++) {
                if (sym[cell(reel, row)] == BONUS) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /**
     * Fait tourner une grille : symboles de paiement (les wilds collants restent), wilds (rouleaux 2 a 4) si la grille
     * en recoit, BONUS (rouleaux 1, 3, 5) avec la chance donnee (2 : force sur les 3 rouleaux, achat).
     */
    private static void fill(Game g, Spin s, int grid, double wildRate, double bonusRate, boolean force) {
        NicheRules r = g.r;
        RandomGenerator rng = g.rng;
        int[] sym = s.sym[grid], mult = s.mult[grid];
        int sticky = s.sticky[grid];
        for (int reel = 0; reel < REELS; reel++) {
            for (int row = 0; row < ROWS; row++) {
                int c = cell(reel, row);
                if ((sticky & (1 << c)) != 0) continue;
                sym[c] = Rotten.pick(r.weights[reel], rng);
                mult[c] = 0;
                if (reel >= 1 && reel <= 3 && wildRate > 0 && rng.nextDouble() < wildRate) {
                    sym[c] = WILD;
                    mult[c] = Rotten.pick(r.wildMults, rng) == 0 ? 2 : 3;
                    s.fresh[grid] |= 1 << c;
                }
            }
        }
        if (bonusRate <= 0) return;
        for (int reel = 0; reel < REELS; reel += 2) {
            if (!force && rng.nextDouble() >= bonusRate) continue;
            // la patte ne tombe jamais sur un wild collant (rouleau 3) : elle prend une des autres cases du rouleau
            int open = 0;
            for (int row = 0; row < ROWS; row++) if ((sticky & (1 << cell(reel, row))) == 0) open++;
            if (open == 0) continue;
            int pick = rng.nextInt(open), row = 0;
            for (; row < ROWS; row++) {
                if ((sticky & (1 << cell(reel, row))) != 0) continue;
                if (pick-- == 0) break;
            }
            int c = cell(reel, row);
            sym[c] = BONUS;
            mult[c] = 0;
            // un wild tombe pendant ce tour sur cette case est remplace par la patte : il ne colle pas
            s.fresh[grid] &= ~(1 << c);
        }
    }

    /** Tours gratuits Multihold. */
    private static void free(Game g) {
        NicheRules r = g.r;
        RandomGenerator rng = g.rng;
        int left = r.freeSpins;
        int grids = 1;
        int[] sticky = new int[GRIDS];
        int[][] mult = new int[GRIDS][CELLS];
        Spin last = null;
        while (left > 0 && !g.capped && g.seq.spins.size() < MAX_SPINS) {
            left--;
            Spin s = new Spin();
            s.kind = FREE;
            s.startGrids = grids;
            if (left == 0) s.events |= EV_LAST;
            for (int k = 0; k < GRIDS; k++) {
                s.sticky[k] = sticky[k];
                for (int c = 0; c < CELLS; c++) {
                    if ((sticky[k] & (1 << c)) != 0) {
                        s.sym[k][c] = WILD;
                        s.mult[k][c] = mult[k][c];
                    }
                }
            }
            // toutes les grilles tournent ; la derniere seule recoit wilds (collants) et BONUS
            for (int k = 0; k < grids; k++) {
                boolean latest = k == grids - 1;
                fill(g, s, k, latest ? r.freeWild : 0, latest && grids < GRIDS ? r.freeBonus : 0, false);
            }
            stick(s, grids - 1, sticky, mult);
            // 3 BONUS sur la derniere grille : la suivante se debloque, recoit ses wilds et tourne aussitot
            while (grids < GRIDS && bonusCount(s.sym[grids - 1]) >= 3) {
                int from = grids - 1, to = grids;
                sticky[to] = sticky[from];
                System.arraycopy(mult[from], 0, mult[to], 0, CELLS);
                s.sticky[to] = sticky[to];
                for (int c = 0; c < CELLS; c++) {
                    if ((sticky[to] & (1 << c)) != 0) {
                        s.sym[to][c] = WILD;
                        s.mult[to][c] = mult[to][c];
                    }
                }
                int add = 1 + Rotten.pick(r.unlockSpins, rng);
                s.unlockAdd[s.unlocked++] = add;
                left += add;
                grids++;
                s.events |= EV_UNLOCK;
                fill(g, s, to, r.freeWild, grids < GRIDS ? r.freeBonus : 0, false);
                stick(s, to, sticky, mult);
            }
            s.grids = grids;
            s.spinsLeft = left;
            long total = 0;
            int[] lm = new int[1];
            for (int k = 0; k < grids; k++) {
                long w = gridMilli(r, s.sym[k], s.mult[k], lm);
                s.lines[k] = lm[0];
                s.gridMilli[k] = (int) Math.min(Integer.MAX_VALUE / 8, w);
                total += w;
            }
            g.seq.spins.add(s);
            g.win(s, total);
            last = s;
        }
        if (last != null) {
            last.events |= EV_END;
            // total du bonus (declenchement compris), annonce a la fin
            long bonus = 0;
            for (Spin sp : g.seq.spins) bonus += sp.kind == FREE ? sp.winMilli : sp.bonusMilli;
            last.bonusMilli = (int) Math.min(Integer.MAX_VALUE / 4, bonus);
        }
    }

    /** Les wilds tombes sur la grille deviennent collants. */
    private static void stick(Spin s, int grid, int[] sticky, int[][] mult) {
        for (int c = 0; c < CELLS; c++) {
            if (s.sym[grid][c] == WILD && (sticky[grid] & (1 << c)) == 0) {
                sticky[grid] |= 1 << c;
                mult[grid][c] = s.mult[grid][c];
            }
        }
        s.sticky[grid] = sticky[grid];
    }

    // =================================================================== suspense
    /** Le dernier rouleau tourne au ralenti : les BONUS des rouleaux 1 et 3 attendent celui du rouleau 5. */
    public static boolean tease(Spin s, int grid, int reel) {
        if (reel != REELS - 1) return false;
        if (s.kind == FREE && (grid != s.startGrids - 1 || s.startGrids >= GRIDS)) return false;
        return hasBonus(s.sym[grid], 0) && hasBonus(s.sym[grid], 2);
    }

    private static boolean hasBonus(int[] sym, int reel) {
        for (int row = 0; row < ROWS; row++) if (sym[cell(reel, row)] == BONUS) return true;
        return false;
    }

    // =================================================================== chronologie (ticks)
    /** Arret d'un rouleau sans suspense : jeu normal (le premier apres 0,9 s, puis toutes les 0,3 s), tours gratuits. */
    public static int plainStop(Spin s, int reel) {
        return s.kind == BASE ? 18 + 6 * reel : 14 + 5 * reel;
    }

    /** Tour normal : arret de chaque rouleau ; un rouleau en suspens tourne Suspense.HOLD ticks de plus. */
    public static int stopTick(Spin s, int reel) {
        int t = plainStop(s, reel);
        if (tease(s, s.kind == BASE ? 0 : s.startGrids - 1, reel)) t += Suspense.HOLD;
        return t;
    }

    public static int spinEnd(Spin s) {
        return stopTick(s, REELS - 1) + 4;
    }

    /** Declenchement : les 3 BONUS brillent, +2 fois la mise, 7 tours ; puis l'ecran se change en 4 grilles. */
    public static final int TRIGGER_T = 58, TRANSITION_T = 44;
    /** Deblocage : BONUS, verrou qui casse, grille qui grandit, copie des wilds (3 ticks chacun), +N tours, puis elle tourne. */
    public static final int UNLOCK_LOCK = 12, UNLOCK_BREAK = 28, UNLOCK_GROW = 40, COPY_STEP = 3, ADD_T = 20, NEW_SPIN = 32;
    public static final int WIN_T = 32, END_T = 60, FADE_T = 30, LAST_T = 20;

    /** Arret d'un rouleau de la grille qui vient de s'ouvrir (ticks depuis le debut de sa rotation, avant NEW_SPIN). */
    public static int newStop(int reel) {
        return 8 + 4 * reel;
    }

    /** Affichage des gains du tour (un gros gain en jeu normal : le chien de garde vient l'annoncer). */
    public static int winShow(Spin s) {
        long lines = 0;
        for (int k = 0; k < GRIDS; k++) lines += s.gridMilli[k];
        if (lines <= 0) return 12;
        return Rotten.bigTier(lines) > 0 && s.kind == BASE ? Rotten.BIG_WIN_TICKS : WIN_T;
    }

    /** Fin du bonus : total (s.bonusMilli), annonce du gros gain si besoin. */
    public static int endShow(Spin s) {
        return Rotten.bigTier(s.bonusMilli) > 0 ? Rotten.BIG_WIN_TICKS : END_T;
    }

    /** Debut du tour gratuit proprement dit (apres l'annonce du dernier tour). */
    public static int spinStart(Spin s) {
        return (s.kind == FREE && (s.events & EV_LAST) != 0) ? LAST_T : 0;
    }

    /** Duree d'un deblocage (u : son rang dans le tour) : depend du nombre de wilds copies. */
    public static int unlockDuration(Spin s, int u) {
        return UNLOCK_GROW + copyTime(s, u) + ADD_T + NEW_SPIN;
    }

    /** Duree de la copie des wilds du deblocage u. */
    public static int copyTime(Spin s, int u) {
        return Math.min(30, 6 + Integer.bitCount(copiedMask(s, u)) * COPY_STEP);
    }

    /** Wilds copies lors du deblocage u (wilds collants de la grille precedente). */
    public static int copiedMask(Spin s, int u) {
        int from = s.startGrids - 1 + u;
        // les wilds de la grille de depart : ses collants apres le tour (ceux tombes pendant le tour compris)
        return from >= 0 && from < GRIDS ? s.sticky[from] : 0;
    }

    /** Debut du deblocage u. */
    public static int unlockStart(Spin s, int u) {
        int t = spinStart(s) + spinEnd(s) + 4;
        for (int k = 0; k < u; k++) t += unlockDuration(s, k);
        return t;
    }

    /** Debut de l'affichage des gains (apres les deblocages). */
    public static int winStart(Spin s) {
        if (s.kind == BASE) return spinEnd(s);
        return unlockStart(s, s.unlocked);
    }

    public static int duration(Spin s) {
        int t;
        if (s.kind == BASE) {
            t = spinEnd(s) + winShow(s);
            if ((s.events & EV_TRIGGER) != 0) t += TRIGGER_T + TRANSITION_T;
        } else {
            t = winStart(s) + winShow(s);
            if ((s.events & EV_END) != 0) t += endShow(s) + FADE_T;
        }
        if ((s.events & EV_MAXWIN) != 0) t += Rotten.BIG_WIN_TICKS;
        return t;
    }

    /** Debut de l'annonce du total du bonus (dernier tour), puis le retrait des grilles. */
    public static int endStart(Spin s) {
        return winStart(s) + winShow(s);
    }

    public static int fadeStart(Spin s) {
        return endStart(s) + endShow(s);
    }

    public static int totalDuration(Sequence seq) {
        int t = 0;
        for (Spin s : seq.spins) t += duration(s);
        return t;
    }

    public static float[] at(Sequence seq, float t) {
        if (seq.spins.isEmpty() || t < 0) return null;
        int acc = 0;
        for (int i = 0; i < seq.spins.size(); i++) {
            int d = duration(seq.spins.get(i));
            if (t < acc + d) return new float[]{i, t - acc};
            acc += d;
        }
        return null;
    }

    public static SlotEngine.Status status(Sequence seq, float t) {
        float[] at = at(seq, t);
        if (at == null) return SlotEngine.Status.NONE;
        Spin s = seq.spins.get((int) at[0]);
        if (s.kind == FREE) return new SlotEngine.Status(2, 2, 0, 0, spinsShown(s, at[1]));
        return SlotEngine.Status.NONE;
    }

    /** Tours restants annonces au temps lt du tour : les tours gagnes a un deblocage comptent des leur annonce (+N tours). */
    public static int spinsShown(Spin s, float lt) {
        int left = s.spinsLeft;
        for (int u = 0; u < s.unlocked; u++) if (lt < unlockStart(s, u) + UNLOCK_GROW + copyTime(s, u)) left -= s.unlockAdd[u];
        return Math.max(0, left);
    }

    public static boolean teasing(Sequence seq, float t) {
        float[] at = at(seq, t);
        if (at == null) return false;
        Spin s = seq.spins.get((int) at[0]);
        float lt = at[1] - spinStart(s);
        int g = s.kind == BASE ? 0 : s.startGrids - 1;
        return tease(s, g, REELS - 1) && lt >= stopTick(s, REELS - 2) && lt < stopTick(s, REELS - 1) + 8;
    }

    public static SlotEngine engine(NicheRules r) {
        return new SlotEngine() {
            @Override
            public Play play(RandomGenerator rng, int buy) {
                Sequence seq = Niche.play(r, rng, buy);
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

    // =================================================================== synchro
    private static final int GRID_INTS = 6, STRIDE = 5 + GRIDS * GRID_INTS, FORMAT = 1;

    public static int[] encode(Sequence seq) {
        int[] out = new int[2 + seq.spins.size() * STRIDE];
        out[0] = (seq.buy & 255) | FORMAT << 8;
        out[1] = (int) Math.min(Integer.MAX_VALUE, seq.totalMilli);
        for (int i = 0; i < seq.spins.size(); i++) {
            Spin s = seq.spins.get(i);
            int o = 2 + i * STRIDE;
            out[o] = s.kind | s.events << 2 | s.startGrids << 10 | s.grids << 13 | s.unlocked << 16;
            out[o + 1] = (s.spinsLeft & 255) | s.unlockAdd[0] << 8 | s.unlockAdd[1] << 11 | s.unlockAdd[2] << 14;
            out[o + 2] = s.winMilli;
            out[o + 3] = s.bonusMilli;
            for (int k = 0; k < GRIDS; k++) {
                int g = o + 5 + k * GRID_INTS;
                for (int c = 0; c < CELLS; c++) {
                    out[g + c / 8] |= (s.sym[k][c] & 15) << ((c % 8) * 4);
                    out[g + 2] |= (s.mult[k][c] == 3 ? 2 : s.mult[k][c] == 2 ? 1 : 0) << (c * 2);
                }
                out[g + 3] = s.sticky[k] | s.fresh[k] << 15;
                out[g + 4] = s.lines[k];
                out[g + 5] = s.gridMilli[k];
            }
        }
        return out;
    }

    public static Sequence decode(int[] d) {
        Sequence seq = new Sequence();
        if (d == null || d.length < 2 || ((d[0] >> 8) & 255) != FORMAT || (d.length - 2) % STRIDE != 0) return seq;
        seq.buy = d[0] & 255;
        seq.totalMilli = d[1];
        int n = (d.length - 2) / STRIDE;
        for (int i = 0; i < n; i++) {
            int o = 2 + i * STRIDE;
            Spin s = new Spin();
            s.kind = d[o] & 3;
            s.events = (d[o] >>> 2) & 255;
            s.startGrids = Math.max(1, Math.min(GRIDS, (d[o] >>> 10) & 7));
            s.grids = Math.max(s.startGrids, Math.min(GRIDS, (d[o] >>> 13) & 7));
            s.unlocked = Math.min(GRIDS - 1, (d[o] >>> 16) & 7);
            s.spinsLeft = d[o + 1] & 255;
            for (int u = 0; u < GRIDS - 1; u++) s.unlockAdd[u] = (d[o + 1] >>> (8 + u * 3)) & 7;
            s.winMilli = d[o + 2];
            s.bonusMilli = d[o + 3];
            for (int k = 0; k < GRIDS; k++) {
                int g = o + 5 + k * GRID_INTS;
                for (int c = 0; c < CELLS; c++) {
                    s.sym[k][c] = Math.min(BONUS, (d[g + c / 8] >> ((c % 8) * 4)) & 15);
                    int m = (d[g + 2] >> (c * 2)) & 3;
                    s.mult[k][c] = m == 2 ? 3 : m == 1 ? 2 : 0;
                }
                s.sticky[k] = d[g + 3] & 0x7FFF;
                s.fresh[k] = (d[g + 3] >>> 15) & 0x7FFF;
                s.lines[k] = d[g + 4];
                s.gridMilli[k] = d[g + 5];
            }
            if (s.kind > FREE) s.kind = BASE;
            seq.spins.add(s);
        }
        return seq;
    }
}
