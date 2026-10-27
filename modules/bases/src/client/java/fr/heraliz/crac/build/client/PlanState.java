package fr.heraliz.crac.build.client;

import fr.heraliz.crac.build.logic.StructureView;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Building Plan selection, kept on the client: the piece kind and the R-key rotation counter, modulo the kind's
 * variants ({@link Sockets#variants}: free-standing foundation orientations, wall facing and hinge, stairs turns and
 * mirror, ramp turns and half rise). R steps forwards, Shift+R backwards.
 */
@OnlyIn(Dist.CLIENT)
public final class PlanState {
    private static PieceKind selected = PieceKind.FOUNDATION;
    private static int rotation;

    private PlanState() {
    }

    public static PieceKind selected() {
        return selected;
    }

    public static void select(PieceKind kind) {
        if (kind != selected) {
            selected = kind;
            rotation = 0;
        }
    }

    public static int rotation() {
        return rotation;
    }

    /** One step forwards (R). */
    public static void rotate() {
        rotation = step(selected, rotation, 1);
    }

    /** One step backwards (Shift+R). */
    public static void rotateBack() {
        rotation = step(selected, rotation, -1);
    }

    /** The rotation counter of {@code kind} moved by {@code delta} steps, modulo its variants. */
    public static int step(PieceKind kind, int rot, int delta) {
        return Math.floorMod(rot + delta, Math.max(1, Sockets.variants(kind)));
    }

    /**
     * What the rotation counter means for a kind, for the HUD (see {@link Sockets#orientWall} and
     * {@link Sockets#volumeRot}): the orientation of a free-standing foundation in degrees; for walls, the side the
     * door swings to and the hinge as seen by the player (other walls: their inside face, mirrored); for stairs, the
     * turn from "entry facing you" and the side the second flight turns to; for ramps, the turn from "low end facing
     * you" and full or half rise. Empty when the kind has no choice.
     */
    public static String rotationText(PieceKind kind, int rot) {
        StringBuilder sb = new StringBuilder();
        for (Frag f : rotationFrags(kind, rot)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(f.arg() == null ? f.en() : String.format(java.util.Locale.ROOT, f.en(), f.arg()));
        }
        return sb.toString();
    }

    /** {@link #rotationText} for the HUD, in the player's language (assets/crac/lang). */
    public static net.minecraft.network.chat.Component rotationLine(PieceKind kind, int rot) {
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
        boolean first = true;
        for (Frag f : rotationFrags(kind, rot)) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(f.arg() == null ? net.minecraft.network.chat.Component.translatable(f.key())
                : net.minecraft.network.chat.Component.translatable(f.key(), f.arg()));
        }
        return out;
    }

    /** One part of a rotation text: its translation key, its English (the unit tests read it) and its number, if any. */
    private record Frag(String key, String en, Object arg) {
        static Frag of(String key, String en) {
            return new Frag(key, en, null);
        }
    }

    private static java.util.List<Frag> rotationFrags(PieceKind kind, int rot) {
        java.util.List<Frag> out = new java.util.ArrayList<>(2);
        if (Sockets.variants(kind) <= 1) {
            return out;
        }
        if (kind.isFoundation()) {
            out.add(new Frag("crac.rot.orientation", "Orientation %s\u00b0", Sockets.freeOrient(kind, rot) * 30));
            return out;
        }
        boolean bit0 = (rot & 1) != 0;
        boolean bit1 = (rot & 2) != 0;
        if (kind == PieceKind.DOORWAY) {
            out.add(bit0 ? Frag.of("crac.rot.opens_toward", "Opens toward you") : Frag.of("crac.rot.opens_away", "Opens away"));
            out.add(bit1 ? Frag.of("crac.rot.hinge_left", "hinge left") : Frag.of("crac.rot.hinge_right", "hinge right"));
            return out;
        }
        if (kind == PieceKind.DOUBLE_DOORWAY || kind.isWall()) {
            boolean door = kind == PieceKind.DOUBLE_DOORWAY;
            out.add(bit0 ? (door ? Frag.of("crac.rot.opens_toward", "Opens toward you") : Frag.of("crac.rot.inside_toward", "Inside toward you"))
                : (door ? Frag.of("crac.rot.opens_away", "Opens away") : Frag.of("crac.rot.inside_away", "Inside away")));
            if (bit1) {
                out.add(Frag.of("crac.rot.mirrored", "mirrored"));
            }
            return out;
        }
        if (kind == PieceKind.TRI_RAMP) {
            int r = Math.floorMod(rot, 6);
            out.add(r >= 3 ? Frag.of("crac.rot.half_rise", "Half rise") : Frag.of("crac.rot.full_rise", "Full rise"));
            out.add(r % 3 == 0 ? Frag.of("crac.rot.high_side_facing", "high side facing you")
                : new Frag("crac.rot.turned", "turned %s°", r % 3 * 120));
            return out;
        }
        int q = rot & 3;
        boolean bit2 = (rot & 4) != 0;
        if (kind == PieceKind.STAIRS_L) {
            out.add(q == 0 ? Frag.of("crac.rot.entry_facing", "Entry facing you") : new Frag("crac.rot.turned_cap", "Turned %s\u00b0", q * 90));
            out.add(bit2 ? Frag.of("crac.rot.turns_left", "turns left") : Frag.of("crac.rot.turns_right", "turns right"));
        } else if (kind == PieceKind.RAMP) {
            out.add(bit2 ? Frag.of("crac.rot.half_rise", "Half rise") : Frag.of("crac.rot.full_rise", "Full rise"));
            out.add(q == 0 ? Frag.of("crac.rot.low_end_facing", "low end facing you") : new Frag("crac.rot.turned", "turned %s\u00b0", q * 90));
        }
        return out;
    }

    /**
     * What the ghost does with the rotation counter beyond {@link #rotationText}, for the HUD.
     *
     * @param text       empty when there is nothing to add
     * @param overridden the ghost does not do what R asks (drawn as a warning)
     */
    public record Note(String text, boolean overridden) {
        public static final Note NONE = new Note("", false);

        /** The note in the player's language (the English {@link #text} stays for the unit tests). */
        public net.minecraft.network.chat.Component display() {
            String key = switch (this.text) {
                case "new building" -> "crac.note.new_building";
                case "attached: keeps the building's angle" -> "crac.note.attached";
                case "flipped: the other swing is blocked" -> "crac.note.flipped";
                case "flipped: the other swing is blocked, hinge mirrored" -> "crac.note.flipped_hinge";
                case "hinge mirrored: the other side is blocked" -> "crac.note.hinge";
                default -> null;
            };
            return key == null ? net.minecraft.network.chat.Component.literal(this.text) : net.minecraft.network.chat.Component.translatable(key);
        }
    }

    /**
     * The note for the socket {@code key} the plan offers for {@code kind} and the counter {@code rot}, aimed from
     * {@code eye}: a foundation starts a "new building" (R turns it) or is "attached" (it keeps the building's angle,
     * a warning when R asks for another one); a door "flipped" when R's swing side is blocked, "hinge mirrored" when
     * a single door's leaf only fits on the other jamb.
     */
    public static Note rotationNote(StructureView view, PieceKind kind, int rot, PieceKey key, Vec3 eye) {
        if (kind.isFoundation()) {
            boolean free = Sockets.freeStanding(view, key);
            return new Note(free ? "new building" : "attached: keeps the building's angle",
                !free && Math.floorMod(rot, Sockets.variants(kind)) != 0);
        }
        if (kind.hasDoor() && Sockets.facingFlipped(key, rot, eye)) {
            return new Note("flipped: the other swing is blocked" + (Sockets.hingeMirrored(key, rot, eye) ? ", hinge mirrored" : ""), true);
        }
        if (kind.hasDoor() && Sockets.hingeMirrored(key, rot, eye)) {
            return new Note("hinge mirrored: the other side is blocked", true);
        }
        return Note.NONE;
    }
}
