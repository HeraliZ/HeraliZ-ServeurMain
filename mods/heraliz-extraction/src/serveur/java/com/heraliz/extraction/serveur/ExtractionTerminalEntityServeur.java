package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ExtractionConfig;
import com.heraliz.extraction.TerminalGeometry;
import com.heraliz.extraction.entity.ExtractionTerminalEntity;
import com.heraliz.extraction.reseau.Reseau;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Partie serveur du terminal d'extraction : chrono de chaque joueur dans la zone (envoyé à son HUD,
 * {@link Reseau.Progression}), extraction (téléportation), fermeture de la zone, zones fixes des admins et leur sauvegarde.
 * Créée par ModRegistry.TERMINAL_USINE.
 * Une zone fixe lit son état (ouverte ou non, temps restant, réglages) dans {@link ExtractionZones} une fois par seconde :
 * c'est {@link Rotation} et les commandes qui l'ouvrent et la ferment.
 */
public class ExtractionTerminalEntityServeur extends ExtractionTerminalEntity {
    /** Tolérance (ticks) avant d'annuler le chrono d'un joueur sorti de la zone. */
    private static final int OUT_GRACE = 10;

    /** Chrono d'un joueur présent dans la zone (serveur). */
    private static final class Extractor {
        int progress;
        int outTicks;
        /** Dernier état envoyé à son HUD (null : rien encore) : dans la zone ou non. */
        Boolean sentInside;
    }

    @Nullable
    private UUID ownerId;
    /** Nom de la zone fixe (registre {@link ExtractionZones}). */
    private String zoneName = "";
    private int lifetime = 6000;
    /** Zone fixe : ticks avant sa fermeture (-1 = permanente), relu dans le registre chaque seconde. */
    private long fixedTicksLeft = -1L;
    private final Map<UUID, Extractor> extractors = new HashMap<>();

    public ExtractionTerminalEntityServeur(EntityType<? extends ExtractionTerminalEntity> type, Level level) {
        super(type, level);
    }

    public void setup(ServerPlayer owner, int durationTicks, int lifetimeTicks, float radius) {
        this.ownerId = owner.getUUID();
        this.lifetime = Math.max(20, lifetimeTicks);
        this.entityData.set(DATA_DURATION, Math.max(1, durationTicks));
        this.entityData.set(DATA_TICKS_LEFT, this.lifetime);
        this.entityData.set(DATA_RADIUS, radius);
    }

    /** Zone fixe d'admin : sans terminal, fermée jusqu'à ce que le registre la dise ouverte (seconde suivante). */
    public void setupFixed(String name, int durationTicks, float radius) {
        this.zoneName = name;
        this.entityData.set(DATA_FIXED, true);
        this.entityData.set(DATA_OPEN, false);
        setInvisible(true); // pas d'ombre au sol : seule la zone est dessinée
        this.entityData.set(DATA_DURATION, Math.max(1, durationTicks));
        this.entityData.set(DATA_RADIUS, radius);
    }

    public String getZoneName() {
        return this.zoneName;
    }

    @Override
    protected void serverTick() {
        if (getState() != STATE_ACTIVE) {
            if (++this.endTicks >= END_LINGER) {
                discard();
            }
            return;
        }

        int ticksLeft;
        if (isFixed()) {
            if (this.tickCount % 20 == 1 && !syncFixed()) {
                return;
            }
            if (!isOpen()) {
                return;
            }
            ticksLeft = (int) Math.min(Integer.MAX_VALUE, this.fixedTicksLeft);
            if (this.fixedTicksLeft > 0L) {
                this.fixedTicksLeft--;
            }
        } else {
            if (this.tickCount == (int) TerminalGeometry.LID_OPEN_START) {
                broadcastSound(SoundEvents.IRON_TRAPDOOR_OPEN, 1.0F, 0.6F);
            }
            if (this.tickCount == (int) TerminalGeometry.DEPLOY_END) {
                broadcastSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.7F);
                broadcastSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0F, 0.5F);
            }
            if (this.tickCount > TerminalGeometry.DEPLOY_END && this.tickCount % 30 == 0) {
                broadcastSound(SoundEvents.FIRE_AMBIENT, 2.0F, 0.5F);
            }

            ticksLeft = this.lifetime - this.tickCount;
            if (ticksLeft <= 0) {
                close();
                return;
            }
            if (this.tickCount % 10 == 0) {
                this.entityData.set(DATA_TICKS_LEFT, ticksLeft);
            }
        }

        refreshZone();
        ServerLevel level = (ServerLevel) this.level();
        float radius = getRadius();
        int duration = getDuration();

        // nouveaux joueurs entrés dans la zone
        AABB area = getBoundingBox().inflate(radius + 1.0D, 4.0D, radius + 1.0D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area, p -> !p.isSpectator() && p.isAlive())) {
            if (!this.extractors.containsKey(player.getUUID()) && isInZone(player)) {
                this.extractors.put(player.getUUID(), new Extractor());
            }
        }

        // chrono de chaque joueur
        int best = 0;
        Iterator<Map.Entry<UUID, Extractor>> it = this.extractors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Extractor> entry = it.next();
            Extractor extractor = entry.getValue();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            boolean dead = !player.isAlive();
            boolean inside = !dead && player.level() == level && isInZone(player);
            if (!inside) {
                // sorti de la zone : la barre disparaît simplement (petite tolérance pour un saut au bord)
                extractor.outTicks++;
                if (dead || extractor.outTicks > OUT_GRACE) {
                    Reseau.envoyer(player, Reseau.Progression.fin());
                    it.remove();
                    continue;
                }
            } else {
                extractor.outTicks = 0;
                extractor.progress++;
            }

            if (extractor.progress >= duration) {
                Reseau.envoyer(player, Reseau.Progression.fin());
                it.remove();
                extract(player);
                continue;
            }
            sendProgress(player, extractor, inside, duration, ticksLeft);
            best = Math.max(best, extractor.progress);
        }

        this.entityData.set(DATA_BEST_PROGRESS, best);
        this.entityData.set(DATA_EXTRACTING, this.extractors.size());
    }

    /**
     * Zone fixe : état lu dans le registre (ouverture, temps restant, temps à tenir, rayon).
     *
     * @return false si la zone n'existe plus (entité retirée)
     */
    private boolean syncFixed() {
        MinecraftServer server = this.level().getServer();
        ExtractionZones zones = ExtractionZones.get(server);
        ExtractionZones.Zone zone = zones.get(this.zoneName);
        // zone supprimée par /extraction remove pendant que son chunk n'était pas chargé, ou recréée ailleurs
        if (zone == null || zone.pos().distanceToSqr(position()) > 4.0D) {
            discard();
            return false;
        }
        long now = Rotation.now(server);
        boolean open = zones.isOpen(zone, now);
        if (open != isOpen()) {
            this.entityData.set(DATA_OPEN, open);
            if (open) {
                broadcastSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.7F);
            } else {
                broadcastSound(SoundEvents.FIRE_EXTINGUISH, 2.0F, 0.6F);
                clearExtractors();
            }
        }
        this.entityData.set(DATA_SMOKE, open && ExtractionConfig.FIXED_SMOKE.get());
        this.entityData.set(DATA_DURATION, Math.max(1, zone.holdSeconds() * 20));
        this.entityData.set(DATA_RADIUS, zone.radius());
        this.fixedTicksLeft = zones.remainingTicks(zone, now);
        this.entityData.set(DATA_TICKS_LEFT, (int) Math.max(-1L, Math.min(Integer.MAX_VALUE, this.fixedTicksLeft)));
        return true;
    }

    private boolean isInZone(ServerPlayer player) {
        return getZone().contains(player.getX(), player.getY(), player.getZ())
                && (isFixed() || isUnderOpenSky(this.level(), player.blockPosition()))
                && !Compat.downed(player);
    }

    /** Chrono vers le HUD du joueur : à son arrivée, quand il sort ou revient, puis toutes les 4 ticks (le HUD interpole). */
    private void sendProgress(ServerPlayer player, Extractor extractor, boolean inside, int duration, int ticksLeft) {
        if (extractor.sentInside != null && extractor.sentInside == inside && this.tickCount % 4 != 0) {
            return;
        }
        extractor.sentInside = inside;
        Reseau.envoyer(player, new Reseau.Progression(extractor.progress, duration, ticksLeft, inside, !isFixed()));
    }

    private void extract(ServerPlayer player) {
        this.level().broadcastEntityEvent(this, EVENT_EXTRACTED);
        broadcastSound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 4.0F, 0.7F);

        Destination.teleport(player);
    }

    /** Fin des 5 minutes : la zone se referme pour tout le monde. */
    private void close() {
        this.entityData.set(DATA_STATE, STATE_CLOSED);
        this.entityData.set(DATA_TICKS_LEFT, 0);
        this.endTicks = 0;
        broadcastSound(SoundEvents.FIRE_EXTINGUISH, 2.0F, 0.6F);
        clearExtractors();
    }

    /** Plus personne ne s'extrait : les chronos disparaissent des écrans. */
    private void clearExtractors() {
        MinecraftServer server = this.level().getServer();
        if (server != null) {
            for (UUID id : this.extractors.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    Reseau.envoyer(player, Reseau.Progression.fin());
                }
            }
        }
        this.extractors.clear();
        this.entityData.set(DATA_BEST_PROGRESS, 0);
        this.entityData.set(DATA_EXTRACTING, 0);
    }

    private void broadcastSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!this.level().isClientSide) {
            clearExtractors();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
        if (tag.getBoolean("Fixed")) {
            setupFixed(tag.getString("ZoneName"), tag.getInt("Duration"), tag.getFloat("Radius"));
            // dernier état connu : pas de clignotement le temps de relire le registre
            this.entityData.set(DATA_OPEN, tag.getBoolean("Open"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        if (isFixed()) {
            tag.putBoolean("Fixed", true);
            tag.putString("ZoneName", this.zoneName);
            tag.putInt("Duration", getDuration());
            tag.putFloat("Radius", getRadius());
            tag.putBoolean("Open", isOpen());
        }
    }
}
