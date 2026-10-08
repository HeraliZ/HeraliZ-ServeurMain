package fr.heralisz.clans.territory;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.server.ServerHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Caisse de ravitaillement posee, version serveur : ouverture et verrou de clan (config caisse_verrouillee_clan), code
 * d'origine. Creee par {@code ClansRegistry.CAISSE_USINE} quand la partie serveur est dans le jar.
 */
public class CaisseRavitaillementBlockServeur extends CaisseRavitaillementBlock {
    public CaisseRavitaillementBlockServeur(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof CaisseRavitaillementBlockEntity be)) {
            return InteractionResult.CONSUME;
        }
        if (ClansConfig.CAISSE_VERROUILLEE_CLAN.get() && !be.clanTag.isEmpty()) {
            ClanStore store = ClanStore.get();
            Clan c = store == null ? null : store.clanDe(sp.getUUID());
            if (c == null || !c.tag.equalsIgnoreCase(be.clanTag)) {
                ServerHandlers.erreur(sp, "zone.caisse.verrouillee", be.clanTag);
                return InteractionResult.CONSUME;
            }
        }
        sp.openMenu(be);
        return InteractionResult.CONSUME;
    }
}
