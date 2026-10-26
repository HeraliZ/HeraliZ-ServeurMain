package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Ligne du classement de la saison (onglet Saison de l'ecran du clan, 25/10) : place au general, identite, points des
 * trois categories (arrondis a l'unite) et place dans chacune.
 */
public record SaisonLigne(int rang, UUID id, String tag, String nom, Banniere banniere, long pve, long pvp, long events,
                          int rPve, int rPvp, int rEvents) {
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(rang);
        b.writeUUID(id);
        Texte.utf(b, tag, 8);
        Texte.utf(b, nom, 32);
        banniere.write(b);
        b.writeVarLong(pve);
        b.writeVarLong(pvp);
        b.writeVarLong(events);
        b.writeVarInt(rPve);
        b.writeVarInt(rPvp);
        b.writeVarInt(rEvents);
    }

    public static SaisonLigne read(FriendlyByteBuf b) {
        return new SaisonLigne(b.readVarInt(), b.readUUID(), b.readUtf(8), b.readUtf(32), Banniere.read(b), b.readVarLong(),
                b.readVarLong(), b.readVarLong(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    /** Somme des trois places (le classement general : la plus petite gagne). */
    public int sommePlaces() {
        return rPve + rPvp + rEvents;
    }
}
