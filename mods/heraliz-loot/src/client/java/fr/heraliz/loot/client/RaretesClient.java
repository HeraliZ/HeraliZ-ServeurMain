package fr.heraliz.loot.client;

import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.Rarete;
import fr.heraliz.loot.RaretesObjets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Couleur de rareté derrière les objets (choix du propriétaire) : un très léger dégradé de la couleur de la rareté, du
 * haut de la case (transparent) vers le bas (22 %), et un trait fin en bas de la case. Dans tous les écrans à cases
 * (inventaire, coffres, sacs, créatif...), dessiné après le fond de l'écran et avant les objets ; la barre d'objets façon
 * DayZ l'appelle aussi ({@link #couleur}, par réflexion). Les coffres T7 gardent leur propre révélation des raretés.
 */
@Mod.EventBusSubscriber(modid = HeralizLoot.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RaretesClient {
    private RaretesClient() {
    }

    /** Opacités du dégradé (haut, milieu, bas) et du trait du bas. */
    private static final int A_MILIEU = 18, A_BAS = 56, A_TRAIT = 191;

    @SubscribeEvent
    public static void fonds(ContainerScreenEvent.Render.Background e) {
        AbstractContainerScreen<?> ecran = e.getContainerScreen();
        if (ecran instanceof CoffreEcran) return;
        GuiGraphics g = e.getGuiGraphics();
        int gx = ecran.getGuiLeft(), gy = ecran.getGuiTop();
        for (Slot s : ecran.getMenu().slots) {
            if (s.isActive() && s.hasItem()) dessinerFond(g, gx + s.x, gy + s.y, s.getItem());
        }
    }

    /** Le fond de rareté d'une case de 16 × 16 en (x, y). */
    public static void dessinerFond(GuiGraphics g, int x, int y, ItemStack st) {
        Rarete r = RaretesObjets.de(st);
        if (r == null) return;
        int c = r.couleur & 0xFFFFFF;
        g.fillGradient(x, y, x + 16, y + 9, c, A_MILIEU << 24 | c);
        g.fillGradient(x, y + 9, x + 16, y + 16, A_MILIEU << 24 | c, A_BAS << 24 | c);
        g.fill(x, y + 15, x + 16, y + 16, A_TRAIT << 24 | c);
    }

    /** Couleur (RVB) de la rareté de cet objet, -1 : aucune. Pour la barre d'objets façon DayZ (appel par réflexion). */
    public static int couleur(ItemStack st) {
        Rarete r = RaretesObjets.de(st);
        return r == null ? -1 : r.couleur & 0xFFFFFF;
    }

    /** Hors connexion, plus de table reçue : retour à la table par défaut. */
    @SubscribeEvent
    public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        RaretesObjets.remplacer(RaretesObjets.Table.vide());
    }
}
