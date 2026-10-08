package fr.heraliz.armory.client;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.item.GearArmorItem;
import fr.heraliz.armory.item.GearCurioItem;
import fr.heraliz.armory.item.SetArmorItem;
import fr.heraliz.armory.network.ArmoryNetwork;
import fr.heraliz.armory.pont.Partie;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Partie CLIENT de l'Armurerie (docs/METHODE_CLIENT_SERVEUR.md §4), chargee par HeralizArmory sur un client physique si
 * elle est dans le jar (-client, -complet). Les ecouteurs client a annotation (ClientSetup, NvgClient, DivingClient...)
 * s'inscrivent seuls ; ici : crochets du code commun et masquage de l'ancien onglet ArkaniaZ.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        busMod.addListener(PartieClient::hideOldArmorTab);
        // reserve de la bouteille de plongee recue du serveur (HUD)
        ArmoryNetwork.RECEPTION_CLIENT.brancher(m -> DivingClient.sync(m.tank(), m.max()));
        // rendus des objets (Forge appelle initializeClient a la creation des objets, apres les constructeurs @Mod)
        SetArmorItem.RENDU_CLIENT = (item, consumer) -> consumer.accept(ClientArmor.extensions(item));
        GearArmorItem.RENDU_CLIENT = (item, consumer) -> consumer.accept(new IClientItemExtensions() {
            private GearRenderer<GearArmorItem> renderer;

            @Override
            public @NotNull HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (renderer == null) renderer = new GearRenderer<>(item.gear());
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        });
        // niveau de detail des ghillies (animations coupees de loin)
        GearCurioItem.DISTANCE_CAMERA = Lod::distSqr;
    }

    /** Masque l'ancien onglet ArkaniaZ (repris de HeralisZ Armor) : on le vide, le jeu cache les onglets vides. */
    private static void hideOldArmorTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(HeralizArmory.OLD_ARMOR_TAB)) return;
        List<ItemStack> old = new ArrayList<>();
        event.getEntries().forEach(e -> old.add(e.getKey()));
        old.forEach(s -> event.getEntries().remove(s));
    }
}
