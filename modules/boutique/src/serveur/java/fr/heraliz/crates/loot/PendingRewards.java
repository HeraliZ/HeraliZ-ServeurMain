package fr.heraliz.crates.loot;

import fr.heraliz.crates.crate.CrateType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recompenses gagnees par un joueur deconnecte avant la fin de l'animation :
 * elles lui sont donnees a sa prochaine connexion.
 * <p>
 * 02/11 (audit points 8 et 23) : aussi les OBJETS qui ne tiennent pas dans l'inventaire (recompenses de caisses, /trade,
 * kits, bons…) : gardes ici au lieu d'etre jetes au sol, et rendus des qu'il y a de la place ({@link #donner},
 * {@link #rendre} a la connexion et toutes les 5 s). Fichier ecrit tout de suite a chaque changement.
 */
public class PendingRewards extends SavedData {
    private static final String NAME = "heralizcrates_pending";
    private final Map<UUID, ListTag> pending = new HashMap<>();
    /** Objets qui attendent de la place dans l'inventaire de leur joueur (02/11). */
    private final Map<UUID, ListTag> objets = new HashMap<>();

    /**
     * Écrit tout de suite le fichier des récompenses en attente (25/10, audit : une récompense retirée seulement en
     * mémoire pouvait être redonnée après un plantage). Petit fichier : écrit à chaque récompense donnée.
     */
    public void sauverMaintenant(MinecraftServer server) {
        setDirty();
        save(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(NAME + ".dat").toFile());
    }

    public static PendingRewards get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PendingRewards::load, PendingRewards::new, NAME);
    }

    public static PendingRewards load(CompoundTag tag) {
        PendingRewards p = new PendingRewards();
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            try {
                p.pending.put(UUID.fromString(key), players.getList(key, Tag.TAG_COMPOUND));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag obj = tag.getCompound("Objets");
        for (String key : obj.getAllKeys()) {
            try {
                ListTag l = obj.getList(key, Tag.TAG_COMPOUND);
                if (!l.isEmpty()) p.objets.put(UUID.fromString(key), l);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, ListTag> e : pending.entrySet()) {
            players.put(e.getKey().toString(), e.getValue());
        }
        tag.put("Players", players);
        CompoundTag obj = new CompoundTag();
        for (Map.Entry<UUID, ListTag> e : objets.entrySet()) obj.put(e.getKey().toString(), e.getValue());
        tag.put("Objets", obj);
        return tag;
    }

    // ------------------------------------------------------------------ objets qui attendent de la place (02/11)
    /**
     * Donne un objet au joueur ; ce qui ne tient pas dans l'inventaire est garde ici (fichier ecrit tout de suite) et
     * rendu des qu'il y a de la place, jamais jete au sol (objets a vie compris). Le joueur est prevenu.
     */
    public static void donner(ServerPlayer p, ItemStack s) {
        if (s == null || s.isEmpty()) return;
        p.getInventory().add(s);
        p.getInventory().setChanged();
        if (s.isEmpty() || p.server == null) return;
        PendingRewards a = get(p.server);
        a.objets.computeIfAbsent(p.getUUID(), k -> new ListTag()).add(s.save(new CompoundTag()));
        a.sauverMaintenant(p.server);
        fr.heraliz.crates.HeralizCrates.LOGGER.info("[HeralizCrates] inventaire plein : {} x{} garde pour {} (rendu des qu'il y a de la place)",
                s.getHoverName().getString(), s.getCount(), p.getGameProfile().getName());
        p.sendSystemMessage(Component.translatableWithFallback("message.heralizcrates.attente",
                "Inventaire plein : %s t'attend ; il te sera donné dès qu'il y aura de la place.", s.getHoverName())
                .withStyle(ChatFormatting.GOLD));
    }

    /**
     * Rend les objets gardes de ce joueur, autant qu'il en tient (le reste continue d'attendre). Objets mis dans
     * l'inventaire PUIS fichier ecrit sans eux, sur le meme tick : un plantage ne les copie jamais.
     */
    public static void rendre(ServerPlayer p) {
        if (p.server == null || !p.isAlive() || p.isSpectator()) return;
        PendingRewards a = get(p.server);
        ListTag l = a.objets.get(p.getUUID());
        if (l == null) return;
        ListTag reste = new ListTag();
        int rendus = 0;
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = ItemStack.of(l.getCompound(i));
            if (s.isEmpty()) continue;
            int avant = s.getCount();
            p.getInventory().add(s);
            rendus += avant - s.getCount();
            if (!s.isEmpty()) reste.add(s.save(new CompoundTag()));
        }
        if (rendus == 0 && reste.size() == l.size()) return;
        p.getInventory().setChanged();
        if (reste.isEmpty()) a.objets.remove(p.getUUID());
        else a.objets.put(p.getUUID(), reste);
        a.sauverMaintenant(p.server);
        if (rendus > 0) {
            p.sendSystemMessage(Component.translatableWithFallback("message.heralizcrates.attente_rendue",
                    "Objets qui t'attendaient remis dans ton inventaire : %s.", rendus).withStyle(ChatFormatting.GREEN));
        }
    }

    /** Toutes les 5 s : objets gardes des joueurs connectes (seulement ceux qui en ont). */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        PendingRewards a = get(server);
        if (a.objets.isEmpty()) return;
        for (UUID u : new ArrayList<>(a.objets.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) rendre(p);
        }
    }

    /** Ajoute une recompense en attente et retourne l'etiquette enregistree (pour la retirer ensuite avec remove). */
    public CompoundTag add(UUID player, CompoundTag reward, CrateType type) {
        CompoundTag t = reward.copy();
        t.putString("crate", type.id);
        pending.computeIfAbsent(player, k -> new ListTag()).add(t);
        setDirty();
        return t;
    }

    /** Retire une recompense precise (celle retournee par add) ; false si elle a deja ete donnee. */
    public boolean remove(UUID player, CompoundTag stored) {
        ListTag list = pending.get(player);
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == stored) {
                list.remove(i);
                if (list.isEmpty()) pending.remove(player);
                setDirty();
                return true;
            }
        }
        return false;
    }

    public List<CompoundTag> take(UUID player) {
        ListTag list = pending.remove(player);
        List<CompoundTag> out = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) out.add(list.getCompound(i));
            setDirty();
        }
        return out;
    }
}
