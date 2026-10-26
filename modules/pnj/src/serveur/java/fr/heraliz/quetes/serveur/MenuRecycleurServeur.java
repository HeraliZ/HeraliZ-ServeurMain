package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.bloc.MenuRecycleur;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Recycleur, partie serveur (25/10) : à chaque tick (broadcastChanges, appelé par le serveur pour le menu ouvert), la
 * première case « à recycler » qui se recycle avance de 1 / (secondes x 20) ; au bout, un paquet de l'objet est retiré, le
 * tirage ({@link Recyclage#tirer}) va dans les cases « récupéré », les hérals sur le compte. Rien ne démarre si la sortie
 * n'a pas assez de cases libres pour le pire tirage (rien ne se perd). L'aperçu de chaque case est renvoyé à l'écran quand
 * le contenu change. Fermeture : tout ce qui reste est rendu (ou tombe au sol si le joueur est mort ou parti).
 */
final class MenuRecycleurServeur extends MenuRecycleur {
    private final ServerPlayer joueur;
    private final Predicate<Player> valide;
    private int enCours = -1, progres, tick, recycles, composants;
    private long herals;
    private boolean fini;
    /** Signature du contenu de toutes les cases au dernier aperçu envoyé, et verdicts des cases « à recycler ». */
    private int signature = Integer.MIN_VALUE;
    private final Recyclage.Verdict[] verdicts = new Recyclage.Verdict[ENTREES];

    MenuRecycleurServeur(int id, Inventory inv, Predicate<Player> valide) {
        super(id, inv, new SimpleContainer(ENTREES), new SimpleContainer(SORTIES), new SimpleContainerData(NB_DONNEES));
        this.joueur = (ServerPlayer) inv.player;
        this.valide = valide;
        donnees.set(D_CASE, -1);
    }

    @Override
    public boolean stillValid(Player p) {
        return valide.test(p);
    }

    @Override
    public void broadcastChanges() {
        if (!fini) avancer();
        super.broadcastChanges();
    }

    private void avancer() {
        if (++tick % 5 == 1) apercuSiChange();
        int total = Math.max(10, Recyclage.ticksParObjet());
        donnees.set(D_DIXIEMES, total / 2);
        donnees.set(D_HERALS, (int) Math.min(Short.MAX_VALUE, herals));
        int i = choisir();
        if (i < 0) {
            enCours = -1;
            progres = 0;
            donnees.set(D_CASE, -1);
            donnees.set(D_PROGRES, 0);
            donnees.set(D_ATTENTE, 0);
            donnees.set(D_ETAT, entree.isEmpty() ? VIDE : RIEN);
            return;
        }
        if (i != enCours) {
            enCours = i;
            progres = 0;
        }
        donnees.set(D_CASE, i);
        donnees.set(D_ATTENTE, Math.min(Short.MAX_VALUE, attente() - 1));
        if (casesLibres() < Recyclage.maxPieces()) {
            donnees.set(D_ETAT, PLEIN);
            donnees.set(D_PROGRES, progres * 1000 / total);
            return;
        }
        donnees.set(D_ETAT, EN_COURS);
        if (++progres >= total) {
            recycler(i);
            progres = 0;
        }
        donnees.set(D_PROGRES, progres * 1000 / total);
    }

    /** La case en cours si elle se recycle encore, sinon la première qui se recycle (-1 : aucune). */
    private int choisir() {
        verdicts();
        if (enCours >= 0 && ok(enCours)) return enCours;
        for (int i = 0; i < ENTREES; i++) if (ok(i)) return i;
        return -1;
    }

    private boolean ok(int i) {
        Recyclage.Verdict v = verdicts[i];
        return v != null && v.etat() == Recyclage.OK && entree.getItem(i).getCount() >= v.par();
    }

    /** Paquets qui attendent, celui en cours compris. */
    private int attente() {
        int n = 0;
        for (int i = 0; i < ENTREES; i++) if (ok(i)) n += entree.getItem(i).getCount() / verdicts[i].par();
        return n;
    }

    private int casesLibres() {
        int n = 0;
        for (int i = 0; i < SORTIES; i++) if (sortie.getItem(i).isEmpty()) n++;
        return n;
    }

    /** Verdicts des cases « à recycler », refaits seulement quand le contenu d'une case change. */
    private final ItemStack[] vus = new ItemStack[ENTREES];
    private Map<String, Long> ventes;

    private void verdicts() {
        Map<String, Long> valeurs = null;
        for (int i = 0; i < ENTREES; i++) {
            ItemStack s = entree.getItem(i);
            if (vus[i] != null && ItemStack.isSameItemSameTags(vus[i], s) && vus[i].getCount() == s.getCount()) continue;
            vus[i] = s.copy();
            if (s.isEmpty()) {
                verdicts[i] = null;
                continue;
            }
            if (valeurs == null) {
                ventes = SaisonQuetes.ventes();
                valeurs = new HashMap<>();
            }
            verdicts[i] = Recyclage.verdict(s, ventes, valeurs);
        }
    }

    private void recycler(int i) {
        Recyclage.Verdict v = verdicts[i];
        ItemStack retire = entree.removeItem(i, v.par());
        if (retire.getCount() < v.par()) {
            // ne devrait pas arriver (vérifié juste avant) : rien n'est perdu, le paquet incomplet est remis
            entree.setItem(i, retire);
            return;
        }
        Recyclage.Tirage t = Recyclage.tirer(v);
        for (ItemStack piece : t.pieces()) {
            ItemStack reste = ((SimpleContainer) sortie).addItem(piece);
            if (!reste.isEmpty()) joueur.getInventory().placeItemBackInInventory(reste);
            composants += piece.getCount();
        }
        if (t.herals() > 0 && Ponts.deposer(joueur.getUUID(), t.herals(), "Recycleur")) herals += t.herals();
        recycles += v.par();
        joueur.playNotifySound(t.pieces().isEmpty() ? SoundEvents.GRINDSTONE_USE : SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS,
                0.35F, t.pieces().isEmpty() ? 0.8F : 1.1F);
    }

    /** Aperçu de toutes les cases non vides, renvoyé à l'écran quand leur contenu a changé. */
    private void apercuSiChange() {
        int sig = 1;
        for (int k = 0; k < slots.size(); k++) {
            ItemStack s = slots.get(k).getItem();
            sig = 31 * sig + (s.isEmpty() ? 0 : Objects.hash(ForgeRegistries.ITEMS.getKey(s.getItem()), s.getCount(), s.getTag()));
        }
        if (sig == signature) return;
        signature = sig;
        Map<String, Long> v = SaisonQuetes.ventes();
        Map<String, Long> valeurs = new HashMap<>();
        List<Reseau.ApercuCase> cases = new ArrayList<>();
        for (int k = 0; k < slots.size(); k++) {
            ItemStack s = slots.get(k).getItem();
            if (s.isEmpty() || estSortie(k)) continue;
            cases.add(Recyclage.apercu(k, Recyclage.verdict(s, v, valeurs)));
        }
        Reseau.envoyer(joueur, new Reseau.ApercuRecyclage(containerId, Recyclage.chance(), Recyclage.maxPieces(), cases));
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (fini) return;
        fini = true;
        if (!(p instanceof ServerPlayer sp) || !sp.isAlive() || sp.hasDisconnected()) {
            clearContainer(p, entree);
            clearContainer(p, sortie);
        } else {
            for (int i = 0; i < ENTREES; i++) sp.getInventory().placeItemBackInInventory(entree.removeItemNoUpdate(i));
            for (int i = 0; i < SORTIES; i++) sp.getInventory().placeItemBackInInventory(sortie.removeItemNoUpdate(i));
            Recyclage.bilan(sp, recycles, composants, herals);
        }
    }
}
