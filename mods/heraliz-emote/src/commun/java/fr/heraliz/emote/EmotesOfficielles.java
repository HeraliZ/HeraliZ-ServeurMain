package fr.heraliz.emote;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.data.opennbs.NBSFileUtils;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les animations officielles des emotes HeraliZ, lues dans le jar ({@code assets/heraliz_emote/emotes/<id>.json} et la
 * musique {@code <id>.nbs}, dans les ressources communes pour que le serveur les ait aussi). Le serveur transmet
 * toujours CETTE copie aux autres joueurs, jamais l'animation envoyée par le jeu du joueur : sans ça, un jeu modifié
 * jouait n'importe quelle animation (une emote encore verrouillée, une pose inventée) sous le numéro d'une emote
 * débloquée. Chargée une fois par emote, gardée en mémoire.
 */
public final class EmotesOfficielles {
    private static final String DOSSIER = "/assets/heraliz_emote/emotes/";
    private static final Map<UUID, Optional<KeyframeAnimation>> CACHE = new HashMap<>();

    private EmotesOfficielles() {
    }

    /** L'animation officielle de cette emote HeraliZ (avec sa musique), ou null si ce n'en est pas une ou si elle est illisible. */
    @Nullable
    public static synchronized KeyframeAnimation de(@Nullable UUID uuid) {
        if (uuid == null) return null;
        return CACHE.computeIfAbsent(uuid, u -> Optional.ofNullable(lire(u))).orElse(null);
    }

    @Nullable
    private static KeyframeAnimation lire(UUID uuid) {
        ListeEmotes.Emote e = ListeEmotes.de(uuid);
        if (e == null) return null;
        try (InputStream in = EmotesOfficielles.class.getResourceAsStream(DOSSIER + e.id() + ".json")) {
            if (in == null) {
                HeralizEmote.LOGGER.error("Emote {} : animation absente du jar", e.id());
                return null;
            }
            List<KeyframeAnimation> lues = AnimationSerializing.deserializeAnimation(in);
            KeyframeAnimation trouvee = null;
            for (KeyframeAnimation a : lues) if (uuid.equals(a.getUuid())) trouvee = a;
            if (trouvee == null) {
                HeralizEmote.LOGGER.error("Emote {} : le fichier ne porte pas le numéro {}", e.id(), uuid);
                return null;
            }
            KeyframeAnimation.AnimationBuilder b = trouvee.mutableCopy();
            try (InputStream musique = EmotesOfficielles.class.getResourceAsStream(DOSSIER + e.id() + ".nbs")) {
                if (musique != null) b.song = NBSFileUtils.read(new DataInputStream(musique));
            }
            return b.build();
        } catch (Exception ex) {
            HeralizEmote.LOGGER.error("Emote {} : animation illisible", e.id(), ex);
            return null;
        }
    }
}
