package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.game.roulette.Roulette;
import fr.heraliz.casino.game.roulette.RouletteRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import static fr.heraliz.casino.block.MachinesServeur.rnd;
import static fr.heraliz.casino.block.MachinesServeur.rndDouble;

/**
 * Partie SERVEUR de {@link RouletteBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new RouletteBlockEntity(...) » côté serveur.
 */
public class RouletteBlockEntityServeur extends RouletteBlockEntity {
   public RouletteBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }


    @Override
    protected int bettingTicks() {
        return rules().bettingSeconds * 20;
    }

    @Override
    protected boolean validSpot(int spot) {
        return Roulette.valid(spot, zeros);
    }

    @Override
    protected void onStart(ServerLevel level) {
        RouletteRules r = rules();
        spinTicks = r.spinSeconds * 20;
        int[] wheel = Roulette.wheel(zeros);
        number = wheel[rnd(wheel.length)];
        ballStart = (float) (rndDouble() * 360);
        // le lancer part de l'angle actuel du cylindre au repos
        wheelStart = (restAngle + IDLE_SPEED * (now() - restTick)) % 360f;
    }

    @Override
    protected boolean runningDone(long now) {
        return now >= phaseStart + spinTicks + 10;
    }

    @Override
    protected long payoutFor(Bet bet) {
        if (number < 0 || !Roulette.wins(bet.spot, number)) return 0;
        return bet.amount * Roulette.returnMultiplier(bet.spot);
    }

    @Override
    protected void onSettled(ServerLevel level) {
        int[] h = new int[Math.min(12, history.length + 1)];
        h[0] = number;
        System.arraycopy(history, 0, h, 1, h.length - 1);
        history = h;
    }

    @Override
    public void tickServer(ServerLevel level) {
        if (phase == IDLE && zeros != rules().zeros) {
            zeros = rules().zeros;
            sync();
        }
        int before = phase;
        super.tickServer(level);
        if (before == RESULT && phase == IDLE) {
            // fin de l'affichage du resultat : le cylindre continue de tourner depuis ou il est
            restAngle = Roulette.wheelAngle(spinTicks + 10 + resultTicks(), spinTicks, wheelStart) % 360f;
            restTick = now();
            sync();
        }
    }
}
