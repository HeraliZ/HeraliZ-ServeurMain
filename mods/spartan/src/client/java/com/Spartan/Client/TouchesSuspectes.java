package com.Spartan.Client;

import com.Spartan.Networks.ClientReportPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Spartan;
import com.Spartan.Utils.ClavierWindows;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Touches des menus de clients de triche : ² et Maj droite. Aucune action du jeu ne peut les prendre (HeraliZ
 * Interface, TouchesInterdites) : un appui en jeu (hors d'un écran où l'on écrit) sur l'une d'elles alors qu'aucune touche des
 * options n'y est attachée est signalé au serveur, qui décide (ReceptionServeur, type « touche »).
 *
 * Sous Windows, l'appui est aussi lu à chaque tick auprès du système (GetAsyncKeyState) : un menu de triche qui
 * ouvre sur cette touche avale l'appui avant le jeu, qui ne le voyait qu'à la fermeture du menu (capture trop tard).
 * Le serveur ignore un second signalement à moins de 0,5 s du premier.
 */
@Mod.EventBusSubscriber(modid = Spartan.MOD_ID, value = Dist.CLIENT)
public final class TouchesSuspectes {
    private TouchesSuspectes() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        int code = event.getKey();
        if (code == GLFW.GLFW_KEY_GRAVE_ACCENT || code == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            signaler(code);
        }
    }

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    private static final int VK_RSHIFT = 0xA1;
    private static final int SCAN_GRAVE = 0x29; // touche sous Échap (² en AZERTY, ` en QWERTY)
    private static int vkGrave = -1;
    private static boolean avantGrave;
    private static boolean avantMaj;
    private static boolean windowsHorsService;
    private static long focusVu;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !WINDOWS || windowsHorsService) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long maintenant = System.currentTimeMillis();
        if (mc.isWindowActive()) {
            focusVu = maintenant;
        }
        // jeu au premier plan, ou il y a moins de 3 s (une fenêtre de triche posée dessus peut prendre le focus) ;
        // jamais pour une touche tapée dans un autre programme
        boolean surLeJeu = maintenant - focusVu < 3000;
        try {
            if (vkGrave < 0) {
                vkGrave = ClavierWindows.INSTANCE.MapVirtualKeyW(SCAN_GRAVE, 1); // MAPVK_VSC_TO_VK
            }
            boolean grave = vkGrave > 0 && enfoncee(vkGrave);
            boolean maj = enfoncee(VK_RSHIFT);
            if (grave && !avantGrave && surLeJeu) {
                signaler(GLFW.GLFW_KEY_GRAVE_ACCENT);
            }
            if (maj && !avantMaj && surLeJeu) {
                signaler(GLFW.GLFW_KEY_RIGHT_SHIFT);
            }
            avantGrave = grave;
            avantMaj = maj;
        } catch (Throwable t) {
            windowsHorsService = true;
            Spartan.LOGGER.debug("[Spartan] lecture du clavier impossible : {}", t.toString());
        }
    }

    /** Enfoncée maintenant, ou appuyée depuis la dernière lecture (appui très court entre deux ticks). */
    private static boolean enfoncee(int vk) {
        return (ClavierWindows.INSTANCE.GetAsyncKeyState(vk) & 0x8001) != 0;
    }

    /**
     * Seuls les écrans où l'on tape du texte (chat, panneau, livre, champ de saisie actif) font ignorer l'appui. Tout
     * autre écran compte : le menu d'un client de triche est souvent un écran du jeu, ouvert par la touche elle-même
     * (avant la 2.7.4, l'appui était ignoré et la capture ne partait qu'à la fermeture du menu).
     */
    private static boolean saisieDeTexte(Screen ecran) {
        if (ecran == null) {
            return false;
        }
        if (ecran instanceof ChatScreen || ecran instanceof AbstractSignEditScreen || ecran instanceof BookEditScreen) {
            return true;
        }
        return ecran.getFocused() instanceof EditBox;
    }

    private static void signaler(int code) {
        String nom = code == GLFW.GLFW_KEY_GRAVE_ACCENT ? "²" : "Maj droite";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null || saisieDeTexte(mc.screen)) {
            return;
        }
        InputConstants.Key touche = InputConstants.Type.KEYSYM.getOrCreate(code);
        for (KeyMapping k : mc.options.keyMappings) {
            if (k != null && touche.equals(k.getKey())) {
                return; // attachée à une action : appui normal
            }
        }
        ModMessages.sendToServer(new ClientReportPacket("touche", nom));
    }
}
