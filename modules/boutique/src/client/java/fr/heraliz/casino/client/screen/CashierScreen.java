package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.CashierBlockEntity;
import fr.heraliz.casino.client.PontEconomieClient;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.client.screen.ui.UiKit;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.EconomieServeur;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Caisse : une ligne par echange avec ses boutons d'echange des capsules (jetons de casino, trouvees sur la carte)
 * contre des objets, puis les encaissements par commande. Le change avec les herals est ferme par le code
 * (EconomieServeur.CHANGE_HERALS) : sa ligne n'apparait plus, le pied de l'ecran rappelle ou trouver les capsules.
 */
public class CashierScreen extends MachineScreen<CashierBlockEntity> {
    private static final int ROW = 22, MAX_ROWS = 10, BW = 30;
    /** Ligne des jetons du serveur : jetons de casino vises par les boutons d'achat et de revente (0 = tout revendre). */
    private static final long[] SERVER_BUY = {10, 100, 1000}, SERVER_SELL = {10, 100, 0};

    /**
     * Une ligne de la caisse. server : change avec les jetons du serveur (chips = jetons de casino et amount = jetons du
     * serveur pour une unite du taux).
     */
    private record Line(int index, boolean command, boolean server, ItemStack icon, Component name, long chips, long amount, boolean buy, boolean sell) {
    }

    private final List<Line> lines = new ArrayList<>();
    private final List<UiButton> serverButtons = new ArrayList<>();
    private int lx, ly, lw, lh;
    /** Lignes affichees : autant que la hauteur de l'ecran le permet (au plus MAX_ROWS) ; les autres a la molette. */
    private int shown, defile;
    /** Au moins une ligne propose l'achat de capsules (sinon la colonne ACHETER n'est pas affichee). */
    private boolean anyBuy;

    public CashierScreen(CashierBlockEntity be) {
        super(be);
        CasinoConfig.Cashier c = CasinoConfig.get().caisse;
        CasinoConfig.JetonsServeur eco = c.economie;
        if (EconomieServeur.ouvert() && (eco.achat || eco.vente)) {
            lines.add(new Line(-1, false, true, ItemStack.EMPTY, Component.empty(), eco.jetonsCasino, eco.jetonsServeur, eco.achat, eco.vente));
        }
        // toutes les lignes sont gardees (plus de 10 : la liste defile a la molette)
        for (int i = 0; c.echangesObjets && i < c.echanges.size(); i++) {
            CasinoConfig.Exchange e = c.echanges.get(i);
            ItemStack s = CashierBlockEntity.stack(e);
            if (s == null || e.jetons <= 0 || (!e.achat && !e.vente)) continue;
            // achat de capsules : jamais avec un objet a NBT (le serveur le refuse aussi)
            boolean buy = e.achat && !s.hasTag();
            if (!buy && !e.vente) continue;
            lines.add(new Line(i, false, false, s, s.getHoverName(), e.jetons, 1, buy, e.vente));
        }
        for (int i = 0; i < c.encaissements.size(); i++) {
            CasinoConfig.CommandExchange e = c.encaissements.get(i);
            if (e.jetons <= 0 || e.commande == null || e.commande.isBlank()) continue;
            lines.add(new Line(i, true, false, new ItemStack(ModRegistry.chip(2)), Component.literal(e.nom), e.jetons, e.montant, false, true));
        }
        for (Line l : lines) anyBuy |= l.buy;
    }

    @Override
    protected void dock(Dock d) {
        // la caisse n'a pas de mise : seulement "i" et fermer
    }

    private int have(Line l) {
        if (l.command || l.server || minecraft == null || minecraft.player == null) return 0;
        return CashierBlockEntity.countStack(minecraft.player, l.icon);
    }

    /** Solde de jetons du serveur (envoye par le mod economie). */
    private static long serverBalance() {
        return PontEconomieClient.solde();
    }

    /**
     * 27/10 : la caisse prend sur le compte de capsules ce qui manque dans l'inventaire (solde envoye par le serveur, qui
     * reverifie tout a chaque achat).
     */
    @Override
    protected long balance() {
        CasinoConfig.Compte c = CasinoConfig.get().compte;
        return super.balance() + (c.actif && c.payerCaisse ? fr.heraliz.casino.net.ComptePacket.clientSolde : 0);
    }

    /** Unites du taux pour viser "target" jetons de casino (0 : tous les jetons de casino de l'inventaire). */
    private long units(Line l, long target, boolean sell) {
        if (target <= 0) return sell ? balance() / l.chips : 0;
        return Math.max(1, target / l.chips);
    }

    @Override
    protected void extraWidgets() {
        serverButtons.clear();
        lw = Math.min(width - 20, 380);
        // pas plus de lignes que la place au-dessus du bandeau du bas (petites fenetres, grande echelle d'interface)
        shown = Math.max(1, Math.min(Math.min(lines.size(), MAX_ROWS), (dockY - 34 - 26 - 8) / ROW));
        defile = Math.max(0, Math.min(defile, lines.size() - shown));
        lh = 26 + shown * ROW;
        lx = (width - lw) / 2;
        ly = dockY - 34 - lh;
        int sellX = lx + lw - 8 - 3 * BW - 4;
        int buyX = sellX - 10 - 3 * BW - 4;
        for (int r = 0; r < shown && defile + r < lines.size(); r++) {
            Line l = lines.get(defile + r);
            int y = ly + 24 + r * ROW + 2;
            if (l.server) {
                serverWidgets(l, buyX, sellX, y);
                continue;
            }
            if (l.buy) {
                long[] counts = {1, 10, -1};
                for (int k = 0; k < 3; k++) {
                    final long n = counts[k];
                    Component label = n < 0 ? Component.translatable("screen.heralizcasino.cashier.all") : Component.literal("+" + n);
                    place(new UiButton(BW, 16, label, UiAtlas.GREEN, b -> {
                        long c = n < 0 ? have(l) : Math.min(n, have(l));
                        if (c <= 0) return;
                        send(CashierBlockEntity.ACTION_BUY, l.index, c);
                        chipSound();
                    }).scale(0.8f).when(() -> have(l) > 0).tip(() -> {
                        long c = n < 0 ? have(l) : Math.min(n, have(l));
                        return Component.translatable("screen.heralizcasino.cashier.buy_tip", c, l.name, Chips.format(c * l.chips));
                    }), buyX + k * (BW + 2), y);
                }
            }
            if (l.sell) {
                long[] counts = l.command ? new long[]{1, 10, 100} : new long[]{1, 10, 64};
                for (int k = 0; k < 3; k++) {
                    final long n = counts[k];
                    place(new UiButton(BW, 16, Component.literal("×" + n), UiAtlas.RED, b -> {
                        if (balance() < n * l.chips) return;
                        send(l.command ? CashierBlockEntity.ACTION_CASH_CMD : CashierBlockEntity.ACTION_SELL, l.index, n);
                        click();
                    }).scale(0.8f).when(() -> balance() >= n * l.chips).tip(() -> l.command
                            ? Component.translatable("screen.heralizcasino.cashier.cash_tip", Chips.format(n * l.chips), n * l.amount, l.name)
                            : Component.translatable("screen.heralizcasino.cashier.sell_tip", Chips.format(n * l.chips), n, l.name)), sellX + k * (BW + 2), y);
                }
            }
        }
    }

    /** Boutons de la ligne des jetons du serveur : acheter 10 / 100 / 1 000 jetons de casino, en revendre 10 / 100 / tous. */
    private void serverWidgets(Line l, int buyX, int sellX, int y) {
        for (int k = 0; k < 3 && l.buy; k++) {
            final long target = SERVER_BUY[k];
            UiButton b = place(new UiButton(BW, 16, Component.literal("+" + Chips.format(units(l, target, false) * l.chips)), UiAtlas.GREEN, x -> {
                long u = units(l, target, false);
                if (serverBalance() < u * l.amount) return;
                send(CashierBlockEntity.ACTION_BUY_SERVER, 0, u);
                chipSound();
            }).scale(0.8f).when(() -> serverBalance() >= units(l, target, false) * l.amount).tip(() -> {
                long u = units(l, target, false);
                return Component.translatable("screen.heralizcasino.cashier.server_buy_tip", PontEconomieClient.format(u * l.amount), Chips.text(u * l.chips));
            }), buyX + k * (BW + 2), y);
            serverButtons.add(b);
        }
        for (int k = 0; k < 3 && l.sell; k++) {
            final long target = SERVER_SELL[k];
            Component label = target <= 0 ? Component.translatable("screen.heralizcasino.cashier.all") : Component.literal("×" + Chips.format(units(l, target, true) * l.chips));
            UiButton b = place(new UiButton(BW, 16, label, UiAtlas.RED, x -> {
                long u = units(l, target, true);
                if (u <= 0 || balance() < u * l.chips) return;
                send(CashierBlockEntity.ACTION_SELL_SERVER, 0, u);
                click();
            }).scale(0.8f).when(() -> {
                long u = units(l, target, true);
                return u > 0 && balance() >= u * l.chips;
            }).tip(() -> {
                long u = Math.max(1, units(l, target, true));
                return Component.translatable("screen.heralizcasino.cashier.server_sell_tip", Chips.text(u * l.chips),
                        PontEconomieClient.format(CasinoConfig.get().caisse.economie.reventeServeur(u)));
            }), sellX + k * (BW + 2), y);
            serverButtons.add(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        UiKit.panel(g, lx, ly, lw, lh);
        int sellX = lx + lw - 8 - 3 * BW - 4;
        int buyX = sellX - 10 - 3 * BW - 4;
        UiKit.text(g, Component.translatable("screen.heralizcasino.cashier.title"), lx + 10, ly + 8, GOLD, 0.8f, -1, true);
        if (anyBuy) UiKit.text(g, Component.translatable("screen.heralizcasino.cashier.buy"), buyX + (3 * BW + 4) / 2f, ly + 8, GREEN, 0.8f, 0, true);
        UiKit.text(g, Component.translatable("screen.heralizcasino.cashier.sell"), sellX + (3 * BW + 4) / 2f, ly + 8, RED, 0.8f, 0, true);
        if (lines.isEmpty()) UiKit.text(g, Component.translatable("screen.heralizcasino.cashier.empty"), lx + lw / 2f, ly + 30, GREY, 1f, 0, true);
        // barre de defilement quand toutes les lignes ne tiennent pas
        int max = lines.size() - shown;
        if (max > 0) {
            int hh = shown * ROW, th = Math.max(8, hh * shown / lines.size()), ty = ly + 24 + (hh - th) * defile / max;
            g.fill(lx + lw - 4, ty, lx + lw - 3, ty + th, 0xA0C89A48);
        }
        for (int r = 0; r < shown && defile + r < lines.size(); r++) {
            Line l = lines.get(defile + r);
            int y = ly + 24 + r * ROW;
            if (r > 0) g.fill(lx + 8, y - 1, lx + lw - 8, y, 0x30C89A48);
            int tx = lx + 30;
            if (l.server) {
                // jeton de laiton du serveur (glyphe du mod economie, agrandi), solde du compte, taux
                g.pose().pushPose();
                g.pose().translate(lx + 10, y + 2, 0);
                g.pose().scale(1.75f, 1.75f, 1);
                g.drawString(font, PontEconomieClient.glyphe(), 0, 0, 0xFFFFFFFF, false);
                g.pose().popPose();
                Component bal = Component.translatable("screen.heralizcasino.cashier.server_balance", PontEconomieClient.montant(serverBalance()));
                UiKit.fitted(g, bal, tx, y + 2, buyX - tx - 6, CREAM, 1f, -1, true);
                double fee = CasinoConfig.get().caisse.economie.commissionRevente;
                Component rate = fee > 0
                        ? Component.translatable("screen.heralizcasino.cashier.rate_server_fee", PontEconomieClient.format(l.amount), Chips.text(l.chips),
                                String.format(java.util.Locale.ROOT, "%.0f", fee * 100))
                        : Component.translatable("screen.heralizcasino.cashier.rate_server", PontEconomieClient.format(l.amount), Chips.text(l.chips));
                UiKit.fitted(g, rate, tx, y + 12, buyX - tx - 6, GOLD, 0.7f, -1, false);
                continue;
            }
            g.renderItem(l.icon, lx + 10, y + 2);
            UiKit.fitted(g, l.name, tx, y + 2, buyX - tx - 6, CREAM, 1f, -1, true);
            Component rate = l.command
                    ? Component.translatable("screen.heralizcasino.cashier.rate_cmd", Chips.format(l.chips), l.amount, l.name)
                    : Component.translatable("screen.heralizcasino.cashier.rate", Chips.format(l.chips));
            UiKit.text(g, rate, tx, y + 12, GOLD, 0.7f, -1, false);
            if (!l.command) UiKit.text(g, Component.translatable("screen.heralizcasino.cashier.have", have(l)), buyX - 6, y + 12, GREY, 0.7f, 1, false);
        }
        super.render(g, mx, my, partial);
    }

    @Override
    protected Component status() {
        return Component.translatable("screen.heralizcasino.cashier.footer");
    }

    /** Le premier rang affiche fait partie de la composition : le changer refait les boutons des lignes. */
    @Override
    protected int layoutKey() {
        return defile;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (lines.size() > shown && mx >= lx && mx < lx + lw && my >= ly && my < ly + lh) {
            defile = Math.max(0, Math.min(lines.size() - shown, defile - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    protected boolean overUi(int mx, int my) {
        return super.overUi(mx, my) || (mx >= lx && mx < lx + lw && my >= ly && my < ly + lh);
    }

    // =================================================================== tests automatiques
    /**
     * Tests automatiques : bouton de la ligne des jetons du serveur (achat : k = 0, 1, 2 pour +10, +100, +1 000 ;
     * revente : k = 3, 4, 5 pour 10, 100, tout), ou null.
     */
    @Nullable
    public UiButton testServerButton(int k) {
        return k >= 0 && k < serverButtons.size() ? serverButtons.get(k) : null;
    }
}
