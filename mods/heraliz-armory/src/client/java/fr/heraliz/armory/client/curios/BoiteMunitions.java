package fr.heraliz.armory.client.curios;

import fr.heraliz.armory.HeralizArmory;
import java.lang.reflect.Constructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.simple.SimpleChannel;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.client.ICuriosScreen;

/**
 * TaCZ Addon : Maj + clic droit sur une boite de munitions de TaCZ y range les munitions de l'inventaire. Son mixin ne
 * vise que l'inventaire de base (InventoryScreen.slotClicked) : dans l'ecran de Curios, devenu l'inventaire
 * ({@link InventaireCurios}), on envoie le meme paquet (AmmoBoxCollectPacket, numero de la case dans l'inventaire du
 * joueur : les cases 5 a 45 sont aux memes numeros dans les deux menus). Le serveur de TaCZ Addon fait tout, comme
 * avant. Par reflexion, sans dependance : sans TaCZ ou TaCZ Addon (ou s'ils changent), le clic reste normal.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class BoiteMunitions {
    private BoiteMunitions() {
    }

    private static boolean pret, absent;
    private static Class<?> boite;
    private static SimpleChannel canal;
    private static Constructor<?> paquet;

    @SubscribeEvent
    public static void clic(ScreenEvent.MouseButtonPressed.Pre e) {
        if (absent || e.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !Screen.hasShiftDown()) return;
        if (!(e.getScreen() instanceof ICuriosScreen) || !(e.getScreen() instanceof AbstractContainerScreen<?> ecran)) return;
        LocalPlayer joueur = Minecraft.getInstance().player;
        Slot slot = ecran.getSlotUnderMouse();
        if (joueur == null || slot == null || !slot.hasItem() || slot.container != joueur.getInventory()) return;
        if (slot.index >= joueur.inventoryMenu.slots.size() || !preparer() || !boite.isInstance(slot.getItem().getItem())) return;
        try {
            canal.sendToServer(paquet.newInstance(slot.index));
            e.setCanceled(true);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            absent = true;
            HeralizArmory.LOGGER.warn("[armurerie] boite de munitions de TaCZ Addon dans l'ecran de Curios : desactive ({})", ex.toString());
        }
    }

    private static boolean preparer() {
        if (pret) return true;
        if (absent) return false;
        try {
            if (!ModList.get().isLoaded("tacz") || !ModList.get().isLoaded("taczaddon")) {
                absent = true;
                return false;
            }
            boite = Class.forName("com.tacz.guns.item.AmmoBoxItem");
            canal = (SimpleChannel) Class.forName("com.mafuyu404.taczaddon.init.NetworkHandler").getField("CHANNEL").get(null);
            paquet = Class.forName("com.mafuyu404.taczaddon.network.AmmoBoxCollectPacket").getConstructor(int.class);
            pret = canal != null;
            absent = !pret;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            absent = true;
            HeralizArmory.LOGGER.info("[armurerie] boite de munitions de TaCZ Addon introuvable : clic normal ({})", ex.toString());
        }
        return pret;
    }
}
