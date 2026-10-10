package fr.heraliz.crates.client.gui;

import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.network.HzNet;
import fr.heraliz.crates.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editeur de butin des operateurs (outil d'edition sur une caisse, maquette validee) : en-tete (nom de la caisse, nombre
 * d'objets et total des chances, pastille « Editeur · staff »), une ligne par objet avec sa quantite, sa rarete (clic
 * pour changer) et sa chance en %. Changer une chance reequilibre les autres (elles gardent leurs proportions ; les
 * poids sont remis sur 100 000). Suppression au survol d'une ligne, ajout depuis l'inventaire.
 */
public class LootEditorScreen extends Screen {
    /** Total des poids apres une saisie de pourcentage : 1 000 = 1 %. */
    private static final int NORMAL_TOTAL = 100_000;
    private static final int ROW_H = 20, HEAD_H = 52, FOOT_H = 36;

    private static final class Row {
        final int orig;
        final ItemStack stack;
        final String itemId;
        final boolean commandOnly, missing;
        int count, weight, rarity;

        Row(int orig, ItemStack stack, String itemId, boolean commandOnly, boolean missing, int count, int weight, int rarity) {
            this.orig = orig;
            this.stack = stack;
            this.itemId = itemId;
            this.commandOnly = commandOnly;
            this.missing = missing;
            this.count = count;
            this.weight = weight;
            this.rarity = rarity;
        }
    }

    /** Champs d'une ligne visible, rebranches sur la ligne de donnees qu'elle affiche. */
    private final class Slot {
        final HzField count, pct;
        final HzButton rarity;
        int bound = -1;

        Slot() {
            count = new HzField(0, 0, 34, 14, 4, v -> v.matches("\\d{0,4}"));
            pct = new HzField(0, 0, 50, 14, 7, v -> v.matches("\\d{0,3}([.,]\\d{0,3})?")).suffix("%");
            rarity = new HzButton(0, 0, 62, 12, Component.empty(), 0x888888, () -> cycleRarity(this)).look(HzButton.Look.PILL);
            count.accent(type.glow);
            pct.accent(type.glow);
            count.setResponder(v -> {
                if (bound < 0) return;
                rows.get(bound).count = Mth.clamp(parseInt(v, 1), 1, fr.heraliz.crates.block.CrateAnim.EDITEUR_MAX_COUNT);
                dirty = true;
            });
            pct.setResponder(v -> {
                if (bound < 0) return;
                double p = parsePct(v);
                if (p < 0) return;
                setChance(bound, p);
                dirty = true;
                refreshValues();
            });
            addRenderableWidget(count);
            addRenderableWidget(rarity);
            addRenderableWidget(pct);
        }

        void setVisible(boolean v) {
            count.visible = pct.visible = rarity.visible = v;
        }
    }

    private final CrateType type;
    private final int version;
    private final List<Row> rows = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();
    private boolean dirty, picking;
    private int scroll, confirmExitAt = -1000, ticks;
    private int left, top, pw, ph;
    private int colCount, colRarity, colPct, colDelete;
    private HzButton addBtn, saveBtn;

    public LootEditorScreen(CrateType type, int version, List<Packets.Row> source) {
        super(Component.translatable("gui.heralizcrates.editor.title", type.crateName()));
        this.type = type;
        this.version = version;
        for (int i = 0; i < source.size(); i++) {
            Packets.Row r = source.get(i);
            boolean missing = r.stack().getItem() == Items.BARRIER && !r.itemId().equals("minecraft:barrier");
            rows.add(new Row(i, r.stack(), r.itemId(), r.commandOnly(), missing, r.count(), r.weight(), r.rarity()));
        }
    }

    // =================================================================== mise en page
    @Override
    protected void init() {
        pw = Math.min(400, width - 24);
        int maxRows = Math.max(3, (height - 12 - HEAD_H - FOOT_H) / ROW_H);
        int shown = Math.max(3, Math.min(rows.size(), maxRows));
        ph = HEAD_H + shown * ROW_H + FOOT_H;
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        colDelete = left + pw - 18;
        colPct = colDelete - 10 - 50;
        colRarity = colPct - 10 - 62;
        colCount = colRarity - 10 - 34;
        slots.clear();
        for (int i = 0; i < shown; i++) slots.add(new Slot());
        int fy = top + ph - 26;
        // « + Ajouter un objet » : bouton sans texte, posé sur les capitales vertes dessinées par render
        addBtn = addRenderableWidget(new HzButton(left + 14, fy + 4, 110, 12, Component.empty(), type.glow, this::openPicker).look(HzButton.Look.LINK));
        saveBtn = addRenderableWidget(new HzButton(left + pw - 14 - 96, fy - 1, 96, 19, Component.translatable("gui.heralizcrates.editor.save"),
                Ui.OK, this::save).look(HzButton.Look.PRIMARY));
        bind();
    }

    private int rowsTop() {
        return top + HEAD_H;
    }

    private int visibleRows() {
        return slots.size();
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows());
    }

    /** Rebranche les champs visibles sur les lignes affichees. */
    private void bind() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            int r = scroll + i;
            int y = rowsTop() + i * ROW_H;
            s.count.setX(colCount);
            s.count.setY(y + 3);
            s.rarity.setBounds(colRarity, y + 4, 62, 12);
            s.pct.setX(colPct);
            s.pct.setY(y + 3);
            if (r >= rows.size()) {
                s.bound = -1;
                s.setVisible(false);
                continue;
            }
            s.bound = r;
            s.setVisible(!picking);
            Row row = rows.get(r);
            s.count.setValue(String.valueOf(row.count));
            s.pct.setValue(fmtPct(chance(r)));
            styleRarity(s, row);
        }
    }

    private void styleRarity(Slot s, Row row) {
        LootRarity r = CardArt.rarity(row.rarity);
        s.rarity.setMessage(r.displayName());
        s.rarity.accent(r.color);
    }

    /** Remet a jour les pourcentages affiches, sauf celui en cours de saisie. */
    private void refreshValues() {
        for (Slot s : slots) {
            if (s.bound < 0 || s.pct.isFocused()) continue;
            s.pct.setValue(fmtPct(chance(s.bound)));
        }
    }

    // =================================================================== donnees
    private long total() {
        long t = 0;
        for (Row r : rows) t += r.weight;
        return t;
    }

    private double chance(int i) {
        long t = total();
        return t <= 0 ? 0 : 100.0 * rows.get(i).weight / t;
    }

    /**
     * Donne a la ligne i la chance p (%) : tous les poids sont remis sur un total de 100 000, les autres lignes gardent
     * leurs proportions entre elles. 0 % = objet desactive (il reste dans la liste).
     */
    private void setChance(int i, double p) {
        int n = rows.size();
        if (n == 1) {
            rows.get(0).weight = NORMAL_TOTAL;
            return;
        }
        p = Mth.clamp(p, 0, 99.999);
        int mine = (int) Math.round(p * NORMAL_TOTAL / 100.0);
        long others = total() - rows.get(i).weight;
        int rest = NORMAL_TOTAL - mine;
        int given = 0, last = -1;
        for (int j = 0; j < n; j++) {
            if (j == i) continue;
            Row r = rows.get(j);
            int w = others > 0 ? (int) Math.round((double) r.weight * rest / others) : rest / (n - 1);
            if (others > 0 && r.weight > 0) w = Math.max(1, w);
            r.weight = w;
            given += w;
            last = j;
        }
        // arrondis : le reste va a la derniere ligne pour que le total tombe juste
        if (last >= 0 && rows.get(last).weight + (rest - given) >= 0) rows.get(last).weight += rest - given;
        rows.get(i).weight = mine;
    }

    private void cycleRarity(Slot s) {
        if (s.bound < 0) return;
        Row row = rows.get(s.bound);
        int n = LootRarity.values().length;
        row.rarity = (row.rarity + (hasShiftDown() ? n - 1 : 1)) % n;
        styleRarity(s, row);
        dirty = true;
    }

    private void deleteRow(int index) {
        if (index < 0 || index >= rows.size()) return;
        rows.remove(index);
        dirty = true;
        setFocused(null);
        bind();
    }

    private void openPicker() {
        picking = true;
        setFocused(null);
        bind();
    }

    private void addFromInventory(ItemStack stack) {
        if (stack.isEmpty()) return;
        int avg = rows.isEmpty() ? 10 : (int) Math.max(1, total() / rows.size());
        rows.add(new Row(-1, stack.copy(), "", false, false, stack.getCount(), avg, 0));
        dirty = true;
        picking = false;
        if (slots.size() < Math.min(rows.size(), Math.max(3, (height - 12 - HEAD_H - FOOT_H) / ROW_H))) {
            // une ligne de plus tient a l'ecran : on agrandit le panneau
            rebuildWidgets();
        }
        scroll = maxScroll();
        bind();
    }

    private void save() {
        List<Packets.SaveRow> out = new ArrayList<>();
        for (Row r : rows) out.add(new Packets.SaveRow(r.orig, r.orig >= 0 ? ItemStack.EMPTY : r.stack, r.count, r.weight, r.rarity));
        HzNet.toServer(new Packets.EditorSave(type.id, version, out));
        dirty = false;
        onClose();
    }

    @Override
    public void onClose() {
        if (picking) {
            picking = false;
            bind();
            return;
        }
        if (dirty && ticks - confirmExitAt > 60) {
            confirmExitAt = ticks;
            return;
        }
        super.onClose();
    }

    private static int parseInt(String v, int def) {
        try {
            return v.isEmpty() ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parsePct(String v) {
        if (v.isEmpty() || v.endsWith(".") || v.endsWith(",")) return -1;
        try {
            return Double.parseDouble(v.replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String fmtPct(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return CardArt.french() ? s.replace('.', ',') : s;
    }

    // =================================================================== boucle et entrees
    @Override
    public void tick() {
        ticks++;
        for (Slot s : slots) {
            s.count.tick();
            s.pct.tick();
            if (s.bound < 0) continue;
            // valeurs remises au propre quand on quitte le champ
            if (!s.pct.isFocused()) {
                String want = fmtPct(chance(s.bound));
                if (!s.pct.getValue().equals(want)) s.pct.setValue(want);
            }
            if (!s.count.isFocused()) {
                String want = String.valueOf(rows.get(s.bound).count);
                if (!s.count.getValue().equals(want)) s.count.setValue(want);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int hoveredRow(double mx, double my) {
        if (mx < left + 4 || mx >= left + pw - 4 || my < rowsTop() || my >= rowsTop() + visibleRows() * ROW_H) return -1;
        int r = scroll + (int) ((my - rowsTop()) / ROW_H);
        return r < rows.size() ? r : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picking) {
            int[] box = pickerBox();
            Inventory inv = minecraft.player.getInventory();
            for (int i = 0; i < 36; i++) {
                int[] p = pickerSlot(box, i);
                if (mx >= p[0] && mx < p[0] + 18 && my >= p[1] && my < p[1] + 18) {
                    addFromInventory(inv.getItem(i));
                    return true;
                }
            }
            if (mx < box[0] || mx >= box[0] + box[2] || my < box[1] || my >= box[1] + box[3]) {
                picking = false;
                bind();
            }
            return true;
        }
        int r = hoveredRow(mx, my);
        if (r >= 0 && mx >= colDelete - 2 && mx < colDelete + 12) {
            deleteRow(r);
            return true;
        }
        boolean hit = super.mouseClicked(mx, my, button);
        if (!hit) setFocused(null);
        return hit;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!picking && my >= rowsTop() && my < rowsTop() + visibleRows() * ROW_H) {
            int before = scroll;
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
            if (scroll != before) {
                setFocused(null);
                bind();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (getFocused() instanceof HzField) {
                setFocused(null);
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // =================================================================== rendu
    @Override
    public void renderBackground(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, 0xC4050608, 0xDC050608);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int glow = type.glow & 0xFFFFFF;
        CardArt.soft(g, left, top, pw, ph, 0xF00B0C0F);
        CardArt.softOutline(g, left + 1, top + 1, pw - 2, ph - 2, Ui.argb(0xFFFFFF, 16));
        Ui.hairline(g, left + 1, left + pw - 1, top, glow, 200, true, true);

        // en-tete : nom de la caisse en capitales, nombre d'objets et total des chances, pastille « Editeur · staff »
        String nom = type.crateName().getString().toUpperCase(Locale.ROOT);
        g.pose().pushPose();
        g.pose().translate(left + 14, top + 10, 0);
        g.pose().scale(1.1f, 1.1f, 1);
        Ui.spaced(g, font, nom, Ui.BOLD, 0, 0, Ui.argb(Ui.TEXT, 255), 1.5f);
        g.pose().popPose();
        long tot = total();
        String resume = Component.translatable("gui.heralizcrates.editor.summary", rows.size(), rows.isEmpty() ? "0" : fmtPct(tot > 0 ? 100.0 : 0)).getString();
        g.drawString(font, Ui.lit(resume, CardArt.MINI), left + 14, top + 25, Ui.argb(Ui.MUTED, 255), false);
        String badge = Component.translatable("gui.heralizcrates.editor.badge").getString().toUpperCase(Locale.ROOT);
        int badgeW = Math.round(Ui.spacedWidth(font, badge, CardArt.MINI_GRAS, 0.8f)) + 10, badgeX = left + pw - 14 - badgeW;
        CardArt.soft(g, badgeX, top + 11, badgeW, 10, Ui.argb(glow, 34));
        CardArt.softOutline(g, badgeX + 1, top + 12, badgeW - 2, 8, Ui.argb(glow, 110));
        Ui.spaced(g, font, badge, CardArt.MINI_GRAS, badgeX + 5, top + 12.5f, Ui.argb(Ui.mix(glow, 0xFFFFFF, 0.3f), 255), 0.8f);

        // en-tetes de colonnes
        int hy = top + 40;
        int faint = Ui.argb(Ui.FAINT, 255);
        Ui.spaced(g, font, Component.translatable("gui.heralizcrates.editor.col.item").getString(), CardArt.MINI, left + 34, hy, faint, 1.0f);
        Ui.spaced(g, font, Component.translatable("gui.heralizcrates.editor.col.count").getString(), CardArt.MINI, colCount + 1, hy, faint, 1.0f);
        Ui.spaced(g, font, Component.translatable("gui.heralizcrates.editor.col.rarity").getString(), CardArt.MINI, colRarity + 1, hy, faint, 1.0f);
        Ui.spaced(g, font, Component.translatable("gui.heralizcrates.editor.col.chance").getString(), CardArt.MINI, colPct + 1, hy, faint, 1.0f);

        // lignes
        int rt = rowsTop();
        int hovered = picking ? -1 : hoveredRow(mouseX, mouseY);
        if (rows.isEmpty()) {
            Ui.centered(g, font, Ui.tr(Ui.SMALL, "gui.heralizcrates.editor.empty"), left + pw / 2f, rt + 22, Ui.argb(Ui.MUTED, 255));
        }
        for (Slot s : slots) {
            if (s.bound < 0) continue;
            Row row = rows.get(s.bound);
            int y = rt + (s.bound - scroll) * ROW_H;
            int col = CardArt.color(row.rarity);
            if (s.bound == hovered) {
                CardArt.soft(g, left + 6, y, pw - 12, ROW_H, Ui.argb(0xFFFFFF, 11));
                CardArt.softOutline(g, left + 7, y + 1, pw - 14, ROW_H - 2, Ui.argb(0xFFFFFF, 23));
            }
            g.fill(left + 9, y + 4, left + 10, y + ROW_H - 4, Ui.argb(col, 255));
            CardArt.item(g, row.stack, left + 22, y + ROW_H / 2f, 16);
            int nameX = left + 34, nameW = colCount - nameX - 8;
            String name = row.stack.getHoverName().getString();
            String shown = font.plainSubstrByWidth(name, nameW);
            Component nameC = Ui.lit(shown, Ui.FONT);
            g.drawString(font, nameC, nameX, y + 6, row.missing ? Ui.argb(Ui.DANGER, 255) : Ui.argb(Ui.TEXT, 255), false);
            String tagKey = row.missing ? "gui.heralizcrates.editor.missing" : row.commandOnly ? "gui.heralizcrates.editor.command"
                    : row.orig < 0 ? "gui.heralizcrates.editor.new" : null;
            if (tagKey != null) {
                Component tag = Ui.tr(Ui.SMALL, tagKey);
                int tx = nameX + font.width(nameC) + 6;
                if (tx + font.width(tag) < colCount - 6) {
                    g.drawString(font, tag, tx, y + 7, row.orig < 0 ? Ui.argb(Ui.OK, 230) : Ui.argb(Ui.MUTED, 200), false);
                }
            }
            if (s.bound == hovered) {
                boolean on = mouseX >= colDelete - 2 && mouseX < colDelete + 12;
                g.drawString(font, Ui.lit("×", Ui.BOLD), colDelete + 2, y + 5, Ui.argb(0xFF8A80, on ? 255 : 160), false);
            }
        }
        // barre de defilement
        int max = maxScroll();
        if (max > 0) {
            int h = visibleRows() * ROW_H;
            int bx = left + pw - 4;
            int th = Math.max(10, h * visibleRows() / rows.size());
            int ty = rt + (h - th) * scroll / max;
            g.fill(bx, ty, bx + 1, ty + th, Ui.argb(glow, 200));
        }

        // pied : etat des modifications
        Ui.hairline(g, left + 14, left + pw - 14, top + ph - FOOT_H + 1, 0xFFFFFF, 18, true, true);
        Component state = null;
        int stateColor = 0;
        if (dirty && ticks - confirmExitAt <= 60) {
            state = Ui.tr(Ui.SMALL, "gui.heralizcrates.editor.confirm_exit");
            stateColor = Ui.argb(Ui.DANGER, 230);
        } else if (dirty) {
            state = Ui.tr(Ui.SMALL, "gui.heralizcrates.editor.dirty");
            stateColor = Ui.argb(0xFFC83A, 220);
        }
        // « + Ajouter un objet » en capitales vertes
        String ajout = Component.translatable("gui.heralizcrates.editor.add").getString().toUpperCase(Locale.ROOT);
        int aw = Math.round(Ui.spacedWidth(font, ajout, CardArt.MINI_GRAS, 1.2f));
        addBtn.setBounds(left + 14, top + ph - 22, aw, 12);
        boolean addHover = addBtn.isHoveredOrFocused() && !picking;
        Ui.spaced(g, font, ajout, CardArt.MINI_GRAS, left + 14, top + ph - 20, Ui.argb(addHover ? 0xCFF3DA : 0x9BE3B3, 255), 1.2f);
        if (state != null) {
            int sx = saveBtn.getX() - 10 - font.width(state);
            if (sx > left + 14 + aw + 16) {
                g.drawString(font, state, sx, top + ph - 21, stateColor, false);
            }
        }

        super.render(g, mouseX, mouseY, partial);

        if (picking) {
            renderPicker(g, mouseX, mouseY);
            return;
        }
        // infobulle de l'objet d'une ligne
        if (hovered >= 0 && mouseX >= left + 12 && mouseX < colCount - 4) {
            Row row = rows.get(hovered);
            List<Component> tip = new ArrayList<>();
            tip.add(Ui.in(row.stack.getHoverName(), Ui.BOLD));
            List<Component> vanilla = getTooltipFromItem(minecraft, row.stack);
            for (int i = 1; i < vanilla.size() && i < 8; i++) {
                tip.add(Ui.in(Component.empty().append(vanilla.get(i)).withStyle(st -> st.withColor(Ui.MUTED)), Ui.SMALL));
            }
            if (!row.itemId.isEmpty()) tip.add(Ui.lit(row.itemId, Ui.SMALL).withStyle(st -> st.withColor(Ui.FAINT)));
            Ui.tooltip(g, font, tip, mouseX, mouseY, CardArt.color(row.rarity));
        }
    }

    private int[] pickerBox() {
        Component title = Ui.tr(Ui.SMALL, "gui.heralizcrates.editor.pick");
        int w = Math.max(9 * 18 + 24, font.width(title) + 24);
        int h = 4 * 18 + 4 + 36;
        return new int[]{(width - w) / 2, (height - h) / 2, w, h};
    }

    private int[] pickerSlot(int[] box, int i) {
        // rangees 1 a 3 : inventaire principal ; derniere rangee : barre d'objets (comme l'inventaire vanilla)
        int row = i < 9 ? 3 : (i - 9) / 9, col = i % 9;
        return new int[]{box[0] + (box[2] - 9 * 18) / 2 + col * 18, box[1] + 26 + row * 18 + (i < 9 ? 4 : 0)};
    }

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        int[] box = pickerBox();
        int glow = type.glow & 0xFFFFFF;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, width, height, 0x90000000);
        CardArt.soft(g, box[0], box[1], box[2], box[3], 0xF50B0C0F);
        CardArt.softOutline(g, box[0] + 1, box[1] + 1, box[2] - 2, box[3] - 2, Ui.argb(0xFFFFFF, 18));
        Ui.hairline(g, box[0] + 1, box[0] + box[2] - 1, box[1], glow, 200, true, true);
        Component title = Ui.tr(Ui.SMALL, "gui.heralizcrates.editor.pick");
        Ui.centered(g, font, title, box[0] + box[2] / 2f, box[1] + 10, Ui.argb(Ui.MUTED, 255));
        g.pose().popPose();
        Inventory inv = minecraft.player.getInventory();
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) {
            int[] p = pickerSlot(box, i);
            boolean hov = mouseX >= p[0] && mouseX < p[0] + 18 && mouseY >= p[1] && mouseY < p[1] + 18;
            ItemStack st = inv.getItem(i);
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            CardArt.soft(g, p[0] + 1, p[1] + 1, 16, 16, hov && !st.isEmpty() ? Ui.argb(glow, 70) : Ui.argb(0xFFFFFF, 16));
            if (!st.isEmpty()) {
                g.renderItem(st, p[0] + 1, p[1] + 1);
                g.renderItemDecorations(font, st, p[0] + 1, p[1] + 1);
            }
            g.pose().popPose();
            if (hov && !st.isEmpty()) hovered = st;
        }
        if (!hovered.isEmpty()) {
            List<Component> tip = new ArrayList<>();
            tip.add(Ui.in(hovered.getHoverName(), Ui.BOLD));
            List<Component> vanilla = getTooltipFromItem(minecraft, hovered);
            for (int i = 1; i < vanilla.size() && i < 8; i++) {
                tip.add(Ui.in(Component.empty().append(vanilla.get(i)).withStyle(st -> st.withColor(Ui.MUTED)), Ui.SMALL));
            }
            Ui.tooltip(g, font, tip, mouseX, mouseY, glow);
        }
    }
}
