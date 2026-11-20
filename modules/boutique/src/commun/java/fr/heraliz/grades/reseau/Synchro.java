package fr.heraliz.grades.reseau;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Liste tenue à jour chez les joueurs par différences (21/11, décision du propriétaire après l'audit réseau du 20/11).
 * Le serveur envoie la liste COMPLÈTE seulement au joueur qui en a besoin (arrivée, changement de vue, demande), puis à
 * tous seulement ce qui change : lignes ajoutées ou changées, lignes retirées, dans un envoi numéroté (le numéro suit
 * celui de l'envoi précédent de la même vue). Le joueur vérifie que chaque différence suit la précédente ; sinon il
 * ignore la suite et redemande la liste complète (au plus une demande toutes les 5 s) : une liste fausse ne reste pas.
 * <p>Pur Java (aucune classe de Minecraft) : vérifiée hors du jeu. La même classe sert aux Grades (liste Tab), aux Clans
 * (tags des joueurs) et à CaraCrea (liste vocale) : un fichier par mod, à garder identiques (au paquet près).</p>
 */
public final class Synchro {
    /** Délai entre deux demandes de liste complète d'un même joueur (côté joueur et côté serveur). */
    public static final long DELAI_DEMANDE_MS = 5000;

    private Synchro() {
    }

    /** Ce qui change dans une vue : clés retirées, lignes ajoutées ou changées (dans l'ordre de la liste). */
    public record Diff<K, V>(List<K> retires, Map<K, V> changes) {
        public boolean vide() {
            return retires.isEmpty() && changes.isEmpty();
        }
    }

    /**
     * Différence entre {@code avant} et {@code apres} pour une vue qui ne montre que les lignes {@code visible} : une
     * ligne visible nouvelle, devenue visible ou changée part en entier ; une ligne visible qui disparaît ou devient
     * invisible est retirée. Rien si la vue ne change pas.
     */
    public static <K, V> Diff<K, V> diff(Map<K, V> avant, Map<K, V> apres, Predicate<? super V> visible) {
        List<K> retires = new ArrayList<>();
        for (Map.Entry<K, V> e : avant.entrySet()) {
            if (!visible.test(e.getValue())) continue;
            V v = apres.get(e.getKey());
            if (v == null || !visible.test(v)) retires.add(e.getKey());
        }
        Map<K, V> changes = new LinkedHashMap<>();
        for (Map.Entry<K, V> e : apres.entrySet()) {
            V v = e.getValue();
            if (!visible.test(v)) continue;
            V a = avant.get(e.getKey());
            if (a == null || !visible.test(a) || !a.equals(v)) changes.put(e.getKey(), v);
        }
        return new Diff<>(retires, changes);
    }

    /** Les lignes visibles d'une vue, dans l'ordre de la liste (envoi complet). */
    public static <K, V> Map<K, V> vue(Map<K, V> table, Predicate<? super V> visible) {
        Map<K, V> m = new LinkedHashMap<>();
        for (Map.Entry<K, V> e : table.entrySet()) if (visible.test(e.getValue())) m.put(e.getKey(), e.getValue());
        return m;
    }

    /** Applique une différence reçue à la table du joueur. */
    public static <K, V> void appliquer(Map<K, V> table, Collection<K> retires, Map<K, V> changes) {
        for (K k : retires) table.remove(k);
        table.putAll(changes);
    }

    /**
     * Ce que fait le mod pour envoyer : mettre le paquet en forme (une seule fois par vue, gardé pour les joueurs
     * suivants du même envoi) et l'envoyer à ce joueur.
     */
    public interface Envoi<P, K, V> {
        /** Liste complète de la vue {@code vue} (numéro {@code version}) pour ce joueur. */
        void complet(P joueur, int vue, int version, Map<K, V> table);

        /** Différence de la vue {@code vue} (numéro {@code version}) pour ce joueur. */
        void difference(P joueur, int vue, int version, Diff<K, V> diff);
    }

    /**
     * Côté serveur : la dernière liste envoyée, le numéro de chaque vue et la vue qu'a chaque joueur. {@code J} : clé d'un
     * joueur (son UUID : un joueur qui réapparaît après sa mort reste le même joueur) ; {@code K}, {@code V} : clé et
     * ligne de la liste. Chaque vue ne montre que ses lignes (exemple : le staff voit tout, les autres pas les cachés).
     * Fil du serveur seulement.
     */
    public static final class Diffuseur<J, K, V> {
        private final List<Predicate<? super V>> vues;
        private final int[] versions;
        /** Vue de chaque joueur qui a la liste ; absent : liste complète au prochain envoi. */
        private final Map<J, Integer> vueDe = new HashMap<>();
        private final Demandes<J> demandes = new Demandes<>();
        private Map<K, V> envoye = Map.of();

        @SafeVarargs
        public Diffuseur(Predicate<? super V>... vues) {
            List<Predicate<? super V>> l = new ArrayList<>(vues.length);
            for (Predicate<? super V> v : vues) l.add(v);
            this.vues = List.copyOf(l);
            this.versions = new int[vues.length];
        }

        /** Ce joueur recevra la liste complète au prochain envoi (arrivée). */
        public void complet(J joueur) {
            vueDe.remove(joueur);
        }

        /** Une différence manquait chez ce joueur : vrai si sa demande est acceptée (une toutes les 5 s au plus). */
        public boolean demande(J joueur, long maintenantMs) {
            if (!demandes.accepter(joueur, maintenantMs)) return false;
            vueDe.remove(joueur);
            return true;
        }

        /** Départ du joueur. */
        public void depart(J joueur) {
            vueDe.remove(joueur);
            demandes.oublier(joueur);
        }

        /** Tout oublier (arrêt du serveur) : chacun recevra la liste complète. */
        public void vider() {
            envoye = Map.of();
            Arrays.fill(versions, 0);
            vueDe.clear();
            demandes.vider();
        }

        /** Numéro de la dernière liste de cette vue. */
        public int version(int vue) {
            return versions[vue];
        }

        /**
         * Publie la liste {@code table} : différence de chaque vue (son numéro + 1 si elle change), puis, joueur par joueur
         * (dans l'ordre de {@code joueurs}) : sa liste complète s'il ne l'a pas encore ou s'il a changé de vue, sinon la
         * différence de sa vue si elle n'est pas vide. Rien ne part si rien n'a changé.
         */
        public <P> void publier(Map<K, V> table, Iterable<? extends P> joueurs, Function<? super P, ? extends J> cle,
                                ToIntFunction<? super P> vueDuJoueur, Envoi<? super P, K, V> envoi) {
            List<Diff<K, V>> diffs = new ArrayList<>(vues.size());
            for (int v = 0; v < vues.size(); v++) {
                Diff<K, V> d = diff(envoye, table, vues.get(v));
                if (!d.vide()) versions[v]++;
                diffs.add(d);
            }
            envoye = table;
            List<Map<K, V>> completes = new ArrayList<>(vues.size());
            for (int v = 0; v < vues.size(); v++) completes.add(null);
            for (P p : joueurs) {
                int v = vueDuJoueur.applyAsInt(p);
                Integer avant = vueDe.put(cle.apply(p), v);
                if (avant == null || avant != v) {
                    Map<K, V> c = completes.get(v);
                    if (c == null) completes.set(v, c = vue(table, vues.get(v)));
                    envoi.complet(p, v, versions[v], c);
                } else if (!diffs.get(v).vide()) {
                    envoi.difference(p, v, versions[v], diffs.get(v));
                }
            }
        }
    }

    /** Côté joueur : numéro du dernier envoi reçu ; demande de la liste complète quand une différence manque. */
    public static final class Suite {
        private int version;
        private boolean aJour, demande;
        private long demandeA;

        /** Liste complète reçue : la liste est à jour, la suite repart de son numéro. */
        public void complet(int version) {
            this.version = version;
            aJour = true;
            demande = false;
        }

        /**
         * Vrai si cette différence suit la dernière reçue : elle est à appliquer. Faux si une différence manque (ou
         * avant toute liste complète) : la liste n'est plus à jour, tout est ignoré jusqu'à la prochaine liste complète.
         */
        public boolean suivante(int version) {
            if (aJour && version == this.version + 1) {
                this.version = version;
                return true;
            }
            aJour = false;
            return false;
        }

        /** Vrai s'il faut demander la liste complète maintenant : liste pas à jour et aucune demande depuis 5 s. */
        public boolean demander(long maintenantMs) {
            if (aJour || demande && maintenantMs - demandeA < DELAI_DEMANDE_MS) return false;
            demande = true;
            demandeA = maintenantMs;
            return true;
        }

        /** Départ du serveur : plus rien de valable. */
        public void oublier() {
            aJour = false;
            demande = false;
        }

        public boolean aJour() {
            return aJour;
        }

        public int version() {
            return version;
        }
    }

    /** Côté serveur : demandes de liste complète, au plus une toutes les 5 s par joueur (un jeu modifié n'en abuse pas). */
    public static final class Demandes<J> {
        private final Map<J, Long> derniere = new HashMap<>();

        public boolean accepter(J joueur, long maintenantMs) {
            Long d = derniere.get(joueur);
            if (d != null && maintenantMs - d < DELAI_DEMANDE_MS) return false;
            derniere.put(joueur, maintenantMs);
            return true;
        }

        public void oublier(J joueur) {
            derniere.remove(joueur);
        }

        public void vider() {
            derniere.clear();
        }
    }
}
