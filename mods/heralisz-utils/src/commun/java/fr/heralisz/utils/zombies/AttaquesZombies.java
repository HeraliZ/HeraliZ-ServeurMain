package fr.heralisz.utils.zombies;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

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

    @Nullable
    public static Genre genre(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id == null || !"mutantszombies".equals(id.getNamespace())) return null;
        String s = id.toString();
        for (Genre g : Genre.values()) if (g.id.equals(s)) return g;
        return null;
    }
}
