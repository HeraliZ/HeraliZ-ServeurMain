package fr.heraliz.wonder.pont;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Usine d'une entité coupée en deux (docs/METHODE_CLIENT_SERVEUR.md §5.1) : la classe commune (état synchronisé,
 * animations, ce que lit le rendu) et sa sous-classe serveur (IA, attaques, phases, butin, sauvegarde).
 * <p>
 * Le choix se fait AU CAS PAR CAS, selon le monde : dans un monde serveur (dédié ou intégré au solo), la version serveur si
 * la partie serveur l'a branchée ; dans le monde du client, TOUJOURS la version commune. Ainsi, même avec le jar -complet,
 * le client affiche exactement la même entité qu'un joueur qui n'a que le jar -client.
 * <pre>
 *    // commun (registre)
 *    public static final UsineEntite&lt;Mammoth&gt; MAMMOTH_USINE = new UsineEntite&lt;&gt;(Mammoth::new);
 *    ENTITIES.register("mammoth", () -&gt; EntityType.Builder.of(MAMMOTH_USINE, MobCategory.MONSTER)...build("mammoth"));
 *    // serveur (Partie.init)
 *    HeraliszMammoth.MAMMOTH_USINE.serveur(MammothServeur::new);
 * </pre>
 * Règle : partout dans le code serveur, on crée l'entité par son type ({@code TYPE.get().create(level)}), JAMAIS par
 * {@code new Mammoth(...)} (on obtiendrait la version neutre, sans IA). verif_split le signale.
 */
public final class UsineEntite<T extends Entity> implements EntityType.EntityFactory<T> {
   private final EntityType.EntityFactory<T> neutre;
   private volatile EntityType.EntityFactory<T> serveur;

   public UsineEntite(EntityType.EntityFactory<T> neutre) {
      this.neutre = neutre;
   }

   /** Branche la version serveur (appelé par la partie serveur, dans Partie.init). */
   public void serveur(EntityType.EntityFactory<T> fabrique) {
      this.serveur = fabrique;
   }

   public boolean serveurBranche() {
      return serveur != null;
   }

   @Override
   public T create(EntityType<T> type, Level level) {
      EntityType.EntityFactory<T> s = serveur;
      return s != null && !level.isClientSide ? s.create(type, level) : neutre.create(type, level);
   }
}
