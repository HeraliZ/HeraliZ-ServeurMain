package fr.heralisz.bases.model;

import fr.heralisz.clans.model.Banniere;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Une ligne de l'ecran PILLER (BASES_SPEC §6) : base d'un autre clan, avec sa banniere et ses membres.
 *
 * @param pillable  vrai si le joueur peut lancer le pillage maintenant
 * @param raison    cle de traduction du refus quand {@code pillable} est faux (« » sinon)
 * @param dejaPilleeParVous le joueur a deja utilise son entree du jour sur cette base
 * @param pillards  pillards actuellement dans la base
 */
public record PillableVue(UUID clan, String tag, String nom, Banniere banniere, int membres, int enLigne, boolean pillable,
                          String raison, boolean dejaPilleeParVous, int pillards) {
    public void write(FriendlyByteBuf b) {
        b.writeUUID(clan);
        b.writeUtf(tag, 8);
        b.writeUtf(nom, 32);
        banniere.write(b);
        b.writeVarInt(membres);
        b.writeVarInt(enLigne);
        b.writeBoolean(pillable);
        b.writeUtf(raison, 96);
        b.writeBoolean(dejaPilleeParVous);
        b.writeVarInt(pillards);
    }

    public static PillableVue read(FriendlyByteBuf b) {
        UUID c = b.readUUID();
        String tag = b.readUtf(8), nom = b.readUtf(32);
        Banniere ban = Banniere.read(b);
        int m = b.readVarInt(), e = b.readVarInt();
        boolean p = b.readBoolean();
        String r = b.readUtf(96);
        return new PillableVue(c, tag, nom, ban, m, e, p, r, b.readBoolean(), b.readVarInt());
    }
}
