package fr.heralisz.utils.train;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Fouille des bagages du train (serveur) : le joueur doit être à bord de cette voiture, près du bagage (position
 * vérifiée par le serveur, celle du paquet Marche) ; le bagage se remplit à sa première ouverture, puis toutes les
 * 20 minutes de jeu, avec les tables de HeraliZ Loot (fr.heraliz.loot.TrainLoot, par réflexion : sans HeraliZ Loot, les
 * bagages restent vides). Contenu gardé en mémoire (repart à zéro au redémarrage du serveur).
 */
public final class BagagesServeur {
    private static final long RECHARGE = 20L * 60 * 20;
    private static final Map<Long, Etat> ETATS = new HashMap<>();
    private static Method tirer;
    private static boolean cherche;

    private static final class Etat {
        final SimpleContainer contenu = new SimpleContainer(18);
        long epoque = -1;
    }

    private BagagesServeur() {}

    static void ouvrir(ServerPlayer p, Train.Bagage m) {
        if (!(p.getVehicle() instanceof Voyageur v) || v.isRemoved()) return;
        int veh = v.getEntityData().get(Voyageur.VEHICULE);
        if (veh != m.vehicule()) return;
        List<Bagages.Place> places = Bagages.places(veh);
        if (m.place() < 0 || m.place() >= places.size()) return;
        Bagages.Place b = places.get(m.place());
        float x = v.getEntityData().get(Voyageur.LX), z = v.getEntityData().get(Voyageur.LZ);
        if (Math.hypot(x - b.lx(), z - b.lz()) > Bagages.PORTEE + 0.4) return;            // trop loin : refusé
        long epoque = p.level().getGameTime() / RECHARGE;
        long cle = ((long) veh << 8) | m.place();
        Etat e = ETATS.computeIfAbsent(cle, k -> new Etat());
        if (e.epoque != epoque) {
            e.epoque = epoque;
            remplir(e.contenu, b.palier(), p.getRandom());
        }
        Component titre = Component.translatableWithFallback("heralisz_utils.train.bagage", "Bagage · %s",
                b.palier().toUpperCase(java.util.Locale.ROOT));
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x2, id, inv, e.contenu, 2) {
            @Override
            public boolean stillValid(Player joueur) {                   // fermé si l'on quitte la voiture ou s'éloigne
                if (!(joueur.getVehicle() instanceof Voyageur vv) || vv.isRemoved()) return false;
                if (vv.getEntityData().get(Voyageur.VEHICULE) != veh) return false;
                return Math.hypot(vv.getEntityData().get(Voyageur.LX) - b.lx(), vv.getEntityData().get(Voyageur.LZ) - b.lz())
                        <= Bagages.PORTEE + 1.0;
            }
        }, titre));
    }

    private static void remplir(SimpleContainer c, String palier, RandomSource r) {
        c.clearContent();
        if (!cherche) {
            cherche = true;
            try {
                tirer = Class.forName("fr.heraliz.loot.TrainLoot").getMethod("tirer", String.class, RandomSource.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                HeraliszUtils.LOG.info("Bagages du train : HeraliZ Loot absent, bagages vides");
            }
        }
        if (tirer == null) return;
        try {
            @SuppressWarnings("unchecked")
            List<ItemStack> objets = (List<ItemStack>) tirer.invoke(null, palier, r);
            java.util.List<Integer> cases = new java.util.ArrayList<>();
            for (int i = 0; i < c.getContainerSize(); i++) cases.add(i);
            java.util.Collections.shuffle(cases, new java.util.Random(r.nextLong()));
            for (int i = 0; i < objets.size() && i < cases.size(); i++) c.setItem(cases.get(i), objets.get(i));
        } catch (ReflectiveOperationException ex) {
            HeraliszUtils.LOG.warn("Bagages du train : tirage impossible", ex);
        }
    }
}
