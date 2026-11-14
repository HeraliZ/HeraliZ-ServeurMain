package fr.heraliz.noyau.client.effets;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.client.kit.Formes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.joml.Matrix4f;

/**
 * Effets à l'écran de jeu, variante V2 choisie par le propriétaire le 14/11 (« beaucoup moins de choses à l'écran ») :
 * en haut à droite, à la place des cadres de Minecraft (retirés), une rangée de petites icônes de la taille d'un cœur,
 * sans cadre ni texte, chacune dans un anneau fin ({@link IconeEffet}) ; de droite à gauche : à soigner, zone, malus,
 * bonus (le plus court d'abord). Rien sans effet. Le bonus de loot n'y est jamais (inventaire seulement, 11/11).
 * <p>Les notifications du même coin (solde, quêtes) se posent dessous : {@link #decalageHautDroite()}.
 * <p>Rien n'est alloué à chaque image : la liste est refaite au tick ({@link EffetsJoueur}).
 */
public final class EffetsHud {
    private EffetsHud() {
    }

    /** Marge depuis le coin, nombre d'icônes au plus (au-delà, les plus longues ne sont pas montrées). */
    private static final float MARGE = 8f;
    private static final int MAX_ICONES = 12;
    /** Place prise sous le haut de l'écran par la rangée (anneau de 12,5 et un écart). */
    private static final int HAUTEUR = 18;

    /** Branche le HUD des effets (partie client du noyau). */
    public static void enregistrer(IEventBus busMod) {
        busMod.addListener((RegisterGuiOverlaysEvent e) ->
                e.registerAbove(VanillaGuiOverlay.POTION_ICONS.id(), "effets", (gui, g, partiel, w, h) -> dessiner(g, w)));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderGuiOverlayEvent.Pre.class, EffetsHud::cadresMinecraft);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, EffetsJoueur::tick);
    }

    /** Les cadres d'effets de Minecraft (haut droit) ne sont plus dessinés : remplacés par la rangée. */
    private static void cadresMinecraft(RenderGuiOverlayEvent.Pre e) {
        if (VanillaGuiOverlay.POTION_ICONS.id().equals(e.getOverlay().id())) e.setCanceled(true);
    }

    /**
     * Unités d'interface dont les notifications du coin haut droit (solde, quêtes…) doivent descendre pour passer sous
     * les effets : 0 sans effet à l'écran.
     */
    public static int decalageHautDroite() {
        Minecraft mc = Minecraft.getInstance();
        return EffetsJoueur.HUD.taille() > 0 && !mc.options.hideGui ? HAUTEUR : 0;
    }

    private static void dessiner(GuiGraphics g, int w) {
        EffetsJoueur.Liste l = EffetsJoueur.HUD;
        int n = Math.min(l.taille(), MAX_ICONES);
        if (n == 0 || Minecraft.getInstance().options.hideGui) return;
        float cy = MARGE + 6.5f, x0 = w - MARGE - 6.5f;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        for (int i = 0; i < n; i++) IconeEffet.anneau(vc, m, x0 - i * IconeEffet.PAS, cy, l.categorie(i), l.reste(i), true);
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < n; i++) IconeEffet.icone(g, l.effet(i).getEffect(), x0 - i * IconeEffet.PAS, cy);
        RenderSystem.disableBlend();
        boolean point = false;
        for (int i = 0; i < n; i++) point |= l.categorie(i) == EffetsJoueur.SOIN;
        if (!point) return;
        float pulsation = EffetsJoueur.pulsation();
        vc = Formes.tampon(g);
        for (int i = 0; i < n; i++) {
            if (l.categorie(i) == EffetsJoueur.SOIN) IconeEffet.point(vc, m, x0 - i * IconeEffet.PAS, cy, pulsation);
        }
        g.flush();
    }
}
