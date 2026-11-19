package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.api.Arrivees;
import fr.heraliz.noyau.api.Notifs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

/**
 * Partie serveur de {@link Arrivees} (19/11, audit des lenteurs, point C1) : tickets d'arrivée, attentes, et {@code /tp} de
 * Minecraft. Reprend la méthode de {@code Prechargement} de CRac et du monde (tickets de 5 × 5 tronçons, action dès que les
 * 3 × 3 du centre sont complets, 3 s au plus), pour tous nos mods.
 * <p>{@code /tp} (commande de Minecraft, staff, {@code /execute … run tp} compris) : Forge appelle
 * {@code EntityTeleportEvent.TeleportCommand} au début de la téléportation, avec l'entité et le point d'arrivée, mais pas
 * le monde d'arrivée : {@code CommandeTpMixin} le note juste avant (avec le lacet, le tangage et les axes relatifs), sur le
 * même fil et dans le même tick. Un joueur envoyé vers une arrivée pas prête : l'événement est annulé, puis la même
 * téléportation est refaite par {@link #puis} dès que l'arrivée est prête (bandeau à celui qui a tapé la commande). Arrivée
 * prête, autre entité qu'un joueur, regard imposé ({@code facing}), contexte absent (mixin pas posé) : Minecraft, comme avant.
 * <p>Fil du serveur seulement ; rien ne tourne sans attente en cours. Réglages : {@code [perf] arrivees_prechargees} et
 * {@code tp_precharge}.
 */
public final class ArriveesServeur implements Arrivees.Service {
   private static final Logger LOG = LogUtils.getLogger();
   private static final ArriveesServeur INSTANCE = new ArriveesServeur();
   /** Rayon du ticket : 5 × 5 tronçons complets autour du point (et leur bordure, lue en tâche de fond). */
   private static final int RAYON_TICKET = 2;
   private static final TicketType<ChunkPos> TICKET = TicketType.create("heraliz_noyau_arrivee", Comparator.comparingLong(ChunkPos::toLong), 20 * 30);
   /** Sans fin : retiré par {@link #lacher}. */
   private static final TicketType<ChunkPos> TICKET_GARDE = TicketType.create("heraliz_noyau_arrivee_fixe", Comparator.comparingLong(ChunkPos::toLong));

   private static final class Attente {
      final ServerPlayer joueur;
      final ServerLevel niveau;
      final int cx, cz;
      final long debut, limite;
      final Runnable action;
      @Nullable
      final Runnable siAbandon;
      /** Rangée dans {@link #PRETES} pour être abandonnée (sinon : action). */
      boolean abandon;

      Attente(ServerPlayer joueur, ServerLevel niveau, int cx, int cz, long debut, long limite, Runnable action, @Nullable Runnable siAbandon) {
         this.joueur = joueur;
         this.niveau = niveau;
         this.cx = cx;
         this.cz = cz;
         this.debut = debut;
         this.limite = limite;
         this.action = action;
         this.siAbandon = siAbandon;
      }
   }

   private static final ArrayList<Attente> ATTENTES = new ArrayList<>();
   /** Attentes finies pendant ce tick (réemployée à chaque tick : rien de créé sans attente). */
   private static final ArrayList<Attente> PRETES = new ArrayList<>();

   private ArriveesServeur() {
   }

   /** Une fois, par la partie serveur du noyau. */
   public static void enregistrer() {
      Arrivees.brancher(INSTANCE);
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
         if (e.phase == TickEvent.Phase.END && !ATTENTES.isEmpty()) tick(e.getServer());
      });
      MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
         ATTENTES.clear();
         PRETES.clear();
      });
      MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityTeleportEvent.TeleportCommand.class, ArriveesServeur::commandeTp);
   }

   // ------------------------------------------------------------------ service
   private static boolean pret(ServerLevel l, int cx, int cz) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (l.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
         }
      }
      return true;
   }

   private static ChunkPos troncon(double x, double z) {
      return new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
   }

   @Override
   public void puis(ServerPlayer joueur, ServerLevel niveau, double x, double z, int attenteMax, Runnable action, @Nullable Runnable siAbandon) {
      MinecraftServer srv = joueur.server;
      if (!srv.isSameThread()) {
         srv.execute(() -> puis(joueur, niveau, x, z, attenteMax, action, siAbandon));
         return;
      }
      retirer(joueur, true);   // une seule téléportation en attente par joueur : la nouvelle remplace l'ancienne
      int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4;
      if (!NoyauConfig.arriveesPrechargees || pret(niveau, cx, cz)) {
         Allege.arriveesToutDeSuite++;
         action.run();
         return;
      }
      ChunkPos cp = new ChunkPos(cx, cz);
      niveau.getChunkSource().addRegionTicket(TICKET, cp, RAYON_TICKET, cp);
      long t = srv.getTickCount();
      ATTENTES.add(new Attente(joueur, niveau, cx, cz, t, t + attenteMax, action, siAbandon));
      Allege.arriveesAttendues++;
   }

   @Override
   public void precharger(ServerLevel niveau, double x, double z) {
      ChunkPos cp = troncon(x, z);
      niveau.getChunkSource().addRegionTicket(TICKET, cp, RAYON_TICKET, cp);
      Allege.arriveesPrechargees++;
   }

   @Override
   public boolean prete(ServerLevel niveau, double x, double z) {
      return pret(niveau, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
   }

   @Override
   public void garder(ServerLevel niveau, double x, double z) {
      ChunkPos cp = troncon(x, z);
      niveau.getChunkSource().addRegionTicket(TICKET_GARDE, cp, RAYON_TICKET, cp);
   }

   @Override
   public void lacher(ServerLevel niveau, double x, double z) {
      ChunkPos cp = troncon(x, z);
      niveau.getChunkSource().removeRegionTicket(TICKET_GARDE, cp, RAYON_TICKET, cp);
   }

   @Override
   public boolean enAttente(ServerPlayer joueur) {
      for (int i = 0; i < ATTENTES.size(); i++) {
         if (ATTENTES.get(i).joueur == joueur) return true;
      }
      return false;
   }

   @Override
   public void annuler(ServerPlayer joueur) {
      retirer(joueur, false);
   }

   /** Retire l'attente de ce joueur (s'il en a une) et joue son action d'abandon. */
   private static void retirer(ServerPlayer joueur, boolean remplacee) {
      for (int i = 0; i < ATTENTES.size(); i++) {
         Attente a = ATTENTES.get(i);
         if (a.joueur == joueur) {
            ATTENTES.remove(i);
            if (remplacee) Allege.arriveesRemplacees++;
            abandonner(a);
            return;
         }
      }
   }

   private static void abandonner(Attente a) {
      Allege.arriveesAbandonnees++;
      if (a.siAbandon == null) return;
      try {
         a.siAbandon.run();
      } catch (Throwable ex) {
         LOG.error("[noyau] arrivée : action d'abandon en erreur", ex);
      }
   }

   /** Fin de chaque tick, seulement s'il y a des attentes : arrivées prêtes ou délai passé, joueurs partis. */
   private static void tick(MinecraftServer srv) {
      long t = srv.getTickCount();
      int garde = 0;
      for (int i = 0; i < ATTENTES.size(); i++) {
         Attente a = ATTENTES.get(i);
         if (a.joueur.isRemoved() || !a.joueur.isAlive() || a.joueur.hasDisconnected() || srv.getLevel(a.niveau.dimension()) != a.niveau) {
            a.abandon = true;
            PRETES.add(a);
            continue;
         }
         boolean prete = pret(a.niveau, a.cx, a.cz);
         if (prete || t >= a.limite) {
            if (!prete) Allege.arriveesDelaiDepasse++;
            Allege.arriveesTicksAttendus += t - a.debut;
            PRETES.add(a);
            continue;
         }
         ATTENTES.set(garde++, a);
      }
      for (int i = ATTENTES.size() - 1; i >= garde; i--) ATTENTES.remove(i);
      // actions après le tri : une action peut demander une nouvelle arrivée
      for (int i = 0; i < PRETES.size(); i++) {
         Attente a = PRETES.get(i);
         if (a.abandon) {
            abandonner(a);
            continue;
         }
         try {
            a.action.run();
         } catch (Throwable ex) {
            LOG.error("[noyau] arrivée : téléportation en erreur", ex);
         }
      }
      PRETES.clear();
   }

   // ------------------------------------------------------------------ /tp de Minecraft
   /** Ce que {@code TeleportCommand.performTeleport} vient de recevoir (noté par CommandeTpMixin, fil du serveur). */
   private static Entity tpEntite;
   private static ServerLevel tpNiveau;
   private static Set<RelativeMovement> tpRelatifs;
   private static float tpLacet, tpTangage;
   private static boolean tpRegard;
   private static CommandSourceStack tpSource;
   private static long tpTick = Long.MIN_VALUE;

   /** CommandeTpMixin, au début de {@code performTeleport} (fil du serveur). */
   public static void noterTp(CommandSourceStack source, Entity entite, ServerLevel niveau, Set<RelativeMovement> relatifs, float lacet,
                              float tangage, boolean regard) {
      MinecraftServer srv = niveau.getServer();
      if (!srv.isSameThread()) return;
      tpEntite = entite;
      tpNiveau = niveau;
      tpRelatifs = relatifs;
      tpLacet = lacet;
      tpTangage = tangage;
      tpRegard = regard;
      tpSource = source;
      tpTick = srv.getTickCount();
   }

   /** CommandeTpMixin, à la fin de {@code performTeleport} : rien ne doit servir à une autre téléportation. */
   public static void oublierTp() {
      tpEntite = null;
      tpNiveau = null;
      tpRelatifs = null;
      tpSource = null;
   }

   private static void commandeTp(EntityTeleportEvent.TeleportCommand e) {
      if (!NoyauConfig.tpPrecharge || !NoyauConfig.arriveesPrechargees || !(e.getEntity() instanceof ServerPlayer p)) return;
      ServerLevel niveau = tpNiveau;
      if (tpEntite != p || niveau == null || tpRegard || !p.server.isSameThread() || tpTick != p.server.getTickCount()) return;
      double x = e.getTargetX(), y = e.getTargetY(), z = e.getTargetZ();
      if (!Level.isInSpawnableBounds(BlockPos.containing(x, y, z))) return;   // Minecraft répondra « position invalide »
      if (pret(niveau, Mth.floor(x) >> 4, Mth.floor(z) >> 4)) return;         // arrivée prête : tout de suite, comme avant
      Set<RelativeMovement> relatifs = tpRelatifs == null ? Set.of() : tpRelatifs;
      float lacet = tpLacet, tangage = tpTangage;
      // lacet et tangage du joueur lui-même (commande sans rotation) : ceux qu'il aura au moment du départ
      boolean memeRegard = lacet == p.getYRot() && tangage == p.getXRot();
      ServerPlayer auteur = tpSource == null ? null : tpSource.getPlayer();
      e.setCanceled(true);
      Allege.tpRetardes++;
      if (auteur != null) {
         Notifs.envoyer(auteur, Component.translatableWithFallback("noyau.arrivee.tp_titre", "Téléportation"),
               Component.translatableWithFallback("noyau.arrivee.tp_texte", "L'arrivée se charge : départ dans un instant (3 s au plus)."),
               Notifs.INFO, 3);
      }
      INSTANCE.puis(p, niveau, x, z, Arrivees.ATTENTE_MAX, () -> {
         float l = memeRegard ? p.getYRot() : lacet, t = memeRegard ? p.getXRot() : tangage;
         if (p.teleportTo(niveau, x, y, z, relatifs, Mth.wrapDegrees(l), Mth.wrapDegrees(t)) && !p.isFallFlying()) {
            // comme la commande de Minecraft
            p.setDeltaMovement(p.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            p.setOnGround(true);
         }
      }, null);
   }
}
