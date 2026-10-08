package fr.heralisz.matchs.moteur.api;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Groupe de joueurs (1 a {@link #MAX} joueurs), independant des clans (MATCHS_SPEC §1.6) : utilise par les duels
 * (2v2) et les zombies. Immuable : toute modification passe par {@link Groupes} qui remplace l'instance.
 *
 * @param id      identifiant stable du groupe (UUID aleatoire a la creation)
 * @param chef    chef du groupe (toujours dans {@code membres})
 * @param membres membres, chef en tete, liste non modifiable
 */
public record Groupe(UUID id, UUID chef, List<UUID> membres) {
    /** Taille maximale d'un groupe. */
    public static final int MAX = 4;

    public Groupe {
        membres = List.copyOf(membres);
    }

    /** Groupe d'un joueur seul (il en est le chef). */
    public static Groupe solo(UUID joueur) {
        return new Groupe(joueur, joueur, List.of(joueur));
    }

    public int taille() {
        return membres.size();
    }

    public boolean contient(UUID joueur) {
        return membres.contains(joueur);
    }

    public boolean estChef(UUID joueur) {
        return chef.equals(joueur);
    }

    public boolean plein() {
        return membres.size() >= MAX;
    }

    /** Membres connectes (nouvelle liste, chef en tete s'il est connecte). */
    public List<ServerPlayer> enLigne(MinecraftServer serveur) {
        List<ServerPlayer> l = new ArrayList<>(membres.size());
        for (UUID u : membres) {
            ServerPlayer p = serveur.getPlayerList().getPlayer(u);
            if (p != null) l.add(p);
        }
        return l;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUUID(chef);
        buf.writeVarInt(membres.size());
        for (UUID u : membres) buf.writeUUID(u);
    }

    public static Groupe read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID(), chef = buf.readUUID();
        int n = Math.min(MAX, buf.readVarInt());
        List<UUID> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(buf.readUUID());
        return new Groupe(id, chef, l);
    }
}
