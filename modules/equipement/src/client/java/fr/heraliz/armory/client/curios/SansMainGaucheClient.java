package fr.heraliz.armory.client.curios;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import fr.heraliz.armory.HeralizArmory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.client.ICuriosScreen;

/**
 * Plus de main gauche dans l'inventaire (31/10, demande du proprietaire), cote joueur. Le serveur range deja tout objet
 * pose dans la main gauche et refuse l'echange des mains (Tactique, {@code serveur/SansMainGauche}) ; ici, la case
 * disparait de l'ecran de Curios (l'inventaire) et de l'inventaire de base de secours :
 * <ul>
 *     <li>la case de la main gauche du menu affiche est remplacee par une {@link CaseCachee} : ni dessinee (ni l'icone du
 *     bouclier, ni objet, ni surbrillance), ni survolee, ni cliquable, ni prise dans un glisser ; le serveur, qui garde
 *     sa vraie case, reste seul juge ;</li>
 *     <li>son cadre, dessine dans le fond de l'inventaire ({@code textures/gui/container/inventory.png}, a 76, 61), est
 *     recouvert par la zone unie juste a sa droite de la meme texture (94, 61 : unie dans la texture de Minecraft et dans
 *     celle du pack de HeraliZ Interface) ;</li>
 *     <li>la touche « echanger les mains » (F) ne fait plus rien dans un inventaire (sur une case, elle envoyait l'objet
 *     survole dans la main gauche) ;</li>
 *     <li>un objet reste dans la main gauche seulement si l'inventaire est plein (le serveur le range des qu'une case se
 *     libere) : le clic droit ne s'en sert jamais (le jeu essayait la main gauche quand la main droite ne faisait rien).
 *     Refuse ici, chez le joueur, et pas seulement au serveur : sinon le joueur commencerait seul a l'utiliser (geste sans
 *     fin, rien de fait).</li>
 * </ul>
 * Createur et spectateur : rien ne change (la case reste dans l'inventaire du createur, staff). Maj + clic d'un bouclier
 * vers la main gauche est fait par le serveur : l'objet est range aussitot (meme tick).
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class SansMainGaucheClient {
    /** Cadre de la case dans le fond de l'inventaire, et zone unie de meme taille juste a droite. */
    private static final int CADRE_X = 76, CADRE_Y = 61, UNI_X = 94, TAILLE = 18;
    /** Position de la case de la main gauche dans l'inventaire de Minecraft et dans celui de Curios. */
    private static final int CASE_X = 77, CASE_Y = 62;

    private SansMainGaucheClient() {
    }

    /** Vrai pour le createur et le spectateur : la main gauche leur reste. */
    static boolean libre() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null || p.isCreative() || p.isSpectator();
    }

    /** Fond de l'inventaire dessine : case remplacee (aussi apres une reconstruction des cases par Curios), cadre efface. */
    @SubscribeEvent
    public static void fond(ContainerScreenEvent.Render.Background e) {
        AbstractContainerScreen<?> ecran = e.getContainerScreen();
        if (!inventaire(ecran) || libre()) return;
        Slot s = cacher(ecran.getMenu());
        if (s != null && s.x == CASE_X && s.y == CASE_Y) {
            e.getGuiGraphics().blit(InventoryScreen.INVENTORY_LOCATION, ecran.getGuiLeft() + CADRE_X, ecran.getGuiTop() + CADRE_Y,
                    UNI_X, CADRE_Y, TAILLE, TAILLE);
        }
    }

    /** Touche « echanger les mains » au clavier dans un inventaire : sans effet. */
    @SubscribeEvent
    public static void touche(ScreenEvent.KeyPressed.Pre e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?>) || e.getScreen() instanceof CreativeModeInventoryScreen || libre()) return;
        if (Minecraft.getInstance().options.keySwapOffhand.isActiveAndMatches(InputConstants.getKey(e.getKeyCode(), e.getScanCode()))) {
            e.setCanceled(true);
        }
    }

    /** Meme touche mise sur un bouton de la souris. */
    @SubscribeEvent
    public static void souris(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?>) || e.getScreen() instanceof CreativeModeInventoryScreen || libre()) return;
        if (Minecraft.getInstance().options.keySwapOffhand.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(e.getButton()))) {
            e.setCanceled(true);
        }
    }

    /** Clic droit : jamais avec un objet reste dans la main gauche. */
    @SubscribeEvent
    public static void utiliser(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem() || e.getHand() != InteractionHand.OFF_HAND || libre()) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && !p.getOffhandItem().isEmpty()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    /** L'inventaire du joueur : ecran de Curios ou inventaire de base (pas le createur). */
    private static boolean inventaire(AbstractContainerScreen<?> ecran) {
        return ecran instanceof ICuriosScreen || ecran.getClass() == InventoryScreen.class;
    }

    /** Remplace la case de la main gauche du menu par une case cachee ; rend cette case (null si le menu n'en a pas). */
    private static Slot cacher(AbstractContainerMenu menu) {
        for (int i = menu.slots.size() - 1; i >= 0; i--) {
            Slot s = menu.slots.get(i);
            if (s instanceof CaseCachee) return s;
            if (s.container instanceof Inventory && s.getContainerSlot() == Inventory.SLOT_OFFHAND) {
                CaseCachee c = new CaseCachee(s);
                menu.slots.set(i, c);
                return c;
            }
        }
        return null;
    }

    /**
     * Case de la main gauche cachee hors createur : meme place, meme inventaire, mais inactive (Minecraft ne dessine, ne
     * survole et ne clique que les cases actives) et sans depot. Ce qui y arrive du serveur y est toujours ecrit.
     */
    static final class CaseCachee extends Slot {
        private final Slot origine;

        CaseCachee(Slot origine) {
            super(origine.container, origine.getContainerSlot(), origine.x, origine.y);
            this.origine = origine;
            this.index = origine.index;
        }

        @Override
        public boolean isActive() {
            return libre() && origine.isActive();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return libre() && origine.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player joueur) {
            return origine.mayPickup(joueur);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return origine.getNoItemIcon();
        }
    }
}
