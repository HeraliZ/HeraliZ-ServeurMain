package fr.heraliz.armory.client.curios;

import com.mrcrayfish.backpacked.client.gui.screen.inventory.BackpackScreen;
import com.mrcrayfish.backpacked.inventory.container.BackpackContainerMenu;
import com.mrcrayfish.backpacked.platform.Services;
import fr.heraliz.noyau.client.BadgeRarete;
import fr.heraliz.noyau.client.kit.CaseVerre;
import fr.heraliz.noyau.client.kit.EcranConteneurVerre;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Écran du sac à dos en verre (aperçu validé par le propriétaire le 14/11), à la place de celui de HeraliZ Backpack
 * ({@link SacVerre}) : le nom du sac et sa rareté en haut, son contenu en grille, « Sur toi » et « Accès rapide » en
 * bas, comme l'inventaire.
 * <ul>
 *     <li>Même menu que l'écran du mod (BackpackContainerMenu) : côté serveur rien ne change, ses règles restent seules
 *     juges (audit du 14/11 : l'écran ne vaut que si ce même sac est sur le dos) ; seules les places des cases changent
 *     chez le joueur.</li>
 *     <li>Comme l'écran du mod : la touche du sac (B) le ferme, le bruit de l'équipement en cuir à l'ouverture.</li>
 *     <li>Erreur de dessin : l'écran du mod prend la suite (même menu).</li>
 * </ul>
 */
public final class EcranSac extends EcranConteneurVerre<BackpackContainerMenu> {
    private final TexteHud rarete = new TexteHud(PoliceHud.MINI);
    private int couleur = -1;
    private boolean ouvert;

    private EcranSac(BackpackContainerMenu menu, Inventory inv, Component titre) {
        super(menu, inv, titre, menu.getCols(), menu.getRows());
    }

    /** L'écran en verre à la place de celui du mod (même menu, même titre). */
    static EcranSac de(AbstractContainerScreen<?> ecranDuMod) {
        return new EcranSac((BackpackContainerMenu) ecranDuMod.getMenu(), Minecraft.getInstance().player.getInventory(), ecranDuMod.getTitle());
    }

    @Override
    protected void init() {
        super.init();
        if (!ouvert) {
            ouvert = true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER, 0.75F, 1.0F));
        }
        // rareté du sac porté (son propre sac : on ne fouille pas le sac d'un autre sur HeraliZ)
        ItemStack sac = menu.isOwner() ? Services.BACKPACK.getBackpackStack(minecraft.player) : ItemStack.EMPTY;
        int c = CaseVerre.couleurRarete(sac);
        int rang = c < 0 ? -1 : BadgeRarete.rangCouleur(c);
        couleur = c;
        rarete.texte(rang < 0 ? "" : BadgeRarete.nom(rang));
    }

    @Override
    protected void apresNom(GuiGraphics g, float x, float cy) {
        if (rarete.vide()) return;
        rarete.ajouter(g, x, cy, Palette.argb(Palette.clair(couleur, 0.2f), 255), TexteHud.GAUCHE, false);
        g.flush();
    }

    /** La touche du sac le ferme, comme dans l'écran du mod. */
    @Override
    public boolean keyPressed(int touche, int code, int modifs) {
        KeyMapping k = SacClient.touche();
        if (k != null && k.matches(touche, code)) {
            onClose();
            return true;
        }
        return super.keyPressed(touche, code, modifs);
    }

    @Override
    protected void secours() {
        SacVerre.panne();
        minecraft.setScreen(new BackpackScreen(menu, minecraft.player.getInventory(), title));
    }
}
