package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.Texte;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Objets du recycleur du spawn d'un joueur, gardés AVEC le joueur (02/11, audit point 6) : avant, se déconnecter, planter
 * ou subir un {@code /redemarrage} avec des objets dedans les jetait au sol (Minecraft enregistre le joueur avant de fermer
 * son écran), et un plantage du serveur les perdait.
 * <ul>
 *     <li>écran ouvert : le contenu des 12 cases est recopié dans les données du joueur à chaque changement
 *     ({@code PlayerPersisted.HeralizRecycleur}, enregistrées avec son inventaire, gardées à la réapparition) : à tout
 *     enregistrement, un objet est soit dans l'inventaire, soit dans cette copie, jamais dans les deux ni dans aucun ;</li>
 *     <li>fermeture et déconnexion (avant l'enregistrement du joueur) : tout revient dans l'inventaire ; ce qui n'y tient
 *     pas reste dans la copie, jamais au sol, et le joueur le retrouve dans le recycleur en le rouvrant, ou dans son
 *     inventaire à sa prochaine connexion ou réapparition s'il y a la place ;</li>
 *     <li>plantage du serveur : à la connexion, la copie enregistrée est rendue de même.</li>
 * </ul>
 * Fil serveur seulement.
 */
final class GardeRecycleur {
    private GardeRecycleur() {
    }

    private static final String PERSISTE = "PlayerPersisted", CLE = "HeralizRecycleur", ENTREE = "entree", SORTIE = "sortie";

    private static CompoundTag persiste(ServerPlayer p, boolean creer) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains(PERSISTE, Tag.TAG_COMPOUND)) {
            if (!creer) return null;
            d.put(PERSISTE, new CompoundTag());
        }
        return d.getCompound(PERSISTE);
    }

    /** Le joueur a des objets gardés par le recycleur du spawn. */
    static boolean present(ServerPlayer p) {
        CompoundTag t = persiste(p, false);
        return t != null && t.contains(CLE, Tag.TAG_COMPOUND);
    }

    /** Recopie le contenu des cases (appelé à chaque changement) ; rien dans les cases : la copie disparaît. */
    static void ecrire(ServerPlayer p, Container entree, Container sortie) {
        ecrire(p, objets(entree), objets(sortie));
    }

    static void ecrire(ServerPlayer p, List<ItemStack> entree, List<ItemStack> sortie) {
        if (entree.isEmpty() && sortie.isEmpty()) {
            CompoundTag t = persiste(p, false);
            if (t != null) t.remove(CLE);
            return;
        }
        CompoundTag g = new CompoundTag();
        g.put(ENTREE, liste(entree));
        g.put(SORTIE, liste(sortie));
        persiste(p, true).put(CLE, g);
    }

    /** Objets gardés : [0] cases « à recycler », [1] cases « récupéré ». */
    @SuppressWarnings("unchecked")
    static List<ItemStack>[] lire(ServerPlayer p) {
        List<ItemStack>[] r = new List[]{new ArrayList<ItemStack>(), new ArrayList<ItemStack>()};
        CompoundTag t = persiste(p, false);
        if (t == null || !t.contains(CLE, Tag.TAG_COMPOUND)) return r;
        CompoundTag g = t.getCompound(CLE);
        for (int i = 0; i < 2; i++) {
            ListTag l = g.getList(i == 0 ? ENTREE : SORTIE, Tag.TAG_COMPOUND);
            for (int k = 0; k < l.size(); k++) {
                ItemStack s = ItemStack.of(l.getCompound(k));
                if (!s.isEmpty()) r[i].add(s);
            }
        }
        return r;
    }

    /**
     * Rend dans l'inventaire tout ce qui y tient (objets déplacés, jamais copiés) ; le reste reste gardé et le joueur est
     * prévenu. Connexion et réapparition (objets gardés après un plantage ou un inventaire plein).
     */
    static void rendre(ServerPlayer p) {
        if (!present(p) || !p.isAlive()) return;
        List<ItemStack>[] g = lire(p);
        int rendus = 0;
        for (List<ItemStack> l : g) {
            for (ItemStack s : l) {
                int avant = s.getCount();
                p.getInventory().add(s);
                rendus += avant - s.getCount();
            }
            l.removeIf(ItemStack::isEmpty);
        }
        ecrire(p, g[0], g[1]);
        p.getInventory().setChanged();
        if (rendus > 0) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.recycleur.rendus", Texte.brut(rendus)), 0xE8C36A);
        }
        prevenir(p);
    }

    /** Il reste des objets gardés : le joueur sait où les retrouver. */
    static void prevenir(ServerPlayer p) {
        if (!present(p)) return;
        List<ItemStack>[] g = lire(p);
        int n = 0;
        for (List<ItemStack> l : g) for (ItemStack s : l) n += s.getCount();
        if (n > 0) Moteur.notif(p, Texte.cle("heraliz_quetes.recycleur.garde", Texte.brut(n)), 0xFFB060);
    }

    private static List<ItemStack> objets(Container c) {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) l.add(c.getItem(i));
        return l;
    }

    private static ListTag liste(List<ItemStack> objets) {
        ListTag l = new ListTag();
        for (ItemStack s : objets) if (!s.isEmpty()) l.add(s.save(new CompoundTag()));
        return l;
    }
}
