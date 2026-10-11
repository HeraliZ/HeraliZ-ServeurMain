package fr.heraliz.emote.client;

import fr.heraliz.emote.client.gui.kit.Echelle;
import fr.heraliz.emote.client.gui.kit.HzButton;
import fr.heraliz.emote.client.gui.kit.SonsUi;
import fr.heraliz.emote.client.gui.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings screen, opened from the mods list ("Config"). HeraliZ interface style (clans kit copy): glass panel, one row
 * per setting (name, short explanation, switch or slider), same settings and same saving as before.
 */
public class HeralizConfigScreen extends Screen {
    private static final int ROW = 25;

    @Nullable
    private final Screen parent;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private int px, py, pw, ph;
    private int closeX, closeY;

    public HeralizConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("heraliz_emote.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        rows.clear();
        pw = Math.min(440, width - 24);
        ph = Math.min(9 * ROW + 92, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int x = px + Ui.MARGE, w = pw - 2 * Ui.MARGE;
        int y = py + 52;
        toggle(x, y, w, "enabled", HeralizConfig.BORROW_ENABLED);
        toggle(x, y += ROW, w, "empty_hand_only", HeralizConfig.EMPTY_HAND_ONLY);
        toggle(x, y += ROW, w, "sync_emote", HeralizConfig.SYNC_EMOTE);
        toggle(x, y += ROW, w, "borrow_sound", HeralizConfig.BORROW_SOUND);
        toggle(x, y += ROW, w, "show_messages", HeralizConfig.SHOW_MESSAGES);
        slider(x, y += ROW, w, "hold_seconds", HeralizConfig.HOLD_SECONDS, 1, 10);
        slider(x, y += ROW, w, "reach", HeralizConfig.REACH, 2, 16);
        toggle(x, y += ROW, w, "other_players", HeralizConfig.OTHER_PLAYERS_SOUNDS);
        toggle(x, y += ROW, w, "hide_emotecraft_emotes", HeralizConfig.HIDE_EMOTECRAFT_EMOTES);
        Component done = CommonComponents.GUI_DONE;
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - 110, py + ph - 28, 110, 18, done, Ui.accent(), this::onClose)
                .look(HzButton.Look.PRIMARY));
        closeX = px + pw - Ui.MARGE - 9;
        closeY = py + 15;
    }

    private void toggle(int x, int y, int w, String key, ForgeConfigSpec.BooleanValue setting) {
        rows.add(addRenderableWidget(new Toggle(x, y, w, ROW - 3, key, setting)));
    }

    private void slider(int x, int y, int w, String key, ForgeConfigSpec.IntValue setting, int min, int max) {
        rows.add(addRenderableWidget(new Slider(x, y, w, ROW - 3, key, setting, min, max)));
    }

    /** Name and short explanation of a setting, on the left of its row. */
    private static void label(GuiGraphics g, Font font, int x, int y, int maxW, String key, boolean hover) {
        g.drawString(font, Ui.tr(Ui.FONT, "heraliz_emote.config." + key), x + 8, y + 2, Ui.argb(hover ? 0xFFFFFF : Ui.TEXT, 255), false);
        String tip = Component.translatable("heraliz_emote.config." + key + ".tooltip").getString();
        g.drawString(font, Ui.lit(Ui.ellipsis(font, tip, Ui.SMALL, maxW), Ui.SMALL), x + 8, y + 12, Ui.argb(Ui.MUTED, 255), false);
    }

    private static void rowBackground(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, hover ? 12 : 5));
    }

    /** On/off switch for a boolean setting (writes the setting at once, like the old on/off button). */
    private static final class Toggle extends AbstractWidget {
        private final String key;
        private final ForgeConfigSpec.BooleanValue setting;

        Toggle(int x, int y, int w, int h, String key, ForgeConfigSpec.BooleanValue setting) {
            super(x, y, w, h, Component.translatable("heraliz_emote.config." + key));
            this.key = key;
            this.setting = setting;
        }

        @Override
        public void onClick(double mx, double my) {
            setting.set(!setting.get());
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            SonsUi.clic(0.6F);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            rowBackground(g, x, y, w, h, isHovered);
            label(g, font, x, y + 1, w - 64, key, isHovered);
            boolean on = setting.get();
            int accent = Ui.accent();
            int sw = 22, sh = 10, sx = x + w - sw - 8, sy = y + (h - sh) / 2;
            Ui.soft(g, sx, sy, sw, sh, on ? Ui.argb(accent, isHovered ? 200 : 160) : Ui.argb(0xFFFFFF, isHovered ? 30 : 18));
            Ui.softOutline(g, sx + 1, sy + 1, sw - 2, sh - 2, on ? Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.3f), 200) : Ui.argb(0xFFFFFF, 50));
            int kx = on ? sx + sw - 8 : sx + 2;
            Ui.soft(g, kx, sy + 2, 6, 6, on ? 0xFFFFFFFF : Ui.argb(Ui.MUTED, 255));
            String state = Component.translatable(on ? "options.on" : "options.off").getString();
            Ui.spacedRight(g, font, state, Ui.SMALL, sx - 6, sy + 2, on ? Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.4f), 255) : Ui.argb(Ui.FAINT, 255), 0.8f);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.translatable("heraliz_emote.config." + key));
        }
    }

    /** Slider for a number setting (writes the setting while dragging, like the old slider). */
    private static final class Slider extends AbstractSliderButton {
        private final String key;
        private final ForgeConfigSpec.IntValue setting;
        private final int min, max;

        Slider(int x, int y, int w, int h, String key, ForgeConfigSpec.IntValue setting, int min, int max) {
            super(x, y, w, h, Component.translatable("heraliz_emote.config." + key), (setting.get() - min) / (double) (max - min));
            this.key = key;
            this.setting = setting;
            this.min = min;
            this.max = max;
        }

        private int current() {
            return Mth.clamp(min + (int) Math.round(this.value * (max - min)), min, max);
        }

        private int trackX() {
            return getX() + getWidth() - 8 - 110;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("heraliz_emote.config." + key + ".value", Component.translatable("heraliz_emote.config." + key), current()));
        }

        @Override
        protected void applyValue() {
            setting.set(current());
        }

        @Override
        public void onClick(double mx, double my) {
            setFromMouse(mx);
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            setFromMouse(mx);
        }

        private void setFromMouse(double mx) {
            double old = value;
            value = Mth.clamp((mx - trackX()) / 110.0, 0, 1);
            value = (current() - min) / (double) (max - min);
            if (old != value) applyValue();
            updateMessage();
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            SonsUi.clic(0.5F);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHovered || isFocused();
            rowBackground(g, x, y, w, h, hover);
            label(g, font, x, y + 1, w - 170, key, hover);
            int accent = Ui.accent();
            int tx = trackX(), ty = y + h / 2;
            g.fill(tx, ty, tx + 110, ty + 2, Ui.argb(0xFFFFFF, 30));
            int fx = tx + (int) Math.round(value * 110);
            g.fill(tx, ty, fx, ty + 2, Ui.argb(accent, 230));
            Ui.soft(g, fx - 3, ty - 3, 7, 8, hover ? 0xFFFFFFFF : Ui.argb(Ui.TEXT, 255));
            String unit = Component.translatable(key.equals("reach") ? "heraliz_emote.config.unit_blocks" : "heraliz_emote.config.unit_seconds").getString();
            Ui.right(g, font, Ui.lit(current() + " " + unit, Ui.BOLD), tx - 10, y + (h - 8) / 2f, Ui.argb(0xFFFFFF, 255));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft != null && minecraft.level != null) {
            Ui.fond(g, width, height);
        } else {
            // from the mods list: no world behind, a dark diagonal gradient instead of the dirt background
            g.fillGradient(0, 0, width, height, 0xFF15181F, 0xFF07080B);
            Ui.band(g, 0, height - height / 3, width, height, Ui.accent(), 14, width / 3);
        }
        Ui.glass(g, px, py, pw, ph);
        Ui.spaced(g, font, title.getString(), Ui.BOLD, px + Ui.MARGE, py + 16, Ui.argb(Ui.TEXT, 255), 1.6f);
        int tw = (int) Ui.spacedWidth(font, title.getString().toUpperCase(Locale.ROOT), Ui.BOLD, 1.6f);
        g.drawString(font, Ui.lit(Component.translatable("heraliz_emote.config.section").getString(), Ui.FONT), px + Ui.MARGE + tw + 14, py + 16,
                Ui.argb(Ui.MUTED, 255), false);
        boolean overClose = mouseX >= closeX - 3 && mouseX < closeX + 10 && mouseY >= closeY - 3 && mouseY < closeY + 10;
        for (int i = 0; i < 7; i++) {
            int c = overClose ? Ui.argb(Ui.TEXT, 255) : Ui.argb(Ui.MUTED, 255);
            g.fill(closeX + i, closeY + i, closeX + i + 1, closeY + i + 1, c);
            g.fill(closeX + 6 - i, closeY + i, closeX + 7 - i, closeY + i + 1, c);
        }
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + 34, 0xFFFFFF, 36, false, true);
        String sub = Component.translatable("heraliz_emote.config.subtitle").getString();
        g.drawString(font, Ui.lit(Ui.ellipsis(font, sub, Ui.SMALL, pw - 2 * Ui.MARGE), Ui.SMALL), px + Ui.MARGE, py + 40, Ui.argb(Ui.MUTED, 255), false);
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + ph - 36, 0xFFFFFF, 30, true, true);
        super.render(g, mouseX, mouseY, partialTick);
        for (AbstractWidget r : rows) {
            if (r.isHovered()) {
                String key = r instanceof Toggle t ? t.key : ((Slider) r).key;
                List<Component> lines = new ArrayList<>();
                lines.add(Ui.tr(Ui.BOLD, "heraliz_emote.config." + key));
                for (var seq : font.split(Ui.tr(Ui.FONT, "heraliz_emote.config." + key + ".tooltip"), 220)) {
                    StringBuilder sb = new StringBuilder();
                    seq.accept((idx, style, cp) -> {
                        sb.appendCodePoint(cp);
                        return true;
                    });
                    lines.add(Ui.lit(sb.toString(), Ui.FONT));
                }
                Ui.tooltip(g, font, lines, mouseX, mouseY, Ui.accent());
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= closeX - 3 && mx < closeX + 10 && my >= closeY - 3 && my < closeY + 10) {
            SonsUi.clic(0.6F);
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void onClose() {
        HeralizConfig.SPEC.save();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }
}
