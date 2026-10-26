package fr.heraliz.corps.client;

import com.caracrea.character.Appearance;
import com.caracrea.character.AppearanceCodec;
import com.caracrea.client.ClientAppearances;
import com.caracrea.client.CompositeSkins;
import fr.heraliz.corps.HeralizCorps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lien souple avec CaraCrea (charge seulement si le mod est present) : la peau peinte d'une apparence enregistree sur un
 * corps. On passe par les « apercus » de CaraCrea (ceux de son createur de personnage) : une cle par apparence, gardee
 * tant qu'un corps l'utilise (compteur), liberee ensuite. CaraCrea peint en arriere-plan ; d'ici la, sa peau d'attente.
 */
final class CaraCreaPeaux {
    private CaraCreaPeaux() {
    }

    private static final Map<UUID, Integer> UTILISATIONS = new HashMap<>();
    private static final Map<UUID, Boolean> FIN = new HashMap<>();
    private static boolean casse;

    /** Cle de l'apparence (retenue une fois de plus), ou null si l'apparence est illisible. */
    @Nullable
    static UUID retenir(UUID proprietaire, CompoundTag apparenceNbt) {
        if (casse) return null;
        try {
            Appearance a = AppearanceCodec.fromNbt(apparenceNbt);
            if (a == null) return null;
            UUID cle = UUID.nameUUIDFromBytes(("heraliz_corps:" + proprietaire + ":" + apparenceNbt).getBytes(StandardCharsets.UTF_8));
            int n = UTILISATIONS.merge(cle, 1, Integer::sum);
            if (n == 1) {
                ClientAppearances.setPreview(cle, a);
                FIN.put(cle, a.bodyType().slim());
            }
            return cle;
        } catch (LinkageError | RuntimeException e) {
            casse = true;
            HeralizCorps.LOG.warn("[corps] lien avec CaraCrea coupe (skin vanilla) : {}", e.toString());
            return null;
        }
    }

    static boolean fin(UUID cle) {
        return Boolean.TRUE.equals(FIN.get(cle));
    }

    /** Peau peinte (ou peau d'attente de CaraCrea) ; {@code defaut} si le lien est casse. */
    static ResourceLocation texture(UUID cle, ResourceLocation defaut) {
        if (casse) return defaut;
        try {
            ResourceLocation r = CompositeSkins.skinFor(cle);
            return r == null ? defaut : r;
        } catch (LinkageError | RuntimeException e) {
            casse = true;
            return defaut;
        }
    }

    static void liberer(UUID cle) {
        Integer n = UTILISATIONS.get(cle);
        if (n == null) return;
        if (n > 1) {
            UTILISATIONS.put(cle, n - 1);
            return;
        }
        UTILISATIONS.remove(cle);
        FIN.remove(cle);
        if (casse) return;
        try {
            ClientAppearances.removePreview(cle);
        } catch (LinkageError | RuntimeException e) {
            casse = true;
        }
    }

    static void toutLiberer() {
        for (UUID cle : UTILISATIONS.keySet().toArray(new UUID[0])) {
            UTILISATIONS.put(cle, 1);
            liberer(cle);
        }
    }
}
