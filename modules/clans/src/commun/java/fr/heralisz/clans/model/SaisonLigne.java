package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Ligne du classement de la saison (onglet Saison de l'ecran du clan, 25/10) : place au general, identite, points des
 * trois categories et place dans chacune. 11/11 (canal 11) : points en CENTIEMES ({@link #CENTIEMES}) : la case montre
 * les points entiers, l'infobulle le compte exact (0,01 par zombie : invisible en points entiers, retour du proprietaire).
 */
public record SaisonLigne(int rang, UUID id, String tag, String nom, Banniere banniere, long pve, long pvp, long events,
                          int rPve, int rPvp, int rEvents) {
    /** Les points voyagent multiplies par 100 (0,37 point = 37). */
    public static final int CENTIEMES = 100;

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

    /** Points (centiemes) de la categorie 0 PVE, 1 PVP, 2 events. */
    public long centiemes(int categorie) {
        return categorie == 0 ? pve : categorie == 1 ? pvp : events;
    }

    /** Place dans la categorie 0 PVE, 1 PVP, 2 events. */
    public int place(int categorie) {
        return categorie == 0 ? rPve : categorie == 1 ? rPvp : rEvents;
    }

    /** Points entiers a afficher (arrondis vers le bas, comme le total du serveur) depuis des centiemes. */
    public static long entiers(long centiemes) {
        return Math.floorDiv(centiemes, CENTIEMES);
    }
}
