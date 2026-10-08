package com.caracrea.model;

import com.caracrea.character.Appearance;

import java.util.function.Predicate;

/**
 * One cube of the detailed player model, in the local space of its bone
 * (vanilla units, +Y down, -Z forward). {@code visible} decides, for a
 * given character, whether the cube is drawn; cubes sharing a {@link Slot}
 * are never visible together.
 *
 * @param name     unique part name
 * @param rotX     rotation of the cube around its own pivot, in radians (pivot = bone origin unless given)
 */
public record BoxSpec(String name, Bone bone, Slot slot, Material material, Group group,
                      float x, float y, float z, float w, float h, float d,
                      float pivotX, float pivotY, float pivotZ, float rotX, float rotY, float rotZ,
                      boolean mirror, Predicate<Appearance> visible) {

    /** Families of cubes, for the per-entity visibility rules (helmets hide hair, etc.). */
    public enum Group {
        BODY, HAIR_HEAD, HAIR_BODY, CLOTH_TORSO, CLOTH_LEGS, SHOES
    }

    public static BoxSpec of(String name, Bone bone, Slot slot, Material material, Group group,
                             float x, float y, float z, float w, float h, float d, Predicate<Appearance> visible) {
        return new BoxSpec(name, bone, slot, material, group, x, y, z, w, h, d, 0, 0, 0, 0, 0, 0, false, visible);
    }

    public BoxSpec rotated(float pivotX, float pivotY, float pivotZ, float rotX, float rotY, float rotZ) {
        return new BoxSpec(name, bone, slot, material, group, x, y, z, w, h, d, pivotX, pivotY, pivotZ, rotX, rotY, rotZ, mirror, visible);
    }

    public BoxSpec mirrored() {
        return new BoxSpec(name, bone, slot, material, group, x, y, z, w, h, d, pivotX, pivotY, pivotZ, rotX, rotY, rotZ, true, visible);
    }

    public boolean isVisible(Appearance appearance) {
        return visible.test(appearance);
    }

    /** Texel column of the unfolded cube in the atlas (the slot's origin). */
    public int u() {
        return slot.u();
    }

    public int v() {
        return slot.v();
    }

    /** Minecraft's texture scale for this cube (0.5 for the high-definition head). */
    public float texScale() {
        return slot.texScale;
    }

    /*
     * Size of the cube's unfolded texture, in texels: the real size rounded to whole atlas
     * pixels (4 per texel). A face edge falling inside a pixel would share that pixel with the
     * next face, which shows as a thin line of the wrong colour along the edge. The cube is
     * still drawn at its real size (Minecraft's cube deformation); its texture is only
     * stretched by a few percent.
     */

    public float texW() {
        return texel(w, slot.maxW);
    }

    public float texH() {
        return texel(h, slot.maxH);
    }

    public float texD() {
        return texel(d, slot.maxD);
    }

    private float texel(float size, float max) {
        if (slot.texScale != 1) {
            return size;
        }
        float rounded = Math.round(size * 4) / 4F;
        float limit = (float) Math.floor(max * 4 + 1e-3F) / 4F;
        return Math.max(0.25F, Math.min(rounded, limit));
    }
}
