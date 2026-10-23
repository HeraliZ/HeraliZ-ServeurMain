package fr.heraliz.casino.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.client.screen.ui.UiKit;
import fr.heraliz.casino.command.Lignes;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.Simulator;
import fr.heraliz.casino.game.cards.BlackjackRules;
import fr.heraliz.casino.game.crash.CrashRules;
import fr.heraliz.casino.game.duel.DuelRules;
import fr.heraliz.casino.game.mines.MinesRules;
import fr.heraliz.casino.game.plinko.PlinkoRules;
import fr.heraliz.casino.game.race.RaceRules;
import fr.heraliz.casino.game.roulette.RouletteRules;
import fr.heraliz.casino.game.slot.RottenRules;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.net.AdminConfigPacket;
import fr.heraliz.casino.net.AdminStatsPacket;
import fr.heraliz.casino.net.CasinoNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.resources.language.I18n;

/**
 * Tablette du Directeur (operateurs) : deux onglets.
 * <ul>
 *   <li>Reglages : probabilites, gains, mises et durees de chaque machine, avec la redistribution calculee en direct.</li>
 *   <li>Statistiques : mise, paye, benefice du casino, redistribution reelle, joueurs... par jour, par machine et pour
 *   tout le casino, avec deux graphiques (benefice et volume par jour), un tableau par jour et le classement des joueurs.</li>
 * </ul>
 */
public class AdminScreen extends Screen {
    // ---- encres et couleurs des graphiques (surface sombre ; palette validee : bleu / rouge / vert d'eau)
    private static final int INK = 0xFFFFFF, INK2 = 0xC3C2B7, MUTED = 0x898781, GRID = 0x2C2C2A, BASE = 0x4A4A46;
    private static final int UP = 0x3987E5, DOWN = 0xE66767, VOLUME = 0x199E70, GOOD = 0x0CA30C, WARN = 0xFAB219;

    /** Formats d'affichage des reglages. */
    private static final int NUM = 0, PCT = 1, BOOL = 2, MULT = 3, SEC = 4, CHIPS = 5, MINUTES = 6;

    /**
     * Un reglage : libelle, lecture / ecriture dans la copie de travail, pas, bornes, format ; info : ce que la valeur
     * donne en jeu ("26 % · 1 partie sur 770"), affiche a cote (null : rien).
     */
    private record Knob(Component label, DoubleSupplier get, DoubleConsumer set, double step, double min, double max, int format,
                        @Nullable java.util.function.Supplier<Component> info) {
        Knob(Component label, DoubleSupplier get, DoubleConsumer set, double step, double min, double max, int format) {
            this(label, get, set, step, min, max, format, null);
        }
    }

    private static final int TAB_SETTINGS = 0, TAB_STATS = 1;
    private static int tab = TAB_SETTINGS;
    private static int period = 2, view = 0;
    private static final int[] PERIOD_DAYS = {1, 7, 30, -1};

    private CasinoConfig.Settings work = CasinoConfig.copy();
    private MachineType selected;
    /** Statistiques : machine choisie (null = tout le casino). */
    @Nullable
    private MachineType statsSel;
    private final List<Knob> knobs = new ArrayList<>();
    private double coef = 1;
    private double[] base = new double[0];
    private double base2, base3;
    private int px, py, pw, ph, listW;
    private static final int KNOB_Y = 50, KNOB_H = 16;
    /**
     * Pages de reglages d'une machine : "main", puis les roues du Loup, puis les probabilites (lecture seule) ; pour la
     * caisse : l'entree du casino (prix, ticket, expulsion des fauches).
     */
    private static final String MAIN = "main", WHEEL = "wheel", SUPER = "super", ODDS = "odds", ENTRY = "entry";
    private int page;
    /** Page "Lumieres" (lumiere de toutes les machines) a la place des reglages d'une machine. */
    private boolean lights;
    private int oddsScroll, oddsHeight;
    private boolean dirty;
    @Nullable
    private JsonObject stats;
    private boolean loading;
    private int tableOffset;

    public AdminScreen(@Nullable MachineType type) {
        super(Component.translatable("screen.heralizcasino.admin.title"));
        this.selected = type == null || type.board() ? MachineType.ROTTEN : type;
        this.statsSel = type == null || !type.game() ? null : type;
    }

    @Override
    protected void init() {
        // style des interfaces HeraliZ : echelle forcee (canevas 640 x 360), comme les ecrans des clans
        fr.heraliz.casino.client.screen.kit.Echelle.forcer(this);
        pw = Math.min(width - 16, 470);
        ph = Math.min(height - 16, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listW = 118;
        rebuild();
        if (tab == TAB_STATS) requestStats();
    }

    /** Tests automatiques : affiche un onglet / une vue. */
    public void testShow(int tab, int view, @Nullable MachineType sel) {
        boolean load = AdminScreen.tab != tab;
        AdminScreen.tab = tab;
        AdminScreen.view = view;
        statsSel = sel;
        rebuild();
        if (load && tab == TAB_STATS) requestStats();
    }

    /** Statistiques recues du serveur. */
    public void setStats(String json) {
        try {
            stats = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            stats = null;
        }
        loading = false;
    }

    private void requestStats() {
        loading = true;
        CasinoNet.CHANNEL.sendToServer(new AdminStatsPacket.Request());
    }

    // =================================================================== composition
    private void rebuild() {
        clearWidgets();
        knobs.clear();
        // onglets
        UiButton t1 = add(new UiButton(78, 16, Component.translatable("screen.heralizcasino.admin.tab_settings"), UiAtlas.DARK, b -> {
            tab = TAB_SETTINGS;
            click();
            rebuild();
        }).scale(0.85f), px + pw - 190, py + 5);
        t1.selected = tab == TAB_SETTINGS;
        UiButton t2 = add(new UiButton(84, 16, Component.translatable("screen.heralizcasino.admin.tab_stats"), UiAtlas.DARK, b -> {
            tab = TAB_STATS;
            click();
            rebuild();
            requestStats();
        }).scale(0.85f), px + pw - 108, py + 5);
        t2.selected = tab == TAB_STATS;
        add(new UiButton(16, 16, Component.literal("✕"), UiAtlas.DARK, b -> onClose()).round(), px + pw - 21, py + 5);
        if (tab == TAB_SETTINGS) buildSettings();
        else buildStats();
    }

    /** Pages de la machine choisie. */
    private List<String> pages() {
        if (lights) return List.of(MAIN);
        if (selected == MachineType.LOUP) return List.of(MAIN, WHEEL, SUPER, ODDS);
        if (selected == MachineType.CAISSE) return List.of(MAIN, ENTRY);
        return selected.game() ? List.of(MAIN, ODDS) : List.of(MAIN);
    }

    private String pageKey() {
        List<String> p = pages();
        return p.get(Math.max(0, Math.min(p.size() - 1, page)));
    }

    /** Haut de la liste des reglages (sous les onglets de page quand la machine en a plusieurs). */
    private int knobTop() {
        if (lights) return py + KNOB_Y - 10;
        return py + (pages().size() > 1 ? KNOB_Y + 14 : KNOB_Y);
    }

    /** Pas des lignes de reglages : resserre quand la fenetre est petite et la page bien remplie. */
    private int knobPitch() {
        int room = py + ph - 32 - knobTop();
        return Math.max(lights ? 10 : 11, Math.min(KNOB_H, room / Math.max(1, knobs.size())));
    }

    /** Tests automatiques : affiche la page des lumieres. */
    public void testLights() {
        tab = TAB_SETTINGS;
        lights = true;
        page = 0;
        rebuild();
    }

    /** Tests automatiques : affiche une page de reglages d'une machine. */
    public void testPage(MachineType type, int page) {
        tab = TAB_SETTINGS;
        selected = type;
        lights = false;
        this.page = page;
        oddsScroll = 0;
        rebuild();
    }

    private void buildSettings() {
        buildKnobs();
        MachineType[] types = java.util.Arrays.stream(MachineType.values()).filter(x -> !x.board()).toArray(MachineType[]::new);
        // une ligne de plus en bas de la liste : les lumieres de toutes les machines
        int rowH = Math.min(18, (ph - 40) / (types.length + 1));
        for (int i = 0; i < types.length; i++) {
            MachineType t = types[i];
            UiButton b = add(new UiButton(listW, rowH - 2, t.displayName(), UiAtlas.DARK, x -> {
                selected = t;
                lights = false;
                coef = 1;
                dirty = false;
                page = 0;
                oddsScroll = 0;
                work = CasinoConfig.copy();
                click();
                rebuild();
            }).scale(0.7f).color(t.glow), px + 8, py + 26 + i * rowH);
            b.bold = false;
            b.selected = !lights && t == selected;
        }
        UiButton lb = add(new UiButton(listW, rowH - 2, Component.translatable("screen.heralizcasino.admin.lights"), UiAtlas.DARK, x -> {
            lights = true;
            coef = 1;
            dirty = false;
            page = 0;
            work = CasinoConfig.copy();
            click();
            rebuild();
        }).scale(0.7f).color(UiKit.GOLD), px + 8, py + 26 + types.length * rowH + 2);
        lb.bold = false;
        lb.selected = lights;
        int kx = px + listW + 18, kw = px + pw - 10 - kx;
        List<String> pages = pages();
        if (pages.size() > 1) {
            int bx = kx;
            for (int i = 0; i < pages.size(); i++) {
                final int k = i;
                Component label = Component.translatable("screen.heralizcasino.admin.page." + pages.get(i));
                int w = Math.round(font.width(label) * 0.7f) + 14;
                UiButton b = add(new UiButton(w, 13, label, UiAtlas.DARK, x -> {
                    page = k;
                    oddsScroll = 0;
                    click();
                    rebuild();
                }).scale(0.7f), bx, py + KNOB_Y - 3);
                b.selected = pageKey().equals(pages.get(i));
                bx += w + 2;
            }
        }
        int pitch = knobPitch(), bh = Math.min(14, pitch - 1);
        for (int i = 0; i < knobs.size(); i++) {
            Knob k = knobs.get(i);
            int y = knobTop() + i * pitch;
            add(new UiButton(16, bh, Component.literal("−"), UiAtlas.DARK, x -> change(k, -1)), kx + kw - 72, y);
            add(new UiButton(16, bh, Component.literal("+"), UiAtlas.DARK, x -> change(k, 1)), kx + kw - 16, y);
        }
        int by = py + ph - 26;
        add(new UiButton(96, 18, Component.translatable("screen.heralizcasino.admin.save"), UiAtlas.GOLD, x -> save()), px + pw - 104, by);
        add(new UiButton(84, 18, Component.translatable("screen.heralizcasino.admin.defaults"), UiAtlas.DARK, x -> defaults()).scale(0.85f), px + pw - 194, by);
    }

    private void buildStats() {
        List<MachineType> list = statTypes();
        int n = list.size() + 1;
        int rowH = Math.min(18, (ph - 40) / n);
        UiButton g = add(new UiButton(listW, rowH - 2, Component.translatable("screen.heralizcasino.admin.whole"), UiAtlas.DARK, x -> {
            statsSel = null;
            tableOffset = 0;
            click();
            rebuild();
        }).scale(0.7f).color(UiKit.GOLD), px + 8, py + 26);
        g.bold = false;
        g.selected = statsSel == null;
        for (int i = 0; i < list.size(); i++) {
            MachineType t = list.get(i);
            UiButton b = add(new UiButton(listW, rowH - 2, t.displayName(), UiAtlas.DARK, x -> {
                statsSel = t;
                tableOffset = 0;
                click();
                rebuild();
            }).scale(0.7f).color(t.glow), px + 8, py + 26 + (i + 1) * rowH);
            b.bold = false;
            b.selected = t == statsSel;
        }
        int kx = px + listW + 18, right = px + pw - 10;
        String[] pk = {"today", "d7", "d30", "all"};
        int bx = right - 4 * 34 + 2;
        for (int i = 0; i < 4; i++) {
            final int k = i;
            UiButton b = add(new UiButton(32, 14, Component.translatable("screen.heralizcasino.admin.period." + pk[i]), UiAtlas.DARK, x -> {
                period = k;
                click();
                rebuild();
            }).scale(0.7f), bx + i * 34, py + 26);
            b.selected = period == i;
        }
        String[] vk = {"overview", "days", "players"};
        int vx = kx;
        for (int i = 0; i < 3; i++) {
            final int k = i;
            int w = i == 0 ? 76 : 58;
            UiButton b = add(new UiButton(w, 14, Component.translatable("screen.heralizcasino.admin.view." + vk[i]), UiAtlas.DARK, x -> {
                view = k;
                tableOffset = 0;
                click();
                rebuild();
            }).scale(0.7f), vx, py + 44);
            b.selected = view == i;
            vx += w + 2;
        }
        add(new UiButton(16, 14, Component.literal("⟳"), UiAtlas.DARK, x -> {
            click();
            requestStats();
        }).tip(() -> Component.translatable("screen.heralizcasino.admin.refresh")), right - 16, py + 44);
    }

    private static List<MachineType> statTypes() {
        List<MachineType> out = new ArrayList<>();
        for (MachineType t : MachineType.values()) if (t.game()) out.add(t);
        return out;
    }

    private UiButton add(UiButton b, int x, int y) {
        b.setX(x);
        b.setY(y);
        addRenderableWidget(b);
        return b;
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // =================================================================== reglages
    private void change(Knob k, int dir) {
        double step = k.step * (hasShiftDown() ? 10 : 1);
        double v = k.format == BOOL ? (k.get.getAsDouble() > 0.5 ? 0 : 1) : k.get.getAsDouble() + dir * step;
        v = Math.max(k.min, Math.min(k.max, v));
        v = Math.round(v * 10000) / 10000.0;
        k.set.accept(v);
        dirty = true;
        click();
    }

    private void save() {
        if (lights) {
            CasinoNet.CHANNEL.sendToServer(new AdminConfigPacket(CasinoConfig.LIGHTS, CasinoConfig.GSON.toJson(work.lumieres)));
            dirty = false;
            click();
            return;
        }
        Object section = CasinoConfig.section(selected, work);
        CasinoNet.CHANNEL.sendToServer(new AdminConfigPacket(selected.id, CasinoConfig.GSON.toJson(section)));
        dirty = false;
        click();
    }

    /** Remet les reglages d'usine de la machine choisie (a enregistrer ensuite). */
    private void defaults() {
        CasinoConfig.Settings fresh = new CasinoConfig.Settings().sanitized();
        CasinoConfig.Settings copy = work;
        if (lights) {
            copy.lumieres = CasinoConfig.defaultLights();
        } else switch (selected) {
            case CAISSE -> copy.caisse = fresh.caisse;
            case ROTTEN -> copy.rotten = fresh.rotten;
            case OLYMPE -> copy.portesOlympe = fresh.portesOlympe;
            case LOUP -> copy.souffleLoup = fresh.souffleLoup;
            case NICHE -> copy.laNiche = fresh.laNiche;
            case TABLEAU, TABLEAU_PERDANTS, TABLEAU_PARIEURS -> {
            }
            case PLINKO -> copy.plinko = fresh.plinko;
            case BLACKJACK -> copy.blackjack = fresh.blackjack;
            case ROULETTE -> copy.roulette = fresh.roulette;
            case CRASH -> copy.crash = fresh.crash;
            case MINES -> copy.mines = fresh.mines;
            case COURSE -> copy.course = fresh.course;
            case PIERRE_FEUILLE_CISEAUX -> copy.pierreFeuilleCiseaux = fresh.pierreFeuilleCiseaux;
            case BATAILLE_NAVALE -> copy.batailleNavale = fresh.batailleNavale;
        }
        work = copy.sanitized();
        coef = 1;
        dirty = true;
        click();
        rebuild();
    }

    private static Component k(String key) {
        return Component.translatable("screen.heralizcasino.admin.k." + key);
    }

    private void limits(DoubleSupplier min, DoubleConsumer setMin, DoubleSupplier max, DoubleConsumer setMax) {
        knobs.add(new Knob(k("min_bet"), min, setMin, 1, 1, 1_000_000, CHIPS));
        knobs.add(new Knob(k("max_bet"), max, setMax, 50, 1, 10_000_000, CHIPS));
    }

    /** "1 partie sur N" a cote d'un reglage de probabilite. */
    private static Component oneIn(double p) {
        return Component.translatable("screen.heralizcasino.admin.one_in", Simulator.oneIn(p));
    }

    /** Poids des cases de la roue du Loup (ou de la super roue) : a cote, la part de chaque case et sa frequence en jeu. */
    private void wheelKnobs(boolean sup) {
        fr.heraliz.casino.game.slot.LoupRules r = work.souffleLoup;
        String[] names = sup ? new String[]{"s_major", "s_grand", "s_super", "s_buzz", "s_mega", "s_manor"}
                : new String[]{"w_mini", "w_minor", "w_major", "w_grand", "w_buzz", "w_mega", "w_manor", "w_super"};
        int[] w = sup ? r.superWheel : r.wheel;
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            knobs.add(new Knob(k(names[i]), () -> w[k], v -> {
                int old = w[k];
                w[k] = (int) Math.round(v);
                int sum = 0;
                for (int x : w) sum += x;
                // la roue doit pouvoir tomber sur au moins une case
                if (sum <= 0) w[k] = old;
            }, 1, 0, 200, NUM, () -> {
                double share = (sup ? Simulator.loupSuperShares(r) : Simulator.loupWheelShares(r))[k];
                double turn = sup ? Simulator.loupSuper(r) : Simulator.loupClassic(r);
                return Component.translatable("screen.heralizcasino.admin.share", Simulator.pct(share), Simulator.oneIn(turn * share));
            }));
        }
    }

    /** Entree du casino : prix, duree du ticket, expulsion des joueurs fauches. */
    private void entryKnobs() {
        CasinoConfig.Entree e = work.caisse.entree;
        knobs.add(new Knob(k("entry_price"), () -> e.prix, v -> e.prix = (long) v, 10, 0, 1_000_000, CHIPS,
                () -> e.prix <= 0 ? Component.translatable("screen.heralizcasino.admin.entry_free") : Component.empty()));
        knobs.add(new Knob(k("entry_minutes"), () -> e.validiteMinutes, v -> e.validiteMinutes = (int) v, 5, 0, 1440, MINUTES,
                () -> e.validiteMinutes <= 0 ? Component.translatable("screen.heralizcasino.admin.entry_once") : Component.empty()));
        knobs.add(new Knob(k("eject_broke"), () -> e.expulserFauches ? 1 : 0, v -> e.expulserFauches = v > 0.5, 1, 0, 1, BOOL));
        knobs.add(new Knob(k("eject_watchers"), () -> e.expulserSpectateurs ? 1 : 0, v -> e.expulserSpectateurs = v > 0.5, 1, 0, 1, BOOL));
        knobs.add(new Knob(k("eject_delay"), () -> e.delaiExpulsion, v -> e.delaiExpulsion = (int) v, 1, 1, 60, SEC));
        CasinoConfig.JetonsServeur eco = work.caisse.economie;
        knobs.add(new Knob(k("eco_entry"), () -> eco.entree ? 1 : 0, v -> eco.entree = v > 0.5, 1, 0, 1, BOOL));
    }

    /**
     * Page "Lumieres" : lumiere emise par chaque machine (0 a 15), et une ligne pour les regler toutes d'un coup. Les
     * machines posees suivent dans la seconde apres l'enregistrement.
     */
    private void lightKnobs() {
        java.util.Map<String, Integer> m = work.lumieres;
        knobs.add(new Knob(Component.translatable("screen.heralizcasino.admin.lights_all").withStyle(s -> s.withBold(true)), () -> {
            double sum = 0;
            for (MachineType t : MachineType.values()) sum += work.light(t);
            return Math.round(sum / MachineType.values().length);
        }, v -> {
            for (MachineType t : MachineType.values()) m.put(t.id, (int) Math.round(v));
        }, 1, 0, 15, NUM, () -> {
            int first = work.light(MachineType.values()[0]);
            for (MachineType t : MachineType.values()) {
                if (work.light(t) != first) return Component.translatable("screen.heralizcasino.admin.lights_mixed");
            }
            return Component.empty();
        }));
        for (MachineType t : MachineType.values()) {
            if (t.board()) continue;
            knobs.add(new Knob(t.displayName(), () -> work.light(t), v -> m.put(t.id, (int) Math.round(v)), 1, 0, 15, NUM,
                    () -> work.light(t) == t.light ? Component.empty() : Component.translatable("screen.heralizcasino.admin.lights_factory", t.light)));
        }
        // les trois ecrans de classement sur une seule ligne (la page tient ainsi sur un petit ecran)
        MachineType[] boards = java.util.Arrays.stream(MachineType.values()).filter(MachineType::board).toArray(MachineType[]::new);
        knobs.add(new Knob(Component.translatable("screen.heralizcasino.admin.lights_boards"), () -> {
            double sum = 0;
            for (MachineType t : boards) sum += work.light(t);
            return Math.round(sum / boards.length);
        }, v -> {
            for (MachineType t : boards) m.put(t.id, (int) Math.round(v));
        }, 1, 0, 15, NUM, () -> {
            for (MachineType t : boards) {
                if (work.light(t) != t.light) return Component.translatable("screen.heralizcasino.admin.lights_factory", t.light);
            }
            return Component.empty();
        }));
    }

    private void buildKnobs() {
        if (lights) {
            lightKnobs();
            return;
        }
        String pk = pageKey();
        if (pk.equals(ODDS)) return;
        if (pk.equals(WHEEL) || pk.equals(SUPER)) {
            wheelKnobs(pk.equals(SUPER));
            return;
        }
        if (pk.equals(ENTRY)) {
            entryKnobs();
            return;
        }
        switch (selected) {
            case ROTTEN -> {
                RottenRules r = work.rotten;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("pay_coef"), () -> r.payScale, v -> r.payScale = v, 0.005, 0.2, 3, PCT));
                knobs.add(new Knob(k("buy_savant"), () -> r.buySavant, v -> r.buySavant = v, 1, 0, 2000, MULT));
                knobs.add(new Knob(k("buy_invasion"), () -> r.buyInvasion, v -> r.buyInvasion = v, 5, 0, 5000, MULT));
                knobs.add(new Knob(k("max_win"), () -> r.maxWin, v -> r.maxWin = v, 100, 100, 100_000, MULT));
                knobs.add(new Knob(k("mutation"), () -> r.mutation, v -> r.mutation = v, 0.005, 0, 0.3, PCT, () -> oneIn(r.mutation)));
                knobs.add(new Knob(k("scatter_savant"), () -> r.scatterSavant, v -> r.scatterSavant = v, 0.001, 0, 0.3, PCT,
                        () -> oneIn(Simulator.rottenBonus(r)[0])));
                knobs.add(new Knob(k("scatter_invasion"), () -> r.scatterInvasion, v -> r.scatterInvasion = v, 0.001, 0, 0.3, PCT,
                        () -> oneIn(Simulator.rottenBonus(r)[1])));
            }
            case OLYMPE -> {
                fr.heraliz.casino.game.slot.OlympeRules r = work.portesOlympe;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("pay_coef"), () -> r.payScale, v -> r.payScale = v, 0.005, 0.2, 3, PCT));
                knobs.add(new Knob(k("buy_free"), () -> r.buyFree, v -> r.buyFree = v, 1, 0, 2000, MULT));
                knobs.add(new Knob(k("max_win"), () -> r.maxWin, v -> r.maxWin = v, 100, 100, 100_000, MULT));
                knobs.add(new Knob(k("scatter"), () -> r.scatter, v -> r.scatter = v, 0.001, 0, 0.2, PCT));
                knobs.add(new Knob(k("orb"), () -> r.orb, v -> r.orb = v, 0.001, 0, 0.2, PCT));
            }
            case LOUP -> {
                fr.heraliz.casino.game.slot.LoupRules r = work.souffleLoup;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("pay_coef"), () -> r.payScale, v -> r.payScale = v, 0.005, 0.2, 3, PCT));
                knobs.add(new Knob(k("buy_wheel"), () -> r.buyWheel, v -> r.buyWheel = v, 1, 0, 2000, MULT));
                knobs.add(new Knob(k("max_win"), () -> r.maxWin, v -> r.maxWin = v, 100, 100, 100_000, MULT));
                knobs.add(new Knob(k("hat"), () -> r.hat, v -> r.hat = v, 0.002, 0, 0.4, PCT));
                knobs.add(new Knob(k("saw"), () -> r.saw, v -> r.saw = v, 0.005, 0, 1, PCT, () -> oneIn(Simulator.loupWheel(r))));
                knobs.add(new Knob(k("gold_saw"), () -> r.gold, v -> r.gold = v, 0.002, 0, 1, PCT, () -> oneIn(Simulator.loupSuper(r))));
                knobs.add(new Knob(k("bonus_hat"), () -> r.bonusHat, v -> r.bonusHat = v, 0.005, 0, 0.9, PCT));
                knobs.add(new Knob(k("mansion_hat"), () -> r.mansionHat, v -> r.mansionHat = v, 0.002, 0, 0.9, PCT));
                knobs.add(new Knob(k("brick_special"), () -> r.brickSpecial, v -> r.brickSpecial = v, 0.005, 0, 1, PCT));
            }
            case NICHE -> {
                fr.heraliz.casino.game.slot.NicheRules r = work.laNiche;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("pay_coef"), () -> r.payScale, v -> r.payScale = v, 0.005, 0.2, 3, PCT));
                knobs.add(new Knob(k("buy_free"), () -> r.buyFree, v -> r.buyFree = v, 1, 0, 2000, MULT));
                knobs.add(new Knob(k("max_win"), () -> r.maxWin, v -> r.maxWin = v, 100, 100, 100_000, MULT));
                knobs.add(new Knob(k("niche_wild"), () -> r.wild, v -> r.wild = v, 0.002, 0, 0.5, PCT));
                knobs.add(new Knob(k("niche_bonus"), () -> r.bonus, v -> r.bonus = v, 0.002, 0, 1, PCT, () -> oneIn(Simulator.nicheFree(r))));
                knobs.add(new Knob(k("niche_free_wild"), () -> r.freeWild, v -> r.freeWild = v, 0.002, 0, 0.5, PCT));
                knobs.add(new Knob(k("niche_free_bonus"), () -> r.freeBonus, v -> r.freeBonus = v, 0.005, 0, 1, PCT));
            }
            case TABLEAU, TABLEAU_PERDANTS, TABLEAU_PARIEURS -> {
            }
            case PLINKO -> {
                PlinkoRules r = work.plinko;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                base = r.multipliers.clone();
                knobs.add(new Knob(k("pay_coef"), () -> coef, v -> {
                    coef = v;
                    for (int i = 0; i < base.length; i++) r.multipliers[i] = round2(base[i] * v);
                }, 0.01, 0.2, 3, PCT));
                knobs.add(new Knob(k("balls"), () -> r.maxBallsPerPlayer, v -> r.maxBallsPerPlayer = (int) v, 1, 1, 40, NUM));
            }
            case BLACKJACK -> {
                BlackjackRules r = work.blackjack;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("bj_pays"), () -> r.blackjackPays, v -> r.blackjackPays = v, 0.3, 1.2, 1.5, MULT));
                knobs.add(new Knob(k("bj_h17"), () -> r.dealerHitsSoft17 ? 1 : 0, v -> r.dealerHitsSoft17 = v > 0.5, 1, 0, 1, BOOL));
                knobs.add(new Knob(k("bj_double"), () -> r.allowDouble ? 1 : 0, v -> r.allowDouble = v > 0.5, 1, 0, 1, BOOL));
                knobs.add(new Knob(k("bj_split"), () -> r.allowSplit ? 1 : 0, v -> r.allowSplit = v > 0.5, 1, 0, 1, BOOL));
                knobs.add(new Knob(k("decision"), () -> r.decisionSeconds, v -> r.decisionSeconds = (int) v, 1, 10, 120, SEC));
            }
            case ROULETTE -> {
                RouletteRules r = work.roulette;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("zeros"), () -> r.zeros, v -> r.zeros = (int) v, 1, 1, 2, NUM));
                knobs.add(new Knob(k("betting"), () -> r.bettingSeconds, v -> r.bettingSeconds = (int) v, 1, 5, 120, SEC));
                knobs.add(new Knob(k("spin"), () -> r.spinSeconds, v -> r.spinSeconds = (int) v, 1, 5, 30, SEC));
            }
            case CRASH -> {
                CrashRules r = work.crash;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("house_edge"), () -> r.houseEdge, v -> r.houseEdge = v, 0.005, 0.01, 0.5, PCT));
                knobs.add(new Knob(k("max_mult"), () -> r.maxMultiplier, v -> r.maxMultiplier = v, 10, 2, 100_000, MULT));
                knobs.add(new Knob(k("betting"), () -> r.bettingSeconds, v -> r.bettingSeconds = (int) v, 1, 5, 120, SEC));
            }
            case MINES -> {
                MinesRules r = work.mines;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("rtp"), () -> r.rtp, v -> r.rtp = v, 0.005, 0.5, 0.99, PCT));
            }
            case COURSE -> {
                RaceRules r = work.course;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("rtp"), () -> r.rtp, v -> r.rtp = v, 0.005, 0.5, 0.99, PCT));
                knobs.add(new Knob(k("betting"), () -> r.bettingSeconds, v -> r.bettingSeconds = (int) v, 1, 5, 120, SEC));
                knobs.add(new Knob(k("race"), () -> r.raceSeconds, v -> r.raceSeconds = (int) v, 1, 6, 40, SEC));
            }
            case PIERRE_FEUILLE_CISEAUX, BATAILLE_NAVALE -> {
                DuelRules r = selected == MachineType.PIERRE_FEUILLE_CISEAUX ? work.pierreFeuilleCiseaux : work.batailleNavale;
                limits(() -> r.minBet, v -> r.minBet = (long) v, () -> r.maxBet, v -> r.maxBet = (long) v);
                knobs.add(new Knob(k("commission"), () -> r.commission, v -> r.commission = v, 0.005, 0, 0.5, PCT));
                knobs.add(new Knob(k("wait"), () -> r.waitSeconds, v -> r.waitSeconds = (int) v, 5, 20, 600, SEC));
                knobs.add(new Knob(k("turn"), () -> r.turnSeconds, v -> r.turnSeconds = (int) v, 1, 5, 60, SEC));
                if (selected == MachineType.PIERRE_FEUILLE_CISEAUX) {
                    knobs.add(new Knob(k("wins"), () -> r.winsNeeded, v -> r.winsNeeded = (int) v, 1, 1, 5, NUM));
                }
            }
            case CAISSE -> {
                CasinoConfig.Cashier c = work.caisse;
                // change avec les jetons du serveur (mod HeraliZ Economie) : X jetons du compte = Y jetons de casino ; ferme
                // par le code depuis le 22/10 (EconomieServeur.CHANGE_HERALS) : ses reglages ne sont plus proposes
                CasinoConfig.JetonsServeur eco = c.economie;
                if (fr.heraliz.casino.economy.EconomieServeur.CHANGE_HERALS) {
                    knobs.add(new Knob(k("eco_active"), () -> eco.actif ? 1 : 0, v -> eco.actif = v > 0.5, 1, 0, 1, BOOL,
                            () -> fr.heraliz.casino.economy.EconomieServeur.present() ? Component.empty() : Component.translatable("screen.heralizcasino.admin.eco_absent")));
                    knobs.add(new Knob(k("eco_server"), () -> eco.jetonsServeur, v -> eco.jetonsServeur = (long) v, 1, 1, 1_000_000, NUM));
                    knobs.add(new Knob(k("eco_casino"), () -> eco.jetonsCasino, v -> eco.jetonsCasino = (long) v, 1, 1, 1_000_000, CHIPS));
                    knobs.add(new Knob(k("eco_fee"), () -> eco.commissionRevente, v -> eco.commissionRevente = v, 0.01, 0, 0.5, PCT));
                }
                knobs.add(new Knob(k("items"), () -> c.echangesObjets ? 1 : 0, v -> c.echangesObjets = v > 0.5, 1, 0, 1, BOOL));
                for (int i = 0; i < Math.min(10, c.echanges.size()); i++) {
                    CasinoConfig.Exchange e = c.echanges.get(i);
                    // nom avec le NBT (deux soins differents ne s'appellent pas tous deux « Consommable »)
                    ItemStack st = fr.heraliz.casino.block.entity.CashierBlockEntity.stack(e);
                    Component name = st != null ? st.getHoverName() : itemName(e.item);
                    knobs.add(new Knob(Component.translatable("screen.heralizcasino.admin.k.rate", name), () -> e.jetons, v -> e.jetons = (long) v, 1, 1, 1_000_000, CHIPS));
                }
            }
        }
    }

    private static Component itemName(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id == null ? "" : id);
        net.minecraft.world.item.Item it = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        return it == null || it == Items.AIR ? Component.literal(String.valueOf(id)) : new ItemStack(it).getHoverName();
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String format(Knob k) {
        double v = k.get.getAsDouble();
        return switch (k.format) {
            case PCT -> String.format(Locale.ROOT, "%.1f %%", v * 100);
            case BOOL -> I18n.get(v > 0.5 ? "gui.yes" : "gui.no").toUpperCase(Locale.ROOT);
            case MULT -> String.format(Locale.ROOT, "x%.2f", v);
            case SEC -> (long) v + " s";
            case MINUTES -> (long) v + " min";
            case CHIPS -> UiKit.fmt((long) v);
            default -> Math.abs(v - Math.round(v)) < 1e-6 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.2f", v);
        };
    }

    // =================================================================== rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // style des interfaces HeraliZ (verre, DM Sans) le temps de ce rendu ; les ecrans a theme gardent l'atlas
        UiKit.clan = true;
        try {
            fr.heraliz.casino.client.screen.kit.Ui.fond(g, width, height);
            UiKit.panel(g, px, py, pw, ph);
            fr.heraliz.casino.client.screen.kit.Ui.spaced(g, font, title.getString(), fr.heraliz.casino.client.screen.kit.Ui.BOLD, px + 10, py + 9,
                    0xFFECEEF2, 1.4f);
            fr.heraliz.casino.client.screen.kit.Ui.hairline(g, px + 8, px + pw - 8, py + 23, 0xFFFFFF, 34, false, true);
            int kx = px + listW + 18;
            g.fill(kx - 8, py + 28, kx - 7, py + ph - 10, 0x1CFFFFFF);
            if (tab == TAB_SETTINGS) renderSettings(g);
            super.render(g, mx, my, partial);
            if (tab == TAB_STATS) renderStats(g, mx, my);
        } finally {
            UiKit.clan = false;
        }
    }

    @Override
    public void removed() {
        fr.heraliz.casino.client.screen.kit.Echelle.restaurer();
        super.removed();
    }

    private void renderSettings(GuiGraphics g) {
        int kx = px + listW + 18, kw = px + pw - 10 - kx;
        if (lights) {
            UiKit.fitted(g, Component.translatable("screen.heralizcasino.admin.lights_title").withStyle(s -> s.withBold(true)), kx, py + 28, kw * 0.45f,
                    0xFFFFFF, 1f, -1, true);
            UiKit.fitted(g, Component.translatable("screen.heralizcasino.admin.lights_scale"), px + pw - 10, py + 29, kw * 0.53f, MUTED, 0.7f, 1, false);
        }
        if (!lights) UiKit.fitted(g, selected.displayName().copy().withStyle(s -> s.withBold(true)), kx, py + 28, kw * 0.42f, selected.glow, 1f, -1, true);
        double[] rtp = lights ? null : CasinoConfig.rtp(selected, work);
        if (rtp != null) {
            double worst = rtp[1];
            int col = worst >= 1 ? DOWN : worst >= 0.97 ? WARN : GOOD;
            String txt = Math.abs(rtp[0] - rtp[1]) < 1e-4 ? String.format(Locale.ROOT, "%.1f %%", rtp[1] * 100)
                    : String.format(Locale.ROOT, "%.1f - %.1f %%", rtp[0] * 100, rtp[1] * 100);
            // machine a sous : redistribution mesuree par une simulation qui s'affine (marge d'erreur tant qu'elle compte)
            if (rtp.length > 2 && rtp[2] >= 0.0005) txt += String.format(Locale.ROOT, " (± %.1f)", rtp[2] * 100);
            UiKit.fitted(g, Component.translatable("screen.heralizcasino.admin.rtp", txt), px + pw - 10, py + 26, kw * 0.56f, col, 0.9f, 1, true);
            Component sub = worst >= 1 ? Component.translatable("screen.heralizcasino.admin.losing")
                    : Component.translatable("screen.heralizcasino.admin.edge", String.format(Locale.ROOT, "%.1f", (1 - worst) * 100));
            UiKit.text(g, sub, px + pw - 10, py + 36, worst >= 1 ? DOWN : MUTED, 0.7f, 1, false);
        }
        int top = knobTop(), pitch = knobPitch(), bh = Math.min(14, pitch - 1);
        for (int i = 0; i < knobs.size(); i++) {
            Knob k = knobs.get(i);
            int y = top + i * pitch, ty = y + (bh - 8) / 2;
            int labelW = kw - 80;
            if (k.info != null) {
                // ce que la valeur donne en jeu, juste avant le bouton "-"
                Component info = k.info.get();
                int iw = Math.min(150, Math.round(font.width(info) * 0.7f));
                UiKit.fitted(g, info, kx + kw - 78, ty + 1, 150, INK2, 0.7f, 1, false);
                labelW -= iw + 8;
            }
            UiKit.fitted(g, k.label, kx, ty, labelW, 0xECEEF2, 0.85f, -1, false);
            UiKit.field(g, kx + kw - 54, y, 36, bh);
            UiKit.fitted(g, Component.literal(format(k)), kx + kw - 36, ty, 32, 0xFFFFFF, 0.85f, 0, false);
        }
        String pk = pageKey();
        if (pk.equals(WHEEL) || pk.equals(SUPER)) {
            fr.heraliz.casino.game.slot.LoupRules r = work.souffleLoup;
            double turn = pk.equals(SUPER) ? Simulator.loupSuper(r) : Simulator.loupClassic(r);
            Component note = Component.translatable(pk.equals(SUPER) ? "screen.heralizcasino.admin.super_note" : "screen.heralizcasino.admin.wheel_note",
                    Simulator.oneIn(turn));
            UiKit.wrapped(g, note, kx, top + knobs.size() * pitch + 6, kw, MUTED, 0.7f);
        } else if (pk.equals(ODDS)) {
            renderOdds(g, kx, top, kw);
        } else if (pk.equals(ENTRY)) {
            // comment delimiter la salle et poser les videurs
            UiKit.wrapped(g, Component.translatable("screen.heralizcasino.admin.entry_note"), kx, top + knobs.size() * pitch + 6, kw, MUTED, 0.7f);
        }
        if (dirty) UiKit.text(g, Component.translatable("screen.heralizcasino.admin.unsaved"), kx, py + ph - 20, WARN, 0.75f, -1, true);
        UiKit.text(g, Component.translatable("screen.heralizcasino.admin.shift"), px + 8, py + ph - 11, MUTED, 0.65f, -1, false);
    }

    /**
     * Page "Probabilites" : ce que donnent les reglages en cours de modification (meme rapport que /casino roll),
     * calcule en arriere-plan. Defile a la molette quand il depasse.
     */
    private void renderOdds(GuiGraphics g, int x, int top, int w) {
        Simulator.Report rep = CasinoConfig.odds(selected, work);
        int bottom = py + ph - 44;
        UiKit.text(g, Component.translatable("screen.heralizcasino.admin.odds_cmd", selected.id), x, py + ph - 38, MUTED, 0.65f, -1, false);
        if (rep == null) {
            UiKit.text(g, Component.translatable("screen.heralizcasino.admin.odds_loading"), x + w / 2f, top + 30, MUTED, 0.9f, 0, false);
            return;
        }
        Component head = rep.rounds <= 0 ? Component.translatable("screen.heralizcasino.admin.odds_exact")
                : Component.translatable(CasinoConfig.oddsSettled(selected, work) ? "screen.heralizcasino.admin.odds_sim" : "screen.heralizcasino.admin.odds_more",
                Simulator.num(rep.rounds));
        UiKit.text(g, head, x, top, MUTED, 0.7f, -1, false);
        int y0 = top + 11;
        oddsScroll = Math.max(0, Math.min(oddsScroll, Math.max(0, oddsHeight - (bottom - y0))));
        g.enableScissor(x - 2, y0, x + w + 2, bottom);
        int y = y0 - oddsScroll;
        for (Simulator.Line l : rep.lines) {
            boolean main = l.key().startsWith("rtp");
            int col = main ? (rep.rtp >= 1 ? DOWN : rep.rtp >= 0.97 ? WARN : 0xFFE08A) : l.key().equals("feature") || l.key().equals("never") ? INK : INK2;
            g.fill(x, y + 2, x + 2, y + 5, 0xFF000000 | (main ? col : BASE));
            y += UiKit.wrapped(g, Lignes.rollLine(l), x + 6, y, w - 8, col, 0.7f) + 3;
        }
        g.disableScissor();
        oddsHeight = y + oddsScroll - y0;
        if (oddsHeight > bottom - y0) UiKit.text(g, Component.translatable("screen.heralizcasino.admin.scroll"), x + w, py + ph - 38, MUTED, 0.6f, 1, false);
    }

    // =================================================================== statistiques : donnees
    /** Compteurs d'une periode : [mises, mise, verse, plus gros gain, joueurs]. */
    private long[] sum(int periodIdx) {
        long[] out = new long[5];
        if (stats == null) return out;
        JsonObject ms = stats.getAsJsonObject("machines");
        int today = stats.get("today").getAsInt();
        int days = PERIOD_DAYS[periodIdx];
        for (MachineType t : scope()) {
            JsonObject o = ms.getAsJsonObject(t.id);
            if (o == null) continue;
            if (days < 0) {
                long[] a = arr(o.getAsJsonArray("total"));
                for (int i = 0; i < 3; i++) out[i] += a[i];
                out[3] = Math.max(out[3], a[3]);
            } else {
                JsonObject ds = o.getAsJsonObject("days");
                for (int d = today - days + 1; d <= today; d++) {
                    JsonElement e = ds.get(Integer.toString(d));
                    if (e == null) continue;
                    long[] a = arr(e.getAsJsonArray());
                    for (int i = 0; i < 3; i++) out[i] += a[i];
                    out[3] = Math.max(out[3], a[3]);
                }
            }
        }
        // joueurs distincts sur la periode
        JsonArray pl = statsSel == null ? stats.getAsJsonArray("p") : ms.getAsJsonObject(statsSel.id).getAsJsonArray("p");
        if (pl != null && pl.size() == 4) out[4] = pl.get(periodIdx).getAsLong();
        return out;
    }

    /** Serie par jour (n derniers jours, le plus ancien d'abord) : [mise, verse, mises]. */
    private long[][] series(int n) {
        long[][] out = new long[n][3];
        if (stats == null) return out;
        JsonObject ms = stats.getAsJsonObject("machines");
        int today = stats.get("today").getAsInt();
        for (MachineType t : scope()) {
            JsonObject o = ms.getAsJsonObject(t.id);
            if (o == null) continue;
            JsonObject ds = o.getAsJsonObject("days");
            for (int i = 0; i < n; i++) {
                JsonElement e = ds.get(Integer.toString(today - n + 1 + i));
                if (e == null) continue;
                long[] a = arr(e.getAsJsonArray());
                out[i][0] += a[1];
                out[i][1] += a[2];
                out[i][2] += a[0];
            }
        }
        return out;
    }

    private List<MachineType> scope() {
        return statsSel == null ? statTypes() : List.of(statsSel);
    }

    private static long[] arr(JsonArray a) {
        long[] out = new long[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsLong();
        return out;
    }

    private static String day(int epochDay) {
        LocalDate d = LocalDate.ofEpochDay(epochDay);
        return String.format(Locale.ROOT, "%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    /** Montant compact : 1 284 / 12,9 k / 4,2 M. */
    private static String compact(long v) {
        long a = Math.abs(v);
        String s = v < 0 ? "-" : "";
        if (a < 10_000) return s + UiKit.fmt(a);
        if (a < 1_000_000) return s + String.format(Locale.FRANCE, "%.1f k", a / 1000.0);
        return s + String.format(Locale.FRANCE, "%.1f M", a / 1_000_000.0);
    }

    private static String signed(long v) {
        return (v > 0 ? "+" : "") + UiKit.fmt(v);
    }

    // =================================================================== statistiques : rendu
    private void renderStats(GuiGraphics g, int mx, int my) {
        int kx = px + listW + 18, right = px + pw - 10, kw = right - kx;
        Component name = statsSel == null ? Component.translatable("screen.heralizcasino.admin.whole") : statsSel.displayName();
        UiKit.fitted(g, name.copy().withStyle(s -> s.withBold(true)), kx, py + 29, kw - 140, 0xFFFFFF, 1f, -1, true);
        int top = py + 62;
        if (stats == null) {
            UiKit.text(g, Component.translatable(loading ? "screen.heralizcasino.admin.loading" : "screen.heralizcasino.admin.no_stats"),
                    kx + kw / 2f, top + 40, MUTED, 1f, 0, false);
            return;
        }
        switch (view) {
            case 1 -> renderTable(g, kx, top, kw, mx, my);
            case 2 -> renderPlayers(g, kx, top, kw);
            default -> renderOverview(g, kx, top, kw, mx, my);
        }
    }

    private void renderOverview(GuiGraphics g, int x, int top, int w, int mx, int my) {
        long[] s = sum(period);
        long profit = s[1] - s[2];
        // ---- tuiles (libelle discret, valeur en encre principale)
        int cols = 4, tw = (w - (cols - 1) * 4) / cols, th = 24;
        double real = s[1] > 0 ? (double) s[2] / s[1] : 0;
        double theo = -1;
        if (statsSel != null) {
            double[] r = CasinoConfig.rtp(statsSel, CasinoConfig.get());
            if (r != null) theo = r[1];
        }
        String[][] tiles = {
                {"wagered", compact(s[1])}, {"paid", compact(s[2])}, {"profit", (profit >= 0 ? "▲ " : "▼ ") + compact(Math.abs(profit))},
                {"rtp", s[1] > 0 ? String.format(Locale.FRANCE, "%.1f %%", real * 100) : "-"},
                {"bets", compact(s[0])}, {"players", compact(s[4])}, {"avg", s[0] > 0 ? compact(s[1] / s[0]) : "-"},
                {"max_win", compact(s[3])}};
        for (int i = 0; i < tiles.length; i++) {
            int tx = x + (i % cols) * (tw + 4), ty = top + (i / cols) * (th + 4);
            // tuile de statistique (charte HeraliZ) : verre leger, legende en capitales espacees
            fr.heraliz.casino.client.screen.kit.Ui.soft(g, tx, ty, tw, th, 0x0EFFFFFF);
            fr.heraliz.casino.client.screen.kit.Ui.softOutline(g, tx + 1, ty + 1, tw - 2, th - 2, 0x18FFFFFF);
            String leg = Component.translatable("screen.heralizcasino.admin.tile." + tiles[i][0]).getString().toUpperCase(Locale.ROOT);
            float sp = fr.heraliz.casino.client.screen.kit.Ui.spacedWidth(font, leg, fr.heraliz.casino.client.screen.kit.Ui.SMALL, 0.6f) <= tw - 10 ? 0.6f : 0f;
            leg = fr.heraliz.casino.client.screen.kit.Ui.ellipsis(font, leg, fr.heraliz.casino.client.screen.kit.Ui.SMALL, tw - 10);
            fr.heraliz.casino.client.screen.kit.Ui.spaced(g, font, leg, fr.heraliz.casino.client.screen.kit.Ui.SMALL, tx + 5, ty + 4, 0xFF8F95A0, sp);
            int vc = INK;
            if (i == 2 && s[1] > 0) vc = profit >= 0 ? GOOD : DOWN;
            if (i == 3 && s[1] > 0 && real > 1) vc = WARN;
            UiKit.fitted(g, Component.literal(tiles[i][1]).withStyle(st -> st.withBold(true)), tx + 5, ty + 13, tw - 10, vc, 1f, -1, true);
        }
        if (theo >= 0) {
            UiKit.text(g, Component.translatable("screen.heralizcasino.admin.theo", String.format(Locale.FRANCE, "%.1f", theo * 100)),
                    x + 3 * (tw + 4) + tw - 3, top + 3, MUTED, 0.6f, 1, false);
        }
        // ---- graphiques : benefice (bleu / rouge autour de zero) et volume mise (vert d'eau), un seul axe chacun
        int n = period <= 1 ? 7 : 30;
        long[][] ser = series(n);
        int today = stats.get("today").getAsInt();
        int cy = top + 2 * (th + 4) + 6;
        int avail = py + ph - 12 - cy;
        // chaque graphique : titre (11 px) + barres + dates (8 px) ; 6 px entre les deux
        int bars = Math.max(40, avail - 2 * 19 - 6);
        int h1 = (int) (bars * 0.56f), h2 = bars - h1;
        long[] profits = new long[n], volumes = new long[n];
        for (int i = 0; i < n; i++) {
            profits[i] = ser[i][0] - ser[i][1];
            volumes[i] = ser[i][0];
        }
        int hover1 = chart(g, x, cy, w, h1, profits, true, Component.translatable("screen.heralizcasino.admin.chart_profit", n), mx, my);
        int hover2 = chart(g, x, cy + h1 + 25, w, h2, volumes, false, Component.translatable("screen.heralizcasino.admin.chart_volume", n), mx, my);
        int hi = hover1 >= 0 ? hover1 : hover2;
        if (hi >= 0) {
            long[] v = ser[hi];
            Component tip = Component.translatable("screen.heralizcasino.admin.day_tip", day(today - n + 1 + hi), signed(v[0] - v[1]), UiKit.fmt(v[0]),
                    UiKit.fmt(v[1]), UiKit.fmt(v[2]));
            tooltip(g, tip, mx, my);
        }
    }

    /**
     * Histogramme par jour. Barres fines ancrees sur la ligne de base (zero), 2 px d'espace entre elles,
     * grille discrete, graduations arrondies. Diverging = benefice (bleu au-dessus de zero, rouge en dessous).
     * @return index de la barre survolee, ou -1
     */
    private int chart(GuiGraphics g, int x, int y, int w, int h, long[] v, boolean diverging, Component title, int mx, int my) {
        UiKit.text(g, title, x, y, INK2, 0.75f, -1, false);
        int cy = y + 11, ch = h;
        int axisW = 30;
        int cx = x + axisW, cw = w - axisW;
        long max = 0, min = 0;
        for (long a : v) {
            max = Math.max(max, a);
            min = Math.min(min, a);
        }
        boolean empty = max == 0 && min == 0;
        long top = nice(max), bottom = diverging ? -nice(-min) : 0;
        if (top == 0 && bottom == 0) top = 1;
        double span = top - bottom;
        int zeroY = cy + (int) Math.round(ch * (top / span));
        // grille et axe (hairlines)
        g.fill(cx, cy, cx + cw, cy + 1, 0xFF000000 | GRID);
        if (bottom < 0) g.fill(cx, cy + ch - 1, cx + cw, cy + ch, 0xFF000000 | GRID);
        g.fill(cx, zeroY, cx + cw, zeroY + 1, 0xFF000000 | BASE);
        UiKit.text(g, Component.literal(compact(top)), cx - 3, cy - 2, MUTED, 0.6f, 1, false);
        UiKit.text(g, Component.literal("0"), cx - 3, zeroY - 2, MUTED, 0.6f, 1, false);
        if (bottom < 0) UiKit.text(g, Component.literal(compact(bottom)), cx - 3, cy + ch - 4, MUTED, 0.6f, 1, false);
        int n = v.length;
        float slot = cw / (float) n;
        int bw = (int) Math.max(1, Math.min(12, slot - 2));
        int hovered = -1;
        for (int i = 0; i < n; i++) {
            int bx = cx + Math.round(i * slot + (slot - bw) / 2f);
            boolean over = mx >= cx + i * slot && mx < cx + (i + 1) * slot && my >= cy - 2 && my < cy + ch + 2;
            if (over) {
                hovered = i;
                g.fill(Math.round(cx + i * slot), cy, Math.round(cx + (i + 1) * slot), cy + ch, 0x18FFFFFF);
            }
            if (v[i] == 0) continue;
            int len = (int) Math.max(1, Math.round(Math.abs(v[i]) / span * ch));
            int col = !diverging ? VOLUME : v[i] >= 0 ? UP : DOWN;
            if (v[i] > 0) g.fill(bx, zeroY - len, bx + bw, zeroY, 0xFF000000 | col);
            else g.fill(bx, zeroY + 1, bx + bw, zeroY + 1 + len, 0xFF000000 | col);
        }
        if (empty) UiKit.text(g, Component.translatable("screen.heralizcasino.admin.empty"), cx + cw / 2f, cy + ch / 2f - 3, MUTED, 0.7f, 0, false);
        // dates : premier et dernier jour
        int today = stats.get("today").getAsInt();
        UiKit.text(g, Component.literal(day(today - n + 1)), cx, cy + ch + 2, MUTED, 0.6f, -1, false);
        UiKit.text(g, Component.translatable("screen.heralizcasino.admin.today_short"), cx + cw, cy + ch + 2, MUTED, 0.6f, 1, false);
        return hovered;
    }

    /** Graduation "ronde" au-dessus de v (1, 2, 5 x 10^n). */
    private static long nice(long v) {
        if (v <= 0) return 0;
        long p = 1;
        while (p * 10 <= v) p *= 10;
        for (long m : new long[]{1, 2, 5, 10}) if (m * p >= v) return m * p;
        return 10 * p;
    }

    private void renderTable(GuiGraphics g, int x, int top, int w, int mx, int my) {
        int n = 30;
        long[][] ser = series(n);
        int today = stats.get("today").getAsInt();
        String[] heads = {"date", "bets", "wagered", "paid", "profit", "rtp"};
        float[] colX = {0, 0.17f, 0.33f, 0.53f, 0.73f, 1f};
        int rowH = 10;
        for (int c = 0; c < heads.length; c++) {
            int align = c == 0 ? -1 : 1;
            float cxp = c == 0 ? x : x + colX[c] * w;
            UiKit.text(g, Component.translatable("screen.heralizcasino.admin.col." + heads[c]), cxp, top, MUTED, 0.65f, align, false);
        }
        g.fill(x, top + 8, x + w, top + 9, 0xFF000000 | GRID);
        int rows = Math.max(1, (py + ph - 14 - (top + 11)) / rowH);
        int maxOffset = Math.max(0, n - rows);
        tableOffset = Math.max(0, Math.min(tableOffset, maxOffset));
        for (int r = 0; r < rows && r + tableOffset < n; r++) {
            int i = n - 1 - (r + tableOffset);
            long[] v = ser[i];
            int y = top + 11 + r * rowH;
            if (r % 2 == 1) g.fill(x, y - 1, x + w, y + rowH - 1, 0x10FFFFFF);
            long profit = v[0] - v[1];
            String[] cells = {day(today - n + 1 + i), UiKit.fmt(v[2]), UiKit.fmt(v[0]), UiKit.fmt(v[1]), signed(profit),
                    v[0] > 0 ? String.format(Locale.FRANCE, "%.1f %%", 100.0 * v[1] / v[0]) : "-"};
            for (int c = 0; c < cells.length; c++) {
                int col = c == 4 && v[0] > 0 ? (profit >= 0 ? GOOD : DOWN) : (v[0] == 0 ? MUTED : INK);
                if (c == 4 && v[0] > 0) cells[c] = (profit >= 0 ? "▲ " : "▼ ") + cells[c];
                float cxp = c == 0 ? x : x + colX[c] * w;
                UiKit.text(g, Component.literal(cells[c]), cxp, y, col, 0.7f, c == 0 ? -1 : 1, false);
            }
        }
        if (maxOffset > 0) UiKit.text(g, Component.translatable("screen.heralizcasino.admin.scroll"), x + w, py + ph - 11, MUTED, 0.6f, 1, false);
    }

    private void renderPlayers(GuiGraphics g, int x, int top, int w) {
        JsonArray arr = stats.getAsJsonArray("top");
        UiKit.text(g, Component.translatable("screen.heralizcasino.admin.players_note"), x, top, MUTED, 0.65f, -1, false);
        int half = (w - 8) / 2;
        List<JsonObject> winners = new ArrayList<>(), losers = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            long net = o.get("paid").getAsLong() - o.get("wagered").getAsLong();
            if (net > 0) winners.add(o);
        }
        for (int i = arr.size() - 1; i >= 0; i--) {
            JsonObject o = arr.get(i).getAsJsonObject();
            long net = o.get("paid").getAsLong() - o.get("wagered").getAsLong();
            if (net < 0) losers.add(o);
        }
        playerColumn(g, x, top + 12, half, Component.translatable("screen.heralizcasino.admin.winners"), winners);
        playerColumn(g, x + half + 8, top + 12, half, Component.translatable("screen.heralizcasino.admin.losers"), losers);
    }

    private void playerColumn(GuiGraphics g, int x, int y, int w, Component title, List<JsonObject> list) {
        UiKit.text(g, title, x, y, INK2, 0.75f, -1, false);
        g.fill(x, y + 9, x + w, y + 10, 0xFF000000 | GRID);
        if (list.isEmpty()) {
            UiKit.text(g, Component.translatable("screen.heralizcasino.admin.nobody"), x, y + 14, MUTED, 0.7f, -1, false);
            return;
        }
        for (int i = 0; i < Math.min(6, list.size()); i++) {
            JsonObject o = list.get(i);
            long wag = o.get("wagered").getAsLong(), paid = o.get("paid").getAsLong(), net = paid - wag;
            int ry = y + 13 + i * 20;
            UiKit.fitted(g, Component.literal(o.get("name").getAsString()), x, ry, w - 60, INK, 0.8f, -1, false);
            UiKit.text(g, Component.literal((net >= 0 ? "▲ " : "▼ ") + signed(net)), x + w, ry, net >= 0 ? GOOD : DOWN, 0.8f, 1, false);
            UiKit.text(g, Component.translatable("screen.heralizcasino.admin.player_line", compact(wag), compact(paid)), x, ry + 9, MUTED, 0.6f, -1, false);
        }
    }

    private void tooltip(GuiGraphics g, Component tip, int mx, int my) {
        int tw = Math.min(width - 16, font.width(tip) + 14);
        int x = Math.max(4, Math.min(width - tw - 4, mx - tw / 2)), y = Math.max(4, my - 22);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        UiKit.pill(g, x, y, tw, 15);
        UiKit.fitted(g, tip, x + tw / 2f, y + 4, tw - 10, INK, 1f, 0, false);
        g.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == TAB_STATS && view == 1) {
            tableOffset = Math.max(0, tableOffset - (int) Math.signum(delta) * 3);
            return true;
        }
        if (tab == TAB_SETTINGS && pageKey().equals(ODDS)) {
            oddsScroll = Math.max(0, oddsScroll - (int) Math.signum(delta) * 14);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
