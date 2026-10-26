package com.caracrea.client;

import com.caracrea.CaraCrea;
import com.caracrea.CaraCreaConfig;
import com.caracrea.character.Appearance;
import com.caracrea.character.BodyType;
import com.caracrea.model.Bone;
import com.caracrea.model.BoxSpec;
import com.caracrea.model.CharacterModelSpec;
import com.caracrea.model.Slot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 3D body: the six vanilla bones (same pivots, so it follows every
 * vanilla or modded animation copied from the player model) carrying the
 * cubes of {@link CharacterModelSpec}, the head included: it is drawn from
 * the atlas with its high-definition face.
 */
public class DetailModel extends HumanoidModel<AbstractClientPlayer> {
    public static final ModelLayerLocation DEFAULT = new ModelLayerLocation(new ResourceLocation(CaraCrea.MOD_ID, "body"), "main");
    public static final ModelLayerLocation SLIM = new ModelLayerLocation(new ResourceLocation(CaraCrea.MOD_ID, "body_slim"), "main");

    private record Detail(ModelPart part, BoxSpec spec) {
    }

    private final List<Detail> details = new ArrayList<>();
    /** Which cubes each appearance shows, computed once per look instead of every frame. */
    private final Map<Appearance, boolean[]> shown = new HashMap<>();

    public DetailModel(ModelPart root, BodyType type) {
        super(root);
        for (BoxSpec box : CharacterModelSpec.boxes(type)) {
            details.add(new Detail(bone(box.bone()).getChild(box.name()), box));
        }
    }

    private ModelPart bone(Bone bone) {
        return switch (bone) {
            case HEAD -> head;
            case BODY -> body;
            case RIGHT_ARM -> rightArm;
            case LEFT_ARM -> leftArm;
            case RIGHT_LEG -> rightLeg;
            case LEFT_LEG -> leftLeg;
        };
    }

    public static LayerDefinition createLayer(BodyType type) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder empty = CubeListBuilder.create();
        root.addOrReplaceChild("head", empty, PartPose.ZERO);
        root.addOrReplaceChild("hat", empty, PartPose.ZERO);
        root.addOrReplaceChild("body", empty, PartPose.ZERO);
        for (Bone bone : new Bone[]{Bone.RIGHT_ARM, Bone.LEFT_ARM, Bone.RIGHT_LEG, Bone.LEFT_LEG}) {
            root.addOrReplaceChild(bone.partName, empty, PartPose.offset(bone.pivotX(), bone.pivotY(type), bone.pivotZ()));
        }
        for (BoxSpec box : CharacterModelSpec.boxes(type)) {
            CubeListBuilder cube = CubeListBuilder.create().texOffs(box.slot().texOffsU(), box.slot().texOffsV());
            if (box.texScale() != 1) {
                // The head: twice the pixels per unit, for the high-definition face.
                cube.addBox(box.x() - box.pivotX(), box.y() - box.pivotY(), box.z() - box.pivotZ(), box.w(), box.h(), box.d(),
                        CubeDeformation.NONE, box.texScale(), box.texScale());
            } else {
                // Texture on whole pixels, geometry at the real size: the difference is a deformation.
                float growX = (box.w() - box.texW()) / 2;
                float growY = (box.h() - box.texH()) / 2;
                float growZ = (box.d() - box.texD()) / 2;
                cube.addBox(box.x() - box.pivotX() + growX, box.y() - box.pivotY() + growY, box.z() - box.pivotZ() + growZ,
                        box.texW(), box.texH(), box.texD(), new CubeDeformation(growX, growY, growZ));
            }
            root.getChild(box.bone().partName).addOrReplaceChild(box.name(), cube,
                    PartPose.offsetAndRotation(box.pivotX(), box.pivotY(), box.pivotZ(), box.rotX(), box.rotY(), box.rotZ()));
        }
        return LayerDefinition.create(mesh, Slot.TEXTURE, Slot.TEXTURE);
    }

    /** Shows the cubes of this character; hair and clothing accents hide under armour that would cover them. */
    public void showFor(Appearance appearance, AbstractClientPlayer player) {
        boolean helmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && CaraCreaConfig.CLIENT.hideHairUnderHelmet.get();
        boolean chestplate = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        boolean leggings = !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        if (shown.size() > 256) {
            shown.clear();
        }
        boolean[] visibleFor = shown.computeIfAbsent(appearance, a -> {
            boolean[] flags = new boolean[details.size()];
            for (int i = 0; i < flags.length; i++) {
                flags[i] = details.get(i).spec().isVisible(a);
            }
            return flags;
        });
        for (int i = 0; i < details.size(); i++) {
            Detail detail = details.get(i);
            BoxSpec spec = detail.spec();
            boolean visible = visibleFor[i] && switch (spec.group()) {
                case HAIR_HEAD -> !helmet;
                case CLOTH_TORSO -> !chestplate;
                case CLOTH_LEGS -> !leggings;
                default -> true;
            };
            detail.part().visible = visible;
        }
    }
}
