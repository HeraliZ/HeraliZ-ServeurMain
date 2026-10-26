package fr.heraliz.crac.build.geom;

import fr.heraliz.crac.build.model.Dirs;
import fr.heraliz.crac.build.model.PieceKey;
import net.minecraft.core.BlockPos;

/**
 * Local frame of a piece: u along direction {@code orient}, v along {@code orient + 3}, y up from the piece's base.
 * Maps local {@code (u, y, v)} to building px ({@code A + u e(orient) + v e(orient + 3)}, {@code y0 + y}) and on to
 * world px. All piece geometry is generated in the local frame and transformed once.
 *
 * @param origin building origin
 * @param ax     anchor x in building px
 * @param az     anchor z in building px
 * @param y0     base height in building px ({@code 24 h})
 */
public record Placement(BlockPos origin, double ax, double az, double y0, int orient) {
    public static Placement of(PieceKey key) {
        return new Placement(key.origin(), key.anchor().toX(), key.anchor().toZ(), key.baseY(), key.orient());
    }

    public double cos() {
        return Dirs.ex(this.orient);
    }

    public double sin() {
        return Dirs.ez(this.orient);
    }

    /** Building x of local (u, v). */
    public double x(double u, double v) {
        return this.ax + u * this.cos() - v * this.sin();
    }

    /** Building z of local (u, v). */
    public double z(double u, double v) {
        return this.az + u * this.sin() + v * this.cos();
    }

    /** Building y of local y. */
    public double y(double y) {
        return this.y0 + y;
    }

    /** Local u of building (x, z). */
    public double u(double x, double z) {
        return (x - this.ax) * this.cos() + (z - this.az) * this.sin();
    }

    /** Local v of building (x, z). */
    public double v(double x, double z) {
        return -(x - this.ax) * this.sin() + (z - this.az) * this.cos();
    }

    /** Local polygon {u, v}[] to building {x, z}[] (orientation is preserved: rotations only). */
    public double[][] apply(double[][] poly) {
        double[][] out = new double[poly.length][];
        for (int i = 0; i < poly.length; i++) {
            out[i] = new double[]{this.x(poly[i][0], poly[i][1]), this.z(poly[i][0], poly[i][1])};
        }
        return out;
    }

    /** Building polygon {x, z}[] to local {u, v}[]. */
    public double[][] invert(double[][] poly) {
        double[][] out = new double[poly.length][];
        for (int i = 0; i < poly.length; i++) {
            out[i] = new double[]{this.u(poly[i][0], poly[i][1]), this.v(poly[i][0], poly[i][1])};
        }
        return out;
    }

    /** World px x of building x. */
    public double worldX(double x) {
        return 16.0 * this.origin.getX() + x;
    }

    public double worldY(double y) {
        return 16.0 * this.origin.getY() + y;
    }

    public double worldZ(double z) {
        return 16.0 * this.origin.getZ() + z;
    }
}
