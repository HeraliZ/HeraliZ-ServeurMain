package fr.heraliz.crates.client.gui;

import fr.heraliz.crates.block.CrateAnim;

import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateKeyItem;
import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.network.HzNet;
import fr.heraliz.crates.network.Packets;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interface d'ouverture (accroupi + clic droit sur une caisse), volontairement epuree : le nom de la caisse, une bande
 * d'objets qui defile puis ralentit sous un repere (facon CS:GO), le lot gagne, deux boutons (Ouvrir, Skip) et le
 * nombre de cles. Une caisse a la fois ; Skip donne le resultat tout de suite. Tout est decide par le serveur.
 */
public class CaseOpenScreen extends Screen {
    private enum State { IDLE, WAIT, SPIN, DONE }

    private static final int CARD_W = 40, CARD_H = 50, GAP = 4, STEP = CARD_W + GAP;
    private static final int BAND_H = 62, ITEM_PX = 26;
    /** Cartes gardees de la bande precedente : la roue repart de ce qu'on voit. */
    private static final int KEEP = 5;

    private final CrateType type;
    private final int session;
    private final boolean free;
    private final List<Packets.Card> contents;
    private final RandomSource random = RandomSource.create();

    private State state = State.IDLE;
    private State before = State.IDLE;
    private int ticks;
    private float now;
    private int waitSince, lastRequest = -100;
    private boolean spaceHeld;

    private List<Packets.Card> strip = new ArrayList<>();
    private boolean idleWrap = true;
    /** pos : coordonnee de la bande sous le repere (px) ; view : la meme, interpolee pour l'image en cours. */
    private float pos, view, spinFrom, spinTo;
    private int spinStart, winIndex = -1, lastCard, doneAt = -1000;

    @Nullable
    private Component error;
    private int errorAt;
    private int keys;

    private int cx, titleY, bandTop, bandW, resultY, keysY;
    private HzButton openBtn, skipBtn;

    public CaseOpenScreen(CrateType type, int session, boolean free, List<Packets.Card> contents) {
        super(type.crateName());
        this.type = type;
        this.session = session;
        this.free = free;
        this.contents = contents;
        buildIdleStrip();
    }

    /** Bande de repos : des objets du contenu tires selon leurs chances (surtout des communs, comme la vraie roue). */
    private void buildIdleStrip() {
        strip = new ArrayList<>();
        double total = 0;
        for (Packets.Card c : contents) total += Math.max(0, c.chance());
        for (int i = 0; i < 40 && !contents.isEmpty(); i++) {
            double r = random.nextDouble() * total;
            Packets.Card pick = contents.get(contents.size() - 1);
            for (Packets.Card c : contents) {
                r -= Math.max(0, c.chance());
                if (r < 0) {
                    pick = c;
                    break;
                }
            }
            strip.add(pick);
        }
        idleWrap = true;
        pos = view = 3 * STEP + CARD_W / 2f;
    }

    // =================================================================== mise en page
    @Override
    protected void init() {
        cx = width / 2;
        bandW = Math.min(7 * STEP, width - 48);
        int total = 24 + BAND_H + 12 + 30 + 18 + 8 + 8;
        titleY = Math.max(6, (height - total) / 2);
        bandTop = titleY + 24;
        resultY = bandTop + BAND_H + 12;
        int buttonsY = resultY + 30;
        keysY = buttonsY + 18 + 8;
        int bw = 74, gap = 8;
        openBtn = addRenderableWidget(new HzButton(cx - bw - gap / 2, buttonsY, bw, 18,
                Component.translatable("gui.heralizcrates.case.open"), type.glow, this::open).look(HzButton.Look.PRIMARY));
        skipBtn = addRenderableWidget(new HzButton(cx + gap / 2, buttonsY, bw, 18,
                Component.translatable("gui.heralizcrates.case.skip"), type.glow, this::skip));
        countKeys();
        refreshButtons();
    }

    private boolean busy() {
        return state == State.WAIT || state == State.SPIN;
    }

    private void refreshButtons() {
        if (openBtn == null) return;
        openBtn.active = !busy() && (free || keys >= 1) && !contents.isEmpty();
        skipBtn.active = state == State.SPIN;
    }

    private void countKeys() {
        if (minecraft == null || minecraft.player == null) return;
        Inventory inv = minecraft.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof CrateKeyItem k && k.type == type) n += st.getCount();
        }
        keys = n;
    }

    // =================================================================== actions
    private void open() {
        if (busy() || ticks - lastRequest < 6 || !(free || keys >= 1) || contents.isEmpty()) return;
        lastRequest = ticks;
        before = state;
        state = State.WAIT;
        waitSince = ticks;
        error = null;
        play(ModRegistry.SND_KEY_TURN.get(), 1.1f, 0.45f);
        HzNet.toServer(new Packets.CaseOpen(session));
        refreshButtons();
    }

    private void skip() {
        if (state != State.SPIN) return;
        spinStart = ticks - CrateAnim.REEL_TICKS;
        finishSpin();
        HzNet.toServer(new Packets.CaseSkip());
    }

    /** Reponse du serveur. */
    public void onResult(Packets.CaseResult r) {
        if (!r.error().isEmpty() || r.winIndex() < 0 || r.winIndex() >= r.strip().size()) {
            state = before;
            if (!r.error().isEmpty() && !r.error().equals("gui.heralizcrates.case.error.busy")) {
                error = Component.translatable(r.error());
                errorAt = ticks;
                play(ModRegistry.SND_LOCKED.get(), 1f, 0.5f);
            }
            refreshButtons();
            return;
        }
        startSpin(r.strip(), r.winIndex());
        play(ModRegistry.SND_ROLL_START.get(), 1f, 0.5f);
        refreshButtons();
    }

    private void startSpin(List<Packets.Card> serverStrip, int win) {
        List<Packets.Card> next = new ArrayList<>(serverStrip);
        int k = Mth.floor(pos / STEP);
        float offset = pos - k * STEP;
        if (next.size() > 2 * KEEP + 2 && win > 2 * KEEP + 1) {
            for (int j = -KEEP; j <= KEEP; j++) {
                Packets.Card c = at(k + j);
                if (c != null) next.set(KEEP + j, c);
            }
            spinFrom = KEEP * STEP + offset;
        } else {
            spinFrom = 3 * STEP + CARD_W / 2f;
        }
        strip = next;
        idleWrap = false;
        winIndex = win;
        // arret n'importe ou sur la carte gagnante, parfois tout au bord (suspense)
        spinTo = win * STEP + CARD_W * (0.08f + 0.84f * random.nextFloat());
        spinStart = ticks;
        pos = view = spinFrom;
        lastCard = Mth.floor(pos / STEP);
        state = State.SPIN;
    }

    private void finishSpin() {
        pos = view = spinTo;
        state = State.DONE;
        doneAt = ticks;
        play(ModRegistry.SND_ROLL_STOP.get(), 1f, 0.7f);
        if (winIndex >= 0 && winIndex < strip.size()) {
            int tier = CardArt.rarity(strip.get(winIndex).rarity()).soundTier;
            SoundEvent s = tier >= 2 ? ModRegistry.SND_REWARD_LEGENDARY.get() : tier == 1 ? ModRegistry.SND_REWARD_RARE.get() : ModRegistry.SND_REWARD_COMMON.get();
            play(s, 1f, 0.8f);
        }
        refreshButtons();
    }

    private void play(SoundEvent s, float pitch, float volume) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    @Nullable
    private Packets.Card at(int i) {
        if (strip.isEmpty()) return null;
        if (idleWrap) return strip.get(Math.floorMod(i, strip.size()));
        return i >= 0 && i < strip.size() ? strip.get(i) : null;
    }

    private float spinPos(float t) {
        float u = Mth.clamp((t - spinStart) / CrateAnim.REEL_TICKS, 0f, 1f);
        float e = 1f - (1f - u) * (1f - u) * (1f - u);
        return spinFrom + (spinTo - spinFrom) * e;
    }

    // =================================================================== boucle
    @Override
    public void tick() {
        ticks++;
        countKeys();
        if (state == State.IDLE && idleWrap) pos += 0.3f;
        if (state == State.WAIT && ticks - waitSince > 60) state = before;
        if (state == State.SPIN) {
            float p = spinPos(ticks);
            int card = Mth.floor(p / STEP);
            if (card != lastCard) {
                lastCard = card;
                float speed = Math.abs(spinPos(ticks) - spinPos(ticks - 1));
                play(ModRegistry.SND_ROLL_TICK.get(), 0.9f + Math.min(0.35f, speed / 160f) + random.nextFloat() * 0.06f, 0.4f);
            }
            if (ticks - spinStart >= CrateAnim.REEL_TICKS) finishSpin();
        }
        refreshButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // aussi appele pendant une deconnexion : plus rien a envoyer si la connexion n'existe plus
        if (minecraft != null && minecraft.getConnection() != null) HzNet.toServer(new Packets.CaseClose(session));
        super.removed();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // un appui = une action : garder la touche enfoncee n'enchaine pas les ouvertures
            if (!spaceHeld) {
                spaceHeld = true;
                if (state == State.SPIN) skip();
                else open();
            }
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) spaceHeld = false;
        return super.keyReleased(key, scan, mods);
    }

    // =================================================================== rendu
    @Override
    public void renderBackground(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, 0xC4050608, 0xDC050608);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        now = ticks + partial;
        if (state == State.SPIN) view = spinPos(now);
        else if (state == State.IDLE && idleWrap) view = pos + 0.3f * partial;
        else view = pos;
        renderBackground(g);
        int glow = type.glow & 0xFFFFFF;

        // titre : nom de la caisse en capitales espacees entre deux filets a sa couleur
        String title = type.crateName().getString();
        float tw = Ui.spacedWidth(font, title.toUpperCase(Locale.ROOT), Ui.BOLD, 1.6f);
        Ui.spacedCentered(g, font, title, Ui.BOLD, cx, titleY, Ui.argb(Ui.TEXT, 255), 1.6f);
        int ly = titleY + 5;
        Ui.hairline(g, (int) (cx - tw / 2 - 52), (int) (cx - tw / 2 - 12), ly, glow, 220, true, false);
        Ui.hairline(g, (int) (cx + tw / 2 + 12), (int) (cx + tw / 2 + 52), ly, glow, 220, false, true);

        renderBand(g);
        renderResult(g);
        super.render(g, mouseX, mouseY, partial);

        // cles restantes
        MutableComponent k;
        int kc = Ui.argb(Ui.MUTED, 255);
        if (free) {
            k = Ui.tr(Ui.SMALL, "gui.heralizcrates.case.keys_free");
        } else if (keys == 0) {
            k = Ui.tr(Ui.SMALL, "gui.heralizcrates.case.keys_none");
            kc = Ui.argb(Ui.DANGER, 210);
        } else {
            k = Ui.tr(Ui.SMALL, keys == 1 ? "gui.heralizcrates.case.keys_one" : "gui.heralizcrates.case.keys_many", keys);
        }
        Ui.centered(g, font, k, cx, keysY, kc);

        // infobulle d'une carte (roue a l'arret)
        if (state != State.SPIN && state != State.WAIT) {
            Packets.Card hovered = cardAt(mouseX, mouseY);
            if (hovered != null) Ui.tooltip(g, font, CardArt.tooltip(hovered), mouseX, mouseY, CardArt.color(hovered.rarity()));
        }
    }

    private int cardsY() {
        return bandTop + (BAND_H - CARD_H) / 2;
    }

    @Nullable
    private Packets.Card cardAt(int mouseX, int mouseY) {
        int y0 = cardsY();
        if (mouseY < y0 || mouseY >= y0 + CARD_H || mouseX < cx - bandW / 2 || mouseX >= cx + bandW / 2) return null;
        float s = mouseX - cx + view;
        int i = Mth.floor(s / STEP);
        return s - i * STEP < CARD_W ? at(i) : null;
    }

    private void renderBand(GuiGraphics g) {
        int glow = type.glow & 0xFFFFFF;
        int bx0 = cx - bandW / 2, bx1 = cx + bandW / 2, by0 = bandTop, by1 = bandTop + BAND_H;
        // fond de la bande, qui deborde et s'efface de chaque cote
        Ui.band(g, bx0 - 48, by0, bx1 + 48, by1, 0x07080A, 225, 70);
        Ui.hairline(g, bx0 - 48, bx1 + 48, by0, glow, 120, true, true);
        Ui.hairline(g, bx0 - 48, bx1 + 48, by1 - 1, 0xFFFFFF, 26, true, true);

        g.enableScissor(bx0, by0 + 1, bx1, by1 - 1);
        int y = cardsY();
        int first = Mth.floor((view - bandW / 2f) / STEP) - 1, last = Mth.floor((view + bandW / 2f) / STEP) + 1;
        boolean done = state == State.DONE && winIndex >= 0 && winIndex < strip.size() && !idleWrap;
        float since = now - doneAt;
        float settle = Mth.clamp(since / 7f, 0f, 1f);
        settle = 1 - (1 - settle) * (1 - settle);
        Packets.Card win = done ? strip.get(winIndex) : null;
        if (win != null && win.rarity() >= LootRarity.LEGENDARY.ordinal()) {
            float wx = cx + (winIndex * STEP - view) + CARD_W / 2f;
            CardArt.rays(g, wx, y + CARD_H / 2f, 90, CardArt.color(win.rarity()), now, settle);
        }
        for (int i = first; i <= last; i++) {
            Packets.Card c = at(i);
            if (c == null) continue;
            int x = Math.round(cx + (i * STEP - view));
            if (done && i == winIndex) {
                float sc = 1 + 0.1f * settle;
                g.pose().pushPose();
                g.pose().translate(x + CARD_W / 2f, y + CARD_H / 2f, 20);
                g.pose().scale(sc, sc, 1);
                g.pose().translate(-(x + CARD_W / 2f), -(y + CARD_H / 2f), 0);
                CardArt.card(g, font, c, x, y, CARD_W, CARD_H, ITEM_PX, 0.75f + 0.25f * (float) Math.sin(now * 0.15f), 0);
                g.pose().popPose();
            } else {
                CardArt.card(g, font, c, x, y, CARD_W, CARD_H, ITEM_PX, 0, done ? 0.75f * settle : 0);
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        // eclat a l'arret, centre sur le repere
        if (win != null && since < 16) {
            float a = 1 - since / 16f;
            Ui.band(g, cx - 90, by0 + 1, cx + 90, by1 - 1, CardArt.color(win.rarity()), (int) (70 * a), 80);
        }
        // bords : les cartes se fondent dans la bande
        int fade = Math.min(64, bandW / 5);
        for (int i = 0; i < fade / 2; i++) {
            float t = (i + 0.5f) / (fade / 2f);
            int a = (int) (225 * (1 - t) * (1 - t));
            g.fill(bx0 + i * 2, by0 + 1, bx0 + i * 2 + 2, by1 - 1, Ui.argb(0x07080A, a));
            g.fill(bx1 - i * 2 - 2, by0 + 1, bx1 - i * 2, by1 - 1, Ui.argb(0x07080A, a));
        }
        g.pose().popPose();
        g.disableScissor();

        // repere : filet blanc, halo a la couleur de la caisse, encoches en haut et en bas
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        g.fill(cx - 3, by0 - 3, cx + 4, by1 + 3, Ui.argb(glow, 34));
        g.fill(cx - 1, by0 - 3, cx + 2, by1 + 3, Ui.argb(glow, 80));
        g.fill(cx, by0 - 5, cx + 1, by1 + 5, Ui.argb(0xFFFFFF, 240));
        for (int k = 0; k < 3; k++) {
            g.fill(cx - 2 + k, by0 - 7 + k, cx + 3 - k, by0 - 6 + k, Ui.argb(glow, 255));
            g.fill(cx - 2 + k, by1 + 6 - k, cx + 3 - k, by1 + 7 - k, Ui.argb(glow, 255));
        }
        g.pose().popPose();
    }

    private void renderResult(GuiGraphics g) {
        if (error != null && ticks - errorAt < 80) {
            Ui.centered(g, font, Ui.in(error, Ui.SMALL), cx, resultY + 5, Ui.argb(Ui.DANGER, 230));
            return;
        }
        if (contents.isEmpty()) {
            Ui.centered(g, font, Ui.tr(Ui.SMALL, "message.heralizcrates.empty"), cx, resultY + 5, Ui.argb(Ui.DANGER, 200));
            return;
        }
        if (state != State.DONE || winIndex < 0 || winIndex >= strip.size() || idleWrap) return;
        Packets.Card c = strip.get(winIndex);
        LootRarity r = CardArt.rarity(c.rarity());
        float a = Mth.clamp((now - doneAt) / 6f, 0f, 1f);
        int alpha = (int) (255 * a);
        if (alpha < 8) return;
        MutableComponent name = Ui.in(Component.empty().append(c.stack().getHoverName()).withStyle(st -> st.withColor(r.color)), Ui.BOLD);
        if (c.count() > 1) name.append(Ui.lit("  ×" + c.count(), Ui.FONT).withStyle(st -> st.withColor(Ui.MUTED)));
        g.pose().pushPose();
        g.pose().translate(0, (1 - a) * 3, 0);
        Ui.centered(g, font, name, cx, resultY, Ui.argb(0xFFFFFF, alpha));
        String rar = r.displayName().getString();
        String rest = "   ·   " + Ui.pct(c.chance());
        float rw = Ui.spacedWidth(font, rar.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f);
        float w = rw + 0.8f + Ui.spacedWidth(font, rest.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f);
        float x0 = cx - w / 2f;
        Ui.spaced(g, font, rar, Ui.SMALL, x0, resultY + 13, Ui.argb(Ui.mix(r.color & 0xFFFFFF, 0xFFFFFF, 0.2f), (int) (alpha * 0.9f)), 0.8f);
        Ui.spaced(g, font, rest, Ui.SMALL, x0 + rw + 0.8f, resultY + 13, Ui.argb(Ui.MUTED, (int) (alpha * 0.9f)), 0.8f);
        g.pose().popPose();
    }
}
