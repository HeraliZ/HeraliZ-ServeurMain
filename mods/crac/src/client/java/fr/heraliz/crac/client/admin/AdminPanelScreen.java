package fr.heraliz.crac.client.admin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.client.ScaledScreen;
import fr.heraliz.crac.client.group.ConfirmScreen;
import fr.heraliz.crac.client.group.GroupTheme;
import fr.heraliz.crac.network.C2SAdminAction;
import fr.heraliz.crac.network.C2SAdminConfig;
import fr.heraliz.crac.network.C2SAdminPlacable;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CAdminData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AdminPanelScreen extends ScaledScreen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 250;
    /** Translation keys of the tabs (assets/crac/lang). */
    private static final String[] TABS = new String[]{
        "crac.admin.tab.groups", "crac.admin.tab.claims", "crac.admin.tab.players", "crac.admin.tab.config", "crac.admin.tab.placable"
    };
    private static final int TAB_CONFIG = 3;
    private static final int TAB_PLACABLE = 4;
    private static final int ROW_H = 20;
    private static final int CFG_ROW_H = 16;
    private static final int TYPE_BOOL = 0;
    private static final int TYPE_LIST = 4;
    private int left;
    private int top;
    private int tab = 0;
    private int scroll = 0;
    private UUID viewingGroup = null;
    private final Map<String, String> edits = new LinkedHashMap<>();
    private int editingRow = -1;
    private String editBuffer = "";
    private String cfgSection = null;
    private String expandedList = null;
    private List<AdminPanelScreen.PlacRow> placRows = null;
    private boolean placAddingId = false;
    private static final int SUB_Y = 46;
    private static final int SUB_H = 14;
    private static final int SUB_PAD = 8;
    private static final int ENTRY_SELF = -1;
    private static final int ENTRY_ADD = -2;
    /** Serverbound custom payloads are capped at 32767 bytes: config edits go in batches below that. */
    private static final int EDIT_BATCH_BYTES = 30000;

    public AdminPanelScreen() {
        super(Component.translatable("crac.admin.panel_title"));
    }

    public void onDataRefreshed() {
        if (this.viewingGroup != null && this.findGroup(this.viewingGroup) == null) {
            this.viewingGroup = null;
        }
    }

    @Override
    protected void init() {
        this.left = (this.guiW() - 420) / 2;
        this.top = (this.guiH() - 250) / 2;
    }

    private boolean configMode() {
        return this.tab == 3 && this.viewingGroup == null;
    }

    private boolean placableMode() {
        return this.tab == 4 && this.viewingGroup == null;
    }

    private int contentTop() {
        if (this.placableMode()) {
            return this.top + 46 + 14 + 6;
        } else if (!this.configMode()) {
            return this.top + 48;
        } else {
            List<AdminPanelScreen.SubTab> tabs = this.subTabs();
            int lastY = tabs.isEmpty() ? this.top + 46 : tabs.get(tabs.size() - 1).y();
            return lastY + 14 + 6;
        }
    }

    private int contentHeight() {
        return this.top + 250 - 14 - this.contentTop();
    }

    private int rowHeight() {
        return !this.configMode() && !this.placableMode() ? 20 : 16;
    }

    private int[] applyRect() {
        return new int[]{this.left + 420 - 14 - 46, this.top + 46, 46, 14};
    }

    private int[] refreshRect() {
        return new int[]{this.left + 420 - 14 - 46 - 4 - 52, this.top + 46, 52, 14};
    }

    private int[] backRect() {
        return new int[]{this.left + 420 - 60, this.top + 26, 46, 16};
    }

    private List<AdminPanelScreen.SubTab> subTabs() {
        List<String> keys = new ArrayList<>();

        for (S2CAdminData.ConfigInfo c : this.configRows()) {
            int dot = c.path().indexOf(46);
            String key = dot < 0 ? c.path() : c.path().substring(0, dot);
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }

        List<AdminPanelScreen.SubTab> out = new ArrayList<>();
        int x = this.left + 14;
        int y = this.top + 46;
        int firstRowLimit = this.refreshRect()[0] - 6;
        int fullLimit = this.left + 420 - 14;

        for (String key : keys) {
            String label = pretty(key);
            int w = this.font.width(label) + 16;
            int limit = y == this.top + 46 ? firstRowLimit : fullLimit;
            if (x + w > limit && x > this.left + 14) {
                x = this.left + 14;
                y += 14;
            }

            out.add(new AdminPanelScreen.SubTab(key, label, x, y, w));
            x += w + 2;
        }

        return out;
    }

    private static String pretty(String key) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(ch));
            } else if (ch == '_') {
                sb.append(' ');
            } else if (Character.isUpperCase(ch)) {
                sb.append(' ').append(ch);
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    private String activeSection() {
        List<AdminPanelScreen.SubTab> tabs = this.subTabs();
        if (tabs.isEmpty()) {
            return null;
        } else {
            for (AdminPanelScreen.SubTab t : tabs) {
                if (t.key().equals(this.cfgSection)) {
                    return this.cfgSection;
                }
            }

            return tabs.get(0).key();
        }
    }

    private List<S2CAdminData.ConfigInfo> sectionRows() {
        String sec = this.activeSection();
        if (sec == null) {
            return List.of();
        } else {
            List<S2CAdminData.ConfigInfo> out = new ArrayList<>();

            for (S2CAdminData.ConfigInfo c : this.configRows()) {
                if (c.path().equals(sec) || c.path().startsWith(sec + ".")) {
                    out.add(c);
                }
            }

            return out;
        }
    }

    private int[] openRect(int rowY) {
        return new int[]{this.left + 420 - 58, rowY + 1, 40, 12};
    }

    private int[] entryRemoveRect(int rowY) {
        return new int[]{this.left + 420 - 26, rowY + 1, 12, 12};
    }

    private List<AdminPanelScreen.CfgRow> visibleRows() {
        List<AdminPanelScreen.CfgRow> out = new ArrayList<>();

        for (S2CAdminData.ConfigInfo c : this.sectionRows()) {
            out.add(new AdminPanelScreen.CfgRow(c, -1));
            if (c.type() == 4 && c.path().equals(this.expandedList)) {
                int n = this.listEntries(c).size();

                for (int i = 0; i < n; i++) {
                    out.add(new AdminPanelScreen.CfgRow(c, i));
                }

                out.add(new AdminPanelScreen.CfgRow(c, -2));
            }
        }

        return out;
    }

    private List<String> listEntries(S2CAdminData.ConfigInfo c) {
        List<String> out = new ArrayList<>();

        for (String part : this.valueOf(c).split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }

        return out;
    }

    private static String entryKey(String entry) {
        int eq = entry.lastIndexOf(61);
        return eq <= 0 ? null : entry.substring(0, eq).trim();
    }

    private static String entryValue(String entry) {
        int eq = entry.lastIndexOf(61);
        return eq < 0 ? entry.trim() : entry.substring(eq + 1).trim();
    }

    private void writeEntries(S2CAdminData.ConfigInfo c, List<String> entries) {
        this.setEdit(c, String.join(", ", entries));
    }

    private void setListEntry(S2CAdminData.ConfigInfo c, int idx, String rawValue) {
        List<String> entries = this.listEntries(c);
        if (idx >= 0 && idx < entries.size()) {
            String key = entryKey(entries.get(idx));
            String v = rawValue.trim();
            entries.set(idx, key == null ? v : key + "=" + v);
            this.writeEntries(c, entries);
        }
    }

    private void removeListEntry(S2CAdminData.ConfigInfo c, int idx) {
        List<String> entries = this.listEntries(c);
        if (idx >= 0 && idx < entries.size()) {
            entries.remove(idx);
            this.writeEntries(c, entries);
        }
    }

    private String shortLabel(S2CAdminData.ConfigInfo c) {
        String sec = this.activeSection();
        return sec != null && c.path().startsWith(sec + ".") ? c.path().substring(sec.length() + 1) : c.path();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float pt) {
        this.renderBackground(g);
        GroupTheme.panel(g, this.left, this.top, 420, 250);
        g.drawString(this.font, I18n.get("crac.admin.panel_title"), this.left + 14, this.top + 10, -6504193);
        int tw = 74;

        for (int i = 0; i < TABS.length; i++) {
            int tx = this.left + 14 + i * (tw + 4);
            int ty = this.top + 26;
            boolean active = i == this.tab;
            g.fill(tx, ty, tx + tw, ty + 16, active ? -14996400 : -16116954);
            GroupTheme.border(g, tx, ty, tw, 16, active ? -11634488 : -14536366);
            g.drawCenteredString(this.font, I18n.get(TABS[i]), tx + tw / 2, ty + 4, active ? -1 : -9536363);
        }

        if (this.tab == 3 && this.viewingGroup == null) {
            this.renderConfigTab(g, mouseX, mouseY);
        } else if (this.tab == 4 && this.viewingGroup == null) {
            this.renderPlacableTab(g, mouseX, mouseY);
        } else {
            this.renderListTab(g, mouseX, mouseY);
        }

        if (this.viewingGroup != null) {
            int[] b = this.backRect();
            boolean hov = in(mouseX, mouseY, b);
            GroupTheme.button(g, this.font, b[0], b[1], b[2], b[3], I18n.get("crac.gui.back"), hov ? -2074037 : -4179669);
        }

        this.renderWidgets(g, mouseX, mouseY, pt);
    }

    private void renderListTab(GuiGraphics g, int mouseX, int mouseY) {
        List<AdminPanelScreen.Row> rows = this.buildRows();
        int areaY = this.contentTop();
        int areaH = this.contentHeight();
        this.clampScroll(rows.size(), 20, areaH);
        this.scissor(g, this.left + 8, areaY, this.left + 420 - 8, areaY + areaH);

        for (int i = 0; i < rows.size(); i++) {
            int ry = areaY + i * 20 - this.scroll;
            if (ry + 20 >= areaY && ry <= areaY + areaH) {
                AdminPanelScreen.Row row = rows.get(i);
                boolean hovRow = mouseX >= this.left + 10 && mouseX < this.left + 420 - 10 && mouseY >= ry && mouseY < ry + 20 - 2;
                g.fill(this.left + 10, ry, this.left + 420 - 10, ry + 20 - 2, hovRow ? -14996402 : -16116954);
                g.drawString(this.font, row.text(), this.left + 16, ry + 5, row.textColor());
                int bx = this.left + 420 - 14;

                for (AdminPanelScreen.RowButton btn : row.buttons()) {
                    int bw = this.font.width(btn.label()) + 12;
                    bx -= bw;
                    boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= ry + 2 && mouseY < ry + 20 - 4;
                    GroupTheme.button(g, this.font, bx, ry + 2, bw, 14, btn.label(), hov ? btn.hoverColor() : btn.color());
                    bx -= 4;
                }
            }
        }

        g.disableScissor();
        if (rows.isEmpty()) {
            g.drawCenteredString(this.font, I18n.get("crac.admin.nothing"), this.left + 210, areaY + areaH / 2 - 4, -9536363);
        }

        this.drawScrollbar(g, rows.size(), 20, areaY, areaH);
    }

    private List<S2CAdminData.ConfigInfo> configRows() {
        S2CAdminData d = AdminClientState.data;
        return d == null ? List.of() : d.config();
    }

    private String valueOf(S2CAdminData.ConfigInfo c) {
        return this.edits.getOrDefault(c.path(), c.value());
    }

    private void renderConfigTab(GuiGraphics g, int mouseX, int mouseY) {
        String active = this.activeSection();

        for (AdminPanelScreen.SubTab t : this.subTabs()) {
            boolean on = t.key().equals(active);
            boolean hov = mouseX >= t.x() && mouseX < t.x() + t.w() && mouseY >= t.y() && mouseY < t.y() + 14;
            g.drawString(this.font, t.label(), t.x() + 8, t.y() + 3, on ? -1 : (hov ? -3878166 : -9536363), false);
            if (on) {
                g.fill(t.x() + 4, t.y() + 14 - 1, t.x() + t.w() - 4, t.y() + 14, -11634488);
            }
        }

        List<AdminPanelScreen.CfgRow> rows = this.visibleRows();
        int areaY = this.contentTop();
        int areaH = this.contentHeight();
        this.clampScroll(rows.size(), 16, areaH);
        int[] ap = this.applyRect();
        int[] rf = this.refreshRect();
        boolean dirty = !this.edits.isEmpty();
        GroupTheme.button(g, this.font, rf[0], rf[1], rf[2], rf[3], I18n.get("crac.admin.refresh"), in(mouseX, mouseY, rf) ? -1 : -11634488);
        GroupTheme.button(
            g, this.font, ap[0], ap[1], ap[2], ap[3], dirty ? I18n.get("crac.admin.apply") + "*" : I18n.get("crac.admin.apply"), !dirty ? -11181958 : (in(mouseX, mouseY, ap) ? -10757772 : -12598694)
        );
        int valueX = this.left + 420 - 150;
        this.scissor(g, this.left + 8, areaY, this.left + 420 - 8, areaY + areaH);

        for (int i = 0; i < rows.size(); i++) {
            int ry = areaY + i * 16 - this.scroll;
            if (ry + 16 >= areaY && ry <= areaY + areaH) {
                AdminPanelScreen.CfgRow row = rows.get(i);
                S2CAdminData.ConfigInfo c = row.cfg();
                boolean hov = mouseX >= this.left + 10 && mouseX < this.left + 420 - 10 && mouseY >= ry && mouseY < ry + 16 - 2;
                boolean editing = i == this.editingRow;
                boolean changed = this.edits.containsKey(c.path());
                g.fill(this.left + 10, ry, this.left + 420 - 10, ry + 16 - 2, !hov && !editing ? -16116954 : -14996402);
                if (row.entry() >= 0) {
                    List<String> entries = this.listEntries(c);
                    if (row.entry() < entries.size()) {
                        String entry = entries.get(row.entry());
                        String key = entryKey(entry);
                        String label = key != null ? key : "#" + (row.entry() + 1);
                        g.drawString(this.font, this.trim(label, valueX - (this.left + 26) - 6), this.left + 26, ry + 4, -9536363);
                        int[] rm = this.entryRemoveRect(ry);
                        int vw = rm[0] - 6 - valueX;
                        String shown = editing ? this.editBuffer + "_" : entryValue(entry);
                        g.drawString(this.font, this.trimEnd(shown, vw), valueX, ry + 4, editing ? -1 : (changed ? -12598694 : -3878166));
                        GroupTheme.button(g, this.font, rm[0], rm[1], rm[2], rm[3], "x", in(mouseX, mouseY, rm) ? -2074037 : -4179669);
                    }
                } else if (row.entry() == -2) {
                    String text = editing ? "+ " + this.editBuffer + "_" : "+ " + I18n.get("crac.admin.add_entry");
                    g.drawString(this.font, text, this.left + 26, ry + 4, editing ? -1 : -12598694);
                } else {
                    String path = this.trim(this.shortLabel(c), valueX - (this.left + 16) - 6);
                    g.drawString(this.font, path, this.left + 16, ry + 4, -9536363);
                    String shown = editing ? this.editBuffer : this.valueOf(c);
                    int vw = this.left + 420 - 16 - valueX;
                    int col = editing ? -1 : (changed ? -12598694 : -3878166);
                    if (c.type() == 4) {
                        int n = this.listEntries(c).size();
                        boolean open = c.path().equals(this.expandedList);
                        g.drawString(this.font, I18n.get(n == 1 ? "crac.admin.entry_one" : "crac.admin.entries", n), valueX, ry + 4, changed ? -12598694 : -9536363);
                        int[] ob = this.openRect(ry);
                        GroupTheme.button(g, this.font, ob[0], ob[1], ob[2], ob[3], I18n.get(open ? "crac.admin.close" : "crac.admin.open"), in(mouseX, mouseY, ob) ? -1 : -11634488);
                    } else if (c.type() == 0 && !editing) {
                        boolean on = Boolean.parseBoolean(shown);
                        g.drawString(this.font, on ? "true" : "false", valueX, ry + 4, on ? -12598694 : -4179669);
                    } else {
                        String text = editing ? shown + "_" : shown;
                        g.drawString(this.font, this.trimEnd(text, vw), valueX, ry + 4, col);
                    }
                }
            }
        }

        g.disableScissor();
        this.drawScrollbar(g, rows.size(), 16, areaY, areaH);
        if (this.editingRow >= 0) {
            g.drawCenteredString(this.font, I18n.get("crac.admin.edit_hint"), this.left + 210, this.top + 250 - 11, -9536363);
        } else if (dirty) {
            g.drawCenteredString(
                this.font, I18n.get(this.edits.size() == 1 ? "crac.admin.unsaved_one" : "crac.admin.unsaved", this.edits.size()), this.left + 210, this.top + 250 - 11, -12598694
            );
        }
    }

    private int[] placApplyRect() {
        return new int[]{this.left + 420 - 14 - 46, this.top + 46, 46, 14};
    }

    private int[] placRefreshRect() {
        return new int[]{this.left + 420 - 14 - 46 - 4 - 52, this.top + 46, 52, 14};
    }

    private int[] placToggleRect(int rowY) {
        return new int[]{this.left + 420 - 74, rowY + 1, 30, 12};
    }

    private int[] placRemoveRect(int rowY) {
        return new int[]{this.left + 420 - 26, rowY + 1, 12, 12};
    }

    private List<AdminPanelScreen.PlacRow> placList() {
        if (this.placRows != null) {
            return this.placRows;
        } else {
            List<AdminPanelScreen.PlacRow> out = new ArrayList<>();
            S2CAdminData d = AdminClientState.data;
            if (d != null) {
                for (S2CAdminData.PlacableInfo p : d.placables()) {
                    out.add(new AdminPanelScreen.PlacRow(p.id(), p.hp(), p.enabled()));
                }
            }

            return out;
        }
    }

    private List<AdminPanelScreen.PlacRow> placEditable() {
        if (this.placRows == null) {
            this.placRows = this.placList();
        }

        return this.placRows;
    }

    private void renderPlacableTab(GuiGraphics g, int mouseX, int mouseY) {
        List<AdminPanelScreen.PlacRow> rows = this.placList();
        int areaY = this.contentTop();
        int areaH = this.contentHeight();
        this.clampScroll(rows.size() + 1, 16, areaH);
        boolean dirty = this.placRows != null;
        int[] ap = this.placApplyRect();
        int[] rf = this.placRefreshRect();
        GroupTheme.button(g, this.font, rf[0], rf[1], rf[2], rf[3], I18n.get("crac.admin.refresh"), in(mouseX, mouseY, rf) ? -1 : -11634488);
        GroupTheme.button(
            g, this.font, ap[0], ap[1], ap[2], ap[3], dirty ? I18n.get("crac.admin.apply") + "*" : I18n.get("crac.admin.apply"), !dirty ? -11181958 : (in(mouseX, mouseY, ap) ? -10757772 : -12598694)
        );
        int hpX = this.left + 420 - 150;
        this.scissor(g, this.left + 8, areaY, this.left + 420 - 8, areaY + areaH);

        for (int i = 0; i <= rows.size(); i++) {
            int ry = areaY + i * 16 - this.scroll;
            if (ry + 16 >= areaY && ry <= areaY + areaH) {
                boolean hov = mouseX >= this.left + 10 && mouseX < this.left + 420 - 10 && mouseY >= ry && mouseY < ry + 16 - 2;
                boolean editing = i == this.editingRow;
                g.fill(this.left + 10, ry, this.left + 420 - 10, ry + 16 - 2, !hov && !editing ? -16116954 : -14996402);
                if (i == rows.size()) {
                    String text = editing ? "+ " + this.editBuffer + "_" : "+ " + I18n.get("crac.admin.add_block");
                    g.drawString(this.font, text, this.left + 16, ry + 4, editing ? -1 : -12598694);
                } else {
                    AdminPanelScreen.PlacRow r = rows.get(i);
                    g.drawString(this.font, this.trim(r.id, hpX - (this.left + 16) - 6), this.left + 16, ry + 4, r.enabled ? -3878166 : -9536363);
                    String hp = editing ? this.editBuffer + "_" : String.valueOf(r.hp);
                    g.drawString(this.font, hp, hpX, ry + 4, editing ? -1 : (r.enabled ? -3878166 : -9536363));
                    int[] tg = this.placToggleRect(ry);
                    GroupTheme.button(
                        g,
                        this.font,
                        tg[0],
                        tg[1],
                        tg[2],
                        tg[3],
                        I18n.get(r.enabled ? "crac.admin.on" : "crac.admin.off"),
                        r.enabled ? (in(mouseX, mouseY, tg) ? -10757772 : -12598694) : -11181958
                    );
                    int[] rm = this.placRemoveRect(ry);
                    GroupTheme.button(g, this.font, rm[0], rm[1], rm[2], rm[3], "x", in(mouseX, mouseY, rm) ? -2074037 : -4179669);
                }
            }
        }

        g.disableScissor();
        this.drawScrollbar(g, rows.size() + 1, 16, areaY, areaH);
        if (this.editingRow >= 0) {
            g.drawCenteredString(this.font, I18n.get("crac.admin.edit_hint"), this.left + 210, this.top + 250 - 11, -9536363);
        } else if (dirty) {
            g.drawCenteredString(this.font, I18n.get("crac.admin.unsaved_apply"), this.left + 210, this.top + 250 - 11, -12598694);
        } else {
            g.drawCenteredString(
                this.font, I18n.get("crac.admin.placable_hint", rows.size()), this.left + 210, this.top + 250 - 11, -9536363
            );
        }
    }

    private boolean placableClicked(double mx, double my) {
        if (in(mx, my, this.placRefreshRect())) {
            this.stopEditing(false);
            this.placRows = null;
            this.send(C2SAdminAction.of(0));
            return true;
        } else if (!in(mx, my, this.placApplyRect())) {
            List<AdminPanelScreen.PlacRow> rows = this.placList();
            int areaY = this.contentTop();
            int areaH = this.contentHeight();

            for (int i = 0; i <= rows.size(); i++) {
                int ry = areaY + i * 16 - this.scroll;
                if (ry + 16 >= areaY && ry <= areaY + areaH && !(mx < this.left + 10) && !(mx >= this.left + 420 - 10) && !(my < ry) && !(my >= ry + 16 - 2)) {
                    if (i == rows.size()) {
                        this.stopEditing(true);
                        this.editingRow = i;
                        this.editBuffer = "";
                        this.placAddingId = true;
                        return true;
                    }

                    if (in(mx, my, this.placRemoveRect(ry))) {
                        this.stopEditing(true);
                        this.placEditable().remove(i);
                        return true;
                    }

                    if (in(mx, my, this.placToggleRect(ry))) {
                        this.stopEditing(true);
                        AdminPanelScreen.PlacRow r = this.placEditable().get(i);
                        r.enabled = !r.enabled;
                        return true;
                    }

                    this.stopEditing(true);
                    this.editingRow = i;
                    this.editBuffer = String.valueOf(rows.get(i).hp);
                    this.placAddingId = false;
                    return true;
                }
            }

            return false;
        } else {
            this.stopEditing(true);
            if (this.placRows != null) {
                List<String> ids = new ArrayList<>();
                List<Integer> hp = new ArrayList<>();
                List<Boolean> en = new ArrayList<>();

                for (AdminPanelScreen.PlacRow r : this.placRows) {
                    ids.add(r.id);
                    hp.add(r.hp);
                    en.add(r.enabled);
                }

                NetworkHandler.CHANNEL.sendToServer(new C2SAdminPlacable(ids, hp, en));
                this.placRows = null;
            }

            return true;
        }
    }

    private void stopEditingPlacable(boolean commit) {
        if (commit) {
            if (this.placAddingId) {
                String id = this.editBuffer.trim();
                if (!id.isEmpty()) {
                    if (!id.contains(":")) {
                        id = "minecraft:" + id;
                    }

                    this.placEditable().add(new AdminPanelScreen.PlacRow(id, 100, true));
                }
            } else {
                List<AdminPanelScreen.PlacRow> rows = this.placEditable();
                if (this.editingRow >= 0 && this.editingRow < rows.size()) {
                    try {
                        rows.get(this.editingRow).hp = Math.max(1, Integer.parseInt(this.editBuffer.trim()));
                    } catch (NumberFormatException var4) {
                    }
                }
            }
        }

        this.editingRow = -1;
        this.editBuffer = "";
        this.placAddingId = false;
    }

    private void clampScroll(int count, int rowH, int areaH) {
        int maxScroll = Math.max(0, count * rowH - areaH);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
    }

    private void drawScrollbar(GuiGraphics g, int count, int rowH, int areaY, int areaH) {
        int total = count * rowH;
        if (total > areaH) {
            int trackX = this.left + 420 - 7;
            g.fill(trackX, areaY, trackX + 3, areaY + areaH, 1073741824);
            int barH = Math.max(12, areaH * areaH / total);
            int maxScroll = total - areaH;
            int barY = areaY + (int)((areaH - barH) * ((double)this.scroll / maxScroll));
            g.fill(trackX, barY, trackX + 3, barY + barH, -11634488);
        }
    }

    private String trim(String s, int maxWidth) {
        return maxWidth > 0 && this.font.width(s) > maxWidth ? this.font.plainSubstrByWidth(s, Math.max(0, maxWidth - this.font.width("..."))) + "..." : s;
    }

    private String trimEnd(String s, int maxWidth) {
        if (maxWidth > 0 && this.font.width(s) > maxWidth) {
            String out = s;

            while (!out.isEmpty() && this.font.width("..." + out) > maxWidth) {
                out = out.substring(1);
            }

            return "..." + out;
        } else {
            return s;
        }
    }

    private static boolean in(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private S2CAdminData.GroupInfo findGroup(UUID id) {
        S2CAdminData d = AdminClientState.data;
        if (d == null) {
            return null;
        } else {
            for (S2CAdminData.GroupInfo g : d.groups()) {
                if (g.id().equals(id)) {
                    return g;
                }
            }

            return null;
        }
    }

    private List<AdminPanelScreen.Row> buildRows() {
        List<AdminPanelScreen.Row> rows = new ArrayList<>();
        S2CAdminData d = AdminClientState.data;
        if (d == null) {
            return rows;
        } else if (this.viewingGroup != null) {
            S2CAdminData.GroupInfo g = this.findGroup(this.viewingGroup);
            if (g == null) {
                return rows;
            } else {
                for (S2CAdminData.MemberInfo m : g.members()) {
                    String status = m.online() ? "§a" + I18n.get("crac.gui.online") : "§8" + I18n.get("crac.gui.offline");
                    String text = m.name() + "  " + status + (m.owner() ? " §6(" + I18n.get("crac.gui.owner_tag") + ")" : "");
                    List<AdminPanelScreen.RowButton> btns = new ArrayList<>();
                    if (!m.owner()) {
                        UUID gid = g.id();
                        UUID mid = m.id();
                        String who = m.name();
                        String group = g.name();
                        btns.add(new AdminPanelScreen.RowButton(I18n.get("crac.gui.kick"), -4179669, -2074037, () -> this.confirm(
                            I18n.get("crac.admin.confirm_kick", who, group), C2SAdminAction.ofUuids(C2SAdminAction.KICK_MEMBER, gid, mid)
                        )));
                        btns.add(new AdminPanelScreen.RowButton(I18n.get("crac.gui.make_owner"), -12598694, -10757772, () -> this.confirm(
                            I18n.get("crac.admin.confirm_owner", who, group), C2SAdminAction.ofUuids(C2SAdminAction.SET_OWNER, gid, mid)
                        )));
                    }

                    rows.add(new AdminPanelScreen.Row(text, -3878166, btns));
                }

                return rows;
            }
        } else {
            switch (this.tab) {
                case 0:
                    for (S2CAdminData.GroupInfo g : d.groups()) {
                        String text = g.name()
                            + " §8["
                            + g.tag()
                            + "]§r  §7"
                            + I18n.get("crac.admin.members_count", g.members().size())
                            + (g.claimTier() >= 0 ? "  §a" + I18n.get("crac.admin.claim_tier", g.claimTier()) : "");
                        UUID gid = g.id();
                        List<AdminPanelScreen.RowButton> btns = new ArrayList<>();
                        String group = g.name();
                        btns.add(new AdminPanelScreen.RowButton(I18n.get("crac.admin.disband"), -4179669, -2074037, () -> this.confirm(
                            I18n.get("crac.admin.confirm_disband", group), C2SAdminAction.ofUuid(C2SAdminAction.DISBAND_GROUP, gid)
                        )));
                        btns.add(new AdminPanelScreen.RowButton(I18n.get("crac.gui.members"), -11634488, -1, () -> {
                            this.viewingGroup = gid;
                            this.scroll = 0;
                        }));
                        rows.add(new AdminPanelScreen.Row(text, -3878166, btns));
                    }
                    break;
                case 1:
                    for (S2CAdminData.ClaimInfo c : d.claims()) {
                        String text = c.groupName() + "  §7" + I18n.get("crac.admin.tier", c.tier()) + "  (" + c.centerX() + ", " + c.centerZ() + ")  r=" + c.radius()
                            + "  " + I18n.get("crac.admin.pieces", c.pieces()) + ("minecraft:overworld".equals(c.dimension()) ? "" : "  §8" + c.dimension());
                        UUID gid = c.groupId();
                        List<AdminPanelScreen.RowButton> btns = new ArrayList<>();
                        String group = c.groupName();
                        btns.add(new AdminPanelScreen.RowButton(I18n.get("crac.admin.wipe"), -4179669, -2074037, () -> this.confirm(
                            I18n.get("crac.admin.confirm_wipe", group),
                            C2SAdminAction.ofUuid(C2SAdminAction.WIPE_CLAIM, gid)
                        )));
                        btns.add(new AdminPanelScreen.RowButton(I18n.get("crac.admin.unclaim"), -4179669, -2074037, () -> this.confirm(
                            I18n.get("crac.admin.confirm_unclaim", group), C2SAdminAction.ofUuid(C2SAdminAction.UNCLAIM, gid)
                        )));
                        rows.add(new AdminPanelScreen.Row(text, -3878166, btns));
                    }
                    break;
                case 2:
                    for (S2CAdminData.PlayerInfo p : d.players()) {
                        String text = p.name()
                            + "  §7"
                            + (p.groupName().isEmpty() ? I18n.get("crac.admin.no_group") : p.groupName())
                            + "  ("
                            + p.x()
                            + ", "
                            + p.y()
                            + ", "
                            + p.z()
                            + ")";
                        UUID pid = p.id();
                        List<AdminPanelScreen.RowButton> btns = new ArrayList<>();
                        btns.add(new AdminPanelScreen.RowButton("TP", -12598694, -10757772, () -> this.send(C2SAdminAction.ofUuids(C2SAdminAction.TP_TO_PLAYER, pid, pid))));
                        rows.add(new AdminPanelScreen.Row(text, -3878166, btns));
                    }
            }

            return rows;
        }
    }

    @Override
    protected boolean clickContent(double mx, double my, int button) {
        if (this.viewingGroup != null && in(mx, my, this.backRect())) {
            this.viewingGroup = null;
            this.scroll = 0;
            return true;
        } else {
            if (this.viewingGroup == null) {
                int tw = 74;

                for (int i = 0; i < TABS.length; i++) {
                    int tx = this.left + 14 + i * (tw + 4);
                    int ty = this.top + 26;
                    if (mx >= tx && mx < tx + tw && my >= ty && my < ty + 16) {
                        if (this.tab != i) {
                            this.tab = i;
                            this.scroll = 0;
                            this.stopEditing(false);
                        }

                        return true;
                    }
                }
            }

            if (this.tab == 3 && this.viewingGroup == null) {
                return this.configClicked(mx, my);
            } else if (this.tab == 4 && this.viewingGroup == null) {
                return this.placableClicked(mx, my);
            } else {
                List<AdminPanelScreen.Row> rows = this.buildRows();
                int areaY = this.contentTop();
                int areaH = this.contentHeight();

                for (int ix = 0; ix < rows.size(); ix++) {
                    int ry = areaY + ix * 20 - this.scroll;
                    if (ry + 20 >= areaY && ry <= areaY + areaH) {
                        int bx = this.left + 420 - 14;

                        for (AdminPanelScreen.RowButton btn : rows.get(ix).buttons()) {
                            int bw = this.font.width(btn.label()) + 12;
                            bx -= bw;
                            if (mx >= bx && mx < bx + bw && my >= ry + 2 && my < ry + 20 - 4) {
                                btn.action().run();
                                return true;
                            }

                            bx -= 4;
                        }
                    }
                }

                return this.clickWidgets(mx, my, button);
            }
        }
    }

    private boolean configClicked(double mx, double my) {
        if (in(mx, my, this.refreshRect())) {
            this.stopEditing(false);
            this.edits.clear();
            this.send(C2SAdminAction.of(0));
            return true;
        } else if (in(mx, my, this.applyRect())) {
            this.stopEditing(true);
            if (!this.edits.isEmpty()) {
                this.sendEdits();
                this.edits.clear();
            }

            return true;
        } else {
            for (AdminPanelScreen.SubTab t : this.subTabs()) {
                if (mx >= t.x() && mx < t.x() + t.w() && my >= t.y() && my < t.y() + 14) {
                    this.stopEditing(true);
                    if (!t.key().equals(this.activeSection())) {
                        this.cfgSection = t.key();
                        this.scroll = 0;
                    }

                    return true;
                }
            }

            List<AdminPanelScreen.CfgRow> rows = this.visibleRows();
            int areaY = this.contentTop();
            int areaH = this.contentHeight();

            for (int i = 0; i < rows.size(); i++) {
                int ry = areaY + i * 16 - this.scroll;
                if (ry + 16 >= areaY && ry <= areaY + areaH && !(mx < this.left + 10) && !(mx >= this.left + 420 - 10) && !(my < ry) && !(my >= ry + 16 - 2)) {
                    AdminPanelScreen.CfgRow row = rows.get(i);
                    S2CAdminData.ConfigInfo c = row.cfg();
                    if (row.entry() >= 0) {
                        if (in(mx, my, this.entryRemoveRect(ry))) {
                            this.stopEditing(true);
                            this.removeListEntry(c, row.entry());
                            return true;
                        }

                        this.stopEditing(true);
                        this.editingRow = i;
                        List<String> entries = this.listEntries(c);
                        this.editBuffer = row.entry() < entries.size() ? entryValue(entries.get(row.entry())) : "";
                        return true;
                    }

                    if (row.entry() == -2) {
                        this.stopEditing(true);
                        this.editingRow = i;
                        this.editBuffer = "";
                        return true;
                    }

                    this.stopEditing(true);
                    if (c.type() == 4) {
                        this.expandedList = c.path().equals(this.expandedList) ? null : c.path();
                    } else if (c.type() == 0) {
                        boolean now = Boolean.parseBoolean(this.valueOf(c));
                        this.setEdit(c, String.valueOf(!now));
                    } else {
                        this.editingRow = i;
                        this.editBuffer = this.valueOf(c);
                    }

                    return true;
                }
            }

            return false;
        }
    }

    private void setEdit(S2CAdminData.ConfigInfo c, String value) {
        if (value.equals(c.value())) {
            this.edits.remove(c.path());
        } else {
            this.edits.put(c.path(), value);
        }
    }

    private void stopEditing(boolean commit) {
        if (this.editingRow >= 0) {
            if (this.placableMode()) {
                this.stopEditingPlacable(commit);
            } else {
                List<AdminPanelScreen.CfgRow> rows = this.visibleRows();
                if (commit && this.editingRow < rows.size()) {
                    AdminPanelScreen.CfgRow row = rows.get(this.editingRow);
                    if (row.entry() >= 0) {
                        this.setListEntry(row.cfg(), row.entry(), this.editBuffer);
                    } else if (row.entry() == -2) {
                        String v = this.editBuffer.trim();
                        if (!v.isEmpty()) {
                            List<String> entries = this.listEntries(row.cfg());
                            entries.add(v);
                            this.writeEntries(row.cfg(), entries);
                        }
                    } else {
                        this.setEdit(row.cfg(), this.editBuffer);
                    }
                }

                this.editingRow = -1;
                this.editBuffer = "";
            }
        }
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (this.editingRow >= 0 && ch >= ' ' && ch != 127) {
            this.editBuffer = this.editBuffer + ch;
            return true;
        } else {
            return super.charTyped(ch, mods);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (this.editingRow >= 0) {
            switch (key) {
                case 256:
                    this.stopEditing(false);
                    return true;
                case 257:
                case 335:
                    this.stopEditing(true);
                    return true;
                case 259:
                    if (!this.editBuffer.isEmpty()) {
                        this.editBuffer = this.editBuffer.substring(0, this.editBuffer.length() - 1);
                    }

                    return true;
                default:
                    return true;
            }
        } else {
            return super.keyPressed(key, scan, mods);
        }
    }

    @Override
    protected boolean scrollContent(double mx, double my, double delta) {
        this.scroll = this.scroll - (int)(delta * this.rowHeight());
        return true;
    }

    private void send(C2SAdminAction action) {
        NetworkHandler.CHANNEL.sendToServer(action);
    }

    /** Destructive actions ask first; the server logs every one of them. */
    private void confirm(String message, C2SAdminAction action) {
        this.minecraft.setScreen(new ConfirmScreen(message, () -> this.send(action), this));
    }

    /** Sends the pending config edits in as many packets as the payload limit needs; drops values the server would refuse. */
    private void sendEdits() {
        List<String> paths = new ArrayList<>();
        List<String> values = new ArrayList<>();
        int bytes = 0;
        int tooLong = 0;
        for (Map.Entry<String, String> e : this.edits.entrySet()) {
            String path = e.getKey();
            String value = e.getValue();
            if (path.length() > C2SAdminConfig.MAX_PATH_CHARS || value.length() > C2SAdminConfig.MAX_VALUE_CHARS) {
                tooLong++;
                continue;
            }
            int size = path.getBytes(StandardCharsets.UTF_8).length + value.getBytes(StandardCharsets.UTF_8).length + 8;
            if (!paths.isEmpty() && (bytes + size > EDIT_BATCH_BYTES || paths.size() >= C2SAdminConfig.MAX_EDITS)) {
                NetworkHandler.CHANNEL.sendToServer(new C2SAdminConfig(paths, values));
                paths = new ArrayList<>();
                values = new ArrayList<>();
                bytes = 0;
            }
            paths.add(path);
            values.add(value);
            bytes += size;
        }
        if (!paths.isEmpty()) {
            NetworkHandler.CHANNEL.sendToServer(new C2SAdminConfig(paths, values));
        }
        if (tooLong > 0 && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                Component.translatable("crac.admin.too_long", String.valueOf(tooLong)).withStyle(ChatFormatting.RED), false
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record CfgRow(S2CAdminData.ConfigInfo cfg, int entry) {
    }

    private static final class PlacRow {
        String id;
        int hp;
        boolean enabled;

        PlacRow(String id, int hp, boolean enabled) {
            this.id = id;
            this.hp = hp;
            this.enabled = enabled;
        }
    }

    private record Row(String text, int textColor, List<AdminPanelScreen.RowButton> buttons) {
    }

    private record RowButton(String label, int color, int hoverColor, Runnable action) {
    }

    private record SubTab(String key, String label, int x, int y, int w) {
    }
}
