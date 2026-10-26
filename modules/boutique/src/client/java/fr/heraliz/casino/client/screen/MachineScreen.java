package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.entity.RoundBlockEntity;
import fr.heraliz.casino.client.Views;
import fr.heraliz.casino.client.camera.CasinoCamera;
import fr.heraliz.casino.client.render.HotspotHighlight;
import fr.heraliz.casino.client.render.MachineRenderer;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.Hotspots;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.client.screen.ui.UiIcons;
import fr.heraliz.casino.client.screen.ui.UiKit;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.config.ClientConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.net.ActionPacket;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ecran de jeu minimaliste : la camera se place sur la machine (plan cinematique) et un petit dock en bas de l'ecran
 * contient le strict necessaire (mise libre, action, "i" d'explication, fermer). On peut aussi cliquer directement
 * dans la scene 3D (cases du tapis, dalles, cartes...) : la zone survolee s'illumine.
 */
public abstract class MachineScreen<T extends MachineBlockEntity> extends Screen {
    public static final int GOLD = UiKit.GOLD, CREAM = UiKit.CREAM, GREY = UiKit.GREY, RED = UiKit.RED, GREEN = UiKit.GREEN;
    private static final Map<MachineType, Long> LAST_BET = new EnumMap<>(MachineType.class);
    /** Zoom choisi sur chaque machine (retrouve en revenant jouer). */
    private static final Map<MachineType, Boolean> LAST_ZOOM = new EnumMap<>(MachineType.class);
    private static final int DOCK_H = 24, GAP = 5, PAD = 7, BET_W = 98;
    /** Panneau du volume : largeur, hauteur, et ou commence la jauge (depuis le bord gauche du panneau). */
    private static final int VOL_W = 168, VOL_H = 44, VOL_TRACK_X = 62, VOL_TRACK_W = 70;

    protected final T be;
    protected long bet;
    /** Ouvert automatiquement au depart d'un tour ou le joueur a mise : se referme seul a la fin. */
    protected boolean watch;
    private boolean infoOpen;

    /** Vrai quand le panneau « i » est ouvert en haut a gauche : rien d'autre ne s'y dessine. */
    protected boolean infoOuvert() {
        return infoOpen;
    }

    @Nullable
    private Component notice;
    private long noticeUntil;
    protected final Hotspots hotspots = new Hotspots();
    /** Zone 3D survolee (identifiant), -1 sinon. */
    protected int hover = -1;
    @Nullable
    protected EditBox betBox;
    private int layout = Integer.MIN_VALUE;
    private final List<UiButton> dockButtons = new ArrayList<>();
    private UiButton infoButton, volumeButton, zoomButton;
    /** Panneau du volume ouvert ; jauge tiree a la souris (0 musique, 1 bruitages, -1 aucune). */
    private boolean volumeOpen;
    private int volumeDrag = -1;
    protected int dockX, dockY, dockW;
    private int betX = -1;
    private float lastPartial;
    /** Dock affiche ; il se range quand l'ecran de la machine prend toute la vue (zoom, intro) et revient sous la souris. */
    private boolean dockShown = true;

    protected MachineScreen(T be) {
        super(be.type.displayName());
        this.be = be;
        this.bet = LAST_BET.getOrDefault(be.type, minBet());
    }

    /** Machine sur laquelle joue cet ecran. */
    public MachineBlockEntity machine() {
        return be;
    }

    // =================================================================== a definir par chaque machine
    /** Remplit le dock (entre le "i" et la croix). Appele a nouveau quand layoutKey() change. */
    protected abstract void dock(Dock d);

    /** Etat qui change la composition du dock (ex. mise / main en cours). */
    protected int layoutKey() {
        return 0;
    }

    /** Plan de camera demande au modele. */
    protected int cameraMode() {
        return MachineDesign.CAM_MENU;
    }

    /** Ligne d'etat au-dessus du dock. */
    @Nullable
    protected Component status() {
        return null;
    }

    protected int statusColor() {
        return CREAM;
    }

    /** Zone 3D cliquable en ce moment ? */
    protected boolean hotspotEnabled(int id) {
        return false;
    }

    protected void onHotspot(int id) {
    }

    @Nullable
    protected Component hotspotTip(int id) {
        return null;
    }

    protected int hotspotColor(int id) {
        return 0xFFD36A;
    }

    /** Lignes supplementaires de l'explication (gains...). */
    protected List<Component> infoExtra() {
        return List.of();
    }

    /** Action principale (Espace / Entree). */
    protected void primary() {
    }

    /** Fin du mode "spectateur" (ecran ouvert automatiquement). */
    protected boolean watchDone() {
        return be instanceof RoundBlockEntity r && r.phase == RoundBlockEntity.IDLE;
    }

    /** Ecran ouvert automatiquement (tour en cours) : se referme a la fin du tour si le joueur n'y touche pas. */
    public void setWatch() {
        watch = true;
    }

    // =================================================================== dock
    /** Composition du dock, de gauche a droite. */
    protected final class Dock {
        private final List<Object> items = new ArrayList<>();

        public UiButton add(UiButton b) {
            items.add(b);
            return b;
        }

        public UiButton button(Component label, int style, int w, Runnable action) {
            return add(new UiButton(w, 18, label, style, b -> action.run()));
        }

        /** Champ de mise (moins, montant libre, plus). */
        public void bet() {
            items.add(BET);
        }

        public void gap(int px) {
            items.add(px);
        }
    }

    private static final Object BET = new Object();

    @Override
    protected void init() {
        CasinoCamera.enter(be);
        CasinoCamera.setZoom(zoomable() && LAST_ZOOM.getOrDefault(be.type, false));
        rebuild();
    }

    /** L'ecran de la machine s'agrandit (zoom du joueur). */
    protected boolean zoomable() {
        return Designs.get(be.type.id).zoomable();
    }

    private void rebuild() {
        clearWidgets();
        dockButtons.clear();
        betBox = null;
        betX = -1;
        dockShown = true;
        layout = layoutKey();
        Dock d = new Dock();
        // reglages personnels : volume de la machine, zoom sur l'ecran
        volumeButton = d.add(new UiButton(16, 16, Component.empty(), UiAtlas.DARK, b -> {
            volumeOpen = !volumeOpen;
            click();
        }) {
            @Override
            protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
                super.renderWidget(g, mouseX, mouseY, partial);
                UiIcons.draw(g, muted() ? UiIcons.MUTED : UiIcons.SPEAKER, getX(), getY(), getWidth(), getHeight(), muted() ? 0xFF8A7A : 0xF2EADC);
            }
        }.round().tip(() -> Component.translatable("screen.heralizcasino.volume_tip")));
        zoomButton = null;
        if (zoomable()) {
            zoomButton = d.add(new UiButton(16, 16, Component.empty(), UiAtlas.DARK, b -> {
                setZoom(!CasinoCamera.zoom());
                click();
            }) {
                @Override
                protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
                    super.renderWidget(g, mouseX, mouseY, partial);
                    UiIcons.draw(g, CasinoCamera.zoom() ? UiIcons.SHRINK : UiIcons.EXPAND, getX(), getY(), getWidth(), getHeight(), 0xF2EADC);
                }
            }.round().tip(() -> Component.translatable("screen.heralizcasino.zoom_tip")));
        }
        infoButton = d.add(new UiButton(16, 16, Component.literal("i"), UiAtlas.DARK, b -> {
            infoOpen = !infoOpen;
            click();
        }).round().tip(() -> Component.translatable("screen.heralizcasino.info_tip")));
        dock(d);
        d.add(new UiButton(16, 16, Component.literal("✕"), UiAtlas.DARK, b -> onClose()).round()
                .tip(() -> Component.translatable("screen.heralizcasino.close_tip")));
        // largeur totale puis placement centre
        int w = 0;
        for (int i = 0; i < d.items.size(); i++) {
            Object o = d.items.get(i);
            if (o instanceof UiButton b) w += b.getWidth();
            else if (o == BET) w += BET_W;
            else if (o instanceof Integer g) w += g;
            if (i < d.items.size() - 1 && !(o instanceof Integer)) w += GAP;
        }
        // ecran etroit : les boutons (sauf les ronds) se resserrent pour que le dock tienne en entier
        int avail = width - 12 - 2 * PAD;
        if (w > avail) {
            int flex = 0;
            for (Object o : d.items) if (o instanceof UiButton b && !b.round) flex += b.getWidth();
            float k = flex > 0 ? Math.max(0.45f, (flex - (w - avail)) / (float) flex) : 1f;
            for (Object o : d.items) {
                if (o instanceof UiButton b && !b.round) {
                    int nw = Math.max(14, Math.round(b.getWidth() * k));
                    w -= b.getWidth() - nw;
                    b.setWidth(nw);
                }
            }
        }
        dockW = w;
        dockX = (width - w) / 2;
        dockY = height - DOCK_H - 12;
        int x = dockX;
        for (int i = 0; i < d.items.size(); i++) {
            Object o = d.items.get(i);
            if (o instanceof UiButton b) {
                b.setX(x);
                b.setY(dockY + (DOCK_H - b.getHeight()) / 2);
                addRenderableWidget(b);
                dockButtons.add(b);
                x += b.getWidth();
            } else if (o == BET) {
                addBetField(x);
                x += BET_W;
            } else if (o instanceof Integer g) {
                x += g;
                continue;
            }
            x += GAP;
        }
        extraWidgets();
        for (UiButton b : dockButtons) b.refresh();
    }

    /** Boutons hors du dock (ex. lignes de la caisse), ajoutes apres chaque recomposition. */
    protected void extraWidgets() {
    }

    /** Place un bouton a une position libre de l'ecran (rafraichi et infobulle comme ceux du dock). */
    protected UiButton place(UiButton b, int x, int y) {
        b.setX(x);
        b.setY(y);
        addRenderableWidget(b);
        dockButtons.add(b);
        return b;
    }

    private void addBetField(int x) {
        betX = x;
        int y = dockY + (DOCK_H - 16) / 2;
        UiButton minus = new UiButton(14, 16, Component.literal("−"), UiAtlas.DARK, b -> {
            setBet(bet - step(bet, false));
            click();
        });
        minus.setX(x);
        minus.setY(y);
        addRenderableWidget(minus);
        dockButtons.add(minus);
        UiButton plus = new UiButton(14, 16, Component.literal("+"), UiAtlas.DARK, b -> {
            setBet(bet + step(bet, true));
            click();
        });
        plus.setX(x + BET_W - 14);
        plus.setY(y);
        addRenderableWidget(plus);
        dockButtons.add(plus);
        EditBox box = new EditBox(font, x + 31, dockY + (DOCK_H - 8) / 2, BET_W - 48, 10, Component.translatable("screen.heralizcasino.bet"));
        box.setBordered(false);
        box.setMaxLength(9);
        box.setTextColor(0xFFFFE08A);
        box.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        box.setValue(Long.toString(bet));
        box.setResponder(s -> {
            if (!s.isEmpty()) {
                try {
                    bet = Long.parseLong(s);
                } catch (NumberFormatException e) {
                    bet = maxBet();
                }
            }
        });
        addRenderableWidget(box);
        betBox = box;
    }

    // =================================================================== cycle
    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || be.isRemoved()
                || minecraft.level.getBlockEntity(be.getBlockPos()) != be || !be.isNear(minecraft.player)) {
            super.onClose();
            return;
        }
        if (betBox != null) betBox.tick();
        CasinoCamera.setMode(cameraMode());
        if (layoutKey() != layout) rebuild();
        for (UiButton b : dockButtons) b.refresh();
        if (infoButton != null) infoButton.selected = infoOpen;
        if (volumeButton != null) volumeButton.selected = volumeOpen;
        if (zoomButton != null) zoomButton.selected = CasinoCamera.zoom();
        if (watch && watchDone()) onClose();
    }

    /** En pleine partie (cartes distribuees, mines payees, duel, rouleaux lances) : impossible de quitter la machine. */
    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null && be.locks(minecraft.player.getUUID())) {
            notice(Component.translatable("message.heralizcasino.locked"));
            return;
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (zoomable()) LAST_ZOOM.put(be.type, CasinoCamera.zoom());
        CasinoCamera.setZoom(false);
        CasinoCamera.exit();
        HotspotHighlight.clear();
        LAST_BET.put(be.type, bet);
        if (volumeDrag >= 0) saveVolume();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // pas de fond : la machine reste visible
    }

    // =================================================================== rendu
    @Override
    public void render(GuiGraphics g, int realX, int realY, float partial) {
        lastPartial = partial;
        int mx = testMouse != null ? testMouse[0] : realX, my = testMouse != null ? testMouse[1] : realY;
        updateHover(mx, my, partial);
        updateDock(my);
        // ---- dock : pas de fond, seulement les boutons et le champ de mise
        if (dockShown && betX >= 0 && betBox != null) {
            int fy = dockY + (DOCK_H - 16) / 2;
            boolean bad = bet < minBet() || bet > maxBet() || bet > balance();
            UiKit.field(g, betX + 16, fy, BET_W - 32, 16);
            g.pose().pushPose();
            g.pose().translate(betX + 18.5f, fy + 2.5f, 0);
            g.pose().scale(0.7f, 0.7f, 1);
            g.renderItem(new ItemStack(ModRegistry.chip(chipIndex(bet))), 0, 0);
            g.pose().popPose();
            betBox.setTextColor(bad ? 0xFFFF7A6A : 0xFFFFE08A);
        }
        super.render(g, mx, my, partial);
        // ---- etat / message du serveur (l'etat se range avec le dock : l'ecran de la machine affiche deja tout)
        Component line = notice != null && System.currentTimeMillis() < noticeUntil ? notice : dockShown ? status() : null;
        if (line != null) {
            int col = line == notice ? 0xFFFFFF : statusColor();
            UiKit.outlinedFit(g, line, width / 2f, dockY - 16, width - 24, col, 1f, 0);
        }
        // ---- solde (en haut a droite), sans fond
        if (showBalance()) {
            Component bal = Component.translatable("screen.heralizcasino.balance", Chips.format(balance()));
            int bw = font.width(bal) + 20;
            g.renderItem(new ItemStack(ModRegistry.chip(2)), width - bw - 10, 8);
            UiKit.outlined(g, bal, width - 10, 12, GOLD, 1f, 1);
        }
        // ---- explication, volume
        if (infoOpen) drawInfo(g);
        if (volumeOpen) drawVolume(g, mx, my);
        // ---- infobulles
        Component tip = null;
        for (UiButton b : dockButtons) {
            if (b.visible && b.isHovered() && b.tooltip != null) tip = b.tooltip.get();
        }
        if (infoButton != null && infoButton.visible && infoButton.isHovered() && infoButton.tooltip != null) tip = infoButton.tooltip.get();
        if (tip == null && hover >= 0) tip = hotspotTip(hover);
        if (tip == null && betX >= 0 && mx >= betX + 16 && mx < betX + BET_W - 16 && my >= dockY && my < dockY + DOCK_H) {
            tip = Component.translatable("screen.heralizcasino.bet_tip", Chips.format(minBet()), Chips.format(maxBet()));
        }
        if (tip != null) tooltip(g, tip, mx, my);
    }

    private void tooltip(GuiGraphics g, Component tip, int mx, int my) {
        int tw = Math.min(width - 16, font.width(tip) + 14);
        int x = Math.max(4, Math.min(width - tw - 4, mx - tw / 2)), y = Math.max(30, my - 24);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        UiKit.pill(g, x, y, tw, 15);
        UiKit.fitted(g, tip, x + tw / 2f, y + 4, tw - 10, 0xFFFFFF, 1f, 0, true);
        g.pose().popPose();
    }

    private void drawInfo(GuiGraphics g) {
        int w = Math.min(210, width / 2);
        int x = 12, y = 12;
        List<Component> lines = new ArrayList<>();
        for (String s : Component.translatable("info.heralizcasino." + be.type.id).getString().split("\n")) lines.add(Component.literal(s));
        List<Component> extra = infoExtra();
        int maxH = dockY - 40;
        // taille du texte : on reduit jusqu'a ce que tout tienne au-dessus du dock
        float k = 0.85f;
        int h = 0;
        for (int pass = 0; pass < 6; pass++) {
            h = 22;
            for (Component c : lines) h += UiKit.wrappedHeight(c, w - 16, k) + 3;
            if (!extra.isEmpty()) h += 6 + extra.size() * Math.round(10 * k + 0.5f);
            if (h <= maxH) break;
            k -= 0.05f;
        }
        h = Math.min(h, maxH);
        float ek = Math.min(0.8f, k);
        int lh = Math.round(10 * k + 0.5f);
        UiKit.panel(g, x, y, w, h);
        UiKit.text(g, title.copy().withStyle(s -> s.withBold(true)), x + 8, y + 7, be.type.glow, 1f, -1, true);
        int ty = y + 21;
        g.enableScissor(x, y, x + w, y + h);
        for (Component c : lines) ty += UiKit.wrapped(g, c, x + 8, ty, w - 16, CREAM, k) + 3;
        if (!extra.isEmpty()) {
            ty += 3;
            g.fill(x + 8, ty, x + w - 8, ty + 1, 0x60C89A48);
            ty += 4;
            for (Component c : extra) {
                UiKit.fitted(g, c, x + 8, ty, w - 16, GOLD, ek, -1, false);
                ty += lh;
            }
        }
        g.disableScissor();
    }

    // =================================================================== volume et zoom du joueur
    /** Coin haut gauche du panneau du volume (au-dessus du bouton haut-parleur, sans toucher la ligne d'etat). */
    private int volX() {
        return volumeButton == null ? dockX : Math.max(6, Math.min(width - VOL_W - 6, volumeButton.getX() - 6));
    }

    private int volY() {
        return dockY - 24 - VOL_H;
    }

    /** Volume personnel (0..2) : 0 la musique, 1 les bruitages de la machine. */
    private static double volume(int k) {
        return (k == 0 ? ClientConfig.MUSIC_VOLUME : ClientConfig.MACHINE_VOLUME).get();
    }

    private static void setVolume(int k, double v) {
        double c = Math.round(Math.max(0, Math.min(2, v)) * 20) / 20.0;
        (k == 0 ? ClientConfig.MUSIC_VOLUME : ClientConfig.MACHINE_VOLUME).set(c);
    }

    private static void saveVolume() {
        try {
            ClientConfig.MUSIC_VOLUME.save();
        } catch (RuntimeException ignored) {
            // configuration pas encore chargee : le reglage vaut pour la session
        }
    }

    private static boolean muted() {
        return volume(0) <= 0.001 && volume(1) <= 0.001;
    }

    /** Jauge du volume sous la souris (0, 1) ou -1. */
    private int volumeRow(double mx, double my) {
        if (!volumeOpen) return -1;
        int x = volX(), y = volY();
        if (mx < x || mx >= x + VOL_W || my < y || my >= y + VOL_H) return -1;
        return my < y + VOL_H / 2f ? 0 : 1;
    }

    private void dragVolume(int row, double mx) {
        float u = (float) ((mx - (volX() + VOL_TRACK_X)) / VOL_TRACK_W);
        setVolume(row, Math.max(0, Math.min(1, u)) * 2);
    }

    private void drawVolume(GuiGraphics g, int mx, int my) {
        int x = volX(), y = volY();
        UiKit.panel(g, x, y, VOL_W, VOL_H);
        for (int k = 0; k < 2; k++) {
            int ry = y + 7 + k * 19;
            double v = volume(k);
            boolean hot = volumeDrag == k || volumeRow(mx, my) == k;
            Component label = Component.translatable(k == 0 ? "screen.heralizcasino.volume_music" : "screen.heralizcasino.volume_fx");
            UiKit.fitted(g, label, x + 8, ry + 1, VOL_TRACK_X - 12, hot ? GOLD : CREAM, 0.7f, -1, true);
            int tx = x + VOL_TRACK_X, tw = VOL_TRACK_W, ty = ry + 2;
            // jauge creusee, remplissage dore (au-dela de 100 % : orange), curseur
            g.fill(tx - 1, ty - 1, tx + tw + 1, ty + 5, 0xC0000000);
            g.fill(tx, ty, tx + tw, ty + 4, 0xFF2A2630);
            int fill = (int) Math.round(tw * Math.min(1, v / 2));
            int half = tw / 2;
            g.fill(tx, ty, tx + Math.min(fill, half), ty + 4, 0xFFE8A93A);
            if (fill > half) g.fill(tx + half, ty, tx + fill, ty + 4, 0xFFFF7A3A);
            g.fill(tx + half, ty - 2, tx + half + 1, ty + 6, 0x80FFFFFF);
            int kx = tx + fill;
            g.fill(kx - 2, ty - 3, kx + 2, ty + 7, hot ? 0xFFFFF4C0 : 0xFFF2EADC);
            g.fill(kx - 1, ty - 2, kx + 1, ty + 6, 0xFF8A6A2A);
            String pct = Math.round(v * 100) + " %";
            UiKit.text(g, Component.literal(pct), x + VOL_W - 8, ry + 1, v <= 0.001 ? RED : v > 1.001 ? 0xFFB070 : CREAM, 0.7f, 1, true);
        }
    }

    /**
     * Ecran agrandi (un seul bouton, pas de molette) : la camera entre dans la machine, l'ecran remplit la vue et la
     * carrosserie l'entoure.
     */
    private void setZoom(boolean on) {
        CasinoCamera.setZoom(on);
        LAST_ZOOM.put(be.type, on);
    }

    /**
     * L'ecran de la machine prend toute la vue (zoom, intro) : le dock et la ligne d'etat se rangent pour ne rien en
     * cacher ; ils reviennent quand la souris descend vers le bas, ou quand un panneau est ouvert.
     */
    private void updateDock(int my) {
        boolean show = !hideDock() && (!autoHide() || my >= dockY - 30 || infoOpen || volumeOpen || keepDock()
                || (betBox != null && betBox.isFocused()));
        if (show == dockShown) return;
        dockShown = show;
        for (UiButton b : dockButtons) b.visible = show;
        if (betBox != null) betBox.visible = show;
    }

    /** Dock range tant que la souris n'est pas en bas : par defaut, quand l'ecran de la machine prend toute la vue. */
    protected boolean autoHide() {
        return CasinoCamera.immersive() >= 0.5f;
    }

    /** Dock cache quoi qu'il arrive, meme sous la souris (ex. bonus en cours : on ne voit que l'ecran de la machine). */
    protected boolean hideDock() {
        return false;
    }

    /** Garder le dock affiche meme quand l'ecran prend toute la vue (ex. achat du bonus en cours, encaissement). */
    protected boolean keepDock() {
        return false;
    }

    /** Le dock reste affiche : la camera cadre l'ecran de la machine au-dessus de lui (et de la ligne d'etat). */
    public boolean reservesDock() {
        return keepDock();
    }

    /** Solde de jetons affiche en haut a droite. */
    protected boolean showBalance() {
        return true;
    }

    /** Rayon de la souris dans le repere du modele {origine, direction}, ou null (camera pas en place, souris sur l'interface). */
    @Nullable
    protected float[] mouseRay(int mx, int my) {
        if (minecraft == null || minecraft.level == null || !CasinoCamera.isOn(be.getBlockPos()) || CasinoCamera.blend() < 0.95f || overUi(mx, my)) {
            return null;
        }
        return CasinoCamera.mouseRayModel(be, mx, my, width, height);
    }

    /** Zone 3D sous la souris (rayon lance depuis la camera de la machine). */
    private void updateHover(int mx, int my, float partial) {
        int i = pickIndex(mx, my, partial);
        hover = i >= 0 ? hotspots.get(i).id : -1;
        if (i >= 0) HotspotHighlight.set(be.getBlockPos(), hotspots.get(i), hotspotColor(hover));
        else HotspotHighlight.clear();
    }

    /** Indice (dans "hotspots") de la zone cliquable sous le point d'ecran, ou -1. */
    private int pickIndex(int mx, int my, float partial) {
        if (minecraft == null || minecraft.level == null || !CasinoCamera.isOn(be.getBlockPos()) || CasinoCamera.blend() < 0.95f || overUi(mx, my)) {
            return -1;
        }
        MachineDesign<?> d = Designs.get(be.type.id);
        if (!d.isBuilt()) return -1;
        float yRot = MachineRenderer.yRot(be.getBlockState());
        Scene s = MachineRenderer.scene(be, minecraft.level, partial, yRot, minecraft.gameRenderer.getMainCamera().getPosition());
        Object view = Views.view(be, s);
        d.hotspotsRaw(view, s, hotspots);
        if (hotspots.size() == 0) return -1;
        float[] r = CasinoCamera.mouseRayModel(be, mx, my, width, height);
        return hotspots.pick(r[0], r[1], r[2], r[3], r[4], r[5], this::hotspotEnabled);
    }

    // =================================================================== tests automatiques
    /** Tests automatiques : position de souris imposee (null = vraie souris). */
    public static int[] testMouse;

    /** Tests automatiques : un point de l'ecran ou se trouve la zone 3D "id" (recherche sur une grille), ou null. */
    public int[] findHotspot(int id) {
        for (int y = 4; y < height; y += 4) {
            for (int x = 4; x < width; x += 4) {
                int i = pickIndex(x, y, lastPartial);
                if (i >= 0 && hotspots.get(i).id == id) return new int[]{x, y};
            }
        }
        return null;
    }

    /** Tests automatiques : ouvre ou ferme l'explication. */
    public void toggleInfo() {
        infoOpen = !infoOpen;
    }

    /** Tests automatiques : ouvre ou ferme le panneau du volume. */
    public void toggleVolume() {
        volumeOpen = !volumeOpen;
    }

    protected boolean overUi(int mx, int my) {
        if (mx >= dockX - PAD && mx < dockX + dockW + PAD && my >= dockY - 20 && my < dockY + DOCK_H + 4) return true;
        if (infoOpen && mx < 12 + Math.min(210, width / 2) + 4 && my < dockY - 36) return true;
        if (volumeOpen && mx >= volX() - 4 && mx < volX() + VOL_W + 4 && my >= volY() - 4 && my < volY() + VOL_H + 4) return true;
        return my < 30 && mx > width - 140;
    }

    // =================================================================== entrees
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        watch = false;
        int row = volumeRow(mx, my);
        if (row >= 0 && button == 0) {
            volumeDrag = row;
            dragVolume(row, mx);
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        setFocused(null);
        if (button == 0 && hover >= 0) {
            onHotspot(hover);
            return true;
        }
        if (infoOpen || volumeOpen) {
            infoOpen = false;
            volumeOpen = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (volumeDrag >= 0) {
            dragVolume(volumeDrag, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (volumeDrag >= 0) {
            volumeDrag = -1;
            saveVolume();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (betX >= 0 && mx >= betX && mx < betX + BET_W && my >= dockY && my < dockY + DOCK_H) {
            setBet(delta > 0 ? bet + step(bet, true) : bet - step(bet, false));
            return true;
        }
        int row = volumeRow(mx, my);
        if (row >= 0) {
            setVolume(row, volume(row) + (delta > 0 ? 0.1 : -0.1));
            saveVolume();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key != GLFW.GLFW_KEY_ESCAPE) watch = false;
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (betBox != null && betBox.isFocused()) {
            if (enter) {
                setFocused(null);
                primary();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_SPACE || enter) {
            primary();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // =================================================================== mise et jetons
    protected long minBet() {
        return CasinoConfig.get().limits(be.type)[0];
    }

    protected long maxBet() {
        return CasinoConfig.get().limits(be.type)[1];
    }

    protected long balance() {
        return minecraft != null && minecraft.player != null ? Chips.count(minecraft.player) : 0;
    }

    protected void setBet(long v) {
        bet = Math.max(minBet(), Math.min(maxBet(), v));
        if (betBox != null) betBox.setValue(Long.toString(bet));
    }

    /**
     * Verifie la mise avant de l'envoyer (le serveur verifie aussi) : limites de la machine et solde.
     * @param total somme retiree (mise x nombre de billes, etc.)
     */
    protected boolean checkBet(long total) {
        if (bet < minBet() || bet > maxBet()) {
            notice(Component.translatable("message.heralizcasino.bet_limits", Chips.format(minBet()), Chips.format(maxBet())).withStyle(ChatFormatting.RED));
            setBet(bet);
            return false;
        }
        if (balance() < total) {
            notice(Component.translatable("message.heralizcasino.not_enough", Chips.format(total), Chips.format(balance())).withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static long step(long bet, boolean up) {
        long b = up ? bet : Math.max(1, bet - 1);
        if (b < 10) return 1;
        if (b < 100) return 5;
        if (b < 1000) return 50;
        if (b < 10000) return 500;
        return 5000;
    }

    private static int chipIndex(long amount) {
        int idx = 0;
        for (int i = 0; i < Chips.VALUES.length; i++) if (amount >= Chips.VALUES[i]) idx = i;
        return idx;
    }

    // =================================================================== utilitaires
    protected long now() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    protected float partialNow() {
        return now() + lastPartial;
    }

    @Nullable
    protected UUID me() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
    }

    protected boolean isMe(@Nullable UUID id) {
        return id != null && id.equals(me());
    }

    protected void send(int action, long... args) {
        CasinoNet.CHANNEL.sendToServer(new ActionPacket(be.getBlockPos(), action, args));
    }

    protected void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    protected void chipSound() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModRegistry.sound(Snd.CHIP), 1.0F));
    }

    protected void notice(Component c) {
        notice = c;
        noticeUntil = System.currentTimeMillis() + 2600;
    }

    /** Message du serveur (barre d'action), affiche au-dessus du dock. */
    public void serverMessage(Component c) {
        notice = c;
        noticeUntil = System.currentTimeMillis() + 3500;
    }

    public static String mult(double m) {
        return UiKit.mult(m);
    }
}
