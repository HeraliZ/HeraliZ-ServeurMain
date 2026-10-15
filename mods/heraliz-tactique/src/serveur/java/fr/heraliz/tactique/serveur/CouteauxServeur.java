package fr.heraliz.tactique.serveur;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSound;
import com.tacz.guns.sound.SoundManager;
import fr.heralisz.tactical.api.collision.ConeFilter;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import fr.heralisz.tactical.api.melee.AttackResult;
import fr.heraliz.tactique.allonge.CompatReanimation;
import fr.heraliz.tactique.couteaux.CoupsCouteau;
import fr.heraliz.tactique.couteaux.PaquetCoupCouteau;
import fr.heraliz.tactique.couteaux.PaquetCoupVu;
import fr.heraliz.tactique.couteaux.ReseauCouteaux;
import fr.heraliz.tactique.onglets.Couteaux;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Coups de couteau TaCZ, décidés par le serveur seul (voir {@link CoupsCouteau}). Le joueur demande un coup
 * ({@link PaquetCoupCouteau}) ; le serveur vérifie tout de son côté, puis frappe {@link CoupsCouteau#delai} ticks
 * plus tard (coup puissant : après la préparation de l'estoc), comme une arme de mêlée de LR (MeleeItemServeur) :
 * <ul>
 *    <li><b>refus</b> (rien ne se passe) : couteaux non frappés par HeraliZ ({@code actif}), joueur mort ou spectateur, pas
 *    de couteau TaCZ en main, à terre ou en train de fouiller ou réanimer (Réanimation), couteau en train d'être sorti
 *    (temps de sortie de TaCZ vu par le serveur, à {@link #SORTIE_TOLEREE_MS} près), cadence du coup précédent pas écoulée,
 *    coup précédent pas encore donné. La cadence est comptée en temps réel (un serveur qui rame ne refuse pas les coups
 *    d'un joueur honnête), à {@link #TOLERANCE_MS} près pour le décalage du réseau : un coup arrivé en avance est accepté,
 *    mais le suivant est compté depuis l'heure où il était dû, si bien qu'un jeu modifié ne frappe jamais plus vite que
 *    la cadence ;</li>
 *    <li><b>accepté</b> : les autres joueurs voient le coup en 3e personne (PaquetCoupVu, 15/10 : gestes des couteaux de
 *    LR, tranches en alternance pour le coup rapide, estoc pour le coup puissant) et l'entendent : coup rapide, son
 *    « melee_push » du couteau envoyé d'ici, à la distance des bruits d'arme de TaCZ (DefaultGunOtherSoundDistance) ;
 *    estoc, bruits plus lourds joués chez eux à la réception de PaquetCoupVu (rien d'ici, sinon il s'entendrait deux
 *    fois) ;</li>
 *    <li><b>coup</b> (si le même couteau est toujours en main, dans la même case) : tout être vivant dans le cône
 *    ({@link ConeFilter} de LR : portée jusqu'au point le plus proche de la cible, ouverture, vue dégagée), dégâts
 *    (attaque du joueur + {@code degats}) × 1 ou × {@code multiplicateur_lourd}, donnés par le coup de LR
 *    ({@link IMeleeWeapon#attackTarget} : événement d'attaque de Forge, que Réanimation, CRac et Spartan peuvent refuser,
 *    coup critique, recul, temps d'invincibilité). Une cible touchée : son d'impact du couteau (« flesh_hit ») pour tous,
 *    le frappeur compris.</li>
 * </ul>
 * Le coup de TaCZ (touche « Mêlée », 13 dégâts) n'est plus donné aux couteaux : {@code GunMeleeEvent} annulé côté
 * serveur (le jeu du joueur ne l'envoie plus, voir CouteauxClient ; un jeu modifié ne passerait pas non plus).
 */
final class CouteauxServeur {
   /** Avance pardonnée sur la cadence, en ms (deux demandes honnêtes peuvent arriver plus rapprochées qu'envoyées). */
   private static final long TOLERANCE_MS = 100L;
   /** Durée d'un tick de jeu, en ms. */
   private static final long MS_PAR_TICK = 50L;
   /** Temps de sortie restant (vu par le serveur, en ms) pardonné : le serveur voit la sortie un peu après le joueur. */
   private static final long SORTIE_TOLEREE_MS = 200L;
   /** Distance des bruits du couteau si la config de TaCZ n'est pas lisible (sa valeur par défaut). */
   private static final int DISTANCE_SON_DEFAUT = 16;

   /** État de chaque joueur qui a frappé (fil du serveur seulement). */
   private static final Map<UUID, Etat> ETATS = new HashMap<>();

   private static final class Etat {
      /** Heure (ms) où le coup suivant est dû : cadence du dernier coup accepté, comptée depuis l'heure où il était dû. */
      long pretMs = Long.MIN_VALUE / 4;
      /** Tick du coup en attente ; -1 : aucun. */
      int frappeA = -1;
      boolean lourd;
      /** Case de la barre et couteau (identifiant TaCZ) au moment de la demande. */
      int caseBarre;
      ResourceLocation arme;
   }

   private CouteauxServeur() {
   }

   static void brancher() {
      ReseauCouteaux.COUP_SERVEUR.brancher(CouteauxServeur::demande);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunMeleeEvent.class, CouteauxServeur::coupDeTacz);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.PlayerTickEvent.class, CouteauxServeur::tick);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class,
         e -> ETATS.remove(e.getEntity().getUUID()));
   }

   /** Coup de TaCZ (touche « Mêlée ») avec un couteau : refusé, le couteau frappe par HeraliZ. */
   private static void coupDeTacz(GunMeleeEvent e) {
      if (e.getLogicalSide().isServer() && CoupsCouteau.actif() && Couteaux.estCouteau(e.getGunItemStack())) {
         e.setCanceled(true);
      }
   }

   private static void demande(PaquetCoupCouteau.Demande d) {
      ServerPlayer joueur = d.joueur();
      if (!CoupsCouteau.actif() || !joueur.isAlive() || joueur.isSpectator()) {
         return;
      }
      ItemStack main = joueur.getMainHandItem();
      IGun arme = IGun.getIGunOrNull(main);
      if (arme == null || !Couteaux.estCouteau(main) || CompatReanimation.empeche(joueur)) {
         return;
      }
      if (IGunOperator.fromLivingEntity(joueur).getSynDrawCoolDown() > SORTIE_TOLEREE_MS) {
         return; // couteau en train d'être sorti
      }
      long maintenant = System.nanoTime() / 1_000_000L;
      Etat etat = ETATS.computeIfAbsent(joueur.getUUID(), u -> new Etat());
      if (etat.frappeA >= 0 || maintenant < etat.pretMs - TOLERANCE_MS) {
         return;
      }
      etat.pretMs = Math.max(maintenant - TOLERANCE_MS, etat.pretMs) + CoupsCouteau.cadence(d.lourd()) * MS_PAR_TICK;
      etat.frappeA = joueur.server.getTickCount() + CoupsCouteau.delai(d.lourd());
      etat.lourd = d.lourd();
      etat.caseBarre = joueur.getInventory().selected;
      etat.arme = arme.getGunId(main);
      // les autres joueurs : le coup en 3e personne (tranche ou estoc, PaquetCoupVu) ; bruit de la tranche envoyé d'ici,
      // ceux de l'estoc joués chez eux à la réception du paquet
      ReseauCouteaux.envoyerAutour(joueur, new PaquetCoupVu(joueur.getId(), d.lourd()));
      if (!d.lourd()) {
         son(joueur, arme, main, SoundManager.MELEE_PUSH, false);
      }
   }

   private static void tick(TickEvent.PlayerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer joueur)) {
         return;
      }
      Etat etat = ETATS.get(joueur.getUUID());
      if (etat == null || etat.frappeA < 0 || joueur.server.getTickCount() < etat.frappeA) {
         return;
      }
      etat.frappeA = -1;
      frapper(joueur, etat);
   }

   private static void frapper(ServerPlayer joueur, Etat etat) {
      if (!CoupsCouteau.actif() || !joueur.isAlive() || joueur.isSpectator() || CompatReanimation.empeche(joueur)) {
         return;
      }
      ItemStack main = joueur.getMainHandItem();
      IGun arme = IGun.getIGunOrNull(main);
      if (arme == null || joueur.getInventory().selected != etat.caseBarre || !etat.arme.equals(arme.getGunId(main))) {
         return; // couteau rangé ou changé entre la demande et le coup
      }
      float degats = (float) ((joueur.getAttributeValue(Attributes.ATTACK_DAMAGE) + CoupsCouteau.degats())
         * (etat.lourd ? CoupsCouteau.multiplicateurLourd() : 1.0D));
      if (degats <= 0.0F) {
         return;
      }
      float recul = CoupsCouteau.recul(etat.lourd);
      ConeFilter cone = new ConeFilter(CoupsCouteau.portee(), CoupsCouteau.angle(etat.lourd));
      boolean touche = false;
      for (Entity cible : cone.filterTargets(joueur, joueur.getEyePosition(), joueur.getLookAngle())) {
         if (cible != joueur && cible instanceof LivingEntity && cible.isAlive()) {
            AttackResult r = IMeleeWeapon.attackTarget(joueur, cible, main, degats, recul);
            touche |= r.hit();
         }
      }
      if (touche) {
         son(joueur, arme, main, SoundManager.FLESH_HIT_SOUND, true);
      }
   }

   /** Un son du couteau (celui de son affichage TaCZ) pour les joueurs proches, et pour le frappeur si demandé. */
   private static void son(ServerPlayer joueur, IGun arme, ItemStack main, String nom, boolean aussiAuFrappeur) {
      int distance = distanceSon();
      if (distance <= 0) {
         return;
      }
      float hauteur = 0.9F + joueur.getRandom().nextFloat() * 0.125F;
      ResourceLocation id = arme.getGunId(main);
      ResourceLocation affichage = arme.getGunDisplayId(main);
      SoundManager.sendSoundToNearby(joueur, distance, id, affichage, nom, 1.0F, hauteur);
      if (aussiAuFrappeur) {
         NetworkHandler.sendToClientPlayer(new ServerMessageSound(joueur.getId(), id, affichage, nom, 1.0F, hauteur, distance), joueur);
      }
   }

   private static int distanceSon() {
      try {
         return GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get();
      } catch (RuntimeException illisible) {
         return DISTANCE_SON_DEFAUT;
      }
   }
}
