package fr.heraliz.clashwar.moteur.api;

import fr.heraliz.clashwar.pont.Services;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Coffre : sauvegarde complete du joueur sur disque avant un match, restauree apres (MATCHS_SPEC §1.1). Jamais en RAM
 * seulement : fichier {@code <serveur>/heraliz_clashwar/coffres/<uuid>.dat} (NbtIo compresse, ecrit en .tmp puis
 * ATOMIC_MOVE). Contenu : inventaire + selection, Curios, sante/absorption, faim, XP, effets, mode de jeu, dimension +
 * position + rotation, soif (ThirstWasTaken), copie brute {@code saveWithoutId} pour enquete.
 * <p>Ordre impose : refus eventuel, descente du vehicule, fermeture du conteneur ouvert, ecriture du sac, instantane
 * <b>sur disque</b>, puis
 * seulement vidage (inventaire, Curios, effets), soif pleine et figee, cooldowns heralisz_tactical a zero.
 * Restauration : vidage, rechargement, soif, teleportation, suppression du fichier <b>apres</b> sauvegarde du joueur.
 * Au login : depot present et aucune partie active pour ce joueur = restauration (reprise apres crash).
 * Fil serveur uniquement.
 */
public final class Coffre {
    private Coffre() {
    }

    /** Raisons de refus (cles de traduction {@code matchs.coffre.refus.<nom en minuscules>}). */
    public enum Refus { COMBAT, A_TERRE, VEHICULE, DEPOT_EXISTANT, MORT, ERREUR }

    /**
     * Raison pour laquelle ce joueur ne peut pas deposer maintenant, ou null s'il le peut : tague en combat
     * ({@link CombatTag}), a terre (Reanimation, ou l'ancien PlayerRevive), depot non rendu, mort. Un passager de vehicule n'est PAS refuse :
     * {@link #deposer} le fait descendre (meme du train) ; {@link Refus#VEHICULE} ne reste que pour l'echec de cette
     * descente. Implementation : {@code moteur.coffre.Coffres}.
     */
    @Nullable
    public static Refus refus(ServerPlayer joueur) {
        Services.Moteur m = Services.MOTEUR;   // moteur/coffre/Coffres (serveur)
        return m != null ? m.coffreRefus(joueur) : Refus.ERREUR;
    }

    /**
     * Sauvegarde le joueur sur disque puis le vide. Rend vrai si le depot est fait (fichier ecrit ET joueur vide) ;
     * faux si refuse ({@link #refus}) ou en cas d'erreur d'ecriture (le joueur n'est alors pas touche).
     *
     * Le joueur ressort du depot en mode Survie, sante, faim et soif pleines (soif figee), sans effet ni tag de combat ;
     * c'est au mode de le teleporter et de l'equiper ({@link Equipement#donner}).
     *
     * @param raison texte libre journalise et ecrit dans le fichier ("clashwar", "duel", "zombies"...)
     */
    public static boolean deposer(ServerPlayer joueur, String raison) {
        Services.Moteur m = Services.MOTEUR;
        return m != null && m.coffreDeposer(joueur, raison);
    }

    /**
     * Rend au joueur son depot (vidage, rechargement, soif, teleportation a la position d'origine) puis supprime le
     * fichier apres sauvegarde du joueur. Purge aussi les objets pretes ({@link Equipement#purger}). Rend faux s'il n'y
     * avait pas de depot ou si la lecture a echoue (le fichier est alors garde pour enquete).
     * <p>Detache aussi le joueur : {@link MatchApi#quitter(ServerPlayer)} (drapeau retire), fin du mode spectateur,
     * bandeau efface. Sans depot, retire quand meme les objets pretes et le drapeau (et rend faux).
     */
    public static boolean rendre(ServerPlayer joueur) {
        Services.Moteur m = Services.MOTEUR;
        return m != null && m.coffreRendre(joueur);
    }

    /** Vrai si un depot non rendu existe sur disque pour ce joueur (connecte ou non). */
    public static boolean aUnDepot(UUID joueur) {
        Path f = fichier(joueur);
        return f != null && Files.isRegularFile(f);
    }

    /** Dossier des depots : {@code <dossier du serveur>/heraliz_clashwar/coffres} (comme heralisz_clans), ou null hors serveur. */
    @Nullable
    public static Path dossier() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return null;
        return s.getServerDirectory().toPath().resolve("heraliz_clashwar").resolve("coffres");
    }

    /** Fichier de depot du joueur ({@code <uuid>.dat}), ou null hors serveur. */
    @Nullable
    public static Path fichier(UUID joueur) {
        Path d = dossier();
        return d == null ? null : d.resolve(joueur + ".dat");
    }
}
