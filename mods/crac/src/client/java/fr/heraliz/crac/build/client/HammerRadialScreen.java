package fr.heraliz.crac.build.client;

import fr.heraliz.crac.heraliz.Txt;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.net.C2SHammer;
import fr.heraliz.crac.build.net.S2CPieceInfo;
import fr.heraliz.crac.build.world.BuildCost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Hammer wheel on the targeted piece: upgrade to each tier (with its cost), repair, and demolish while allowed. The
 * current tier has a white edge, lower tiers are dimmed, Demolish is red; the hub tells what the entry under the mouse
 * costs or why it cannot be used.
 */
@OnlyIn(Dist.CLIENT)
public class HammerRadialScreen extends RadialMenuScreen {
    private static final int REPAIR = -1;
    private static final int DEMOLISH = -2;

    private final Piece target;
    /** Tier ordinal per entry, or REPAIR / DEMOLISH. */
    private final List<Integer> entries = new ArrayList<>();

    public HammerRadialScreen(InputConstants.Key trigger, Piece target) {
        super(Component.translatable("crac.hammer.title"), trigger);
        this.target = target;
        for (Tier t : Tier.values()) {
            if (t != Tier.TWIG) {
                this.entries.add(t.ordinal());
            }
        }
        this.entries.add(REPAIR);
        PieceInfoCache.Entry info = PieceInfoCache.get(target.id);
        if (info != null && info.canDemolish()) {
            this.entries.add(DEMOLISH);
        }
    }

    @Nullable
    private S2CPieceInfo info() {
        PieceInfoCache.Entry e = PieceInfoCache.get(this.target.id);
        return e == null ? null : e.info();
    }

    @Override
    protected int count() {
        return this.entries.size();
    }

    @Override
    protected boolean enabled(int i) {
        return this.problem(i) == null;
    }

    @Override
    protected boolean active(int i) {
        return this.entries.get(i) == this.target.tier.ordinal();
    }

    @Override
    protected boolean conseille(int i) {
        return fr.heraliz.crac.bases.TutoClient.bois() && this.entries.get(i) == Tier.WOOD.ordinal()
            && this.target.tier.ordinal() < Tier.WOOD.ordinal();
    }

    @Override
    protected Look look(int i) {
        int entry = this.entries.get(i);
        if (entry == DEMOLISH) {
            return Look.DANGER;
        }
        if (this.active(i)) {
            return Look.CURRENT;
        }
        return entry >= 0 && entry < this.target.tier.ordinal() ? Look.DIMMED : Look.NORMAL;
    }

    @Override
    protected String heading() {
        String s = BuildClient.pieceName(this.target.kind()).getString() + " · " + BuildClient.tierName(this.target.tier).getString();
        S2CPieceInfo info = this.info();
        return info == null ? s : s + " · " + Component.translatable("crac.wheel.hp", info.hp(), info.maxHp()).getString();
    }

    private List<BuildCost> cost(int entry) {
        if (entry == REPAIR) {
            return BuildClient.repairCost(this.target.tier);
        }
        return entry >= 0 ? BuildClient.upgradeCost(this.target.kind(), Tier.byOrdinal(entry)) : List.of();
    }

    /** Why entry {@code i} cannot be used, or null. */
    @Nullable
    private String problem(int i) {
        int entry = this.entries.get(i);
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null || entry == DEMOLISH) {
            return null;
        }
        S2CPieceInfo info = this.info();
        if (entry == REPAIR) {
            return info == null ? "crac.hammer.waiting" : HammerController.repairProblem(player, info);
        }
        Tier tier = Tier.byOrdinal(entry);
        if (info != null && !info.privileged()) {
            return "crac.build.not_yours";
        }
        if (tier.ordinal() <= this.target.tier.ordinal()) {
            return tier == this.target.tier ? Txt.k("crac.build.already", BuildClient.tierName(tier)) : "crac.hammer.lower";
        }
        if (!BuildClient.has(player, this.cost(entry))) {
            return "crac.hammer.not_enough";
        }
        return null;
    }

    private boolean affordable(int entry) {
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        return player == null || BuildClient.has(player, this.cost(entry));
    }

    @Override
    protected void drawEntry(GuiGraphics g, int i, float x, float y, boolean hover, float alpha) {
        int entry = this.entries.get(i);
        float size = hover ? 23.33F : 20.67F;
        float top = y - 17.33F;
        float labelY = top + size + 5.67F;
        int text = a(TEXTE, 217 * alpha);
        if (entry >= 0) {
            Tier tier = Tier.byOrdinal(entry);
            String name = BuildClient.tierName(tier).getString();
            if (!BuildIcons.draw(g, BuildIcons.tier(tier), x - size / 2, top, size, alpha)) {
                this.label(g, name, x, top + size / 2, text);
            }
            String label;
            int color = text;
            if (entry == this.target.tier.ordinal()) {
                label = Component.translatable("crac.wheel.current", name).getString();
            } else if (entry < this.target.tier.ordinal()) {
                label = name;
            } else {
                List<BuildCost> cost = this.cost(entry);
                label = cost.isEmpty() ? name : name + " · " + cost.get(0).count();
                if (!this.affordable(entry)) {
                    color = a(ROUGE_PALE, 255 * alpha);
                }
            }
            this.label(g, label, x, labelY, color);
        } else if (entry == REPAIR) {
            marteaux(g, x, top + size / 2, size * 0.32F, a(0xE3E6EB, 255 * alpha));
            List<BuildCost> cost = this.cost(entry);
            String name = Component.translatable("crac.hammer.repair").getString();
            this.label(g, cost.isEmpty() ? name : name + " · " + cost.get(0).count(), x, labelY, this.affordable(entry) ? text : a(ROUGE_PALE, 255 * alpha));
        } else {
            croix(g, x, top + size / 2, size * 0.22F, size * 0.09F, a(ROUGE_PALE, 255 * alpha));
            String name = Component.translatable("crac.hammer.demolish").getString();
            PieceInfoCache.Entry e = PieceInfoCache.get(this.target.id);
            int left = e == null ? -1 : e.demolishSecondsLeft();
            this.label(g, left > 0 ? name + " · " + String.format("%d:%02d", left / 60, left % 60) : name, x, labelY, a(ROSE, 255 * alpha));
        }
    }

    @Override
    protected void drawHub(GuiGraphics g, float cx, float cy) {
        List<HubLine> lines = new ArrayList<>();
        if (this.hovered < 0) {
            lines.add(title(BuildClient.pieceName(this.target.kind()).getString()));
            lines.add(line(BuildClient.tierName(this.target.tier), a(OR, 255)));
            S2CPieceInfo info = this.info();
            if (info != null) {
                lines.add(line(Component.translatable("crac.wheel.hp", info.hp(), info.maxHp()), a(ATTENUE, 255)));
            }
            this.hub(g, cx, cy, lines, 0.67F);
            return;
        }
        int entry = this.entries.get(this.hovered);
        String title = (entry >= 0 ? Component.translatable("crac.hammer.upgrade_to", BuildClient.tierName(Tier.byOrdinal(entry)))
            : Component.translatable(entry == REPAIR ? "crac.hammer.repair" : "crac.hammer.demolish")).getString();
        lines.add(title(title));
        String problem = this.problem(this.hovered);
        if (entry == DEMOLISH) {
            lines.add(line(Component.translatable("crac.hammer.no_refund"), a(ATTENUE, 255)));
        } else if (problem != null && !"crac.hammer.not_enough".equals(problem)) {
            // not about materials: say why
            lines.add(line(Txt.c(problem), a(ROUGE_PALE, 255)));
        } else {
            lines.addAll(PlanRadialScreen.costLines(this.minecraft == null ? null : this.minecraft.player, this.cost(entry)));
        }
        this.hub(g, cx, cy, lines, 0.67F);
    }

    @Override
    protected void pick(int i) {
        int entry = this.entries.get(i);
        if (entry == REPAIR) {
            HammerController.startAutoRepair();
        } else if (entry == DEMOLISH) {
            HammerController.send(C2SHammer.DEMOLISH, Tier.TWIG);
        } else {
            HammerController.send(C2SHammer.UPGRADE, Tier.byOrdinal(entry));
        }
    }
}
