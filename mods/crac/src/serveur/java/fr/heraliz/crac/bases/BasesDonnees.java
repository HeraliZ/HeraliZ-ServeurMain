package fr.heraliz.crac.bases;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Données du monde des bases gardées avec le monde principal ({@code data/crac_bases.dat}, comme les groupes) :
 * par joueur, l'étape du tutoriel, le temps passé en protection « nouveau joueur », le dernier /home et la protection
 * de base encore due ; par groupe, la fin de la protection anti-raid de sa base (heure en ms).
 */
public final class BasesDonnees extends SafeSavedData {
    public static final String ID = "crac_bases";

    /** Étape du tutoriel d'un joueur. */
    public enum Etape {
        /** Pas encore fait (attend la fin de la cinématique, au spawn). */
        AUCUNE,
        /** Kit reçu, pas encore envoyé dans le monde des bases. */
        KIT,
        /** Dans le monde des bases, protégé (contour vert) tant que sa base n'est pas posée. */
        PHASE,
        /** Terminé (ou joueur qui avait déjà une base). */
        FINI;

        static Etape parRang(int r) {
            Etape[] v = values();
            return r >= 0 && r < v.length ? v[r] : AUCUNE;
        }
    }

    public static final class Joueur {
        public Etape etape = Etape.AUCUNE;
        /** Ticks de jeu passés en protection « nouveau joueur ». */
        public long phaseTicks;
        /** Heure (ms) du dernier /home réussi. */
        public long dernierHome;
        /** La première base posée recevra la protection anti-raid. */
        public boolean protectionDue;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Etape", this.etape.ordinal());
            t.putLong("Phase", this.phaseTicks);
            t.putLong("Home", this.dernierHome);
            t.putBoolean("Due", this.protectionDue);
            return t;
        }

        static Joueur load(CompoundTag t) {
            Joueur j = new Joueur();
            j.etape = Etape.parRang(t.getInt("Etape"));
            j.phaseTicks = t.getLong("Phase");
            j.dernierHome = t.getLong("Home");
            j.protectionDue = t.getBoolean("Due");
            return j;
        }
    }

    private final Map<UUID, Joueur> joueurs = new HashMap<>();
    private final Map<UUID, Long> protections = new HashMap<>();

    public static BasesDonnees get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BasesDonnees::load,
            () -> SafeSavedData.fresh(server.overworld(), ID, new BasesDonnees()), ID);
    }

    public Joueur joueur(UUID id) {
        return this.joueurs.computeIfAbsent(id, k -> new Joueur());
    }

    @Nullable
    public Joueur siConnu(UUID id) {
        return this.joueurs.get(id);
    }

    /** Fin (ms) de la protection de base du groupe, 0 sans protection. */
    public long finProtection(@Nullable UUID groupe) {
        Long f = groupe == null ? null : this.protections.get(groupe);
        return f == null ? 0L : f;
    }

    public void proteger(UUID groupe, long fin) {
        this.protections.put(groupe, fin);
        this.setDirty();
    }

    public boolean retirerProtection(UUID groupe) {
        boolean r = this.protections.remove(groupe) != null;
        if (r) {
            this.setDirty();
        }
        return r;
    }

    public Map<UUID, Long> protections() {
        return this.protections;
    }

    public void marquer() {
        this.setDirty();
    }

    public static BasesDonnees load(CompoundTag tag) {
        BasesDonnees d = new BasesDonnees();
        ListTag js = tag.getList("Joueurs", Tag.TAG_COMPOUND);
        for (int i = 0; i < js.size(); i++) {
            CompoundTag t = js.getCompound(i);
            try {
                d.joueurs.put(t.getUUID("Id"), Joueur.load(t));
            } catch (RuntimeException e) {
                Crac.LOGGER.warn("[CRac] bases : joueur illisible ignoré ({})", e.toString());
            }
        }
        ListTag ps = tag.getList("Protections", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            CompoundTag t = ps.getCompound(i);
            try {
                d.protections.put(t.getUUID("Groupe"), t.getLong("Fin"));
            } catch (RuntimeException e) {
                Crac.LOGGER.warn("[CRac] bases : protection illisible ignorée ({})", e.toString());
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag js = new ListTag();
        this.joueurs.forEach((id, j) -> {
            CompoundTag t = j.save();
            t.putUUID("Id", id);
            js.add(t);
        });
        tag.put("Joueurs", js);
        ListTag ps = new ListTag();
        this.protections.forEach((g, fin) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Groupe", g);
            t.putLong("Fin", fin);
            ps.add(t);
        });
        tag.put("Protections", ps);
        return tag;
    }
}
