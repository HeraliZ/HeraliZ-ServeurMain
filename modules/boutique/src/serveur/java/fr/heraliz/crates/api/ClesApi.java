package fr.heraliz.crates.api;

import fr.heraliz.crates.cles.ComptesCles;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateKeyItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Clés de caisse sur le compte des joueurs (25/10) : à utiliser par les autres mods pour DONNER une clé au lieu de donner
 * l'objet {@code heralizcrates:<caisse>_key}. Lue par réflexion par HeraliZ Quêtes (récompenses, /wipe) et HeraliZ ClashWar
 * (clé d'événement du vainqueur) : ne jamais renommer cette classe ni changer ces signatures. Fil serveur seulement.
 */
public final class ClesApi {
    private ClesApi() {
    }

    /**
     * Caisse désignée par « quete », « quete_key » ou « heralizcrates:quete_key » (alias des commandes acceptés) ; null si
     * ce n'est pas une caisse connue.
     */
    public static CrateType caisse(String id) {
        if (id == null) return null;
        String s = id.startsWith("heralizcrates:") ? id.substring("heralizcrates:".length()) : id;
        if (s.contains(":")) return null;
        return CrateType.byId(s);
    }

    /** Vrai si c'est l'identifiant d'objet d'une clé de caisse ({@code heralizcrates:<caisse>_key}). */
    public static boolean estIdCle(String idObjet) {
        return idObjet != null && idObjet.startsWith("heralizcrates:") && idObjet.endsWith("_key") && caisse(idObjet) != null;
    }

    public static boolean estCle(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() instanceof CrateKeyItem;
    }

    /**
     * Ajoute {@code n} clés de cette caisse au compte du joueur (en ligne ou non) et l'écrit tout de suite. Faux si la
     * caisse est inconnue, si le compte est fermé (fichier illisible) ou si l'écriture échoue : rien n'a été donné.
     */
    public static boolean crediter(UUID joueur, String caisse, int n, String source) {
        CrateType t = caisse(caisse);
        if (t == null || joueur == null) return false;
        var srv = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = srv == null ? null : srv.getPlayerList().getPlayer(joueur);
        return ComptesCles.crediter(srv, joueur, p == null ? null : p.getGameProfile().getName(), t, n, source);
    }

    /**
     * Comme {@link #crediter}, mais une seule fois pour un même identifiant (26/10, récompenses des quêtes du jour de
     * HeraliZ Quêtes) : refaire la même demande (après un plantage) ne donne rien de plus et rend vrai.
     */
    public static boolean crediterUneFois(UUID joueur, String caisse, int n, String source, String id) {
        CrateType t = caisse(caisse);
        if (t == null || joueur == null) return false;
        var srv = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = srv == null ? null : srv.getPlayerList().getPlayer(joueur);
        return ComptesCles.crediterUneFois(srv, joueur, p == null ? null : p.getGameProfile().getName(), t, n, source, id);
    }

    /** Si {@code s} est une clé physique : créditée au joueur (toute la pile) et vrai ; sinon faux, rien fait. */
    public static boolean crediterObjet(ServerPlayer p, ItemStack s, String source) {
        if (!estCle(s)) return false;
        return ComptesCles.crediter(p.server, p.getUUID(), p.getGameProfile().getName(), ((CrateKeyItem) s.getItem()).type, s.getCount(), source);
    }

    /** Clés de cette caisse sur le compte du joueur (0 si inconnue). */
    public static int solde(UUID joueur, String caisse) {
        CrateType t = caisse(caisse);
        return t == null || joueur == null ? 0 : ComptesCles.solde(joueur, t);
    }

    /** Remise à zéro (/wipe) : clés des caisses de jeu retirées, clés cosmétiques gardées ({@link ComptesCles#wipe}). */
    public static boolean wipe(UUID joueur) {
        if (joueur == null) return false;
        var srv = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = srv == null ? null : srv.getPlayerList().getPlayer(joueur);
        // à appeler AVANT de vider l'inventaire : ses clés physiques passent d'abord sur le compte (une conversion restée
        // en route est terminée), puis les clés de jeu partent ; les clés cosmétiques achetées sont ainsi gardées
        if (p != null) fr.heraliz.crates.cles.MigrationCles.convertir(p);
        return ComptesCles.wipe(srv, joueur);
    }

    /** Renvoie au joueur l'état de ses clés (écrans de la boutique et des caisses). */
    public static void envoyer(ServerPlayer p) {
        ComptesCles.envoyer(p);
    }
}
