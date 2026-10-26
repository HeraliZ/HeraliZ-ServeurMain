package fr.heraliz.usure;

import fr.heraliz.usure.regles.Regles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Atelier de réparation, partie serveur (26/10) : l'aperçu ({@link ReparationServeur#apercu}) est recalculé quand l'objet
 * posé ou le kit change ; « Réparer » revérifie tout (kit du même niveau toujours dans la case de la barre, objet
 * réparable) puis répare comme avant ({@link ReparationServeur#appliquer} : règles du serveur, utilisation du kit, son,
 * message). Fermeture : l'objet posé est rendu (au sol si le joueur est mort ou parti). Le menu se ferme seul si le kit
 * quitte la main.
 */
final class MenuReparationServeur extends MenuReparation {
    private final ServerPlayer joueur;
    private final int niveau;
    private boolean fini;
    /** Dernier état vu (objet posé, kit) : l'aperçu n'est recalculé que s'il change. */
    private ItemStack vuCible = ItemStack.EMPTY, vuKit = ItemStack.EMPTY;
    private int tick;

    MenuReparationServeur(int id, Inventory inv, int caseKit, int niveau) {
        super(id, inv, new SimpleContainer(1), new SimpleContainerData(NB_DONNEES), caseKit);
        this.joueur = (ServerPlayer) inv.player;
        this.niveau = niveau;
        donnees.set(D_NIVEAU, niveau);
        majApercu(true);
    }

    private ItemStack kit() {
        ItemStack k = joueur.getInventory().getItem(caseKit);
        return k.getItem() instanceof KitReparation kr && kr.niveau == niveau ? k : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.isAlive() && !kit().isEmpty() && p.getInventory().selected == caseKit;
    }

    @Override
    public void broadcastChanges() {
        if (!fini && ++tick % 4 == 0) majApercu(false);
        super.broadcastChanges();
    }

    private void majApercu(boolean force) {
        ItemStack c = cible.getItem(0), k = kit();
        if (!force && ItemStack.isSameItemSameTags(c, vuCible) && c.getCount() == vuCible.getCount()
                && ItemStack.isSameItemSameTags(k, vuKit) && k.getCount() == vuKit.getCount()) return;
        vuCible = c.copy();
        vuKit = k.copy();
        Regles r = ReglesServeur.get();
        int total = r.kitUtilisations[niveau];
        donnees.set(D_TOTAL, total);
        donnees.set(D_RESTANTES, k.isEmpty() ? 0 : Math.max(0, total - (k.hasTag() ? k.getTag().getInt(KitReparation.UTILISEES) : 0)));
        donnees.set(D_GAIN, Math.round(r.kitGain[niveau] * 10f));
        int[] a = ReparationServeur.apercu(c, niveau);
        int code = k.isEmpty() && a[0] != VIDE ? SANS_KIT : a[0];
        donnees.set(D_CODE, code);
        donnees.set(D_GENRE, a[1]);
        donnees.set(D_AVANT, a[2]);
        donnees.set(D_APRES, a[3]);
        donnees.set(D_MAX_AVANT, a[4]);
        donnees.set(D_MAX_APRES, a[5]);
    }

    @Override
    public boolean clickMenuButton(Player p, int bouton) {
        if (bouton != BOUTON_REPARER || fini) return false;
        ItemStack k = kit(), c = cible.getItem(0);
        if (k.isEmpty() || c.isEmpty() || !KitReparation.reparable(c)) return false;
        if (ReparationServeur.apercu(c, niveau)[0] != OK) return false;
        ReparationServeur.appliquer(joueur, k, c, niveau);
        cible.setChanged();
        majApercu(true);
        return true;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (fini) return;
        fini = true;
        if (!(p instanceof ServerPlayer sp) || !sp.isAlive() || sp.hasDisconnected()) clearContainer(p, cible);
        else sp.getInventory().placeItemBackInInventory(cible.removeItemNoUpdate(0));
    }
}
