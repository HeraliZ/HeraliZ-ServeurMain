package com.Spartan.Client;

import com.Spartan.Networks.ClientReportPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Networks.TouchesPacket;
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
 * Touches des menus de clients de triche. Depuis l'audit du 02/11 (point 13), la liste n'est plus dans ce jar : le
 * serveur l'envoie à la connexion ({@link TouchesPacket}, réglage {@code integrity.touchesSurveillees} ; par défaut ² et
 * Maj droite, qu'aucune action du jeu ne peut prendre : HeraliZ Interface, TouchesInterdites). Un appui en jeu (hors d'un
 * écran où l'on écrit) sur l'une d'elles alors qu'aucune touche des options n'y est attachée est signalé au serveur
 * (code GLFW de la touche), qui décide (ReceptionServeur, type « touche »).
 *
 * Sous Windows, l'appui est aussi lu à chaque tick auprès du système (GetAsyncKeyState) : un menu de triche qui
 * ouvre sur cette touche avale l'appui avant le jeu, qui ne le voyait qu'à la fermeture du menu (capture trop tard).
 * Le serveur ignore un second signalement à moins de 0,5 s du premier.
 */
@Mod.EventBusSubscriber(modid = Spartan.MOD_ID, value = Dist.CLIENT)
public final class TouchesSuspectes {
    private TouchesSuspectes() {
    }

    /** Touches surveillées (codes GLFW), reçues du serveur à chaque connexion ; aucune avant. */
    private static volatile int[] codes = new int[0];
    /** Code virtuel Windows de chaque touche surveillée (-1 : inconnu, lu seulement par le jeu), et son état d'avant. */
    private static int[] vks = new int[0];
    private static boolean[] avant = new boolean[0];

    /** Liste envoyée par le serveur (thread du jeu). */
    static void surveiller(TouchesPacket p) {
        int[] c = p.codes();
        int[] v = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            v[i] = vkWindows(c[i]);
        }
        vks = v;
        avant = new boolean[c.length];
        codes = c;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        int code = event.getKey();
        for (int c : codes) {
            if (c == code) {
                signaler(code);
                return;
            }
        }
    }

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    private static final int SCAN_GRAVE = 0x29; // touche sous Échap (² en AZERTY, ` en QWERTY)
    private static boolean windowsHorsService;
    private static long focusVu;

    /** Code virtuel Windows d'une touche GLFW (touches où se cachent les menus de triche), -1 si inconnu. */
    private static int vkWindows(int glfw) {
        return switch (glfw) {
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> -2; // dépend de la disposition du clavier : lu par son code de balayage
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> 0xA1;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> 0xA3;
            case GLFW.GLFW_KEY_RIGHT_ALT -> 0xA5;
            case GLFW.GLFW_KEY_INSERT -> 0x2D;
            case GLFW.GLFW_KEY_DELETE -> 0x2E;
            case GLFW.GLFW_KEY_HOME -> 0x24;
            case GLFW.GLFW_KEY_END -> 0x23;
            case GLFW.GLFW_KEY_PAGE_UP -> 0x21;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 0x22;
            case GLFW.GLFW_KEY_PAUSE -> 0x13;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> 0x91;
            default -> glfw >= GLFW.GLFW_KEY_F1 && glfw <= GLFW.GLFW_KEY_F12 ? 0x70 + (glfw - GLFW.GLFW_KEY_F1) : -1;
        };
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        int[] c = codes;
        if (event.phase != TickEvent.Phase.END || !WINDOWS || windowsHorsService || c.length == 0) {
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
            int[] v = vks;
            boolean[] a = avant;
            for (int i = 0; i < c.length && i < v.length && i < a.length; i++) {
                if (v[i] == -2) {
                    v[i] = ClavierWindows.INSTANCE.MapVirtualKeyW(SCAN_GRAVE, 1); // MAPVK_VSC_TO_VK
                    if (v[i] <= 0) {
                        v[i] = -1;
                    }
                }
                if (v[i] <= 0) {
                    continue;
                }
                boolean appui = enfoncee(v[i]);
                if (appui && !a[i] && surLeJeu) {
                    signaler(c[i]);
                }
                a[i] = appui;
            }
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
        ModMessages.sendToServer(new ClientReportPacket("touche", Integer.toString(code)));
    }
}
