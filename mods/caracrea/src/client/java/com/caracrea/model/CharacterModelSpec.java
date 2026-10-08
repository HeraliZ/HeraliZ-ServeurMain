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
 * The detailed body: a torso that narrows at the waist, arms and legs that
 * taper, with hands and feet, hair, shoes and clothing accents as real 3D
 * volumes. The head stays the plain vanilla cube with the face painted on
 * it (in high definition): only hair is added around it.
 *
 * <p>The list holds every cube of every preset; {@link BoxSpec#isVisible}
 * picks the ones a given character shows. Coordinates are vanilla model
 * units in bone space: +Y down, -Z forward, +X the player's left.
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
        torso(list, type);
        arms(list, type);
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
     * Lengths in model units, measured down from the top of the head: front
     * (the fringe), sides, back, the part falling on the back of the body,
     * and the strands falling on the chest.
     */
    private record HairShape(String id, float front, float side, float back, float longBack, float strands) {
    }

    private static final List<HairShape> HAIR = List.of(
            new HairShape("crew", 0.9F, 2.0F, 2.4F, 0, 0),
            new HairShape("short_messy", 1.8F, 2.6F, 2.8F, 0, 0),
            new HairShape("side_part", 2.1F, 2.6F, 2.8F, 0, 0),
            new HairShape("quiff", 1.0F, 2.4F, 2.6F, 0, 0),
            new HairShape("slicked_back", 0.6F, 2.4F, 3.0F, 0, 0),
            new HairShape("undercut", 2.4F, 0, 1.8F, 0, 0),
            new HairShape("fade", 1.0F, 0, 0, 0, 0),
            new HairShape("curly_short", 2.0F, 3.0F, 3.2F, 0, 0),
            new HairShape("dreads", 1.4F, 6.0F, 8.0F, 0, 0),
            new HairShape("shoulder_length", 1.8F, 7.5F, 8.0F, 0, 0),
            new HairShape("bob", 2.6F, 6.2F, 6.2F, 0, 0),
            new HairShape("pixie", 2.2F, 3.2F, 3.0F, 0, 0),
            new HairShape("long_straight", 2.1F, 8.0F, 8.0F, 4.5F, 0),
            new HairShape("long_wavy", 1.8F, 8.0F, 8.0F, 4.0F, 0),
            new HairShape("ponytail", 1.2F, 5.0F, 6.0F, 0, 0),
            new HairShape("bun", 0.9F, 5.0F, 6.0F, 0, 0),
            new HairShape("braid", 1.6F, 5.0F, 6.0F, 0, 0),
            new HairShape("lob", 2.4F, 8.0F, 8.0F, 0, 0));

    /**
     * Hair stays inside the hat layer of a vanilla skin (0.5 around the head)
     * and, on the body, inside the jacket layer (0.25): armour never has
     * hair poking through, and it hides under helmets anyway.
     */
    private static final float SLAB = 0.45F;
    private static final float CAP = 4.45F;
    private static final float TOP = -8.5F;

    private static void hair(List<BoxSpec> list) {
        // The cap covers the top of the head and the tops of the slabs, so they read as one mass of hair.
        Predicate<Appearance> plainCap = hair("crew", "short_messy", "side_part", "quiff", "slicked_back", "pixie", "bob",
                "shoulder_length", "long_straight", "long_wavy", "ponytail", "bun", "braid", "undercut", "dreads", "fade", "lob");
        box(list, "hair_cap", Bone.HEAD, Slot.HAIR_TOP, Material.HAIR, Group.HAIR_HEAD,
                -CAP, TOP, -CAP, 2 * CAP, 1.2F, 2 * CAP, plainCap);
        box(list, "hair_cap_curly", Bone.HEAD, Slot.HAIR_TOP, Material.HAIR, Group.HAIR_HEAD,
                -CAP, TOP, -CAP, 2 * CAP, 1.3F, 2 * CAP, hair("curly_short"));
        box(list, "hair_cap_afro", Bone.HEAD, Slot.HAIR_TOP, Material.HAIR, Group.HAIR_HEAD,
                -4.5F, TOP, -4.5F, 9.0F, 1.7F, 9.0F, hair("afro"));
        for (HairShape shape : HAIR) {
            Predicate<Appearance> is = hair(shape.id());
            float x0 = -4.4F;
            float w = 8.8F;
            // The slabs start where the cap ends: no two faces share a plane (no flicker), and
            // the streaks run on from the cap into the fringe without a seam. Lengths are still
            // given from the top of the head (y = -8).
            float top = TOP + (shape.id().equals("curly_short") ? 1.3F : 1.2F);
            float hidden = top + 8.0F;
            if (shape.front() > hidden + 0.05F) {
                box(list, "hair_front_" + shape.id(), Bone.HEAD, Slot.HAIR_FRONT, Material.HAIR, Group.HAIR_HEAD,
                        x0, top, -4.0F - SLAB, w, shape.front() - hidden, SLAB, is);
            }
            if (shape.side() > hidden + 0.05F) {
                box(list, "hair_right_" + shape.id(), Bone.HEAD, Slot.HAIR_RIGHT, Material.HAIR, Group.HAIR_HEAD,
                        -4.0F - SLAB, top, -4.4F, SLAB, shape.side() - hidden, 8.8F, is);
                box(list, "hair_left_" + shape.id(), Bone.HEAD, Slot.HAIR_LEFT, Material.HAIR, Group.HAIR_HEAD,
                        4.0F, top, -4.4F, SLAB, shape.side() - hidden, 8.8F, is);
            }
            if (shape.back() > hidden + 0.05F) {
                // Thinner at the back under a bun or a ponytail, so they stand out from it.
                boolean tied = shape.id().equals("ponytail") || shape.id().equals("bun") || shape.id().equals("braid");
                box(list, "hair_back_" + shape.id(), Bone.HEAD, Slot.HAIR_BACK, Material.HAIR, Group.HAIR_HEAD,
                        -4.4F, top, 4.0F, w, shape.back() - hidden, tied ? 0.25F : SLAB, is);
            }
            if (shape.longBack() > 0) {
                box(list, "hair_long_" + shape.id(), Bone.BODY, Slot.HAIR_LONG, Material.HAIR, Group.HAIR_BODY,
                        -4.1F, -0.25F, 2.0F, 8.2F, shape.longBack() + 0.25F, 0.25F, is);
            }
            if (shape.strands() > 0) {
                box(list, "hair_strand_right_" + shape.id(), Bone.BODY, Slot.HAIR_STRAND_RIGHT, Material.HAIR, Group.HAIR_BODY,
                        -4.2F, -0.25F, -2.25F, 1.3F, shape.strands() + 0.25F, 0.25F, is);
                box(list, "hair_strand_left_" + shape.id(), Bone.BODY, Slot.HAIR_STRAND_LEFT, Material.HAIR, Group.HAIR_BODY,
                        2.9F, -0.25F, -2.25F, 1.3F, shape.strands() + 0.25F, 0.25F, is);
            }
        }
        // Afro: thicker all around, still within the hat layer.
        float afroTop = TOP + 1.7F;
        box(list, "hair_afro_front", Bone.HEAD, Slot.HAIR_FRONT, Material.HAIR, Group.HAIR_HEAD,
                -4.0F, afroTop, -4.5F, 8.0F, 1.0F, 0.5F, hair("afro"));
        box(list, "hair_afro_right", Bone.HEAD, Slot.HAIR_RIGHT, Material.HAIR, Group.HAIR_HEAD,
                -4.5F, afroTop, -4.5F, 0.5F, 4.8F, 9.0F, hair("afro"));
        box(list, "hair_afro_left", Bone.HEAD, Slot.HAIR_LEFT, Material.HAIR, Group.HAIR_HEAD,
                4.0F, afroTop, -4.5F, 0.5F, 4.8F, 9.0F, hair("afro"));
        box(list, "hair_afro_back", Bone.HEAD, Slot.HAIR_BACK, Material.HAIR, Group.HAIR_HEAD,
                -4.0F, afroTop, 4.0F, 8.0F, 5.8F, 0.5F, hair("afro"));
        // Ponytail: the tie, the tail down the back of the head, then down the back.
        box(list, "hair_tie", Bone.HEAD, Slot.HAIR_EXTRA, Material.HAIR, Group.HAIR_HEAD,
                -1.2F, -5.4F, 4.0F, 2.4F, 1.8F, 0.5F, hair("ponytail"));
        box(list, "hair_tail", Bone.HEAD, Slot.HAIR_TAIL, Material.HAIR, Group.HAIR_HEAD,
                -1.0F, -3.6F, 4.0F, 2.0F, 4.1F, 0.5F, hair("ponytail"));
        box(list, "hair_tail_body", Bone.BODY, Slot.HAIR_LONG, Material.HAIR, Group.HAIR_BODY,
                -1.0F, -0.25F, 2.0F, 2.0F, 3.25F, 0.25F, hair("ponytail"));
        // Bun.
        box(list, "hair_bun", Bone.HEAD, Slot.HAIR_EXTRA, Material.HAIR, Group.HAIR_HEAD,
                -1.8F, -7.4F, 4.0F, 3.6F, 2.8F, 0.5F, hair("bun"));
        // Braid down the back.
        box(list, "hair_braid", Bone.BODY, Slot.HAIR_TAIL, Material.HAIR, Group.HAIR_BODY,
                -0.9F, -0.25F, 2.0F, 1.8F, 7.25F, 0.25F, hair("braid"));
    }

    // ----------------------------------------------------------------- torso

    private static void torso(List<BoxSpec> list, BodyType type) {
        // Everything stays inside the jacket layer of a vanilla skin: x within 4.25, z within 2.25.
        if (type == BodyType.MALE) {
            box(list, "chest", Bone.BODY, Slot.CHEST, Material.SHIRT, Group.BODY, -4.0F, 0, -2.2F, 8.0F, 5.0F, 4.4F, ALWAYS);
            box(list, "waist", Bone.BODY, Slot.WAIST, Material.SHIRT, Group.BODY, -3.6F, 5.0F, -2.0F, 7.2F, 4.0F, 4.0F, ALWAYS);
            box(list, "hips", Bone.BODY, Slot.HIPS, Material.SHIRT, Group.BODY, -4.0F, 9.0F, -2.2F, 8.0F, 3.0F, 4.4F, ALWAYS);
        } else {
            box(list, "chest", Bone.BODY, Slot.CHEST, Material.SHIRT, Group.BODY, -3.8F, 0, -1.9F, 7.6F, 5.2F, 3.8F, ALWAYS);
            // The bust: a wedge flush with the chest at the top, leaning out to the front of the jacket layer.
            rotated(list, "bust", Bone.BODY, Slot.BUST, Material.SHIRT, Group.CLOTH_TORSO,
                    -2.8F, 1.4F, -1.9F, 5.6F, 3.0F, 0.85F,
                    a -> !Catalog.optionId(a, Trait.SHIRT_STYLE).equals("jacket"), 0, 1.4F, -1.9F, -0.11F, 0, 0);
            box(list, "waist", Bone.BODY, Slot.WAIST, Material.SHIRT, Group.BODY, -3.2F, 5.2F, -1.75F, 6.4F, 4.0F, 3.5F, ALWAYS);
            box(list, "hips", Bone.BODY, Slot.HIPS, Material.SHIRT, Group.BODY, -4.0F, 9.2F, -2.2F, 8.0F, 2.8F, 4.4F, ALWAYS);
        }
        // Clothing accents.
        Predicate<Appearance> collar = option(Trait.SHIRT_STYLE, "polo", "dress_shirt", "jacket");
        box(list, "collar_right", Bone.BODY, Slot.COLLAR_RIGHT, Material.SHIRT_TRIM, Group.CLOTH_TORSO,
                -3.4F, -0.25F, -2.25F, 3.0F, 1.2F, 0.25F, collar);
        box(list, "collar_left", Bone.BODY, Slot.COLLAR_LEFT, Material.SHIRT_TRIM, Group.CLOTH_TORSO,
                0.4F, -0.25F, -2.25F, 3.0F, 1.2F, 0.25F, collar);
        box(list, "hood", Bone.BODY, Slot.HOOD, Material.SHIRT_TRIM, Group.CLOTH_TORSO, -4.0F, -0.25F, 1.85F, 8.0F, 3.85F, 0.4F,
                option(Trait.SHIRT_STYLE, "hoodie"));
        box(list, "pocket", Bone.BODY, Slot.POCKET, Material.SHIRT_TRIM, Group.CLOTH_TORSO, -3.0F, 7.4F, -2.25F, 6.0F, 3.4F, 0.25F,
                option(Trait.SHIRT_STYLE, "hoodie"));
        box(list, "belt", Bone.BODY, Slot.BELT, Material.BELT, Group.CLOTH_LEGS, -4.25F, 10.6F, -2.25F, 8.5F, 1.3F, 4.5F,
                option(Trait.PANTS_STYLE, "jeans", "cargo", "trousers", "ripped_jeans", "shorts"));
    }

    // ------------------------------------------------------------------ arms

    private static void arms(List<BoxSpec> list, BodyType type) {
        boolean slim = type.slim();
        for (Bone bone : new Bone[]{Bone.RIGHT_ARM, Bone.LEFT_ARM}) {
            boolean left = bone.isLeft();
            String side = left ? "left" : "right";
            // Vanilla arm cube: x from -3 to 1 (right) or -1 to 3 (left); slim: -2..1 / -1..2.
            float x0 = left ? -1.0F : (slim ? -2.0F : -3.0F);
            float width = slim ? 3.0F : 4.0F;
            float cx = x0 + width / 2;
            float sw = width + 0.4F;
            box(list, "shoulder_" + side, bone, left ? Slot.SHOULDER_LEFT : Slot.SHOULDER_RIGHT, Material.SHIRT, Group.BODY,
                    cx - sw / 2, -2.2F, -2.2F, sw, 1.2F, 4.4F, ALWAYS);
            box(list, "arm_upper_" + side, bone, left ? Slot.ARM_UPPER_LEFT : Slot.ARM_UPPER_RIGHT, Material.SHIRT, Group.BODY,
                    x0, -2.0F, -2.0F, width, 6.0F, 4.0F, ALWAYS);
            float lw = slim ? 2.7F : 3.5F;
            box(list, "arm_lower_" + side, bone, left ? Slot.ARM_LOWER_LEFT : Slot.ARM_LOWER_RIGHT, Material.SHIRT, Group.BODY,
                    cx - lw / 2, 4.0F, -1.75F, lw, 4.0F, 3.5F, ALWAYS);
            // The hand is thinner than the wrist from the front and broad from the side, like a real hand.
            float hw = slim ? 2.5F : 3.2F;
            box(list, "hand_" + side, bone, left ? Slot.HAND_LEFT : Slot.HAND_RIGHT, Material.SKIN, Group.BODY,
                    cx - hw / 2, 7.8F, -1.5F, hw, 2.4F, 3.0F, ALWAYS);
        }
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
            box(list, "boot_rain_" + side, bone, boot, Material.SHOE, Group.SHOES, -2.0F, 4.1F, -2.0F, 4.0F, 6.6F, 4.0F,
                    option(Trait.SHOES_STYLE, "rain_boots"));
            // Skirt: one half per leg, meeting in the middle, like a skirt painted on vanilla pants.
            box(list, "skirt_" + side, bone, left ? Slot.SKIRT_LEFT : Slot.SKIRT_RIGHT, Material.SKIRT, Group.CLOTH_LEGS,
                    left ? -1.9F : -2.25F, -0.25F, -2.25F, 4.15F, 5.75F, 4.5F, option(Trait.PANTS_STYLE, "skirt"));
        }
    }
}
