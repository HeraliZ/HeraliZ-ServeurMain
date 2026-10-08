package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.economy.EconomieServeur;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;
import java.util.List;

/**
 * Partie SERVEUR de {@link CashierBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new CashierBlockEntity(...) » côté serveur.
 */
public class CashierBlockEntityServeur extends CashierBlockEntity {
   public CashierBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }

    /** Unites du taux par operation, au plus (1 unite = jetonsServeur du compte = jetonsCasino de casino). */
    private static final long MAX_UNITS = 100_000_000L;

    private static void removeItems(ServerPlayer p, Item item, int count) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.hasTag()) {
                int k = Math.min(count, s.getCount());
                s.shrink(k);
                count -= k;
                if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }
        inv.setChanged();
    }

    @Override
    public void handleAction(ServerPlayer p, int action, long[] args) {
        if (!(level instanceof ServerLevel sl) || args.length < 2) return;
        CasinoConfig.Cashier c = CasinoConfig.get().caisse;
        int index = (int) args[0];
        long count = Math.max(1, Math.min(10_000, args[1]));
        if (action == ACTION_BUY_SERVER || action == ACTION_SELL_SERVER) {
            serverExchange(p, action == ACTION_BUY_SERVER, Math.max(1, Math.min(MAX_UNITS, args[1])));
        } else if (action == ACTION_BUY || action == ACTION_SELL) {
            if (!c.echangesObjets) return;
            List<CasinoConfig.Exchange> list = c.echanges;
            if (index < 0 || index >= list.size()) return;
            CasinoConfig.Exchange e = list.get(index);
            Item item = item(e);
            if (item == null || e.jetons <= 0) return;
            if (action == ACTION_BUY) {
                if (!e.achat) return;
                int have = countItems(p, item);
                int n = (int) Math.min(count, have);
                if (n <= 0) {
                    msg(p, Component.translatable("message.heralizcasino.cashier.no_items", new ItemStack(item).getHoverName()).withStyle(ChatFormatting.RED));
                    return;
                }
                removeItems(p, item, n);
                long chips = n * e.jetons;
                ChipsServeur.give(p, chips);
                msg(p, Component.translatable("message.heralizcasino.cashier.bought", Chips.format(chips), n, new ItemStack(item).getHoverName())
                        .withStyle(ChatFormatting.GREEN));
                tx(0, chips);
            } else {
                if (!e.vente) return;
                long chips = count * e.jetons;
                if (!ChipsServeur.take(p, chips)) {
                    msg(p, Component.translatable("message.heralizcasino.not_enough", Chips.format(chips), Chips.format(Chips.count(p))).withStyle(ChatFormatting.RED));
                    return;
                }
                long left = count;
                while (left > 0) {
                    int k = (int) Math.min(left, item.getMaxStackSize());
                    ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(item, k));
                    left -= k;
                }
                msg(p, Component.translatable("message.heralizcasino.cashier.sold", Chips.format(chips), count, new ItemStack(item).getHoverName())
                        .withStyle(ChatFormatting.GREEN));
                tx(1, chips);
            }
        } else if (action == ACTION_CASH_CMD) {
            List<CasinoConfig.CommandExchange> list = c.encaissements;
            if (index < 0 || index >= list.size()) return;
            CasinoConfig.CommandExchange e = list.get(index);
            if (e.jetons <= 0 || e.commande == null || e.commande.isBlank()) return;
            long chips = count * e.jetons;
            if (!ChipsServeur.take(p, chips)) {
                msg(p, Component.translatable("message.heralizcasino.not_enough", Chips.format(chips), Chips.format(Chips.count(p))).withStyle(ChatFormatting.RED));
                return;
            }
            long amount = count * e.montant;
            MinecraftServer server = sl.getServer();
            String cmd = e.commande.replace("{player}", p.getGameProfile().getName()).replace("{amount}", Long.toString(amount));
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
            } catch (Exception ex) {
                HeralizCasino.LOGGER.error("[HeralizCasino] Commande d'encaissement en erreur : {}", cmd, ex);
            }
            msg(p, Component.translatable("message.heralizcasino.cashier.cashed", Chips.format(chips), Chips.format(amount), e.nom)
                    .withStyle(ChatFormatting.GREEN));
            tx(1, chips);
        }
    }

    /**
     * Change avec les jetons du serveur, "units" fois le taux (jetonsServeur du compte = jetonsCasino de casino). Tout
     * est verifie avant de bouger quoi que ce soit : achat = retrait du compte (refuse si le solde ne suffit pas), puis
     * jetons de casino donnes ; revente = place sur le compte verifiee, jetons de casino repris (refuse s'il n'en a pas
     * assez), puis depot. Chaque mouvement du compte est inscrit au journal de l'economie.
     */
    private void serverExchange(ServerPlayer p, boolean buy, long units) {
        CasinoConfig.JetonsServeur e = CasinoConfig.get().caisse.economie;
        if (!EconomieServeur.ouvert() || !EconomieServeur.disponible()) {
            msg(p, Component.translatable("message.heralizcasino.cashier.server_off").withStyle(ChatFormatting.RED));
            return;
        }
        if (buy ? !e.achat : !e.vente) return;
        // revente : le casino garde sa commission (commissionRevente), l'achat reste au taux plein
        long server = buy ? units * e.jetonsServeur : e.reventeServeur(units), chips = units * e.jetonsCasino;
        if (server <= 0) return;
        java.util.UUID id = p.getUUID();
        String name = p.getGameProfile().getName();
        if (buy) {
            if (!EconomieServeur.retirer(id, server, EconomieServeur.MOTIF_ACHAT)) {
                msg(p, Component.translatable("message.heralizcasino.cashier.server_poor", EconomieServeur.format(server),
                        EconomieServeur.format(EconomieServeur.solde(id))).withStyle(ChatFormatting.RED));
                return;
            }
            ChipsServeur.give(p, chips);
            msg(p, Component.translatable("message.heralizcasino.cashier.server_bought", Chips.text(chips), EconomieServeur.format(server))
                    .withStyle(ChatFormatting.GREEN));
            HeralizCasino.LOGGER.info("[HeralizCasino] {} achete {} jetons de casino pour {} jetons du serveur", name, chips, server);
            tx(0, chips);
        } else {
            if (!EconomieServeur.peutRecevoir(id, server)) {
                msg(p, Component.translatable("message.heralizcasino.cashier.server_full", EconomieServeur.format(server)).withStyle(ChatFormatting.RED));
                return;
            }
            if (!ChipsServeur.take(p, chips)) {
                msg(p, Component.translatable("message.heralizcasino.not_enough", Chips.format(chips), Chips.format(Chips.count(p))).withStyle(ChatFormatting.RED));
                return;
            }
            if (!EconomieServeur.deposer(id, server, EconomieServeur.MOTIF_VENTE)) {
                // l'economie a lache entre-temps : on rend les jetons de casino
                ChipsServeur.give(p, chips);
                msg(p, Component.translatable("message.heralizcasino.cashier.server_off").withStyle(ChatFormatting.RED));
                return;
            }
            msg(p, Component.translatable("message.heralizcasino.cashier.server_sold", Chips.text(chips), EconomieServeur.format(server))
                    .withStyle(ChatFormatting.GREEN));
            HeralizCasino.LOGGER.info("[HeralizCasino] {} revend {} jetons de casino contre {} jetons du serveur", name, chips, server);
            tx(1, chips);
        }
    }

    private void tx(int type, long amount) {
        lastTx = now();
        lastTxType = type;
        lastTxAmount = amount;
        sync();
    }

    @Override
    protected void tickServer(ServerLevel level) {
    }
}
