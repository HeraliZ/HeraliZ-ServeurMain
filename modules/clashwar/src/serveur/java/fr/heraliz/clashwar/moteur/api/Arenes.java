package fr.heraliz.clashwar.moteur.api;

import fr.heraliz.clashwar.pont.Services;

import fr.heraliz.clashwar.HeralizClashWar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Cartes et instances d'arenes (MATCHS_SPEC §1.5). Dimensions : {@link HeralizClashWar#ARENES} (jour fixe) et
 * {@link HeralizClashWar#ZOMBIES} (nuit fixe), generateur vide. Fil serveur uniquement.
 * <p>Regles dans les dimensions d'arenes (appliquees par le moteur) : casse/pose refusees sauf
 * {@link Partie#peutCasser}/{@link Partie#peutPoser}, explosions sans degats aux blocs, sortie des bornes =
 * {@link Partie#surSortieBornes}, chute dans le vide = elimination.
 */
public final class Arenes {
    private Arenes() {
    }

    /** Pas de la grille des instances (blocs) : chaque instance occupe une case de 512 x 512. */
    public static final int ESPACEMENT = 512;
    /** Blocs poses par tick lors de la pose d'une instance. */
    public static final int BLOCS_PAR_TICK = 20_000;

    private static Map<String, Carte> cartes = Map.of();
    private static final List<Instance> INSTANCES = new ArrayList<>();

    /**
     * Prend une instance libre de cette carte, ou en pose une nouvelle (case suivante de la grille, registre en
     * SavedData). L'instance rendue est marquee occupee ; elle peut ne pas etre encore prete ({@link Instance#prete()},
     * {@link Instance#quandPrete(Runnable)}). Rend null si la carte est inconnue ou sa structure absente.
     * <p>Une nouvelle instance est posee a raison de {@link #BLOCS_PAR_TICK} blocs par tick (file commune a toutes les
     * poses) : compter quelques secondes pour une grande carte. Implementation : {@code moteur.arenes.ArenesService}.
     */
    @Nullable
    public static Instance reserver(String carte) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return null;
        Services.Moteur m = Services.MOTEUR;   // moteur/arenes/ArenesService (serveur)
        return m != null ? m.arenesReserver(s, carte) : null;
    }

    /**
     * Rend l'instance : nettoie les entites (objets au sol, grenades, corps, mobs), remet l'etat propre au mode (portes
     * zombies...) puis la marque libre. Sans effet si l'instance est deja libre.
     * <p>La remise en etat propre a un mode s'enregistre avec {@link #quandLiberee(Consumer)}.
     */
    public static void liberer(Instance instance) {
        Services.Moteur m = Services.MOTEUR;
        if (m != null) m.arenesLiberer(instance);
    }

    /**
     * Ajoute un ecouteur appele a chaque liberation d'instance (apres le nettoyage des entites, avant le marquage
     * libre) : remise en etat propre au mode (le mode filtre sur {@code instance.carte().type()}). A appeler une fois
     * (au chargement du module).
     */
    public static void quandLiberee(Consumer<Instance> ecouteur) {
        Services.Moteur m = Services.MOTEUR;
        if (m != null) m.arenesQuandLiberee(ecouteur);
    }

    /** Nettoie les entites de l'instance sans la liberer (entre deux manches) ; rend le nombre retire. */
    public static int nettoyer(Instance instance) {
        Services.Moteur m = Services.MOTEUR;
        return m != null ? m.arenesNettoyer(instance) : 0;
    }

    /** Carte par identifiant, ou null. */
    @Nullable
    public static Carte carte(String id) {
        return cartes.get(id);
    }

    /** Toutes les cartes chargees (non modifiable). */
    public static Collection<Carte> cartes() {
        return cartes.values();
    }

    /** Cartes d'un type ({@code Carte.TYPE_*}), dans l'ordre de chargement. */
    public static List<Carte> cartesDeType(String type) {
        List<Carte> l = new ArrayList<>();
        for (Carte c : cartes.values()) if (c.type().equals(type)) l.add(c);
        return l;
    }

    /** Instance qui contient ce point (dans ses bornes), ou null. */
    @Nullable
    public static Instance instanceA(Level niveau, double x, double y, double z) {
        for (Instance i : INSTANCES) {
            if (i.niveau() == niveau && i.contient(x, y, z)) return i;
        }
        return null;
    }

    /** Vrai si le niveau est une dimension de matchs (arenes ou zombies). */
    public static boolean estArene(Level niveau) {
        return niveau != null && (niveau.dimension() == HeralizClashWar.ARENES);
    }

    /** Instances connues (posees ou en cours de pose), non modifiable. */
    public static List<Instance> instances() {
        return Collections.unmodifiableList(INSTANCES);
    }

    // ------------------------------------------------------------------ reserve au moteur
    /** Reserve au moteur : remplace les cartes (rechargement des donnees). */
    public static void definirCartes(Map<String, Carte> nouvelles) {
        cartes = Collections.unmodifiableMap(new LinkedHashMap<>(nouvelles));
    }

    /** Reserve au moteur : inscrit une instance posee (chargement du registre ou nouvelle pose). */
    public static void inscrire(Instance instance) {
        INSTANCES.add(instance);
    }

    /** Reserve au moteur : oublie toutes les instances (arret du serveur). */
    public static void vider() {
        INSTANCES.clear();
    }
}
