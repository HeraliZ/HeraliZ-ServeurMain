package com.dayzhotbar.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

/**
 * The hotbar: nine slanted slots drawn in dark grey chalk over a smudged dark background,
 * the slot number in the corner, and a bolder white chalk frame that slides to
 * the selected slot, spattered with blood. Sits where the vanilla bar was (its top edge at
 * height - 22), so the hearts, food and XP bar keep their place.
 */
public final class HotbarOverlay implements IGuiOverlay {
    private static final int SLOT = 20;
    private static final int BOTTOM_MARGIN = 2;
    private static final int OFFHAND_GAP = 10;
    private static final int GAUGE_WIDTH = 3;

    private static final int BONE = 0xE6E1D3;
    private static final int GLASS = 0x0E0F0F;
    private static final int GLASS_LIT = 0x3A3731;
    private static final int CHALK_WHITE = 0xF4F1E8;

    private static final long IDLE_DELAY_MS = 4000;
    private static final long FADE_MS = 700;
    private static final float IDLE_LEVEL = 0.45f;

    private static final int HINT_GREEN = 0x55E07A;
    /** Slot (0-8) another mod asks to point out, -1 for none (CRac's bases tutorial: "take the plan in hand"). */
    private static volatile int hinted = -1;

    private final ChalkAtlas chalk = new ChalkAtlas();
    private boolean frameRequested;
    private float shownSlot = Float.NaN;
    private int lastSelected = -1;
    private long lastFrameMs;
    private long lastActivityMs;
    private final Item[] lastItems = new Item[10];
    private final int[] lastCounts = new int[10];

    /**
     * Points a hotbar slot out with a soft green glow that pulses, until called again with -1. Called by reflection
     * (CRac's bases tutorial): keep its name and signature.
     */
    public static void surligner(int slot) {
        hinted = slot >= 0 && slot < 9 ? slot : -1;
    }

    /** Called when the vanilla hotbar was cancelled in its place this frame. */
    void requestFrame() {
        frameRequested = true;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (!frameRequested) {
            return;
        }
        frameRequested = false;
        try {
            draw(gui, g, partialTick, width, height);
        } finally {
            // Cancelling the vanilla hotbar's Pre event also skips its Post event, where other mods draw next to the
            // hotbar (Simple Voice Chat's microphone icon, the zombiemod HUD...): it is sent here instead, right after
            // our hotbar, as vanilla would.
            MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Post(gui.getMinecraft().getWindow(), g, partialTick,
                    VanillaGuiOverlay.HOTBAR.type()));
        }
    }

    private void draw(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        if (mc.options.hideGui || !(mc.getCameraEntity() instanceof Player player)) {
            return;
        }
        gui.setupOverlayRenderState(true, false);

        Inventory inv = player.getInventory();
        long now = Util.getMillis();
        float dt = Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        float fade = fade(mc, player, inv, now);
        slide(inv.selected, dt);

        float scale = (float) mc.getWindow().getGuiScale();
        float k = (float) Math.tan(Math.toRadians(HotbarConfig.SKEW.get()));
        int gap = HotbarConfig.SPACING.get();
        int step = SLOT + gap;
        int total = 9 * SLOT + 8 * gap;
        int left = width / 2 - total / 2;
        int top = height - BOTTOM_MARGIN - SLOT;
        int bottom = top + SLOT;

        ItemStack offhand = player.getOffhandItem();
        HumanoidArm offhandSide = player.getMainArm().getOpposite();
        int offhandX = offhandSide == HumanoidArm.LEFT ? left - OFFHAND_GAP - SLOT : left + total + OFFHAND_GAP;
        float attack = 1;
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR && mc.player != null) {
            attack = mc.player.getAttackStrengthScale(0);
        }
        int gaugeX = offhandSide == HumanoidArm.LEFT ? left + total + OFFHAND_GAP : left - OFFHAND_GAP - GAUGE_WIDTH;

        // Chalk slots: smudged background, then the frame, which fades out under the selection sliding over it.
        chalk.begin(g, scale, k);
        for (int i = 0; i < 9; i++) {
            chalk.draw(SlotArt.BACKGROUNDS + i, left + i * step, top,
                    argb(GLASS, (inv.items.get(i).isEmpty() ? 0.32f : 0.55f) * fade));
        }
        if (!offhand.isEmpty()) {
            chalk.draw(SlotArt.BACKGROUNDS + 9, offhandX, top, argb(GLASS, 0.55f * fade));
        }
        int frame = HotbarConfig.frameColor();
        for (int i = 0; i < 9; i++) {
            float covered = Math.max(0, 1 - Math.abs(shownSlot - i));
            if (covered < 1) {
                chalk.draw(SlotArt.FRAMES + i, left + i * step, top, argb(frame, 0.9f * (1 - covered) * fade));
            }
        }
        if (!offhand.isEmpty()) {
            chalk.draw(SlotArt.FRAMES + 9, offhandX, top, argb(frame, 0.9f * fade));
        }
        float sx = left + shownSlot * step;
        chalk.draw(SlotArt.BACKGROUNDS + Math.round(shownSlot), sx, top, argb(GLASS_LIT, 0.5f * fade));
        chalk.draw(SlotArt.SELECTED, sx, top, argb(CHALK_WHITE, fade));
        if (HotbarConfig.BLOOD.get()) {
            int red = HotbarConfig.bloodColor();
            chalk.draw(SlotArt.BLOOD, sx, top, argb(red, 0.95f * fade));
            chalk.draw(SlotArt.BLOOD_RIM, sx, top, argb(shade(red, 0.42f), 0.9f * fade));
            chalk.draw(SlotArt.BLOOD_SHINE, sx, top, argb(tint(red, 0.45f), 0.6f * fade));
        }
        chalk.end();

        Slant p = new Slant(g, k, top + SLOT / 2f);
        if (attack < 1) {
            p.box(gaugeX, top, gaugeX + GAUGE_WIDTH, bottom, argb(GLASS, 0.5f * fade));
            p.box(gaugeX, bottom - SLOT * attack, gaugeX + GAUGE_WIDTH, bottom, argb(BONE, 0.9f * fade));
            g.flush();
        }

        // Rarity tint behind the items (HeraliZ Loot, when present): a soft gradient and a thin line at the bottom
        boolean tint = false;
        for (int i = 0; i < 9; i++) {
            tint |= rarity(p, inv.items.get(i), left + i * step, top, bottom, fade);
        }
        if (!offhand.isEmpty()) {
            tint |= rarity(p, offhand, offhandX, top, bottom, fade);
        }
        if (tint) {
            g.flush();
        }

        // Slot pointed out by another mod: pulsing green glow and frame
        int hint = hinted;
        if (hint >= 0) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
            int x = left + hint * step;
            p.gradient(x + 1, top + 1, x + SLOT - 1, bottom - 1, argb(HINT_GREEN, 0.10f + 0.12f * pulse), argb(HINT_GREEN, 0.28f + 0.22f * pulse));
            int edge = argb(HINT_GREEN, 0.55f + 0.45f * pulse);
            p.box(x, top, x + SLOT, top + 1, edge);
            p.box(x, bottom - 1, x + SLOT, bottom, edge);
            p.box(x, top, x + 1, bottom, edge);
            p.box(x + SLOT - 1, top, x + SLOT, bottom, edge);
            g.flush();
        }

        // Items, with a small count in the bottom corner and the slot number in the top one
        Labels labels = new Labels(g, mc.font, scale);
        int seed = 1;
        for (int i = 0; i < 9; i++) {
            int x = left + i * step;
            renderItem(g, mc.font, player, partialTick, inv.items.get(i), x + 2, top + 2, seed++);
            count(labels, p, inv.items.get(i), x, bottom);
            if (HotbarConfig.NUMBERS.get()) {
                labels.topLeft(Integer.toString(i + 1), p.shear(x + 2.5f, top + 2.5f), top + 2.5f,
                        argb(BONE, (i == inv.selected ? 1f : 0.65f) * fade));
            }
        }
        if (!offhand.isEmpty()) {
            renderItem(g, mc.font, player, partialTick, offhand, offhandX + 2, top + 2, seed);
            count(labels, p, offhand, offhandX, bottom);
        }
    }

    private static java.lang.invoke.MethodHandle rarityColour;
    private static boolean raritySearched;

    /** RGB colour of the item's rarity from HeraliZ Loot (soft dependency, read by reflection), -1 when none. */
    private static int rarityColour(ItemStack stack) {
        if (!raritySearched) {
            raritySearched = true;
            try {
                Class<?> c = Class.forName("fr.heraliz.loot.client.RaretesClient");
                rarityColour = java.lang.invoke.MethodHandles.publicLookup().findStatic(c, "couleur",
                        java.lang.invoke.MethodType.methodType(int.class, ItemStack.class));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                rarityColour = null;
            }
        }
        if (rarityColour == null || stack.isEmpty()) {
            return -1;
        }
        try {
            return (int) rarityColour.invokeExact(stack);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Rarity tint inside one slanted slot; returns whether something was drawn. */
    private static boolean rarity(Slant p, ItemStack stack, int slotX, int top, int bottom, float fade) {
        int c = rarityColour(stack);
        if (c < 0) {
            return false;
        }
        float mid = top + (bottom - top) * 0.55f;
        p.gradient(slotX + 1, top + 1, slotX + SLOT - 1, mid, argb(c, 0), argb(c, 0.07f * fade));
        p.gradient(slotX + 1, mid, slotX + SLOT - 1, bottom - 1, argb(c, 0.07f * fade), argb(c, 0.22f * fade));
        p.box(slotX + 1, bottom - 2, slotX + SLOT - 1, bottom - 1, argb(c, 0.75f * fade));
        return true;
    }

    private static void count(Labels labels, Slant p, ItemStack stack, int slotX, int bottom) {
        if (!stack.isEmpty() && stack.getCount() != 1) {
            labels.bottomRight(Integer.toString(stack.getCount()), p.shear(slotX + SLOT - 2, bottom - 2), bottom - 2, 0xFFFFFFFF);
        }
    }

    /** 1 while the hotbar is in use, then fades down to IDLE_LEVEL. Items stay fully visible. */
    private float fade(Minecraft mc, Player player, Inventory inv, long now) {
        boolean active = mc.screen != null || inv.selected != lastSelected || hinted >= 0;
        for (int i = 0; i < 10; i++) {
            ItemStack stack = i < 9 ? inv.items.get(i) : player.getOffhandItem();
            // Item and count only: a gun's ammo count in its NBT changing while firing is not activity.
            if (stack.getItem() != lastItems[i] || stack.getCount() != lastCounts[i]) {
                lastItems[i] = stack.getItem();
                lastCounts[i] = stack.getCount();
                active = true;
            }
        }
        if (active) {
            lastActivityMs = now;
        }
        if (!HotbarConfig.DIM_WHEN_IDLE.get()) {
            return 1;
        }
        long idle = now - lastActivityMs - IDLE_DELAY_MS;
        return idle <= 0 ? 1 : 1 - (1 - IDLE_LEVEL) * Math.min(1, idle / (float) FADE_MS);
    }

    /** Eases the selection frame towards the selected slot; a mouse-wheel wrap (9 to 1) jumps. */
    private void slide(int selected, float dt) {
        if (Float.isNaN(shownSlot) || Math.abs(selected - lastSelected) == 8) {
            shownSlot = selected;
        } else {
            shownSlot += (selected - shownSlot) * (1 - (float) Math.exp(-dt * 24));
            if (Math.abs(selected - shownSlot) < 0.01f) {
                shownSlot = selected;
            }
        }
        lastSelected = selected;
    }

    /**
     * Same as the vanilla hotbar slot, including the squash when an item is picked up,
     * except for the count, drawn smaller by {@link #count}.
     */
    private static void renderItem(GuiGraphics g, Font font, Player player, float partialTick, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) {
            return;
        }
        float pop = stack.getPopTime() - partialTick;
        if (pop > 0) {
            float s = 1 + pop / 5;
            g.pose().pushPose();
            g.pose().translate(x + 8, y + 12, 0);
            g.pose().scale(1 / s, (s + 1) / 2, 1);
            g.pose().translate(-(x + 8), -(y + 12), 0);
        }
        g.renderItem(player, stack, x, y, seed);
        if (pop > 0) {
            g.pose().popPose();
        }
        // An empty text replaces the count; the durability bar, cooldown and Forge decorators are kept.
        g.renderItemDecorations(font, stack, x, y, "");
    }

    /** rgb darkened to {@code f} of its brightness. */
    private static int shade(int rgb, float f) {
        return Math.round((rgb >> 16 & 0xFF) * f) << 16 | Math.round((rgb >> 8 & 0xFF) * f) << 8 | Math.round((rgb & 0xFF) * f);
    }

    /** rgb moved {@code f} of the way to white. */
    private static int tint(int rgb, float f) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        return Math.round(r + (255 - r) * f) << 16 | Math.round(g + (255 - g) * f) << 8 | Math.round(b + (255 - b) * f);
    }

    private static int argb(int rgb, float alpha) {
        int a = Math.max(5, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | rgb & 0xFFFFFF;
    }

    /** Slanted like italics: sheared horizontally around the hotbar's middle line, as the chalk slots. */
    private static final class Slant {
        private final GuiGraphics g;
        private final float k;
        private final float centerY;

        Slant(GuiGraphics g, float k, float centerY) {
            this.g = g;
            this.k = k;
            this.centerY = centerY;
        }

        float shear(float x, float y) {
            return x + k * (centerY - y);
        }

        /** A slanted rectangle going from {@code top} colour (upper edge) to {@code bottom} colour (lower edge). */
        void gradient(float x0, float y0, float x1, float y1, int top, int bottom) {
            VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
            Matrix4f pose = g.pose().last().pose();
            buffer.vertex(pose, shear(x0, y0), y0, 0).color(top >> 16 & 0xFF, top >> 8 & 0xFF, top & 0xFF, top >>> 24).endVertex();
            buffer.vertex(pose, shear(x0, y1), y1, 0).color(bottom >> 16 & 0xFF, bottom >> 8 & 0xFF, bottom & 0xFF, bottom >>> 24).endVertex();
            buffer.vertex(pose, shear(x1, y1), y1, 0).color(bottom >> 16 & 0xFF, bottom >> 8 & 0xFF, bottom & 0xFF, bottom >>> 24).endVertex();
            buffer.vertex(pose, shear(x1, y0), y0, 0).color(top >> 16 & 0xFF, top >> 8 & 0xFF, top & 0xFF, top >>> 24).endVertex();
        }

        /** A plain slanted rectangle, with the same winding as GuiGraphics.fill. */
        void box(float x0, float y0, float x1, float y1, int color) {
            VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
            Matrix4f pose = g.pose().last().pose();
            int a = color >>> 24;
            int r = color >> 16 & 0xFF;
            int gr = color >> 8 & 0xFF;
            int b = color & 0xFF;
            buffer.vertex(pose, shear(x0, y0), y0, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, shear(x0, y1), y1, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, shear(x1, y1), y1, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(pose, shear(x1, y0), y0, 0).color(r, gr, b, a).endVertex();
        }
    }

    /** Small text whose font pixels each cover whole screen pixels, so it stays sharp at every GUI scale. */
    private static final class Labels {
        private static final int DIGIT_HEIGHT = 7;

        private final GuiGraphics g;
        private final Font font;
        private final float guiScale;
        private final float size;

        Labels(GuiGraphics g, Font font, float guiScale) {
            this.g = g;
            this.font = font;
            this.guiScale = guiScale;
            this.size = Math.max(1, Math.round(guiScale * 0.5f)) / guiScale;
        }

        void topLeft(String text, float x, float y, int color) {
            draw(text, x, y, color);
        }

        void bottomRight(String text, float right, float bottom, int color) {
            draw(text, right - (font.width(text) - 1) * size, bottom - DIGIT_HEIGHT * size, color);
        }

        private void draw(String text, float x, float y, int color) {
            g.pose().pushPose();
            g.pose().translate(snap(x), snap(y), 200);
            g.pose().scale(size, size, 1);
            g.drawString(font, text, 0, 0, color, true);
            g.pose().popPose();
        }

        private float snap(float v) {
            return Math.round(v * guiScale) / guiScale;
        }
    }
}
