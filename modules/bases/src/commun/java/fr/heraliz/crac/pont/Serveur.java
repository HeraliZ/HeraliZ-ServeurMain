package fr.heraliz.crac.pont;

import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ce que les blocs, entités et mixins communs de CRac demandent à la logique serveur (claims, structures, explosifs,
 * protections : src/serveur). Branché par serveur/PartieServeur (docs/METHODE_CLIENT_SERVEUR.md, piège n°17). Sans
 * partie serveur (jar -client en solo), chaque appel a un résultat neutre : rien ne se passe, rien n'est protégé.
 */
public final class Serveur {
    private Serveur() {
    }

    public interface Moteur {
        /** Clic droit sur un Claim Block (corps serveur d'origine de ClaimBlock.use). */
        InteractionResult claimUtiliser(ClaimBlock bloc, BlockState state, ServerLevel sl, BlockPos pos, ServerPlayer sp, InteractionHand hand);

        /** Claim Block retiré : son claim disparaît (corps serveur d'origine de ClaimBlock.onRemove). */
        void claimRetire(ServerLevel sl, BlockPos pos);

        /** Le joueur peut-il ouvrir le stock d'entretien de ce claim (droits du groupe / du clan) ? */
        boolean claimAcces(ClaimBlockEntity be, ServerLevel sl, Player player);

        /** Clic droit sur une pièce de structure (StructureService.onUse, côté serveur). */
        InteractionResult structureUtiliser(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit);

        /** Le bloc sous une colonne de fondation a changé : son sol est vérifié au tick suivant. */
        void structureSol(Level level, BlockPos pos);

        /** Un bloc de structure a disparu. */
        void structureDisparue(Level level, BlockPos pos);

        /** Entité de bloc de structure chargée : vérification de son contenu. */
        void structureVerifier(Level level, BlockPos pos);

        /** Détonation d'un C4 : dégâts (collé à une pièce) ou destruction des blocs autour. */
        void c4(ServerLevel level, BlockPos pos, Vec3 hit, @Nullable Direction face);

        /**
         * Juste avant la détonation d'un C4 ou d'une charge collé en {@code pos} : prévient le lanceur si la base visée ne
         * se raide pas maintenant (hors Purge, base trop récente), sinon l'explosif partait sans effet ni message (25/10).
         */
        default void avantDetonation(ServerLevel level, BlockPos pos, @Nullable net.minecraft.world.entity.Entity lanceur) {
        }

        /** Détonation d'une charge explosive : dégâts aux pièces puis destruction des blocs autour. */
        void charge(ServerLevel level, BlockPos pos, Vec3 hit, Vec3 position, @Nullable Direction face);

        /**
         * Un objet adossé (armoire, sa case du haut, caisse en métal) a quitté {@code pos} : un mur qui lui avait laissé
         * la moitié intérieure de cette case la reprend au tick suivant (HeraliZ, 26/10).
         */
        default void objetAdosseRetire(ServerLevel sl, BlockPos pos) {
        }

        /** Le feu peut-il passer de {@code from} à {@code to} (claims) ? */
        boolean feuPeutPasser(ServerLevel sl, BlockPos from, BlockPos to);

        /** Un fluide peut-il couler de {@code from} à {@code to} (claims) ? */
        boolean fluidePeutEntrer(ServerLevel sl, BlockPos from, BlockPos to);
    }

    public static volatile Moteur MOTEUR;
}
