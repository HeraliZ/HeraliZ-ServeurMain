package fr.heraliz.armory.client.curios;

import fr.heraliz.armory.HeralizArmory;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.client.ICuriosScreen;
import top.theillusivec4.curios.client.gui.CuriosButton;
import top.theillusivec4.curios.common.inventory.container.CuriosContainerV2;
import top.theillusivec4.curios.common.network.NetworkHandler;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

/**
 * L'inventaire du joueur est l'ecran de Curios (demande du proprietaire, 13/10) : la touche de l'inventaire ouvre
 * directement l'ecran de Curios (l'inventaire du joueur, l'artisanat 2 x 2, l'armure et, a gauche, le panneau des slots
 * Curios toujours deplie) au lieu de l'inventaire de base, puis le bouton Curios n'a plus lieu d'etre.
 * <ul>
 *     <li>Ouverture : l'inventaire de base de Minecraft (InventoryScreen, ouvert par le jeu du joueur seul) n'est pas
 *     affiche ; le paquet d'ouverture de Curios part au serveur, qui ouvre son menu (CuriosContainerV2) comme le bouton
 *     ou la touche de Curios : rien n'apparait avant l'ecran de Curios (pas de clignotement). Un nouvel appui pendant
 *     l'attente ne redemande rien (pas de double ouverture). Sans reponse en {@link #ATTENTE_MS} ms (serveur sans Curios,
 *     ouverture refusee par un plugin), l'appui suivant ouvre l'inventaire de base une fois, puis on reessaie.</li>
 *     <li>Pas touche : creatif et spectateur (inventaire du jeu), monture a inventaire (cheval : ouvert par le serveur),
 *     joueur a terre (Reanimation refuse les ecrans avant nous), un autre menu deja ouvert, un serveur sans Curios.</li>
 *     <li>Le serveur decide de tout : objets deplaces par le menu de Curios, jamais copies (anti-duplication de Spartan) ;
 *     l'objet tenu a la souris est rendu par Curios. Fermeture par la touche de l'inventaire ou Echap, comme avant.</li>
 *     <li>Ecran de Curios hors creatif : plus de bouton « inventaire de base / Curios » ; livre de recettes ferme a chaque
 *     ouverture (ouvert, Curios le pose par-dessus l'inventaire et le panneau) ; panneau aussi haut que l'inventaire
 *     ({@link #fond}). Fond, cases et pastilles : textures du pack impose de HeraliZ Interface
 *     ({@code curios/textures/gui/inventory_revamp.png}).</li>
 *     <li>Classes internes de Curios 5.14.1 (paquet, menu, bouton) : une erreur coupe tout pour la session et rend
 *     l'inventaire de base (journal).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class InventaireCurios {
    private InventaireCurios() {
    }

    /** Delai de reponse du serveur au paquet d'ouverture de Curios. */
    static final long ATTENTE_MS = 1500;
    /** Texture de l'ecran de Curios (remplacee par le pack impose de HeraliZ Interface). */
    private static final ResourceLocation TEXTURE = new ResourceLocation("curios", "textures/gui/inventory_revamp.png");
    /** Hauteur de la partie haute du panneau dans la texture (lignes 0 a 158 ; le bas : lignes 159 a 165). */
    private static final int HAUT_TEXTURE = 159, BAS = 7;

    /** Heure limite de la reponse attendue du serveur (0 : rien demande). */
    private static long attente;
    private static boolean enPanne, secoursSignale;
    /** Dernier ecran de Curios initialise : le livre de recettes n'est ferme qu'a son ouverture, pas a chaque init. */
    private static WeakReference<Screen> dernier = new WeakReference<>(null);

    /** Touche de l'inventaire : l'ecran de Curios a la place de l'inventaire de base. */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void ouverture(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s instanceof ICuriosScreen) {
            attente = 0;
            return;
        }
        if (e.isCanceled() || enPanne || s == null || s.getClass() != InventoryScreen.class) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        ClientPacketListener co = mc.getConnection();
        if (joueur == null || co == null || mc.gameMode == null || mc.gameMode.hasInfiniteItems() || joueur.isSpectator()) return;
        if (joueur.containerMenu != joueur.inventoryMenu) return;
        try {
            Connection lien = co.getConnection();
            if (!lien.isMemoryConnection() && !NetworkHandler.INSTANCE.isRemotePresent(lien)) return;
            long maintenant = Util.getMillis();
            if (attente != 0 && maintenant >= attente) {
                // pas de reponse a temps : l'inventaire de base cette fois, l'ecran de Curios sera redemande ensuite
                attente = 0;
                if (!secoursSignale) {
                    secoursSignale = true;
                    HeralizArmory.LOGGER.warn("[armurerie] ecran de Curios sans reponse du serveur : inventaire de base");
                }
                return;
            }
            e.setCanceled(true);
            if (attente != 0) return;
            attente = maintenant + ATTENTE_MS;
            NetworkHandler.INSTANCE.sendToServer(new CPacketOpenCurios(ItemStack.EMPTY));
        } catch (LinkageError | RuntimeException ex) {
            panne(ex);
            e.setCanceled(false);
        }
    }

    /** Ecran de Curios (hors creatif) : sans le bouton Curios, livre de recettes ferme a l'ouverture. */
    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (!(s instanceof ICuriosScreen) || enPanne) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.gameMode.hasInfiniteItems()) return;
        try {
            for (GuiEventListener l : new ArrayList<>(e.getListenersList())) {
                if (l instanceof CuriosButton) e.removeListener(l);
            }
            if (dernier.get() != s) {
                dernier = new WeakReference<>(s);
                if (s instanceof RecipeUpdateListener r) {
                    RecipeBookComponent livre = r.getRecipeBookComponent();
                    if (livre.isVisible()) livre.toggleVisibility();
                }
            }
        } catch (LinkageError | RuntimeException ex) {
            panne(ex);
        }
    }

    /**
     * Panneau Curios aussi haut que l'inventaire : Curios le coupe sous la derniere case (7 slots : 140 px pour 166).
     * Meme dessin que CuriosScreenV2.renderBg (memes morceaux de texture), seule la hauteur change ; seulement la
     * disposition simple (une page, sans slots cosmetiques), sinon le dessin de Curios reste. Avant les fonds de rarete
     * (HeraliZ Loot) et les objets.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void fond(ContainerScreenEvent.Render.Background e) {
        AbstractContainerScreen<?> ecran = e.getContainerScreen();
        if (enPanne || !(ecran instanceof ICuriosScreen) || !(ecran.getMenu() instanceof CuriosContainerV2 menu)) return;
        try {
            List<Integer> grille = menu.grid;
            int haut = ecran.getYSize() - BAS;
            if (menu.totalPages > 1 || menu.hasCosmetics || grille.isEmpty() || haut > HAUT_TEXTURE
                    || 7 + grille.get(0) * 18 >= haut) return;
            GuiGraphics g = e.getGuiGraphics();
            int i = ecran.getGuiLeft(), j = ecran.getGuiTop();
            int x = -33 - (grille.size() - 1) * 18;
            for (int r = 0; r < grille.size(); r++) {
                int u = r == 0 ? 91 : 98;
                g.blit(TEXTURE, i + x, j, u, 0, 25, haut);
                g.blit(TEXTURE, i + x, j + haut, u, HAUT_TEXTURE, 25, BAS);
                if (grille.size() == 1) {
                    g.blit(TEXTURE, i + x + 7, j, 98, 0, 25, haut);
                    g.blit(TEXTURE, i + x + 7, j + haut, 98, HAUT_TEXTURE, 25, BAS);
                }
                x += r == 0 ? 25 : 18;
            }
            x -= grille.size() * 18;
            for (int lignes : grille) {
                g.blit(TEXTURE, i + x, j + 7, 7, 7, 18, lignes * 18);
                x += 18;
            }
        } catch (LinkageError | RuntimeException ex) {
            panne(ex);
        }
    }

    @SubscribeEvent
    public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
        attente = 0;
    }

    private static void panne(Throwable t) {
        if (enPanne) return;
        enPanne = true;
        attente = 0;
        HeralizArmory.LOGGER.error("[armurerie] ecran de Curios en panne : inventaire de base pour cette session", t);
    }
}
