package fr.heralisz.utils.inspecter;

import fr.heralisz.utils.coffres.CoffresPersonnels;
import net.minecraft.server.TickTask;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu d'une vue de /inspecter (coffre de 6 lignes de Minecraft, rien à installer chez les joueurs) : décors et boutons
 * ne se prennent pas ; un bouton agit au clic. Après chaque clic, la sauvegarde d'un joueur déconnecté est réécrite.
 * Fermé dès que le joueur inspecté se connecte ou se déconnecte (Inspecter).
 */
final class MenuInspection extends ChestMenu {
    final Cible cible;
    final Vue vue;
    private final Runnable entretien;
    private int ticks;

    MenuInspection(int id, Inventory inv, Cible cible, Vue vue, Runnable entretien) {
        super(MenuType.GENERIC_9x6, id, inv, vue, 6);
        CoffresPersonnels.casesSelonConteneur(this, vue, Vue.TAILLE);
        this.cible = cible;
        this.vue = vue;
        this.entretien = entretien;
        cible.ouvert();
    }

    @Override
    public void clicked(int slotId, int bouton, ClickType type, Player p) {
        if (slotId >= 0 && slotId < Vue.TAILLE && !this.vue.modifiable(slotId)) {
            Vue.Case c = this.vue.caseA(slotId);
            Runnable action = c == null ? null : c.action();
            if (action != null && type == ClickType.PICKUP && this.getCarried().isEmpty()) {
                // au tick suivant : on n'ouvre pas un autre menu au milieu du clic de celui-ci
                this.cible.srv.tell(new TickTask(this.cible.srv.getTickCount(), action));
            }
            this.sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, bouton, type, p);
        this.cible.sauver();
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        if (slot.container == this.vue && !this.vue.modifiable(slot.getContainerSlot())) {
            return false;
        }
        return super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index >= 0 && index < Vue.TAILLE && !this.vue.modifiable(index)) {
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(p, index);
    }

    @Override
    public void broadcastChanges() {
        if (this.entretien != null && ++this.ticks % 1200 == 0) {
            this.entretien.run();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player p) {
        return this.cible.valide();
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        this.cible.sauver();
        this.cible.ferme();
    }
}
