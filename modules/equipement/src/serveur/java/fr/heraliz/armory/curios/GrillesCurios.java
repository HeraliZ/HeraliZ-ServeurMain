package fr.heraliz.armory.curios;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.type.ICuriosMenu;

/**
 * Grilles d'artisanat 2 x 2 depuis que l'ecran de Curios est l'inventaire du joueur (0.14.0, client/curios/
 * InventaireCurios) : il a sa propre grille, distincte de celle de l'inventaire de base (que lisent le corps et les camos
 * a vie a la mort). Les objets sont toujours DEPLACES, jamais copies (anti-duplication de Spartan), avec mise a jour du
 * resultat de l'artisanat (jamais un resultat sans ingredients).
 * <ul>
 *     <li>Ouverture de l'ecran de Curios : ce qui reste dans la grille de l'inventaire de base (inventaire de base ouvert
 *     en secours puis remplace par l'ecran de Curios, cote serveur il n'est jamais ferme) revient dans l'inventaire,
 *     comme a la fermeture de l'inventaire de base. Sinon ces objets resteraient caches dans cette grille.</li>
 *     <li>Mort avec l'ecran de Curios ouvert : sans rien faire, sa grille tomberait au sol bien apres (au reapparaitre),
 *     loin du corps et sans que les camos a vie soient retires. A la mort confirmee (LOW : apres la mise a terre de
 *     Reanimation et les morts annulees des matchs, avant la photo du corps de HeraliZ Utils en LOWEST), ces objets vont
 *     dans l'inventaire du joueur : ils tombent avec lui, sur son corps. Inventaire plein : dans la grille de
 *     l'inventaire de base (le corps et les camos la lisent deja), sinon ils restent ou ils sont.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID)
public final class GrillesCurios {
    private GrillesCurios() {
    }

    @SubscribeEvent
    public static void ouverture(PlayerContainerEvent.Open e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getContainer() instanceof ICuriosMenu) || !p.isAlive()) return;
        CraftingContainer base = p.inventoryMenu.getCraftSlots();
        for (int i = 0; i < base.getContainerSize(); i++) {
            if (base.getItem(i).isEmpty()) continue;
            ItemStack objet = base.removeItem(i, base.getItem(i).getCount());
            if (!objet.isEmpty()) p.getInventory().placeItemBackInInventory(objet);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void mort(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.containerMenu == p.inventoryMenu || !(p.containerMenu instanceof ICuriosMenu)) return;
        CraftingContainer base = p.inventoryMenu.getCraftSlots();
        for (Slot s : p.containerMenu.slots) {
            if (!(s.container instanceof CraftingContainer grille) || grille == base || !s.hasItem()) continue;
            int i = s.getContainerSlot();
            ItemStack objet = grille.removeItem(i, grille.getItem(i).getCount());
            if (objet.isEmpty()) continue;
            p.getInventory().add(objet);
            if (objet.isEmpty()) continue;
            if (i < base.getContainerSize() && base.getItem(i).isEmpty()) base.setItem(i, objet);
            else grille.setItem(i, objet);
        }
    }
}
