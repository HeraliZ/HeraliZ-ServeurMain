package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ExtractionConfig;
import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.ModRegistry;
import com.heraliz.extraction.api.ExtractionApi;
import com.heraliz.extraction.reseau.Reseau;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Rotation des zones d'extraction fixes : quelques zones ouvertes à la fois (réglage rotation.openAtOnce), chacune pour
 * un temps tiré au hasard, puis d'autres prennent le relais (d'abord celles fermées depuis le plus longtemps). Annonces
 * dans le chat, rappel aux joueurs proches avant la fermeture, liste des zones ouvertes envoyée aux joueurs (carte du
 * monde : {@link ExtractionApi}). Une fois par seconde, presque rien à faire.
 */
@Mod.EventBusSubscriber(modid = HeralizExtraction.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Rotation {
    static final int TICKS_PER_MINUTE = 1200;
    /** Joueurs prévenus de la fermeture d'une zone : à moins de tant de blocs. */
    private static final double NEARBY = 200.0D;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final String[] DIRECTIONS = {"au nord", "au nord-est", "à l'est", "au sud-est", "au sud", "au sud-ouest",
            "à l'ouest", "au nord-ouest"};

    private static int ticks;

    private Rotation() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++ticks % 20 == 0) {
            tick(event.getServer());
        }
    }

    /** À la connexion : la liste des extractions ouvertes (carte du monde). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Reseau.envoyer(player, new Reseau.SyncExtractions(snapshot(player.server)));
        }
    }

    static void tick(MinecraftServer server) {
        ExtractionZones data = ExtractionZones.get(server);
        long now = now(server);
        boolean changed = false;
        boolean rotation = ExtractionConfig.ROTATION_ENABLED.get();
        int warn = ExtractionConfig.ROTATION_WARN_MINUTES.get() * TICKS_PER_MINUTE;

        for (ExtractionZones.Zone zone : List.copyOf(data.all())) {
            long until = data.openUntil(zone.name());
            if (until <= 0L) {
                continue;
            }
            long left = until - now;
            if (left <= 0L) {
                if (zone.mode() == ExtractionZones.Mode.ROTATION && rotation && closedCandidates(data, now, zone).isEmpty()) {
                    // personne pour prendre le relais : la zone reste ouverte un tour de plus, sans rien annoncer
                    data.open(zone.name(), now + randomDuration());
                } else {
                    data.close(zone.name(), now);
                    tellNearby(server, zone, Component.literal(" est fermée.").withStyle(ChatFormatting.GRAY));
                }
                changed = true;
            } else if (warn > 0 && left <= warn && left > warn - 20) {
                tellNearby(server, zone, Component.literal(" ferme dans " + formatMinutes(left) + ".").withStyle(ChatFormatting.RED));
            }
        }

        if (rotation) {
            List<ExtractionZones.Zone> pool = data.all().stream().filter(z -> z.mode() == ExtractionZones.Mode.ROTATION).toList();
            int target = Math.min(ExtractionConfig.ROTATION_OPEN.get(), pool.size());
            int open = (int) pool.stream().filter(z -> data.isOpen(z, now)).count();
            while (open < target) {
                List<ExtractionZones.Zone> candidates = closedCandidates(data, now, null);
                if (candidates.isEmpty()) {
                    break;
                }
                // les zones fermées depuis le plus longtemps (ou jamais ouvertes) d'abord, un peu de hasard parmi elles
                ExtractionZones.Zone pick = candidates.get(RANDOM.nextInt(Math.max(1, (candidates.size() + 1) / 2)));
                long duration = randomDuration();
                data.open(pick.name(), now + duration);
                announceOpening(server, pick, duration);
                open++;
                changed = true;
            }
        }

        healEntities(server, data, now);
        if (changed) {
            sync(server);
        }
    }

    /** Zones de la rotation fermées, triées de la plus anciennement fermée à la plus récente ({@code except} exclue). */
    private static List<ExtractionZones.Zone> closedCandidates(ExtractionZones data, long now, @Nullable ExtractionZones.Zone except) {
        List<ExtractionZones.Zone> out = new ArrayList<>();
        for (ExtractionZones.Zone z : data.all()) {
            if (z.mode() == ExtractionZones.Mode.ROTATION && (except == null || !z.name().equals(except.name())) && !data.isOpen(z, now)) {
                out.add(z);
            }
        }
        out.sort(Comparator.comparingLong(z -> data.closedAt(z.name())));
        return out;
    }

    static long randomDuration() {
        int min = ExtractionConfig.ROTATION_MIN_MINUTES.get();
        int max = Math.max(min, ExtractionConfig.ROTATION_MAX_MINUTES.get());
        return (long) min * TICKS_PER_MINUTE + RANDOM.nextInt((max - min) * TICKS_PER_MINUTE + 1);
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ------------------------------------------------------------------ ouvertures et fermetures (commandes)

    /** Ouvre une zone pour {@code durationTicks} et l'annonce. */
    static void open(MinecraftServer server, ExtractionZones.Zone zone, long durationTicks) {
        ExtractionZones data = ExtractionZones.get(server);
        data.open(zone.name(), now(server) + durationTicks);
        announceOpening(server, zone, durationTicks);
        sync(server);
    }

    /** Ferme une zone tout de suite (la rotation en ouvre une autre à la seconde suivante). */
    static void close(MinecraftServer server, ExtractionZones.Zone zone) {
        ExtractionZones data = ExtractionZones.get(server);
        data.close(zone.name(), now(server));
        tellNearby(server, zone, Component.literal(" est fermée.").withStyle(ChatFormatting.GRAY));
        sync(server);
    }

    /** Envoie à tous les joueurs la liste des extractions ouvertes. */
    static void sync(MinecraftServer server) {
        Reseau.envoyerATous(new Reseau.SyncExtractions(snapshot(server)));
    }

    static List<ExtractionApi.Extraction> snapshot(MinecraftServer server) {
        ExtractionZones data = ExtractionZones.get(server);
        long now = now(server);
        List<ExtractionApi.Extraction> out = new ArrayList<>();
        for (ExtractionZones.Zone z : data.all()) {
            if (data.isOpen(z, now)) {
                long left = data.remainingTicks(z, now);
                out.add(new ExtractionApi.Extraction(z.name(), z.displayName(), z.dimension(), z.pos().x, z.pos().y, z.pos().z,
                        z.radius(), z.holdSeconds(), left < 0L ? -1L : now + left));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ entités des zones

    /**
     * Une zone ouverte dont le terrain est chargé doit avoir son entité (perdue si le monde a été retouché, ou supprimée
     * à la main) : on la recrée ; un doublon est retiré.
     */
    private static void healEntities(MinecraftServer server, ExtractionZones data, long now) {
        for (ExtractionZones.Zone zone : data.all()) {
            if (!data.isOpen(zone, now)) {
                continue;
            }
            ServerLevel level = level(server, zone);
            BlockPos pos = BlockPos.containing(zone.pos());
            if (level == null || !level.isPositionEntityTicking(pos) || !level.areEntitiesLoaded(ChunkPos.asLong(pos))) {
                continue;
            }
            List<ExtractionTerminalEntityServeur> found = level.getEntitiesOfClass(ExtractionTerminalEntityServeur.class,
                    new AABB(pos).inflate(2.0D), e -> e.isFixed() && zone.name().equals(e.getZoneName()));
            if (found.isEmpty()) {
                place(level, zone);
            } else {
                for (int i = 1; i < found.size(); i++) {
                    found.get(i).discard();
                }
            }
        }
    }

    /** Pose l'entité (invisible) d'une zone fixe : c'est elle qui dessine la zone et compte les joueurs. */
    static void place(ServerLevel level, ExtractionZones.Zone zone) {
        ExtractionTerminalEntityServeur entity = (ExtractionTerminalEntityServeur) ModRegistry.EXTRACTION_TERMINAL_ENTITY.get().create(level);
        if (entity == null) {
            return;
        }
        entity.setupFixed(zone.name(), zone.holdSeconds() * 20, zone.radius());
        entity.moveTo(zone.pos().x, zone.pos().y, zone.pos().z, 0.0F, 0.0F);
        level.addFreshEntity(entity);
    }

    @Nullable
    static ServerLevel level(MinecraftServer server, ExtractionZones.Zone zone) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, zone.dimension()));
    }

    // ------------------------------------------------------------------ messages

    private static MutableComponent prefix() {
        return Component.literal("[Extraction] ").withStyle(ChatFormatting.GOLD);
    }

    /** « [Extraction] Aérodrome nord ouverte pendant 45 min · 850 m au nord-est » à chaque joueur de la dimension. */
    private static void announceOpening(MinecraftServer server, ExtractionZones.Zone zone, long durationTicks) {
        if (!ExtractionConfig.ROTATION_ANNOUNCE.get()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().location().equals(zone.dimension())) {
                continue;
            }
            MutableComponent msg = prefix()
                    .append(Component.literal(zone.displayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(" ouverte pendant ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formatMinutes(durationTicks)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" · " + where(player, zone)).withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(msg.withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/extractions"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Voir les extractions ouvertes (/extractions)")))));
            player.playNotifySound(SoundEvents.UI_TOAST_IN, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    /** Message aux joueurs proches de la zone (fermeture, rappel). */
    private static void tellNearby(MinecraftServer server, ExtractionZones.Zone zone, Component end) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().location().equals(zone.dimension())
                    && player.position().distanceToSqr(zone.pos()) < NEARBY * NEARBY) {
                player.sendSystemMessage(prefix()
                        .append(Component.literal(zone.displayName()).withStyle(ChatFormatting.WHITE))
                        .append(end));
            }
        }
    }

    /** « 850 m au nord-est », « 1,2 km à l'ouest », « ici ». */
    static String where(ServerPlayer player, ExtractionZones.Zone zone) {
        double dx = zone.pos().x - player.getX();
        double dz = zone.pos().z - player.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 20.0D) {
            return "ici";
        }
        int dir = Math.floorMod((int) Math.round(Math.atan2(dx, -dz) / (Math.PI / 4.0D)), 8);
        String dist = d < 1000.0D ? Math.round(d / 10.0D) * 10 + " m"
                : String.format(Locale.ROOT, "%.1f km", d / 1000.0D).replace('.', ',');
        return dist + " " + DIRECTIONS[dir];
    }

    /** « 45 min », « 1 h 05 », « 40 s ». */
    static String formatMinutes(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        if (seconds < 60L) {
            return seconds + " s";
        }
        long minutes = (seconds + 59L) / 60L;
        return minutes < 60L ? minutes + " min" : (minutes / 60L) + " h " + String.format(Locale.ROOT, "%02d", minutes % 60L);
    }
}
