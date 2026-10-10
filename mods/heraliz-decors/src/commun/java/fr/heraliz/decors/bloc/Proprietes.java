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

    public static final EnumProperty<Taille> TAILLE = EnumProperty.create("taille", Taille.class);
    public static final EnumProperty<Pose> POSE = EnumProperty.create("pose", Pose.class);
    public static final EnumProperty<Pose> POSE2 = EnumProperty.create("pose", Pose.class, Pose.SOL, Pose.PLAFOND);
    public static final EnumProperty<Pose> POSE_SOL_MUR = EnumProperty.create("pose", Pose.class, Pose.SOL, Pose.MUR);

    private Proprietes() {}
}
