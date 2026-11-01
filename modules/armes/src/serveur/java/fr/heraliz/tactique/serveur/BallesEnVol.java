package fr.heraliz.tactique.serveur;

import com.mojang.logging.LogUtils;
import com.tacz.guns.entity.EntityKineticBullet;
import fr.heralisz.tactical.config.ServerConfig;
import fr.heraliz.noyau.api.Fuites;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * Balles de TaCZ ({@code tacz:bullet}, {@link EntityKineticBullet}) côté serveur : elles volent comme TaCZ le veut (un pas
 * à chaque tick, jusqu'à toucher ou finir leur vie) et aucune ne reste figée dans le monde. Banc du 01/11 à 150 bots :
 * 2 300 à 3 150 balles présentes en même temps sur ~4 100 entités, ~40 seulement qui tournaient à chaque tick.
 * <p>
 * <b>Ce que fait TaCZ 1.1.8</b> (jar intact) : la balle avance de sa vitesse à chaque tick (médiane des gunpacks 240 m/s x
 * GlobalBulletSpeedModifier 2 = 24 blocs par tick) et se retire quand {@code tickCount >= life - 1}, test fait DANS son tick
 * ({@code life} = vie de la fiche, 0,5 à 1,1 s pour presque toutes nos armes). Elle est déclarée {@code noSave()} : jamais
 * écrite avec le tronçon (rien ne revient après un redémarrage), elle disparaît quand son tronçon se décharge.
 * <p>
 * <b>Cause 1, balles figées</b> : Minecraft ne fait tourner une entité que dans la zone simulée (tronçons à
 * {@code simulation-distance} d'un joueur : {@code DistanceManager.inEntityTickingRange}). Une balle qui en sort (à 24
 * blocs par tick, c'est après 3 ou 4 ticks pour un tir manqué) n'est plus jamais tournée : ni son pas, ni le test de sa
 * vie. Elle reste là, suivie par Minecraft (portée « other » de spigot.yml, 64 blocs), tant qu'un joueur garde son tronçon
 * chargé (distance de vue + 2). Un joueur qui approche reçoit son apparition : TaCZ la fait voler chez lui depuis là
 * (traçante qui part de nulle part) ; un joueur qui la remet dans la zone simulée la réveille : elle repart et peut le
 * toucher, des minutes après le tir. <b>Correction</b> : à la fin de chaque tick du serveur, chaque balle suivie ici dont
 * le {@code tickCount} n'a pas bougé depuis le passage d'avant (le monde ne l'a pas tournée pendant un tick entier) est
 * retirée. Elle est alors à plus de 16 x simulation-distance blocs de tout joueur (65 blocs à la simulation 4), donc hors
 * de la portée de suivi de tous : personne ne la voyait plus, rien ne disparaît à l'écran d'un joueur. Une balle qui
 * avance n'est jamais retirée. Réglage {@code [balles] retirer_figees}.
 * <p>
 * <b>Cause 2, tirs à distance en retard ou perdus</b> : Mohist applique la portée d'activation de Spigot
 * ({@code org.spigotmc.ActivationRange}, {@code entity-activation-range: misc 16} de spigot.yml). Une balle est une
 * entité « misc » ({@code Projectile} n'est ni un monstre ni un animal) et n'est pas dans la liste des toujours actives
 * de Spigot (ThrowableProjectile, AbstractHurtingProjectile, fusées, tridents…). À plus de 16 blocs de tout joueur, elle
 * ne tourne que si son activation n'a pas expiré ou si {@code (currentTick - activatedTick - 1) % 20 == 0} (alors,
 * jamais au sol, elle est « immunisée » 20 ticks de plus). Or {@code currentTick} de Mohist est l'horloge / 50 ms : à 20
 * ticks par seconde il avance de 1 et la balle retrouve tout de suite son immunité ; dès qu'un tick dure plus de 50 ms,
 * il saute de 2 ou 3, l'instant du test est manqué et la balle s'arrête jusqu'au prochain multiple de 20 (près d'une
 * seconde) en vieillissant quand même (Spigot compte le tick sauté). Simulation (tirs sur un joueur à 60 blocs et plus) :
 * ticks de 48 ms en moyenne, 6 % des balles n'arrivent jamais ; 55 ms, 17 % ; 100 ms, 40 % perdues et 40 % en retard
 * d'environ 10 ticks. <b>Correction</b> : à son arrivée dans le monde, le champ {@code activatedTick} de Mohist de la
 * balle est mis au maximum : Spigot la voit toujours active (comme une flèche en vol), sans rien changer pour les autres
 * entités. Réglage {@code [balles] toujours_actives}. Sans Mohist (Forge seul), le champ n'existe pas : rien à faire.
 * <p>
 * Coût : une balle de plus dans un petit tableau à chaque tir (~5 par tick à 150 joueurs en combat) et, à chaque tick,
 * un passage sur les seules balles en vol (~40 à 50) : quelques microsecondes. Aucun parcours des entités du monde.
 */
final class BallesEnVol {
   private static final Logger LOG = LogUtils.getLogger();
   /** Réglages relus une fois par seconde (ForgeConfigSpec.get() n'est pas gratuit). */
   private static final int RELECTURE = 20;
   /** Bilan dans le journal au plus toutes les 30 min, seulement si des balles ont été retirées. */
   private static final int BILAN = 20 * 60 * 30;
   /** Balle pas encore vue par un passage : son tickCount n'est pas encore noté (aucune balle n'en a de négatif). */
   private static final int PAS_VUE = -1;
   /** Activation de Spigot donnée une fois pour toutes : Spigot compare {@code activatedTick >= currentTick}. */
   private static final long TOUJOURS = Long.MAX_VALUE;

   /** Balles en vol suivies (même ordre que {@link #vues}), {@link #nb} premières cases. */
   private static EntityKineticBullet[] balles = new EntityKineticBullet[64];
   /** tickCount de chaque balle au passage d'avant. */
   private static int[] vues = new int[64];
   private static int nb;
   private static boolean toujoursActives = true;
   private static boolean retirerFigees = true;
   /** {@code Entity.activatedTick} de Mohist (public long), null sans Mohist ou s'il est devenu inaccessible. */
   @Nullable
   private static Field activation = champActivation();
   /**
    * Portée d'activation de Spigot présente (Mohist) ? Alors une balle ne peut être retirée parce qu'elle n'avance plus que si
    * elle est rendue toujours active : sinon une balle à plus de 16 blocs de tout joueur, seulement mise en pause par Spigot,
    * serait prise pour une balle figée et retirée en plein vol (tir à distance perdu).
    */
   private static final boolean SPIGOT = classe("org.spigotmc.ActivationRange");
   private static int horloge;
   private static long retirees;
   private static long retireesAuBilan;

   private BallesEnVol() {
   }

   static void brancher() {
      // LOWEST : seulement les balles vraiment ajoutées au monde (pas annulées par un autre mod), comme BallesTrain
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, BallesEnVol::arrivee);
      MinecraftForge.EVENT_BUS.addListener(BallesEnVol::finDuTick);
      MinecraftForge.EVENT_BUS.addListener(BallesEnVol::demarre);
      MinecraftForge.EVENT_BUS.addListener(BallesEnVol::arret);
      Fuites.surveiller("tactique.balles_en_vol", () -> nb);
   }

   private static void arrivee(EntityJoinLevelEvent e) {
      if (!(e.getEntity() instanceof EntityKineticBullet balle) || e.getLevel().isClientSide()) {
         return;
      }
      if (toujoursActives) {
         toujoursActive(balle);
      }
      if (retirerFigees && retraitSur()) {
         suivre(balle);
      }
   }

   /** Retirer les balles qui n'avancent plus est sûr : pas de Spigot, ou balles rendues toujours actives. */
   private static boolean retraitSur() {
      return !SPIGOT || (toujoursActives && activation != null);
   }

   private static boolean classe(String nom) {
      try {
         Class.forName(nom, false, BallesEnVol.class.getClassLoader());
         return true;
      } catch (ClassNotFoundException | LinkageError absente) {
         return false;
      }
   }

   /** Fin du tick du serveur : tous les mondes ont tourné leurs entités. */
   private static void finDuTick(TickEvent.ServerTickEvent e) {
      if (e.phase != TickEvent.Phase.END) {
         return;
      }
      if (++horloge % RELECTURE == 0) {
         relire();
      }
      if (horloge % BILAN == 0) {
         bilan();
      }
      if (nb > 0 && !retraitSur()) {
         vider();   // activation de Spigot laissée à Spigot : plus aucun retrait (comme avant)
         return;
      }
      // à l'envers : la dernière case, déjà vue, vient boucher le trou d'une balle oubliée
      for (int i = nb - 1; i >= 0; i--) {
         EntityKineticBullet balle = balles[i];
         if (!balle.isRemoved()) {
            int t = balle.tickCount;
            if (t != vues[i]) {
               vues[i] = t;
               continue;
            }
            // pas un seul pas depuis le passage d'avant : son monde ne la tourne plus (hors de la zone simulée, ou
            // monde sans joueur qui ne tourne plus ses entités) ; personne ne la voit
            balle.discard();
            retirees++;
         }
         oublier(i);
      }
   }

   private static void suivre(EntityKineticBullet balle) {
      if (nb == balles.length) {
         balles = Arrays.copyOf(balles, nb * 2);
         vues = Arrays.copyOf(vues, nb * 2);
      }
      balles[nb] = balle;
      vues[nb] = PAS_VUE;
      nb++;
   }

   private static void oublier(int i) {
      nb--;
      balles[i] = balles[nb];
      vues[i] = vues[nb];
      balles[nb] = null;
   }

   private static void vider() {
      Arrays.fill(balles, 0, nb, null);
      nb = 0;
   }

   /** Spigot (Mohist) : la balle compte toujours comme active, sa portée d'activation ne l'arrête plus jamais. */
   private static void toujoursActive(Entity balle) {
      Field f = activation;
      if (f == null) {
         return;
      }
      try {
         f.setLong(balle, TOUJOURS);
      } catch (IllegalAccessException | RuntimeException ex) {
         activation = null;
         LOG.warn("[tactique] Balles de TaCZ : activation de Spigot pas modifiable ({}), balles laissées à Spigot", ex.toString());
      }
   }

   @Nullable
   private static Field champActivation() {
      try {
         Field f = Entity.class.getField("activatedTick");
         int m = f.getModifiers();
         if (f.getType() == long.class && !Modifier.isStatic(m) && !Modifier.isFinal(m)) {
            return f;
         }
      } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
         // Forge seul : pas de portée d'activation de Spigot
      }
      return null;
   }

   private static void relire() {
      toujoursActives = lire(ServerConfig.BALLES_TOUJOURS_ACTIVES, true);
      boolean retirer = lire(ServerConfig.BALLES_RETIRER_FIGEES, true);
      if (!retirer) {
         vider();
      }
      retirerFigees = retirer;
   }

   private static void demarre(ServerStartedEvent e) {
      relire();
      LOG.info("[tactique] Balles de TaCZ : toujours actives = {} ({}), figées retirées = {}", toujoursActives,
         activation != null ? "portée d'activation de Spigot (Mohist) ignorée pour elles"
            : SPIGOT ? "portée d'activation de Spigot présente mais pas modifiable" : "pas de portée d'activation sur ce serveur",
         retirerFigees && retraitSur());
   }

   private static void bilan() {
      long n = retirees - retireesAuBilan;
      if (n > 0) {
         LOG.info("[tactique] Balles de TaCZ : {} balles figées retirées en 30 min ({} depuis le démarrage), {} en vol", n, retirees, nb);
         retireesAuBilan = retirees;
      }
   }

   private static void arret(ServerStoppedEvent e) {
      vider();
      horloge = 0;
      retirees = 0;
      retireesAuBilan = 0;
   }

   private static boolean lire(@Nullable ForgeConfigSpec.BooleanValue valeur, boolean defaut) {
      try {
         if (valeur != null) {
            Boolean v = valeur.get();
            if (v != null) {
               return v;
            }
         }
      } catch (IllegalStateException | NullPointerException pasEncoreChargee) {
         // config serveur pas encore lue : valeur par défaut
      }
      return defaut;
   }
}
