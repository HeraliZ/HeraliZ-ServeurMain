package fr.heraliz.reanimation.compat;

import fr.heraliz.reanimation.ReanimationMod;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Objets à vie de HeraliZ Tactique ({@code fr.heraliz.tactique.avie.ObjetsAVie}, lu par réflexion) rangés dans un sac
 * de la victime d'une fouille (audit du 02/11, point 8 : un objet à vie n'est jamais perdu) : à l'ouverture de la fouille,
 * ils sortent de ses sacs (porté sur le dos ou rangé dans l'inventaire) et reviennent dans son inventaire (sinon gardés
 * par Tactique et rendus dès qu'une case se libère). Ses objets à vie ne se prennent déjà pas ({@code SearchMenu}) : le
 * fouilleur peut prendre le sac, plus ce qu'il contenait à vie. Sans Tactique : rien.
 */
public final class ObjetsAVieFouille {
   private static volatile Method estAVie;
   private static volatile Method contientAVie;
   private static volatile Method neJamaisDetruire;
   private static volatile boolean cherche;

   private ObjetsAVieFouille() {
   }

   private static boolean pret() {
      if (!cherche) {
         try {
            Class<?> c = Class.forName("fr.heraliz.tactique.avie.ObjetsAVie");
            estAVie = c.getMethod("estAVie", ItemStack.class);
            contientAVie = c.getMethod("contientAVie", ItemStack.class);
            neJamaisDetruire = c.getMethod("neJamaisDetruire", UUID.class, ItemStack.class, String.class);
         } catch (ReflectiveOperationException | LinkageError ex) {
            estAVie = null;
            contientAVie = null;
            neJamaisDetruire = null;
         }
         cherche = true;
      }
      return neJamaisDetruire != null;
   }

   /** Avant d'ouvrir la fouille de {@code victime} (fil du serveur). */
   public static void proteger(LivingEntity victime) {
      if (!(victime instanceof Player p) || !pret()) return;
      try {
         int n = 0;
         Container inv = p.getInventory();
         for (int i = 0; i < inv.getContainerSize(); i++) n += sortir(p, inv.getItem(i));
         Container curios = CuriosFouille.de(victime).conteneur();
         for (int i = 0; i < curios.getContainerSize(); i++) n += sortir(p, curios.getItem(i));
         if (n > 0) ReanimationMod.LOGGER.info("[fouille] {} : objets à vie sortis de {} sac(s) avant la fouille", p.getGameProfile().getName(), n);
      } catch (ReflectiveOperationException | RuntimeException ex) {
         ReanimationMod.LOGGER.warn("[fouille] objets à vie des sacs : {}", ex.toString());
      }
   }

   private static int sortir(Player p, ItemStack s) throws ReflectiveOperationException {
      if (s.isEmpty() || Boolean.TRUE.equals(estAVie.invoke(null, s)) || !Boolean.TRUE.equals(contientAVie.invoke(null, s))) return 0;
      neJamaisDetruire.invoke(null, p.getUUID(), s, "fouille");
      return 1;
   }
}
