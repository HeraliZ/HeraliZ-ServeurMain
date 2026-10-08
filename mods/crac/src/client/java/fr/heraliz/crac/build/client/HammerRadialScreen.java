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

/** Hammer wheel on the targeted piece: upgrade to each tier (with its cost), repair, and demolish while allowed. */
@OnlyIn(Dist.CLIENT)
public class HammerRadialScreen extends RadialMenuScreen {
    private static final int ICON = 26;
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

    @Override
    protected void drawEntry(GuiGraphics g, int i, int x, int y, boolean hover) {
        int entry = this.entries.get(i);
        boolean on = this.enabled(i);
        int color = on ? (hover ? WHITE : 0xFFD8D8D8) : DARK_GREY;
        int size = hover && on ? ICON + 4 : ICON;
        if (entry >= 0) {
            Tier tier = Tier.byOrdinal(entry);
            if (!BuildIcons.draw(g, BuildIcons.tier(tier), x - size / 2, y - size / 2 - 5, size)) {
                this.lines(g, BuildClient.tierName(tier).getString(), x, y - 5, 44, color, 0.75F);
            }
            List<BuildCost> cost = this.cost(entry);
            if (!cost.isEmpty()) {
                this.text(g, cost.get(0).count() + "x", x, y + size / 2 - 1, on ? GREY : DARK_GREY, 0.65F);
            }
        } else if (entry == REPAIR) {
            this.text(g, Component.translatable("crac.hammer.repair").getString(), x, y - 6, color, 0.85F);
            List<BuildCost> cost = this.cost(entry);
            if (!cost.isEmpty()) {
                this.text(g, cost.get(0).count() + "x", x, y + 5, on ? GREY : DARK_GREY, 0.65F);
            }
        } else {
            this.text(g, Component.translatable("crac.hammer.demolish").getString(), x, y - 6, hover ? 0xFFFF8080 : 0xFFE06060, 0.85F);
            PieceInfoCache.Entry e = PieceInfoCache.get(this.target.id);
            int left = e == null ? -1 : e.demolishSecondsLeft();
            if (left > 0) {
                this.text(g, String.format("%d:%02d", left / 60, left % 60), x, y + 5, GREY, 0.65F);
            }
        }
    }

    @Override
    protected void drawHub(GuiGraphics g, int cx, int cy) {
        if (this.hovered < 0) {
            this.lines(g, BuildClient.pieceName(this.target.kind()).getString(), cx, cy - 12, 88, WHITE, 0.85F);
            this.text(g, BuildClient.tierName(this.target.tier).getString(), cx, cy + 2, GOLD, 0.75F);
            S2CPieceInfo info = this.info();
            if (info != null) {
                this.text(g, info.hp() + " / " + info.maxHp() + " HP", cx, cy + 12, 0xFFC8C8C8, 0.7F);
            }
            return;
        }
        int entry = this.entries.get(this.hovered);
        String title = (entry >= 0 ? Component.translatable("crac.hammer.upgrade_to", BuildClient.tierName(Tier.byOrdinal(entry))) : Component.translatable(entry == REPAIR ? "crac.hammer.repair" : "crac.hammer.demolish")).getString();
        this.lines(g, title, cx, cy - 16, 88, WHITE, 0.8F);
        int y = cy - 4;
        for (BuildCost c : this.cost(entry)) {
            boolean have = this.minecraft != null && this.minecraft.player != null
                && (this.minecraft.player.getAbilities().instabuild || BuildClient.count(this.minecraft.player, c) >= c.count());
            this.text(g, c.count() + "x " + c.displayName().getString(), cx, y, have ? 0xFFC8C8C8 : RED, 0.7F);
            y += 8;
        }
        String problem = this.problem(this.hovered);
        if (problem != null) {
            this.lines(g, Txt.c(problem).getString(), cx, y + 6, 90, RED, 0.6F);
        } else if (entry == DEMOLISH) {
            this.lines(g, Component.translatable("crac.hammer.no_refund").getString(), cx, y + 6, 90, GREY, 0.6F);
        }
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
