package fr.heraliz.loot.client.rendu.scene;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pile de transformations (repere du modele, en pixels).
 */
public final class Pose {
    private final Matrix4f[] stack = new Matrix4f[64];
    private int depth;

    public Pose() {
        for (int i = 0; i < stack.length; i++) stack[i] = new Matrix4f();
    }

    public Matrix4f m() {
        return stack[depth];
    }

    public Pose reset() {
        depth = 0;
        stack[0].identity();
        return this;
    }

    public Pose push() {
        stack[depth + 1].set(stack[depth]);
        depth++;
        return this;
    }

    public Pose pop() {
        if (depth > 0) depth--;
        return this;
    }

    public Pose translate(float x, float y, float z) {
        m().translate(x, y, z);
        return this;
    }

    public Pose rotX(float deg) {
        if (deg != 0) m().rotateX((float) Math.toRadians(deg));
        return this;
    }

    public Pose rotY(float deg) {
        if (deg != 0) m().rotateY((float) Math.toRadians(deg));
        return this;
    }

    public Pose rotZ(float deg) {
        if (deg != 0) m().rotateZ((float) Math.toRadians(deg));
        return this;
    }

    /** Rotation autour d'un axe quelconque (normalise). */
    public Pose rot(float deg, float ax, float ay, float az) {
        if (deg != 0) {
            float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            m().rotate((float) Math.toRadians(deg), ax / l, ay / l, az / l);
        }
        return this;
    }

    public Pose scale(float s) {
        m().scale(s);
        return this;
    }

    public Pose scale(float x, float y, float z) {
        m().scale(x, y, z);
        return this;
    }

    public Pose mul(Matrix4f o) {
        m().mul(o);
        return this;
    }

    /** Point local -> repere du modele. */
    public float[] apply(float x, float y, float z) {
        Vector3f v = m().transformPosition(x, y, z, new Vector3f());
        return new float[]{v.x, v.y, v.z};
    }
}
