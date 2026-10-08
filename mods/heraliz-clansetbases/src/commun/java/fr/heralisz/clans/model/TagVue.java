package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Tag d'un joueur en ligne, diffuse a tous : « [TAG] » colore au-dessus de la tete et dans la liste Tab.
 */
public record TagVue(String tag, int couleur) {
    public void write(FriendlyByteBuf b) {
        Texte.utf(b, tag, 8);
        b.writeInt(couleur);
    }

    public static TagVue read(FriendlyByteBuf b) {
        return new TagVue(b.readUtf(8), b.readInt());
    }
}
