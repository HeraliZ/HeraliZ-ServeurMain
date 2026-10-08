package com.caracrea.client.gui;

import com.caracrea.client.CompositeSkins;
import com.caracrea.client.gui.kit.Echelle;
import com.caracrea.client.gui.kit.HzButton;
import com.caracrea.client.gui.kit.HzField;
import com.caracrea.client.gui.kit.SonsUi;
import com.caracrea.client.gui.kit.Ui;
import com.caracrea.client.voice.VoiceClient;
import com.caracrea.network.CaraCreaNetwork.GroupInfo;
import com.caracrea.voice.VoiceActivity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Voice groups: talk to your group from anywhere. Shows your group and its members, or the groups to join (with their
 * password if locked) and a form to create one: name, optional password, and kind (normal: the group keeps its words
 * to itself; open: the players around hear you too; isolated: you only hear your group). HeraliZ interface style.
 */
public class VoiceGroupsScreen extends Screen {
    private static final int ROW = 24;
    private static final String[] TYPES = {"normal", "open", "isolated"};

    private final Screen parent;
    private int px, py, pw, ph, listY, listH;
    private HzField name;
    private HzField password;
    private HzButton create;
    private HzButton leave;
    private int type;
    private float scroll;
    private boolean wasInGroup;

    public VoiceGroupsScreen(Screen parent) {
        super(Component.translatable("caracrea.voice.groups.title"));
        this.parent = parent;
    }

    private boolean inGroup() {
        return minecraft.player != null && VoiceClient.groupOf(minecraft.player.getUUID()) != 0;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(440, width - 24);
        ph = Math.min(320, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listY = py + 48;
        listH = py + ph - 40 - 84 - listY;
        String keptName = name == null ? "" : name.getValue();
        String keptPassword = password == null ? "" : password.getValue();
        int fx = px + Ui.MARGE + 90;
        int fw = pw - 2 * Ui.MARGE - 90;
        int fy = listY + listH + 10;
        int accent = Ui.accent();
        name = addRenderableWidget(new HzField(fx, fy, fw, 16, 24, HzField.TOUT).accent(accent)
                .placeholder(Component.translatable("caracrea.voice.groups.name.hint").getString()).remplacerAuFocus(false));
        name.setValue(keptName);
        password = addRenderableWidget(new HzField(fx, fy + 22, fw, 16, 32, HzField.TOUT).accent(accent)
                .placeholder(Component.translatable("caracrea.voice.groups.password.hint").getString()).remplacerAuFocus(false));
        password.setValue(keptPassword);
        int by = py + ph - 28;
        create = addRenderableWidget(new HzButton(px + Ui.MARGE, by, 140, 18, Component.translatable("caracrea.voice.groups.create"), accent,
                this::create).look(HzButton.Look.PRIMARY));
        leave = addRenderableWidget(new HzButton(px + Ui.MARGE, by, 140, 18, Component.translatable("caracrea.voice.groups.leave"), accent,
                VoiceClient::leaveGroup).danger());
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - 100, by, 100, 18, Component.translatable("gui.done"), accent, this::onClose));
        update();
    }

    private void create() {
        if (!name.getValue().isBlank()) {
            VoiceClient.createGroup(name.getValue().trim(), password.getValue(), type);
        }
    }

    private void update() {
        boolean in = inGroup();
        name.visible = !in;
        password.visible = !in;
        create.visible = !in;
        create.active = !name.getValue().isBlank();
        leave.visible = in;
        wasInGroup = in;
    }

    @Override
    public void tick() {
        name.tick();
        password.tick();
        update();
    }

    private List<PlayerInfo> members(int group) {
        List<PlayerInfo> list = new ArrayList<>();
        if (minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (VoiceClient.groupOf(info.getProfile().getId()) == group) {
                    list.add(info);
                }
            }
        }
        return list;
    }

    private String typeName(int t) {
        return Component.translatable("caracrea.voice.group.type." + TYPES[Mth.clamp(t, 0, 2)]).getString();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean in = inGroup();
        UUID me = minecraft.player == null ? null : minecraft.player.getUUID();
        GroupInfo mine = in ? VoiceClient.group(VoiceClient.groupOf(me)) : null;
        String subtitle = mine != null ? Component.translatable("caracrea.voice.groups.yours", mine.name(), typeName(mine.type())).getString()
                : Component.translatable("caracrea.voice.groups.hint").getString();
        VoiceUi.panel(g, font, width, height, px, py, pw, ph, title.getString(), subtitle);
        VoiceUi.close(g, px + pw - Ui.MARGE - 7, py + 14, mouseX, mouseY);
        int x = px + Ui.MARGE;
        int w = pw - 2 * Ui.MARGE;
        if (in) {
            Ui.section(g, font, Component.translatable("caracrea.voice.groups.members").getString(), x, listY, w);
            int y = listY + 14;
            for (PlayerInfo member : members(VoiceClient.groupOf(me))) {
                UUID uuid = member.getProfile().getId();
                boolean talking = VoiceActivity.isTalking(uuid) || uuid.equals(me) && VoiceClient.transmitting();
                VoiceUi.rowBackground(g, x, y, w, ROW - 4, false);
                ResourceLocation skin = CompositeSkins.skinFor(uuid);
                Ui.soft(g, x + 4, y + 2, 16, 16, talking ? Ui.argb(Ui.OK, 255) : Ui.argb(0xFFFFFF, 30));
                g.blit(skin, x + 5, y + 3, 14, 14, 32, 32, 32, 32, 256, 256);
                g.blit(skin, x + 5, y + 3, 14, 14, 160, 32, 32, 32, 256, 256);
                g.drawString(font, Ui.lit(member.getProfile().getName(), Ui.FONT), x + 26, y + 6, Ui.argb(talking ? 0xFFFFFF : Ui.TEXT, 255), false);
                y += ROW;
                if (y > listY + listH + 70) {
                    break;
                }
            }
            if (mine != null) {
                String hint = Component.translatable("caracrea.voice.group.type." + TYPES[Mth.clamp(mine.type(), 0, 2)] + ".hint").getString();
                g.drawString(font, Ui.lit(Ui.ellipsis(font, hint, Ui.SMALL, w), Ui.SMALL), x, py + ph - 50, Ui.argb(Ui.MUTED, 255), false);
            }
        } else {
            List<GroupInfo> groups = VoiceClient.groups();
            Ui.section(g, font, Component.translatable("caracrea.voice.groups.list").getString(), x, listY, w);
            if (groups.isEmpty()) {
                Ui.centered(g, font, Ui.tr(Ui.FONT, "caracrea.voice.groups.none"), px + pw / 2f, listY + 30, Ui.argb(Ui.MUTED, 255));
            }
            int top = listY + 14;
            int height = listH - 14;
            scroll = Mth.clamp(scroll, 0, Math.max(0, groups.size() * ROW - height));
            g.enableScissor(px, top, px + pw, top + height);
            int y = top - Math.round(scroll);
            for (GroupInfo group : groups) {
                boolean hover = VoiceUi.inside(mouseX, mouseY, x, y, w, ROW - 4) && mouseY >= top && mouseY < top + height;
                VoiceUi.rowBackground(g, x, y, w, ROW - 4, hover);
                g.drawString(font, Ui.lit(Ui.ellipsis(font, group.name(), Ui.FONT, w / 2 - 12), Ui.FONT), x + 8, y + 6, Ui.argb(Ui.TEXT, 255), false);
                String info = Component.translatable("caracrea.voice.groups.info", typeName(group.type()), members(group.id()).size()).getString();
                g.drawString(font, Ui.lit(Ui.ellipsis(font, info, Ui.SMALL, w / 2 - 80), Ui.SMALL), x + w / 2, y + 7, Ui.argb(Ui.MUTED, 255), false);
                if (group.locked()) {
                    lock(g, x + w - 84, y + 6);
                }
                int bx = x + w - 70;
                boolean over = VoiceUi.inside(mouseX, mouseY, bx, y + 2, 64, ROW - 8);
                Ui.soft(g, bx, y + 2, 64, ROW - 8, Ui.argb(Ui.accent(), over ? 70 : 34));
                Ui.softOutline(g, bx + 1, y + 3, 62, ROW - 10, Ui.argb(Ui.accent(), over ? 255 : 170));
                Ui.spacedCentered(g, font, Component.translatable("caracrea.voice.groups.join").getString(), Ui.SMALL, bx + 32, y + (ROW - 10) / 2f,
                        0xFFFFFFFF, 0.6f);
                y += ROW;
            }
            g.disableScissor();
            // Creation form.
            int fy = listY + listH + 10;
            Ui.hairline(g, x, x + w, fy - 5, 0xFFFFFF, 30, false, true);
            g.drawString(font, Ui.tr(Ui.FONT, "caracrea.voice.groups.name"), x, fy + 4, Ui.argb(Ui.TEXT, 255), false);
            g.drawString(font, Ui.tr(Ui.FONT, "caracrea.voice.groups.password"), x, fy + 26, Ui.argb(Ui.TEXT, 255), false);
            g.drawString(font, Ui.tr(Ui.FONT, "caracrea.voice.groups.kind"), x, fy + 48, Ui.argb(Ui.TEXT, 255), false);
            String[] labels = {typeName(0), typeName(1), typeName(2)};
            VoiceUi.segments(g, font, x + 90, fy + 44, w - 90, 16, labels, type, mouseX, mouseY);
            String hint = Component.translatable("caracrea.voice.group.type." + TYPES[type] + ".hint").getString();
            g.drawString(font, Ui.lit(Ui.ellipsis(font, hint, Ui.SMALL, w - 90), Ui.SMALL), x + 90, fy + 64, Ui.argb(Ui.MUTED, 255), false);
        }
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + ph - 36, 0xFFFFFF, 30, true, true);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static void lock(GuiGraphics g, int x, int y) {
        int c = Ui.argb(Ui.MUTED, 255);
        g.fill(x + 1, y, x + 6, y + 1, c);
        g.fill(x + 1, y, x + 2, y + 4, c);
        g.fill(x + 5, y, x + 6, y + 4, c);
        g.fill(x, y + 3, x + 7, y + 8, c);
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
        if (inGroup()) {
            return false;
        }
        int x = px + Ui.MARGE;
        int w = pw - 2 * Ui.MARGE;
        int fy = listY + listH + 10;
        if (VoiceUi.inside(mx, my, x + 90, fy + 44, w - 90, 16)) {
            type = Mth.clamp((int) ((mx - x - 90) * 3 / (w - 90)), 0, 2);
            SonsUi.clic(0.6F);
            return true;
        }
        int top = listY + 14;
        if (VoiceUi.inside(mx, my, px, top, pw, listH - 14) && mx >= x + w - 70) {
            int index = (int) Math.floor((my - top + scroll) / ROW);
            List<GroupInfo> groups = VoiceClient.groups();
            if (index >= 0 && index < groups.size()) {
                // A locked group takes the password typed in the field below.
                VoiceClient.joinGroup(groups.get(index).id(), password.getValue());
                SonsUi.clic(0.6F);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (float) delta * 20;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER && !inGroup() && name.isFocused()) {
            create();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
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
