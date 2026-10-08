package fr.heralisz.matchs.zombies.jeu;

import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Lectures typées des parties de zombies (corps d'origine de ZombiesApi.partieDe / partieA / planches / interagir) :
 * côté serveur, le code du jeu les appelle directement ; l'API commune ZombiesApi y passe par pont/Services.
 */
public final class PartiesZombies {
    private PartiesZombies() {
    }

    private static final String TAG_PARTIE = ZombiesApi.TAG_PARTIE;

    /** Partie de la creature (ou du joueur), ou null. */
    @Nullable
    public static PartieZombies partieDe(Entity e) {
        if (e == null) return null;
        if (e instanceof ServerPlayer p) {
            Partie pa = MatchApi.partieDe(p);
            return pa instanceof PartieZombies z ? z : null;
        }
        if (!e.getPersistentData().contains(TAG_PARTIE)) return null;
        Partie pa = MatchApi.partie(e.getPersistentData().getString(TAG_PARTIE));
        return pa instanceof PartieZombies z ? z : null;
    }

    /** Partie dont l'instance contient ce bloc, ou null. */
    @Nullable
    public static PartieZombies partieA(Level niveau, BlockPos pos) {
        return PartieZombies.a(niveau, pos);
    }

    /**
     * Planches restantes (0..6) de la fenetre qui contient ce bloc dans une partie en cours, ou -1 si le bloc n'est
     * dans aucune fenetre d'une partie (fenetre de decor : la considerer pleine).
     */
    public static int planches(Level niveau, BlockPos pos) {
        PartieZombies p = PartieZombies.a(niveau, pos);
        return p == null ? -1 : p.planchesA(pos);
    }

    /**
     * Le joueur interagit (comme s'il appuyait sur F) : achat, reparation d'une planche, prise de l'arme de la boite...
     * Rend vrai si quelque chose a ete fait ou refuse (message envoye). A appeler depuis un {@code use} de bloc du contenu.
     */
    public static boolean interagir(ServerPlayer joueur) {
        PartieZombies p = partieDe(joueur);
        return p != null && p.interagir(joueur);
    }

    /** Données de jeu rechargées (ConfigZombies) : tables de la boîte et recettes recalculées. */
    public static void invaliderTables() {
        TableBoite.invalider();
        Recettes.invalider();
    }
}
