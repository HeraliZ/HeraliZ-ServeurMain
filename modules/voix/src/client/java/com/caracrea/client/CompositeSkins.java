package com.caracrea.client;

import com.caracrea.CaraCrea;
import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.FaceState;
import com.caracrea.paint.Canvas;
import com.caracrea.paint.Colors;
import com.caracrea.paint.Rect;
import com.caracrea.paint.SkinLayout;
import com.caracrea.paint.SkinPainter;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The painted textures of every player: the skin (vanilla layout, 256x256:
 * tab list, first-person hands, other mods, cubic body) and the atlas of
 * the 3D body (512x512, only on the GPU, with the high-definition head).
 *
 * <p>Everything is painted on background threads, images included: the
 * render thread only uploads. A burst of changes (a slider dragged in the
 * creator) paints only the latest look. The face is painted in all its
 * states (eyes and mouth shapes) ahead of time, so blinking and talking
 * just upload one of them: no painting ever happens during gameplay. Those
 * states are only prepared for the players close enough to be animated,
 * after every skin around is painted, and freed when they stay away.
 */
public final class CompositeSkins {
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    /** Players around; the creator preview has its own thread so it always reacts at once. */
    private static final ExecutorService PLAYERS = painter("CaraCrea skin painter");
    private static final ExecutorService PREVIEW = painter("CaraCrea preview painter");
    private static ResourceLocation placeholderSkin;
    private static ResourceLocation placeholderDetail;

    private CompositeSkins() {
    }

    private static ExecutorService painter(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY + 1);
            return thread;
        });
    }

    private static final class Entry {
        final UUID uuid;
        final ResourceLocation skinLocation;
        final DynamicTexture skin;
        final ResourceLocation detailLocation;
        final DetailTexture detail;
        /** The latest look asked for; paint jobs for older looks give up. */
        final AtomicInteger wanted = new AtomicInteger();
        Appearance appearance;
        FaceState state = FaceState.NEUTRAL;
        boolean painted;
        /** Whether the face states are needed (the player is close enough to be animated). */
        boolean facesWanted;
        /** Look version whose face states are being painted, 0 if none. */
        int facesPainting;
        /** The face in each state, ready to upload; null until painted. */
        NativeImage[] hdFaces;
        NativeImage[][] flatFaces;
        int shownFace = -1;

        Entry(UUID uuid) {
            this.uuid = uuid;
            Minecraft mc = Minecraft.getInstance();
            skin = new DynamicTexture(Canvas.SKIN_SIZE, Canvas.SKIN_SIZE, false);
            skinLocation = mc.getTextureManager().register("caracrea/skin/" + uuid, skin);
            detail = new DetailTexture(Canvas.DETAIL_SIZE);
            detailLocation = new ResourceLocation(CaraCrea.MOD_ID, "detail/" + uuid);
            mc.getTextureManager().register(detailLocation, detail);
        }

        boolean wants(int version) {
            return ENTRIES.get(uuid) == this && wanted.get() == version;
        }

        void closeFaces() {
            if (hdFaces != null) {
                for (NativeImage image : hdFaces) {
                    image.close();
                }
            }
            if (flatFaces != null) {
                for (NativeImage[] regions : flatFaces) {
                    for (NativeImage image : regions) {
                        image.close();
                    }
                }
            }
            hdFaces = null;
            flatFaces = null;
            shownFace = -1;
        }
    }

    private static Entry entry(UUID uuid) {
        Entry entry = ENTRIES.get(uuid);
        if (entry == null) {
            entry = new Entry(uuid);
            entry.appearance = ClientAppearances.resolve(uuid);
            ENTRIES.put(uuid, entry);
            schedule(entry);
        }
        return entry;
    }

    /** The skin texture of a player (vanilla layout; a placeholder until painted). */
    public static ResourceLocation skinFor(UUID uuid) {
        if (uuid == null || !RenderSystem.isOnRenderThread()) {
            return placeholderSkin();
        }
        Entry entry = entry(uuid);
        return entry.painted ? entry.skinLocation : placeholderSkin();
    }

    /** The atlas of a player's 3D body (a placeholder until painted). */
    public static ResourceLocation detailFor(UUID uuid) {
        if (uuid == null || !RenderSystem.isOnRenderThread()) {
            return placeholderDetail();
        }
        Entry entry = entry(uuid);
        return entry.painted ? entry.detailLocation : placeholderDetail();
    }

    private static ExecutorService executor(Entry entry) {
        return ClientAppearances.isPreview(entry.uuid) ? PREVIEW : PLAYERS;
    }

    /** Paints the entry's look in the background; its face states follow if they are wanted. */
    private static void schedule(Entry entry) {
        int version = entry.wanted.incrementAndGet();
        Appearance appearance = entry.appearance;
        Minecraft mc = Minecraft.getInstance();
        executor(entry).execute(() -> {
            if (entry.wanted.get() != version) {
                return; // a newer look is already waiting
            }
            try {
                SkinPainter.Painted painted = SkinPainter.paint(appearance, FaceState.NEUTRAL);
                NativeImage skin = toImage(painted.skin().pixels(), Canvas.SKIN_SIZE, Canvas.SKIN_SIZE);
                NativeImage detail = toImage(painted.detail().pixels(), Canvas.DETAIL_SIZE, Canvas.DETAIL_SIZE);
                mc.execute(() -> {
                    if (!entry.wants(version)) {
                        skin.close();
                        detail.close();
                        return;
                    }
                    entry.skin.setPixels(skin);
                    entry.skin.upload();
                    entry.detail.upload(detail);
                    entry.closeFaces();
                    entry.facesPainting = 0;
                    entry.shownFace = SkinPainter.index(FaceState.NEUTRAL);
                    entry.painted = true;
                    if (entry.facesWanted) {
                        scheduleFaces(entry);
                    }
                });
            } catch (RuntimeException e) {
                CaraCrea.LOGGER.error("Could not paint {}", appearance, e);
            }
        });
    }

    /**
     * Paints the face states of the entry's current look in the background. Queued behind the
     * textures already waiting, so players arriving together all get their skin first.
     */
    private static void scheduleFaces(Entry entry) {
        int version = entry.wanted.get();
        if (entry.facesPainting == version || entry.hdFaces != null) {
            return;
        }
        entry.facesPainting = version;
        Appearance appearance = entry.appearance;
        Minecraft mc = Minecraft.getInstance();
        executor(entry).execute(() -> {
            if (entry.wanted.get() != version) {
                return;
            }
            try {
                long start = System.nanoTime();
                SkinPainter.FaceVariants faces = SkinPainter.paintFaceVariants(appearance);
                NativeImage[] hd = new NativeImage[SkinPainter.FACE_STATES];
                NativeImage[][] flat = new NativeImage[SkinPainter.FACE_STATES][];
                Rect hdRect = SkinPainter.hdFaceRect();
                Rect[] regions = SkinPainter.faceRegions();
                for (int i = 0; i < hd.length; i++) {
                    hd[i] = toImage(faces.hd()[i], hdRect.w(), hdRect.h());
                    flat[i] = new NativeImage[regions.length];
                    for (int r = 0; r < regions.length; r++) {
                        flat[i][r] = toImage(faces.flat()[i][r], regions[r].w(), regions[r].h());
                    }
                }
                long micros = (System.nanoTime() - start) / 1000;
                mc.execute(() -> {
                    if (entry.facesPainting == version) {
                        entry.facesPainting = 0;
                    }
                    if (!entry.wants(version) || !entry.facesWanted) {
                        close(hd, flat);
                        return;
                    }
                    entry.closeFaces();
                    entry.shownFace = SkinPainter.index(FaceState.NEUTRAL);
                    entry.hdFaces = hd;
                    entry.flatFaces = flat;
                    showFace(entry);
                    CaraCrea.LOGGER.debug("Painted the face states of a character in {} us", micros);
                });
            } catch (RuntimeException e) {
                CaraCrea.LOGGER.error("Could not paint the faces of {}", appearance, e);
            }
        });
    }

    private static NativeImage toImage(int[] pixels, int width, int height) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, Colors.toAbgr(pixels[y * width + x]));
            }
        }
        return image;
    }

    private static void close(NativeImage[] hd, NativeImage[][] flat) {
        for (NativeImage image : hd) {
            image.close();
        }
        for (NativeImage[] regions : flat) {
            for (NativeImage image : regions) {
                image.close();
            }
        }
    }

    /** Uploads the face matching the entry's state, if it is not shown yet (a few kilobytes, no painting). */
    private static void showFace(Entry entry) {
        int index = SkinPainter.index(entry.state);
        if (!entry.painted || entry.hdFaces == null || index == entry.shownFace) {
            return;
        }
        Rect hd = SkinPainter.hdFaceRect();
        entry.detail.uploadRegion(entry.hdFaces[index], hd.x(), hd.y());
        Rect[] regions = SkinPainter.faceRegions();
        entry.skin.bind();
        for (int r = 0; r < regions.length; r++) {
            NativeImage image = entry.flatFaces[index][r];
            image.upload(0, regions[r].x(), regions[r].y(), 0, 0, image.getWidth(), image.getHeight(), false, false, false, false);
        }
        entry.shownFace = index;
    }

    /** The player is close enough to be animated: get their face states ready (cheap when they are). */
    public static void wantFaces(UUID uuid) {
        Entry entry = ENTRIES.get(uuid);
        if (entry == null || entry.facesWanted) {
            return;
        }
        entry.facesWanted = true;
        if (entry.painted) {
            scheduleFaces(entry);
        }
    }

    /** The player has stayed out of animation range: show the resting face and free the states. */
    public static void dropFaces(UUID uuid) {
        Entry entry = ENTRIES.get(uuid);
        if (entry == null || !entry.facesWanted) {
            return;
        }
        entry.state = FaceState.NEUTRAL;
        showFace(entry);
        entry.facesWanted = false;
        entry.facesPainting = 0;
        entry.closeFaces();
        entry.shownFace = SkinPainter.index(FaceState.NEUTRAL);
    }

    /** Changes the blink / mouth state of a player. */
    public static void setFaceState(UUID uuid, FaceState state) {
        Entry entry = ENTRIES.get(uuid);
        if (entry == null || entry.state.equals(state)) {
            return;
        }
        entry.state = state;
        showFace(entry);
    }

    /** Whether a player's textures are painted (the creator waits for this instead of showing the grey placeholder). */
    public static boolean isPainted(UUID uuid) {
        if (uuid == null || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        return entry(uuid).painted;
    }

    /** Current face state of a player, if their skin exists. */
    public static FaceState faceState(UUID uuid) {
        Entry entry = ENTRIES.get(uuid);
        return entry == null ? FaceState.NEUTRAL : entry.state;
    }

    private static ResourceLocation placeholderSkin() {
        if (placeholderSkin == null) {
            NativeImage image = new NativeImage(Canvas.SKIN_SIZE, Canvas.SKIN_SIZE, true);
            int grey = Colors.toAbgr(Colors.rgb(96, 92, 88));
            for (SkinLayout.Part part : SkinLayout.Part.VALUES) {
                if (!part.outer) {
                    Rect r = SkinLayout.bounds(part, BodyType.MALE);
                    for (int y = r.y(); y < r.y1(); y++) {
                        for (int x = r.x(); x < r.x1(); x++) {
                            image.setPixelRGBA(x, y, grey);
                        }
                    }
                }
            }
            placeholderSkin = Minecraft.getInstance().getTextureManager().register("caracrea/placeholder", new DynamicTexture(image));
        }
        return placeholderSkin;
    }

    private static ResourceLocation placeholderDetail() {
        if (placeholderDetail == null) {
            // Plain grey everywhere: a neutral silhouette for the frame or two before painting.
            NativeImage image = new NativeImage(16, 16, false);
            image.fillRect(0, 0, 16, 16, Colors.toAbgr(Colors.rgb(96, 92, 88)));
            placeholderDetail = Minecraft.getInstance().getTextureManager().register("caracrea/placeholder_detail", new DynamicTexture(image));
        }
        return placeholderDetail;
    }

    /** The appearance of a player changed: repaint in the background (the old look stays until then). */
    public static void invalidate(UUID uuid) {
        Entry entry = ENTRIES.get(uuid);
        if (entry == null) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            entry.appearance = ClientAppearances.resolve(uuid);
            schedule(entry);
        } else {
            Minecraft.getInstance().execute(() -> {
                if (ENTRIES.get(uuid) == entry) {
                    entry.appearance = ClientAppearances.resolve(uuid);
                    schedule(entry);
                }
            });
        }
    }

    public static void release(UUID uuid) {
        Entry entry = ENTRIES.remove(uuid);
        if (entry != null) {
            entry.wanted.incrementAndGet();
            entry.closeFaces();
            Minecraft.getInstance().getTextureManager().release(entry.skinLocation);
            Minecraft.getInstance().getTextureManager().release(entry.detailLocation);
        }
    }

    /** Frees the textures of players who left; called every few seconds. */
    public static void retainOnly(Set<UUID> present) {
        List<UUID> gone = new ArrayList<>();
        for (UUID uuid : ENTRIES.keySet()) {
            if (!present.contains(uuid) && !ClientAppearances.isPreview(uuid)) {
                gone.add(uuid);
            }
        }
        gone.forEach(CompositeSkins::release);
    }

    public static void clear() {
        for (UUID uuid : new ArrayList<>(ENTRIES.keySet())) {
            release(uuid);
        }
    }

    public static int count() {
        return ENTRIES.size();
    }
}
