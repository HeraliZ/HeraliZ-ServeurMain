package fr.heralisz.camo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Givre Vivant (collection de Noël du 22/11, choix du propriétaire : « deux skins en un ») : une inspection de l'arme la
 * couvre de givre, la suivante la dégivre, et elle reste dans son dernier état. L'état est gardé sur l'arme (NBT) et
 * changé par le serveur seul (paquet GivrePacket, fr.heralisz.camo.serveur.GivreServeur) : il suit l'arme et tous les
 * joueurs voient le même. Le dessin (camo 99) reçoit l'avancement et le sens du passage (uniforme CamoEtat).
 */
public final class GivreVivant {
   /** Vrai : givrée (ou en train de geler) ; faux ou absent : dégivrée. */
   public static final String GIVRE = "HeraliszGivre";
   /** Heure du monde (ticks) d'où part le passage en cours ; il dure {@link #DUREE} ticks. */
   public static final String DEPUIS = "HeraliszGivreT";
   /** Durée d'un passage (gel ou dégel), en ticks : 2,5 s, comme dans les rendus validés. */
   public static final int DUREE = 50;

   private GivreVivant() {
   }

   public static boolean givree(ItemStack arme) {
      CompoundTag t = arme.getTag();
      return t != null && t.getBoolean(GIVRE);
   }

   /**
    * Avancement du givre à l'heure {@code temps} (ticks du monde, fraction comprise) : 0 acier nu, 1 entièrement givrée.
    */
   public static float etat(ItemStack arme, double temps) {
      CompoundTag t = arme.getTag();
      if (t == null || !t.contains(GIVRE)) {
         return 0.0F;
      }
      double k = (temps - t.getLong(DEPUIS)) / DUREE;
      float a = (float) Math.max(0.0, Math.min(1.0, k));
      return t.getBoolean(GIVRE) ? a : 1.0F - a;
   }

   /**
    * Bascule (serveur seulement) à l'heure {@code maintenant} : le passage repart de l'état affiché à cet instant, pour
    * qu'une inspection pendant un gel le retourne en dégel sans saut.
    */
   public static void basculer(ItemStack arme, long maintenant) {
      float e = etat(arme, maintenant);
      boolean gel = !givree(arme);
      CompoundTag t = arme.getOrCreateTag();
      t.putBoolean(GIVRE, gel);
      t.putLong(DEPUIS, maintenant - Math.round((gel ? e : 1.0F - e) * DUREE));
   }
}
