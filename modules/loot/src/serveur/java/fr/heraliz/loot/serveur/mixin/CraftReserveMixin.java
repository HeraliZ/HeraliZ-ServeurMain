package fr.heraliz.loot.serveur.mixin;

import fr.heraliz.loot.CraftReserve;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabrication réservée au grade Fondateur ({@link CraftReserve}, 14/11). {@code CraftingMenu.slotChangedCraftingGrid} est
 * le seul endroit où le serveur calcule le résultat d'une grille de fabrication : 2×2 de l'inventaire
 * ({@code InventoryMenu.slotsChanged}) comme 3×3 de l'établi. Pour un joueur qui n'a pas le droit, le résultat reste vide
 * (envoyé tel quel au joueur, comme Minecraft le fait sans recette) : rien à prendre, ni au clic, ni par Maj + clic, ni par
 * le livre de recettes. Les objets posés dans la grille restent à lui (rendus à la fermeture, comme d'habitude).
 */
@Mixin(CraftingMenu.class)
public abstract class CraftReserveMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), cancellable = true)
    private static void heraliz$craftReserve(AbstractContainerMenu menu, Level niveau, Player joueur, CraftingContainer grille,
            ResultContainer resultat, CallbackInfo ci) {
        if (niveau.isClientSide() || !(joueur instanceof ServerPlayer sp) || CraftReserve.permis(sp)) return;
        resultat.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        sp.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
        ci.cancel();
    }
}
