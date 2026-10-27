package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.economy.CompteCapsules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Partie SERVEUR de {@link AtmBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : depot et retrait sur le compte de
 * capsules ({@link CompteCapsules}, qui decide de tout), resultat ecrit dans l'ecran du distributeur. La distance du joueur
 * est deja verifiee par ActionPacket ({@code isNear}).
 * Creee par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new AtmBlockEntity(...) » cote serveur.
 */
public class AtmBlockEntityServeur extends AtmBlockEntity {
    /** Capsules par operation, au plus (le plafond du compte reste celui des reglages). */
    private static final long MAX_OPERATION = 1_000_000_000L;

    public AtmBlockEntityServeur(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void handleAction(ServerPlayer p, int action, long[] args) {
        if (!(level instanceof ServerLevel)) return;
        long n = args.length > 0 ? Math.max(0, Math.min(MAX_OPERATION, args[0])) : 0;
        switch (action) {
            case ACTION_OUVRIR -> CompteCapsules.envoyer(p);
            case ACTION_DEPOSER -> {
                long fait = CompteCapsules.deposer(p, n, true);
                if (fait > 0) tx(TX_DEPOT, fait);
            }
            case ACTION_RETIRER -> {
                if (n <= 0) return;
                long fait = CompteCapsules.retirer(p, n, true);
                if (fait > 0) tx(TX_RETRAIT, fait);
            }
            default -> {
            }
        }
    }

    /** Dernier mouvement, pour l'animation (capsules dans l'entonnoir ou dans le bac) vue par tous. */
    private void tx(int type, long amount) {
        lastTx = now();
        lastTxType = type;
        lastTxAmount = amount;
        sync();
    }
}
