package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.ClientConfig;
import fr.heraliz.usure.client.kit.Echelle;
import fr.heraliz.usure.client.kit.HzButton;
import fr.heraliz.usure.client.kit.SonsUi;
import fr.heraliz.usure.client.kit.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Reglages du joueur (liste des mods, bouton « Config ») : petit indicateur de l'arme en main, en bas a droite. */
public class EcranReglages extends Screen {
    private final Screen parent;
    private int px, py, pw, ph;

    public EcranReglages(Screen parent) {
        super(Component.translatable("heraliz_usure.reglages.titre"));
        this.parent = parent;
    }

    private static String t(String cle) {
        return Component.translatable(cle).getString();
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(420, width - 24);
        ph = 120;
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int cw = pw - 2 * Ui.MARGE;
        String[] modes = {t("heraliz_usure.reglages.hud.toujours"), t("heraliz_usure.reglages.hud.si_usee"), t("heraliz_usure.reglages.hud.jamais")};
        addRenderableWidget(new Segments(px + Ui.MARGE + cw / 2, py + 54, cw / 2 - 8, 14, modes, () -> ClientConfig.HUD.get().ordinal(), i -> {
            ClientConfig.HUD.set(ClientConfig.ModeHud.values()[i]);
            ClientConfig.SPEC.save();
        }));
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - 100, py + ph - 28, 100, 18, Component.translatable("gui.done"), Ui.accent(), this::onClose)
                .look(HzButton.Look.PRIMARY));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Cadre.panneau(g, font, width, height, px, py, pw, ph, title.getString(), t("heraliz_usure.reglages.sous_titre"));
        Cadre.fermer(g, px + pw - Ui.MARGE - 7, py + 14, mouseX, mouseY);
        int x = px + Ui.MARGE, cw = pw - 2 * Ui.MARGE;
        ligne(g, x, py + 48, cw, "heraliz_usure.reglages.hud", mouseX, mouseY);
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + ph - 36, 0xFFFFFF, 30, true, true);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void ligne(GuiGraphics g, int x, int y, int w, String cle, int mx, int my) {
        Cadre.ligne(g, x, y, w, 26, Cadre.dedans(mx, my, x, y, w, 26));
        g.drawString(font, Ui.lit(t(cle), Ui.FONT), x + 8, y + 4, Ui.argb(Ui.TEXT, 255), false);
        g.drawString(font, Ui.lit(Ui.ellipsis(font, t(cle + ".aide"), Ui.SMALL, w / 2 - 16), Ui.SMALL), x + 8, y + 15, Ui.argb(Ui.MUTED, 255), false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (bouton == GLFW.GLFW_MOUSE_BUTTON_LEFT && Cadre.dedans(mx, my, px + pw - Ui.MARGE - 10, py + 11, 13, 13)) {
            SonsUi.clic(0.6F);
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, bouton);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }
}
