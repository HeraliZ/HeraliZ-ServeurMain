package fr.heralisz.matchs.pont;

import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.Instance;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.api.Partie;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Services serveur derrière les API communes de HeraliZ Matchs (moteur/api, zombies/jeu/api : mêmes noms et mêmes
 * signatures pour les autres modules et pour la réflexion). Branché par serveur/PartieServeur (docs/
 * METHODE_CLIENT_SERVEUR.md, piège n°17). Sans partie serveur, chaque API rend ce qu'elle rendait hors d'un serveur :
 * rien, faux, 0, null, -1.
 */
public final class Services {
    private Services() {
    }

    public interface Moteur {
        // ---- Arenes (moteur/arenes/ArenesService)
        @Nullable
        Instance arenesReserver(MinecraftServer s, String carte);

        void arenesLiberer(Instance instance);

        void arenesQuandLiberee(Consumer<Instance> ecouteur);

        int arenesNettoyer(Instance instance);

        // ---- ChoixClasse (moteur/kits/ChoixService)
        void choixOuvrir(Partie partie, Collection<ServerPlayer> joueurs, List<Kit> kits, Map<String, Integer> limites, int secondes,
                         Component titre, Consumer<Map<UUID, Kit>> quandFini);

        boolean choixEnCours(Partie partie);

        void choixAnnuler(Partie partie);

        @Nullable
        Kit choixDe(UUID joueur);

        // ---- Coffre (moteur/coffre/Coffres)
        @Nullable
        Coffre.Refus coffreRefus(ServerPlayer joueur);

        boolean coffreDeposer(ServerPlayer joueur, String raison);

        boolean coffreRendre(ServerPlayer joueur);

        // ---- CombatTag, MoteurConfig (moteur/combat/CombatService)
        void combatApresMarquage(ServerPlayer joueur);

        void combatApresEffacement(ServerPlayer joueur);

        void combatCommandesBloquees(List<? extends String> commandes);

        // ---- Equipement (moteur/kits/Distribution, moteur/compat/Compat)
        boolean equipementDonner(ServerPlayer joueur, Kit kit, String matchId);

        List<ItemStack> equipementPiles(Kit kit);

        int equipementPurgerCurios(ServerPlayer joueur, Predicate<ItemStack> filtre);

        // ---- MatchApi (moteur/combat/RestrictionsMatch)
        void restrictionsSynchroniser(ServerPlayer joueur);

        // ---- Spectateur (moteur/spectateur/Spectateurs)
        void spectateurSuivre(ServerPlayer joueur, Partie partie);

        void spectateurArreter(ServerPlayer joueur, boolean restaurerMode);

        boolean spectateurEst(UUID joueur);

        // ---- ZombiesApi, ConfigZombies (zombies/jeu/PartieZombies, TableBoite, Recettes)
        /** Partie de zombies (PartieZombies) de la créature ou du joueur, ou null. */
        @Nullable
        Partie zombiesPartieDe(Entity e);

        /** Partie de zombies (PartieZombies) dont l'instance contient ce bloc, ou null. */
        @Nullable
        Partie zombiesPartieA(Level niveau, BlockPos pos);

        int zombiesPlanches(Level niveau, BlockPos pos);

        boolean zombiesInteragir(ServerPlayer joueur);

        void zombiesInvaliderTables();
    }

    public static volatile Moteur MOTEUR;
}
