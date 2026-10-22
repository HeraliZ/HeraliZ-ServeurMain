package fr.heraliz.emote.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.client.DeblocagesClient;
import fr.heraliz.emote.client.HeralizConfig;
import fr.heraliz.emote.client.HeralizKeys;
import fr.heraliz.emote.client.catalog.EmoteCatalog;
import fr.heraliz.emote.client.gui.kit.Echelle;
import fr.heraliz.emote.client.gui.kit.HzButton;
import fr.heraliz.emote.client.gui.kit.HzField;
import fr.heraliz.emote.client.gui.kit.SonsUi;
import fr.heraliz.emote.client.gui.kit.Ui;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import net.minecraft.client.gui.GuiGraphics;
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
 * The emote menu: every emote Emotecraft knows, sorted in categories, with a live preview of your own character playing
 * the one under the mouse. Opened with H by default.
 * <p>HeraliZ interface style (copy of the clans kit): forced scale on a 640 x 360 canvas, glass panel, DM Sans, spaced
 * capitals, clan accent. Left: categories; middle: emote tiles; right: preview stage, details, Play / Stop.
 * <p>Emotes not unlocked yet (emote crate) are greyed with a padlock: clicking one still shows it on the stage, and the
 * Play button then reads "To unlock in the emote crate" instead.
 */
public class EmoteBrowserScreen extends Screen {
    private static final int TILE_W = 52;
    private static final int TILE_H = 60;
    private static final int CAT_ROW = 17;
    private static final List<String> TABS = List.of("all", "dance", "music", "party", "social", "sport", "survival", "other");

    private static String lastCategory = "all";
    private static double lastScroll;
    @Nullable
    private static UUID lastSelected;

    private record Entry(KeyframeAnimation anim, Component name, Component description, Component author, String category,
                         boolean ours, boolean props, String search) {
        boolean locked() {
            return !DeblocagesClient.debloquee(anim.getUuid());
        }
    }

    private record Tab(String key, String label, long count, int x0, int x1, int y) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final EmotePreview preview = new EmotePreview();
    private String category = lastCategory;
    private HzField search;
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
    private boolean opened;
    private HzButton playButton, stopButton;
    private int playW;

    /** Panel, columns (categories / grid / preview) and footer. */
    private int px, py, pw, ph;
    private int cx0, cx1;
    private int gx0, gy0, gx1, gy1;
    private int sx0, sy0, sx1, sy1;
    private int footY;
    private int closeX, closeY;
    /** "Others' sound" switch (footer). */
    private int soundX0, soundX1, soundY;

    public EmoteBrowserScreen() {
        super(Component.translatable("heraliz_emote.menu.title"));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        if (!opened) {
            opened = true;
            SonsUi.ouvrir(0.5F);
        }
        pw = Math.min(612, width - 20);
        ph = Math.min(336, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int top = py + 44;
        footY = py + ph - 22;
        int bottom = footY - 12;

        // categories (left), preview (right), grid (between)
        cx0 = px + Ui.MARGE;
        cx1 = cx0 + 92;
        sx1 = px + pw - Ui.MARGE;
        sx0 = sx1 - Math.max(170, Math.min(200, pw / 3));
        sy0 = top;
        sy1 = bottom;
        gx0 = cx1 + Ui.ESPACE_L;
        gx1 = sx0 - Ui.ESPACE_L;
        gy0 = top + 14;
        gy1 = bottom;

        if (entries.isEmpty()) collect();

        String query = search == null ? "" : search.getValue();
        boolean focus = search == null || search.isFocused();
        search = new HzField(gx1 - 120, py + 13, 120, 14, 40, HzField.TOUT)
                .placeholder(Component.translatable("heraliz_emote.menu.search").getString())
                .remplacerAuFocus(false)
                .accent(Ui.accent());
        search.setValue(query);
        search.setResponder(s -> refilter(true));
        addRenderableWidget(search);

        int bw = (sx1 - sx0 - 6) / 2;
        playW = bw;
        playButton = addRenderableWidget(new HzButton(sx0, sy1 - 20, bw, 20, Component.translatable("heraliz_emote.menu.play_btn"), Ui.accent(),
                () -> play(selected)).look(HzButton.Look.PRIMARY));
        stopButton = addRenderableWidget(new HzButton(sx0 + bw + 6, sy1 - 20, sx1 - sx0 - bw - 6, 20, Component.translatable("heraliz_emote.menu.stop_btn"),
                Ui.accent(), () -> {
            ClientEmoteAPI.playEmote(null);
            onClose();
        }));
        Component close = Component.translatable("heraliz_emote.menu.close");
        int closeW = (int) Ui.spacedWidth(font, close.getString().toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 2;
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - closeW, footY, closeW, 10, close, Ui.accent(), this::onClose)
                .look(HzButton.Look.LINK));
        String sound = Component.translatable("heraliz_emote.menu.others_sound").getString();
        soundX1 = px + pw - Ui.MARGE - closeW - 22;
        soundX0 = soundX1 - (int) Ui.spacedWidth(font, sound.toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) - 16;
        soundY = footY;
        closeX = px + pw - Ui.MARGE - 9;
        closeY = py + 15;

        tabs.clear();
        int y = top + 14;
        for (String key : TABS) {
            long count = entries.stream().filter(e -> matches(e, key)).count();
            if (count == 0 && !key.equals("all")) continue;
            tabs.add(new Tab(key, Component.translatable("heraliz_emote.category." + key).getString(), count, cx0, cx1, y));
            y += CAT_ROW;
        }
        refilter(false);
        if (selected == null && !shown.isEmpty()) selected = shown.get(0);
        if (focus) setInitialFocus(search);
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
        return Math.max(1, (gx1 - gx0 - 4) / TILE_W);
    }

    private int pad() {
        return (gx1 - gx0 - 4 - columns() * TILE_W) / 2;
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
        int col = (int) Math.floor((mx - gx0 - pad()) / TILE_W);
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
        // locked emote: one wide inactive button "to unlock in the emote crate" in place of Play / Stop
        boolean locked = selected != null && selected.locked();
        playButton.active = !locked;
        stopButton.visible = !locked;
        playButton.setMessage(Component.translatable(locked ? "heraliz_emote.menu.locked_btn" : "heraliz_emote.menu.play_btn"));
        playButton.setBounds(sx0, sy1 - 20, locked ? sx1 - sx0 : playW, 20);
        if (statusTicks > 0 && --statusTicks == 0) status = null;
    }

    private boolean over(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        int accent = Ui.accent();
        hovered = entryAt(mouseX, mouseY);

        // ---- header: title, count, search, close cross
        Ui.spaced(g, font, title.getString(), Ui.BOLD, px + Ui.MARGE, py + 16, Ui.argb(Ui.TEXT, 255), 1.6f);
        int tw = (int) Ui.spacedWidth(font, title.getString().toUpperCase(Locale.ROOT), Ui.BOLD, 1.6f);
        long withProps = entries.stream().filter(Entry::props).count();
        String sub = Component.translatable("heraliz_emote.menu.subtitle", entries.size(), withProps).getString();
        g.drawString(font, Ui.lit(Ui.ellipsis(font, sub, Ui.FONT, search.getX() - (px + Ui.MARGE + tw + 16) - 8), Ui.FONT),
                px + Ui.MARGE + tw + 16, py + 16, Ui.argb(Ui.MUTED, 255), false);
        boolean overClose = over(mouseX, mouseY, closeX - 3, closeY - 3, closeX + 10, closeY + 10);
        croix(g, closeX, closeY, 7, overClose ? Ui.argb(Ui.TEXT, 255) : Ui.argb(Ui.MUTED, 255));
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + 34, 0xFFFFFF, 36, false, true);

        // ---- categories
        Ui.section(g, font, Component.translatable("heraliz_emote.menu.categories").getString(), cx0, sy0 + 1, cx1 - cx0);
        for (Tab t : tabs) {
            boolean active = t.key.equals(category);
            boolean hover = over(mouseX, mouseY, t.x0, t.y, t.x1, t.y + CAT_ROW - 2);
            if (active) {
                Ui.soft(g, t.x0, t.y, t.x1 - t.x0, CAT_ROW - 2, Ui.argb(accent, 30));
                g.fill(t.x0, t.y + 2, t.x0 + 2, t.y + CAT_ROW - 4, Ui.argb(accent, 255));
            } else if (hover) {
                Ui.soft(g, t.x0, t.y, t.x1 - t.x0, CAT_ROW - 2, Ui.argb(0xFFFFFF, 12));
            }
            int c = active ? Ui.argb(0xFFFFFF, 255) : hover ? Ui.argb(Ui.TEXT, 255) : Ui.argb(Ui.MUTED, 255);
            g.drawString(font, Ui.lit(t.label, active ? Ui.BOLD : Ui.FONT), t.x0 + 8, t.y + 3, c, false);
            Ui.right(g, font, Ui.lit(String.valueOf(t.count), Ui.SMALL), t.x1 - 6, t.y + 4,
                    active ? Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.35f), 255) : Ui.argb(Ui.FAINT, 255));
        }

        // ---- grid
        String catLabel = tabs.stream().filter(t -> t.key.equals(category)).map(Tab::label).findFirst().orElse("");
        String gridTitle = search.getValue().isBlank() ? catLabel : Component.translatable("heraliz_emote.menu.search").getString().replace("…", "");
        Ui.section(g, font, gridTitle + "  ·  " + shown.size(), gx0, sy0 + 1, gx1 - gx0 - 128);
        g.enableScissor(gx0, gy0, gx1, gy1);
        int cols = columns();
        int pad = pad();
        for (int i = 0; i < shown.size(); i++) {
            int x = gx0 + pad + (i % cols) * TILE_W;
            int y = gy0 + (i / cols) * TILE_H - (int) scroll;
            if (y + TILE_H < gy0 || y > gy1) continue;
            drawTile(g, shown.get(i), x, y, accent);
        }
        g.disableScissor();
        if (shown.isEmpty()) {
            Ui.centered(g, font, Ui.tr(Ui.FONT, "heraliz_emote.menu.empty"), (gx0 + gx1) / 2f, (gy0 + gy1) / 2f - 4, Ui.argb(Ui.MUTED, 255));
        }
        int content = contentHeight();
        if (content > gy1 - gy0) {
            int track = gy1 - gy0;
            int thumb = Math.max(16, track * track / content);
            int ty = gy0 + (int) ((track - thumb) * scroll / (content - track));
            g.fill(gx1 - 2, gy0, gx1, gy1, Ui.argb(0xFFFFFF, 18));
            g.fill(gx1 - 2, ty, gx1, ty + thumb, Ui.argb(accent, 200));
        }

        renderPreviewPanel(g, partialTick, accent);

        // ---- footer: hint, others' sound switch, close
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, footY - 7, 0xFFFFFF, 30, true, true);
        String hint = Component.translatable("heraliz_emote.menu.hint").getString();
        g.drawString(font, Ui.lit(Ui.ellipsis(font, hint, Ui.SMALL, soundX0 - (px + Ui.MARGE) - 12), Ui.SMALL), px + Ui.MARGE, footY + 1,
                Ui.argb(Ui.FAINT, 255), false);
        boolean on = HeralizConfig.OTHER_PLAYERS_SOUNDS.get();
        boolean overSound = over(mouseX, mouseY, soundX0, soundY - 2, soundX1, soundY + 10);
        Ui.pastille(g, soundX0, soundY, 8, accent, on, overSound);
        Ui.spaced(g, font, Component.translatable("heraliz_emote.menu.others_sound").getString(), Ui.SMALL, soundX0 + 14, soundY + 1.5f,
                overSound ? Ui.argb(Ui.TEXT, 255) : Ui.argb(Ui.MUTED, 255), 0.8f);

        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) {
            Ui.tooltip(g, font, hovered.locked()
                    ? List.of(Ui.in(hovered.name, Ui.BOLD), Ui.tr(Ui.SMALL, "heraliz_emote.menu.locked_tip"))
                    : List.of(Ui.in(hovered.name, Ui.BOLD)), mouseX, mouseY, accent);
        } else if (overSound) {
            Ui.tooltip(g, font, List.of(Ui.tr(Ui.BOLD, "heraliz_emote.config.other_players"),
                    Ui.tr(Ui.FONT, on ? "heraliz_emote.menu.on" : "heraliz_emote.menu.off")), mouseX, mouseY, accent);
        }
    }

    /** Close cross drawn pixel by pixel (no texture, no font glyph). */
    private static void croix(GuiGraphics g, int x, int y, int size, int argb) {
        for (int i = 0; i < size; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, argb);
            g.fill(x + size - 1 - i, y + i, x + size - i, y + i + 1, argb);
            if (i < size - 1) {
                g.fill(x + i + 1, y + i, x + i + 2, y + i + 1, Ui.argb(argb, (argb >>> 24) / 3));
                g.fill(x + size - 2 - i, y + i, x + size - 1 - i, y + i + 1, Ui.argb(argb, (argb >>> 24) / 3));
            }
        }
    }

    private void drawTile(GuiGraphics g, Entry e, int x, int y, int accent) {
        boolean isSelected = e == selected;
        boolean isHovered = e == hovered;
        int tx = x + 2, ty = y + 2, w = TILE_W - 4, h = TILE_H - 4;
        if (isSelected) {
            Ui.soft(g, tx, ty, w, h, Ui.argb(accent, isHovered ? 46 : 32));
            Ui.softOutline(g, tx + 1, ty + 1, w - 2, h - 2, Ui.argb(accent, 230));
        } else {
            Ui.soft(g, tx, ty, w, h, Ui.argb(0xFFFFFF, isHovered ? 20 : 8));
            Ui.softOutline(g, tx + 1, ty + 1, w - 2, h - 2, Ui.argb(0xFFFFFF, isHovered ? 60 : 14));
        }
        int ix = x + (TILE_W - 32) / 2;
        int iy = y + 6;
        boolean locked = e.locked();
        // locked: icon dimmed and greyed (selected or hovered: a little brighter, it is the one shown on the stage)
        if (locked) RenderSystem.setShaderColor(0.42f, 0.44f, 0.48f, isSelected || isHovered ? 0.85f : 0.6f);
        EmoteIcons.Icon icon = EmoteIcons.get(e.anim);
        if (icon != null) {
            g.blit(icon.texture(), ix, iy, 32, 32, 0, 0, icon.width(), icon.height(), icon.width(), icon.height());
        } else {
            Ui.soft(g, ix, iy, 32, 32, Ui.argb(Ui.mix(accent, 0x0D0F14, 0.45f), 255));
            String letter = e.name.getString().isEmpty() ? "?" : e.name.getString().substring(0, 1).toUpperCase(Locale.ROOT);
            Ui.gros(g, font, Ui.lit(letter, Ui.BOLD), ix + 16 - font.width(Ui.lit(letter, Ui.BOLD)) * 0.75f, iy + 10, 1.5f, 0xFFFFFFFF);
        }
        if (locked) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            cadenas(g, ix + 32 - 7, iy + 32 - 9, Ui.argb(0xD8DCE4, 235));
        }
        if (e.props && !locked) {
            // small gold diamond: this emote comes with props
            int dx = x + TILE_W - 9, dy = y + 5;
            for (int r = 0; r < 5; r++) {
                int half = 2 - Math.abs(r - 2);
                g.fill(dx - half, dy + r, dx + half + 1, dy + r + 1, Ui.argb(Ui.OR, 240));
            }
        }
        String name = Ui.ellipsis(font, e.name.getString(), Ui.SMALL, TILE_W - 6);
        Ui.centered(g, font, Ui.lit(name, Ui.SMALL), x + TILE_W / 2f, y + 42, locked
                ? (isSelected || isHovered ? Ui.argb(Ui.MUTED, 255) : Ui.argb(Ui.FAINT, 255))
                : isSelected ? Ui.argb(0xFFFFFF, 255) : isHovered ? Ui.argb(Ui.TEXT, 255) : Ui.argb(Ui.MUTED, 255));
    }

    /** Small padlock drawn pixel by pixel (7 x 9), on a dark disc so it reads on any icon. */
    private static void cadenas(GuiGraphics g, int x, int y, int argb) {
        Ui.soft(g, x - 2, y - 1, 11, 12, Ui.argb(0x0D0F14, 200));
        // shackle
        g.fill(x + 1, y, x + 6, y + 1, argb);
        g.fill(x + 1, y + 1, x + 2, y + 4, argb);
        g.fill(x + 5, y + 1, x + 6, y + 4, argb);
        // body and keyhole
        g.fill(x, y + 4, x + 7, y + 9, argb);
        g.fill(x + 3, y + 5, x + 4, y + 8, Ui.argb(0x0D0F14, 255));
    }

    private void renderPreviewPanel(GuiGraphics g, float partialTick, int accent) {
        // stage: dark vertical gradient, soft accent glow on the floor, faint outline
        int stageH = Math.max(90, (int) ((sy1 - sy0) * 0.52f));
        int st0 = sy0, st1 = sy0 + stageH;
        Ui.soft(g, sx0, st0, sx1 - sx0, stageH, Ui.argb(0x000000, 60));
        g.fillGradient(sx0 + 1, st0 + 1, sx1 - 1, st1 - 1, Ui.argb(Ui.mix(0x0D0F14, accent, 0.10f), 150), Ui.argb(Ui.mix(0x0D0F14, accent, 0.28f), 190));
        Ui.softOutline(g, sx0 + 1, st0 + 1, sx1 - sx0 - 2, stageH - 2, Ui.argb(0xFFFFFF, 18));
        g.fill(sx0 + 2, st0 + 1, sx1 - 2, st0 + 2, Ui.argb(0xFFFFFF, 30));

        Entry e = previewed;
        int cx = (sx0 + sx1) / 2;
        int w = sx1 - sx0;
        int feet = st1 - 14;
        Ui.band(g, sx0 + 8, feet - 1, sx1 - 8, feet + 1, accent, 110, 40);
        Ui.band(g, sx0 + 22, feet + 1, sx1 - 22, feet + 3, accent, 40, 30);
        if (e != null) {
            float size = Math.min((stageH - 20) / 2.4F, w / 2.6F);
            // big scenery (a basketball hoop...) must not spill out of the stage
            g.enableScissor(sx0 + 1, st0 + 1, sx1 - 1, st1 - 1);
            preview.render(g, cx, feet, size, yaw, -6, partialTick);
            g.disableScissor();
        }
        if (status != null) {
            int sw = Math.min(w - 12, font.width(Ui.in(status, Ui.SMALL)) + 12);
            Ui.soft(g, cx - sw / 2, st0 + 6, sw, 13, Ui.argb(Ui.DANGER, 40));
            Ui.softOutline(g, cx - sw / 2 + 1, st0 + 7, sw - 2, 11, Ui.argb(Ui.DANGER, 120));
            Ui.centered(g, font, Ui.in(status, Ui.SMALL), cx, st0 + 9, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.3f), 255));
        } else {
            Ui.spacedRight(g, font, Component.translatable("heraliz_emote.menu.drag").getString(), Ui.SMALL, sx1 - 6, st1 - 9,
                    Ui.argb(0xFFFFFF, 60), 0.6f);
        }
        if (e == null) return;

        int y = st1 + 9;
        String name = Ui.ellipsis(font, e.name.getString(), Ui.BOLD, (int) ((w - 4) / 1.3f));
        Ui.gros(g, font, Ui.lit(name, Ui.BOLD), sx0, y, 1.3f, Ui.argb(0xFFFFFF, 255));
        y += 17;

        int x = sx0;
        x += Ui.chip(g, font, Component.translatable("heraliz_emote.category." + (e.ours ? e.category : "other")).getString(), x, y, accent) + 4;
        float seconds = (e.anim.isInfinite ? e.anim.endTick + 1 - e.anim.returnToTick : e.anim.stopTick) / 20.0F;
        String duration = String.format(Locale.ROOT, "%.1f", seconds);
        if (Ui.french()) duration = duration.replace('.', ',');
        x += Ui.chip(g, font, Component.translatable(e.anim.isInfinite ? "heraliz_emote.menu.loop" : "heraliz_emote.menu.once", duration).getString(),
                x, y, 0xA0A6B2) + 4;
        if (e.props && x + 40 < sx1) Ui.chip(g, font, Component.translatable("heraliz_emote.menu.props_chip").getString(), x, y, Ui.OR);
        y += 17;

        int bottom = sy1 - 26;
        List<FormattedCharSequence> lines = font.split(Ui.in(e.description, Ui.FONT), w);
        int maxLines = Math.max(0, (bottom - y - (e.author.getString().isEmpty() ? 0 : 11)) / 10);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            g.drawString(font, lines.get(i), sx0, y, Ui.argb(Ui.TEXT, 210), false);
            y += 10;
        }
        if (!e.author.getString().isEmpty() && y + 9 <= bottom) {
            g.drawString(font, Ui.tr(Ui.SMALL, "heraliz_emote.menu.by", e.author), sx0, y + 2, Ui.argb(Ui.FAINT, 255), false);
        }
    }

    private void play(@Nullable Entry e) {
        if (e == null || minecraft == null) return;
        lastSelected = e.anim.getUuid();
        Component refused = fr.heraliz.emote.client.ClientRules.refusal(e.anim.getUuid());
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
        if (button == 0 && over(mx, my, closeX - 3, closeY - 3, closeX + 10, closeY + 10)) {
            SonsUi.clic(0.6F);
            onClose();
            return true;
        }
        if (button == 0 && over(mx, my, soundX0, soundY - 2, soundX1, soundY + 10)) {
            // same setting as the old "Others' sound: ON/OFF" button, saved at once
            HeralizConfig.OTHER_PLAYERS_SOUNDS.set(!HeralizConfig.OTHER_PLAYERS_SOUNDS.get());
            HeralizConfig.SPEC.save();
            SonsUi.clic(0.6F);
            return true;
        }
        for (Tab t : tabs) {
            if (over(mx, my, t.x0, t.y, t.x1, t.y + CAT_ROW - 2)) {
                if (!category.equals(t.key)) SonsUi.clic(0.5F);
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
        if (mx >= sx0 && mx < sx1 && my >= sy0 && my < sy1 - 26) {
            dragging = true;
            return true;
        }
        if (search.isFocused() && !search.isMouseOver(mx, my)) search.setFocused(false);
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
        SonsUi.fermer(0.5F);
        super.onClose();
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
