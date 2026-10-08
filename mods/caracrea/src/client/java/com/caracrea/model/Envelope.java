package com.caracrea.model;

import com.caracrea.character.BodyType;

import java.util.ArrayList;
import java.util.List;

/**
 * The volume a vanilla skin occupies, bone by bone: the base cube plus its
 * outer layer (0.5 around the head for the hat, 0.25 around the body, arms
 * and legs). No cube of the 3D body may leave it, so armour made for
 * vanilla players never has the body poking through it.
 */
public final class Envelope {
    private static final float EPSILON = 1e-4F;

    private Envelope() {
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} in bone space. */
    public static float[] bounds(Bone bone, BodyType type) {
        boolean slim = type.slim();
        return switch (bone) {
            case HEAD -> new float[]{-4.5F, -8.5F, -4.5F, 4.5F, 0.5F, 4.5F};
            case BODY -> new float[]{-4.25F, -0.25F, -2.25F, 4.25F, 12.25F, 2.25F};
            case RIGHT_ARM -> new float[]{slim ? -2.25F : -3.25F, -2.25F, -2.25F, 1.25F, 10.25F, 2.25F};
            case LEFT_ARM -> new float[]{-1.25F, -2.25F, -2.25F, slim ? 2.25F : 3.25F, 10.25F, 2.25F};
            case RIGHT_LEG, LEFT_LEG -> new float[]{-2.25F, -0.25F, -2.25F, 2.25F, 12.25F, 2.25F};
        };
    }

    /** Every cube that leaves the vanilla volume, described; empty when the model is safe. */
    public static List<String> violations(BodyType type) {
        List<String> problems = new ArrayList<>();
        for (BoxSpec box : CharacterModelSpec.boxes(type)) {
            float[] limit = bounds(box.bone(), type);
            for (int corner = 0; corner < 8; corner++) {
                float[] p = corner(box, corner);
                for (int axis = 0; axis < 3; axis++) {
                    if (p[axis] < limit[axis] - EPSILON || p[axis] > limit[axis + 3] + EPSILON) {
                        problems.add(String.format("%s %s: %s = %.3f outside [%.2f, %.2f]", type, box.name(),
                                "xyz".charAt(axis), p[axis], limit[axis], limit[axis + 3]));
                        corner = 8;
                        break;
                    }
                }
            }
        }
        return problems;
    }

    /** A corner of a cube in bone space, after its rotation about its pivot (same order as Minecraft: X, then Y, then Z). */
    private static float[] corner(BoxSpec box, int index) {
        float x = box.x() + ((index & 1) == 0 ? 0 : box.w()) - box.pivotX();
        float y = box.y() + ((index & 2) == 0 ? 0 : box.h()) - box.pivotY();
        float z = box.z() + ((index & 4) == 0 ? 0 : box.d()) - box.pivotZ();
        float c = (float) Math.cos(box.rotX());
        float s = (float) Math.sin(box.rotX());
        float ny = y * c - z * s;
        float nz = y * s + z * c;
        y = ny;
        z = nz;
        c = (float) Math.cos(box.rotY());
        s = (float) Math.sin(box.rotY());
        float nx = x * c + z * s;
        nz = -x * s + z * c;
        x = nx;
        z = nz;
        c = (float) Math.cos(box.rotZ());
        s = (float) Math.sin(box.rotZ());
        nx = x * c - y * s;
        ny = x * s + y * c;
        return new float[]{nx + box.pivotX(), ny + box.pivotY(), z + box.pivotZ()};
    }
}
