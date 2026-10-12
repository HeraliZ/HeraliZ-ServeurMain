package com.caracrea.client.gui;

import com.caracrea.CaraCreaConfig;
import com.caracrea.client.gui.kit.Echelle;
import com.caracrea.client.gui.kit.HzButton;
import com.caracrea.client.gui.kit.SonsUi;
import com.caracrea.client.gui.kit.Ui;
import com.caracrea.client.voice.VoiceClient;
import com.caracrea.voice.net.VoiceProtocol;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Settings of the built-in voice chat ({@code /voix}, or the « Réglages » button of the voice menu, key ")" on AZERTY), in
 * the HeraliZ interface style: a glass panel with four tabs — <i>General</i> (on / off, push-to-talk or voice activation
 * with an automatic or set threshold on a live meter, mute, couper tout le monde, mode de voix chuchoter / parler /
 * crier, icons), <i>Microphone</i> (device, amplification, automatic volume, noise suppression strength,
 * and the microphone test: hear yourself with a large level meter showing the background level and the threshold),
 * <i>Sound</i> (output, volume, 3D, walls, jitter buffer, sound test) and <i>Network</i> (UDP or game connection, ping,
 * losses, buffer, bit rates). The per-player volumes and the groups are a click away. Changes apply at once.
 */
public class VoiceSettingsScreen extends Screen {
    private static final String[] TABS = {"general", "micro", "sound", "network"};
    private static final int[] BUFFERS = {0, 40, 60, 80, 120, 200};

    private final Screen parent;
    private final List<List<Row>> tabs = new ArrayList<>();
    private final List<String> microphones = new ArrayList<>();
    private final List<String> speakers = new ArrayList<>();
    private int tab;
    private int px, py, pw, ph;
    private int listY, listH;
    private float scroll;
    private Row dragging;
    private float shownLevel;
    private float peak;
    private long peakTime;
    /** Simple Voice Chat carries the voices, or the server runs none: only a card saying where to go. */
    private boolean managed;

    public VoiceSettingsScreen(Screen parent) {
        super(Component.translatable("caracrea.voice.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        microphones.clear();
        microphones.add("");
        microphones.addAll(VoiceClient.microphones());
        speakers.clear();
        speakers.add("");
        speakers.addAll(VoiceClient.speakers());
        pw = Math.min(500, width - 24);
        ph = Math.min(330, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + 66;
        listH = py + ph - 40 - listY;
        managed = managed();
        if (managed) {
            initManaged();
            return;
        }

        CaraCreaConfig.Client c = CaraCreaConfig.CLIENT;
        tabs.clear();
        List<Row> general = new ArrayList<>();
        general.add(new Toggle("enabled", c.voiceChat::get, v -> set(c.voiceChat, v)));
        general.add(new Segments("mode", () -> new String[]{text("caracrea.voice.mode.ptt", VoiceClient.PUSH_TO_TALK.getTranslatedKeyMessage()),
                text("caracrea.voice.mode.activation")}, () -> c.pushToTalk.get() ? 0 : 1, i -> set(c.pushToTalk, i == 0)));
        general.add(new Toggle("auto_activation", c.automaticActivation::get, v -> set(c.automaticActivation, v)) {
            @Override
            boolean shown() {
                return !c.pushToTalk.get();
            }
        });
        general.add(new Threshold());
        general.add(new Toggle("mute", c.muted::get, v -> set(c.muted, v)) {
            @Override
            String hint() {
                return text("caracrea.voice.mute.hint", VoiceClient.MUTE.getTranslatedKeyMessage());
            }
        });
        // Couper tout le monde : on n'entend plus aucune voix, les autres nous entendent toujours.
        general.add(new Toggle("mute_all", c.deafened::get, v -> set(c.deafened, v)) {
            @Override
            String hint() {
                return text("caracrea.voice.mute_all.hint", keyName(VoiceClient.MUTE_ALL));
            }
        });
        // Mode de voix (même ordre que VoiceProtocol.MODE_* : chuchoter, parler, crier).
        general.add(new Segments("voice_mode", () -> new String[]{text(VoiceClient.modeKey(VoiceProtocol.MODE_WHISPER)),
                text(VoiceClient.modeKey(VoiceProtocol.MODE_TALK)), text(VoiceClient.modeKey(VoiceProtocol.MODE_SHOUT))},
                VoiceClient::voiceMode, VoiceClient::setVoiceMode) {
            @Override
            String hint() {
                return text("caracrea.voice.voice_mode.hint", keyName(VoiceClient.MODE));
            }
        });
        general.add(new Toggle("icons", c.icons::get, v -> set(c.icons, v)));
        tabs.add(general);

        List<Row> micro = new ArrayList<>();
        micro.add(new Choice("microphone", microphones, c.microphone));
        micro.add(new Info("capture", () -> VoiceClient.microphoneUnavailable() ? text("caracrea.voice.capture.none")
                : VoiceClient.microphoneKind().isEmpty() ? "—" : VoiceClient.microphoneKind(),
                () -> VoiceClient.microphoneUnavailable() ? Ui.DANGER : Ui.TEXT));
        micro.add(new Slider("gain", c.micGain, 4.0));
        micro.add(new Toggle("agc", c.automaticGain::get, v -> set(c.automaticGain, v)));
        micro.add(new Segments("denoise", () -> new String[]{text("caracrea.voice.denoise.off"), text("caracrea.voice.denoise.light"),
                text("caracrea.voice.denoise.normal"), text("caracrea.voice.denoise.strong")},
                () -> c.denoise.get() ? c.denoiseStrength.get() : 0, i -> {
            set(c.denoise, i > 0);
            if (i > 0) {
                set(c.denoiseStrength, i);
            }
        }));
        micro.add(new Toggle("test", VoiceClient::testing, VoiceClient::setTesting));
        micro.add(new MicTest());
        tabs.add(micro);

        List<Row> sound = new ArrayList<>();
        sound.add(new Choice("speaker", speakers, c.speaker));
        sound.add(new Slider("volume", c.voiceVolume, 2.0));
        sound.add(new Info("output", () -> text(switch (VoiceClient.outputKind()) {
            case "3D (HRTF)" -> "caracrea.voice.output.hrtf";
            case "3D" -> "caracrea.voice.output.3d";
            case "stereo" -> "caracrea.voice.output.stereo";
            case "none" -> "caracrea.voice.output.none";
            default -> "caracrea.voice.output.off";
        }), () -> Ui.TEXT));
        sound.add(new Toggle("occlusion", c.occlusion::get, v -> set(c.occlusion, v)));
        sound.add(new Segments("buffer", () -> new String[]{text("caracrea.voice.buffer.auto"), "40 ms", "60 ms", "80 ms", "120 ms", "200 ms"},
                () -> indexOf(BUFFERS, c.jitterBufferMs.get()), i -> set(c.jitterBufferMs, BUFFERS[i])));
        sound.add(new Action("test_sound", "caracrea.voice.test_sound.play", VoiceClient::testSound, VoiceClient::active));
        tabs.add(sound);

        List<Row> network = new ArrayList<>();
        network.add(new Segments("transport", () -> new String[]{text("caracrea.voice.transport.auto"), text("caracrea.voice.transport.tcp")},
                () -> c.transport.get() == CaraCreaConfig.Transport.TCP ? 1 : 0,
                i -> set(c.transport, i == 1 ? CaraCreaConfig.Transport.TCP : CaraCreaConfig.Transport.AUTO)));
        network.add(new Info("state", () -> text(switch (VoiceClient.transportState()) {
            case "UDP" -> "caracrea.voice.state.udp";
            case "CONNECTING" -> "caracrea.voice.state.connecting";
            case "TCP" -> "caracrea.voice.state.tcp";
            default -> "caracrea.voice.state.off";
        }), () -> switch (VoiceClient.transportState()) {
            case "UDP" -> Ui.OK;
            case "CONNECTING", "TCP" -> Ui.AMBRE;
            default -> Ui.MUTED;
        }));
        network.add(new Info("ping", () -> VoiceClient.rttMs() < 0 ? "—" : VoiceClient.rttMs() + " ms", () -> Ui.TEXT));
        network.add(new Info("loss", () -> String.format(Locale.FRANCE, "%.1f %%", VoiceClient.lossPermille() / 10.0),
                () -> VoiceClient.lossPermille() > 50 ? Ui.AMBRE : Ui.TEXT));
        network.add(new Info("buffer_now", () -> VoiceClient.bufferMs() + " ms" + (c.jitterBufferMs.get() == 0
                ? " (" + text("caracrea.voice.buffer.auto").toLowerCase(Locale.ROOT) + ")" : ""), () -> Ui.TEXT));
        network.add(new Info("rates", () -> String.format(Locale.FRANCE, "↑ %.0f kbit/s · ↓ %.0f kbit/s", VoiceClient.kbpsUp(),
                VoiceClient.kbpsDown()), () -> Ui.TEXT));
        tabs.add(network);

        int accent = Ui.accent();
        int by = py + ph - 28;
        addRenderableWidget(new HzButton(px + Ui.MARGE, by, 130, 18, Component.translatable("caracrea.voice.volumes.open"), accent,
                () -> minecraft.setScreen(new VoiceVolumesScreen(this))));
        if (VoiceClient.groupsAllowed()) {
            addRenderableWidget(new HzButton(px + Ui.MARGE + 136, by, 90, 18, Component.translatable("caracrea.voice.groups.open"), accent,
                    () -> minecraft.setScreen(new VoiceGroupsScreen(this))));
        }
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - 100, by, 100, 18, Component.translatable("gui.done"), accent, this::onClose)
                .look(HzButton.Look.PRIMARY));
        scroll = 0;
    }

    /** Whether this screen only points to where the voices are handled (no microphone test, no device here). */
    private static boolean managed() {
        return VoiceClient.svcActive() || !VoiceClient.serverSupported();
    }

    private void initManaged() {
        int accent = Ui.accent();
        if (VoiceClient.otherVoiceChat()) {
            addRenderableWidget(new HzButton(px + pw / 2 - 110, py + 170, 220, 20, Component.translatable("caracrea.voice.managed.open_svc"), accent,
                    this::openSvc).look(HzButton.Look.PRIMARY));
        }
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - 100, py + ph - 28, 100, 18, Component.translatable("gui.done"), accent, this::onClose));
    }

    /** Opens Simple Voice Chat's own settings (its public screen), or its main menu. */
    private void openSvc() {
        for (String name : new String[]{"de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen", "de.maxhenkel.voicechat.gui.VoiceChatScreen"}) {
            try {
                Class<?> type = Class.forName(name);
                Screen screen;
                try {
                    screen = (Screen) type.getConstructor(Screen.class).newInstance(parent);
                } catch (NoSuchMethodException e) {
                    screen = (Screen) type.getConstructor().newInstance();
                }
                minecraft.setScreen(screen);
                return;
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                // Try the next one.
            }
        }
    }

    private void renderManaged(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean svc = VoiceClient.svcActive() || VoiceClient.otherVoiceChat();
        VoiceUi.panel(g, font, width, height, px, py, pw, ph, title.getString(), "");
        VoiceUi.close(g, px + pw - Ui.MARGE - 7, py + 14, mouseX, mouseY);
        int cardX = px + Ui.MARGE;
        int cardW = pw - 2 * Ui.MARGE;
        Ui.soft(g, cardX, py + 70, cardW, 130, Ui.argb(0xFFFFFF, 6));
        Ui.centered(g, font, Ui.tr(Ui.BOLD, svc ? "caracrea.voice.managed.svc" : "caracrea.voice.managed.off"), px + pw / 2f, py + 100,
                Ui.argb(0xFFFFFF, 255));
        String hint = Component.translatable(svc ? "caracrea.voice.managed.svc.hint" : "caracrea.voice.managed.off.hint").getString();
        int y = py + 120;
        for (var line : font.split(Ui.lit(hint, Ui.FONT), cardW - 40)) {
            g.drawString(font, line, (int) (px + pw / 2f - font.width(line) / 2f), y, Ui.argb(Ui.MUTED, 255), false);
            y += 11;
        }
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + ph - 36, 0xFFFFFF, 30, true, true);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** Nom de la touche, ou « à assigner (Contrôles) » quand elle n'en a pas. */
    private static String keyName(KeyMapping key) {
        return key.isUnbound() ? text("caracrea.voice.key.unbound") : key.getTranslatedKeyMessage().getString();
    }

    private List<Row> visible() {
        List<Row> list = new ArrayList<>();
        for (Row row : tabs.get(tab)) {
            if (row.shown()) {
                list.add(row);
            }
        }
        return list;
    }

    private int contentHeight() {
        int h = 0;
        for (Row row : visible()) {
            h += row.height() + 3;
        }
        return h;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void tick() {
        float level = VoiceClient.micLevel();
        shownLevel = level > shownLevel ? level : shownLevel + (level - shownLevel) * 0.35F;
        float db = VoiceUi.scale(VoiceClient.micLevelDb());
        long now = System.currentTimeMillis();
        if (db >= peak || now - peakTime > 1200) {
            peak = db;
            peakTime = now;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (managed != managed()) {
            rebuildWidgets();
        }
        if (managed) {
            renderManaged(g, mouseX, mouseY, partialTick);
            return;
        }
        VoiceUi.panel(g, font, width, height, px, py, pw, ph, title.getString(), status());
        status(g);
        VoiceUi.close(g, px + pw - Ui.MARGE - 7, py + 14, mouseX, mouseY);
        // Tabs.
        int tx = px + Ui.MARGE;
        int accent = Ui.accent();
        for (int i = 0; i < TABS.length; i++) {
            String name = text("caracrea.voice.tab." + TABS[i]);
            int w = (int) Ui.spacedWidth(font, name.toUpperCase(Locale.ROOT), Ui.BOLD, 1.0f);
            boolean over = VoiceUi.inside(mouseX, mouseY, tx - 4, py + 44, w + 8, 16);
            Ui.spaced(g, font, name, Ui.BOLD, tx, py + 48, Ui.argb(i == tab ? 0xFFFFFF : over ? Ui.TEXT : Ui.MUTED, 255), 1.0f);
            if (i == tab) {
                g.fill(tx, py + 58, tx + w, py + 59, Ui.argb(accent, 230));
            }
            tx += w + 18;
        }
        // Rows.
        int max = Math.max(0, contentHeight() - listH);
        scroll = Mth.clamp(scroll, 0, max);
        boolean inList = VoiceUi.inside(mouseX, mouseY, px, listY, pw, listH);
        int mx = inList || dragging != null ? mouseX : -1000;
        int my = inList || dragging != null ? mouseY : -1000;
        g.enableScissor(px, listY, px + pw, listY + listH);
        int y = listY - Math.round(scroll);
        int x = px + Ui.MARGE;
        int w = pw - 2 * Ui.MARGE - (max > 0 ? 6 : 0);
        for (Row row : visible()) {
            if (y + row.height() > listY && y < listY + listH) {
                row.render(g, x, y, w, mx, my);
            }
            y += row.height() + 3;
        }
        g.disableScissor();
        if (max > 0) {
            int thumb = Math.max(14, listH * listH / (listH + max));
            int thumbY = listY + Math.round((listH - thumb) * scroll / max);
            g.fill(px + pw - Ui.MARGE - 2, listY, px + pw - Ui.MARGE, listY + listH, Ui.argb(0xFFFFFF, 14));
            g.fill(px + pw - Ui.MARGE - 2, thumbY, px + pw - Ui.MARGE, thumbY + thumb, Ui.argb(0xFFFFFF, 90));
        }
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + ph - 36, 0xFFFFFF, 30, true, true);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** One line under the title: what the voice chat does right now. */
    private String status() {
        if (!VoiceClient.serverSupported()) {
            return text(VoiceClient.otherVoiceChat() ? "caracrea.voice.status.other" : "caracrea.voice.status.server_off");
        }
        if (!CaraCreaConfig.CLIENT.voiceChat.get()) {
            return text("caracrea.voice.status.off");
        }
        if (VoiceClient.microphoneUnavailable()) {
            return text("caracrea.voice.status.no_mic");
        }
        return text("caracrea.voice.status.on");
    }

    /** Status chip at the right of the title. */
    private void status(GuiGraphics g) {
        String label;
        int color;
        if (!VoiceClient.serverSupported()) {
            label = text(VoiceClient.otherVoiceChat() ? "caracrea.voice.chip.svc" : "caracrea.voice.chip.off");
            color = Ui.MUTED;
        } else if (!CaraCreaConfig.CLIENT.voiceChat.get()) {
            label = text("caracrea.voice.chip.disabled");
            color = Ui.DANGER;
        } else {
            switch (VoiceClient.transportState()) {
                case "UDP" -> {
                    int rtt = VoiceClient.rttMs();
                    label = "UDP" + (rtt >= 0 ? " · " + rtt + " ms" : "");
                    color = Ui.OK;
                }
                case "CONNECTING" -> {
                    label = text("caracrea.voice.chip.connecting");
                    color = Ui.AMBRE;
                }
                default -> {
                    label = text("caracrea.voice.chip.tcp");
                    color = Ui.AMBRE;
                }
            }
        }
        int tw = (int) Ui.spacedWidth(font, title.getString().toUpperCase(Locale.ROOT), Ui.BOLD, 1.6f);
        Ui.chip(g, font, label, px + Ui.MARGE + tw + 10, py + 13, color);
    }

    // ---------------------------------------------------------------- rows

    private abstract class Row {
        final String key;

        Row(String key) {
            this.key = key;
        }

        boolean shown() {
            return true;
        }

        int height() {
            return VoiceUi.ROW;
        }

        Component name() {
            return Component.translatable("caracrea.voice." + key);
        }

        String hint() {
            String k = "caracrea.voice." + key + ".hint";
            String t = Component.translatable(k).getString();
            return t.equals(k) ? "" : t;
        }

        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            boolean hover = VoiceUi.inside(mouseX, mouseY, x, y, w, height());
            VoiceUi.rowBackground(g, x, y, w, height(), hover);
            VoiceUi.label(g, font, x, y, labelWidth(w), name(), hint(), hover);
        }

        int labelWidth(int w) {
            return w / 2 - 8;
        }

        boolean click(double mx, double my, int x, int y, int w) {
            return false;
        }

        void drag(double mx, int x, int w) {
        }

        int controlX(int x, int w) {
            return x + w / 2 + 8;
        }

        int controlW(int x, int w) {
            return x + w - 8 - controlX(x, w);
        }
    }

    private class Toggle extends Row {
        final BooleanSupplier value;
        final Consumer<Boolean> change;

        Toggle(String key, BooleanSupplier value, Consumer<Boolean> change) {
            super(key);
            this.value = value;
            this.change = change;
        }

        @Override
        int labelWidth(int w) {
            return w - 90;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            super.render(g, x, y, w, mouseX, mouseY);
            VoiceUi.toggle(g, font, x + w, y, height(), value.getAsBoolean(), VoiceUi.inside(mouseX, mouseY, x, y, w, height()));
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            change.accept(!value.getAsBoolean());
            SonsUi.clic(0.6F);
            return true;
        }
    }

    private class Segments extends Row {
        final Supplier<String[]> labels;
        final IntSupplier selected;
        final IntConsumer change;

        Segments(String key, Supplier<String[]> labels, IntSupplier selected, IntConsumer change) {
            super(key);
            this.labels = labels;
            this.selected = selected;
            this.change = change;
        }

        @Override
        int controlX(int x, int w) {
            return x + w * 2 / 5;
        }

        @Override
        int labelWidth(int w) {
            return w * 2 / 5 - 12;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            super.render(g, x, y, w, mouseX, mouseY);
            VoiceUi.segments(g, font, controlX(x, w), y + 5, controlW(x, w), height() - 10, labels.get(), selected.getAsInt(), mouseX, mouseY);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            int cx = controlX(x, w);
            int cw = controlW(x, w);
            if (mx < cx || mx >= cx + cw) {
                return false;
            }
            int n = labels.get().length;
            int index = Mth.clamp((int) ((mx - cx) * n / cw), 0, n - 1);
            change.accept(index);
            SonsUi.clic(0.6F);
            return true;
        }
    }

    private class Slider extends Row {
        final ForgeConfigSpec.DoubleValue value;
        final double max;

        Slider(String key, ForgeConfigSpec.DoubleValue value, double max) {
            super(key);
            this.value = value;
            this.max = max;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            super.render(g, x, y, w, mouseX, mouseY);
            int cx = controlX(x, w);
            int cw = controlW(x, w) - 46;
            boolean hover = VoiceUi.inside(mouseX, mouseY, cx - 4, y, cw + 8, height()) || dragging == this;
            VoiceUi.slider(g, cx, y + height() / 2 - 1, cw, (float) (value.get() / max), hover);
            Ui.right(g, font, Ui.lit(Math.round(value.get() * 100) + " %", Ui.BOLD), x + w - 8, y + (height() - 8) / 2f, Ui.argb(0xFFFFFF, 255));
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (mx < controlX(x, w) - 6) {
                return false;
            }
            dragging = this;
            drag(mx, x, w);
            return true;
        }

        @Override
        void drag(double mx, int x, int w) {
            double t = Mth.clamp((mx - controlX(x, w)) / (controlW(x, w) - 46), 0, 1) * max;
            double stepped = Math.round(t * 20) / 20.0;
            value.set(Math.abs(stepped - 1) < 0.051 ? 1 : stepped);
        }
    }

    /** A device chosen with arrows. */
    private class Choice extends Row {
        final List<String> options;
        final ForgeConfigSpec.ConfigValue<String> value;

        Choice(String key, List<String> options, ForgeConfigSpec.ConfigValue<String> value) {
            super(key);
            this.options = options;
            this.value = value;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            super.render(g, x, y, w, mouseX, mouseY);
            int cx = controlX(x, w);
            int cw = controlW(x, w);
            int cy = y + 5;
            int ch = height() - 10;
            String current = value.get();
            String name = current.isEmpty() ? text("caracrea.voice.device.default") : current;
            boolean left = VoiceUi.inside(mouseX, mouseY, cx, cy, 16, ch);
            boolean right = VoiceUi.inside(mouseX, mouseY, cx + cw - 16, cy, 16, ch);
            Ui.soft(g, cx, cy, cw, ch, Ui.argb(0xFFFFFF, 10));
            Ui.softOutline(g, cx + 1, cy + 1, cw - 2, ch - 2, Ui.argb(0xFFFFFF, 30));
            arrow(g, cx + 6, cy + ch / 2, true, left ? Ui.argb(Ui.accent(), 255) : Ui.argb(Ui.MUTED, 255));
            arrow(g, cx + cw - 10, cy + ch / 2, false, right ? Ui.argb(Ui.accent(), 255) : Ui.argb(Ui.MUTED, 255));
            Ui.centered(g, font, Ui.lit(Ui.ellipsis(font, name, Ui.FONT, cw - 32), Ui.FONT), cx + cw / 2f, cy + (ch - 8) / 2f,
                    Ui.argb(Ui.TEXT, 255));
        }

        private void arrow(GuiGraphics g, int x, int cy, boolean left, int color) {
            for (int i = 0; i < 4; i++) {
                int px = left ? x + i : x + 3 - i;
                g.fill(px, cy - i - 1, px + 1, cy + i + 1, color);
            }
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            int cx = controlX(x, w);
            int cw = controlW(x, w);
            if (mx < cx || options.isEmpty()) {
                return false;
            }
            int index = Math.max(0, options.indexOf(value.get()));
            index = Math.floorMod(index + (mx < cx + cw / 2 ? -1 : 1), options.size());
            set(value, options.get(index));
            SonsUi.clic(0.6F);
            return true;
        }
    }

    private class Info extends Row {
        final Supplier<String> value;
        final IntSupplier color;

        Info(String key, Supplier<String> value, IntSupplier color) {
            super(key);
            this.value = value;
            this.color = color;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            super.render(g, x, y, w, mouseX, mouseY);
            String v = Ui.ellipsis(font, value.get(), Ui.BOLD, controlW(x, w));
            Ui.right(g, font, Ui.lit(v, Ui.BOLD), x + w - 8, y + (height() - 8) / 2f, Ui.argb(color.getAsInt(), 255));
        }
    }

    private class Action extends Row {
        final String button;
        final Runnable action;
        final BooleanSupplier enabled;

        Action(String key, String button, Runnable action, BooleanSupplier enabled) {
            super(key);
            this.button = button;
            this.action = action;
            this.enabled = enabled;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            super.render(g, x, y, w, mouseX, mouseY);
            int bw = 90;
            int bx = x + w - 8 - bw;
            int by = y + 5;
            boolean on = enabled.getAsBoolean();
            boolean over = on && VoiceUi.inside(mouseX, mouseY, bx, by, bw, height() - 10);
            Ui.soft(g, bx, by, bw, height() - 10, Ui.argb(0xFFFFFF, !on ? 4 : over ? 18 : 7));
            Ui.softOutline(g, bx + 1, by + 1, bw - 2, height() - 12, over ? Ui.argb(Ui.accent(), 230) : Ui.argb(0xFFFFFF, on ? 46 : 20));
            Ui.spacedCentered(g, font, text(button), Ui.BOLD, bx + bw / 2f, by + (height() - 18) / 2f,
                    on ? 0xFFFFFFFF : Ui.argb(Ui.FAINT, 255), 1.0f);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (mx >= x + w - 98 && enabled.getAsBoolean()) {
                action.run();
                SonsUi.clic(0.6F);
                return true;
            }
            return false;
        }
    }

    /** The microphone level, with the threshold to drag in voice activation with a set threshold. */
    private class Threshold extends Row {
        Threshold() {
            super("threshold");
        }

        private boolean manual() {
            return !CaraCreaConfig.CLIENT.pushToTalk.get() && !CaraCreaConfig.CLIENT.automaticActivation.get();
        }

        @Override
        Component name() {
            return Component.translatable(manual() ? "caracrea.voice.threshold" : "caracrea.voice.level");
        }

        @Override
        String hint() {
            return text(manual() ? "caracrea.voice.threshold.hint" : "caracrea.voice.level.hint");
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            super.render(g, x, y, w, mouseX, mouseY);
            float threshold = CaraCreaConfig.CLIENT.activationThreshold.get().floatValue();
            boolean passes = CaraCreaConfig.CLIENT.pushToTalk.get() ? VoiceClient.transmitting()
                    : manual() ? shownLevel >= threshold : VoiceClient.voiceDetected();
            VoiceUi.meter(g, controlX(x, w), y + 9, controlW(x, w), 8, shownLevel, passes, manual() ? threshold : -1, -1);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (!manual() || mx < controlX(x, w) - 4) {
                return false;
            }
            dragging = this;
            drag(mx, x, w);
            return true;
        }

        @Override
        void drag(double mx, int x, int w) {
            CaraCreaConfig.CLIENT.activationThreshold.set((double) Mth.clamp((float) (mx - controlX(x, w)) / controlW(x, w), 0, 1));
        }
    }

    /** The microphone test: a large meter in dB with the peak, the background level and whether a voice is heard. */
    private class MicTest extends Row {
        MicTest() {
            super("mic_test");
        }

        @Override
        int height() {
            return 54;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            boolean hover = VoiceUi.inside(mouseX, mouseY, x, y, w, height());
            VoiceUi.rowBackground(g, x, y, w, height(), hover);
            VoiceUi.label(g, font, x, y, w - 16, name(), text(VoiceClient.testing() ? "caracrea.voice.mic_test.on" : "caracrea.voice.mic_test.hint"),
                    hover);
            float level = VoiceUi.scale(VoiceClient.micLevelDb());
            float floor = VoiceUi.scale(VoiceClient.micFloorDb());
            boolean voice = VoiceClient.voiceDetected();
            int mx = x + 8;
            int mw = w - 16;
            VoiceUi.meter(g, mx, y + 28, mw, 10, level, voice, -1, floor);
            int px2 = mx + Math.round(mw * peak);
            g.fill(px2 - 1, y + 27, px2, y + 39, Ui.argb(0xFFFFFF, 200));
            for (int db = -60; db <= 0; db += 10) {
                int tx = mx + Math.round(mw * VoiceUi.scale(db));
                g.fill(tx, y + 39, tx + 1, y + 41, Ui.argb(0xFFFFFF, 50));
            }
            String readout = String.format(Locale.FRANCE, "%s %.0f dB · %s %.0f dB · %s", text("caracrea.voice.mic_test.level"),
                    Math.max(-99, VoiceClient.micLevelDb()), text("caracrea.voice.mic_test.floor"), Math.max(-99, VoiceClient.micFloorDb()),
                    text(voice ? "caracrea.voice.mic_test.voice" : "caracrea.voice.mic_test.silence"));
            g.drawString(font, Ui.lit(Ui.ellipsis(font, readout, Ui.SMALL, mw), Ui.SMALL), mx, y + 43,
                    Ui.argb(voice ? Ui.OK : Ui.MUTED, 255), false);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (VoiceUi.inside(mx, my, px + pw - Ui.MARGE - 10, py + 11, 13, 13)) {
            SonsUi.clic(0.6F);
            onClose();
            return true;
        }
        if (managed) {
            return false;
        }
        int tx = px + Ui.MARGE;
        for (int i = 0; i < TABS.length; i++) {
            int w = (int) Ui.spacedWidth(font, text("caracrea.voice.tab." + TABS[i]).toUpperCase(Locale.ROOT), Ui.BOLD, 1.0f);
            if (VoiceUi.inside(mx, my, tx - 4, py + 44, w + 8, 16)) {
                if (tab != i) {
                    tab = i;
                    scroll = 0;
                    SonsUi.clic(0.6F);
                }
                return true;
            }
            tx += w + 18;
        }
        if (VoiceUi.inside(mx, my, px, listY, pw, listH)) {
            int y = listY - Math.round(scroll);
            int x = px + Ui.MARGE;
            int w = pw - 2 * Ui.MARGE - (contentHeight() > listH ? 6 : 0);
            for (Row row : visible()) {
                if (my >= y && my < y + row.height()) {
                    return row.click(mx, my, x, y, w);
                }
                y += row.height() + 3;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(mx, px + Ui.MARGE, pw - 2 * Ui.MARGE - (contentHeight() > listH ? 6 : 0));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            CaraCreaConfig.CLIENT_SPEC.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (float) delta * 20, 0, Math.max(0, contentHeight() - listH));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB && !managed) {
            tab = (tab + 1) % TABS.length;
            scroll = 0;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** Opens on a given tab (0 general, 1 microphone, 2 sound, 3 network). */
    public VoiceSettingsScreen tab(int index) {
        tab = Mth.clamp(index, 0, TABS.length - 1);
        return this;
    }

    private static <T> void set(ForgeConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        CaraCreaConfig.CLIENT_SPEC.save();
    }

    @Override
    public void onClose() {
        VoiceClient.stopTests();
        CaraCreaConfig.CLIENT_SPEC.save();
        minecraft.setScreen(parent);
    }

    /** Also when another screen replaces this one (volumes, groups, Simple Voice Chat): no test keeps running. */
    @Override
    public void removed() {
        VoiceClient.stopTests();
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
