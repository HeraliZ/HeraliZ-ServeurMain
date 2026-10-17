package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.HzButton;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Clavier d'un coffre à code (point de quête « code ») : on tape le code trouvé ailleurs sur la carte (chiffres et
 * lettres, au clavier ou en cliquant), Entrée pour valider. Le serveur vérifie le code.
 */
public final class EcranClavier extends Screen {
    private static final int MAX = 12;
    private final String point, titre;
    private String code = "";
    private int px, py, pw, ph;

    private EcranClavier(Reseau.Clavier c) {
        super(Component.translatable("gui.heraliz_quetes.clavier"));
        point = c.point();
        String t = TexteClient.str(c.titre());
        titre = t.isEmpty() ? TexteClient.tr("gui.heraliz_quetes.clavier") : t;
    }

    static void ouvrir(Reseau.Clavier c) {
        SonsUi.ouvrir(0.5F);
        Minecraft.getInstance().setScreen(new EcranClavier(c));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = 200;
        ph = 214;
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int t = 40, e = 6, x0 = px + (pw - 3 * t - 2 * e) / 2, y0 = py + 66;
        for (int i = 0; i < 9; i++) {
            String ch = Integer.toString(i + 1);
            addRenderableWidget(new HzButton(x0 + (i % 3) * (t + e), y0 + (i / 3) * (24 + e), t, 24, Component.literal(ch), Ui.ACCENT_NEUTRE,
                    () -> taper(ch)));
        }
        int y3 = y0 + 3 * (24 + e);
        addRenderableWidget(new HzButton(x0, y3, t, 24, Component.literal("←"), Ui.MUTED, this::effacer));
        addRenderableWidget(new HzButton(x0 + t + e, y3, t, 24, Component.literal("0"), Ui.ACCENT_NEUTRE, () -> taper("0")));
        addRenderableWidget(new HzButton(x0 + 2 * (t + e), y3, t, 24, Component.literal("OK"), Ui.OK, this::valider).look(HzButton.Look.PRIMARY));
    }

    private void taper(String s) {
        if (code.length() < MAX) code += s;
    }

    private void effacer() {
        if (!code.isEmpty()) code = code.substring(0, code.length() - 1);
    }

    private void valider() {
        if (code.isEmpty()) return;
        Reseau.CANAL.sendToServer(new Reseau.Code(point, code));
        onClose();
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (Character.isLetterOrDigit(c)) {
            taper(String.valueOf(Character.toUpperCase(c)));
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            effacer();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            valider();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph, 225);
        Ui.spacedCentered(g, f, Ui.ellipsis(f, titre, Ui.SMALL, pw - 20), Ui.SMALL, px + pw / 2f, py + 12, Ui.argb(Ui.MUTED, 255), 0.8f);
        int cw = pw - 36, cx = px + 18, cy = py + 28;
        Ui.soft(g, cx, cy, cw, 26, Ui.argb(0x000000, 120));
        Ui.softOutline(g, cx + 1, cy + 1, cw - 2, 24, Ui.argb(0xFFFFFF, 40));
        String vu = code.isEmpty() ? "_" : code + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ");
        Ui.gros(g, f, Ui.lit(vu, Ui.BOLD), cx + cw / 2f - f.width(Ui.lit(vu, Ui.BOLD)) * 0.8f, cy + 7, 1.6f, Ui.argb(Ui.OK, 255));
        super.render(g, mx, my, pt);
    }
}
