package com.caracrea.client.gui;

import com.caracrea.character.Appearance;
import com.caracrea.character.AppearanceRandomizer;
import com.caracrea.character.BodyType;
import com.caracrea.character.Catalog;
import com.caracrea.character.Option;
import com.caracrea.character.Scar;
import com.caracrea.character.Swatch;
import com.caracrea.character.Trait;
import com.caracrea.client.CompositeSkins;
import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.network.CaraCreaNetwork.SaveAppearance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The character creator: categories on the left, the character in the
 * middle (facing you, drag to turn, wheel to zoom), the choices of the
 * category on the right. Every change shows on the character within a frame
 * or two; nothing is sent to the server until "Confirm".
 */
public class CharacterCreatorScreen extends Screen {
    private static final int MARGIN = 10;
    private static final int GAP = 8;
    private static final int BAR = 26;
    private static final int ROW_GAP = 10;

    /** Groups of traits shown together, with where the camera looks. */
    enum Category {
        BODY("body", 0.95F, 2.3F, Trait.BODY_TYPE, Trait.SKIN_TONE),
        FACE("face", 1.72F, 0.82F, Trait.NOSE_STYLE, Trait.MOUTH_STYLE, Trait.MOUTH_WIDTH, Trait.LIP_COLOR, Trait.BROW_STYLE, Trait.FACIAL_HAIR),
        EYES("eyes", 1.74F, 0.66F, Trait.EYE_SHAPE, Trait.EYE_COLOR, Trait.EYE_SIZE, Trait.EYE_SPACING, Trait.EYE_HEIGHT, Trait.IRIS_SIZE),
        HAIR("hair", 1.66F, 1.1F, Trait.HAIR_STYLE, Trait.HAIR_COLOR),
        TOP("top", 1.12F, 1.25F, Trait.SHIRT_STYLE, Trait.SHIRT_COLOR, Trait.SHIRT_COLOR2),
        BOTTOM("bottom", 0.55F, 1.3F, Trait.PANTS_STYLE, Trait.PANTS_COLOR),
        SHOES("shoes", 0.2F, 0.8F, Trait.SHOES_STYLE, Trait.SHOES_COLOR),
        DETAILS("details", 0.95F, 2.3F, Trait.MARKS, Trait.WEAR);

        final String id;
        /** Height (in blocks above the feet) the camera centres on. */
        final float focus;
        /** How much of the character fits in the stage, in blocks. */
        final float frame;
        final Trait[] traits;

        Category(String id, float focus, float frame, Trait... traits) {
            this.id = id;
            this.focus = focus;
            this.frame = frame;
            this.traits = traits;
        }

        Component title() {
            return Component.translatable("caracrea.category." + id);
        }

        Component hint() {
            return Component.translatable("caracrea.category." + id + ".hint");
        }
    }

    private final Appearance original;
    private final boolean firstTime;
    private final Random random = new Random();
    private Appearance current;
    private PreviewPlayer preview;
    private Category category = Category.BODY;
    private final List<Row> rows = new ArrayList<>();

    // Layout.
    private int sideX, sideY, sideW, sideH;
    private int stageX, stageY, stageW, stageH;
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int barY;

    // Camera of the preview, eased towards its targets.
    private float yaw = -18;
    private float yawTarget = -18;
    private float pitch;
    private float frame = 2.3F;
    private float frameTarget = 2.3F;
    private float focus = 0.95F;
    private float focusTarget = 0.95F;
    private float headTurn;
    private float headTilt;
    // Minecraft puts the cursor in the middle of the screen on opening, often over the
    // character: the head only starts following it once the mouse has really moved.
    private double openMouseX = Double.NaN;
    private double openMouseY;
    private boolean mouseMovedSinceOpen;
    // Animations follow time, not frames, so they run at the same speed at any frame rate.
    private long lastFrame;
    private float step = 1;
    private boolean dragging;
    private double lastDragX;

    // Content scrolling and animations.
    private float scroll;
    private float scrollTarget;
    private int contentHeight;
    private float contentFade = 1;
    private float tabY = -1;
    private final Map<String, Float> hovers = new HashMap<>();
    private Row activeRow;
    private Component tooltip;
    private int selectedScar;

    public CharacterCreatorScreen(Appearance appearance, boolean firstTime) {
        super(Component.translatable("caracrea.screen.title"));
        this.original = appearance;
        this.current = appearance;
        this.firstTime = firstTime;
    }

    // ----------------------------------------------------------------- layout

    @Override
    protected void init() {
        if (preview == null && minecraft != null && minecraft.level != null) {
            preview = new PreviewPlayer(minecraft.level, current);
        }
        barY = height - MARGIN - BAR;
        sideX = MARGIN;
        sideY = MARGIN;
        sideW = Mth.clamp(width / 8, 74, 104);
        sideH = barY - GAP - sideY;
        stageX = sideX + sideW + GAP;
        stageY = MARGIN;
        stageW = Mth.clamp((int) (width * 0.3F), 110, 250);
        stageH = sideH;
        panelX = stageX + stageW + GAP;
        panelY = MARGIN;
        panelW = width - MARGIN - panelX;
        panelH = sideH;
        contentX = panelX + 10;
        contentY = panelY + 38;
        contentW = panelW - 24;
        contentH = panelY + panelH - 8 - contentY;
        setCategory(category, true);
    }

    private void setCategory(Category category, boolean instant) {
        boolean changed = this.category != category;
        this.category = category;
        focusTarget = category.focus;
        frameTarget = category.frame;
        if (instant) {
            focus = focusTarget;
            frame = frameTarget;
        }
        if (changed || instant) {
            scroll = 0;
            scrollTarget = 0;
            if (changed) {
                contentFade = 0;
            }
        }
        rows.clear();
        // Scars first on their page: placing them is the main thing there.
        if (category == Category.DETAILS) {
            rows.add(new ScarRow());
        }
        for (Trait trait : category.traits) {
            if (trait == Trait.BODY_TYPE) {
                rows.add(new SegmentRow(trait));
            } else if (trait.kind() == Trait.Kind.SLIDER) {
                rows.add(new SliderRow(trait));
            } else if (trait.kind() == Trait.Kind.FLAGS) {
                rows.add(new ToggleRow(trait));
            } else if (Catalog.isColor(trait)) {
                rows.add(new SwatchRow(trait));
            } else {
                rows.add(new ChipRow(trait));
            }
        }
        relayoutContent();
    }

    private void relayoutContent() {
        contentHeight = 0;
        for (Row row : rows) {
            contentHeight += row.height(contentW) + ROW_GAP;
        }
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - contentH);
    }

    // ------------------------------------------------------------------ state

    private void change(Trait trait, int value) {
        set(current.with(trait, value));
    }

    private void set(Appearance appearance) {
        if (!appearance.equals(current)) {
            current = appearance;
            if (preview != null) {
                preview.setAppearance(appearance);
            }
            relayoutContent();
        }
    }

    /** New random values for the current category only: the rest of the character stays as it is. */
    private void randomizeCategory() {
        Appearance sample = AppearanceRandomizer.random(random, current.bodyType());
        Appearance next = current;
        for (Trait trait : category.traits) {
            if (trait != Trait.BODY_TYPE) {
                next = next.with(trait, sample.get(trait));
            }
        }
        set(next);
    }

    /** A whole new character of the same body type; placed scars are kept. */
    private void randomizeAll() {
        Appearance next = AppearanceRandomizer.random(random, current.bodyType());
        for (int i = 0; i < Scar.COUNT; i++) {
            for (Trait trait : Scar.traits(i)) {
                next = next.with(trait, current.get(trait));
            }
        }
        set(next);
    }

    /** Switching body type keeps every choice; only facial hair and lipstick follow the usual defaults. */
    private void switchBodyType(BodyType type) {
        Appearance next = current.with(Trait.BODY_TYPE, type.ordinal());
        if (type == BodyType.FEMALE) {
            next = next.with(Trait.FACIAL_HAIR, 0);
        } else {
            next = next.with(Trait.LIP_COLOR, 0);
        }
        set(next);
    }

    private void confirm() {
        CaraCreaNetwork.CHANNEL.sendToServer(new SaveAppearance(current));
        close();
    }

    /** Closed by the server, even the first-time creator (it opens again once the player is free). */
    public void forceClose() {
        close();
    }

    private void close() {
        disposePreview();
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void disposePreview() {
        if (preview != null) {
            preview.dispose();
            preview = null;
        }
    }

    @Override
    public void onClose() {
        if (!firstTime) {
            close();
        }
    }

    @Override
    public void removed() {
        disposePreview();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !firstTime;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (preview != null) {
            preview.idleTick();
        }
    }

    // ----------------------------------------------------------------- render

    /** Share of the way to a target covered this frame, for a rate given per 1/60 s. */
    private float ease(float rate) {
        return 1 - (float) Math.pow(1 - rate, step);
    }

    private float hover(String key, boolean hovered) {
        float value = hovers.getOrDefault(key, 0F);
        value += ((hovered ? 1 : 0) - value) * ease(0.35F);
        hovers.put(key, value);
        return value;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tooltip = null;
        long now = System.nanoTime();
        step = lastFrame == 0 ? 1 : Mth.clamp((now - lastFrame) / 16_666_667F, 0.05F, 6);
        lastFrame = now;
        yaw += (yawTarget - yaw) * ease(0.25F);
        frame += (frameTarget - frame) * ease(0.2F);
        focus += (focusTarget - focus) * ease(0.2F);
        scroll += (scrollTarget - scroll) * ease(0.4F);
        contentFade = Math.min(1, contentFade + 0.12F * step);

        g.fillGradient(0, 0, width, height, Ui.BACKDROP_TOP, Ui.BACKDROP_BOTTOM);
        renderSidebar(g, mouseX, mouseY);
        renderStage(g, mouseX, mouseY);
        renderPanel(g, mouseX, mouseY);
        renderBar(g, mouseX, mouseY);
        if (tooltip != null) {
            g.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        Ui.shadow(g, sideX, sideY, sideW, sideH);
        Ui.round(g, sideX, sideY, sideW, sideH, 4, Ui.PANEL);
        Ui.label(g, font, Component.translatable("caracrea.screen.title"), sideX + 10, sideY + 11, sideW - 16, Ui.MUTED);
        Category[] categories = Category.values();
        int itemH = 20;
        int y0 = sideY + 26;
        float targetY = y0 + category.ordinal() * itemH;
        tabY = tabY < 0 ? targetY : tabY + (targetY - tabY) * ease(0.3F);
        // Selected highlight sliding between categories.
        Ui.round(g, sideX + 5, Math.round(tabY), sideW - 10, itemH - 2, 3, Ui.SURFACE_HOVER);
        g.fill(sideX + 5, Math.round(tabY) + 4, sideX + 7, Math.round(tabY) + itemH - 6, Ui.ACCENT);
        for (int i = 0; i < categories.length; i++) {
            int y = y0 + i * itemH;
            boolean selected = categories[i] == category;
            boolean hovered = Ui.inside(mouseX, mouseY, sideX + 5, y, sideW - 10, itemH - 2);
            float h = hover("cat" + i, hovered && !selected);
            if (h > 0.01F) {
                Ui.round(g, sideX + 5, y, sideW - 10, itemH - 2, 3, Ui.alpha(Ui.SURFACE, h));
            }
            int color = selected ? Ui.TEXT : Ui.mix(Ui.MUTED, Ui.TEXT_DIM, h);
            Ui.text(g, font, Ui.clip(font, categories[i].title(), sideW - 24), sideX + 13, y, itemH - 2, color);
        }
    }

    private void renderStage(GuiGraphics g, int mouseX, int mouseY) {
        Ui.shadow(g, stageX, stageY, stageW, stageH);
        Ui.round(g, stageX, stageY, stageW, stageH, 4, Ui.SUNKEN);
        // A soft light behind the character and a floor shadow.
        int cx = stageX + stageW / 2;
        g.fillGradient(stageX, stageY, stageX + stageW, stageY + stageH * 2 / 3, 0x00000000, 0x18E4A23F);
        g.fillGradient(stageX, stageY + stageH * 2 / 3, stageX + stageW, stageY + stageH, 0x18E4A23F, 0x00000000);
        // Fit the framed part of the character to the stage, in height and in width.
        float fit = Math.min((stageH - 12) / frame, (stageW - 16) / Math.min(frame, 1.05F));
        int scale = Math.max(12, Math.round(fit));
        int feetY = Math.round(stageY + stageH / 2F + focus * scale);
        g.enableScissor(stageX + 1, stageY + 1, stageX + stageW - 1, stageY + stageH - 1);
        if (feetY < stageY + stageH + scale / 4) {
            for (int i = 0; i < 6; i++) {
                int rx = Math.round(scale * (0.42F - i * 0.05F));
                int ry = Math.max(1, Math.round(scale * (0.09F - i * 0.012F)));
                g.fill(cx - rx, feetY - ry, cx + rx, feetY + ry, 0x10000000);
            }
        }
        if (preview != null && CompositeSkins.isPainted(preview.getUUID())) {
            // The character looks at you; the head glances at the mouse only while it is over
            // the stage, a little, and less when the camera is close to the face.
            float turnTarget = 0;
            float tiltTarget = 0;
            if (Double.isNaN(openMouseX)) {
                openMouseX = mouseX;
                openMouseY = mouseY;
            } else if (Math.abs(mouseX - openMouseX) + Math.abs(mouseY - openMouseY) > 6) {
                mouseMovedSinceOpen = true;
            }
            if (mouseMovedSinceOpen && Ui.inside(mouseX, mouseY, stageX, stageY, stageW, stageH) && !dragging) {
                float reach = Mth.clamp((frame - 0.6F) / 1.6F, 0.25F, 1);
                float headY = feetY - 1.75F * scale;
                turnTarget = Mth.clamp((float) Math.atan((cx - mouseX) / 80F) * 24, -12, 12) * reach;
                tiltTarget = Mth.clamp((float) Math.atan((mouseY - headY) / 80F) * 10, -4, 7) * reach;
            }
            headTurn += (turnTarget - headTurn) * ease(0.15F);
            headTilt += (tiltTarget - headTilt) * ease(0.15F);
            float bodyYaw = 180 + yaw;
            preview.face(bodyYaw, bodyYaw + headTurn, headTilt);
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(pitch));
            pose.mul(camera);
            InventoryScreen.renderEntityInInventory(g, cx, feetY, scale, pose, camera, preview);
        }
        g.disableScissor();
        // Turn buttons.
        int by = stageY + stageH - 24;
        stageButton(g, stageX + 8, by, true, mouseX, mouseY);
        stageButton(g, stageX + stageW - 26, by, false, mouseX, mouseY);
        Component hint = Component.translatable("caracrea.screen.drag_hint");
        if (font.width(hint) < stageW - 64) {
            Ui.textCentered(g, font, hint, stageX + 28, by, stageW - 56, 18, 0x66FFFFFF);
        }
    }

    private void stageButton(GuiGraphics g, int x, int y, boolean left, int mouseX, int mouseY) {
        boolean hovered = Ui.inside(mouseX, mouseY, x, y, 18, 18);
        float h = hover(left ? "turnL" : "turnR", hovered);
        Ui.disc(g, x + 9, y + 9, 9, Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, h));
        Ui.arrow(g, left ? x + 5 : x + 7, y + 5, 8, left, Ui.mix(Ui.TEXT_DIM, Ui.ACCENT, h));
    }

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        Ui.shadow(g, panelX, panelY, panelW, panelH);
        Ui.round(g, panelX, panelY, panelW, panelH, 4, Ui.PANEL);
        Ui.bigText(g, font, category.title(), panelX + 10, panelY + 9, 1.4F, Ui.TEXT);
        Component hint = firstTime && category == Category.BODY ? Component.translatable("caracrea.screen.first_time") : category.hint();
        g.drawString(font, Ui.clip(font, hint, panelW - 20), panelX + 10, panelY + 24, firstTime && category == Category.BODY ? Ui.ACCENT : Ui.MUTED, false);
        g.fill(panelX + 10, contentY - 5, panelX + panelW - 10, contentY - 4, Ui.LINE);

        boolean inContent = Ui.inside(mouseX, mouseY, contentX - 4, contentY, contentW + 8, contentH);
        int mx = inContent ? mouseX : -10000;
        int my = inContent ? mouseY : -10000;
        g.enableScissor(contentX - 4, contentY, contentX + contentW + 4, contentY + contentH);
        int y = Math.round(contentY - scroll + (1 - contentFade) * 8);
        for (Row row : rows) {
            int h = row.height(contentW);
            if (y + h >= contentY && y <= contentY + contentH) {
                row.render(g, contentX, y, contentW, mx, my, contentFade);
            }
            y += h + ROW_GAP;
        }
        g.disableScissor();
        // Fade at the edges of the scroll area, and a thin scrollbar.
        if (scroll > 1) {
            g.fillGradient(contentX - 4, contentY, contentX + contentW + 4, contentY + 8, 0xC0141820, 0x00141820);
        }
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            if (scroll < maxScroll - 1) {
                g.fillGradient(contentX - 4, contentY + contentH - 8, contentX + contentW + 4, contentY + contentH, 0x00141820, 0xC0141820);
            }
            int trackX = panelX + panelW - 8;
            int thumbH = Math.max(14, contentH * contentH / contentHeight);
            int thumbY = contentY + Math.round((contentH - thumbH) * scroll / maxScroll);
            Ui.round(g, trackX, thumbY, 3, thumbH, 1, 0x40FFFFFF);
        }
    }

    private void renderBar(GuiGraphics g, int mouseX, int mouseY) {
        int h = BAR;
        // Random: this category, or everything.
        Component randomLabel = Component.translatable("caracrea.screen.random_category", category.title());
        // Fixed width, so the buttons do not move when the category changes.
        int rw = stageW - 44;
        button(g, "rand", stageX, barY, rw, h, Ui.clip(font, randomLabel, rw - 12), mouseX, mouseY, false);
        button(g, "randAll", stageX + rw + 4, barY, 40, h, Component.translatable("caracrea.screen.random_all"), mouseX, mouseY, false);
        Component confirm = Component.translatable("caracrea.screen.confirm");
        int cw = Math.max(96, font.width(confirm) + 32);
        int cx = width - MARGIN - cw;
        button(g, "confirm", cx, barY, cw, h, confirm, mouseX, mouseY, true);
        int left = cx - 6;
        if (!firstTime) {
            Component cancel = Component.translatable("caracrea.screen.cancel");
            int kw = Math.max(70, font.width(cancel) + 20);
            button(g, "cancel", left - kw, barY, kw, h, cancel, mouseX, mouseY, false);
            left -= kw + 6;
            if (!current.equals(original)) {
                Component reset = Component.translatable("caracrea.screen.reset");
                int tw = font.width(reset);
                int tx = left - tw - 4;
                if (tx > panelX) {
                    boolean hovered = Ui.inside(mouseX, mouseY, tx - 4, barY, tw + 8, h);
                    Ui.text(g, font, reset, tx, barY, h, hovered ? Ui.TEXT : Ui.MUTED);
                    if (hovered) {
                        g.fill(tx, barY + h - 7, tx + tw, barY + h - 6, Ui.MUTED);
                    }
                }
            }
        }
        sideHint(g);
    }

    private void sideHint(GuiGraphics g) {
        Component keys = Component.translatable("caracrea.screen.keys");
        if (font.width(keys) <= sideW + GAP - 8) {
            Ui.text(g, font, keys, sideX + 4, barY, BAR, 0x55FFFFFF);
        }
    }

    private void button(GuiGraphics g, String key, int x, int y, int w, int h, Component label, int mouseX, int mouseY, boolean primary) {
        boolean hovered = Ui.inside(mouseX, mouseY, x, y, w, h);
        float a = hover(key, hovered);
        if (primary) {
            Ui.round(g, x, y, w, h, 4, Ui.mix(Ui.ACCENT, Ui.ACCENT_HOVER, a));
            Ui.textCentered(g, font, label, x, y, w, h, Ui.ON_ACCENT);
        } else {
            Ui.round(g, x, y, w, h, 4, Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, a));
            Ui.textCentered(g, font, Ui.clip(font, label, w - 8), x, y, w, h, Ui.mix(Ui.TEXT_DIM, Ui.TEXT, a));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        // Categories.
        int itemH = 20;
        int y0 = sideY + 26;
        for (Category c : Category.values()) {
            if (Ui.inside(mx, my, sideX + 5, y0 + c.ordinal() * itemH, sideW - 10, itemH - 2)) {
                if (c != category) {
                    setCategory(c, false);
                }
                return true;
            }
        }
        // Bottom bar.
        if (my >= barY && my < barY + BAR) {
            int rw = stageW - 44;
            if (Ui.inside(mx, my, stageX, barY, rw, BAR)) {
                randomizeCategory();
                return true;
            }
            if (Ui.inside(mx, my, stageX + rw + 4, barY, 40, BAR)) {
                randomizeAll();
                return true;
            }
            Component confirm = Component.translatable("caracrea.screen.confirm");
            int cw = Math.max(96, font.width(confirm) + 32);
            int cx = width - MARGIN - cw;
            if (Ui.inside(mx, my, cx, barY, cw, BAR)) {
                confirm();
                return true;
            }
            if (!firstTime) {
                Component cancel = Component.translatable("caracrea.screen.cancel");
                int kw = Math.max(70, font.width(cancel) + 20);
                int left = cx - 6;
                if (Ui.inside(mx, my, left - kw, barY, kw, BAR)) {
                    close();
                    return true;
                }
                left -= kw + 6;
                Component reset = Component.translatable("caracrea.screen.reset");
                int tw = font.width(reset);
                if (!current.equals(original) && Ui.inside(mx, my, left - tw - 8, barY, tw + 8, BAR)) {
                    set(original);
                    return true;
                }
            }
        }
        // Stage: turn buttons, then drag to turn.
        if (Ui.inside(mx, my, stageX, stageY, stageW, stageH)) {
            int by = stageY + stageH - 24;
            if (Ui.inside(mx, my, stageX + 8, by, 18, 18)) {
                yawTarget -= 45;
                return true;
            }
            if (Ui.inside(mx, my, stageX + stageW - 26, by, 18, 18)) {
                yawTarget += 45;
                return true;
            }
            dragging = true;
            lastDragX = mx;
            return true;
        }
        // Content.
        if (Ui.inside(mx, my, contentX - 4, contentY, contentW + 8, contentH)) {
            int y = Math.round(contentY - scroll);
            for (Row row : rows) {
                int h = row.height(contentW);
                if (my >= y && my < y + h) {
                    if (row.click(mx, my, contentX, y, contentW)) {
                        activeRow = row;
                        return true;
                    }
                    return false;
                }
                y += h + ROW_GAP;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            float delta = (float) (mx - lastDragX) * 1.6F;
            lastDragX = mx;
            yawTarget -= delta;
            yaw -= delta;
            pitch = Mth.clamp(pitch + (float) dy * 0.5F, -20, 20);
            return true;
        }
        if (activeRow != null) {
            int y = Math.round(contentY - scroll);
            for (Row row : rows) {
                if (row == activeRow) {
                    row.drag(mx, my, contentX, y, contentW);
                    return true;
                }
                y += row.height(contentW) + ROW_GAP;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        activeRow = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (Ui.inside(mx, my, stageX, stageY, stageW, stageH)) {
            frameTarget = Mth.clamp(frameTarget * (1 - (float) delta * 0.12F), 0.55F, 2.6F);
            // Zoomed in, look at the face; zoomed out, at the whole body.
            focusTarget = Mth.lerp(Mth.clamp((2.3F - frameTarget) / 1.45F, 0, 1), 0.95F, 1.76F);
            return true;
        }
        if (Ui.inside(mx, my, panelX, panelY, panelW, panelH)) {
            scrollTarget = Mth.clamp(scrollTarget - (float) delta * 24, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (key == GLFW.GLFW_KEY_R && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            randomizeCategory();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            yawTarget -= 45;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            yawTarget += 45;
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            Category[] all = Category.values();
            int next = Math.floorMod(category.ordinal() + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), all.length);
            setCategory(all[next], false);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ------------------------------------------------------------------- rows

    private Component valueName(Trait trait, int value) {
        String id = Catalog.id(trait, value);
        String key = switch (trait) {
            case SHIRT_COLOR, SHIRT_COLOR2, PANTS_COLOR, SHOES_COLOR -> "caracrea.color." + id;
            default -> "caracrea." + trait.key() + "." + id;
        };
        return Component.translatable(key);
    }

    private static Component traitName(Trait trait) {
        return Component.translatable("caracrea.trait." + trait.key());
    }

    /** One block of the panel: a small label and its control. */
    private abstract class Row {
        static final int LABEL = 11;

        abstract int height(int w);

        abstract void render(GuiGraphics g, int x, int y, int w, int mx, int my, float alpha);

        /** Returns true when the click was used (the row then receives drags). */
        abstract boolean click(double mx, double my, int x, int y, int w);

        void drag(double mx, double my, int x, int y, int w) {
        }

        void label(GuiGraphics g, Component text, int x, int y, float alpha) {
            Ui.label(g, font, text, x, y, Ui.alpha(Ui.MUTED, alpha));
        }
    }

    /** Two large segments (body type). */
    private final class SegmentRow extends Row {
        private final Trait trait;

        SegmentRow(Trait trait) {
            this.trait = trait;
        }

        @Override
        int height(int w) {
            return LABEL + 22;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mx, int my, float alpha) {
            label(g, traitName(trait), x, y, alpha);
            int sy = y + LABEL;
            Ui.round(g, x, sy, w, 22, 4, Ui.alpha(Ui.SUNKEN, alpha));
            int half = (w - 4) / 2;
            for (int i = 0; i < 2; i++) {
                int sx = x + 2 + i * half;
                boolean on = current.get(trait) == i;
                boolean hovered = Ui.inside(mx, my, sx, sy + 2, half, 18);
                float h = hover("seg" + i, hovered && !on);
                if (on) {
                    Ui.round(g, sx, sy + 2, half, 18, 3, Ui.alpha(Ui.ACCENT, alpha));
                } else if (h > 0.01F) {
                    Ui.round(g, sx, sy + 2, half, 18, 3, Ui.alpha(Ui.SURFACE_HOVER, h * alpha));
                }
                Ui.textCentered(g, font, valueName(trait, i), sx, sy + 2, half, 18, Ui.alpha(on ? Ui.ON_ACCENT : Ui.TEXT_DIM, alpha));
            }
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            int half = (w - 4) / 2;
            for (int i = 0; i < 2; i++) {
                if (Ui.inside(mx, my, x + 2 + i * half, y + LABEL + 2, half, 18)) {
                    if (current.get(trait) != i) {
                        switchBodyType(BodyType.of(i));
                    }
                    return true;
                }
            }
            return false;
        }
    }

    /** Presets as a grid of chips. */
    private final class ChipRow extends Row {
        private static final int CHIP_H = 17;
        private final Trait trait;

        ChipRow(Trait trait) {
            this.trait = trait;
        }

        int columns(int w) {
            return w >= 250 ? 3 : w >= 120 ? 2 : 1;
        }

        @Override
        int height(int w) {
            int rows = (Catalog.count(trait) + columns(w) - 1) / columns(w);
            return LABEL + rows * (CHIP_H + 3) - 3;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mx, int my, float alpha) {
            label(g, traitName(trait), x, y, alpha);
            Component value = valueName(trait, current.get(trait));
            int vw = font.width(value);
            g.pose().pushPose();
            g.pose().translate(x + w - vw * 0.75F, y, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            g.drawString(font, value, 0, 0, Ui.alpha(Ui.ACCENT, alpha), false);
            g.pose().popPose();
            int cols = columns(w);
            int cw = (w - (cols - 1) * 3) / cols;
            int count = Catalog.count(trait);
            for (int i = 0; i < count; i++) {
                int cx = x + (i % cols) * (cw + 3);
                int cy = y + LABEL + (i / cols) * (CHIP_H + 3);
                boolean on = current.get(trait) == i;
                boolean hovered = Ui.inside(mx, my, cx, cy, cw, CHIP_H);
                float h = hover(trait.key() + i, hovered && !on);
                int fill = on ? Ui.ACCENT : Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, h);
                Ui.round(g, cx, cy, cw, CHIP_H, 3, Ui.alpha(fill, alpha));
                boolean suits = Catalog.suits(trait, i, current.bodyType());
                int color = on ? Ui.ON_ACCENT : suits ? Ui.mix(Ui.TEXT_DIM, Ui.TEXT, h) : Ui.MUTED;
                Component name = Ui.clip(font, valueName(trait, i), cw - 8);
                Ui.textCentered(g, font, name, cx, cy, cw, CHIP_H, Ui.alpha(color, alpha));
                if (hovered && font.width(valueName(trait, i)) > cw - 8) {
                    tooltip = valueName(trait, i);
                }
            }
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            int cols = columns(w);
            int cw = (w - (cols - 1) * 3) / cols;
            for (int i = 0; i < Catalog.count(trait); i++) {
                if (Ui.inside(mx, my, x + (i % cols) * (cw + 3), y + LABEL + (i / cols) * (CHIP_H + 3), cw, CHIP_H)) {
                    change(trait, i);
                    return true;
                }
            }
            return false;
        }
    }

    /** Colour swatches. */
    private final class SwatchRow extends Row {
        private static final int SIZE = 15;
        private static final int SPACE = 4;
        private final Trait trait;

        SwatchRow(Trait trait) {
            this.trait = trait;
        }

        int perLine(int w) {
            return Math.max(1, (w + SPACE) / (SIZE + SPACE));
        }

        @Override
        int height(int w) {
            int lines = (Catalog.count(trait) + perLine(w) - 1) / perLine(w);
            return LABEL + lines * (SIZE + SPACE) - SPACE + 2;
        }

        private int color(int index) {
            if (trait == Trait.LIP_COLOR && index == 0) {
                int skin = Catalog.swatch(current, Trait.SKIN_TONE).argb();
                return com.caracrea.paint.Colors.tint(com.caracrea.paint.Colors.shade(skin, 0.86F), com.caracrea.paint.Colors.rgb(190, 90, 100), 0.3F);
            }
            return Catalog.palette(trait).get(index).argb();
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mx, int my, float alpha) {
            label(g, traitName(trait), x, y, alpha);
            Component value = valueName(trait, current.get(trait));
            int vw = font.width(value);
            g.pose().pushPose();
            g.pose().translate(x + w - vw * 0.75F, y, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            g.drawString(font, value, 0, 0, Ui.alpha(Ui.ACCENT, alpha), false);
            g.pose().popPose();
            List<Swatch> palette = Catalog.palette(trait);
            int per = perLine(w);
            int selected = current.get(trait);
            for (int i = 0; i < palette.size(); i++) {
                int sx = x + (i % per) * (SIZE + SPACE);
                int sy = y + LABEL + 1 + (i / per) * (SIZE + SPACE);
                int cx = sx + SIZE / 2;
                int cy = sy + SIZE / 2;
                boolean hovered = Ui.inside(mx, my, sx, sy, SIZE, SIZE);
                float h = hover(trait.key() + "s" + i, hovered);
                int r = SIZE / 2 - (i == selected ? 1 : 0) + Math.round(h);
                Ui.disc(g, cx, cy, r, Ui.alpha(color(i), alpha));
                if (i == selected) {
                    Ui.ring(g, cx, cy, SIZE / 2 + 2, Ui.alpha(Ui.ACCENT, alpha));
                } else {
                    Ui.ring(g, cx, cy, r, Ui.alpha(0x30000000, alpha));
                }
                if (hovered) {
                    tooltip = valueName(trait, i);
                }
            }
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            int per = perLine(w);
            for (int i = 0; i < Catalog.count(trait); i++) {
                if (Ui.inside(mx, my, x + (i % per) * (SIZE + SPACE), y + LABEL + 1 + (i / per) * (SIZE + SPACE), SIZE, SIZE)) {
                    change(trait, i);
                    return true;
                }
            }
            return false;
        }
    }

    /** Five-step slider. */
    private final class SliderRow extends Row {
        private final Trait trait;

        SliderRow(Trait trait) {
            this.trait = trait;
        }

        @Override
        int height(int w) {
            return LABEL + 14;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mx, int my, float alpha) {
            label(g, traitName(trait), x, y, alpha);
            int steps = Trait.SLIDER_STEPS;
            int value = current.get(trait);
            int tx = x + 6;
            int tw = w - 12;
            int cy = y + LABEL + 7;
            Ui.round(g, tx, cy - 2, tw, 4, 2, Ui.alpha(Ui.SUNKEN, alpha));
            int knob = tx + tw * value / (steps - 1);
            Ui.round(g, tx, cy - 2, knob - tx + 2, 4, 2, Ui.alpha(Ui.ACCENT_SOFT, alpha));
            for (int i = 0; i < steps; i++) {
                int px = tx + tw * i / (steps - 1);
                g.fill(px, cy - 1, px + 1, cy + 1, Ui.alpha(i <= value ? Ui.ACCENT : Ui.BORDER, alpha));
            }
            boolean hovered = Ui.inside(mx, my, tx - 6, y + LABEL, tw + 12, 14) || activeRow == this;
            float h = hover(trait.key() + "k", hovered);
            Ui.disc(g, knob, cy, 5 + Math.round(h), Ui.alpha(Ui.ACCENT, alpha));
            Ui.disc(g, knob, cy, 2, Ui.alpha(Ui.ON_ACCENT, alpha));
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (!Ui.inside(mx, my, x, y + LABEL - 2, w, 18)) {
                return false;
            }
            drag(mx, my, x, y, w);
            return true;
        }

        @Override
        void drag(double mx, double my, int x, int y, int w) {
            float t = Mth.clamp((float) (mx - (x + 6)) / (w - 12), 0, 1);
            change(trait, Math.round(t * (Trait.SLIDER_STEPS - 1)));
        }
    }

    /** On/off chips (marks). The old fixed face scars are replaced by placed scars and not offered here. */
    private final class ToggleRow extends Row {
        private static final int CHIP_H = 16;
        private final Trait trait;
        private final List<Integer> bits = new ArrayList<>();

        ToggleRow(Trait trait) {
            this.trait = trait;
            List<Option> options = Catalog.options(trait);
            for (int i = 0; i < options.size(); i++) {
                if (!options.get(i).id().startsWith("scar")) {
                    bits.add(i);
                }
            }
        }

        private List<int[]> layout(int x, int y, int w) {
            List<int[]> chips = new ArrayList<>();
            int cx = x;
            int cy = y + LABEL;
            for (int bit : bits) {
                int cw = font.width(valueName(trait, bit)) + 16;
                if (cx + cw > x + w && cx > x) {
                    cx = x;
                    cy += CHIP_H + 3;
                }
                chips.add(new int[]{cx, cy, cw, bit});
                cx += cw + 3;
            }
            return chips;
        }

        private Component valueName(Trait trait, int bit) {
            return Component.translatable("caracrea.marks." + Catalog.options(trait).get(bit).id());
        }

        @Override
        int height(int w) {
            List<int[]> chips = layout(0, 0, w);
            return chips.isEmpty() ? LABEL : chips.get(chips.size() - 1)[1] + CHIP_H;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mx, int my, float alpha) {
            label(g, traitName(trait), x, y, alpha);
            for (int[] c : layout(x, y, w)) {
                boolean on = current.hasMark(1 << c[3]);
                boolean hovered = Ui.inside(mx, my, c[0], c[1], c[2], CHIP_H);
                float h = hover("mark" + c[3], hovered && !on);
                Ui.round(g, c[0], c[1], c[2], CHIP_H, 3, Ui.alpha(on ? Ui.ACCENT : Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, h), alpha));
                Ui.textCentered(g, font, valueName(trait, c[3]), c[0], c[1], c[2], CHIP_H,
                        Ui.alpha(on ? Ui.ON_ACCENT : Ui.mix(Ui.TEXT_DIM, Ui.TEXT, h), alpha));
            }
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            for (int[] c : layout(x, y, w)) {
                if (Ui.inside(mx, my, c[0], c[1], c[2], CHIP_H)) {
                    set(current.withMark(1 << c[3], !current.hasMark(1 << c[3])));
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Scars: three slots, a front and back silhouette to click where the
     * selected scar goes, and buttons to turn, resize or remove it.
     */
    private final class ScarRow extends Row {
        private static final int TAB_H = 16;
        private static final int ACTION_H = 16;

        /** Pixel size of one model unit on the silhouettes, for a row width. */
        private int unit(int w) {
            return Mth.clamp((w - 24) / 34, 2, 5);
        }

        private int mapH(int w) {
            return unit(w) * 32;
        }

        @Override
        int height(int w) {
            return LABEL + TAB_H + 6 + 10 + mapH(w) + 6 + ACTION_H + 4 + font.lineHeight * 2 + 2;
        }

        /** {x, y, w, h} of a body part on a silhouette; view 0 = front, 1 = back. */
        private int[] partRect(Scar.Part part, int view, int ox, int oy, int u) {
            int arm = current.bodyType().slim() ? 3 : 4;
            boolean back = view == 1;
            return switch (part) {
                case HEAD -> new int[]{ox + 4 * u, oy, 8 * u, 8 * u};
                case BODY -> new int[]{ox + 4 * u, oy + 8 * u, 8 * u, 12 * u};
                case RIGHT_ARM -> new int[]{back ? ox + 12 * u : ox + (4 - arm) * u, oy + 8 * u, arm * u, 12 * u};
                case LEFT_ARM -> new int[]{back ? ox + (4 - arm) * u : ox + 12 * u, oy + 8 * u, arm * u, 12 * u};
                case RIGHT_LEG -> new int[]{back ? ox + 8 * u : ox + 4 * u, oy + 20 * u, 4 * u, 12 * u};
                case LEFT_LEG -> new int[]{back ? ox + 4 * u : ox + 8 * u, oy + 20 * u, 4 * u, 12 * u};
            };
        }

        /** Left edge of each silhouette. */
        private int[] mapOrigins(int x, int w) {
            int u = unit(w);
            int total = 16 * u * 2 + 20;
            int start = x + (w - total) / 2;
            return new int[]{start, start + 16 * u + 20};
        }

        private int mapTop(int y) {
            return y + LABEL + TAB_H + 6 + 10;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mx, int my, float alpha) {
            label(g, Component.translatable("caracrea.scar.title"), x, y, alpha);
            // Slots.
            for (int i = 0; i < Scar.COUNT; i++) {
                int tx = x + i * 52;
                boolean on = i == selectedScar;
                boolean used = current.get(Scar.traits(i)[0]) != 0;
                boolean hovered = Ui.inside(mx, my, tx, y + LABEL, 48, TAB_H);
                float h = hover("scarTab" + i, hovered && !on);
                Ui.round(g, tx, y + LABEL, 48, TAB_H, 3, Ui.alpha(on ? Ui.ACCENT : Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, h), alpha));
                Component name = Component.translatable("caracrea.scar.slot", i + 1);
                Ui.text(g, font, name, tx + 6, y + LABEL, TAB_H, Ui.alpha(on ? Ui.ON_ACCENT : Ui.TEXT_DIM, alpha));
                Ui.disc(g, tx + 41, y + LABEL + TAB_H / 2, 3, Ui.alpha(used ? (on ? Ui.ON_ACCENT : Ui.SCAR) : Ui.BORDER, alpha));
            }
            // Silhouettes.
            int u = unit(w);
            int top = mapTop(y);
            int[] origins = mapOrigins(x, w);
            int skin = Catalog.swatch(current, Trait.SKIN_TONE).argb();
            for (int view = 0; view < 2; view++) {
                Component caption = Component.translatable(view == 0 ? "caracrea.scar.front" : "caracrea.scar.back");
                Ui.label(g, font, caption, origins[view] + 8 * u - Math.round(font.width(caption.getString().toUpperCase(java.util.Locale.ROOT)) * 0.375F),
                        top - 9, Ui.alpha(Ui.MUTED, alpha));
                for (Scar.Part part : Scar.Part.values()) {
                    int[] r = partRect(part, view, origins[view], top, u);
                    boolean hovered = Ui.inside(mx, my, r[0], r[1], r[2], r[3]);
                    int fill = hovered ? com.caracrea.paint.Colors.lighten(skin, 0.12F) : skin;
                    g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], Ui.alpha(fill, alpha));
                    Ui.roundOutline(g, r[0], r[1], r[2], r[3], Ui.alpha(0x50000000, alpha));
                }
                // Eyes on the front head, so front and back are easy to tell apart.
                if (view == 0) {
                    int[] head = partRect(Scar.Part.HEAD, 0, origins[0], top, u);
                    g.fill(head[0] + 2 * u, head[1] + 4 * u, head[0] + 3 * u, head[1] + 5 * u, Ui.alpha(0x90000000, alpha));
                    g.fill(head[0] + 5 * u, head[1] + 4 * u, head[0] + 6 * u, head[1] + 5 * u, Ui.alpha(0x90000000, alpha));
                }
            }
            // Scars on the silhouettes.
            for (int i = 0; i < Scar.COUNT; i++) {
                Trait[] t = Scar.traits(i);
                int zone = current.get(t[0]);
                if (zone == 0) {
                    continue;
                }
                Scar.Zone z = Scar.Zone.VALUES[zone - 1];
                int view = z.back ? 1 : 0;
                int[] r = partRect(z.part, view, origins[view], top, u);
                float cx = r[0] + (current.get(t[1]) + 0.5F) / Scar.POSITIONS * r[2];
                float cy = r[1] + (current.get(t[2]) + 0.5F) / Scar.POSITIONS * r[3];
                int shape = current.get(t[3]);
                double angle = Scar.angleStep(shape) * Math.PI / Scar.ANGLES;
                float half = new float[]{1.3F, 2.2F, 3.2F}[Scar.lengthStep(shape)] / 2 * u;
                float dx = (float) Math.cos(angle) * half;
                float dy = (float) Math.sin(angle) * half;
                Ui.line(g, cx - dx, cy - dy, cx + dx, cy + dy, i == selectedScar ? 2 : 1,
                        Ui.alpha(i == selectedScar ? Ui.ACCENT : Ui.SCAR, alpha));
            }
            // Actions for the selected scar.
            Trait[] t = Scar.traits(selectedScar);
            boolean placed = current.get(t[0]) != 0;
            int ay = top + mapH(w) + 6;
            int ax = x;
            int lengthStep = Scar.lengthStep(current.get(t[3]));
            Component[] labels = {
                    Component.translatable("caracrea.scar.rotate"),
                    Component.translatable("caracrea.scar.size", Component.translatable("caracrea.scar.size." + lengthStep)),
                    Component.translatable("caracrea.scar.remove")};
            for (int i = 0; i < labels.length; i++) {
                int bw = font.width(labels[i]) + 14;
                boolean hovered = placed && Ui.inside(mx, my, ax, ay, bw, ACTION_H);
                float h = hover("scarAct" + i, hovered);
                Ui.round(g, ax, ay, bw, ACTION_H, 3, Ui.alpha(Ui.mix(Ui.SURFACE, Ui.SURFACE_HOVER, h), placed ? alpha : alpha * 0.5F));
                Ui.textCentered(g, font, labels[i], ax, ay, bw, ACTION_H, Ui.alpha(placed ? Ui.mix(Ui.TEXT_DIM, Ui.TEXT, h) : Ui.MUTED, alpha));
                ax += bw + 3;
            }
            g.drawWordWrap(font, Component.translatable("caracrea.scar.hint"), x, ay + ACTION_H + 5, w, Ui.alpha(Ui.MUTED, alpha));
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            for (int i = 0; i < Scar.COUNT; i++) {
                if (Ui.inside(mx, my, x + i * 52, y + LABEL, 48, TAB_H)) {
                    selectedScar = i;
                    return true;
                }
            }
            Trait[] t = Scar.traits(selectedScar);
            int u = unit(w);
            int top = mapTop(y);
            int[] origins = mapOrigins(x, w);
            for (int view = 0; view < 2; view++) {
                for (Scar.Part part : Scar.Part.values()) {
                    int[] r = partRect(part, view, origins[view], top, u);
                    if (Ui.inside(mx, my, r[0], r[1], r[2], r[3])) {
                        int px = Mth.clamp((int) ((mx - r[0]) / r[2] * Scar.POSITIONS), 0, Scar.POSITIONS - 1);
                        int py = Mth.clamp((int) ((my - r[1]) / r[3] * Scar.POSITIONS), 0, Scar.POSITIONS - 1);
                        boolean wasPlaced = current.get(t[0]) != 0;
                        Appearance next = current.with(t[0], Scar.Zone.of(part, view == 1).ordinal() + 1).with(t[1], px).with(t[2], py);
                        if (!wasPlaced) {
                            next = next.with(t[3], Scar.shape(1, 1));
                        }
                        set(next);
                        // Show the side where the scar went.
                        float facing = view == 1 ? 180 : 0;
                        yawTarget = facing + Math.round((yawTarget - facing) / 360F) * 360F;
                        return true;
                    }
                }
            }
            if (current.get(t[0]) == 0) {
                return false;
            }
            int ay = top + mapH(w) + 6;
            int ax = x;
            int shape = current.get(t[3]);
            String[] keys = {"caracrea.scar.rotate", "caracrea.scar.size", "caracrea.scar.remove"};
            for (int i = 0; i < keys.length; i++) {
                Component label = i == 1
                        ? Component.translatable(keys[i], Component.translatable("caracrea.scar.size." + Scar.lengthStep(shape)))
                        : Component.translatable(keys[i]);
                int bw = font.width(label) + 14;
                if (Ui.inside(mx, my, ax, ay, bw, ACTION_H)) {
                    switch (i) {
                        case 0 -> change(t[3], Scar.shape(Scar.angleStep(shape) + 1, Scar.lengthStep(shape)));
                        case 1 -> change(t[3], Scar.shape(Scar.angleStep(shape), (Scar.lengthStep(shape) + 1) % Scar.LENGTHS));
                        default -> set(current.with(t[0], 0).with(t[1], 0).with(t[2], 0).with(t[3], 0));
                    }
                    return true;
                }
                ax += bw + 3;
            }
            return false;
        }
    }
}
