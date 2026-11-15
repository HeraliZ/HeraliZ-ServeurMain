package fr.heraliz.tactique.serveur;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Brûlures qui ont un auteur (audit du 15/11). Chez Minecraft, une entité en feu prend ses dégâts de feu
 * ({@code minecraft:on_fire}, 1 par seconde) sans auteur : ni tag de combat, ni crédit du tueur.
 * <ul>
 *   <li><b>Molotov</b> (combat B1) : le nuage au sol et l'éclaboussure d'un molotov ({@link #molotov}) marquent ce qu'ils
 *   allument. Tant qu'elle brûle depuis (au plus {@link #APRES_MOLOTOV} ticks après le dernier allumage, le temps de son
 *   effet « inflammable » et de son feu), chaque dégât de feu de la victime est rejoué au nom du lanceur : « EN COMBAT »
 *   des deux côtés (noyau), crédit du tueur (Réanimation retient celui qui met à terre, clans), chiffres des dégâts ; et là
 *   où un coup du lanceur serait refusé (zone sans PvP de WorldGuard, protections du spawn et des nouveaux joueurs), la
 *   victime brûle sans perdre de vie. Même montant (l'effet « inflammable » le double toujours), aucune armure (comme le
 *   feu), aucun recul (le coup rejoué ne repousse pas). Lanceur introuvable (parti, mort, autre monde) : feu sans auteur,
 *   comme avant.</li>
 * </ul>
 * Fil du serveur. Rien ne tourne sans brûlure marquée ; les marques sont oubliées à la fin de leur temps (contrôle toutes
 * les 5 s, seulement s'il en reste).
 */
public final class Brulures {
   /** Temps (ticks) pendant lequel le feu d'un molotov reste au nom du lanceur, après le feu restant au dernier allumage. */
   static final int APRES_MOLOTOV = 140;
   private static final int PURGE = 100;

   private enum Genre { MOLOTOV }

   /** Brûlure marquée d'une victime : genre, auteur, fin (heure du monde). */
   private static final class Marque {
      Genre genre;
      UUID auteur;
      long jusqua;
   }

   private static final Map<LivingEntity, Marque> MARQUES = new IdentityHashMap<>();
   /** Victime du coup rejoué en cours : son recul est annulé. */
   @Nullable
   private static LivingEntity sansRecul;
   private static int compteur;

   private Brulures() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, Brulures::attaque);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Brulures::recul);
      MinecraftForge.EVENT_BUS.addListener(Brulures::tick);
   }

   /**
    * Le nuage ou l'éclaboussure d'un molotov vient d'allumer {@code victime} (code du moteur tactique, juste après
    * {@code setSecondsOnFire}) : son feu brûle au nom de {@code lanceur}. Sans lanceur vivant : rien.
    */
   public static void molotov(Entity victime, @Nullable Entity lanceur) {
      if (!(victime instanceof LivingEntity v) || v.level().isClientSide() || !(lanceur instanceof LivingEntity l)) return;
      Marque m = MARQUES.computeIfAbsent(v, k -> new Marque());
      m.genre = Genre.MOLOTOV;
      m.auteur = l.getUUID();
      m.jusqua = v.level().getGameTime() + Math.max(0, v.getRemainingFireTicks()) + APRES_MOLOTOV;
   }

   /** Dégât de feu sans auteur d'une victime marquée : rejoué au nom de l'auteur de sa brûlure. */
   private static void attaque(LivingAttackEvent e) {
      if (MARQUES.isEmpty() || sansRecul != null) return;
      DamageSource source = e.getSource();
      if (source.getEntity() != null || !source.is(DamageTypes.ON_FIRE)) return;
      LivingEntity v = e.getEntity();
      if (!(v.level() instanceof ServerLevel niveau)) return;
      Marque m = MARQUES.get(v);
      if (m == null) return;
      if (niveau.getGameTime() > m.jusqua) {
         MARQUES.remove(v);
         return;
      }
      if (!(niveau.getEntity(m.auteur) instanceof LivingEntity auteur) || !auteur.isAlive()) return;
      e.setCanceled(true);
      blesser(v, auteur, e.getAmount());
   }

   /** Dégât de feu au nom de {@code auteur} (cause seulement : ni armure de mêlée, ni coup « au travers »), sans recul. */
   static void blesser(LivingEntity v, LivingEntity auteur, float montant) {
      DamageSource feu = new DamageSource(v.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
         .getHolderOrThrow(DamageTypes.ON_FIRE), null, auteur);
      sansRecul = v;
      try {
         v.hurt(feu, montant);
      } finally {
         sansRecul = null;
      }
   }

   private static void recul(LivingKnockBackEvent e) {
      if (sansRecul != null && e.getEntity() == sansRecul) e.setCanceled(true);
   }

   private static void tick(TickEvent.ServerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || MARQUES.isEmpty() || ++compteur < PURGE) return;
      compteur = 0;
      MARQUES.entrySet().removeIf(x -> x.getKey().isRemoved() || x.getKey().level().getGameTime() > x.getValue().jusqua);
   }
}
