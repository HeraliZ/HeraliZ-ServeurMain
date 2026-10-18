package fr.heraliz.quetes.client;

import com.caracrea.character.Appearance;
import com.caracrea.character.AppearanceCodec;
import com.caracrea.client.ClientAppearances;
import com.caracrea.client.CompositeSkins;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lien souple avec CaraCrea (classe chargée seulement si le mod est là) : le skin peint d'une apparence de PNJ. Comme les
 * cadavres de HeraliZ Utils, on passe par les « aperçus » du créateur de personnage : une clé par apparence, partagée par
 * tous les PNJ qui la portent. CaraCrea peint en arrière-plan ; d'ici là, sa peau d'attente.
 */
final class CaraCreaPnj {
    private static final Logger LOG = LogUtils.getLogger();
    /** Apparences déjà confiées à CaraCrea (quelques PNJ au plus : jamais libérées avant de quitter le serveur). */
    private static final Map<CompoundTag, UUID> CLES = new HashMap<>();
    private static boolean casse;

    private CaraCreaPnj() {
    }

    /** Skin peint de l'apparence, ou null (illisible, ou lien avec CaraCrea coupé). */
    static ResourceLocation texture(CompoundTag apparence) {
        if (casse) return null;
        try {
            UUID cle = CLES.get(apparence);
            if (cle == null) {
                Appearance a = AppearanceCodec.fromNbt(apparence);
                if (a == null) return null;
                cle = UUID.nameUUIDFromBytes(("heraliz_quetes:pnj:" + apparence).getBytes(StandardCharsets.UTF_8));
                ClientAppearances.setPreview(cle, a);
                CLES.put(apparence.copy(), cle);
            }
            return CompositeSkins.skinFor(cle);
        } catch (LinkageError | RuntimeException e) {
            casse = true;
            LOG.warn("[heraliz_quetes] lien avec CaraCrea coupé (skin des fichiers) : {}", e.toString());
            return null;
        }
    }

    /** En quittant le serveur : rend les aperçus à CaraCrea. */
    static void toutLiberer() {
        if (!casse) {
            try {
                for (UUID cle : CLES.values()) ClientAppearances.removePreview(cle);
            } catch (LinkageError | RuntimeException e) {
                casse = true;
            }
        }
        CLES.clear();
    }
}
