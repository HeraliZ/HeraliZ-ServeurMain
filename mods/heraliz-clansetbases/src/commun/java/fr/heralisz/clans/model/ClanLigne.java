package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Ligne du classement (top 20) : rang, identite, niveau, xp totale, membres, kills, territoires.
 */
public record ClanLigne(int rang, UUID id, String tag, String nom, Banniere banniere, int niveau, long xpTotale, int membres,
                        int kills, int territoires) {
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(rang);
        b.writeUUID(id);
        Texte.utf(b, tag, 8);
        Texte.utf(b, nom, 32);
        banniere.write(b);
        b.writeVarInt(niveau);
        b.writeVarLong(xpTotale);
        b.writeVarInt(membres);
        b.writeVarInt(kills);
        b.writeVarInt(territoires);
    }

    public static ClanLigne read(FriendlyByteBuf b) {
        return new ClanLigne(b.readVarInt(), b.readUUID(), b.readUtf(8), b.readUtf(32), Banniere.read(b), b.readVarInt(),
                b.readVarLong(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }
}
