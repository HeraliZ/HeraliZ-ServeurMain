package fr.heraliz.armory.client;

import fr.heraliz.armory.item.PieceItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Branche le modele en relief sur le rendu d'armure vanilla.
 * Sur les rendus qui portent SetArmorLayer (joueurs, mannequin, porte-armure, mobs humanoides), la couche vanilla recoit un
 * modele vide et SetArmorLayer dessine la piece en translucide (visieres en verre). Ailleurs (rendu d'un autre mod), repli
 * sur l'ancien chemin : modele en relief rendu par la couche vanilla (opaque, Forge copie la pose de l'entite dessus).
 */
public final class ClientArmor {
    private ClientArmor() {}

    public static IClientItemExtensions extensions(PieceItem item) {
        return new IClientItemExtensions() {
            @Override
            public Model getGenericArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (SetArmorLayer.handles(entity)) return SetArmorLayer.NOTHING;
                Model model = IClientItemExtensions.super.getGenericArmorModel(entity, stack, slot, original);
                // combinaison (0.9.0) : portee au torse mais couvre tout ; le jeu vient de recopier la visibilite du torse
                if (item.def().isSuit() && model instanceof HumanoidModel<?> h) h.setAllVisible(true);
                return model;
            }

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                HumanoidModel<LivingEntity> model = ArmorModels.INSTANCE.modelFor(item.def(), entity);
                if (item.def().nvg()) ArmorModels.applyGroups(model, "nvg", NvgClient.progress(entity, stack));
                return model;
            }
        };
    }
}
