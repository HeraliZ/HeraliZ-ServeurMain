package fr.heraliz.emote.client.dev;

import fr.heraliz.emote.client.HeralizConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;

/** Settings screen, opened from the mods list ("Config"). */
/** Ancien ecran de reglages (2.5.1), banc seulement. */
public class AncienReglages extends Screen {
    @Nullable
    private final Screen parent;

    public AncienReglages(@Nullable Screen parent) {
        super(Component.translatable("heraliz_emote.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 310;
        int x = this.width / 2 - w / 2;
        int y = 40;
        int row = 22;
        toggle(x, y, w, "enabled", HeralizConfig.BORROW_ENABLED);
        toggle(x, y += row, w, "empty_hand_only", HeralizConfig.EMPTY_HAND_ONLY);
        toggle(x, y += row, w, "sync_emote", HeralizConfig.SYNC_EMOTE);
        toggle(x, y += row, w, "borrow_sound", HeralizConfig.BORROW_SOUND);
        toggle(x, y += row, w, "show_messages", HeralizConfig.SHOW_MESSAGES);
        slider(x, y += row, w, "hold_seconds", HeralizConfig.HOLD_SECONDS, 1, 10);
        slider(x, y += row, w, "reach", HeralizConfig.REACH, 2, 16);
        toggle(x, y += row, w, "other_players", HeralizConfig.OTHER_PLAYERS_SOUNDS);
        toggle(x, y += row, w, "hide_emotecraft_emotes", HeralizConfig.HIDE_EMOTECRAFT_EMOTES);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void toggle(int x, int y, int w, String key, ForgeConfigSpec.BooleanValue setting) {
        addRenderableWidget(toggleButton(x, y, w, key, setting));
    }

    private void slider(int x, int y, int w, String key, ForgeConfigSpec.IntValue setting, int min, int max) {
        addRenderableWidget(sliderButton(x, y, w, key, setting, min, max));
    }

    /** An on/off button for a setting, labelled {@code heraliz_emote.config.<key>} (and its tooltip). */
    static CycleButton<Boolean> toggleButton(int x, int y, int w, String key, ForgeConfigSpec.BooleanValue setting) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(setting.get())
                .create(x, y, w, 20, Component.translatable("heraliz_emote.config." + key), (b, v) -> setting.set(v));
        button.setTooltip(Tooltip.create(Component.translatable("heraliz_emote.config." + key + ".tooltip")));
        return button;
    }

    /** A slider for a number setting, labelled {@code heraliz_emote.config.<key>.value}. */
    static AbstractSliderButton sliderButton(int x, int y, int w, String key, ForgeConfigSpec.IntValue setting, int min, int max) {
        AbstractSliderButton slider = new AbstractSliderButton(x, y, w, 20, Component.empty(),
                (setting.get() - min) / (double) (max - min)) {
            {
                updateMessage();
            }

            private int current() {
                return Mth.clamp(min + (int) Math.round(this.value * (max - min)), min, max);
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("heraliz_emote.config." + key + ".value",
                        Component.translatable("heraliz_emote.config." + key), current()));
            }

            @Override
            protected void applyValue() {
                setting.set(current());
            }
        };
        slider.setTooltip(Tooltip.create(Component.translatable("heraliz_emote.config." + key + ".tooltip")));
        return slider;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("heraliz_emote.config.subtitle"), this.width / 2, 28, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        HeralizConfig.SPEC.save();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }
}
