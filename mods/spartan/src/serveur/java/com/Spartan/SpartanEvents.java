package com.Spartan;

import com.Spartan.alert.AlertManager;
import com.Spartan.ban.BanManager;
import com.Spartan.ban.PlayerRegistry;
import com.Spartan.check.CheckManager;
import com.Spartan.check.HandshakeMonitor;
import com.Spartan.check.Mitigation;
import com.Spartan.check.item.AntiDupe;
import com.Spartan.check.movement.MovementEngine;
import com.Spartan.config.SpartanConfig;
import com.Spartan.esp.Noise;
import com.Spartan.esp.VisibilityEngine;
import com.Spartan.evidence.EvidenceRecorder;
import com.Spartan.integrity.ChallengeManager;
import com.Spartan.integrity.LauncherGate;
import com.Spartan.scan.ScanManager;
import com.Spartan.serveur.compat.HeralizCompat;
import com.Spartan.util.AsyncIO;
import com.Spartan.util.PacketGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Branchement des détections sur les événements serveur.
 * Tout tourne côté serveur : c'est la partie que le client ne peut pas falsifier.
 */
@Mod.EventBusSubscriber(modid = Spartan.MOD_ID)
public class SpartanEvents {
    /** Grâce après un saut de position (tp, respawn, connexion, changement de dimension). */
    private static final long GRACE_MS = 3000;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || isFake(player) || !SpartanConfig.ENABLED.get()) {
            return;
        }

        // Mouvement : rejeu des paquets réellement envoyés par le client (positions,
        // téléportations confirmées, cadence) et correction immédiate.
        MovementEngine.tick(player);
        if (player.hasDisconnected()) {
            return; // expulsé pendant ce tick
        }
        // Effets, détections de combat différées.
        CheckManager.runTick(player);
        if (player.hasDisconnected()) {
            return;
        }
        HandshakeMonitor.tick(player);
        com.Spartan.capture.CaptureManager.tick(player);
        ChallengeManager.tick(player);
        ScanManager.tick(player);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isFake(player) || !SpartanConfig.ENABLED.get()) {
            return;
        }
        // Verrou de combat après une détection : aucun coup ne passe.
        if (Mitigation.isCombatLocked(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        boolean violation = CheckManager.onAttack(player, event.getTarget());
        if (violation && SpartanConfig.MITIGATE_COMBAT.get()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || isFake(player)
                || !(event.getLevel() instanceof Level level)
                || !SpartanConfig.ENABLED.get()) {
            return;
        }
        if (Mitigation.isBlockLocked(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        boolean violation = CheckManager.onBlockBreak(player, event.getState(), event.getPos(), level);
        if (violation && SpartanConfig.MITIGATE_BLOCKS.get()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && event.getEntity() instanceof ServerPlayer player
                && !isFake(player)
                && SpartanConfig.ENABLED.get()) {
            CheckManager.onStartBreaking(player, event.getPos(), event.getLevel());
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Les bans d'abord : la purge RGPD du registre épargne les joueurs bannis.
        BanManager.load(SpartanConfig.DATA_RETENTION_DAYS.get());
        PlayerRegistry.load(SpartanConfig.DATA_RETENTION_DAYS.get(), BanManager::isBannedUuid);
        ChallengeManager.warmUp();
        ScanManager.load();
        Noise.checkTacz();
        com.Spartan.alert.CapturesDiscord.demarrer();
    }

    /** Porte des paquets de mods : posée pendant la négociation de connexion, avant le moindre paquet de jeu. */
    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void onNegotiation(net.minecraftforge.event.entity.player.PlayerNegotiationEvent event) {
        com.Spartan.integrity.PorteJoueurs.poser(event.getConnection());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isFake(player)) {
            return;
        }
        String ip = player.getIpAddress();

        // Ban d'IP en cours (IPv4 et IPv6 : la liste vanilla ne gère que l'IPv4).
        BanManager.BanRecord ipBan = BanManager.findIpBan(ip);
        if (ipBan != null) {
            player.connection.disconnect(BanManager.kickMessage(ipBan));
            return;
        }

        com.Spartan.integrity.PorteJoueurs.rattacher(player);
        CheckManager.grantGrace(player.getUUID(), GRACE_MS);
        MovementEngine.onLogin(player);
        HandshakeMonitor.onLogin(player.getUUID());
        ScanManager.onLogin(player);
        PlayerRegistry.recordLogin(player.getUUID(), player.getName().getString(), ip);

        // Même IP qu'un joueur banni : simple alerte (une IP peut être partagée).
        if (SpartanConfig.ALERT_SAME_IP.get()) {
            BanManager.BanRecord ban = BanManager.findActiveByIp(ip);
            if (ban != null && (ban.uuid == null || !ban.uuid.equals(player.getUUID().toString()))) {
                AlertManager.info(player.server, "Même IP qu'un banni",
                        player.getName().getString() + " se connecte avec l'IP de " + ban.name
                                + " (banni : " + ban.reason + "). À surveiller.",
                        AlertManager.COLOR_INFO);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.Spartan.integrity.PorteJoueurs.rattacher(player);
        CheckManager.grantGrace(player.getUUID(), GRACE_MS);
            VisibilityEngine.forget(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.Spartan.integrity.PorteJoueurs.rattacher(player);
        CheckManager.grantGrace(player.getUUID(), GRACE_MS);
            VisibilityEngine.forget(player);
        }
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        VisibilityEngine.onTagsUpdated();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        VisibilityEngine.onChunkLoad(event);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        VisibilityEngine.onChunkUnload(event);
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        // Perle de l'ender / fruit chorus : action du joueur, aucune grâce (le moteur de
        // mouvement suit la téléportation jusqu'à sa confirmation). Commandes : grâce.
        if (event.getEntity() instanceof ServerPlayer player
                && !(event instanceof EntityTeleportEvent.EnderPearl)
                && !(event instanceof EntityTeleportEvent.ChorusFruit)) {
            com.Spartan.integrity.PorteJoueurs.rattacher(player);
        CheckManager.grantGrace(player.getUUID(), GRACE_MS);
        }
    }

    private static int serverTicks = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        com.Spartan.integrity.PorteJoueurs.tick(event.getServer());
        // Anti-ESP : lignes de vue entre joueurs, décisions d'envoi appliquées tout de suite.
        VisibilityEngine.tick(event.getServer());
        // Anti-duplication : un relevé de temps en temps (même numéro à deux endroits au même instant).
        AntiDupe.tick(event.getServer());
        // Une fois par heure : écrit le registre si besoin, applique la rétention RGPD.
        if (++serverTicks % 72000 == 0) {
            int days = SpartanConfig.DATA_RETENTION_DAYS.get();
            PlayerRegistry.purge(days, BanManager::isBannedUuid);
            BanManager.anonymize(days);
            PlayerRegistry.flushIfDirty();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Registre et bans posés juste avant l'arrêt doivent être écrits sur le disque.
        PlayerRegistry.flushIfDirty();
        AsyncIO.flush(5000);
        VisibilityEngine.reset();
    }

    /**
     * Les machines de mods (déployeur Create, etc.) agissent via un faux joueur :
     * elles frappent et cassent des blocs à des cadences de machine. Ignorées.
     */
    private static boolean isFake(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EvidenceRecorder.clear(player.getUUID());
            MovementEngine.clear(player.getUUID());
            CheckManager.clear(player.getUUID());
            PacketGuard.clear(player.getUUID());
            HandshakeMonitor.clear(player.getUUID());
            Mitigation.clear(player.getUUID());
            ChallengeManager.clear(player.getUUID());
            LauncherGate.clear(player.getUUID());
            com.Spartan.integrity.PorteJoueurs.oublier(player.getUUID());
            ScanManager.clear(player.getUUID());
            com.Spartan.capture.CaptureManager.clear(player.getUUID());
            com.Spartan.serveur.ReceptionServeur.oublierTouches(player.getUUID());
            VisibilityEngine.forget(player);
            HeralizCompat.oublier(player.getUUID());
        }
    }
}
