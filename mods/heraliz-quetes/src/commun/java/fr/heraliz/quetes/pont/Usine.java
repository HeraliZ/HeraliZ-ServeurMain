package fr.heraliz.quetes.pont;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Usine générique (blocs, objets, ou tout autre objet créé une fois au chargement) dont la partie serveur peut fournir une
 * sous-classe (docs/METHODE_CLIENT_SERVEUR.md §5.3). Choix selon la PRÉSENCE de la partie serveur. A = argument du
 * constructeur ({@code Void} et {@link #creer()} s'il n'y en a pas).
 * <pre>
 *    // commun
 *    public static final Usine&lt;Void, Block&gt; TRAP_USINE = Usine.simple(TrapBlock::new);
 *    BLOCKS.register("trap", TRAP_USINE::creer);
 *    // serveur (Partie.init) — avant les événements d'enregistrement
 *    ModBlocks.TRAP_USINE.serveur(a -&gt; new TrapBlockServeur());
 * </pre>
 * Le plus souvent, un bloc n'a pas besoin de sous-classe : sa méthode use() appelle une méthode neutre de son entité de bloc,
 * que la version serveur de l'entité de bloc remplace (§5.2). On ne passe par ici que si le bloc ou l'objet porte lui-même
 * de la logique.
 */
public final class Usine<A, T> implements Function<A, T> {
   private final Function<A, T> neutre;
   private volatile Function<A, ? extends T> serveur;

   public Usine(Function<A, T> neutre) {
      this.neutre = neutre;
   }

   /** Usine sans argument. */
   public static <T> Usine<Void, T> simple(Supplier<T> neutre) {
      return new Usine<>(a -> neutre.get());
   }

   /** Branche la version serveur (appelé par la partie serveur, dans Partie.init). */
   public void serveur(Function<A, ? extends T> fabrique) {
      this.serveur = fabrique;
   }

   public boolean serveurBranche() {
      return serveur != null;
   }

   @Override
   public T apply(A a) {
      Function<A, ? extends T> s = serveur;
      return s != null ? s.apply(a) : neutre.apply(a);
   }

   /** Pour une usine sans argument. */
   public T creer() {
      return apply(null);
   }
}
