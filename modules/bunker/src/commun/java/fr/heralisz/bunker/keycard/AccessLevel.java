package fr.heralisz.bunker.keycard;

import net.minecraft.ChatFormatting;

/**
 * Niveaux d'accès façon Rust : chaque lecteur n'accepte que la carte de sa couleur.
 * <p>17/11 (demande du propriétaire : « une nouvelle carte pour chaque bunker + un keypad aussi ») : une carte et un
 * lecteur par bunker de la carte. Pas des couleurs ({@link #couleur} faux) : ni fragments, ni portes à carte, ni salles
 * à carte ; leur lecteur, posé à côté d'une porte de bunker (/salle bunker), est le seul à l'ouvrir. Identifiants figés
 * (keycard_bunker_bois, card_reader_bunker_pic...) : ils restent dans les inventaires et sur la carte.</p>
 */
public enum AccessLevel {
   GREEN("green", ChatFormatting.GREEN, 1, true),
   BLUE("blue", ChatFormatting.BLUE, 2, true),
   RED("red", ChatFormatting.RED, 3, true),
   BUNKER_BOIS("bunker_bois", ChatFormatting.GOLD, 3, false),
   BUNKER_PIC("bunker_pic", ChatFormatting.DARK_PURPLE, 3, false);

   public final String id;
   public final ChatFormatting color;
   public final int tier;
   /** Carte de couleur (verte, bleue, rouge) : fragments, portes à carte, salles à carte. Faux : carte d'un bunker. */
   public final boolean couleur;

   AccessLevel(String id, ChatFormatting color, int tier, boolean couleur) {
      this.id = id;
      this.color = color;
      this.tier = tier;
      this.couleur = couleur;
   }
}
