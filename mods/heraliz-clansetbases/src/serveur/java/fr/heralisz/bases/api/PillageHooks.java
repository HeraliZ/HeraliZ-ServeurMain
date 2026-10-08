package fr.heralisz.bases.api;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.PillableVue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * POINT D'EXTENSION du module pillage (BASES_SPEC §4, §5) : le coeur appelle ces methodes pour tout ce qui concerne un
 * joueur NON membre du clan proprietaire (un « visiteur ») dans une base, et pour les commandes / listes de pillage.
 * Le module installe son implementation par {@link BasesApi#pillage(PillageHooks)} depuis
 * {@code fr.heralisz.bases.pillage.PillageModule#init}. Par defaut ({@link #AUCUN}) : aucun visiteur n'a de droit,
 * aucun pillage n'est possible. Toutes les methodes sont appelees sur le fil serveur.
 * <p>Le coeur garde la main sur : la coque et tout ce qui est hors du volume constructible (jamais modifiable), les
 * interdits de la dimension, les membres (permissions BASE_*), les admins (op en creatif / spectateur).
 */
public interface PillageHooks {
    /** Usage d'un bloc par clic droit. */
    enum Usage {
        /** Bloc avec inventaire (coffre, four, baril, conteneur modde...). */
        CONTENEUR,
        /** Porte, trappe, portillon, levier, bouton. */
        PORTE,
        /** Tout autre bloc interactif (table de craft, lit...). */
        AUTRE
    }

    PillageHooks AUCUN = new PillageHooks() {
    };

    /** Vrai si ce visiteur a le droit d'etre dans cette base maintenant (pillard actif). Sinon il est renvoye. */
    default boolean estPillard(ServerPlayer p, Base base) {
        return false;
    }

    /** Le visiteur casse un bloc DU VOLUME CONSTRUCTIBLE (jamais la coque : deja refuse par le coeur). */
    default boolean peutCasser(ServerPlayer p, Base base, BlockPos pos, BlockState etat) {
        return false;
    }

    /** Le visiteur pose un bloc dans le volume constructible. */
    default boolean peutPoser(ServerPlayer p, Base base, BlockPos pos, BlockState etat) {
        return false;
    }

    /** Le visiteur fait un clic droit sur un bloc (n'importe ou dans la base). */
    default boolean peutUtiliser(ServerPlayer p, Base base, BlockPos pos, BlockState etat, Usage usage) {
        return false;
    }

    /**
     * {@code /base piller [tag]} ou bouton de l'ecran PILLER : {@code tag} null = ouvrir l'ecran (le coeur a deja envoye
     * la liste) ; sinon lancer le pillage (verifications, puis {@link BasesApi#programmerEntree}).
     */
    default void piller(ServerPlayer p, @Nullable String tag) {
        if (tag != null) BasesApi.erreur(p, "msg.heralisz_bases.pillage_indisponible");
    }

    /** Liste de l'ecran PILLER : {@code parDefaut} est calculee par le coeur (toutes les bases pretes des autres clans). */
    default List<PillableVue> pillables(ServerPlayer p, List<PillableVue> parDefaut) {
        return parDefaut;
    }

    /** Tags des clans qui pillent cette base en ce moment (onglet BASE : « Attaquee par [TAG] »). */
    default List<String> attaquants(Base base) {
        return List.of();
    }

    /** Nombre de pillards dans la base. */
    default int pillards(Base base) {
        return 0;
    }

    /** Fin du pillage de ce joueur (epoch ms) pour le HUD « PILLAGE · 1 vie · fin a 23 h » ; 0 = fin de la fenetre. */
    default long finPillageMs(ServerPlayer p) {
        return 0;
    }

    /**
     * Un joueur quitte une base (sortie, ejection, deconnexion n'est PAS comprise) : le module clot son pillage s'il en
     * avait un. {@code base} peut etre null si la base n'existe plus.
     */
    default void sortie(ServerPlayer p, @Nullable Base base) {
    }

    /** Ajoute des sous-commandes a {@code /base} et a {@code /base admin} (ex. {@code coups <x y z>}). */
    default void commandes(LiteralArgumentBuilder<CommandSourceStack> base, LiteralArgumentBuilder<CommandSourceStack> admin) {
    }
}
