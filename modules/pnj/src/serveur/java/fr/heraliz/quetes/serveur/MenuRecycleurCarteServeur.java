package fr.heraliz.quetes.serveur;

import fr.heraliz.quetes.bloc.MenuRecycleurCarte;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Écran du recycleur de la carte, partie serveur (31/10) : les cases de la machine
 * ({@link RecycleurCarteEntiteServeur}, partagées par tous les écrans ouverts dessus), l'inventaire du joueur, l'état du
 * recyclage lu sur la machine. Rien ne se rend à la fermeture : le contenu reste dans la machine.
 * <ul>
 * <li>cases « à recycler » : seulement un objet que CE recycleur recycle ({@link Recyclage#verdict} : ni objet qui ne
 * donne rien, ni objet acheté trop rentable, ni objet à vie, de quête, de match, ni sac plein) : jamais une cachette ;</li>
 * <li>cases « récupéré » : on ne peut que les vider (commun) ;</li>
 * <li>chaque clic qui change la machine est sauvegardé dans l'ordre sûr ({@link RecycleurCarteEntiteServeur#apresClic}) ;
 * échange avec la barre entre deux cases pleines : refusé.</li>
 * </ul>
 * Fermé si la machine part, si le joueur s'éloigne (8 blocs), passe à terre ou en match.
 */
final class MenuRecycleurCarteServeur extends MenuRecycleurCarte {
    private final ServerPlayer joueur;
    private final RecycleurCarteEntiteServeur machine;
    private final Vec3 centre;
    private final Donnees vue;
    private int tickValide, tickApercu;
    /** Signature du contenu de toutes les cases au dernier aperçu envoyé. */
    private int signature = Integer.MIN_VALUE;
    /** Ventes des marchands, relues au plus une fois par tick (tri des cases « à recycler »). */
    private Map<String, Long> ventes;
    private int ventesTick = -1;

    /** Données de l'écran : celles de la machine (partagées) et les hérals reçus par CE joueur depuis l'ouverture. */
    private static final class Donnees implements ContainerData {
        private final RecycleurCarteEntiteServeur m;
        long herals;

        Donnees(RecycleurCarteEntiteServeur m) {
            this.m = m;
        }

        @Override
        public int get(int i) {
            return switch (i) {
                case D_PROGRES -> m.dProgres;
                case D_ATTENTE -> m.dAttente;
                case D_ETAT -> m.dEtat;
                case D_HERALS -> (int) Math.min(Short.MAX_VALUE, herals);
                case D_CASE -> m.dCase;
                case D_DIXIEMES -> m.dDixiemes;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return NB_DONNEES;
        }
    }

    MenuRecycleurCarteServeur(int id, Inventory inv, RecycleurCarteEntiteServeur machine) {
        this(id, inv, machine, new Donnees(machine));
    }

    private MenuRecycleurCarteServeur(int id, Inventory inv, RecycleurCarteEntiteServeur machine, Donnees vue) {
        super(id, inv, machine.entree, machine.sortie, vue);
        this.joueur = (ServerPlayer) inv.player;
        this.machine = machine;
        this.vue = vue;
        this.centre = Vec3.atCenterOf(machine.getBlockPos());
        machine.vues.add(this);
        machine.reprendre();
    }

    /** Hérals « rien » versés au déposant {@code u} : montrés sur son écran. */
    void herals(UUID u, long n) {
        if (joueur.getUUID().equals(u)) vue.herals += n;
    }

    @Override
    public boolean stillValid(Player p) {
        if (!machine.utilisable() || p.level() != machine.getLevel() || p.distanceToSqr(centre) >= 64) return false;
        // à terre, en match : vérifié toutes les 10 ticks (réflexion vers Réanimation)
        if (++tickValide % 10 == 0 && p instanceof ServerPlayer sp && !sp.isCreative()) {
            return !Ponts.aTerre(sp) && !sp.getPersistentData().getBoolean("heralisz:en_match");
        }
        return true;
    }

    /** Une case « à recycler » n'accepte qu'un objet que ce recycleur recycle. */
    @Override
    protected boolean accepte(ItemStack s) {
        if (!machine.utilisable() || s.isEmpty()) return false;
        int t = joueur.server.getTickCount();
        if (ventes == null || ventesTick != t) {
            ventes = SaisonQuetes.ventes();
            ventesTick = t;
        }
        return Recyclage.verdict(s, ventes, new HashMap<>(), true).etat() == Recyclage.OK;
    }

    @Override
    public void clicked(int index, int bouton, ClickType type, Player p) {
        if (!machine.utilisable()) return;
        // touches 1 à 9 / F entre une case pleine de la machine et une case pleine de l'inventaire : objets dans les deux
        // sens, impossible à sauvegarder sans risque de copie : refusé
        if (type == ClickType.SWAP && index >= 0 && index < ENTREES + SORTIES && slots.get(index).hasItem()) {
            ItemStack barre = bouton == 40 ? p.getOffhandItem() : (bouton >= 0 && bouton < 9 ? p.getInventory().getItem(bouton) : ItemStack.EMPTY);
            if (!barre.isEmpty()) return;
        }
        // 02/11 (audit point 20) : au plus 4 clics qui changent la machine par seconde et par joueur (chacun sauvegarde le
        // joueur et la machine sur le fil serveur : Maj + clic en boucle faisait monter le tick) ; au-delà, le clic est
        // refusé et l'écran du joueur revient à l'état réel
        long t = System.currentTimeMillis();
        if (t - fenetre >= 1000 || t < fenetre) {
            fenetre = t;
            clics = 0;
        }
        if (clics >= CLICS_PAR_SECONDE && toucheMachine(index, type)) {
            sendAllDataToRemote();
            return;
        }
        machine.avantClic();
        boolean change = false;
        try {
            super.clicked(index, bouton, type, p);
        } finally {
            change = machine.apresClic(joueur);
        }
        if (change) clics++;
    }

    private static final int CLICS_PAR_SECONDE = 4;
    private long fenetre;
    private int clics;

    /** Ce clic peut changer le contenu de la machine (case de la machine, Maj + clic, double clic, glisser). */
    private static boolean toucheMachine(int index, ClickType type) {
        if (index >= 0 && index < ENTREES + SORTIES) return true;
        return type == ClickType.QUICK_MOVE || type == ClickType.PICKUP_ALL || type == ClickType.QUICK_CRAFT;
    }

    @Override
    public boolean clickMenuButton(Player p, int bouton) {
        if (!machine.utilisable()) return false;
        machine.avantClic();
        try {
            return super.clickMenuButton(p, bouton);
        } finally {
            machine.apresClic(joueur);
        }
    }

    @Override
    public void broadcastChanges() {
        if (++tickApercu % 5 == 1) apercuSiChange();
        super.broadcastChanges();
    }

    /** Aperçu de toutes les cases non vides (machine et inventaire), renvoyé à l'écran quand leur contenu a changé. */
    private void apercuSiChange() {
        int sig = 1;
        for (int k = 0; k < slots.size(); k++) {
            ItemStack s = slots.get(k).getItem();
            sig = 31 * sig + (s.isEmpty() ? 0 : Objects.hash(ForgeRegistries.ITEMS.getKey(s.getItem()), s.getCount(), s.getTag()));
        }
        sig = 31 * sig + Recyclage.version;
        if (sig == signature) return;
        signature = sig;
        Map<String, Long> v = SaisonQuetes.ventes();
        Map<String, Long> valeurs = new HashMap<>();
        List<Reseau.ApercuCase> cases = new ArrayList<>();
        for (int k = 0; k < slots.size(); k++) {
            ItemStack s = slots.get(k).getItem();
            if (s.isEmpty() || estSortie(k)) continue;
            cases.add(Recyclage.apercu(k, Recyclage.verdict(s, v, valeurs, true)));
        }
        Reseau.envoyer(joueur, new Reseau.ApercuRecyclage(containerId, Recyclage.chance(true), Recyclage.maxPieces(), cases));
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        machine.vues.remove(this);
    }
}
