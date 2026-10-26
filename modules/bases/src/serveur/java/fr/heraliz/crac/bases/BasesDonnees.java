package fr.heraliz.crac.bases;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Données du monde des bases gardées avec le monde principal ({@code data/crac_bases.dat}, comme les groupes) :
 * par joueur, l'étape du tutoriel, le temps passé en protection « nouveau joueur », le dernier /home, le dernier
 * /rtp, ses homes (/home poser) et la protection de base encore due ; par groupe, la fin de la protection anti-raid
 * de sa base (heure en ms).
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

    /** Un home (/home poser) : monde, pieds du joueur, regard. */
    public record Home(ResourceLocation monde, double x, double y, double z, float lacet, float tangage) {
        CompoundTag save(String nom) {
            CompoundTag t = new CompoundTag();
            t.putString("Nom", nom);
            t.putString("Monde", this.monde.toString());
            t.putDouble("X", this.x);
            t.putDouble("Y", this.y);
            t.putDouble("Z", this.z);
            t.putFloat("Lacet", this.lacet);
            t.putFloat("Tangage", this.tangage);
            return t;
        }

        @Nullable
        static Home load(CompoundTag t) {
            ResourceLocation m = ResourceLocation.tryParse(t.getString("Monde"));
            return m == null ? null : new Home(m, t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"), t.getFloat("Lacet"), t.getFloat("Tangage"));
        }
    }

    public static final class Joueur {
        public Etape etape = Etape.AUCUNE;
        /** Ticks de jeu passés en protection « nouveau joueur ». */
        public long phaseTicks;
        /** Heure (ms) du dernier /home réussi. */
        public long dernierHome;
        /**
         * Fin (heure en ms) et durée (ms) de la recharge de la téléportation vers la base, fixées au départ (26/10) : un
         * changement de grade ou de l'arbre du clan ne compte qu'à la téléportation suivante ; 0 avant la première.
         */
        public long finHome, dureeHome;
        /** Heure (ms) du dernier /rtp réussi. */
        public long dernierZone;
        /** La première base posée recevra la protection anti-raid. */
        public boolean protectionDue;
        /** Le home « base » automatique a déjà été donné (première base ou migration) : jamais redonné. */
        public boolean homeAuto;
        /** Bâtiment de la cabane du tutoriel (sa première fondation), -1 avant. */
        public long batiment = -1L;
        /** Côté de la première fondation où va la deuxième (0 à 3), -1 : n'importe lequel. */
        public int arete = -1;
        /** Tutoriel des bases dû à la création de son clan, lancé à son prochain passage au spawn hors combat (1.21.0). */
        public boolean tutoClan;
        /** Fin du tutoriel en attente (tour de caméra), gardée au redémarrage : effacement de la cabane à la connexion. */
        public boolean finEnAttente;
        /** Homes du joueur, par nom (ordre de création). */
        public final Map<String, Home> homes = new LinkedHashMap<>();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Etape", this.etape.ordinal());
            t.putLong("Phase", this.phaseTicks);
            t.putLong("Home", this.dernierHome);
            t.putLong("HomeFin", this.finHome);
            t.putLong("HomeDuree", this.dureeHome);
            t.putLong("Zone", this.dernierZone);
            t.putBoolean("Due", this.protectionDue);
            t.putBoolean("HomeAuto", this.homeAuto);
            t.putLong("Batiment", this.batiment);
            t.putInt("Arete", this.arete);
            t.putBoolean("TutoClan", this.tutoClan);
            t.putBoolean("FinAttente", this.finEnAttente);
            ListTag hs = new ListTag();
            this.homes.forEach((nom, h) -> hs.add(h.save(nom)));
            t.put("Homes", hs);
            return t;
        }

        static Joueur load(CompoundTag t) {
            Joueur j = new Joueur();
            j.etape = Etape.parRang(t.getInt("Etape"));
            j.phaseTicks = t.getLong("Phase");
            j.dernierHome = t.getLong("Home");
            j.finHome = t.getLong("HomeFin");
            j.dureeHome = t.getLong("HomeDuree");
            j.dernierZone = t.getLong("Zone");
            j.protectionDue = t.getBoolean("Due");
            j.homeAuto = t.getBoolean("HomeAuto");
            j.batiment = t.contains("Batiment") ? t.getLong("Batiment") : -1L;
            j.arete = t.contains("Arete") ? t.getInt("Arete") : -1;
            j.tutoClan = t.getBoolean("TutoClan");
            j.finEnAttente = t.getBoolean("FinAttente");
            ListTag hs = t.getList("Homes", Tag.TAG_COMPOUND);
            for (int i = 0; i < hs.size(); i++) {
                CompoundTag h = hs.getCompound(i);
                Home home = Home.load(h);
                if (home != null && !h.getString("Nom").isEmpty()) {
                    j.homes.put(h.getString("Nom"), home);
                }
            }
            return j;
        }
    }

    private final Map<UUID, Joueur> joueurs = new HashMap<>();
    private final Map<UUID, Long> protections = new HashMap<>();

    public static BasesDonnees get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BasesDonnees::load,
            () -> SafeSavedData.fresh(server.overworld(), ID, new BasesDonnees()), ID);
    }

    /** Les homes de tous les joueurs qui ne sont plus dans une de leurs bases (base effacée) : retirés. */
    void nettoyerHomes(net.minecraft.server.MinecraftServer server) {
        this.joueurs.forEach((id, j) -> j.homes.values().removeIf(h -> {
            var l = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, h.monde()));
            return l != null && MondeBases.estBases(l)
                && !Deplacements.baseEn(l, net.minecraft.core.BlockPos.containing(h.x(), h.y(), h.z()), id);
        }));
        this.marquer();
    }

    /** Remise à zéro du serveur : tutoriels, homes, recharges et protections de tous les joueurs oubliés. */
    void toutEffacer() {
        this.joueurs.clear();
        this.protections.clear();
        this.setDirty();
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
