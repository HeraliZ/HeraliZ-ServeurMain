package fr.heralisz.clans.territory;

import fr.heralisz.clans.ClansConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bonus de loot des membres presents dans une zone que leur clan tient, applique par HeraliZ Loot (heraliz_loot) quand
 * ils ouvrent un conteneur : chances d'objets plus rares et quantites. Contrat entre les deux mods, sans dependance
 * (CLAUDE.md §5) : donnees persistantes du joueur {@code PlayerPersisted.HeralizBonusLoot.clan = {Pct: <n>, Fin: <heure en ms>}},
 * posees et renouvelees toutes les 60 s tant qu'il y est (valables 90 s : s'eteignent seules apres une deconnexion ou un
 * plantage), retirees a la sortie de la zone.
 */
final class LootBonus {
    private static final String PERSISTE = "PlayerPersisted";
    private static final String CLE = "HeralizBonusLoot";
    private static final String SOURCE = "clan";
    private static final long RENOUVELLEMENT_MS = 60_000;
    private static final long VALIDITE_MS = 90_000;

    /** Joueur -> dernier renouvellement (ms). */
    private final Map<UUID, Long> derniers = new HashMap<>();

    LootBonus(MinecraftServer server) {
    }

    /** Le joueur est dans une zone de son clan : pose (ou renouvelle) le bonus. */
    void present(ServerPlayer p, long now) {
        Long last = derniers.get(p.getUUID());
        if (last != null && now - last < RENOUVELLEMENT_MS) return;
        derniers.put(p.getUUID(), now);
        CompoundTag source = new CompoundTag();
        source.putInt("Pct", ClansConfig.LOOT_BONUS.get());
        source.putLong("Fin", now + VALIDITE_MS);
        CompoundTag donnees = p.getPersistentData();
        CompoundTag persiste = donnees.getCompound(PERSISTE);
        CompoundTag sources = persiste.getCompound(CLE);
        sources.put(SOURCE, source);
        persiste.put(CLE, sources);
        donnees.put(PERSISTE, persiste);
    }

    /** Le joueur n'est plus dans une zone de son clan : retire le bonus s'il l'avait. */
    void absent(ServerPlayer p) {
        if (derniers.remove(p.getUUID()) != null) {
            CompoundTag persiste = p.getPersistentData().getCompound(PERSISTE);
            if (persiste.contains(CLE, Tag.TAG_COMPOUND)) {
                persiste.getCompound(CLE).remove(SOURCE);
            }
        }
    }

    /** Deconnexion : on oublie (le bonus s'eteint seul). */
    void oublier(UUID joueur) {
        derniers.remove(joueur);
    }

    boolean aLeBonus(UUID joueur) {
        return derniers.containsKey(joueur);
    }
}
