package fr.heraliz.grades.serveur;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.reseau.ReseauGrades;
import fr.heraliz.grades.reseau.Synchro;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Infos du nouveau tab : grade affiché, tag de clan et surnom de chaque joueur connecté. Le pseudo et le ping viennent de
 * la liste de Minecraft. Un joueur caché par vanish du staff ({@link VanishServeur}) n'est envoyé qu'à ceux qui le voient
 * (staff), marqué « caché ».
 * <p>21/11 (décision du propriétaire après l'audit réseau du 20/11) : seulement ce qui change. Avant, la liste COMPLÈTE
 * partait à tous les joueurs à chaque arrivée, départ, changement de grade, de tag ou de surnom (à 150 joueurs : 150
 * listes de 150 lignes pour une seule arrivée). Maintenant : la liste complète au joueur qui arrive (pour lui seul), puis
 * des différences numérotées ({@link Synchro}) à ceux qui l'ont déjà, dans deux vues : le staff (tout, cachés marqués)
 * et les autres (sans les cachés ; un joueur qui se cache leur est « retiré »). Les changements d'un même tick partent en
 * un seul envoi, à la fin du tick ; toutes les 2 s, un contrôle relit tout (grade qui expire, clan, passage dans le staff
 * à la connexion AuthMe) et n'envoie que ce qui a changé ; rien ne part si rien ne change. Un joueur à qui une
 * différence manque redemande la liste complète (au plus une fois toutes les 5 s).</p>
 */
final class TabServeur {
    /** Vues : le staff (tout, cachés marqués) et les autres joueurs (sans les cachés). */
    private static final int STAFF = 0, PUBLIC = 1;
    private static final Synchro.Diffuseur<UUID, UUID, ReseauGrades.LigneTab> TAB = new Synchro.Diffuseur<>(l -> true, l -> !l.cache());
    private static boolean sale;

    private TabServeur() {
    }

    static void oublier() {
        TAB.vider();
        sale = false;
    }

    /** Quelque chose a changé (grade, surnom, vanish) : envoyé à la fin du tick, en un seul envoi pour tout le tick. */
    static void marquer() {
        sale = true;
    }

    /** Arrivée : liste complète pour lui, sa ligne pour les autres (fin du tick). */
    static void connexion(ServerPlayer p) {
        TAB.complet(p.getUUID());
        sale = true;
    }

    /** Départ : sa ligne retirée chez les autres (fin du tick ; il n'est plus dans la liste des joueurs). */
    static void deconnexion(UUID joueur) {
        TAB.depart(joueur);
        sale = true;
    }

    /** Une différence manquait chez ce joueur : liste complète à la fin du tick (au plus une fois toutes les 5 s). */
    static void demande(ServerPlayer p) {
        if (TAB.demande(p.getUUID(), System.currentTimeMillis())) sale = true;
    }

    /**
     * Fin de chaque tick : rien si rien n'a été signalé ; {@code controle} (toutes les 2 s) : tout est relu et seul ce
     * qui a changé part.
     */
    static void tick(MinecraftServer srv, boolean controle) {
        if (!sale && !controle) return;
        sale = false;
        envoyer(srv);
    }

    private static Map<UUID, ReseauGrades.LigneTab> lignes(List<ServerPlayer> joueurs) {
        Map<UUID, ReseauGrades.LigneTab> out = new LinkedHashMap<>();
        for (ServerPlayer p : joueurs) {
            Grade g = ServeurGrades.registre == null ? Grade.SURVIVANT : ServeurGrades.registre.affiche(p.getUUID());
            MutableComponent clan = TagClan.prefixe(p);
            String tag = TagClan.tag(clan);
            int couleur = clan == null || clan.getStyle().getColor() == null ? 0xFFFFFF : clan.getStyle().getColor().getValue();
            String surnom = SurnomsServeur.surnomAffiche(p);
            out.put(p.getUUID(), new ReseauGrades.LigneTab(p.getUUID(), g.id, tag.length() > 16 ? tag.substring(0, 16) : tag, couleur,
                    surnom == null ? "" : surnom, VanishServeur.cache(p.getUUID())));
        }
        return out;
    }

    private static void envoyer(MinecraftServer srv) {
        List<ServerPlayer> joueurs = srv.getPlayerList().getPlayers();
        // chaque paquet est mis en forme une seule fois par vue (20/11), et seulement s'il part à quelqu'un
        Packet<?>[] complets = new Packet<?>[2], differences = new Packet<?>[2];
        TAB.<ServerPlayer>publier(lignes(joueurs), joueurs, ServerPlayer::getUUID, p -> VanishServeur.voitLesCaches(p) ? STAFF : PUBLIC,
                new Synchro.Envoi<ServerPlayer, UUID, ReseauGrades.LigneTab>() {
                    @Override
                    public void complet(ServerPlayer p, int vue, int version, Map<UUID, ReseauGrades.LigneTab> table) {
                        if (complets[vue] == null) complets[vue] = paquet(new ReseauGrades.InfosTab(version, new ArrayList<>(table.values())));
                        p.connection.send(complets[vue]);
                    }

                    @Override
                    public void difference(ServerPlayer p, int vue, int version, Synchro.Diff<UUID, ReseauGrades.LigneTab> d) {
                        if (differences[vue] == null) {
                            differences[vue] = paquet(new ReseauGrades.DeltaTab(version, d.retires(), new ArrayList<>(d.changes().values())));
                        }
                        p.connection.send(differences[vue]);
                    }
                });
    }

    private static Packet<?> paquet(Object message) {
        return ReseauGrades.CANAL.toVanillaPacket(message, NetworkDirection.PLAY_TO_CLIENT);
    }
}
