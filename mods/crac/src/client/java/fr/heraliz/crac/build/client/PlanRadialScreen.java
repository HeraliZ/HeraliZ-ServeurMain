package fr.heraliz.crac.build.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.world.BuildCost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Building Plan wheel: the 13 pieces with their icon, name and twig cost. */
@OnlyIn(Dist.CLIENT)
public class PlanRadialScreen extends RadialMenuScreen {
    private static final PieceKind[] KINDS = PieceKind.values();
    private static final int ICON = 26;

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

    private boolean affordable(PieceKind kind) {
        return this.minecraft == null || this.minecraft.player == null || BuildClient.has(this.minecraft.player, BuildClient.placeCost(kind));
    }

    @Override
    protected void drawEntry(GuiGraphics g, int i, int x, int y, boolean hover) {
        PieceKind kind = KINDS[i];
        int size = hover ? ICON + 4 : ICON;
        if (!BuildIcons.draw(g, BuildIcons.piece(kind), x - size / 2, y - size / 2 - 4, size)) {
            this.lines(g, BuildClient.pieceName(kind).getString(), x, y - 4, 40, hover ? WHITE : 0xFFD0D0D0, 0.6F);
        }
        List<BuildCost> cost = BuildClient.placeCost(kind);
        if (!cost.isEmpty()) {
            String c = String.valueOf(cost.get(0).count()) + (cost.size() > 1 ? "+" : "");
            this.text(g, c, x, y + size / 2 + 1, this.affordable(kind) ? GREY : RED, 0.6F);
        }
    }

    @Override
    protected void drawHub(GuiGraphics g, int cx, int cy) {
        PieceKind kind = this.hovered >= 0 ? KINDS[this.hovered] : PlanState.selected();
        this.lines(g, BuildClient.pieceName(kind).getString(), cx, cy - 10, 84, this.hovered >= 0 ? WHITE : GOLD, 0.85F);
        List<BuildCost> cost = BuildClient.placeCost(kind);
        boolean ok = this.affordable(kind);
        int y = cy + 4;
        if (cost.isEmpty()) {
            this.text(g, Component.translatable("crac.hud.free").getString(), cx, y, GREY, 0.7F);
        }
        for (BuildCost c : cost) {
            this.text(g, c.count() + "x " + c.displayName().getString(), cx, y, ok ? 0xFFC8C8C8 : RED, 0.7F);
            y += 8;
        }
    }

    @Override
    protected void pick(int i) {
        PlanState.select(KINDS[i]);
    }
}
