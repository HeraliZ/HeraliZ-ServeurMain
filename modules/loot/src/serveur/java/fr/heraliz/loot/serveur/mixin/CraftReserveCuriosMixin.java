package fr.heraliz.loot.serveur.mixin;

import fr.heraliz.loot.CraftReserve;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabrication réservée au grade Fondateur ({@link CraftReserve}) dans l'inventaire réel du jeu, celui de Curios (audit du
 * 15/11, duplication n° 2). Ses deux écrans ({@code CuriosContainer} et {@code CuriosContainerV2}, celui qu'on ouvre ; V2
 * hérite de V1 mais refait la méthode sans l'appeler) ont leur propre grille 2×2 et calculent eux-mêmes la recette dans
 * {@code slotsChanged} ({@code m_6199_} : {@code RecipeManager.getRecipeFor}, puis la case 0, vu par {@code javap} sur
 * curios-forge-5.14.1), sans passer par {@code CraftingMenu.slotChangedCraftingGrid} ({@link CraftReserveMixin}). La grille
 * est cachée par l'inventaire en verre, mais elle existe côté serveur : un jeu modifié, ou l'écran de secours de Curios,
 * y fabriquait encore.
 * <p>Pour un joueur qui n'a pas le droit : la case résultat reste vide et le joueur la reçoit vide, comme
 * {@link CraftReserveMixin}. Les objets posés dans la grille restent à lui (rendus à la fermeture). Le joueur est lu dans
 * les cases de l'inventaire du menu (pas de {@code @Shadow} sur Curios). Noms de Curios, non obfusqués ({@code remap =
 * false}), Curios absent de la compilation de Loot (cibles par leur nom, {@code @Pseudo} comme {@code MatmosServeurMixin}
 * du noyau) ; {@code require = 0} et config non obligatoire : si Curios change, rien ne plante (le garde-fou
 * {@link CraftResultatMixin} reste).</p>
 */
@Pseudo
@Mixin(targets = {"top.theillusivec4.curios.common.inventory.container.CuriosContainer",
        "top.theillusivec4.curios.common.inventory.container.CuriosContainerV2"}, remap = false)
public abstract class CraftReserveCuriosMixin {
    @Inject(method = "m_6199_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void heraliz$craftReserve(Container grille, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        ServerPlayer sp = joueur(menu);
        if (sp == null || CraftReserve.permis(sp)) return;
        if (!menu.slots.isEmpty() && menu.getSlot(0) instanceof ResultSlot resultat) {
            resultat.container.setItem(resultat.getContainerSlot(), ItemStack.EMPTY);
        }
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        sp.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
        ci.cancel();
    }

    /** Le joueur du menu (côté serveur seulement), lu dans ses cases d'inventaire ; null chez le joueur (jeu solo). */
    @Nullable
    private static ServerPlayer joueur(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory inv) return inv.player instanceof ServerPlayer sp ? sp : null;
        }
        return null;
    }
}
