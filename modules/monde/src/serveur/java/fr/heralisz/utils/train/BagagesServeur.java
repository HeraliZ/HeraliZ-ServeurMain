package fr.heralisz.utils.train;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.minecraftforge.network.PacketDistributor;

/**
 * Fouille des bagages du train (serveur) : le joueur doit être à bord de cette voiture, près du bagage (position
 * vérifiée par le serveur, celle du paquet Marche), et le bagage doit être « à fouiller » pendant cette période
 * (Bagages.actif). Il se remplit à sa première ouverture de la période avec la table de son palier de HeraliZ Loot
 * (fr.heraliz.loot.TrainLoot, par réflexion : sans HeraliZ Loot, les bagages restent vides). Un bagage ouvert perd ses
 * étoiles chez tous les joueurs (paquet BagagesOuverts). Contenu gardé en mémoire (perdu au redémarrage du serveur).
 */
public final class BagagesServeur {
    private static final Map<Long, SimpleContainer> CONTENUS = new HashMap<>();
    private static final Set<Long> OUVERTS = new HashSet<>();
    private static long epoqueCourante = -1;
    private static Method tirer;
    private static boolean cherche;

    private BagagesServeur() {}

    private static void periode(long epoque) {
        if (epoque != epoqueCourante) {
            epoqueCourante = epoque;
            CONTENUS.clear();
            OUVERTS.clear();
        }
    }

    private static Train.BagagesOuverts etat() {
        long[] c = new long[OUVERTS.size()];
        int i = 0;
        for (long k : OUVERTS) c[i++] = k;
        return new Train.BagagesOuverts(epoqueCourante, c);
    }

    /** Bagages déjà ouverts pendant la période en cours, pour un joueur qui se connecte. */
    static void envoyer(ServerPlayer p) {
        periode(Bagages.epoque(p.server.overworld().getGameTime()));
        Train.CANAL.send(PacketDistributor.PLAYER.with(() -> p), etat());
    }

    static void ouvrir(ServerPlayer p, Train.Bagage m) {
        if (!(p.getVehicle() instanceof Voyageur v) || v.isRemoved()) return;
        int veh = v.getEntityData().get(Voyageur.VEHICULE);
        if (veh != m.vehicule()) return;
        List<Bagages.Place> places = Bagages.places(veh);
        if (m.place() < 0 || m.place() >= places.size()) return;
        Bagages.Place b = places.get(m.place());
        float x = v.getEntityData().get(Voyageur.LX), z = v.getEntityData().get(Voyageur.LZ);
        if (Math.hypot(x - b.lx(), z - b.lz()) > Bagages.PORTEE + 0.4) return;            // trop loin : refusé
        long epoque = Bagages.epoque(p.level().getGameTime());
        periode(epoque);
        if (!Bagages.actif(veh, m.place(), epoque)) return;                               // du décor pour cette période
        long cle = Bagages.cle(veh, m.place());
        SimpleContainer contenu = CONTENUS.get(cle);
        if (contenu == null) {
            contenu = new SimpleContainer(18);
            remplir(contenu, b.palier(), p.getRandom());
            CONTENUS.put(cle, contenu);
        }
        if (OUVERTS.add(cle)) Train.CANAL.send(PacketDistributor.ALL.noArg(), etat());
        final SimpleContainer inv = contenu;
        Component titre = Component.translatable("block.heraliz_loot." + b.modele());
        p.openMenu(new SimpleMenuProvider((id, pinv, pl) -> new ChestMenu(MenuType.GENERIC_9x2, id, pinv, inv, 2) {
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
