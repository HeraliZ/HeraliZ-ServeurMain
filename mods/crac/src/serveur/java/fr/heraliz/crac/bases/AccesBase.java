package fr.heraliz.crac.bases;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

/**
 * Accès donnés dans l'armoire à outils (CRac 1.10.0, écran validé le 18/10) : pour chaque groupe (une base par groupe),
 * les joueurs à qui son propriétaire a donné l'accès, comme l'autorisation d'une TC de Rust. Un seul niveau : avec
 * l'accès, on ouvre les portes et les coffres, on construit et on répare ; seul le propriétaire gère la liste. Les
 * membres du groupe gardent leur accès ; celui du clan n'est plus donné tout seul ({@code ClanAccess}). Gardé avec le
 * monde principal ({@code data/crac_acces_bases.dat}).
 */
public final class AccesBase extends SafeSavedData {
    public static final String ID = "crac_acces_bases";
    /** Joueurs au plus dans la liste d'une base (en plus de son groupe). */
    public static final int MAX = 32;

    /** Groupe -> (joueur -> dernier pseudo connu), dans l'ordre où l'accès a été donné. */
    private final Map<UUID, LinkedHashMap<UUID, String>> acces = new HashMap<>();
    /**
     * Joueur -> l'armoire qu'il a posée lui-même (monde, position) : une seule par joueur (demande du propriétaire,
     * 18/10, CRac 1.11.0) ; il peut avoir l'accès à d'autres.
     */
    private final Map<UUID, Armoire> armoires = new HashMap<>();

    public record Armoire(String monde, long pos) {
    }

    /** L'armoire posée par ce joueur, si elle tient toujours son claim ; sinon null. */
    @org.jetbrains.annotations.Nullable
    public Armoire armoireDe(MinecraftServer server, UUID joueur) {
        Armoire a = this.armoires.get(joueur);
        if (a == null) {
            return null;
        }
        for (net.minecraft.server.level.ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().toString().equals(a.monde())
                && fr.heraliz.crac.claim.ClaimManager.getByAnchor(l, net.minecraft.core.BlockPos.of(a.pos())) != null) {
                return a;
            }
        }
        return null;
    }

    public void armoirePosee(UUID joueur, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        this.armoires.put(joueur, new Armoire(level.dimension().location().toString(), pos.asLong()));
        this.setDirty();
    }

    public static AccesBase get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AccesBase::load, () -> SafeSavedData.fresh(server.overworld(), ID, new AccesBase()), ID);
    }

    /** Le joueur a-t-il reçu l'accès à la base de ce groupe ? (fil du serveur) */
    public static boolean a(UUID groupe, UUID joueur) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || groupe == null || joueur == null) {
            return false;
        }
        Map<UUID, String> m = get(server).acces.get(groupe);
        return m != null && m.containsKey(joueur);
    }

    /** Joueurs qui ont l'accès (pseudo connu), sans les membres du groupe. */
    public Map<UUID, String> de(UUID groupe) {
        Map<UUID, String> m = this.acces.get(groupe);
        return m == null ? Map.of() : Collections.unmodifiableMap(m);
    }

    /** Faux si la liste est pleine ou s'il l'avait déjà. */
    public boolean donner(UUID groupe, UUID joueur, String nom) {
        LinkedHashMap<UUID, String> m = this.acces.computeIfAbsent(groupe, k -> new LinkedHashMap<>());
        if (m.containsKey(joueur) || m.size() >= MAX) {
            return false;
        }
        m.put(joueur, nom);
        this.setDirty();
        return true;
    }

    public boolean retirer(UUID groupe, UUID joueur) {
        Map<UUID, String> m = this.acces.get(groupe);
        if (m == null || m.remove(joueur) == null) {
            return false;
        }
        if (m.isEmpty()) {
            this.acces.remove(groupe);
        }
        this.setDirty();
        return true;
    }

    /** Toute la liste d'une base (base effacée). */
    public void effacer(UUID groupe) {
        if (this.acces.remove(groupe) != null) {
            this.setDirty();
        }
    }

    /** Pseudo à jour quand le joueur se connecte. */
    public void renommer(UUID joueur, String nom) {
        for (Map<UUID, String> m : this.acces.values()) {
            if (m.containsKey(joueur) && !nom.equals(m.get(joueur))) {
                m.put(joueur, nom);
                this.setDirty();
            }
        }
    }

    public static AccesBase load(CompoundTag tag) {
        AccesBase d = new AccesBase();
        ListTag arm = tag.getList("Armoires", 10);
        for (int i = 0; i < arm.size(); i++) {
            CompoundTag a = arm.getCompound(i);
            if (a.hasUUID("Joueur")) {
                d.armoires.put(a.getUUID("Joueur"), new Armoire(a.getString("Monde"), a.getLong("Pos")));
            }
        }
        ListTag bases = tag.getList("Bases", 10);
        for (int i = 0; i < bases.size(); i++) {
            CompoundTag b = bases.getCompound(i);
            try {
                LinkedHashMap<UUID, String> m = new LinkedHashMap<>();
                ListTag js = b.getList("Joueurs", 10);
                for (int k = 0; k < js.size(); k++) {
                    CompoundTag j = js.getCompound(k);
                    m.put(j.getUUID("Id"), j.getString("Nom"));
                }
                if (!m.isEmpty()) {
                    d.acces.put(b.getUUID("Groupe"), m);
                }
            } catch (RuntimeException e) {
                Crac.LOGGER.error("[CRac] accès d'une base illisible, ignoré : {}", b, e);
            }
        }
        return d;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag bases = new ListTag();
        for (Map.Entry<UUID, LinkedHashMap<UUID, String>> e : this.acces.entrySet()) {
            CompoundTag b = new CompoundTag();
            b.putUUID("Groupe", e.getKey());
            ListTag js = new ListTag();
            for (Map.Entry<UUID, String> j : e.getValue().entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putUUID("Id", j.getKey());
                t.putString("Nom", j.getValue());
                js.add(t);
            }
            b.put("Joueurs", js);
            bases.add(b);
        }
        tag.put("Bases", bases);
        ListTag arm = new ListTag();
        for (Map.Entry<UUID, Armoire> e : this.armoires.entrySet()) {
            CompoundTag a = new CompoundTag();
            a.putUUID("Joueur", e.getKey());
            a.putString("Monde", e.getValue().monde());
            a.putLong("Pos", e.getValue().pos());
            arm.add(a);
        }
        tag.put("Armoires", arm);
        return tag;
    }
}
