package fr.heraliz.casino.game.slot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * "Souffle du Loup" (logique pure), sur le modele de Huff N' Even More Puff : 5 rouleaux x 3 rangees, 243 facons de
 * gagner.
 * <ul>
 * <li>LOUP : joker des rouleaux 2 a 4 ; il remplace les cartes et les cochons, jamais les chapeaux ni les scies.</li>
 * <li>6 CHAPEAUX ou plus : 6 tours gratuits ; chaque chapeau declencheur pose un cadre de paille.</li>
 * <li>3 SCIES ou plus : la roue (SCIES, MEGA CHAPEAU, MANOIR, MINI, MINOR, MAJOR, GRAND ou SUPER ROUE). 2 scies bleues
 * et 1 scie d'or : directement la SUPER ROUE (SUPER SCIES, SUPER MEGA CHAPEAU, SUPER MANOIR, MAJOR, GRAND, SUPER).</li>
 * <li>Pendant les tours gratuits, chaque chapeau cree un cadre de paille ou ameliore le sien (paille, bois, brique) ;
 * 3 chapeaux d'un coup : un tour de plus. A la fin, les cadres deviennent des maisons, le loup souffle et chaque maison
 * revele son gain (jetons, jackpot, ou pour une brique un nouveau tour de roue).</li>
 * <li>Les bonus SUPER se jouent sur une grille agrandie a 5 x 6 : la borne s'allonge, trois rangees apparaissent
 * au-dessus des rouleaux et les scies y sont recopiees.</li>
 * </ul>
 * Toute la partie est tiree d'un coup au lancement ; sa chronologie (ticks) est calculee ici, a l'identique pour le
 * serveur (versement du gain a la fin) et pour les clients (animation).
 */
public final class Loup {
    private Loup() {
    }

    public static final int REELS = 5, ROWS = 3, CELLS = REELS * ROWS;
    /** Grille des bonus : 6 rangees (les 3 du haut n'existent qu'en SUPER) ; case = rouleau * 6 + rangee (0 en haut). */
    public static final int BROWS = 6, BCELLS = REELS * BROWS, TOP = BROWS - ROWS;
    /** Symboles : 0-4 cartes, 5-7 cochons, LOUP (joker), CHAPEAU, SCIE (bleue), SCIE D'OR, case vide. */
    public static final int WOLF = 8, HAT = 9, SAW = 10, GOLD = 11, EMPTY = 12;
    /** Etapes d'une partie : tour normal, tour gratuit, mise en place d'un bonus, tour de roue gagne par une maison. */
    public static final int BASE = 0, FREE = 1, SETUP = 2, SPIN_WHEEL = 3;
    public static final int EV_HATS = 1, EV_WHEEL = 2, EV_SUPER = 4, EV_JACKPOT = 8, EV_BONUS = 16, EV_BONUS_END = 32, EV_RETRIGGER = 64,
            EV_EXPAND = 128, EV_COLLAPSE = 256, EV_MAXWIN = 512, EV_LAST = 1024, EV_UPGRADE = 2048, EV_DIRECT = 4096, EV_PRIZE = 8192;
    /** Bonus : tours gratuits (6 chapeaux), scies, mega chapeau, manoir, et leurs versions SUPER (grille 5 x 6). */
    public static final int B_HATS = 1, B_BUZZ = 2, B_MEGA = 3, B_MANSION = 4, B_SUPER_BUZZ = 5, B_SUPER_MEGA = 6, B_SUPER_MANSION = 7;
    /** Issues des roues : jackpots, puis bonus de la roue, SUPER ROUE, bonus de la super roue. */
    public static final int J_MINI = 0, J_MINOR = 1, J_MAJOR = 2, J_GRAND = 3, J_SUPER = 4, W_BUZZ = 5, W_MEGA = 6, W_MANSION = 7, W_UPGRADE = 8,
            S_BUZZ = 9, S_MEGA = 10, S_MANSION = 11;
    /** Les 12 cases de la roue et de la super roue (dans l'ordre, sens horaire depuis le haut). */
    public static final int[] WHEEL = {W_BUZZ, J_MINI, W_MEGA, J_MINOR, W_MANSION, J_MINI, W_BUZZ, W_UPGRADE, W_MEGA, J_MAJOR, W_MANSION, J_GRAND};
    public static final int[] SUPER_WHEEL = {S_BUZZ, J_MAJOR, S_MEGA, J_GRAND, S_MANSION, J_MAJOR, S_BUZZ, J_SUPER, S_MEGA, J_MAJOR, S_MANSION, J_GRAND};
    /** Ordre des poids des roues dans les reglages. */
    public static final int[] WHEEL_OUT = {J_MINI, J_MINOR, J_MAJOR, J_GRAND, W_BUZZ, W_MEGA, W_MANSION, W_UPGRADE};
    public static final int[] SUPER_OUT = {J_MAJOR, J_GRAND, J_SUPER, S_BUZZ, S_MEGA, S_MANSION};
    /** Gain d'une maison : 0-7 jetons (indice dans les valeurs de son etage), jackpot, ou un nouveau tour de roue. */
    public static final int R_MINI = 8, R_MINI2 = 9, R_MINOR = 10, R_MAJOR = 11, R_GRAND = 12, R_WHEEL = 13;
    /** Chapeau tombe sur une brique alors que tout est en brique : il rapporte une prime au lieu de construire. */
    public static final int PRIZE = 31;
    public static final int BUY_WHEEL = 1;
    public static final int MAX_SPINS = 120, MAX_WHEELS = 3, MAX_MEGAS = 6;

    public static int cell(int reel, int row) {
        return reel * ROWS + row;
    }

    /** Case de la grille des bonus (rangee 0 a 5, les rangees 3 a 5 sont celles du jeu normal). */
    public static int bcell(int reel, int row) {
        return reel * BROWS + row;
    }

    /** Case du jeu normal vers la grille des bonus. */
    public static int fromBase(int c) {
        return bcell(c / ROWS, c % ROWS + TOP);
    }

    public static boolean isSuper(int bonus) {
        return bonus >= B_SUPER_BUZZ;
    }

    public static boolean isMansion(int bonus) {
        return bonus == B_MANSION || bonus == B_SUPER_MANSION;
    }

    /** Premiere rangee de la grille des bonus en jeu (0 en SUPER, 3 sinon). */
    public static int firstRow(int bonus) {
        return isSuper(bonus) ? 0 : TOP;
    }

    public static boolean isJackpot(int out) {
        return out >= J_MINI && out <= J_SUPER;
    }

    /** Bonus gagne a la roue. */
    public static int featureOf(int out) {
        return switch (out) {
            case W_BUZZ -> B_BUZZ;
            case W_MEGA -> B_MEGA;
            case W_MANSION -> B_MANSION;
            case S_BUZZ -> B_SUPER_BUZZ;
            case S_MEGA -> B_SUPER_MEGA;
            default -> B_SUPER_MANSION;
        };
    }

    // ---- mega chapeaux : rectangles (rouleau, rangee de la grille des bonus, largeur, hauteur) ranges sur 12 bits
    public static int mega(int x, int y, int w, int h) {
        return x | y << 3 | w << 6 | h << 9;
    }

    public static int megaX(int m) {
        return m & 7;
    }

    public static int megaY(int m) {
        return (m >> 3) & 7;
    }

    public static int megaW(int m) {
        return (m >> 6) & 7;
    }

    public static int megaH(int m) {
        return (m >> 9) & 7;
    }

    public static final class Spin {
        public int kind, events;
        /** Bonus en jeu (mise en place, tours gratuits) ou gagne (tour normal, tour de roue). */
        public int bonus;
        /** Tour normal : symboles. */
        public final int[] grid = new int[CELLS];
        /** Cadres apres cette etape (0 vide, 1 paille, 2 bois, 3 brique), sur la grille des bonus. */
        public final int[] tier = new int[BCELLS];
        /** Fin du bonus : gain de chaque maison. */
        public final int[] reward = new int[BCELLS];
        /** Tour gratuit : cases ou un chapeau tombe, et case ou il construit (lui-meme, une autre, ou PRIZE). */
        public int hats;
        public final int[] hatTo = new int[BCELLS];
        /** Primes de chapeaux (tout etait deja en brique). */
        public int prizes;
        /** Tour gratuit : colonnes qui tournent au ralenti (il ne manque qu'un chapeau pour un tour de plus). */
        public int teaseMask;
        /** Mise en place : scies bleues, scies d'or, copies dans les rangees du haut (SUPER), mega chapeaux. */
        public int saws, golds, copies;
        public int[] megas = new int[0];
        public int spinsLeft;
        /** Roue : case d'arret (-1 aucune), case de la super roue, jackpot gagne (-1). */
        public int wheelSeg = -1, superSeg = -1, jackpot = -1;
        public int winMilli, lineMilli, jackpotMilli;

        public int count(int sym) {
            int n = 0;
            for (int g : grid) if (g == sym) n++;
            return n;
        }

        public int houses() {
            int n = 0;
            for (int t : tier) if (t > 0) n++;
            return n;
        }

        public boolean hat(int c) {
            return (hats & (1 << c)) != 0;
        }
    }

    public static final class Sequence {
        public final List<Spin> spins = new ArrayList<>();
        public long totalMilli;
        public int buy;
    }

    // =================================================================== gains
    /** Valeur d'une maison (x la mise, millemes) : jetons ou jackpot ; un tour de roue ne vaut rien par lui-meme. */
    public static long rewardMilli(LoupRules r, int tier, int reward) {
        if (tier <= 0) return 0;
        if (reward >= R_MINI) {
            return switch (reward) {
                case R_MINI -> jackpotMilli(r, J_MINI);
                case R_MINI2 -> 2 * jackpotMilli(r, J_MINI);
                case R_MINOR -> jackpotMilli(r, J_MINOR);
                case R_MAJOR -> jackpotMilli(r, J_MAJOR);
                case R_GRAND -> jackpotMilli(r, J_GRAND);
                default -> 0;
            };
        }
        double[] v = values(r, tier);
        return Math.round(v[Math.max(0, Math.min(v.length - 1, reward))] * r.payScale * 1000);
    }

    public static double[] values(LoupRules r, int tier) {
        return tier <= 1 ? r.strawValues : tier == 2 ? r.stickValues : r.brickValues;
    }

    public static long jackpotMilli(LoupRules r, int j) {
        return Math.round(r.jackpots[Math.max(0, Math.min(r.jackpots.length - 1, j))] * r.payScale * 1000);
    }

    public static long prizeMilli(LoupRules r) {
        return Math.round(r.prize * r.payScale * 1000);
    }

    /** Gain des 243 facons (x la mise) ; cells recoit le masque des cases gagnantes. */
    public static double ways(LoupRules r, int[] grid, int[] cells) {
        double total = 0;
        int mask = 0;
        for (int s = 0; s < 8; s++) {
            long ways = 1;
            int len = 0, m = 0;
            for (int reel = 0; reel < REELS; reel++) {
                int n = 0;
                for (int row = 0; row < ROWS; row++) {
                    int g = grid[cell(reel, row)];
                    if (g == s || g == WOLF) {
                        n++;
                        m |= 1 << cell(reel, row);
                    }
                }
                if (n == 0) break;
                ways *= n;
                len++;
            }
            if (len < 3) continue;
            total += r.pays[s][len - 3] * ways * r.payScale;
            mask |= m;
        }
        if (cells != null) cells[0] = mask;
        return total;
    }

    // =================================================================== tirage
    private static final class Game {
        final LoupRules r;
        final RandomGenerator rng;
        final Sequence seq = new Sequence();
        final long max;
        boolean capped;
        int wheelBudget = MAX_WHEELS;

        Game(LoupRules r, RandomGenerator rng) {
            this.r = r;
            this.rng = rng;
            this.max = Math.round(r.maxWin * 1000);
        }

        /** Gain d'une etape ; au gain maximum, la partie s'arrete la. */
        void win(Spin s, long milli) {
            if (milli <= 0 || capped) return;
            long room = max - seq.totalMilli;
            long add = Math.min(milli, room);
            s.winMilli = (int) Math.min(Integer.MAX_VALUE / 4, s.winMilli + add);
            seq.totalMilli += add;
            if (seq.totalMilli >= max) {
                capped = true;
                s.events |= EV_MAXWIN;
            }
        }

        boolean room() {
            return !capped && seq.spins.size() < MAX_SPINS;
        }
    }

    public static Sequence play(LoupRules r, RandomGenerator rng, int buy) {
        Game g = new Game(r, rng);
        g.seq.buy = buy;
        Spin first = baseSpin(r, rng, buy == BUY_WHEEL);
        g.seq.spins.add(first);
        g.win(first, first.lineMilli);
        int bonus = 0;
        if (first.count(HAT) >= 6) {
            first.events |= EV_HATS;
            bonus = B_HATS;
        }
        int saws = first.count(SAW), golds = first.count(GOLD);
        if (!g.capped && saws + golds >= 3) {
            int out = spinWheel(g, first, golds > 0 && saws >= 2);
            if (isJackpot(out)) jackpot(g, first, out);
            else bonus = featureOf(out);
        }
        if (bonus != 0 && g.room()) {
            first.events |= EV_BONUS;
            first.bonus = bonus;
            feature(g, first, bonus);
        }
        return g.seq;
    }

    /** Tourne la roue (ou directement la super roue) ; rend l'issue finale. */
    private static int spinWheel(Game g, Spin s, boolean direct) {
        int out;
        if (direct) {
            s.events |= EV_SUPER | EV_DIRECT;
        } else {
            s.events |= EV_WHEEL;
            out = WHEEL_OUT[Rotten.pick(g.r.wheel, g.rng)];
            s.wheelSeg = segment(WHEEL, out, g.rng);
            if (out != W_UPGRADE) return out;
            s.events |= EV_SUPER | EV_UPGRADE;
        }
        out = SUPER_OUT[Rotten.pick(g.r.superWheel, g.rng)];
        s.superSeg = segment(SUPER_WHEEL, out, g.rng);
        return out;
    }

    private static void jackpot(Game g, Spin s, int out) {
        s.jackpot = out;
        s.jackpotMilli = (int) jackpotMilli(g.r, out);
        s.events |= EV_JACKPOT;
        g.win(s, s.jackpotMilli);
    }

    /** Case de la roue ou tombe l'issue "out" (au hasard parmi les cases de ce type). */
    private static int segment(int[] wheel, int out, RandomGenerator rng) {
        int n = 0;
        for (int w : wheel) if (w == out) n++;
        int k = rng.nextInt(Math.max(1, n));
        for (int i = 0; i < wheel.length; i++) if (wheel[i] == out && k-- == 0) return i;
        return 0;
    }

    private static Spin baseSpin(LoupRules r, RandomGenerator rng, boolean forceSaws) {
        Spin s = new Spin();
        s.kind = BASE;
        for (int reel = 0; reel < REELS; reel++) {
            for (int row = 0; row < ROWS; row++) {
                s.grid[cell(reel, row)] = rng.nextDouble() < r.hat ? HAT : Rotten.pick(r.weights[reel], rng);
            }
        }
        // achat de la roue : 3 rouleaux tires au hasard portent une scie
        int forced = 0;
        while (forceSaws && Integer.bitCount(forced) < 3) forced |= 1 << rng.nextInt(REELS);
        // une scie au plus par rouleau, une seule scie d'or par tour
        boolean gold = false;
        for (int reel = 0; reel < REELS; reel++) {
            if ((forced & (1 << reel)) == 0 && rng.nextDouble() >= r.saw) continue;
            boolean g = !gold && rng.nextDouble() < r.gold;
            gold |= g;
            s.grid[cell(reel, rng.nextInt(ROWS))] = g ? GOLD : SAW;
        }
        s.lineMilli = (int) Math.round(ways(r, s.grid, null) * 1000);
        return s;
    }

    /** Lance un bonus : mise en place (grille agrandie en SUPER), puis les tours gratuits et le souffle final. */
    private static void feature(Game g, Spin prev, int type) {
        LoupRules r = g.r;
        RandomGenerator rng = g.rng;
        boolean sup = isSuper(type);
        Spin set = new Spin();
        set.kind = SETUP;
        set.bonus = type;
        Arrays.fill(set.grid, EMPTY);
        if (sup) set.events |= EV_EXPAND;
        // les chapeaux declencheurs (6 ou plus) gardent leur cadre, quel que soit le bonus
        if (prev.kind == BASE && (prev.events & EV_HATS) != 0) {
            for (int c = 0; c < CELLS; c++) if (prev.grid[c] == HAT) set.hats |= 1 << fromBase(c);
        }
        if (type != B_HATS && type != B_MEGA && type != B_SUPER_MEGA) {
            // les scies qui ont declenche la roue ; sans elles (roue d'une maison) : 3 a 5 scies au hasard
            if (prev.kind == BASE) {
                for (int c = 0; c < CELLS; c++) {
                    if (prev.grid[c] == SAW) set.saws |= 1 << fromBase(c);
                    if (prev.grid[c] == GOLD) set.golds |= 1 << fromBase(c);
                }
            }
            if ((set.saws | set.golds) == 0) {
                int n = 3 + rng.nextInt(3);
                while (Integer.bitCount(set.saws) < n) set.saws |= 1 << bcell(rng.nextInt(REELS), TOP + rng.nextInt(ROWS));
            }
            if (sup) set.copies = up(set.saws | set.golds);
        }
        if (type == B_MEGA || type == B_SUPER_MEGA) set.megas = megaHats(rng, sup);
        apply(plan(set).hits, set.tier, Integer.MAX_VALUE);
        set.spinsLeft = r.bonusSpins;
        g.seq.spins.add(set);
        free(g, set);
    }

    /** Copie des scies dans les rangees du haut (bonus SUPER). */
    static int up(int mask) {
        int out = 0;
        for (int c = 0; c < BCELLS; c++) if ((mask & (1 << c)) != 0 && c % BROWS >= TOP) out |= 1 << (c - TOP);
        return out;
    }

    /** Mega chapeaux de 2 x 2, 3 x 3 ou 5 x 3 (et 3 x 5 sur la grille agrandie), entiers et sans se chevaucher. */
    private static int[] megaHats(RandomGenerator rng, boolean sup) {
        int[][] sizes = sup ? new int[][]{{2, 2}, {3, 3}, {5, 3}, {3, 5}} : new int[][]{{2, 2}, {3, 3}, {5, 3}};
        int[] sizeW = sup ? new int[]{44, 32, 12, 12} : new int[]{58, 32, 10};
        int count = 1 + Rotten.pick(sup ? new int[]{10, 35, 35, 20} : new int[]{55, 33, 12}, rng);
        int lo = sup ? 0 : TOP;
        long used = 0;
        List<Integer> out = new ArrayList<>();
        for (int k = 0; k < count && out.size() < MAX_MEGAS; k++) {
            for (int attempt = 0; attempt < 40; attempt++) {
                int[] sz = sizes[Rotten.pick(sizeW, rng)];
                int w = sz[0], h = sz[1];
                if (h > BROWS - lo) continue;
                int x = rng.nextInt(REELS - w + 1), y = lo + rng.nextInt(BROWS - lo - h + 1);
                long m = 0;
                for (int dx = 0; dx < w; dx++) for (int dy = 0; dy < h; dy++) m |= 1L << bcell(x + dx, y + dy);
                if ((m & used) != 0) continue;
                used |= m;
                out.add(mega(x, y, w, h));
                break;
            }
        }
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** Tours gratuits : les chapeaux construisent et ameliorent les cadres, puis le loup souffle les maisons. */
    private static void free(Game g, Spin set) {
        LoupRules r = g.r;
        RandomGenerator rng = g.rng;
        int type = set.bonus;
        boolean mansion = isMansion(type);
        int lo = firstRow(type);
        int[] tier = set.tier.clone();
        double rate = mansion ? r.mansionHat : r.bonusHat;
        int left = r.bonusSpins;
        Spin last = null;
        while (left > 0 && g.room()) {
            left--;
            Spin s = new Spin();
            s.kind = FREE;
            s.bonus = type;
            Arrays.fill(s.grid, EMPTY);
            if (left == 0) s.events |= EV_LAST;
            int count = 0;
            for (int reel = 0; reel < REELS; reel++) {
                for (int row = lo; row < BROWS; row++) {
                    int c = bcell(reel, row);
                    if (rng.nextDouble() >= rate) continue;
                    s.hats |= 1 << c;
                    count++;
                    // sur une brique, le chapeau va construire ailleurs ; tout est en brique : une prime
                    int to = tier[c] >= 3 ? elsewhere(tier, lo, rng) : c;
                    s.hatTo[c] = to;
                    if (to == PRIZE) s.prizes++;
                    else tier[to] = mansion ? 3 : Math.min(3, tier[to] + 1);
                }
            }
            if (count >= r.retriggerHats && r.retriggerSpins > 0) {
                left += r.retriggerSpins;
                s.events |= EV_RETRIGGER;
            }
            // suspense : des qu'il ne manque plus qu'un chapeau pour un tour de plus, les colonnes suivantes ralentissent
            if (r.retriggerSpins > 0 && r.retriggerHats > 1) {
                int seen = 0;
                for (int reel = 0; reel < REELS; reel++) {
                    if (reel > 0 && seen == r.retriggerHats - 1) s.teaseMask |= 1 << reel;
                    for (int row = lo; row < BROWS; row++) if (s.hat(bcell(reel, row))) seen++;
                }
            }
            s.spinsLeft = left;
            System.arraycopy(tier, 0, s.tier, 0, BCELLS);
            g.seq.spins.add(s);
            if (s.prizes > 0) {
                s.events |= EV_PRIZE;
                g.win(s, s.prizes * prizeMilli(r));
            }
            last = s;
        }
        if (last == null || g.capped) return;
        last.events |= EV_BONUS_END;
        if (isSuper(type)) last.events |= EV_COLLAPSE;
        long total = 0;
        int wheels = 0;
        for (int c = 0; c < BCELLS; c++) {
            if (tier[c] <= 0) continue;
            int rw = reward(g, tier[c]);
            last.reward[c] = rw;
            if (rw == R_WHEEL) wheels++;
            total += rewardMilli(r, tier[c], rw);
        }
        g.win(last, total);
        // chaque maison de brique qui cachait la roue : un nouveau tour de roue
        for (int k = 0; k < wheels && g.room(); k++) houseWheel(g);
    }

    /** Case qui n'est pas encore en brique (au hasard), ou PRIZE si tout est en brique. */
    private static int elsewhere(int[] tier, int lo, RandomGenerator rng) {
        int free = 0;
        for (int c = 0; c < BCELLS; c++) if (c % BROWS >= lo && tier[c] < 3) free++;
        if (free == 0) return PRIZE;
        int pick = rng.nextInt(free);
        for (int c = 0; c < BCELLS; c++) if (c % BROWS >= lo && tier[c] < 3 && pick-- == 0) return c;
        return PRIZE;
    }

    /** Gain d'une maison : jetons selon son etage ; un bois peut cacher un jackpot, une brique un jackpot ou la roue. */
    private static int reward(Game g, int tier) {
        LoupRules r = g.r;
        RandomGenerator rng = g.rng;
        if (tier == 1) return Rotten.pick(r.strawWeights, rng);
        if (tier == 2) {
            if (rng.nextDouble() < r.stickJackpot) return R_MINI + Rotten.pick(r.stickJackpots, rng);
            return Rotten.pick(r.stickWeights, rng);
        }
        if (rng.nextDouble() < r.brickSpecial) {
            int[] w = r.brickSpecials.clone();
            if (g.wheelBudget <= 0) w[5] = 0;
            int sum = 0;
            for (int x : w) sum += x;
            if (sum > 0) {
                int k = Rotten.pick(w, rng);
                if (k == 5) {
                    g.wheelBudget--;
                    return R_WHEEL;
                }
                return R_MINI + k;
            }
        }
        return Rotten.pick(r.brickWeights, rng);
    }

    /** Tour de roue gagne par une maison de brique : jackpot, ou un nouveau bonus sur une grille vide. */
    private static void houseWheel(Game g) {
        Spin w = new Spin();
        w.kind = SPIN_WHEEL;
        Arrays.fill(w.grid, EMPTY);
        g.seq.spins.add(w);
        int out = spinWheel(g, w, false);
        if (isJackpot(out)) {
            jackpot(g, w, out);
        } else if (g.room()) {
            w.events |= EV_BONUS;
            w.bonus = featureOf(out);
            feature(g, w, w.bonus);
        }
    }

    // =================================================================== mise en place d'un bonus
    /** Deroule de la mise en place : chaque coup sur un cadre (instant depuis le debut de l'action, case, brique directe). */
    public static final class Plan {
        /** {instant, case, 1 = brique directe (manoir) / 0 = un etage de plus}. */
        public final List<int[]> hits = new ArrayList<>();
        /** Scies : {case de depart, instant du depart, 1 = or, 1 = copie}. */
        public final List<int[]> saws = new ArrayList<>();
        /** Fin de la pose des cadres des chapeaux declencheurs ; apparition des scies ; fin de l'action. */
        public int formEnd, sawsShown = -1, end;
        /** Instant ou chaque mega chapeau commence sa chute. */
        public int[] megaAt = new int[0];
    }

    public static final int FORM_STEP = 3, FORM_TAIL = 14, CONVERT_T = 10;
    public static final int SAW_INTRO = 16, SAW_COPY = 20, SAW_GAP = 10, SAW_STEP = 4, SAW_TAIL = 12;
    public static final int MEGA_GAP = 18, MEGA_FALL = 12, MEGA_TAIL = 22, BRICK_STEP = 6, BRICK_TAIL = 14;

    /** Ce que fait une mise en place (temps depuis le debut de l'action, apres l'agrandissement de la grille). */
    public static Plan plan(Spin set) {
        Plan p = new Plan();
        boolean mansion = isMansion(set.bonus);
        int t = 4;
        // les chapeaux declencheurs deviennent des cadres de paille
        int formed = set.hats;
        if (formed != 0) {
            int k = 0;
            for (int c = 0; c < BCELLS; c++) if ((formed & (1 << c)) != 0) p.hits.add(new int[]{t + k++ * FORM_STEP, c, 0});
            t += k * FORM_STEP + FORM_TAIL;
        }
        p.formEnd = t;
        // au manoir, tout est en brique : les cadres deja poses aussi
        if (mansion && formed != 0) {
            for (int c = 0; c < BCELLS; c++) if ((formed & (1 << c)) != 0) p.hits.add(new int[]{t + 2, c, 1});
            t += CONVERT_T;
        }
        switch (set.bonus) {
            case B_MANSION -> {
                // les scies deviennent des maisons de brique
                p.sawsShown = t;
                int k = 0;
                for (int c = 0; c < BCELLS; c++) {
                    if (((set.saws | set.golds) & (1 << c)) == 0) continue;
                    int at = t + SAW_INTRO + k++ * BRICK_STEP;
                    p.hits.add(new int[]{at, c, 1});
                    // la scie tourne sur place jusqu'a devenir une brique
                    p.saws.add(new int[]{c, at, (set.golds & (1 << c)) != 0 ? 1 : 0, 0});
                }
                t += SAW_INTRO + k * BRICK_STEP + BRICK_TAIL;
            }
            case B_BUZZ, B_SUPER_BUZZ, B_SUPER_MANSION -> {
                // les scies filent vers la droite, de la colonne la plus a droite a la plus a gauche
                p.sawsShown = t;
                int all = set.saws | set.golds | set.copies;
                int go = t + SAW_INTRO + (set.copies != 0 ? SAW_COPY : 0), j = 0, last = go;
                for (int reel = REELS - 1; reel >= 0; reel--) {
                    boolean any = false;
                    for (int row = 0; row < BROWS; row++) {
                        int c = bcell(reel, row);
                        if ((all & (1 << c)) == 0) continue;
                        any = true;
                        int start = go + j * SAW_GAP;
                        boolean copy = (set.copies & (1 << c)) != 0;
                        boolean gold = copy ? (set.golds & (1 << (c + TOP))) != 0 : (set.golds & (1 << c)) != 0;
                        p.saws.add(new int[]{c, start, gold ? 1 : 0, copy ? 1 : 0});
                        for (int x = reel; x < REELS; x++) p.hits.add(new int[]{start + (x - reel) * SAW_STEP + 2, bcell(x, row), mansion ? 1 : 0});
                        last = Math.max(last, start + (REELS - reel) * SAW_STEP + 6);
                    }
                    if (any) j++;
                }
                t = last + SAW_TAIL;
            }
            case B_MEGA, B_SUPER_MEGA -> {
                // les mega chapeaux tombent un a un, chaque case couverte devient un cadre de paille
                p.megaAt = new int[set.megas.length];
                int last = t;
                for (int k = 0; k < set.megas.length; k++) {
                    int m = set.megas[k], drop = t + 8 + k * MEGA_GAP, land = drop + MEGA_FALL;
                    p.megaAt[k] = drop;
                    for (int dx = 0; dx < megaW(m); dx++) {
                        for (int dy = 0; dy < megaH(m); dy++) p.hits.add(new int[]{land + 1 + dx, bcell(megaX(m) + dx, megaY(m) + dy), 0});
                    }
                    last = land;
                }
                t = last + MEGA_TAIL;
            }
            default -> t += 6;
        }
        p.end = t;
        return p;
    }

    /** Applique les coups portes avant l'instant "until" (l'ordre ne change rien au resultat). */
    public static void apply(List<int[]> hits, int[] tier, float until) {
        for (int[] h : hits) {
            if (h[0] > until) continue;
            tier[h[1]] = h[2] == 1 ? 3 : Math.min(3, tier[h[1]] + 1);
        }
    }

    // =================================================================== suspense
    /**
     * Le rouleau tourne au ralenti : il ne manque plus qu'une scie pour la roue, ou un ou deux chapeaux pour les tours
     * gratuits.
     */
    public static boolean tease(Spin s, int reel) {
        if (s.kind != BASE || reel <= 0) return false;
        int saws = 0, hats = 0;
        for (int c = 0; c < reel * ROWS; c++) {
            if (s.grid[c] == SAW || s.grid[c] == GOLD) saws++;
            if (s.grid[c] == HAT) hats++;
        }
        return saws == 2 || hats == 4 || hats == 5;
    }

    /** Symbole que le rouleau en suspens fait esperer (la SCIE pour la roue, sinon le CHAPEAU). */
    public static int teaseSymbol(Spin s, int reel) {
        int saws = 0;
        for (int c = 0; c < reel * ROWS; c++) if (s.grid[c] == SAW || s.grid[c] == GOLD) saws++;
        return saws == 2 ? SAW : HAT;
    }

    // =================================================================== chronologie (ticks)
    /** Arret d'un rouleau sans suspense (tour normal) : le premier apres 0,9 s, puis un toutes les 0,3 s. */
    public static int plainStop(int reel) {
        return 18 + 6 * reel;
    }

    /** Arret du rouleau "reel" : chaque rouleau en suspens tourne Suspense.HOLD ticks de plus, au ralenti. */
    public static int stopTick(Spin s, int reel) {
        int t = plainStop(reel);
        for (int k = 1; k <= reel; k++) if (tease(s, k)) t += Suspense.HOLD;
        return t;
    }

    /** Fin de la rotation des rouleaux (tour normal). */
    public static int spinEnd(Spin s) {
        return stopTick(s, REELS - 1) + 2;
    }

    /** La roue : rotation (de WHEEL_GO a WHEEL_STOP), puis la case gagnante ; la super roue a d'abord sa presentation. */
    public static final int WHEEL_T = 150, WHEEL_GO = 12, WHEEL_STOP = 118, SUPER_INTRO = 56;
    public static final int JACKPOT_T = 70, BONUS_INTRO = 50, HOUSE_INTRO = 34;
    /** Mise en place : agrandissement de la grille (SUPER), annonce des tours gratuits. */
    public static final int EXPAND_T = 84, FS_INTRO = 36;
    /**
     * Tours gratuits : rebond d'un chapeau vers une autre case, relance, dernier tour, retrecissement de la grille ; les
     * colonnes de chapeaux tournent comme des rouleaux : arret de la premiere, ecart entre deux colonnes, le chapeau
     * reste pose un instant avant de construire, attente apres la derniere colonne.
     */
    public static final int HOP = 10, RETRIG = 28, LAST_T = 20, COLLAPSE_T = 40;
    public static final int FREE_FIRST = 16, FREE_STEP = 7, LAND_HIT = 4, FREE_SETTLE = 14;
    /** Fin du bonus : les cadres deviennent des maisons, le loup inspire (BLOW_WIND) puis les souffle une a une. */
    public static final int BUILD_STEP = 3, BUILD_PAD = 14, BLOW_WIND = 24, BLOW_TAIL = 30, REVEAL_JACK = 16;
    private static final int[] REVEAL_STEP = {5, 7, 11};

    /** Affichage du gain des facons (tour normal) ; un tour perdu reste un instant a l'ecran. */
    public static int lineShow(Spin s) {
        if (s.lineMilli <= 0) return 12;
        return Rotten.bigTier(s.lineMilli) > 0 ? Rotten.BIG_WIN_TICKS : 32;
    }

    /** Debut des roues d'une etape (apres les rouleaux, ou apres l'annonce pour la roue d'une maison). */
    public static int wheelStart(Spin s) {
        return s.kind == SPIN_WHEEL ? HOUSE_INTRO : spinEnd(s) + lineShow(s);
    }

    public static int wheelTime(Spin s) {
        return (s.wheelSeg >= 0 ? WHEEL_T : 0) + (s.superSeg >= 0 ? SUPER_INTRO + WHEEL_T : 0);
    }

    /** Debut de la presentation de la super roue. */
    public static int superStart(Spin s) {
        return wheelStart(s) + (s.wheelSeg >= 0 ? WHEEL_T : 0);
    }

    public static int jackpotStart(Spin s) {
        return wheelStart(s) + wheelTime(s);
    }

    public static int introStart(Spin s) {
        return jackpotStart(s) + ((s.events & EV_JACKPOT) != 0 ? JACKPOT_T : 0);
    }

    /** Mise en place : debut de l'action (apres l'agrandissement de la grille). */
    public static int actionStart(Spin s) {
        return (s.events & EV_EXPAND) != 0 ? EXPAND_T : 0;
    }

    /** Debut de la rotation des colonnes de chapeaux d'un tour gratuit (apres l'annonce du dernier tour). */
    public static int hatStart(Spin s) {
        return (s.events & EV_LAST) != 0 ? LAST_T : 0;
    }

    /** Tour gratuit : la colonne "reel" tourne au ralenti (il ne manque qu'un chapeau pour un tour de plus). */
    public static boolean freeTease(Spin s, int reel) {
        return s.kind == FREE && (s.teaseMask & (1 << reel)) != 0;
    }

    /** Arret de la colonne "reel" d'un tour gratuit : ses chapeaux se posent alors (les autres sont passes en tombant). */
    public static int freeStop(Spin s, int reel) {
        int t = hatStart(s) + FREE_FIRST + FREE_STEP * reel;
        for (int k = 1; k <= reel; k++) if (freeTease(s, k)) t += Suspense.HOLD_SHORT;
        return t;
    }

    /** Instant ou le chapeau de la case c se pose (arret de sa colonne). */
    public static int landAt(Spin s, int c) {
        return freeStop(s, c / BROWS);
    }

    /** Premier chapeau qui se change en prime (tout etait en brique), -1 aucun. */
    public static int prizeAt(Spin s) {
        int t = -1;
        for (int c = 0; c < BCELLS; c++) if (s.hat(c) && s.hatTo[c] == PRIZE) t = t < 0 ? landAt(s, c) : Math.min(t, landAt(s, c));
        return t;
    }

    /** Un chapeau tombe sur une brique et rebondit ailleurs (ou se change en prime). */
    public static boolean redirects(Spin s) {
        for (int c = 0; c < BCELLS; c++) if (s.hat(c) && s.hatTo[c] != c) return true;
        return false;
    }

    /** Duree d'un tour gratuit : les colonnes tournent et s'arretent une a une, les chapeaux construisent. */
    public static int bonusSpin(Spin s) {
        return freeStop(s, REELS - 1) + LAND_HIT + (redirects(s) ? HOP : 0) + FREE_SETTLE;
    }

    /** Coups portes par les chapeaux d'un tour gratuit : {instant, case, brique directe}. */
    public static List<int[]> freeHits(Spin s) {
        List<int[]> out = new ArrayList<>();
        boolean mansion = isMansion(s.bonus);
        for (int c = 0; c < BCELLS; c++) {
            if (!s.hat(c) || s.hatTo[c] == PRIZE) continue;
            out.add(new int[]{landAt(s, c) + LAND_HIT + (s.hatTo[c] != c ? HOP : 0), s.hatTo[c], mansion ? 1 : 0});
        }
        return out;
    }

    public static int buildStart(Spin s) {
        return bonusSpin(s) + ((s.events & EV_RETRIGGER) != 0 ? RETRIG : 0);
    }

    /** Fin du bonus : instant (depuis buildStart) ou chaque cadre devient une maison, -1 sans cadre ; paille d'abord. */
    public static int[] buildTimes(Spin s) {
        int[] out = new int[BCELLS];
        Arrays.fill(out, -1);
        int t = BUILD_PAD / 2;
        for (int tier = 1; tier <= 3; tier++) {
            for (int c = 0; c < BCELLS; c++) {
                if (s.tier[c] != tier) continue;
                out[c] = t;
                t += BUILD_STEP;
            }
        }
        return out;
    }

    public static int build(Spin s) {
        return BUILD_PAD + s.houses() * BUILD_STEP + 6;
    }

    public static int blowStart(Spin s) {
        return buildStart(s) + build(s);
    }

    /**
     * Souffle final : instant (depuis blowStart) ou la maison de chaque case s'envole et revele son gain, -1 sans
     * maison. La paille part la premiere, la brique en dernier, de gauche a droite ; un jackpot laisse plus de temps.
     */
    public static int[] revealTimes(Spin s) {
        int[] out = new int[BCELLS];
        Arrays.fill(out, -1);
        int t = BLOW_WIND;
        for (int tier = 1; tier <= 3; tier++) {
            for (int c = 0; c < BCELLS; c++) {
                if (s.tier[c] != tier) continue;
                out[c] = t;
                t += REVEAL_STEP[tier - 1] + (s.reward[c] >= R_MINI ? REVEAL_JACK : 0);
            }
        }
        return out;
    }

    /** Duree du souffle final. */
    public static int blow(Spin s) {
        int t = BLOW_WIND;
        for (int c = 0; c < BCELLS; c++) if (s.tier[c] > 0) t += REVEAL_STEP[s.tier[c] - 1] + (s.reward[c] >= R_MINI ? REVEAL_JACK : 0);
        return t + BLOW_TAIL;
    }

    public static int endStart(Spin s) {
        return blowStart(s) + blow(s);
    }

    /** Affichage du gain final du bonus. */
    public static int endShow(Spin s) {
        return Rotten.bigTier(s.winMilli) > 0 ? Rotten.BIG_WIN_TICKS : 30;
    }

    /** Fin d'un bonus SUPER : la grille reprend sa taille. */
    public static int collapseStart(Spin s) {
        return endStart(s) + endShow(s);
    }

    public static int duration(Spin s) {
        int t;
        switch (s.kind) {
            case FREE -> {
                t = bonusSpin(s);
                if ((s.events & EV_RETRIGGER) != 0) t += RETRIG;
                if ((s.events & EV_BONUS_END) != 0) {
                    t += build(s) + blow(s) + endShow(s);
                    if ((s.events & EV_COLLAPSE) != 0) t += COLLAPSE_T;
                }
            }
            case SETUP -> t = actionStart(s) + plan(s).end + FS_INTRO;
            default -> {
                t = wheelStart(s) + wheelTime(s);
                if ((s.events & EV_JACKPOT) != 0) t += JACKPOT_T;
                if ((s.events & EV_BONUS) != 0) t += BONUS_INTRO;
            }
        }
        if ((s.events & EV_MAXWIN) != 0) t += Rotten.BIG_WIN_TICKS;
        return t;
    }

    public static int totalDuration(Sequence seq) {
        int t = 0;
        for (Spin s : seq.spins) t += duration(s);
        return t;
    }

    /** Etape en cours a l'instant t : {indice, temps local}, ou null. */
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
        if (s.kind == FREE || s.kind == SETUP) return new SlotEngine.Status(2, 1, 0, 0, s.spinsLeft);
        return SlotEngine.Status.NONE;
    }

    /** A l'instant t (ticks depuis le lancement), un rouleau tourne-t-il au ralenti (la borne affiche son annonce) ? */
    public static boolean teasing(Sequence seq, float t) {
        float[] at = at(seq, t);
        if (at == null) return false;
        Spin s = seq.spins.get((int) at[0]);
        float lt = at[1];
        for (int reel = 1; reel < REELS; reel++) {
            if (tease(s, reel) && lt >= stopTick(s, reel - 1) && lt < stopTick(s, reel) + 8) return true;
            if (freeTease(s, reel) && lt >= freeStop(s, reel - 1) && lt < freeStop(s, reel) + 8) return true;
        }
        return false;
    }

    /**
     * Grand moment de mise en scene (presentation et tour de la super roue, agrandissement de la grille) : l'ecran de
     * jeu s'efface pour laisser toute la place a la borne.
     */
    public static boolean cinematic(Sequence seq, float t) {
        float[] at = at(seq, t);
        if (at == null) return false;
        Spin s = seq.spins.get((int) at[0]);
        float lt = at[1];
        if (s.kind == SETUP) return (s.events & EV_EXPAND) != 0 && lt < EXPAND_T + 6;
        if (s.kind == FREE || s.superSeg < 0) return false;
        int st = superStart(s);
        return lt >= st && lt < st + SUPER_INTRO + WHEEL_T;
    }

    public static SlotEngine engine(LoupRules r) {
        return new SlotEngine() {
            @Override
            public Play play(RandomGenerator rng, int buy) {
                Sequence seq = Loup.play(r, rng, buy);
                return new Play(encode(seq), seq.totalMilli, totalDuration(seq));
            }

            @Override
            public double buyPrice(int buy) {
                return buy == BUY_WHEEL ? r.buyWheel : 0;
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
    private static final int STRIDE = 25;
    /** Format des donnees (les parties sauvegardees par une ancienne version sont ignorees). */
    private static final int FORMAT = 4;

    public static int[] encode(Sequence seq) {
        int[] out = new int[2 + seq.spins.size() * STRIDE];
        out[0] = (seq.buy & 255) | FORMAT << 8;
        out[1] = (int) Math.min(Integer.MAX_VALUE, seq.totalMilli);
        for (int i = 0; i < seq.spins.size(); i++) {
            Spin s = seq.spins.get(i);
            int o = 2 + i * STRIDE;
            out[o] = s.kind | s.events << 2 | s.bonus << 18 | Math.min(31, s.prizes) << 21;
            out[o + 1] = (s.spinsLeft & 255) | (s.wheelSeg + 1) << 8 | (s.superSeg + 1) << 12 | (s.jackpot + 1) << 16 | (s.teaseMask & 31) << 19;
            out[o + 2] = s.winMilli;
            out[o + 3] = s.lineMilli;
            out[o + 4] = s.jackpotMilli;
            for (int c = 0; c < CELLS; c++) out[o + 5 + c / 8] |= (s.grid[c] & 15) << ((c % 8) * 4);
            for (int c = 0; c < BCELLS; c++) {
                out[o + 7 + c / 15] |= (s.tier[c] & 3) << ((c % 15) * 2);
                out[o + 9 + c / 8] |= (s.reward[c] & 15) << ((c % 8) * 4);
                out[o + 14 + c / 6] |= (s.hatTo[c] & 31) << ((c % 6) * 5);
            }
            out[o + 13] = s.hats;
            out[o + 19] = s.saws;
            out[o + 20] = s.golds;
            out[o + 21] = s.copies;
            for (int k = 0; k < Math.min(MAX_MEGAS, s.megas.length); k++) out[o + 22 + k / 2] |= (s.megas[k] & 0xFFF) << ((k % 2) * 12);
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
            s.events = (d[o] >>> 2) & 0xFFFF;
            s.bonus = Math.min(B_SUPER_MANSION, (d[o] >>> 18) & 7);
            s.prizes = (d[o] >>> 21) & 31;
            s.spinsLeft = d[o + 1] & 255;
            s.wheelSeg = Math.min(WHEEL.length - 1, ((d[o + 1] >> 8) & 15) - 1);
            s.superSeg = Math.min(SUPER_WHEEL.length - 1, ((d[o + 1] >> 12) & 15) - 1);
            s.jackpot = Math.min(J_SUPER, ((d[o + 1] >> 16) & 7) - 1);
            s.teaseMask = (d[o + 1] >> 19) & 31;
            s.winMilli = d[o + 2];
            s.lineMilli = d[o + 3];
            s.jackpotMilli = d[o + 4];
            for (int c = 0; c < CELLS; c++) s.grid[c] = Math.min(EMPTY, (d[o + 5 + c / 8] >> ((c % 8) * 4)) & 15);
            for (int c = 0; c < BCELLS; c++) {
                s.tier[c] = (d[o + 7 + c / 15] >> ((c % 15) * 2)) & 3;
                s.reward[c] = Math.min(R_WHEEL, (d[o + 9 + c / 8] >> ((c % 8) * 4)) & 15);
                int to = (d[o + 14 + c / 6] >> ((c % 6) * 5)) & 31;
                s.hatTo[c] = to < BCELLS || to == PRIZE ? to : c;
            }
            s.hats = d[o + 13] & ((1 << BCELLS) - 1);
            s.saws = d[o + 19];
            s.golds = d[o + 20];
            s.copies = d[o + 21];
            List<Integer> megas = new ArrayList<>();
            for (int k = 0; k < MAX_MEGAS; k++) {
                int m = (d[o + 22 + k / 2] >> ((k % 2) * 12)) & 0xFFF;
                if (megaW(m) == 0 || megaH(m) == 0) break;
                if (megaX(m) + megaW(m) > REELS || megaY(m) + megaH(m) > BROWS) break;
                megas.add(m);
            }
            s.megas = megas.stream().mapToInt(Integer::intValue).toArray();
            seq.spins.add(s);
        }
        return seq;
    }
}
