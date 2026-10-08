package fr.heralisz.tags.client;

import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.network.Packets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Etat client : tags debloques, roue (4 ids, "" = vide), tags actifs dans le monde (recus par TagS2C).
 * Tout est touche sur le thread de rendu (handlers consumerMainThread + ecrans).
 * Les tags actifs sont tenus dans une liste indexee (parcours sans allocation par le rendu) et un index par id.
 */
public final class ClientTags {
    private ClientTags() {
    }

    /**
     * Tag actif cote client : le paquet d'origine, l'heure de reception (horloge client, ms) qui sert de t=0 pour
     * l'apparition et le fondu, et l'objet de rendu (Decal cuit par le DecalBaker, null tant qu'il n'existe pas).
     */
    public static final class TagActif {
        public final Packets.TagS2C paquet;
        public final long recuLe;
        /** Objet de rendu (fr.heralisz.tags.client.render.Decal) ; null = pas encore cuit ou rien de visible. */
        public Object rendu;

        TagActif(Packets.TagS2C paquet, long recuLe) {
            this.paquet = paquet;
            this.recuLe = recuLe;
        }

        public long id() {
            return paquet.id();
        }

        /** Age en secondes depuis la reception. */
        public float age(long nowMs) {
            return (nowMs - recuLe) / 1000f;
        }
    }

    private static final Set<String> DEBLOQUES = new LinkedHashSet<>();
    private static final String[] ROUE = {"", "", "", ""};
    private static final Map<Long, TagActif> ACTIFS = new HashMap<>();
    private static final ArrayList<TagActif> LISTE = new ArrayList<>();
    private static final List<TagActif> LISTE_VUE = Collections.unmodifiableList(LISTE);
    /** Incremente a chaque changement de la liste des tags actifs ou de leur rendu (lots du DecalRenderer). */
    private static int version;
    /** Portee de pose annoncee par le serveur (EtatS2C) ; ≤ 0 tant qu'il ne l'a pas annoncee. */
    private static float porteeServeur = -1f;

    // ------------------------------------------------------------------ etat du joueur
    /** Portee du rayon de pose : celle du serveur (c'est elle qu'il verifie), sinon la config locale. */
    public static double portee() {
        return porteeServeur > 0 ? porteeServeur : fr.heralisz.tags.TagsConfig.PORTEE.get();
    }

    public static void porteeServeur(float portee) {
        float p = Float.isFinite(portee) ? portee : -1f;
        if (p != porteeServeur && p > 0) HeraliszTags.LOGGER.debug("[HeraliZ Tags] portee de pose du serveur : {} blocs", p);
        porteeServeur = p;
    }

    public static void etat(List<String> debloques, List<String> roue) {
        DEBLOQUES.clear();
        for (String s : debloques) if (TagCatalog.existe(s)) DEBLOQUES.add(s);
        for (int i = 0; i < ROUE.length; i++) {
            String s = i < roue.size() ? roue.get(i) : "";
            ROUE[i] = s != null && TagCatalog.existe(s) ? s : "";
        }
    }

    public static Set<String> debloques() {
        return Collections.unmodifiableSet(DEBLOQUES);
    }

    public static boolean possede(String id) {
        return DEBLOQUES.contains(id);
    }

    public static int nombreDebloques() {
        return DEBLOQUES.size();
    }

    /** Roue : 4 ids ("" = vide). Copie modifiable. */
    public static List<String> roue() {
        List<String> l = new ArrayList<>(4);
        Collections.addAll(l, ROUE);
        return l;
    }

    /** Id du quartier i ("" = vide). 0 = haut, puis sens horaire (droite, bas, gauche). */
    public static String roue(int i) {
        return i < 0 || i >= ROUE.length ? "" : ROUE[i];
    }

    /** Pose locale d'un quartier (optimiste) : a confirmer par RoueC2S, l'EtatS2C de retour fait foi. */
    public static void roueLocale(int i, String id) {
        if (i >= 0 && i < ROUE.length) ROUE[i] = id == null ? "" : id;
    }

    // ------------------------------------------------------------------ tags actifs
    /** Version de l'ensemble des tags actifs (change a chaque ajout, retrait, purge, vidage ou recuisson). */
    public static int version() {
        return version;
    }

    /** A appeler quand le rendu d'un tag change sans que la liste bouge (cuisson, recuisson). */
    public static void modifie() {
        version++;
    }

    public static TagActif ajouter(Packets.TagS2C p) {
        TagActif t = new TagActif(p, System.currentTimeMillis());
        TagActif ancien = ACTIFS.put(p.id(), t);
        if (ancien != null) {
            liberer(ancien);
            LISTE.remove(ancien);
        }
        LISTE.add(t);
        version++;
        return t;
    }

    public static void retirer(long id) {
        TagActif t = ACTIFS.remove(id);
        if (t != null) {
            liberer(t);
            LISTE.remove(t);
            version++;
        }
    }

    public static Collection<TagActif> actifs() {
        return LISTE_VUE;
    }

    /** Liste indexee des tags actifs (ordre d'arrivee) : parcours par index, sans allocation. */
    public static List<TagActif> liste() {
        return LISTE_VUE;
    }

    /** Oublie les tags expires (duree en secondes) ; appele par le rendu une ou deux fois par seconde. */
    public static void purger(float dureeS) {
        if (LISTE.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (int i = LISTE.size() - 1; i >= 0; i--) {
            TagActif t = LISTE.get(i);
            if (t.age(now) > dureeS) {
                liberer(t);
                LISTE.remove(i);
                ACTIFS.remove(t.id());
                version++;
            }
        }
    }

    /** Vide tout (changement de monde / deconnexion). */
    public static void vider() {
        for (int i = 0; i < LISTE.size(); i++) liberer(LISTE.get(i));
        LISTE.clear();
        ACTIFS.clear();
        version++;
    }

    /** Libere l'objet de rendu (Decal.close() → VertexBuffer.close()). */
    private static void liberer(TagActif t) {
        if (t.rendu instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception e) {
                HeraliszTags.LOGGER.warn("[HeraliZ Tags] liberation d'un tag impossible", e);
            }
        }
        t.rendu = null;
    }
}
