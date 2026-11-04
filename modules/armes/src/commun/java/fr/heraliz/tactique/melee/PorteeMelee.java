package fr.heraliz.tactique.melee;

import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.collision.ITargetFilter;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import fr.heralisz.tactical.item.melee.CombatData;
import fr.heraliz.tactique.onglets.Couteaux;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Portée des armes de corps à corps de HeraliZ Tactique, réglée en un seul endroit : {@code portee_max} de la section
 * {@code [melee]} de {@code heralisz_tactical-server.toml} (3 blocs par défaut).
 * <ul>
 *    <li>Armes de mêlée (moteur repris de LR Tactical, fiches {@code data/heralisz_melee/index/melee}) : chaque coup
 *    (gauche, droit, combos, balayages) a sa zone ({@code ConeFilter}, {@code OBBFilter}, {@code RayFilter}) ; sa
 *    portée est ramenée à {@link #max()} si la fiche dit plus (les fiches disent 4 à 4,5, la masse 3 et 3,5). Le joueur
 *    choisit ses cibles avec cette portée (config serveur envoyée par Forge à la connexion), le serveur la revérifie
 *    ({@link #aPortee}). Couteaux à vie : 2 blocs dans leur fiche depuis le 04/11, 1 le 03/11 (skins_couteaux.py).</li>
 *    <li>Couteaux TaCZ ({@code fr.heraliz.tactique.couteaux.CoupsCouteau}) et couteaux de GeckoLib : {@link #couteaux()}
 *    ({@code portee_couteaux}, 2 blocs depuis le 04/11, 1 le 03/11).</li>
 *    <li>Tout coup porté avec une de ces armes en main, coup de Minecraft compris, est revérifié par le serveur
 *    ({@link #porteeEnMain}, PorteeMeleeServeur).</li>
 * </ul>
 * Mesure : des yeux du joueur au point le plus proche de la boîte de la cible, comme le cône de LR et comme l'anticheat
 * Spartan (contrôle « Reach » : 3,3 + au moins 0,3 de marge ; au-delà, il annule le coup et bloque les coups du joueur
 * 3 s). Avec 3, un coup accepté par HeraliZ ne fait jamais réagir Spartan.
 */
public final class PorteeMelee {
   /** Réglage par défaut : la portée d'un coup de Minecraft. */
   public static final double DEFAUT = 3.0D;
   /**
    * Tolérance du serveur sur une cible annoncée par le joueur (armes de LR : le joueur envoie ses cibles) : entre ce
    * qu'il voyait et l'arrivée du paquet, la cible a pu bouger. 3 + 0,4 reste sous le seuil de Spartan (au moins 3,6).
    */
   public static final double MARGE_SERVEUR = 0.4D;

   private PorteeMelee() {
   }

   /** Portée maximale de toutes les armes de corps à corps (config serveur, 3 par défaut). */
   public static double max() {
      return valeur(ServerConfig.MELEE_PORTEE_MAX, DEFAUT);
   }

   /** Réglage par défaut de la portée des couteaux (03/11, choix du propriétaire : 1 bloc au plus, clic gauche et droit). */
   public static final double DEFAUT_COUTEAUX = 2.0D;   // 04/11 (propriétaire) : 2 blocs (avant 1)

   /**
    * Portée des couteaux TaCZ et des couteaux de GeckoLib ({@code [melee] portee_couteaux}, 1 par défaut), jamais plus que
    * {@link #max()}. Les couteaux à vie (armes de mêlée de LR) ont la leur dans leur fiche (max_range de chaque coup,
    * écrit par modules/armes/outils/skins_couteaux.py, PORTEE_COUTEAUX : 1 aussi).
    */
   public static double couteaux() {
      return Math.min(valeur(ServerConfig.MELEE_PORTEE_COUTEAUX, DEFAUT_COUTEAUX), max());
   }

   /**
    * Portée de l'arme de corps à corps tenue, -1 si ce n'en est pas une (03/11) : couteau TaCZ ou couteau de GeckoLib :
    * {@link #couteaux()} ; arme de mêlée de LR : la plus longue portée des coups de sa fiche (gauche, droit, combos),
    * ramenée à {@link #max()} (1 pour les couteaux à vie et la batte depuis le 03/11). Lue par le serveur à chaque coup porté
    * (PorteeMeleeServeur), jamais à chaque image.
    */
   public static double porteeEnMain(ItemStack main) {
      if (main.isEmpty()) {
         return -1.0D;
      }
      if (main.getItem() instanceof IMeleeWeapon) {
         return HeraliszTacticalAPI.getMeleeIndex(main).map(PorteeMelee::porteeFiche).orElse(-1.0D);
      }
      return MeleeGeckolib.estCouteau(main) || Couteaux.estCouteau(main) ? couteaux() : -1.0D;
   }

   /** La plus longue portée des coups d'une fiche de mêlée de LR, plafonnée ; -1 si elle n'a aucun coup. */
   private static double porteeFiche(MeleeWeaponIndex<?> index) {
      CombatData combat = index.getData().getAttackInfo();
      if (combat == null) {
         return -1.0D;
      }
      double portee = -1.0D;
      for (List<CombatData.MeleeAttackInfo> coups : combat.attackInfo.values()) {
         for (CombatData.MeleeAttackInfo coup : coups) {
            ITargetFilter zone = coup.getHitbox() != null ? coup.getHitbox() : CombatData.DEFAULT_HITBOX;
            portee = Math.max(portee, zone.getMaxRange());
         }
      }
      return portee < 0.0D ? -1.0D : plafonner(portee);
   }

   /** La portée d'une arme, ramenée à la portée maximale si elle va plus loin. */
   public static double plafonner(double portee) {
      return Math.min(portee, max());
   }

   /** Distance des yeux de l'attaquant au point le plus proche de la boîte de la cible (0 si les yeux sont dedans). */
   public static double distance(Entity attaquant, Entity cible) {
      Vec3 oeil = attaquant.getEyePosition();
      AABB boite = cible.getBoundingBox();
      double x = Mth.clamp(oeil.x, boite.minX, boite.maxX);
      double y = Mth.clamp(oeil.y, boite.minY, boite.maxY);
      double z = Mth.clamp(oeil.z, boite.minZ, boite.maxZ);
      return oeil.distanceTo(new Vec3(x, y, z));
   }

   /**
    * Contrôle du serveur pour une cible annoncée par le joueur : à portée (déjà plafonnée), plus {@link #MARGE_SERVEUR}.
    * Avant (code de LR) : distance des pieds aux pieds, qui refusait des coups que le joueur voyait à portée.
    */
   public static boolean aPortee(Entity attaquant, Entity cible, double portee) {
      return distance(attaquant, cible) <= portee + MARGE_SERVEUR;
   }

   private static <T> T valeur(@Nullable ForgeConfigSpec.ConfigValue<T> valeur, T defaut) {
      try {
         if (valeur != null) {
            T v = valeur.get();
            if (v != null) {
               return v;
            }
         }
      } catch (IllegalStateException pasEncoreRecue) {
         // chez le joueur, la config serveur n'existe qu'une fois connecté : valeur par défaut en attendant
      }
      return defaut;
   }
}
