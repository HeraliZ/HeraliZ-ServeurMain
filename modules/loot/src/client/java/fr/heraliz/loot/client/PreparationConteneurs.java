package fr.heraliz.loot.client;

import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.client.rendu.conteneurs.Allegement;
import fr.heraliz.loot.client.rendu.conteneurs.Conteneurs;
import fr.heraliz.loot.client.rendu.mesh.Mesh;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maillages des conteneurs T2-T6 fabriqués en fond pendant le chargement des ressources (audit des images par seconde du
 * 19/11, point 6 ; T-perf-7 du 26/10).
 * <p>
 * Avant, chacun des 246 maillages (maillage du modèle, occlusion ambiante cuite : grille de voxels et 64 rayons par
 * sommet, salissure, version allégée) était fait à la première vue du conteneur, sur les fils de construction des
 * morceaux de carte d'Embeddium (un ou deux sur un petit processeur) : ≈ 4,3 s de calcul à l'entrée dans la première
 * ville, terrain en retard. Maintenant ils sont tous faits une fois par lancement, en fond, dès le premier chargement des
 * modèles (écran de chargement) : quelques fils de basse priorité, la moitié des cœurs au plus (4). Même code, même
 * résultat ({@link Conteneurs#maillages}, {@link Allegement#de} : déjà sûrs entre fils, gardés pour la partie). Un
 * conteneur demandé par le terrain avant la fin est fait à la demande, comme avant. Les vues (faces de bloc, liées à la
 * grande texture des blocs) restent faites à la première vue : c'est rapide.
 */
final class PreparationConteneurs {
    private static volatile boolean lance;

    private PreparationConteneurs() {}

    /** Une seule fois par lancement (les maillages ne dépendent pas des ressources : jamais refaits ensuite). */
    static void lancer() {
        if (lance) return;
        lance = true;
        List<String> noms = new ArrayList<>();
        for (String nom : Conteneurs.noms()) noms.add(nom);
        if (noms.isEmpty()) return;
        int fils = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        AtomicInteger numero = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(fils, r -> {
            Thread t = new Thread(r, "HeraliZ-conteneurs-" + numero.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        long debut = System.nanoTime();
        AtomicInteger reste = new AtomicInteger(noms.size());
        for (String nom : noms) {
            pool.execute(() -> {
                try {
                    Mesh[] m = Conteneurs.maillages(nom);
                    if (m != null) Allegement.de(nom, m[0]);
                } catch (RuntimeException e) {
                    // fait à la demande par le terrain, comme avant
                }
                if (reste.decrementAndGet() == 0) {
                    pool.shutdown();
                    HeralizLoot.LOG.info("[loot] {} maillages de conteneurs prêts en fond ({} fils, {} ms)", noms.size(), fils,
                            (System.nanoTime() - debut) / 1_000_000);
                }
            });
        }
    }
}
