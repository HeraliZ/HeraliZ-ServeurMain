package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Invitation recue, vue par le joueur invite : le clan (id, tag, nom, banniere, membres/max), qui invite et le temps
 * restant (ms a l'envoi).
 */
public record InvitationVue(UUID clanId, String tag, String nom, Banniere banniere, int membres, int membresMax, String auteurNom,
                            long resteMs) {
    public void write(FriendlyByteBuf b) {
        b.writeUUID(clanId);
        Texte.utf(b, tag, 8);
        Texte.utf(b, nom, 32);
        banniere.write(b);
        b.writeVarInt(membres);
        b.writeVarInt(membresMax);
        Texte.utf(b, auteurNom, 48);
        b.writeVarLong(resteMs);
    }

    public static InvitationVue read(FriendlyByteBuf b) {
        return new InvitationVue(b.readUUID(), b.readUtf(8), b.readUtf(32), Banniere.read(b), b.readVarInt(), b.readVarInt(),
                b.readUtf(48), b.readVarLong());
    }
}
