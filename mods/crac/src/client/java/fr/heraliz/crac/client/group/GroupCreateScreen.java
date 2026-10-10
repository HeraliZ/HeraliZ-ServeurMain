package fr.heraliz.crac.client.group;

import java.awt.Color;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.group.GroupNames;
import fr.heraliz.crac.network.C2SCreateGroup;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import static fr.heraliz.crac.client.kit.Dessin.*;

/** Create a group: name, tag (3 or 4 letters) and colour, in the style of the group screen. */
public class GroupCreateScreen extends EcranGroupe {
    private static final float W = 260;
    private static final float H = 214;
    private String name = "";
    private String tag = "";
    private float hue = 0.58F;
    private int focus = 1;
    private boolean draggingHue = false;

    public GroupCreateScreen() {
        super(Component.translatable("crac.gui.create_group"));
    }

    @Override
    protected void initCanevas() {
        this.placer(W, H);
    }

    /** The server's limit (synced config), never above what the packet carries. */
    private static int nameMax() {
        try {
            return Math.min(GroupNames.WIRE_MAX_NAME, Config.groupNameMaxLength.get());
        } catch (RuntimeException e) {
            return 24;
        }
    }

    private boolean valid() {
        return GroupNames.cleanName(this.name, nameMax()) != null && GroupNames.cleanTag(this.tag) != null;
    }

    private int selectedColor() {
        return Color.HSBtoRGB(this.hue, 0.85F, 0.95F) & 0xFFFFFF;
    }

    // geometry
    private float fx() {
        return this.x0 + 13.33F;
    }

    private float fw() {
        return W - 26.67F;
    }

    private float nameY() {
        return this.y0 + 52;
    }

    private float tagY() {
        return this.y0 + 86;
    }

    private float tagW() {
        return 64;
    }

    private float hueY() {
        return this.y0 + 122;
    }

    private float hueW() {
        return this.fw() - 30;
    }

    private float[] createRect() {
        return new float[]{this.fx(), this.y0 + H - 30, this.fw(), 18};
    }

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        this.fond(g);
        int color = this.selectedColor();
        this.panneau(g, color);
        this.entete(g, -1, Component.translatable("crac.gui.create_group").getString(), BLANC, Component.translatable("crac.gui.create_hint").getString());
        this.libelle(g, Component.translatable("crac.gui.group_name").getString(), this.fx(), this.nameY() - 7);
        this.champ(g, this.fx(), this.nameY(), this.fw(), 15.33F, this.name, "", this.focus == 1);
        this.libelle(g, Component.translatable("crac.gui.group_tag").getString(), this.fx(), this.tagY() - 7);
        this.champ(g, this.fx(), this.tagY(), this.tagW(), 15.33F, this.tag, "", this.focus == 2);
        texte(g, this.font, lit(Component.translatable("crac.gui.tag_hint").getString(), MINI), this.fx() + this.tagW() + 6, this.tagY() + 7.67F - 4.21F, a(ATTENUE, 255));
        this.libelle(g, Component.translatable("crac.gui.group_color").getString(), this.fx(), this.hueY() - 7);
        // hue bar, its cursor, and the colour it gives
        float hx = this.fx(), hy = this.hueY(), hw = this.hueW(), hh = 10;
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            float a0 = (float) i / steps, a1 = (float) (i + 1) / steps;
            degradeH(g, hx + hw * a0, hy, hx + hw * a1, hy + hh, a(Color.HSBtoRGB(a0, 0.85F, 0.95F), 255), a(Color.HSBtoRGB(a1, 0.85F, 0.95F), 255));
        }
        contour(g, hx, hy, hw, hh, this.px, a(BLANC, 46));
        float cx = hx + this.hue * hw;
        rect(g, cx - 1.33F, hy - 2, cx + 1.33F, hy + hh + 2, a(BLANC, 255));
        rect(g, cx - 0.67F, hy - 1.33F, cx + 0.67F, hy + hh + 1.33F, a(color, 255));
        float sx = hx + hw + 6;
        rect(g, sx, hy - 2, sx + 24, hy + hh + 2, a(color, 255));
        contour(g, sx, hy - 2, 24, hh + 4, this.px, a(BLANC, 89));
        // preview of the name in its colour
        if (!this.name.isEmpty() || !this.tag.isEmpty()) {
            String preview = this.name + (this.tag.isEmpty() ? "" : "  [" + this.tag + "]");
            texte(g, this.font, lit(preview, GRAS_9), this.fx(), this.y0 + 150 - 5.44F, a(mix(color, BLANC, 0.5F), 255));
        }
        rect(g, this.fx(), this.y0 + H - 38, this.fx() + this.fw(), this.y0 + H - 38 + this.px, a(BLANC, 20));
        float[] b = this.createRect();
        boolean ok = this.valid();
        this.grandBouton(g, b[0], b[1], b[2], b[3], Component.translatable("crac.gui.create").getString(), -2, ok && dans(mx, my, b[0], b[1], b[2], b[3]), ok);
    }

    @Override
    protected boolean clic(double mx, double my, int button) {
        if (dans(mx, my, this.fx(), this.nameY(), this.fw(), 15.33F)) {
            this.focus = 1;
            return true;
        }
        if (dans(mx, my, this.fx(), this.tagY(), this.tagW(), 15.33F)) {
            this.focus = 2;
            return true;
        }
        if (dans(mx, my, this.fx() - 2, this.hueY() - 3, this.hueW() + 4, 16)) {
            this.draggingHue = true;
            this.updateHue(mx);
            this.focus = 0;
            return true;
        }
        float[] b = this.createRect();
        if (this.valid() && dans(mx, my, b[0], b[1], b[2], b[3])) {
            this.accept();
            return true;
        }
        this.focus = 0;
        return false;
    }

    @Override
    protected boolean glisse(double mx, double my, int button, double dx, double dy) {
        if (this.draggingHue) {
            this.updateHue(mx);
            return true;
        }
        return false;
    }

    @Override
    protected boolean relache(double mx, double my, int button) {
        this.draggingHue = false;
        return false;
    }

    private void updateHue(double mx) {
        this.hue = (float) Mth.clamp((mx - this.fx()) / this.hueW(), 0.0, 1.0);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (this.focus == 1) {
            if (this.name.length() < nameMax() && GroupNames.isNameChar(c)) {
                this.name = this.name + c;
                return true;
            }
        } else if (this.focus == 2 && this.tag.length() < GroupNames.TAG_MAX && GroupNames.isTagChar(c)) {
            this.tag = this.tag + Character.toUpperCase(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case 257:
            case 335:
                if (this.valid()) {
                    this.accept();
                }
                return true;
            case 258:
                this.focus = this.focus == 1 ? 2 : 1;
                return true;
            case 259:
                if (this.focus == 1 && !this.name.isEmpty()) {
                    this.name = this.name.substring(0, this.name.length() - 1);
                } else if (this.focus == 2 && !this.tag.isEmpty()) {
                    this.tag = this.tag.substring(0, this.tag.length() - 1);
                }
                return true;
            default:
                return super.keyPressed(key, scan, mods);
        }
    }

    private void accept() {
        NetworkHandler.CHANNEL.sendToServer(new C2SCreateGroup(GroupNames.cleanName(this.name, nameMax()), this.tag, this.selectedColor()));
        this.onClose();
    }
}
