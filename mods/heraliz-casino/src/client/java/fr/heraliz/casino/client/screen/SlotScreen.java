package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.SlotBlockEntity;
import fr.heraliz.casino.client.camera.CasinoCamera;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.game.slot.LoupRules;
import fr.heraliz.casino.game.slot.NicheRules;
import fr.heraliz.casino.game.slot.OlympeRules;
import fr.heraliz.casino.game.slot.RottenRules;
import fr.heraliz.casino.game.slot.SlotEngine;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Machines a sous (Rotten, Portes de l'Olympe, Souffle du Loup, La Niche) : le joueur s'assoit sur le tabouret face a l'ecran.
 * Mise, LANCER, et BONUS pour acheter directement les tours gratuits (avec confirmation).
 */
public class SlotScreen extends MachineScreen<SlotBlockEntity> {
    private boolean buyOpen;
    /** Achat en attente de confirmation (0 aucun) et fin du delai de confirmation. */
    private int confirm;
    private long confirmUntil;

    public SlotScreen(SlotBlockEntity be) {
        super(be);
    }

    private String key(String k) {
        return "screen.heralizcasino." + be.type.id + "." + k;
    }

    @Override
    protected void init() {
        super.init();
        send(SlotBlockEntity.ACTION_SIT);
        // intro de la machine sur son ecran, une fois par installation (pas a chaque redimensionnement de la fenetre)
        if (!introPlayed) {
            introPlayed = true;
            if (!playing()) be.introStart = now();
        }
    }

    private boolean introPlayed;

    @Override
    public void removed() {
        send(SlotBlockEntity.ACTION_LEAVE);
        super.removed();
    }

    private boolean playing() {
        return be.playing(now());
    }

    private boolean taken() {
        return be.seated != null && !isMe(be.seated);
    }

    private boolean cinematic() {
        return playing() && be.cinematic(partialNow() - be.spinStart);
    }

    @Override
    protected boolean showBalance() {
        return !cinematic();
    }

    @Override
    protected int layoutKey() {
        return (buyOpen ? 1 : 0) + confirm * 10;
    }

    @Override
    protected boolean keepDock() {
        return buyOpen;
    }

    @Override
    protected void dock(Dock d) {
        d.bet();
        SlotEngine e = be.engine();
        if (e.buyPrice(1) > 0 || e.buyPrice(2) > 0) {
            d.button(Component.translatable("screen.heralizcasino.rotten.bonus"), UiAtlas.GOLD, 58, () -> {
                buyOpen = !buyOpen;
                confirm = 0;
                click();
            }).when(() -> !playing() && !taken()).tip(() -> Component.translatable("screen.heralizcasino.rotten.bonus_tip"));
        }
        d.button(Component.translatable("screen.heralizcasino.spin"), UiAtlas.RED, 96, this::primary).scale(1.05f).when(() -> !playing() && !taken());
    }

    @Override
    protected void extraWidgets() {
        if (!buyOpen) return;
        SlotEngine e = be.engine();
        int y = dockY - 48, cx = width / 2;
        boolean two = e.buyPrice(1) > 0 && e.buyPrice(2) > 0;
        if (e.buyPrice(1) > 0) addBuy(two ? cx - 124 : cx - 60, y, 1, e.buyPrice(1), UiAtlas.GREEN);
        if (e.buyPrice(2) > 0) addBuy(two ? cx + 4 : cx - 60, y, 2, e.buyPrice(2), UiAtlas.RED);
    }

    private void addBuy(int x, int y, int kind, double price, int style) {
        place(new UiButton(120, 20, Component.empty(), style, b -> buy(kind)).label(() -> confirm == kind
                        ? Component.translatable("screen.heralizcasino.rotten.confirm", Chips.format(cost(price)))
                        : Component.translatable(key("buy" + kind), Chips.format(cost(price))))
                .when(() -> !playing() && !taken() && balance() >= cost(price))
                .tip(() -> Component.translatable(key("buy_tip" + kind), mult(price))), x, y).scale(0.8f);
    }

    private long cost(double price) {
        return (long) Math.ceil(bet * price);
    }

    private void buy(int kind) {
        double price = be.engine().buyPrice(kind);
        if (!checkBet(cost(price))) return;
        if (confirm != kind || System.currentTimeMillis() > confirmUntil) {
            confirm = kind;
            confirmUntil = System.currentTimeMillis() + 4000;
            click();
            return;
        }
        send(SlotBlockEntity.ACTION_BUY, bet, kind);
        chipSound();
        confirm = 0;
        buyOpen = false;
    }

    @Override
    protected void primary() {
        if (playing() || taken() || !checkBet(bet)) return;
        buyOpen = false;
        confirm = 0;
        send(SlotBlockEntity.ACTION_SPIN, bet);
    }

    @Override
    public void tick() {
        super.tick();
        if (confirm != 0 && System.currentTimeMillis() > confirmUntil) confirm = 0;
    }

    @Override
    protected int cameraMode() {
        return isMe(be.seated) || playing() ? MachineDesign.CAM_PLAY : MachineDesign.CAM_MENU;
    }

    /**
     * Pas de petits messages sous l'ecran (regles au repos, "bonne chance", "perdu"...) : la machine affiche deja tout.
     * Seule exception : la machine est prise par un autre joueur (sinon les boutons grises ne s'expliquent pas).
     */
    @Override
    protected Component status() {
        if (taken()) return Component.translatable("screen.heralizcasino.rotten.taken", be.spinnerName.isEmpty() ? "?" : be.spinnerName);
        return null;
    }

    @Override
    protected int statusColor() {
        return GREY;
    }

    /**
     * Bonus en cours (tours gratuits, relances, roue, grille agrandie), ou n'importe quel tour quand l'ecran est
     * agrandi : la barre du bas disparait, meme sous la souris ; on ne voit que la machine. Elle revient des que le
     * gain total s'affiche.
     */
    @Override
    protected boolean hideDock() {
        if (!playing() || CasinoCamera.summary() > 0) return false;
        if (CasinoCamera.immersive() > 0.5f || cinematic()) return true;
        SlotEngine.Status s = be.status(partialNow() - be.spinStart);
        return s.mode() > 0 || s.kind() == 1;
    }

    /** Ecran agrandi : la barre ne se range que pendant l'intro et les tours (voir hideDock), pas au repos. */
    @Override
    protected boolean autoHide() {
        return CasinoCamera.focus() > 0.5f;
    }

    @Override
    protected List<Component> infoExtra() {
        List<Component> out = new ArrayList<>();
        CasinoConfig.Settings c = CasinoConfig.get();
        switch (be.type) {
            case OLYMPE -> {
                OlympeRules r = c.portesOlympe;
                String[] names = {"blue", "green", "yellow", "purple", "red", "chalice", "ring", "hourglass", "crown"};
                for (int s = 8; s >= 0; s--) {
                    double[] p = r.pays[s];
                    out.add(Component.translatable("screen.heralizcasino.portes_olympe.pay", Component.translatable("screen.heralizcasino.portes_olympe.sym." + names[s]),
                            mult(p[0] * r.payScale), mult(p[1] * r.payScale), mult(p[2] * r.payScale)));
                }
                out.add(Component.translatable("screen.heralizcasino.rotten.pay_max", mult(r.maxWin)));
            }
            case LOUP -> {
                LoupRules r = c.souffleLoup;
                String[] names = {"ten", "jack", "queen", "king", "ace", "pig_straw", "pig_stick", "pig_brick"};
                for (int s = 7; s >= 0; s--) {
                    double[] p = r.pays[s];
                    out.add(Component.translatable("screen.heralizcasino.souffle_loup.pay", Component.translatable("screen.heralizcasino.souffle_loup.sym." + names[s]),
                            mult(p[0] * r.payScale), mult(p[1] * r.payScale), mult(p[2] * r.payScale)));
                }
                out.add(Component.translatable("screen.heralizcasino.souffle_loup.pay_jackpots", mult(r.jackpots[0] * r.payScale), mult(r.jackpots[1] * r.payScale),
                        mult(r.jackpots[2] * r.payScale), mult(r.jackpots[3] * r.payScale), mult(r.jackpots[4] * r.payScale)));
                out.add(Component.translatable("screen.heralizcasino.souffle_loup.pay_houses", mult(range(r.strawValues, false) * r.payScale),
                        mult(range(r.strawValues, true) * r.payScale), mult(range(r.stickValues, false) * r.payScale), mult(range(r.stickValues, true) * r.payScale),
                        mult(range(r.brickValues, false) * r.payScale), mult(range(r.brickValues, true) * r.payScale)));
                out.add(Component.translatable("screen.heralizcasino.rotten.pay_max", mult(r.maxWin)));
            }
            case NICHE -> {
                NicheRules r = c.laNiche;
                String[] names = {"ten", "jack", "queen", "king", "ace", "bone", "collar", "dog1", "dog2", "dog3", "dog4"};
                for (int s = 10; s >= 0; s--) {
                    double[] p = r.pays[s];
                    out.add(Component.translatable("screen.heralizcasino.la_niche.pay", Component.translatable("screen.heralizcasino.la_niche.sym." + names[s]),
                            mult(p[0] * r.payScale), mult(p[1] * r.payScale), mult(p[2] * r.payScale)));
                }
                out.add(Component.translatable("screen.heralizcasino.la_niche.pay_wild"));
                out.add(Component.translatable("screen.heralizcasino.la_niche.pay_bonus", r.freeSpins, mult(r.scatterPay * r.payScale)));
                out.add(Component.translatable("screen.heralizcasino.la_niche.pay_grids"));
                out.add(Component.translatable("screen.heralizcasino.rotten.pay_max", mult(r.maxWin)));
            }
            default -> {
                RottenRules r = c.rotten;
                String[] names = {"ten", "jack", "queen", "king", "ace", "zgreen", "zblue", "zyellow", "zred", "clown"};
                for (int s = 9; s >= 0; s--) {
                    double[] p = r.pays[s];
                    out.add(Component.translatable("screen.heralizcasino.rotten.pay", Component.translatable("screen.heralizcasino.rotten.sym." + names[s]),
                            mult(p[0] * r.payScale), mult(p[1] * r.payScale), mult(p[2] * r.payScale), mult(p[3] * r.payScale)));
                }
                out.add(Component.translatable("screen.heralizcasino.rotten.pay_max", mult(r.maxWin)));
            }
        }
        return out;
    }

    /** Plus petite ou plus grande valeur d'une liste. */
    private static double range(double[] v, boolean max) {
        double x = v[0];
        for (double d : v) x = max ? Math.max(x, d) : Math.min(x, d);
        return x;
    }
}
