package fr.heraliz.crac.build.client;

import java.util.List;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.net.S2CPieceInfo;
import fr.heraliz.crac.build.world.BuildCost;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * In-game HUD of the building tools. Plan: the placement verdict under the crosshair and the selected piece in the
 * bottom right corner. Hammer: a Rust-like panel next to the crosshair (name, tier, health, stability) and the repair
 * progress bar.
 */
@OnlyIn(Dist.CLIENT)
public final class BuildHud {
    private static final int PANEL_BG = 0xA0101010;
    private static final int PANEL_EDGE = 0x60FFFFFF;

    private BuildHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen instanceof RadialMenuScreen || player.isSpectator()) {
            return;
        }
        if (BuildClient.holdingPlan(player)) {
            plan(g, mc.font, player, width, height);
        } else if (BuildClient.holdingHammer(player)) {
            hammer(g, mc.font, width, height);
        }
    }

    // ------------------------------------------------------------------ plan

    private static void plan(GuiGraphics g, Font font, LocalPlayer player, int w, int h) {
        PlanController.Status st = PlanController.status();
        if (st != null) {
            int color = switch (st.severity()) {
                case OK -> 0x7CFC7C;
                case ERROR -> 0xFF6060;
                case HINT -> 0xB0B0B0;
            };
            g.drawCenteredString(font, st.message(), w / 2, h / 2 + 12, color);
        }
        PieceKind kind = PlanState.selected();
        List<BuildCost> cost = BuildClient.placeCost(kind);
        boolean ok = BuildClient.has(player, cost);
        Component c = cost.isEmpty() ? Component.translatable("crac.hud.free") : Component.literal(BuildClient.describe(cost));
        // what R does for this kind (orientation, swing and hinge, stairs turn and mirror, ramp rise), then the keys
        Component meaning = PlanState.rotationLine(kind, PlanState.rotation());
        boolean hasMeaning = !PlanState.rotationText(kind, PlanState.rotation()).isEmpty();
        PlanState.Note note = PlanController.rotationNote();
        Component rotLine = !hasMeaning ? Component.empty() : note.text().isEmpty() ? meaning
            : Component.empty().append(meaning).append(" (").append(note.display()).append(")");
        // the wheel opens with the Use key, whatever it is bound to
        KeyMapping use = Minecraft.getInstance().options.keyUse;
        String useKey = use.isDefault() ? Component.translatable("crac.hud.rmb").getString() : use.getTranslatedKeyMessage().getString();
        String rotKey = BuildKeys.ROTATE.getTranslatedKeyMessage().getString();
        Component keys = hasMeaning ? Component.translatable("crac.hud.keys_rot", rotKey, useKey) : Component.translatable("crac.hud.keys", useKey);
        float scale = 0.75F;
        int textW = Math.max(Math.max(font.width(BuildClient.pieceName(kind)), font.width(c)),
            (int) Math.ceil(Math.max(font.width(rotLine), font.width(keys)) * scale));
        int bw = Math.min(Math.max(150, 46 + textW + 6), Math.max(150, w / 2));
        int bh = !hasMeaning ? 46 : 54;
        int x = w - bw - 6;
        int y = h - bh - 6;
        panel(g, x, y, bw, bh);
        int tx = x + 6;
        if (BuildIcons.draw(g, BuildIcons.piece(kind), x + 5, y + 5, 36)) {
            tx = x + 46;
        }
        int room = x + bw - tx - 4;
        g.drawString(font, BuildClient.pieceName(kind), tx, y + 6, 0xFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(c.getString(), room), tx, y + 18, ok ? 0xC8C8C8 : 0xFF6060);
        int line = y + 31;
        if (hasMeaning) {
            small(g, font, font.plainSubstrByWidth(rotLine.getString(), (int) (room / scale)), tx, line, scale, note.overridden() ? 0xFFB060 : 0xE0D090);
            line += 10;
        } else {
            line += 1;
        }
        small(g, font, font.plainSubstrByWidth(keys.getString(), (int) (room / scale)), tx, line, scale, 0x909090);
    }

    private static void small(GuiGraphics g, Font font, String text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ hammer

    private static void hammer(GuiGraphics g, Font font, int w, int h) {
        Piece target = HammerController.target();
        int cx = w / 2;
        int cy = h / 2;
        if (target != null) {
            PieceInfoCache.Entry entry = PieceInfoCache.get(target.id);
            S2CPieceInfo info = entry == null ? null : entry.info();
            Tier tier = info == null ? target.tier : Tier.byOrdinal(info.tier());
            boolean demolish = entry != null && entry.canDemolish();
            int pw = 124;
            int ph = demolish ? 54 : 44;
            int x = cx + 40;
            int y = cy - 8;
            panel(g, x, y, pw, ph);
            g.drawString(font, BuildClient.pieceName(target.kind()), x + 5, y + 4, 0xFFFFFF);
            int tx = x + 5;
            if (BuildIcons.draw(g, BuildIcons.tier(tier), x + 5, y + 14, 9)) {
                tx += 12;
            }
            g.drawString(font, BuildClient.tierName(tier), tx, y + 15, tierColor(tier));
            int barX = x + 5;
            int barY = y + 27;
            int barW = pw - 10;
            g.fill(barX, barY, barX + barW, barY + 5, 0xFF2A2A2A);
            if (info != null && info.maxHp() > 0) {
                float r = Math.max(0, Math.min(1, (float) info.hp() / info.maxHp()));
                g.fill(barX, barY, barX + Math.round(barW * r), barY + 5, healthColor(r));
                String hp = info.hp() + " / " + info.maxHp();
                g.pose().pushPose();
                g.pose().translate(x + pw - 5 - font.width(hp) * 0.7F, y + 15.5F, 0);
                g.pose().scale(0.7F, 0.7F, 1.0F);
                g.drawString(font, hp, 0, 0, 0xC8C8C8);
                g.pose().popPose();
                g.drawString(font, Component.translatable("crac.hud.stability", info.stability()), x + 5, y + 34, stabilityColor(info.stability()));
            } else {
                g.drawString(font, "...", x + 5, y + 34, 0x909090);
            }
            if (demolish) {
                int left = entry.demolishSecondsLeft();
                Component d = left < 0 ? Component.translatable("crac.hud.demolish_allowed") : Component.translatable("crac.hud.demolish_time", String.format("%d:%02d", left / 60, left % 60));
                g.drawString(font, d, x + 5, y + 44, 0xE08080);
            }
        }
        float progress = HammerController.progress();
        if (progress > 0) {
            int bw = 64;
            g.fill(cx - bw / 2 - 1, cy + 11, cx + bw / 2 + 1, cy + 17, 0xC0000000);
            g.fill(cx - bw / 2, cy + 12, cx - bw / 2 + Math.round(bw * progress), cy + 16, 0xFF7CC0FF);
            g.drawCenteredString(font, Component.translatable("crac.hud.repairing"), cx, cy + 19, 0xC8C8C8);
        }
        Component msg = HammerController.message();
        if (msg != null) {
            g.drawCenteredString(font, msg, cx, cy - 22, 0xFF8060);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x, y, x + w, y + 1, PANEL_EDGE);
    }

    private static int tierColor(Tier t) {
        return switch (t) {
            case TWIG -> 0xD8C48A;
            case WOOD -> 0xC8955A;
            case STONE -> 0xB8B8B8;
            case METAL -> 0xA8C0D8;
            case ARMORED -> 0x8FA3B8;
        };
    }

    private static int healthColor(float r) {
        if (r > 0.6F) return 0xFF5EC45E;
        if (r > 0.3F) return 0xFFE0C040;
        return 0xFFE05050;
    }

    private static int stabilityColor(int s) {
        if (s >= 50) return 0x7CFC7C;
        if (s >= 25) return 0xE0C040;
        return 0xFF6060;
    }
}
