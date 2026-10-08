package com.caracrea.paint;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.FaceState;

/**
 * Paints a character. Painting is deterministic, so every client produces
 * the same textures for the same appearance, and it uses no shared state,
 * so it runs on a background thread.
 *
 * <ul>
 *   <li>The skin: the vanilla 64x64 layout at 4x, read by the tab list, the
 *   first-person hands, other mods and the classic cubic body.</li>
 *   <li>The detail atlas of the 3D body, whose top-left corner holds the
 *   head at 8x: the face is painted in high definition.</li>
 *   <li>The face in its 24 states (3 for the eyes, 8 mouth shapes),
 *   painted once, so blinking and talking only swap pixels in game.</li>
 * </ul>
 */
public final class SkinPainter {
    /** Number of face states: {@link FaceState#EYES_OPEN} + 1 eye levels times the mouth levels. */
    public static final int FACE_STATES = (FaceState.EYES_OPEN + 1) * FaceState.MOUTH_SHAPES;

    /** The head in the vanilla layout at 8x: texels 0..32 x 0..16, also its place in the atlas. */
    public static final Rect HD_HEAD = new Rect(0, 0, 32 * Canvas.HD_SCALE, 16 * Canvas.HD_SCALE);

    private SkinPainter() {
    }

    /** Both textures of a character. */
    public record Painted(Canvas skin, Canvas detail) {
    }

    /**
     * The face in every state: {@code hd[state]} is the front of the head in
     * the atlas ({@link #hdFaceRect()}), {@code flat[state][region]} the
     * {@link #faceRegions()} of the skin.
     */
    public record FaceVariants(int[][] hd, int[][][] flat) {
    }

    public static int index(FaceState state) {
        return state.eyes() * FaceState.MOUTH_SHAPES + state.mouth();
    }

    public static FaceState state(int index) {
        return new FaceState(index / FaceState.MOUTH_SHAPES, index % FaceState.MOUTH_SHAPES);
    }

    public static Painted paint(Appearance appearance, FaceState state) {
        return new Painted(paintSkin(appearance, state), paintDetail(appearance, state));
    }

    /** The skin, vanilla layout: body, clothes, face, marks, beard, then hair. */
    public static Canvas paintSkin(Appearance appearance, FaceState state) {
        Canvas canvas = new Canvas(Canvas.SKIN_SIZE);
        paintSkinLayers(canvas, appearance, state);
        return canvas;
    }

    /** The detail atlas, with the high-definition head in its corner. */
    public static Canvas paintDetail(Appearance appearance, FaceState state) {
        Canvas canvas = new Canvas(Canvas.DETAIL_SIZE);
        DetailPainter.paint(canvas, appearance);
        Canvas head = new Canvas(64 * Canvas.HD_SCALE, Canvas.HD_SCALE, HD_HEAD);
        paintSkinLayers(head, appearance, state);
        // The atlas and the 8x layout share the same pixel coordinates for the head.
        int[] from = head.pixels();
        int[] to = canvas.pixels();
        for (int y = 0; y < HD_HEAD.h(); y++) {
            System.arraycopy(from, y * HD_HEAD.w(), to, y * Canvas.DETAIL_SIZE, HD_HEAD.w());
        }
        return canvas;
    }

    /** The front of the head in the atlas. */
    public static Rect hdFaceRect() {
        return SkinLayout.faceRect(Canvas.HD_SCALE);
    }

    /**
     * The regions of the skin that depend on the face state: the front of the
     * head, and the front of the hat layer (where a full beard has volume
     * under the mouth for the cubic body).
     */
    public static Rect[] faceRegions() {
        return new Rect[]{SkinLayout.faceRect(), SkinLayout.rect(SkinLayout.Part.HAT, SkinLayout.Face.FRONT, BodyType.MALE)};
    }

    /** Paints the face in all its states. */
    public static FaceVariants paintFaceVariants(Appearance appearance) {
        Rect[] regions = faceRegions();
        int[][] hd = new int[FACE_STATES][];
        int[][][] flat = new int[FACE_STATES][regions.length][];
        for (int i = 0; i < FACE_STATES; i++) {
            FaceState state = state(i);
            Canvas face = new Canvas(64 * Canvas.HD_SCALE, Canvas.HD_SCALE, hdFaceRect());
            paintSkinLayers(face, appearance, state);
            hd[i] = face.pixels();
            for (int r = 0; r < regions.length; r++) {
                Canvas region = new Canvas(Canvas.SKIN_SIZE, Canvas.SCALE, regions[r]);
                paintSkinLayers(region, appearance, state);
                flat[i][r] = region.pixels();
            }
        }
        return new FaceVariants(hd, flat);
    }

    private static void paintSkinLayers(Canvas canvas, Appearance appearance, FaceState state) {
        BodyPainter.paint(canvas, appearance);
        ScarPainter.paint(canvas, appearance);
        ClothesPainter.paint(canvas, appearance);
        BodyPainter.shadeLimbs(canvas, appearance);
        FacePainter.paint(canvas, appearance, state);
        MarksPainter.paint(canvas, appearance);
        BeardPainter.paint(canvas, appearance, state);
        HairPainter.paint(canvas, appearance);
    }

    /** Repaints only the face regions of a 4x skin for a new face state. */
    public static void repaintFace(Canvas skin, Appearance appearance, FaceState state) {
        Rect[] regions = faceRegions();
        skin.setClip(regions);
        for (Rect region : regions) {
            skin.fill(region, 0);
        }
        paintSkinLayers(skin, appearance, state);
        skin.setClip((Rect[]) null);
    }
}
