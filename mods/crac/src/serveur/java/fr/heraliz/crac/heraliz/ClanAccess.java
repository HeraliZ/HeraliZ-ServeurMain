package fr.heraliz.crac.heraliz;

import java.util.Optional;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.group.Group;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Who may act in a claim. The group that owns it, as before; and when HeraliZ Clans is installed
 * ({@code heraliz.clansAccess}), the members of the clan of the group's owner, each by the permission of his rank
 * ({@code Perm.BASE_*}, the same ones the clan bunkers of HeraliZ Bases use). Soft dependency: the clans classes are
 * only touched through {@link Bridge}, and only when the mod is loaded. Server thread only.
 */
public final class ClanAccess {
    /** What a player wants to do in the claim, and the clan permission it takes. */
    public enum Action {
        /** Be there and use what one sees: beds, crafting blocks, buckets, tools (BASE_ENTRER). */
        ENTER,
        /** Place, upgrade, repair or demolish pieces, place placables and blocks (BASE_CONSTRUIRE). */
        BUILD,
        /** Mine blocks, pick up placables (BASE_CASSER). */
        BREAK,
        /** Doors of the pieces, vanilla doors, trapdoors and gates (BASE_PORTES). */
        DOORS,
        /** The upkeep stock of the Claim Block (BASE_COFFRES). */
        CHESTS,
        /** Upgrade or break the Claim Block (BASE_GERER). */
        MANAGE
    }

    private static final String CLANS = "heralisz_clans";
    private static Boolean loaded;
    private static boolean warned;

    private ClanAccess() {
    }

    /** Whether HeraliZ Clans is installed. */
    public static boolean clansLoaded() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded(CLANS);
        }
        return loaded;
    }

    private static boolean enabled() {
        return clansLoaded() && Config.SPEC.isLoaded() && Config.clansAccess.get();
    }

    /** A member of the group, or a member of its owner's clan holding the permission of {@code action}. */
    public static boolean allows(@Nullable Group group, @Nullable UUID player, Action action) {
        if (group == null || player == null) {
            return false;
        }
        return group.isMember(player) || viaClan(group, player, action);
    }

    /** Only the clan part of {@link #allows}. */
    public static boolean viaClan(Group group, UUID player, Action action) {
        if (!enabled()) {
            return false;
        }
        try {
            return Bridge.allows(group.owner(), player, action);
        } catch (LinkageError | RuntimeException e) {
            warnOnce(e);
            return false;
        }
    }

    /** Whether a member of the clan of the group's owner is online (the base counts as lived in, no inactivity decay). */
    public static boolean clanOnline(Group group) {
        if (!enabled()) {
            return false;
        }
        try {
            return Bridge.online(group.owner());
        } catch (LinkageError | RuntimeException e) {
            warnOnce(e);
            return false;
        }
    }

    /**
     * HeraliZ Clans: the CRac group of a player who has no group yet but may manage his clan's base (BASE_GERER, the
     * chief always), created on the spot with his clan's name, tag and colour, so that a clan claims its base without
     * going through /group. Null when clan access is off or the player may not.
     */
    @Nullable
    public static Group groupForClanManager(net.minecraft.server.MinecraftServer server, UUID player) {
        if (!enabled()) {
            return null;
        }
        try {
            return Bridge.groupForClanManager(server, player);
        } catch (LinkageError | RuntimeException e) {
            warnOnce(e);
            return null;
        }
    }

    private static void warnOnce(Throwable e) {
        if (!warned) {
            warned = true;
            Crac.LOGGER.warn("[CRac] HeraliZ Clans could not be asked about a claim: clan access is off", e);
        }
    }

    /** The only class that names HeraliZ Clans types: loaded on first use, which only happens with the mod present. */
    private static final class Bridge {
        private Bridge() {
        }

        static boolean allows(UUID owner, UUID player, Action action) {
            Optional<UUID> clan = fr.heralisz.clans.api.ClansApi.clanDe(owner);
            return clan.isPresent() && fr.heralisz.clans.api.ClansApi.aPerm(player, clan.get(), perm(action));
        }

        @Nullable
        static Group groupForClanManager(net.minecraft.server.MinecraftServer server, UUID player) {
            Optional<UUID> clan = fr.heralisz.clans.api.ClansApi.clanDe(player);
            if (clan.isEmpty() || !fr.heralisz.clans.api.ClansApi.aPerm(player, clan.get(), fr.heralisz.clans.model.Perm.BASE_GERER)) {
                return null;
            }
            Optional<fr.heralisz.clans.api.ClanInfo> info = fr.heralisz.clans.api.ClansApi.infos(clan.get());
            if (info.isEmpty()) {
                return null;
            }
            fr.heraliz.crac.group.GroupData data = fr.heraliz.crac.group.GroupData.get(server);
            String tag = info.get().tag();
            int color = info.get().banniere().accent() & 0xFFFFFF;
            Group g = data.create(info.get().nom(), tag, color, player);
            if (g != null) {
                Crac.LOGGER.info("[CRac] group {} [{}] created for the Claim Block of clan member {}", g.name(), g.tag(), player);
            }
            return g;
        }

        static boolean online(UUID owner) {
            return fr.heralisz.clans.api.ClansApi.clanDe(owner)
                .flatMap(fr.heralisz.clans.api.ClansApi::infos)
                .map(info -> !info.enLigne().isEmpty())
                .orElse(false);
        }

        private static fr.heralisz.clans.model.Perm perm(Action action) {
            return switch (action) {
                case ENTER -> fr.heralisz.clans.model.Perm.BASE_ENTRER;
                case BUILD -> fr.heralisz.clans.model.Perm.BASE_CONSTRUIRE;
                case BREAK -> fr.heralisz.clans.model.Perm.BASE_CASSER;
                case DOORS -> fr.heralisz.clans.model.Perm.BASE_PORTES;
                case CHESTS -> fr.heralisz.clans.model.Perm.BASE_COFFRES;
                case MANAGE -> fr.heralisz.clans.model.Perm.BASE_GERER;
            };
        }
    }
}
