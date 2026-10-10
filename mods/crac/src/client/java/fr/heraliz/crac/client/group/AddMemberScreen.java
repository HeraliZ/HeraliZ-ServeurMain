package fr.heraliz.crac.client.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.network.C2SGroupAction;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2COnlinePlayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import static fr.heraliz.crac.client.kit.Dessin.*;

/** Add a member: a search field and the online players who can join (click one), in the style of the group screen. */
public class AddMemberScreen extends EcranGroupe {
    private static final float W = 240;
    private static final float H = 236;
    private static final float ROW = 20;
    private static final int VISIBLE = 7;
    private final List<S2COnlinePlayers.Entry> all;
    private final List<S2COnlinePlayers.Entry> filtered = new ArrayList<>();
    private String search = "";
    private int scroll = 0;

    public AddMemberScreen(List<S2COnlinePlayers.Entry> players) {
        super(Component.translatable("crac.gui.add_member"));
        this.all = players;
    }

    public static void open(List<S2COnlinePlayers.Entry> players) {
        Minecraft.getInstance().setScreen(new AddMemberScreen(players));
    }

    @Override
    protected void initCanevas() {
        this.placer(W, H);
        this.refilter();
    }

    private void refilter() {
        this.filtered.clear();
        String q = this.search.toLowerCase(Locale.ROOT);
        for (S2COnlinePlayers.Entry e : this.all) {
            if (q.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(q)) {
                this.filtered.add(e);
            }
        }
        this.scroll = 0;
    }

    private float listX() {
        return this.x0 + 13.33F;
    }

    private float listW() {
        return W - 26.67F;
    }

    private float listY() {
        return this.y0 + 58;
    }

    private float[] backRect() {
        String s = Component.translatable("crac.gui.back").getString();
        return new float[]{this.x0 + 13.33F, this.y0 + H - 25.33F, this.largeurGrandBouton(s), 16};
    }

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        this.fond(g);
        this.panneau(g, ACCENT);
        this.entete(g, -1, Component.translatable("crac.gui.add_member").getString(), BLANC, Component.translatable("crac.gui.add_member_hint").getString());
        this.champ(g, this.listX(), this.y0 + 38, this.listW(), 15.33F, this.search, Component.translatable("crac.gui.search").getString(), true);
        if (this.filtered.isEmpty()) {
            texte(g, this.font, lit(Component.translatable("crac.gui.no_players").getString(), MINI), this.listX(), this.listY() + 8 - 4.21F, a(ATTENUE, 255));
        }
        int end = Math.min(this.filtered.size(), this.scroll + VISIBLE);
        for (int idx = this.scroll; idx < end; idx++) {
            S2COnlinePlayers.Entry e = this.filtered.get(idx);
            float y = this.listY() + (idx - this.scroll) * ROW;
            boolean hov = dans(mx, my, this.listX(), y, this.listW(), ROW);
            if (hov) {
                this.ligneSurvol(g, this.listX(), y, this.listW(), ROW);
            }
            this.tete(g, e.id(), this.listX() + 5.33F, y + 4, 12);
            String name = Ui.ellipsis(this.font, e.name(), GRAS_9, Math.round(this.listW() - 60));
            texte(g, this.font, lit(name, GRAS_9), this.listX() + 22.67F, y + ROW / 2 - 5.44F, a(BLANC, 255));
            if (hov) {
                String add = Component.translatable("crac.gui.add").getString();
                float aw = largeurCaps(this.font, add, MINI_GRAS, 0.6F);
                caps(g, this.font, add, MINI_GRAS, this.listX() + this.listW() - 6 - aw, y + ROW / 2 - 4.21F, 0.6F, a(VERT_PALE, 255));
            }
        }
        if (this.filtered.size() > VISIBLE) {
            float track = VISIBLE * ROW;
            float thumb = Math.max(12, track * VISIBLE / this.filtered.size());
            float ty = this.listY() + (track - thumb) * this.scroll / Math.max(1, this.filtered.size() - VISIBLE);
            rect(g, this.x0 + W - 6, ty, this.x0 + W - 5, ty + thumb, a(BLANC, 56));
        }
        rect(g, this.listX(), this.y0 + H - 32, this.listX() + this.listW(), this.y0 + H - 32 + this.px, a(BLANC, 20));
        float[] b = this.backRect();
        this.grandBouton(g, b[0], b[1], b[2], b[3], Component.translatable("crac.gui.back").getString(), -1, dans(mx, my, b[0], b[1], b[2], b[3]), true);
    }

    @Override
    protected boolean clic(double mx, double my, int button) {
        int end = Math.min(this.filtered.size(), this.scroll + VISIBLE);
        for (int idx = this.scroll; idx < end; idx++) {
            float y = this.listY() + (idx - this.scroll) * ROW;
            if (dans(mx, my, this.listX(), y, this.listW(), ROW)) {
                NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.ADD, this.filtered.get(idx).id()));
                return true;
            }
        }
        float[] b = this.backRect();
        if (dans(mx, my, b[0], b[1], b[2], b[3])) {
            NetworkHandler.CHANNEL.sendToServer(new C2SGroupAction(C2SGroupAction.Type.REFRESH, null));
            return true;
        }
        return false;
    }

    @Override
    protected boolean molette(double mx, double my, double delta) {
        int max = Math.max(0, this.filtered.size() - VISIBLE);
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(delta), 0, max);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (c >= ' ' && c != 127 && this.search.length() < 16) {
            this.search = this.search + c;
            this.refilter();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 259 && !this.search.isEmpty()) {
            this.search = this.search.substring(0, this.search.length() - 1);
            this.refilter();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
