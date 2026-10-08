package fr.heraliz.casino.client;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.client.screen.MachineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Evenements cote client (bus Forge) lies aux ecrans de jeu.
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    /** Elements du HUD caches sous le panneau d'un ecran de jeu. */
    private static final Set<ResourceLocation> HIDDEN = Set.of(
            VanillaGuiOverlay.HOTBAR.id(), VanillaGuiOverlay.CROSSHAIR.id(), VanillaGuiOverlay.PLAYER_HEALTH.id(),
            VanillaGuiOverlay.ARMOR_LEVEL.id(), VanillaGuiOverlay.FOOD_LEVEL.id(), VanillaGuiOverlay.AIR_LEVEL.id(),
            VanillaGuiOverlay.MOUNT_HEALTH.id(), VanillaGuiOverlay.JUMP_BAR.id(), VanillaGuiOverlay.EXPERIENCE_BAR.id(),
            VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.RECORD_OVERLAY.id(), VanillaGuiOverlay.CHAT_PANEL.id());
    /**
     * Pastille du solde de HeraliZ Economie (en haut a droite) : dans un ecran de machine, elle chevaucherait le solde de
     * jetons de casino ; a la caisse, la ligne des jetons et le message du guichet disent deja tout.
     */
    private static final ResourceLocation ECONOMIE_HUD = new ResourceLocation("heralisz_economie", "solde");

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();
        // scene de l'expulsion : plus rien a l'ecran que les bandes noires et les titres du casino
        if (fr.heraliz.casino.client.zone.ZoneCinema.active()) {
            if (!HeralizCasino.MODID.equals(id.getNamespace())) event.setCanceled(true);
            return;
        }
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().screen;
        if ((screen instanceof MachineScreen<?> || screen instanceof fr.heraliz.casino.client.zone.EntranceScreen) && HIDDEN.contains(id)) {
            event.setCanceled(true);
        } else if (screen instanceof MachineScreen<?> && ECONOMIE_HUD.equals(id)) {
            event.setCanceled(true);
        }
    }

    /** Les messages de la barre d'action (gains, erreurs) seraient caches par le panneau : on les affiche au-dessus. */
    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (event.isOverlay() && Minecraft.getInstance().screen instanceof MachineScreen<?> screen) {
            screen.serverMessage(event.getMessage());
        }
    }
}
