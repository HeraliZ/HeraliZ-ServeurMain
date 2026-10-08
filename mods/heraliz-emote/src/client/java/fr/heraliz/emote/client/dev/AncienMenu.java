package fr.heraliz.emote.client.dev;

import fr.heraliz.emote.client.gui.EmoteIcons;
import fr.heraliz.emote.client.gui.EmotePreview;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.EmoteAudio;
import fr.heraliz.emote.client.HeralizKeys;
import fr.heraliz.emote.client.catalog.EmoteCatalog;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The emote menu: every emote Emotecraft knows, sorted in categories, with a live preview of
 * your own character playing the one under the mouse. Opened with H by default.
 */
/** Ancien menu (2.5.1), banc seulement : captures « avant ». */
public class AncienMenu extends Screen {
    private static final int TILE_W = 46;
    private static final int TILE_H = 54;
    private static final int GOLD = 0xFFFFC94A;
    private static final int PURPLE = 0xFF6A48C8;
    private static final List<String> TABS = List.of("all", "dance", "music", "party", "social", "sport", "survival", "other");

    private static String lastCategory = "all";
    private static double lastScroll;
    @Nullable
    private static UUID lastSelected;

    private record Entry(KeyframeAnimation anim, Component name, Component description, Component author, String category,
                         boolean ours, boolean props, String search) {
    }

    private record Tab(String key, Component label, int x0, int x1, int y) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final EmotePreview preview = new EmotePreview();
    private String category = lastCategory;
    private EditBox search;
    @Nullable
    private Entry selected;
    @Nullable
    private Entry hovered;
    @Nullable
    private Entry previewed;
    private double scroll = lastScroll;
    private float yaw = -28;
    private boolean dragging;
    private long lastClickTime;
    @Nullable
    private Entry lastClicked;
    @Nullable
    private Component status;
    private int statusTicks;

    private int gx0, gy0, gx1, gy1;
    private int px0, py0, px1, py1;
    private int tabY;
    private int soundsX;

    public AncienMenu() {
        super(Component.translatable("heraliz_emote.menu.title"));
    }

    @Override
    protected void init() {
        int margin = 10;
        int leftW = Math.max(200, (int) (width * 0.6));
        gx0 = margin;
        gx1 = Math.min(margin + leftW, width - 150);
        tabY = 28;
        gy0 = tabY + 22;
        gy1 = height - 30;
        px0 = gx1 + 10;
        py0 = 8;
        px1 = width - margin;
        py1 = height - 30;

        if (entries.isEmpty()) collect();

        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, gx1 - 132, 8, 132, 14, Component.translatable("heraliz_emote.menu.search"));
        search.setHint(Component.translatable("heraliz_emote.menu.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(s -> refilter(true));
        addRenderableWidget(search);

        int bw = (px1 - px0 - 24) / 2;
        addRenderableWidget(Button.builder(Component.translatable("heraliz_emote.menu.play"), b -> play(selected))
                .bounds(px0 + 8, py1 - 26, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("heraliz_emote.menu.stop"), b -> {
            ClientEmoteAPI.playEmote(null);
            onClose();
        }).bounds(px0 + 16 + bw, py1 - 26, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("heraliz_emote.menu.close"), b -> onClose())
                .bounds(width - margin - 80, height - 24, 80, 18).build());
        soundsX = width - margin - 80 - 4 - 120;
        addRenderableWidget(EmoteAudio.othersButton(soundsX, height - 24, 120, 18, "heraliz_emote.menu.others_sound"));

        tabs.clear();
        int x = gx0, y = tabY;
        for (String key : TABS) {
            long count = entries.stream().filter(e -> matches(e, key)).count();
            if (count == 0 && !key.equals("all")) continue;
            Component label = Component.translatable("heraliz_emote.category." + key).append(" " + count);
            int w = font.width(label) + 12;
            if (x + w > gx1 && x > gx0) {
                // no room left on this row: wrap the tabs
                x = gx0;
                y += 19;
            }
            tabs.add(new Tab(key, label, x, x + w, y));
            x += w + 3;
        }
        gy0 = y + 22;
        refilter(false);
        if (selected == null && !shown.isEmpty()) selected = shown.get(0);
        setInitialFocus(search);
    }

    private void collect() {
        Map<String, Integer> order = new HashMap<>();
        int i = 0;
        for (String id : EmoteCatalog.all().keySet()) order.put(id, i++);
        for (KeyframeAnimation anim : ClientEmoteAPI.clientEmoteList()) {
            EmoteCatalog.EmoteInfo info = EmoteCatalog.get(anim.getUuid());
            Component name = text(anim.extraData.get("name"), Component.literal("?"));
            Component description = text(anim.extraData.get("description"), Component.empty());
            Component author = text(anim.extraData.get("author"), Component.empty());
            String category = info == null ? "other" : info.category();
            entries.add(new Entry(anim, name, description, author, category, info != null, info != null && info.hasProps(),
                    name.getString().toLowerCase(Locale.ROOT) + " " + description.getString().toLowerCase(Locale.ROOT)));
            if (anim.getUuid().equals(lastSelected)) selected = entries.get(entries.size() - 1);
        }
        entries.sort(Comparator.<Entry>comparingInt(e -> e.ours ? 0 : 1)
                .thenComparingInt(e -> {
                    EmoteCatalog.EmoteInfo info = EmoteCatalog.get(e.anim.getUuid());
                    return info == null ? 0 : order.getOrDefault(info.id(), 0);
                })
                .thenComparing(e -> e.name.getString().toLowerCase(Locale.ROOT)));
    }

    private static Component text(@Nullable Object raw, Component fallback) {
        if (!(raw instanceof String s) || s.isBlank()) return fallback;
        try {
            MutableComponent c = Component.Serializer.fromJson(s);
            return c == null ? fallback : c;
        } catch (RuntimeException e) {
            return Component.literal(s);
        }
    }

    private static boolean matches(Entry e, String category) {
        return switch (category) {
            case "all" -> true;
            case "other" -> !e.ours || e.category.equals("other");
            default -> e.ours && e.category.equals(category);
        };
    }

    private void refilter(boolean resetScroll) {
        shown.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (Entry e : entries) {
            if (matches(e, category) && (q.isEmpty() || e.search.contains(q))) shown.add(e);
        }
        if (resetScroll) scroll = 0;
        clampScroll();
    }

    private int columns() {
        return Math.max(1, (gx1 - gx0) / TILE_W);
    }

    private int contentHeight() {
        return (shown.size() + columns() - 1) / columns() * TILE_H;
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight() - (gy1 - gy0)));
    }

    @Nullable
    private Entry entryAt(double mx, double my) {
        if (mx < gx0 || mx >= gx1 || my < gy0 || my >= gy1) return null;
        int cols = columns();
        int pad = (gx1 - gx0 - cols * TILE_W) / 2;
        int col = (int) ((mx - gx0 - pad) / TILE_W);
        int row = (int) ((my - gy0 + scroll) / TILE_H);
        if (col < 0 || col >= cols) return null;
        int i = row * cols + col;
        return i >= 0 && i < shown.size() ? shown.get(i) : null;
    }

    @Override
    public void tick() {
        search.tick();
        Entry target = hovered != null ? hovered : selected;
        if (target != previewed) {
            previewed = target;
            preview.show(target == null ? null : target.anim, 0);
        }
        preview.tick();
        if (statusTicks > 0 && --statusTicks == 0) status = null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hovered = entryAt(mouseX, mouseY);

        // title
        g.pose().pushPose();
        g.pose().translate(gx0, 9, 0);
        g.pose().scale(1.4F, 1.4F, 1);
        g.drawString(font, Component.literal("Heraliz ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Emote").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)), 0, 0, 0xFFFFFF);
        g.pose().popPose();

        // tabs
        for (Tab t : tabs) {
            boolean active = t.key.equals(category);
            boolean over = mouseX >= t.x0 && mouseX < t.x1 && mouseY >= t.y && mouseY < t.y + 16;
            g.fill(t.x0, t.y, t.x1, t.y + 16, active ? GOLD : over ? 0x70FFFFFF : 0x70000000);
            g.drawCenteredString(font, t.label, (t.x0 + t.x1) / 2, t.y + 4, active ? 0x202020 : 0xFFFFFF);
        }

        // grid
        g.fill(gx0 - 3, gy0 - 3, gx1 + 3, gy1 + 3, 0x80000000);
        g.enableScissor(gx0, gy0, gx1, gy1);
        int cols = columns();
        int pad = (gx1 - gx0 - cols * TILE_W) / 2;
        for (int i = 0; i < shown.size(); i++) {
            int x = gx0 + pad + (i % cols) * TILE_W;
            int y = gy0 + (i / cols) * TILE_H - (int) scroll;
            if (y + TILE_H < gy0 || y > gy1) continue;
            drawTile(g, shown.get(i), x, y);
        }
        g.disableScissor();
        if (shown.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("heraliz_emote.menu.empty"), (gx0 + gx1) / 2, (gy0 + gy1) / 2, 0xA0A0A0);
        }
        int content = contentHeight();
        if (content > gy1 - gy0) {
            int track = gy1 - gy0;
            int thumb = Math.max(16, track * track / content);
            int ty = gy0 + (int) ((track - thumb) * scroll / (content - track));
            g.fill(gx1 - 2, gy0, gx1, gy1, 0x40FFFFFF);
            g.fill(gx1 - 2, ty, gx1, ty + thumb, GOLD);
        }

        renderPreviewPanel(g, partialTick);

        Component hint = Component.translatable("heraliz_emote.menu.hint");
        if (gx0 + font.width(hint) + 6 < soundsX) g.drawString(font, hint, gx0, height - 20, 0x909090);
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) g.renderTooltip(font, hovered.name, mouseX, mouseY);
    }

    private void drawTile(GuiGraphics g, Entry e, int x, int y) {
        boolean isSelected = e == selected;
        boolean isHovered = e == hovered;
        int bg = isHovered ? 0x60FFFFFF : isSelected ? 0x40FFC94A : 0x30FFFFFF;
        g.fill(x + 2, y + 2, x + TILE_W - 2, y + TILE_H - 2, bg);
        if (isSelected) {
            g.fill(x + 1, y + 1, x + TILE_W - 1, y + 2, GOLD);
            g.fill(x + 1, y + TILE_H - 2, x + TILE_W - 1, y + TILE_H - 1, GOLD);
            g.fill(x + 1, y + 1, x + 2, y + TILE_H - 1, GOLD);
            g.fill(x + TILE_W - 2, y + 1, x + TILE_W - 1, y + TILE_H - 1, GOLD);
        }
        int ix = x + (TILE_W - 32) / 2;
        int iy = y + 5;
        EmoteIcons.Icon icon = EmoteIcons.get(e.anim);
        if (icon != null) {
            g.blit(icon.texture(), ix, iy, 32, 32, 0, 0, icon.width(), icon.height(), icon.width(), icon.height());
        } else {
            g.fill(ix, iy, ix + 32, iy + 32, PURPLE);
            String letter = e.name.getString().isEmpty() ? "?" : e.name.getString().substring(0, 1).toUpperCase(Locale.ROOT);
            g.drawCenteredString(font, letter, ix + 16, iy + 12, 0xFFFFFF);
        }
        if (e.props) g.drawString(font, "✦", x + TILE_W - 10, y + 4, GOLD);
        String name = e.name.getString();
        g.pose().pushPose();
        g.pose().translate(x + TILE_W / 2.0F, y + 40, 0);
        g.pose().scale(0.7F, 0.7F, 1);
        int max = (int) ((TILE_W - 2) / 0.7F);
        if (font.width(name) > max) name = font.plainSubstrByWidth(name, max - font.width("…")) + "…";
        g.drawCenteredString(font, name, 0, 0, isSelected ? 0xFFE08A : 0xE8E8E8);
        g.pose().popPose();
    }

    private void renderPreviewPanel(GuiGraphics g, float partialTick) {
        g.fillGradient(px0, py0, px1, py1, 0xE0181428, 0xE0261A3C);
        g.fill(px0, py0, px1, py0 + 1, PURPLE);
        g.fill(px0, py1 - 1, px1, py1, PURPLE);
        g.fill(px0, py0, px0 + 1, py1, PURPLE);
        g.fill(px1 - 1, py0, px1, py1, PURPLE);

        Entry e = previewed;
        int cx = (px0 + px1) / 2;
        int w = px1 - px0;
        int stage = (int) ((py1 - py0) * 0.56);
        int feet = py0 + 12 + stage;
        // floor
        g.fill(cx - w / 3, feet - 1, cx + w / 3, feet + 2, 0x40FFFFFF);
        if (e != null) {
            float size = Math.min(stage / 2.6F, w / 2.4F);
            // big scenery (a basketball hoop...) must not spill out of the panel
            g.enableScissor(px0 + 1, py0 + 1, px1 - 1, py1 - 1);
            preview.render(g, cx, feet, size, yaw, -6, partialTick);
            g.disableScissor();
        }
        int y = feet + 10;
        if (e == null) return;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(1.35F, 1.35F, 1);
        g.drawCenteredString(font, e.name.copy().withStyle(ChatFormatting.BOLD), 0, 0, 0xFFFFFF);
        g.pose().popPose();
        y += 15;

        List<Component> chips = new ArrayList<>();
        chips.add(Component.translatable("heraliz_emote.category." + (e.ours ? e.category : "other")));
        float seconds = (e.anim.isInfinite ? e.anim.endTick + 1 - e.anim.returnToTick : e.anim.stopTick) / 20.0F;
        String duration = String.format(Locale.ROOT, "%.1f", seconds).replace('.', ',');
        chips.add(Component.translatable(e.anim.isInfinite ? "heraliz_emote.menu.loop" : "heraliz_emote.menu.once", duration));
        if (e.props) chips.add(Component.translatable("heraliz_emote.menu.props"));
        int total = 0;
        for (Component c : chips) total += font.width(c) + 10;
        int x = cx - total / 2;
        for (int i = 0; i < chips.size(); i++) {
            Component c = chips.get(i);
            int cw = font.width(c) + 8;
            g.fill(x, y, x + cw, y + 11, i == 2 ? 0xC0FFC94A : 0x60FFFFFF);
            g.drawString(font, c, x + 4, y + 2, i == 2 ? 0x202020 : 0xFFFFFF, false);
            x += cw + 2;
        }
        y += 16;
        List<FormattedCharSequence> lines = font.split(e.description, w - 20);
        int maxLines = Math.max(1, (py1 - 34 - y) / 10);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            g.drawCenteredString(font, lines.get(i), cx, y, 0xC8C8D8);
            y += 10;
        }
        if (!e.author.getString().isEmpty() && y < py1 - 40) {
            g.drawCenteredString(font, Component.translatable("heraliz_emote.menu.by", e.author).withStyle(ChatFormatting.DARK_GRAY), cx, y + 2, 0x808080);
        }
        if (status != null) g.drawCenteredString(font, status, cx, py0 + 6, 0xFF6060);
    }

    private void play(@Nullable Entry e) {
        if (e == null || minecraft == null) return;
        lastSelected = e.anim.getUuid();
        Component refused = fr.heraliz.emote.client.ClientRules.refusal();
        if (refused != null) {
            status = refused;
            statusTicks = 80;
            return;
        }
        if (ClientEmoteAPI.playEmote(e.anim)) {
            onClose();
        } else {
            status = Component.translatable("heraliz_emote.menu.cannot");
            statusTicks = 80;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (Tab t : tabs) {
            if (mx >= t.x0 && mx < t.x1 && my >= t.y && my < t.y + 16) {
                category = t.key;
                lastCategory = category;
                refilter(true);
                return true;
            }
        }
        Entry e = entryAt(mx, my);
        if (e != null && button == 0) {
            long now = System.currentTimeMillis();
            if (e == lastClicked && now - lastClickTime < 350) {
                play(e);
            } else {
                selected = e;
                lastSelected = e.anim.getUuid();
            }
            lastClicked = e;
            lastClickTime = now;
            return true;
        }
        if (mx >= px0 && mx < px1 && my >= py0 && my < py1 - 30) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw += (float) dx * 2.0F;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= gx0 && mx < gx1 && my >= gy0 && my < gy1) {
            scroll -= delta * TILE_H * 0.75;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            play(selected);
            return true;
        }
        if (!search.isFocused() && HeralizKeys.OPEN_MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        if (!shown.isEmpty() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)
                && !(search.isFocused() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT))) {
            int i = Math.max(0, shown.indexOf(selected));
            int cols = columns();
            i += switch (key) {
                case GLFW.GLFW_KEY_LEFT -> -1;
                case GLFW.GLFW_KEY_RIGHT -> 1;
                case GLFW.GLFW_KEY_UP -> -cols;
                default -> cols;
            };
            selected = shown.get(Mth.clamp(i, 0, shown.size() - 1));
            int row = shown.indexOf(selected) / cols;
            if (row * TILE_H < scroll) scroll = row * TILE_H;
            if ((row + 1) * TILE_H > scroll + (gy1 - gy0)) scroll = (row + 1) * TILE_H - (gy1 - gy0);
            clampScroll();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        lastScroll = scroll;
        lastCategory = category;
        preview.show(null, 0);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For the CI scenario: show one emote as if it had been clicked. */
    public void select(UUID uuid) {
        for (Entry e : entries) {
            if (e.anim.getUuid().equals(uuid)) {
                selected = e;
                category = e.ours ? e.category : "other";
                refilter(false);
                int i = shown.indexOf(e);
                if (i >= 0) scroll = Math.max(0, (i / columns()) * TILE_H - TILE_H);
                clampScroll();
            }
        }
    }
}
