package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.data.CasinoStats;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Arrays;
import java.util.List;

/**
 * Partie SERVEUR de {@link LeaderboardBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new LeaderboardBlockEntity(...) » côté serveur.
 */
public class LeaderboardBlockEntityServeur extends LeaderboardBlockEntity {
   public LeaderboardBlockEntityServeur(MachineType type, BlockPos pos, BlockState state) {
      super(type, pos, state);
   }

   public LeaderboardBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }

    private static final int REFRESH = 40;

    @Override
    protected void tickServer(ServerLevel level) {
        if ((level.getGameTime() + worldPosition.hashCode()) % REFRESH != 0) return;
        CasinoStats stats = CasinoStats.get(level.getServer());
        List<CasinoStats.Row> top = stats.board(level.getServer(), kind(), ROWS);
        String[] n = new String[top.size()], m = new String[top.size()];
        long[] v = new long[top.size()];
        for (int i = 0; i < top.size(); i++) {
            n[i] = top.get(i).name();
            v[i] = top.get(i).value();
            m[i] = top.get(i).machine();
        }
        Object[] rec = stats.record(level.getServer());
        String rn = rec == null ? "" : (String) rec[0], rm = rec == null ? "" : (String) rec[2];
        long ra = rec == null ? 0 : (Long) rec[1];
        if (!Arrays.equals(n, names) || !Arrays.equals(v, values) || !Arrays.equals(m, machines) || !rn.equals(recordName) || ra != recordAmount
                || !rm.equals(recordMachine)) {
            names = n;
            values = v;
            machines = m;
            recordName = rn;
            recordAmount = ra;
            recordMachine = rm;
            sync();
        }
    }

    @Override
    public void handleAction(ServerPlayer player, int action, long[] args) {
    }
}
