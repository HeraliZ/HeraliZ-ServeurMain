package fr.heraliz.grades;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;

/**
 * Les grades HeraliZ. Un joueur a au plus un grade du staff et au plus un grade de la boutique ; Survivant est le grade
 * de tout le monde (rien à enregistrer). Le préfixe affiché est celui du grade le plus visible : staff, sinon boutique,
 * sinon Survivant.
 */
public enum Grade {
   FONDATEUR("fondateur", "Fondateur", 0xE5484D, Type.STAFF, 3),
   MODERATEUR("moderateur", "Modérateur", 0xB48CFF, Type.STAFF, 2),
   HELPER("helper", "Helper", 0x5FD08A, Type.STAFF, 1),
   ROI_DE_LA_ZONE("roi_de_la_zone", "Roi de la Zone", 0xFFC23A, Type.BOUTIQUE, 3),
   TRAQUEUR("traqueur", "Traqueur", 0x8FD3FF, Type.BOUTIQUE, 2),
   RESCAPE("rescape", "Rescapé", 0xE39B5E, Type.BOUTIQUE, 1),
   SURVIVANT("survivant", "Survivant", 0xA0A6B0, Type.BASE, 0),
   /** Pour ceux qui portent le tag HeraliZ sur Discord (compte lié) : donne seulement le kit Soutien, jamais de préfixe. */
   SOUTIEN("soutien", "Soutien", 0xF5C842, Type.SOUTIEN, 0);

   public enum Type { STAFF, BOUTIQUE, BASE, SOUTIEN }

   public final String id;
   /** Nom français : texte de secours des messages du serveur et de la console. */
   public final String nomFr;
   public final int couleur;
   public final Type type;
   /** Rang dans son type (plus grand = plus haut). */
   public final int rang;

   Grade(String id, String nomFr, int couleur, Type type, int rang) {
      this.id = id;
      this.nomFr = nomFr;
      this.couleur = couleur;
      this.type = type;
      this.rang = rang;
   }

   /** Grade par identifiant (« traqueur »...), null si inconnu. */
   public static Grade parId(String id) {
      if (id == null) return null;
      String s = id.toLowerCase(Locale.ROOT);
      for (Grade g : values()) {
         if (g.id.equals(s)) return g;
      }
      return null;
   }

   /** Vrai si ce grade s'enregistre (staff ou boutique) : Survivant et Soutien ne se donnent ni ne se retirent. */
   public boolean attribuable() {
      return type == Type.STAFF || type == Type.BOUTIQUE;
   }

   /** Nom du grade dans la langue du joueur, en couleur. */
   public MutableComponent nom() {
      return Component.translatableWithFallback("grade.heraliz_grades." + id, nomFr).withStyle(st -> st.withColor(TextColor.fromRgb(couleur)));
   }

   /** Nom du grade dans la langue du joueur, sans couleur. */
   public MutableComponent nomSansCouleur() {
      return Component.translatableWithFallback("grade.heraliz_grades." + id, nomFr);
   }

   /** Préfixe « [Traqueur] » placé devant le pseudo. */
   public MutableComponent prefixe() {
      return Component.literal("[").append(Component.translatableWithFallback("grade.heraliz_grades." + id, nomFr)).append("] ")
            .withStyle(st -> st.withColor(TextColor.fromRgb(couleur)));
   }

   /** Préfixe en codes couleur Bukkit (§x§R§R§G§G§B§B), pour le nom affiché dans le chat des serveurs hybrides. */
   public String prefixeBukkit() {
      String hex = String.format(Locale.ROOT, "%06X", couleur);
      StringBuilder sb = new StringBuilder("§x");
      for (char c : hex.toCharArray()) sb.append('§').append(c);
      return sb.append('[').append(nomFr).append("] §r").toString();
   }
}
