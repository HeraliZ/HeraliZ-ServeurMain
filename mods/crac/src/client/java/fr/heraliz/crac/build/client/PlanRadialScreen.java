package fr.heraliz.crac.build.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.world.BuildCost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Building Plan wheel: the 13 pieces with their icon and cost (red and dimmed when the player cannot pay it); the hub
 * names the piece under the mouse (or the selected one), its cost and how much the player has.
 */
@OnlyIn(Dist.CLIENT)
public class PlanRadialScreen extends RadialMenuScreen {
    private static final PieceKind[] KINDS = PieceKind.values();

    public PlanRadialScreen(InputConstants.Key trigger) {
        super(Component.translatable("crac.plan.title"), trigger);
    }

    @Override
    protected int count() {
        return KINDS.length;
    }

    @Override
    protected boolean enabled(int i) {
        return true;
    }

    @Override
    protected boolean active(int i) {
        return KINDS[i] == PlanState.selected();
    }

    @Override
    protected boolean conseille(int i) {
        return KINDS[i].ordinal() == fr.heraliz.crac.bases.TutoClient.roue();
    }

    @Override
    protected Look look(int i) {
        return this.active(i) ? Look.CURRENT : this.affordable(KINDS[i]) ? Look.NORMAL : Look.DIMMED;
    }

    @Override
    protected String heading() {
        return Component.translatable("crac.plan.title").getString();
    }

    private boolean affordable(PieceKind kind) {
        return this.minecraft == null || this.minecraft.player == null || BuildClient.has(this.minecraft.player, BuildClient.placeCost(kind));
    }

    @Override
    protected void drawEntry(GuiGraphics g, int i, float x, float y, boolean hover, float alpha) {
        PieceKind kind = KINDS[i];
        float size = hover ? 23.33F : 20.67F;
        float top = y - 17.33F;
        if (!BuildIcons.draw(g, BuildIcons.piece(kind), x - size / 2, top, size, alpha)) {
            // no icon: the name in its place
            this.label(g, BuildClient.pieceName(kind).getString(), x, top + size / 2, a(TEXTE, 255 * alpha));
        }
        List<BuildCost> cost = BuildClient.placeCost(kind);
        if (!cost.isEmpty()) {
            String c = cost.get(0).count() + (cost.size() > 1 ? "+" : "");
            this.label(g, c, x, top + size + 5.67F, a(this.affordable(kind) ? TEXTE : ROUGE_PALE, (this.affordable(kind) ? 217 : 255) * alpha));
        }
    }

    @Override
    protected void drawHub(GuiGraphics g, float cx, float cy) {
        PieceKind kind = this.hovered >= 0 ? KINDS[this.hovered] : PlanState.selected();
        List<HubLine> lines = new ArrayList<>();
        lines.add(title(BuildClient.pieceName(kind).getString()));
        lines.addAll(costLines(this.minecraft == null ? null : this.minecraft.player, BuildClient.placeCost(kind)));
        this.hub(g, cx, cy, lines, 0.8F);
    }

    /** "16 × Stick" (green when the player has enough, red otherwise) and "You have 64 · 3 missing". */
    static List<HubLine> costLines(LocalPlayer player, List<BuildCost> cost) {
        List<HubLine> lines = new ArrayList<>();
        if (cost.isEmpty()) {
            lines.add(line(Component.translatable("crac.hud.free"), a(VERT_PALE, 255)));
            return lines;
        }
        boolean creative = player != null && player.getAbilities().instabuild;
        BuildCost missing = null;
        int missingHave = 0;
        for (BuildCost c : cost) {
            int have = player == null ? 0 : BuildClient.count(player, c);
            boolean ok = creative || have >= c.count();
            lines.add(line(Component.translatable("crac.wheel.cost", c.count(), c.displayName()), a(ok ? VERT_PALE : ROUGE_PALE, 255)));
            if (!ok && missing == null) {
                missing = c;
                missingHave = have;
            }
        }
        if (player != null && !creative) {
            if (missing != null) {
                lines.add(line(Component.translatable("crac.wheel.missing", missingHave, missing.count() - missingHave), a(ATTENUE, 255)));
            } else if (cost.size() == 1) {
                lines.add(line(Component.translatable("crac.wheel.have", BuildClient.count(player, cost.get(0))), a(ATTENUE, 255)));
            }
        }
        return lines;
    }

    @Override
    protected void pick(int i) {
        PlanState.select(KINDS[i]);
    }
}
