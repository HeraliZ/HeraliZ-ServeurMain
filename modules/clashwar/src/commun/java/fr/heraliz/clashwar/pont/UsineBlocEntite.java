package fr.heraliz.clashwar.pont;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Usine d'une entité de bloc coupée en deux (docs/METHODE_CLIENT_SERVEUR.md §5.2) : la classe commune (données lues par
 * le rendu, synchronisation) et sa sous-classe serveur (rituels, salle, règles, sauvegarde des données serveur).
 * <p>
 * Minecraft crée une entité de bloc sans dire dans quel monde (constructeur (pos, état)) : le choix se fait donc selon la
 * PRÉSENCE de la partie serveur, pas selon le monde. Avec -complet, les entités de bloc du client sont aussi des versions
 * serveur : leur code doit donc rester sûr côté client (le ticker serveur n'est donné qu'au monde serveur, et les méthodes
 * serveur vérifient {@code level.isClientSide}), comme dans le mod d'origine.
 * <pre>
 *    // commun
 *    public static final UsineBlocEntite&lt;HornAltarBlockEntity&gt; ALTAR_USINE = new UsineBlocEntite&lt;&gt;(HornAltarBlockEntity::new);
 *    BLOCK_ENTITIES.register("horn_altar", () -&gt; BlockEntityType.Builder.of(ALTAR_USINE, HORN_ALTAR.get()).build(null));
 *    // dans le bloc : newBlockEntity(pos, state) { return ModBlocks.ALTAR_BE.get().create(pos, state); }   (jamais new)
 *    // serveur (Partie.init)
 *    ModBlocks.ALTAR_USINE.serveur(HornAltarBlockEntityServeur::new);
 * </pre>
 */
public final class UsineBlocEntite<T extends BlockEntity> implements BlockEntityType.BlockEntitySupplier<T> {
   private final BlockEntityType.BlockEntitySupplier<T> neutre;
   private volatile BlockEntityType.BlockEntitySupplier<T> serveur;

   public UsineBlocEntite(BlockEntityType.BlockEntitySupplier<T> neutre) {
      this.neutre = neutre;
   }

   /** Branche la version serveur (appelé par la partie serveur, dans Partie.init). */
   public void serveur(BlockEntityType.BlockEntitySupplier<T> fabrique) {
      this.serveur = fabrique;
   }

   public boolean serveurBranche() {
      return serveur != null;
   }

   @Override
   public T create(BlockPos pos, BlockState state) {
      BlockEntityType.BlockEntitySupplier<T> s = serveur;
      return s != null ? s.create(pos, state) : neutre.create(pos, state);
   }
}
