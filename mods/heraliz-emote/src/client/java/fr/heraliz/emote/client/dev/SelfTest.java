package fr.heraliz.emote.client.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import fr.heraliz.emote.HeralizEmote;
import fr.heraliz.emote.client.BorrowController;
import fr.heraliz.emote.client.EmoteAudio;
import fr.heraliz.emote.client.EmoteInstaller;
import fr.heraliz.emote.client.HeralizConfig;
import fr.heraliz.emote.client.HeralizConfigScreen;
import fr.heraliz.emote.client.catalog.EmoteCatalog;
import fr.heraliz.emote.client.gui.EmoteBrowserScreen;
import io.github.kosmx.emotes.api.events.client.ClientEmoteAPI;
import io.github.kosmx.emotes.arch.gui.EmoteMenuImpl;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayer;
import io.github.kosmx.emotes.executor.emotePlayer.IEmotePlayerEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Development only (excluded from the release jar): scripted scenario run by CI with Emotecraft
 * installed. Checks that the 15 emotes reach Emotecraft, plays each one in front of the camera,
 * borrows an emote from another player and takes screenshots along the way.
 * Enabled with the system property {@code heraliz.selftest=true}.
 */
@Mod.EventBusSubscriber(modid = HeralizEmote.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("heraliz.selftest");
    private static final double X = 0.5;
    private static final double Y = -60;
    private static final double Z = 0.5;

    private static int state;
    private static int ticks;
    private static int failures;
    private static final List<Step> STEPS = new ArrayList<>();
    private static int stepIndex;
    private static int stepTick;
    private static RemotePlayer dancer;

    private interface Action {
        void run(Minecraft mc, int t);
    }

    private record Step(String name, int length, Action action) {
    }

    private record Bundled(String id, String name, UUID uuid) {
    }

    private SelfTest() {
    }

    private static void log(String s) {
        HeralizEmote.LOGGER.info("[SELFTEST] {}", s);
    }

    private static net.minecraft.world.phys.Vec3 walkFrom;

    private static void checkVolumes(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean others = HeralizConfig.OTHER_PLAYERS_SOUNDS.get();
        try {
            SoundEvent harp = SoundEvents.NOTE_BLOCK_HARP.value();
            SoundInstance own = new SimpleSoundInstance(harp, SoundSource.PLAYERS, 1, 1, RandomSource.create(), p.getX(), p.getY(), p.getZ());
            SoundInstance other = new SimpleSoundInstance(harp, SoundSource.PLAYERS, 1, 1, RandomSource.create(), p.getX() + 5, p.getY(), p.getZ());
            SoundInstance noteBlock = new SimpleSoundInstance(harp, SoundSource.RECORDS, 1, 1, RandomSource.create(), p.getX() + 5, p.getY(), p.getZ());
            SoundInstance step = new SimpleSoundInstance(SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 1, 1, RandomSource.create(), p.getX() + 5, p.getY(), p.getZ());
            HeralizConfig.OTHER_PLAYERS_SOUNDS.set(true);
            float otherOn = EmoteAudio.factor(other);
            HeralizConfig.OTHER_PLAYERS_SOUNDS.set(false);
            float ownOff = EmoteAudio.factor(own), otherOff = EmoteAudio.factor(other);
            float block = EmoteAudio.factor(noteBlock), wool = EmoteAudio.factor(step);
            float music = EmoteAudio.MUSIC_VOLUME;
            if (ownOff != music || otherOn != music || otherOff != 0.0F || block != 1.0F || wool != 1.0F) {
                fail("emote volumes: own " + ownOff + ", other player " + otherOn + " / " + otherOff + ", note block " + block + ", other sound " + wool);
            } else {
                log("emote volumes: songs at " + Math.round(music * 100) + " %, other players' off when asked, note blocks and other sounds untouched");
            }
        } finally {
            HeralizConfig.OTHER_PLAYERS_SOUNDS.set(others);
        }
    }

    private static void fail(String s) {
        failures++;
        HeralizEmote.LOGGER.error("[SELFTEST] FAIL {}", s);
    }

    private static void cmd(Minecraft mc, String command) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> log("screenshot " + name));
    }

    private static void face(LocalPlayer p, float yaw, float pitch) {
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
    }

    private static List<Bundled> bundled() throws Exception {
        List<Bundled> out = new ArrayList<>();
        for (String id : EmoteInstaller.index()) {
            byte[] data = EmoteInstaller.resource(id + ".json");
            if (data == null) {
                fail("missing resource " + id);
                continue;
            }
            JsonObject o = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            out.add(new Bundled(id, o.get("name").getAsString(), UUID.fromString(o.get("uuid").getAsString())));
        }
        return out;
    }

    private static KeyframeAnimation find(UUID uuid) {
        for (KeyframeAnimation a : ClientEmoteAPI.clientEmoteList()) {
            if (uuid.equals(a.getUuid())) return a;
        }
        return null;
    }

    private static IEmotePlayer playing(Object player) {
        if (player instanceof IEmotePlayerEntity<?> holder && holder.isPlayingEmote()) return holder.getEmote();
        return null;
    }

    private static void buildSteps(Minecraft mc) throws Exception {
        List<Bundled> emotes = bundled();
        log("bundled emotes: " + emotes.size());
        if (emotes.size() != EmoteCatalog.all().size()) fail("catalog has " + EmoteCatalog.all().size() + " emotes, index " + emotes.size());

        // installed next to Emotecraft's emotes
        Path dir = EmoteInstaller.emotesDirectory();
        for (Bundled b : emotes) {
            if (!Files.isRegularFile(dir.resolve("heraliz_" + b.id + ".json"))) fail("not installed: " + b.id);
            if (!Files.isRegularFile(dir.resolve("heraliz_" + b.id + ".png"))) fail("icon not installed: " + b.id);
        }

        // Emotecraft's own emotes (waving, clap, backflip...) are left out
        long builtin = ClientEmoteAPI.clientEmoteList().stream().filter(a -> a.extraData.containsKey("isBuiltin")).count();
        if (builtin > 0) fail(builtin + " emotes of Emotecraft are still listed");
        else log("Emotecraft's own emotes are left out (" + ClientEmoteAPI.clientEmoteList().size() + " emotes listed)");

        // Emotecraft's own menu, with the new emotes in its list
        STEPS.add(new Step("emotecraft menu", 50, (m, t) -> {
            if (t == 1) m.setScreen(new EmoteMenuImpl(null));
            if (t == 40) shot(m, "00_emotecraft_menu");
            if (t == 50) m.setScreen(null);
        }));
        STEPS.add(new Step("config screen", 30, (m, t) -> {
            if (t == 1) m.setScreen(new HeralizConfigScreen(null));
            if (t == 20) shot(m, "00b_config_screen");
            if (t == 30) m.setScreen(null);
        }));
        // the music of emotes at half volume, other players' on or off (also on Music & Sounds)
        STEPS.add(new Step("emote sounds", 30, (m, t) -> {
            if (t == 1) m.setScreen(new SoundOptionsScreen(null, m.options));
            if (t == 20) shot(m, "00e_sound_options");
            if (t == 30) {
                m.setScreen(null);
                checkVolumes(m);
            }
        }));
        // the emote menu with its live preview
        String[] showcase = {"guitar", "piano", "birthday", "chef", "basketball"};
        for (int i = 0; i < showcase.length; i++) {
            String id = showcase[i];
            String name = "00c_menu_" + i;
            STEPS.add(new Step("menu " + id, 40, (m, t) -> {
                if (t == 1) {
                    EmoteBrowserScreen screen = new EmoteBrowserScreen();
                    m.setScreen(screen);
                    EmoteCatalog.EmoteInfo info = EmoteCatalog.all().get(id);
                    if (info != null) screen.select(info.uuid());
                }
                if (t == 34) shot(m, name);
                if (t == 40) m.setScreen(null);
            }));
        }

        int n = 1;
        for (Bundled b : emotes) {
            KeyframeAnimation anim = find(b.uuid);
            if (anim == null) {
                fail("Emotecraft did not load " + b.id + " (" + b.name + ")");
                continue;
            }
            log(String.format(Locale.ROOT, "loaded %s \"%s\" loop=%s ticks=%d..%d", b.id, b.name, anim.isInfinite, anim.returnToTick, anim.endTick));
            int[] at;
            if (anim.isInfinite) {
                int period = anim.endTick - anim.returnToTick + 1;
                at = new int[]{anim.returnToTick + 2, anim.returnToTick + period / 4 + 2, anim.returnToTick + period / 2 + 2,
                        anim.returnToTick + 3 * period / 4 + 2};
            } else {
                at = new int[]{anim.endTick / 5, 2 * anim.endTick / 5, 3 * anim.endTick / 5, 4 * anim.endTick / 5};
            }
            String prefix = String.format(Locale.ROOT, "%02d_%s", n++, b.id);
            int length = at[3] + 8;
            STEPS.add(new Step("emote " + b.id, length, (m, t) -> {
                LocalPlayer p = m.player;
                if (p == null) return;
                if (t == 1) {
                    face(p, 0, 0);
                    if (!ClientEmoteAPI.playEmote(anim)) fail("Emotecraft refused to play " + b.id);
                }
                if (t == 4) {
                    IEmotePlayer e = playing(p);
                    if (e == null || !b.uuid.equals(e.getData().getUuid())) fail(b.id + " is not playing");
                }
                for (int i = 0; i < at.length; i++) {
                    if (t == at[i]) shot(m, prefix + "_" + i);
                }
                if (t == length) ClientEmoteAPI.playEmote(null);
            }));
        }

        // borrowing: another player dances the floss, we look at them and borrow it
        Bundled floss = emotes.stream().filter(b -> b.id.equals("floss")).findFirst().orElse(null);
        KeyframeAnimation flossAnim = floss == null ? null : find(floss.uuid);
        STEPS.add(new Step("borrow", 120, (m, t) -> {
            LocalPlayer p = m.player;
            if (p == null || m.level == null || flossAnim == null) return;
            if (t == 1) {
                cmd(m, "item replace entity @p weapon.mainhand with minecraft:air");
                dancer = new RemotePlayer(m.level, new GameProfile(UUID.fromString("5c3a4e2b-0f6d-4e8a-9a8b-1d2c3e4f5a6b"), "Danseur"));
                dancer.setPos(X + 2.2, Y, Z);
                dancer.setOldPosAndRot();
                dancer.setYRot(90);
                dancer.yBodyRot = 90;
                dancer.yBodyRotO = 90;
                dancer.setYHeadRot(90);
                dancer.yHeadRotO = 90;
                m.level.addPlayer(-4242, dancer);
                ((IEmotePlayerEntity<?>) dancer).playEmote(flossAnim, 0, false);
                m.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            if (t >= 2 && t < 24) face(p, -90, 8); // looking at the dancer (east)
            if (t == 17) {
                if (!BorrowController.tryBorrow(m)) fail("could not borrow from the dancer");
                else log("borrowed, pending=" + BorrowController.hasPending());
            }
            if (t == 22) checkInStep(p);
            if (t == 24) {
                // side by side, facing the camera
                dancer.setPos(X + 1.3, Y, Z);
                dancer.setOldPosAndRot();
                dancer.setYRot(0);
                dancer.yBodyRot = 0;
                dancer.yBodyRotO = 0;
                dancer.setYHeadRot(0);
                dancer.yHeadRotO = 0;
                face(p, 0, 0);
                m.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            if (t == 30 || t == 33 || t == 36) shot(m, "90_borrow_" + (t - 30) / 3);
            if (t == 40) checkInStep(p);
            if (t == 100) {
                ClientEmoteAPI.playEmote(null);
                m.level.removeEntity(dancer.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
        }));

        // walking emotes: going forward keeps the cart rolling, going sideways stops it
        // (EmoteHolderMixin); and an emote with props on the ground keeps the body still while
        // the camera turns (EmoteMotion)
        KeyframeAnimation cart = emotes.stream().filter(b -> b.id.equals("shopping_cart")).findFirst().map(b -> find(b.uuid)).orElse(null);
        KeyframeAnimation hoop = emotes.stream().filter(b -> b.id.equals("basketball")).findFirst().map(b -> find(b.uuid)).orElse(null);
        STEPS.add(new Step("walking and fixed scenes", 100, (m, t) -> {
            if (t == 1) walkFrom = null;
            LocalPlayer p = m.player;
            if (p == null) return;
            if (cart == null || hoop == null) {
                if (t == 1) fail("the cart or the basketball emote is missing");
                return;
            }
            if (t == 1) {
                cmd(m, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y, Z));
                face(p, 0, 0);
                if (!ClientEmoteAPI.playEmote(cart)) fail("Emotecraft refused to play the cart");
            }
            // the real movement keys: the input goes through EmoteMotion (slower) and Emotecraft's
            // stop check (EmoteHolderMixin), like a player walking with the emote
            m.options.keyUp.setDown(t >= 30 && t < 50);
            if (t == 30) walkFrom = p.position();
            if (t == 50) {
                double walked = p.position().distanceTo(walkFrom);
                if (playing(p) == null) fail("the cart stopped while walking forward");
                else if (walked < 0.5) fail("the player did not walk with the cart (" + walked + " blocks)");
                else log(String.format(Locale.ROOT, "walking forward: the cart keeps going (%.2f blocks in 20 ticks)", walked));
            }
            m.options.keyLeft.setDown(t >= 52 && t < 62);
            if (t == 64) {
                if (playing(p) != null) fail("the cart kept going while walking sideways");
                else log("walking sideways: the cart stopped");
            }
            if (t == 66) {
                cmd(m, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y, Z));
                face(p, 0, 0);
            }
            if (t == 70 && !ClientEmoteAPI.playEmote(hoop)) fail("Emotecraft refused to play basketball");
            if (t >= 76 && t < 86) {
                // turning the camera (not the body: Emotecraft turns the body to it)
                p.setYRot(4 * (t - 75));
                p.setYHeadRot(4 * (t - 75));
            }
            if (t == 88) {
                // yBodyRotO: what this frame renders from, whichever END listener ran first
                float drift = Math.abs(net.minecraft.util.Mth.wrapDegrees(p.yBodyRotO));
                if (drift > 1) fail("the body turned with the camera during basketball (" + drift + " degrees)");
                else log("fixed scene: the body kept its direction while the camera turned");
            }
            if (t == 96) {
                ClientEmoteAPI.playEmote(null);
                face(p, 0, 0);
            }
        }));

        // icon renders: Steve frozen in each icon pose on green
        IconStudio studio = new IconStudio();
        for (Bundled b : emotes) {
            KeyframeAnimation anim = find(b.uuid);
            EmoteCatalog.EmoteInfo info = EmoteCatalog.get(b.uuid);
            if (anim == null || info == null) continue;
            STEPS.add(new Step("icon " + b.id, 4, (m, t) -> {
                if (t == 1) {
                    if (m.screen != studio) m.setScreen(studio);
                    studio.pose(anim, info.icon());
                }
                if (t == 3) shot(m, "icon_" + b.id);
            }));
        }
        STEPS.add(new Step("icons done", 2, (m, t) -> {
            if (t == 2) m.setScreen(null);
        }));
    }

    private static void checkInStep(LocalPlayer p) {
        IEmotePlayer mine = playing(p);
        IEmotePlayer theirs = playing(dancer);
        if (mine == null || theirs == null || !theirs.getData().getUuid().equals(mine.getData().getUuid())) {
            fail("borrowed floss is not playing");
        } else {
            log("in step: mine tick " + mine.getTick() + ", dancer tick " + theirs.getTick());
            if (Math.abs(mine.getTick() - theirs.getTick()) > 2) fail("not in step: " + mine.getTick() + " vs " + theirs.getTick());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            switch (state) {
                case 0 -> {
                    if (mc.screen instanceof TitleScreen && ticks > 40) {
                        log("creating world");
                        LevelSettings settings = new LevelSettings("selftest", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT);
                        WorldOptions options = new WorldOptions(12345L, false, false);
                        mc.createWorldOpenFlows().createFreshLevel("selftest", settings, options,
                                reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                        state = 1;
                        ticks = 0;
                    }
                }
                case 1 -> {
                    if (mc.level != null && mc.player != null && mc.screen == null) {
                        if (ticks > 60) {
                            setupWorld(mc);
                            state = 2;
                            ticks = 0;
                        }
                    } else if (ticks > 6000) {
                        fail("world never loaded");
                        finish(mc);
                    }
                }
                case 2 -> {
                    if (mc.player != null) face(mc.player, 0, 0);
                    if (ticks == 60) {
                        buildSteps(mc);
                        stepIndex = 0;
                        stepTick = 0;
                        state = STEPS.isEmpty() ? 4 : 3;
                        log("starting " + STEPS.size() + " steps");
                    }
                }
                case 3 -> {
                    if (mc.player == null) {
                        finish(mc);
                        return;
                    }
                    Step s = STEPS.get(stepIndex);
                    stepTick++;
                    s.action.run(mc, stepTick);
                    if (stepTick >= s.length) {
                        log("step done: " + s.name);
                        stepIndex++;
                        stepTick = 0;
                        if (stepIndex >= STEPS.size()) {
                            state = 4;
                            ticks = 0;
                        }
                    }
                }
                case 4 -> {
                    if (ticks > 20) finish(mc);
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            HeralizEmote.LOGGER.error("[SELFTEST] failure", t);
            failures++;
            finish(mc);
        }
    }

    private static void setupWorld(Minecraft mc) {
        log("setting up world");
        cmd(mc, "gamerule doDaylightCycle false");
        cmd(mc, "gamerule doWeatherCycle false");
        cmd(mc, "gamerule doMobSpawning false");
        cmd(mc, "time set 6000");
        cmd(mc, "weather clear");
        // a plain floor and a wall behind the dancers, so the poses read well
        cmd(mc, "fill -8 -61 -8 8 -61 8 smooth_stone");
        cmd(mc, "fill -8 -60 -3 8 -55 -3 light_gray_concrete");
        cmd(mc, String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 0 0", X, Y, Z));
        if (mc.player != null) {
            mc.player.setPos(X, Y, Z);
            mc.player.getAbilities().flying = false;
        }
        // something in the hand: hidden during emotes with props, visible otherwise
        cmd(mc, "item replace entity @p weapon.mainhand with minecraft:diamond_sword");
        mc.options.hideGui = true;
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }

    private static void finish(Minecraft mc) {
        if (failures == 0) log("finished OK");
        else log("finished with " + failures + " failure(s)");
        try {
            new File(mc.gameDirectory, "selftest.done").createNewFile();
        } catch (Exception ignored) {
        }
        state = 99;
        mc.stop();
    }

    static {
        if (ENABLED) log("self test enabled");
    }
}
