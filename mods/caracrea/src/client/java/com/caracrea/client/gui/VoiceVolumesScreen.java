package com.caracrea.client.gui;

import com.caracrea.client.CompositeSkins;
import com.caracrea.client.gui.kit.Echelle;
import com.caracrea.client.gui.kit.HzButton;
import com.caracrea.client.gui.kit.SonsUi;
import com.caracrea.client.gui.kit.Ui;
import com.caracrea.client.voice.TabIcons;
import com.caracrea.client.voice.VoiceVolumes;
import com.caracrea.voice.VoiceActivity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The volume of each player's voice for you (0 to 200 %), and a button to silence someone completely. Kept on your
 * computer, across sessions. HeraliZ interface style.
 */
public class VoiceVolumesScreen extends Screen {
    private static final int ROW = 24;

    private final Screen parent;
    private final List<PlayerInfo> players = new ArrayList<>();
    private int px, py, pw, ph, listY, listH;
    private float scroll;
    private UUID dragging;

    public VoiceVolumesScreen(Screen parent) {
        super(Component.translatable("caracrea.voice.volumes.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        players.clear();
        if (minecraft.getConnection() != null && minecraft.player != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (!info.getProfile().getId().equals(minecraft.player.getUUID())) {
                    players.add(info);
                }
            }
        }
        players.sort(Comparator.comparing(info -> info.getProfile().getName().toLowerCase(java.util.Locale.ROOT)));
        pw = Math.min(440, width - 24);
        ph = Math.min(320, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + 48;
        listH = py + ph - 40 - listY;
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - 100, py + ph - 28, 100, 18, Component.translatable("gui.done"), Ui.accent(),
                this::onClose).look(HzButton.Look.PRIMARY));
    }

    private int maxScroll() {
        return Math.max(0, players.size() * (ROW + 3) - listH);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VoiceUi.panel(g, font, width, height, px, py, pw, ph, title.getString(), Component.translatable("caracrea.voice.volumes.hint").getString());
        VoiceUi.close(g, px + pw - Ui.MARGE - 7, py + 14, mouseX, mouseY);
        if (players.isEmpty()) {
            Ui.centered(g, font, Ui.tr(Ui.FONT, "caracrea.voice.volumes.empty"), px + pw / 2f, listY + 20, Ui.argb(Ui.MUTED, 255));
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        boolean inList = VoiceUi.inside(mouseX, mouseY, px, listY, pw, listH);
        int mx = inList || dragging != null ? mouseX : -1000;
        int my = inList || dragging != null ? mouseY : -1000;
        g.enableScissor(px, listY, px + pw, listY + listH);
        int y = listY - Math.round(scroll);
        for (PlayerInfo info : players) {
            if (y + ROW > listY && y < listY + listH) {
                row(g, info, px + Ui.MARGE, y, pw - 2 * Ui.MARGE, mx, my);
            }
            y += ROW + 3;
        }
        g.disableScissor();
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + ph - 36, 0xFFFFFF, 30, true, true);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int sliderX(int x, int w) {
        return x + Math.min(150, w / 3) + 30;
    }

    private int sliderW(int x, int w) {
        return x + w - 8 - 44 - 46 - sliderX(x, w);
    }

    private void row(GuiGraphics g, PlayerInfo info, int x, int y, int w, int mouseX, int mouseY) {
        UUID uuid = info.getProfile().getId();
        boolean hover = VoiceUi.inside(mouseX, mouseY, x, y, w, ROW);
        VoiceUi.rowBackground(g, x, y, w, ROW, hover);
        boolean talking = VoiceActivity.isTalking(uuid);
        ResourceLocation skin = CompositeSkins.skinFor(uuid);
        Ui.soft(g, x + 5, y + 4, 16, 16, talking ? Ui.argb(Ui.OK, 255) : Ui.argb(0xFFFFFF, 30));
        g.blit(skin, x + 6, y + 5, 14, 14, 32, 32, 32, 32, 256, 256);
        g.blit(skin, x + 6, y + 5, 14, 14, 160, 32, 32, 32, 256, 256);
        String name = Ui.ellipsis(font, info.getProfile().getName(), Ui.FONT, Math.min(150, w / 3) - 8);
        g.drawString(font, Ui.lit(name, Ui.FONT), x + 26, y + (ROW - 8) / 2, Ui.argb(Ui.TEXT, 255), false);
        int state = TabIcons.state(uuid);
        if (state >= 0) {
            int nx = x + 28 + font.width(Ui.lit(name, Ui.FONT));
            g.blit(TabIcons.TEXTURE, nx, y + 8, 8, 8, state * 16, 0, 16, 16, TabIcons.COUNT * 16, 16);
        }
        float volume = VoiceVolumes.get(uuid);
        int sx = sliderX(x, w);
        int sw = sliderW(x, w);
        VoiceUi.slider(g, sx, y + ROW / 2 - 1, sw, volume / 2, hover && VoiceUi.inside(mouseX, mouseY, sx - 4, y, sw + 8, ROW) || uuid.equals(dragging));
        Ui.right(g, font, Ui.lit(Math.round(volume * 100) + " %", Ui.BOLD), sx + sw + 42, y + (ROW - 8) / 2f,
                volume == 0 ? Ui.argb(Ui.DANGER, 255) : Ui.argb(0xFFFFFF, 255));
        int bx = x + w - 8 - 44;
        boolean muted = volume == 0;
        boolean over = VoiceUi.inside(mouseX, mouseY, bx, y + 4, 44, ROW - 8);
        Ui.soft(g, bx, y + 4, 44, ROW - 8, muted ? Ui.argb(Ui.DANGER, over ? 120 : 80) : Ui.argb(0xFFFFFF, over ? 18 : 7));
        Ui.softOutline(g, bx + 1, y + 5, 42, ROW - 10, muted ? Ui.argb(Ui.DANGER, 200) : over ? Ui.argb(Ui.accent(), 230) : Ui.argb(0xFFFFFF, 46));
        Ui.spacedCentered(g, font, Component.translatable(muted ? "caracrea.voice.volumes.unmute" : "caracrea.voice.volumes.mute").getString(),
                Ui.SMALL, bx + 22, y + (ROW - 6) / 2f, 0xFFFFFFFF, 0.6f);
    }

    private PlayerInfo at(double my) {
        int index = (int) Math.floor((my - listY + scroll) / (ROW + 3));
        return index >= 0 && index < players.size() ? players.get(index) : null;
    }

    private void setFrom(UUID uuid, double mx) {
        int x = px + Ui.MARGE;
        int w = pw - 2 * Ui.MARGE;
        float value = (float) Mth.clamp((mx - sliderX(x, w)) / sliderW(x, w), 0, 1) * 2;
        float stepped = Math.round(value * 20) / 20F;
        VoiceVolumes.set(uuid, Math.abs(stepped - 1) < 0.051F ? 1 : stepped);
    }

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
        if (VoiceUi.inside(mx, my, px, listY, pw, listH)) {
            PlayerInfo info = at(my);
            if (info != null) {
                UUID uuid = info.getProfile().getId();
                if (mx >= px + pw - Ui.MARGE - 8 - 44) {
                    VoiceVolumes.set(uuid, VoiceVolumes.get(uuid) == 0 ? 1 : 0);
                    SonsUi.clic(0.6F);
                } else if (mx >= sliderX(px + Ui.MARGE, pw - 2 * Ui.MARGE) - 6) {
                    dragging = uuid;
                    setFrom(uuid, mx);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            setFrom(dragging, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (float) delta * 20, 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        VoiceVolumes.save();
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
