package fr.heraliz.crac.build.client.render;

import fr.heraliz.crac.build.geom.Placement;

/**
 * One planar convex polygon of a piece mesh, in pixels: in the piece's local frame (u, y, v: y up from the piece's
 * base, see {@link Placement}) until it is placed with {@link #transform}, then in building or world pixels.
 * UVs are in tile units: [0, 1] spans the material's texture once. Immutable.
 */
public final class MeshFace {
    public final Mat mat;
    /** x, y, z per vertex. */
    public final double[] pos;
    /** u, v per vertex. */
    public final double[] uv;
    public final double nx;
    public final double ny;
    public final double nz;
    /** Constant brightness multiplier baked into the vertex colours. */
    public final float tone;
    /** Faces darken towards this height (contact shadow at the foot of walls); NaN for none. */
    public final double contactY;

    public MeshFace(Mat mat, double[] pos, double[] uv, double nx, double ny, double nz, float tone, double contactY) {
        this.mat = mat;
        this.pos = pos;
        this.uv = uv;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.tone = tone;
        this.contactY = contactY;
    }

    public int size() {
        return this.pos.length / 3;
    }

    public double x(int i) {
        return this.pos[i * 3];
    }

    public double y(int i) {
        return this.pos[i * 3 + 1];
    }

    public double z(int i) {
        return this.pos[i * 3 + 2];
    }

    public double u(int i) {
        return this.uv[i * 2];
    }

    public double v(int i) {
        return this.uv[i * 2 + 1];
    }

    public MeshFace withGeometry(double[] pos, double[] uv) {
        return new MeshFace(this.mat, pos, uv, this.nx, this.ny, this.nz, this.tone, this.contactY);
    }

    public MeshFace withMat(Mat mat) {
        return new MeshFace(mat, this.pos, this.uv, this.nx, this.ny, this.nz, this.tone, this.contactY);
    }

    /** The same polygon moved along its normal by {@code d} pixels. */
    public MeshFace offset(double d) {
        double[] p = this.pos.clone();
        for (int i = 0; i < p.length; i += 3) {
            p[i] += this.nx * d;
            p[i + 1] += this.ny * d;
            p[i + 2] += this.nz * d;
        }
        return this.withGeometry(p, this.uv);
    }

    /** The same polygon seen from the other side. */
    public MeshFace flipped() {
        int n = this.size();
        double[] p = new double[this.pos.length];
        double[] t = new double[this.uv.length];
        for (int i = 0; i < n; i++) {
            int j = n - 1 - i;
            p[i * 3] = this.pos[j * 3];
            p[i * 3 + 1] = this.pos[j * 3 + 1];
            p[i * 3 + 2] = this.pos[j * 3 + 2];
            t[i * 2] = this.uv[j * 2];
            t[i * 2 + 1] = this.uv[j * 2 + 1];
        }
        return new MeshFace(this.mat, p, t, -this.nx, -this.ny, -this.nz, this.tone, this.contactY);
    }

    /**
     * This local-frame face (u, y, v) placed by {@code p} in building px: positions and normal rotated about y by the
     * piece's orientation (a rotation: winding and texture stay as they are) and translated; the contact height moves
     * with the base.
     */
    public MeshFace transform(Placement p) {
        double c = p.cos();
        double s = p.sin();
        double[] q = new double[this.pos.length];
        for (int i = 0; i < q.length; i += 3) {
            double u = this.pos[i];
            double v = this.pos[i + 2];
            q[i] = p.x(u, v);
            q[i + 1] = p.y(this.pos[i + 1]);
            q[i + 2] = p.z(u, v);
        }
        return new MeshFace(this.mat, q, this.uv, this.nx * c - this.nz * s, this.ny, this.nx * s + this.nz * c, this.tone,
            Double.isNaN(this.contactY) ? Double.NaN : p.y(this.contactY));
    }

    /** The same face moved by (dx, dy, dz) px. */
    public MeshFace translate(double dx, double dy, double dz) {
        double[] q = this.pos.clone();
        for (int i = 0; i < q.length; i += 3) {
            q[i] += dx;
            q[i + 1] += dy;
            q[i + 2] += dz;
        }
        return new MeshFace(this.mat, q, this.uv, this.nx, this.ny, this.nz, this.tone, Double.isNaN(this.contactY) ? Double.NaN : this.contactY + dy);
    }

    /** Area in square pixels. */
    public double area() {
        double ax = 0;
        double ay = 0;
        double az = 0;
        int n = this.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x0 = this.x(i);
            double y0 = this.y(i);
            double z0 = this.z(i);
            double x1 = this.x(j);
            double y1 = this.y(j);
            double z1 = this.z(j);
            ax += (y0 - y1) * (z0 + z1);
            ay += (z0 - z1) * (x0 + x1);
            az += (x0 - x1) * (y0 + y1);
        }
        return 0.5 * Math.sqrt(ax * ax + ay * ay + az * az);
    }
}
