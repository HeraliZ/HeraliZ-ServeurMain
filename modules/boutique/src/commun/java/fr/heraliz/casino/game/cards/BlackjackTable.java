package fr.heraliz.casino.game.cards;

import java.util.function.IntSupplier;

/**
 * Deroulement d'une main de blackjack (logique pure, partagee serveur / client / tests).
 * Chaque carte a l'instant (tick) ou elle se pose sur la table : le client anime la distribution a partir de la.
 * Les resultats sont calcules des que la main est jouee mais ne sont affiches / payes qu'a "settleTick".
 */
public final class BlackjackTable {
    public static final int IDLE = 0, PLAYER = 1, DEALER = 2, DONE = 3;
    public static final int R_NONE = 0, R_WIN = 1, R_LOSE = 2, R_PUSH = 3, R_BLACKJACK = 4, R_BUST = 5;
    public static final int MAX = 12;
    /** Delai entre deux cartes distribuees (ticks). */
    public static final int DEAL = 8, DEALER_DRAW = 14;

    public int phase = IDLE;
    public final int[][] hands = new int[2][MAX];
    public final long[][] handTicks = new long[2][MAX];
    public final int[] handSize = new int[2];
    public final long[] bets = new long[2];
    public final boolean[] doubled = new boolean[2];
    public final int[] results = new int[2];
    public final long[] payouts = new long[2];
    public int handCount = 1;
    public int active;
    public final int[] dealer = new int[MAX];
    public final long[] dealerTicks = new long[MAX];
    public int dealerSize;
    /** Instant ou la carte cachee du croupier est retournee (Long.MAX_VALUE = pas encore). */
    public long revealTick = Long.MAX_VALUE;
    /** Instant du reglement (gains affiches et payes). */
    public long settleTick = Long.MAX_VALUE;
    /** Actions refusees avant cet instant (animation en cours). */
    public long busyUntil;
    /** Limite de reflexion du joueur. */
    public long deadline;
    /** Gains deja verses (serveur). */
    public boolean paid;

    private final BlackjackRules rules;

    public BlackjackTable(BlackjackRules rules) {
        this.rules = rules;
    }

    public BlackjackRules rules() {
        return rules;
    }

    public void reset() {
        phase = IDLE;
        for (int h = 0; h < 2; h++) {
            handSize[h] = 0;
            bets[h] = 0;
            doubled[h] = false;
            results[h] = R_NONE;
            payouts[h] = 0;
        }
        handCount = 1;
        active = 0;
        dealerSize = 0;
        revealTick = Long.MAX_VALUE;
        settleTick = Long.MAX_VALUE;
        busyUntil = 0;
        paid = false;
    }

    // =================================================================== actions
    /** Nouvelle main (la mise est deja encaissee). */
    public void start(long bet, long now, IntSupplier draw) {
        reset();
        bets[0] = bet;
        phase = PLAYER;
        give(0, draw.getAsInt(), now + 6);
        dealer(draw.getAsInt(), now + 6 + DEAL);
        give(0, draw.getAsInt(), now + 6 + DEAL * 2);
        dealer(draw.getAsInt(), now + 6 + DEAL * 3);
        busyUntil = now + 6 + DEAL * 3 + 4;
        deadline = busyUntil + rules.decisionSeconds * 20L;
        int up = Cards.rank(dealer[0]);
        boolean dealerBj = Cards.blackjack(dealer, 2);
        boolean playerBj = Cards.blackjack(hands[0], 2);
        if ((up == 0 || up >= 9) && dealerBj) {
            // le croupier regarde sa carte cachee : blackjack, la main s'arrete
            revealTick = busyUntil + 2;
            finish(busyUntil + 16);
        } else if (playerBj) {
            revealTick = busyUntil + 2;
            finish(busyUntil + 16);
        }
    }

    public boolean canAct(long now) {
        return phase == PLAYER && now >= busyUntil;
    }

    public void hit(long now, IntSupplier draw) {
        if (!canAct(now)) return;
        give(active, draw.getAsInt(), now + DEAL);
        busyUntil = now + DEAL + 2;
        deadline = busyUntil + rules.decisionSeconds * 20L;
        int t = Cards.total(hands[active], handSize[active]);
        if (t >= 21) next(now + DEAL + 2, draw);
    }

    public void stand(long now, IntSupplier draw) {
        if (!canAct(now)) return;
        next(now, draw);
    }

    public boolean canDouble(long now) {
        return canAct(now) && rules.allowDouble && handSize[active] == 2 && !splitAces();
    }

    /** Double la mise (le supplement est deja encaisse) : une seule carte puis la main suivante. */
    public void doubleDown(long now, IntSupplier draw) {
        if (!canDouble(now)) return;
        bets[active] *= 2;
        doubled[active] = true;
        give(active, draw.getAsInt(), now + DEAL);
        busyUntil = now + DEAL + 4;
        next(now + DEAL + 4, draw);
    }

    public boolean canSplit(long now) {
        return canAct(now) && rules.allowSplit && handCount == 1 && handSize[0] == 2
                && Cards.bjValue(hands[0][0]) == Cards.bjValue(hands[0][1]);
    }

    /** Partage (la seconde mise est deja encaissee). */
    public void split(long now, IntSupplier draw) {
        if (!canSplit(now)) return;
        handCount = 2;
        hands[1][0] = hands[0][1];
        handTicks[1][0] = now + 4;
        handSize[1] = 1;
        handSize[0] = 1;
        bets[1] = bets[0];
        give(0, draw.getAsInt(), now + 4 + DEAL);
        give(1, draw.getAsInt(), now + 4 + DEAL * 2);
        busyUntil = now + 4 + DEAL * 2 + 2;
        deadline = busyUntil + rules.decisionSeconds * 20L;
        active = 0;
        if (splitAces()) {
            // As partages : une seule carte chacun
            dealerPlay(busyUntil, draw);
        } else if (Cards.total(hands[0], 1 + 1) == 21) {
            next(busyUntil, draw);
        }
    }

    private boolean splitAces() {
        return handCount == 2 && Cards.rank(hands[0][0]) == 0 && Cards.rank(hands[1][0]) == 0;
    }

    /** Temps ecoule : le joueur reste. */
    public void timeout(long now, IntSupplier draw) {
        if (phase == PLAYER && now >= deadline && now >= busyUntil) next(now, draw);
    }

    private void next(long now, IntSupplier draw) {
        if (active < handCount - 1) {
            active++;
            busyUntil = Math.max(busyUntil, now);
            deadline = busyUntil + rules.decisionSeconds * 20L;
            if (Cards.total(hands[active], handSize[active]) >= 21) next(now, draw);
            return;
        }
        dealerPlay(now, draw);
    }

    private void dealerPlay(long now, IntSupplier draw) {
        phase = DEALER;
        revealTick = now + 6;
        long t = revealTick;
        boolean allBust = true;
        for (int h = 0; h < handCount; h++) if (Cards.total(hands[h], handSize[h]) <= 21) allBust = false;
        if (!allBust) {
            while (true) {
                int total = Cards.total(dealer, dealerSize);
                boolean soft = Cards.soft(dealer, dealerSize);
                if (total > 17 || (total == 17 && !(soft && rules.dealerHitsSoft17))) break;
                t += DEALER_DRAW;
                dealer(draw.getAsInt(), t);
            }
        }
        finish(t + 12);
    }

    /** Calcule les resultats (affiches et payes a "settle"). */
    private void finish(long settle) {
        phase = DEALER;
        settleTick = settle;
        int d = Cards.total(dealer, dealerSize);
        boolean dealerBj = Cards.blackjack(dealer, dealerSize);
        for (int h = 0; h < handCount; h++) {
            int p = Cards.total(hands[h], handSize[h]);
            boolean bj = handCount == 1 && Cards.blackjack(hands[h], handSize[h]);
            long bet = bets[h];
            if (bj && dealerBj) {
                results[h] = R_PUSH;
                payouts[h] = bet;
            } else if (bj) {
                results[h] = R_BLACKJACK;
                payouts[h] = bet + (long) Math.floor(bet * rules.blackjackPays);
            } else if (dealerBj) {
                results[h] = R_LOSE;
            } else if (p > 21) {
                results[h] = R_BUST;
            } else if (d > 21 || p > d) {
                results[h] = R_WIN;
                payouts[h] = bet * 2;
            } else if (p == d) {
                results[h] = R_PUSH;
                payouts[h] = bet;
            } else {
                results[h] = R_LOSE;
            }
        }
    }

    public long totalBet() {
        return bets[0] + (handCount > 1 ? bets[1] : 0);
    }

    public long totalPayout() {
        return payouts[0] + (handCount > 1 ? payouts[1] : 0);
    }

    // =================================================================== cartes
    private void give(int h, int card, long tick) {
        if (handSize[h] >= MAX) return;
        hands[h][handSize[h]] = card;
        handTicks[h][handSize[h]] = tick;
        handSize[h]++;
    }

    private void dealer(int card, long tick) {
        if (dealerSize >= MAX) return;
        dealer[dealerSize] = card;
        dealerTicks[dealerSize] = tick;
        dealerSize++;
    }

    /** Carte du croupier telle qu'elle doit etre montree a l'instant "now" (cachee avant le retournement). */
    public int dealerVisible(int i, long now) {
        if (i == 1 && now < revealTick) return Cards.HIDDEN;
        return dealer[i];
    }
}
