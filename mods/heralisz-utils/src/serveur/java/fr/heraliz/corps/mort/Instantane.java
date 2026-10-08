package fr.heraliz.corps.mort;

import fr.heraliz.corps.compat.CuriosCompat;
import fr.heraliz.corps.entite.CorpsContenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Photo de l'equipement d'un joueur a l'instant de sa mort ({@code LivingDeathEvent}, juste avant la chute des objets) :
 * pour chaque objet porte (l'objet lui-meme, par identite), la case du corps qui lui revient. Les objets tombes
 * ({@code LivingDropsEvent}) sont ensuite ranges a leur place grace a elle : vanilla et Curios lachent les objets
 * d'origine, sans copie.
 */
final class Instantane {
    /** Objet porte -> case du corps (0..41) ou {@link CorpsContenu#CURIOS} + rang dans {@link #curiosIds}. */
    final Map<ItemStack, Integer> places = new IdentityHashMap<>();
    /** Emplacement Curios de chaque accessoire porte (dans l'ordre de Curios). */
    final List<String> curiosIds = new ArrayList<>();
    final Component cause;
    final boolean execution;

    private Instantane(Component cause, boolean execution) {
        this.cause = cause;
        this.execution = execution;
    }

    static Instantane prendre(ServerPlayer p, DamageSource source) {
        Instantane s = new Instantane(Causes.calculer(p, source), Causes.execution(source));
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size() && i < CorpsContenu.NB_INVENTAIRE; i++) s.noter(inv.items.get(i), CorpsContenu.INVENTAIRE + i);
        for (int i = 0; i < inv.armor.size() && i < 4; i++) s.noter(inv.armor.get(i), CorpsContenu.ARMURE + i);
        if (!inv.offhand.isEmpty()) s.noter(inv.offhand.get(0), CorpsContenu.MAIN_GAUCHE);
        boolean[] sacPris = {false};
        CuriosCompat.parcourir(p, (id, index, cosmetique, stack) -> {
            if (!cosmetique && !sacPris[0] && CuriosCompat.SAC.equals(id)) {
                sacPris[0] = true;
                s.noter(stack, CorpsContenu.SAC);
            } else {
                s.curiosIds.add(id);
                s.noter(stack, CorpsContenu.CURIOS + s.curiosIds.size() - 1);
            }
        });
        return s;
    }

    private void noter(ItemStack stack, int place) {
        if (stack != null && !stack.isEmpty()) places.putIfAbsent(stack, place);
    }

    /**
     * Case d'un objet tombe : par identite, sinon (mod qui aurait copie l'objet) un objet porte identique (meme objet,
     * meme nombre, meme NBT) pas encore retrouve. Chaque place n'est rendue qu'une fois.
     */
    @Nullable
    Integer retrouver(ItemStack tombe) {
        Integer place = places.remove(tombe);
        if (place != null) return place;
        Iterator<Map.Entry<ItemStack, Integer>> it = places.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ItemStack, Integer> e = it.next();
            if (ItemStack.matches(e.getKey(), tombe)) {
                it.remove();
                return e.getValue();
            }
        }
        return null;
    }
}
