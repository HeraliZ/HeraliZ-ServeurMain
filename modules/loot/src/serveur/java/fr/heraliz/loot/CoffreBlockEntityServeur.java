package fr.heraliz.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

/**
 * Partie SERVEUR de {@link CoffreBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new CoffreBlockEntity(...) » côté serveur.
 */
public class CoffreBlockEntityServeur extends CoffreBlockEntity {
   public CoffreBlockEntityServeur(BlockPos pos, BlockState st) {
      super(pos, st);
      java.util.Arrays.fill(rarete, (byte) -1);
   }

    /** Coffre vidé : il disparaît au plus tard 30 s après. Joueur encore dedans à l'heure : on attend au plus 60 s. */
    static final int SURSIS_VIDE = 600, SURSIS_SPECTATEUR = 1200;

    final NonNullList<ItemStack> objets = NonNullList.withSize(TAILLE, ItemStack.EMPTY);
    final byte[] rarete = new byte[TAILLE];
    final boolean[] identifie = new boolean[TAILLE];
    boolean genere;
    int courant = -1, progres;
    final Set<UUID> spectateurs = new HashSet<>();
    private final Map<UUID, Boolean> attente = new HashMap<>();
    /** Coffre temporaire : heure de disparition (-1 : permanent, posé à la main) et point d'apparition d'origine. */
    long expire = -1;
    String point = "";
    /**
     * Coffre d'un événement de HeraliZ Événements (« helico#12 », 25/10), "" sinon : à sa première ouverture, l'objet
     * d'événement à revendre y est posé et l'événement est prévenu (EvenementsApi, par réflexion : sans ce mod, rien).
     */
    String evenement = "";

    /** Fait apparaître le coffre (posé juste avant par PointsT7) pour presence ticks, rattaché au point (ou ""). */
    public void apparaitre(int presence, String point) {
        long now = level.getGameTime();
        etat = APPARITION;
        debut = now;
        expire = now + dureeApparition() + presence;
        this.point = point == null ? "" : point;
        genere = false;
        synchroniser();
    }

    private boolean vide() {
        for (ItemStack st : objets) if (!st.isEmpty()) return false;
        return true;
    }

    // ------------------------------------------------------------------------------------------ loot
    void generer() {
        generer(0);
    }

    /** Tire le contenu du coffre, avec le bonus de loot (%) du joueur qui l'ouvre le premier. */
    void generer(int bonus) {
        RandomSource r = level.random;
        TablesCoffres.Table t = TablesCoffres.table(type().nom);
        java.util.Arrays.fill(rarete, (byte) -1);
        java.util.Arrays.fill(identifie, false);
        Collections.fill(objets, ItemStack.EMPTY);
        if (t == null || t.objets().isEmpty()) {
            HeralizLoot.LOG.warn("Aucune table de loot pour {}", type().nom);
            genere = true;
            return;
        }
        List<Integer> cases = new ArrayList<>();
        for (int i = 0; i < TAILLE; i++) {
            cases.add(i);
        }
        Collections.shuffle(cases, new java.util.Random(r.nextLong()));
        int n = TablesCoffres.nombre(t, t.min(), t.max(), r);
        java.util.Set<String> deja = new java.util.HashSet<>();
        for (int k = 0; k < Math.min(n, TAILLE); k++) {
            ItemStack st = k == 0 ? TablesCoffres.tirerPlein(t, r, bonus, deja) : TablesCoffres.tirerUnique(t, r, bonus, deja);
            int i = cases.get(k);
            objets.set(i, st);
            Rarete ra = Rarete.de(st);
            rarete[i] = (byte) (ra == null ? 0 : ra.ordinal());
        }
        genere = true;
        courant = -1;
        progres = 0;
        setChanged();
    }

    /** Remet le coffre à neuf (commande de test) : nouveau loot, fermé. */
    public void reinitialiser() {
        genere = false;
        etat = FERME;
        debut = level.getGameTime();
        spectateurs.clear();
        attente.clear();
        generer();
        synchroniser();
    }

    int restants() {
        int n = 0;
        for (int i = 0; i < TAILLE; i++) {
            if (!objets.get(i).isEmpty() && !identifie[i]) {
                n++;
            }
        }
        return n;
    }

    int prochain() {
        for (int i = 0; i < TAILLE; i++) {
            if (!objets.get(i).isEmpty() && !identifie[i]) {
                return i;
            }
        }
        return -1;
    }

    int duree(int i) {
        return i < 0 || rarete[i] < 0 ? 20 : Rarete.values()[rarete[i]].duree;
    }

    // ------------------------------------------------------------------------------------------ ouverture / fermeture
    @Override
    public void ouvrir(ServerPlayer p) {
        if (etat == APPARITION || etat == DISPARITION) {
            return;
        }
        if (!genere) {
            int bonus = BonusLoot.pourcent(p);
            generer(bonus);
            BonusLoot.annoncer(p, bonus);
            // coffre d'événement T7 : points une fois, au premier qui l'ouvre ; coffre d'un événement du Directeur (épave
            // de l'hélicoptère) : aucun, ses points viennent de l'objet d'événement à revendre (choix du propriétaire)
            if (evenement.isEmpty()) {
                SaisonLoot.conteneur(p, 7);
                if (level instanceof net.minecraft.server.level.ServerLevel sl) EvenementsLoot.fouille(p, sl, worldPosition, 7, "");
            } else {
                prevenirEvenement(p);
            }
        }
        long now = level.getGameTime();
        switch (etat) {
            case OUVERT -> ouvrirEcran(p);
            case OUVERTURE -> attente.put(p.getUUID(), true);
            default -> {
                float t = etat == FERMETURE ? tempsAnimation(now, 0) : 0;
                etat = OUVERTURE;
                debut = now - (long) t;
                attente.put(p.getUUID(), true);
                synchroniser();
            }
        }
    }

    private static java.lang.reflect.Method coffreOuvert, objetDuCoffre;
    private static boolean evenementsCherche;

    /**
     * Première ouverture du coffre d'un événement : l'objet d'événement (numéroté, signé) dans une case vide (coffre plein :
     * à la place de la dernière), puis l'événement est prévenu ({@code fr.heraliz.evenements.api.EvenementsApi}), une fois.
     */
    private void prevenirEvenement(ServerPlayer p) {
        String ev = evenement;
        evenement = "";   // une seule fois, même si l'appel échoue
        setChanged();
        if (!evenementsCherche) {
            evenementsCherche = true;
            try {
                Class<?> api = Class.forName("fr.heraliz.evenements.api.EvenementsApi");
                coffreOuvert = api.getMethod("coffreOuvert", ServerPlayer.class, String.class);
                objetDuCoffre = api.getMethod("objetDuCoffre", String.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                coffreOuvert = null;
                objetDuCoffre = null;
            }
        }
        if (coffreOuvert == null) return;
        try {
            if (objetDuCoffre != null && objetDuCoffre.invoke(null, ev) instanceof ItemStack obj && !obj.isEmpty()) {
                int libre = TAILLE - 1;
                for (int i = 0; i < TAILLE; i++) {
                    if (objets.get(i).isEmpty()) {
                        libre = i;
                        break;
                    }
                }
                objets.set(libre, obj);
                Rarete ra = Rarete.de(obj);
                rarete[libre] = (byte) (ra == null ? 0 : ra.ordinal());
                identifie[libre] = false;
            }
            coffreOuvert.invoke(null, p, ev);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizLoot.LOG.warn("Coffre de l'evenement {} : ouverture non transmise ({})", ev, e.toString());
        }
    }

    private void ouvrirEcran(ServerPlayer p) {
        spectateurs.add(p.getUUID());
        NetworkHooks.openScreen(p, this, buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    boolean estIdentifie(int i) {
        return identifie[i];
    }

    @Override
    public void fermer(Player p) {
        spectateurs.remove(p.getUUID());
    }

    @Override
    protected void tickLogique(Level l, BlockPos pos, BlockState st) {
        tickOrigine(l, pos, st, this);
    }

    /** Corps d'origine de CoffreBlockEntity.tickServeur, tel quel. */
    static void tickOrigine(Level l, BlockPos pos, BlockState st, CoffreBlockEntityServeur be) {
        long now = l.getGameTime();
        TypeCoffre t = be.type();
        if (be.etat == APPARITION) {
            if (now - be.debut >= be.dureeApparition()) {
                be.etat = FERME;
                be.debut = now;
                be.synchroniser();
            }
            return;
        }
        if (be.etat == DISPARITION) {
            if (now - be.debut >= DUREE_DISPARITION) {
                PointsT7.libere(l, be.point, now);
                l.removeBlock(pos, false);
            }
            return;
        }
        if (be.expire >= 0) {
            if (be.genere && be.vide()) be.expire = Math.min(be.expire, now + SURSIS_VIDE);
            boolean occupe = !be.spectateurs.isEmpty() || !be.attente.isEmpty();
            if (now >= be.expire + SURSIS_SPECTATEUR && occupe) {
                for (UUID id : new ArrayList<>(be.spectateurs)) {
                    if (l.getPlayerByUUID(id) instanceof ServerPlayer sp) sp.closeContainer();
                }
                be.spectateurs.clear();
                be.attente.clear();
                occupe = false;
                be.etat = FERME;
            }
            if (now >= be.expire && !occupe && be.etat == FERME) {
                be.etat = DISPARITION;
                be.debut = now;
                be.synchroniser();
                return;
            }
        }
        if (be.etat == OUVERTURE && now - be.debut >= t.ouverture) {
            be.etat = OUVERT;
            be.synchroniser();
            for (UUID id : new ArrayList<>(be.attente.keySet())) {
                if (l.getPlayerByUUID(id) instanceof ServerPlayer sp && sp.distanceToSqr(pos.getCenter()) < 64) {
                    be.ouvrirEcran(sp);
                }
            }
            be.attente.clear();
        }
        // spectateurs partis sans fermer proprement (déconnexion, mort...)
        be.spectateurs.removeIf(id -> {
            Player p = l.getPlayerByUUID(id);
            return p == null || !(p.containerMenu instanceof CoffreMenu m) || m.coffre != be;
        });
        if (!be.spectateurs.isEmpty()) {
            if (be.courant < 0 || be.identifie[be.courant] || be.objets.get(be.courant).isEmpty()) {
                be.courant = be.prochain();
                be.progres = 0;
            }
            if (be.courant >= 0 && ++be.progres >= be.duree(be.courant)) {
                be.identifie[be.courant] = true;
                be.courant = be.prochain();
                be.progres = 0;
                be.setChanged();
            }
        } else {
            be.progres = 0;                                             // personne ne regarde : l'objet en cours repart de zéro
        }
        if (be.etat == OUVERT && be.spectateurs.isEmpty() && be.attente.isEmpty()) {
            be.etat = FERMETURE;
            be.debut = now;
            be.synchroniser();
        } else if (be.etat == FERMETURE && now - be.debut >= t.fermeture()) {
            be.etat = FERME;
            be.synchroniser();
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new CoffreMenuServeur(id, inv, this);
    }

    // ------------------------------------------------------------------------------------------ sauvegarde / synchro
    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ContainerHelper.saveAllItems(t, objets);
        t.putByteArray("Rarete", rarete);
        byte[] id = new byte[TAILLE];
        for (int i = 0; i < TAILLE; i++) {
            id[i] = (byte) (identifie[i] ? 1 : 0);
        }
        t.putByteArray("Identifie", id);
        t.putBoolean("Genere", genere);
        t.putLong("Expire", expire);
        t.putString("Point", point);
        t.putString("Evenement", evenement);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        if (t.contains("Rarete")) {                                     // sauvegarde complète (serveur)
            Collections.fill(objets, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(t, objets);
            byte[] ra = t.getByteArray("Rarete"), id = t.getByteArray("Identifie");
            for (int i = 0; i < TAILLE; i++) {
                rarete[i] = i < ra.length ? ra[i] : -1;
                identifie[i] = i < id.length && id[i] != 0;
            }
            genere = t.getBoolean("Genere");
            expire = t.contains("Expire") ? t.getLong("Expire") : -1;
            point = t.getString("Point");
            evenement = t.getString("Evenement");
        }
        if (t.contains("Etat")) {                                       // synchro de l'animation (client)
            etat = t.getInt("Etat");
            debut = t.getLong("Debut");
        }
    }
}
