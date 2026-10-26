package com.caracrea.model;

import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.character.Catalog;
import com.caracrea.character.Trait;
import com.caracrea.model.BoxSpec.Group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The detailed body: a man's torso (broad chest, slight taper to the waist), legs with feet, hair made of a cap, a
 * ring and slabs whose painted locks shape the cut, and clothing accents (collars, hood, pocket, belt) as real 3D
 * volumes. The head stays the plain vanilla cube with the face painted on it (in high definition): only hair is added
 * around it. The arms are the vanilla Minecraft arms, drawn with the painted skin (see CaraCreaClient): the same as
 * the arms seen in first person.
 *
 * <p>The list holds every cube of every preset; {@link BoxSpec#isVisible} picks the ones a given character shows.
 * Coordinates are vanilla model units in bone space: +Y down, -Z forward, +X the player's left. The body, arms and
 * legs stay inside the volume of a vanilla skin (see {@link Envelope}) so armour covers them; hair may go a little
 * beyond the hat layer for volume, since it is hidden under helmets.
 */
public final class CharacterModelSpec {
    private static final Map<BodyType, List<BoxSpec>> CACHE = new EnumMap<>(BodyType.class);

    private CharacterModelSpec() {
    }

    public static synchronized List<BoxSpec> boxes(BodyType type) {
        return CACHE.computeIfAbsent(type, CharacterModelSpec::build);
    }

    public static List<BoxSpec> visible(Appearance appearance) {
        List<BoxSpec> list = new ArrayList<>();
        for (BoxSpec box : boxes(appearance.bodyType())) {
            if (box.isVisible(appearance)) {
                list.add(box);
            }
        }
        return list;
    }

    // ------------------------------------------------------------ predicates

    private static final Predicate<Appearance> ALWAYS = a -> true;

    private static Predicate<Appearance> hair(String... ids) {
        Set<String> set = Set.of(ids);
        return a -> set.contains(Catalog.hair(a).id());
    }

    private static Predicate<Appearance> option(Trait trait, String... ids) {
        Set<String> set = Set.of(ids);
        return a -> set.contains(Catalog.optionId(a, trait));
    }

    // ------------------------------------------------------------- building

    private static List<BoxSpec> build(BodyType type) {
        List<BoxSpec> list = new ArrayList<>();
        // The head: the vanilla cube, drawn from the atlas with the high-definition face.
        box(list, "head", Bone.HEAD, Slot.HEAD, Material.HEAD, Group.BODY, -4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, ALWAYS);
        hair(list);
        torso(list);
        legs(list);
        return Collections.unmodifiableList(list);
    }

    private static BoxSpec box(List<BoxSpec> list, String name, Bone bone, Slot slot, Material material, Group group,
                               float x, float y, float z, float w, float h, float d, Predicate<Appearance> visible) {
        BoxSpec box = BoxSpec.of(name, bone, slot, material, group, x, y, z, w, h, d, visible);
        list.add(box);
        return box;
    }

    /** Adds a box turned about a pivot (bone space). */
    private static void rotated(List<BoxSpec> list, String name, Bone bone, Slot slot, Material material, Group group,
                                float x, float y, float z, float w, float h, float d, Predicate<Appearance> visible,
                                float pivotX, float pivotY, float pivotZ, float rotX, float rotY, float rotZ) {
        list.add(BoxSpec.of(name, bone, slot, material, group, x, y, z, w, h, d, visible)
                .rotated(pivotX, pivotY, pivotZ, rotX, rotY, rotZ));
    }

    // ------------------------------------------------------------------ hair

    /**
     * How far down each slab goes, in model units from the top of the head (y = -8): the fringe, the sides next to
     * the face (temples) and behind the ears, and the nape. The painted locks give each cut its outline within these
     * bounds (see the hair shader of DetailPainter).
     */
    public record HairShape(String id, float front, float temple, float side, float back) {
        public float sideMax() {
            return Math.max(temple, side);
        }
    }

    public static final List<HairShape> HAIR = List.of(
            new HairShape("crew", 1.0F, 2.2F, 2.6F, 3.0F),
            new HairShape("short_messy", 2.4F, 2.7F, 3.2F, 3.5F),
            new HairShape("side_part", 2.3F, 2.5F, 3.0F, 3.3F),
            new HairShape("quiff", 0.8F, 2.3F, 2.8F, 3.1F),
            new HairShape("slicked_back", 0.6F, 2.5F, 3.2F, 4.0F),
            new HairShape("undercut", 2.6F, 0, 0, 1.4F),
            new HairShape("fade", 1.1F, 0, 0, 0),
            new HairShape("curly_short", 2.2F, 2.8F, 3.4F, 3.6F),
            new HairShape("afro", 0.9F, 3.6F, 4.6F, 5.4F),
            new HairShape("dreads", 1.6F, 5.2F, 7.2F, 8.0F),
            new HairShape("shoulder_length", 2.0F, 6.2F, 7.8F, 8.0F));

    public static HairShape hairShape(String id) {
        for (HairShape shape : HAIR) {
            if (shape.id().equals(id)) {
                return shape;
            }
        }
        return null;
    }

    /** Thickness of the slabs around the head: inside the hat layer of a vanilla skin (0.5). */
    private static final float SLAB = 0.45F;
    /**
     * Top of the slabs: under the ring around the top of the head, itself under the cap. The three steps (cap, ring,
     * slabs) read as the rounded top of a head of hair rather than the edge of a box.
     */
    private static final float SLAB_TOP = -8.15F;

    private static void hair(List<BoxSpec> list) {
        // The top of the head: a cap, and under it a ring overhanging the head a little. Thicker for the cuts with
        // volume on top.
        Predicate<Appearance> flat = hair("crew", "short_messy", "side_part", "quiff", "slicked_back", "fade", "dreads", "shoulder_length");
        Predicate<Appearance> full = hair("undercut", "curly_short");
        box(list, "hair_cap", Bone.HEAD, Slot.HAIR_TOP, Material.HAIR, Group.HAIR_HEAD,
                -3.75F, -8.55F, -3.75F, 7.5F, 0.55F, 7.5F, flat);
        box(list, "hair_ring", Bone.HEAD, Slot.HAIR_RING, Material.HAIR, Group.HAIR_HEAD,
                -4.25F, -8.35F, -4.25F, 8.5F, 0.35F, 8.5F, flat);
        box(list, "hair_cap_thick", Bone.HEAD, Slot.HAIR_TOP, Material.HAIR, Group.HAIR_HEAD,
                -3.8F, -8.85F, -3.8F, 7.6F, 0.85F, 7.6F, full);
        box(list, "hair_ring_thick", Bone.HEAD, Slot.HAIR_RING, Material.HAIR, Group.HAIR_HEAD,
                -4.3F, -8.5F, -4.3F, 8.6F, 0.5F, 8.6F, full);
        // Quiff: the hair at the front sweeps up and back: a lock raised at the front, then lying back on the head.
        rotated(list, "hair_quiff", Bone.HEAD, Slot.HAIR_EXTRA, Material.HAIR, Group.HAIR_HEAD,
                -3.3F, -9.15F, -4.55F, 6.6F, 0.65F, 3.4F, hair("quiff"), 0, -8.5F, -1.15F, -0.22F, 0, 0);
        box(list, "hair_quiff_back", Bone.HEAD, Slot.HAIR_EXTRA_BACK, Material.HAIR, Group.HAIR_HEAD,
                -3.0F, -9.0F, -1.5F, 6.0F, 0.5F, 3.2F, hair("quiff"));
        // Afro: a big rounded mass, well beyond the hat layer (it is hidden under helmets), that leaves the face free:
        // a dome in three steps down to the hairline, then the sides and the back behind the temples.
        Predicate<Appearance> afro = hair("afro");
        box(list, "hair_afro_crown", Bone.HEAD, Slot.AFRO_CROWN, Material.HAIR, Group.HAIR_HEAD,
                -3.6F, -10.9F, -3.4F, 7.2F, 0.5F, 8.0F, afro);
        box(list, "hair_afro_dome", Bone.HEAD, Slot.AFRO_DOME, Material.HAIR, Group.HAIR_HEAD,
                -4.6F, -10.4F, -4.3F, 9.2F, 0.5F, 9.3F, afro);
        box(list, "hair_afro_top", Bone.HEAD, Slot.AFRO_TOP, Material.HAIR, Group.HAIR_HEAD,
                -5.2F, -9.9F, -4.6F, 10.4F, 2.5F, 9.9F, afro);
        box(list, "hair_afro_right", Bone.HEAD, Slot.AFRO_SIDE_RIGHT, Material.HAIR, Group.HAIR_HEAD,
                -5.0F, -7.4F, -3.6F, 1.0F, 3.4F, 8.9F, afro);
        box(list, "hair_afro_left", Bone.HEAD, Slot.AFRO_SIDE_LEFT, Material.HAIR, Group.HAIR_HEAD,
                4.0F, -7.4F, -3.6F, 1.0F, 3.4F, 8.9F, afro);
        box(list, "hair_afro_back", Bone.HEAD, Slot.AFRO_BACK, Material.HAIR, Group.HAIR_HEAD,
                -4.0F, -7.4F, 4.0F, 8.0F, 4.4F, 1.3F, afro);
        for (HairShape shape : HAIR) {
            if (shape.id().equals("afro")) {
                continue;
            }
            Predicate<Appearance> is = hair(shape.id());
            float hidden = SLAB_TOP + 8;
            // Front and back slabs 8.8 wide, side slabs 8.8 deep: at the corners no two faces share a plane.
            if (shape.front() > hidden + 0.05F) {
                box(list, "hair_front_" + shape.id(), Bone.HEAD, Slot.HAIR_FRONT, Material.HAIR, Group.HAIR_HEAD,
                        -4.4F, SLAB_TOP, -4.0F - SLAB, 8.8F, shape.front() - hidden, SLAB, is);
            }
            if (shape.sideMax() > hidden + 0.05F) {
                box(list, "hair_right_" + shape.id(), Bone.HEAD, Slot.HAIR_RIGHT, Material.HAIR, Group.HAIR_HEAD,
                        -4.0F - SLAB, SLAB_TOP, -4.4F, SLAB, shape.sideMax() - hidden, 8.8F, is);
                box(list, "hair_left_" + shape.id(), Bone.HEAD, Slot.HAIR_LEFT, Material.HAIR, Group.HAIR_HEAD,
                        4.0F, SLAB_TOP, -4.4F, SLAB, shape.sideMax() - hidden, 8.8F, is);
            }
            if (shape.back() > hidden + 0.05F) {
                box(list, "hair_back_" + shape.id(), Bone.HEAD, Slot.HAIR_BACK, Material.HAIR, Group.HAIR_HEAD,
                        -4.4F, SLAB_TOP, 4.0F, 8.8F, shape.back() - hidden, SLAB, is);
            }
        }
    }

    // ----------------------------------------------------------------- torso

    private static void torso(List<BoxSpec> list) {
        // Everything stays inside the jacket layer of a vanilla skin: x within 4.25, z within 2.25.
        // A man's torso: a broad chest, a slight taper to the waist, the hips a little wider again under the belt.
        box(list, "chest", Bone.BODY, Slot.CHEST, Material.SHIRT, Group.BODY, -4.0F, 0, -2.2F, 8.0F, 5.6F, 4.4F, ALWAYS);
        box(list, "waist", Bone.BODY, Slot.WAIST, Material.SHIRT, Group.BODY, -3.7F, 5.6F, -2.0F, 7.4F, 3.8F, 4.0F, ALWAYS);
        box(list, "hips", Bone.BODY, Slot.HIPS, Material.SHIRT, Group.BODY, -3.8F, 9.4F, -2.05F, 7.6F, 2.6F, 4.1F, ALWAYS);
        // Clothing accents.
        Predicate<Appearance> collar = option(Trait.SHIRT_STYLE, "polo", "dress_shirt", "jacket", "flannel");
        box(list, "collar_right", Bone.BODY, Slot.COLLAR_RIGHT, Material.SHIRT_TRIM, Group.CLOTH_TORSO,
                -3.3F, -0.2F, -2.25F, 3.0F, 1.3F, 0.25F, collar);
        box(list, "collar_left", Bone.BODY, Slot.COLLAR_LEFT, Material.SHIRT_TRIM, Group.CLOTH_TORSO,
                0.3F, -0.2F, -2.25F, 3.0F, 1.3F, 0.25F, collar);
        box(list, "hood", Bone.BODY, Slot.HOOD, Material.SHIRT_TRIM, Group.CLOTH_TORSO, -3.8F, -0.25F, 1.75F, 7.6F, 3.6F, 0.5F,
                option(Trait.SHIRT_STYLE, "hoodie"));
        box(list, "pocket", Bone.BODY, Slot.POCKET, Material.SHIRT_TRIM, Group.CLOTH_TORSO, -2.8F, 7.0F, -2.2F, 5.6F, 3.0F, 0.2F,
                option(Trait.SHIRT_STYLE, "hoodie"));
        box(list, "belt", Bone.BODY, Slot.BELT, Material.BELT, Group.CLOTH_LEGS, -3.95F, 10.5F, -2.2F, 7.9F, 1.15F, 4.4F,
                option(Trait.PANTS_STYLE, "jeans", "cargo", "trousers", "ripped_jeans", "shorts"));
    }

    // ------------------------------------------------------------------ legs

    private static void legs(List<BoxSpec> list) {
        // Everything stays inside the pants layer of a vanilla skin: within 2.25 of the leg's centre.
        for (Bone bone : new Bone[]{Bone.RIGHT_LEG, Bone.LEFT_LEG}) {
            boolean left = bone.isLeft();
            String side = left ? "left" : "right";
            Slot foot = left ? Slot.FOOT_LEFT : Slot.FOOT_RIGHT;
            Slot boot = left ? Slot.BOOT_LEFT : Slot.BOOT_RIGHT;
            box(list, "leg_upper_" + side, bone, left ? Slot.LEG_UPPER_LEFT : Slot.LEG_UPPER_RIGHT, Material.PANTS, Group.BODY,
                    -2.0F, 0, -2.0F, 4.0F, 6.0F, 4.0F, ALWAYS);
            box(list, "leg_lower_" + side, bone, left ? Slot.LEG_LOWER_LEFT : Slot.LEG_LOWER_RIGHT, Material.PANTS, Group.BODY,
                    -1.8F, 6.0F, -1.8F, 3.6F, 5.2F, 3.6F, ALWAYS);
            // Feet: one shape per kind of shoe, standing on the sole.
            box(list, "foot_" + side, bone, foot, Material.SHOE, Group.SHOES, -2.0F, 10.6F, -2.25F, 4.0F, 1.0F, 4.35F,
                    option(Trait.SHOES_STYLE, "boots", "combat_boots", "hiking_boots", "rain_boots", "sandals"));
            box(list, "foot_sneaker_" + side, bone, foot, Material.SHOE, Group.SHOES, -2.15F, 10.4F, -2.25F, 4.3F, 1.2F, 4.4F,
                    option(Trait.SHOES_STYLE, "sneakers"));
            box(list, "foot_dress_" + side, bone, foot, Material.SHOE, Group.SHOES, -1.9F, 10.8F, -2.25F, 3.8F, 0.8F, 4.25F,
                    option(Trait.SHOES_STYLE, "dress_shoes"));
            box(list, "foot_bare_" + side, bone, foot, Material.SHOE, Group.SHOES, -1.9F, 10.9F, -2.2F, 3.8F, 1.1F, 4.2F,
                    option(Trait.SHOES_STYLE, "barefoot"));
            box(list, "sole_" + side, bone, left ? Slot.SOLE_LEFT : Slot.SOLE_RIGHT, Material.SOLE, Group.SHOES,
                    -2.2F, 11.6F, -2.25F, 4.4F, 0.4F, 4.45F, a -> !Catalog.optionId(a, Trait.SHOES_STYLE).equals("barefoot"));
            // Boot shafts of three heights.
            box(list, "boot_" + side, bone, boot, Material.SHOE, Group.SHOES, -2.0F, 7.6F, -2.0F, 4.0F, 3.1F, 4.0F,
                    option(Trait.SHOES_STYLE, "boots", "hiking_boots"));
            box(list, "boot_combat_" + side, bone, boot, Material.SHOE, Group.SHOES, -2.0F, 6.0F, -2.0F, 4.0F, 4.7F, 4.0F,
                    option(Trait.SHOES_STYLE, "combat_boots"));
            box(list, "boot_rain_" + side, bone, boot, Material.SHOE, Group.SHOES, -2.15F, 5.5F, -2.15F, 4.3F, 5.2F, 4.3F,
                    option(Trait.SHOES_STYLE, "rain_boots"));
        }
    }
}
