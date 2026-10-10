package fr.heraliz.crac.client.group;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Confirmation des écrans de groupe (quitter, dissoudre, nommer chef) au style des maquettes : la question, « Non » et
 * « Oui » (en rouge quand l'action est lourde). Échap répond « Non » : retour à l'écran qui a demandé. Le panneau admin
 * garde sa propre confirmation ({@link ConfirmScreen}).
 */
public class ConfirmationEcran extends EcranGroupe {
    private static final float W = 240;
    private final String message;
    private final Runnable onYes;
    @Nullable
    private final Screen back;
    private final boolean danger;
    private List<FormattedCharSequence> lines = List.of();

    public ConfirmationEcran(String message, Runnable onYes, @Nullable Screen back, boolean danger) {
        super(Component.translatable("crac.gui.confirm"));
        this.message = message;
        this.onYes = onYes;
        this.back = back;
        this.danger = danger;
    }

    @Override
    protected void initCanevas() {
        this.lines = this.font.split(lit(this.message, MOYEN), 210);
        this.placer(W, 30 + this.lines.size() * 9 + 34);
    }

    private float[] yesRect() {
        return new float[]{this.x0 + W / 2 + 2.67F, this.y0 + this.ph - 28, W / 2 - 16, 16};
    }

    private float[] noRect() {
        return new float[]{this.x0 + 13.33F, this.y0 + this.ph - 28, W / 2 - 16, 16};
    }

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        this.fond(g);
        this.panneau(g, this.danger ? ROUGE : ACCENT);
        capsCentre(g, this.font, Component.translatable("crac.gui.confirm").getString(), MINI_GRAS, this.x0 + W / 2, this.y0 + 14 - 4.21F, 0.8F, a(ATTENUE, 255));
        float y = this.y0 + 26;
        for (FormattedCharSequence line : this.lines) {
            float w = this.font.width(line);
            g.pose().pushPose();
            g.pose().translate(this.x0 + (W - w) / 2, y, 0);
            g.drawString(this.font, line, 0, 0, a(TEXTE, 255), false);
            g.pose().popPose();
            y += 9;
        }
        float[] n = this.noRect();
        float[] o = this.yesRect();
        this.grandBouton(g, n[0], n[1], n[2], n[3], Component.translatable("crac.gui.no").getString(), -1, dans(mx, my, n[0], n[1], n[2], n[3]), true);
        this.grandBouton(g, o[0], o[1], o[2], o[3], Component.translatable("crac.gui.yes").getString(), this.danger ? 0xFF6B6B : -2,
            dans(mx, my, o[0], o[1], o[2], o[3]), true);
    }

    @Override
    protected boolean clic(double mx, double my, int button) {
        float[] o = this.yesRect();
        if (dans(mx, my, o[0], o[1], o[2], o[3])) {
            if (this.onYes != null) {
                this.onYes.run();
            }
            this.minecraft.setScreen(this.back);
            return true;
        }
        float[] n = this.noRect();
        if (dans(mx, my, n[0], n[1], n[2], n[3])) {
            this.minecraft.setScreen(this.back);
            return true;
        }
        return false;
    }

    /** Escape answers "No": back to the screen that asked, never past it. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.back);
    }
}
