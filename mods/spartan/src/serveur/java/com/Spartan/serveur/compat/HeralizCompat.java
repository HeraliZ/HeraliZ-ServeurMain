package com.Spartan.serveur.compat;

import com.Spartan.Spartan;
import com.Spartan.esp.Allies;
import com.Spartan.esp.Noise;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Branchements avec les mods HeraliZ, sans dépendance de compilation (chacun est facultatif ; un mod absent ou une API
 * changée n'empêche rien, le journal le dit) :
 * <ul>
 *    <li><b>alliés de l'anti-ESP</b> : coéquipiers d'un match (même partie, même équipe ; mode zombies : même partie),
 *    sinon membres du même clan. Jamais cachés l'un à l'autre (marqueurs, ping, HUD d'équipe) ;</li>
 *    <li><b>voix de CaraCrea</b> : un joueur caché qui parle est envoyé aux joueurs à portée de sa voix (portée
 *    normale ou chuchotement de la config de CaraCrea), sinon ils entendraient une voix sans personne.</li>
 * </ul>
 */
public final class HeralizCompat {
    private static final MethodHandles.Lookup L = MethodHandles.publicLookup();

    private HeralizCompat() {
    }

    /** Appelé à la mise en place commune (FMLCommonSetupEvent), une fois tous les mods construits. */
    public static void brancher() {
        allies();
        voix();
    }

    // ------------------------------------------------------------------ alliés
    private static MethodHandle clanDe;
    private static MethodHandle partieMatch;
    private static MethodHandle equipeDe;
    private static MethodHandle partieZombies;

    private static void allies() {
        if (ModList.get().isLoaded("heralisz_clans")) {
            clanDe = handle("fr.heralisz.clans.api.ClansApi", "clanDe", MethodType.methodType(Optional.class, UUID.class));
        }
        if (ModList.get().isLoaded("heralisz_matchs")) {
            try {
                Class<?> partie = Class.forName("fr.heralisz.matchs.moteur.api.Partie");
                partieMatch = handle("fr.heralisz.matchs.moteur.api.MatchApi", "partieDe", MethodType.methodType(partie, ServerPlayer.class));
                Class<?> equipe = Class.forName("fr.heralisz.matchs.moteur.api.Equipe");
                equipeDe = L.findVirtual(partie, "equipeDe", MethodType.methodType(equipe, UUID.class));
                partieZombies = handle("fr.heralisz.matchs.zombies.jeu.api.ZombiesApi", "partieDe", MethodType.methodType(partie, Entity.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                Spartan.LOGGER.warn("[Spartan] Matchs : API non reconnue ({}), coéquipiers non déclarés à l'anti-ESP.", e.toString());
            }
        }
        if (clanDe == null && partieMatch == null && partieZombies == null) {
            return;
        }
        Allies.register(HeralizCompat::allies);
        Spartan.LOGGER.info("[Spartan] Anti-ESP : alliés HeraliZ reconnus (clans : {}, matchs : {}, zombies : {}).",
                clanDe != null, partieMatch != null, partieZombies != null);
    }

    /** Vrai si a et b ne doivent jamais être cachés l'un à l'autre. */
    static boolean allies(ServerPlayer a, ServerPlayer b) {
        try {
            Object ma = partieMatch == null ? null : partieMatch.invoke(a);
            Object mb = partieMatch == null ? null : partieMatch.invoke(b);
            if (ma != null || mb != null) {
                // en match, seules les équipes comptent (deux membres d'un clan peuvent s'affronter en duel)
                if (ma != mb) {
                    return false;
                }
                Object ea = equipeDe.invoke(ma, a.getUUID());
                Object eb = equipeDe.invoke(mb, b.getUUID());
                return ea == eb;
            }
            Object za = partieZombies == null ? null : partieZombies.invoke((Entity) a);
            Object zb = partieZombies == null ? null : partieZombies.invoke((Entity) b);
            if (za != null || zb != null) {
                return za == zb;
            }
            if (clanDe != null) {
                Optional<?> ca = (Optional<?>) clanDe.invoke(a.getUUID());
                return ca.isPresent() && ca.equals(clanDe.invoke(b.getUUID()));
            }
        } catch (Throwable t) {
            throw new IllegalStateException(t); // signalé une fois par Allies, puis ignoré
        }
        return false;
    }

    // ------------------------------------------------------------------ voix de CaraCrea
    /** Joueurs qui parlent → chuchotent-ils ? (écrit par les événements de CaraCrea, thread serveur) */
    private static final Map<UUID, Boolean> PARLENT = new ConcurrentHashMap<>();
    private static Supplier<Double> portee = () -> 48.0;
    private static Supplier<Double> porteeChuchote = () -> 24.0;

    @SuppressWarnings("unchecked")
    private static void voix() {
        if (!ModList.get().isLoaded("caracrea")) {
            return;
        }
        try {
            Class<? extends Event> debut = (Class<? extends Event>) Class.forName("com.caracrea.api.voice.VoiceEvent$TalkStart");
            Class<? extends Event> fin = (Class<? extends Event>) Class.forName("com.caracrea.api.voice.VoiceEvent$TalkStop");
            MethodHandle joueur = L.findVirtual(Class.forName("com.caracrea.api.voice.VoiceEvent"), "getPlayer",
                    MethodType.methodType(UUID.class));
            MethodHandle chuchote = L.findVirtual(debut, "isWhisper", MethodType.methodType(boolean.class));
            Object server = Class.forName("com.caracrea.CaraCreaConfig").getField("SERVER").get(null);
            Object normal = server.getClass().getField("voiceRange").get(server);
            Object bas = server.getClass().getField("whisperRange").get(server);
            if (normal instanceof ForgeConfigSpec.DoubleValue n && bas instanceof ForgeConfigSpec.DoubleValue w) {
                portee = n;
                porteeChuchote = w;
            }
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) debut, e -> {
                try {
                    PARLENT.put((UUID) joueur.invoke(e), (boolean) chuchote.invoke(e));
                } catch (Throwable ignored) {
                    // jamais casser la voix
                }
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) fin, e -> {
                try {
                    PARLENT.remove((UUID) joueur.invoke(e));
                } catch (Throwable ignored) {
                    // jamais casser la voix
                }
            });
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
                if (e.phase == TickEvent.Phase.START && !PARLENT.isEmpty()) {
                    PARLENT.forEach((id, bas2) -> Noise.voice(id, bas2 ? porteeChuchote.get() : portee.get()));
                }
            });
            Spartan.LOGGER.info("[Spartan] Anti-ESP : voix de CaraCrea reliée (un joueur caché qui parle est montré à ceux qui l'entendent).");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Spartan.LOGGER.warn("[Spartan] CaraCrea : API de la voix non reconnue ({}). Un joueur caché qui parle pourrait "
                    + "être entendu sans être vu.", e.toString());
        }
    }

    /** Oubli d'un joueur déconnecté (au cas où CaraCrea n'envoie pas TalkStop). */
    public static void oublier(UUID joueur) {
        PARLENT.remove(joueur);
    }

    private static MethodHandle handle(String classe, String nom, MethodType type) {
        try {
            return L.findStatic(Class.forName(classe), nom, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            Spartan.LOGGER.warn("[Spartan] {}.{} introuvable ({}) : branchement ignoré.", classe, nom, e.toString());
            return null;
        }
    }
}
