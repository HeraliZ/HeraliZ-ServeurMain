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
 * Interface d'ouverture (accroupi + clic droit sur une caisse, maquette validée) : la sorte et le nom de la caisse, une
 * bande de cartes qui défile puis ralentit sous un repère (façon CS:GO), le lot gagné (nom, rareté, chance), le bouton
 * principal (Ouvrir, Passer pendant la roue, Ouvrir encore ; touche Espace) et Fermer, les clés, et le contenu complet
 * de la caisse sur demande. Une caisse à la fois ; Passer donne le résultat tout de suite. Tout est décidé par le serveur.
 */
public class CaseOpenScreen extends Screen {
    private enum State { IDLE, WAIT, SPIN, DONE }

    private static final int CARD_W = 50, CARD_H = 63, GAP = 4, STEP = CARD_W + GAP;
    private static final int BAND_H = 86, ITEM_PX = 30;
    /** Hauteur du bloc (de la sorte de caisse aux clés), pour le centrer. */
    private static final int BLOC_H = 226;
    /** Lignes de la liste du contenu. */
    private static final int LIGNE_H = 18;
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

    private int cx, top, bandTop, bandW, resultY, buttonsY, keysY;
    private HzButton openBtn, closeBtn, contentsBtn;
    /** Contenu de la caisse affiché (liste), et son défilement. */
    private boolean showContents;
    private int contentsScroll;
    private List<Packets.Card> sorted = List.of();

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
        bandW = Math.max(3 * STEP, width - 16);
        top = Math.max(8, Math.round((height - BLOC_H) / 2f) - 19);
        bandTop = top + 52;
        resultY = top + 150;
        buttonsY = top + 192;
        keysY = top + 217;
        int bw1 = 114, bw2 = 81, gap = 5, x0 = cx - (bw1 + gap + bw2) / 2;
        openBtn = addRenderableWidget(new HzButton(x0, buttonsY, bw1, 17, Component.empty(), type.glow, this::primary).look(HzButton.Look.PRIMARY));
        closeBtn = addRenderableWidget(new HzButton(x0 + bw1 + gap, buttonsY, bw2, 17,
                Component.translatable("gui.heralizcrates.case.close"), type.glow, this::onClose));
        // lien « Voir le contenu » : bouton sans texte, posé sur le texte souligné dessiné par renderKeys
        contentsBtn = addRenderableWidget(new HzButton(cx, keysY, 10, 10, Component.empty(), type.glow, this::toggleContents).look(HzButton.Look.LINK));
        sorted = new ArrayList<>(contents);
        sorted.sort((a, b) -> a.rarity() != b.rarity() ? Integer.compare(b.rarity(), a.rarity()) : Float.compare(a.chance(), b.chance()));
        countKeys();
        refreshButtons();
    }

    /** Bouton principal : ouvrir (au repos ou après un tirage) ou passer la roue. */
    private void primary() {
        if (state == State.SPIN) skip();
        else open();
    }

    private void toggleContents() {
        showContents = !showContents;
        contentsScroll = 0;
    }

    private boolean busy() {
        return state == State.WAIT || state == State.SPIN;
    }

    private void refreshButtons() {
        if (openBtn == null) return;
        openBtn.active = state == State.SPIN || (!busy() && (free || keys >= 1) && !contents.isEmpty());
    }

    /** Libellé du bouton principal selon l'état de la roue. */
    private String primaryLabel() {
        String k = state == State.SPIN ? "gui.heralizcrates.case.skip" : state == State.DONE ? "gui.heralizcrates.case.again" : "gui.heralizcrates.case.open";
        return Component.translatable(k).getString();
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
        if (key == GLFW.GLFW_KEY_ESCAPE && showContents) {
            showContents = false;
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showContents) {
            contentsScroll = Mth.clamp(contentsScroll - (int) Math.signum(delta) * LIGNE_H, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // la liste du contenu se ferme d'un clic à côté
        if (showContents && !contentsBtn.isMouseOver(mouseX, mouseY)) {
            int[] r = contentsRect();
            if (mouseX < r[0] || mouseX >= r[0] + r[2] || mouseY < r[1] || mouseY >= r[1] + r[3]) {
                showContents = false;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) spaceHeld = false;
        return super.keyReleased(key, scan, mods);
    }

    // =================================================================== rendu
    @Override
    public void renderBackground(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, 0xB8050608, 0xD8050608);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        now = ticks + partial;
        if (state == State.SPIN) view = spinPos(now);
        else if (state == State.IDLE && idleWrap) view = pos + 0.3f * partial;
        else view = pos;
        renderBackground(g);
        int glow = type.glow & 0xFFFFFF;

        // sorte de caisse et rareté, puis le nom entre deux filets à sa couleur
        String sorte = (Component.translatable(categoryKey()).getString() + "  ·  " + type.tier.displayName().getString()).toUpperCase(Locale.ROOT);
        float sw = Ui.spacedWidth(font, sorte, CardArt.MINI_GRAS, 2.0f);
        Ui.spaced(g, font, sorte, CardArt.MINI_GRAS, cx - sw / 2f, top, Ui.argb(Ui.mix(glow, 0xFFFFFF, 0.2f), 255), 2.0f);
        String title = type.crateName().getString().toUpperCase(Locale.ROOT);
        float ts = 1.58f, tw = Ui.spacedWidth(font, title, Ui.BOLD, 2.2f) * ts;
        titre(g, title, cx - tw / 2f, top + 9, ts, glow);
        int ly = top + 19;
        Ui.hairline(g, (int) (cx - tw / 2 - 49), (int) (cx - tw / 2 - 9), ly, glow, 220, true, false);
        Ui.hairline(g, (int) (cx + tw / 2 + 9), (int) (cx + tw / 2 + 49), ly, glow, 220, false, true);

        renderBand(g);
        renderResult(g);
        super.render(g, mouseX, mouseY, partial);
        renderPrimaryLabel(g);
        renderKeys(g);

        if (showContents) {
            renderContents(g, mouseX, mouseY);
        } else if (state != State.SPIN && state != State.WAIT) {
            // infobulle d'une carte (roue à l'arrêt)
            Packets.Card hovered = cardAt(mouseX, mouseY);
            if (hovered != null) Ui.tooltip(g, font, CardArt.tooltip(hovered), mouseX, mouseY, CardArt.color(hovered.rarity()));
        }
    }

    /** Sorte de la caisse (« Caisse de boss », « Caisse de vote »…). */
    private String categoryKey() {
        return switch (type) {
            case SUJET_N07, SYLVORN, MERE_DES_RUINES, PORTEUR_DE_PESTE -> "gui.heralizcrates.case.kind.boss";
            case VOTE -> "gui.heralizcrates.case.kind.vote";
            case QUETE -> "gui.heralizcrates.case.kind.quest";
            case EVENT -> "gui.heralizcrates.case.kind.event";
            default -> "gui.heralizcrates.case.kind.items";
        };
    }

    /** Grand titre en capitales espacées avec un halo de la couleur de la caisse. */
    private void titre(GuiGraphics g, String s, float x, float y, float sc, int glow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1);
        int halo = Ui.argb(glow, 46);
        Ui.spaced(g, font, s, Ui.BOLD, -0.6f, 0, halo, 2.2f);
        Ui.spaced(g, font, s, Ui.BOLD, 0.6f, 0, halo, 2.2f);
        Ui.spaced(g, font, s, Ui.BOLD, 0, -0.6f, halo, 2.2f);
        Ui.spaced(g, font, s, Ui.BOLD, 0, 0.6f, halo, 2.2f);
        Ui.spaced(g, font, s, Ui.BOLD, 0, 0, Ui.argb(0xFFFFFF, 255), 2.2f);
        g.pose().popPose();
    }

    /** Libellé du bouton principal et la touche Espace dans sa pastille. */
    private void renderPrimaryLabel(GuiGraphics g) {
        String label = primaryLabel().toUpperCase(Locale.ROOT);
        String key = Component.translatable("gui.heralizcrates.case.key_space").getString().toUpperCase(Locale.ROOT);
        float lw = Ui.spacedWidth(font, label, Ui.BOLD, 1.2f) * 0.9f, kw = Ui.spacedWidth(font, key, CardArt.MINI_GRAS, 0.8f) + 8;
        float x = openBtn.getX() + (openBtn.getWidth() - (lw + 6 + kw)) / 2f;
        int y = openBtn.getY();
        int text = openBtn.active ? 0xFFFFFFFF : Ui.argb(0xFFFFFF, 90);
        g.pose().pushPose();
        g.pose().translate(x, y + 5f, 0);
        g.pose().scale(0.9f, 0.9f, 1);
        Ui.spaced(g, font, label, Ui.BOLD, 0, 0, text, 1.2f);
        g.pose().popPose();
        int kx = Math.round(x + lw + 6);
        CardArt.soft(g, kx, y + 4, Math.round(kw), 9, Ui.argb(0xFFFFFF, openBtn.active ? 28 : 12));
        CardArt.softOutline(g, kx + 1, y + 5, Math.round(kw) - 2, 7, Ui.argb(0xFFFFFF, openBtn.active ? 70 : 30));
        Ui.spaced(g, font, key, CardArt.MINI_GRAS, kx + 4, y + 5.5f, text, 0.8f);
    }

    /** Clés restantes (pastille à la couleur de la caisse) et le lien vers le contenu. */
    private void renderKeys(GuiGraphics g) {
        String k;
        int col = type.glow & 0xFFFFFF;
        if (free) {
            k = Component.translatable("gui.heralizcrates.case.keys_free").getString();
        } else if (keys == 0) {
            k = Component.translatable("gui.heralizcrates.case.keys_none").getString();
            col = Ui.DANGER;
        } else {
            k = Component.translatable(keys == 1 ? "gui.heralizcrates.case.keys_one" : "gui.heralizcrates.case.keys_many", keys).getString();
        }
        k = k.toUpperCase(Locale.ROOT);
        int pw = Math.round(Ui.spacedWidth(font, k, CardArt.MINI_GRAS, 0.8f)) + 10;
        Component lien = Ui.lit(Component.translatable("gui.heralizcrates.case.contents").getString(), CardArt.MINI);
        int lw = font.width(lien);
        int total = pw + 8 + lw, x0 = cx - total / 2;
        CardArt.soft(g, x0, keysY, pw, 10, Ui.argb(col, 34));
        CardArt.softOutline(g, x0 + 1, keysY + 1, pw - 2, 8, Ui.argb(col, 110));
        Ui.spaced(g, font, k, CardArt.MINI_GRAS, x0 + 5, keysY + 1.5f, Ui.argb(Ui.mix(col, 0xFFFFFF, 0.3f), 255), 0.8f);
        // le lien : bouton invisible posé sur le texte souligné
        int lx = x0 + pw + 8;
        contentsBtn.setBounds(lx, keysY, lw, 10);
        boolean hover = contentsBtn.isHoveredOrFocused();
        g.drawString(font, lien, lx, keysY + 1, Ui.argb(hover || showContents ? Ui.TEXT : Ui.MUTED, 255), false);
        g.fill(lx, keysY + 9, lx + lw, keysY + 10, Ui.argb(hover || showContents ? Ui.TEXT : Ui.MUTED, 140));
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
        // fond de la bande sur toute la largeur, fondu en haut et en bas ; filet à la couleur de la caisse
        int f = Math.round(BAND_H * 0.18f);
        g.fillGradient(0, by0, width, by0 + f, Ui.argb(0x07080A, 0), Ui.argb(0x07080A, 209));
        g.fill(0, by0 + f, width, by1 - f, Ui.argb(0x07080A, 209));
        g.fillGradient(0, by1 - f, width, by1, Ui.argb(0x07080A, 209), Ui.argb(0x07080A, 0));
        Ui.hairline(g, Math.round(width * 0.1f), Math.round(width * 0.9f), by0 + f - 1, glow, 136, true, true);

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
            CardArt.rays(g, wx, y + CARD_H / 2f, 110, CardArt.color(win.rarity()), now, settle);
        }
        for (int i = first; i <= last; i++) {
            Packets.Card c = at(i);
            if (c == null) continue;
            int x = Math.round(cx + (i * STEP - view));
            if (done && i == winIndex) {
                float sc = 1 + 0.1f * settle;
                g.pose().pushPose();
                g.pose().translate(x + CARD_W / 2f, y + CARD_H / 2f - 3 * settle, 20);
                g.pose().scale(sc, sc, 1);
                g.pose().translate(-(x + CARD_W / 2f), -(y + CARD_H / 2f), 0);
                CardArt.card(g, font, c, x, y, CARD_W, CARD_H, ITEM_PX, 0.75f + 0.25f * (float) Math.sin(now * 0.15f), 0);
                g.pose().popPose();
            } else {
                CardArt.card(g, font, c, x, y, CARD_W, CARD_H, ITEM_PX, 0, done ? 0.8f * settle : 0);
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        // éclat à l'arrêt, centré sur le repère
        if (win != null && since < 16) {
            float a = 1 - since / 16f;
            Ui.band(g, cx - 100, by0 + 1, cx + 100, by1 - 1, CardArt.color(win.rarity()), (int) (70 * a), 90);
        }
        // bords : les cartes se fondent dans la bande
        int fade = Math.min(80, bandW / 5);
        for (int i = 0; i < fade / 2; i++) {
            float t = (i + 0.5f) / (fade / 2f);
            int a = (int) (209 * (1 - t) * (1 - t));
            g.fill(bx0 + i * 2, by0 + 1, bx0 + i * 2 + 2, by1 - 1, Ui.argb(0x07080A, a));
            g.fill(bx1 - i * 2 - 2, by0 + 1, bx1 - i * 2, by1 - 1, Ui.argb(0x07080A, a));
        }
        g.pose().popPose();
        g.disableScissor();

        // repère : filet blanc avec un halo à la couleur de la caisse
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        g.fill(cx - 4, by0 + 1, cx + 5, by1 - 1, Ui.argb(glow, 22));
        g.fill(cx - 2, by0 + 1, cx + 3, by1 - 1, Ui.argb(glow, 46));
        g.fill(cx - 1, by0 + 1, cx + 2, by1 - 1, Ui.argb(glow, 90));
        g.fill(cx, by0 + 1, cx + 1, by1 - 1, Ui.argb(0xFFFFFF, 245));
        g.pose().popPose();
    }

    private void renderResult(GuiGraphics g) {
        if (error != null && ticks - errorAt < 80) {
            Ui.centered(g, font, Ui.in(error, Ui.SMALL), cx, resultY + 6, Ui.argb(Ui.DANGER, 230));
            return;
        }
        if (contents.isEmpty()) {
            Ui.centered(g, font, Ui.tr(Ui.SMALL, "message.heralizcrates.empty"), cx, resultY + 6, Ui.argb(Ui.DANGER, 200));
            return;
        }
        if (state != State.DONE || winIndex < 0 || winIndex >= strip.size() || idleWrap) return;
        Packets.Card c = strip.get(winIndex);
        LootRarity r = CardArt.rarity(c.rarity());
        int col = r.color & 0xFFFFFF;
        float a = Mth.clamp((now - doneAt) / 6f, 0f, 1f);
        int alpha = (int) (255 * a);
        if (alpha < 8) return;
        String nom = c.stack().getHoverName().getString() + (c.count() > 1 ? "  ×" + c.count() : "");
        Component name = Ui.lit(nom, Ui.BOLD);
        float sc = 1.42f, nw = font.width(name) * sc;
        g.pose().pushPose();
        g.pose().translate(cx - nw / 2f, resultY + (1 - a) * 3, 0);
        g.pose().scale(sc, sc, 1);
        int halo = Ui.argb(col, alpha * 50 / 255);
        g.drawString(font, name, -1, 0, halo, false);
        g.drawString(font, name, 1, 0, halo, false);
        g.drawString(font, name, 0, -1, halo, false);
        g.drawString(font, name, 0, 1, halo, false);
        g.drawString(font, name, 0, 0, Ui.argb(col, alpha), false);
        g.pose().popPose();
        String ligne = (r.displayName().getString() + "  ·  " + Ui.pct(c.chance())).toUpperCase(Locale.ROOT);
        float lw = Ui.spacedWidth(font, ligne, CardArt.MINI_GRAS, 1.6f);
        Ui.spaced(g, font, ligne, CardArt.MINI_GRAS, cx - lw / 2f, resultY + 19 + (1 - a) * 3, Ui.argb(Ui.mix(col, 0xFFFFFF, 0.3f), alpha), 1.6f);
    }

    // =================================================================== contenu de la caisse
    /** Rectangle de la liste du contenu : {x, y, largeur, hauteur}. */
    private int[] contentsRect() {
        int w = Math.min(260, width - 24);
        int listH = Math.min(sorted.size() * LIGNE_H, Math.max(LIGNE_H * 3, keysY - 14 - (top + 30) - 24));
        int h = 24 + listH + 6;
        return new int[] {cx - w / 2, keysY - 8 - h, w, h};
    }

    private int maxScroll() {
        int[] r = contentsRect();
        return Math.max(0, sorted.size() * LIGNE_H - (r[3] - 30));
    }

    private void renderContents(GuiGraphics g, int mouseX, int mouseY) {
        int[] r = contentsRect();
        int x0 = r[0], y0 = r[1], w = r[2], h = r[3];
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        CardArt.soft(g, x0, y0, w, h, 0xF20B0C0F);
        CardArt.softOutline(g, x0 + 1, y0 + 1, w - 2, h - 2, Ui.argb(0xFFFFFF, 20));
        Ui.hairline(g, x0 + 10, x0 + w - 10, y0, type.glow & 0xFFFFFF, 200, true, true);
        String titre = Component.translatable("gui.heralizcrates.case.contents_title", sorted.size()).getString().toUpperCase(Locale.ROOT);
        Ui.spaced(g, font, titre, CardArt.MINI_GRAS, x0 + 8, y0 + 8, Ui.argb(Ui.MUTED, 255), 1.2f);
        int listTop = y0 + 22, listH = h - 28;
        g.enableScissor(x0 + 2, listTop, x0 + w - 2, listTop + listH);
        Packets.Card hovered = null;
        for (int i = 0; i < sorted.size(); i++) {
            int y = listTop + i * LIGNE_H - contentsScroll;
            if (y + LIGNE_H < listTop || y > listTop + listH) continue;
            Packets.Card c = sorted.get(i);
            LootRarity rar = CardArt.rarity(c.rarity());
            int col = rar.color & 0xFFFFFF;
            boolean hov = mouseX >= x0 + 4 && mouseX < x0 + w - 4 && mouseY >= Math.max(y, listTop) && mouseY < Math.min(y + LIGNE_H, listTop + listH);
            if (hov) {
                CardArt.soft(g, x0 + 4, y, w - 8, LIGNE_H, Ui.argb(0xFFFFFF, 12));
                hovered = c;
            }
            g.fill(x0 + 6, y + 4, x0 + 7, y + LIGNE_H - 4, Ui.argb(col, 255));
            CardArt.item(g, c.stack(), x0 + 18, y + LIGNE_H / 2f, 14);
            String nom = c.stack().getHoverName().getString() + (c.count() > 1 ? " ×" + c.count() : "");
            g.drawString(font, Ui.lit(nom, CardArt.MINI), x0 + 29, y + 6, Ui.argb(Ui.TEXT, 255), false);
            String pct = Ui.pct(c.chance());
            Component pc = Ui.lit(pct, CardArt.MINI_GRAS);
            g.drawString(font, pc, x0 + w - 8 - font.width(pc), y + 6, Ui.argb(0xFFFFFF, 230), false);
            String rn = rar.displayName().getString().toUpperCase(Locale.ROOT);
            int rw = Math.round(Ui.spacedWidth(font, rn, CardArt.MINI_GRAS, 0.6f)) + 8;
            int rx = x0 + w - 8 - font.width(pc) - 8 - rw;
            CardArt.soft(g, rx, y + 4, rw, 10, Ui.argb(col, 30));
            CardArt.softOutline(g, rx + 1, y + 5, rw - 2, 8, Ui.argb(col, 100));
            Ui.spaced(g, font, rn, CardArt.MINI_GRAS, rx + 4, y + 5.5f, Ui.argb(Ui.mix(col, 0xFFFFFF, 0.3f), 255), 0.6f);
        }
        g.disableScissor();
        g.pose().popPose();
        if (hovered != null) Ui.tooltip(g, font, CardArt.tooltip(hovered), mouseX, mouseY, CardArt.color(hovered.rarity()));
    }
}
