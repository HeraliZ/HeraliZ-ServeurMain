package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ExtractionConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registre des zones d'extraction fixes créées par les admins (sauvegardé avec le monde,
 * dans data/heraliz_extraction_zones.dat). Chaque zone existe aussi sous forme d'entité
 * dans le monde ; une entité dont le nom n'est plus dans ce registre se supprime d'elle-même.
 * <p>
 * Le registre garde aussi l'état d'ouverture de chaque zone (heure de jeu de fermeture, dernière fermeture), mené par
 * {@link Rotation} et les commandes : il survit aux redémarrages. Avec la rotation par vagues (réglage rotation.waves),
 * il garde aussi la fin de la vague en cours et les zones qu'elle a ouvertes (exclues de la vague suivante).
 */
public class ExtractionZones extends SavedData {
    private static final String FILE = "heraliz_extraction_zones";

    /** Comment une zone s'ouvre. */
    public enum Mode {
        /** Ouverte à tour de rôle avec les autres (réglages [rotation]). */
        ROTATION("rotation", "en rotation"),
        /** Toujours ouverte. */
        PERMANENT("permanent", "permanente"),
        /** Jamais ouverte par la rotation (seulement à la main : /extraction open). */
        CLOSED("closed", "fermée hors ouverture à la main");

        public final String id;
        public final String label;

        Mode(String id, String label) {
            this.id = id;
            this.label = label;
        }

        /** Libellé traduit chez le joueur (clé {@code heraliz_extraction.mode.<id>}, {@link #label} en secours). */
        public MutableComponent text() {
            return Component.translatableWithFallback("heraliz_extraction.mode." + this.id, this.label);
        }

        public static Mode byId(String id) {
            for (Mode m : values()) {
                if (m.id.equals(id)) {
                    return m;
                }
            }
            return ROTATION;
        }
    }

    /**
     * @param durationSeconds temps à tenir, 0 = réglage zones.durationSeconds
     * @param label           nom affiché (vide = le nom de la zone, mis en forme)
     */
    public record Zone(String name, ResourceLocation dimension, Vec3 pos, float radius, int durationSeconds, Mode mode,
                       String label) {
        public Zone(String name, ResourceLocation dimension, Vec3 pos, float radius, int durationSeconds) {
            this(name, dimension, pos, radius, durationSeconds, Mode.ROTATION, "");
        }

        /** Temps à tenir dans la zone (secondes). */
        public int holdSeconds() {
            return this.durationSeconds > 0 ? this.durationSeconds : ExtractionConfig.FIXED_DURATION_SECONDS.get();
        }

        /** « Aérodrome nord » : le libellé, sinon le nom sans tirets, avec une majuscule. */
        public String displayName() {
            if (!this.label.isEmpty()) {
                return this.label;
            }
            String s = this.name.replace('_', ' ').replace('-', ' ').trim();
            return s.isEmpty() ? this.name : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
        }

        public Zone withMode(Mode m) {
            return new Zone(this.name, this.dimension, this.pos, this.radius, this.durationSeconds, m, this.label);
        }

        public Zone withLabel(String l) {
            return new Zone(this.name, this.dimension, this.pos, this.radius, this.durationSeconds, this.mode, l);
        }
    }

    private final Map<String, Zone> zones = new LinkedHashMap<>();
    /** Heure de jeu (overworld) de fermeture des zones ouvertes par la rotation ou à la main. */
    private final Map<String, Long> openUntil = new HashMap<>();
    /** Heure de jeu de la dernière fermeture (la rotation rouvre d'abord les zones fermées depuis longtemps). */
    private final Map<String, Long> closedAt = new HashMap<>();
    /** Heure de jeu de la fin de la vague en cours (0 : aucune vague en cours). */
    private long vagueFin;
    /** Zones ouvertes par la vague en cours (ou la dernière vague finie) : la vague suivante les évite. */
    private final Set<String> vagueZones = new LinkedHashSet<>();

    public static ExtractionZones get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ExtractionZones::load, ExtractionZones::new, FILE);
    }

    public boolean contains(String name) {
        return this.zones.containsKey(name);
    }

    @Nullable
    public Zone get(String name) {
        return this.zones.get(name);
    }

    public Collection<Zone> all() {
        return this.zones.values();
    }

    /**
     * Numéro de la zone pour les joueurs (« Extraction 3 ») : son rang parmi toutes les zones, rangées par nom (18/10,
     * demande du propriétaire : les noms posés en jeu sont pris au hasard et ne sont plus montrés aux joueurs ; le staff
     * garde les vrais noms). Le même partout : carte du monde, menu Échap, chat.
     */
    public int numero(Zone zone) {
        int n = 1;
        for (String nom : this.zones.keySet()) {
            if (nom.compareToIgnoreCase(zone.name()) < 0) {
                n++;
            }
        }
        return n;
    }

    /** « Extraction 3 », dans la langue du joueur. */
    public MutableComponent nomPublic(Zone zone) {
        return Component.translatableWithFallback("heraliz_extraction.zone.nom", "Extraction %s", String.valueOf(numero(zone)));
    }

    public void add(Zone zone) {
        this.zones.put(zone.name(), zone);
        setDirty();
    }

    public void remove(String name) {
        if (this.zones.remove(name) != null) {
            this.openUntil.remove(name);
            this.closedAt.remove(name);
            this.vagueZones.remove(name);
            setDirty();
        }
    }

    // ------------------------------------------------------------------ vagues (rotation.waves)

    /** Heure de jeu de la fin de la vague en cours, 0 si aucune. */
    public long vagueFin() {
        return this.vagueFin;
    }

    /** Zones ouvertes par la vague en cours, ou par la dernière vague finie (copie). */
    public Set<String> vagueZones() {
        return new LinkedHashSet<>(this.vagueZones);
    }

    /** Nouvelle vague, ou vague en cours complétée : fin et zones qu'elle a ouvertes. */
    public void vague(long fin, Collection<String> zones) {
        this.vagueFin = fin;
        this.vagueZones.clear();
        this.vagueZones.addAll(zones);
        setDirty();
    }

    /** Termine la vague en cours tout de suite (ses zones restent celles à éviter au tirage suivant). */
    public void finirVague() {
        if (this.vagueFin != 0L) {
            this.vagueFin = 0L;
            setDirty();
        }
    }

    // ------------------------------------------------------------------ ouverture

    /** Zone ouverte à l'heure de jeu {@code now} (permanente, ou ouverte jusqu'à plus tard). */
    public boolean isOpen(Zone zone, long now) {
        return zone.mode() == Mode.PERMANENT || this.openUntil.getOrDefault(zone.name(), 0L) > now;
    }

    /** Heure de jeu de fermeture d'une zone ouverte pour un temps, 0 sinon. */
    public long openUntil(String name) {
        return this.openUntil.getOrDefault(name, 0L);
    }

    /** Ticks avant la fermeture : -1 = zone permanente, 0 = fermée. */
    public long remainingTicks(Zone zone, long now) {
        if (zone.mode() == Mode.PERMANENT) {
            return -1L;
        }
        return Math.max(0L, this.openUntil.getOrDefault(zone.name(), 0L) - now);
    }

    public long closedAt(String name) {
        return this.closedAt.getOrDefault(name, 0L);
    }

    public void open(String name, long until) {
        this.openUntil.put(name, until);
        setDirty();
    }

    public void close(String name, long now) {
        this.openUntil.remove(name);
        this.closedAt.put(name, now);
        setDirty();
    }

    // ------------------------------------------------------------------ sauvegarde

    private static ExtractionZones load(CompoundTag tag) {
        ExtractionZones data = new ExtractionZones();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag z = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(z.getString("Dimension"));
            if (dim == null) {
                continue;
            }
            String name = z.getString("Name");
            // zones d'avant la rotation : sans « Mode », elles entrent dans la rotation
            data.zones.put(name, new Zone(name, dim,
                    new Vec3(z.getDouble("X"), z.getDouble("Y"), z.getDouble("Z")),
                    z.getFloat("Radius"), z.getInt("Duration"), Mode.byId(z.getString("Mode")), z.getString("Label")));
            if (z.getLong("OpenUntil") > 0L) {
                data.openUntil.put(name, z.getLong("OpenUntil"));
            }
            if (z.getLong("ClosedAt") > 0L) {
                data.closedAt.put(name, z.getLong("ClosedAt"));
            }
        }
        data.vagueFin = Math.max(0L, tag.getLong("VagueFin"));
        ListTag vague = tag.getList("Vague", Tag.TAG_STRING);
        for (int i = 0; i < vague.size(); i++) {
            data.vagueZones.add(vague.getString(i));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone zone : this.zones.values()) {
            CompoundTag z = new CompoundTag();
            z.putString("Name", zone.name());
            z.putString("Dimension", zone.dimension().toString());
            z.putDouble("X", zone.pos().x);
            z.putDouble("Y", zone.pos().y);
            z.putDouble("Z", zone.pos().z);
            z.putFloat("Radius", zone.radius());
            z.putInt("Duration", zone.durationSeconds());
            z.putString("Mode", zone.mode().id);
            if (!zone.label().isEmpty()) {
                z.putString("Label", zone.label());
            }
            long until = openUntil(zone.name());
            if (until > 0L) {
                z.putLong("OpenUntil", until);
            }
            long closed = closedAt(zone.name());
            if (closed > 0L) {
                z.putLong("ClosedAt", closed);
            }
            list.add(z);
        }
        tag.put("Zones", list);
        if (this.vagueFin > 0L) {
            tag.putLong("VagueFin", this.vagueFin);
        }
        if (!this.vagueZones.isEmpty()) {
            ListTag vague = new ListTag();
            for (String name : this.vagueZones) {
                vague.add(StringTag.valueOf(name));
            }
            tag.put("Vague", vague);
        }
        return tag;
    }
}
