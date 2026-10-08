package com.caracrea.server;

import com.caracrea.CaraCrea;
import com.caracrea.voice.net.VoiceSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the voice chat needs from the HeraliZ mods, read without a compile dependency (server thread, once per tick and
 * player):
 * <ul>
 *   <li>matches ({@code heralisz_matchs}): {@code MatchApi.partieDe(UUID)}, {@code Partie.id()}, {@code
 *   Partie.spectateurs()}, {@code Partie.equipeDe(UUID)}, {@code Equipe.id()} — the match, team and whether the player
 *   spectates it. Without the API: the {@code heralisz:en_match} flag (one shared match per dimension, spectator game
 *   mode = spectator of the match);</li>
 *   <li>downed players (Reanimation), through {@link Busy#downed}.</li>
 * </ul>
 */
final class HeralizVoiceCompat {
    private static final String IN_MATCH = "heralisz:en_match";
    private static boolean looked;
    private static MethodHandle matchOf;
    private static MethodHandle matchId;
    private static MethodHandle spectators;
    private static MethodHandle teamOf;
    private static MethodHandle teamId;
    private static boolean broken;
    /** Match and team ids (strings) as small numbers. */
    private static final Map<String, Integer> MATCHES = new HashMap<>();
    private static final Map<String, Integer> TEAMS = new HashMap<>();
    private static final Map<Object, Integer> DIMENSIONS = new HashMap<>();

    private HeralizVoiceCompat() {
    }

    private static void lookUp() {
        looked = true;
        if (!ModList.get().isLoaded("heralisz_matchs")) {
            return;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> api = Class.forName("fr.heralisz.matchs.moteur.api.MatchApi");
            Class<?> partie = Class.forName("fr.heralisz.matchs.moteur.api.Partie");
            Class<?> equipe = Class.forName("fr.heralisz.matchs.moteur.api.Equipe");
            matchOf = lookup.findStatic(api, "partieDe", MethodType.methodType(partie, UUID.class))
                    .asType(MethodType.methodType(Object.class, UUID.class));
            matchId = lookup.findVirtual(partie, "id", MethodType.methodType(String.class))
                    .asType(MethodType.methodType(String.class, Object.class));
            spectators = lookup.findVirtual(partie, "spectateurs", MethodType.methodType(java.util.Set.class))
                    .asType(MethodType.methodType(Collection.class, Object.class));
            teamOf = lookup.findVirtual(partie, "equipeDe", MethodType.methodType(equipe, UUID.class))
                    .asType(MethodType.methodType(Object.class, Object.class, UUID.class));
            teamId = lookup.findVirtual(equipe, "id", MethodType.methodType(String.class))
                    .asType(MethodType.methodType(String.class, Object.class));
            CaraCrea.LOGGER.info("Voice chat: HeraliZ matches found, voices kept inside each match");
        } catch (ReflectiveOperationException | LinkageError e) {
            matchOf = null;
            CaraCrea.LOGGER.warn("Voice chat: HeraliZ matches found but not their API ({}); using the in-match flag", e.toString());
        }
    }

    private static int intern(Map<String, Integer> ids, String id) {
        Integer known = ids.get(id);
        if (known == null) {
            known = ids.size() + 1;
            ids.put(id, known);
        }
        return known;
    }

    /** Fills match, team and spectator flag of entry {@code i}. */
    static void match(ServerPlayer player, VoiceSnapshot s, int i) {
        if (!looked) {
            lookUp();
        }
        if (matchOf != null && !broken) {
            try {
                UUID uuid = player.getUUID();
                Object partie = (Object) matchOf.invokeExact(uuid);
                if (partie == null) {
                    return;
                }
                s.match[i] = intern(MATCHES, (String) matchId.invokeExact(partie));
                Collection<?> watching = (Collection<?>) spectators.invokeExact(partie);
                if (watching != null && watching.contains(uuid)) {
                    s.flags[i] |= VoiceSnapshot.MATCH_SPECTATOR;
                } else {
                    Object team = (Object) teamOf.invokeExact(partie, uuid);
                    if (team != null) {
                        s.team[i] = intern(TEAMS, (String) teamId.invokeExact(team));
                    }
                }
                return;
            } catch (Throwable e) {
                broken = true;
                CaraCrea.LOGGER.warn("Voice chat: reading the HeraliZ match API failed ({}); using the in-match flag", e.toString());
            }
        }
        if (player.getPersistentData().getBoolean(IN_MATCH)) {
            // Without the API: everyone flagged in the same dimension shares one match (ids far from the API's).
            Object dimension = player.level().dimension();
            Integer id = DIMENSIONS.get(dimension);
            if (id == null) {
                id = 100_000 + DIMENSIONS.size();
                DIMENSIONS.put(dimension, id);
            }
            s.match[i] = id;
            if (player.isSpectator()) {
                s.flags[i] |= VoiceSnapshot.MATCH_SPECTATOR;
            }
        }
    }

    static boolean downed(ServerPlayer player) {
        return Busy.downed(player);
    }
}
