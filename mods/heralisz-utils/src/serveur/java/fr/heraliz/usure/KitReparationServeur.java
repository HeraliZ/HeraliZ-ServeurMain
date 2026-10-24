package fr.heraliz.usure;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Partie SERVEUR de {@link KitReparation} (docs/METHODE_CLIENT_SERVEUR.md §5.3) : la reparation au clic droit dans
 * l'inventaire (methodes d'origine, telles quelles ; seule la reparation passe par {@link ReparationServeur}). Creee par
 * l'usine Contenu.KIT_USINE des que la partie serveur est dans le jar (avec -complet, aussi cote client : la reparation
 * reste derriere « player instanceof ServerPlayer », comme a l'origine) ; ne jamais faire « new KitReparation(...) ».
 */
public class KitReparationServeur extends KitReparation {
    public KitReparationServeur(int niveau, Rarity rarete) {
        super(niveau, rarete);
    }

    public KitReparationServeur(Params a) {
        super(a);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack kit, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack cible = slot.getItem();
        if (!reparable(cible) || !slot.allowModification(player)) return false;
        if (player instanceof ServerPlayer sp) {
            ReparationServeur.appliquer(sp, kit, cible, niveau);
            slot.setChanged();
        }
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack kit, ItemStack autre, Slot slot, ClickAction action, Player player, SlotAccess porte) {
        // arme tenue au curseur, clic droit sur le kit : meme reparation (l'arme est l'objet porte)
        if (action != ClickAction.SECONDARY || !reparable(autre) || !slot.allowModification(player)) return false;
        if (player instanceof ServerPlayer sp) {
            ReparationServeur.appliquer(sp, kit, autre, niveau);
            slot.setChanged();
        }
        return true;
    }
}
