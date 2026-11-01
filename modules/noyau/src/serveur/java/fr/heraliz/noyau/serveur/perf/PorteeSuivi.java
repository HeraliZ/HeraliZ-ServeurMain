package fr.heraliz.noyau.serveur.perf;

import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.Collections;

/**
 * Point 2 du chantier « Minecraft allégé » (01/11) : portée de suivi d'une entité ({@code ChunkMap.TrackedEntity
 * .getEffectiveRange}, à quelle distance un joueur la voit). Minecraft la calcule à chaque fois qu'il juge une paire
 * (entité, joueur) : pour chaque entité suivie, chaque fois qu'un joueur bouge (chaque paquet de mouvement), chaque fois
 * qu'une entité change de section, à chaque tick. Pour prendre la plus grande portée de ses passagers, il parcourt
 * {@code getIndirectPassengers()} : un flux Java neuf à chaque appel (une dizaine d'objets), même sans aucun passager.
 * Banc du 01/11 : 2 500 entités suivies (dont 1 227 balles de TaCZ) et 80 joueurs qui bougent, ce flux visible en haut du
 * profil.
 * <p>Ici, une entité sans passager rend une liste vide qui ne crée rien : la boucle de Minecraft ne tourne pas, la portée
 * est la même (celle de l'entité). Seulement pour les classes d'entités qui gardent les méthodes de Minecraft
 * ({@code getIndirectPassengers} et {@code isVehicle} non redéfinies, vérifié une fois par classe) ; sinon, et avec un
 * passager, rien ne change. Réglage : {@code [perf] portee_suivi_rapide}.
 */
public final class PorteeSuivi {
   private PorteeSuivi() {
   }

   private static final Method PASSAGERS;
   private static final Method VEHICULE;

   static {
      Method p = null;
      Method v = null;
      try {
         // noms de Minecraft à l'exécution (SRG) : getIndirectPassengers, isVehicle
         p = ObfuscationReflectionHelper.findMethod(Entity.class, "m_146897_");
         v = ObfuscationReflectionHelper.findMethod(Entity.class, "m_20160_");
      } catch (RuntimeException | LinkageError e) {
         p = null;
         v = null;
      }
      PASSAGERS = p;
      VEHICULE = v;
   }

   /** Vrai si la classe garde les deux méthodes de Minecraft (aucun mod ne les a redéfinies pour elle). */
   private static final ClassValue<Boolean> COMME_MINECRAFT = new ClassValue<>() {
      @Override
      protected Boolean computeValue(Class<?> type) {
         if (PASSAGERS == null || VEHICULE == null) {
            return Boolean.FALSE;
         }
         try {
            return type.getMethod(PASSAGERS.getName()).getDeclaringClass() == Entity.class
                  && type.getMethod(VEHICULE.getName()).getDeclaringClass() == Entity.class;
         } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return Boolean.FALSE;
         }
      }
   };

   /** Ce que la boucle de {@code getEffectiveRange} parcourt : rien (sans rien créer) pour une entité sans passager. */
   public static Iterable<Entity> passagers(Entity entite) {
      if (NoyauConfig.porteeSuiviRapide && COMME_MINECRAFT.get(entite.getClass()) && !entite.isVehicle()) {
         Allege.porteeRapide++;
         return Collections.emptyList();
      }
      return entite.getIndirectPassengers();
   }
}
