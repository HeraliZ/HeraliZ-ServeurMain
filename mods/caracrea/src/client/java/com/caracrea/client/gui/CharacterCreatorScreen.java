package com.caracrea.client.gui;

import com.caracrea.character.Appearance;
import com.caracrea.character.AppearanceRandomizer;
import com.caracrea.character.Catalog;
import com.caracrea.character.Option;
import com.caracrea.character.Scar;
import com.caracrea.character.Swatch;
import com.caracrea.character.Trait;
import com.caracrea.client.CompositeSkins;
import com.caracrea.client.gui.kit.Echelle;
import com.caracrea.client.gui.kit.Ui;
import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.network.CaraCreaNetwork.SaveAppearance;
import com.caracrea.paint.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static com.caracrea.client.gui.CreatorUi.*;

/**
 * The character creator (validated mock-up): numbered categories on the left with what is chosen in each, the character in
 * the middle (drag to turn, wheel to zoom), the choices of the category in a glass panel on the right, and the actions at the
 * bottom. Every change shows on the character within a frame or two; nothing is sent to the server until "Confirm".
 * Laid out on the 640 x 360 canvas of our full screens ({@link Echelle}).
 */
public class CharacterCreatorScreen extends Screen {
    private static final int CAT_W = 133;
    private static final int CAT_H = 24;
    private static final int PANEL_W = 178;
    private static final float OPT_H = 18;
    private static final float OPT_GAP = 3;
    /** From the top of a section to its content (title capitals 10 px below the top). */
    private static final float SECTION = 22;
    private static final float SLIDER_H = 13.33F;
    private static final float BAR_H = 18;

    /** Groups of traits shown together, with where the camera looks. */
    enum Category {
        BODY("body", 0.95F, 2.0F, Trait.SKIN_TONE),
        FACE("face", 1.72F, 0.82F, Trait.NOSE_STYLE, Trait.MOUTH_STYLE, Trait.MOUTH_WIDTH, Trait.BROW_STYLE, Trait.FACIAL_HAIR, Trait.LIP_COLOR),
        EYES("eyes", 1.74F, 0.66F, Trait.EYE_SHAPE, Trait.EYE_COLOR, Trait.EYE_SIZE, Trait.EYE_SPACING, Trait.EYE_HEIGHT, Trait.IRIS_SIZE),
        HAIR("hair", 1.66F, 1.1F, Trait.HAIR_STYLE, Trait.HAIR_COLOR),
        TOP("top", 0.95F, 2.0F, Trait.SHIRT_STYLE, Trait.SHIRT_COLOR, Trait.SHIRT_COLOR2),
        BOTTOM("bottom", 0.55F, 1.3F, Trait.PANTS_STYLE, Trait.PANTS_COLOR),
        SHOES("shoes", 0.2F, 0.8F, Trait.SHOES_STYLE, Trait.SHOES_COLOR),
        DETAILS("details", 0.95F, 2.0F, Trait.MARKS, Trait.WEAR);

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

    // Layout, in pixels of the canvas; px = one screen pixel (the thin borders of the mock-up).
    private float px = 1 / 3F;
    private int margin;
    private int catX, catY;
    private int stageX, stageY, stageW, stageH;
    private float stageCX;
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int barY;
    private float randomW;

    // Camera of the preview, eased towards its targets.
    private float yaw = -18;
    private float yawTarget = -18;
    private float pitch;
    private float frame = 2.0F;
    private float frameTarget = 2.0F;
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
    private float contentHeight;
    private float contentFade = 1;
    private float tabY = -1;
    private final Map<String, Float> hovers = new HashMap<>();
    private Row activeRow;
    private String tooltip;
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
        Echelle.forcer(this);
        px = 1.0F / (float) minecraft.getWindow().getGuiScale();
        if (preview == null && minecraft.level != null) {
            preview = new PreviewPlayer(minecraft.level, current);
        }
        margin = Mth.clamp(Math.round(width * 0.05F), 16, 32);
        barY = height - 44;
        catX = margin;
        catY = 79;
        panelW = PANEL_W;
        panelX = width - margin - panelW;
        panelY = 22;
        panelH = barY - 10 - panelY;
        stageX = catX + CAT_W + 4;
        stageW = panelX - 4 - stageX;
        stageY = 64;
        stageH = barY - stageY;
        stageCX = stageX + stageW / 2F;
        contentX = panelX + 10;
        contentW = panelW - 21;
        contentY = panelY + 40;
        contentH = panelY + panelH - 1 - contentY;
        // The Random button keeps one width whatever the category.
        randomW = 110;
        for (Category c : Category.values()) {
            randomW = Math.max(randomW, 11.33F + capsWidth(font, randomLabel(c), BOUTON_BOLD, 0.85F) + 12);
        }
        randomW = Math.min(randomW, 128);
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
        Trait previous = null;
        for (Trait trait : category.traits) {
            if (trait.kind() == Trait.Kind.SLIDER) {
                // The mouth's width sits under the mouth; other sliders open a section of their own.
                boolean titled = trait != Trait.MOUTH_WIDTH && (previous == null || previous.kind() != Trait.Kind.SLIDER);
                rows.add(new SliderRow(trait, titled));
            } else if (trait.kind() == Trait.Kind.FLAGS) {
                rows.add(new ToggleRow(trait));
            } else if (Catalog.isColor(trait)) {
                rows.add(new SwatchRow(trait));
            } else {
                rows.add(new ChipRow(trait));
            }
            previous = trait;
        }
        relayoutContent();
    }

    private void relayoutContent() {
        contentHeight = 12;
        for (Row row : rows) {
            contentHeight += row.height();
        }
        scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll());
    }

    private float maxScroll() {
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
            next = next.with(trait, sample.get(trait));
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
        Echelle.restaurer();
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

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
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

        renderBackdrop(g);
        renderStage(g, mouseX, mouseY);
        renderHeader(g);
        renderCategories(g, mouseX, mouseY);
        renderPanel(g, mouseX, mouseY);
        renderBar(g, mouseX, mouseY);
        if (tooltip != null) {
            Ui.tooltip(g, font, List.of(Ui.lit(tooltip, Ui.SMALL)), mouseX, mouseY, ACCENT);
        }
    }

    /** The world stays visible behind a dark veil (darker at the sides, where the text is) and a soft vignette. */
    private void renderBackdrop(GuiGraphics g) {
        float[] at = {0, 0.24F, 0.46F, 0.62F, 1};
        int[] alpha = {235, 209, 153, 173, 230};
        for (int i = 0; i < at.length - 1; i++) {
            gradientH(g, width * at[i], 0, width * at[i + 1], height, a(NIGHT, alpha[i]), a(NIGHT, alpha[i + 1]));
        }
        gradientV(g, 0, 0, width, height * 0.3F, a(0, 90), a(0, 0));
        gradientV(g, 0, height * 0.6F, width, height, a(0, 0), a(0, 120));
    }

    private void renderStage(GuiGraphics g, int mouseX, int mouseY) {
        // Fit the framed part of the character to the stage, in height and in width.
        float fit = Math.min((stageH - 8) / frame, (stageW - 16) / Math.min(frame, 1.05F));
        int scale = Math.max(12, Math.round(fit));
        int cx = Math.round(stageCX);
        int feetY = Math.round(stageY + stageH / 2F + focus * scale);
        // A faint red light behind the character, and its shadow on the floor.
        glow(g, stageCX, stageY + stageH * 0.47F, Math.min(stageW * 0.36F, 95), stageH * 0.46F, a(ACCENT, 26));
        if (feetY < stageY + stageH + scale / 4) {
            glow(g, cx, feetY, scale * 0.62F, Math.max(2, scale * 0.075F), a(0, 150));
        }
        int clipBottom = stageY + stageH + 2;
        g.enableScissor(stageX, 0, panelX + 2, clipBottom);
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
            if (mouseMovedSinceOpen && inside(mouseX, mouseY, stageX, stageY, stageW, stageH) && !dragging) {
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
        // Close-ups: the body fades into a dark band at the bottom, and into the dark left side, instead of being cut.
        float below = Mth.clamp((feetY - clipBottom) / 24F, 0, 1);
        if (below > 0) {
            gradientV(g, 0, clipBottom - 42, width, clipBottom, a(NIGHT, 0), a(NIGHT, 225 * below));
            gradientV(g, 0, clipBottom, width, clipBottom + 18, a(NIGHT, 225 * below), a(NIGHT, 0));
        }
        float side = Mth.clamp((scale * 0.5F - (stageCX - stageX) + 6) / 12F, 0, 1);
        if (side > 0) {
            gradientH(g, stageX, 0, stageX + 26, clipBottom, a(NIGHT, 215 * side), a(NIGHT, 0));
        }
    }

    private void renderHeader(GuiGraphics g) {
        float capTop = 24.33F;
        if (firstTime) {
            String chip = tr("caracrea.screen.first_chip");
            float w = capsWidth(font, chip, MINI, 0.56F) + 6;
            rect(g, margin, 22, margin + w, 30, a(ACCENT, 34));
            border(g, margin, 22, w, 8, px, a(ACCENT, 90));
            caps(g, font, chip, MINI, margin + 3, 26 - 4.21F, 0.56F, a(mix(ACCENT, WHITE, 0.3F), 255));
            capTop = 42.67F;
        }
        caps(g, font, title.getString(), TITLE, margin, capTop - 4.44F, 1.135F, 1.93F, a(LIGHT, 255));
        Component line = Component.translatable(firstTime ? "caracrea.screen.first_time" : "caracrea.screen.subtitle");
        text(g, font, Ui.in(line, TEXT), margin, capTop + 17.33F - 2.95F, 1.1F, a(firstTime ? WELCOME : MUTED, 255));
    }

    private void renderCategories(GuiGraphics g, int mouseX, int mouseY) {
        Category[] all = Category.values();
        float target = catY + category.ordinal() * CAT_H;
        tabY = tabY < 0 ? target : tabY + (target - tabY) * ease(0.3F);
        // The selection slides from one category to the next.
        float sy = tabY;
        gradientH(g, catX, sy, catX + CAT_W * 0.75F, sy + CAT_H, a(ACCENT, 51), a(ACCENT, 10));
        gradientH(g, catX + CAT_W * 0.75F, sy, catX + CAT_W, sy + CAT_H, a(ACCENT, 10), a(ACCENT, 0));
        border(g, catX, sy, CAT_W, CAT_H, px, a(ACCENT, 92));
        rect(g, catX, sy + 4, catX + 1.33F, sy + CAT_H - 4, a(ACCENT, 255));
        for (int i = 0; i < all.length; i++) {
            float y = catY + i * CAT_H;
            boolean selected = all[i] == category;
            float h = hover("cat" + i, !selected && inside(mouseX, mouseY, catX, y, CAT_W, CAT_H));
            if (h > 0.01F) {
                rect(g, catX, y, catX + CAT_W, y + CAT_H, a(WHITE, 9 * h));
            }
            caps(g, font, String.format(Locale.ROOT, "%02d", i + 1), MINI_BOLD, catX + 6.67F, y + 7.79F, 0.5F,
                    a(selected ? PINK : mix(FAINT, MUTED, h), 255));
            caps(g, font, all[i].title().getString(), BOLD_9, catX + 21.33F, y + 2.28F, 1.05F, a(selected ? WHITE : mix(OPTION, WHITE, h), 255));
            String value = Ui.ellipsis(font, summary(all[i]), MINI, CAT_W - 27);
            text(g, font, lit(value, MINI), catX + 21.33F, y + 13.24F, a(selected ? BUTTON : mix(MUTED, OPTION, h), 255));
        }
    }

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        glass(g, panelX, panelY, panelW, panelH, px);
        // Red line at the top, fading at both ends.
        float mid = panelX + panelW / 2F;
        gradientH(g, panelX + 10, panelY, mid, panelY + 2 * px, a(ACCENT, 0), a(ACCENT, 255));
        gradientH(g, mid, panelY, panelX + panelW - 10, panelY + 2 * px, a(ACCENT, 255), a(ACCENT, 0));
        caps(g, font, category.title().getString(), TITLE, contentX, panelY + 7.8F, 1.6F, a(WHITE, 255));
        String hint = Ui.ellipsis(font, category.hint().getString(), AIDE, contentW + 4);
        text(g, font, lit(hint, AIDE), contentX, panelY + 23F, a(MUTED, 255));
        rect(g, contentX, panelY + 39.67F, contentX + contentW + 1, panelY + 39.67F + px, a(WHITE, 23));

        boolean inContent = inside(mouseX, mouseY, contentX - 4, contentY, contentW + 8, contentH);
        int mx = inContent ? mouseX : -10000;
        int my = inContent ? mouseY : -10000;
        g.enableScissor(contentX - 6, contentY, contentX + contentW + 6, contentY + contentH);
        float y = contentY - scroll + (1 - contentFade) * 8;
        for (Row row : rows) {
            float h = row.height();
            if (y + h >= contentY && y <= contentY + contentH) {
                row.render(g, contentX, y, mx, my, contentFade);
            }
            y += h;
        }
        g.disableScissor();
        // Fades where the content goes on, and a thin scrollbar.
        float max = maxScroll();
        if (scroll > 1) {
            gradientV(g, panelX + px, contentY, panelX + panelW - px, contentY + 8, a(GLASS, 200), a(GLASS, 0));
        }
        if (max > 0) {
            if (scroll < max - 1) {
                gradientV(g, panelX + px, panelY + panelH - 23, panelX + panelW - px, panelY + panelH - px, a(GLASS, 0), a(GLASS, 242));
            }
            float thumbH = Math.max(14, contentH * contentH / contentHeight);
            float thumbY = contentY + (contentH - thumbH) * scroll / max;
            rect(g, panelX + panelW - 5, thumbY, panelX + panelW - 4, thumbY + thumbH, a(WHITE, 56));
        }
    }

    // ------------------------------------------------------------- bottom bar

    private String randomLabel(Category c) {
        return tr("caracrea.screen.random_category", c.title().getString());
    }

    private float allX() {
        return margin + randomW + 4;
    }

    private float allW() {
        return capsWidth(font, tr("caracrea.screen.random_all"), BOUTON_BOLD, 0.85F) + 14.67F;
    }

    private float cancelW() {
        return firstTime ? 0 : (panelW - 4) / 2.4F;
    }

    private float confirmX() {
        return firstTime ? panelX : panelX + cancelW() + 4;
    }

    private float turnY() {
        return barY + 13;
    }

    /** Distance from the stage centre to the turn buttons (they make room for the hint between them). */
    private float turnOffset() {
        return Math.max(67.67F, capsWidth(font, tr("caracrea.screen.drag_hint"), MINI, 0.65F) / 2 + 15);
    }

    /** {x, y, w, h} of the "back to my current look" link, or null when there is nothing to undo. */
    private float[] resetRect() {
        if (firstTime || current.equals(original)) {
            return null;
        }
        float w = capsWidth(font, tr("caracrea.screen.reset"), MINI, 0.43F);
        return new float[]{panelX + (panelW - w) / 2, barY + 26 - 5, w, 10};
    }

    private void renderBar(GuiGraphics g, int mx, int my) {
        float y = barY;
        float cy = y + BAR_H / 2;
        // Random: this category, or everything.
        float h = hover("rand", inside(mx, my, margin, y, randomW, BAR_H));
        button(g, margin, y, randomW, BAR_H, false, h, px);
        String label = randomLabel(category);
        float content = 11.33F + capsWidth(font, label, BOUTON_BOLD, 0.85F);
        float x = margin + (randomW - content) / 2;
        int color = a(mix(BUTTON, WHITE, h), 255);
        die(g, x, cy - 3.67F, color);
        caps(g, font, label, BOUTON_BOLD, x + 11.33F, cy - 4.55F, 0.85F, color);
        float ax = allX(), aw = allW();
        h = hover("randAll", inside(mx, my, ax, y, aw, BAR_H));
        button(g, ax, y, aw, BAR_H, false, h, px);
        caps(g, font, tr("caracrea.screen.random_all"), BOUTON_BOLD, ax + 7.33F, cy - 4.55F, 0.85F, a(mix(BUTTON, WHITE, h), 255));

        // Turn buttons, with the hint between them, under the character.
        float ty = turnY();
        String drag = tr("caracrea.screen.drag_hint");
        float dw = capsWidth(font, drag, MINI, 0.65F);
        caps(g, font, drag, MINI, stageCX - dw / 2, ty - 4.21F, 0.65F, a(GREY, 255));
        turnButton(g, stageCX - turnOffset(), ty, true, mx, my);
        turnButton(g, stageCX + turnOffset(), ty, false, mx, my);

        // Cancel (not on the first time) and Confirm.
        if (!firstTime) {
            float cw = cancelW();
            h = hover("cancel", inside(mx, my, panelX, y, cw, BAR_H));
            button(g, panelX, y, cw, BAR_H, false, h, px);
            String cancel = tr("caracrea.screen.cancel");
            caps(g, font, cancel, TEXT_BOLD, panelX + (cw - capsWidth(font, cancel, TEXT_BOLD, 0.96F)) / 2, cy - 4.7F, 0.96F,
                    a(mix(BUTTON, WHITE, h), 255));
        }
        float vx = confirmX(), vw = panelX + panelW - vx;
        h = hover("confirm", inside(mx, my, vx, y, vw, BAR_H));
        button(g, vx, y, vw, BAR_H, true, h, px);
        String confirm = tr("caracrea.screen.confirm"), enter = tr("caracrea.screen.key.enter");
        float tw = capsWidth(font, confirm, TEXT_BOLD, 0.96F);
        float tx = vx + (vw - tw - 4 - keyWidth(font, enter, 0.6F, 8)) / 2;
        caps(g, font, confirm, TEXT_BOLD, tx, cy - 4.7F, 0.96F, a(WHITE, 255));
        key(g, font, enter, tx + tw + 4, cy, 6.33F, 0.6F, 8, a(WHITE, 41), px);
        float[] reset = resetRect();
        if (reset != null) {
            boolean hovered = inside(mx, my, reset[0] - 2, reset[1], reset[2] + 4, reset[3]);
            int col = a(hovered ? WHITE : GREY, 255);
            float ry = reset[1] + 5;
            caps(g, font, tr("caracrea.screen.reset"), MINI, reset[0], ry - 4.21F, 0.43F, col);
            rect(g, reset[0], ry + 3.08F, reset[0] + reset[2], ry + 3.08F + px, col);
        }

        // Keyboard shortcuts.
        // 23/10 (rendu validé) : touches de 9 px, textes en 7,5 px, flèches dessinées et centrées.
        float sy = barY + 28.5F;
        float sx = margin;
        int fill = a(WHITE, 31);
        sx += keyArrow(g, sx, sy, 9, 10.5F, 0, fill, px) + 3.33F;
        sx += keyArrow(g, sx, sy, 9, 10.5F, 1, fill, px) + 3.33F;
        sx += caps(g, font, tr("caracrea.screen.key.category"), TEXT, sx, sy - 4.9F, 0.43F, a(KEYS, 255)) + 9.33F;
        sx += keyArrow(g, sx, sy, 9, 10.5F, 2, fill, px) + 3.33F;
        sx += keyArrow(g, sx, sy, 9, 10.5F, 3, fill, px) + 3.33F;
        sx += caps(g, font, tr("caracrea.screen.key.turn"), TEXT, sx, sy - 4.9F, 0.43F, a(KEYS, 255)) + 9.33F;
        sx += keyLarge(g, font, tr("caracrea.screen.key.ctrl_r"), sx, sy, 9, 0.4F, 10.5F, fill, px) + 3.33F;
        caps(g, font, tr("caracrea.screen.key.random"), TEXT, sx, sy - 4.9F, 0.43F, a(KEYS, 255));
    }

    private void turnButton(GuiGraphics g, float cx, float cy, boolean left, int mx, int my) {
        float h = hover(left ? "turnL" : "turnR", inside(mx, my, cx - 9, cy - 9, 18, 18));
        disc(g, cx, cy, 9, a(GLASS, 153 + 40 * h));
        ring(g, cx, cy, 9 - px, 9, a(WHITE, 41 + 50 * h));
        chevron(g, cx, cy, 2.6F, left, 1.0F, a(mix(0xD9DDE3, WHITE, h), 255));
    }

    // -------------------------------------------------------------- summaries

    private String name(Trait trait) {
        return valueName(trait, current.get(trait)).getString();
    }

    private String id(Trait trait) {
        return Catalog.id(trait, current.get(trait));
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    private static String join(String first, String second) {
        return first + " · " + second;
    }

    /** What is chosen in a category, under its name on the left ("Hoodie · olive"). */
    private String summary(Category c) {
        return switch (c) {
            case BODY -> tr("caracrea.summary.body", lower(name(Trait.SKIN_TONE)));
            case FACE -> join("none".equals(id(Trait.FACIAL_HAIR)) ? tr("caracrea.summary.no_beard") : name(Trait.FACIAL_HAIR),
                    tr("caracrea.summary.brows", lower(name(Trait.BROW_STYLE))));
            case EYES -> join(name(Trait.EYE_COLOR), lower(name(Trait.EYE_SHAPE)));
            case HAIR -> "bald".equals(id(Trait.HAIR_STYLE)) ? name(Trait.HAIR_STYLE)
                    : join(name(Trait.HAIR_STYLE), lower(name(Trait.HAIR_COLOR)));
            case TOP -> join(name(Trait.SHIRT_STYLE), lower(name(Trait.SHIRT_COLOR)));
            case BOTTOM -> join(name(Trait.PANTS_STYLE), lower(name(Trait.PANTS_COLOR)));
            case SHOES -> "barefoot".equals(id(Trait.SHOES_STYLE)) ? name(Trait.SHOES_STYLE)
                    : join(name(Trait.SHOES_STYLE), lower(name(Trait.SHOES_COLOR)));
            case DETAILS -> {
                int scars = Scar.of(current).size();
                String s = scars == 0 ? tr("caracrea.summary.scars.none") : scars == 1 ? tr("caracrea.summary.scars.one")
                        : tr("caracrea.summary.scars.many", scars);
                yield join(s, lower(name(Trait.WEAR)));
            }
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        // Categories.
        for (Category c : Category.values()) {
            if (inside(mx, my, catX, catY + c.ordinal() * CAT_H, CAT_W, CAT_H)) {
                if (c != category) {
                    setCategory(c, false);
                }
                return true;
            }
        }
        // Bottom bar.
        if (inside(mx, my, margin, barY, randomW, BAR_H)) {
            randomizeCategory();
            return true;
        }
        if (inside(mx, my, allX(), barY, allW(), BAR_H)) {
            randomizeAll();
            return true;
        }
        if (inside(mx, my, confirmX(), barY, panelX + panelW - confirmX(), BAR_H)) {
            confirm();
            return true;
        }
        if (!firstTime && inside(mx, my, panelX, barY, cancelW(), BAR_H)) {
            close();
            return true;
        }
        float[] reset = resetRect();
        if (reset != null && inside(mx, my, reset[0] - 2, reset[1], reset[2] + 4, reset[3])) {
            set(original);
            return true;
        }
        // Turn buttons, then drag the character to turn it.
        float ty = turnY();
        if (inside(mx, my, stageCX - turnOffset() - 9, ty - 9, 18, 18)) {
            yawTarget -= 45;
            return true;
        }
        if (inside(mx, my, stageCX + turnOffset() - 9, ty - 9, 18, 18)) {
            yawTarget += 45;
            return true;
        }
        if (inside(mx, my, stageX, 0, stageW, barY)) {
            dragging = true;
            lastDragX = mx;
            return true;
        }
        // Content.
        if (inside(mx, my, contentX - 4, contentY, contentW + 8, contentH)) {
            float y = contentY - scroll;
            for (Row row : rows) {
                float h = row.height();
                if (my >= y && my < y + h) {
                    if (row.click(mx, my, contentX, y)) {
                        activeRow = row;
                        return true;
                    }
                    return false;
                }
                y += h;
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
            float y = contentY - scroll;
            for (Row row : rows) {
                if (row == activeRow) {
                    row.drag(mx, my, contentX, y);
                    return true;
                }
                y += row.height();
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
        if (inside(mx, my, stageX, 0, stageW, barY)) {
            frameTarget = Mth.clamp(frameTarget * (1 - (float) delta * 0.12F), 0.55F, 2.6F);
            // Zoomed in, look at the face; zoomed out, at the whole body.
            focusTarget = Mth.lerp(Mth.clamp((2.0F - frameTarget) / 1.15F, 0, 1), 0.95F, 1.76F);
            return true;
        }
        if (inside(mx, my, panelX, panelY, panelW, panelH)) {
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

    /** Section title: spaced capitals, a hairline, and the current value on the right in pink. */
    private void section(GuiGraphics g, String label, String value, float x, float y, float alpha) {
        float capTop = y + 10;
        float lw = caps(g, font, label, TEXT, x, capTop - 3.06F, 0.8F, a(MUTED, 255 * alpha));
        float right = x + contentW;
        if (value != null) {
            String v = Ui.ellipsis(font, value, TEXT_BOLD, Math.max(20, Math.round(contentW - lw - 20)));
            right -= width(font, v, TEXT_BOLD);
            text(g, font, lit(v, TEXT_BOLD), right, capTop - 3.06F, a(VALUE, 255 * alpha));
            right -= 4.67F;
        }
        gradientH(g, x + lw + 4.67F, capTop + 1.75F, right, capTop + 1.75F + px, a(WHITE, 41 * alpha), a(WHITE, 0));
    }

    /** Background of an option (the mock-up's .opt): discreet grey, red when chosen. */
    private void optionBack(GuiGraphics g, float x, float y, float w, float h, boolean on, float hover, float alpha) {
        if (on) {
            gradientV(g, x, y, x + w, y + h, a(ACCENT, 87 * alpha), a(ACCENT, 51 * alpha));
            border(g, x, y, w, h, px, a(0xF05A50, 191 * alpha));
        } else {
            rect(g, x, y, x + w, y + h, a(WHITE, (9 + 10 * hover) * alpha));
            border(g, x, y, w, h, px, a(WHITE, (23 + 33 * hover) * alpha));
        }
    }

    /**
     * An option with its centred label, ticked and bold when chosen (without the tick, then without the bold, when the
     * label would not fit). Returns true when the label had to be cut.
     */
    private boolean option(GuiGraphics g, float x, float y, float w, float h, String label, boolean on, float hover, float alpha) {
        optionBack(g, x, y, w, h, on, hover, alpha);
        float room = w - 3;
        ResourceLocation f = on ? TEXT_BOLD : TEXT;
        float tick = on ? 4.33F : 0;
        if (on && width(font, label, f) + tick > room) {
            tick = 0;
            if (width(font, label, f) > room) {
                f = TEXT;
            }
        }
        String s = Ui.ellipsis(font, label, f, Math.max(8, (int) (room - tick)));
        float tx = x + (w - width(font, s, f) - tick) / 2 + tick;
        int color = a(on ? WHITE : mix(OPTION, WHITE, hover), 255 * alpha);
        if (tick > 0) {
            check(g, tx - tick, y + h / 2 - 1.33F, 2.67F, 0.5F, color);
        }
        text(g, font, lit(s, f), tx, y + h / 2 - 4.9F, color);
        return !s.equals(label);
    }

    /** Track of a slider line: {start, end}, between the label column and the value column. */
    private float[] track(float x, String label, float valueW) {
        float lw = Math.max(50, width(font, label, TEXT) + 4);
        return new float[]{x + lw + 6, x + contentW - Math.max(30, valueW) - 6};
    }

    /** A slider line: label, track filled in red up to the knob (or from the middle), notches, knob, value on the right. */
    private void slider(GuiGraphics g, float x, float y, String label, String value, float valueW, int v, int min, int max,
                        boolean fromMiddle, boolean enabled, float hover, float alpha) {
        float cy = y + SLIDER_H / 2;
        text(g, font, lit(label, TEXT), x, cy - 4.9F, a(enabled ? OPTION : FAINT, 255 * alpha));
        float[] t = track(x, label, valueW);
        rect(g, t[0], cy - 0.67F, t[1], cy + 0.67F, a(WHITE, 31 * alpha));
        float knob = t[0] + (t[1] - t[0]) * (max > min ? (v - min) / (float) (max - min) : 0);
        if (enabled) {
            float from = fromMiddle ? (t[0] + t[1]) / 2 : t[0];
            rect(g, Math.min(from, knob), cy - 0.67F, Math.max(from, knob), cy + 0.67F, a(ACCENT, 255 * alpha));
        }
        int steps = max - min + 1;
        if (steps <= 9) {
            for (int i = 0; i < steps; i++) {
                float sx = t[0] + (t[1] - t[0]) * i / (steps - 1);
                rect(g, sx - 0.33F, cy - 1.33F, sx + 0.33F, cy + 1.33F, a(WHITE, 56 * alpha));
            }
        }
        if (enabled) {
            disc(g, knob, cy + 0.67F, 4.33F, a(0, 70 * alpha));
            ring(g, knob, cy, 3.33F, 4.67F + 0.5F * hover, a(ACCENT, 115 * alpha));
            disc(g, knob, cy, 3.33F, a(WHITE, 255 * alpha));
        } else {
            disc(g, knob, cy, 2.67F, a(FAINT, 255 * alpha));
        }
        text(g, font, lit(value, TEXT_BOLD), x + contentW - width(font, value, TEXT_BOLD), cy - 4.9F, a(enabled ? VALUE : FAINT, 255 * alpha));
    }

    /** Value of a slider from the mouse position. */
    private int sliderValue(double mx, float x, String label, float valueW, int min, int max) {
        float[] t = track(x, label, valueW);
        float f = Mth.clamp((float) (mx - t[0]) / (t[1] - t[0]), 0, 1);
        return min + Math.round(f * (max - min));
    }

    /** One block of the panel (heights in canvas pixels, fractions allowed). */
    private abstract class Row {
        abstract float height();

        abstract void render(GuiGraphics g, float x, float y, int mx, int my, float alpha);

        /** Returns true when the click was used (the row then receives drags). */
        abstract boolean click(double mx, double my, float x, float y);

        void drag(double mx, double my, float x, float y) {
        }
    }

    /** Presets as a grid of options (only those that can still be chosen: the retired ones are not shown). */
    private final class ChipRow extends Row {
        private final Trait trait;
        private final List<Integer> values = new ArrayList<>();
        private final int cols;

        ChipRow(Trait trait) {
            this.trait = trait;
            for (int i = 0; i < Catalog.count(trait); i++) {
                if (Catalog.offered(trait, i)) {
                    values.add(i);
                }
            }
            cols = columns();
        }

        /** One line when the few options fit side by side, otherwise three columns (two for long names). */
        private int columns() {
            int n = values.size();
            int[] tries = n <= 5 ? new int[]{n, 3, 2} : new int[]{3, 2};
            for (int c : tries) {
                if (c < 1 || c > n) {
                    continue;
                }
                float cw = (contentW - (c - 1) * OPT_GAP) / c;
                boolean fits = true;
                for (int v : values) {
                    if (width(font, valueName(trait, v).getString(), TEXT) + 3 > cw) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    return c;
                }
            }
            return Math.max(1, Math.min(n, 2));
        }

        private float cellW() {
            return (contentW - (cols - 1) * OPT_GAP) / cols;
        }

        @Override
        float height() {
            int lines = (values.size() + cols - 1) / cols;
            return SECTION + lines * (OPT_H + OPT_GAP) - OPT_GAP;
        }

        @Override
        void render(GuiGraphics g, float x, float y, int mx, int my, float alpha) {
            section(g, traitName(trait).getString(), name(trait), x, y, alpha);
            float cw = cellW();
            for (int n = 0; n < values.size(); n++) {
                int v = values.get(n);
                float cx = x + (n % cols) * (cw + OPT_GAP);
                float cy = y + SECTION + (n / cols) * (OPT_H + OPT_GAP);
                boolean on = current.get(trait) == v;
                boolean hovered = inside(mx, my, cx, cy, cw, OPT_H);
                String label = valueName(trait, v).getString();
                if (option(g, cx, cy, cw, OPT_H, label, on, hover(trait.key() + v, hovered && !on), alpha) && hovered) {
                    tooltip = label;
                }
            }
        }

        @Override
        boolean click(double mx, double my, float x, float y) {
            float cw = cellW();
            for (int n = 0; n < values.size(); n++) {
                if (inside(mx, my, x + (n % cols) * (cw + OPT_GAP), y + SECTION + (n / cols) * (OPT_H + OPT_GAP), cw, OPT_H)) {
                    change(trait, values.get(n));
                    return true;
                }
            }
            return false;
        }
    }

    /** Colour swatches. */
    private final class SwatchRow extends Row {
        private static final float SIZE = 13;
        private static final float STEP = 17;
        private final Trait trait;

        SwatchRow(Trait trait) {
            this.trait = trait;
        }

        private int perLine() {
            return Math.max(1, (int) ((contentW - SIZE) / STEP) + 1);
        }

        @Override
        float height() {
            int lines = (Catalog.count(trait) + perLine() - 1) / perLine();
            return SECTION + (lines - 1) * STEP + SIZE + 3;
        }

        private int color(int index) {
            if (trait == Trait.LIP_COLOR && index == 0) {
                int skin = Catalog.swatch(current, Trait.SKIN_TONE).argb();
                return Colors.tint(Colors.shade(skin, 0.86F), Colors.rgb(190, 90, 100), 0.3F);
            }
            return Catalog.palette(trait).get(index).argb();
        }

        @Override
        void render(GuiGraphics g, float x, float y, int mx, int my, float alpha) {
            section(g, traitName(trait).getString(), name(trait), x, y, alpha);
            List<Swatch> palette = Catalog.palette(trait);
            int per = perLine();
            int selected = current.get(trait);
            for (int i = 0; i < palette.size(); i++) {
                float cx = x + SIZE / 2 + (i % per) * STEP;
                float cy = y + SECTION + SIZE / 2 + (i / per) * STEP;
                boolean hovered = inside(mx, my, cx - SIZE / 2 - 1, cy - SIZE / 2 - 1, SIZE + 2, SIZE + 2);
                float h = hover(trait.key() + "s" + i, hovered);
                float r = SIZE / 2 + 0.5F * h;
                disc(g, cx, cy + 0.67F, r + 0.33F, a(0, 90 * alpha));
                disc(g, cx, cy, r, a(color(i), 255 * alpha));
                ring(g, cx, cy, r - px, r, a(WHITE, 41 * alpha));
                if (i == selected) {
                    ring(g, cx, cy, SIZE / 2 + 2, SIZE / 2 + 2 + 2 * px, a(WHITE, 255 * alpha));
                }
                if (hovered) {
                    tooltip = valueName(trait, i).getString();
                }
            }
        }

        @Override
        boolean click(double mx, double my, float x, float y) {
            int per = perLine();
            for (int i = 0; i < Catalog.count(trait); i++) {
                float cx = x + SIZE / 2 + (i % per) * STEP;
                float cy = y + SECTION + SIZE / 2 + (i / per) * STEP;
                if (inside(mx, my, cx - SIZE / 2 - 1, cy - SIZE / 2 - 1, SIZE + 2, SIZE + 2)) {
                    change(trait, i);
                    return true;
                }
            }
            return false;
        }
    }

    /** Five-step slider, with a word for each step ("Medium"); the first of a group opens a section. */
    private final class SliderRow extends Row {
        private final Trait trait;
        private final boolean titled;
        private final float valueW;

        SliderRow(Trait trait, boolean titled) {
            this.trait = trait;
            this.titled = titled;
            float w = 0;
            for (int i = 0; i < Trait.SLIDER_STEPS; i++) {
                w = Math.max(w, width(font, word(i), TEXT_BOLD));
            }
            valueW = w;
        }

        private String word(int value) {
            return tr("caracrea.slider." + trait.key() + "." + value);
        }

        private float top() {
            return titled ? SECTION - 4 : 3.33F;
        }

        @Override
        float height() {
            return top() + SLIDER_H;
        }

        @Override
        void render(GuiGraphics g, float x, float y, int mx, int my, float alpha) {
            if (titled) {
                section(g, tr("caracrea.section.tuning"), null, x, y, alpha);
            }
            int value = current.get(trait);
            boolean hovered = inside(mx, my, x, y + top(), contentW, SLIDER_H) || activeRow == this;
            slider(g, x, y + top(), traitName(trait).getString(), word(value), valueW, value, 0, Trait.SLIDER_STEPS - 1, false, true,
                    hover(trait.key() + "k", hovered), alpha);
        }

        @Override
        boolean click(double mx, double my, float x, float y) {
            if (!inside(mx, my, x, y + top(), contentW, SLIDER_H)) {
                return false;
            }
            drag(mx, my, x, y);
            return true;
        }

        @Override
        void drag(double mx, double my, float x, float y) {
            change(trait, sliderValue(mx, x, traitName(trait).getString(), valueW, 0, Trait.SLIDER_STEPS - 1));
        }
    }

    /** On/off options with a box (marks), two per line. The old fixed face scars are replaced by placed scars. */
    private final class ToggleRow extends Row {
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

        private String label(int bit) {
            return tr("caracrea.marks." + Catalog.options(trait).get(bit).id());
        }

        private float cellW() {
            return (contentW - OPT_GAP) / 2;
        }

        @Override
        float height() {
            int lines = (bits.size() + 1) / 2;
            return SECTION + lines * (OPT_H + OPT_GAP) - OPT_GAP;
        }

        @Override
        void render(GuiGraphics g, float x, float y, int mx, int my, float alpha) {
            section(g, traitName(trait).getString(), null, x, y, alpha);
            float cw = cellW();
            for (int n = 0; n < bits.size(); n++) {
                int bit = bits.get(n);
                float cx = x + (n % 2) * (cw + OPT_GAP);
                float cy = y + SECTION + (n / 2) * (OPT_H + OPT_GAP);
                boolean on = current.hasMark(1 << bit);
                boolean hovered = inside(mx, my, cx, cy, cw, OPT_H);
                float h = hover("mark" + bit, hovered && !on);
                optionBack(g, cx, cy, cw, OPT_H, on, h, alpha);
                float bx = cx + 4.67F, by = cy + OPT_H / 2 - 2.67F;
                if (on) {
                    rect(g, bx, by, bx + 5.33F, by + 5.33F, a(WHITE, 255 * alpha));
                    check(g, bx + 1.2F, by + 1.1F, 2.93F, 0.6F, a(0xB3201B, 255 * alpha));
                } else {
                    border(g, bx, by, 5.33F, 5.33F, px, a(WHITE, 89 * alpha));
                }
                String s = label(bit);
                String shown = Ui.ellipsis(font, s, on ? TEXT_BOLD : TEXT, Math.round(cw - 16));
                text(g, font, lit(shown, on ? TEXT_BOLD : TEXT), cx + 12.67F, cy + OPT_H / 2 - 4.9F,
                        a(on ? WHITE : mix(OPTION, WHITE, h), 255 * alpha));
                if (hovered && !shown.equals(s)) {
                    tooltip = s;
                }
            }
        }

        @Override
        boolean click(double mx, double my, float x, float y) {
            float cw = cellW();
            for (int n = 0; n < bits.size(); n++) {
                if (inside(mx, my, x + (n % 2) * (cw + OPT_GAP), y + SECTION + (n / 2) * (OPT_H + OPT_GAP), cw, OPT_H)) {
                    int bit = bits.get(n);
                    set(current.withMark(1 << bit, !current.hasMark(1 << bit)));
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Scars: three slots, a front and a back silhouette to click (or drag on) where the selected scar goes, then its
     * size, bend and orientation. A change that would bring two scars too close together is refused (see Scar).
     */
    private final class ScarRow extends Row {
        /** How long the "too close" warning stays, in milliseconds. */
        private static final long WARNING_MS = 2500;
        private static final float GAP = 23.33F;
        private static final float LINE = 6.67F;
        /** The three settings of the selected scar: trait field, first and last value. */
        private static final int[][] SETTINGS = {{Scar.SIZE, 1, Scar.SIZES}, {Scar.BEND, 0, 2 * Scar.BENDS}, {Scar.ANGLE, 0, Scar.ANGLES - 1}};
        private static final String[] SETTING_KEYS = {"caracrea.scar.size", "caracrea.scar.bend", "caracrea.scar.angle"};
        /** What the mouse drags: -1 nothing, 0 the scar on a silhouette, 1 + i the slider i. */
        private int dragging = -1;
        private long refusedAt;
        private final float[] valueW = new float[SETTINGS.length];

        ScarRow() {
            for (int i = 0; i < SETTINGS.length; i++) {
                for (int v = SETTINGS[i][1]; v <= SETTINGS[i][2]; v++) {
                    valueW[i] = Math.max(valueW[i], width(font, settingValue(i, v), TEXT_BOLD));
                }
            }
        }

        /** Pixel size of one model unit on the silhouettes. */
        private int unit() {
            return Mth.clamp((contentW - 24) / 34, 2, 5);
        }

        private float mapTop(float y) {
            return y + SECTION + OPT_H + 6.67F;
        }

        private float slidersTop(float y) {
            return mapTop(y) + unit() * 32 + 9.33F + 2.67F;
        }

        private float hintTop(float y) {
            return slidersTop(y) + 3 * SLIDER_H + 3.33F;
        }

        private float removeW() {
            return capsWidth(font, tr("caracrea.scar.remove"), TEXT_BOLD, 0.6F) + 10.67F;
        }

        private List<FormattedCharSequence> lines(String key) {
            return font.split(Ui.tr(TEXT, key), Math.round(contentW - removeW() - 6));
        }

        private float hintH() {
            int n = Math.max(lines("caracrea.scar.hint").size(), lines("caracrea.scar.too_close").size());
            return Math.max(n * LINE, SLIDER_H);
        }

        @Override
        float height() {
            return hintTop(0) + hintH() + 2;
        }

        /** Shown value of setting {@code i}: "5 / 7", "Slight", "45°". */
        private String settingValue(int i, int value) {
            return switch (SETTINGS[i][0]) {
                case Scar.SIZE -> value + " / " + Scar.SIZES;
                case Scar.BEND -> {
                    int d = Math.abs(value - Scar.BENDS);
                    yield tr(d == 0 ? "caracrea.scar.bend.none" : d <= Scar.BENDS / 2 ? "caracrea.scar.bend.light" : "caracrea.scar.bend.strong");
                }
                default -> Math.round(value * 180F / Scar.ANGLES) + "°";
            };
        }

        /** {x, y, w, h} of a body part on a silhouette; view 0 = front, 1 = back. */
        private float[] partRect(Scar.Part part, int view, float ox, float oy, int u) {
            int arm = current.bodyType().slim() ? 3 : 4;
            boolean back = view == 1;
            return switch (part) {
                case HEAD -> new float[]{ox + 4 * u, oy, 8 * u, 8 * u};
                case BODY -> new float[]{ox + 4 * u, oy + 8 * u, 8 * u, 12 * u};
                case RIGHT_ARM -> new float[]{back ? ox + 12 * u : ox + (4 - arm) * u, oy + 8 * u, arm * u, 12 * u};
                case LEFT_ARM -> new float[]{back ? ox + (4 - arm) * u : ox + 12 * u, oy + 8 * u, arm * u, 12 * u};
                case RIGHT_LEG -> new float[]{back ? ox + 8 * u : ox + 4 * u, oy + 20 * u, 4 * u, 12 * u};
                case LEFT_LEG -> new float[]{back ? ox + 4 * u : ox + 8 * u, oy + 20 * u, 4 * u, 12 * u};
            };
        }

        /** Left edge of each silhouette. */
        private float[] mapOrigins(float x) {
            float total = 16 * unit() * 2 + GAP;
            float start = x + (contentW - total) / 2;
            return new float[]{start, start + 16 * unit() + GAP};
        }

        /** The selected scar, brought to the 1.3.0 settings if it was saved before (straight, nearest size and angle). */
        private Appearance modern(Appearance a) {
            Trait[] t = Scar.traits(selectedScar);
            Scar scar = Scar.get(a, selectedScar);
            if (scar == null || a.get(t[Scar.SIZE]) != 0) {
                return a;
            }
            int size = Math.round((scar.length() - Scar.MIN_LENGTH) / (Scar.MAX_LENGTH - Scar.MIN_LENGTH) * (Scar.SIZES - 1)) + 1;
            int angle = Math.round(scar.angle() / (float) Math.PI * Scar.ANGLES) % Scar.ANGLES;
            return a.with(t[Scar.ANGLE], angle).with(t[Scar.SIZE], size).with(t[Scar.BEND], Scar.BENDS);
        }

        /** Applies a change unless two scars would come too close; then the warning shows instead. */
        private void tryApply(Appearance next) {
            if (Scar.valid(next)) {
                set(next);
            } else {
                refusedAt = System.currentTimeMillis();
            }
        }

        @Override
        void render(GuiGraphics g, float x, float y, int mx, int my, float alpha) {
            int used = Scar.of(current).size();
            section(g, tr("caracrea.scar.title"), tr("caracrea.scar.count", used, Scar.COUNT), x, y, alpha);
            // Slots.
            float tw = (contentW - 2 * OPT_GAP) / 3;
            for (int i = 0; i < Scar.COUNT; i++) {
                float tx = x + i * (tw + OPT_GAP), ty = y + SECTION;
                boolean on = i == selectedScar;
                boolean placed = current.get(Scar.traits(i)[Scar.ZONE]) != 0;
                float h = hover("scarTab" + i, !on && inside(mx, my, tx, ty, tw, OPT_H));
                optionBack(g, tx, ty, tw, OPT_H, on, h, alpha);
                ResourceLocation f = on ? TEXT_BOLD : TEXT;
                String name = tr("caracrea.scar.slot", i + 1);
                float nw = width(font, name, f);
                float lx = tx + (tw - nw - 4.33F) / 2 + 4.33F;
                disc(g, lx - 3, ty + OPT_H / 2, 1.33F, a(placed ? DANGER : WHITE, (placed ? 255 : 56) * alpha));
                text(g, font, lit(name, f), lx, ty + OPT_H / 2 - 4.9F, a(on ? WHITE : mix(OPTION, WHITE, h), 255 * alpha));
            }
            // Silhouettes.
            int u = unit();
            float top = mapTop(y);
            float[] origins = mapOrigins(x);
            int skin = Catalog.swatch(current, Trait.SKIN_TONE).argb();
            for (int view = 0; view < 2; view++) {
                String caption = tr(view == 0 ? "caracrea.scar.front" : "caracrea.scar.back");
                float cw = capsWidth(font, caption, TEXT, 0.43F);
                caps(g, font, caption, TEXT, origins[view] + 8 * u - cw / 2, top + 32 * u + 5 - 3.06F, 0.43F, a(MUTED, 255 * alpha));
                for (Scar.Part part : Scar.Part.values()) {
                    float[] r = partRect(part, view, origins[view], top, u);
                    boolean hovered = inside(mx, my, r[0], r[1], r[2], r[3]);
                    rect(g, r[0], r[1], r[0] + r[2], r[1] + r[3], a(hovered ? Colors.lighten(skin, 0.12F) : skin, 255 * alpha));
                    border(g, r[0], r[1], r[2], r[3], px, a(0, 46 * alpha));
                }
                // Eyes on the front head, so front and back are easy to tell apart.
                if (view == 0) {
                    float[] head = partRect(Scar.Part.HEAD, 0, origins[0], top, u);
                    float e = u * 0.78F;
                    rect(g, head[0] + 1.5F * u, head[1] + 3.6F * u, head[0] + 1.5F * u + e, head[1] + 3.6F * u + e, a(0x2B2B2B, 255 * alpha));
                    rect(g, head[0] + 5.6F * u, head[1] + 3.6F * u, head[0] + 5.6F * u + e, head[1] + 3.6F * u + e, a(0x2B2B2B, 255 * alpha));
                }
            }
            // Scars on the silhouettes, as they are drawn on the body; a dotted circle marks the selected one.
            for (int i = 0; i < Scar.COUNT; i++) {
                Scar scar = Scar.get(current, i);
                if (scar == null) {
                    continue;
                }
                int view = scar.zone().back ? 1 : 0;
                float[] r = partRect(scar.zone().part, view, origins[view], top, u);
                float[] cube = Scar.cube(scar.zone().part, current.bodyType());
                float[] p = scar.stroke(current.bodyType());
                boolean selected = i == selectedScar;
                int color = a(selected ? 0xFF4A3D : SCAR, 255 * alpha);
                float t = selected ? 1.33F : 0.67F;
                for (int k = 0; k + 3 < p.length; k += 2) {
                    float x0 = onMapX(p[k], cube, r, scar.zone().back), y0 = onMapY(p[k + 1], cube, r);
                    stroke(g, x0, y0, onMapX(p[k + 2], cube, r, scar.zone().back), onMapY(p[k + 3], cube, r), t, color);
                    disc(g, x0, y0, t / 2, color);
                }
                if (selected && p.length >= 2) {
                    int m = (p.length / 2) / 2 * 2;
                    dashedRing(g, onMapX(p[m], cube, r, scar.zone().back), onMapY(p[m + 1], cube, r), 4.33F, 0.5F, 12, a(WHITE, 230 * alpha));
                }
            }
            // Settings of the selected scar.
            Appearance shown = modern(current);
            Trait[] t = Scar.traits(selectedScar);
            boolean placed = current.get(t[Scar.ZONE]) != 0;
            for (int i = 0; i < SETTINGS.length; i++) {
                float sy = slidersTop(y) + i * SLIDER_H;
                int min = SETTINGS[i][1];
                int max = SETTINGS[i][2];
                int value = placed ? shown.get(t[SETTINGS[i][0]]) : SETTINGS[i][0] == Scar.BEND ? Scar.BENDS : min;
                boolean hovered = placed && (inside(mx, my, x, sy, contentW, SLIDER_H) || dragging == 1 + i);
                slider(g, x, sy, tr(SETTING_KEYS[i]), placed ? settingValue(i, value) : "–", valueW[i], value, min, max,
                        SETTINGS[i][0] == Scar.BEND, placed, hover("scarSet" + i, hovered), alpha);
            }
            // Hint (or the "too close" warning) and Remove.
            float hy = hintTop(y);
            float rowH = hintH();
            float bw = removeW();
            float bx = x + contentW - bw;
            float by = hy + (rowH - SLIDER_H) / 2;
            float h = hover("scarRemove", placed && inside(mx, my, bx, by, bw, SLIDER_H));
            float dim = placed ? 1 : 0.45F;
            rect(g, bx, by, bx + bw, by + SLIDER_H, a(WHITE, (13 + 9 * h) * alpha * dim));
            border(g, bx, by, bw, SLIDER_H, px, a(DANGER, (102 + 60 * h) * alpha * dim));
            caps(g, font, tr("caracrea.scar.remove"), TEXT_BOLD, bx + 5.33F, by + SLIDER_H / 2 - 4.9F, 0.6F,
                    a(placed ? mix(0xFF9A94, WHITE, h) : FAINT, 255 * alpha));
            boolean warning = System.currentTimeMillis() - refusedAt < WARNING_MS;
            List<FormattedCharSequence> hint = lines(warning ? "caracrea.scar.too_close" : "caracrea.scar.hint");
            float ly = hy + (rowH - hint.size() * LINE) / 2 + LINE / 2 - 4.9F;
            int color = a(warning ? DANGER : MUTED, 255 * alpha);
            if (color >>> 24 >= 4) {
                for (FormattedCharSequence line : hint) {
                    g.pose().pushPose();
                    g.pose().translate(x, ly, 0);
                    g.drawString(font, line, 0, 0, color, false);
                    g.pose().popPose();
                    ly += LINE;
                }
            }
        }

        /** Silhouette x of a bone-space x on a face (seen from behind on the back view). */
        private float onMapX(float bx, float[] cube, float[] r, boolean back) {
            float t = (bx - cube[0]) / cube[3];
            return r[0] + (back ? 1 - t : t) * r[2];
        }

        private float onMapY(float by, float[] cube, float[] r) {
            return r[1] + (by - cube[1]) / cube[4] * r[3];
        }

        @Override
        boolean click(double mx, double my, float x, float y) {
            dragging = -1;
            float tw = (contentW - 2 * OPT_GAP) / 3;
            for (int i = 0; i < Scar.COUNT; i++) {
                if (inside(mx, my, x + i * (tw + OPT_GAP), y + SECTION, tw, OPT_H)) {
                    selectedScar = i;
                    return true;
                }
            }
            if (place(mx, my, x, y, true)) {
                dragging = 0;
                return true;
            }
            Trait[] t = Scar.traits(selectedScar);
            if (current.get(t[Scar.ZONE]) == 0) {
                return false;
            }
            for (int i = 0; i < SETTINGS.length; i++) {
                if (inside(mx, my, x, slidersTop(y) + i * SLIDER_H, contentW, SLIDER_H)) {
                    dragging = 1 + i;
                    drag(mx, my, x, y);
                    return true;
                }
            }
            float bw = removeW();
            if (inside(mx, my, x + contentW - bw, hintTop(y) + (hintH() - SLIDER_H) / 2, bw, SLIDER_H)) {
                Appearance next = current;
                for (Trait trait : t) {
                    next = next.with(trait, 0);
                }
                set(next);
                return true;
            }
            return false;
        }

        @Override
        void drag(double mx, double my, float x, float y) {
            if (dragging == 0) {
                place(mx, my, x, y, false);
            } else if (dragging > 0) {
                int i = dragging - 1;
                int value = sliderValue(mx, x, tr(SETTING_KEYS[i]), valueW[i], SETTINGS[i][1], SETTINGS[i][2]);
                Trait trait = Scar.traits(selectedScar)[SETTINGS[i][0]];
                Appearance next = modern(current);
                if (next.get(trait) != value || next != current) {
                    tryApply(next.with(trait, value));
                }
            }
        }

        /**
         * Puts the selected scar where the mouse is on a silhouette (a new scar starts small, straight and slanted).
         * Returns false when the mouse is not over the body. {@code turn}: also turn the character to that side.
         */
        private boolean place(double mx, double my, float x, float y, boolean turn) {
            Trait[] t = Scar.traits(selectedScar);
            int u = unit();
            float top = mapTop(y);
            float[] origins = mapOrigins(x);
            for (int view = 0; view < 2; view++) {
                for (Scar.Part part : Scar.Part.values()) {
                    float[] r = partRect(part, view, origins[view], top, u);
                    if (!inside(mx, my, r[0], r[1], r[2], r[3])) {
                        continue;
                    }
                    int gx = Mth.clamp((int) ((mx - r[0]) / r[2] * Scar.POSITIONS), 0, Scar.POSITIONS - 1);
                    int gy = Mth.clamp((int) ((my - r[1]) / r[3] * Scar.POSITIONS), 0, Scar.POSITIONS - 1);
                    Appearance next = modern(current).with(t[Scar.ZONE], Scar.Zone.of(part, view == 1).ordinal() + 1)
                            .with(t[Scar.X], gx).with(t[Scar.Y], gy);
                    if (current.get(t[Scar.ZONE]) == 0) {
                        next = next.with(t[Scar.ANGLE], Scar.ANGLES / 6).with(t[Scar.SIZE], 3).with(t[Scar.BEND], Scar.BENDS);
                    }
                    tryApply(next);
                    if (turn) {
                        // Show the side where the scar went.
                        float facing = view == 1 ? 180 : 0;
                        yawTarget = facing + Math.round((yawTarget - facing) / 360F) * 360F;
                    }
                    return true;
                }
            }
            return false;
        }
    }
}
