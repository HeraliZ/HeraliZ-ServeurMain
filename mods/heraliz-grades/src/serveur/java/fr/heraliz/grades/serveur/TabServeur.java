package fr.heraliz.grades.serveur;

import fr.heraliz.grades.Grade;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Infos du nouveau tab : grade affiché et tag de clan de chaque joueur connecté, envoyés à tous quand ils changent
 * (vérifié toutes les 2 s) et au joueur qui arrive. Le pseudo et le ping viennent de la liste de Minecraft.
 */
final class TabServeur {
    private static List<ReseauGrades.LigneTab> dernier = List.of();

    private TabServeur() {
    }

    static void oublier() {
        dernier = List.of();
    }

    private static List<ReseauGrades.LigneTab> lignes(MinecraftServer srv) {
        List<ReseauGrades.LigneTab> out = new ArrayList<>();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            Grade g = ServeurGrades.registre == null ? Grade.SURVIVANT : ServeurGrades.registre.affiche(p.getUUID());
            MutableComponent clan = TagClan.prefixe(p);
            String tag = clan == null ? "" : clan.getString().trim().replaceAll("^\\[|\\]$", "");
            int couleur = clan == null || clan.getStyle().getColor() == null ? 0xFFFFFF : clan.getStyle().getColor().getValue();
            out.add(new ReseauGrades.LigneTab(p.getUUID(), g.id, tag.length() > 16 ? tag.substring(0, 16) : tag, couleur));
        }
        return out;
    }

    /** Renvoie les infos à tous si elles ont changé (ou toujours si force). */
    static void maj(MinecraftServer srv, boolean force) {
        List<ReseauGrades.LigneTab> l = lignes(srv);
        if (!force && l.equals(dernier)) return;
        dernier = l;
        ReseauGrades.CANAL.send(PacketDistributor.ALL.noArg(), new ReseauGrades.InfosTab(l));
    }
}
