package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;

/**
 * Vue d'une zone pour le panneau du drapeau (clic droit sur le socle). proprioTag vide = zone libre.
 *
 * @param zoneId             identifiant de la zone
 * @param nom                nom affiche
 * @param proprioTag         tag du proprietaire ("" si libre)
 * @param proprioNom         nom du clan proprietaire
 * @param banniere           banniere du proprietaire (null si libre)
 * @param tenueDepuisMs      ms depuis la capture
 * @param lootBonus          bonus de loot (%) des membres presents
 * @param membre             le joueur est membre du clan proprietaire
 * @param peutLancer         il a la permission RAVITAILLEMENT
 * @param ravitaillementDansMs ms avant le prochain signal possible (0 = disponible)
 * @param etat               etat courant de la zone
 */
public record ZoneVue(String zoneId, String nom, String proprioTag, String proprioNom, @Nullable Banniere banniere, long tenueDepuisMs,
                      int lootBonus, boolean membre, boolean peutLancer, long ravitaillementDansMs, EtatZone etat) {
    public void write(FriendlyByteBuf b) {
        Texte.utf(b, zoneId, 32);
        Texte.utf(b, nom, 48);
        Texte.utf(b, proprioTag, 8);
        Texte.utf(b, proprioNom, 32);
        b.writeBoolean(banniere != null);
        if (banniere != null) banniere.write(b);
        b.writeVarLong(tenueDepuisMs);
        b.writeVarInt(lootBonus);
        b.writeBoolean(membre);
        b.writeBoolean(peutLancer);
        b.writeVarLong(ravitaillementDansMs);
        b.writeVarInt(etat.ordinal());
    }

    public static ZoneVue read(FriendlyByteBuf b) {
        String id = b.readUtf(32), nom = b.readUtf(48), tag = b.readUtf(8), pnom = b.readUtf(32);
        Banniere ban = b.readBoolean() ? Banniere.read(b) : null;
        return new ZoneVue(id, nom, tag, pnom, ban, b.readVarLong(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarLong(),
                EtatZone.parOrdinal(b.readVarInt()));
    }

    public boolean libre() {
        return proprioTag.isEmpty();
    }
}
