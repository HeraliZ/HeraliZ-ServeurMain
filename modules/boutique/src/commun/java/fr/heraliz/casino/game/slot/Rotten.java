package fr.heraliz.casino.game.slot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Machine a sous "Rotten" (logique pure, partagee serveur / client / simulations) : 6 rouleaux x 5 rangees, 35 lignes.
 * <ul>
 * <li>WILD : remplace les symboles ordinaires ; une ligne de WILD paie comme le clown.</li>
 * <li>MUTATION : lance une serie de relances ; un ou plusieurs symboles choisis deviennent des clowns ou des WILD
 * pendant toute la serie (une nouvelle MUTATION ajoute des relances et un symbole).</li>
 * <li>3 SAVANT FOU : 10 tours gratuits ou les MUTATIONS sont bien plus frequentes.</li>
 * <li>3 INVASION TOTALE : 10 tours gratuits ou les symboles mutes le restent jusqu'a la fin du bonus.</li>
 * </ul>
 * Toute la partie (tour normal, relances, tours gratuits) est tiree d'un coup par le serveur au lancement, puis
 * rejouee a l'identique par les clients ; le gain est verse a la fin de l'animation.
 */
public final class Rotten {
    private Rotten() {
    }

    public static final int REELS = 6, ROWS = 5, CELLS = REELS * ROWS;
    public static final int TEN = 0, JACK = 1, QUEEN = 2, KING = 3, ACE = 4, ZGREEN = 5, ZBLUE = 6, ZYELLOW = 7, ZRED = 8, CLOWN = 9;
    public static final int WILD = 10, SAVANT = 11, INVASION = 12, MUTATION = 13, SYMBOLS = 14;
    /** Symboles qui peuvent muter (tous les ordinaires sauf le clown). */
    public static final int SWITCHABLE = 9;

    public static final int BASE = 0, RESPIN = 1, FREE = 2;
    public static final int MODE_BASE = 0, MODE_SAVANT = 1, MODE_INVASION = 2;
    /** Evenements d'un tour (affichage) : declenchement / passage en Invasion, relance du bonus, mutation, gain max, fin du bonus. */
    public static final int EV_SAVANT = 1, EV_INVASION = 2, EV_RETRIGGER = 4, EV_MUTATION = 8, EV_MAXWIN = 16, EV_BONUS_END = 32;
    /** Achat du bonus. */
    public static final int BUY_NONE = 0, BUY_SAVANT = 1, BUY_INVASION = 2;
    /** Nombre maximal de tours d'une partie. */
    public static final int MAX_SPINS = 220;

    /** Les 35 lignes : rangee (0 = haut) sur chaque rouleau. */
    public static final int[][] LINES = {
            {0, 0, 0, 0, 0, 0}, {1, 1, 1, 1, 1, 1}, {2, 2, 2, 2, 2, 2}, {3, 3, 3, 3, 3, 3}, {4, 4, 4, 4, 4, 4},
            {0, 1, 2, 2, 1, 0}, {4, 3, 2, 2, 3, 4}, {1, 2, 3, 3, 2, 1}, {3, 2, 1, 1, 2, 3}, {0, 1, 1, 1, 1, 0},
            {4, 3, 3, 3, 3, 4}, {1, 0, 0, 0, 0, 1}, {3, 4, 4, 4, 4, 3}, {2, 1, 1, 1, 1, 2}, {2, 3, 3, 3, 3, 2},
            {1, 2, 2, 2, 2, 1}, {3, 2, 2, 2, 2, 3}, {0, 0, 1, 1, 0, 0}, {4, 4, 3, 3, 4, 4}, {1, 1, 2, 2, 1, 1},
            {3, 3, 2, 2, 3, 3}, {2, 2, 1, 1, 2, 2}, {2, 2, 3, 3, 2, 2}, {0, 1, 0, 1, 0, 1}, {4, 3, 4, 3, 4, 3},
            {1, 2, 1, 2, 1, 2}, {3, 2, 3, 2, 3, 2}, {2, 1, 2, 1, 2, 1}, {2, 3, 2, 3, 2, 3}, {0, 1, 2, 3, 4, 4},
            {4, 3, 2, 1, 0, 0}, {1, 0, 1, 0, 1, 0}, {3, 4, 3, 4, 3, 4}, {2, 1, 0, 0, 1, 2}, {2, 3, 4, 4, 3, 2},
    };

    public static int cell(int reel, int row) {
        return reel * ROWS + row;
    }

    // =================================================================== un tour
    public static final class Spin {
        public int kind, mode, events;
        /** Symboles tombes (avant mutation). */
        public final int[] grid = new int[CELLS];
        /** Symboles mutes pendant ce tour (bit s) et ceux qui deviennent des WILD (sinon clown) ; mutations ajoutees par ce tour. */
        public int switched, wilds, added;
        /** Tours gratuits restants / au total, relances restantes (apres ce tour). */
        public int freeLeft, freeTotal, respinsLeft;
        /** Gain du tour (x la mise, en millemes). */
        public int winMilli;

        /** Symbole affiche a la fin du tour (apres mutation). */
        public int finalSymbol(int c) {
            int s = grid[c];
            if (s < SWITCHABLE && (switched & (1 << s)) != 0) return (wilds & (1 << s)) != 0 ? WILD : CLOWN;
            return s;
        }

        public int[] finalGrid() {
            int[] out = new int[CELLS];
            for (int c = 0; c < CELLS; c++) out[c] = finalSymbol(c);
            return out;
        }

        /** La case change de symbole a la mutation. */
        public boolean mutates(int c) {
            int s = grid[c];
            return s < SWITCHABLE && (switched & (1 << s)) != 0;
        }

        public int count(int symbol) {
            int n = 0;
            for (int s : grid) if (s == symbol) n++;
            return n;
        }
    }

    /** Une partie complete. */
    public static final class Sequence {
        public final List<Spin> spins = new ArrayList<>();
        /** Gain total (x la mise, en millemes). */
        public long totalMilli;
        public int buy;
    }

    // =================================================================== tirage
    public static Sequence play(RottenRules r, RandomGenerator rng, int buy) {
        Sequence seq = new Sequence();
        seq.buy = buy;
        double maxMilli = r.maxWin * 1000;
        Spin first = new Spin();
        first.kind = BASE;
        first.mode = MODE_BASE;
        fill(r, rng, first.grid, false, buy == BUY_NONE, buy == BUY_NONE ? r.mutation : 0);
        if (buy == BUY_SAVANT) force(rng, first.grid, SAVANT, 3);
        if (buy == BUY_INVASION) force(rng, first.grid, INVASION, 3);
        add(r, seq, first, maxMilli);
        int sav = first.count(SAVANT), inv = first.count(INVASION);
        if (first.count(MUTATION) > 0 && !capped(seq, maxMilli)) series(r, rng, seq, first, 0, 0, false, maxMilli);
        if (!capped(seq, maxMilli)) {
            if (inv >= 3) bonus(r, rng, seq, first, MODE_INVASION, maxMilli);
            else if (sav >= 3) bonus(r, rng, seq, first, MODE_SAVANT, maxMilli);
        }
        if (seq.totalMilli >= maxMilli) {
            seq.totalMilli = (long) maxMilli;
            seq.spins.get(seq.spins.size() - 1).events |= EV_MAXWIN;
        }
        return seq;
    }

    private static boolean capped(Sequence seq, double maxMilli) {
        return seq.totalMilli >= maxMilli || seq.spins.size() >= MAX_SPINS;
    }

    private static void add(RottenRules r, Sequence seq, Spin s, double maxMilli) {
        s.winMilli = (int) Math.min(Integer.MAX_VALUE / 2, Math.round(evaluate(r, s.finalGrid(), null) * 1000));
        seq.spins.add(s);
        seq.totalMilli += s.winMilli;
    }

    /** Tours gratuits. */
    private static void bonus(RottenRules r, RandomGenerator rng, Sequence seq, Spin trigger, int mode, double maxMilli) {
        trigger.events |= mode == MODE_INVASION ? EV_INVASION : EV_SAVANT;
        int left = mode == MODE_INVASION ? r.freeInvasion : r.freeSavant, total = left;
        trigger.freeLeft = left;
        trigger.freeTotal = total;
        // Invasion totale : les mutations s'accumulent jusqu'a la fin du bonus
        int keep = 0, keepWilds = 0;
        Spin last = trigger;
        while (left > 0 && !capped(seq, maxMilli)) {
            left--;
            Spin s = new Spin();
            s.kind = FREE;
            s.mode = mode;
            fill(r, rng, s.grid, true, true, mode == MODE_SAVANT ? r.mutationSavant : r.mutationInvasion);
            if (mode == MODE_INVASION) {
                s.switched = keep;
                s.wilds = keepWilds;
            }
            int sav = s.count(SAVANT), inv = s.count(INVASION);
            if (mode == MODE_SAVANT && inv >= 3) {
                mode = MODE_INVASION;
                left = r.freeInvasion;
                total = r.freeInvasion;
                s.events |= EV_INVASION;
            } else if (mode == MODE_SAVANT && sav >= 3 && r.retriggerSavant > 0) {
                left += r.retriggerSavant;
                total += r.retriggerSavant;
                s.events |= EV_RETRIGGER;
            } else if (mode == MODE_INVASION && inv >= 3 && r.retriggerInvasion > 0) {
                left += r.retriggerInvasion;
                total += r.retriggerInvasion;
                s.events |= EV_RETRIGGER;
            }
            s.freeLeft = left;
            s.freeTotal = total;
            add(r, seq, s, maxMilli);
            last = s;
            if (s.count(MUTATION) > 0 && !capped(seq, maxMilli)) {
                int[] kept = series(r, rng, seq, s, mode == MODE_INVASION ? keep : 0, mode == MODE_INVASION ? keepWilds : 0, mode == MODE_INVASION, maxMilli);
                if (mode == MODE_INVASION) {
                    keep = kept[0];
                    keepWilds = kept[1];
                }
                last = seq.spins.get(seq.spins.size() - 1);
                // les relances ne consomment pas de tour gratuit : on reporte le compteur
                for (int i = seq.spins.indexOf(s) + 1; i < seq.spins.size(); i++) {
                    seq.spins.get(i).freeLeft = left;
                    seq.spins.get(i).freeTotal = total;
                }
            }
        }
        last.events |= EV_BONUS_END;
    }

    /**
     * Serie de relances MUTATION declenchee par le tour "trigger".
     * @return symboles mutes et WILD a la fin de la serie (pour l'Invasion totale)
     */
    private static int[] series(RottenRules r, RandomGenerator rng, Sequence seq, Spin trigger, int switched, int wilds, boolean invasion, double maxMilli) {
        int[] sw = {switched, wilds};
        int before = sw[0];
        int n = 1 + pick(trigger.kind == FREE ? r.switchCountFree : r.switchCount, rng);
        for (int i = 0; i < n; i++) addSwitch(r, rng, sw);
        trigger.events |= EV_MUTATION;
        trigger.added = sw[0] & ~before;
        int left = 1 + pick(r.respins, rng);
        trigger.respinsLeft = left;
        while (left > 0 && !capped(seq, maxMilli)) {
            left--;
            Spin s = new Spin();
            s.kind = RESPIN;
            s.mode = trigger.mode;
            fill(r, rng, s.grid, invasion || trigger.kind == FREE, false, r.mutationRespin);
            s.switched = sw[0];
            s.wilds = sw[1];
            if (s.count(MUTATION) > 0) {
                int b = sw[0];
                left += 1 + rng.nextInt(2);
                addSwitch(r, rng, sw);
                s.events |= EV_MUTATION;
                s.added = sw[0] & ~b;
            }
            s.respinsLeft = left;
            s.freeLeft = trigger.freeLeft;
            s.freeTotal = trigger.freeTotal;
            add(r, seq, s, maxMilli);
        }
        return sw;
    }

    /** Ajoute un symbole mute (s'il en reste). */
    private static void addSwitch(RottenRules r, RandomGenerator rng, int[] sw) {
        int[] w = r.switchPick.clone();
        for (int s = 0; s < SWITCHABLE; s++) if ((sw[0] & (1 << s)) != 0) w[s] = 0;
        int total = 0;
        for (int x : w) total += Math.max(0, x);
        if (total <= 0) return;
        int s = pick(w, rng);
        sw[0] |= 1 << s;
        if (rng.nextDouble() < r.switchWild) sw[1] |= 1 << s;
    }

    /** Remplit la grille : symboles ordinaires, puis au plus un symbole de bonus par rouleau, puis une MUTATION. */
    private static void fill(RottenRules r, RandomGenerator rng, int[] grid, boolean free, boolean scatters, double mutation) {
        for (int reel = 0; reel < REELS; reel++) {
            for (int row = 0; row < ROWS; row++) grid[cell(reel, row)] = pick(r.weights[reel], rng);
        }
        if (scatters) {
            double ps = free ? r.scatterSavantFree : r.scatterSavant, pi = free ? r.scatterInvasionFree : r.scatterInvasion;
            for (int reel = 0; reel < REELS; reel++) {
                double u = rng.nextDouble();
                if (u < ps) grid[cell(reel, rng.nextInt(ROWS))] = SAVANT;
                else if (u < ps + pi) grid[cell(reel, rng.nextInt(ROWS))] = INVASION;
            }
        }
        if (mutation > 0 && rng.nextDouble() < mutation) {
            int reel = 1 + rng.nextInt(REELS - 2), row = rng.nextInt(ROWS);
            if (grid[cell(reel, row)] < WILD) grid[cell(reel, row)] = MUTATION;
        }
    }

    /** Achat du bonus : au moins n symboles de bonus sur des rouleaux differents. */
    private static void force(RandomGenerator rng, int[] grid, int symbol, int n) {
        int have = 0;
        boolean[] used = new boolean[REELS];
        for (int reel = 0; reel < REELS; reel++) {
            for (int row = 0; row < ROWS; row++) {
                if (grid[cell(reel, row)] == symbol) {
                    used[reel] = true;
                    have++;
                }
            }
        }
        while (have < n) {
            int reel = rng.nextInt(REELS);
            if (used[reel]) continue;
            int row = rng.nextInt(ROWS);
            if (grid[cell(reel, row)] >= SAVANT) continue;
            grid[cell(reel, row)] = symbol;
            used[reel] = true;
            have++;
        }
    }

    public static int pick(int[] weights, RandomGenerator rng) {
        int total = 0;
        for (int w : weights) total += Math.max(0, w);
        if (total <= 0) return 0;
        int x = rng.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            x -= Math.max(0, weights[i]);
            if (x < 0) return i;
        }
        return weights.length - 1;
    }

    // =================================================================== lecture des lignes
    /**
     * Gain d'une grille (x la mise). cells : si non null, recoit le masque (bit c) des cases gagnantes.
     */
    public static double evaluate(RottenRules r, int[] grid, long[] cells) {
        double total = 0;
        long mask = 0;
        for (int[] line : LINES) {
            int wildRun = 0;
            while (wildRun < REELS && grid[cell(wildRun, line[wildRun])] == WILD) wildRun++;
            int sym = wildRun < REELS ? grid[cell(wildRun, line[wildRun])] : WILD;
            int count = 0;
            if (sym < WILD) {
                while (count < REELS) {
                    int s = grid[cell(count, line[count])];
                    if (s != sym && s != WILD) break;
                    count++;
                }
            }
            double pay = sym < WILD && count >= 3 ? r.pays[sym][count - 3] : 0;
            double wildPay = wildRun >= 3 ? r.pays[CLOWN][wildRun - 3] : 0;
            int used = count;
            if (wildPay > pay) {
                pay = wildPay;
                used = wildRun;
            }
            if (pay > 0) {
                total += pay * r.payScale;
                for (int i = 0; i < used; i++) mask |= 1L << cell(i, line[i]);
            }
        }
        if (cells != null) cells[0] = mask;
        return total;
    }

    // =================================================================== suspense
    /**
     * Symbole de bonus que le rouleau "reel" fait esperer : deux SAVANT FOU (ou deux INVASION TOTALE) sont deja tombes
     * sur les rouleaux precedents et il n'en manque qu'un. -1 : pas de suspense (tour ordinaire, relance, bonus deja gagne).
     */
    public static int teaseSymbol(Spin s, int reel) {
        if (s.kind == RESPIN || reel <= 0) return -1;
        int sav = 0, inv = 0;
        for (int c = 0; c < reel * ROWS; c++) {
            if (s.grid[c] == SAVANT) sav++;
            else if (s.grid[c] == INVASION) inv++;
        }
        if (inv >= 3) return -1;
        if (inv == 2) return INVASION;
        // pendant l'Invasion totale, seuls les symboles INVASION relancent le bonus
        if (sav == 2 && !(s.kind == FREE && s.mode == MODE_INVASION)) return SAVANT;
        return -1;
    }

    public static boolean tease(Spin s, int reel) {
        return teaseSymbol(s, reel) >= 0;
    }

    // =================================================================== chronologie (ticks)
    /** Annonces : mutation, bonus gagne, tours en plus, fin du bonus (ticks). */
    public static final int MUTATION_T = 36, BONUS_T = 64, RETRIGGER_T = 36, END_T = 60;

    /** Arret d'un rouleau sans suspense : le premier apres une seconde, puis un toutes les 0,3 s (relances plus vives). */
    public static int plainStop(Spin s, int reel) {
        return s.kind == RESPIN ? 14 + 4 * reel : 20 + 6 * reel;
    }

    /**
     * Arret du rouleau "reel" (ticks depuis le debut du tour) : un rouleau en suspens (il ne manque qu'un symbole de
     * bonus) tourne Suspense.HOLD ticks de plus, au ralenti.
     */
    public static int stopTick(Spin s, int reel) {
        int t = plainStop(s, reel);
        if (s.kind != RESPIN) {
            for (int k = 1; k <= reel; k++) if (tease(s, k)) t += Suspense.HOLD;
        }
        return t;
    }

    /** Instant de la mutation des symboles (juste apres l'arret du dernier rouleau). */
    public static int morphAt(Spin s) {
        return stopTick(s, REELS - 1) + 3;
    }

    /** Fin de l'arret des rouleaux (et de la mutation s'il y en a une) : le gain s'affiche alors. */
    public static int landed(Spin s) {
        int t = morphAt(s);
        boolean morph = false;
        for (int c = 0; c < CELLS && !morph; c++) morph = s.mutates(c);
        if (morph) t += 14;
        return t;
    }

    /** Niveau d'un gros gain (x la mise, en millemes) : 0 aucun, 1 GROS (20x), 2 MEGA (50x), 3 EPIQUE (150x). */
    public static int bigTier(long milli) {
        return milli >= 150_000 ? 3 : milli >= 50_000 ? 2 : milli >= 20_000 ? 1 : 0;
    }

    /** Duree de l'animation du zombie des gros gains (ticks). */
    public static final int BIG_WIN_TICKS = 80;

    /** Temps d'affichage du gain d'un tour (gain simple ou gros gain avec le zombie) ; un tour perdu reste un instant a l'ecran. */
    public static int winShow(Spin s) {
        if (s.winMilli <= 0) return 12;
        return bigTier(s.winMilli) > 0 ? BIG_WIN_TICKS : 34;
    }

    public static int duration(Spin s) {
        int t = landed(s);
        t += winShow(s);
        if ((s.events & EV_MUTATION) != 0) t += MUTATION_T;
        if ((s.events & (EV_SAVANT | EV_INVASION)) != 0) t += BONUS_T;
        if ((s.events & EV_RETRIGGER) != 0) t += RETRIGGER_T;
        if ((s.events & EV_BONUS_END) != 0) t += END_T;
        if ((s.events & EV_MAXWIN) != 0) t += BIG_WIN_TICKS;
        return t;
    }

    // =================================================================== moteur commun des machines a sous
    public static SlotEngine engine(RottenRules r) {
        return new SlotEngine() {
            @Override
            public Play play(RandomGenerator rng, int buy) {
                Sequence seq = Rotten.play(r, rng, buy);
                return new Play(encode(seq), seq.totalMilli, totalDuration(seq));
            }

            @Override
            public double buyPrice(int buy) {
                return buy == BUY_SAVANT ? r.buySavant : buy == BUY_INVASION ? r.buyInvasion : 0;
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

    /** Etat affiche au temps t (ticks depuis le lancement). */
    public static SlotEngine.Status status(Sequence seq, float t) {
        int acc = 0;
        for (Spin s : seq.spins) {
            int d = duration(s);
            if (t < acc + d) {
                int kind = s.kind == RESPIN ? 1 : s.kind == FREE ? 2 : 0;
                return new SlotEngine.Status(s.mode, kind, s.freeTotal - s.freeLeft, s.freeTotal, s.respinsLeft);
            }
            acc += d;
        }
        return SlotEngine.Status.NONE;
    }

    public static int totalDuration(Sequence seq) {
        int t = 0;
        for (Spin s : seq.spins) t += duration(s);
        return t;
    }

    /** A l'instant t (ticks depuis le lancement), un rouleau tourne-t-il au ralenti (la borne affiche son annonce) ? */
    public static boolean teasing(Sequence seq, float t) {
        int acc = 0;
        for (Spin s : seq.spins) {
            int d = duration(s);
            if (t < acc + d) {
                float lt = t - acc;
                for (int reel = 1; reel < REELS; reel++) {
                    if (tease(s, reel) && lt >= stopTick(s, reel - 1) && lt < stopTick(s, reel) + 8) return true;
                }
                return false;
            }
            acc += d;
        }
        return false;
    }

    // =================================================================== synchro (tableau d'entiers)
    private static final int STRIDE = 8;

    public static int[] encode(Sequence seq) {
        int[] out = new int[2 + seq.spins.size() * STRIDE];
        out[0] = seq.buy;
        out[1] = (int) Math.min(Integer.MAX_VALUE, seq.totalMilli);
        for (int i = 0; i < seq.spins.size(); i++) {
            Spin s = seq.spins.get(i);
            int o = 2 + i * STRIDE;
            out[o] = s.kind | s.mode << 2 | s.events << 4;
            out[o + 1] = s.switched | s.wilds << 9 | s.added << 18;
            out[o + 2] = s.freeLeft | s.respinsLeft << 8 | s.freeTotal << 16;
            out[o + 3] = s.winMilli;
            for (int c = 0; c < CELLS; c++) out[o + 4 + c / 8] |= (s.grid[c] & 15) << ((c % 8) * 4);
        }
        return out;
    }

    public static Sequence decode(int[] data) {
        Sequence seq = new Sequence();
        if (data == null || data.length < 2) return seq;
        seq.buy = data[0];
        seq.totalMilli = data[1];
        int n = (data.length - 2) / STRIDE;
        for (int i = 0; i < n; i++) {
            int o = 2 + i * STRIDE;
            Spin s = new Spin();
            s.kind = data[o] & 3;
            s.mode = (data[o] >> 2) & 3;
            s.events = data[o] >> 4;
            s.switched = data[o + 1] & 511;
            s.wilds = (data[o + 1] >> 9) & 511;
            s.added = (data[o + 1] >> 18) & 511;
            s.freeLeft = data[o + 2] & 255;
            s.respinsLeft = (data[o + 2] >> 8) & 255;
            s.freeTotal = (data[o + 2] >> 16) & 255;
            s.winMilli = data[o + 3];
            for (int c = 0; c < CELLS; c++) s.grid[c] = (data[o + 4 + c / 8] >> ((c % 8) * 4)) & 15;
            seq.spins.add(s);
        }
        return seq;
    }
}
