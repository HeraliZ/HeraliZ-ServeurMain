package fr.heraliz.crac.dev;

import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Clan access test (dev client with HeraliZ Clans only; the only bench class that names clans types, loaded on the
 * first "clan" step): a hut claimed by the CRac group of a fake player who leads the clan "CLN", and the real player
 * put in that clan with a chosen rank. He is no member of the CRac group: whatever he may do there comes from his clan
 * permissions (ClanAccess).
 */
public final class ClanBench {
    private static final Logger LOG = LogManager.getLogger("crac-banc");
    public static final UUID FAKE_CHIEF = UUID.nameUUIDFromBytes("crac-banc-chef".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private ClanBench() {
    }

    /** {"clan": "setup x y z"}, {"clan": "grade RECRUE|MEMBRE|HAUT_GRADE"}. */
    public static void run(ServerLevel level, ServerPlayer p, String arg) {
        String[] a = arg.trim().split("\\s+");
        fr.heralisz.clans.server.ClanStore store = fr.heralisz.clans.server.ClanStore.get();
        if (store == null) {
            LOG.warn("[crac-banc] clans non charges");
            return;
        }
        if (a[0].equals("setup")) {
            BlockPos ref = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            BancServeur.cabane(level, ref, FAKE_CHIEF, "Clanique", "CLN", List.of(Tier.STONE, Tier.STONE, Tier.WOOD, Tier.STONE));
            long now = System.currentTimeMillis();
            fr.heralisz.clans.model.Clan c = store.clanDe(FAKE_CHIEF);
            if (c == null) {
                c = new fr.heralisz.clans.model.Clan(UUID.randomUUID(), "CLN", "Clanique", FAKE_CHIEF, now);
                c.membres.put(FAKE_CHIEF, new fr.heralisz.clans.model.Membre(FAKE_CHIEF, "ChefBanc", fr.heralisz.clans.model.Grade.CHEF, now, now, null));
                c.membres.put(p.getUUID(), new fr.heralisz.clans.model.Membre(p.getUUID(), p.getGameProfile().getName(),
                    fr.heralisz.clans.model.Grade.RECRUE, now, now, null));
                store.ajouter(c);
            }
            LOG.info("[crac-banc] clan CLN : chef {} (faux joueur), {} recrue ; ClansApi dit clan du joueur = {}", FAKE_CHIEF,
                p.getGameProfile().getName(), fr.heralisz.clans.api.ClansApi.clanDe(p.getUUID()).isPresent());
        } else if (a[0].equals("grade")) {
            fr.heralisz.clans.model.Clan c = store.clanDe(p.getUUID());
            if (c != null && c.membres.get(p.getUUID()) != null) {
                c.membres.get(p.getUUID()).grade = fr.heralisz.clans.model.Grade.valueOf(a[1]);
                LOG.info("[crac-banc] grade du joueur dans CLN : {} ; BASE_CONSTRUIRE = {}", a[1],
                    fr.heralisz.clans.api.ClansApi.aPerm(p.getUUID(), c.id, fr.heralisz.clans.model.Perm.BASE_CONSTRUIRE));
            }
        }
        fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement.sendToPlayer(level, p);
    }
}
