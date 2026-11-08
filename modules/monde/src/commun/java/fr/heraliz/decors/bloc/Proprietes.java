package fr.heraliz.decors.bloc;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Propriétés partagées des décors. */
public final class Proprietes {
    /** Taille d'un cristal, d'un rocher, d'une stalactite, d'une lampe. */
    public enum Taille implements StringRepresentable {
        PETIT("petit"), MOYEN("moyen"), GRAND("grand");
        private final String n;
        Taille(String n) { this.n = n; }
        @Override public String getSerializedName() { return n; }
    }

    /** Pose d'un décor : au sol, contre un mur, au plafond. */
    public enum Pose implements StringRepresentable {
        SOL("sol"), MUR("mur"), PLAFOND("plafond");
        private final String n;
        Pose(String n) { this.n = n; }
        @Override public String getSerializedName() { return n; }
    }

    /** Quart d'un décor posé sur 2 × 2 blocs (nord-ouest, nord-est, sud-ouest, sud-est) et son décalage depuis le nord-ouest. */
    public enum Quart implements StringRepresentable {
        NO("no", 0, 0), NE("ne", 1, 0), SO("so", 0, 1), SE("se", 1, 1);
        private final String n;
        public final int dx;
        public final int dz;
        Quart(String n, int dx, int dz) { this.n = n; this.dx = dx; this.dz = dz; }
        @Override public String getSerializedName() { return n; }
    }

    public static final EnumProperty<Quart> QUART = EnumProperty.create("partie", Quart.class);
    public static final EnumProperty<Taille> TAILLE = EnumProperty.create("taille", Taille.class);
    public static final EnumProperty<Pose> POSE = EnumProperty.create("pose", Pose.class);
    public static final EnumProperty<Pose> POSE2 = EnumProperty.create("pose", Pose.class, Pose.SOL, Pose.PLAFOND);
    public static final EnumProperty<Pose> POSE_SOL_MUR = EnumProperty.create("pose", Pose.class, Pose.SOL, Pose.MUR);

    private Proprietes() {}
}
