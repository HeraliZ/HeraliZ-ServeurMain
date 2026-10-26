package fr.heraliz.grades.serveur;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Infos du nouveau tab : grade affiché et tag de clan de chaque joueur connecté, envoyés quand ils changent (vérifié
 * toutes les 2 s) et au joueur qui arrive. Le pseudo et le ping viennent de la liste de Minecraft. Un joueur caché par
 * /staff vanish ({@link VanishServeur}) n'est envoyé qu'à ceux qui le voient (staff), marqué « caché ».
 */
final class TabServeur {
    private static List<ReseauGrades.LigneTab> dernier = List.of();
    private static Set<UUID> derniersVoyants = Set.of();

    private TabServeur() {
    }

    static void oublier() {
        dernier = List.of();
        derniersVoyants = Set.of();
    }

    private static List<ReseauGrades.LigneTab> lignes(MinecraftServer srv) {
        List<ReseauGrades.LigneTab> out = new ArrayList<>();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            Grade g = ServeurGrades.registre == null ? Grade.SURVIVANT : ServeurGrades.registre.affiche(p.getUUID());
            MutableComponent clan = TagClan.prefixe(p);
            String tag = TagClan.tag(clan);
            int couleur = clan == null || clan.getStyle().getColor() == null ? 0xFFFFFF : clan.getStyle().getColor().getValue();
            String surnom = SurnomsServeur.surnomAffiche(p);
            out.add(new ReseauGrades.LigneTab(p.getUUID(), g.id, tag.length() > 16 ? tag.substring(0, 16) : tag, couleur, surnom == null ? "" : surnom,
                    VanishServeur.cache(p.getUUID())));
        }
        return out;
    }

    /** Renvoie les infos si elles ont changé (ou toujours si force) : la liste complète au staff, sans les cachés aux autres. */
    static void maj(MinecraftServer srv, boolean force) {
        List<ReseauGrades.LigneTab> l = lignes(srv);
        Set<UUID> voyants = new HashSet<>();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) if (VanishServeur.voitLesCaches(p)) voyants.add(p.getUUID());
        if (!force && l.equals(dernier) && voyants.equals(derniersVoyants)) return;
        dernier = l;
        derniersVoyants = voyants;
        List<ReseauGrades.LigneTab> publiques = l.stream().filter(x -> !x.cache()).toList();
        ReseauGrades.InfosTab tout = new ReseauGrades.InfosTab(l), sansCaches = new ReseauGrades.InfosTab(publiques);
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            ReseauGrades.CANAL.send(PacketDistributor.PLAYER.with(() -> p), voyants.contains(p.getUUID()) ? tout : sansCaches);
        }
    }
}
