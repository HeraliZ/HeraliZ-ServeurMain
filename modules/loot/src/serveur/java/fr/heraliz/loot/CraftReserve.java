package fr.heraliz.loot;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Établi et artisanat réservés au grade Fondateur (14/11, choix du propriétaire : « bloquer l'accès à la table de craft à
 * tout le monde sauf fonda », et plus d'artisanat dans l'inventaire). Réglage {@code craft_fondateurs_seulement} de
 * {@code [postes_vanilla]} dans {@code heraliz_loot-server.toml} (vrai par défaut ; faux : règles d'avant).
 * <ul>
 *   <li><b>Établi</b> : seul un fondateur l'ouvre ; tous les autres, créatif et staff compris, lisent « Ce poste est hors
 *   service. » ({@link ConteneursVanilla}). Avant : hors service pour les joueurs, libre en créatif et pour le staff.</li>
 *   <li><b>Fabrication</b> : pour tous les autres, le résultat de la grille (2×2 de l'inventaire comme 3×3 de l'établi) reste
 *   vide, décidé par le serveur ({@code fr.heraliz.loot.serveur.mixin.CraftReserveMixin}) : un jeu modifié qui montrerait
 *   encore la grille ne fabrique rien.</li>
 *   <li>Audit du 15/11 (duplication n° 2) : aussi la grille 2×2 de l'inventaire réel, celui de Curios, qui calcule sa
 *   recette lui-même ({@code CraftReserveCuriosMixin}) ; et garde-fou sur toute case résultat de Minecraft
 *   ({@code ResultSlot}), qui ne se prend plus ({@code CraftResultatMixin}). Le livre de recettes ne fait que poser les
 *   objets dans la grille : le résultat reste vide. Table d'armurier (TaCZ), recycleur, marchands : pas des grilles de
 *   Minecraft, inchangés ; les autres postes de Minecraft sont déjà hors service ({@link ConteneursVanilla}).</li>
 * </ul>
 * Fondateur : grade du staff « fondateur » de HeraliZ Grades, connecté à son compte (AuthMe), lu par réflexion
 * ({@code GradesApi.fondateur}). Sans HeraliZ Grades (serveur d'essai) : les opérateurs de niveau 4. Fil serveur seulement.
 */
public final class CraftReserve {
    private static Method fondateur;
    private static boolean cherche;

    private CraftReserve() {
    }

    /** Règle en service ? (faux tant que la configuration n'est pas chargée) */
    public static boolean active() {
        return ConfigLoot.SPEC.isLoaded() && ConfigLoot.POSTES_CRAFT_FONDATEURS.get();
    }

    static boolean etabli(BlockState st) {
        return st.is(Blocks.CRAFTING_TABLE);
    }

    /** Ce joueur peut-il fabriquer et ouvrir l'établi ? Vrai pour tous quand la règle est coupée. */
    public static boolean permis(ServerPlayer sp) {
        if (!active()) return true;
        Method m = methode();
        if (m == null) return sp.hasPermissions(4);
        try {
            return Boolean.TRUE.equals(m.invoke(null, sp));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Method methode() {
        if (!cherche) {
            cherche = true;
            try {
                fondateur = Class.forName("fr.heraliz.grades.serveur.GradesApi").getMethod("fondateur", ServerPlayer.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                fondateur = null;
            }
        }
        return fondateur;
    }
}
