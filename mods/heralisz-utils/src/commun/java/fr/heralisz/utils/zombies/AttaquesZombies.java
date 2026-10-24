package fr.heralisz.utils.zombies;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attaques animees des zombies de Mutants & Zombies (Crawler, Blister, Brute), sans toucher a leur jar : le serveur
 * (serveur/zombies/AttaquesZombiesServeur) ne laisse plus passer leurs coups « au contact » ; il lance un geste,
 * prevenu aux joueurs par un evenement d'entite, et ne touche qu'a l'impact du geste si la cible est encore a portee.
 * Le Crawler bondit en plus sur sa cible (degats a l'impact). Les joueurs (client/zombies) jouent le geste sur le
 * modele du mod. Constantes communes aux deux cotes : les durees doivent etre les memes.
 */
public final class AttaquesZombies {
    private AttaquesZombies() {
    }

    /** Evenements d'entite (broadcastEntityEvent) : geste de frappe lance, preparation du bond. Hors des ids de Minecraft. */
    public static final byte EVT_FRAPPE = 121, EVT_BOND = 122;

    public enum Genre {
        /** morsure et coup de griffes rapides, bond */
        CRAWLER("mutantszombies:crawler", 5, 14),
        /** revers du bras droit */
        BLISTER("mutantszombies:blister_zombie", 8, 16),
        /** les deux bras leves au-dessus de la tete puis abattus */
        BRUTE("mutantszombies:zombie_brute", 14, 22);

        public final String id;
        /** ticks entre le debut du geste et l'impact */
        public final int armement;
        /** ticks minimum entre deux gestes */
        public final int recharge;

        Genre(String id, int armement, int recharge) {
            this.id = id;
            this.armement = armement;
            this.recharge = recharge;
        }
    }

    /** Ticks de preparation du bond du Crawler (accroupi) avant le saut. */
    public static final int BOND_PREPARATION = 6;

    /**
     * Genre de chaque type d'entite deja vu (Optional.empty() : pas un zombie de Mutants & Zombies). genre() est appele a
     * chaque tick pour chaque creature (serveur) et a chaque image pour chaque creature dessinee (joueur) : sans ce cache,
     * une recherche dans le registre et une chaine de caracteres creee a chaque appel.
     */
    private static final Map<EntityType<?>, Optional<Genre>> CACHE = new ConcurrentHashMap<>();

    @Nullable
    public static Genre genre(Entity e) {
        EntityType<?> type = e.getType();
        Optional<Genre> g = CACHE.get(type);
        if (g == null) {
            g = Optional.ofNullable(calculer(type));
            CACHE.put(type, g);
        }
        return g.orElse(null);
    }

    @Nullable
    private static Genre calculer(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null || !"mutantszombies".equals(id.getNamespace())) return null;
        String s = id.toString();
        for (Genre g : Genre.values()) if (g.id.equals(s)) return g;
        return null;
    }
}
