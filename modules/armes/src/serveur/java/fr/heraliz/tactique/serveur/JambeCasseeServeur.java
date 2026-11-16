package fr.heraliz.tactique.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.tactique.jambe.JambeCassee;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import org.slf4j.Logger;

/**
 * Jambe cassée sans perte des coups (28/10, retour du propriétaire : « avec la jambe cassée, on ne peut plus frapper ni
 * les zombies ni les joueurs »).
 * <p>
 * Cause : l'effet {@code geckolib:broken_bone} de geckolib222 (contenu ArkaniaZ, jar d'un autre auteur, jamais modifié)
 * pose toutes les secondes, en plus de Lenteur III, <b>Faiblesse II</b> pour 5 000 ticks, sans particules ni icône.
 * Faiblesse II retire 8 points d'attaque : une arme de mêlée GeckoLib (batte, machette, hache… 6 à 9 points) ne fait plus
 * que 0 à 1 point, à mains nues rien. Et l'attelle retire la jambe cassée et la lenteur, pas la faiblesse : on restait
 * sans coups jusqu'à 4 min après s'être soigné.
 * <p>
 * Ici, seule cette Faiblesse-là est refusée (reconnue à sa forme : niveau II, 5 000 ticks, ambiante, sans particules ; nos
 * autres faiblesses sont de niveau I) quand le joueur a la jambe cassée. La lenteur et les règles de saut
 * ({@link JambeCassee}) ne changent pas. À la connexion, une faiblesse de cette forme restée sur le joueur est retirée.
 * <p>
 * <b>Saut sans élan décidé aussi par le serveur</b> (audit du 15/11, combat B3) : le saut est joué chez le joueur
 * ({@link JambeCassee}) ; un jeu modifié pouvait garder le bond de la course. Le serveur, qui voit le saut quand il reçoit
 * le déplacement, mesure la distance au sol parcourue jusqu'à l'atterrissage et la compare à ce que permet un saut sans
 * élan ({@link #permis}) : temps en l'air déduit de la hauteur d'arrivée (courbe de saut de Minecraft : sans dépendre
 * des retards du réseau ni du serveur), vitesse de départ permise deux fois plus forte que celle de notre saut, plus la
 * poussée de l'air et une marge. Au-delà, le joueur est ramené au point de départ du saut (rien d'autre). Jamais jugé :
 * coup reçu pendant le saut (recul), véhicule, eau, échelle, élytres, lévitation ou chute lente, sol glissant (glace),
 * long vol (plus de 2 s : chute), créatif et spectateur. Rien ne tourne sans saut en cours d'un joueur à la jambe
 * cassée.
 */
public final class JambeCasseeServeur {
   private static final int DUREE_GECKOLIB = 5000;
   private static final int NIVEAU_GECKOLIB = 1;
   private static final Logger LOG = LogUtils.getLogger();
   /** Vitesse de départ au sol permise (blocs par tick) : notre saut en donne au plus 0,094 (course, vitesse I, insensible). */
   private static final double DEPART_MAX = 0.13;
   /** Marge en blocs (arrondis, bord de bloc). */
   private static final double MARGE = 0.6;
   /** Au-delà (ticks en l'air), c'est une chute : pas jugé. */
   private static final int DUREE_MAX = 40;
   /** Distance au sol permise après t ticks en l'air (t de 0 à DUREE_MAX). */
   private static final double[] PERMIS = new double[DUREE_MAX + 1];
   /** Sauts en cours des joueurs à la jambe cassée : point de départ et heure. */
   private static final Map<UUID, Saut> SAUTS = new HashMap<>();

   private record Saut(double x, double y, double z) {
   }

   static {
      // déplacement en l'air de Minecraft, sans course : poussée 0,02 par tick, puis frottement de l'air 0,91
      double v = DEPART_MAX;
      double total = MARGE;
      PERMIS[0] = total;
      for (int t = 1; t <= DUREE_MAX; t++) {
         total += v + 0.02;
         v = (v + 0.02) * 0.91;
         PERMIS[t] = total;
      }
   }

   private JambeCasseeServeur() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(JambeCasseeServeur::applicable);
      MinecraftForge.EVENT_BUS.addListener(JambeCasseeServeur::connexion);
      MinecraftForge.EVENT_BUS.addListener(JambeCasseeServeur::saut);
      MinecraftForge.EVENT_BUS.addListener(JambeCasseeServeur::atterrissage);
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> SAUTS.remove(e.getEntity().getUUID()));
   }

   /** Le serveur voit le saut (déplacement reçu) : point de départ, si le saut peut être jugé. */
   private static void saut(LivingEvent.LivingJumpEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p.isCreative() || p.isSpectator() || !JambeCassee.cassee(p) || excuse(p)) {
         return;
      }
      // sol glissant (glace) : l'élan au sol y est bien plus fort, pas jugé
      if (p.level().getBlockState(p.getOnPos()).getBlock().getFriction() > 0.61F) return;
      SAUTS.put(p.getUUID(), new Saut(p.getX(), p.getY(), p.getZ()));
   }

   /** À l'atterrissage : distance au sol depuis le départ, comparée à un saut sans élan. */
   private static void atterrissage(TickEvent.PlayerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || SAUTS.isEmpty() || !(e.player instanceof ServerPlayer p)) return;
      Saut s = SAUTS.get(p.getUUID());
      if (s == null) return;
      if (p.hurtTime > 0 || excuse(p) || !p.isAlive() || p.getY() - s.y() < -12.0) {
         SAUTS.remove(p.getUUID()); // recul d'un coup, véhicule, eau... ou longue chute : pas jugé
         return;
      }
      if (!p.onGround()) return;
      SAUTS.remove(p.getUUID());
      int t = tempsEnLAir(p.getY() - s.y());
      if (t < 0) return;
      double dx = p.getX() - s.x(), dz = p.getZ() - s.z();
      double d = Math.sqrt(dx * dx + dz * dz);
      if (d <= permis(t) || d > 16.0) return; // au-delà de 16 blocs : téléporté, pas un saut
      p.connection.teleport(s.x(), s.y(), s.z(), p.getYRot(), p.getXRot());
      LOG.info("[jambe cassée] {} : saut de {} blocs (permis {}), ramené au départ du saut", p.getGameProfile().getName(),
         String.format(java.util.Locale.ROOT, "%.2f", d), String.format(java.util.Locale.ROOT, "%.2f", permis(t)));
   }

   /** Ce qui empêche de juger un saut (déplacement qui ne vient pas que du saut). */
   private static boolean excuse(ServerPlayer p) {
      return p.isPassenger() || p.isFallFlying() || p.isInWater() || p.isInLava() || p.onClimbable() || p.getAbilities().flying
         || p.hasEffect(MobEffects.LEVITATION) || p.hasEffect(MobEffects.SLOW_FALLING) || p.hasEffect(MobEffects.JUMP);
   }

   /** Distance au sol permise après {@code t} ticks en l'air (un tick de plus pour l'arrondi). */
   static double permis(int t) {
      return PERMIS[Math.max(0, Math.min(DUREE_MAX, t + 1))];
   }

   /**
    * Ticks en l'air d'un saut de Minecraft (0,42 vers le haut, gravité 0,08, frottement 0,98 par tick) qui retombe à
    * {@code dy} blocs de son départ ; -1 si plus de {@link #DUREE_MAX} ticks ou si {@code dy} est trop haut.
    */
   static int tempsEnLAir(double dy) {
      if (dy > 1.3) return -1;
      double y = 0.0;
      double v = 0.42;
      for (int t = 1; t <= DUREE_MAX; t++) {
         y += v;
         v = (v - 0.08) * 0.98;
         if (v < 0.0 && y <= dy + 1.0E-3) return t;
      }
      return -1;
   }

   /** La faiblesse posée par l'effet de jambe cassée de geckolib222 ? */
   private static boolean faiblesseJambe(MobEffectInstance i) {
      return i != null && i.getEffect() == MobEffects.WEAKNESS && i.getAmplifier() == NIVEAU_GECKOLIB && i.isAmbient()
            && !i.isVisible() && i.getDuration() >= DUREE_GECKOLIB - 40;
   }

   private static void applicable(MobEffectEvent.Applicable e) {
      MobEffectInstance i = e.getEffectInstance();
      if (i.getEffect() == MobEffects.WEAKNESS && e.getEntity() instanceof ServerPlayer p && faiblesseJambe(i)
            && JambeCassee.cassee(p)) {
         e.setResult(Event.Result.DENY);
      }
   }

   /** Joueur sauvegardé avec l'ancienne faiblesse (avant ce correctif, ou soigné à l'attelle) : retirée. */
   private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p)) return;
      MobEffectInstance i = p.getEffect(MobEffects.WEAKNESS);
      if (i != null && i.getAmplifier() == NIVEAU_GECKOLIB && i.isAmbient() && !i.isVisible()) {
         p.removeEffect(MobEffects.WEAKNESS);
      }
   }
}
