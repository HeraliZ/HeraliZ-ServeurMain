package fr.heraliz.casino.client;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.entity.BlackjackBlockEntity;
import fr.heraliz.casino.block.entity.CashierBlockEntity;
import fr.heraliz.casino.block.entity.CrashBlockEntity;
import fr.heraliz.casino.block.entity.HorseRaceBlockEntity;
import fr.heraliz.casino.block.entity.LeaderboardBlockEntity;
import fr.heraliz.casino.block.entity.MinesBlockEntity;
import fr.heraliz.casino.block.entity.NavalBlockEntity;
import fr.heraliz.casino.block.entity.PlinkoBlockEntity;
import fr.heraliz.casino.block.entity.RouletteBlockEntity;
import fr.heraliz.casino.block.entity.RoundBlockEntity;
import fr.heraliz.casino.block.entity.RpsBlockEntity;
import fr.heraliz.casino.block.entity.SlotBlockEntity;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.machines.BlackjackView;
import fr.heraliz.casino.client.render.scene.machines.BoardView;
import fr.heraliz.casino.client.render.scene.machines.CashierView;
import fr.heraliz.casino.client.render.scene.machines.CrashView;
import fr.heraliz.casino.client.render.scene.machines.LoupView;
import fr.heraliz.casino.client.render.scene.machines.MinesView;
import fr.heraliz.casino.client.render.scene.machines.NavalView;
import fr.heraliz.casino.client.render.scene.machines.NicheView;
import fr.heraliz.casino.client.render.scene.machines.OlympeView;
import fr.heraliz.casino.client.render.scene.machines.PlinkoView;
import fr.heraliz.casino.client.render.scene.machines.RaceView;
import fr.heraliz.casino.client.render.scene.machines.RottenView;
import fr.heraliz.casino.client.render.scene.machines.RouletteView;
import fr.heraliz.casino.client.render.scene.machines.RoundView;
import fr.heraliz.casino.client.render.scene.machines.RpsView;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.mines.MinesRules;
import fr.heraliz.casino.game.slot.Loup;
import fr.heraliz.casino.game.slot.Niche;
import fr.heraliz.casino.game.slot.Olympe;
import fr.heraliz.casino.game.slot.Rotten;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Remplit la vue de chaque type de machine a partir de son entite (etat synchronise par le serveur).
 */
public final class ViewBinder {
    private ViewBinder() {
    }

    @Nullable
    private static UUID me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID();
    }

    public static void fill(MachineBlockEntity be, Object view, Scene s) {
        // le joueur local vient de s'installer (intro) : l'ecran repart de zero, sans la derniere partie d'un autre
        if (be instanceof SlotBlockEntity sb && view instanceof RottenView v) {
            v.rules = CasinoConfig.get().rotten;
            if (sb.clientGame instanceof Rotten.Sequence seq) v.seq = fresh(sb) ? NO_ROTTEN : seq;
            v.since = s.since(sb.spinStart);
            v.intro = intro(sb, s);
            v.bet = sb.bet;
            v.win = sb.win;
            v.cost = sb.cost;
            v.player = sb.spinnerName;
        } else if (be instanceof SlotBlockEntity sb && view instanceof OlympeView v) {
            v.rules = CasinoConfig.get().portesOlympe;
            if (sb.clientGame instanceof Olympe.Sequence seq) v.seq = fresh(sb) ? NO_OLYMPE : seq;
            v.since = s.since(sb.spinStart);
            v.intro = intro(sb, s);
            v.bet = sb.bet;
        } else if (be instanceof SlotBlockEntity sb && view instanceof LoupView v) {
            v.rules = CasinoConfig.get().souffleLoup;
            if (sb.clientGame instanceof Loup.Sequence seq) v.seq = fresh(sb) ? NO_LOUP : seq;
            v.since = s.since(sb.spinStart);
            v.intro = intro(sb, s);
            v.bet = sb.bet;
        } else if (be instanceof SlotBlockEntity sb && view instanceof NicheView v) {
            v.rules = CasinoConfig.get().laNiche;
            if (sb.clientGame instanceof Niche.Sequence seq) v.seq = fresh(sb) ? NO_NICHE : seq;
            v.since = s.since(sb.spinStart);
            v.intro = intro(sb, s);
            v.bet = sb.bet;
        } else if (be instanceof LeaderboardBlockEntity lb && view instanceof BoardView v) {
            v.kind = lb.kind();
            v.names = lb.names;
            v.values = lb.values;
            if (v.machines.length != lb.machines.length) v.machines = new String[lb.machines.length];
            for (int i = 0; i < lb.machines.length; i++) v.machines[i] = machineName(lb.machines[i]);
            v.recordName = lb.recordName;
            v.recordAmount = lb.recordAmount;
            v.recordMachine = machineName(lb.recordMachine);
        } else if (be instanceof RouletteBlockEntity r && view instanceof RouletteView v) {
            round(r, v, s);
            v.zeros = r.zeros;
            v.number = r.number;
            v.spinTicks = r.spinTicks;
            v.wheelStart = r.wheelStart;
            v.ballStart = r.ballStart;
            v.restAngle = r.restAngle;
            v.sinceRest = s.since(r.restTick);
            v.history = r.history;
            v.spinTime = r.phase == RoundBlockEntity.RESULT ? r.spinTicks + 10 + s.since(r.phaseStart) : s.since(r.phaseStart);
        } else if (be instanceof CrashBlockEntity c && view instanceof CrashView v) {
            round(c, v, s);
            v.growth = c.growth;
            v.crashed = c.crashed;
            v.crashShown = c.crashShown;
            v.sinceCrash = s.since(c.crashTick);
            v.flight = c.crashed ? c.crashTick - c.phaseStart : s.since(c.phaseStart);
            v.crashes = c.history;
            int in = 0;
            long total = 0;
            for (RoundBlockEntity.Bet b : c.bets) {
                if (b.extra == 0) {
                    in++;
                    total += b.amount;
                }
            }
            v.inPlay = in;
            v.totalInPlay = total;
            UUID me = me();
            v.myCash = me == null ? 0 : c.cashedOut(me);
        } else if (be instanceof HorseRaceBlockEntity h && view instanceof RaceView v) {
            round(h, v, s);
            System.arraycopy(h.odds, 0, v.odds, 0, Math.min(h.odds.length, v.odds.length));
            v.seed = h.raceSeed;
            v.winner = h.winner;
            v.raceTicks = h.raceTicks;
            v.history = h.history;
        } else if (be instanceof PlinkoBlockEntity p && view instanceof PlinkoView v) {
            UUID me = me();
            int n = Math.min(PlinkoView.MAX, p.balls.size());
            for (int i = 0; i < n; i++) {
                PlinkoBlockEntity.Ball b = p.balls.get(i);
                v.path[i] = b.path;
                v.t[i] = s.since(b.start);
                v.mine[i] = b.player.equals(me);
            }
            v.count = n;
            v.multipliers = p.multipliers;
        } else if (be instanceof BlackjackBlockEntity bj && view instanceof BlackjackView v) {
            v.table = bj.table;
            v.player = bj.playerName;
            v.doneTick = bj.doneTick;
        } else if (be instanceof MinesBlockEntity m && view instanceof MinesView v) {
            v.phase = m.phase;
            v.mines = m.mines;
            v.revealed = m.revealed;
            System.arraycopy(m.tileTicks, 0, v.tileTicks, 0, 25);
            v.mineShown = m.mineShown;
            v.boomTile = m.boomTile;
            v.endTick = m.endTick;
            v.mult100 = m.mult100;
            v.bet = m.bet;
            v.win = m.win;
            int safe = MinesRules.TILES - m.mines;
            v.next100 = m.revealedCount() < safe
                    ? Math.round(CasinoConfig.get().mines.multiplier(m.mines, m.revealedCount() + 1) * 100) : m.mult100;
        } else if (be instanceof RpsBlockEntity r && view instanceof RpsView v) {
            v.phase = r.phase;
            v.step = r.step;
            v.round = r.round;
            v.scoreA = r.scoreA;
            v.scoreB = r.scoreB;
            v.winsNeeded = CasinoConfig.get().pierreFeuilleCiseaux.winsNeeded;
            v.chosenA = r.chosenA;
            v.chosenB = r.chosenB;
            v.shownA = r.shownA;
            v.shownB = r.shownB;
            v.roundWinner = r.roundWinner;
            v.winner = r.winner;
            v.sinceReveal = r.step == RpsBlockEntity.REVEAL ? s.since(r.revealTick) : 1e6f;
            v.sincePhase = s.since(r.phaseStart);
            v.countdown = Math.max(0f, (r.deadline - s.tick - s.partial) / 20f);
            v.nameA = r.nameA;
            v.nameB = r.nameB;
            v.stake = r.stake;
            v.mySide = r.sideOf(me());
            v.botSide = r.botSide;
        } else if (be instanceof NavalBlockEntity n && view instanceof NavalView v) {
            v.phase = n.phase;
            v.step = n.step;
            v.turn = n.turn;
            v.winner = n.winner;
            v.readyA = n.readyA;
            v.readyB = n.readyB;
            v.shotsOnA = n.shotsOnA;
            v.hitsOnA = n.hitsOnA;
            v.shotsOnB = n.shotsOnB;
            v.hitsOnB = n.hitsOnB;
            v.wrecksA = n.wrecksA;
            v.wrecksB = n.wrecksB;
            v.lastShot = n.lastShot;
            v.lastShotSide = n.lastShotSide;
            v.sinceShot = n.lastShot >= 0 ? s.since(n.lastShotTick) : 1e6f;
            v.sincePhase = s.since(n.phaseStart);
            v.countdown = Math.max(0f, (n.deadline - s.tick - s.partial) / 20f);
            v.mySide = n.sideOf(me());
            v.myBoard = v.mySide != 0 ? n.clientBoard : new int[0];
            v.held = n.clientHeld;
            v.heldH = n.clientHeldH;
            v.holdX = n.clientHoldX;
            v.holdZ = n.clientHoldZ;
            v.snapCell = n.clientSnap;
            v.snapOk = n.clientSnapOk;
            v.nameA = n.nameA;
            v.nameB = n.nameB;
            v.stake = n.stake;
            v.botSide = n.botSide;
        } else if (be instanceof CashierBlockEntity c && view instanceof CashierView v) {
            v.lastTx = c.lastTx;
            v.lastTxType = c.lastTxType;
            v.lastTxAmount = c.lastTxAmount;
            v.rates = rates();
        }
    }

    /** Ticks depuis l'intro du joueur local a cette machine ; un lancement posterieur la coupe (1e6). */
    private static float intro(SlotBlockEntity sb, Scene s) {
        return fresh(sb) ? s.since(sb.introStart) : 1e6f;
    }

    /** Le joueur local s'est installe apres le dernier lancement : son ecran est neuf. */
    private static boolean fresh(SlotBlockEntity sb) {
        return sb.introStart > sb.spinStart;
    }

    private static final Rotten.Sequence NO_ROTTEN = new Rotten.Sequence();
    private static final Olympe.Sequence NO_OLYMPE = new Olympe.Sequence();
    private static final Loup.Sequence NO_LOUP = new Loup.Sequence();
    private static final Niche.Sequence NO_NICHE = new Niche.Sequence();

    /** Nom affiche d'une machine (identifiant), vide si inconnue. */
    private static String machineName(String id) {
        MachineType t = MachineType.byId(id);
        return t == null ? "" : t.displayName().getString();
    }

    /** Etat commun des jeux en tours : phase, compte a rebours, mises par case (toutes / les miennes). */
    private static void round(RoundBlockEntity r, RoundView v, Scene s) {
        v.phase = r.phase;
        v.sincePhase = s.since(r.phaseStart);
        v.countdown = r.phase == RoundBlockEntity.BETTING ? Math.max(0f, (r.bettingEnd - s.tick - s.partial) / 20f) : 0f;
        java.util.Arrays.fill(v.totals, 0);
        java.util.Arrays.fill(v.mine, 0);
        UUID me = me();
        for (RoundBlockEntity.Bet b : r.bets) {
            int spot = b.spot;
            if (r instanceof CrashBlockEntity) spot = 0;
            if (spot < 0 || spot >= v.totals.length) continue;
            v.totals[spot] += b.amount;
            if (b.player.equals(me)) v.mine[spot] += b.amount;
        }
    }

    // ---- tableau des taux de la caisse (noms des objets traduits), recalcule quand la config change
    private static String ratesKey;
    private static String[] ratesCache = new String[0];

    private static String[] rates() {
        String key = CasinoConfig.json();
        if (key.equals(ratesKey)) return ratesCache;
        java.util.List<String> lines = new java.util.ArrayList<>();
        CasinoConfig.Cashier c = CasinoConfig.get().caisse;
        // en tete : le change avec les herals, la monnaie du serveur (mod HeraliZ Economie) : « 1 héral = 1 jeton »
        if (fr.heraliz.casino.economy.EconomieServeur.ouvert() && (c.economie.achat || c.economie.vente)) {
            lines.add(c.economie.jetonsServeur + (c.economie.jetonsServeur >= 2 ? " hérals = " : " héral = ")
                    + c.economie.jetonsCasino + (c.economie.jetonsCasino >= 2 ? " jetons" : " jeton"));
            if (c.economie.commissionRevente > 0)
                lines.add("Revente : -" + Math.round(c.economie.commissionRevente * 100) + " %");
        }
        for (CasinoConfig.Exchange e : CasinoConfig.get().caisse.echanges) {
            if (!c.echangesObjets) break;
            Item item = CashierBlockEntity.item(e);
            if (item == null) continue;
            lines.add("1 " + new ItemStack(item).getHoverName().getString() + " = " + e.jetons);
        }
        for (CasinoConfig.CommandExchange e : CasinoConfig.get().caisse.encaissements) {
            lines.add(e.jetons + " j = " + e.montant + " " + e.nom);
        }
        ratesCache = lines.toArray(new String[0]);
        ratesKey = key;
        return ratesCache;
    }

    /** Joueur en cours sur la machine (dernier a avoir joue), ou null. */
    @Nullable
    public static UUID player(MachineBlockEntity be) {
        if (be instanceof SlotBlockEntity sb) return sb.spinner;
        if (be instanceof BlackjackBlockEntity bj) return bj.player;
        if (be instanceof MinesBlockEntity m) return m.player;
        return null;
    }
}
