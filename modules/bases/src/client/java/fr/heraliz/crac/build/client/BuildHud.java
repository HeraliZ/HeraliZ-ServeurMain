package fr.heraliz.crac.build.client;

import java.util.List;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.net.S2CPieceInfo;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.client.kit.Ui;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * In-game HUD of the building tools (validated mock-ups). Plan: the placement verdict in a small card under the
 * crosshair (green, red or grey) and the selected piece in a glass card in the bottom right corner (cost, what R does,
 * keys). Hammer: a glass card next to the crosshair (name, tier, health bar, stability, demolish time, keys), a ring
 * around the crosshair while repairing, and the last refusal above it.
 */
@OnlyIn(Dist.CLIENT)
public final class BuildHud {
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

    /** One screen pixel, in GUI pixels (the thin borders of the mock-ups). */
    private static float px() {
        return 1.0F / (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    // ------------------------------------------------------------------ plan

    private static void plan(GuiGraphics g, Font font, LocalPlayer player, int w, int h) {
        float px = px();
        PlanController.Status st = PlanController.status();
        if (st != null) {
            verdict(g, font, st, w / 2.0F, h / 2.0F + 16.67F, Math.min(220, w - 40), px);
        }
        PieceKind kind = PlanState.selected();
        List<BuildCost> cost = BuildClient.placeCost(kind);
        boolean ok = BuildClient.has(player, cost);
        // what R does for this kind (orientation, swing and hinge, stairs turn and mirror, ramp rise), and the note
        boolean hasMeaning = !PlanState.rotationText(kind, PlanState.rotation()).isEmpty();
        Component meaning = PlanState.rotationLine(kind, PlanState.rotation());
        PlanState.Note note = PlanController.rotationNote();
        String useKey = keyName(Minecraft.getInstance().options.keyUse, "crac.hud.rmb");
        String rotKey = BuildKeys.ROTATE.getTranslatedKeyMessage().getString();

        float margin = Math.max(8, Math.min(32, Math.round(w * 0.05F)));
        float cw = 146.67F;
        // la rangée des touches (cases de 7 de haut) tient toujours dans la carte, avec la même marge en bas
        float ch = (hasMeaning ? 42.33F : 36.33F) + 14;
        float x = w - margin - cw;
        float y = h - margin - ch;
        card(g, x, y, cw, ch, px);
        float ix = x + 6.67F, iy = y + 6;
        rect(g, ix, iy, ix + 21.33F, iy + 21.33F, a(BLANC, 10));
        BuildIcons.draw(g, BuildIcons.piece(kind), ix + 0.67F, iy + 0.67F, 20, 1);
        float tx = ix + 21.33F + 5.33F;
        float room = x + cw - 6.67F - tx;
        caps(g, font, Ui.ellipsis(font, BuildClient.pieceName(kind).getString(), GRAS_9, Math.round(room)), GRAS_9, tx, y + 10 - 3.02F, 0.8F, a(BLANC, 255));
        // cost: green when the player has it, then how much he has (one material)
        String c = cost.isEmpty() ? Component.translatable("crac.hud.free").getString() : BuildClient.describe(cost);
        float cy = y + 21.67F;
        String have = cost.size() == 1 && !player.getAbilities().instabuild
            ? " · " + Component.translatable("crac.hud.have", BuildClient.count(player, cost.get(0))).getString() : "";
        String shown = Ui.ellipsis(font, c, MINI, Math.round(room - (have.isEmpty() ? 0 : largeur(font, have, MINI))));
        texte(g, font, lit(shown, MINI), tx, cy - 4.21F, a(ok ? VERT_PALE : ROUGE_PALE, 255));
        if (!have.isEmpty() && shown.equals(c)) {
            texte(g, font, lit(have, MINI), tx + largeur(font, shown, MINI), cy - 4.21F, a(ATTENUE, 255));
        }
        float ky = y + 36.33F;
        if (hasMeaning) {
            float ly = y + 34.67F;
            String m = meaning.getString();
            int mc = note.overridden() ? 0xFFB060 : 0xE8D49A;
            float mw = Math.min(largeur(font, m, MINI), cw - 13.33F);
            String ms = Ui.ellipsis(font, m, MINI, Math.round(cw - 13.33F));
            texte(g, font, lit(ms, MINI), x + 6.67F, ly - 4.21F, a(mc, 255));
            if (!note.text().isEmpty() && ms.equals(m)) {
                String n = Ui.ellipsis(font, " · " + note.display().getString(), MINI, Math.round(cw - 13.33F - mw));
                texte(g, font, lit(n, MINI), x + 6.67F + mw, ly - 4.21F, a(note.overridden() ? 0xFFB060 : ATTENUE, 255));
            }
            ky = y + 42.33F;
        }
        rect(g, x + 6.67F, ky, x + cw - 6.67F, ky + px, a(BLANC, 23));
        // keys: R turn, Shift + R back (when R means something), right click pieces
        float kx = x + 6.67F, kc = ky + 7;
        int kcol = a(TEXTE, 184);
        if (hasMeaning) {
            kx += touche(g, font, rotKey, kx, kc, 7, 0.3F, 6.67F, px) + 2.67F;
            kx += caps0(g, font, Component.translatable("crac.hud.turn").getString(), kx, kc, kcol) + 3.33F;
            kx += touche(g, font, Component.translatable("crac.hud.shift", rotKey).getString(), kx, kc, 7, 0.3F, 6.67F, px) + 2.67F;
            kx += caps0(g, font, Component.translatable("crac.hud.back").getString(), kx, kc, kcol) + 3.33F;
        }
        kx += touche(g, font, useKey, kx, kc, 7, 0.3F, 6.67F, px) + 2.67F;
        caps0(g, font, Component.translatable("crac.hud.pieces").getString(), kx, kc, kcol);
    }

    /** Small text after a key (not in capitals), lowered 1 unit to sit level with the key's letters; returns its width. */
    private static float caps0(GuiGraphics g, Font font, String s, float x, float cy, int argb) {
        texte(g, font, lit(s, MINI), x, cy - 3.21F, argb);
        return largeur(font, s, MINI);
    }

    /** The verdict card under the crosshair: a dot and the message, green (ready), red (refused) or grey (hint). */
    private static void verdict(GuiGraphics g, Font font, PlanController.Status st, float cx, float top, float maxW, float px) {
        String msg = st.message().getString();
        if (msg.endsWith(".") && !msg.endsWith("..")) {
            msg = msg.substring(0, msg.length() - 1);
        }
        int bg, edge, dot, text;
        switch (st.severity()) {
            case OK -> {
                bg = a(0x08180E, 179);
                edge = a(VERT, 128);
                dot = VERT;
                text = 0xCFF3DA;
            }
            case ERROR -> {
                bg = a(0x1C0808, 184);
                edge = a(ROUGE, 128);
                dot = ROUGE;
                text = ROSE;
            }
            default -> {
                bg = a(0x0A0C10, 179);
                edge = a(BLANC, 31);
                dot = GRIS;
                text = BOUTON;
            }
        }
        List<FormattedCharSequence> lines = font.split(lit(msg, MOYEN), Math.round(maxW - 18));
        float tw = 0;
        for (FormattedCharSequence l : lines) {
            tw = Math.max(tw, font.width(l));
        }
        float w = Math.max(93.33F, tw + 18 + 6.67F);
        float h = 14.67F + (lines.size() - 1) * 8;
        float x = cx - w / 2;
        rect(g, x, top, x + w, top + h, bg);
        contour(g, x, top, w, h, px, edge);
        float contentX = cx - (tw + 6.67F) / 2;
        float dy = top + 7.33F;
        halo(g, contentX + 1.67F, dy, 3.33F, 3.33F, a(dot, 110));
        disque(g, contentX + 1.67F, dy, 1.67F, a(dot, 255));
        float ly = top + 7.33F - 4.7F;
        for (FormattedCharSequence l : lines) {
            g.pose().pushPose();
            g.pose().translate(contentX + 6.67F, ly, 0);
            g.drawString(font, l, 0, 0, a(text, 255), false);
            g.pose().popPose();
            ly += 8;
        }
    }

    // ------------------------------------------------------------------ hammer

    private static void hammer(GuiGraphics g, Font font, int w, int h) {
        float px = px();
        Piece target = HammerController.target();
        float cx = w / 2.0F;
        float cy = h / 2.0F;
        if (target != null) {
            PieceInfoCache.Entry entry = PieceInfoCache.get(target.id);
            S2CPieceInfo info = entry == null ? null : entry.info();
            Tier tier = info == null ? target.tier : Tier.byOrdinal(info.tier());
            boolean demolish = entry != null && entry.canDemolish();
            float cw = 126.67F, ch = 64;
            float x = Math.min(cx + 16.67F, w - cw - 4);
            float y = cy - 13.33F;
            card(g, x, y, cw, ch, px);
            float ix = x + 6, inner = cw - 12;
            float tx = ix;
            if (BuildIcons.draw(g, BuildIcons.tier(tier), ix, y + 7.33F, 10, 1)) {
                tx += 14;
            }
            caps(g, font, BuildClient.pieceName(target.kind()).getString(), MOYEN_GRAS, tx, y + 7 - 2.68F, 0.68F, a(BLANC, 255));
            texte(g, font, BuildClient.tierName(tier).copy().withStyle(s -> s.withFont(MINI)), tx, y + 16.33F - 4.21F, a(0xC8C8C8, 255));
            float barY = y + 23.33F;
            rect(g, ix, barY, ix + inner, barY + 2, a(BLANC, 26));
            if (info != null && info.maxHp() > 0) {
                String max = " / " + info.maxHp();
                String hp = String.valueOf(info.hp());
                float mw = largeur(font, max, MINI);
                texte(g, font, lit(max, MINI), ix + inner - mw, y + 12.33F - 4.21F, a(ATTENUE, 255));
                texte(g, font, lit(hp, MOYEN_GRAS), ix + inner - mw - largeur(font, hp, MOYEN_GRAS), y + 12.33F - 4.7F, a(BLANC, 255));
                float r = Math.max(0, Math.min(1, (float) info.hp() / info.maxHp()));
                int[] col = healthColors(r);
                degradeH(g, ix, barY, ix + inner * r, barY + 2, a(col[0], 255), a(col[1], 255));
                String stab = Component.translatable("crac.hud.stability", info.stability()).getString();
                texte(g, font, lit(stab, MINI), ix, y + 32.33F - 4.21F, a(stabilityColor(info.stability()), 255));
            } else {
                texte(g, font, lit("…", MINI), ix, y + 32.33F - 4.21F, a(ATTENUE, 255));
            }
            if (demolish) {
                int left = entry.demolishSecondsLeft();
                String d = (left < 0 ? Component.translatable("crac.hud.demolish_allowed")
                    : Component.translatable("crac.hud.demolish_time", String.format("%d:%02d", left / 60, left % 60))).getString();
                texte(g, font, lit(d, MINI), ix + inner - largeur(font, d, MINI), y + 32.33F - 4.21F, a(ROSE, 255));
            }
            rect(g, ix, y + 39.33F, ix + inner, y + 39.33F + px, a(BLANC, 23));
            int kcol = a(TEXTE, 184);
            float kx = ix + touche(g, font, keyName(Minecraft.getInstance().options.keyUse, "crac.hud.rmb"), ix, y + 46.67F, 7, 0.3F, 6.67F, px) + 2.67F;
            caps0(g, font, Component.translatable("crac.hud.wheel_hint").getString(), kx, y + 46.67F, kcol);
            kx = ix + touche(g, font, keyName(Minecraft.getInstance().options.keyAttack, "crac.hud.lmb"), ix, y + 55.33F, 7, 0.3F, 6.67F, px) + 2.67F;
            caps0(g, font, Component.translatable("crac.hud.repair_hint").getString(), kx, y + 55.33F, kcol);
        }
        float progress = HammerController.progress();
        if (progress > 0) {
            // ring around the crosshair, "REPAIRING" on its left
            arc(g, cx, cy, 10.5F, 12, 0, 1, a(BLANC, 46));
            arc(g, cx, cy, 9.5F, 13, 0, progress, a(0x7CC0FF, 50));
            arc(g, cx, cy, 10.5F, 12, 0, progress, a(0x7CC0FF, 255));
            String s = Component.translatable("crac.hud.repairing").getString();
            float sw = largeurCaps(font, s, MINI_GRAS, 0.6F);
            caps(g, font, s, MINI_GRAS, cx - 16 - sw, cy - 4.21F, 0.6F, a(0xCFE6FF, 255));
        }
        Component msg = HammerController.message();
        if (msg != null) {
            Component m = Ui.in(msg, MOYEN);
            float mw = font.width(m);
            texte(g, font, m, cx - mw / 2 + 0.5F, cy - 26 + 0.7F, a(0, 150));
            texte(g, font, m, cx - mw / 2, cy - 26, a(ROSE, 255));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Name of a key for the HUD: ours ("Right click") while it has its default binding. */
    private static String keyName(KeyMapping key, String defaultName) {
        return key.isDefault() ? Component.translatable(defaultName).getString() : key.getTranslatedKeyMessage().getString();
    }

    /** Glass card of the HUD (darker than the screens: it sits on the world). */
    private static void card(GuiGraphics g, float x, float y, float w, float h, float px) {
        degradeV(g, x, y + h, x + w, y + h + 4, a(0, 60), a(0, 0));
        rect(g, x, y, x + w, y + h, a(0x0A0C10, 189));
        contour(g, x, y, w, h, px, a(BLANC, 26));
        rect(g, x + px, y + px, x + w - px, y + 2 * px, a(BLANC, 36));
    }

    private static int[] healthColors(float r) {
        if (r > 0.6F) return new int[]{0x3E9A5E, VERT};
        if (r > 0.3F) return new int[]{0xB08A2E, 0xE0C040};
        return new int[]{0xA03A3A, ROUGE};
    }

    private static int stabilityColor(int s) {
        if (s >= 50) return VERT_PALE;
        if (s >= 25) return 0xE0C040;
        return ROUGE_PALE;
    }
}
