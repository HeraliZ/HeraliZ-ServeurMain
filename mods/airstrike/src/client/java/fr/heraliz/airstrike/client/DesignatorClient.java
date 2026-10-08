package fr.heraliz.airstrike.client;

import fr.heraliz.airstrike.item.PayloadItem;
import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.item.DesignatorItem;
import fr.heraliz.airstrike.network.DesignatePacket;
import fr.heraliz.airstrike.network.LoadCartridgePacket;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.registry.ModSounds;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Client state of the binoculars: zoom, payload selection, range finder, designation input. */
public final class DesignatorClient {
    public static final float[] ZOOMS = {2.5f, 4.0f, 6.5f, 10.0f};
    public static final int SCOPE_DELAY = 5;

    public static final KeyMapping CYCLE_KEY = new KeyMapping("key.airstrike.cycle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.airstrike");

    public static int zoomIndex = 1;
    /** Range finder result of the last tick. */
    public static Vec3 aimPoint;
    public static double aimDistance = -1;
    public static boolean aimInRange;
    /** Game time of the last designation (lock animation). */
    public static long lockTime = -1000;
    public static long errorTime = -1000;
    public static String errorKey = "";

    private static boolean wasScoped;
    /** Ticks the swap key has been held: a short tap cycles, holding opens the wheel. */
    private static int keyHeldTicks;
    private static final int WHEEL_DELAY = 5;
    private static boolean sensInit;
    private static float lastYaw, lastPitch;

    private DesignatorClient() {
    }

    public static boolean isUsingDesignator(Player p) {
        return p != null && p.isUsingItem() && p.getUseItem().getItem() instanceof DesignatorItem;
    }

    public static boolean isScoped() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null && mc.options.getCameraType().isFirstPerson() && isUsingDesignator(p)
                && p.getTicksUsingItem() >= SCOPE_DELAY;
    }

    public static float zoom() {
        return ZOOMS[Mth.clamp(zoomIndex, 0, ZOOMS.length - 1)];
    }

    public static int maxRange() {
        try {
            return AirstrikeConfig.MAX_RANGE.get();
        } catch (IllegalStateException e) {
            return 150;
        }
    }

    public static List<StrikeType> availableTypes() {
        List<StrikeType> out = new ArrayList<>();
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return out;
        for (StrikeType t : StrikeType.values()) {
            if (p.getAbilities().instabuild || PayloadItem.countPayload(p.getInventory(), t) > 0) out.add(t);
        }
        return out;
    }

    public static int payloadCount(StrikeType t) {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? 0 : PayloadItem.countPayload(p.getInventory(), t);
    }

    private static void play(SoundEvent s, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, 0.7f));
    }

    /** The binoculars in hand (main hand first), or EMPTY. */
    public static ItemStack heldDesignator(Player p) {
        if (p == null) return ItemStack.EMPTY;
        if (p.getMainHandItem().getItem() instanceof DesignatorItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof DesignatorItem) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** Cartridge loaded in the held binoculars, or null. */
    public static StrikeType loaded() {
        return DesignatorItem.cartridge(heldDesignator(Minecraft.getInstance().player));
    }

    /** Key G / shift + scroll: swaps the cartridge for the next type carried in the inventory. */
    public static void cycle(int dir) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ItemStack held = heldDesignator(p);
        if (held.isEmpty() || mc.level == null || mc.gameMode == null) return;
        long now = mc.level.getGameTime();
        if (DesignatorItem.isReloading(held, now) || now < DesignatorItem.clientReloadBlock) return;
        StrikeType current = DesignatorItem.cartridge(held);
        List<StrikeType> available = availableTypes();
        StrikeType next = null;
        int n = StrikeType.values().length;
        int start = current == null ? (dir > 0 ? -1 : 0) : current.ordinal();
        for (int i = 1; i <= n; i++) {
            StrikeType t = StrikeType.byId(Math.floorMod(start + dir * i, n));
            if (t != current && available.contains(t)) {
                next = t;
                break;
            }
        }
        if (next == null) {
            error(current == null ? "hud.airstrike.status.no_payload" : "hud.airstrike.status.no_other_payload");
            return;
        }
        load(next);
    }

    /** Asks the server to load this cartridge into the held binoculars (reload animation). */
    public static void load(StrikeType type) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ItemStack held = heldDesignator(p);
        if (held.isEmpty() || mc.level == null || mc.gameMode == null) return;
        long now = mc.level.getGameTime();
        if (DesignatorItem.isReloading(held, now) || now < DesignatorItem.clientReloadBlock) return;
        if (p.isUsingItem()) mc.gameMode.releaseUsingItem(p);
        DesignatorItem.clientReloadBlock = now + 10;
        Network.CHANNEL.sendToServer(new LoadCartridgePacket(type));
    }

    /** Opens the cartridge wheel (key held). */
    public static void openWheel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || heldDesignator(mc.player).isEmpty()) return;
        if (mc.player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(mc.player);
        mc.setScreen(new CartridgeWheelScreen());
        play(ModSounds.DESIGNATOR_BEEP.get(), 0.8f);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean using = isUsingDesignator(p);
        // tap the key: next cartridge; hold it: cartridge wheel
        while (CYCLE_KEY.consumeClick()) {
            // presses are tracked with isDown below
        }
        boolean holding = !heldDesignator(p).isEmpty();
        if (CYCLE_KEY.isDown() && mc.screen == null && holding) {
            if (++keyHeldTicks == WHEEL_DELAY) openWheel();
        } else {
            if (keyHeldTicks > 0 && keyHeldTicks < WHEEL_DELAY && mc.screen == null && holding) cycle(1);
            keyHeldTicks = 0;
        }
        boolean scoped = isScoped();
        if (scoped && !wasScoped) play(ModSounds.DESIGNATOR_ZOOM.get(), 1.0f);
        wasScoped = scoped;
        if (using) {
            // the range finder measures further than the designation range (out of range targets show in amber)
            HitResult hit = p.pick(Math.max(300, maxRange() * 2), 1.0f, true);
            if (hit.getType() != HitResult.Type.MISS) {
                aimPoint = hit.getLocation();
                aimDistance = aimPoint.distanceTo(p.getEyePosition());
                aimInRange = aimDistance <= maxRange();
            } else {
                aimPoint = null;
                aimDistance = -1;
                aimInRange = false;
            }
        }
    }

    static void error(String key) {
        Minecraft mc = Minecraft.getInstance();
        errorKey = key;
        errorTime = mc.level == null ? 0 : mc.level.getGameTime();
        play(ModSounds.RADIO_DENIED.get(), 1.0f);
        // outside the binoculars view the HUD is hidden: use the action bar
        if (!isScoped() && mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key)
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
    }

    /** Left click while looking through the binoculars. */
    public static void designate() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (p.getCooldowns().isOnCooldown(p.getUseItem().getItem())) {
            error("hud.airstrike.status.cooldown");
            return;
        }
        if (aimPoint == null || !aimInRange) {
            error("hud.airstrike.status.no_target");
            return;
        }
        if (loaded() == null) {
            error("hud.airstrike.status.no_cartridge");
            return;
        }
        if (ClientStrikes.myActive() != null) {
            error("hud.airstrike.status.busy");
            return;
        }
        Network.CHANNEL.sendToServer(new DesignatePacket(aimPoint));
        lockTime = mc.level.getGameTime();
        play(ModSounds.DESIGNATOR_LOCK.get(), 1.0f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Input hooks (called from ClientEvents)

    public static boolean onMouseButton(int button, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !isScoped()) return false;
        if (action == GLFW.GLFW_PRESS && mc.options.keyAttack.matchesMouse(button)) {
            designate();
            return true;
        }
        return false;
    }

    public static boolean onScroll(double delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !isScoped() || delta == 0) return false;
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            cycle(delta > 0 ? -1 : 1);
        } else {
            int n = Mth.clamp(zoomIndex + (delta > 0 ? 1 : -1), 0, ZOOMS.length - 1);
            if (n != zoomIndex) {
                zoomIndex = n;
                play(ModSounds.DESIGNATOR_ZOOM.get(), 0.9f + n * 0.1f);
            }
        }
        return true;
    }

    /** Scales the mouse look while zoomed (called at the start of every frame, after the mouse turned the player). */
    public static void onRenderTickStart() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !isScoped() || mc.screen != null) {
            sensInit = false;
            return;
        }
        float yaw = p.getYRot(), pitch = p.getXRot();
        if (!sensInit) {
            sensInit = true;
            lastYaw = yaw;
            lastPitch = pitch;
            return;
        }
        float f = Math.min(1f, 1.15f / zoom());
        float dy = yaw - lastYaw, dp = pitch - lastPitch;
        if (Math.abs(dy) < 60f && Math.abs(dp) < 60f) {
            float ny = lastYaw + dy * f;
            float np = Mth.clamp(lastPitch + dp * f, -90f, 90f);
            p.setYRot(ny);
            p.setXRot(np);
            p.yRotO += ny - yaw;
            p.xRotO += np - pitch;
            yaw = ny;
            pitch = np;
        }
        lastYaw = yaw;
        lastPitch = pitch;
    }
}
