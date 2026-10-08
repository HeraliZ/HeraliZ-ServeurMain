package fr.heralisz.matchs.moteur.kits;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Equipement;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.compat.Armures;
import fr.heralisz.matchs.moteur.compat.Tacz;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Distribution d'un {@link Kit} (implementation de {@link Equipement#donner}) : TaCZ pour les armes, heralisz_tactical
 * pour la melee, les soins et les grenades, HeraliZ Armurerie pour la tenue ({@link Armures}). Chaque pile est marquee
 * comme pretee. Fil serveur.
 */
public final class Distribution {
    private Distribution() {
    }

    /** Voir {@link Equipement#donner}. */
    public static boolean donner(ServerPlayer p, Kit kit, String matchId) {
        Inventory inv = p.getInventory();
        int donnes = 0;
        boolean principale = false;
        // tenue
        for (String id : armure(kit)) {
            ItemStack st = Armures.piece(id);
            if (st.isEmpty()) continue;
            Equipement.marquer(st, matchId);
            if (Armures.equiper(p, st)) donnes++;
        }
        // armes : cases 0, 1, 2
        if (kit.principale() != null) {
            ItemStack st = Tacz.arme(kit.principale());
            if (!st.isEmpty()) {
                poser(inv, 0, Equipement.marquer(st, matchId));
                principale = true;
                donnes++;
            }
        }
        if (kit.secondaire() != null) {
            ItemStack st = Tacz.arme(kit.secondaire());
            if (!st.isEmpty()) {
                poser(inv, 1, Equipement.marquer(st, matchId));
                donnes++;
            }
        }
        if (kit.melee() != null) {
            ItemStack st = Tacz.objet(kit.melee(), 1);
            if (!st.isEmpty()) {
                poser(inv, 2, Equipement.marquer(st, matchId));
                donnes++;
            }
        }
        int caseLibre = 3;
        for (Kit.Objet o : kit.grenades()) {
            ItemStack st = Tacz.objet(o.id(), o.nombre());
            if (st.isEmpty()) continue;
            caseLibre = poserApres(inv, caseLibre, Equipement.marquer(st, matchId));
            donnes++;
        }
        for (Kit.Objet o : kit.soins()) {
            ItemStack st = Tacz.objet(o.id(), o.nombre());
            if (st.isEmpty()) continue;
            caseLibre = poserApres(inv, caseLibre, Equipement.marquer(st, matchId));
            donnes++;
        }
        inv.selected = 0;
        p.inventoryMenu.broadcastChanges();
        if (donnes == 0) HeraliszMatchs.LOG.warn("[matchs] kit {} : rien donne a {}", kit.id(), p.getGameProfile().getName());
        return principale || donnes > 0;
    }

    /** Voir {@link Equipement#piles}. */
    public static List<ItemStack> piles(Kit kit) {
        List<ItemStack> l = new ArrayList<>();
        if (kit.principale() != null) ajouter(l, Tacz.arme(kit.principale()));
        if (kit.secondaire() != null) ajouter(l, Tacz.arme(kit.secondaire()));
        if (kit.melee() != null) ajouter(l, Tacz.objet(kit.melee(), 1));
        for (Kit.Objet o : kit.grenades()) ajouter(l, Tacz.objet(o.id(), o.nombre()));
        for (Kit.Objet o : kit.soins()) ajouter(l, Tacz.objet(o.id(), o.nombre()));
        for (String id : armure(kit)) ajouter(l, Armures.piece(id));
        return l;
    }

    private static void ajouter(List<ItemStack> l, ItemStack st) {
        if (!st.isEmpty()) l.add(st);
    }

    private static List<String> armure(Kit kit) {
        if (!kit.armurePieces().isEmpty()) return kit.armurePieces();
        if (kit.armureKit() != null) return Armures.piecesDuKit(kit.armureKit());
        return List.of();
    }

    private static void poser(Inventory inv, int caseVoulue, ItemStack st) {
        if (inv.getItem(caseVoulue).isEmpty()) inv.setItem(caseVoulue, st);
        else inv.add(st);
    }

    /** Pose dans la premiere case libre a partir de {@code depuis} (barre puis sac) ; rend la case suivante. */
    private static int poserApres(Inventory inv, int depuis, ItemStack st) {
        for (int i = depuis; i < 36; i++) {
            if (inv.items.get(i).isEmpty()) {
                inv.setItem(i, st);
                return i + 1;
            }
        }
        inv.add(st);
        return 36;
    }
}
