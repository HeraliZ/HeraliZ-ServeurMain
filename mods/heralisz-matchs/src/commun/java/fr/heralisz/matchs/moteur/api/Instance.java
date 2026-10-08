package fr.heralisz.matchs.moteur.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exemplaire pose d'une carte dans sa dimension (grille espacee de {@link Arenes#ESPACEMENT} blocs). Obtenu par
 * {@link Arenes#reserver(String)}, rendu par {@link Arenes#liberer(Instance)} ; les instances restent posees d'une
 * partie a l'autre (pas de re-pose).
 * <p>La pose se fait par tranches (20 000 blocs par tick) : tant que {@link #prete()} est faux, ne teleporter
 * personne ; utiliser {@link #quandPrete(Runnable)}. Toutes les coordonnees rendues ici sont <b>absolues</b>.
 */
public final class Instance {
    private final String id;
    private final Carte carte;
    private final ServerLevel niveau;
    private final BlockPos origine;
    private final int index;
    private final AABB bornes;
    private final Map<String, List<Pose>> cache = new HashMap<>();
    private final List<Runnable> enAttente = new ArrayList<>();
    private boolean prete;
    @Nullable
    private String occupant;

    /**
     * Reserve au moteur ({@link Arenes}).
     *
     * @param carte   carte posee
     * @param niveau  niveau de la dimension de la carte
     * @param origine coin minimal de la structure posee
     * @param index   rang de la case dans la grille (identifiant stable dans le registre des instances)
     */
    public Instance(Carte carte, ServerLevel niveau, BlockPos origine, int index) {
        this.id = carte.id() + "#" + index;
        this.carte = carte;
        this.niveau = niveau;
        this.origine = origine.immutable();
        this.index = index;
        this.bornes = carte.bornes().move(origine);
    }

    /** « carte#index », unique et stable. */
    public String id() {
        return id;
    }

    public Carte carte() {
        return carte;
    }

    public ServerLevel niveau() {
        return niveau;
    }

    /** Coin minimal de la structure posee. */
    public BlockPos origine() {
        return origine;
    }

    public int index() {
        return index;
    }

    /** Bornes jouables absolues (sortie = retour ou elimination selon le mode). */
    public AABB bornes() {
        return bornes;
    }

    /** Vrai si le point (absolu) est dans les bornes. */
    public boolean contient(double x, double y, double z) {
        return bornes.contains(x, y, z);
    }

    /** Poses absolues du point nomme (liste vide si la carte ne le declare pas). Mise en cache, non modifiable. */
    public List<Pose> points(String nom) {
        List<Pose> l = cache.get(nom);
        if (l == null) {
            List<Pose> rel = carte.pointsRelatifs(nom);
            List<Pose> abs = new ArrayList<>(rel.size());
            for (Pose p : rel) abs.add(p.decaler(origine));
            l = List.copyOf(abs);
            cache.put(nom, l);
        }
        return l;
    }

    /** Premiere pose du point nomme, ou null. */
    @Nullable
    public Pose point(String nom) {
        List<Pose> l = points(nom);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Vrai quand la structure est entierement posee et l'instance nettoyee. */
    public boolean prete() {
        return prete;
    }

    /** Execute l'action quand l'instance est prete (tout de suite si elle l'est deja). Fil serveur. */
    public void quandPrete(Runnable action) {
        if (prete) action.run();
        else enAttente.add(action);
    }

    /** Reserve au moteur : marque l'instance prete et lance les actions en attente. */
    public void marquerPrete() {
        prete = true;
        if (enAttente.isEmpty()) return;
        List<Runnable> l = new ArrayList<>(enAttente);
        enAttente.clear();
        for (Runnable r : l) r.run();
    }

    /** Id de la partie qui occupe l'instance, ou null si elle est libre. */
    @Nullable
    public String occupant() {
        return occupant;
    }

    /** Reserve au moteur : null = libre. */
    public void occuper(@Nullable String partieId) {
        this.occupant = partieId;
    }

    public boolean libre() {
        return occupant == null;
    }

    @Override
    public String toString() {
        return "Instance[" + id + " @ " + niveau.dimension().location() + " " + origine.toShortString() + (prete ? "" : " (pose)") + "]";
    }
}
