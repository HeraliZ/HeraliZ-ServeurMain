package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.InterfaceConfig;
import fr.heraliz.interfaces.kit.Flou;
import fr.heraliz.interfaces.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Branchements du client. Le menu Echap vanilla est remplace par {@link EcranPause} seulement quand :
 * <ul>
 *     <li>l'ecran qui s'ouvre est EXACTEMENT {@link PauseScreen} (un mod qui ouvre sa propre sous-classe garde la
 *     sienne) et qu'aucun autre mod n'a deja annule ou remplace l'ouverture (priorite basse, evenement annule ignore) ;</li>
 *     <li>c'est le vrai menu (titre « menu.game ») et non la pause sans menu de F3+Echap (titre « menu.paused ») ;</li>
 *     <li>un monde est charge, le menu est actif dans la config et notre ecran n'a pas plante dans cette session
 *     (sinon : menu vanilla jusqu'au redemarrage, voir {@link #secours}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class InterfaceClient {
    private InterfaceClient() {
    }

    private static boolean enPanne;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void ouverture(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s == null || s.getClass() != PauseScreen.class || enPanne) return;
        if (!InterfaceConfig.oui(InterfaceConfig.MENU_ACTIF, true) || !menuComplet(s)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        e.setNewScreen(new EcranPause());
    }

    /** Vrai menu de pause (avec boutons) : titre « menu.game » ; F3+Echap ouvre la pause sans menu (« menu.paused »). */
    public static boolean menuComplet(Screen s) {
        return s.getTitle().getContents() instanceof TranslatableContents tc && "menu.game".equals(tc.getKey());
    }

    /** Notre ecran a plante : journal, menu vanilla a la place et pour le reste de la session. */
    public static void secours(Throwable t) {
        enPanne = true;
        HeralizInterface.LOG.error("[interface] menu Echap HeraliZ en panne, retour au menu vanilla pour cette session", t);
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new PauseScreen(true));
    }

    /** Rechargement des ressources (F3+T, pack de ressources) : programmes du flou et glyphes recrees au besoin. */
    @Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Ressources {
        private Ressources() {
        }

        @SubscribeEvent
        public static void rechargement(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) rm -> {
                Flou.liberer();
                Ui.viderCache();
            });
        }
    }
}
