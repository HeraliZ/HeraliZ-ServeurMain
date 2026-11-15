package fr.heraliz.tactique.serveur;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heraliz.noyau.api.ATerre;
import java.lang.reflect.Field;
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
import net.minecraftforge.fml.LogicalSide;
import org.slf4j.Logger;

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
 *   <li><b>Balles incendiaires</b> (combat M3, décision du propriétaire du 15/11) : une balle de TaCZ qui enflamme
 *   (munitions incendiaires : {@code "ignite": {"entity": true, "block": false}} dans les données du gunpack) met la cible,
 *   joueur ou créature, en feu {@link #FEU_BALLE} ticks (3 s, visible), et lui retire UN seul tic de feu de
 *   {@link #TIC_BALLE} PV (0,5 cœur) par balle qui touche, au nom du tireur (EN COMBAT, crédit du tueur, chiffres). Les
 *   dégâts de feu de Minecraft (1 PV par seconde tant qu'elle brûle) ne s'appliquent pas à ce feu-là seulement : une
 *   cible qui brûlait déjà d'un vrai feu (feu, lave, molotov), ou dont le feu est rallongé ensuite par l'un d'eux, brûle
 *   comme d'habitude. L'eau éteint ce feu comme les autres. Un seul tic par cible et par tick (les plombs d'une même
 *   cartouche). Pas de tic sur une cible à terre (il l'achèverait ; son feu ne la blesse pas non plus) ; rien si la balle
 *   a été refusée (zone sans PvP, protections : le feu de TaCZ est alors éteint). Jamais de feu sur les blocs (données du
 *   gunpack, et
 *   {@code IgniteBlock = false} de {@code tacz-common.toml}).</li>
 * </ul>
 * Fil du serveur. Rien ne tourne sans brûlure marquée ; les marques sont oubliées à la fin de leur temps (contrôle toutes
 * les 5 s, seulement s'il en reste).
 */
public final class Brulures {
   /** Temps (ticks) pendant lequel le feu d'un molotov reste au nom du lanceur, après le feu restant au dernier allumage. */
   static final int APRES_MOLOTOV = 140;
   /** Feu d'une balle incendiaire : 3 s. */
   static final int FEU_BALLE = 60;
   /** Tic de feu d'une balle incendiaire : 1 PV (0,5 cœur). */
   static final float TIC_BALLE = 1.0F;
   private static final int PURGE = 100;
   private static final Logger LOG = LogUtils.getLogger();

   private enum Genre { MOLOTOV, BALLE }

   /** Brûlure marquée d'une victime : genre, auteur, fin (heure du monde). */
   private static final class Marque {
      Genre genre;
      UUID auteur;
      long jusqua;
   }

   private static final Map<LivingEntity, Marque> MARQUES = new IdentityHashMap<>();
   /** Cibles qui ont déjà eu le tic d'une balle incendiaire pendant ce tick (vidé à la fin du tick). */
   private static final Map<LivingEntity, Boolean> TICS = new IdentityHashMap<>();
   /** Balle de TaCZ en train de toucher (avant puis après ses dégâts, dans le même appel de TaCZ) et feu de sa cible avant. */
   @Nullable
   private static Entity balleEnCours;
   private static int feuAvant;
   /** Champ « enflamme » des balles de TaCZ (privé, sans accesseur) ; null : introuvable. */
   @Nullable
   private static Field enflamme;
   private static boolean cherche;
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
      // après les réglages des dégâts (DegatsArmes, plafond des fusils à pompe : un plomb annulé n'enflamme pas)
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, Brulures::avantBalle);
      MinecraftForge.EVENT_BUS.addListener(Brulures::apresBalle);
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
      long maintenant = niveau.getGameTime();
      if (maintenant > m.jusqua) {
         MARQUES.remove(v);
         return;
      }
      if (m.genre == Genre.BALLE) {
         // feu de nos balles seulement : chaque balle a déjà fait son tic. Un feu plus long que le nôtre vient d'ailleurs
         // (feu, lave, molotov...) : il brûle comme d'habitude
         if (v.getRemainingFireTicks() <= m.jusqua - maintenant + 1) {
            e.setCanceled(true);
         } else {
            MARQUES.remove(v);
         }
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

   /** Avant les dégâts d'une balle de TaCZ (et avant son « enflamme ») : feu de la cible avant la balle. */
   private static void avantBalle(EntityHurtByGunEvent.Pre e) {
      balleEnCours = null;
      if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getHurtEntity() instanceof LivingEntity v) || !incendiaire(e.getBullet())) {
         return;
      }
      balleEnCours = e.getBullet();
      feuAvant = v.getRemainingFireTicks();
   }

   /** Après les dégâts d'une balle incendiaire (cible encore en vie) : 3 s de feu et le tic du tireur. */
   private static void apresBalle(EntityHurtByGunEvent.Post e) {
      Entity balle = balleEnCours;
      balleEnCours = null;
      if (balle == null || balle != e.getBullet() || !(e.getHurtEntity() instanceof LivingEntity v)
            || !(v.level() instanceof ServerLevel niveau) || !v.isAlive() || v.fireImmune()) {
         return;
      }
      long maintenant = niveau.getGameTime();
      Marque m = MARQUES.get(v);
      if (m != null && (maintenant > m.jusqua || m.genre == Genre.BALLE && feuAvant > m.jusqua - maintenant + 1)) {
         MARQUES.remove(v); // marque finie, ou feu rallongé par autre chose depuis : ce n'est plus le nôtre
         m = null;
      }
      if (v.invulnerableTime == 0) {
         // les deux coups de la balle ont été refusés (zone sans PvP, protections) : ni feu, ni tic
         if (feuAvant <= 0) v.clearFire();
         return;
      }
      if (m == null && feuAvant > 0) {
         // il brûlait déjà d'un vrai feu : il continue comme d'habitude
      } else if (m == null || m.genre == Genre.BALLE) {
         v.setSecondsOnFire(FEU_BALLE / 20);
         if (m == null) {
            m = new Marque();
            MARQUES.put(v, m);
         }
         m.genre = Genre.BALLE;
         m.auteur = e.getAttacker() != null ? e.getAttacker().getUUID() : null;
         m.jusqua = maintenant + v.getRemainingFireTicks();
      }
      // molotov en cours : son feu continue au nom de son lanceur ; la balle n'ajoute que son tic
      LivingEntity tireur = e.getAttacker();
      if (tireur == null || !tireur.isAlive() || ATerre.aTerreReanimation(v) || TICS.put(v, Boolean.TRUE) != null) return;
      v.invulnerableTime = 0; // comme TaCZ avant chacun de ses coups
      blesser(v, tireur, TIC_BALLE);
   }

   /** Balle de TaCZ qui enflamme (champ privé {@code igniteEntity} de TaCZ 1.1.8, lu par réflexion, comme BallesTrain). */
   private static boolean incendiaire(@Nullable Entity balle) {
      if (!(balle instanceof EntityKineticBullet)) return false;
      if (!cherche) {
         cherche = true;
         try {
            enflamme = EntityKineticBullet.class.getDeclaredField("igniteEntity");
            enflamme.setAccessible(true);
         } catch (ReflectiveOperationException | RuntimeException ex) {
            enflamme = null;
            LOG.warn("[brûlures] champ « igniteEntity » des balles de TaCZ introuvable : balles incendiaires sans effet ({})", ex.toString());
         }
      }
      try {
         return enflamme != null && enflamme.getBoolean(balle);
      } catch (IllegalAccessException | RuntimeException ex) {
         return false;
      }
   }

   private static void recul(LivingKnockBackEvent e) {
      if (sansRecul != null && e.getEntity() == sansRecul) e.setCanceled(true);
   }

   private static void tick(TickEvent.ServerTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      if (!TICS.isEmpty()) TICS.clear();
      if (MARQUES.isEmpty() || ++compteur < PURGE) return;
      compteur = 0;
      MARQUES.entrySet().removeIf(x -> x.getKey().isRemoved() || x.getKey().level().getGameTime() > x.getValue().jusqua);
   }
}
