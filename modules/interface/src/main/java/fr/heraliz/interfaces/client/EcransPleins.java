package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Une seule règle pour tous les écrans plein écran des mods HeraliZ (25/10, audit des écrans en jeu : « que toutes les
 * interfaces aient la même charte, comme celles du clan ») : derrière eux, comme derrière les écrans du clan, ni HUD
 * (panneau de zone, Purge, suivi de quête, cœurs, barre d'objets) ni main ou arme du joueur ; ils passaient par-dessus les
 * titres. Gardent le HUD : le chat et les inventaires / coffres / fouilles (style DayZ de base, choix du propriétaire).
 * Les écrans qui ne dessinaient aucun fond reçoivent celui des écrans du clan ({@link #FOND}). Mort : plus de main ni
 * d'arme à l'écran (l'arme du mod tactique se dessinait avant le masquage de l'écran de mort).
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class EcransPleins {
    private EcransPleins() {
    }

    /** Paquets de nos mods (et du créateur de personnage). */
    private static final String[] NOS_PAQUETS = {"fr.heraliz.", "fr.heralisz.", "com.caracrea.", "com.heraliz."};
    /** Écrans sans fond à eux : le dégradé sombre des écrans du clan est posé derrière (nom simple de la classe). */
    private static final Set<String> FOND = Set.of("EcranWarps", "EcranKits", "EmoteBrowserScreen", "ExecutionScreen");
    /** Écrans à cases de nos mods qui sont des panneaux pleins (charte du clan, pas le style DayZ) : HUD caché aussi. */
    private static final Set<String> PANNEAUX = Set.of("EcranRecycleur");

    /**
     * Écran de modification d'arme de TaCZ et ses sous-classes (l'atelier d'arme du mod tactique) : TaCZ y dessine l'arme
     * par le rendu de la main, caméra sur l'arme. La main y reste donc dessinée (27/10 : l'arme n'apparaissait plus dans
     * l'atelier) ; le HUD reste caché comme derrière nos autres écrans.
     */
    private static final String ECRAN_MODIFICATION_TACZ = "com.tacz.guns.client.gui.GunRefitScreen";

    private static Screen dernier;
    private static boolean plein, fond, armeVisible;

    /** L'écran ouvert est un écran plein écran de nos mods (pas le chat, pas un inventaire ni un coffre). */
    public static boolean plein() {
        Screen s = Minecraft.getInstance().screen;
        if (s != dernier) {
            dernier = s;
            plein = s != null && !(s instanceof ChatScreen) && nous(s)
                    && (!(s instanceof AbstractContainerScreen<?>) || PANNEAUX.contains(s.getClass().getSimpleName()));
            fond = plein && FOND.contains(s.getClass().getSimpleName());
            armeVisible = plein && modificationTacz(s.getClass());
        }
        return plein;
    }

    private static boolean modificationTacz(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(ECRAN_MODIFICATION_TACZ)) return true;
        }
        return false;
    }

    private static boolean nous(Screen s) {
        String n = s.getClass().getName();
        for (String p : NOS_PAQUETS) if (n.startsWith(p)) return true;
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hud(RenderGuiOverlayEvent.Pre e) {
        if (plein()) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void main(RenderHandEvent e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (plein() && !armeVisible || p != null && p.isDeadOrDying()) e.setCanceled(true);
    }

    /** Fond des écrans du clan (dégradé noir, plus sombre en bas), avant que l'écran ne se dessine. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void fond(ScreenEvent.Render.Pre e) {
        if (!plein() || !fond) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getScreen().width, h = e.getScreen().height, bandes = 24;
        for (int i = 0; i < bandes; i++) {
            int x0 = w * i / bandes, x1 = w * (i + 1) / bandes;
            float t = (i + 0.5f) / bandes;
            int haut = (int) (0x8C + (0xB4 - 0x8C) * t * 0.5f) << 24, bas = (int) (0x8C + (0xB4 - 0x8C) * (0.5f + t * 0.5f)) << 24;
            g.fillGradient(x0, 0, x1, h, haut, bas);
        }
    }
}
